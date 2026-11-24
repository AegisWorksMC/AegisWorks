package com.industrialcore.blockentity;

import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.QuantumCompressorBalance;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.QuantumCompressorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

import java.util.Optional;

/**
 * Block entity of the Phase 25 quantum compressor.
 *
 * <p>One input slot and four output slots. The machine reads the shared
 * {@code industrialcore:crushing} recipes and processes up to {@link
 * QuantumCompressorBalance#BATCH_CAP} items in one {@link QuantumCompressorBalance#CYCLE_TICKS}
 * tick cycle, so it does not need a recipe type of its own and a datapack that changes a crushing
 * recipe changes the compressor with it.</p>
 *
 * <p>State machine, in the same spirit as the other machines of the mod:</p>
 * <ul>
 *   <li>a plan (locked input item, batch, result per item, total FE) is fixed when a cycle
 *       starts; changing or shrinking the input aborts the plan without paying anything out,</li>
 *   <li>the items are only removed from the input when the result has been placed,</li>
 *   <li>a blocked output (or not enough room for the whole batch) stops the machine before any
 *       FE is spent for that tick,</li>
 *   <li>missing FE keeps the progress and resumes at the same tick, paying only the remainder,</li>
 *   <li>inventory, FE, progress and the locked plan are stored in the NBT.</li>
 * </ul>
 *
 * <p>Performance: the recipe is looked up once per cycle start (and at most every
 * {@link QuantumCompressorBalance#RETRY_INTERVAL_TICKS} ticks while idle), never per tick. A
 * running cycle compares one item stack and walks the four output slots, so the per-tick cost does
 * not depend on the batch size or on the number of crushing recipes. No neighbour or network
 * lookup happens in {@code tick} at all.</p>
 */
