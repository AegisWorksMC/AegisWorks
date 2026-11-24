package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.FaceAwareItemHandler;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ReconstructorMinerals;
import com.industrialcore.machine.ReconstructionRecipe;
import com.industrialcore.menu.ReconstructorMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * Block entity of the Phase 15 matter reconstructor.
 *
 * <p>The machine has four slots:</p>
 * <ul>
 *   <li>{@link #ANALYSIS_SLOT} - the sample. Only the player may fill it and only the analyse
 *       button consumes it.</li>
 *   <li>{@link #MATERIAL_SLOT} - the material (stone or deepslate).</li>
 *   <li>{@link #CRYSTAL_SLOT} - the reconstruction crystal.</li>
 *   <li>{@link #OUTPUT_SLOT} - the produced raw ore.</li>
 * </ul>
 *
 * <p>The rules that keep the machine safe:</p>
 * <ul>
 *   <li>analysing is irreversible and per machine: the unlocked minerals live in the NBT, so a
 *       second furnace needs its own samples,</li>
 *   <li>material and crystal are only consumed when the run finishes and the result is placed,</li>
 *   <li>a full output slot freezes the machine without spending FE,</li>
 *   <li>the result is always raw ore, never an ingot, so no loop can grow items.</li>
 * </ul>
 */
public class ReconstructorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int ANALYSIS_SLOT = 0;
    public static final int MATERIAL_SLOT = 1;
    public static final int CRYSTAL_SLOT = 2;
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
    private static final String TAG_FACES = "Faces";
    private static final String TAG_MINERALS = "Minerals";
    private static final String TAG_SELECTED = "Selected";

    private final ReconstructorItemHandler inventory = new ReconstructorItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    /** Phase 11.3-11.5: per-face mode, target slot and item filter. */
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    private final IItemHandler[] sideHandlers = new IItemHandler[6];
    private final ModEnergyStorage energy =
            new ModEnergyStorage(Config.reconstructorCapacity(), Config.reconstructorMaxReceive());
    /** Unlocked minerals and the current selection (Phase 15). */
    private final ReconstructorMinerals minerals = new ReconstructorMinerals();

    private int progress;
    private MachineStatus status = MachineStatus.NO_SELECTION;

    /** Cached recipe list; rebuilt when the recipe manager instance changes (datapack reload). */
    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<ReconstructionRecipe> cachedRecipes = List.of();

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

    public ReconstructorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECONSTRUCTOR.get(), pos, state);
        for (int side = 0; side < 6; side++) {
            sideHandlers[side] = new FaceAwareItemHandler(automationHandler, faceConfig,
                    Direction.from3DDataValue(side), null,
                    slot -> slot == MATERIAL_SLOT || slot == CRYSTAL_SLOT,
                    slot -> slot >= 0 && slot < MOTOR_SLOT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ReconstructorBlockEntity machine) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tick(serverLevel);
        }
    }

    /**
     * The processing loop of the Phase 15 specification, in the same order:
     * selection, recipe, output space, material, crystal, energy, progress, completion.
     */
    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(Config.reconstructorCapacity(), getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Config.reconstructorMaxReceive();
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        String mineral = minerals.selected();
        if (mineral.isEmpty()) {
            setStatus(MachineStatus.NO_SELECTION);
            return;
        }
        ReconstructionRecipe recipe = findRecipe(mineral);
        if (recipe == null) {
            setStatus(MachineStatus.NO_RECIPE);
            return;
        }
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT), recipe.result())) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        if (!holdsEnough(inventory.getStackInSlot(MATERIAL_SLOT), recipe.material(),
                recipe.materialCount())) {
            setStatus(MachineStatus.NO_MATERIAL);
            return;
        }
        if (!holdsEnough(inventory.getStackInSlot(CRYSTAL_SLOT), recipe.crystal(),
                recipe.crystalCount())) {
            setStatus(MachineStatus.NO_CRYSTAL);
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

    /**
     * Consumes the material and the crystal and places the result. The ingredients are taken in
     * the same step as the result appears, so a half finished run never loses items.
     */
    private void finish(ReconstructionRecipe recipe) {
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT), recipe.result())) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        if (!holdsEnough(inventory.getStackInSlot(MATERIAL_SLOT), recipe.material(),
                recipe.materialCount())) {
            setStatus(MachineStatus.NO_MATERIAL);
            return;
        }
        if (!holdsEnough(inventory.getStackInSlot(CRYSTAL_SLOT), recipe.crystal(),
                recipe.crystalCount())) {
            setStatus(MachineStatus.NO_CRYSTAL);
            return;
        }
        consume(MATERIAL_SLOT, recipe.materialCount());
        consume(CRYSTAL_SLOT, recipe.crystalCount());
        ProcessingBlockEntity.insertResult(inventory, OUTPUT_SLOT, recipe.result());
        progress = 0;
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    /** True when the slot holds at least {@code count} items of the ingredient. */
    private static boolean holdsEnough(ItemStack stack, Ingredient ingredient, int count) {
        if (count <= 0) {
            return true;
        }
        return !stack.isEmpty() && stack.getCount() >= count && ingredient.test(stack);
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
    // Analysis and mineral selection
    // ------------------------------------------------------------------

    /** Outcome of an analysis request. */
    public enum AnalysisResult {
        /** The mineral was unlocked and the sample was consumed. */
        UNLOCKED,
        /** The analysis slot is empty. */
        NO_SAMPLE,
        /** The sample does not belong to any reconstruction recipe. */
        NO_MATCH,
        /** The mineral of the sample is already unlocked; nothing was consumed. */
        ALREADY_UNLOCKED,
        /** The request came from the client side. */
        REJECTED
    }

    /**
     * Analyses the sample in the analysis slot.
     *
     * <p>The server looks for a recipe whose sample matches and whose mineral is not unlocked yet.
     * Only such a recipe unlocks a mineral and consumes one sample; every other case changes
     * nothing.</p>
     */
    public AnalysisResult analyzeSample() {
        if (level == null || level.isClientSide) {
            return AnalysisResult.REJECTED;
        }
        ItemStack sample = inventory.getStackInSlot(ANALYSIS_SLOT);
        if (sample.isEmpty()) {
            return AnalysisResult.NO_SAMPLE;
        }
        ReconstructionRecipe recipe = findAnalysisRecipe(sample);
        if (recipe == null) {
            return findSampleRecipe(sample) != null
                    ? AnalysisResult.ALREADY_UNLOCKED
                    : AnalysisResult.NO_MATCH;
        }
        if (!minerals.unlock(recipe.mineral())) {
            return AnalysisResult.ALREADY_UNLOCKED;
        }
        inventory.extractItem(ANALYSIS_SLOT, 1, false);
        selectMineral(recipe.mineral());
        setChanged();
        syncToClients();
        return AnalysisResult.UNLOCKED;
    }

    /**
     * Selects an unlocked mineral. Selecting a different mineral starts a new run, so the
     * progress is reset.
     *
     * @return false when the mineral is not unlocked
     */
    public boolean selectMineral(String mineral) {
        String previous = minerals.selected();
        if (!minerals.select(mineral)) {
            return false;
        }
        if (!mineral.equals(previous)) {
            progress = 0;
        }
        setChanged();
        syncToClients();
        return true;
    }

    /** Switches to the next unlocked mineral. */
    public boolean cycleMineral() {
        String previous = minerals.selected();
        String next = minerals.cycle();
        if (next.isEmpty()) {
            return false;
        }
        if (!next.equals(previous)) {
            progress = 0;
        }
        setChanged();
        syncToClients();
        return true;
    }

    /** True when the sample can be analysed right now (used to enable the button). */
    public boolean canAnalyse(ItemStack sample) {
        if (sample == null || sample.isEmpty()) {
            return false;
        }
        return findAnalysisRecipe(sample) != null;
    }

    /** The recipe of a sample whose mineral is not unlocked yet. */
    @Nullable
    private ReconstructionRecipe findAnalysisRecipe(ItemStack sample) {
        for (ReconstructionRecipe recipe : allRecipes()) {
            if (recipe.matchesSample(sample) && !minerals.isUnlocked(recipe.mineral())) {
                return recipe;
            }
        }
        return null;
    }

    /** The first recipe whose sample matches, no matter whether it is unlocked. */
    @Nullable
    private ReconstructionRecipe findSampleRecipe(ItemStack sample) {
        for (ReconstructionRecipe recipe : allRecipes()) {
            if (recipe.matchesSample(sample)) {
                return recipe;
            }
        }
        return null;
    }

    /** The recipe of the selected mineral. */
    @Nullable
    private ReconstructionRecipe findRecipe(String mineral) {
        for (ReconstructionRecipe recipe : allRecipes()) {
            if (recipe.mineral().equals(mineral)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * All reconstruction recipes of the current recipe manager. The list is cached and rebuilt
     * when the manager instance changes, which happens on every datapack reload.
     */
    private List<ReconstructionRecipe> allRecipes() {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (cachedRecipeManager != manager) {
            cachedRecipeManager = manager;
            List<ReconstructionRecipe> found = new ArrayList<>();
            for (var holder : manager.getAllRecipesFor(ModRecipes.RECONSTRUCTION_TYPE.get())) {
                found.add(holder.value());
            }
            cachedRecipes = List.copyOf(found);
        }
        return cachedRecipes;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.reconstructor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReconstructorMenu(containerId, playerInventory, this, dataAccess);
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
        return "reconstructor";
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

    /** Duration of the running operation, or the default when nothing is selected. */
    public int getOperationTime() {
        ReconstructionRecipe recipe = level == null ? null : findRecipe(minerals.selected());
        int base = recipe == null ? com.industrialcore.machine.ReconstructorBalance.IRON_TIME_TICKS
                : recipe.timeTicks();
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

    /** Unlocked mineral keys in the order they were unlocked. */
    public List<String> getUnlockedMinerals() {
        return minerals.unlocked();
    }

    /** The selected mineral key, empty when nothing is selected. */
    public String getSelectedMineral() {
        return minerals.selected();
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
            case MachineActions.FACE_SLOT ->
                    faceConfig.cycleSlot(side, new int[] {MATERIAL_SLOT, CRYSTAL_SLOT});
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
        setChanged();
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.ReconstructorBlock.LIT)
                    && st.getValue(com.industrialcore.block.ReconstructorBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.ReconstructorBlock.LIT, working),
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
        tag.put(TAG_MINERALS, saveMinerals());
        tag.putString(TAG_SELECTED, minerals.selected());
    }

    private ListTag saveMinerals() {
        ListTag list = new ListTag();
        for (String mineral : minerals.unlocked()) {
            list.add(StringTag.valueOf(mineral));
        }
        return list;
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
            faceConfig.setMode(side, com.industrialcore.machine.FaceMode.byIndex(face.getByte("Mode") & 0xFF));
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
        if (tag.contains(TAG_MINERALS)) {
            ListTag list = tag.getList(TAG_MINERALS, 8);
            List<String> keys = new ArrayList<>();
            for (int index = 0; index < list.size(); index++) {
                keys.add(list.getString(index));
            }
            minerals.restore(keys, tag.getString(TAG_SELECTED));
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
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Display data only: the GUI needs the face settings and the unlocked minerals; the
        // inventory must never travel through a block update.
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_FACES, saveFaces());
        tag.put(TAG_MINERALS, saveMinerals());
        tag.putString(TAG_SELECTED, minerals.selected());
        return tag;
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /**
     * Player view of the four slots. The analysis slot takes any item (the player decides what to
     * analyse), the material and the crystal slot only take items that a recipe asks for and the
     * output slot can never be filled from the outside.
     */
    public static final class ReconstructorItemHandler extends ItemStackHandler {
        private final ReconstructorBlockEntity owner;

        ReconstructorItemHandler(ReconstructorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case ANALYSIS_SLOT -> true;
                case MATERIAL_SLOT -> owner.acceptsMaterial(stack);
                case CRYSTAL_SLOT -> owner.acceptsCrystal(stack);
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

    private boolean acceptsMaterial(ItemStack stack) {
        List<ReconstructionRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            // No recipe is known (for example while the datapack is loading): do not block the
            // player, the machine checks the ingredient again before every run.
            return true;
        }
        for (ReconstructionRecipe recipe : recipes) {
            if (recipe.material().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean acceptsCrystal(ItemStack stack) {
        List<ReconstructionRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            return true;
        }
        for (ReconstructionRecipe recipe : recipes) {
            if (recipe.crystal().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Automation view: pipes may insert into the material and the crystal slot and may extract
     * from every slot. The analysis slot is never filled by a pipe, because only the player may
     * analyse a sample.
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
            if (slot != MATERIAL_SLOT && slot != CRYSTAL_SLOT) {
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
            return slot == MATERIAL_SLOT || slot == CRYSTAL_SLOT;
        }
    }
}
