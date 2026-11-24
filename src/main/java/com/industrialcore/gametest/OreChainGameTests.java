package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 31: the fifteen stage ore line, driven through the real server tick and recipe manager.
 *
 * <p>Line under test (the first half of the chain; stages 7-15 run in the synthesizer and are
 * audited by {@code Phase30GameTests} and the unit tests):</p>
 * <pre>
 *   stage 1  raw ore    -> crusher            -> 2 dust
 *   stage 2  dust       -> electrolyzer       -> 2 pure ore per dust
 *   stage 3  pure ore   -> precision crusher  -> 2 exposed per pure ore
 *   stage 4  exposed    -> metal press        -> 2 washed
 *   ... banking at grade 3: one raw ore smelts into eight ingots
 * </pre>
 * <p>The old 3x / 6x lines these tests used to drive were removed with the old multiplication
 * line: their recipes no longer exist, so the tests now prove the stages that replaced them.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreChainGameTests {
    private static final int Y = 6;

    private OreChainGameTests() {}

    /** Stage 1: one raw ore becomes two dust. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void theCrusherDoublesARawOreIntoDust(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        crusher.setEnergyStored(20_000);
        runUntilInputEmpty(helper, crusher, 300);

        ItemStack output = crusher.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_DUST.get()) && output.getCount() == 2,
                "expected two iron dust, got " + output);
        helper.succeed();
    }

    /** Stage 2: one dust becomes two pure ore. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void theElectrolyzerDoublesDustIntoPureOre(GameTestHelper helper) {
        ProcessingBlockEntity electrolyzer = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTROLYZER);
        electrolyzer.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 1));
        electrolyzer.setEnergyStored(20_000);
        runUntilOutput(helper, electrolyzer, 400);

        ItemStack output = electrolyzer.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.PURE_IRON.get()) && output.getCount() == 2,
                "expected two pure iron, got " + output);
        helper.assertTrue(electrolyzer.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                "the dust was not consumed");
        helper.succeed();
    }

    /** Stage 3: one pure ore becomes two exposed ore; raw ore and ore blocks are refused. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void thePrecisionCrusherOnlyTakesAPureOre(GameTestHelper helper) {
        ProcessingBlockEntity precision = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.PRECISION_CRUSHER);
        precision.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 4));
        precision.setEnergyStored(20_000);
        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, precision);
        }
        helper.assertTrue(precision.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the precision crusher accepted a raw ore");
        helper.assertTrue(precision.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 4,
                "the rejected raw ore was consumed");

        precision.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.IRON_ORE, 1));
        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, precision);
        }
        helper.assertTrue(precision.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "an ore block is no longer the 6x entry and must be refused");
        precision.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                ItemStack.EMPTY);

        precision.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        for (int tick = 0; tick < 400; tick++) {
            tickMachine(helper, precision);
        }
        ItemStack output = precision.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.EXPOSED_IRON.get()) && output.getCount() == 2,
                "expected two exposed iron, got " + output);
        helper.succeed();
    }

    /** Stage 4: the metal press washes exposed ore into the washed grade, two at a time. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void theMetalPressWashesExposedOre(GameTestHelper helper) {
        ProcessingBlockEntity press = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.METAL_PRESS);
        press.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.EXPOSED_IRON.get(), 1));
        press.setEnergyStored(20_000);
        runUntilOutput(helper, press, 300);

        ItemStack output = press.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_WASHED.get()) && output.getCount() == 2,
                "expected two washed iron, got " + output);
        helper.succeed();
    }

    /**
     * The bankable checkpoint of the first three stages: one raw ore becomes eight exposed ore
     * and smelts into eight ingots, the old lines' x3 / x6 entries folded into one chain.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 3000)
    public static void oneRawOreBankingAtGradeThreeYieldsEightIngots(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        crusher.setEnergyStored(20_000);
        runUntilInputEmpty(helper, crusher, 400);
        ItemStack dust = takeOutput(crusher);
        helper.assertTrue(dust.is(ModItems.IRON_DUST.get()) && dust.getCount() == 2,
                "stage 1 produced " + dust);

        ProcessingBlockEntity electrolyzer = placeMachine(helper, new BlockPos(2, Y, 6),
                ProcessingKind.ELECTROLYZER);
        electrolyzer.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, dust);
        electrolyzer.setEnergyStored(20_000);
        runUntilInputEmpty(helper, electrolyzer, 800);
        ItemStack pure = takeOutput(electrolyzer);
        helper.assertTrue(pure.is(ModItems.PURE_IRON.get()) && pure.getCount() == 4,
                "stage 2 produced " + pure);

        ProcessingBlockEntity precision = placeMachine(helper, new BlockPos(2, Y, 10),
                ProcessingKind.PRECISION_CRUSHER);
        precision.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, pure);
        precision.setEnergyStored(20_000);
        runUntilInputEmpty(helper, precision, 800);
        ItemStack exposed = takeOutput(precision);
        helper.assertTrue(exposed.is(ModItems.EXPOSED_IRON.get()) && exposed.getCount() == 8,
                "stage 3 produced " + exposed);

        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 14),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, exposed);
        furnace.setEnergyStored(20_000);
        for (int tick = 0; tick < 1_200
                && furnace.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT)
                .getCount() < 8; tick++) {
            tickMachine(helper, furnace);
        }
        ItemStack ingots = furnace.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(ingots.is(Items.IRON_INGOT) && ingots.getCount() == 8,
                "the grade 3 checkpoint produced " + ingots + " instead of 8 ingots");
        helper.succeed();
    }

    /**
     * Stage 7 in the synthesizer: sixteen concentrate alone become thirty-two refined iron. The
     * middle stages are not catalyst gated - a consumed catalyst per operation would cost more
     * metal than the batch yields - so the second slot must stay empty and unused.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void theMiddleStageNeedsNoCatalyst(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(0,
                new ItemStack(ModItems.IRON_CONCENTRATE.get(), 16));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        runSynthesizer(helper, machine, SynthesisBalance.TIME_TICKS + 100);

        ItemStack output = machine.getInventory()
                .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_REFINED.get()) && output.getCount() == 32,
                "stage 7 produced " + output + " instead of 32 refined iron");
        helper.assertTrue(machine.getInventory().getStackInSlot(1).isEmpty(),
                "stage 7 must not need a catalyst");
        helper.succeed();
    }

    /**
     * Stage 15 in the synthesizer: thirty-two stabilized iron plus one ultimate synthetic ore
     * become sixty-four ingots, and the catalyst is consumed by the craft.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void theFinalStageConsumesItsCatalyst(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 6));
        machine.getInventory().setStackInSlot(0,
                new ItemStack(ModItems.IRON_STABILIZED.get(), 32));
        machine.getInventory().setStackInSlot(1,
                new ItemStack(ModItems.SYNTHETIC_ORE_ULTIMATE.get(), 1));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        runSynthesizer(helper, machine, SynthesisBalance.ULTIMATE_TIME_TICKS + 100);

        ItemStack output = machine.getInventory()
                .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(Items.IRON_INGOT) && output.getCount() == 64,
                "stage 15 produced " + output + " instead of 64 iron ingots");
        helper.assertTrue(machine.getInventory().getStackInSlot(1).isEmpty(),
                "the ultimate synthetic ore catalyst was not consumed");
        helper.succeed();
    }

    /**
     * A power cut in the middle of stage 15 must not cost the run. The synthesizer charges its
     * 120 FE/tick one tick at a time, so the 1,200 FE of one operation are never held at once
     * and the 40,000 FE buffer is enough as long as a cable keeps feeding it. When the feed
     * stops, the machine reports no energy and leaves both the progress and the ingredients
     * alone; when it comes back, the operation resumes and finishes.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 1400)
    public static void aPowerCutKeepsTheProgressOfTheFinalStage(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 10));
        machine.getInventory().setStackInSlot(0,
                new ItemStack(ModItems.IRON_STABILIZED.get(), 32));
        machine.getInventory().setStackInSlot(1,
                new ItemStack(ModItems.SYNTHETIC_ORE_ULTIMATE.get(), 1));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        // Halfway into the 10 tick operation: enough to prove it started, short enough that it
        // cannot finish and consume the ingredients before the power is cut.
        for (int tick = 0; tick < 5; tick++) {
            machine.setEnergyStored(SynthesisBalance.CAPACITY);
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(),
                    machine.getBlockState(), machine);
        }
        int progress = machine.getProgressForTesting();
        helper.assertTrue(progress > 0,
                "the final stage did not start within 5 ticks of power");

        // The cable is gone: fifty ticks with an empty buffer.
        for (int tick = 0; tick < 50; tick++) {
            machine.setEnergyStored(0);
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(),
                    machine.getBlockState(), machine);
        }
        helper.assertTrue(machine.getStatusForTesting() == MachineStatus.NO_ENERGY,
                "an empty buffer reported " + machine.getStatusForTesting());
        helper.assertTrue(machine.getProgressForTesting() == progress,
                "the power cut moved the progress from " + progress + " to "
                        + machine.getProgressForTesting());
        helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 32,
                "the power cut consumed the stabilized grade");
        helper.assertTrue(machine.getInventory().getStackInSlot(1).getCount() == 1,
                "the power cut consumed the catalyst");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the power cut produced an item");

        // Power returns; the same operation has to finish without the player touching anything.
        for (int tick = 0; tick < SynthesisBalance.ULTIMATE_TIME_TICKS + 100; tick++) {
            machine.setEnergyStored(SynthesisBalance.CAPACITY);
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(),
                    machine.getBlockState(), machine);
            if (!machine.getInventory()
                    .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
                break;
            }
        }
        ItemStack output = machine.getInventory()
                .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(Items.IRON_INGOT) && output.getCount() == 64,
                "the resumed run produced " + output + " instead of 64 iron ingots");
        helper.assertTrue(machine.getInventory().getStackInSlot(1).isEmpty(),
                "the resumed run did not consume the catalyst");
        helper.succeed();
    }

    /**
     * Ticks the machine with a cable behind it: the 72,000 FE of a stage-15 run do not fit in the
     * 40,000 FE buffer, so the test refills every tick the way a connected network does.
     */
    private static void runSynthesizer(GameTestHelper helper, OreSynthesizerBlockEntity machine,
                                       int limit) {
        for (int tick = 0; tick < limit
                && machine.getInventory().getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT)
                .isEmpty(); tick++) {
            machine.setEnergyStored(SynthesisBalance.CAPACITY);
            BlockState state = machine.getBlockState();
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state,
                    machine);
        }
    }

    /** No FE must never consume the input or create progress. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void noPowerLeavesTheInputUntouched(GameTestHelper helper) {
        ProcessingBlockEntity electrolyzer = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTROLYZER);
        electrolyzer.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 2));
        electrolyzer.setEnergyStored(0);
        for (int tick = 0; tick < 200; tick++) {
            tickMachine(helper, electrolyzer);
        }
        helper.assertTrue(electrolyzer.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 2,
                "the input was consumed without power");
        helper.assertTrue(electrolyzer.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "an item was produced without power");
        helper.assertTrue(electrolyzer.getProgress() == 0,
                "progress advanced without power: " + electrolyzer.getProgress());
        helper.succeed();
    }

    /** A full output keeps the input and does not spend FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fullOutputKeepsTheInput(GameTestHelper helper) {
        ProcessingBlockEntity electrolyzer = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTROLYZER);
        electrolyzer.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 1));
        electrolyzer.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 64));
        electrolyzer.setEnergyStored(20_000);
        for (int tick = 0; tick < 200; tick++) {
            tickMachine(helper, electrolyzer);
        }
        helper.assertTrue(electrolyzer.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 1,
                "the input was consumed while the output was full");
        helper.assertTrue(electrolyzer.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).getCount() == 64,
                "the full output stack changed");
        helper.assertTrue(electrolyzer.getEnergyStoredForTesting() == 20_000,
                "FE was spent while the output was full");
        helper.succeed();
    }

    /** Progress, the locked input and the planned result survive a save/load cycle. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void processingSurvivesSaveAndLoad(GameTestHelper helper) {
        ProcessingBlockEntity original = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.PRECISION_CRUSHER);
        original.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.PURE_GOLD.get(), 2));
        original.setEnergyStored(20_000);
        // Half of one 10 tick precision crush: a whole multiple would land exactly on a
        // finished operation, where the progress counter is back at zero.
        for (int tick = 0; tick < 5; tick++) {
            tickMachine(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the machine did not start working");
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        ProcessingBlockEntity restored = placeMachine(helper, new BlockPos(5, Y, 2),
                ProcessingKind.PRECISION_CRUSHER);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "progress was restored as " + restored.getProgress() + " instead of " + savedProgress);
        helper.assertTrue(restored.getInventory().getStackInSlot(ProcessingBlockEntity.INPUT_SLOT)
                        .is(ModItems.PURE_GOLD.get()),
                "the input was not restored");

        for (int tick = 0; tick < 400
                && restored.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty();
             tick++) {
            tickMachine(helper, restored);
        }
        helper.assertTrue(restored.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.EXPOSED_GOLD.get()),
                "the restored machine did not finish its operation");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ItemStack takeOutput(ProcessingBlockEntity machine) {
        ItemStack output = machine.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).copy();
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        return output;
    }

    private static void runUntilOutput(GameTestHelper helper, ProcessingBlockEntity machine,
                                       int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (!machine.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty()) {
                return;
            }
            tickMachine(helper, machine);
        }
    }

    /** Ticks until the input slot drains, so a multi-item batch runs through completely. */
    private static void runUntilInputEmpty(GameTestHelper helper, ProcessingBlockEntity machine,
                                           int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (machine.getInventory().getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty()) {
                return;
            }
            tickMachine(helper, machine);
        }
    }

    private static ProcessingBlockEntity placeMachine(GameTestHelper helper, BlockPos pos,
                                                      ProcessingKind kind) {
        Block block = switch (kind) {
            case CRUSHER -> ModBlocks.CRUSHER.get();
            case ELECTRIC_FURNACE -> ModBlocks.ELECTRIC_FURNACE.get();
            case ELECTROLYZER -> ModBlocks.ELECTROLYZER.get();
            case PRECISION_CRUSHER -> ModBlocks.PRECISION_CRUSHER.get();
            case METAL_PRESS -> ModBlocks.METAL_PRESS.get();
            case FAST_CRUSHER -> ModBlocks.FAST_CRUSHER.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail("processing block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static OreSynthesizerBlockEntity placeSynthesizer(GameTestHelper helper,
                                                              BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof OreSynthesizerBlockEntity machine)) {
            helper.fail("ore synthesizer block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static void tickMachine(GameTestHelper helper, ProcessingBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
    }
}
