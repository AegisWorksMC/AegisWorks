package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.ReconstructorBlockEntity;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests of the Phase 15 matter reconstructor.
 *
 * <p>All tests drive the real server tick and the real recipe manager, so the analysis, the
 * energy cost, the material consumption and the save/load cycle are checked exactly as they
 * happen in a world.</p>
 *
 * <p>The line under test:</p>
 * <pre>
 *   pure iron sample -> analysis -> iron unlocked
 *   stone x8 + reconstruction crystal x1 + 4,000 FE -> raw iron x1
 *   raw iron -> crusher -> 2 iron dust
 * </pre>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReconstructorGameTests {
    private static final int Y = 6;
    /** Energy of the iron recipe: 200 ticks x 20 FE. */
    private static final int IRON_ENERGY = 4_000;
    /** Starting energy of the test machines. */
    private static final int START_ENERGY = 20_000;

    private ReconstructorGameTests() {}

    // ------------------------------------------------------------------
    // The seven tests of the Phase 15 specification
    // ------------------------------------------------------------------

    /** Without an analysis nothing is produced, and no FE and no material is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void reconstructorDoesNotGenerateBeforeAnalysis(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(machine.getStatus() == MachineStatus.NO_SELECTION,
                "expected NO_SELECTION but was " + machine.getStatus());
        helper.assertTrue(count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 0,
                "the machine produced a raw ore without an analysis");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ReconstructorBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the output slot is not empty");
        helper.assertTrue(machine.getEnergyStoredForTesting() == START_ENERGY,
                "the machine spent " + (START_ENERGY - machine.getEnergyStoredForTesting())
                        + " FE without an analysis");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.MATERIAL_SLOT, Items.STONE) == 8,
                "the material was consumed without an analysis");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.CRYSTAL_SLOT,
                        ModItems.RECONSTRUCTION_CRYSTAL.get()) == 1,
                "the crystal was consumed without an analysis");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.ANALYSIS_SLOT,
                        ModItems.PURE_IRON.get()) == 1,
                "the sample was consumed without an analysis");
        helper.assertTrue(machine.getProgress() == 0, "the progress advanced without an analysis");
        helper.succeed();
    }

    /** The analysis unlocks the mineral once, eats one sample and refuses the second attempt. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void reconstructorAnalysisUnlocksTheMineralOnce(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 2));

        ReconstructorBlockEntity.AnalysisResult first = machine.analyzeSample();
        helper.assertTrue(first == ReconstructorBlockEntity.AnalysisResult.UNLOCKED,
                "the analysis failed: " + first);
        helper.assertTrue(machine.getUnlockedMinerals().equals(List.of("iron")),
                "the unlocked minerals are " + machine.getUnlockedMinerals());
        helper.assertTrue("iron".equals(machine.getSelectedMineral()),
                "the unlocked mineral was not selected: " + machine.getSelectedMineral());
        helper.assertTrue(count(machine, ReconstructorBlockEntity.ANALYSIS_SLOT,
                        ModItems.PURE_IRON.get()) == 1,
                "the analysis did not consume exactly one sample");

        ReconstructorBlockEntity.AnalysisResult second = machine.analyzeSample();
        helper.assertTrue(second == ReconstructorBlockEntity.AnalysisResult.ALREADY_UNLOCKED,
                "the second analysis was not rejected: " + second);
        helper.assertTrue(count(machine, ReconstructorBlockEntity.ANALYSIS_SLOT,
                        ModItems.PURE_IRON.get()) == 1,
                "the second analysis consumed the sample");
        helper.assertTrue(machine.getUnlockedMinerals().equals(List.of("iron")),
                "the second analysis changed the unlocked set: " + machine.getUnlockedMinerals());
        helper.succeed();
    }

    /** One run eats 8 stone, 1 crystal and 4,000 FE and gives exactly one raw iron. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void reconstructorConsumesMaterialCrystalAndEnergy(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        helper.assertTrue(machine.analyzeSample() == ReconstructorBlockEntity.AnalysisResult.UNLOCKED,
                "the analysis failed");
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 1,
                "the machine produced "
                        + count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON)
                        + " raw iron instead of 1");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT).isEmpty(),
                "the material was not consumed");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT).isEmpty(),
                "the crystal was not consumed");
        helper.assertTrue(machine.getEnergyStoredForTesting() == START_ENERGY - IRON_ENERGY,
                "the machine spent " + (START_ENERGY - machine.getEnergyStoredForTesting())
                        + " FE instead of " + IRON_ENERGY);
        helper.assertItemEntityNotPresent(Items.RAW_IRON);
        helper.succeed();
    }

    /** A full output stops the machine without eating material, crystal or FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void reconstructorStopsWhenTheOutputIsFull(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        machine.analyzeSample();
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.OUTPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 64));
        machine.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(machine.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + machine.getStatus());
        helper.assertTrue(machine.getEnergyStoredForTesting() == START_ENERGY,
                "the blocked machine spent "
                        + (START_ENERGY - machine.getEnergyStoredForTesting()) + " FE");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.MATERIAL_SLOT, Items.STONE) == 8,
                "the material was eaten while the output was full");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.CRYSTAL_SLOT,
                        ModItems.RECONSTRUCTION_CRYSTAL.get()) == 1,
                "the crystal was eaten while the output was full");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 64,
                "the full output stack changed");

        // Emptying the output lets the machine continue on its own.
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        for (int tick = 0; tick < 300; tick++) {
            tickMachine(helper, machine);
        }
        helper.assertTrue(count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 1,
                "the machine did not resume after the output was emptied");
        helper.assertTrue(machine.getEnergyStoredForTesting() == START_ENERGY - IRON_ENERGY,
                "the resumed machine spent "
                        + (START_ENERGY - machine.getEnergyStoredForTesting()) + " FE");
        helper.succeed();
    }

    /** The unlocked set, the selection, the progress, the FE and the stock survive a reload. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void reconstructorSurvivesSaveAndLoad(GameTestHelper helper) {
        ReconstructorBlockEntity original = placeMachine(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        original.analyzeSample();
        original.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        original.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        original.setEnergyStored(15_000);
        for (int tick = 0; tick < 50; tick++) {
            tickMachine(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the machine did not start working");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        ReconstructorBlockEntity restored = placeMachine(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getUnlockedMinerals().equals(List.of("iron")),
                "the unlocked minerals were lost: " + restored.getUnlockedMinerals());
        helper.assertTrue("iron".equals(restored.getSelectedMineral()),
                "the selected mineral was lost: " + restored.getSelectedMineral());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getEnergyStoredForTesting() == original.getEnergyStoredForTesting(),
                "the FE was restored as " + restored.getEnergyStoredForTesting());
        helper.assertTrue(count(restored, ReconstructorBlockEntity.MATERIAL_SLOT, Items.STONE) == 8,
                "the material was lost");
        helper.assertTrue(count(restored, ReconstructorBlockEntity.CRYSTAL_SLOT,
                        ModItems.RECONSTRUCTION_CRYSTAL.get()) == 1,
                "the crystal was lost");

        for (int tick = 0; tick < 300
                && count(restored, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 0; tick++) {
            tickMachine(helper, restored);
        }
        helper.assertTrue(count(restored, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 1,
                "the restored machine did not finish its run");
        helper.succeed();
    }

    /** One completion gives exactly one raw ore, never two. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void reconstructorNeverProducesTwoOutputsAtOnce(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        machine.analyzeSample();
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 400; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON) == 1,
                "the machine produced "
                        + count(machine, ReconstructorBlockEntity.OUTPUT_SLOT, Items.RAW_IRON)
                        + " raw iron instead of 1");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT).isEmpty(),
                "the material was not consumed");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT).isEmpty(),
                "the crystal was not consumed");
        helper.assertItemEntityNotPresent(Items.RAW_IRON);
        helper.succeed();
    }

    /** The produced raw ore flows through a pipe into the crusher and becomes two dust. */
    @GameTest(template = "quarry_test", timeoutTicks = 1200)
    public static void reconstructorOutputFeedsTheOreLine(GameTestHelper helper) {
        ReconstructorBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, Y, 2));
        ProcessingBlockEntity crusher = placeCrusher(helper, new BlockPos(4, Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);

        // The pipe may only take the raw ore from the machine: the filter of the east face
        // allows nothing else, so the material and the crystal stay in the machine.
        int east = Direction.EAST.get3DDataValue();
        helper.assertTrue(machine.applyFaceAction(east, MachineActions.FACE_FILTER, ItemStack.EMPTY),
                "the filter action was rejected");
        helper.assertTrue(machine.applyFaceAction(east, MachineActions.FACE_ADD_ITEM,
                        new ItemStack(Items.RAW_IRON)),
                "the filter item was rejected");

        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.ANALYSIS_SLOT,
                new ItemStack(ModItems.PURE_IRON.get(), 1));
        machine.analyzeSample();
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.MATERIAL_SLOT,
                new ItemStack(Items.STONE, 8));
        machine.getInventory().setStackInSlot(ReconstructorBlockEntity.CRYSTAL_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.setEnergyStored(START_ENERGY);
        crusher.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 900
                && count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, ModItems.IRON_DUST.get()) == 0;
             tick++) {
            tickMachine(helper, machine);
            tickPipe(helper, pipe);
            tickMachine(helper, crusher);
        }

        helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 2,
                "the crusher produced "
                        + count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, ModItems.IRON_DUST.get())
                        + " iron dust instead of 2");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.MATERIAL_SLOT, Items.STONE) == 0,
                "the material was not consumed by the reconstruction");
        helper.assertTrue(count(machine, ReconstructorBlockEntity.CRYSTAL_SLOT,
                        ModItems.RECONSTRUCTION_CRYSTAL.get()) == 0,
                "the crystal was not consumed by the reconstruction");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds an item");
        helper.assertItemEntityNotPresent(Items.RAW_IRON);
        helper.assertItemEntityNotPresent(ModItems.IRON_DUST.get());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ReconstructorBlockEntity placeMachine(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.RECONSTRUCTOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ReconstructorBlockEntity machine)) {
            helper.fail("reconstructor block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static ProcessingBlockEntity placeCrusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRUSHER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail("crusher block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static ItemPipeBlockEntity placePipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_PIPE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ItemPipeBlockEntity pipe)) {
            helper.fail("item pipe block entity missing at " + pos);
            return null;
        }
        return pipe;
    }

    private static void tickMachine(GameTestHelper helper, ReconstructorBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ReconstructorBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
    }

    private static void tickMachine(GameTestHelper helper, ProcessingBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }

    private static int count(ReconstructorBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(ProcessingBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }
}
