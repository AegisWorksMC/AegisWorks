package com.industrialcore.blockentity;

import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PhaseReactionRecipe;
import com.industrialcore.machine.PhaseReactorBalance;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.PhaseReactorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity of the Phase 24 phase reactor.
 *
 * <p>Three interchangeable ingredient slots and one output; every shipped recipe is exactly three
 * items in and one item out, so the reactor is the first multi-input machine that a pipe can feed
 * without any filter or face configuration.</p>
 *
 * <p>State machine (identical to the other multi-input machine, the assembler):</p>
 * <ul>
 *   <li>the plan is locked when a run starts; changing any ingredient discards the partial
 *       progress instead of finishing a different product,</li>
 *   <li>the three ingredients are only removed when the result has been placed,</li>
 *   <li>a full output freezes the machine before any FE is spent,</li>
 *   <li>missing FE keeps the progress and the ingredients,</li>
 *   <li>inventory, FE, progress and the locked plan are stored in the NBT, so a reload continues
 *       the run exactly where it stopped.</li>
 * </ul>
 *
 * <p>Performance: the recipe list is cached per {@link RecipeManager} instance (a datapack reload
 * replaces the manager, which invalidates the cache), an idle machine re-checks its recipe only
 * every {@link PhaseReactorBalance#RETRY_INTERVAL_TICKS} ticks, and a running machine never
 * searches at all - it only compares its three locked inputs, which is three reference comparisons
 * per tick.</p>
 */
public class PhaseReactorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int INPUT_START = 0;
    public static final int INPUT_COUNT = PhaseReactorBalance.INPUT_SLOTS;
    public static final int OUTPUT_SLOT = 3;
    public static final int MOTOR_SLOT = 4;
    public static final int BATTERY_SLOT = 5;
    public static final int SLOT_COUNT = 6;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TIME = 4;
    public static final int DATA_COUNT = 5;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TIME = "Time";
    private static final String TAG_COST = "Cost";

    private final ReactorItemHandler inventory = new ReactorItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy =
            new ModEnergyStorage(PhaseReactorBalance.CAPACITY, PhaseReactorBalance.MAX_RECEIVE);

    private int progress;
    private int retryCooldown;
    private MachineStatus status = MachineStatus.NO_INPUT;

    /** One item per ingredient slot that the running operation belongs to. */
    private final ItemStack[] lockedInputs = new ItemStack[] {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private ItemStack plannedResult = ItemStack.EMPTY;
    private int plannedTime;
    private int plannedCost;

    /** Reused ingredient list; avoids one list allocation per tick per machine. */
    private final List<ItemStack> scratch = new ArrayList<>(INPUT_COUNT);

    /** Cached recipe list; rebuilt when the recipe manager instance changes (datapack reload). */
    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<PhaseReactionRecipe> cachedRecipes = List.of();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> getOperationTime();
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

    public PhaseReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHASE_REACTOR.get(), pos, state);
        for (int index = 0; index < INPUT_COUNT; index++) {
            scratch.add(ItemStack.EMPTY);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  PhaseReactorBlockEntity reactor) {
        if (level instanceof ServerLevel serverLevel) {
            reactor.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(PhaseReactorBalance.CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = PhaseReactorBalance.MAX_RECEIVE;
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        for (int index = 0; index < INPUT_COUNT; index++) {
            scratch.set(index, inventory.getStackInSlot(INPUT_START + index));
        }

        if (!lockedInputsMatch()) {
            // An ingredient changed or disappeared while the operation was running: discard the
            // partial progress instead of finishing a different product.
            resetPlan();
            setStatus(anyInputEmpty() ? MachineStatus.NO_INPUT : MachineStatus.NO_RECIPE);
            return;
        }

        if (!isLocked()) {
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            if (anyInputEmpty()) {
                setStatus(MachineStatus.NO_INPUT);
                if (progress != 0) {
                    progress = 0;
                    setChanged();
                }
                return;
            }
            PhaseReactionRecipe recipe = PhaseReactionRecipe.find(allRecipes(), scratch);
            if (recipe == null) {
                setStatus(MachineStatus.NO_RECIPE);
                retryCooldown = PhaseReactorBalance.RETRY_INTERVAL_TICKS;
                if (progress != 0) {
                    progress = 0;
                    setChanged();
                }
                return;
            }
            lock(level, recipe);
        }

        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT),
                plannedResult)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }

        if (progress >= plannedTime) {
            finishOperation();
            return;
        }

        if (plannedCost > 0) {
            if (energy.getEnergyStored() < plannedCost) {
                setStatus(MachineStatus.NO_ENERGY);
                return;
            }
            energy.consume(plannedCost);
            workTracker.recordActiveTick(level, getMachineProgressId());
        }
        progress++;
        setStatus(MachineStatus.WORKING);
        setChanged();
        if (progress >= plannedTime) {
            finishOperation();
        }
    }

    /** Consumes one item from every ingredient slot and inserts the result. */
    private void finishOperation() {
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT),
                plannedResult)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        if (!lockedInputsMatch()) {
            resetPlan();
            setStatus(MachineStatus.NO_INPUT);
            setChanged();
            return;
        }
        for (int index = 0; index < INPUT_COUNT; index++) {
            inventory.extractItem(INPUT_START + index, 1, false);
        }
        ProcessingBlockEntity.insertResult(inventory, OUTPUT_SLOT, plannedResult);
        resetPlan();
        progress = 0;
        retryCooldown = 0;
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    private void lock(ServerLevel level, PhaseReactionRecipe recipe) {
        for (int index = 0; index < INPUT_COUNT; index++) {
            ItemStack stack = inventory.getStackInSlot(INPUT_START + index);
            lockedInputs[index] = stack.copyWithCount(1);
        }
        plannedResult = recipe.result().copy();
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        plannedTime = UpgradeMath.applyMasteryTime(
                UpgradeMath.acceleratedTime(recipe.timeTicks(), getMotorCount()), ownerLvl);
        plannedCost = UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(recipe.energyPerTick(), getBatteryCount()), ownerLvl);
        progress = 0;
        setChanged();
    }

    private boolean isLocked() {
        return !plannedResult.isEmpty() && plannedTime > 0;
    }

    private boolean lockedInputsMatch() {
        if (!isLocked()) {
            return true;
        }
        for (int index = 0; index < INPUT_COUNT; index++) {
            ItemStack current = inventory.getStackInSlot(INPUT_START + index);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current,
                    lockedInputs[index])) {
                return false;
            }
        }
        return true;
    }

    /** True while at least one ingredient slot is still empty (the machine is waiting for input). */
    private boolean anyInputEmpty() {
        for (int index = 0; index < INPUT_COUNT; index++) {
            if (inventory.getStackInSlot(INPUT_START + index).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void resetPlan() {
        for (int index = 0; index < INPUT_COUNT; index++) {
            lockedInputs[index] = ItemStack.EMPTY;
        }
        plannedResult = ItemStack.EMPTY;
        plannedTime = 0;
        plannedCost = 0;
        progress = 0;
    }

    /** All phase reaction recipes of the current recipe manager, cached per manager instance. */
    private List<PhaseReactionRecipe> allRecipes() {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (cachedRecipeManager != manager) {
            cachedRecipeManager = manager;
            List<PhaseReactionRecipe> found = new ArrayList<>();
            for (var holder : manager.getAllRecipesFor(ModRecipes.PHASE_REACTION_TYPE.get())) {
                found.add(holder.value());
            }
            cachedRecipes = List.copyOf(found);
        }
        return cachedRecipes;
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.PhaseReactorBlock.LIT)
                    && st.getValue(com.industrialcore.block.PhaseReactorBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.PhaseReactorBlock.LIT, working),
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
        return Component.translatable("block.industrialcore.phase_reactor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PhaseReactorMenu(containerId, playerInventory, this, dataAccess);
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
        return "phase_reactor";
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

    /** Capability view of one face; the reactor has no per-face settings, so every face is equal. */
    public IItemHandler getItemHandler(@Nullable net.minecraft.core.Direction side) {
        return automationHandler;
    }

    public MachineStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    /** Duration of the running operation, or the default when idle. */
    public int getOperationTime() {
        return plannedTime > 0 ? plannedTime : PhaseReactorBalance.CATALYST_TIME_TICKS;
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
        ListTag locked = new ListTag();
        for (ItemStack stack : lockedInputs) {
            locked.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        }
        tag.put(TAG_LOCKED, locked);
        if (!plannedResult.isEmpty()) {
            tag.put(TAG_RESULT, plannedResult.save(registries));
        }
        tag.putInt(TAG_TIME, plannedTime);
        tag.putInt(TAG_COST, plannedCost);
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
        ListTag locked = tag.getList(TAG_LOCKED, 10);
        for (int index = 0; index < INPUT_COUNT; index++) {
            lockedInputs[index] = index < locked.size()
                    ? ItemStack.parseOptional(registries, locked.getCompound(index))
                    : ItemStack.EMPTY;
        }
        plannedResult = tag.contains(TAG_RESULT)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT))
                : ItemStack.EMPTY;
        plannedTime = Math.max(0, tag.getInt(TAG_TIME));
        plannedCost = Math.max(0, tag.getInt(TAG_COST));
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
        // Display data only; the GUI reads the status through the container data.
        return new CompoundTag();
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** Player view of the four slots. The output slot can never be filled from the outside. */
    public static final class ReactorItemHandler extends ItemStackHandler {
        private final PhaseReactorBlockEntity owner;

        ReactorItemHandler(PhaseReactorBlockEntity owner) {
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
            return slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT
                    && owner.acceptsIngredient(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            // A player or a pipe changed a slot: re-check the recipe on the next tick instead of
            // waiting out the idle backoff.
            owner.retryCooldown = 0;
            owner.setChanged();
        }
    }

    /** True when the item appears as an ingredient of some phase reaction recipe. */
    private boolean acceptsIngredient(ItemStack stack) {
        List<PhaseReactionRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            // Nothing known yet (datapack loading): never block the player; the tick re-checks.
            return true;
        }
        return PhaseReactionRecipe.anyRecipeAccepts(recipes, stack);
    }

    /** Automation view: insert into the ingredient slots, extract from the output only. */
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
            if (slot < INPUT_START || slot >= INPUT_START + INPUT_COUNT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT) {
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
            return slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT;
        }
    }
}
