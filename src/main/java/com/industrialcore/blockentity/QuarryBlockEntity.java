package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.machine.MiningHarvest;
import com.industrialcore.machine.MiningPlan;
import com.industrialcore.machine.MiningProfiler;
import com.industrialcore.machine.MiningSync;
import com.industrialcore.machine.QuarryFrame;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.QuarryMenu;
import com.industrialcore.preview.PreviewData;
import com.industrialcore.preview.PreviewRegistry;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server authoritative quarry.
 *
 * <p>The machine stores a linear cursor into a deterministic {@link MiningPlan}. Every server
 * tick it advances the cursor, checks chunk loading, energy and inventory space, then breaks
 * the real block and stores its regular drops. The client only receives state for display.</p>
 */
public class QuarryBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int OUTPUT_START = 2;
    public static final int OUTPUT_SLOTS = 18;
    public static final int TOTAL_SLOTS = OUTPUT_START + OUTPUT_SLOTS;

    /** ContainerData indices shared with the GUI. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_CURSOR = 3;
    public static final int DATA_VOLUME = 4;
    public static final int DATA_MOTORS = 5;
    public static final int DATA_BATTERIES = 6;
    public static final int DATA_RUNNING = 7;
    public static final int DATA_PREVIEW = 8;
    public static final int DATA_WIDTH = 9;
    public static final int DATA_DEPTH = 10;
    public static final int DATA_HEIGHT = 11;
    public static final int DATA_MINED = 12;
    public static final int DATA_SKIPPED = 13;
    public static final int DATA_COUNT = 14;

    private static final String TAG_WIDTH = "RangeWidth";
    private static final String TAG_DEPTH = "RangeDepth";
    private static final String TAG_HEIGHT = "RangeHeight";
    private static final String TAG_CONFIGURED = "RangeConfigured";
    private static final String TAG_CURSOR = "Cursor";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_PREVIEW = "Preview";
    private static final String TAG_MOTORS = "Motors";
    private static final String TAG_BATTERIES = "Batteries";
    private static final String TAG_MINED = "Mined";
    private static final String TAG_SKIPPED = "Skipped";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_PREV_CURSOR = "PrevCursor";
    private static final String TAG_SYNC_INTERVAL = "SyncInterval";
    private static final String TAG_WORK = "Work";
    private static final String TAG_PREV_WORK = "PrevWork";

    private static final int DEFAULT_WIDTH = 3;
    private static final int DEFAULT_DEPTH = 3;
    private static final int DEFAULT_HEIGHT = 3;
    /** Upper bound accepted from NBT; matches the maximum the config permits. */
    private static final int HARD_UPGRADE_LIMIT = 10_000_000;

    private final QuarryItemHandler inventory = new QuarryItemHandler(TOTAL_SLOTS, this);
    private final RangedWrapper outputView = new RangedWrapper(inventory, OUTPUT_START, TOTAL_SLOTS);
    private final ItemStackHandler storageSimulation = new ItemStackHandler(OUTPUT_SLOTS);
    private final ModEnergyStorage energy = new ModEnergyStorage(20_000, 1_000);
    private final List<ItemStack> dropBuffer = new ArrayList<>();
    private final ItemStack miningTool = new ItemStack(Items.NETHERITE_PICKAXE);

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_CURSOR -> cursor;
                case DATA_VOLUME -> plan.volume();
                case DATA_MOTORS -> motorCount;
                case DATA_BATTERIES -> batteryCount;
                case DATA_RUNNING -> running ? 1 : 0;
                case DATA_PREVIEW -> preview ? 1 : 0;
                case DATA_WIDTH -> rangeWidth;
                case DATA_DEPTH -> rangeDepth;
                case DATA_HEIGHT -> rangeHeight;
                case DATA_MINED -> blocksMined;
                case DATA_SKIPPED -> blocksSkipped;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // All values are server authoritative; the client never writes them.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private MiningPlan plan = new MiningPlan(DEFAULT_WIDTH, DEFAULT_DEPTH, DEFAULT_HEIGHT);
    private int rangeWidth = DEFAULT_WIDTH;
    private int rangeDepth = DEFAULT_DEPTH;
    private int rangeHeight = DEFAULT_HEIGHT;
    private boolean rangeConfigured;
    private int cursor;
    private boolean running;
    private boolean preview;
    private int motorCount;
    private int batteryCount;
    private int blocksMined;
    private int blocksSkipped;
    private QuarryStatus status = QuarryStatus.NO_RANGE;
    private double opsAccumulator;
    /** Backoff counter for the (expensive) full-inventory retry; see {@link #mineTick}. */
    private int inventoryRetryCooldown;
    /** Set when an output slot changes so a full-inventory pause resumes immediately. */
    private boolean inventoryChanged;
    /** Derived speed; recomputed only when the motor count or the speed config changes. */
    private double cachedOpsPerTick = -1.0D;
    private int cachedOpsMotors = -1;
    private double cachedOpsBase = Double.NaN;
    private double cachedOpsPerMotor = Double.NaN;
    private double cachedOpsMax = Double.NaN;
    private int syncCooldown;
    /** Cursor of the previous visual update; the client interpolates from this value. */
    private int previousCursor;
    /** Within-block work fraction of the previous visual update (0..1). */
    private float previousWork;
    /** Cursor of the last visual update; -1 means "nothing synced yet". */
    private int lastSyncedCursor = -1;
    /** Within-block work fraction published by the last visual update (0..1). */
    private float lastSyncedWork;
    /** Current interval between visual updates (grows with the number of running machines). */
    private int visualInterval = 1;
    /** Factory wide interval before the per-machine speed limit is applied. */
    private int factoryVisualInterval = 1;
    private int visualIntervalCooldown;
    /** Battery count and receive limit already applied to the energy buffer. */
    private int appliedBatteryCount = -1;
    private int appliedMaxReceive = -1;
    /** Whether this machine currently contributes to {@link MiningScheduler#activeMachines()}. */
    private boolean registeredActive;
    @Nullable
    private FakePlayer miningFakePlayer;
    private final MachineWorkTracker workTracker = new MachineWorkTracker();

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARRY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity quarry) {
        if (level instanceof ServerLevel serverLevel) {
            quarry.tick(serverLevel);
        }
    }

    // ------------------------------------------------------------------
    // Ticking / mining
    // ------------------------------------------------------------------

    private void tick(ServerLevel level) {
        long startNanos = Config.enableMiningProfiling() ? System.nanoTime() : 0L;
        int minedBefore = blocksMined;
        boolean changed = false;

        // One shared per-tick budget for all quarries, so total work cannot grow without bound.
        MiningScheduler.beginTick(level.getGameTime(), Config.globalOperationsPerTick());

        // Upgrade items are consumed exactly once, on the server thread.
        if (consumeUpgrade(MOTOR_SLOT, true)) {
            changed = true;
        }
        if (consumeUpgrade(BATTERY_SLOT, false)) {
            changed = true;
        }

        // Only recompute the derived energy limits when their inputs actually changed.
        if (batteryCount != appliedBatteryCount) {
            appliedBatteryCount = batteryCount;
            int capacity = UpgradeMath.capacity(batteryCount,
                    Config.baseEnergyCapacity(), Config.energyPerBattery(), Config.maxEnergyCapacity());
            if (energy.getMaxEnergyStored() != capacity) {
                energy.setCapacity(capacity);
                changed = true;
            }
        }
        int maxReceive = Config.maxEnergyReceive();
        if (maxReceive != appliedMaxReceive) {
            appliedMaxReceive = maxReceive;
            energy.setMaxReceive(maxReceive);
        }

        if (!rangeConfigured || plan.volume() <= 0) {
            running = false;
            changed |= setStatus(QuarryStatus.NO_RANGE);
        } else if (!running) {
            changed |= setStatus(cursor >= plan.volume() ? QuarryStatus.COMPLETE : QuarryStatus.STOPPED);
        } else {
            changed |= mineTick(level);
        }

        // Only machines that really mine take part in the global budget divisor; paused or idle
        // machines must not slow down working machines in a large factory.
        setRegistered(running && status == QuarryStatus.RUNNING);

        if (changed) {
            setChanged();
        }

        // Visual sync: interval adapts to the number of running machines and nothing is sent
        // while the cursor does not move, so a factory of paused machines is silent. The factory
        // wide interval is recomputed every 20 ticks, while the per-machine speed limit is applied
        // every tick so a fast machine cannot cover dozens of blocks in one update (Phase 9).
        if (--visualIntervalCooldown <= 0) {
            visualIntervalCooldown = 20;
            factoryVisualInterval = MiningScheduler.visualSyncInterval(
                    Config.visualSyncMinIntervalTicks(), Config.visualSyncMaxIntervalTicks(),
                    Config.visualSyncFullRateMachines());
        }
        visualInterval = MiningSync.syncInterval(factoryVisualInterval, currentOperationsPerTick(),
                Config.visualMaxBlocksPerUpdate());
        if (++syncCooldown >= visualInterval) {
            syncCooldown = 0;
            if (running && cursor != lastSyncedCursor) {
                syncNow();
            }
        }

        if (startNanos != 0L) {
            MiningProfiler.record(System.nanoTime() - startNanos, blocksMined - minedBefore, running);
            MiningProfiler.maybeLog(level.getGameTime());
        }
    }

    private void setRegistered(boolean active) {
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.QuarryBlock.LIT)
                    && st.getValue(com.industrialcore.block.QuarryBlock.LIT) != active) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.QuarryBlock.LIT, active),
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
        int volume = plan.volume();
        if (cursor >= volume) {
            running = false;
            return setStatus(QuarryStatus.COMPLETE);
        }

        // A full-inventory retry runs the block loot table and simulates all 18 output slots,
        // so it is backed off instead of repeating every tick. Any inventory change (a pipe or a
        // player taking items out) clears the wait immediately, so machines stay responsive.
        if (inventoryRetryCooldown > 0) {
            if (!inventoryChanged) {
                inventoryRetryCooldown--;
                return false;
            }
            inventoryChanged = false;
        }

        // Cheap preconditions of the very next block are checked BEFORE any work is banked or
        // shared work units are claimed: a machine that cannot work right now must not advance its
        // displayed progress, take budget away from machines that can, or resend unchanged state.
        Direction facing = getBlockState().getValue(QuarryBlock.FACING);
        BlockPos firstTarget = targetFor(cursor, facing);
        if (!level.isLoaded(firstTarget)) {
            // Never force-load chunks: wait until a player loads the area again.
            return pauseWithoutClaiming(QuarryStatus.PAUSED_CHUNK_UNLOADED);
        }
        BlockState firstState = level.getBlockState(firstTarget);
        boolean skippable = firstState.isAir() || !firstState.getFluidState().isEmpty()
                || !isMineable(firstState, firstTarget);
        if (!skippable && energy.getEnergyStored() < currentEnergyPerBlock()) {
            return pauseWithoutClaiming(QuarryStatus.PAUSED_NO_ENERGY);
        }

        double ops = currentOperationsPerTick();
        double hardCap = Math.max(1.0D, Config.maxOpsPerTick());
        // Fractional work is only banked while the machine is actually able to work, so a paused
        // machine cannot show a half-finished block that is not being mined.
        double bankedBefore = opsAccumulator;
        opsAccumulator = Math.min(opsAccumulator + ops, hardCap);
        int budget = (int) Math.floor(opsAccumulator + 1.0E-9D);
        if (budget <= 0) {
            return false;
        }
        budget = Math.min(budget, (int) Math.floor(hardCap));
        if (budget <= 0) {
            return false;
        }

        // The global budget grants at most a fair share per tick. Work that is not granted stays
        // in the accumulator (bounded by the per-tick cap), so it is delayed, never lost or
        // duplicated, and a single tick can never process an unbounded number of blocks.
        int granted = MiningScheduler.claim(budget);
        if (granted <= 0) {
            // No shared budget this tick: do not keep the work that could not be used.
            opsAccumulator = bankedBefore;
            return false;
        }
        opsAccumulator = Math.max(0.0D, opsAccumulator - granted);
        int limit = granted;

        QuarryStatus nextStatus = QuarryStatus.RUNNING;
        int cost = currentEnergyPerBlock();
        int processed = 0;
        boolean consumedEnergyThisTick = false;

        // The preconditions above already resolved the first block. Reuse that target and state
        // instead of computing the frame and reading the block a second time: every mined block
        // used to pay for two identical lookups.
        boolean seeded = true;
        while (processed < limit) {
            if (cursor >= volume) {
                nextStatus = QuarryStatus.COMPLETE;
                break;
            }

            BlockPos target;
            BlockState state;
            if (seeded) {
                target = firstTarget;
                state = firstState;
                seeded = false;
            } else {
                target = targetFor(cursor, facing);
                if (!level.isLoaded(target)) {
                    // Never force-load chunks: wait until a player loads the area again.
                    nextStatus = QuarryStatus.PAUSED_CHUNK_UNLOADED;
                    break;
                }
                state = level.getBlockState(target);
            }
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                cursor++;
                processed++;
                changed = true;
                continue;
            }
            if (!isMineable(state, target)) {
                cursor++;
                blocksSkipped++;
                processed++;
                changed = true;
                continue;
            }

            if (energy.getEnergyStored() < cost) {
                nextStatus = QuarryStatus.PAUSED_NO_ENERGY;
                break;
            }

            dropBuffer.clear();
            collectDrops(level, target, state, dropBuffer);
            if (!canStore(dropBuffer)) {
                // Do not touch the block: nothing is destroyed and nothing is lost.
                nextStatus = QuarryStatus.PAUSED_INVENTORY_FULL;
                break;
            }

            energy.consume(cost);
            consumedEnergyThisTick = true;
            // The contents were copied by collectDrops; clear them now so removing the block
            // cannot drop a second copy (vanilla containers drop their contents on removal).
            MiningHarvest.clearContainerContents(level, target);
            level.removeBlock(target, false);
            storeDrops(level, dropBuffer);

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
        if (nextStatus == QuarryStatus.PAUSED_INVENTORY_FULL) {
            // Back off the expensive retry; cleared immediately by the next extraction.
            inventoryRetryCooldown = Math.max(1, Config.inventoryRetryCooldownTicks());
            inventoryChanged = false;
        }
        if (nextStatus != QuarryStatus.RUNNING) {
            // Prevent a burst of queued operations when the machine resumes.
            opsAccumulator = 0.0D;
        }
        boolean statusChanged = setStatus(nextStatus);
        changed |= statusChanged;
        if (statusChanged) {
            // Only actual transitions are sent: retries that change nothing stay silent.
            syncNow();
        }
        return changed;
    }

    /** Enters a paused state without claiming shared work units or resending unchanged state. */
    private boolean pauseWithoutClaiming(QuarryStatus next) {
        opsAccumulator = 0.0D;
        boolean statusChanged = setStatus(next);
        if (statusChanged) {
            syncNow();
        }
        return statusChanged;
    }

    /**
     * Derived mining speed. Recomputed only when the motor count or one of the speed config
     * values changes; every other tick reuses the cached result.
     */
    private double currentOperationsPerTick() {
        double base = Config.baseOpsPerTick();
        double perMotor = Config.opsPerMotor();
        double max = Config.maxOpsPerTick();
        if (motorCount != cachedOpsMotors || Double.compare(base, cachedOpsBase) != 0
                || Double.compare(perMotor, cachedOpsPerMotor) != 0
                || Double.compare(max, cachedOpsMax) != 0) {
            cachedOpsMotors = motorCount;
            cachedOpsBase = base;
            cachedOpsPerMotor = perMotor;
            cachedOpsMax = max;
            cachedOpsPerTick = UpgradeMath.operationsPerTick(motorCount, base, perMotor, max);
        }
        if (level instanceof ServerLevel sl) {
            return UpgradeMath.applyMasterySpeed(cachedOpsPerTick, max,
                    workTracker.ownerLevel(sl, getMachineProgressId()));
        }
        return cachedOpsPerTick;
    }

    private boolean isMineable(BlockState state, BlockPos pos) {
        if (state.is(ModTags.QUARRY_UNBREAKABLE)) {
            return false;
        }
        if (state.is(ModBlocks.QUARRY.get())) {
            return false;
        }
        // Bedrock, barriers and other indestructible blocks report a negative hardness.
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private void collectDrops(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> output) {
        // Cached: a lookup per broken block adds up in large factories.
        FakePlayer fakePlayer = miningFakePlayer;
        if (fakePlayer == null || fakePlayer.level() != level) {
            fakePlayer = FakePlayerFactory.getMinecraft(level);
            miningFakePlayer = fakePlayer;
        }
        MiningHarvest.collectDrops(level, pos, state, fakePlayer, miningTool, output);
    }

    private boolean canStore(List<ItemStack> drops) {
        return MiningHarvest.canStore(outputView, storageSimulation, drops);
    }

    private void storeDrops(ServerLevel level, List<ItemStack> drops) {
        MiningHarvest.store(level, worldPosition, outputView, drops);
    }

    /**
     * Installs the upgrade items sitting in a slot. The whole stack is taken at once (a full stack
     * of 64 takes effect in one tick instead of over 64 ticks); only the part that still fits under
     * the safety cap is consumed, so nothing is voided.
     */
    private boolean consumeUpgrade(int slot, boolean motor) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return false;
        }
        if (motor && !stack.is(ModItems.MOTOR.get())) {
            return false;
        }
        if (!motor && !stack.is(ModItems.BATTERY.get())) {
            return false;
        }
        int configLimit = Math.max(1, Config.maxUpgradeCount());
        int limit = level instanceof ServerLevel sl
                ? Math.min(configLimit, workTracker.upgradeLimit(sl, getMachineProgressId()))
                : configLimit;
        int stored = motor ? motorCount : batteryCount;
        int room = Math.max(0, limit - stored);
        if (room <= 0) {
            return false;
        }
        ItemStack extracted = inventory.extractItem(slot, Math.min(room, stack.getCount()), false);
        if (extracted.isEmpty()) {
            return false;
        }
        if (motor) {
            motorCount = UpgradeMath.saturatingAdd(motorCount, extracted.getCount(), limit);
        } else {
            batteryCount = UpgradeMath.saturatingAdd(batteryCount, extracted.getCount(), limit);
        }
        return true;
    }

    /**
     * FE per mined block after the battery efficiency. A battery no longer just enlarges the
     * buffer: a full stack cuts the cost of every block by 35 % (100 -> 65 FE at 64 batteries).
     */
    private int currentEnergyPerBlock() {
        int base = UpgradeMath.discountedEnergy(Config.energyPerBlock(), batteryCount);
        if (level instanceof ServerLevel sl) {
            return UpgradeMath.applyMasteryEnergy(base, workTracker.ownerLevel(sl, getMachineProgressId()));
        }
        return base;
    }

    // ------------------------------------------------------------------
    // Range
    // ------------------------------------------------------------------

    /**
     * Applies a new mining area. Always called on the server. The area starts one block in
     * front of the machine, extends to its right (width) and forward (depth), and downwards
     * from the machine's Y level (height).
     */
    public void applyRange(int width, int depth, int height) {
        int maxWidth = Math.max(1, Config.maxRangeWidth());
        int maxDepth = Math.max(1, Config.maxRangeDepth());
        int maxHeight = Math.max(1, Config.maxRangeHeight());

        int newWidth = Mth.clamp(width, 1, maxWidth);
        int newDepth = Mth.clamp(depth, 1, maxDepth);
        int newHeight = Mth.clamp(height, 1, maxHeight);

        if (level != null) {
            // The lowest buildable block is minBuildHeight, so the number of layers includes it.
            newHeight = Math.min(newHeight, QuarryFrame.maxHeight(
                    worldPosition.getY(), level.getMinBuildHeight()));
        }

        long maxVolume = Math.max(1, Config.maxVolume());
        while ((long) newWidth * newDepth * newHeight > maxVolume) {
            if (newHeight > 1 && newHeight >= newDepth && newHeight >= newWidth) {
                newHeight--;
            } else if (newDepth > 1 && newDepth >= newWidth) {
                newDepth--;
            } else if (newWidth > 1) {
                newWidth--;
            } else {
                break;
            }
        }

        rangeWidth = newWidth;
        rangeDepth = newDepth;
        rangeHeight = newHeight;
        rangeConfigured = true;
        plan = new MiningPlan(rangeWidth, rangeDepth, rangeHeight);
        cursor = 0;
        blocksMined = 0;
        blocksSkipped = 0;
        running = false;
        opsAccumulator = 0.0D;
        inventoryRetryCooldown = 0;
        inventoryChanged = false;
        previousWork = 0.0F;
        lastSyncedWork = 0.0F;
        status = QuarryStatus.STOPPED;
        setChanged();
        syncNow();
    }

    public void setRunning(boolean value) {
        if (value) {
            if (!rangeConfigured || plan.volume() <= 0) {
                running = false;
                status = QuarryStatus.NO_RANGE;
                setChanged();
                syncNow();
                return;
            }
            if (cursor >= plan.volume()) {
                // Starting again after completion restarts the area but keeps the collected loot.
                cursor = 0;
                blocksMined = 0;
                blocksSkipped = 0;
            }
            running = true;
            status = QuarryStatus.RUNNING;
            opsAccumulator = 0.0D;
            inventoryRetryCooldown = 0;
            inventoryChanged = false;
        } else {
            running = false;
            opsAccumulator = 0.0D;
            inventoryRetryCooldown = 0;
            inventoryChanged = false;
            status = !rangeConfigured ? QuarryStatus.NO_RANGE
                    : cursor >= plan.volume() ? QuarryStatus.COMPLETE : QuarryStatus.STOPPED;
        }
        setChanged();
        syncNow();
    }

    public void toggleRunning() {
        setRunning(!running);
    }

    public void togglePreview() {
        preview = !preview;
        setChanged();
        syncNow();
    }

    public BlockPos targetFor(int index) {
        return targetFor(index, getBlockState().getValue(QuarryBlock.FACING));
    }

    /**
     * Target of a cursor index for an already resolved facing. The machine reads the facing once
     * per tick instead of once per target.
     */
    private BlockPos targetFor(int index, Direction facing) {
        if (index < 0 || index >= plan.volume()) {
            return worldPosition;
        }
        QuarryFrame.Point target = QuarryFrame.target(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                facing.get3DDataValue(), rangeWidth, rangeDepth, rangeHeight, index);
        return new BlockPos(target.x(), target.y(), target.z());
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.quarry");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new QuarryMenu(containerId, playerInventory, this, dataAccess);
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
        return "quarry";
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** Output-only view used by pipes/hoppers and by automation hooks. */
    public IItemHandler getOutputHandler() {
        return outputView;
    }

    public QuarryItemHandler getInventory() {
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
    // Sync / persistence
    // ------------------------------------------------------------------

    private void writeSyncData(CompoundTag tag) {
        tag.putInt(TAG_WIDTH, rangeWidth);
        tag.putInt(TAG_DEPTH, rangeDepth);
        tag.putInt(TAG_HEIGHT, rangeHeight);
        tag.putBoolean(TAG_CONFIGURED, rangeConfigured);
        tag.putInt(TAG_CURSOR, cursor);
        tag.putBoolean(TAG_RUNNING, running);
        tag.putBoolean(TAG_PREVIEW, preview);
        tag.putInt(TAG_MOTORS, motorCount);
        tag.putInt(TAG_BATTERIES, batteryCount);
        tag.putInt(TAG_MINED, blocksMined);
        tag.putInt(TAG_SKIPPED, blocksSkipped);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_PREV_CURSOR, previousCursor);
        tag.putFloat(TAG_PREV_WORK, previousWork);
        tag.putFloat(TAG_WORK, currentWorkFraction());
        tag.putInt(TAG_SYNC_INTERVAL, Math.max(1, visualInterval));
    }

    private void readSyncData(CompoundTag tag) {
        if (tag.contains(TAG_WIDTH)) {
            rangeWidth = tag.getInt(TAG_WIDTH);
        }
        if (tag.contains(TAG_DEPTH)) {
            rangeDepth = tag.getInt(TAG_DEPTH);
        }
        if (tag.contains(TAG_HEIGHT)) {
            rangeHeight = tag.getInt(TAG_HEIGHT);
        }
        if (tag.contains(TAG_CONFIGURED)) {
            rangeConfigured = tag.getBoolean(TAG_CONFIGURED);
        }
        if (tag.contains(TAG_CURSOR)) {
            cursor = tag.getInt(TAG_CURSOR);
        }
        if (tag.contains(TAG_RUNNING)) {
            running = tag.getBoolean(TAG_RUNNING);
        }
        if (tag.contains(TAG_PREVIEW)) {
            preview = tag.getBoolean(TAG_PREVIEW);
        }
        if (tag.contains(TAG_MOTORS)) {
            motorCount = UpgradeMath.clampCount(tag.getInt(TAG_MOTORS), HARD_UPGRADE_LIMIT);
        }
        if (tag.contains(TAG_BATTERIES)) {
            batteryCount = UpgradeMath.clampCount(tag.getInt(TAG_BATTERIES), HARD_UPGRADE_LIMIT);
        }
        if (tag.contains(TAG_MINED)) {
            blocksMined = Math.max(0, tag.getInt(TAG_MINED));
        }
        if (tag.contains(TAG_SKIPPED)) {
            blocksSkipped = Math.max(0, tag.getInt(TAG_SKIPPED));
        }
        if (tag.contains(TAG_STATUS)) {
            QuarryStatus[] values = QuarryStatus.values();
            int ordinal = Mth.clamp(tag.getInt(TAG_STATUS), 0, values.length - 1);
            status = values[ordinal];
        }
        // Never read the server config here: this method also runs on the client when a
        // block update arrives. The capacity is part of the synced/saved tag instead.
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        } else {
            energy.setCapacity(UpgradeMath.capacity(batteryCount, 20_000, 10_000, 2_000_000_000));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }

        rangeWidth = Mth.clamp(rangeWidth, 1, MiningPlan.MAX_DIMENSION);
        rangeDepth = Mth.clamp(rangeDepth, 1, MiningPlan.MAX_DIMENSION);
        rangeHeight = Mth.clamp(rangeHeight, 1, MiningPlan.MAX_DIMENSION);
        plan = new MiningPlan(rangeWidth, rangeDepth, rangeHeight);
        cursor = Mth.clamp(cursor, 0, plan.volume());
        previousCursor = Mth.clamp(previousCursor, 0, plan.volume());
        if (tag.contains(TAG_PREV_CURSOR)) {
            previousCursor = Mth.clamp(tag.getInt(TAG_PREV_CURSOR), 0, plan.volume());
        }
        if (tag.contains(TAG_PREV_WORK)) {
            previousWork = Math.max(0.0F, Math.min(0.999F, tag.getFloat(TAG_PREV_WORK)));
        }
        if (tag.contains(TAG_WORK)) {
            lastSyncedWork = Math.max(0.0F, Math.min(0.999F, tag.getFloat(TAG_WORK)));
            // The banked work is real progress: restore it so a save/load round trip does not
            // silently lose or repeat part of a block.
            opsAccumulator = lastSyncedWork;
        }
        if (tag.contains(TAG_SYNC_INTERVAL)) {
            visualInterval = Mth.clamp(tag.getInt(TAG_SYNC_INTERVAL), 1, 400);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSyncData(tag);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readSyncData(tag);
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        }
        if (level == null || !level.isClientSide) {
            workTracker.load(tag);
        }
        inventoryRetryCooldown = 0;
        inventoryChanged = false;
        if (level != null && level.isClientSide) {
            updatePreviewCache();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSyncData(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        miningFakePlayer = null;
        if (level.isClientSide) {
            updatePreviewCache();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        setRegistered(false);
        miningFakePlayer = null;
        if (level != null && level.isClientSide) {
            PreviewRegistry.remove(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // Never leave a stale entry in the global budget divisor.
        setRegistered(false);
        miningFakePlayer = null;
        if (level != null && level.isClientSide) {
            // Do not keep a rendering entry for a chunk the client no longer has.
            PreviewRegistry.remove(worldPosition);
        }
    }

    /**
     * Sends the current state to watching clients (and refreshes the local preview cache).
     *
     * <p>The cursor of the previous update is kept so the client can interpolate between the two
     * confirmed positions instead of jumping. When the cursor moves backwards (a new area was
     * applied) interpolation is disabled for that update.</p>
     */
    public void syncNow() {
        if (level instanceof ServerLevel serverLevel) {
            float work = currentWorkFraction();
            int previous = lastSyncedCursor;
            float previousFraction = lastSyncedWork;
            if (previous < 0 || previous > cursor) {
                // Nothing synced yet, or the area restarted: do not sweep backwards over the
                // previous area, start the new update at the current confirmed position.
                previous = cursor;
                previousFraction = work;
            }
            previousCursor = previous;
            previousWork = previousFraction;
            lastSyncedCursor = cursor;
            lastSyncedWork = work;
            MiningProfiler.recordVisualSync();
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        updatePreviewCache();
    }

    /**
     * Fraction of the work towards the next position that is already banked. This is the same
     * number the mining loop uses, so the drawn drill position and the block that is about to be
     * broken come from one model (see {@link MiningSync}).
     */
    private float currentWorkFraction() {
        // Only a machine that is really mining may report partial progress; while paused the drill
        // stays on the confirmed block instead of showing work that is not happening.
        if (!running || status != QuarryStatus.RUNNING || opsAccumulator <= 0.0D) {
            return 0.0F;
        }
        double fraction = opsAccumulator - Math.floor(opsAccumulator);
        if (!Double.isFinite(fraction)) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(0.999D, fraction));
    }

    private void updatePreviewCache() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if ((preview || running) && rangeConfigured) {
            PreviewRegistry.update(new PreviewData(
                    worldPosition,
                    getBlockState().getValue(QuarryBlock.FACING),
                    rangeWidth,
                    rangeDepth,
                    rangeHeight,
                    previousCursor,
                    previousWork,
                    cursor,
                    currentWorkFraction(),
                    plan.volume(),
                    Math.max(1, visualInterval),
                    status,
                    running,
                    preview), level.getGameTime());
        } else {
            PreviewRegistry.remove(worldPosition);
        }
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
        updatePreviewCache();
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPreviewEnabled() {
        return preview;
    }

    public boolean isRangeConfigured() {
        return rangeConfigured;
    }

    /** Interval currently used between visual updates (diagnostics/tests). */
    public int getVisualSyncInterval() {
        return Math.max(1, visualInterval);
    }

    /** Within-block work fraction used for the drill/frame model (diagnostics/tests). */
    public float getWorkFraction() {
        return currentWorkFraction();
    }

    /**
     * Confirmed drill index (cursor + banked work). The client draws both the head and the frame
     * from this value, so a test can assert that the frame cannot run ahead of the mining loop.
     */
    public double getDrillIndex() {
        return MiningSync.drillIndex(cursor, currentWorkFraction());
    }

    public int getMotorCount() {
        return motorCount;
    }

    public int getBatteryCount() {
        return batteryCount;
    }

    public int getCursor() {
        return cursor;
    }

    public int getVolume() {
        return rangeConfigured ? plan.volume() : 0;
    }

    public int getRangeWidth() {
        return rangeWidth;
    }

    public int getRangeDepth() {
        return rangeDepth;
    }

    public int getRangeHeight() {
        return rangeHeight;
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public int getBlocksSkipped() {
        return blocksSkipped;
    }

    /** Test/integration hook: current FE buffer. */
    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    /** Test/integration hook: directly sets the stored FE (clamped). */
    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    /** Test hook: forces the cursor, clamped to the current plan. */
    public void setCursorForTesting(int value) {
        cursor = Mth.clamp(value, 0, plan.volume());
    }

    /** Inventory handler with upgrade slot validation. */
    public static final class QuarryItemHandler extends ItemStackHandler {
        private final QuarryBlockEntity owner;

        QuarryItemHandler(int size, QuarryBlockEntity owner) {
            super(size);
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
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            // Also wakes up a full-inventory pause without waiting out its backoff.
            owner.inventoryChanged = true;
            owner.setChanged();
        }
    }
}
