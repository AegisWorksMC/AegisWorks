package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.OreSynthesizerMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
 * Block entity of the synthetic ore synthesizer.
 *
 * <p>Three ingredient slots and one output. Unlike the phase reactor, a recipe entry carries a
 * count: the line consumes four adamantite or two synthetic ore per craft. The machine therefore
 * matches whole stacks and consumes the listed amount from each matched slot, which is why the
 * recipe type is its own ({@code industrialcore:synthesis}) instead of reusing the phase reaction
 * (that would also make every synthesis recipe runnable in the existing reactor).</p>
 */
public class OreSynthesizerBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int INPUT_START = 0;
    public static final int INPUT_COUNT = SynthesisBalance.INPUT_SLOTS;
    public static final int OUTPUT_SLOT = INPUT_COUNT;
    public static final int MOTOR_SLOT = OUTPUT_SLOT + 1;
    public static final int BATTERY_SLOT = MOTOR_SLOT + 1;
    public static final int SLOT_COUNT = BATTERY_SLOT + 1;

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
    private static final String TAG_RESULT = "Result";
    private static final String TAG_TIME = "Time";
    private static final String TAG_COST = "Cost";

    private final ModEnergyStorage energy =
            new ModEnergyStorage(SynthesisBalance.CAPACITY, SynthesisBalance.MAX_RECEIVE);

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return slot < INPUT_COUNT && !stack.isEmpty();
        }

        @Override
        protected void onContentsChanged(int slot) {
            OreSynthesizerBlockEntity.this.setChanged();
        }
    };

    private int progress;
    private int retryCooldown;
    private MachineStatus status = MachineStatus.NO_INPUT;
    private ItemStack plannedResult = ItemStack.EMPTY;
    private int plannedTime;
    private int plannedCost;

    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<SynthesisRecipe> cachedRecipes = List.of();

    private final List<ItemStack> scratch = new ArrayList<>(INPUT_COUNT);

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
            // Server authoritative.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public OreSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_SYNTHESIZER.get(), pos, state);
        this.energy.setMaxReceive(Math.max(1, Config.synthesizerMaxReceive()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  OreSynthesizerBlockEntity machine) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(SynthesisBalance.CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Math.max(1, Config.synthesizerMaxReceive());
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }

        fillScratch();
        boolean anyInput = false;
        for (ItemStack stack : scratch) {
            anyInput |= !stack.isEmpty();
        }
        if (!anyInput) {
            resetPlan();
            setStatus(MachineStatus.NO_INPUT);
            return;
        }

        SynthesisRecipe recipe = findRecipe(level);
        if (recipe == null) {
            resetPlan();
            setStatus(MachineStatus.NO_RECIPE);
            retryCooldown = SynthesisBalance.RETRY_INTERVAL_TICKS;
            return;
        }

        if (!isPlanned(recipe)) {
            plan(level, recipe);
        }

        if (!canInsertResult(plannedResult)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }

        if (progress >= plannedTime) {
            finishOperation(recipe);
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
            finishOperation(recipe);
        }
    }

    private void finishOperation(SynthesisRecipe recipe) {
        fillScratch();
        int[] mapping = recipe.matchSlots(scratch);
        if (mapping == null || !canInsertResult(plannedResult)) {
            // Inputs changed under the run: discard the partial progress, never the items.
            resetPlan();
            setStatus(MachineStatus.NO_RECIPE);
            return;
        }
        for (int index = 0; index < mapping.length; index++) {
            int slot = mapping[index];
            int count = recipe.ingredients().get(index).count();
            inventory.extractItem(slot, count, false);
        }
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, plannedResult.copy());
        } else {
            output.grow(plannedResult.getCount());
        }
        resetPlan();
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    private boolean canInsertResult(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void fillScratch() {
        scratch.clear();
        for (int index = 0; index < INPUT_COUNT; index++) {
            scratch.add(inventory.getStackInSlot(index));
        }
    }

    @Nullable
    private SynthesisRecipe findRecipe(ServerLevel level) {
        RecipeManager manager = level.getRecipeManager();
        if (manager != this.cachedRecipeManager) {
            this.cachedRecipeManager = manager;
            this.cachedRecipes = manager.getAllRecipesFor(ModRecipes.SYNTHESIS_TYPE.get())
                    .stream().map(RecipeHolder::value).toList();
        }
        return SynthesisRecipe.find(this.cachedRecipes, scratch);
    }

    private boolean isPlanned(SynthesisRecipe recipe) {
        return !plannedResult.isEmpty()
                && plannedTime > 0
                && ItemStack.isSameItemSameComponents(plannedResult, recipe.result());
    }

    private void plan(ServerLevel level, SynthesisRecipe recipe) {
        plannedResult = recipe.result().copy();
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        plannedTime = UpgradeMath.applyMasteryTime(
                UpgradeMath.acceleratedTime(Math.max(1, recipe.timeTicks()), getMotorCount()),
                ownerLvl);
        int rawCost = Math.max(0, recipe.energyPerTick());
        plannedCost = rawCost <= 0 ? 0
                : UpgradeMath.applyMasteryEnergy(
                        UpgradeMath.discountedEnergy(rawCost, getBatteryCount()), ownerLvl);
        progress = 0;
        setChanged();
    }

    private void resetPlan() {
        if (progress != 0 || !plannedResult.isEmpty()) {
            progress = 0;
            plannedResult = ItemStack.EMPTY;
            plannedTime = 0;
            plannedCost = 0;
            setChanged();
        }
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.OreSynthesizerBlock.LIT)
                    && st.getValue(com.industrialcore.block.OreSynthesizerBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.OreSynthesizerBlock.LIT, working),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        if (this.status != next) {
            this.status = next;
            setChanged();
        }
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
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

    private int getOperationTime() {
        return plannedTime > 0 ? plannedTime : SynthesisBalance.TIME_TICKS;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.ore_synthesizer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new OreSynthesizerMenu(containerId, playerInventory, this, dataAccess);
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
        return "ore_synthesizer";
    }

    public ModEnergyStorage getEnergyStorage() {
        return this.energy;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    /** Automation: insert into any ingredient slot, extract from the output slot only. */
    public IItemHandler getItemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return SLOT_COUNT;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return inventory.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot >= INPUT_COUNT) {
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
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot >= 0 && slot < INPUT_COUNT && !stack.isEmpty();
            }
        };
    }

    public ContainerData getContainerData() {
        return this.dataAccess;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public int getProgressForTesting() {
        return progress;
    }

    public MachineStatus getStatusForTesting() {
        return status;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Stores the planned result as id and count; the machine only makes plain items. */
    private static void putStack(CompoundTag tag, String key, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        tag.putString(key + "Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        tag.putInt(key + "Count", stack.getCount());
    }

    private static ItemStack readStack(CompoundTag tag, String key) {
        if (!tag.contains(key + "Id")) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key + "Id"));
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, tag.getInt(key + "Count")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        workTracker.save(tag);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.putInt(TAG_TIME, plannedTime);
        tag.putInt(TAG_COST, plannedCost);
        putStack(tag, TAG_RESULT, plannedResult);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level == null || !level.isClientSide) {
            workTracker.load(tag);
        }
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        if (tag.contains(TAG_INVENTORY)) {
            UpgradeInventoryUtil.loadWithMinSize(inventory, registries,
                    tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        }
        plannedTime = Math.max(0, tag.getInt(TAG_TIME));
        plannedCost = Math.max(0, tag.getInt(TAG_COST));
        plannedResult = readStack(tag, TAG_RESULT);
        if (plannedResult.isEmpty()) {
            resetPlan();
        } else {
            progress = Math.min(progress, plannedTime);
        }
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
    }
}
