package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.FaceAwareItemHandler;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.ItemFilterMode;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ProcessingBalance;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.CrusherMenu;
import com.industrialcore.menu.ElectricFurnaceMenu;
import com.industrialcore.menu.ElectrolyzerMenu;
import com.industrialcore.menu.FastCrusherMenu;
import com.industrialcore.menu.MetalPressMenu;
import com.industrialcore.menu.PrecisionCrusherMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModRecipes;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared implementation of the processing machines: crusher, electric furnace, electrolyzer,
 * precision crusher (Phase 7 and Phase 12) and the fast crusher (Phase 23, batch cycles).
 *
 * <p>One block entity type serves all four blocks (like the four pipe/cable tiers); the block
 * decides whether recipes come from {@code industrialcore:crushing}, {@code electrolysis},
 * {@code precision_crushing} or the vanilla {@link RecipeType#SMELTING} type. The state machine is
 * deliberately strict:</p>
 * <ul>
 *   <li>an empty input or an item without a recipe is never consumed,</li>
 *   <li>progress only advances while the exact FE cost of that tick was paid,</li>
 *   <li>the input stack is locked when an operation starts, so swapping the input cannot finish
 *       another item's operation,
 *   <li>the fast crusher locks a whole batch at once: input count, the batch cap and the room
 *       left in the output decide it, and the batch pays recipe FE/t x size for one normal
 *       recipe cycle - identical FE per item, up to {@code FAST_CRUSH_BATCH} times the
 *       throughput,</li>
 *   <li>the item is only removed from the input when the result has been placed in the output,</li>
 *   <li>a full output freezes progress at the last tick and keeps the input,</li>
 *   <li>everything (inventory, FE, progress, locked plan) is stored in the block entity NBT.</li>
 * </ul>
 *
 * <p>Automation sees a two-slot view: pipes may only insert into the input slot and only extract
 * from the output slot, so a pipe can never pull back ingredients or push into the result slot.</p>
 */
public class ProcessingBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int MOTOR_SLOT = 2;
    public static final int BATTERY_SLOT = 3;
    public static final int SLOT_COUNT = 4;

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
    private static final String TAG_FACES = "Faces";

    private final ProcessingKind kind;
    private final ProcessingItemHandler inventory = new ProcessingItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    /** Phase 11.3-11.5: per-face mode, target slot and item filter (default: old behaviour). */
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    private final IItemHandler[] sideHandlers = new IItemHandler[6];
    /** Receive limit is applied every tick from the config, like the auto crafter. */
    private final ModEnergyStorage energy;

    private int progress;
    private int retryCooldown;
    private MachineStatus status = MachineStatus.IDLE;

    /** One input item the running operation belongs to. Empty means "not started". */
    private ItemStack lockedInput = ItemStack.EMPTY;
    private ItemStack plannedResult = ItemStack.EMPTY;
    private int plannedTime;
    @Nullable
    private Object cachedRecipeManager;
    private ItemStack cachedPlanInput = ItemStack.EMPTY;
    @Nullable
    private Plan cachedPlan;
    private int plannedCost;

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

    public ProcessingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROCESSING.get(), pos, state);
        this.kind = state.getBlock() instanceof com.industrialcore.block.ProcessingBlock block
                ? block.kind()
                : ProcessingKind.CRUSHER;
        this.energy = new ModEnergyStorage(kind.capacity(), kind.maxReceive());
        for (int side = 0; side < 6; side++) {
            sideHandlers[side] = new FaceAwareItemHandler(automationHandler, faceConfig,
                    Direction.from3DDataValue(side), null,
                    slot -> slot == INPUT_SLOT,
                    slot -> slot == OUTPUT_SLOT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingBlockEntity machine) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        boolean changed = false;
        int capacity = UpgradeMath.scaledCapacity(kind.capacity(), getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            changed = true;
        }
        // A field write per tick per machine adds up in a large factory; only apply a change.
        int maxReceive = kind.maxReceive();
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (!lockedInput.isEmpty() && !inputMatches(input)) {
            // The input changed or disappeared while the operation was running: discard the
            // partial progress instead of finishing a different item's operation.
            resetPlan();
            setStatus(input.isEmpty() ? MachineStatus.NO_INPUT : MachineStatus.NO_RECIPE);
            setChanged();
            return;
        }

        if (lockedInput.isEmpty()) {
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
            Plan plan = findPlan(level, input);
            if (plan == null) {
                setStatus(MachineStatus.NO_RECIPE);
                retryCooldown = Config.machineRetryIntervalTicks();
                if (progress != 0) {
                    progress = 0;
                    setChanged();
                }
                return;
            }
            int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
            boolean ownerLimitBroken = workTracker.isOwnerBranchLimitBroken(level, getMachineProgressId());
            int motors = getMotorCount();
            int motorTime = UpgradeMath.acceleratedTime(plan.timeTicks(), motors);
            int masteryTime = UpgradeMath.applyMasteryTime(motorTime, ownerLvl);
            int lbTime = com.industrialcore.level.MachineLevelMath.limitBrokenCycleTime(
                    masteryTime, ownerLvl, ownerLimitBroken, motors);
            plannedTime = com.industrialcore.ConfigBalanceHelper.scaleProcessingTime(
                    lbTime, Config.processingSpeedMultiplier());
            int unitTickCost = com.industrialcore.ConfigBalanceHelper.scaleEnergyConsumption(
                    UpgradeMath.applyMasteryEnergy(
                            UpgradeMath.discountedEnergy(plan.energyPerTick(), getBatteryCount()), ownerLvl),
                    Config.energyConsumptionMultiplier());
            long unitTotalCost = (long) Math.max(0, unitTickCost) * Math.max(1, plannedTime);

            int parallelCap = com.industrialcore.level.MachineLevelMath.parallelBatchSize(ownerLvl, ownerLimitBroken);
            int batchSize = 1;
            if (kind == ProcessingKind.FAST_CRUSHER) {
                // Combine Fast Crusher's native batch cap with Phase 99 Limit-Break hyper-parallel cap.
                int fastCap = Math.max(1, fastBatchSize(input, plan.result()));
                int effectiveCap = Math.max(fastCap, parallelCap);
                ItemStack existingOut = inventory.getStackInSlot(OUTPUT_SLOT);
                if (!existingOut.isEmpty() && !ItemStack.isSameItemSameComponents(existingOut, plan.result())) {
                    setStatus(MachineStatus.OUTPUT_FULL);
                    retryCooldown = Config.machineRetryIntervalTicks();
                    return;
                }
                int outMax = Math.min(inventory.getSlotLimit(OUTPUT_SLOT), plan.result().getMaxStackSize());
                com.industrialcore.level.ParallelExecutionLogic.BatchPlan bp =
                        com.industrialcore.level.ParallelExecutionLogic.computeBatch(
                                effectiveCap, input.getCount(), 1, plan.result().getCount(),
                                existingOut.getCount(), outMax, unitTotalCost, energy.getEnergyStored());
                if (bp.outputFull() || bp.actualBatch() <= 0) {
                    setStatus(MachineStatus.OUTPUT_FULL);
                    retryCooldown = Config.machineRetryIntervalTicks();
                    return;
                }
                batchSize = bp.actualBatch();
            } else if (parallelCap > 1) {
                ItemStack existingOut = inventory.getStackInSlot(OUTPUT_SLOT);
                if (!existingOut.isEmpty() && !ItemStack.isSameItemSameComponents(existingOut, plan.result())) {
                    setStatus(MachineStatus.OUTPUT_FULL);
                    retryCooldown = Config.machineRetryIntervalTicks();
                    return;
                }
                int outMax = Math.min(inventory.getSlotLimit(OUTPUT_SLOT), plan.result().getMaxStackSize());
                com.industrialcore.level.ParallelExecutionLogic.BatchPlan bp =
                        com.industrialcore.level.ParallelExecutionLogic.computeBatch(
                                parallelCap, input.getCount(), 1, plan.result().getCount(),
                                existingOut.getCount(), outMax, unitTotalCost, energy.getEnergyStored());
                if (bp.outputFull() || bp.actualBatch() <= 0) {
                    setStatus(MachineStatus.OUTPUT_FULL);
                    retryCooldown = Config.machineRetryIntervalTicks();
                    return;
                }
                batchSize = bp.actualBatch();
            }

            lockedInput = input.copyWithCount(batchSize);
            plannedResult = plan.result().copyWithCount(
                    scaleCount(plan.result().getCount(), batchSize));
            int baseCost = scaleCount(plan.energyPerTick(), batchSize);
            int battCost = UpgradeMath.discountedEnergy(baseCost, getBatteryCount());
            plannedCost = com.industrialcore.ConfigBalanceHelper.scaleEnergyConsumption(
                    UpgradeMath.applyMasteryEnergy(battCost, ownerLvl),
                    Config.energyConsumptionMultiplier());
            progress = 0;
            setChanged();
        }

        if (!canAccept(inventory.getStackInSlot(OUTPUT_SLOT), plannedResult)) {
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
        level.blockEntityChanged(worldPosition);
        if (progress >= plannedTime) {
            finishOperation();
        }
    }

    /**
     * Consumes exactly one locked input item and inserts the planned result. Both conditions are
     * re-checked here, so a pipe filling the output at the last tick cannot destroy the result.
     */
    private void finishOperation() {
        if (!canAccept(inventory.getStackInSlot(OUTPUT_SLOT), plannedResult)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        int amount = lockedInput.getCount();
        ItemStack extracted = inventory.extractItem(INPUT_SLOT, amount, false);
        if (extracted.getCount() < amount) {
            // The tick guard keeps the input at least as large as the locked batch, so this is a
            // safety net: a partial extraction must never earn a full batch result. The items go
            // straight back into the input slot.
            if (!extracted.isEmpty()) {
                inventory.insertItem(INPUT_SLOT, extracted, false);
            }
            resetPlan();
            setStatus(extracted.isEmpty() ? MachineStatus.NO_INPUT : MachineStatus.NO_RECIPE);
            setChanged();
            return;
        }
        insertResult(inventory, OUTPUT_SLOT, plannedResult);
        // Phase 113: Specialization Module bonus yield chance (+1 extra output item)
        double bonusChance = UpgradeMath.processingBonusYieldChance(getSpecialCount());
        if (bonusChance > 0.0D && level != null && level.random.nextDouble() < bonusChance) {
            ItemStack bonus = plannedResult.copyWithCount(1);
            if (canAccept(inventory.getStackInSlot(OUTPUT_SLOT), bonus)) {
                insertResult(inventory, OUTPUT_SLOT, bonus);
            }
        }
        resetPlan();
        progress = 0;
        retryCooldown = 0;
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    private boolean inputMatches(ItemStack input) {
        return !input.isEmpty()
                && ItemStack.isSameItemSameComponents(input, lockedInput)
                // A player may shrink the stack mid-cycle; a batch smaller than the locked one
                // must abort the plan instead of paying for items that no longer exist.
                && input.getCount() >= lockedInput.getCount();
    }

    private void resetPlan() {
        lockedInput = ItemStack.EMPTY;
        plannedResult = ItemStack.EMPTY;
        plannedTime = 0;
        plannedCost = 0;
        progress = 0;
    }

    /** Recipe lookup for the current kind. Returns {@code null} when nothing matches. */
    @Nullable
    private Plan findPlan(ServerLevel level, ItemStack input) {
        Object rm = level.getRecipeManager();
        if (rm == cachedRecipeManager && !cachedPlanInput.isEmpty()
                && ItemStack.isSameItemSameComponents(input, cachedPlanInput)) {
            return cachedPlan == null ? null : new Plan(cachedPlan.result().copy(),
                    cachedPlan.timeTicks(), cachedPlan.energyPerTick());
        }
        Plan resolved = resolvePlanUncached(level, input);
        cachedRecipeManager = rm;
        cachedPlanInput = input.copyWithCount(1);
        cachedPlan = resolved == null ? null : new Plan(resolved.result().copy(),
                resolved.timeTicks(), resolved.energyPerTick());
        return resolved;
    }

    @Nullable
    private Plan resolvePlanUncached(ServerLevel level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        ProcessorRecipe.Family family = kind.processorFamily();
        if (family != null) {
            // Each machine owns a recipe type, so a raw ore can never be precision crushed, an ore
            // block can never be electrolysed and an ingot can never be electrolysed by accident.
            Optional<RecipeHolder<ProcessorRecipe>> holder = level.getRecipeManager()
                    .getRecipeFor(family.type().get(), recipeInput, level);
            if (holder.isEmpty()) {
                return null;
            }
            ProcessorRecipe recipe = holder.get().value();
            ItemStack result = recipe.result().copy();
            if (result.isEmpty()) {
                return null;
            }
            return new Plan(result, Math.max(1, recipe.timeTicks()),
                    Math.max(0, recipe.energyPerTick()));
        }
        if (kind == ProcessingKind.CRUSHER || kind == ProcessingKind.FAST_CRUSHER) {
            Optional<RecipeHolder<CrushingRecipe>> holder = level.getRecipeManager()
                    .getRecipeFor(ModRecipes.CRUSHING_TYPE.get(), recipeInput, level);
            if (holder.isEmpty()) {
                return null;
            }
            CrushingRecipe recipe = holder.get().value();
            ItemStack result = recipe.result().copy();
            if (result.isEmpty()) {
                return null;
            }
            return new Plan(result, Math.max(1, recipe.timeTicks()), Math.max(0, recipe.energyPerTick()));
        }
        Optional<RecipeHolder<SmeltingRecipe>> holder = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, recipeInput, level);
        if (holder.isEmpty()) {
            return null;
        }
        SmeltingRecipe recipe = holder.get().value();
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (result.isEmpty()) {
            return null;
        }
        int time = ProcessingBalance.furnaceTime(recipe.getCookingTime(), kind.furnaceSpeedPercent());
        return new Plan(result, time, kind.defaultEnergyPerTick());
    }

    /**
     * Items of the current input that one fast crusher cycle may process: capped by the batch
     * size, by the input count and by the room left in the output slot. Zero means the machine
     * has to wait for space.
     */
    private int fastBatchSize(ItemStack input, ItemStack resultPerItem) {
        int max = Math.min(input.getCount(), ProcessingBalance.FAST_CRUSH_BATCH);
        int perItem = Math.max(1, resultPerItem.getCount());
        int capacity = Math.min(64, resultPerItem.getMaxStackSize());
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return Math.min(max, capacity / perItem);
        }
        if (!ItemStack.isSameItemSameComponents(output, resultPerItem)) {
            return 0;
        }
        int free = Math.max(0, capacity - output.getCount());
        return Math.min(max, free / perItem);
    }

    /** Multiplies through a long so a large datapack value can never wrap into a small number. */
    private static int scaleCount(int value, int factor) {
        long scaled = (long) Math.max(0, value) * Math.max(0, factor);
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    /** True when the result fits into the output slot (empty or the same item with room). */
    public static boolean canAccept(ItemStack output, ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        if (output.isEmpty()) {
            return result.getCount() <= Math.min(64, result.getMaxStackSize());
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= Math.min(64, output.getMaxStackSize());
    }

    /** Inserts a previously checked result. Callers must call {@link #canAccept} first. */
    public static void insertResult(ItemStackHandler inventory, int slot, ItemStack result) {
        ItemStack output = inventory.getStackInSlot(slot);
        if (output.isEmpty()) {
            inventory.setStackInSlot(slot, result.copy());
            return;
        }
        output.grow(result.getCount());
        inventory.setStackInSlot(slot, output);
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.ProcessingBlock.LIT)
                    && st.getValue(com.industrialcore.block.ProcessingBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.ProcessingBlock.LIT, working),
                        Block.UPDATE_CLIENTS);
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
        return Component.translatable(kind.translationKey());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return switch (kind) {
            case CRUSHER -> new CrusherMenu(containerId, playerInventory, this, dataAccess);
            case ELECTRIC_FURNACE -> new ElectricFurnaceMenu(containerId, playerInventory, this, dataAccess);
            case ELECTROLYZER -> new ElectrolyzerMenu(containerId, playerInventory, this, dataAccess);
            case PRECISION_CRUSHER -> new PrecisionCrusherMenu(containerId, playerInventory, this,
                    dataAccess);
            case METAL_PRESS -> new MetalPressMenu(containerId, playerInventory, this, dataAccess);
            case FAST_CRUSHER -> new FastCrusherMenu(containerId, playerInventory, this, dataAccess);
        };
    }

    public ProcessingKind getKind() {
        return kind;
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
        return kind.id();
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    /** Capability view of one face (Phase 11.3); null side keeps the old unrestricted view. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return automationHandler;
        }
        return sideHandlers[side.get3DDataValue()];
    }

    public MachineFaceConfig getFaceConfig() {
        return faceConfig;
    }

    /**
     * Applies one face action from the GUI. Server validated; the caller (network handler) has
     * already checked the player's distance and that the block still exists.
     */
    public boolean applyFaceAction(int side, int action, @Nullable ItemStack filterStack) {
        if (level == null || level.isClientSide || side < 0 || side >= 6) {
            return false;
        }
        switch (action) {
            case MachineActions.FACE_MODE -> faceConfig.cycleMode(side);
            case MachineActions.FACE_SLOT -> faceConfig.cycleSlot(side, new int[] {INPUT_SLOT, OUTPUT_SLOT});
            case MachineActions.FACE_FILTER -> faceConfig.cycleFilter(side);
            case MachineActions.FACE_ADD_ITEM -> {
                if (filterStack == null || filterStack.isEmpty()) {
                    return false;
                }
                faceConfig.toggleAllowed(side,
                        BuiltInRegistries.ITEM.getKey(filterStack.getItem()).toString());
            }
            case MachineActions.FACE_CLEAR -> faceConfig.setAllowed(side, java.util.List.of());
            default -> {
                return false;
            }
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
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

    public int getProgress() {
        return progress;
    }

    /** Duration of the running operation, or the configured default when idle. */
    public int getOperationTime() {
        if (plannedTime > 0) {
            return plannedTime;
        }
        return UpgradeMath.acceleratedTime(kind.defaultTimeTicks(), getMotorCount());
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        int legacy = stack.is(ModItems.MOTOR.get()) ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
        return upgradeBank.motors() + legacy;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        int legacy = stack.is(ModItems.BATTERY.get()) ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
        return upgradeBank.batteries() + legacy;
    }

    public int getSpecialCount() {
        return upgradeBank.specials();
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank getUpgradeBank() {
        return upgradeBank;
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory getSpecialCategory() {
        return com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory.PROCESSING;
    }

    @Override
    public String getUpgradeMachineId() {
        return getMachineProgressId();
    }

    @Override
    public int getLegacyUpgradeCount() {
        ItemStack ms = inventory.getStackInSlot(MOTOR_SLOT);
        ItemStack bs = inventory.getStackInSlot(BATTERY_SLOT);
        int lm = ms.is(ModItems.MOTOR.get()) ? ms.getCount() : 0;
        int lb = bs.is(ModItems.BATTERY.get()) ? bs.getCount() : 0;
        return lm + lb;
    }

    @Override
    public int getTotalUpgradeLimit() {
        return (level instanceof ServerLevel sl)
                ? workTracker.totalUpgradeLimit(sl, getMachineProgressId())
                : com.industrialcore.level.MachineLevelMath.totalUpgradeLimitForLevel(1);
    }

    @Override
    public void onUpgradeBankChanged() {
        energy.setCapacity(UpgradeMath.scaledCapacity(kind.capacity(), getBatteryCount()));
        setChanged();
    }

    public int getEnergyPerTick() {
        return plannedCost;
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
        upgradeBank.save(tag);
        workTracker.save(tag);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        if (!lockedInput.isEmpty()) {
            tag.put(TAG_LOCKED, lockedInput.save(registries));
        }
        if (!plannedResult.isEmpty()) {
            tag.put(TAG_RESULT, plannedResult.save(registries));
        }
        tag.putInt(TAG_TIME, plannedTime);
        tag.putInt(TAG_COST, plannedCost);
        tag.put(TAG_FACES, saveFaces());
    }

    private CompoundTag saveFaces() {
        CompoundTag faces = new CompoundTag();
        for (int side = 0; side < 6; side++) {
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
        for (int side = 0; side < 6; side++) {
            if (!faces.contains(Integer.toString(side))) {
                continue;
            }
            CompoundTag face = faces.getCompound(Integer.toString(side));
            faceConfig.setMode(side, FaceMode.byIndex(face.getByte("Mode") & 0xFF));
            faceConfig.setSlot(side, face.getInt("Slot"));
            faceConfig.setFilterMode(side, ItemFilterMode.byIndex(face.getByte("Filter") & 0xFF));
            java.util.List<String> ids = new java.util.ArrayList<>();
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
        if (level == null || !level.isClientSide) {
            upgradeBank.load(tag);
            workTracker.load(tag);
        }
        if (tag.contains(TAG_FACES)) {
            loadFaces(tag.getCompound(TAG_FACES));
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
            UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        }
        lockedInput = tag.contains(TAG_LOCKED)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_LOCKED))
                : ItemStack.EMPTY;
        plannedResult = tag.contains(TAG_RESULT)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_RESULT))
                : ItemStack.EMPTY;
        plannedTime = Math.max(0, tag.getInt(TAG_TIME));
        plannedCost = Math.max(0, tag.getInt(TAG_COST));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        if (lockedInput.isEmpty() || plannedResult.isEmpty() || plannedTime <= 0) {
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
        // Display data only: the GUI needs the face settings; the inventory must never travel
        // through a block update.
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_FACES, saveFaces());
        return tag;
    }

    private void onInventoryChanged() {
        retryCooldown = 0;
        setChanged();
    }

    /** Player/menu view: the output slot cannot be filled manually. */
    public static final class ProcessingItemHandler extends ItemStackHandler {
        private final ProcessingBlockEntity owner;

        ProcessingItemHandler(ProcessingBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) {
                return true;
            }
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.onInventoryChanged();
        }
    }

    /**
     * Automation view: insert into the input slot only, extract from the output slot only. This is
     * what pipes and hoppers see, so ingredients can never be pulled back out and the result slot
     * can never be filled from the outside.
     */
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
            return slot == INPUT_SLOT;
        }
    }

    /** Immutable "this operation is possible" description. */
    private record Plan(ItemStack result, int timeTicks, int energyPerTick) {}
}
