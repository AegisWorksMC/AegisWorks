package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.MiningProfiler;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.menu.QuarryMenu;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Performance and large-factory safety tests on the real server.
 *
 * <p>Measurements are taken by ticking the quarry directly ({@link QuarryBlockEntity#serverTick})
 * so that the numbers describe the machine and not the whole server. They are logged, not
 * asserted as hard thresholds (except for very generous regression limits), because wall-clock
 * time depends on the host machine.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    private static final int QUARRY_Y = 6;
    private static final int BLOCKS = 16;

    private PerformanceGameTests() {}

    /** 0 motors vs 64 motors on the same machine, measured the same way. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void miningThroughputScalesWithMotorCount(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 4, 4, 1);

        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(4, 4, 1);
        quarry.setRunning(true);
        Measurement without = measure(helper, quarry, cells.length, 400);
        helper.assertTrue(quarry.getBlocksMined() == cells.length,
                "without motors only " + quarry.getBlocksMined() + " of " + cells.length + " blocks were mined");
        log("0 motors (cold)", without, cells.length);

        // Second run of the same configuration: the JVM is warm, which is the comparable number.
        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(4, 4, 1);
        quarry.setRunning(true);
        Measurement withoutWarm = measure(helper, quarry, cells.length, 400);
        helper.assertTrue(quarry.getBlocksMined() == cells.length,
                "the warm run mined " + quarry.getBlocksMined() + " of " + cells.length + " blocks");
        log("0 motors (warm)", withoutWarm, cells.length);

        // 64 motors are consumed one per server tick.
        quarry.setRunning(false);
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT,
                new ItemStack(ModItems.MOTOR.get(), 64));
        for (int tick = 0; tick < 64; tick++) {
            tick(helper, quarry);
        }
        helper.assertTrue(quarry.getMotorCount() == 64,
                "expected 64 installed motors but found " + quarry.getMotorCount());

        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(4, 4, 1);
        quarry.setRunning(true);
        Measurement withMotors = measure(helper, quarry, cells.length, 400);
        helper.assertTrue(quarry.getBlocksMined() == cells.length,
                "with 64 motors only " + quarry.getBlocksMined() + " of " + cells.length + " blocks were mined");
        log("64 motors", withMotors, cells.length);

        double slow = without.blocksPerTick();
        double fast = withMotors.blocksPerTick();
        helper.assertTrue(fast > slow * 4.0D,
                "64 motors should be much faster: " + fast + " vs " + slow + " blocks/tick");
        helper.assertTrue(fast <= Config.maxOpsPerTick() + 0.01D,
                "blocks per tick exceeded the safety cap: " + fast);
        helper.succeed();
    }

    /** A stopped machine must not cost anything meaningful per tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void idleMachineTickStaysCheap(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        quarry.applyRange(3, 3, 1);
        // Never started, no energy, no upgrades: the worst case for "many machines doing nothing".
        int iterations = 2_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            tick(helper, quarry);
        }
        long elapsed = System.nanoTime() - start;
        double microsPerTick = elapsed / (double) iterations / 1_000.0D;
        IndustrialCore.LOGGER.info("[perf] idle quarry tick: {} us/tick ({} ticks)", String.format("%.3f", microsPerTick), iterations);
        helper.assertTrue(microsPerTick < 100.0D,
                "an idle quarry tick should be far below 100 us but was " + microsPerTick + " us");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.STOPPED,
                "an idle machine should stay STOPPED but was " + quarry.getStatus());
        helper.succeed();
    }

    /** Two machines that run at the same time share one global per-tick budget. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void globalBudgetCapsTotalWork(GameTestHelper helper) {
        QuarryBlockEntity first = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        QuarryBlockEntity second = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 8), Direction.EAST);
        BlockPos[] firstCells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 3, 3, 1);
        BlockPos[] secondCells = cells(new BlockPos(3, QUARRY_Y, 8), Direction.EAST, 3, 3, 1);
        fill(helper, firstCells);
        fill(helper, secondCells);

        for (QuarryBlockEntity quarry : new QuarryBlockEntity[] { first, second }) {
            // 80 motors (~4.25 blocks/tick) so both machines really want more than their share.
            quarry.setRunning(false);
            quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT,
                    new ItemStack(ModItems.MOTOR.get(), 80));
            for (int i = 0; i < 80; i++) {
                tick(helper, quarry);
            }
            helper.assertTrue(quarry.getMotorCount() == 80,
                    "expected 80 motors but found " + quarry.getMotorCount());
        }

        for (QuarryBlockEntity quarry : new QuarryBlockEntity[] { first, second }) {
            quarry.setEnergyStored(20_000);
            quarry.applyRange(3, 3, 1);
            quarry.setRunning(true);
            // Let them reach the RUNNING state (and register as active) before measuring.
            tick(helper, quarry);
        }

        int budget = 4;
        int minedBefore = first.getBlocksMined() + second.getBlocksMined();
        int throttledBefore = (int) MiningScheduler.throttledClaims();
        MiningScheduler.setBudgetOverrideForTesting(budget);
        try {
            // A fresh virtual server tick, so the measurement does not share the budget of the
            // ticks the server already ran for this game tick.
            MiningScheduler.beginTick(helper.getLevel().getGameTime() + 1L, budget);
            tick(helper, first);
            tick(helper, second);
        } finally {
            // Test default again: the suite runs isolated from the shared per-tick budget.
            MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        }
        int mined = first.getBlocksMined() + second.getBlocksMined() - minedBefore;
        helper.assertTrue(mined <= budget,
                "the global budget of " + budget + " was exceeded: " + mined + " blocks in one tick");
        helper.assertTrue(mined >= 1, "the global budget stopped all progress");
        helper.assertTrue(MiningScheduler.throttledClaims() > throttledBefore,
                "no claim was throttled, so the budget was not applied");
        IndustrialCore.LOGGER.info("[perf] global budget {}: mined {} blocks in one tick across 2 machines",
                budget, mined);
        helper.succeed();
    }

    /** Only machines that actually mine take part in the global budget divisor. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void schedulerTracksOnlyMiningMachines(GameTestHelper helper) {
        int baseline = MiningScheduler.activeMachines();
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 3, 3, 1);
        fill(helper, cells);
        quarry.setEnergyStored(0);
        quarry.applyRange(3, 3, 1);
        quarry.setRunning(true);

        // The base speed is 0.25 blocks/tick, so four ticks are needed before the first attempt.
        for (int i = 0; i < 4; i++) {
            tick(helper, quarry);
        }
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                "expected PAUSED_NO_ENERGY but was " + quarry.getStatus());
        helper.assertTrue(MiningScheduler.activeMachines() == baseline,
                "a machine without energy must not be counted as active");

        quarry.setEnergyStored(20_000);
        for (int i = 0; i < 4; i++) {
            tick(helper, quarry);
        }
        helper.assertTrue(quarry.getStatus() == QuarryStatus.RUNNING,
                "the machine did not resume: " + quarry.getStatus());
        helper.assertTrue(MiningScheduler.activeMachines() == baseline + 1,
                "a mining machine was not registered: " + MiningScheduler.activeMachines());

        quarry.setRunning(false);
        tick(helper, quarry);
        helper.assertTrue(MiningScheduler.activeMachines() == baseline,
                "a stopped machine was not unregistered: " + MiningScheduler.activeMachines());
        helper.succeed();
    }

    /** The GUI data channel must report live values, not a stale snapshot. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void menuDataReportsLiveEnergyAndProgress(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 2, 2, 1);
        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(2, 2, 1);
        quarry.setRunning(true);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        QuarryMenu menu = new QuarryMenu(1, player.getInventory(), quarry, quarry.getContainerData());

        helper.assertTrue(menu.getEnergyCapacity() == quarry.getEnergyStorage().getMaxEnergyStored(),
                "menu capacity does not match the block entity");
        helper.assertTrue(QuarryBlockEntity.DATA_COUNT == 14,
                "DATA_COUNT must cover every synced index (the client cache uses it): "
                        + QuarryBlockEntity.DATA_COUNT);
        helper.assertTrue(quarry.getContainerData().getCount() == QuarryBlockEntity.DATA_COUNT,
                "the block entity exposes a different number of data slots");

        int energyBefore = menu.getEnergyStored();
        for (int i = 0; i < 40; i++) {
            tick(helper, quarry);
        }
        helper.assertTrue(menu.getEnergyStored() < energyBefore,
                "the menu energy did not change while mining: " + menu.getEnergyStored());
        helper.assertTrue(menu.getEnergyStored() == quarry.getEnergyStoredForTesting(),
                "menu energy " + menu.getEnergyStored() + " differs from the machine "
                        + quarry.getEnergyStoredForTesting());
        helper.assertTrue(menu.getCursor() == quarry.getCursor(),
                "menu cursor does not match the machine cursor");
        helper.assertTrue(menu.getBlocksMined() > 0, "menu progress did not advance");
        helper.assertTrue(menu.getStatus() == quarry.getStatus(), "menu status does not match the machine");
        helper.succeed();
    }

    /** Visual updates are stretched once many machines run, and shrink back afterwards. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void visualSyncIntervalAdaptsToMachineCount(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        quarry.applyRange(2, 2, 1);
        int min = Config.visualSyncMinIntervalTicks();
        int max = Config.visualSyncMaxIntervalTicks();

        int extra = 400;
        int crowded;
        for (int i = 0; i < extra; i++) {
            MiningScheduler.onMachineStarted();
        }
        try {
            for (int i = 0; i < 21; i++) {
                tick(helper, quarry);
            }
            crowded = quarry.getVisualSyncInterval();
            // Phase 9 bounds how many positions one update may cover. The factory wide interval is
            // still max, but a machine mining 0.25 positions per tick is shortened to 16 ticks
            // (4 positions) instead of being allowed to cover 5.
            int expected = com.industrialcore.machine.MiningSync.syncInterval(
                    max, 0.25D, Config.visualMaxBlocksPerUpdate());
            helper.assertTrue(crowded == expected,
                    "with " + (extra + 1) + " machines the interval should be " + expected
                            + " but was " + crowded);
            helper.assertTrue(crowded <= max, "the interval grew beyond the factory maximum");
        } finally {
            for (int i = 0; i < extra; i++) {
                MiningScheduler.onMachineStopped();
            }
        }

        for (int i = 0; i < 25; i++) {
            tick(helper, quarry);
        }
        int relaxed = quarry.getVisualSyncInterval();
        helper.assertTrue(relaxed < crowded,
                "the interval did not shrink after the other machines stopped: " + relaxed);
        helper.assertTrue(relaxed >= min, "the interval fell below the configured minimum: " + relaxed);
        helper.succeed();
    }

    /**
     * Root cause guard for the "energy does not update" bug: vanilla applies synced menu values
     * through {@link ContainerData#set(int, int)}, so the server object must ignore writes while
     * the client's own cache must accept them.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void serverDataIsReadOnlyAndClientCacheIsWritable(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        quarry.setEnergyStored(5_000);
        DataSlot serverSlot = DataSlot.forContainer(quarry.getContainerData(), QuarryBlockEntity.DATA_ENERGY);
        int serverBefore = serverSlot.get();
        serverSlot.set(serverBefore - 1_000);
        helper.assertTrue(serverSlot.get() == serverBefore,
                "the block entity data must stay server authoritative, but a write changed it");

        ContainerData clientCache = new SimpleContainerData(QuarryBlockEntity.DATA_COUNT);
        DataSlot clientSlot = DataSlot.forContainer(clientCache, QuarryBlockEntity.DATA_ENERGY);
        clientSlot.set(1_234);
        helper.assertTrue(clientSlot.get() == 1_234,
                "the client menu cache must accept synced values, but the write was ignored");
        helper.succeed();
    }

    /** Paused machines must not send visual updates at all; running ones stay rate limited. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void visualUpdatesAreSilentWhilePaused(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        quarry.applyRange(3, 3, 1);

        MiningProfiler.reset();
        for (int i = 0; i < 200; i++) {
            tick(helper, quarry);
        }
        long pausedSyncs = MiningProfiler.visualSyncs();
        helper.assertTrue(pausedSyncs == 0,
                "a stopped machine sent " + pausedSyncs + " visual updates in 200 ticks");

        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 3, 3, 1);
        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(3, 3, 1);
        quarry.setRunning(true);

        MiningProfiler.reset();
        for (int i = 0; i < 200; i++) {
            tick(helper, quarry);
        }
        long runningSyncs = MiningProfiler.visualSyncs();
        int upperBound = 200 / Config.visualSyncMinIntervalTicks() + 2;
        helper.assertTrue(runningSyncs > 0, "a running machine sent no visual updates");
        helper.assertTrue(runningSyncs <= upperBound,
                "too many visual updates for one machine: " + runningSyncs + " > " + upperBound);
        IndustrialCore.LOGGER.info("[perf] visual updates in 200 ticks: paused={} running={}",
                pausedSyncs, runningSyncs);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 3 scenarios: large factories, paused machines, generators.
    // Every test logs its numbers; thresholds are kept generous on purpose so the
    // measurements stay comparable between the baseline and the optimized run.
    // ------------------------------------------------------------------

    /**
     * Many configured but stopped machines: each idle machine must stay in its own cheap
     * early-out path (no shared scanning, no budget participation, no sync traffic).
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void factoryOfIdleQuarriesStaysCheap(GameTestHelper helper) {
        BlockPos[] spots = {
                new BlockPos(1, QUARRY_Y, 1), new BlockPos(1, QUARRY_Y, 7),
                new BlockPos(7, QUARRY_Y, 1), new BlockPos(7, QUARRY_Y, 7)
        };
        QuarryBlockEntity[] machines = new QuarryBlockEntity[spots.length];
        for (int i = 0; i < spots.length; i++) {
            machines[i] = placeQuarry(helper, spots[i], Direction.EAST);
            machines[i].applyRange(2, 2, 2);
        }

        for (int warm = 0; warm < 100; warm++) {
            for (QuarryBlockEntity machine : machines) {
                tick(helper, machine);
            }
        }

        int iterations = 1_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (QuarryBlockEntity machine : machines) {
                tick(helper, machine);
            }
        }
        long elapsed = System.nanoTime() - start;
        int machineTicks = iterations * machines.length;
        double perMachine = elapsed / (double) machineTicks / 1_000.0D;
        IndustrialCore.LOGGER.info("[perf] {} idle quarries: {} us/machine-tick, {} us for all {} in one tick ({} machine ticks)",
                machines.length, String.format("%.3f", perMachine),
                String.format("%.3f", perMachine * machines.length), machines.length, machineTicks);
        for (QuarryBlockEntity machine : machines) {
            helper.assertTrue(machine.getStatus() == QuarryStatus.STOPPED,
                    "an idle machine should stay STOPPED but was " + machine.getStatus());
        }
        helper.assertTrue(perMachine < 100.0D,
                "an idle machine tick should stay far below 100 us but was " + perMachine + " us");
        helper.succeed();
    }

    /**
     * Worst case for "stopped machine": 64 motors are installed (so the machine retries at
     * the highest rate) while it waits for FE, and again while its output inventory is full.
     * Logs the amortised tick cost and the visual updates sent while nothing changed.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void pausedQuarryTicksAreCheap(GameTestHelper helper) {
        // --- (1) waiting for FE -------------------------------------------------
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        install64Motors(helper, quarry);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 2, 1, 1);
        fill(helper, cells);
        quarry.setEnergyStored(0);
        quarry.applyRange(2, 1, 1);
        quarry.setRunning(true);
        settleUntil(helper, quarry, QuarryStatus.PAUSED_NO_ENERGY, 60);
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                "expected PAUSED_NO_ENERGY but was " + quarry.getStatus());

        for (int warm = 0; warm < 100; warm++) {
            tick(helper, quarry);
        }
        long syncsBefore = MiningProfiler.visualSyncs();
        int iterations = 1_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            tick(helper, quarry);
        }
        double microsNoEnergy = (System.nanoTime() - start) / (double) iterations / 1_000.0D;
        long syncDelta = Math.max(0L, MiningProfiler.visualSyncs() - syncsBefore);
        IndustrialCore.LOGGER.info("[perf] PAUSED_NO_ENERGY (64 motors): {} us/tick, {} visual updates in {} ticks",
                String.format("%.3f", microsNoEnergy), syncDelta, iterations);
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                "the machine left PAUSED_NO_ENERGY without energy: " + quarry.getStatus());
        helper.assertTrue(helper.getBlockState(cells[0]).is(Blocks.STONE),
                "a block was destroyed while waiting for FE");
        helper.assertTrue(microsNoEnergy < 500.0D,
                "a FE-starved machine tick should stay below 500 us but was " + microsNoEnergy + " us");
        helper.assertTrue(syncDelta <= 3,
                "a machine that changed nothing must stay silent, but sent " + syncDelta
                        + " visual updates in " + iterations + " ticks");

        // --- (2) waiting for output space --------------------------------------
        for (int slot = QuarryBlockEntity.OUTPUT_START; slot < QuarryBlockEntity.TOTAL_SLOTS; slot++) {
            quarry.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }
        quarry.setEnergyStored(20_000);
        quarry.setRunning(true);
        settleUntil(helper, quarry, QuarryStatus.PAUSED_INVENTORY_FULL, 60);
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL,
                "expected PAUSED_INVENTORY_FULL but was " + quarry.getStatus());

        for (int warm = 0; warm < 100; warm++) {
            tick(helper, quarry);
        }
        long fullSyncsBefore = MiningProfiler.visualSyncs();
        long fullStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            tick(helper, quarry);
        }
        double microsFull = (System.nanoTime() - fullStart) / (double) iterations / 1_000.0D;
        long fullSyncDelta = Math.max(0L, MiningProfiler.visualSyncs() - fullSyncsBefore);
        IndustrialCore.LOGGER.info("[perf] PAUSED_INVENTORY_FULL (64 motors): {} us/tick, {} visual updates in {} ticks",
                String.format("%.3f", microsFull), fullSyncDelta, iterations);
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL,
                "the machine left PAUSED_INVENTORY_FULL on its own: " + quarry.getStatus());
        helper.assertTrue(helper.getBlockState(cells[0]).is(Blocks.STONE),
                "a block was destroyed while the inventory was full");
        int stone = countItems(quarry, Items.STONE);
        helper.assertTrue(stone == 18 * 64, "collected stone changed to " + stone);
        helper.assertTrue(microsFull < 15.0D,
                "a full-inventory machine tick should stay far below its 26.207 us baseline but was "
                        + microsFull + " us");
        helper.assertTrue(fullSyncDelta <= 3,
                "a machine that changed nothing must stay silent, but sent " + fullSyncDelta
                        + " visual updates in " + iterations + " ticks");

        helper.succeed();
    }

    /**
     * The generator's neighbour push is the only per-tick network work in the mod; measure it
     * with an adjacent receiver present (the baseline for the neighbour capability cache).
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void generatorPushTickIsCheap(GameTestHelper helper) {
        GenerationBlockEntity generator = placeGenerator(helper, new BlockPos(4, QUARRY_Y, 6));
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(5, QUARRY_Y, 6), Direction.EAST);
        quarry.setEnergyStored(0);

        for (int warm = 0; warm < 100; warm++) {
            generatorTick(helper, generator);
        }
        generator.getEnergyStorage().setEnergy(50_000);
        int iterations = 400;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            generatorTick(helper, generator);
        }
        double micros = (System.nanoTime() - start) / (double) iterations / 1_000.0D;
        long received = quarry.getEnergyStoredForTesting();
        double storedAfter = generator.getEnergyStorage().getEnergyStored();
        IndustrialCore.LOGGER.info("[perf] generator push tick: {} us/tick over {} ticks, quarry received {} FE, generator holds {} FE",
                String.format("%.3f", micros), iterations, received, storedAfter);
        helper.assertTrue(received > 0, "the generator never pushed FE into the adjacent quarry");
        helper.assertTrue(micros < 1_000.0D,
                "a generator tick should stay below 1000 us but was " + micros + " us");
        helper.succeed();
    }

    /** A moderate upgrade count, measured exactly like the 0 and 64 motor runs. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void moderateMotorCountThroughput(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 4, 4, 1);

        quarry.setRunning(false);
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT, new ItemStack(ModItems.MOTOR.get(), 8));
        for (int i = 0; i < 8; i++) {
            tick(helper, quarry);
        }
        helper.assertTrue(quarry.getMotorCount() == 8,
                "expected 8 installed motors but found " + quarry.getMotorCount());

        fill(helper, cells);
        quarry.setEnergyStored(20_000);
        quarry.applyRange(4, 4, 1);
        quarry.setRunning(true);
        Measurement measurement = measure(helper, quarry, cells.length, 400);
        helper.assertTrue(quarry.getBlocksMined() == cells.length,
                "the 8 motor run mined " + quarry.getBlocksMined() + " of " + cells.length + " blocks");
        log("8 motors", measurement, cells.length);

        double speed = measurement.blocksPerTick();
        helper.assertTrue(speed > 0.40D,
                "8 motors should beat the 0.25 base speed noticeably but was " + speed + " blocks/tick");
        helper.assertTrue(speed <= Config.maxOpsPerTick() + 0.01D,
                "blocks per tick exceeded the safety cap: " + speed);
        helper.succeed();
    }

    /**
     * Four running quarries at once: measures the per-machine tick cost while several
     * machines share the global budget, and checks that all of them finish their own area.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void multipleQuarriesMineInParallel(GameTestHelper helper) {
        BlockPos[] spots = {
                new BlockPos(1, QUARRY_Y, 1), new BlockPos(1, QUARRY_Y, 7),
                new BlockPos(7, QUARRY_Y, 1), new BlockPos(7, QUARRY_Y, 7)
        };
        QuarryBlockEntity[] machines = new QuarryBlockEntity[spots.length];
        for (int i = 0; i < spots.length; i++) {
            machines[i] = placeQuarry(helper, spots[i], Direction.EAST);
            BlockPos origin = spots[i].relative(Direction.EAST);
            fill(helper, cells(origin, Direction.EAST, 3, 3, 1));
            machines[i].setRunning(false);
            machines[i].getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT,
                    new ItemStack(ModItems.MOTOR.get(), 32));
            for (int install = 0; install < 32; install++) {
                tick(helper, machines[i]);
            }
            helper.assertTrue(machines[i].getMotorCount() == 32,
                    "expected 32 motors but found " + machines[i].getMotorCount());
            machines[i].setEnergyStored(20_000);
            machines[i].applyRange(3, 3, 1);
            machines[i].setRunning(true);
        }

        int iterations = 0;
        long start = System.nanoTime();
        while (iterations < 400 && !allMined(machines, 9)) {
            for (QuarryBlockEntity machine : machines) {
                tick(helper, machine);
            }
            iterations++;
        }
        long elapsed = System.nanoTime() - start;
        int machineTicks = iterations * machines.length;
        double perMachine = machineTicks == 0 ? 0.0D : elapsed / (double) machineTicks / 1_000.0D;
        int totalBlocks = machines.length * 9;
        IndustrialCore.LOGGER.info("[perf] 4 quarries (32 motors each): {} blocks in {} ticks = {} blocks/tick overall, {} us/quarry-tick",
                totalBlocks, iterations, String.format("%.2f", iterations == 0 ? 0.0D : totalBlocks / (double) iterations),
                String.format("%.3f", perMachine));
        for (int i = 0; i < machines.length; i++) {
            helper.assertTrue(machines[i].getBlocksMined() == 9,
                    "quarry " + i + " mined " + machines[i].getBlocksMined() + " of 9 blocks");
        }
        helper.assertTrue(perMachine < 500.0D,
                "a running quarry tick should stay below 500 us but was " + perMachine + " us");
        helper.succeed();
    }

    /** Chunk unload and block removal must never leave a machine registered as active. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void unloadAndRemovalUnregisterMachines(GameTestHelper helper) {
        int baseline = MiningScheduler.activeMachines();
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, QUARRY_Y, 2), Direction.EAST);
        fill(helper, cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 3, 3, 1));
        quarry.setEnergyStored(20_000);
        quarry.applyRange(3, 3, 1);
        quarry.setRunning(true);
        tick(helper, quarry);
        helper.assertTrue(MiningScheduler.activeMachines() == baseline + 1,
                "the running machine did not register: " + MiningScheduler.activeMachines());

        quarry.onChunkUnloaded();
        helper.assertTrue(MiningScheduler.activeMachines() == baseline,
                "chunk unload left the machine registered: " + MiningScheduler.activeMachines());

        // The machine is still logically running, so the next tick registers it again.
        tick(helper, quarry);
        helper.assertTrue(MiningScheduler.activeMachines() == baseline + 1,
                "the machine did not re-register after the chunk came back: "
                        + MiningScheduler.activeMachines());

        quarry.setRemoved();
        helper.assertTrue(MiningScheduler.activeMachines() == baseline,
                "block removal left the machine registered: " + MiningScheduler.activeMachines());
        helper.succeed();
    }

    /**
     * Phase 3 guard: machines that cannot work at all must not spend shared work units,
     * otherwise a factory of paused machines would starve the machines that are mining.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void pausedMachinesDoNotConsumeGlobalBudget(GameTestHelper helper) {
        QuarryBlockEntity working = placeQuarry(helper, new BlockPos(1, QUARRY_Y, 1), Direction.EAST);
        fill(helper, cells(new BlockPos(2, QUARRY_Y, 1), Direction.EAST, 3, 3, 1));
        working.setEnergyStored(20_000);
        working.applyRange(3, 3, 1);
        working.setRunning(true);
        // Warm the accumulator so the machine requests work on the very next tick.
        for (int i = 0; i < 3; i++) {
            tick(helper, working);
        }

        int[] starvedTemplateZ = { 1, 7 };
        QuarryBlockEntity starvedA = placeQuarry(helper, new BlockPos(7, QUARRY_Y, starvedTemplateZ[0]), Direction.EAST);
        QuarryBlockEntity starvedB = placeQuarry(helper, new BlockPos(7, QUARRY_Y, starvedTemplateZ[1]), Direction.EAST);
        for (int z : starvedTemplateZ) {
            fill(helper, cells(new BlockPos(8, QUARRY_Y, z), Direction.EAST, 1, 1, 1));
        }
        for (QuarryBlockEntity starved : new QuarryBlockEntity[] { starvedA, starvedB }) {
            starved.setEnergyStored(0);
            starved.applyRange(1, 1, 1);
            starved.setRunning(true);
            settleUntil(helper, starved, QuarryStatus.PAUSED_NO_ENERGY, 60);
            helper.assertTrue(starved.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                    "expected PAUSED_NO_ENERGY but was " + starved.getStatus());
        }

        // One virtual server tick with a total work budget of 4, shared by everything that asks.
        MiningScheduler.setBudgetOverrideForTesting(4);
        try {
            MiningScheduler.beginTick(helper.getLevel().getGameTime() + 1L, 4);
            int minedBefore = working.getBlocksMined();
            tick(helper, starvedA);
            tick(helper, starvedB);
            tick(helper, working);
            helper.assertTrue(working.getBlocksMined() > minedBefore,
                    "paused machines consumed the shared budget: the working machine mined 0 blocks");
        } finally {
            MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        }
        helper.succeed();
    }

    /** The generator's neighbour cache must notice placed/broken blocks and keep delivering FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void generatorRediscoversChangedNeighbours(GameTestHelper helper) {
        GenerationBlockEntity generator = placeGenerator(helper, new BlockPos(4, QUARRY_Y, 6));
        helper.setBlock(new BlockPos(5, QUARRY_Y, 6), Blocks.STONE); // no energy capability here
        generator.getEnergyStorage().setEnergy(20_000);
        for (int i = 0; i < 20; i++) {
            generatorTick(helper, generator);
        }
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() > 0,
                "generator energy disappeared without any receiver");

        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(5, QUARRY_Y, 6), Direction.EAST);
        quarry.setEnergyStored(0);
        for (int i = 0; i < 40; i++) {
            generatorTick(helper, generator);
        }
        helper.assertTrue(quarry.getEnergyStoredForTesting() > 0,
                "the generator did not refresh its cached neighbour after the block changed");
        helper.succeed();
    }

    /** The operator diagnostic command must be registered and run (read-only, no side effects). */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void perfCommandRuns(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int result;
        try {
            result = server.getCommands().getDispatcher()
                    .execute("industrialcore perf", server.createCommandSourceStack());
        } catch (Exception exception) {
            helper.fail("the perf command failed: " + exception.getMessage());
            return;
        }
        helper.assertTrue(result > 0, "the perf command reported no success: " + result);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private record Measurement(int ticks, long nanos, int blocks) {
        double microsPerBlock() {
            return blocks <= 0 ? 0.0D : (nanos / (double) blocks) / 1_000.0D;
        }

        double blocksPerTick() {
            return ticks <= 0 ? 0.0D : blocks / (double) ticks;
        }
    }

    private static Measurement measure(GameTestHelper helper, QuarryBlockEntity quarry, int expected, int maxTicks) {
        int ticks = 0;
        long start = System.nanoTime();
        while (quarry.getBlocksMined() < expected && ticks < maxTicks) {
            tick(helper, quarry);
            ticks++;
        }
        long elapsed = System.nanoTime() - start;
        return new Measurement(ticks, elapsed, quarry.getBlocksMined());
    }

    private static void log(String label, Measurement measurement, int blocks) {
        IndustrialCore.LOGGER.info(
                "[perf] {}: {} blocks in {} ticks = {} blocks/tick, {} us/block (total {} ms)",
                label, blocks, measurement.ticks(),
                String.format("%.2f", measurement.blocksPerTick()),
                String.format("%.2f", measurement.microsPerBlock()),
                String.format("%.2f", measurement.nanos() / 1_000_000.0D));
    }

    private static void tick(GameTestHelper helper, QuarryBlockEntity quarry) {
        BlockState state = quarry.getBlockState();
        QuarryBlockEntity.serverTick(helper.getLevel(), quarry.getBlockPos(), state, quarry);
    }

    private static void generatorTick(GameTestHelper helper, GenerationBlockEntity generator) {
        BlockState state = generator.getBlockState();
        GenerationBlockEntity.serverTick(helper.getLevel(), generator.getBlockPos(), state, generator);
    }

    private static void install64Motors(GameTestHelper helper, QuarryBlockEntity quarry) {
        quarry.setRunning(false);
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT,
                new ItemStack(ModItems.MOTOR.get(), 64));
        for (int i = 0; i < 64; i++) {
            tick(helper, quarry);
        }
        helper.assertTrue(quarry.getMotorCount() == 64,
                "expected 64 installed motors but found " + quarry.getMotorCount());
    }

    /** Ticks the machine until it reaches the expected status or the tick budget runs out. */
    private static void settleUntil(GameTestHelper helper, QuarryBlockEntity quarry,
                                    QuarryStatus expected, int maxTicks) {
        for (int i = 0; i < maxTicks && quarry.getStatus() != expected; i++) {
            tick(helper, quarry);
        }
    }

    private static boolean allMined(QuarryBlockEntity[] machines, int expected) {
        for (QuarryBlockEntity machine : machines) {
            if (machine.getBlocksMined() < expected) {
                return false;
            }
        }
        return true;
    }

    private static GenerationBlockEntity placeGenerator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof GenerationBlockEntity generator)) {
            helper.fail("generator block entity missing at " + pos);
            return null;
        }
        return generator;
    }

    private static int countItems(QuarryBlockEntity quarry, Item item) {
        int total = 0;
        for (int slot = QuarryBlockEntity.OUTPUT_START; slot < QuarryBlockEntity.TOTAL_SLOTS; slot++) {
            ItemStack stack = quarry.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void fill(GameTestHelper helper, BlockPos[] cells) {
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.STONE);
        }
    }

    private static QuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos, Direction facing) {
        // GameTest suites share one server: disable the cross-machine budget while measuring so
        // other tests in the same batch cannot starve this machine's work units.
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        helper.setBlock(pos, ModBlocks.QUARRY.get().defaultBlockState().setValue(QuarryBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            helper.fail("quarry block entity missing at " + pos);
            return null;
        }
        return quarry;
    }

    private static BlockPos[] cells(BlockPos origin, Direction facing, int width, int depth, int height) {
        Direction right = facing.getClockWise();
        BlockPos[] result = new BlockPos[width * depth * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    result[index++] = origin.relative(right, x).relative(facing, z).below(y);
                }
            }
        }
        return result;
    }
}