public class QuantumCompressorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = QuantumCompressorBalance.OUTPUT_SLOTS;
    public static final int MOTOR_SLOT = OUTPUT_START + OUTPUT_COUNT;
    public static final int BATTERY_SLOT = MOTOR_SLOT + 1;
    public static final int SLOT_COUNT = BATTERY_SLOT + 1;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TIME = 4;
    public static final int DATA_BATCH = 5;
    public static final int DATA_COST = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TIME = "Time";
    private static final String TAG_COST = "Cost";
    private static final String TAG_BATCH = "Batch";

    private final CompressorItemHandler inventory = new CompressorItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy =
            new ModEnergyStorage(QuantumCompressorBalance.CAPACITY,
                    QuantumCompressorBalance.MAX_RECEIVE);

    private int progress;
    private int retryCooldown;
    private MachineStatus status = MachineStatus.NO_INPUT;

    /** The whole input batch the running cycle belongs to; empty means "not started". */
    private ItemStack lockedInput = ItemStack.EMPTY;
    /** Result of one input item (never multiplied by the batch, so it stays a legal stack). */
    private ItemStack plannedResultPerItem = ItemStack.EMPTY;
    private int lockedBatch;
    private int plannedTime;
    private long plannedTotalCost;

    /** Reused scratch arrays; the output scan runs every tick, so it must not allocate. */
    private final int[] slotCounts = new int[OUTPUT_COUNT];
    private final boolean[] sameItem = new boolean[OUTPUT_COUNT];

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> getOperationTime();
                case DATA_BATCH -> lockedBatch;
                case DATA_COST -> getEnergyPerTick();
                default -> 0;
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

    public QuantumCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_COMPRESSOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  QuantumCompressorBlockEntity compressor) {
        if (level instanceof ServerLevel serverLevel) {
            compressor.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(QuantumCompressorBalance.CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = QuantumCompressorBalance.MAX_RECEIVE;
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (isLocked() && !inputStillValid(input)) {
            // The input changed or disappeared mid-cycle: drop the partial progress instead of
            // finishing an operation the player no longer asked for.
            resetPlan();
            setStatus(input.isEmpty() ? MachineStatus.NO_INPUT : MachineStatus.NO_RECIPE);
            return;
        }

        if (!isLocked()) {
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            if (input.isEmpty()) {
                setStatus(MachineStatus.NO_INPUT);
                if (progress != 0) {
                    progress = 0;
                    setChanged();
                }
                return;
            }
            CrushingRecipe recipe = findCrushingRecipe(level, input);
            if (recipe == null) {
                setStatus(MachineStatus.NO_RECIPE);
                retryCooldown = QuantumCompressorBalance.RETRY_INTERVAL_TICKS;
                if (progress != 0) {
                    progress = 0;
                    setChanged();
                }
                return;
            }
            ItemStack resultPerItem = recipe.result().copy();
            if (resultPerItem.isEmpty()) {
                setStatus(MachineStatus.NO_RECIPE);
                retryCooldown = QuantumCompressorBalance.RETRY_INTERVAL_TICKS;
                return;
            }
            long freeSpace = freeOutputSpace(resultPerItem);
            int batch = QuantumCompressorBalance.batchSize(input.getCount(),
                    resultPerItem.getCount(), freeSpace);
            if (batch <= 0) {
                // Not even one result fits: wait without re-running the recipe lookup every tick.
                // Emptying an output slot clears the cooldown immediately.
                setStatus(MachineStatus.OUTPUT_FULL);
                retryCooldown = QuantumCompressorBalance.RETRY_INTERVAL_TICKS;
                return;
            }
            lock(level, input, resultPerItem, recipe, batch);
        }

        if (QuantumCompressorBalance.outputCount(plannedResultPerItem.getCount(), lockedBatch)
                > freeOutputSpace(plannedResultPerItem)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }

        if (progress >= plannedTime) {
            finishOperation();
            return;
        }

        long payment = QuantumCompressorBalance.paymentFor(plannedTotalCost, progress + 1,
                plannedTime);
        if (payment > 0) {
            if (energy.getEnergyStored() < payment) {
                setStatus(MachineStatus.NO_ENERGY);
                return;
            }
            energy.consume((int) Math.min(Integer.MAX_VALUE, payment));
            workTracker.recordActiveTick(level, getMachineProgressId());
        }
        progress++;
        setStatus(MachineStatus.WORKING);
        setChanged();
        if (progress >= plannedTime) {
            finishOperation();
        }
    }

    private void lock(ServerLevel level, ItemStack input, ItemStack resultPerItem,
                      CrushingRecipe recipe, int batch) {
        lockedInput = input.copyWithCount(batch);
        plannedResultPerItem = resultPerItem;
        lockedBatch = batch;
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        plannedTime = UpgradeMath.applyMasteryTime(
                UpgradeMath.acceleratedTime(QuantumCompressorBalance.CYCLE_TICKS, getMotorCount()),
                ownerLvl);
        long rawTotal = QuantumCompressorBalance.totalEnergy(recipe.timeTicks(),
                recipe.energyPerTick(), batch);
        if (rawTotal <= 0) {
            plannedTotalCost = 0;
        } else {
            int clamped = QuantumCompressorBalance.clampToInt(rawTotal);
            int battCost = UpgradeMath.discountedEnergy(clamped, getBatteryCount());
            plannedTotalCost = UpgradeMath.applyMasteryEnergy(battCost, ownerLvl);
        }
        progress = 0;
        setChanged();
    }

    /**
     * Removes the whole locked batch and places every produced item into the output slots. Both
     * conditions are re-checked here, so a pipe filling an output slot at the last tick cannot
     * destroy the result.
     */
    private void finishOperation() {
        long produced = QuantumCompressorBalance.outputCount(plannedResultPerItem.getCount(),
                lockedBatch);
        if (produced > freeOutputSpace(plannedResultPerItem) || !inputStillValid(
                inventory.getStackInSlot(INPUT_SLOT))) {
            setStatus(produced > freeOutputSpace(plannedResultPerItem)
                    ? MachineStatus.OUTPUT_FULL : MachineStatus.NO_INPUT);
            if (!inputStillValid(inventory.getStackInSlot(INPUT_SLOT))) {
                resetPlan();
            }
            return;
        }
        ItemStack extracted = inventory.extractItem(INPUT_SLOT, lockedBatch, false);
        if (extracted.getCount() < lockedBatch) {
            // Safety net: a partial extraction must never earn a full batch result.
            if (!extracted.isEmpty()) {
                inventory.insertItem(INPUT_SLOT, extracted, false);
            }
            resetPlan();
            setStatus(extracted.isEmpty() ? MachineStatus.NO_INPUT : MachineStatus.NO_RECIPE);
            setChanged();
            return;
        }
        insertAll(plannedResultPerItem, lockedBatch);
        resetPlan();
        progress = 0;
        retryCooldown = 0;
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    /** Spreads {@code batch x resultPerItem} over the output slots, filling existing stacks first. */
    private void insertAll(ItemStack resultPerItem, int batch) {
        int perItem = Math.max(1, resultPerItem.getCount());
        long remaining = QuantumCompressorBalance.outputCount(perItem, batch);
        for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_COUNT && remaining > 0; slot++) {
            int limit = Math.min(QuantumCompressorBalance.SLOT_LIMIT,
                    resultPerItem.getMaxStackSize());
            ItemStack current = inventory.getStackInSlot(slot);
            if (current.isEmpty()) {
                int placed = (int) Math.min(remaining, limit);
                inventory.setStackInSlot(slot, resultPerItem.copyWithCount(placed));
                remaining -= placed;
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(current, resultPerItem)) {
                continue;
            }
            int placed = (int) Math.min(remaining, Math.max(0, limit - current.getCount()));
            if (placed > 0) {
                current.grow(placed);
                inventory.setStackInSlot(slot, current);
                remaining -= placed;
            }
        }
    }

    /** Items of the planned result that still fit into the output slots. */
    private long freeOutputSpace(ItemStack resultPerItem) {
        for (int offset = 0; offset < OUTPUT_COUNT; offset++) {
            ItemStack stack = inventory.getStackInSlot(OUTPUT_START + offset);
            slotCounts[offset] = stack.getCount();
            sameItem[offset] = !stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, resultPerItem);
        }
        return QuantumCompressorBalance.freeSpaceFor(slotCounts, sameItem,
                Math.min(QuantumCompressorBalance.SLOT_LIMIT, resultPerItem.getMaxStackSize()));
    }

    private boolean inputStillValid(ItemStack input) {
        return !input.isEmpty()
                && ItemStack.isSameItemSameComponents(input, lockedInput)
                // A player or a pipe may shrink the stack mid-cycle; a batch smaller than the
                // locked one must abort the plan instead of paying for items that are gone.
                && input.getCount() >= lockedBatch;
    }

    private boolean isLocked() {
        return !lockedInput.isEmpty() && lockedBatch > 0 && plannedTime > 0
                && !plannedResultPerItem.isEmpty();
    }

    private void resetPlan() {
        lockedInput = ItemStack.EMPTY;
        plannedResultPerItem = ItemStack.EMPTY;
        lockedBatch = 0;
        plannedTime = 0;
        plannedTotalCost = 0;
        progress = 0;
    }

    /** The crushing recipe of the input item, or {@code null}. Looked up on cycle start only. */
    @Nullable
    private CrushingRecipe findCrushingRecipe(ServerLevel level, ItemStack input) {
        Optional<RecipeHolder<CrushingRecipe>> holder = level.getRecipeManager()
                .getRecipeFor(ModRecipes.CRUSHING_TYPE.get(), new SingleRecipeInput(input), level);
        if (holder.isEmpty()) {
            return null;
        }
        CrushingRecipe recipe = holder.get().value();
        return recipe.result().isEmpty() ? null : recipe;
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.QuantumCompressorBlock.LIT)
                    && st.getValue(com.industrialcore.block.QuantumCompressorBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.QuantumCompressorBlock.LIT, working),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
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
        return Component.translatable("block.industrialcore.quantum_compressor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory,
                                            Player player) {
        return new QuantumCompressorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
    }

    @Override
    public String getMachineProgressId() {
        return "quantum_compressor";
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    /** Capability view of one face; the compressor has no per-face settings. */
    public IItemHandler getItemHandler(@Nullable net.minecraft.core.Direction side) {
        return automationHandler;
    }

    public MachineStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    /** Batch the current cycle processes, {@code 0} while idle. */
    public int getBatch() {
        return lockedBatch;
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    /** Duration of the running cycle, or the default when idle. */
    public int getOperationTime() {
        return plannedTime > 0 ? plannedTime : QuantumCompressorBalance.CYCLE_TICKS;
    }

    /** Average FE per tick of the running cycle (display only; the cycle pays the exact sum). */
    public int getEnergyPerTick() {
        if (plannedTotalCost <= 0 || plannedTime <= 0) {
            return 0;
        }
        return QuantumCompressorBalance.averageEnergyPerTick(plannedTotalCost, plannedTime);
    }

    /** Total FE the running cycle will spend, {@code 0} while idle. */
    public long getPlannedTotalCost() {
        return plannedTotalCost;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        resetPlan();
        setChanged();
    }

    // ------------------------------------------------------------------
    // Persistence / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        workTracker.save(tag);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        if (!lockedInput.isEmpty()) {
            tag.put(TAG_LOCKED, lockedInput.save(registries));
        }
        if (!plannedResultPerItem.isEmpty()) {
            tag.put(TAG_RESULT, plannedResultPerItem.save(registries));
        }
        tag.putInt(TAG_TIME, plannedTime);
        tag.putInt(TAG_COST, QuantumCompressorBalance.clampToInt(plannedTotalCost));
        tag.putInt(TAG_BATCH, lockedBatch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level == null || !level.isClientSide) {
            workTracker.load(tag);
        }
        if (level != null && level.isClientSide) {
            // The client only receives display data; the authoritative inventory must never be
            // rebuilt from a block update.
            return;
        }
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
        if (tag.contains(TAG_INVENTORY)) {
            UpgradeInventoryUtil.loadWithMinSize(inventory, registries,
                    tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        }
        lockedInput = tag.contains(TAG_LOCKED)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_LOCKED))
                : ItemStack.EMPTY;
        plannedResultPerItem = tag.contains(TAG_RESULT)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT))
                : ItemStack.EMPTY;
        plannedTime = Math.max(0, tag.getInt(TAG_TIME));
        plannedTotalCost = Math.max(0, tag.getInt(TAG_COST));
        lockedBatch = Math.max(0, Math.min(QuantumCompressorBalance.BATCH_CAP,
                tag.getInt(TAG_BATCH)));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        if (!isLocked()) {
            resetPlan();
        } else {
            progress = Math.min(progress, plannedTime);
        }
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
        retryCooldown = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Display data only; the GUI reads the status and progress through the container data.
        return new CompoundTag();
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** Player view: only the input slot may be filled; the outputs are read-only for players. */
    public static final class CompressorItemHandler extends ItemStackHandler {
        private final QuantumCompressorBlockEntity owner;

        CompressorItemHandler(QuantumCompressorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return slot == INPUT_SLOT;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT) {
                return QuantumCompressorBalance.SLOT_LIMIT;
            }
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            // A player or a pipe changed a slot: re-check on the next tick instead of waiting out
            // the idle backoff.
            owner.retryCooldown = 0;
            owner.setChanged();
        }
    }

    /** Automation view: insert into the input, extract from any output slot. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != INPUT_SLOT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < OUTPUT_START || slot >= OUTPUT_START + OUTPUT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT;
        }
    }
}
