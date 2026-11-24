package com.industrialcore.blockentity;

import com.industrialcore.block.TransporterBlock;
import com.industrialcore.block.TransporterCasingBlock;
import com.industrialcore.machine.TransporterCasingPart;
import com.industrialcore.machine.TransporterShellStyle;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.GridPos;
import com.industrialcore.machine.TransportChannel;
import com.industrialcore.machine.TransportRole;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.machine.TransporterRegistry;
import com.industrialcore.machine.TransporterStatus;
import com.industrialcore.machine.TransporterStructure;
import com.industrialcore.machine.TransporterTransfer;
import com.industrialcore.menu.TransporterMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Block entity of the Phase 26-29 transporters. One class serves all four tiers; the tier
 * ({@link TransporterKind}) selects performance, buffer size, FE capability and shell geometry,
 * so the four machines share their code without becoming indistinguishable from each other.
 *
 * <p>How a transfer works: the device holds a channel name and one role. On its interval (only
 * when its structure is valid) a sender asks {@link TransporterRegistry} for the receivers of its
 * channel - a small, pre-built, deterministically ordered list, never a world scan - walks those
 * candidates, skips unloaded chunks without force-loading them, and moves items or FE into the
 * first receiver that accepts them. Only one receiver per attempt. With no registered receiver
 * the search backs off for 20 ticks, so an empty channel costs nothing per tick.</p>
 *
 * <p>Safety: items are withdrawn from this buffer only after the destination really accepted them
 * (see {@link TransporterTransfer#moveBatch}), FE is withdrawn with {@code consume} and credited
 * with {@code generate} plus an automatic refund of any remainder, so neither items nor FE can be
 * duplicated or lost. A transfer costs 0 FE at every tier and loses 0 percent of the moved FE.</p>
 *
 * <p>Cycle safety: a device has exactly one role, so every channel edge points SEND -> RECEIVE
 * and no cycle can exist inside the channel network; FE cannot circulate.</p>
 *
 * <p>Performance: the shell is walked at most once per {@link #VALIDATE_INTERVAL_TICKS} (never
 * every tick), the registry entry is refreshed idempotently on the same rhythm, item movement is
 * stack/slot based (a 4096-item transfer is a bounded slot loop, never 4096 iterations), and the
 * neighbour FE lookups of a receiver are cached per block state exactly like the energy buffer
 * does.</p>
 */
public class TransporterBlockEntity extends BlockEntity
        implements MenuProvider, TransporterTransfer.Buffer {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_ROLE = 3;
    public static final int DATA_STRUCTURE = 4;
    public static final int DATA_RECEIVERS = 5;
    public static final int DATA_LAST_ITEMS = 6;
    public static final int DATA_LAST_POWER = 7;
    public static final int DATA_ITEM_ENABLED = 8;
    public static final int DATA_POWER_ENABLED = 9;
    public static final int DATA_COUNT = 10;

    /** One shell walk and one registry refresh per interval; never per tick. */
    private static final int VALIDATE_INTERVAL_TICKS = 20;
    /** Backoff after a transfer found no registered receiver on its channel. */
    private static final int NO_TARGET_RETRY_TICKS = 20;

    private static final String TAG_CHANNEL = "Channel";
    private static final String TAG_ROLE = "Role";
    private static final String TAG_ITEM_ENABLED = "ItemEnabled";
    private static final String TAG_POWER_ENABLED = "PowerEnabled";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_STATUS = "Status";

    private static final Direction[] DIRECTIONS = Direction.values();

    private final TransporterKind kind;
    private final TransporterItemHandler inventory;
    /** Null for the item-only tiers 26/27: they have no FE buffer and no energy capability. */
    @Nullable
    private final ModEnergyStorage energy;
    /** The device never moves, so its own position can be computed once. */
    private final GridPos self;

    private final BlockPos[] neighbourPositions = new BlockPos[DIRECTIONS.length];
    private final BlockState[] neighbourStates = new BlockState[DIRECTIONS.length];
    private final IEnergyStorage[] neighbourStorages = new IEnergyStorage[DIRECTIONS.length];

    private String channel = TransportChannel.NONE;
    private String channelKey = TransportChannel.NONE;
    private TransportRole role = TransportRole.SEND;
    private boolean itemEnabled = true;
    private boolean powerEnabled = true;
    private boolean structureValid;
    private TransporterStatus status = TransporterStatus.NO_STRUCTURE;

    private int intervalCounter;
    private int retryCooldown;
    private int validateCooldown;
    private int lastItemsMoved;
    private int lastPowerMoved;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy == null ? 0 : energy.getEnergyStored();
                case DATA_CAPACITY -> energy == null ? 0 : energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_ROLE -> role.ordinal();
                case DATA_STRUCTURE -> structureValid ? 1 : 0;
                case DATA_RECEIVERS -> registeredPeerCount();
                case DATA_LAST_ITEMS -> lastItemsMoved;
                case DATA_LAST_POWER -> lastPowerMoved;
                case DATA_ITEM_ENABLED -> itemEnabled ? 1 : 0;
                case DATA_POWER_ENABLED -> powerEnabled ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative; the client menu reads its synced copy.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TransporterBlockEntity(BlockPos pos, BlockState state) {
        this(resolveKind(state), pos, state);
    }

    private TransporterBlockEntity(TransporterKind kind, BlockPos pos, BlockState state) {
        this(ModBlockEntities.transporterType(kind), kind, pos, state);
    }

    /**
     * Constructor for tiers with their own block entity type (Phase 63's self-building machine),
     * so the block entity type always matches the block that created it.
     */
    protected TransporterBlockEntity(BlockEntityType<?> type, TransporterKind kind, BlockPos pos,
                                     BlockState state) {
        super(type, pos, state);
        this.kind = kind;
        this.inventory = new TransporterItemHandler(kind.slotCount());
        this.energy = kind.supportsPower()
                ? new ModEnergyStorage(kind.energyCapacity(), kind.energyMaxReceive())
                : null;
        this.self = new GridPos(pos.getX(), pos.getY(), pos.getZ());
        this.structureValid = kind.structureSize() == 0;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            neighbourPositions[index] = worldPosition.relative(DIRECTIONS[index]);
        }
    }

    private static TransporterKind resolveKind(BlockState state) {
        return state.getBlock() instanceof TransporterBlock block
                ? block.kind()
                : TransporterKind.BASIC;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TransporterBlockEntity transporter) {
        if (level instanceof ServerLevel serverLevel) {
            transporter.tick(serverLevel);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    private void tick(ServerLevel level) {
        if (energy != null) {
            if (energy.getMaxEnergyStored() != kind.energyCapacity()) {
                energy.setCapacity(kind.energyCapacity());
                setChanged();
            }
            if (energy.getMaxReceive() != kind.energyMaxReceive()) {
                energy.setMaxReceive(kind.energyMaxReceive());
            }
        }

        // Shell + registry rhythm: at most one full walk per 20 ticks, never every tick.
        if (validateCooldown > 0) {
            validateCooldown--;
        } else {
            revalidate(level);
        }

        if (!structureValid) {
            setStatus(TransporterStatus.NO_STRUCTURE);
            return;
        }
        if (channelKey.isEmpty()) {
            setStatus(TransporterStatus.NO_CHANNEL);
            return;
        }

        if (role == TransportRole.RECEIVE) {
            tickReceiver(level);
            return;
        }
        tickSender(level);
    }

    /** A receiver only exports its FE buffer; its item buffer is drained by pipes. */
    private void tickReceiver(ServerLevel level) {
        int pushed = 0;
        if (energy != null && powerEnabled && energy.getEnergyStored() > 0) {
            pushed = pushToNeighbours(level);
        }
        setStatus(pushed > 0 ? TransporterStatus.WORKING : TransporterStatus.IDLE);
    }

    private void tickSender(ServerLevel level) {
        if (retryCooldown > 0) {
            retryCooldown--;
        }
        intervalCounter++;
        if (intervalCounter < kind.transferIntervalTicks()) {
            return;
        }
        intervalCounter = 0;
        if (retryCooldown > 0) {
            return;
        }
        fire(level);
    }

    /**
     * One transfer attempt: items first, then FE - each with its own single-destination pass over
     * the same deterministic candidate list.
     */
    private void fire(ServerLevel level) {
        // Live view of the index bucket: iteration never allocates and no registry entry is
        // touched while we walk it (single server thread).
        Collection<GridPos> targets = TransporterRegistry.receivers(level, channelKey);
        boolean pending = (itemEnabled && !inventoryEmpty())
                || (powerEnabled && energy != null && energy.getEnergyStored() > 0);

        if (targets.isEmpty()) {
            // Nothing registered: back off instead of searching every tick. Items and FE stay.
            retryCooldown = NO_TARGET_RETRY_TICKS;
            setStatus(pending ? TransporterStatus.NO_RECEIVER : TransporterStatus.IDLE);
            return;
        }

        int itemsMoved = 0;
        if (itemEnabled && !inventoryEmpty()) {
            itemsMoved = fireItems(level, targets);
        }
        int powerMoved = 0;
        if (powerEnabled && energy != null && kind.powerTransferPerTick() > 0
                && energy.getEnergyStored() > 0) {
            powerMoved = firePower(level, targets);
        }
        lastItemsMoved = itemsMoved;
        lastPowerMoved = powerMoved;

        if (itemsMoved > 0 || powerMoved > 0) {
            setStatus(TransporterStatus.WORKING);
        } else {
            setStatus(pending ? TransporterStatus.RECEIVER_FULL : TransporterStatus.IDLE);
        }
        if (powerMoved > 0) {
            setChanged();
        }
    }

    /** Moves up to the tier's item cap into the first receiver that accepts anything. */
    private int fireItems(ServerLevel level, Collection<GridPos> targets) {
        int cap = kind.maxItemsPerTransfer();
        for (GridPos target : targets) {
            TransporterBlockEntity receiver = resolveReceiver(level, target);
            if (receiver == null) {
                continue;
            }
            int moved = TransporterTransfer.moveBatch(this, receiver, cap);
            if (moved > 0) {
                return moved; // exactly one destination per attempt
            }
        }
        return 0;
    }

    /** Moves up to the tier's FE cap into the first receiver with room; the rest is refunded. */
    private int firePower(ServerLevel level, Collection<GridPos> targets) {
        for (GridPos target : targets) {
            TransporterBlockEntity receiver = resolveReceiver(level, target);
            if (receiver == null || receiver.energy == null) {
                continue;
            }
            int amount = TransporterTransfer.powerMove(
                    energy.getEnergyStored(), receiver.energy.getEnergyStored(),
                    receiver.energy.getMaxEnergyStored(), kind.powerTransferPerTick());
            if (amount <= 0) {
                continue;
            }
            int taken = energy.consume(amount);
            if (taken <= 0) {
                return 0;
            }
            int accepted = receiver.energy.generate(taken);
            if (accepted < taken) {
                energy.generate(taken - accepted); // refund: total FE never changes
            }
            receiver.setChanged();
            setChanged();
            return taken;
        }
        return 0;
    }

    /**
     * Resolves a candidate position to a live receiver. Unloaded chunks are skipped before any
     * block access, so the lookup never force-loads a chunk - the transfer simply waits.
     */
    @Nullable
    private TransporterBlockEntity resolveReceiver(ServerLevel level, GridPos target) {
        if (target.equals(self)) {
            return null;
        }
        BlockPos targetPos = new BlockPos(target.x(), target.y(), target.z());
        if (!level.isLoaded(targetPos)) {
            return null;
        }
        if (!(level.getBlockEntity(targetPos) instanceof TransporterBlockEntity receiver)) {
            return null;
        }
        if (receiver.role != TransportRole.RECEIVE || !receiver.structureValid) {
            return null;
        }
        return receiver.channelKey.equals(this.channelKey) ? receiver : null;
    }

    /**
     * Pushes the FE buffer into the six neighbours, using the energy buffer's cache: a lookup is
     * only repeated when the neighbour's block state changes or its chunk is not loaded.
     */
    private int pushToNeighbours(ServerLevel level) {
        int budget = kind.powerPushPerTick();
        int moved = 0;
        for (int index = 0; index < DIRECTIONS.length && budget > 0; index++) {
            int stored = energy.getEnergyStored();
            if (stored <= 0) {
                break;
            }
            BlockPos neighbourPos = neighbourPositions[index];
            if (!level.isLoaded(neighbourPos)) {
                neighbourStates[index] = null;
                neighbourStorages[index] = null;
                continue;
            }
            BlockState state = level.getBlockState(neighbourPos);
            if (state != neighbourStates[index]) {
                neighbourStates[index] = state;
                neighbourStorages[index] = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, neighbourPos,
                        DIRECTIONS[index].getOpposite());
            }
            IEnergyStorage target = neighbourStorages[index];
            if (target == null || !target.canReceive()) {
                continue;
            }
            int accepted = target.receiveEnergy(Math.min(budget, stored), false);
            if (accepted > 0) {
                energy.consume(accepted);
                moved += accepted;
                budget -= accepted;
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------
    // Structure validation and registry
    // ------------------------------------------------------------------

    /** Immediate revalidation (block placement, a neighbour change, an NBT reload). */
    public void revalidateNow() {
        if (level instanceof ServerLevel serverLevel) {
            revalidate(serverLevel);
        }
    }

    /**
     * Walks the shell once and refreshes this device's registry entry. The walk skips cells in
     * unloaded chunks (an unloaded cell makes the structure invalid instead of loading it), so a
     * transporter at a chunk border never drags a chunk in just to look at it.
     */
    private void revalidate(ServerLevel level) {
        validateCooldown = VALIDATE_INTERVAL_TICKS;
        boolean valid = true;
        int size = kind.structureSize();
        if (size > 0) {
            BlockState ownState = getBlockState();
            if (!(ownState.getBlock() instanceof TransporterBlock)) {
                valid = false;
            } else {
                int facing = ownState.getValue(TransporterBlock.FACING).get3DDataValue();
                valid = structureMatches(level, facing, size);
            }
        }
        if (valid != structureValid) {
            boolean newlyFormed = !structureValid && valid;
            structureValid = valid;
            syncSettings();
            if (newlyFormed) {
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.45F, 1.35F);
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.40F, 1.587F);
            }
        }
        applyShellStyles(level, size, valid);
        refreshRegistry(level);
    }

    /**
     * Stamps the visual part of the machine onto every casing cell (Phase 62), or resets the
     * cells to their plain cube when the shell is broken.
     *
     * <p>Only the Advanced tier has a part layout, so Basic and the Quantum Array are untouched.
     * Writing uses {@link Block#UPDATE_CLIENTS} alone: the block state of a casing never takes
     * part in the shell check, so no neighbour revalidation is needed and stamping 55 cells
     * cannot cascade. A cell is only written when its state actually differs, which makes the
     * normal case (a formed machine validating every 20 ticks) read-only.</p>
     */
    private void applyShellStyles(ServerLevel level, int size, boolean valid) {
        if (!stylesShell() || size <= 0) {
            return;
        }
        Block casing = casingBlock();
        BlockState ownState = getBlockState();
        if (!(ownState.getBlock() instanceof TransporterBlock)) {
            return;
        }
        int facing = ownState.getValue(TransporterBlock.FACING).get3DDataValue();
        for (TransporterStructure.ShellCell cell : TransporterStructure.shellCells(size,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), facing)) {
            if (cell.controller()) {
                continue;
            }
            BlockPos position = new BlockPos(cell.world().x(), cell.world().y(), cell.world().z());
            if (!level.isLoaded(position)) {
                return; // never force-load a chunk just to paint it
            }
            BlockState state = level.getBlockState(position);
            if (!state.is(casing) || !(state.getBlock() instanceof TransporterCasingBlock)) {
                continue;
            }
            TransporterCasingPart part;
            int partFacing;
            if (valid) {
                TransporterShellStyle.CasingStyle style = TransporterShellStyle.styleFor(size,
                        cell.localX(), cell.localY(), cell.localZ(), facing);
                if (style == null) {
                    continue;
                }
                part = style.part();
                partFacing = style.facing();
            } else {
                if (TransporterCasingBlock.partOf(state) == TransporterCasingPart.PLAIN) {
                    continue;
                }
                part = TransporterCasingPart.PLAIN;
                partFacing = state.getValue(TransporterCasingBlock.FACING).get3DDataValue();
            }
            if (state.getValue(TransporterCasingBlock.PART) == part.id()
                    && state.getValue(TransporterCasingBlock.FACING).get3DDataValue() == partFacing) {
                continue;
            }
            level.setBlock(position, state
                    .setValue(TransporterCasingBlock.PART, part.id())
                    .setValue(TransporterCasingBlock.FACING,
                            Direction.from3DDataValue(partFacing)),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Whether this machine has a casing shell that is re-styled on validation (Phase 62). The
     * Phase 63 machine answers {@code false}: its parts carry their own permanent model.
     */
    protected boolean stylesShell() {
        return true;
    }

    /**
     * Structure test of the machine. The four shell tiers validate the hollow cube shell; the
     * Phase 63 controller overrides this with its own 3x2x3 ownership test.
     */
    protected boolean structureMatches(ServerLevel level, int facing, int size) {
        int offset = TransporterStructure.controllerOffset(size);
        int minY = worldPosition.getY() - offset;
        int maxY = worldPosition.getY() + (size - 1 - offset);
        if (minY < level.getMinBuildHeight() || maxY >= level.getMaxBuildHeight()) {
            return false;
        }
        return shellMatches(level, facing, size);
    }

    private boolean shellMatches(ServerLevel level, int facing, int size) {
        Block casing = casingBlock();
        for (GridPos cell : TransporterStructure.casingCells(size,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), facing)) {
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(position) || !level.getBlockState(position).is(casing)) {
                return false;
            }
        }
        for (GridPos cell : TransporterStructure.airCells(size,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), facing)) {
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(position) || !level.getBlockState(position).isAir()) {
                return false;
            }
        }
        return true;
    }

    private Block casingBlock() {
        return switch (kind) {
            case BASIC -> ModBlocks.TRANSPORTER_CASING.get();
            case ADVANCED -> ModBlocks.ADVANCED_TRANSPORTER_CASING.get();
            case ARRAY -> ModBlocks.QUANTUM_TRANSPORTER_CASING.get();
            // The single-block tier has no shell and never asks for one.
            case QUANTUM -> ModBlocks.QUANTUM_TRANSPORTER.get();
        };
    }

    /**
     * Keeps the connection cache in step: a usable device registers itself, anything else is
     * removed. {@link ChannelIndex#upsert} is idempotent, so calling this on a rhythm (or after
     * every configuration change) costs one map lookup and produces no churn.
     */
    private void refreshRegistry(ServerLevel level) {
        if (structureValid && !channelKey.isEmpty()) {
            TransporterRegistry.upsert(level, self, channelKey, role);
        } else {
            TransporterRegistry.remove(level, self);
        }
    }

    // ------------------------------------------------------------------
    // Configuration (validated server side; the payload handler re-checks distance)
    // ------------------------------------------------------------------

    /** Sets the channel name; invalid names (empty, too long, control chars) are rejected. */
    public void setChannel(String raw) {
        String display = TransportChannel.display(raw);
        String newKey = TransportChannel.key(display);
        if (display.isEmpty() || display.equals(channel)) {
            return;
        }
        channel = display;
        channelKey = newKey;
        retryCooldown = 0;
        if (level instanceof ServerLevel serverLevel) {
            refreshRegistry(serverLevel);
        }
        syncSettings();
        setChanged();
    }

    public void setRole(TransportRole next) {
        if (next == null || next == role) {
            return;
        }
        role = next;
        retryCooldown = 0;
        intervalCounter = 0;
        if (level instanceof ServerLevel serverLevel) {
            refreshRegistry(serverLevel);
        }
        syncSettings();
        setChanged();
    }

    /** The item on/off toggle exists on the FE tiers (Phase 28/29 GUI); 26/27 always send. */
    public void toggleItemEnabled() {
        if (!kind.supportsPower()) {
            return;
        }
        itemEnabled = !itemEnabled;
        retryCooldown = 0;
        syncSettings();
        setChanged();
    }

    /** The FE on/off toggle gates all FE movement: channel send, channel receive and push-out. */
    public void togglePowerEnabled() {
        if (!kind.supportsPower()) {
            return;
        }
        powerEnabled = !powerEnabled;
        retryCooldown = 0;
        syncSettings();
        setChanged();
    }

    /** Small settings packet to the client (channel, role, toggles) after a change. */
    private void syncSettings() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void setStatus(TransporterStatus next) {
        if (status != next) {
            status = next;
            setChanged();
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle: register on load, unregister on removal/unload
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            // Also the safety net for chunk reloads: validates the shell and (re)registers.
            revalidate(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            TransporterRegistry.remove(serverLevel, self);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel serverLevel) {
            TransporterRegistry.remove(serverLevel, self);
        }
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------
    // TransferBuffer view of the own inventory (no per-tick adapter objects)
    // ------------------------------------------------------------------

    @Override
    public int slots() {
        return inventory.getSlots();
    }

    @Override
    public int count(int slot) {
        return inRange(slot) ? inventory.getStackInSlot(slot).getCount() : 0;
    }

    @Override
    public Object token(int slot) {
        if (!inRange(slot)) {
            return null;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    public int space(int slot, Object token) {
        if (!inRange(slot) || !(token instanceof ItemStack wanted)) {
            return 0;
        }
        ItemStack present = inventory.getStackInSlot(slot);
        int limit = Math.min(inventory.getSlotLimit(slot), wanted.getMaxStackSize());
        if (present.isEmpty()) {
            return Math.max(0, limit);
        }
        if (!ItemStack.isSameItemSameComponents(present, wanted)) {
            return 0;
        }
        return Math.max(0, limit - present.getCount());
    }

    @Override
    public int extract(int slot, int amount) {
        if (!inRange(slot) || amount <= 0) {
            return 0;
        }
        return inventory.extractItem(slot, amount, false).getCount();
    }

    @Override
    public int insert(int slot, Object token, int amount) {
        if (!inRange(slot) || amount <= 0 || !(token instanceof ItemStack wanted)) {
            return 0;
        }
        ItemStack leftover = inventory.insertItem(slot, wanted.copyWithCount(amount), false);
        return amount - leftover.getCount();
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < inventory.getSlots();
    }

    private boolean inventoryEmpty() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore." + kind.machineId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TransporterMenu(containerId, playerInventory, this, dataAccess);
    }

    public TransporterKind getKind() {
        return kind;
    }

    @Nullable
    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getItemHandler() {
        return inventory;
    }

    /** Capability view of one face; a transporter buffer has no per-face rules. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return inventory;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public TransporterStatus getStatus() {
        return status;
    }

    public boolean isStructureValid() {
        return structureValid;
    }

    public String getChannel() {
        return channel;
    }

    public TransportRole getRole() {
        return role;
    }

    public boolean isItemEnabled() {
        return itemEnabled;
    }

    public boolean isPowerEnabled() {
        return powerEnabled;
    }

    public int getEnergyStoredForTesting() {
        return energy == null ? 0 : energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        if (energy != null) {
            energy.setEnergy(amount);
            setChanged();
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    private int registeredPeerCount() {
        if (!(level instanceof ServerLevel serverLevel) || channelKey.isEmpty()) {
            return 0;
        }
        // A sender shows its destinations, a receiver shows who could feed it.
        return role == TransportRole.SEND
                ? TransporterRegistry.receiverCount(serverLevel, channelKey)
                : TransporterRegistry.senderCount(serverLevel, channelKey);
    }

    // ------------------------------------------------------------------
    // Persistence / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_CHANNEL, channel);
        tag.putInt(TAG_ROLE, role.ordinal());
        tag.putBoolean(TAG_ITEM_ENABLED, itemEnabled);
        tag.putBoolean(TAG_POWER_ENABLED, powerEnabled);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        if (energy != null) {
            tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readSettings(tag);
        if (level != null && level.isClientSide) {
            // Display settings only; the authoritative buffer never comes from a block update.
            return;
        }
        if (energy != null && tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        }
        intervalCounter = 0;
        retryCooldown = 0;
        lastItemsMoved = 0;
        lastPowerMoved = 0;
        status = TransporterStatus.byOrdinal(tag.getInt(TAG_STATUS));
        if (level instanceof ServerLevel serverLevel) {
            // A re-load of an already placed device (save tests, structure swaps): rebuild the
            // shell verdict and move the registry entry to the restored channel/role.
            revalidate(serverLevel);
        } else {
            // Fresh NBT load: onLoad validates once the level is attached.
            structureValid = kind.structureSize() == 0;
            validateCooldown = 0;
        }
    }

    private void readSettings(CompoundTag tag) {
        if (tag.contains(TAG_CHANNEL)) {
            channel = TransportChannel.display(tag.getString(TAG_CHANNEL));
            channelKey = TransportChannel.key(channel);
        }
        if (tag.contains(TAG_ROLE)) {
            TransportRole[] roles = TransportRole.values();
            int ordinal = Math.max(0, Math.min(roles.length - 1, tag.getInt(TAG_ROLE)));
            role = roles[ordinal];
        }
        itemEnabled = !tag.contains(TAG_ITEM_ENABLED) || tag.getBoolean(TAG_ITEM_ENABLED);
        powerEnabled = !tag.contains(TAG_POWER_ENABLED) || tag.getBoolean(TAG_POWER_ENABLED);
        if (tag.contains(TAG_STATUS)) {
            status = TransporterStatus.byOrdinal(tag.getInt(TAG_STATUS));
        }
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // The settings tag above is useless without a packet carrying it: without this override
        // sendBlockUpdated only sends the block state, so a reopened GUI showed an empty channel
        // and stale toggles while the server had already stored them.
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Settings only; the GUI reads FE, status and counts through the container data.
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_CHANNEL, channel);
        tag.putInt(TAG_ROLE, role.ordinal());
        tag.putBoolean(TAG_ITEM_ENABLED, itemEnabled);
        tag.putBoolean(TAG_POWER_ENABLED, powerEnabled);
        tag.putInt(TAG_STATUS, status.ordinal());
        return tag;
    }

    // ------------------------------------------------------------------
    // Handler
    // ------------------------------------------------------------------

    /**
     * The whole buffer is a plain buffer on every face: pipes insert and extract freely, and the
     * only place items ever leave it is the single {@code moveBatch} call of a transfer.
     */
    private final class TransporterItemHandler extends ItemStackHandler {
        private TransporterItemHandler(int slots) {
            super(slots);
        }

        @Override
        protected void onContentsChanged(int slot) {
            // A pipe or player changed the buffer: retry a backed-off transfer immediately.
            retryCooldown = 0;
            setChanged();
        }
    }
}
