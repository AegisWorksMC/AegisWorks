package com.industrialcore.blockentity;

import com.industrialcore.machine.DistributionPlan;
import com.industrialcore.machine.DistributorOutputState;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.ItemFilterMode;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeConnectionRules;
import com.industrialcore.menu.ItemDistributorMenu;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity of the Phase 19 item distributor.
 *
 * <p>One internal buffer stack receives items from any face and is pushed into the adjacent
 * inventories in a round-robin order. There is no energy cost and no recipe: the block is pure
 * logistics, so a beginner can place it between a machine and two chests and it works.</p>
 *
 * <p>Rules of the distribution pass (see {@link DistributionPlan}):</p>
 * <ul>
 *   <li>only faces whose {@link FaceMode} allows extraction are outputs; {@code AUTO} (the
 *       default) and {@code OUTPUT} do, {@code INPUT} and {@code NONE} do not,</li>
 *   <li>the face the buffered items just came in through is never pushed back into,</li>
 *   <li>an output that is full or has no inventory is skipped, and its share goes to the next
 *       outputs,</li>
 *   <li>if nothing accepts anything the items stay in the buffer (nothing is voided),</li>
 *   <li>at most one insert per output per tick, so the work is bounded by six inserts.</li>
 * </ul>
 *
 * <p>Neighbour capabilities are cached per direction and invalidated by a neighbour block change,
 * a chunk unload or the block being removed, exactly like the item pipe. An idle distributor that
 * holds nothing and whose neighbourhood has already been resolved does no work per tick.</p>
 */
