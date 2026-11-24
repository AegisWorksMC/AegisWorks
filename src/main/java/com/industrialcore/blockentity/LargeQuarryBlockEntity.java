package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.LargeQuarryBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.machine.LargeQuarryBalance;
import com.industrialcore.machine.LargeQuarrySlots;
import com.industrialcore.machine.MiningHarvest;
import com.industrialcore.machine.MiningPlan;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.PickaxeStats;
import com.industrialcore.machine.QuarryFrame;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.LargeQuarryMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server authoritative large quarry (Phase 20, reworked in Phase 22).
 *
 * <p>The machine is no longer a general excavator. It scans its fixed area and mines <b>only the
 * ore blocks the player selected in the GUI</b>; stone, deepslate, dirt and every other block stay
 * untouched. That is the difference to the normal quarry, which clears everything for landscaping
 * and building material.</p>
 *
 * <p>Installed pickaxes decide the result and the price:</p>
 * <ul>
 *   <li>Enchantments of all installed pickaxes are merged by adding their levels (Fortune II + II
 *       = IV, Silk Touch I + I = II, ...). There is no mod specific cap.</li>
 *   <li>Silk Touch recovers the ore block itself, {@code 1 + fortune / 2} blocks per mined block.
 *       Without Silk Touch the ordinary drops are multiplied by {@code 1 + fortune}. Silk Touch
 *       therefore never discards Fortune and Fortune never discards Silk Touch.</li>
 *   <li>Efficiency also speeds up the scan (5% per merged level, hard per-tick cap).</li>
 *   <li>The FE cost per mined ore block grows with the tool count and the merged levels, including
 *       a superlinear term, so very strong setups need serious power infrastructure.</li>
 * </ul>
 *
 * <p>Performance rules: the only per-tick work is the cursor step over the fixed plan; nothing is
 * scanned again, no neighbour lookup is repeated, and the merged enchantment totals plus the FE
 * cost are cached until the tool inventory changes. A tick breaks at most
 * {@code large_quarry.max_operations_per_tick} blocks through the shared {@link MiningScheduler}
 * budget. Unloaded chunks are never force loaded, and a block is only destroyed after its drops
 * were proven to fit.</p>
 */
