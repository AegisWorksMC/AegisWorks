package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.CultivationRecipe;
import com.industrialcore.machine.FaceAwareItemHandler;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.SeedLineBalance;
import com.industrialcore.menu.SeedCultivatorMenu;
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
 * Block entity of the Phase 21 seed cultivator.
 *
 * <p>Three slots: the seed (one item, consumed when a batch starts), the dust feed and the billet
 * output. The machine grows a <em>batch</em> of {@code N} billets with one progress bar:</p>
 * <ul>
 *   <li>the batch size is fixed when the run starts: one billet per {@code feedPerUnit} (4) dust in
 *       the feed slot, at most {@link SeedLineBalance#MAX_BATCH},</li>
 *   <li>the feed is paid in four instalments of {@code N} dust: one at the start and one at 25%,
 *       50% and 75% of the run,</li>
 *   <li>a missing instalment pauses the run without consuming FE and without losing progress; the
 *       run resumes as soon as the dust arrives (the "continuous feed" of the specification),</li>
 *   <li>the energy per tick is {@code ceil(remaining FE / remaining ticks)}, so a batch always
 *       costs exactly {@code 24,000 x N} FE whatever the cable tier supplies.</li>
 * </ul>
 *
 * <p>Safety rules: nothing is consumed unless the output can hold the whole batch, the seed is
 * matched against a per-metal recipe (an iron seed can never grow a copper billet) and the run
 * remembers its recipe, so emptying the seed slot mid-run cannot change the output.</p>
 */
public class SeedCultivatorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    public static final int SEED_SLOT = 0;
    public static final int FEED_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int MOTOR_SLOT = 3;
    public static final int BATTERY_SLOT = 4;
    public static final int SLOT_COUNT = 5;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TIME = 4;
    public static final int DATA_BATCH = 5;
    public static final int DATA_FEED_LEFT = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_BATCH = "Batch";
    private static final String TAG_FEED_PAID = "FeedPaid";
    private static final String TAG_ENERGY_SPENT = "EnergySpent";
    private static final String TAG_ACTIVE = "ActiveResult";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_FACES = "Faces";

    private final CultivatorItemHandler inventory = new CultivatorItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    private final IItemHandler[] sideHandlers = new IItemHandler[6];
    private final ModEnergyStorage energy = new ModEnergyStorage(
            Config.seedCultivatorCapacity(), Config.seedCultivatorMaxReceive());

    /** Ticks of the current batch, 0 while nothing runs. */
    private int progress;
    /** Billets of the current batch, 0 while nothing runs. */
    private int batch;
    /** Feed instalments already paid (1 right after the start). */
    private int feedPaid;
    /** FE paid into the current batch; the per-tick cost is derived from it. */
    private long energySpent;
    private MachineStatus status = MachineStatus.IDLE;

    /** Recipe of the running batch, kept so emptying the seed slot cannot change the output. */
    @Nullable
    private CultivationRecipe activeRecipe;
    /**
     * Registry id of the billet the running batch produces. The recipe itself cannot be
     * reconstructed from an empty seed slot after a reload, so this id is saved and the recipe is
     * looked up again from it.
     */
    @Nullable
    private String activeResultId;
    /** Cache of the idle lookup: the seed item that was resolved last, and its recipe (may be null). */
    @Nullable
    private Item cachedSeedItem;
    @Nullable
    private CultivationRecipe cachedSeedRecipe;
    private boolean seedResolved;
    /** Cached recipe list; rebuilt when the recipe manager instance changes (datapack reload). */
    @Nullable
    private RecipeManager cachedRecipeManager;
    private List<CultivationRecipe> cachedRecipes = List.of();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TIME -> getOperationTime();
                case DATA_BATCH -> batch;
                case DATA_FEED_LEFT -> feedLeft();
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

    public SeedCultivatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEED_CULTIVATOR.get(), pos, state);
        for (int side = 0; side < 6; side++) {
            sideHandlers[side] = new FaceAwareItemHandler(automationHandler, faceConfig,
                    Direction.from3DDataValue(side), null,
                    slot -> slot == SEED_SLOT || slot == FEED_SLOT,
                    slot -> slot >= 0 && slot < MOTOR_SLOT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SeedCultivatorBlockEntity machine) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tick(serverLevel);
        }
    }

    /** One server tick: start a batch, pay the next feed instalment, pay FE, advance or finish. */
    private void tick(ServerLevel level) {
        int capacity = UpgradeMath.scaledCapacity(Config.seedCultivatorCapacity(), getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Config.seedCultivatorMaxReceive();
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        if (batch <= 0) {
            startBatch();
            return;
        }
        CultivationRecipe recipe = this.activeRecipe;
        if (recipe == null) {
            // A batch restored from disk has no recipe object yet: look it up from the saved
            // output id (the seed was consumed when the batch started).
            recipe = recipeForResult(this.activeResultId);
            if (recipe == null) {
                resetRun();
                setStatus(MachineStatus.NO_RECIPE);
                return;
            }
            this.activeRecipe = recipe;
        }

        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        int time = UpgradeMath.applyMasteryTime(
                UpgradeMath.acceleratedTime(recipe.timeTicksFor(batch), getMotorCount()), ownerLvl);
        if (progress < time && feedPaid < SeedLineBalance.FEED_INSTALMENTS) {
            int threshold = instalmentThreshold(time, feedPaid);
            if (progress >= threshold) {
                if (!holdsEnough(inventory.getStackInSlot(FEED_SLOT), recipe, batch)) {
                    setStatus(MachineStatus.NO_MATERIAL);
                    return;
                }
                consume(FEED_SLOT, batch);
                feedPaid++;
                setChanged();
            }
        }

        if (progress < time) {
            long baseTotal = recipe.energyFor(batch);
            double mult = UpgradeMath.energyCostMultiplier(getBatteryCount())
                    * com.industrialcore.level.MachineLevelMath.masteryEnergyMultiplier(ownerLvl);
            long total = Math.max(1L, Math.round(baseTotal * mult));
            long remaining = Math.max(0L, total - energySpent);
            int cost = SeedLineBalance.requiredFePerTick(remaining, time - progress);
            if (cost > 0 && energy.getEnergyStored() < cost) {
                setStatus(MachineStatus.NO_ENERGY);
                return;
            }
            if (cost > 0) {
                energy.consume(cost);
                energySpent += cost;
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

    /** Threshold tick of the feed instalment that is due next ({@code paid} is 1..3). */
    private static int instalmentThreshold(int time, int paid) {
        return (int) (((long) time * paid + SeedLineBalance.FEED_INSTALMENTS - 1)
                / SeedLineBalance.FEED_INSTALMENTS);
    }

    /** Tries to start a batch; consumes nothing when any condition is missing. */
    private void startBatch() {
        ItemStack seed = inventory.getStackInSlot(SEED_SLOT);
        if (seed.isEmpty()) {
            setStatus(MachineStatus.NO_INPUT);
            return;
        }
        CultivationRecipe recipe = recipeForSeed(seed);
        if (recipe == null) {
            setStatus(MachineStatus.NO_RECIPE);
            return;
        }
        ItemStack feed = inventory.getStackInSlot(FEED_SLOT);
        if (!recipe.matchesFeed(feed) || feed.getCount() < recipe.feedPerUnit()) {
            setStatus(MachineStatus.NO_MATERIAL);
            return;
        }
        int wanted = SeedLineBalance.batchFromFeed(feed.getCount());
        ItemStack result = recipe.result().copyWithCount(wanted);
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT), result)) {
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        int cost = SeedLineBalance.requiredFePerTick(recipe.energyFor(wanted),
                recipe.timeTicksFor(wanted));
        if (cost > 0 && energy.getEnergyStored() < cost) {
            setStatus(MachineStatus.NO_ENERGY);
            return;
        }

        // Start: one seed and the first feed instalment.
        consume(SEED_SLOT, 1);
        consume(FEED_SLOT, wanted);
        this.batch = wanted;
        this.progress = 0;
        this.feedPaid = 1;
        this.energySpent = 0L;
        this.activeRecipe = recipe;
        this.activeResultId = BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString();
        setStatus(MachineStatus.WORKING);
        setChanged();
    }

    /** Places the whole batch and resets the run. The output space was checked at the start. */
    private void finish(CultivationRecipe recipe) {
        ItemStack result = recipe.result().copyWithCount(batch);
        if (!ProcessingBlockEntity.canAccept(inventory.getStackInSlot(OUTPUT_SLOT), result)) {
            // Only possible when something else filled the slot during the run: keep the progress,
            // spend nothing more and wait for the output to be drained.
            setStatus(MachineStatus.OUTPUT_FULL);
            return;
        }
        ProcessingBlockEntity.insertResult(inventory, OUTPUT_SLOT, result);
        resetRun();
        setStatus(MachineStatus.IDLE);
        setChanged();
    }

    private void resetRun() {
        progress = 0;
        batch = 0;
        feedPaid = 0;
        energySpent = 0L;
        activeRecipe = null;
        activeResultId = null;
    }

    /** True when the slot holds at least {@code count} items of the recipe's feed. */
    private static boolean holdsEnough(ItemStack stack, CultivationRecipe recipe, int count) {
        return !stack.isEmpty() && stack.getCount() >= count && recipe.matchesFeed(stack);
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

    /**
     * Recipe of a seed. The lookup result is memoised per seed item, so an idle machine with a seed
     * that has no recipe does not re-scan the recipe manager every tick.
     */
    @Nullable
    private CultivationRecipe recipeForSeed(ItemStack seed) {
        if (seed.isEmpty()) {
            return null;
        }
        Item item = seed.getItem();
        if (seedResolved && item == cachedSeedItem) {
            return cachedSeedRecipe;
        }
        CultivationRecipe found = null;
        for (CultivationRecipe recipe : allRecipes()) {
            if (recipe.matchesSeed(seed)) {
                found = recipe;
                break;
            }
        }
        cachedSeedItem = item;
        cachedSeedRecipe = found;
        seedResolved = true;
        return found;
    }

    /** Recipe of a saved billet id, or null when the recipe no longer exists (datapack change). */
    @Nullable
    private CultivationRecipe recipeForResult(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (CultivationRecipe recipe : allRecipes()) {
            if (BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    /** All cultivation recipes of the current recipe manager; rebuilt on a datapack reload. */
    private List<CultivationRecipe> allRecipes() {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        if (cachedRecipeManager != manager) {
            cachedRecipeManager = manager;
            List<CultivationRecipe> found = new ArrayList<>();
            for (var holder : manager.getAllRecipesFor(ModRecipes.CULTIVATION_TYPE.get())) {
                found.add(holder.value());
            }
            cachedRecipes = List.copyOf(found);
            seedResolved = false;
        }
        return cachedRecipes;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.seed_cultivator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SeedCultivatorMenu(containerId, playerInventory, this, dataAccess);
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
        return "seed_cultivator";
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

    public int getBatch() {
        return batch;
    }

    /** Feed dust still to be paid for the running batch (0 while idle). */
    public int feedLeft() {
        if (batch <= 0) {
            return 0;
        }
        return Math.max(0, SeedLineBalance.FEED_INSTALMENTS - feedPaid) * batch;
    }

    /** Feed dust the next instalment needs (0 while idle). */
    public int nextInstalment() {
        return batch > 0 && feedPaid < SeedLineBalance.FEED_INSTALMENTS ? batch : 0;
    }

    /** Duration of the running batch, or the N=0 default when nothing runs. */
    public int getOperationTime() {
        int base;
        if (batch <= 0) {
            base = SeedLineBalance.CULTIVATOR_TIME_TICKS;
        } else {
            CultivationRecipe recipe = activeRecipe;
            base = recipe == null ? SeedLineBalance.billetTime(batch) : recipe.timeTicksFor(batch);
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
                    faceConfig.cycleSlot(side, new int[] {SEED_SLOT, FEED_SLOT});
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
        resetRun();
        setChanged();
    }

    private void setStatus(MachineStatus next) {
        boolean working = next == MachineStatus.WORKING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.SeedCultivatorBlock.LIT)
                    && st.getValue(com.industrialcore.block.SeedCultivatorBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.SeedCultivatorBlock.LIT, working),
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
        tag.putInt(TAG_BATCH, batch);
        tag.putInt(TAG_FEED_PAID, feedPaid);
        tag.putLong(TAG_ENERGY_SPENT, energySpent);
        if (activeResultId != null) {
            tag.putString(TAG_ACTIVE, activeResultId);
        }
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
        batch = Math.max(0, Math.min(SeedLineBalance.MAX_BATCH, tag.getInt(TAG_BATCH)));
        feedPaid = Math.max(0, Math.min(SeedLineBalance.FEED_INSTALMENTS, tag.getInt(TAG_FEED_PAID)));
        energySpent = Math.max(0L, tag.getLong(TAG_ENERGY_SPENT));
        activeResultId = tag.contains(TAG_ACTIVE) ? tag.getString(TAG_ACTIVE) : null;
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
        status = values[ordinal];
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Display data only: the GUI needs the face settings, nothing else travels this way.
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_FACES, saveFaces());
        return tag;
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    /** Player view: the seed and the feed slots take their items, the output slot only outputs. */
    public static final class CultivatorItemHandler extends ItemStackHandler {
        private final SeedCultivatorBlockEntity owner;

        CultivatorItemHandler(SeedCultivatorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SEED_SLOT -> owner.acceptsSeed(stack);
                case FEED_SLOT -> owner.acceptsFeed(stack);
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

    private boolean acceptsSeed(ItemStack stack) {
        List<CultivationRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            // Unknown datapack state: do not block the player, the machine re-checks on start.
            return true;
        }
        for (CultivationRecipe recipe : recipes) {
            if (recipe.matchesSeed(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean acceptsFeed(ItemStack stack) {
        List<CultivationRecipe> recipes = allRecipes();
        if (recipes.isEmpty()) {
            return true;
        }
        for (CultivationRecipe recipe : recipes) {
            if (recipe.matchesFeed(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Automation view: pipes fill the seed and feed slots and may drain any slot. */
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
            if (slot != SEED_SLOT && slot != FEED_SLOT) {
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
            return slot == SEED_SLOT || slot == FEED_SLOT;
        }
    }
}
