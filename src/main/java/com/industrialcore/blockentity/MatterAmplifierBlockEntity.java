package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.AmplificationRecipe;
import com.industrialcore.machine.FaceAwareItemHandler;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.SeedLineBalance;
import com.industrialcore.menu.MatterAmplifierMenu;
import com.industrialcore.registry.ModBlockEntities;
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
import net.minecraft.world.item.Item;
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
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity of the Phase 21 matter amplifier.
 *
 * <p>Its job is the one thing no other machine can do: turn a cultivated billet into
 * <em>ore blocks</em> ({@code minecraft:*_ore}), which before Phase 21 could only be obtained by
 * mining with silk touch. Those blocks feed the existing 6x line, so the amplifier automates the
 * entry of the 6x chain rather than adding a multiplier of its own.</p>
 *
 * <p>Four slots: billet input, crystal catalyst, ore output and dust output. One run takes 1,200
 * ticks at 60 FE per tick (72,000 FE) and consumes the billet and the crystal only when the run
 * finishes, exactly like the Phase 15 reconstructor. A full output freezes the machine without
 * spending FE and without losing progress.</p>
 */
public class MatterAmplifierBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int INPUT_SLOT = 0;
    public static final int CATALYST_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int DUST_OUTPUT_SLOT = 3;
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
    private static final String TAG_FACES = "Faces";

    private final AmplifierItemHandler inventory = new AmplifierItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    private final IItemHandler[] sideHandlers = new IItemHandler[6];
    private final ModEnergyStorage energy = new ModEnergyStorage(
            Config.matterAmplifierCapacity(), Config.matterAmplifierMaxReceive());

    private int progress;
    private MachineStatus status = MachineStatus.IDLE;

    /** Recipe of the running job, kept so a changed input slot cannot change the output. */
    @Nullable
    private AmplificationRecipe activeRecipe;
    /** Idle lookup cache: the billet item resolved last and its recipe (may be null). */
    @Nullable
    private Item cachedInputItem;
    @Nullable
    private AmplificationRecipe cachedInputRecipe;
    private boolean inputResolved;
    /** Cached recipe list; rebuilt when the recipe manager instance changes (datapack reload). */
    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<AmplificationRecipe> cachedRecipes = List.of();

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

    public MatterAmplifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATTER_AMPLIFIER.get(), pos, state);
        for (int side = 0; side < 6; side++) {
            sideHandlers[side] = new FaceAwareItemHandler(automationHandler, faceConfig,
                    Direction.from3DDataValue(side), null,
                    slot -> slot == INPUT_SLOT || slot == CATALYST_SLOT,
                    slot -> slot >= 0 && slot < MOTOR_SLOT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  MatterAmplifierBlockEntity machine) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tick(serverLevel);
        }
    }

    /** One server tick: recipe, both outputs, both inputs, FE, progress, completion. */
    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(Config.matterAmplifierCapacity(), getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Config.matterAmplifierMaxReceive();
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        AmplificationRecipe recipe = this.activeRecipe;
        if (recipe == null) {
            if (input.isEmpty()) {
                setStatus(MachineStatus.NO_INPUT);
                return;
            }
            recipe = recipeForInput(input);
            if (recipe == null) {
                setStatus(MachineStatus.NO_RECIPE);
                return;
            }
            this.activeRecipe = recipe;
        }
        if (input.isEmpty()) {
            // The billet is only consumed at completion, so pulling it out pauses the run and
            // keeps the progress; putting the same billet back resumes it. Nothing is produced
            // and no FE is spent while the slot stays empty.
            setStatus(MachineStatus.NO_INPUT);
            return;
        }
        if (!recipe.matchesIngredient(input)) {
            // A different billet is a different job: start over instead of spending one metal and
            // producing the ore of another. The FE of the aborted attempt is not refunded (same
            // rule as every other machine), but nothing is consumed.
            this.activeRecipe = null;
            this.progress = 0;
            setStatus(MachineStatus.NO_INPUT);
            return;
        }
        if (!recipe.matchesCatalyst(inventory.getStackInSlot(CATALYST_SLOT))) {
            setStatus(MachineStatus.NO_CRYSTAL);
            return;
        }
        if (!fitsBothOutputs(recipe)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        int time = UpgradeMath.applyMasteryTime(
                UpgradeMath.acceleratedTime(recipe.timeTicks(), getMotorCount()), ownerLvl);
        if (progress < time) {
            int cost = UpgradeMath.applyMasteryEnergy(
                    UpgradeMath.discountedEnergy(recipe.energyPerTick(), getBatteryCount()), ownerLvl);
            if (energy.getEnergyStored() < cost) {
                setStatus(MachineStatus.NO_ENERGY);
                return;
            }
            if (cost > 0) {
                energy.consume(cost);
                workTracker.recordActiveTick(level, getMachineProgressId());
            }
            progress++;
            setStatus(MachineStatus.WORKING);
            setChanged();
        }
        if (progress >= time) {
            finish(recipe);
        }
    }

    /** True when both outputs can take this recipe's products. */
    private boolean fitsBothOutputs(AmplificationRecipe recipe) {
        return ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT), recipe.result())
                && ProcessingBlockEntity.canAccept(inventory.getStackInSlot(DUST_OUTPUT_SLOT),
                recipe.secondary());
    }

    /** Consumes the billet and the crystal and places both outputs in one step. */
    private void finish(AmplificationRecipe recipe) {
        if (!fitsBothOutputs(recipe)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        if (!recipe.matchesIngredient(inventory.getStackInSlot(INPUT_SLOT))
                || !recipe.matchesCatalyst(inventory.getStackInSlot(CATALYST_SLOT))) {
            setStatus(MachineStatus.NO_INPUT);
            return;
        }
        consume(INPUT_SLOT, 1);
        consume(CATALYST_SLOT, 1);
        ProcessingBlockEntity.insertResult(inventory, OUTPUT_SLOT, recipe.result());
        ProcessingBlockEntity.insertResult(inventory, DUST_OUTPUT_SLOT, recipe.secondary());
        progress = 0;
        activeRecipe = null;
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    /** Removes {@code count} items from the slot. */
    private void consume(int slot, int count) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        int left = stack.getCount() - Math.min(count, stack.getCount());
        if (left <= 0) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        } else {
            stack.setCount(left);
            inventory.setStackInSlot(slot, stack);
        }
    }

    // ------------------------------------------------------------------
    // Recipes (cached)
    // ------------------------------------------------------------------

    /** Recipe of a billet, memoised per item so an idle machine does not re-scan every tick. */
    @Nullable
    private AmplificationRecipe recipeForInput(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        Item item = input.getItem();
        if (inputResolved && item == cachedInputItem) {
            return cachedInputRecipe;
        }
        AmplificationRecipe found = null;
        for (AmplificationRecipe recipe : allRecipes()) {
            if (recipe.matchesIngredient(input)) {
                found = recipe;
                break;
            }
        }
        cachedInputItem = item;
        cachedInputRecipe = found;
        inputResolved = true;
        return found;
    }

    /** All amplification recipes of the current recipe manager; rebuilt on a datapack reload. */
    private List<AmplificationRecipe> allRecipes() {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (cachedRecipeManager != manager) {
            cachedRecipeManager = manager;
            List<AmplificationRecipe> found = new ArrayList<>();
            for (var holder : manager.getAllRecipesFor(ModRecipes.AMPLIFICATION_TYPE.get())) {
                found.add(holder.value());
            }
            cachedRecipes = List.copyOf(found);
            inputResolved = false;
        }
        return cachedRecipes;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.matter_amplifier");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MatterAmplifierMenu(containerId, playerInventory, this, dataAccess);
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
        return "matter_amplifier";
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    /** Capability view of one face (Phase 11.3); null side keeps the unrestricted view. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return automationHandler;
        }
        return sideHandlers[side.get3DDataValue()];
    }

    public MachineFaceConfig getFaceConfig() {
        return faceConfig;
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

    /** Duration of this recipe, or the default when no billet is inserted. */
    public int getOperationTime() {
        AmplificationRecipe recipe = activeRecipe;
        int base;
        if (recipe != null) {
            base = recipe.timeTicks();
        } else {
            ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
            AmplificationRecipe found = recipeForInput(input);
            base = found == null ? SeedLineBalance.AMPLIFIER_TIME_TICKS : found.timeTicks();
        }
        int motorTime = UpgradeMath.acceleratedTime(base, getMotorCount());
        if (level instanceof ServerLevel sl) {
            return UpgradeMath.applyMasteryTime(motorTime, workTracker.ownerLevel(sl, getMachineProgressId()));
        }
        return motorTime;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    /** Applies one face action from the GUI (validated by the network handler). */
    public boolean applyFaceAction(int side, int action, @Nullable ItemStack filterStack) {
        if (level == null || level.isClientSide || side < 0 || side >= 6) {
            return false;
        }
        switch (action) {
            case MachineActions.FACE_MODE -> faceConfig.cycleMode(side);
            case MachineActions.FACE_SLOT ->
                    faceConfig.cycleSlot(side, new int[] {INPUT_SLOT, CATALYST_SLOT});
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
        setChanged();
        syncToClients();
        return true;
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
        activeRecipe = null;
        setChanged();
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.MatterAmplifierBlock.LIT)
                    && st.getValue(com.industrialcore.block.MatterAmplifierBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.MatterAmplifierBlock.LIT, working),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        if (status != next) {
            status = next;
            setChanged();
        }
    }

    /** Sends the display data to the clients, but only when something actually changed. */
    private void syncToClients() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
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
            faceConfig.setMode(side,
                    com.industrialcore.machine.FaceMode.byIndex(face.getByte("Mode") & 0xFF));
            faceConfig.setSlot(side, face.getInt("Slot"));
            faceConfig.setFilterMode(side,
                    com.industrialcore.machine.ItemFilterMode.byIndex(face.getByte("Filter") & 0xFF));
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
        if (level == null || !level.isClientSide) {
            workTracker.load(tag);
        }
        if (tag.contains(TAG_FACES)) {
            loadFaces(tag.getCompound(TAG_FACES));
        }
        if (level != null && level.isClientSide) {
            // Display data only: the authoritative inventory never travels through a block update.
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
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_FACES, saveFaces());
        return tag;
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** Player view: billet and crystal in, nothing into the two output slots. */
    public static final class AmplifierItemHandler extends ItemStackHandler {
        private final MatterAmplifierBlockEntity owner;

        AmplifierItemHandler(MatterAmplifierBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case INPUT_SLOT -> owner.acceptsInput(stack);
                case CATALYST_SLOT -> owner.acceptsCatalyst(stack);
                case MOTOR_SLOT -> stack.is(ModItems.MOTOR.get());
                case BATTERY_SLOT -> stack.is(ModItems.BATTERY.get());
                default -> false;
            };
        }
    }

    public int getMotorCount() {
        ItemStack stack = inventory.getStackInSlot(MOTOR_SLOT);
        return stack.is(ModItems.MOTOR.get())
                ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get())
                ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
    }

    private boolean acceptsInput(ItemStack stack) {
        List<AmplificationRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            return true;
        }
        for (AmplificationRecipe recipe : recipes) {
            if (recipe.matchesIngredient(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean acceptsCatalyst(ItemStack stack) {
        List<AmplificationRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            return true;
        }
        for (AmplificationRecipe recipe : recipes) {
            if (recipe.matchesCatalyst(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Automation view: pipes fill the billet and crystal slots and may drain all four. */
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
            if (slot != INPUT_SLOT && slot != CATALYST_SLOT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= MOTOR_SLOT) {
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
            return slot == INPUT_SLOT || slot == CATALYST_SLOT;
        }
    }
}
