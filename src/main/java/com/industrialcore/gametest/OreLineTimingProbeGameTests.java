package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Measures the ore amplification line twice under identical conditions, changing only how long one
 * run occupies a machine: case A uses the production value, case B uses the proposed 25 ticks.
 *
 * <p><b>What is measured and what is not.</b> The greedy schedule, the machine placement, the
 * input stock, the catalyst route and the machine counts are the same in both cases; only
 * {@code timeTicks} differs, so the comparison is controlled. Every plan is built from the real
 * recipe JSON and every run is executed on the real machine, which is what validates the output
 * items and counts. Case A runs on the production recipes as they ship (10 ticks). Case B's
 * duration is <em>not</em> schedule-only any more: {@code caseBProposedTwentyFiveTicksFromRaw}
 * swaps those chain recipes to 25 ticks through {@link RecipeSwapForTests} for the length of the
 * test method, so the machines really work 25 ticks and the metered FE can be compared with the
 * schedule figure, and it restores them in a {@code finally}. Only the stacked-catalyst case B
 * still runs the 10 tick machines under a 25 tick schedule, because that measurement predates the
 * swap and is kept unchanged. The duration, peak draw, concurrency and output counts are re-run
 * through the scheduler rather than scaled from case A.</p>
 *
 * <p>No power grid is connected here, exactly like the documented measurement this reproduces:
 * every machine is topped up before each of its ticks, so FE starvation cannot occur and the idle
 * counters below distinguish "waiting for inputs" from "no work left" instead. Real grid behaviour
 * is measured by {@code PowerGridGameTests}.</p>
 *
 * <p>All numbers are logged with the {@code [timing]} tag.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreLineTimingProbeGameTests {
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    private static final String ULTIMATE = "industrialcore:synthetic_ore_ultimate";
    private static final int TARGET_INGOTS = 32_768;
    /** The production value: every recipe of the chain is verified to carry it. */
    private static final int PRODUCTION_TICKS = 10;
    /** The proposal under test: 25 ticks, swapped into the real recipes by the case B test. */
    private static final int PROPOSED_TICKS = 25;
    /** Makespan of the documented 5x4 build from raw ore, to be reproduced by case A. */
    private static final int DOCUMENTED_5X4 = 5_839;
    /** Guard so a broken schedule fails with a diagnostic instead of spinning forever. */
    private static final int HARD_LIMIT = 60_000;
    private static final String STAGE_FIFTEEN = "industrialcore:oreline_s15_iron";

    private static final int Y = 3;
    private static final int MAX_SYNTHESIZERS = 7;
    private static final int MAX_CRUSHERS = 6;
    /** The documented standard build: five synthesizers and four crushers. */
    private static final int SYNTHESIZERS = 5;
    private static final int CRUSHERS = 4;
    /** Full buffer per machine, the condition the reference measurement uses. */
    private static final int PROCESSOR_BUFFER = 20_000;
    /** Coal loaded into every generator of a grid run (16 x 64,000 FE of possible energy). */
    private static final int COAL_PER_GENERATOR = 16;
    /** A starved run may take at most this many times its plan before the grid is called stuck. */
    private static final int GRID_RUN_TICK_LIMIT = 8;

    /**
     * The power mesh, one level above the machines: a cable over every active machine (placed by
     * {@code withGrid}), these bridge and spare nodes, and nothing else. The bridge joins the
     * synthesizer half to the processor half over the empty {@code z=2}/{@code z=3} rows, so no
     * cable ever sits above an inactive machine and no generator ever touches one.
     */
    private static final BlockPos[] GRID_MESH_EXTRAS = {
        new BlockPos(1, Y + 1, 2), new BlockPos(1, Y + 1, 3),
        new BlockPos(3, Y + 1, 4), new BlockPos(3, Y + 1, 5),
    };
    /**
     * Generator slots, in bank order, each one level <em>above</em> the mesh (the mesh sits at
     * {@code Y + 1}, these at {@code Y + 2}): 14, 15 and 16 are nested configurations of the same
     * mesh, so the bank size is the only variable between the runs, and no generator ever touches
     * a machine.
     */
    private static final BlockPos[] GRID_GENERATOR_SLOTS = {
        new BlockPos(0, Y + 2, 0), new BlockPos(1, Y + 2, 0), new BlockPos(2, Y + 2, 0),
        new BlockPos(0, Y + 2, 1), new BlockPos(1, Y + 2, 1),
        new BlockPos(1, Y + 2, 2), new BlockPos(1, Y + 2, 3),
        new BlockPos(0, Y + 2, 4), new BlockPos(1, Y + 2, 4), new BlockPos(2, Y + 2, 4),
        new BlockPos(0, Y + 2, 5), new BlockPos(1, Y + 2, 5), new BlockPos(2, Y + 2, 5),
        new BlockPos(0, Y + 2, 6),
        new BlockPos(3, Y + 2, 4), new BlockPos(3, Y + 2, 5),
    };
    /** The buffer: beside the mesh, touching exactly one cable and no machine. */
    private static final BlockPos GRID_BUFFER_POS = new BlockPos(3, Y + 1, 3);

    private OreLineTimingProbeGameTests() {}

    /** Case A, from raw ore: the production 10 tick chain, reproduced on the same harness. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseAProductionTenTicksFromRaw(GameTestHelper helper) {
        Probe probe = Probe.build(helper, true, PRODUCTION_TICKS).withMachines(SYNTHESIZERS,
                CRUSHERS);
        probe.run();
        probe.report("A 10 tick, from raw, 5x4");
        probe.assertOutputs(helper, "A");
        helper.assertTrue(probe.elapsed == DOCUMENTED_5X4,
                "case A measured " + probe.elapsed + " ticks instead of the documented "
                        + DOCUMENTED_5X4 + ": " + probe.diagnostic());
        probe.assertEnergyBalance(helper, "A");
        helper.succeed();
    }

    /** Case B, from raw ore: identical to case A, only the run duration becomes 25 ticks.
     *
     * <p>Unlike case A, the duration is not schedule-only here: the chain's production recipes are
     * swapped to 25 ticks for the duration of this method, so the machines really work 25 ticks.
     * The swap, the measurement and the restore are one synchronous call - no asynchronous work,
     * no waiting and no game tick boundary in between (see {@link RecipeSwapForTests}) - and the
     * {@code finally} puts the original recipes back even when an assertion fails. The pass
     * conditions are that every run really occupied its machine for the planned duration, that
     * the machines meter exactly what the schedule asks for, and that the restore is clean.</p> */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBProposedTwentyFiveTicksFromRaw(GameTestHelper helper) {
        RecipeSwapForTests.Swap swap = RecipeSwapForTests.install(
                helper.getLevel().getRecipeManager(), PROPOSED_TICKS);
        try {
            helper.assertTrue(swap.isSwapped(),
                    "B recipe swap failed: " + swap.swapSummary());
            helper.assertTrue(swap.swappedCount() > 0,
                    "B swapped no recipe, so the machines would still run " + PRODUCTION_TICKS
                            + " ticks: " + swap.swapSummary());
            Probe probe = Probe.build(helper, true, PROPOSED_TICKS).withMachines(SYNTHESIZERS,
                    CRUSHERS);
            probe.run();
            probe.report("B 25 tick, from raw, 5x4");
            probe.assertOutputs(helper, "B");
            probe.assertEnergyBalance(helper, "B");
            helper.assertTrue(probe.earlyCompletes == 0,
                    "B: " + probe.earlyCompletes + " run(s) finished before their planned "
                            + PROPOSED_TICKS + " ticks, so the real machine did not run the "
                            + "swapped duration: " + String.join(" | ", probe.earlyTrace));
            helper.assertTrue(probe.meteredFe == probe.scheduleFe,
                    "B: the machines metered " + probe.meteredFe + " FE while the schedule asks "
                            + "for " + probe.scheduleFe + " FE (real consumption "
                            + (probe.meteredFe - probe.scheduleFe) + " FE off)");
        } finally {
            swap.restore();
        }
        helper.assertTrue(swap.isRestored(),
                "B: the production recipes were not restored: " + swap.restoreSummary());
        helper.succeed();
    }

    /** Case A with the 512 ultimate catalysts already stocked: the line without the ladder. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseAProductionTenTicksWithStockedCatalysts(GameTestHelper helper) {
        Probe probe = Probe.build(helper, false, PRODUCTION_TICKS).withMachines(SYNTHESIZERS,
                CRUSHERS);
        probe.seed(ULTIMATE, 512);
        probe.run();
        probe.report("A 10 tick, stocked catalysts, 5x4");
        probe.assertOutputs(helper, "A stocked");
        probe.assertEnergyBalance(helper, "A stocked");
        helper.succeed();
    }

    /** Case B with the same 512 stocked catalysts, so only the line's duration differs.
     *
     * <p>Kept as it was measured: the schedule holds each machine for 25 ticks while the recipes
     * stay at their production 10 ticks, so its FE total is a schedule figure. The real 25 tick
     * measurement lives in {@link #caseBProposedTwentyFiveTicksFromRaw}.</p> */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBProposedTwentyFiveTicksWithStockedCatalysts(GameTestHelper helper) {
        Probe probe = Probe.build(helper, false, PROPOSED_TICKS).withMachines(SYNTHESIZERS,
                CRUSHERS);
        probe.seed(ULTIMATE, 512);
        probe.run();
        probe.report("B 25 tick, stocked catalysts, 5x4");
        probe.assertOutputs(helper, "B stocked");
        probe.assertEnergyBalance(helper, "B stocked");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // case B on a real power grid (hierarchy i: sequential, one machine at a time)
    // ------------------------------------------------------------------

    /** Fourteen generators: 560 FE/t of generation behind the 25 tick line. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBGridFourteenGenerators(GameTestHelper helper) {
        runCaseBOnGrid(helper, 14);
    }

    /** Fifteen generators: 600 FE/t, the number the documented build uses. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBGridFifteenGenerators(GameTestHelper helper) {
        runCaseBOnGrid(helper, 15);
    }

    /** Sixteen generators: 640 FE/t, spare capacity over the nominal peak. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBGridSixteenGenerators(GameTestHelper helper) {
        runCaseBOnGrid(helper, 16);
    }

    /**
     * The body all three generator counts share, so the bank size is the only variable.
     *
     * <p>The chain recipes are swapped to 25 ticks for the length of this method (see
     * {@link RecipeSwapForTests}), a real bank of generators, Advanced cable and one basic buffer
     * are wired to the line, and every machine tick is preceded by one grid tick: generators,
     * then cables, then the buffer - the phase order {@code PowerGridGameTests} measures with.
     * Nothing tops a machine up any more: when the grid cannot pay for a tick the machine does
     * not work that tick, the lost time is counted, and a run that still has no output after
     * {@code GRID_RUN_TICK_LIMIT} times its plan fails with the grid's own summary instead of
     * being forced through.</p>
     *
     * <p><b>Limitation of this hierarchy.</b> The harness still executes one machine at a time
     * inside {@code execute}, so the grid only ever serves a single machine: the 600 FE/t peak of
     * five simultaneous stage-15 synthesizers cannot appear here, and a single machine's draw is
     * all these runs can measure. Supply adequacy against the line's <em>total</em> consumption,
     * the fuel behaviour and the FE balance are what this stage establishes; simultaneous
     * operation is the next stage.</p>
     */
    private static void runCaseBOnGrid(GameTestHelper helper, int generatorCount) {
        String label = "B grid " + generatorCount + " gens, from raw, 5x4";
        RecipeSwapForTests.Swap swap = RecipeSwapForTests.install(
                helper.getLevel().getRecipeManager(), PROPOSED_TICKS);
        try {
            helper.assertTrue(swap.isSwapped(),
                    label + ": recipe swap failed: " + swap.swapSummary());
            Probe probe = Probe.build(helper, true, PROPOSED_TICKS)
                    .withMachines(SYNTHESIZERS, CRUSHERS);
            probe.withGrid(helper, generatorCount, BufferTier.BASIC);
            probe.run();
            probe.report(label);
            probe.assertOutputs(helper, label);
            helper.assertTrue(probe.earlyCompletes == 0,
                    label + ": " + probe.earlyCompletes
                            + " run(s) finished before their planned " + PROPOSED_TICKS
                            + " ticks: " + String.join(" | ", probe.earlyTrace));
            helper.assertTrue(probe.meteredFe == probe.scheduleFe,
                    label + ": the machines metered " + probe.meteredFe
                            + " FE while the schedule asks for " + probe.scheduleFe + " FE ("
                            + (probe.meteredFe - probe.scheduleFe) + " FE off)");
            helper.assertTrue(probe.grid.machineReceived >= probe.meteredFe,
                    label + ": the machines spent " + probe.meteredFe + " FE but only received "
                            + probe.grid.machineReceived + " FE from the grid");
            probe.assertGridBalance(helper, label);
        } finally {
            swap.restore();
        }
        helper.assertTrue(swap.isRestored(),
                label + ": the production recipes were not restored: " + swap.restoreSummary());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // case B on a real power grid, hierarchy ii: parallel, every machine every tick
    // ------------------------------------------------------------------

    /** Fourteen generators behind the line while all twelve machines tick every simulated tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBParallelFourteenGenerators(GameTestHelper helper) {
        runCaseBParallelOnGrid(helper, 14);
    }

    /** Fifteen generators: 600 FE/t against the five-synthesizer peak this run measures. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBParallelFifteenGenerators(GameTestHelper helper) {
        runCaseBParallelOnGrid(helper, 15);
    }

    /** Sixteen generators: 640 FE/t of headroom over the same measured peak. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void caseBParallelSixteenGenerators(GameTestHelper helper) {
        runCaseBParallelOnGrid(helper, 16);
    }

    /**
     * The body all three parallel counts share, so the bank size stays the only variable.
     *
     * <p>The order of operations is fixed: the chain recipes are swapped to 25 ticks first, the
     * machines and the grid are placed <em>after</em> that (the synthesizer caches recipes by
     * manager instance, so its first lookup must happen inside the window), then
     * {@link Probe#runParallel} ticks the whole line, and only then the {@code finally} restores
     * - no swap and no restore ever happens inside the measurement. The assertions are the
     * sequential grid run's, plus the hard limit and the exact output count: peak adequacy and
     * starvation are measured here, never asserted.</p>
     */
    private static void runCaseBParallelOnGrid(GameTestHelper helper, int generatorCount) {
        String label = "B parallel " + generatorCount + " gens, from raw, 5x4";
        RecipeSwapForTests.Swap swap = RecipeSwapForTests.install(
                helper.getLevel().getRecipeManager(), PROPOSED_TICKS);
        try {
            helper.assertTrue(swap.isSwapped(),
                    label + ": recipe swap failed: " + swap.swapSummary());
            helper.assertTrue(swap.swappedCount() > 0,
                    label + ": swapped no recipe, so the machines would still run "
                            + PRODUCTION_TICKS + " ticks: " + swap.swapSummary());
            Probe probe = Probe.build(helper, true, PROPOSED_TICKS)
                    .withMachines(SYNTHESIZERS, CRUSHERS);
            probe.withGrid(helper, generatorCount, BufferTier.BASIC);
            probe.runParallel();
            probe.report(label);
            helper.assertTrue(probe.elapsed > 0,
                    label + ": the line did not reach " + TARGET_INGOTS + " ingots within "
                            + HARD_LIMIT + " ticks: " + probe.diagnostic());
            probe.assertOutputs(helper, label);
            helper.assertTrue(probe.produced(IRON_INGOT) == TARGET_INGOTS,
                    label + ": the line produced " + probe.produced(IRON_INGOT)
                            + " ingots instead of exactly " + TARGET_INGOTS + ": "
                            + probe.diagnostic());
            helper.assertTrue(probe.earlyCompletes == 0,
                    label + ": " + probe.earlyCompletes
                            + " run(s) finished before their planned " + PROPOSED_TICKS
                            + " ticks: " + String.join(" | ", probe.earlyTrace));
            helper.assertTrue(probe.meteredFe == probe.scheduleFe,
                    label + ": the machines metered " + probe.meteredFe
                            + " FE while the schedule asks for " + probe.scheduleFe + " FE ("
                            + (probe.meteredFe - probe.scheduleFe) + " FE off)");
            helper.assertTrue(probe.grid.machineReceived >= probe.meteredFe,
                    label + ": the machines spent " + probe.meteredFe + " FE but only received "
                            + probe.grid.machineReceived + " FE from the grid");
            probe.assertGridBalance(helper, label);
        } finally {
            swap.restore();
        }
        helper.assertTrue(swap.isRestored(),
                label + ": the production recipes were not restored: " + swap.restoreSummary());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // shared assertions
    // ------------------------------------------------------------------

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    /** Which machine runs a plan. */
    private enum Kind {
        CRUSHER, ELECTROLYZER, PRECISION_CRUSHER, METAL_PRESS, SYNTHESIZER
    }

    /** One recipe of the chain: its production duration and the duration this case schedules. */
    private record Plan(String id, Kind kind, int order, List<ItemStack> inputs, ItemStack output,
                        int recipeTicks, int timeTicks, int energyPerTick) {}

    /** An output that lands in the buffer on a given tick. */
    private record Completion(int tick, String item, int count) {}

    /** One executed run: which machine ran which plan, when and at what draw. */
    private record Run(Unit unit, String planId, int start, int end, int fePerTick) {}

    /** A placed machine and the tick it becomes free again. */
    private static final class Unit {
        final Kind kind;
        /** Relative position, so the power mesh can be built from the active set. */
        final BlockPos position;
        final ProcessingBlockEntity processor;
        final OreSynthesizerBlockEntity synthesizer;
        boolean active = true;
        int freeAt = 1;
        int busyTicks;
        int idleTicks;
        int idleInputTicks;
        int idleDoneTicks;
        /** Ticks this machine could not work because the grid had no FE for it. */
        int starvedTicks;
        /** Runs that lost at least one tick to the grid being short of FE. */
        int starvedRuns;
        /** Parallel mode: the run in flight, or null while this machine is free. */
        Plan runningPlan;
        /** Parallel mode: the tick the in-flight run was picked on. */
        int runStartTick;
        /** Parallel mode: machine ticks the in-flight run has seen, starved ones included. */
        int workDone;
        /** Parallel mode: machine tick on which the output first appeared (0 = not yet). */
        int workOutputAt;
        /** Parallel mode: the in-flight run lost at least one tick to a short grid. */
        boolean runStarved;
        final List<String> trace = new ArrayList<>();

        Unit(Kind kind, BlockPos position, ProcessingBlockEntity processor,
             OreSynthesizerBlockEntity synthesizer) {
            this.kind = kind;
            this.position = position;
            this.processor = processor;
            this.synthesizer = synthesizer;
        }
    }

    /**
     * One measurement: machines, recipe graph, buffers and the greedy schedule, plus the counters
     * the report needs.
     */
    private static final class Probe {
        private final ServerLevel level;
        private final List<Unit> units = new ArrayList<>();
        private final List<Plan> plans;
        private final Map<String, Integer> remaining = new LinkedHashMap<>();
        private final Map<String, Long> stock = new HashMap<>();
        private final Map<String, Long> made = new HashMap<>();
        private final Map<String, Integer> completed = new HashMap<>();
        private final Map<String, Integer> finishedAt = new LinkedHashMap<>();
        private final List<Completion> pending = new ArrayList<>();
        private final List<int[]> jobs = new ArrayList<>();
        private final List<Run> runs = new ArrayList<>();
        private final Map<String, Integer> startedAt = new LinkedHashMap<>();
        private final Map<String, Long> externalSeeds = new LinkedHashMap<>();
        /** Runs each plan needs, snapshotted before the line starts consuming them. */
        private final Map<String, Integer> required = new LinkedHashMap<>();
        private final int scheduledTicks;
        private int now;
        int elapsed;
        int firstIngotTick;
        /** FE the schedule asks for: {@code duration x FE/t} summed over every run. */
        long scheduleFe;
        /** FE the machines really consumed while they were ticked. */
        long meteredFe;
        /** FE the harness pushed into the machines to keep them fed. */
        long injectedFe;
        /** Machine ticks that really executed a recipe (the production duration of each run). */
        long machineTicks;
        /** Machine ticks the schedule held a machine (the case's duration of each run). */
        long busyTicks;
        /** Runs whose machine wrote its output before its planned duration had elapsed. */
        int earlyCompletes;
        /** The first few of those: plan, tick the output appeared, duration that was planned. */
        final List<String> earlyTrace = new ArrayList<>();
        /** The real generator bank, when {@link #withGrid} wired one to the line. */
        private Grid grid;
        /** Set by {@link #runParallel}: every active machine is ticked every simulated tick. */
        private boolean parallel;
        /** Parallel mode: the highest draw of one tick summed over every machine, and its tick. */
        private int peakTickConsumption;
        private int peakTickConsumptionAt;

        private Probe(ServerLevel level, List<Plan> plans, int scheduledTicks) {
            this.level = level;
            this.plans = plans;
            this.scheduledTicks = scheduledTicks;
        }

        static Probe build(GameTestHelper helper, boolean withCatalystLadder,
                           int scheduledTicks) {
            ServerLevel level = helper.getLevel();
            Map<String, Plan> byOutput = choosePlans(level, withCatalystLadder, scheduledTicks);
            List<Plan> ordered = new ArrayList<>(byOutput.values());
            ordered.sort((left, right) -> left.order() != right.order()
                    ? Integer.compare(left.order(), right.order())
                    : left.id().compareTo(right.id()));

            Probe probe = new Probe(level, ordered, scheduledTicks);
            Map<String, Long> external = new HashMap<>();
            probe.demand(IRON_INGOT, TARGET_INGOTS, external, 0);
            external.forEach(probe::seed);
            probe.externalSeeds.putAll(external);
            probe.required.putAll(probe.remaining);

            for (int index = 0; index < MAX_SYNTHESIZERS; index++) {
                probe.placeSynthesizer(helper, new BlockPos(index % 3, Y, index / 3));
            }
            for (int index = 0; index < MAX_CRUSHERS; index++) {
                probe.placeProcessor(helper, Kind.CRUSHER, new BlockPos(index % 3, Y, 5 + index / 3));
            }
            probe.placeProcessor(helper, Kind.ELECTROLYZER, new BlockPos(0, Y, 4));
            probe.placeProcessor(helper, Kind.PRECISION_CRUSHER, new BlockPos(1, Y, 4));
            probe.placeProcessor(helper, Kind.METAL_PRESS, new BlockPos(2, Y, 4));
            return probe;
        }

        /** Uses only the first {@code synths} synthesizers and {@code crushers} crushers. */
        Probe withMachines(int synths, int crushers) {
            int seenSynths = 0;
            int seenCrushers = 0;
            for (Unit unit : this.units) {
                if (unit.kind == Kind.SYNTHESIZER) {
                    unit.active = seenSynths++ < synths;
                } else if (unit.kind == Kind.CRUSHER) {
                    unit.active = seenCrushers++ < crushers;
                }
            }
            return this;
        }

        /**
         * Wires a real power grid to the line; from then on every machine tick is fed by it.
         *
         * <p>The mesh is derived from the active machines: one Advanced cable directly above each
         * of them, the bridge nodes of {@link #GRID_MESH_EXTRAS}, the generators one level above
         * the mesh and the buffer beside it touching exactly one cable. No generator touches a
         * machine, so every FE a machine receives can only have come through the network - the
         * layout rule {@code PowerGridGameTests} is built on.</p>
         *
         * <p>Call this after {@link #withMachines}: the mesh follows the active set.</p>
         */
        Probe withGrid(GameTestHelper helper, int generatorCount, BufferTier tier) {
            if (this.grid != null) {
                throw new IllegalStateException("the grid is already placed");
            }
            if (generatorCount < 1 || generatorCount > GRID_GENERATOR_SLOTS.length) {
                throw new IllegalArgumentException("no slots for " + generatorCount + " generators");
            }
            this.grid = new Grid(generatorCount);
            this.grid.place(helper, generatorCount, tier);
            return this;
        }

        /**
         * Runs the line until the target is met; returns the tick the last ingot appeared on.
         *
         * <p>A run that starts on tick {@code T} delivers on {@code T + scheduledTicks - 1} and
         * frees its machine on {@code T + scheduledTicks}, exactly like the reference harness, so
         * case A is directly comparable with the documented measurement.</p>
         */
        int run() {
            if (this.grid != null) {
                this.grid.startTotal = this.grid.totalStored();
                this.grid.machineStoredStart = this.storedInMachines();
            }
            for (int tick = 1; tick <= HARD_LIMIT; tick++) {
                this.now = tick;
                this.deliver(tick);
                if (this.produced(IRON_INGOT) >= TARGET_INGOTS) {
                    this.elapsed = tick;
                    return tick;
                }
                for (Unit unit : this.units) {
                    if (!unit.active || unit.freeAt > tick) {
                        continue;
                    }
                    Plan next = this.pick(unit.kind);
                    if (next == null) {
                        unit.idleTicks++;
                        if (this.hasPendingWork(unit.kind)) {
                            unit.idleInputTicks++;
                        } else {
                            unit.idleDoneTicks++;
                        }
                        continue;
                    }
                    this.execute(unit, next, tick);
                    unit.freeAt = tick + next.timeTicks();
                }
            }
            this.elapsed = -1;
            return -1;
        }

        /** Hands over every output whose run has finished by this tick. */
        private void deliver(int tick) {
            while (!this.pending.isEmpty() && this.pending.get(0).tick() <= tick) {
                Completion completion = this.pending.remove(0);
                this.stock.merge(completion.item(), (long) completion.count(), Long::sum);
                this.made.merge(completion.item(), (long) completion.count(), Long::sum);
                if (completion.item().equals(IRON_INGOT) && this.firstIngotTick == 0) {
                    this.firstIngotTick = completion.tick();
                }
            }
        }

        long produced(String item) {
            return this.stock.getOrDefault(item, 0L);
        }

        long made(String item) {
            return this.made.getOrDefault(item, 0L);
        }

        /**
         * Executes one run on the real machine.
         *
         * <p>The machine is ticked its <em>production</em> duration, which is what really validates
         * the output; the schedule occupies it for the case's duration. Energy is metered per tick:
         * what was pushed in, and what the machine did not give back after its tick.</p>
         */
        private void execute(Unit unit, Plan plan, int startTick) {
            for (ItemStack input : plan.inputs()) {
                this.stock.merge(key(input), (long) -input.getCount(), Long::sum);
            }
            this.remaining.merge(plan.id(), -1, Integer::sum);
            this.completed.merge(plan.id(), 1, Integer::sum);
            if (this.remaining.getOrDefault(plan.id(), 0) <= 0) {
                this.finishedAt.putIfAbsent(plan.id(), startTick + plan.timeTicks() - 1);
            }

            if (unit.processor != null) {
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                        ItemStack.EMPTY);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                        ItemStack.EMPTY);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                        plan.inputs().get(0).copy());
            } else {
                for (int slot = 0; slot < OreSynthesizerBlockEntity.SLOT_COUNT; slot++) {
                    unit.synthesizer.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                }
                for (int index = 0; index < plan.inputs().size()
                        && index < OreSynthesizerBlockEntity.INPUT_COUNT; index++) {
                    unit.synthesizer.getInventory().setStackInSlot(index,
                            plan.inputs().get(index).copy());
                }
            }

            int firstOutputTick = 0;
            // Without a grid the harness still tops the machine up itself and ticks it exactly its
            // planned duration. With a grid the supply comes from the bank instead: a starved
            // machine is never forced through - the grid keeps ticking for it until its output
            // appears, and a run that needs more than GRID_RUN_TICK_LIMIT x its plan fails with
            // the grid's summary below.
            int plannedTicks = plan.recipeTicks();
            int maxTicks = this.grid == null ? plannedTicks : plannedTicks * GRID_RUN_TICK_LIMIT;
            int performed = 0;
            boolean starvedInRun = false;
            while (performed < maxTicks
                    && (this.grid == null ? performed < plannedTicks : firstOutputTick == 0)) {
                performed++;
                if (this.grid != null) {
                    this.grid.tick();
                }
                if (unit.processor != null) {
                    int before = unit.processor.getEnergyStoredForTesting();
                    int charged = before;
                    if (this.grid == null) {
                        unit.processor.setEnergyStored(PROCESSOR_BUFFER);
                        charged = unit.processor.getEnergyStoredForTesting();
                        this.injectedFe += charged - before;
                    }
                    BlockState state = unit.processor.getBlockState();
                    ProcessingBlockEntity.serverTick(this.level, unit.processor.getBlockPos(), state,
                            unit.processor);
                    int consumed = (this.grid == null ? charged : before)
                            - unit.processor.getEnergyStoredForTesting();
                    this.meteredFe += consumed;
                    if (this.grid != null) {
                        starvedInRun |= this.grid.observe(unit, consumed,
                                unit.processor.getStatus() == MachineStatus.NO_ENERGY);
                    }
                } else {
                    int before = unit.synthesizer.getEnergyStoredForTesting();
                    int charged = before;
                    if (this.grid == null) {
                        unit.synthesizer.setEnergyStored(SynthesisBalance.CAPACITY);
                        charged = unit.synthesizer.getEnergyStoredForTesting();
                        this.injectedFe += charged - before;
                    }
                    BlockState state = unit.synthesizer.getBlockState();
                    OreSynthesizerBlockEntity.serverTick(this.level, unit.synthesizer.getBlockPos(),
                            state, unit.synthesizer);
                    int consumed = (this.grid == null ? charged : before)
                            - unit.synthesizer.getEnergyStoredForTesting();
                    this.meteredFe += consumed;
                    if (this.grid != null) {
                        starvedInRun |= this.grid.observe(unit, consumed,
                                unit.synthesizer.getStatusForTesting() == MachineStatus.NO_ENERGY);
                    }
                }
                // When the output appears tells whether the machine really needed the planned
                // duration: a machine that finishes early is running a shorter recipe than the
                // schedule believes, which is exactly what a recipe swap must not leave hidden.
                if (firstOutputTick == 0 && !outputSlot(unit).isEmpty()) {
                    firstOutputTick = performed;
                }
            }
            if (starvedInRun) {
                unit.starvedRuns++;
                if (this.grid != null) {
                    this.grid.starvedRuns++;
                }
            }
            if (this.grid != null && firstOutputTick == 0) {
                throw new IllegalStateException("grid run of " + plan.id() + " at tick " + startTick
                        + " produced nothing in " + performed + " ticks (limit " + maxTicks
                        + "), last status " + statusOf(unit) + ": " + this.grid.summary()
                        + "; trace=" + String.join(" / ", unit.trace));
            }
            if (firstOutputTick > 0 && firstOutputTick < plan.recipeTicks()) {
                this.earlyCompletes++;
                if (this.earlyTrace.size() < 8) {
                    this.earlyTrace.add(shortName(plan.id()) + " out@t" + firstOutputTick + "/"
                            + plan.recipeTicks() + " scheduled at t" + startTick);
                }
            }
            this.machineTicks += plan.recipeTicks();

            ItemStack output;
            if (unit.processor != null) {
                output = unit.processor.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                        ItemStack.EMPTY);
            } else {
                output = unit.synthesizer.getInventory()
                        .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
                unit.synthesizer.getInventory().setStackInSlot(
                        OreSynthesizerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
            }

            unit.busyTicks += plan.timeTicks();
            this.busyTicks += plan.timeTicks();
            this.scheduleFe += (long) plan.timeTicks() * plan.energyPerTick();
            this.jobs.add(new int[]{startTick, startTick + plan.timeTicks(),
                    plan.energyPerTick()});
            this.runs.add(new Run(unit, plan.id(), startTick, startTick + plan.timeTicks(),
                    plan.energyPerTick()));
            this.startedAt.putIfAbsent(plan.id(), startTick);
            if (unit.trace.size() >= 8) {
                unit.trace.remove(0);
            }
            unit.trace.add("t" + startTick + " " + plan.id() + " -> " + output);
            if (output.isEmpty() || !key(output).equals(key(plan.output()))
                    || output.getCount() != plan.output().getCount()) {
                throw new IllegalStateException("run of " + plan.id() + " at tick " + startTick
                        + " produced " + output + " instead of " + plan.output()
                        + "; trace=" + String.join(" / ", unit.trace));
            }
            this.pending.add(new Completion(startTick + plan.timeTicks() - 1, key(output),
                    output.getCount()));
            this.pending.sort((left, right) -> Integer.compare(left.tick(), right.tick()));
        }

        // ------------------------------------------------------------------
        // hierarchy ii: parallel, every machine every simulated tick
        // ------------------------------------------------------------------

        /**
         * The parallel run: one simulated tick advances everything at once in a fixed order -
         * grid, delivery, one pick turn for every machine, then one real {@code serverTick} for
         * every machine.
         *
         * <p>Everything the sequential {@link #run()} measures is measured again here: this is
         * still one synchronous call with no game tick boundary in it, so the recipe swap done
         * by the caller can neither leak to another test nor be observed changing mid-run. The
         * only difference is the schedule - a machine no longer finishes its whole run before
         * the next one is looked at, so several machines can draw on the same tick and the peak
         * below is a per-tick <em>sum</em>, not a single machine's draw.</p>
         *
         * <p>Completion keeps two clocks apart on purpose: a run whose output appears on work
         * tick {@code F} is extracted on {@code F}, its output joins the stock through
         * {@link #deliver} on {@code F + 1}, and the machine is free from {@code F + 1} - one
         * honest tick of handover, which also makes it impossible to deliver a starved run's
         * output before it exists. When the target lands, in-flight runs are drained first
         * (normally there are none - the demand graph is exact) so the books still close; hitting
         * {@link #HARD_LIMIT} fails instead of succeeding.</p>
         */
        int runParallel() {
            this.parallel = true;
            if (this.grid != null) {
                this.grid.startTotal = this.grid.totalStored();
                this.grid.machineStoredStart = this.storedInMachines();
            }
            int targetTick = 0;
            boolean draining = false;
            for (int tick = 1; tick <= HARD_LIMIT; tick++) {
                this.now = tick;
                // Fixed phase order: the grid feeds this tick's machine ticks, never the reverse.
                if (this.grid != null) {
                    this.grid.tick();
                }
                this.deliver(tick);
                if (!draining && this.produced(IRON_INGOT) >= TARGET_INGOTS) {
                    draining = true;
                    targetTick = tick;
                }
                if (!draining) {
                    for (Unit unit : this.units) {
                        if (!unit.active || unit.freeAt > tick || unit.runningPlan != null) {
                            continue;
                        }
                        Plan next = this.pick(unit.kind);
                        if (next == null) {
                            unit.idleTicks++;
                            if (this.hasPendingWork(unit.kind)) {
                                unit.idleInputTicks++;
                            } else {
                                unit.idleDoneTicks++;
                            }
                            continue;
                        }
                        this.startRun(unit, next, tick);
                    }
                }
                long draw = 0;
                for (Unit unit : this.units) {
                    if (unit.active) {
                        draw += this.workOneTick(unit, tick);
                    }
                }
                if (draw > this.peakTickConsumption) {
                    this.peakTickConsumption = (int) draw;
                    this.peakTickConsumptionAt = tick;
                }
                if (this.grid != null) {
                    this.grid.observePeak(draw);
                }
                if (draining && this.runningCount() == 0) {
                    this.elapsed = targetTick;
                    return targetTick;
                }
            }
            this.elapsed = -1;
            return -1;
        }

        /** How many machines are mid-run right now. */
        private int runningCount() {
            int count = 0;
            for (Unit unit : this.units) {
                if (unit.active && unit.runningPlan != null) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Locks one run: its inputs leave the stock and the machine is loaded exactly like
         * {@link #execute} loads it - but only from the pick turn, after the swap has been
         * installed and the machines were placed, so the recipe the machine finds is the
         * swapped one and no swap or restore ever happens inside the measurement.
         */
        private void startRun(Unit unit, Plan plan, int startTick) {
            for (ItemStack input : plan.inputs()) {
                this.stock.merge(key(input), (long) -input.getCount(), Long::sum);
            }
            this.remaining.merge(plan.id(), -1, Integer::sum);
            this.completed.merge(plan.id(), 1, Integer::sum);

            if (unit.processor != null) {
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                        ItemStack.EMPTY);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                        ItemStack.EMPTY);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                        plan.inputs().get(0).copy());
            } else {
                for (int slot = 0; slot < OreSynthesizerBlockEntity.SLOT_COUNT; slot++) {
                    unit.synthesizer.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                }
                for (int index = 0; index < plan.inputs().size()
                        && index < OreSynthesizerBlockEntity.INPUT_COUNT; index++) {
                    unit.synthesizer.getInventory().setStackInSlot(index,
                            plan.inputs().get(index).copy());
                }
            }

            unit.runningPlan = plan;
            unit.runStartTick = startTick;
            unit.workDone = 0;
            unit.workOutputAt = 0;
            unit.runStarved = false;
            this.startedAt.putIfAbsent(plan.id(), startTick);
            if (unit.trace.size() >= 8) {
                unit.trace.remove(0);
            }
            unit.trace.add("t" + startTick + " " + plan.id() + " started");
        }

        /**
         * One real {@code serverTick} of one machine - running or idle - metered before and
         * after, followed by the completion and stuck-run checks of a running machine. Idle
         * machines are ticked with an empty inventory and cost nothing, so the per-tick sum the
         * peak is taken from is the whole picture, not a sample.
         */
        private long workOneTick(Unit unit, int tick) {
            int before = this.storedOf(unit);
            int charged = before;
            if (this.grid == null) {
                // No grid: the harness feeds the machine itself, exactly like execute does.
                if (unit.processor != null) {
                    unit.processor.setEnergyStored(PROCESSOR_BUFFER);
                } else {
                    unit.synthesizer.setEnergyStored(SynthesisBalance.CAPACITY);
                }
                charged = this.storedOf(unit);
                this.injectedFe += charged - before;
            }
            if (unit.processor != null) {
                BlockState state = unit.processor.getBlockState();
                ProcessingBlockEntity.serverTick(this.level, unit.processor.getBlockPos(), state,
                        unit.processor);
            } else {
                BlockState state = unit.synthesizer.getBlockState();
                OreSynthesizerBlockEntity.serverTick(this.level, unit.synthesizer.getBlockPos(),
                        state, unit.synthesizer);
            }
            int consumed = charged - this.storedOf(unit);
            this.meteredFe += consumed;

            if (unit.runningPlan == null) {
                return consumed;
            }
            Plan plan = unit.runningPlan;
            if (this.grid != null) {
                // Starvation is only meaningful for a run that wanted to work this tick.
                unit.runStarved |= this.grid.observeStarved(unit,
                        statusOf(unit) == MachineStatus.NO_ENERGY);
            }
            unit.workDone++;
            // When the output appears tells whether the machine really needed the planned
            // duration - the same early-finish tripwire the sequential run uses.
            if (unit.workOutputAt == 0 && !outputSlot(unit).isEmpty()) {
                unit.workOutputAt = unit.workDone;
            }
            if (unit.workOutputAt > 0) {
                this.finishRun(unit, tick);
            } else if (unit.workDone
                    >= plan.recipeTicks() * (this.grid == null ? 1 : GRID_RUN_TICK_LIMIT)) {
                throw new IllegalStateException("run of " + plan.id() + " at tick "
                        + unit.runStartTick + " produced nothing in " + unit.workDone + " ticks"
                        + (this.grid == null ? "" : ", grid " + this.grid.summary())
                        + "; trace=" + String.join(" / ", unit.trace));
            }
            return consumed;
        }

        /** Extracts a finished run's output, books it, and frees the machine from tick + 1. */
        private void finishRun(Unit unit, int finishTick) {
            Plan plan = unit.runningPlan;
            if (unit.workOutputAt < plan.recipeTicks()) {
                this.earlyCompletes++;
                if (this.earlyTrace.size() < 8) {
                    this.earlyTrace.add(shortName(plan.id()) + " out@t" + unit.workOutputAt + "/"
                            + plan.recipeTicks() + " scheduled at t" + unit.runStartTick);
                }
            }
            this.machineTicks += plan.recipeTicks();

            ItemStack output;
            if (unit.processor != null) {
                output = unit.processor.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                        ItemStack.EMPTY);
                // The run is over: never leave an input behind for an idle tick to restart on.
                unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                        ItemStack.EMPTY);
            } else {
                output = unit.synthesizer.getInventory()
                        .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
                unit.synthesizer.getInventory().setStackInSlot(
                        OreSynthesizerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
            }

            int occupied = finishTick + 1 - unit.runStartTick;
            unit.busyTicks += occupied;
            this.busyTicks += occupied;
            this.scheduleFe += (long) plan.timeTicks() * plan.energyPerTick();
            this.jobs.add(new int[]{unit.runStartTick, finishTick + 1, plan.energyPerTick()});
            this.runs.add(new Run(unit, plan.id(), unit.runStartTick, finishTick + 1,
                    plan.energyPerTick()));
            if (this.remaining.getOrDefault(plan.id(), 0) <= 0) {
                this.finishedAt.putIfAbsent(plan.id(), finishTick);
            }
            if (unit.trace.size() >= 8) {
                unit.trace.remove(0);
            }
            unit.trace.add("t" + unit.runStartTick + " " + plan.id() + " -> " + output
                    + " done t" + finishTick);
            if (output.isEmpty() || !key(output).equals(key(plan.output()))
                    || output.getCount() != plan.output().getCount()) {
                throw new IllegalStateException("run of " + plan.id() + " at tick "
                        + unit.runStartTick + " produced " + output + " instead of "
                        + plan.output() + "; trace=" + String.join(" / ", unit.trace));
            }
            // The output joins the stock through deliver() on the next tick; the machine is
            // free from the next tick. Two clocks, deliberately kept apart.
            this.pending.add(new Completion(finishTick, key(output), output.getCount()));
            this.pending.sort((left, right) -> Integer.compare(left.tick(), right.tick()));
            if (unit.runStarved) {
                unit.starvedRuns++;
                if (this.grid != null) {
                    this.grid.starvedRuns++;
                }
            }
            unit.runningPlan = null;
            unit.freeAt = finishTick + 1;
        }

        private Plan pick(Kind kind) {
            for (Plan plan : this.plans) {
                if (plan.kind() != kind || this.remaining.getOrDefault(plan.id(), 0) <= 0) {
                    continue;
                }
                if (this.has(plan)) {
                    return plan;
                }
            }
            return null;
        }

        /** Work of this machine type is still scheduled but its inputs have not arrived yet. */
        private boolean hasPendingWork(Kind kind) {
            for (Plan plan : this.plans) {
                if (plan.kind() == kind && this.remaining.getOrDefault(plan.id(), 0) > 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean has(Plan plan) {
            for (ItemStack input : plan.inputs()) {
                if (this.produced(key(input)) < input.getCount()) {
                    return false;
                }
            }
            return true;
        }

        // ------------------------------------------------------------------
        // metrics
        // ------------------------------------------------------------------

        /** Highest simultaneous draw of the schedule and its first tick, as {FE/t, tick}. */
        int[] peakFeWithTick() {
            int[] draw = new int[this.now + 2];
            for (Run run : this.runs) {
                draw[Math.max(1, run.start())] += run.fePerTick();
                if (run.end() < draw.length) {
                    draw[run.end()] -= run.fePerTick();
                }
            }
            return peakOf(draw);
        }

        /** How many runs were in flight on a tick, and which plans they were. */
        String runsAt(int tick) {
            int count = 0;
            Map<String, Integer> byPlan = new LinkedHashMap<>();
            for (Run run : this.runs) {
                if (run.start() <= tick && tick < run.end()) {
                    count++;
                    byPlan.merge(shortName(run.planId()), 1, Integer::sum);
                }
            }
            return count + " run(s) " + byPlan;
        }

        private static String shortName(String planId) {
            int slash = planId.indexOf('/');
            return slash >= 0 ? planId.substring(slash + 1) : planId;
        }

        /** Highest number of synthesizers busy at the same time, as {count, tick}. */
        int[] peakSynthesizers() {
            int[] count = new int[this.now + 2];
            for (Run run : this.runs) {
                if (run.unit().kind != Kind.SYNTHESIZER) {
                    continue;
                }
                count[Math.max(1, run.start())]++;
                if (run.end() < count.length) {
                    count[run.end()]--;
                }
            }
            return peakOf(count);
        }

        /** Highest number of stage-15 runs busy at the same time, as {count, tick}. */
        int[] peakStageFifteen() {
            int[] count = new int[this.now + 2];
            for (Run run : this.runs) {
                if (!run.planId().equals(STAGE_FIFTEEN)) {
                    continue;
                }
                count[Math.max(1, run.start())]++;
                if (run.end() < count.length) {
                    count[run.end()]--;
                }
            }
            return peakOf(count);
        }

        private static int[] peakOf(int[] delta) {
            int current = 0;
            int peak = 0;
            int peakTick = 0;
            for (int tick = 1; tick < delta.length; tick++) {
                current += delta[tick];
                if (current > peak) {
                    peak = current;
                    peakTick = tick;
                }
            }
            return new int[]{peak, peakTick};
        }

        int completed(String idPart) {
            return this.completed.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(idPart))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }

        long scheduleTotalFe() {
            return this.scheduleFe;
        }

        /** Idle split, as "kind: busy/idle(input=x done=y) ". */
        String busySummary() {
            StringBuilder text = new StringBuilder();
            Map<Kind, int[]> totals = new LinkedHashMap<>();
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                int[] entry = totals.computeIfAbsent(unit.kind, key -> new int[4]);
                entry[0] += unit.busyTicks;
                entry[1] += unit.idleTicks;
                entry[2] += unit.idleInputTicks;
                entry[3] += unit.idleDoneTicks;
            }
            totals.forEach((kind, value) -> text.append(kind).append(": busy=").append(value[0])
                    .append(" idle=").append(value[1]).append("(input=").append(value[2])
                    .append(" done=").append(value[3]).append(" feStarved=0 outputFull=0) "));
            return text.toString();
        }

        /** Tick at which each stage first started and finished, as "s7=.. s15=..". */
        String stageTimeline() {
            StringBuilder starts = new StringBuilder();
            StringBuilder ends = new StringBuilder();
            for (int stage = 7; stage <= 15; stage++) {
                String id = "industrialcore:oreline_s" + stage + "_iron";
                Integer start = this.startedAt.get(id);
                Integer end = this.finishedAt.get(id);
                if (start != null) {
                    starts.append(starts.isEmpty() ? "" : " ").append('s').append(stage).append('=')
                            .append(start);
                }
                if (end != null) {
                    ends.append(ends.isEmpty() ? "" : " ").append('s').append(stage).append('=')
                            .append(end);
                }
            }
            return "starts[" + starts + "] ends[" + ends + "]";
        }

        String externalSeedSummary() {
            StringBuilder text = new StringBuilder();
            this.externalSeeds.forEach((item, count) -> text.append(shortName(item)).append('=')
                    .append(count).append(' '));
            return text.toString().trim();
        }

        /** Schedule figure, and the machine-metered figure behind it. */
        String energySummary() {
            if (this.grid != null) {
                // The harness injects nothing here: every FE the machines spent came from the bank.
                return String.format(Locale.ROOT,
                        "schedule=%d metered=%d injected=%d gridBalanceError=%d",
                        this.scheduleFe, this.meteredFe, this.injectedFe,
                        this.grid.balanceError());
            }
            long remaining = 0;
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                if (unit.processor != null) {
                    remaining += unit.processor.getEnergyStoredForTesting();
                } else if (unit.synthesizer != null) {
                    remaining += unit.synthesizer.getEnergyStoredForTesting();
                }
            }
            long balance = this.injectedFe - this.meteredFe - remaining;
            return String.format(Locale.ROOT,
                    "schedule=%d metered=%d injected=%d leftInMachines=%d balanceError=%d",
                    this.scheduleFe, this.meteredFe, this.injectedFe, remaining, balance);
        }

        void report(String label) {
            int[] peakDraw = this.peakFeWithTick();
            int[] peakSynths = this.peakSynthesizers();
            int[] peakStage15 = this.peakStageFifteen();
            double avgAll = this.elapsed > 0 ? this.scheduleFe / (double) this.elapsed : 0;
            double avgBusy = this.busyTicks > 0 ? this.scheduleFe / (double) this.busyTicks : 0;
            IndustrialCore.LOGGER.info(
                    "[timing] {}: elapsed={} ticks ({} s) firstIngot={} scheduleFe={} "
                            + "avgFEperTickAllJobs={} avgFEperTickBusy={} peak={} FE/t at tick {} "
                            + "(peak synthesizers {} at {}, peak stage15 {} at {}) | {}",
                    label, this.elapsed, seconds(this.elapsed), this.firstIngotTick, this.scheduleFe,
                    String.format(Locale.ROOT, "%.1f", avgAll),
                    String.format(Locale.ROOT, "%.1f", avgBusy), peakDraw[0], peakDraw[1],
                    peakSynths[0], peakSynths[1], peakStage15[0], peakStage15[1],
                    runsAt(peakDraw[1]));
            IndustrialCore.LOGGER.info(
                    "[timing] {}: energy[{}] machineTicks={} scheduleBusy={} | {}",
                    label, energySummary(), this.machineTicks, this.busyTicks, busySummary());
            IndustrialCore.LOGGER.info(
                    "[timing] {}: machineDuration[runs={} finishedEarly={}{}]",
                    label, this.runs.size(), this.earlyCompletes,
                    this.earlyTrace.isEmpty() ? ""
                            : " " + String.join(" | ", this.earlyTrace));
            IndustrialCore.LOGGER.info(
                    "[timing] {}: ingots={} s15runs={} catalystsMade={} catalystLeft={} "
                            + "dustCrushed={} oreLineRuns={} ladderRuns={} | {} | external seeds: {}",
                    label, this.produced(IRON_INGOT), this.completed("oreline_s15_"),
                    this.made(ULTIMATE), this.produced(ULTIMATE), this.completed("crush_raw_"),
                    this.completed("oreline_"), this.completed("synthesis_"), this.stageTimeline(),
                    externalSeedSummary());            IndustrialCore.LOGGER.info(
                    "[timing] {}: used plans outside the 10 tick set: {}",
                    label, usedRecipeSummary());
            if (this.grid != null) {
                IndustrialCore.LOGGER.info("[timing] {}: grid[{}]", label, gridSummary());
                IndustrialCore.LOGGER.info("[timing] {}: fuel[{}]", label, fuelSummary());
            }
            if (this.parallel) {
                IndustrialCore.LOGGER.info(
                        "[timing] {}: parallel[peakConsumption={} FE/t at tick {} "
                                + "schedulePeak={} FE/t at tick {} runs={} | {}]",
                        label, this.peakTickConsumption, this.peakTickConsumptionAt,
                        peakDraw[0], peakDraw[1], this.runs.size(), unitTimeline());
            }
        }

        void assertOutputs(GameTestHelper helper, String label) {
            helper.assertTrue(this.elapsed > 0, label + " never produced " + TARGET_INGOTS
                    + " ingots: " + diagnostic());
            helper.assertTrue(this.produced(IRON_INGOT) >= TARGET_INGOTS,
                    label + " stopped at " + this.produced(IRON_INGOT) + " ingots: " + diagnostic());
            helper.assertTrue(this.completed("oreline_s15_") == TARGET_INGOTS / 64,
                    label + " ran stage 15 " + this.completed("oreline_s15_") + " times instead of "
                            + (TARGET_INGOTS / 64) + ": " + diagnostic());
            if (this.completed("synthesis_") > 0) {
                helper.assertTrue(this.made(ULTIMATE) == TARGET_INGOTS / 64,
                        label + " crafted " + this.made(ULTIMATE) + " catalysts instead of "
                                + (TARGET_INGOTS / 64) + ": " + diagnostic());
            }
        }

        /** The harness feeds the machines itself, so its own books must close exactly. */
        void assertEnergyBalance(GameTestHelper helper, String label) {
            long remaining = 0;
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                remaining += unit.processor != null ? unit.processor.getEnergyStoredForTesting()
                        : unit.synthesizer.getEnergyStoredForTesting();
            }
            long error = this.injectedFe - this.meteredFe - remaining;
            helper.assertTrue(error == 0, label + " energy books are off by " + error
                    + " FE (injected=" + this.injectedFe + " metered=" + this.meteredFe
                    + " left=" + remaining + ")");
        }

        /**
         * The grid's own books. Creation and consumption are read from the phases themselves:
         * generators are the only phase that can create FE, cables and the buffer only move it,
         * and a machine's own tick is the only place FE disappears.
         */
        void assertGridBalance(GameTestHelper helper, String label) {
            Grid grid = this.grid;
            helper.assertTrue(grid.cablePhaseDelta == 0,
                    label + ": the cable phase changed the stored total by " + grid.cablePhaseDelta
                            + " FE, so the network is not lossless");
            helper.assertTrue(grid.bufferPhaseDelta == 0,
                    label + ": the buffer phase changed the stored total by "
                            + grid.bufferPhaseDelta + " FE, so the buffer is not lossless");
            helper.assertTrue(grid.balanceError() == 0,
                    label + ": the grid balance is off by " + grid.balanceError() + " FE (start="
                            + grid.startTotal + " generated=" + grid.generated + " end="
                            + grid.totalStored() + " consumed=" + this.meteredFe + ")");
            int machinesEnd = this.storedInMachines();
            helper.assertTrue((long) grid.machineStoredStart + grid.machineReceived
                            - this.meteredFe == machinesEnd,
                    label + ": the machines' own books are off (start=" + grid.machineStoredStart
                            + " received=" + grid.machineReceived + " consumed=" + this.meteredFe
                            + " end=" + machinesEnd + ")");
        }

        /** Flows, peaks, starvation and the balance - measured phase by phase, nothing inferred. */
        String gridSummary() {
            Grid grid = this.grid;
            return String.format(Locale.ROOT,
                    "gens=%d cables=%d buffer=%dFE ticks=%d generated=%d cableOut=%d "
                            + "machines[received=%d spent=%d %d->%d] "
                            + "buffer[charged=%d discharged=%d now=%d] "
                            + "peaks[generation=%d/t delivery=%d/t consumption=%d/t] "
                            + "starved=%d ticks in %d runs {%s} | "
                            + "balance %d + %d - %d - %d = %d",
                    grid.generators.size(), grid.cables.size(),
                    grid.buffer == null ? 0 : grid.buffer.getEnergyStorage().getMaxEnergyStored(),
                    grid.ticks, grid.generated, grid.cableOut, grid.machineReceived, this.meteredFe,
                    grid.machineStoredStart, this.storedInMachines(), grid.bufferCharged,
                    grid.bufferDischarged, grid.bufferStored(), grid.peakGeneration,
                    grid.peakDelivery, grid.peakConsumption, grid.starvedTicks, grid.starvedRuns,
                    starvedSummary(), grid.startTotal, grid.generated, grid.totalStored(),
                    this.meteredFe, grid.balanceError());
        }

        /** What the bank really burned, and whether it burned while nothing needed the FE. */
        String fuelSummary() {
            Grid grid = this.grid;
            int coalEnd = grid.coal();
            int generation = com.industrialcore.machine.GenerationKind.THERMAL.output();
            return String.format(Locale.ROOT,
                    "coal %d -> %d (used %d), burnTicks used=%d remaining=%d, generated=%d FE "
                            + "(burnTicks x %d FE/t = %d), gridTicks=%d, noGeneration=%d, "
                            + "pausedWithFuel=%d",
                    grid.coalStart, coalEnd, grid.coalStart - coalEnd, grid.burnTicksUsed,
                    grid.burnTimeSum(), grid.generated, generation,
                    grid.burnTicksUsed * generation, grid.ticks, grid.ticksWithoutGeneration,
                    grid.ticksPausedWithFuel);
        }

        /** Per machine: how many ticks the grid cost it, and how many runs were hit. */
        String starvedSummary() {
            StringBuilder text = new StringBuilder();
            Map<Kind, Integer> seen = new LinkedHashMap<>();
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                int number = seen.merge(unit.kind, 1, Integer::sum);
                if (unit.starvedTicks > 0) {
                    text.append(unit.kind).append(number).append(" starved=")
                            .append(unit.starvedTicks).append("t in ").append(unit.starvedRuns)
                            .append(" runs; ");
                }
            }
            return text.isEmpty() ? "none" : text.toString().trim();
        }

        /** Parallel mode, per machine: run count and the real first start / last finish. */
        String unitTimeline() {
            StringBuilder text = new StringBuilder();
            Map<Unit, int[]> seen = new LinkedHashMap<>();
            for (Run run : this.runs) {
                int[] entry = seen.computeIfAbsent(run.unit(),
                        key -> new int[]{run.start(), run.end(), 0});
                entry[0] = Math.min(entry[0], run.start());
                entry[1] = Math.max(entry[1], run.end());
                entry[2]++;
            }
            Map<Kind, Integer> numbers = new LinkedHashMap<>();
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                int number = numbers.merge(unit.kind, 1, Integer::sum);
                int[] entry = seen.get(unit);
                text.append(unit.kind).append(number).append(" @(")
                        .append(unit.position.getX()).append(',').append(unit.position.getY())
                        .append(',').append(unit.position.getZ()).append(") ");
                if (entry == null) {
                    text.append("no run; ");
                } else {
                    text.append("runs=").append(entry[2]).append(" start=t").append(entry[0])
                            .append(" end=t").append(entry[1]).append("; ");
                }
            }
            return text.toString().trim();
        }

        // ------------------------------------------------------------------
        // the power grid
        // ------------------------------------------------------------------

        /**
         * A real generator bank, Advanced cable mesh and one buffer, ticked once before every
         * machine tick: the supply side of the run, phase by phase.
         *
         * <p>Phases follow {@code PowerGridGameTests}: generators (the only phase that can create
         * FE), cables (redistribution only), buffer (redistribution only), then the machine's own
         * tick (the only consumption). Reading the stored total between the phases turns every
         * number in the report into a measurement instead of an inference.</p>
         *
         * <p>The grid advances only while a machine is being worked on: this harness runs the whole
         * line inside one game tick, so idle schedule time cannot advance it. That is a property of
         * this hierarchy, not of the mod - what it measures is whether the bank can pay for the
         * line's real consumption tick by tick, plus the fuel behaviour behind it.</p>
         */
        private final class Grid {
            final List<GenerationBlockEntity> generators = new ArrayList<>();
            final List<CableBlockEntity> cables = new ArrayList<>();
            EnergyBufferBlockEntity buffer;
            /** Burn time of each generator before its tick, so the used burn ticks are exact. */
            private final int[] burnScratch;
            int coalStart;
            int ticks;
            int machineStoredStart;
            int starvedTicks;
            int starvedRuns;
            int peakGeneration;
            int peakDelivery;
            int peakConsumption;
            long generated;
            long cableOut;
            long machineReceived;
            long bufferCharged;
            long bufferDischarged;
            long burnTicksUsed;
            long ticksWithoutGeneration;
            long ticksPausedWithFuel;
            long cablePhaseDelta;
            long bufferPhaseDelta;
            long startTotal;

            Grid(int generatorCount) {
                this.burnScratch = new int[generatorCount];
            }

            /** A cable over every active machine, the extra nodes, then the buffer and the bank. */
            void place(GameTestHelper helper, int generatorCount, BufferTier tier) {
                for (Unit unit : Probe.this.units) {
                    if (unit.active) {
                        this.cables.add(Probe.this.placeCable(helper, unit.position.above()));
                    }
                }
                for (BlockPos extra : GRID_MESH_EXTRAS) {
                    this.cables.add(Probe.this.placeCable(helper, extra));
                }
                this.buffer = Probe.this.placeBuffer(helper, GRID_BUFFER_POS, tier);
                for (int index = 0; index < generatorCount; index++) {
                    this.generators.add(
                            Probe.this.placeGenerator(helper, GRID_GENERATOR_SLOTS[index]));
                }
                this.coalStart = this.coal();
            }

            /** One grid tick: generators, then cables, then the buffer. */
            void tick() {
                int index = 0;
                for (GenerationBlockEntity generator : this.generators) {
                    this.burnScratch[index++] = generator.getBurnTime();
                }
                int machinesStart = Probe.this.storedInMachines();
                int totalBefore = this.totalStored();
                int bufferStart = this.bufferStored();
                for (GenerationBlockEntity generator : this.generators) {
                    GenerationBlockEntity.serverTick(Probe.this.level, generator.getBlockPos(),
                            generator.getBlockState(), generator);
                }
                int afterGenerators = this.totalStored();
                long madeThisTick = afterGenerators - totalBefore;
                this.generated += madeThisTick;
                index = 0;
                for (GenerationBlockEntity generator : this.generators) {
                    int before = this.burnScratch[index++];
                    int after = generator.getBurnTime();
                    if (after < before) {
                        this.burnTicksUsed += before - after;
                    } else if (after > before) {
                        // A fresh coal started during this tick: its full length minus what is
                        // left of it now is the part this tick really burned.
                        this.burnTicksUsed += generator.getBurnTimeTotal() - after;
                    }
                }
                if (madeThisTick <= 0) {
                    this.ticksWithoutGeneration++;
                    if (this.burnTimeSum() > 0) {
                        // Fuel on board, a burn in progress, nothing produced: the bank is full
                        // and paused, so these ticks cost no coal at all.
                        this.ticksPausedWithFuel++;
                    }
                }
                if (madeThisTick > this.peakGeneration) {
                    this.peakGeneration = (int) madeThisTick;
                }

                int machinesBefore = Probe.this.storedInMachines();
                int bufferBeforeCables = this.bufferStored();
                for (CableBlockEntity cable : this.cables) {
                    CableBlockEntity.serverTick(Probe.this.level, cable.getBlockPos(),
                            cable.getBlockState(), cable);
                }
                int afterCables = this.totalStored();
                this.cablePhaseDelta += afterCables - afterGenerators;
                int machinesAfterCables = Probe.this.storedInMachines();
                int bufferAfterCables = this.bufferStored();
                int delivered = (machinesAfterCables - machinesBefore)
                        + (bufferAfterCables - bufferBeforeCables);
                this.cableOut += delivered;
                this.bufferCharged += Math.max(0, bufferAfterCables - bufferStart);
                if (delivered > this.peakDelivery) {
                    this.peakDelivery = delivered;
                }

                if (this.buffer != null) {
                    EnergyBufferBlockEntity.serverTick(Probe.this.level, this.buffer.getBlockPos(),
                            this.buffer.getBlockState(), this.buffer);
                }
                int afterBuffer = this.totalStored();
                this.bufferPhaseDelta += afterBuffer - afterCables;
                this.bufferDischarged += Math.max(0, bufferAfterCables - this.bufferStored());
                // Whatever the machines gained during any phase of this tick is what the grid
                // really delivered to them, whichever component pushed it.
                this.machineReceived += Probe.this.storedInMachines() - machinesStart;
                this.ticks++;
            }

            /**
             * One machine tick: how much it drew and whether it had to stop for want of FE.
             * Returns true when the tick was starved, so a run can count itself affected once.
             */
            boolean observe(Unit unit, int consumed, boolean noEnergy) {
                if (consumed > this.peakConsumption) {
                    this.peakConsumption = consumed;
                }
                if (!noEnergy) {
                    return false;
                }
                unit.starvedTicks++;
                this.starvedTicks++;
                return true;
            }

            /** Parallel mode: starvation only - the peak is the per-tick aggregate below. */
            boolean observeStarved(Unit unit, boolean noEnergy) {
                if (!noEnergy) {
                    return false;
                }
                unit.starvedTicks++;
                this.starvedTicks++;
                return true;
            }

            /** Parallel mode: the peak draw is what every machine drew on the same tick. */
            void observePeak(long drawThisTick) {
                if (drawThisTick > this.peakConsumption) {
                    this.peakConsumption = (int) drawThisTick;
                }
            }

            int storedInGenerators() {
                int total = 0;
                for (GenerationBlockEntity generator : this.generators) {
                    total += generator.getEnergyStorage().getEnergyStored();
                }
                return total;
            }

            int storedInCables() {
                int total = 0;
                for (CableBlockEntity cable : this.cables) {
                    total += cable.getEnergyStorage().getEnergyStored();
                }
                return total;
            }

            int bufferStored() {
                return this.buffer == null ? 0 : this.buffer.getEnergyStoredForTesting();
            }

            int coal() {
                int total = 0;
                for (GenerationBlockEntity generator : this.generators) {
                    total += generator.getInventory()
                            .getStackInSlot(GenerationBlockEntity.FUEL_SLOT).getCount();
                }
                return total;
            }

            int burnTimeSum() {
                int total = 0;
                for (GenerationBlockEntity generator : this.generators) {
                    total += generator.getBurnTime();
                }
                return total;
            }

            /** Everything that can hold FE here: bank, cables, buffer and every machine. */
            int totalStored() {
                int total = this.storedInGenerators() + this.storedInCables() + this.bufferStored();
                for (Unit unit : Probe.this.units) {
                    total += Probe.this.storedOf(unit);
                }
                return total;
            }

            /** Created minus spent minus still stored: must be exactly zero. */
            long balanceError() {
                return this.startTotal + this.generated - this.totalStored()
                        - Probe.this.meteredFe;
            }

            /** Short form for a run that never produced its output. */
            String summary() {
                return "ticks=" + this.ticks + " generated=" + this.generated + " starved="
                        + this.starvedTicks + " coal=" + this.coalStart + "->" + this.coal()
                        + " balanceError=" + this.balanceError();
            }
        }

        /** The output slot of whichever machine ran the plan. */
        private static ItemStack outputSlot(Unit unit) {
            return unit.processor != null
                    ? unit.processor.getInventory()
                            .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT)
                    : unit.synthesizer.getInventory()
                            .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
        }

        private static MachineStatus statusOf(Unit unit) {
            return unit.processor != null ? unit.processor.getStatus()
                    : unit.synthesizer.getStatusForTesting();
        }

        /** FE held by one placed machine, active or not. */
        private int storedOf(Unit unit) {
            return unit.processor != null ? unit.processor.getEnergyStoredForTesting()
                    : unit.synthesizer.getEnergyStoredForTesting();
        }

        /** FE held by every placed machine: the grid's balance needs the idle ones too. */
        int storedInMachines() {
            int total = 0;
            for (Unit unit : this.units) {
                total += storedOf(unit);
            }
            return total;
        }

        String diagnostic() {
            StringBuilder text = new StringBuilder();
            text.append("elapsed=").append(this.elapsed)
                    .append(" ingots=").append(this.produced(IRON_INGOT));
            for (Unit unit : this.units) {
                if (!unit.active) {
                    continue;
                }
                text.append(" | ").append(unit.kind).append(" busy=").append(unit.busyTicks)
                        .append(" idle=").append(unit.idleTicks);
            }
            this.remaining.forEach((id, runs) -> {
                if (runs > 0) {
                    text.append(" | pending ").append(id).append('=').append(runs);
                }
            });
            return text.toString();
        }

        void seed(String item, long count) {
            this.stock.merge(item, count, Long::sum);
        }

        private void seed(String item, Long count) {
            this.seed(item, count.longValue());
        }

        // ------------------------------------------------------------------
        // machines
        // ------------------------------------------------------------------

        private void placeSynthesizer(GameTestHelper helper, BlockPos pos) {
            helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
            BlockEntity entity = this.level.getBlockEntity(helper.absolutePos(pos));
            if (!(entity instanceof OreSynthesizerBlockEntity synthesizer)) {
                throw new IllegalStateException("synthesizer missing at " + pos);
            }
            this.units.add(new Unit(Kind.SYNTHESIZER, pos, null, synthesizer));
        }

        private void placeProcessor(GameTestHelper helper, Kind kind, BlockPos pos) {
            ProcessingKind processingKind = switch (kind) {
                case CRUSHER -> ProcessingKind.CRUSHER;
                case ELECTROLYZER -> ProcessingKind.ELECTROLYZER;
                case PRECISION_CRUSHER -> ProcessingKind.PRECISION_CRUSHER;
                case METAL_PRESS -> ProcessingKind.METAL_PRESS;
                case SYNTHESIZER -> throw new IllegalArgumentException("not a processor");
            };
            helper.setBlock(pos, block(processingKind).defaultBlockState());
            BlockEntity entity = this.level.getBlockEntity(helper.absolutePos(pos));
            if (!(entity instanceof ProcessingBlockEntity processor)) {
                throw new IllegalStateException(kind + " missing at " + pos);
            }
            this.units.add(new Unit(kind, pos, processor, null));
        }

        private CableBlockEntity placeCable(GameTestHelper helper, BlockPos pos) {
            helper.setBlock(pos, ModBlocks.CABLE_ADVANCED.get().defaultBlockState());
            BlockEntity entity = this.level.getBlockEntity(helper.absolutePos(pos));
            if (!(entity instanceof CableBlockEntity cable)) {
                throw new IllegalStateException("cable missing at " + pos);
            }
            return cable;
        }

        private GenerationBlockEntity placeGenerator(GameTestHelper helper, BlockPos pos) {
            helper.setBlock(pos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                    .setValue(GenerationBlock.FACING, Direction.EAST));
            BlockEntity entity = this.level.getBlockEntity(helper.absolutePos(pos));
            if (!(entity instanceof GenerationBlockEntity generator)) {
                throw new IllegalStateException("generator missing at " + pos);
            }
            generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                    new ItemStack(Items.COAL, COAL_PER_GENERATOR));
            return generator;
        }

        private EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos,
                                                    BufferTier tier) {
            Block block = switch (tier) {
                case BASIC -> ModBlocks.ENERGY_BUFFER.get();
                case REINFORCED -> ModBlocks.ENERGY_BUFFER_MK2.get();
                case HIGH_DENSITY -> ModBlocks.ENERGY_BUFFER_MK3.get();
            };
            helper.setBlock(pos, block.defaultBlockState());
            BlockEntity entity = this.level.getBlockEntity(helper.absolutePos(pos));
            if (!(entity instanceof EnergyBufferBlockEntity buffer)) {
                throw new IllegalStateException("buffer missing at " + pos);
            }
            return buffer;
        }

        private static Block block(ProcessingKind kind) {
            return switch (kind) {
                case CRUSHER -> ModBlocks.CRUSHER.get();
                case ELECTRIC_FURNACE -> ModBlocks.ELECTRIC_FURNACE.get();
                case ELECTROLYZER -> ModBlocks.ELECTROLYZER.get();
                case PRECISION_CRUSHER -> ModBlocks.PRECISION_CRUSHER.get();
                case METAL_PRESS -> ModBlocks.METAL_PRESS.get();
                case FAST_CRUSHER -> ModBlocks.FAST_CRUSHER.get();
            };
        }

        // ------------------------------------------------------------------
        // recipe graph
        // ------------------------------------------------------------------

        /**
         * One plan per output item, following the chain the player actually walks, exactly like the
         * reference harness, then every selected plan is checked to carry the production duration
         * so the scheduled duration is the only difference between the two cases.
         */
        private static Map<String, Plan> choosePlans(ServerLevel level, boolean withLadder,
                                                     int scheduledTicks) {
            List<Plan> all = new ArrayList<>();
            for (RecipeHolder<CrushingRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.CRUSHING_TYPE.get())) {
                CrushingRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.CRUSHER, recipe.ingredient(), recipe.result(),
                        recipe.timeTicks(), recipe.energyPerTick(), scheduledTicks));
            }
            for (RecipeHolder<ProcessorRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.ELECTROLYSIS_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.ELECTROLYZER, recipe.ingredient(), recipe.result(),
                        recipe.timeTicks(), recipe.energyPerTick(), scheduledTicks));
            }
            for (RecipeHolder<ProcessorRecipe> holder : level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.PRECISION_CRUSHING_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.PRECISION_CRUSHER, recipe.ingredient(),
                        recipe.result(), recipe.timeTicks(), recipe.energyPerTick(), scheduledTicks));
            }
            for (RecipeHolder<ProcessorRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.PRESSING_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.METAL_PRESS, recipe.ingredient(), recipe.result(),
                        recipe.timeTicks(), recipe.energyPerTick(), scheduledTicks));
            }
            for (RecipeHolder<SynthesisRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.SYNTHESIS_TYPE.get())) {
                SynthesisRecipe recipe = holder.value();
                String id = holder.id().getPath();
                if (!withLadder && id.startsWith("synthesis_")) {
                    continue;
                }
                List<ItemStack> inputs = new ArrayList<>();
                for (SynthesisRecipe.Entry entry : recipe.ingredients()) {
                    inputs.add(stack(entry.ingredient(), entry.count()));
                }
                all.add(new Plan(holder.id().toString(), Kind.SYNTHESIZER, order(id), inputs,
                        recipe.result().copy(), recipe.timeTicks(),
                        scheduledDuration(recipe.timeTicks(), scheduledTicks),
                        recipe.energyPerTick()));
            }

            Map<String, Plan> best = new LinkedHashMap<>();
            for (Plan plan : all) {
                String output = key(plan.output());
                Plan current = best.get(output);
                if (current == null || plan.order() < current.order()
                        || (plan.order() == current.order() && plan.id().compareTo(current.id()) < 0)) {
                    best.put(output, plan);
                }
            }
            return best;
        }

        /** The scheduled duration: 25 ticks for a production-10-tick run, the recipe's own value
         * for everything else (the press and blast recipes outside the ore line are not part of
         * the proposal and keep their production duration in both cases). */
        private static int scheduledDuration(int recipeTicks, int scheduledTicks) {
            return recipeTicks == PRODUCTION_TICKS ? scheduledTicks : recipeTicks;
        }

        /** Used plans that are not production 10 tick, so the report can name them. */
        private String usedRecipeSummary() {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, Integer> entry : this.required.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                for (Plan plan : this.plans) {
                    if (plan.id().equals(entry.getKey())
                            && plan.recipeTicks() != PRODUCTION_TICKS) {
                        text.append(shortName(plan.id())).append(':').append(plan.recipeTicks())
                                .append("t x").append(entry.getValue()).append(' ');
                    }
                }
            }
            return text.isEmpty() ? "none" : text.toString().trim();
        }

        private static Plan toPlan(ResourceLocation id, Kind kind, Ingredient ingredient,
                                   ItemStack result, int timeTicks, int energyPerTick,
                                   int scheduledTicks) {
            return new Plan(id.toString(), kind, order(id.getPath()),
                    List.of(stack(ingredient, 1)), result.copy(), timeTicks,
                    scheduledDuration(timeTicks, scheduledTicks), energyPerTick);
        }

        /** Chain order: the line before the catalyst ladder, and inside the line stage 1 first. */
        private static int order(String id) {
            if (id.startsWith("crush_raw_")) {
                return 0;
            }
            if (id.startsWith("oreline_s") && id.length() > 9) {
                int end = 9;
                while (end < id.length() && Character.isDigit(id.charAt(end))) {
                    end++;
                }
                try {
                    return 10 + Integer.parseInt(id.substring(9, end));
                } catch (NumberFormatException ignored) {
                    return 400;
                }
            }
            if (id.startsWith("oreline_drop_")) {
                return 950;
            }
            if (id.startsWith("synthesis_")) {
                return 100 + ladderIndex(id);
            }
            return 500;
        }

        private static int ladderIndex(String id) {
            return switch (id) {
                case "synthesis_1" -> 1;
                case "synthesis_2" -> 2;
                case "synthesis_3" -> 3;
                case "synthesis_4" -> 4;
                case "synthesis_5" -> 5;
                case "synthesis_6" -> 6;
                case "synthesis_high_density" -> 7;
                case "synthesis_unstable_ultimate" -> 8;
                case "synthesis_ultimate" -> 9;
                default -> 50;
            };
        }

        private static ItemStack stack(Ingredient ingredient, int count) {
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                throw new IllegalStateException("empty ingredient");
            }
            return items[0].copyWithCount(count);
        }

        /** Splits what the target needs into runs per recipe and the raw items it starts from. */
        private void demand(String item, long count, Map<String, Long> external, int depth) {
            if (count <= 0) {
                return;
            }
            if (depth > 32) {
                throw new IllegalStateException("recipe chain loops at " + item);
            }
            Plan plan = null;
            for (Plan candidate : this.plans) {
                if (key(candidate.output()).equals(item)) {
                    plan = candidate;
                    break;
                }
            }
            if (plan == null) {
                external.merge(item, count, Long::sum);
                return;
            }
            int runs = (int) ((count + plan.output().getCount() - 1) / plan.output().getCount());
            if (runs <= 0) {
                return;
            }
            this.remaining.merge(plan.id(), runs, Integer::sum);
            for (ItemStack input : plan.inputs()) {
                this.demand(key(input), (long) input.getCount() * runs, external, depth + 1);
            }
        }

        private static String key(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }
}
