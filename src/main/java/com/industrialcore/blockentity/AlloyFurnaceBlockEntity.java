package com.industrialcore.blockentity;

import com.industrialcore.machine.AlloyBalance;
import com.industrialcore.machine.AlloyRecipe;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.AlloyFurnaceMenu;
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
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
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

import java.util.List;

/**
 * Block entity of the Phase 17 alloy furnace.
 *
 * <p>The furnace has four slots: two ingredients, one fuel slot and the output. Unlike every other
 * machine of the mod it uses <b>no FE at all</b>: it burns ordinary furnace fuel for one tick per
 * tick of work. That makes the parts line reachable before the first generator, and it gives the
 * machine a different "feel" than the electric processors (a design goal of this phase).</p>
 *
 * <p>The state machine is the same strict one as the other machines:</p>
 * <ul>
 *   <li>the two ingredients are only removed when the result has been placed,</li>
 *   <li>a full output stops the machine before any fuel is burnt,</li>
 *   <li>no fuel means {@link MachineStatus#NO_FUEL} and no progress,</li>
 *   <li>changing an ingredient mid-run discards the partial progress instead of finishing a
 *       different alloy,</li>
 *   <li>fuel, inventory, progress and burn state are stored in the NBT.</li>
 * </ul>
 *
 * <p>Automation may insert into either ingredient slot (each slot only accepts items that appear
 * as that ingredient in a recipe) and may only extract from the output slot.</p>
 */