public class LargeQuarryBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    //
    // The slot layout is defined once, in LargeQuarrySlots. These names are kept because the menu,
    // the GameTests and older callers use them, but they are only aliases now: two independent
    // definitions of the same range are exactly what broke the Phase 20 -> Phase 22 migration.

    /** Collected ore / dust slots. */
    public static final int OUTPUT_START = LargeQuarrySlots.OUTPUT_START;
    public static final int OUTPUT_SLOTS = LargeQuarrySlots.OUTPUT_SLOTS;
    /** Pickaxe slots; only the first {@code toolSlots} are active. */
    public static final int TOOL_START = LargeQuarrySlots.TOOL_START;
    public static final int TOOL_CAPACITY = LargeQuarrySlots.TOOL_CAPACITY;
    /** Motor slot that permanently unlocks one more pickaxe arm. */
    public static final int UPGRADE_SLOT = LargeQuarrySlots.UPGRADE_SLOT;
    /** Phase 87: battery slot for FE buffer expansion and energy discount. */
    public static final int BATTERY_SLOT = LargeQuarrySlots.BATTERY_SLOT;
    public static final int TOTAL_SLOTS = LargeQuarrySlots.TOTAL_SLOTS;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_CURSOR = 3;
    public static final int DATA_VOLUME = 4;
    public static final int DATA_RUNNING = 5;
    public static final int DATA_WIDTH = 6;
    public static final int DATA_DEPTH = 7;
    public static final int DATA_HEIGHT = 8;
    public static final int DATA_MINED = 9;
    public static final int DATA_SKIPPED = 10;
    public static final int DATA_ITEMS = 11;
    public static final int DATA_TOOL_SLOTS = 12;
    public static final int DATA_ORE_TOTAL = 13;
    public static final int DATA_ORE_SELECTED = 14;
    public static final int DATA_COST = 15;
    public static final int DATA_PICKAXES = 16;
    public static final int DATA_EFFICIENCY = 17;
    public static final int DATA_FORTUNE = 18;
    public static final int DATA_SILK = 19;
    /** Phase 82: installed motors (arms beyond the eight-arm limit drive the frame). */
    public static final int DATA_MOTORS = 20;
    public static final int DATA_COUNT = 21;

    private static final String TAG_WIDTH = "AreaWidth";
    private static final String TAG_DEPTH = "AreaDepth";
    private static final String TAG_HEIGHT = "AreaHeight";
    private static final String TAG_CURSOR = "Cursor";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_MINED = "Mined";
    private static final String TAG_SKIPPED = "Skipped";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_TOOL_SLOTS = "ToolSlots";
    /** Phase 81: every installed motor, including those beyond the eight-arm layout limit. */
    private static final String TAG_MOTORS = "Motors";
    private static final String TAG_SELECTED_ORES = "SelectedOres";
    private static final String TAG_SELECTION_SET = "SelectionSet";
    private static final String TAG_CLIENT = "Client";
    private static final String TAG_CLIENT_ORES = "Ores";
    private static final String TAG_CLIENT_TOOLS = "Tools";
    private static final String TAG_CLIENT_EFFICIENCY = "Efficiency";
    private static final String TAG_CLIENT_FORTUNE = "Fortune";
    private static final String TAG_CLIENT_SILK = "Silk";
    private static final String TAG_CLIENT_COST = "Cost";
    private static final String TAG_CLIENT_TOOL_SLOTS = "ClientToolSlots";
    private static final String TAG_CLIENT_ORE_TOTAL = "OreTotal";

    /** Ticks before a blocked attempt retries; taking items out clears the wait immediately. */
    private static final int PAUSE_RETRY_TICKS = 8;

    private final LargeQuarryItemHandler inventory = new LargeQuarryItemHandler(this);
    private final OutputHandler outputView = new OutputHandler();
    private final ItemStackHandler storageSimulation = new ItemStackHandler(OUTPUT_SLOTS);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LargeQuarryBalance.CAPACITY, LargeQuarryBalance.MAX_RECEIVE);
    /** Plain tool used for the loot roll: the machine applies the merged enchantments itself. */
    private final ItemStack baseTool = new ItemStack(Items.NETHERITE_PICKAXE);
    private final List<DropPlan> dropPlan = new ArrayList<>();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_CURSOR -> cursor;
                case DATA_VOLUME -> volume();
                case DATA_RUNNING -> running ? 1 : 0;
                case DATA_WIDTH -> areaWidth;
                case DATA_DEPTH -> areaDepth;
                case DATA_HEIGHT -> areaHeight;
                case DATA_MINED -> blocksMined;
                case DATA_SKIPPED -> blocksSkipped;
                case DATA_ITEMS -> itemsCollected;
                case DATA_TOOL_SLOTS -> toolSlots;
                case DATA_MOTORS -> motorCount;
                case DATA_ORE_TOTAL -> oreCandidates.size();
                case DATA_ORE_SELECTED -> selectedOres.size();
                case DATA_COST -> cachedCost;
                case DATA_PICKAXES -> cachedStats.tools();
                case DATA_EFFICIENCY -> cachedStats.efficiency();
                case DATA_FORTUNE -> cachedStats.fortune();
                case DATA_SILK -> cachedStats.silkTouch();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative; the client menu keeps its own synced copy.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private MiningPlan plan = new MiningPlan(
            LargeQuarryBalance.DEFAULT_WIDTH,
            LargeQuarryBalance.DEFAULT_DEPTH,
            LargeQuarryBalance.DEFAULT_HEIGHT);
    private int areaWidth = LargeQuarryBalance.DEFAULT_WIDTH;
    private int areaDepth = LargeQuarryBalance.DEFAULT_DEPTH;
    private int areaHeight = LargeQuarryBalance.DEFAULT_HEIGHT;
    private int cursor;
    private boolean running;
    private int blocksMined;
    private int blocksSkipped;
    private int itemsCollected;
    private QuarryStatus status = QuarryStatus.STOPPED;
    private double opsAccumulator;
    private int pauseRetryCooldown;
    private boolean inventoryChanged;
    private int appliedMaxReceive = -1;
    private int appliedCapacity = -1;
    private boolean registeredActive;
    private boolean areaOverride;
    @Nullable
    private FakePlayer miningFakePlayer;
    private final MachineWorkTracker workTracker = new MachineWorkTracker();

    /** Number of unlocked pickaxe arms. Only grows: a motor never takes an arm away again. */
    private int toolSlots = LargeQuarryBalance.BASE_TOOL_SLOTS;
    /** All motors ever installed; arms use the first ones, the rest raise the frame speed. */
    private int motorCount;
    /** Merged enchantment totals and the resulting FE cost; recomputed on tool changes only. */
    private PickaxeStats cachedStats = PickaxeStats.EMPTY;
    private int cachedCost = LargeQuarryBalance.ENERGY_PER_BLOCK;
    private boolean toolStatsDirty = true;
    /** Block ids the player selected; an empty set means "mine nothing". */
    private final Set<ResourceLocation> selectedOres = new HashSet<>();
    private boolean selectionInitialized;
    private List<Block> oreCandidates = List.of();
    private boolean oreIndexDirty = true;
    /** Display copy for the client GUI (the server selection is never trusted from the client). */
    private List<String> clientOreIds = List.of();
    private int clientOreTotal;
    private int clientToolSlots = LargeQuarryBalance.BASE_TOOL_SLOTS;
    private int clientPickaxes;
    private int clientEfficiency;
    private int clientFortune;
    private int clientSilk;
    private int clientCost = LargeQuarryBalance.ENERGY_PER_BLOCK;
    /** Test hook: how often the server asked clients to re-read the ore selection. */
    private int clientSyncRequests;

    public LargeQuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_QUARRY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  LargeQuarryBlockEntity quarry) {
        if (level instanceof ServerLevel serverLevel) {
            quarry.tick(serverLevel);
        }
    }

    // ------------------------------------------------------------------
    // Ticking / mining
    // ------------------------------------------------------------------

    private void tick(ServerLevel level) {
        // One shared per-tick budget for every quarry type, so total block work stays bounded.
        MiningScheduler.beginTick(level.getGameTime(), Config.globalOperationsPerTick());

        boolean changed = applyConfiguredArea();
        changed |= refreshCachedState();

        if (!running) {
            changed |= setStatus(cursor >= volume() ? QuarryStatus.COMPLETE : QuarryStatus.STOPPED);
        } else {
            changed |= mineTick(level);
        }

        setRegistered(running && status == QuarryStatus.RUNNING);

        if (changed) {
            setChanged();
        }
    }

    /** Applies the FE configuration and recomputes the cached values only when they are stale. */
    private boolean refreshCachedState() {
        boolean changed = false;
        int capacity = UpgradeMath.scaledCapacity(Config.largeQuarryCapacity(), getBatteryCount());
        if (capacity != appliedCapacity) {
            appliedCapacity = capacity;
            if (energy.getMaxEnergyStored() != capacity) {
                energy.setCapacity(capacity);
                changed = true;
            }
        }
        int maxReceive = Config.largeQuarryMaxReceive();
        if (maxReceive != appliedMaxReceive) {
            appliedMaxReceive = maxReceive;
            energy.setMaxReceive(maxReceive);
        }
        if (oreIndexDirty) {
            refreshOreIndex();
            changed = true;
        }
        if (toolStatsDirty) {
            refreshToolStats();
            changed = true;
        }
        return changed;
    }

    private void setRegistered(boolean active) {
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.LargeQuarryBlock.LIT)
                    && st.getValue(com.industrialcore.block.LargeQuarryBlock.LIT) != active) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.LargeQuarryBlock.LIT, active),
                        Block.UPDATE_CLIENTS);
            }
        }
        if (registeredActive == active) {
            return;
        }
        registeredActive = active;
        if (active) {
            MiningScheduler.onMachineStarted();
        } else {
            MiningScheduler.onMachineStopped();
        }
    }

    private boolean mineTick(ServerLevel level) {
        boolean changed = false;
        if (cursor >= volume()) {
            running = false;
            return setStatus(QuarryStatus.COMPLETE);
        }
        if (cachedStats.tools() <= 0) {
            return pause(QuarryStatus.PAUSED_NO_TOOLS);
        }
        if (selectedOres.isEmpty()) {
            return pause(QuarryStatus.PAUSED_NO_SELECTION);
        }

        // A blocked attempt rolls the loot table, so retries are backed off. Taking anything out
        // of the output clears the wait immediately.
        if (pauseRetryCooldown > 0) {
            if (!inventoryChanged) {
                pauseRetryCooldown--;
                return false;
            }
            inventoryChanged = false;
        }

        Direction facing = getBlockState().getValue(LargeQuarryBlock.FACING);
        BlockPos firstTarget = targetFor(cursor, facing);
        if (!level.isLoaded(firstTarget)) {
            // Never force-load chunks: the machine waits until a player loads the area again.
            return pause(QuarryStatus.PAUSED_CHUNK_UNLOADED);
        }
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        int miningCost = UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(cachedCost, getBatteryCount()), ownerLvl);
        BlockState firstState = level.getBlockState(firstTarget);
        boolean firstNeedsWork = isTarget(firstState) && isMineable(firstState, firstTarget);
        if (firstNeedsWork && energy.getEnergyStored() < miningCost) {
            return pause(QuarryStatus.PAUSED_NO_ENERGY);
        }

        double hardCap = Math.max(1.0D, Config.largeQuarryMaxOperationsPerTick());
        double baseOps = LargeQuarryBalance.operationsPerTick(
                Config.largeQuarryOperationsPerTick(), hardCap, cachedStats, motorCount);
        double ops = UpgradeMath.applyMasterySpeed(baseOps, hardCap, ownerLvl);
        double bankedBefore = opsAccumulator;
        opsAccumulator = Math.min(opsAccumulator + ops, hardCap);
        int requested = (int) Math.floor(opsAccumulator + 1.0E-9D);
        if (requested <= 0) {
            return false;
        }
        int granted = MiningScheduler.claim(Math.min(requested, (int) Math.floor(hardCap)));
        if (granted <= 0) {
            // No shared budget this tick: keep the work that was not granted, never lose it.
            opsAccumulator = bankedBefore;
            return false;
        }
        opsAccumulator = Math.max(0.0D, opsAccumulator - granted);

        QuarryStatus nextStatus = QuarryStatus.RUNNING;
        int processed = 0;
        boolean consumedEnergyThisTick = false;
        boolean seeded = true;
        BlockPos target = firstTarget;
        BlockState state = firstState;

        while (processed < granted) {
            if (cursor >= volume()) {
                nextStatus = QuarryStatus.COMPLETE;
                break;
            }
            if (!seeded) {
                target = targetFor(cursor, facing);
                if (!level.isLoaded(target)) {
                    nextStatus = QuarryStatus.PAUSED_CHUNK_UNLOADED;
                    break;
                }
                state = level.getBlockState(target);
            }
            seeded = false;

            boolean skippable = isSkippable(state);
            boolean targetOre = isTarget(state);
            if (skippable || !targetOre || !isMineable(state, target)) {
                // Air, fluids, non ore blocks and protected blocks are stepped over. This is what
                // keeps stone, deepslate and dirt intact (and why scanning them costs no FE).
                cursor++;
                if (!skippable) {
                    blocksSkipped++;
                }
                processed++;
                changed = true;
                continue;
            }

            dropPlan.clear();
            buildDrops(level, target, state, dropPlan);
            if (dropPlan.isEmpty()) {
                // Nothing recoverable (for example a modded ore whose loot table is empty).
                cursor++;
                blocksSkipped++;
                processed++;
                changed = true;
                continue;
            }
            if (energy.getEnergyStored() < miningCost) {
                nextStatus = QuarryStatus.PAUSED_NO_ENERGY;
                break;
            }
            if (!canStore(dropPlan)) {
                // Do not touch the block: nothing is destroyed and nothing is lost.
                nextStatus = QuarryStatus.PAUSED_INVENTORY_FULL;
                break;
            }

            energy.consume(miningCost);
            consumedEnergyThisTick = true;
            MiningHarvest.clearContainerContents(level, target);
            level.removeBlock(target, false);
            int stored = store(dropPlan);
            itemsCollected += stored;

            cursor++;
            blocksMined++;
            processed++;
            changed = true;
        }

        if (consumedEnergyThisTick) {
            workTracker.recordActiveTick(level, getMachineProgressId());
        }
        if (nextStatus == QuarryStatus.COMPLETE) {
            running = false;
        }
        if (nextStatus == QuarryStatus.PAUSED_INVENTORY_FULL
                || nextStatus == QuarryStatus.PAUSED_NO_ENERGY) {
            pauseRetryCooldown = Math.max(1, Math.min(PAUSE_RETRY_TICKS,
                    Config.inventoryRetryCooldownTicks()));
            inventoryChanged = false;
        }
        if (nextStatus != QuarryStatus.RUNNING) {
            // Prevent a burst of queued operations when the machine resumes.
            opsAccumulator = 0.0D;
        }
        changed |= setStatus(nextStatus);
        return changed;
    }

    /** Enters a paused state without claiming shared work units. */
    private boolean pause(QuarryStatus next) {
        opsAccumulator = 0.0D;
        return setStatus(next);
    }

    /**
     * Builds the drops of one ore block.
     *
     * <p>Silk Touch wins for the block itself, Fortune then multiplies the number of whole blocks.
     * Without Silk Touch the ordinary loot roll is used and Fortune multiplies its items. The two
     * effects are never applied to the same drop twice.</p>
     */
    private void buildDrops(ServerLevel level, BlockPos pos, BlockState state, List<DropPlan> out) {
        if (cachedStats.hasSilkTouch()) {
            ItemStack blockItem = new ItemStack(state.getBlock().asItem());
            if (!blockItem.isEmpty()) {
                int count = LargeQuarryBalance.silkBlockCount(cachedStats);
                if (count > 0) {
                    out.add(new DropPlan(blockItem, count));
                }
                return;
            }
            // A modded ore without an item cannot be recovered as a block: fall back to its drops.
        }

        FakePlayer fakePlayer = miningFakePlayer;
        if (fakePlayer == null || fakePlayer.level() != level) {
            fakePlayer = FakePlayerFactory.getMinecraft(level);
            miningFakePlayer = fakePlayer;
        }
        List<ItemStack> loot = new ArrayList<>(4);
        MiningHarvest.collectDrops(level, pos, state, fakePlayer, baseTool, loot);
        int multiplier = LargeQuarryBalance.rawDropMultiplier(cachedStats);
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            int count = LargeQuarryBalance.saturatedCount(stack.getCount(), multiplier);
            if (count > 0) {
                out.add(new DropPlan(stack.copyWithCount(1), count));
            }
        }
    }

    /** Simulates every planned stack; a plan that does not fit entirely is never applied. */
    private boolean canStore(List<DropPlan> planToStore) {
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            storageSimulation.setStackInSlot(slot, inventory.getStackInSlot(OUTPUT_START + slot).copy());
        }
        for (DropPlan plan : planToStore) {
            if (!simulateInsert(storageSimulation, plan.template(), plan.count())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Inserts a checked plan and returns how many items were stored. A remainder (which cannot
     * happen after {@link #canStore}) is dropped at the machine instead of being deleted.
     */
    private int store(List<DropPlan> planToStore) {
        int stored = 0;
        int perStack = 0;
        for (DropPlan plan : planToStore) {
            perStack = Math.max(1, Math.min(64, plan.template().getMaxStackSize()));
            long remaining = plan.count();
            while (remaining > 0L) {
                int chunk = (int) Math.min(remaining, perStack);
                ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory,
                        plan.template().copyWithCount(chunk), false);
                stored += chunk - rest.getCount();
                if (!rest.isEmpty() && level != null) {
                    Block.popResource(level, worldPosition, rest);
                }
                remaining -= chunk;
            }
        }
        dropPlan.clear();
        return stored;
    }

    /**
     * Inserts up to {@code count} items into a scratch handler in stack sized steps.
     *
     * <p>The loop is bounded by the free space of the handler, not by {@code count}: an absurd drop
     * count (a very high merged Fortune level) fails after the handler is full instead of looping
     * millions of times.</p>
     */
    private static boolean simulateInsert(ItemStackHandler handler, ItemStack template, int count) {
        long remaining = Math.max(0, count);
        int perStack = Math.max(1, Math.min(64, template.getMaxStackSize()));
        while (remaining > 0L) {
            int chunk = (int) Math.min(remaining, perStack);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(
                    handler, template.copyWithCount(chunk), false);
            if (!rest.isEmpty()) {
                return false;
            }
            remaining -= chunk;
        }
        return true;
    }

    private boolean isSkippable(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    /** True when the block is an ore from {@code c:ores} and the player selected it. */
    private boolean isTarget(BlockState state) {
        if (!state.is(ModTags.ORES)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && selectedOres.contains(id);
    }

    private boolean isMineable(BlockState state, BlockPos pos) {
        if (state.is(ModTags.QUARRY_UNBREAKABLE)) {
            return false;
        }
        if (state.is(ModBlocks.QUARRY.get()) || state.is(ModBlocks.LARGE_QUARRY.get())) {
            return false;
        }
        // Bedrock, barriers and other indestructible blocks report a negative hardness.
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    // ------------------------------------------------------------------
    // Ore selection
    // ------------------------------------------------------------------

    /** All ore blocks of {@code c:ores}, in a stable order shared by server and client. */
    public static List<Block> computeOreCandidates() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().is(ModTags.ORES)) {
                blocks.add(block);
            }
        }
        blocks.sort(Comparator.comparing(block -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            return id == null ? "" : id.toString();
        }));
        return List.copyOf(blocks);
    }

    /**
     * Recomputes the candidate list and, on the first call of a new machine, selects every ore so
     * a freshly placed quarry works without any GUI interaction.
     */
    public void refreshOreIndex() {
        oreCandidates = computeOreCandidates();
        if (!selectionInitialized) {
            for (Block block : oreCandidates) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                if (id != null) {
                    selectedOres.add(id);
                }
            }
            selectionInitialized = true;
            // Fresh machine: setChanged() only marks the chunk dirty and sends nothing, so
            // without this push the client copy would keep its empty display selection and the
            // picker would show "nothing selected" until the first click made every other cell
            // jump to selected at once.
            selectionChanged();
        }
        // Drop ids whose block disappeared (removed mod / datapack change) so the set stays valid.
        selectedOres.removeIf(id -> !BuiltInRegistries.BLOCK.containsKey(id));
        oreIndexDirty = false;
    }

    /** Marks the cached candidate list stale; the next tick (or GUI open) rebuilds it. */
    public void invalidateOreIndex() {
        oreIndexDirty = true;
    }

    /** Server: toggle one candidate by its index in {@link #getOreCandidates()}. */
    public boolean toggleOre(int index) {
        if (level == null || level.isClientSide) {
            return false;
        }
        refreshOreIndex();
        if (index < 0 || index >= oreCandidates.size()) {
            return false;
        }
        Block block = oreCandidates.get(index);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return false;
        }
        if (!selectedOres.remove(id)) {
            selectedOres.add(id);
        }
        selectionChanged();
        return true;
    }

    /** Server: select every candidate ore. */
    public void selectAllOres() {
        if (level == null || level.isClientSide) {
            return;
        }
        refreshOreIndex();
        for (Block block : oreCandidates) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id != null) {
                selectedOres.add(id);
            }
        }
        selectionChanged();
    }

    /** Server: clear the selection. The machine pauses instead of mining unselected ores. */
    public void clearOres() {
        if (level == null || level.isClientSide) {
            return;
        }
        selectedOres.clear();
        // An empty selection is a deliberate choice, not "not initialised yet": without this flag
        // the next tick would re-select every ore and start mining again.
        selectionInitialized = true;
        selectionChanged();
    }

    private void selectionChanged() {
        clientSyncRequests++;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public List<Block> getOreCandidates() {
        if (oreIndexDirty) {
            refreshOreIndex();
        }
        return oreCandidates;
    }

    public boolean isOreSelected(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && selectedOres.contains(id);
    }

    /** Sorted ids for the client GUI. */
    public List<String> getSelectedOreIds() {
        List<String> ids = new ArrayList<>(selectedOres.size());
        for (ResourceLocation id : selectedOres) {
            ids.add(id.toString());
        }
        ids.sort(String::compareTo);
        return ids;
    }

    // ------------------------------------------------------------------
    // Tools and upgrades
    // ------------------------------------------------------------------

    /**
     * Recomputes the merged enchantment totals and the per-block FE cost.
     *
     * <p>The loop bound and the slot numbers come from {@link LargeQuarrySlots}, never from a bare
     * constant plus a saved count: the crash this replaced was {@code Slot 18 not in valid range}
     * because a Phase 20 inventory had been restored with 18 slots while the pickaxe range started
     * at 18. {@link #ensureLayout()} keeps the array at the layout size, so an out-of-range index
     * here would be a real bug instead of a silent difference between two definitions.</p>
     */
    private void refreshToolStats() {
        ensureLayout();
        PickaxeStats stats = PickaxeStats.EMPTY;
        int installed = 0;
        int active = LargeQuarrySlots.activeToolSlots(toolSlots);
        for (int index = 0; index < active; index++) {
            ItemStack stack = inventory.getStackInSlot(LargeQuarrySlots.toolSlot(index));
            if (stack.isEmpty()) {
                continue;
            }
            installed++;
            stats = stats.plus(
                    enchantLevel(stack, Enchantments.EFFICIENCY),
                    enchantLevel(stack, Enchantments.FORTUNE),
                    enchantLevel(stack, Enchantments.SILK_TOUCH));
        }
        cachedStats = stats;
        cachedCost = LargeQuarryBalance.miningCost(
                Config.largeQuarryEnergyPerBlock(), stats, installed);
        toolStatsDirty = false;
    }

    /** Merged level of one enchantment on a stack; unknown or missing enchantments count as zero. */
    private static int enchantLevel(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
        int total = 0;
        var enchantments = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            boolean matches = entry.getKey().unwrapKey()
                    .map(found -> found.equals(key))
                    .orElse(false);
            if (matches) {
                total = PickaxeStats.saturatingAdd(total, entry.getValue());
            }
        }
        return total;
    }

    /**
     * Installs the motors sitting in the upgrade slot.
     *
     * <p>Phase 81: the whole stack is consumed at once, so pushing in a full stack of 64 takes
     * effect in one tick. The first motors unlock pickaxe arms up to the eight-arm layout limit;
     * every further motor raises the frame speed (see
     * {@link LargeQuarryBalance#motorSpeedMultiplier(int)}), so the slot never stops being useful.
     * Motors are permanent: an arm is never taken away again, so exchanging an upgrade can never
     * hide (and thereby void) an installed pickaxe.</p>
     */
    private void tryConsumeUpgrade() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        ItemStack stack = inventory.getStackInSlot(UPGRADE_SLOT);
        if (stack.isEmpty() || !stack.is(ModItems.MOTOR.get())) {
            return;
        }
        int limit = workTracker.upgradeLimit(sl, getMachineProgressId());
        int room = Math.max(0, limit - motorCount);
        if (room <= 0) {
            return;
        }
        int install = Math.min(room, stack.getCount());
        int arms = Math.min(install, LargeQuarryBalance.MAX_TOOL_SLOTS - toolSlots);
        if (arms > 0) {
            toolSlots = LargeQuarryBalance.clampToolSlots(toolSlots + arms);
            toolStatsDirty = true;
        }
        motorCount = UpgradeMath.saturatingAdd(motorCount, install, 1_000_000);
        stack.shrink(install);
        if (stack.isEmpty()) {
            inventory.setStackInSlot(UPGRADE_SLOT, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ------------------------------------------------------------------
    // Area
    // ------------------------------------------------------------------

    private boolean applyConfiguredArea() {
        if (areaOverride) {
            return false;
        }
        int width = LargeQuarryBalance.clampDimension(Config.largeQuarryWidth());
        int depth = LargeQuarryBalance.clampDimension(Config.largeQuarryDepth());
        int height = LargeQuarryBalance.clampDimension(Config.largeQuarryHeight());
        if (level != null) {
            height = Math.min(height, QuarryFrame.maxHeight(
                    worldPosition.getY(), level.getMinBuildHeight()));
        }
        if (width == areaWidth && depth == areaDepth && height == areaHeight) {
            return false;
        }
        areaWidth = width;
        areaDepth = depth;
        areaHeight = height;
        plan = new MiningPlan(width, depth, height);
        cursor = Mth.clamp(cursor, 0, plan.volume());
        return true;
    }

    public void setRunning(boolean value) {
        if (value) {
            if (cursor >= volume()) {
                // Starting again after completion restarts the area but keeps the collected loot.
                cursor = 0;
                blocksMined = 0;
                blocksSkipped = 0;
                itemsCollected = 0;
            }
            running = true;
            status = QuarryStatus.RUNNING;
            opsAccumulator = 0.0D;
            pauseRetryCooldown = 0;
            inventoryChanged = false;
            // The machine may have been placed before the tool slots were read from NBT.
            toolStatsDirty = true;
        } else {
            running = false;
            opsAccumulator = 0.0D;
            pauseRetryCooldown = 0;
            inventoryChanged = false;
            status = cursor >= volume() ? QuarryStatus.COMPLETE : QuarryStatus.STOPPED;
        }
        setChanged();
    }

    public void toggleRunning() {
        setRunning(!running);
    }

    public BlockPos targetFor(int index) {
        return targetFor(index, getBlockState().getValue(LargeQuarryBlock.FACING));
    }

    private BlockPos targetFor(int index, Direction facing) {
        QuarryFrame.Point point = QuarryFrame.target(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                facing.get3DDataValue(), plan, index);
        return new BlockPos(point.x(), point.y(), point.z());
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.large_quarry");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        // The GUI always opens against a current candidate list, so the controls match the tags.
        refreshOreIndex();
        refreshToolStats();
        return new LargeQuarryMenu(containerId, playerInventory, this, dataAccess);
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
        return "large_quarry";
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get())
                ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** Output-only view used by pipes, distributors and hoppers. */
    public IItemHandler getOutputHandler() {
        return outputView;
    }

    public LargeQuarryItemHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_WIDTH, areaWidth);
        tag.putInt(TAG_DEPTH, areaDepth);
        tag.putInt(TAG_HEIGHT, areaHeight);
        tag.putInt(TAG_CURSOR, cursor);
        tag.putBoolean(TAG_RUNNING, running);
        tag.putInt(TAG_MINED, blocksMined);
        tag.putInt(TAG_SKIPPED, blocksSkipped);
        tag.putInt(TAG_ITEMS, itemsCollected);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.putInt(TAG_TOOL_SLOTS, toolSlots);
        tag.putInt(TAG_MOTORS, motorCount);
        ListTag ores = new ListTag();
        for (ResourceLocation id : selectedOres) {
            ores.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_SELECTED_ORES, ores);
        tag.putBoolean(TAG_SELECTION_SET, selectionInitialized);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CLIENT)) {
            // Display payload (chunk sync or block update), recognised by its shape instead of
            // by the level: a client block entity can receive this while its level is still
            // null, and the old level-based check then fell into the save branch below, which
            // left the picker with an empty display selection that no later update repaired.
            applyDisplayTag(tag.getCompound(TAG_CLIENT));
            return;
        }
        if (level != null && level.isClientSide) {
            // A save-shaped tag never belongs to a client copy: applying it would overwrite the
            // display state with half-parsed authoritative fields.
            IndustrialCore.LOGGER.error(
                    "Large quarry at {} received a save tag on the client; ignoring it",
                    worldPosition);
            return;
        }
        areaWidth = LargeQuarryBalance.clampDimension(
                tag.contains(TAG_WIDTH) ? tag.getInt(TAG_WIDTH) : areaWidth);
        areaDepth = LargeQuarryBalance.clampDimension(
                tag.contains(TAG_DEPTH) ? tag.getInt(TAG_DEPTH) : areaDepth);
        areaHeight = LargeQuarryBalance.clampDimension(
                tag.contains(TAG_HEIGHT) ? tag.getInt(TAG_HEIGHT) : areaHeight);
        plan = new MiningPlan(areaWidth, areaDepth, areaHeight);
        cursor = Mth.clamp(tag.getInt(TAG_CURSOR), 0, plan.volume());
        running = tag.getBoolean(TAG_RUNNING);
        blocksMined = Math.max(0, tag.getInt(TAG_MINED));
        blocksSkipped = Math.max(0, tag.getInt(TAG_SKIPPED));
        itemsCollected = Math.max(0, tag.getInt(TAG_ITEMS));
        QuarryStatus[] values = QuarryStatus.values();
        status = values[Mth.clamp(tag.getInt(TAG_STATUS), 0, values.length - 1)];
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
        if (tag.contains(TAG_INVENTORY)) {
            // The handler keeps its own (code owned) size while the saved items are placed; a Phase
            // 20 save therefore loads losslessly into the current layout instead of shrinking it.
            inventory.beginLoad();
            try {
                inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
            } finally {
                inventory.endLoad();
            }
        }
        ensureLayout();
        toolSlots = LargeQuarrySlots.activeToolSlots(
                tag.contains(TAG_TOOL_SLOTS)
                        ? tag.getInt(TAG_TOOL_SLOTS) : LargeQuarryBalance.BASE_TOOL_SLOTS);
        motorCount = UpgradeMath.clampCount(tag.getInt(TAG_MOTORS), 1_000_000);
        workTracker.load(tag);
        // A motor that was saved in the upgrade slot without being consumed (for example when the
        // world was closed between the insert and the tick) still unlocks its arm exactly once.
        tryConsumeUpgrade();
        selectedOres.clear();
        ListTag ores = tag.getList(TAG_SELECTED_ORES, Tag.TAG_STRING);
        for (int index = 0; index < ores.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(ores.getString(index));
            if (id != null) {
                selectedOres.add(id);
            }
        }
        selectionInitialized = tag.getBoolean(TAG_SELECTION_SET);
        oreIndexDirty = true;
        toolStatsDirty = true;
        pauseRetryCooldown = 0;
        inventoryChanged = false;
    }

    /**
     * Applies the display tag the server sends with a block update: the selected ore ids and the
     * tool summary for the GUI. Dispatched by tag shape, so it works even when this block entity
     * has no level yet, and it never touches the authoritative inventory, cursor or selection.
     */
    private void applyDisplayTag(CompoundTag client) {
        List<String> ids = new ArrayList<>();
        ListTag list = client.getList(TAG_CLIENT_ORES, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            ids.add(list.getString(index));
        }
        clientOreIds = List.copyOf(ids);
        clientOreTotal = client.getInt(TAG_CLIENT_ORE_TOTAL);
        clientToolSlots = LargeQuarryBalance.clampToolSlots(
                client.getInt(TAG_CLIENT_TOOL_SLOTS));
        clientPickaxes = Math.max(0, client.getInt(TAG_CLIENT_TOOLS));
        clientEfficiency = Math.max(0, client.getInt(TAG_CLIENT_EFFICIENCY));
        clientFortune = Math.max(0, client.getInt(TAG_CLIENT_FORTUNE));
        clientSilk = Math.max(0, client.getInt(TAG_CLIENT_SILK));
        clientCost = Math.max(0, client.getInt(TAG_CLIENT_COST));
    }

    /**
     * Display tag: selection ids, tool summary and the per-block FE cost.
     *
     * <p>Only these values are sent; the inventory, the cursor and the selection logic stay server
     * authoritative, and the payload is rebuilt only when the machine sends a block update.</p>
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag client = new CompoundTag();
        ListTag ores = new ListTag();
        for (ResourceLocation id : selectedOres) {
            ores.add(StringTag.valueOf(id.toString()));
        }
        client.put(TAG_CLIENT_ORES, ores);
        client.putInt(TAG_CLIENT_ORE_TOTAL, oreCandidates.size());
        client.putInt(TAG_CLIENT_TOOL_SLOTS, toolSlots);
        client.putInt(TAG_CLIENT_TOOLS, cachedStats.tools());
        client.putInt(TAG_CLIENT_EFFICIENCY, cachedStats.efficiency());
        client.putInt(TAG_CLIENT_FORTUNE, cachedStats.fortune());
        client.putInt(TAG_CLIENT_SILK, cachedStats.silkTouch());
        client.putInt(TAG_CLIENT_COST, cachedCost);
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_CLIENT, client);
        return tag;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        miningFakePlayer = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        setRegistered(false);
        miningFakePlayer = null;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // Never leave a stale entry in the global budget divisor.
        setRegistered(false);
        miningFakePlayer = null;
    }

    // ------------------------------------------------------------------
    // Accessors (GUI, GameTests, integrations)
    // ------------------------------------------------------------------

    public QuarryStatus getStatus() {
        return status;
    }

    private boolean setStatus(QuarryStatus next) {
        if (status == next) {
            return false;
        }
        status = next;
        return true;
    }

    /** Installed motors, including those beyond the eight-arm layout limit. */
    public int getMotorCount() {
        return motorCount;
    }

    public boolean isRunning() {
        return running;
    }

    public int getCursor() {
        return cursor;
    }

    public int volume() {
        return plan.volume();
    }

    public int getVolume() {
        return volume();
    }

    public int getAreaWidth() {
        return areaWidth;
    }

    public int getAreaDepth() {
        return areaDepth;
    }

    public int getAreaHeight() {
        return areaHeight;
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public int getBlocksSkipped() {
        return blocksSkipped;
    }

    public int getItemsCollected() {
        return itemsCollected;
    }

    public int getToolSlotCount() {
        return toolSlots;
    }

    public PickaxeStats getPickaxeStats() {
        if (toolStatsDirty) {
            refreshToolStats();
        }
        return cachedStats;
    }

    public int getMiningCost() {
        if (toolStatsDirty) {
            refreshToolStats();
        }
        return cachedCost;
    }

    /** Client-side display copy of the selection (empty on the server until requested). */
    public List<String> getClientOreIds() {
        return clientOreIds;
    }

    public int getClientOreTotal() {
        return clientOreTotal;
    }

    public int getClientToolSlots() {
        return clientToolSlots;
    }

    public int getClientPickaxes() {
        return clientPickaxes;
    }

    public int getClientEfficiency() {
        return clientEfficiency;
    }

    public int getClientFortune() {
        return clientFortune;
    }

    public int getClientSilk() {
        return clientSilk;
    }

    public int getClientCost() {
        return clientCost;
    }

    /**
     * Test hook: number of client sync requests issued for the ore selection. A fresh machine
     * must push its automatic initial selection once and every player change must push again;
     * the packets themselves cannot be observed from a GameTest.
     */
    public int getClientSyncRequestsForTesting() {
        return clientSyncRequests;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public void setCursorForTesting(int value) {
        cursor = Mth.clamp(value, 0, plan.volume());
    }

    /** Test hook: force the merged tool cache to be rebuilt after inserting pickaxes directly. */
    public void markToolsChangedForTesting() {
        toolStatsDirty = true;
        refreshToolStats();
    }

    /** Test hook: select exactly one ore block, by registry id, without the GUI. */
    public void selectOnlyForTesting(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        selectedOres.clear();
        if (id != null) {
            selectedOres.add(id);
        }
        selectionInitialized = true;
        oreIndexDirty = true;
        setChanged();
    }

    /**
     * Test/integration hook: overrides the configured area of this machine only, so a GameTest can
     * exercise the mining loop with a handful of blocks instead of the configured 245 positions.
     * Never called by the GUI or the network.
     */
    public void setAreaForTesting(int width, int depth, int height) {
        areaOverride = true;
        areaWidth = LargeQuarryBalance.clampDimension(width);
        areaDepth = LargeQuarryBalance.clampDimension(depth);
        areaHeight = LargeQuarryBalance.clampDimension(height);
        plan = new MiningPlan(areaWidth, areaDepth, areaHeight);
        cursor = 0;
        blocksMined = 0;
        blocksSkipped = 0;
        itemsCollected = 0;
        running = false;
        opsAccumulator = 0.0D;
        pauseRetryCooldown = 0;
        inventoryChanged = false;
        status = QuarryStatus.STOPPED;
        setChanged();
    }

    /**
     * Internal storage of the machine. The handler itself accepts inserts (the machine has to
     * store its own drops and hold the pickaxes); the GUI slot wrappers, the automation view
     * {@link OutputHandler} and the filters keep players and pipes out of the wrong slots.
     */
    /**
     * Fixed-size inventory of the machine.
     *
     * <p>{@link ItemStackHandler#deserializeNBT(net.minecraft.core.HolderLookup.Provider,
     * CompoundTag)} restores {@code Size} from the save, which silently replaces the array the slot
     * constants address. That is how a Phase 20 world (18 outputs, no pickaxes) crashed the Phase 22
     * machine: the array came back with 18 entries while {@link LargeQuarrySlots#TOOL_START} was 18.
     * This handler never takes its size from data - it keeps the layout size and migrates the saved
     * items into it.</p>
     */
    public static final class LargeQuarryItemHandler extends ItemStackHandler {
        private final LargeQuarryBlockEntity owner;
        /** Set while a save is being applied, so loading does not fire gameplay side effects. */
        private boolean loading;

        LargeQuarryItemHandler(LargeQuarryBlockEntity owner) {
            super(LargeQuarrySlots.TOTAL_SLOTS);
            this.owner = owner;
        }

        void beginLoad() {
            loading = true;
        }

        void endLoad() {
            loading = false;
        }

        /**
         * Places the saved stacks into the current layout. The saved {@code Size} is deliberately
         * ignored: it describes the machine that wrote the world, not the machine reading it.
         */
        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            if (getSlots() != LargeQuarrySlots.TOTAL_SLOTS) {
                // Repair an array that was already resized by an older build before this fix.
                setSize(LargeQuarrySlots.TOTAL_SLOTS);
            }
            for (int slot = 0; slot < getSlots(); slot++) {
                setStackInSlot(slot, ItemStack.EMPTY);
            }
            int savedSize = tag.contains("Size") ? tag.getInt("Size") : getSlots();
            ListTag saved = tag.getList("Items", Tag.TAG_COMPOUND);
            int dropped = 0;
            for (int index = 0; index < saved.size(); index++) {
                CompoundTag entry = saved.getCompound(index);
                int slot = LargeQuarrySlots.migrateSlot(entry.getInt("Slot"), savedSize);
                if (!LargeQuarrySlots.isInRange(slot)) {
                    dropped++;
                    continue;
                }
                // NeoForge 1.21 stores the stack inline in the entry ("Item" is the older
                // wrapper); both shapes are accepted so an older save still loads.
                CompoundTag itemTag = entry.contains("Item", Tag.TAG_COMPOUND)
                        ? entry.getCompound("Item") : entry;
                ItemStack stack = ItemStack.parseOptional(provider, itemTag);
                if (!stack.isEmpty()) {
                    setStackInSlot(slot, stack);
                }
            }
            if (dropped > 0) {
                // Only a future layout that shrinks the inventory can hit this. Reported loudly
                // instead of being swallowed, so the migration stays visible.
                IndustrialCore.LOGGER.error(
                        "Large quarry at {} dropped {} saved stacks: the save uses slots outside "
                                + "the current layout (saved size {}, layout {})",
                        owner.getBlockPos(), dropped, savedSize, LargeQuarrySlots.TOTAL_SLOTS);
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (loading) {
                return;
            }
            owner.inventoryChanged = true;
            if (LargeQuarrySlots.isToolSlot(slot)) {
                owner.toolStatsDirty = true;
            }
            if (LargeQuarrySlots.isUpgradeSlot(slot)) {
                owner.tryConsumeUpgrade();
            }
            owner.setChanged();
        }
    }

    /** True when the inventory is really at the layout size; repairs and reports it otherwise. */
    private void ensureLayout() {
        if (inventory.getSlots() == LargeQuarrySlots.TOTAL_SLOTS) {
            return;
        }
        IndustrialCore.LOGGER.error("Large quarry at {} had {} slots instead of {}; repairing",
                worldPosition, inventory.getSlots(), LargeQuarrySlots.TOTAL_SLOTS);
        inventory.setSize(LargeQuarrySlots.TOTAL_SLOTS);
    }

    /**
     * Automation view: extraction only. Pipes, distributors and hoppers can drain the collected
     * ore, but they can never push items into this machine and never see the pickaxe slots.
     */
    private final class OutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < OUTPUT_SLOTS
                    ? inventory.getStackInSlot(OUTPUT_START + slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= 0 && slot < OUTPUT_SLOTS
                    ? inventory.extractItem(OUTPUT_START + slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < OUTPUT_SLOTS ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    /** One planned output: a template stack and how many items of it the block yields. */
    private record DropPlan(ItemStack template, int count) {}
}
