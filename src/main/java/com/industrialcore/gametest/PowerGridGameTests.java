package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Can a real generator bank keep five stage-15 synthesizers at their rated draw, and where does
 * every FE go?
 *
 * <p>One tick is split into four phases and the stored total is read between them, so creation and
 * consumption are measured instead of inferred:</p>
 *
 * <ol>
 *   <li>generators - the only phase that can create FE, so its total change <em>is</em> the
 *       tick's generation;</li>
 *   <li>cables - redistribution only, so its total change must be zero;</li>
 *   <li>buffer (when present) - redistribution only, so its total change must be zero;</li>
 *   <li>synthesizers - the only phase that can consume FE, so its total drop <em>is</em> the
 *       tick's consumption.</li>
 * </ol>
 *
 * <p>Every run then checks {@code start + generated == end + consumed} to the FE. Nothing is
 * derived from progress counters, so a non-zero balance means the grid itself gained or lost
 * energy.</p>
 *
 * <p>Layout of one box (the control box is 12 x 12 x 12), chosen so that <b>no generator touches a
 * synthesizer</b>: generators at {@code (4,6,1..8)} and {@code (6,6,1..8)}, one spine of Advanced
 * cable at {@code (5,6,1..10)}, and the five synthesizers in the row above the spine at
 * {@code (5,7,1..5)}. A synthesizer there touches exactly one cable and nothing else, so any FE it
 * receives can only have come through the network. The optional buffer sits at {@code (5,7,8)},
 * likewise with a spine cable as its only neighbour.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerGridGameTests {
    private static final int Y = 6;
    private static final int SPINE_X = 5;
    private static final int SPINE_LENGTH = 10;
    private static final int CRAFTS = 5;
    /** One stage-15 craft: 10 ticks at 120 FE/t. */
    private static final int CRAFT_TIME = 10;
    private static final int CRAFT_DRAW = 120;
    private static final int CRAFT_FE = CRAFT_TIME * CRAFT_DRAW;
    /** Upper bound so a deadlocked grid fails with a diagnostic instead of spinning. */
    private static final int LIMIT = 400;

    private PowerGridGameTests() {}

    /** Fifteen generators, no buffer: the documented bank, measured with the balance. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fifteenGeneratorsBalanceToTheFe(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 15, null, false).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "only " + measure.completed + " of " + (5 * CRAFTS) + " crafts finished: "
                        + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.assertTrue(measure.cablePhaseDelta == 0,
                "distribution changed the stored total by " + measure.cablePhaseDelta
                        + " FE, so it is not lossless: " + measure.report());
        helper.assertTrue(measure.peakReceived >= 5 * CRAFT_DRAW,
                "the machines never received " + (5 * CRAFT_DRAW) + " FE/t together (peak "
                        + measure.peakReceived + "): " + measure.report());
        helper.assertTrue(measure.directReceived == 0,
                "the machines received " + measure.directReceived
                        + " FE straight from a generator, so the layout still has a bypass: "
                        + measure.report());
        helper.assertTrue(measure.consumed == 5 * CRAFTS * CRAFT_FE,
                "the crafts spent " + measure.consumed + " FE instead of the nominal "
                        + (5 * CRAFTS * CRAFT_FE) + ": " + measure.report());
        helper.assertTrue(measure.makespan <= 62,
                "fifteen generators needed " + measure.makespan + " ticks: " + measure.report());
        helper.succeed();
    }

    /** Fourteen generators: 560 FE/t for a 600 FE/t demand. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fourteenGeneratorsFallShortOfTheStageFifteenPeak(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 14, null, false).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "only " + measure.completed + " crafts finished: " + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.assertTrue(measure.peakReceived <= 14 * 40,
                "fourteen 40 FE/t generators delivered " + measure.peakReceived + " FE/t");
        helper.assertTrue(measure.makespan >= 60,
                "fourteen generators finished in " + measure.makespan
                        + " ticks, no slower than fifteen: " + measure.report());
        helper.succeed();
    }

    /** Sixteen generators: 640 FE/t, more than the peak needs. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void sixteenGeneratorsCoverThePeakWithSpareCapacity(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 16, null, false).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "only " + measure.completed + " crafts finished: " + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.assertTrue(measure.peakReceived >= 5 * CRAFT_DRAW,
                "sixteen generators delivered only " + measure.peakReceived + " FE/t: "
                        + measure.report());
        helper.assertTrue(measure.makespan <= 62,
                "sixteen generators needed " + measure.makespan + " ticks: " + measure.report());
        helper.succeed();
    }

    /** A reinforced buffer that starts empty has to charge from the pool first. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void anEmptyReinforcedBufferChargesFromThePool(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 15, BufferTier.REINFORCED, false).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "the buffer deadlocked the grid after " + measure.completed + " crafts: "
                        + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.assertTrue(measure.bufferReceived > 0,
                "the empty buffer never took any energy in (charged " + measure.bufferReceived
                        + " FE): " + measure.report());
        helper.succeed();
    }

    /** The same buffer, already full: it can only pass energy on. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aChargedReinforcedBufferOnlyPassesEnergyOn(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 15, BufferTier.REINFORCED, true).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "the charged buffer deadlocked the grid after " + measure.completed + " crafts: "
                        + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.succeed();
    }

    /** A basic buffer, already full: 512 FE/t receive, 256 FE/t per face out. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aChargedBasicBufferOnlyPassesEnergyOn(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 15, BufferTier.BASIC, true).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "the charged buffer deadlocked the grid after " + measure.completed + " crafts: "
                        + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.succeed();
    }

    /** A high density buffer, already full: 4,096 FE/t both ways. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aChargedHighDensityBufferOnlyPassesEnergyOn(GameTestHelper helper) {
        Grid.Measure measure = Grid.build(helper, 15, BufferTier.HIGH_DENSITY, true).run(CRAFTS);
        IndustrialCore.LOGGER.info("[grid] {}", measure.report());

        helper.assertTrue(measure.completed == 5 * CRAFTS,
                "the charged buffer deadlocked the grid after " + measure.completed + " crafts: "
                        + measure.report());
        helper.assertTrue(measure.balanceError() == 0,
                "the FE balance is off by " + measure.balanceError() + " FE: " + measure.report());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // the grid
    // ------------------------------------------------------------------

    /** One test box: spine, generators, synthesizers, an optional buffer and the measurement. */
    private static final class Grid {
        private final GameTestHelper helper;
        private final List<CableBlockEntity> cables = new ArrayList<>();
        private final List<GenerationBlockEntity> generators = new ArrayList<>();
        private final List<OreSynthesizerBlockEntity> synthesizers = new ArrayList<>();
        private EnergyBufferBlockEntity buffer;

        private Grid(GameTestHelper helper) {
            this.helper = helper;
        }

        static Grid build(GameTestHelper helper, int generatorCount, BufferTier tier,
                          boolean charged) {
            Grid grid = new Grid(helper);
            for (int z = 1; z <= SPINE_LENGTH; z++) {
                grid.cables.add(grid.placeCable(new BlockPos(SPINE_X, Y, z)));
            }
            for (int index = 0; index < generatorCount; index++) {
                int side = index < 8 ? SPINE_X - 1 : SPINE_X + 1;
                int z = (index < 8 ? index : index - 8) + 1;
                grid.generators.add(grid.placeGenerator(new BlockPos(side, Y, z)));
            }
            for (int z = 1; z <= 5; z++) {
                grid.synthesizers.add(grid.placeSynthesizer(new BlockPos(SPINE_X, Y + 1, z)));
            }
            if (tier != null) {
                grid.buffer = grid.placeBuffer(new BlockPos(SPINE_X, Y + 1, 8), tier);
                if (charged) {
                    grid.buffer.setEnergyStored(
                            grid.buffer.getEnergyStorage().getMaxEnergyStored());
                }
            }
            for (OreSynthesizerBlockEntity synthesizer : grid.synthesizers) {
                refill(synthesizer);
            }
            return grid;
        }

        /**
         * Ticks the grid until every machine has finished {@code craftsPerMachine} crafts.
         *
         * <p>Inside one tick: harvest and refill, generators, cables, buffer, synthesizers - and
         * the stored total is read between each phase, so creation and consumption are measured
         * rather than estimated.</p>
         */
        Measure run(int craftsPerMachine) {
            Measure measure = new Measure(this.generators.size(), this.buffer == null
                    ? "none" : this.buffer.getTier().name(),
                    this.buffer == null ? "-"
                            : (this.buffer.getEnergyStoredForTesting() > 0 ? "charged" : "empty"),
                    this.synthesizers.size());
            measure.startTotal = this.totalStored();
            int target = craftsPerMachine * this.synthesizers.size();
            int[] done = new int[this.synthesizers.size()];
            int tick = 0;
            while (measure.completed < target && tick < LIMIT) {
                tick++;
                for (int index = 0; index < this.synthesizers.size(); index++) {
                    OreSynthesizerBlockEntity synthesizer = this.synthesizers.get(index);
                    ItemStack output = synthesizer.getInventory()
                            .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
                    if (output.isEmpty()) {
                        continue;
                    }
                    measure.completed++;
                    done[index]++;
                    measure.ingots += output.getCount();
                    measure.craftCost.add(measure.runningTicks[index]);
                    measure.runningTicks[index] = 0;
                    if (measure.firstOutputTick == 0) {
                        measure.firstOutputTick = tick;
                    }
                    synthesizer.getInventory()
                            .setStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
                    // Only machines that still owe crafts get new inputs. Refilling a finished
                    // machine would start a craft that nobody harvests, and its ticks would show
                    // up as consumption that no completed craft accounts for.
                    if (done[index] < craftsPerMachine) {
                        refill(synthesizer);
                    }
                }

                int totalBefore = this.totalStored();
                int[] storedBefore = this.machineStored();
                int[] beforeProgress = new int[this.synthesizers.size()];
                for (int index = 0; index < this.synthesizers.size(); index++) {
                    beforeProgress[index] = this.synthesizers.get(index).getProgressForTesting();
                }

                for (GenerationBlockEntity generator : this.generators) {
                    GenerationBlockEntity.serverTick(this.helper.getLevel(), generator.getBlockPos(),
                            generator.getBlockState(), generator);
                }
                int afterGenerators = this.totalStored();
                int generatedThisTick = afterGenerators - totalBefore;
                measure.generated += generatedThisTick;
                int[] storedAfterGenerators = this.machineStored();

                int bufferBeforeCables = this.buffer == null
                        ? 0 : this.buffer.getEnergyStoredForTesting();
                for (CableBlockEntity cable : this.cables) {
                    CableBlockEntity.serverTick(this.helper.getLevel(), cable.getBlockPos(),
                            cable.getBlockState(), cable);
                }
                int afterCables = this.totalStored();
                measure.cablePhaseDelta += afterCables - afterGenerators;
                if (this.buffer != null) {
                    measure.bufferReceived += this.buffer.getEnergyStoredForTesting()
                            - bufferBeforeCables;
                }

                if (this.buffer != null) {
                    EnergyBufferBlockEntity.serverTick(this.helper.getLevel(),
                            this.buffer.getBlockPos(), this.buffer.getBlockState(), this.buffer);
                }
                int afterBuffer = this.totalStored();
                measure.bufferPhaseDelta += afterBuffer - afterCables;

                int[] storedBeforeMachines = this.machineStored();
                int consumedThisTick = 0;
                for (int index = 0; index < this.synthesizers.size(); index++) {
                    OreSynthesizerBlockEntity synthesizer = this.synthesizers.get(index);
                    OreSynthesizerBlockEntity.serverTick(this.helper.getLevel(),
                            synthesizer.getBlockPos(), synthesizer.getBlockState(), synthesizer);
                    int storedAfter = synthesizer.getEnergyStoredForTesting();
                    // A machine never receives during its own tick, so the drop across that tick
                    // is exactly what the craft paid.
                    int consumed = storedBeforeMachines[index] - storedAfter;
                    consumedThisTick += Math.max(0, consumed);
                    if (consumed != 0 && consumed != CRAFT_DRAW && measure.anomalies.size() < 12) {
                        measure.anomalies.add(String.format(Locale.ROOT,
                                "t%d S%d drop=%d p%d>%d st=%s in=%d", tick, index + 1, consumed,
                                beforeProgress[index], synthesizer.getProgressForTesting(),
                                synthesizer.getStatusForTesting(), storedBeforeMachines[index]));
                    }
                    if (consumed > 0) {
                        measure.runningTicks[index]++;
                    }
                    if (consumed <= 0
                            && synthesizer.getStatusForTesting() == MachineStatus.NO_ENERGY) {
                        measure.noEnergyTicks++;
                    }
                }
                measure.consumed += consumedThisTick;

                // Delivery split: what arrived straight from a generator during the generator
                // phase, and what arrived through the network during the cable phase.
                int directThisTick = 0;
                int networkThisTick = 0;
                for (int index = 0; index < this.synthesizers.size(); index++) {
                    int direct = storedAfterGenerators[index] - storedBefore[index];
                    int network = storedBeforeMachines[index] - storedAfterGenerators[index];
                    directThisTick += direct;
                    networkThisTick += network;
                    measure.perMachineDirect[index] += direct;
                    measure.perMachineNetwork[index] += network;
                    measure.perMachine[index] += direct + network;
                }
                measure.directReceived += directThisTick;
                measure.networkReceived += networkThisTick;
                int deliveredThisTick = directThisTick + networkThisTick;
                measure.peakReceived = Math.max(measure.peakReceived, deliveredThisTick);
                if (deliveredThisTick >= 5 * CRAFT_DRAW && measure.firstFullTick == 0) {
                    measure.firstFullTick = tick;
                }
                if (measure.trace.size() < 10) {
                    measure.trace.add(String.format(Locale.ROOT, "t%d:gen%d/dir%d/net%d/cons%d",
                            tick, generatedThisTick, directThisTick, networkThisTick,
                            consumedThisTick));
                }
                measure.makespan = tick;
                measure.endTotal = this.totalStored();
            }
            measure.generatorStoredEnd = this.storedInGenerators();
            measure.cableStoredEnd = this.storedInCables();
            measure.machineStoredEnd = this.storedInSynthesizers();
            measure.bufferStoredEnd = this.buffer == null
                    ? 0 : this.buffer.getEnergyStoredForTesting();
            measure.burnTicksEnd = this.burnTicks();
            measure.endTotal = this.totalStored();
            return measure;
        }

        /** All FE held by the grid right now; nothing outside these blocks is a source or sink. */
        private int totalStored() {
            int total = this.storedInGenerators() + this.storedInCables()
                    + this.storedInSynthesizers();
            if (this.buffer != null) {
                total += this.buffer.getEnergyStoredForTesting();
            }
            return total;
        }

        private int[] machineStored() {
            int[] stored = new int[this.synthesizers.size()];
            for (int index = 0; index < this.synthesizers.size(); index++) {
                stored[index] = this.synthesizers.get(index).getEnergyStoredForTesting();
            }
            return stored;
        }

        private int burnTicks() {
            int total = 0;
            for (GenerationBlockEntity generator : this.generators) {
                total += generator.getBurnTime();
            }
            return total;
        }

        private int storedInGenerators() {
            int total = 0;
            for (GenerationBlockEntity generator : this.generators) {
                total += generator.getEnergyStorage().getEnergyStored();
            }
            return total;
        }

        private int storedInCables() {
            int total = 0;
            for (CableBlockEntity cable : this.cables) {
                total += cable.getEnergyStoredForTesting();
            }
            return total;
        }

        private int storedInSynthesizers() {
            int total = 0;
            for (OreSynthesizerBlockEntity synthesizer : this.synthesizers) {
                total += synthesizer.getEnergyStoredForTesting();
            }
            return total;
        }

        private CableBlockEntity placeCable(BlockPos pos) {
            this.helper.setBlock(pos, ModBlocks.CABLE_ADVANCED.get().defaultBlockState());
            BlockEntity entity = this.helper.getLevel().getBlockEntity(this.helper.absolutePos(pos));
            if (!(entity instanceof CableBlockEntity cable)) {
                throw new IllegalStateException("cable missing at " + pos);
            }
            return cable;
        }

        private GenerationBlockEntity placeGenerator(BlockPos pos) {
            this.helper.setBlock(pos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                    .setValue(GenerationBlock.FACING, Direction.EAST));
            BlockEntity entity = this.helper.getLevel().getBlockEntity(this.helper.absolutePos(pos));
            if (!(entity instanceof GenerationBlockEntity generator)) {
                throw new IllegalStateException("generator missing at " + pos);
            }
            generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                    new ItemStack(Items.COAL, 16));
            return generator;
        }

        private OreSynthesizerBlockEntity placeSynthesizer(BlockPos pos) {
            this.helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
            BlockEntity entity = this.helper.getLevel().getBlockEntity(this.helper.absolutePos(pos));
            if (!(entity instanceof OreSynthesizerBlockEntity synthesizer)) {
                throw new IllegalStateException("synthesizer missing at " + pos);
            }
            return synthesizer;
        }

        private EnergyBufferBlockEntity placeBuffer(BlockPos pos, BufferTier tier) {
            Block block = switch (tier) {
                case BASIC -> ModBlocks.ENERGY_BUFFER.get();
                case REINFORCED -> ModBlocks.ENERGY_BUFFER_MK2.get();
                case HIGH_DENSITY -> ModBlocks.ENERGY_BUFFER_MK3.get();
            };
            this.helper.setBlock(pos, block.defaultBlockState());
            BlockEntity entity = this.helper.getLevel().getBlockEntity(this.helper.absolutePos(pos));
            if (!(entity instanceof EnergyBufferBlockEntity bufferEntity)) {
                throw new IllegalStateException("buffer missing at " + pos);
            }
            return bufferEntity;
        }

        /** The stage-15 iron inputs: 32 stabilized + 1 ultimate. */
        private static void refill(OreSynthesizerBlockEntity synthesizer) {
            synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START,
                    new ItemStack(ModItems.IRON_STABILIZED.get(), 32));
            synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START + 1,
                    new ItemStack(ModItems.SYNTHETIC_ORE_ULTIMATE.get(), 1));
        }

        /** What one grid run measured. */
        private static final class Measure {
            private final int generatorCount;
            private final String bufferName;
            private final String bufferState;
            private final List<String> trace = new ArrayList<>();
            private final List<Integer> craftCost = new ArrayList<>();
            private final List<String> anomalies = new ArrayList<>();
            private int bufferReceived;
            private final int[] perMachine;
            private final int[] perMachineDirect;
            private final int[] perMachineNetwork;
            private final int[] runningTicks;
            private int startTotal;
            private int makespan;
            private int completed;
            private int ingots;
            private int generated;
            private int consumed;
            private int directReceived;
            private int networkReceived;
            private int peakReceived;
            private int noEnergyTicks;
            private int firstFullTick;
            private int firstOutputTick;
            private int burnTicksEnd;
            private int generatorStoredEnd;
            private int cableStoredEnd;
            private int machineStoredEnd;
            private int bufferStoredEnd;
            private int cablePhaseDelta;
            private int bufferPhaseDelta;
            private int endTotal;

            private Measure(int generatorCount, String bufferName, String bufferState, int machines) {
                this.generatorCount = generatorCount;
                this.bufferName = bufferName;
                this.bufferState = bufferState;
                this.perMachine = new int[machines];
                this.perMachineDirect = new int[machines];
                this.perMachineNetwork = new int[machines];
                this.runningTicks = new int[machines];
            }

            /** start + generated - end - consumed; must be exactly zero. */
            private int balanceError() {
                return this.startTotal + this.generated - this.endTotal - this.consumed;
            }

            private String report() {
                StringBuilder machines = new StringBuilder();
                for (int index = 0; index < this.perMachine.length; index++) {
                    machines.append(index == 0 ? "" : " ").append('S').append(index + 1)
                            .append('=').append(this.perMachine[index])
                            .append("(dir").append(this.perMachineDirect[index])
                            .append("/net").append(this.perMachineNetwork[index]).append(')');
                }
                StringBuilder costs = new StringBuilder();
                for (Integer cost : this.craftCost) {
                    costs.append(costs.isEmpty() ? "" : ",").append(cost);
                }
                return String.format(Locale.ROOT,
                        "gens=%d buffer=%s/%s makespan=%d ticks (%.1f s) crafts=%d ingots=%d | "
                                + "start=%d generated=%d consumed=%d nominal=%d endTotal=%d "
                                + "balanceError=%d | end: generators=%d cables=%d machines=%d "
                                + "buffer=%d | phases: cableDelta=%d bufferDelta=%d | "
                                + "delivery: direct=%d network=%d peak=%d firstFull=%d | "
                                + "fuelBurnTicks=%d (=%d FE) | noEnergy=%d firstOutput=%d | "
                                + "bufferReceived=%d | anomalies %s | "
                                + "perMachine %s | craftTicks %s | trace %s",
                        this.generatorCount, this.bufferName, this.bufferState, this.makespan,
                        this.makespan / 20.0D, this.completed, this.ingots,
                        this.startTotal, this.generated, this.consumed, this.completed * CRAFT_FE,
                        this.endTotal, this.balanceError(),
                        this.generatorStoredEnd, this.cableStoredEnd, this.machineStoredEnd,
                        this.bufferStoredEnd, this.cablePhaseDelta, this.bufferPhaseDelta,
                        this.directReceived, this.networkReceived, this.peakReceived,
                        this.firstFullTick, this.burnTicksEnd, this.burnTicksEnd * 40,
                        this.noEnergyTicks, this.firstOutputTick, this.bufferReceived,
                        this.anomalies.isEmpty() ? "none" : String.join(" | ", this.anomalies),
                        machines.toString(), costs.toString(), String.join(" ", this.trace));
            }
        }
    }
}