public class AlloyFurnaceBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int FIRST_SLOT = 0;
    public static final int SECOND_SLOT = 1;
    public static final int FUEL_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int MOTOR_SLOT = 4;
    public static final int BATTERY_SLOT = 5;
    public static final int SLOT_COUNT = 6;

    public static final int DATA_STATUS = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_TIME = 2;
    public static final int DATA_BURN = 3;
    public static final int DATA_BURN_TOTAL = 4;
    public static final int DATA_COUNT = 5;

    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_BURN = "Burn";
    private static final String TAG_BURN_TOTAL = "BurnTotal";
    private static final String TAG_INVENTORY = "Inventory";

    private final AlloyItemHandler inventory = new AlloyItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();

    private int progress;
    private int burnTime;
    private int burnTimeTotal;
    private MachineStatus status = MachineStatus.NO_INPUT;

    /** Cached recipe list; rebuilt when the recipe manager instance changes (datapack reload). */
    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<AlloyRecipe> cachedRecipes = List.of();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> getOperationTime();
                case DATA_BURN -> burnTime;
                case DATA_BURN_TOTAL -> burnTimeTotal;
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

    public AlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALLOY_FURNACE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AlloyFurnaceBlockEntity furnace) {
        if (level instanceof ServerLevel serverLevel) {
            furnace.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        ItemStack first = inventory.getStackInSlot(FIRST_SLOT);
        ItemStack second = inventory.getStackInSlot(SECOND_SLOT);
        AlloyRecipe recipe = AlloyRecipe.find(allRecipes(), first, second);
        if (recipe == null) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            setStatus(first.isEmpty() || second.isEmpty() ? MachineStatus.NO_INPUT
                    : MachineStatus.NO_RECIPE);
            return;
        }

        // A full output must not burn fuel and must not eat the ingredients.
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT),
                recipe.result())) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }

        int requiredTime = effectiveTime(recipe);
        if (progress >= requiredTime) {
            finish(recipe);
            return;
        }

        if (burnTime <= 0 && !consumeFuel(level)) {
            setStatus(MachineStatus.NO_FUEL);
            return;
        }

        burnTime--;
        progress++;
        workTracker.recordActiveTick(level, getMachineProgressId());
        setStatus(MachineStatus.WORKING);
        setChanged();
        if (progress >= requiredTime) {
            finish(recipe);
        }
    }

    /**
     * Consumes the two ingredients and inserts the result. The conditions are checked again here,
     * so a pipe that filled the output during the last tick cannot destroy the alloy.
     */
    private void finish(AlloyRecipe recipe) {
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT),
                recipe.result())) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        if (!recipe.matchesStacks(inventory.getStackInSlot(FIRST_SLOT),
                inventory.getStackInSlot(SECOND_SLOT))) {
            progress = 0;
            setStatus(MachineStatus.NO_INPUT);
            setChanged();
            return;
        }
        inventory.extractItem(FIRST_SLOT, 1, false);
        inventory.extractItem(SECOND_SLOT, 1, false);
        ProcessingBlockEntity.insertResult(inventory, OUTPUT_SLOT, recipe.result());
        progress = 0;
        burnTimeTotal = Math.max(burnTimeTotal, burnTime);
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    /** Takes one fuel item and starts its burn. Returns false when no fuel is available. */
    private boolean consumeFuel(ServerLevel level) {
        ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
        if (fuel.isEmpty()) {
            return false;
        }
        int rawDuration = fuel.getBurnTime(RecipeType.SMELTING);
        if (rawDuration <= 0) {
            return false;
        }
        int duration = UpgradeMath.extendedBurnTime(rawDuration, getBatteryCount());
        if (fuel.hasCraftingRemainingItem()) {
            inventory.setStackInSlot(FUEL_SLOT, fuel.getCraftingRemainingItem());
        } else {
            fuel.shrink(1);
        }
        burnTime = duration;
        burnTimeTotal = duration;
        setChanged();
        return true;
    }

    /** All alloying recipes of the current recipe manager, cached per recipe manager instance. */
    private List<AlloyRecipe> allRecipes() {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (cachedRecipeManager != manager) {
            cachedRecipeManager = manager;
            List<AlloyRecipe> found = new java.util.ArrayList<>();
            for (var holder : manager.getAllRecipesFor(ModRecipes.ALLOYING_TYPE.get())) {
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
            if (st.hasProperty(com.industrialcore.block.AlloyFurnaceBlock.LIT)
                    && st.getValue(com.industrialcore.block.AlloyFurnaceBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.AlloyFurnaceBlock.LIT, working),
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
        return Component.translatable("block.industrialcore.alloy_furnace");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AlloyFurnaceMenu(containerId, playerInventory, this, dataAccess);
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
        return "alloy_furnace";
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

    public MachineStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getBurnTimeTotal() {
        return burnTimeTotal;
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    private int effectiveTime(AlloyRecipe recipe) {
        int motorTime = UpgradeMath.acceleratedTime(recipe.timeTicks(), getMotorCount());
        int ownerLvl = level instanceof ServerLevel sl
                ? workTracker.ownerLevel(sl, getMachineProgressId()) : 0;
        return UpgradeMath.applyMasteryTime(motorTime, ownerLvl);
    }

    /** Duration of the running operation, or the default when nothing is being alloyed. */
    public int getOperationTime() {
        AlloyRecipe recipe = AlloyRecipe.find(allRecipes(),
                inventory.getStackInSlot(FIRST_SLOT), inventory.getStackInSlot(SECOND_SLOT));
        return recipe == null ? AlloyBalance.TIME_TICKS : effectiveTime(recipe);
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
        progress = 0;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Persistence / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        workTracker.save(tag);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.putInt(TAG_BURN, burnTime);
        tag.putInt(TAG_BURN_TOTAL, burnTimeTotal);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level == null || !level.isClientSide) {
            workTracker.load(tag);
        }
        if (level != null && level.isClientSide) {
            // The client only receives display data; the inventory must never be rebuilt from a
            // block update.
            return;
        }
        if (tag.contains(TAG_INVENTORY)) {
            UpgradeInventoryUtil.loadWithMinSize(inventory, registries,
                    tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        }
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        burnTime = Math.max(0, tag.getInt(TAG_BURN));
        burnTimeTotal = Math.max(burnTime, tag.getInt(TAG_BURN_TOTAL));
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // The GUI reads status and progress from the container data; no block entity data has to
        // travel with a block update.
        return new CompoundTag();
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** Player view of the four slots. The output slot can never be filled from the outside. */
    public static final class AlloyItemHandler extends ItemStackHandler {
        private final AlloyFurnaceBlockEntity owner;

        AlloyItemHandler(AlloyFurnaceBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case FIRST_SLOT -> owner.acceptsFirst(stack);
                case SECOND_SLOT -> owner.acceptsSecond(stack);
                case FUEL_SLOT -> stack.getBurnTime(RecipeType.SMELTING) > 0;
                case MOTOR_SLOT -> stack.is(ModItems.MOTOR.get());
                case BATTERY_SLOT -> stack.is(ModItems.BATTERY.get());
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.setChanged();
        }
    }

    /** True when the item is the first ingredient of some recipe. */
    private boolean acceptsFirst(ItemStack stack) {
        List<AlloyRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            // Nothing known yet (datapack loading): never block the player; the tick re-checks.
            return true;
        }
        for (AlloyRecipe recipe : recipes) {
            if (recipe.isFirst(stack)) {
                return true;
            }
        }
        return false;
    }

    /** True when the item is the second ingredient of some recipe. */
    private boolean acceptsSecond(ItemStack stack) {
        List<AlloyRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            return true;
        }
        for (AlloyRecipe recipe : recipes) {
            if (recipe.isSecond(stack)) {
                return true;
            }
        }
        return false;
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
            if (slot != FIRST_SLOT && slot != SECOND_SLOT && slot != FUEL_SLOT) {
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
            return slot == FIRST_SLOT || slot == SECOND_SLOT || slot == FUEL_SLOT;
        }
    }
}
