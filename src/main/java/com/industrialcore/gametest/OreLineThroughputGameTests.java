package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * Does one raw ore reach 32,768 ingots inside five minutes (6,000 ticks) with four synthesizers?
 *
 * <p>Ten real machines are placed: four synthesizers, three crushers, one electrolyzer, one
 * precision crusher and one metal press. Every operation is executed on the machine itself - the
 * inputs are put into its slots, the machine is ticked once per tick of the line and the output is
 * taken out - so the tick cost of a run is the recipe's own value, not a number this test assumes.
 * The buffer between the machines is a test-side map that hands items over in a single tick, and
 * every machine is kept on a full internal buffer, exactly as a connected cable network would.
 * <b>Transport is therefore idealised</b>: a real pipe network adds travel time on top, so the
 * measured makespan is a lower bound.</p>
 *
 * <p>Two situations are measured separately because they cost different amounts of work:</p>
 * <ul>
 *   <li>the 512 ultimate catalysts are already in the chest, so only the line runs;</li>
 *   <li>the catalyst dust of the eight metals is crushed from raw ore and the whole ladder is
 *       crafted on the same four synthesizers, which is the situation the five minute target is
 *       really about.</li>
 * </ul>
 *
 * <p>The loop advances the line synchronously inside one game tick, so the reported number is
 * <em>simulated line ticks</em>, not the wall clock time of the test run. One tick of the line is
 * 1/20 s in game.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreLineThroughputGameTests {
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    private static final String ULTIMATE = "industrialcore:synthetic_ore_ultimate";
    private static final int TARGET_INGOTS = 32_768;
    /** Five minutes at 20 ticks per second. */
    private static final int FIVE_MINUTES = 6_000;
    /**
     * Makespan of the reference build from raw ore, measured on the current data (7,479 ticks).
     * The five minute target is asserted by the sweep instead.
     */
    private static final int REFERENCE_CEILING = 7_600;
    /** Guard so a broken schedule fails with a diagnostic instead of spinning forever. */
    private static final int HARD_LIMIT = 30_000;
    /** The stage-15 recipe of the iron chain, the most expensive draw in the line. */
    private static final String STAGE_FIFTEEN = "industrialcore:oreline_s15_iron";

    private static final int Y = 3;
    /** Machines placed in the test box; a measurement uses a subset of them. */
    private static final int MAX_SYNTHESIZERS = 7;
    private static final int MAX_CRUSHERS = 6;
    /** The reference layout: four synthesizers, three crushers, one of each processor. */
    private static final int SYNTHESIZERS = 4;
    private static final int CRUSHERS = 3;

    private OreLineThroughputGameTests() {}

    /**
     * Catalysts stocked: 512 ultimate synthetic ore sit in the chest, so the four synthesizers
     * only run stages 7-15 of the line.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void fourSynthesizersFinishTheLineInFiveMinutesWithStockedCatalysts(
            GameTestHelper helper) {
        Harness harness = Harness.build(helper, false).withMachines(SYNTHESIZERS, CRUSHERS);
        harness.seed(ULTIMATE, 512);

        int elapsed = harness.run();
        helper.assertTrue(elapsed > 0, "the line never produced 32,768 ingots: "
                + harness.diagnostic());
        helper.assertTrue(harness.produced(IRON_INGOT) >= TARGET_INGOTS,
                "the line stopped at " + harness.produced(IRON_INGOT) + " ingots");
        IndustrialCore.LOGGER.info("[perf] ore line, catalysts stocked: {} ticks ({} s), "
                        + "{} FE total, {} FE/t peak, {} line runs on the synthesizers, "
                        + "{} crushed ores; stage ends {}",
                elapsed, seconds(elapsed), harness.totalFe(), harness.peakFe(),
                harness.completed("oreline_"), harness.completed("crush_raw_"),
                harness.stageTimeline());
        helper.assertTrue(elapsed <= FIVE_MINUTES,
                "the line needed " + elapsed + " ticks (" + seconds(elapsed) + " s) with stocked "
                        + "catalysts, over the 6,000 tick target: " + harness.diagnostic());
        helper.succeed();
    }

    /**
     * The reference build (four synthesizers, three crushers), measured from one raw ore.
     *
     * <p>The eight catalyst dusts are crushed from raw ore, the whole catalyst ladder runs on the
     * same four synthesizers as the line, and stage 15 consumes the ultimate catalysts as they
     * arrive. Every recipe of the route is priced by its own JSON, so this is the honest makespan
     * of the documented chain - still with transport idealised to a single tick.</p>
     *
     * <p>This build does <b>not</b> meet the five minute target; the figure this test pins is the
     * reference makespan, so a regression is visible. {@link #fiveMinutesAreReachableWithMore
     * Machines(GameTestHelper)} searches for a build that does meet it.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fourSynthesizersFinishTheWholeChainFromRaw(GameTestHelper helper) {
        Harness harness = Harness.build(helper, true).withMachines(SYNTHESIZERS, CRUSHERS);

        int elapsed = harness.run();
        helper.assertTrue(elapsed > 0, "the line never produced 32,768 ingots: "
                + harness.diagnostic());
        helper.assertTrue(harness.produced(IRON_INGOT) >= TARGET_INGOTS,
                "the line stopped at " + harness.produced(IRON_INGOT) + " ingots");
        IndustrialCore.LOGGER.info("[perf] ore line, from raw ore, {} synthesizers / {} crushers: "
                        + "{} ticks ({} s), {} FE total, {} FE/t peak, {} line runs, {} ladder "
                        + "runs; stage ends {}",
                SYNTHESIZERS, CRUSHERS, elapsed, seconds(elapsed), harness.totalFe(),
                harness.peakFe(), harness.completed("oreline_"), harness.completed("synthesis_"),
                harness.stageTimeline());
        helper.assertTrue(elapsed <= REFERENCE_CEILING,
                "the reference build regressed to " + elapsed + " ticks (" + seconds(elapsed)
                        + " s), over the documented " + REFERENCE_CEILING + " tick reference: "
                        + harness.diagnostic());
        helper.succeed();
    }

    /**
     * How many machines the from-raw chain actually needs to meet the five minute target.
     *
     * <p>The sweep adds synthesizers and crushers - never time or FE - and reports every
     * configuration it tried. The assertion is that at least one build inside the swept range
     * reaches 6,000 ticks, so the target stays measurable instead of being assumed.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void fiveMinutesAreReachableWithMoreMachines(GameTestHelper helper) {
        StringBuilder table = new StringBuilder();
        int bestElapsed = Integer.MAX_VALUE;
        int bestSynths = 0;
        int bestCrushers = 0;
        int firstElapsed = Integer.MAX_VALUE;
        int firstSynths = 0;
        int firstCrushers = 0;
        for (int synths = SYNTHESIZERS; synths <= MAX_SYNTHESIZERS; synths++) {
            for (int crushers = CRUSHERS; crushers <= MAX_CRUSHERS; crushers++) {
                Harness harness = Harness.build(helper, true).withMachines(synths, crushers);
                int elapsed = harness.run();
                harness.release();
                if (elapsed <= 0) {
                    continue;
                }
                table.append(String.format(Locale.ROOT, "%dx%d=%d%s ", synths, crushers, elapsed,
                        elapsed <= FIVE_MINUTES ? "*" : ""));
                if (elapsed < bestElapsed) {
                    bestElapsed = elapsed;
                    bestSynths = synths;
                    bestCrushers = crushers;
                }
                if (elapsed <= FIVE_MINUTES && firstElapsed > FIVE_MINUTES) {
                    // The sweep goes up in synthesizers first, then crushers, so the first hit is
                    // the smallest build that meets the target.
                    firstElapsed = elapsed;
                    firstSynths = synths;
                    firstCrushers = crushers;
                }
            }
        }
        IndustrialCore.LOGGER.info("[perf] ore line sweep (synthesizers x crushers, '*' = under "
                + "6,000): {}", table.toString().trim());
        helper.assertTrue(bestElapsed <= FIVE_MINUTES,
                "no build up to " + MAX_SYNTHESIZERS + " synthesizers and " + MAX_CRUSHERS
                        + " crushers met the target; best was " + bestSynths + "x" + bestCrushers
                        + " at " + bestElapsed + " ticks. Sweep: " + table);
        IndustrialCore.LOGGER.info("[perf] smallest winning build: {} synthesizers / {} crushers, "
                + "{} ticks ({} s); fastest swept: {} synthesizers / {} crushers, {} ticks",
                firstSynths, firstCrushers, firstElapsed, seconds(firstElapsed), bestSynths,
                bestCrushers, bestElapsed);
        helper.succeed();
    }

    /**
     * The documented standard build - five synthesizers and four crushers - measured from one raw
     * ore, with the concurrency and peak-draw report the power decision needs.
     *
     * <p>Same chain as {@link #fourSynthesizersFinishTheWholeChainFromRaw(GameTestHelper)}: the
     * eight catalyst dusts are crushed from raw ore, the ladder and the line share the five
     * synthesizers, and stage 15 consumes the ultimate catalysts as they arrive.</p>
     *
     * <p>The peak draw is the schedule's own draw: the sum of {@code energyPerTick} over the runs
     * that overlap on a tick. It is <b>not</b> a measurement of a cable network - every machine is
     * kept on a full buffer here, so nothing can be starved - and it is therefore an upper bound
     * on what the build would draw if the supply kept up, not proof that the supply does.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void fiveSynthesizersFromRawMeasurePeakAndConcurrency(GameTestHelper helper) {
        int synths = 5;
        int crushers = 4;
        Harness harness = Harness.build(helper, true).withMachines(synths, crushers);

        int elapsed = harness.run();
        helper.assertTrue(elapsed > 0, "the line never produced 32,768 ingots: "
                + harness.diagnostic());
        helper.assertTrue(harness.produced(IRON_INGOT) >= TARGET_INGOTS,
                "the line stopped at " + harness.produced(IRON_INGOT) + " ingots");

        int[] peakDraw = harness.peakFeWithTick();
        int[] peakSynths = harness.peakSynthesizers();
        int[] peakStage15 = harness.peakStageFifteen();
        int stage15Runs = harness.completed("oreline_s15_");
        long catalysts = harness.made(ULTIMATE);

        IndustrialCore.LOGGER.info("[perf] ore line {}x{}, from raw ore: {} ticks ({} s), "
                        + "{} FE total, peak {} FE/t at tick {}, peak {} synthesizers at tick {}, "
                        + "peak {} stage-15 runs at tick {}; {}; stage starts {}; stage ends {}; "
                        + "{} s15 runs, {} catalysts made, {} left, {} dust crushed",
                synths, crushers, elapsed, seconds(elapsed), harness.totalFe(),
                peakDraw[0], peakDraw[1], peakSynths[0], peakSynths[1], peakStage15[0],
                peakStage15[1], harness.stageFifteenPerSynthesizer(),
                harness.stageStartTimeline(), harness.stageTimeline(), stage15Runs, catalysts,
                harness.produced(ULTIMATE), harness.completed("crush_raw_"));
        IndustrialCore.LOGGER.info("[perf] synthesizers {} ({} synthesizers)",
                harness.synthesizerBusySummary(), synths);

        helper.assertTrue(elapsed <= FIVE_MINUTES,
                synths + "x" + crushers + " needed " + elapsed + " ticks (" + seconds(elapsed)
                        + " s), over the 6,000 tick target: " + harness.diagnostic());
        helper.assertTrue(peakDraw[0] <= synths * 120,
                "the schedule drew " + peakDraw[0] + " FE/t, more than " + synths
                        + " stage-15 machines can (" + (synths * 120) + ")");
        helper.assertTrue(peakStage15[0] >= 1,
                "no synthesizer ever entered stage 15: " + harness.diagnostic());
        helper.assertTrue(stage15Runs == TARGET_INGOTS / 64,
                "stage 15 ran " + stage15Runs + " times instead of " + (TARGET_INGOTS / 64));
        helper.assertTrue(catalysts == TARGET_INGOTS / 64,
                "the ladder crafted " + catalysts + " ultimate catalysts instead of "
                        + (TARGET_INGOTS / 64) + "; the line needs one per stage-15 run");
        helper.succeed();
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    /** Which machine runs a plan. */
    private enum Kind {
        CRUSHER, ELECTROLYZER, PRECISION_CRUSHER, METAL_PRESS, SYNTHESIZER
    }

    /** One recipe of the chain, priced by its own JSON values. */
    private record Plan(String id, Kind kind, int order, List<ItemStack> inputs, ItemStack output,
                        int timeTicks, int energyPerTick) {}

    /** An output that lands in the buffer on a given tick. */
    private record Completion(int tick, String item, int count) {}

    /**
     * One executed run: which machine ran which recipe, from which tick to which tick and at what
     * draw. Kept beside {@code jobs} so concurrency can be answered per machine without changing
     * the numbers the power assertions already use.
     */
    private record Run(Unit unit, String planId, int start, int end, int fePerTick) {}

    /** A placed machine and the tick it becomes free again. */
    private static final class Unit {
        final Kind kind;
        final ProcessingBlockEntity processor;
        final OreSynthesizerBlockEntity synthesizer;
        boolean active = true;
        int freeAt = 1;
        int busyTicks;
        int idleTicks;
        final java.util.ArrayDeque<String> trace = new java.util.ArrayDeque<>();

        Unit(Kind kind, ProcessingBlockEntity processor, OreSynthesizerBlockEntity synthesizer) {
            this.kind = kind;
            this.processor = processor;
            this.synthesizer = synthesizer;
        }
    }

    /**
     * The whole experiment: machines, the recipe graph, the buffers and the greedy schedule.
     *
     * <p>Schedule: the free machine takes the first plan that still has runs left and whose
     * inputs are all in the buffer. Stage 15 is listed before the catalyst ladder, so it starts
     * as soon as one catalyst exists and the ladder keeps feeding it.</p>
     */
    private static final class Harness {
        private final ServerLevel level;
        private final List<Unit> units = new ArrayList<>();
        private final List<Plan> plans;
        private final Map<String, Integer> remaining = new LinkedHashMap<>();
        private final Map<String, Long> stock = new HashMap<>();
        private final Map<String, Long> made = new HashMap<>();
        private final Map<String, Integer> completed = new HashMap<>();
        private final Map<String, Integer> finishedAt = new LinkedHashMap<>();
        private final List<Completion> pending = new ArrayList<>();
        /** Every run as {start tick, end tick, FE/t}, so power can be measured, not assumed. */
        private final List<int[]> jobs = new ArrayList<>();
        /** The same runs with the machine and recipe attached, for the concurrency report. */
        private final List<Run> runs = new ArrayList<>();
        /** Tick the first run of each recipe started on. */
        private final Map<String, Integer> startedAt = new LinkedHashMap<>();
        private int now;

        private Harness(ServerLevel level, List<Plan> plans) {
            this.level = level;
            this.plans = plans;
        }

        static Harness build(GameTestHelper helper, boolean withCatalystLadder) {
            ServerLevel level = helper.getLevel();
            Map<String, Plan> byOutput = choosePlans(level, withCatalystLadder);
            List<Plan> ordered = new ArrayList<>(byOutput.values());
            ordered.sort((left, right) -> left.order() != right.order()
                    ? Integer.compare(left.order(), right.order())
                    : left.id().compareTo(right.id()));

            Harness harness = new Harness(level, ordered);
            Map<String, Long> external = new HashMap<>();
            harness.demand(IRON_INGOT, TARGET_INGOTS, external, 0);
            external.forEach(harness::seed);

            for (int index = 0; index < MAX_SYNTHESIZERS; index++) {
                harness.placeSynthesizer(helper,
                        new BlockPos(index % 3, Y, index / 3));
            }
            for (int index = 0; index < MAX_CRUSHERS; index++) {
                harness.placeProcessor(helper, Kind.CRUSHER,
                        new BlockPos(index % 3, Y, 5 + index / 3));
            }
            harness.placeProcessor(helper, Kind.ELECTROLYZER, new BlockPos(0, Y, 4));
            harness.placeProcessor(helper, Kind.PRECISION_CRUSHER, new BlockPos(1, Y, 4));
            harness.placeProcessor(helper, Kind.METAL_PRESS, new BlockPos(2, Y, 4));
            return harness;
        }

        /** Uses only the first {@code synths} synthesizers and {@code crushers} crushers. */
        Harness withMachines(int synths, int crushers) {
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

        /** Clears the machines so the next configuration starts from an empty factory. */
        void release() {
            for (Unit unit : this.units) {
                if (unit.processor != null) {
                    unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                            ItemStack.EMPTY);
                    unit.processor.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                            ItemStack.EMPTY);
                    unit.processor.setEnergyStored(0);
                } else {
                    for (int slot = 0; slot < OreSynthesizerBlockEntity.SLOT_COUNT; slot++) {
                        unit.synthesizer.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    unit.synthesizer.setEnergyStored(0);
                }
                unit.freeAt = 1;
                unit.busyTicks = 0;
                unit.idleTicks = 0;
                unit.trace.clear();
            }
        }

        /**
         * Runs the line until the target is met; returns the tick the last ingot appeared on.
         *
         * <p>A run that starts on tick {@code T} puts its output into the buffer on tick
         * {@code T + time - 1} and frees the machine on {@code T + time}, so no stage can start
         * before its input physically exists.</p>
         */
        int run() {
            for (int tick = 1; tick <= HARD_LIMIT; tick++) {
                this.now = tick;
                this.deliver(tick);
                if (this.produced(IRON_INGOT) >= TARGET_INGOTS) {
                    return tick;
                }
                for (Unit unit : this.units) {
                    if (!unit.active || unit.freeAt > tick) {
                        continue;
                    }
                    Plan next = this.pick(unit.kind);
                    if (next == null) {
                        unit.idleTicks++;
                        continue;
                    }
                    this.execute(unit, next, tick);
                    unit.freeAt = tick + next.timeTicks();
                }
            }
            return -1;
        }

        /** Hands over every output whose run has finished by this tick. */
        private void deliver(int tick) {
            while (!this.pending.isEmpty() && this.pending.get(0).tick() <= tick) {
                Completion completion = this.pending.remove(0);
                this.stock.merge(completion.item(), (long) completion.count(), Long::sum);
                this.made.merge(completion.item(), (long) completion.count(), Long::sum);
            }
        }

        long produced(String item) {
            return this.stock.getOrDefault(item, 0L);
        }

        /** FE the whole route spends, {@code time x FE/t} per run. */
        long totalFe() {
            long total = 0;
            for (int[] job : this.jobs) {
                total += (long) (job[1] - job[0]) * job[2];
            }
            return total;
        }

        /** Highest simultaneous draw of all machines, in FE/t. */
        int peakFe() {
            List<int[]> events = new ArrayList<>(this.jobs.size() * 2);
            for (int[] job : this.jobs) {
                events.add(new int[]{job[0], job[2]});
                events.add(new int[]{job[1], -job[2]});
            }
            // A machine frees at the end tick and may start again on it, so ends come first.
            events.sort((left, right) -> left[0] != right[0]
                    ? Integer.compare(left[0], right[0])
                    : Integer.compare(left[1], right[1]));
            int current = 0;
            int peak = 0;
            for (int[] event : events) {
                current += event[1];
                peak = Math.max(peak, current);
            }
            return peak;
        }

        long made(String item) {
            return this.made.getOrDefault(item, 0L);
        }

        /**
         * Highest simultaneous draw of the schedule and the first tick it happens on, as
         * {FE/t, tick}.
         *
         * <p>This is the draw of the schedule itself - the sum of {@code energyPerTick} over the
         * runs that overlap - not a reading taken from a cable network. The harness keeps every
         * machine's buffer full, so no machine can ever be starved here.</p>
         */
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

        /** Highest number of synthesizers busy at the same time, as {count, tick}. */
        int[] peakSynthesizers() {
            return peakOverlap(run -> run.unit().kind == Kind.SYNTHESIZER);
        }

        /** Highest number of stage-15 runs busy at the same time, as {count, tick}. */
        int[] peakStageFifteen() {
            return peakOverlap(run -> run.planId().equals(STAGE_FIFTEEN));
        }

        private int[] peakOverlap(java.util.function.Predicate<Run> filter) {
            int[] count = new int[this.now + 2];
            for (Run run : this.runs) {
                if (!filter.test(run)) {
                    continue;
                }
                count[Math.max(1, run.start())]++;
                if (run.end() < count.length) {
                    count[run.end()]--;
                }
            }
            return peakOf(count);
        }

        /** Turns a start/end delta array into {peak value, first tick of that peak}. */
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

        /** Stage-15 ticks each active synthesizer spent, as "S1=1024 S2=1024 ...". */
        String stageFifteenPerSynthesizer() {
            List<String> parts = new ArrayList<>();
            int ordinal = 0;
            for (Unit unit : this.units) {
                if (unit.kind != Kind.SYNTHESIZER || !unit.active) {
                    continue;
                }
                ordinal++;
                int ticks = 0;
                for (Run run : this.runs) {
                    if (run.unit() == unit && run.planId().equals(STAGE_FIFTEEN)) {
                        ticks += run.end() - run.start();
                    }
                }
                parts.add("S" + ordinal + "=" + ticks);
            }
            return String.join(" ", parts);
        }

        /** Tick at which each stage first started, as "s7=... s8=...". */
        String stageStartTimeline() {
            StringBuilder text = new StringBuilder();
            for (int stage = 7; stage <= 15; stage++) {
                Integer tick = this.startedAt.get("industrialcore:oreline_s" + stage + "_iron");
                if (tick != null) {
                    text.append(text.isEmpty() ? "" : " ").append("s").append(stage).append('=')
                            .append(tick);
                }
            }
            return text.toString();
        }

        /** Share of the synthesizers' time that was spent busy, over all active synthesizers. */
        String synthesizerBusySummary() {
            int busy = 0;
            int idle = 0;
            for (Unit unit : this.units) {
                if (unit.kind != Kind.SYNTHESIZER || !unit.active) {
                    continue;
                }
                busy += unit.busyTicks;
                idle += unit.idleTicks;
            }
            return busy + " busy / " + idle + " idle ticks";
        }

        /** Tick at which each ore-line stage of the iron chain delivered its last item. */
        String stageTimeline() {
            StringBuilder text = new StringBuilder();
            for (int stage = 7; stage <= 15; stage++) {
                Integer tick = this.finishedAt.get("industrialcore:oreline_s" + stage + "_iron");
                if (tick != null) {
                    text.append(text.isEmpty() ? "" : " ").append("s").append(stage).append('=')
                            .append(tick);
                }
            }
            return text.toString();
        }

        int completed(String idPart) {
            return this.completed.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(idPart))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
        }

        String diagnostic() {
            StringBuilder text = new StringBuilder();
            text.append("elapsed=").append(this.now)
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
            this.stock.forEach((item, amount) -> {
                if (amount != 0 && (item.contains("stabilized") || item.contains("ultimate"))) {
                    text.append(" | stock ").append(item).append('=').append(amount);
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
            this.units.add(new Unit(Kind.SYNTHESIZER, null, synthesizer));
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
            this.units.add(new Unit(kind, processor, null));
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

        /**
         * Executes one run on the real machine and returns when its output is in the buffer.
         *
         * <p>The machine is cleaned before every run so no state leaks from the previous one, and
         * the output is verified: a run that produces nothing or the wrong amount fails the test
         * instead of silently shrinking the chain.</p>
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

            ItemStack output = ItemStack.EMPTY;
            for (int tick = 0; tick < plan.timeTicks(); tick++) {
                if (unit.processor != null) {
                    // A connected cable network keeps the buffer full; the machine draws per tick.
                    unit.processor.setEnergyStored(20_000);
                    BlockState state = unit.processor.getBlockState();
                    ProcessingBlockEntity.serverTick(this.level, unit.processor.getBlockPos(), state,
                            unit.processor);
                } else {
                    unit.synthesizer.setEnergyStored(SynthesisBalance.CAPACITY);
                    BlockState state = unit.synthesizer.getBlockState();
                    OreSynthesizerBlockEntity.serverTick(this.level, unit.synthesizer.getBlockPos(),
                            state, unit.synthesizer);
                }
            }
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
            this.jobs.add(new int[]{startTick, startTick + plan.timeTicks(),
                    plan.energyPerTick()});
            this.runs.add(new Run(unit, plan.id(), startTick, startTick + plan.timeTicks(),
                    plan.energyPerTick()));
            this.startedAt.putIfAbsent(plan.id(), startTick);
            if (unit.trace.size() >= 8) {
                unit.trace.removeFirst();
            }
            unit.trace.addLast("t" + startTick + " " + plan.id() + " -> " + output);
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

        private boolean has(Plan plan) {
            for (ItemStack input : plan.inputs()) {
                if (this.produced(key(input)) < input.getCount()) {
                    return false;
                }
            }
            return true;
        }

        // ------------------------------------------------------------------
        // recipe graph
        // ------------------------------------------------------------------

        /**
         * One plan per output item, following the chain the player actually walks.
         *
         * <p>Several recipes can produce the same item (an ore block, a raw ore and the ore-line
         * stage 1 all yield dust). The {@code order} number picks the ore-line entrance, so the
         * measurement never drifts onto the slower ore-block recipe or onto the documented
         * crystal loop of Quantum / Aether / Nebula.</p>
         */
        private static Map<String, Plan> choosePlans(ServerLevel level, boolean withLadder) {
            List<Plan> all = new ArrayList<>();
            for (RecipeHolder<CrushingRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.CRUSHING_TYPE.get())) {
                CrushingRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.CRUSHER, recipe.ingredient(), recipe.result(),
                        recipe.timeTicks(), recipe.energyPerTick()));
            }
            for (RecipeHolder<ProcessorRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.ELECTROLYSIS_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.ELECTROLYZER, recipe.ingredient(),
                        recipe.result(), recipe.timeTicks(), recipe.energyPerTick()));
            }
            for (RecipeHolder<ProcessorRecipe> holder : level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.PRECISION_CRUSHING_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.PRECISION_CRUSHER, recipe.ingredient(),
                        recipe.result(), recipe.timeTicks(), recipe.energyPerTick()));
            }
            for (RecipeHolder<ProcessorRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.PRESSING_TYPE.get())) {
                ProcessorRecipe recipe = holder.value();
                all.add(toPlan(holder.id(), Kind.METAL_PRESS, recipe.ingredient(), recipe.result(),
                        recipe.timeTicks(), recipe.energyPerTick()));
            }
            for (RecipeHolder<SynthesisRecipe> holder
                    : level.getRecipeManager().getAllRecipesFor(ModRecipes.SYNTHESIS_TYPE.get())) {
                SynthesisRecipe recipe = holder.value();
                String id = holder.id().getPath();
                if (!withLadder && id.startsWith("synthesis_")) {
                    continue; // the stocked-catalyst case never runs the ladder
                }
                List<ItemStack> inputs = new ArrayList<>();
                for (SynthesisRecipe.Entry entry : recipe.ingredients()) {
                    inputs.add(stack(entry.ingredient(), entry.count()));
                }
                all.add(new Plan(holder.id().toString(), Kind.SYNTHESIZER, order(id), inputs,
                        recipe.result().copy(), recipe.timeTicks(), recipe.energyPerTick()));
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

        private static Plan toPlan(ResourceLocation id, Kind kind, Ingredient ingredient,
                                   ItemStack result, int timeTicks, int energyPerTick) {
            return new Plan(id.toString(), kind, order(id.getPath()),
                    List.of(stack(ingredient, 1)), result.copy(), timeTicks, energyPerTick);
        }

        /**
         * Chain order: the line before the catalyst ladder, and inside the line stage 1 first.
         * A lower number is scheduled earlier.
         */
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
                // Quantum / Aether / Nebula have two entries per stage 1: the ore block and the
                // normal mining drop. Prefer the block, because stage 15 of those three lines
                // produces the drop again - walking the drop would close the documented crystal
                // loop and the chain would never terminate.
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