public class ItemDistributorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int BUFFER_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    public static final int DATA_STATUS = 0;
    public static final int DATA_NEXT_SIDE = 1;
    public static final int DATA_OUTPUT_BASE = 2;
    public static final int DATA_COUNT = DATA_OUTPUT_BASE + 6;

    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_FACES = "Faces";
    private static final String TAG_CURSOR = "Cursor";
    private static final String TAG_INTAKE = "Intake";
    private static final String TAG_STATUS = "Status";

    private static final Direction[] DIRECTIONS = Direction.values();
    /** Short backoff after every output refused; a blocked line must not re-scan every tick. */
    private static final int BLOCKED_RETRY_TICKS = 5;

    private final BufferHandler inventory = new BufferHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    /** Phase 11.3-11.5: per-face mode, target slot and item filter, reused as-is. */
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    private final IItemHandler[] sideHandlers = new IItemHandler[DIRECTIONS.length];

    /** Reused candidate list; avoids one list allocation per tick per distributor. */
    private final List<DistributionPlan.Output> candidates = new ArrayList<>(DIRECTIONS.length);
    private final DistributorOutputState[] outputStates = new DistributorOutputState[DIRECTIONS.length];

    private final BlockState[] neighbourStates = new BlockState[DIRECTIONS.length];
    private final IItemHandler[] neighbourHandlers = new IItemHandler[DIRECTIONS.length];
    private final ItemPipeBlockEntity[] neighbourPipes = new ItemPipeBlockEntity[DIRECTIONS.length];
    private int resolvedDirections;

    private int cursorSide;
    private int lastIntakeSide = -1;
    private int retryCooldown;
    private MachineStatus status = MachineStatus.NO_INPUT;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_STATUS -> status.ordinal();
                case DATA_NEXT_SIDE -> cursorSide;
                default -> {
                    int side = index - DATA_OUTPUT_BASE;
                    yield side >= 0 && side < DIRECTIONS.length ? outputStates[side].ordinal() : 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative; the client menu reads its own synced copy.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ItemDistributorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_DISTRIBUTOR.get(), pos, state);
        for (int side = 0; side < DIRECTIONS.length; side++) {
            outputStates[side] = DistributorOutputState.UNCONNECTED;
            sideHandlers[side] = new SideHandler(side);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ItemDistributorBlockEntity distributor) {
        if (level instanceof ServerLevel serverLevel) {
            distributor.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        ItemStack buffer = inventory.getStackInSlot(BUFFER_SLOT);
        if (buffer.isEmpty()) {
            lastIntakeSide = -1;
            setStatus(MachineStatus.NO_INPUT);
            if (resolvedDirections < DIRECTIONS.length) {
                // Resolve the neighbourhood once (after placement or a block change) so the GUI
                // can show the output states even while nothing is buffered.
                for (int side = 0; side < DIRECTIONS.length; side++) {
                    outputStates[side] = probe(level, side, ItemStack.EMPTY, null);
                }
            }
            return;
        }

        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }

        candidates.clear();
        for (int side = 0; side < DIRECTIONS.length; side++) {
            outputStates[side] = probe(level, side, buffer, candidates);
        }

        if (candidates.isEmpty()) {
            setStatus(MachineStatus.OUTPUT_FULL);
            retryCooldown = BLOCKED_RETRY_TICKS;
            return;
        }

        int[] plan = DistributionPlan.plan(candidates, buffer.getCount(), startIndex());
        int moved = 0;
        for (int index = 0; index < plan.length; index++) {
            int amount = plan[index];
            if (amount <= 0) {
                continue;
            }
            DistributionPlan.Output output = candidates.get(index);
            if (inventory.getStackInSlot(BUFFER_SLOT).isEmpty()) {
                break;
            }
            ItemPipeBlockEntity pipe = pipeAt(level, output.side());
            if (pipe != null) {
                Direction arrival = DIRECTIONS[output.side()].getOpposite();
                if (!pipe.accept(inventory.getStackInSlot(BUFFER_SLOT).copyWithCount(amount),
                        arrival, 0)) {
                    outputStates[output.side()] = DistributorOutputState.FULL;
                    continue;
                }
                inventory.extractItem(BUFFER_SLOT, amount, false);
                moved += amount;
                continue;
            }
            IItemHandler handler = handlerAt(level, DIRECTIONS[output.side()]);
            if (handler == null) {
                outputStates[output.side()] = DistributorOutputState.UNCONNECTED;
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler,
                    inventory.getStackInSlot(BUFFER_SLOT).copyWithCount(amount), false);
            int inserted = amount - remainder.getCount();
            if (inserted <= 0) {
                outputStates[output.side()] = DistributorOutputState.FULL;
                continue;
            }
            // The inserted copy left this block only now, so the buffer shrinks by exactly the
            // amount the neighbour accepted; a partial insert keeps the rest.
            inventory.extractItem(BUFFER_SLOT, inserted, false);
            moved += inserted;
        }

        if (moved <= 0) {
            setStatus(MachineStatus.OUTPUT_FULL);
            retryCooldown = BLOCKED_RETRY_TICKS;
            return;
        }
        int lastUsed = DistributionPlan.lastUsedIndex(plan);
        if (lastUsed >= 0) {
            cursorSide = (candidates.get(lastUsed).side() + 1) % DIRECTIONS.length;
        }
        setStatus(MachineStatus.WORKING);
        retryCooldown = 0;
        setChanged();
    }

    /** First candidate at or after the round-robin cursor, wrapping to the first one. */
    private int startIndex() {
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).side() >= cursorSide) {
                return index;
            }
        }
        return 0;
    }

    /**
     * Evaluates one face. With {@code collect} the face is added as a distribution candidate.
     *
     * @param buffer  the buffered stack, or an empty stack for the idle display pass
     * @param collect candidate list, or null to only classify the face
     */
    private DistributorOutputState probe(ServerLevel level, int side, ItemStack buffer,
                                         @Nullable List<DistributionPlan.Output> collect) {
        if (!faceConfig.mode(side).allowsExtract()) {
            return DistributorOutputState.DISABLED;
        }
        if (side == lastIntakeSide) {
            // Handing the item straight back through the face it came from would bounce it
            // between the source and this block forever.
            return DistributorOutputState.INTAKE;
        }
        boolean idle = buffer.isEmpty();
        ItemPipeBlockEntity pipe = pipeAt(level, side);
        if (pipe != null) {
            // A pipe is a transport, not an inventory: it takes one stack while its tube is empty.
            if (!PipeConnectionRules.canTraverse(pipe.getMode(DIRECTIONS[side].getOpposite()))) {
                return DistributorOutputState.UNCONNECTED;
            }
            if (!idle && pipe.getBufferCount() > 0) {
                return DistributorOutputState.FULL;
            }
            if (collect != null) {
                collect.add(new DistributionPlan.Output(side, buffer.getCount()));
            }
            return DistributorOutputState.READY;
        }
        IItemHandler handler = handlerAt(level, DIRECTIONS[side]);
        if (handler == null) {
            return DistributorOutputState.UNCONNECTED;
        }
        if (idle) {
            return DistributorOutputState.READY;
        }
        int capacity = capacityOf(handler, buffer);
        if (capacity <= 0) {
            return DistributorOutputState.FULL;
        }
        if (collect != null) {
            collect.add(new DistributionPlan.Output(side, capacity));
        }
        return DistributorOutputState.READY;
    }

    /** Items the neighbour would accept right now. */
    private static int capacityOf(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true);
        return Math.max(0, stack.getCount() - remainder.getCount());
    }

    private IItemHandler handlerAt(ServerLevel level, Direction direction) {
        int index = direction.get3DDataValue();
        resolveNeighbour(level, index);
        return neighbourHandlers[index];
    }

    @Nullable
    private ItemPipeBlockEntity pipeAt(ServerLevel level, int side) {
        resolveNeighbour(level, side);
        return neighbourPipes[side];
    }

    /** Resolves the capability and the pipe of one face, cached per neighbour block state. */
    private void resolveNeighbour(ServerLevel level, int index) {
        Direction direction = DIRECTIONS[index];
        BlockPos target = worldPosition.relative(direction);
        if (!level.isLoaded(target)) {
            markUnresolved(index);
            return;
        }
        BlockState state = level.getBlockState(target);
        if (state == neighbourStates[index] && neighbourStates[index] != null) {
            return;
        }
        neighbourHandlers[index] = level.getCapability(
                Capabilities.ItemHandler.BLOCK, target, direction.getOpposite());
        BlockEntity blockEntity = level.getBlockEntity(target);
        neighbourPipes[index] = blockEntity instanceof ItemPipeBlockEntity pipe ? pipe : null;
        if (neighbourStates[index] == null) {
            resolvedDirections++;
        }
        neighbourStates[index] = state;
    }

    private void markUnresolved(int index) {
        if (neighbourStates[index] != null) {
            neighbourStates[index] = null;
            resolvedDirections = Math.max(0, resolvedDirections - 1);
        }
        neighbourHandlers[index] = null;
        neighbourPipes[index] = null;
    }

    /** Called when a neighbour changed; the next tick resolves that capability again. */
    public void invalidateNeighbourCache() {
        for (int index = 0; index < DIRECTIONS.length; index++) {
            neighbourStates[index] = null;
            neighbourHandlers[index] = null;
            neighbourPipes[index] = null;
        }
        resolvedDirections = 0;
        retryCooldown = 0;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        invalidateNeighbourCache();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNeighbourCache();
    }

    private void setStatus(MachineStatus next) {
        if (status != next) {
            status = next;
            setChanged();
        }
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.item_distributor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ItemDistributorMenu(containerId, playerInventory, this, dataAccess);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public MachineStatus getStatus() {
        return status;
    }

    /** Side the next pass starts at. */
    public int getNextSide() {
        return cursorSide;
    }

    public DistributorOutputState getOutputState(int side) {
        return side >= 0 && side < DIRECTIONS.length
                ? outputStates[side] : DistributorOutputState.UNCONNECTED;
    }

    public int getBufferCount() {
        return inventory.getStackInSlot(BUFFER_SLOT).getCount();
    }

    public MachineFaceConfig getFaceConfig() {
        return faceConfig;
    }

    /** Capability view of one face; a null side keeps the unrestricted automation view. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return automationHandler;
        }
        return sideHandlers[side.get3DDataValue()];
    }

    /**
     * Applies one face action from the GUI. Server validated; the network handler has already
     * checked the player's distance and that the block still exists.
     *
     * <p>There is only one buffer slot, so the "target slot" action has nothing to cycle and is
     * rejected instead of silently doing nothing.</p>
     */
    public boolean applyFaceAction(int side, int action, @Nullable ItemStack filterStack) {
        if (level == null || level.isClientSide || side < 0 || side >= DIRECTIONS.length) {
            return false;
        }
        switch (action) {
            case MachineActions.FACE_MODE -> faceConfig.cycleMode(side);
            case MachineActions.FACE_FILTER -> faceConfig.cycleFilter(side);
            case MachineActions.FACE_ADD_ITEM -> {
                if (filterStack == null || filterStack.isEmpty()) {
                    return false;
                }
                faceConfig.toggleAllowed(side,
                        BuiltInRegistries.ITEM.getKey(filterStack.getItem()).toString());
            }
            case MachineActions.FACE_CLEAR -> faceConfig.setAllowed(side, List.of());
            default -> {
                return false;
            }
        }
        retryCooldown = 0;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack stack = inventory.getStackInSlot(BUFFER_SLOT);
        if (!stack.isEmpty()) {
            Block.popResource(level, worldPosition, stack.copy());
        }
        inventory.setStackInSlot(BUFFER_SLOT, ItemStack.EMPTY);
        lastIntakeSide = -1;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Persistence / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.put(TAG_FACES, saveFaces());
        tag.putInt(TAG_CURSOR, cursorSide);
        tag.putInt(TAG_INTAKE, lastIntakeSide);
        tag.putInt(TAG_STATUS, status.ordinal());
    }

    private CompoundTag saveFaces() {
        CompoundTag faces = new CompoundTag();
        for (int side = 0; side < DIRECTIONS.length; side++) {
            CompoundTag face = new CompoundTag();
            face.putByte("Mode", (byte) faceConfig.mode(side).ordinal());
            face.putInt("Slot", faceConfig.slot(side));
            face.putByte("Filter", (byte) faceConfig.filterMode(side).ordinal());
            ListTag ids = new ListTag();
            for (String id : faceConfig.allowedIds(side)) {
                ids.add(StringTag.valueOf(id));
            }
            face.put("Items", ids);
            faces.put(Integer.toString(side), face);
        }
        return faces;
    }

    private void loadFaces(CompoundTag faces) {
        for (int side = 0; side < DIRECTIONS.length; side++) {
            if (!faces.contains(Integer.toString(side))) {
                continue;
            }
            CompoundTag face = faces.getCompound(Integer.toString(side));
            faceConfig.setMode(side, FaceMode.byIndex(face.getByte("Mode") & 0xFF));
            faceConfig.setSlot(side, face.getInt("Slot"));
            faceConfig.setFilterMode(side, ItemFilterMode.byIndex(face.getByte("Filter") & 0xFF));
            List<String> ids = new ArrayList<>();
            ListTag items = face.getList("Items", 8);
            for (int index = 0; index < items.size(); index++) {
                ids.add(items.getString(index));
            }
            faceConfig.setAllowed(side, ids);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_FACES)) {
            loadFaces(tag.getCompound(TAG_FACES));
        }
        if (level != null && level.isClientSide) {
            // The client only receives display data; the authoritative buffer must never be
            // rebuilt from a block update.
            return;
        }
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        }
        cursorSide = Math.max(0, Math.min(DIRECTIONS.length - 1, tag.getInt(TAG_CURSOR)));
        lastIntakeSide = tag.getInt(TAG_INTAKE);
        if (lastIntakeSide < 0 || lastIntakeSide >= DIRECTIONS.length) {
            lastIntakeSide = -1;
        }
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
        retryCooldown = 0;
        invalidateNeighbourCache();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Display data only: the GUI reads the face settings from here; the buffer travels
        // through the menu slots.
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_FACES, saveFaces());
        return tag;
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** One buffer stack. The player may take items out of the GUI, automation may not. */
    public static final class BufferHandler extends ItemStackHandler {
        private final ItemDistributorBlockEntity owner;

        BufferHandler(ItemDistributorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == BUFFER_SLOT;
        }

        @Override
        protected void onContentsChanged(int slot) {
            // An arrival or a GUI change wakes the next pass immediately.
            owner.retryCooldown = 0;
            owner.setChanged();
        }
    }

    /** Automation view: insert into the buffer, never extract from it. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == BUFFER_SLOT ? inventory.getStackInSlot(BUFFER_SLOT) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != BUFFER_SLOT) {
                return stack;
            }
            return inventory.insertItem(BUFFER_SLOT, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // The distributor is the one that moves items out; a pipe must not drain its buffer.
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BUFFER_SLOT ? inventory.getSlotLimit(BUFFER_SLOT) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == BUFFER_SLOT;
        }
    }

    /**
     * Capability view of one face: the shared {@link MachineFaceConfig} rules of that face plus the
     * record of which face the items came in through. Extraction is never exposed, so a feeding
     * pipe can never take its own items back.
     */
    private final class SideHandler implements IItemHandler {
        private final int side;

        SideHandler(int side) {
            this.side = side;
        }

        private boolean allowed(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return faceConfig.canInsert(side, BUFFER_SLOT, id);
        }

        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == BUFFER_SLOT ? inventory.getStackInSlot(BUFFER_SLOT) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != BUFFER_SLOT || !allowed(stack)) {
                return stack;
            }
            ItemStack remainder = inventory.insertItem(BUFFER_SLOT, stack, simulate);
            if (!simulate && remainder.getCount() < stack.getCount()) {
                // Remember the arrival face so the next pass does not push the stack back into it.
                lastIntakeSide = side;
                retryCooldown = 0;
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BUFFER_SLOT ? inventory.getSlotLimit(BUFFER_SLOT) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == BUFFER_SLOT && allowed(stack);
        }
    }
}
