package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Phase 23: the fast crusher, driven through the real server tick and the real recipe manager.
 *
 * <p>The contract under test: one cycle (the crushing recipe's own 10 ticks for the raw-iron
 * entrance) processes a whole
 * batch of up to eight items, draws recipe FE/t x batch (80 FE/t for raw iron), costs exactly the
 * same FE per item as the normal crusher and never loses or duplicates an item when the input,
 * the output or the power are in the wrong state.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FastCrusherGameTests {
    private static final int Y = 6;

    private FastCrusherGameTests() {}

    /** A full batch of eight raw iron crushes in one cycle: 16 dust, 800 FE, 10 ticks. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void eightRawIronCrushInOneCycle(GameTestHelper helper) {
        ProcessingBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        machine.setEnergyStored(20_000);

        int ticks = 0;
        for (; ticks < 300; ticks++) {
            if (!machine.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty()) {
                break;
            }
            tickMachine(helper, machine);
        }

        ItemStack output = machine.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_DUST.get()) && output.getCount() == 16,
                "8 raw iron must crush into 16 dust in one batch cycle, got " + output);
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                "the batch input was not fully consumed");
        helper.assertTrue(ticks <= 11,
                "the batch took " + ticks + " ticks instead of one 10 tick cycle");
        helper.assertTrue(machine.getEnergyStoredForTesting() == 20_000 - 800,
                "a full batch must cost 800 FE but "
                        + (20_000 - machine.getEnergyStoredForTesting()) + " was spent");
        helper.succeed();
    }

    /** A partial batch of three costs three single crushes: 6 dust, 300 FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aPartialBatchScalesTheEnergy(GameTestHelper helper) {
        ProcessingBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 3));
        machine.setEnergyStored(20_000);

        for (int tick = 0; tick < 300
                && machine.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty();
             tick++) {
            tickMachine(helper, machine);
        }

        ItemStack output = machine.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_DUST.get()) && output.getCount() == 6,
                "3 raw iron must crush into 6 dust, got " + output);
        helper.assertTrue(machine.getEnergyStoredForTesting() == 20_000 - 300,
                "a batch of three must cost 300 FE but "
                        + (20_000 - machine.getEnergyStoredForTesting()) + " was spent");
        helper.succeed();
    }

    /** A full output keeps the whole batch and every FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aFullOutputKeepsTheBatch(GameTestHelper helper) {
        ProcessingBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 64));
        machine.setEnergyStored(20_000);

        for (int tick = 0; tick < 60; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 8,
                "the input was consumed while the output was full");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).getCount() == 64,
                "the full output stack changed");
        helper.assertTrue(machine.getEnergyStoredForTesting() == 20_000,
                "FE was spent while the output was full");
        helper.assertTrue(machine.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + machine.getStatus());
        helper.succeed();
    }

    /** No FE keeps the batch, the progress and the output untouched. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void noPowerKeepsTheBatch(GameTestHelper helper) {
        ProcessingBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        machine.setEnergyStored(0);

        for (int tick = 0; tick < 60; tick++) {
            tickMachine(helper, machine);
        }

        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 8,
                "the input was consumed without power");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "an item was produced without power");
        helper.assertTrue(machine.getProgress() == 0,
                "progress advanced without power: " + machine.getProgress());
        helper.assertTrue(machine.getStatus() == MachineStatus.NO_ENERGY,
                "expected NO_ENERGY but was " + machine.getStatus());
        helper.succeed();
    }

    /** A running batch survives a save/load cycle: progress, batch size and cost. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void aRunningBatchSurvivesSaveAndLoad(GameTestHelper helper) {
        ProcessingBlockEntity original = placeMachine(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        original.setEnergyStored(20_000);
        // Halfway through the 10 tick cycle: long enough to be working, short enough that the
        // batch cannot finish and consume the input before the save.
        for (int tick = 0; tick < 5; tick++) {
            tickMachine(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the machine did not start working");
        helper.assertTrue(original.getEnergyPerTick() == 80,
                "a batch of eight must draw 80 FE/t but drew " + original.getEnergyPerTick());
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        ProcessingBlockEntity restored = placeMachine(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).getCount() == 8,
                "the batch input was not restored");
        helper.assertTrue(restored.getEnergyPerTick() == 80,
                "the batch energy per tick was not restored");

        for (int tick = 0; tick < 300
                && restored.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty();
             tick++) {
            tickMachine(helper, restored);
        }
        ItemStack output = restored.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.IRON_DUST.get()) && output.getCount() == 16,
                "the restored batch did not finish: " + output);
        helper.succeed();
    }

    /** The batch output feeds the existing 2x line: raw ore -> dust -> furnace -> two ingots. */
    @GameTest(template = "quarry_test", timeoutTicks = 1200)
    public static void theBatchOutputFeedsTheExistingOreLine(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2));
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        crusher.setEnergyStored(20_000);
        for (int tick = 0; tick < 300
                && crusher.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty();
             tick++) {
            tickMachine(helper, crusher);
        }
        ItemStack dust = crusher.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).copy();
        helper.assertTrue(dust.is(ModItems.IRON_DUST.get()) && dust.getCount() == 2,
                "one raw iron must still crush into two dust, got " + dust);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);

        ProcessingBlockEntity furnace = placeProcessing(helper, new BlockPos(4, Y, 2),
                ModBlocks.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, dust);
        furnace.setEnergyStored(20_000);
        for (int tick = 0; tick < 600
                && furnace.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).getCount() < 2;
             tick++) {
            tickMachine(helper, furnace);
        }
        ItemStack ingots = furnace.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(ingots.is(Items.IRON_INGOT) && ingots.getCount() == 2,
                "the fast crusher output must smelt into two ingots, got " + ingots);
        helper.succeed();
    }

    /** A pipe pulls the finished batch out of the machine and carries it into a chest. */
    @GameTest(template = "quarry_test", timeoutTicks = 1200)
    public static void aPipeExtractsTheBatchOutput(GameTestHelper helper) {
        ProcessingBlockEntity machine = placeMachine(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 4));
        machine.setEnergyStored(20_000);
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, Y, 2));
        Container chest = placeChest(helper, new BlockPos(4, Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);

        for (int tick = 0; tick < 900 && count(chest, ModItems.IRON_DUST.get()) < 8; tick++) {
            tickMachine(helper, machine);
            tickPipe(helper, pipe);
        }

        helper.assertTrue(count(chest, ModItems.IRON_DUST.get()) == 8,
                "the chest received " + count(chest, ModItems.IRON_DUST.get()) + " of 8 dust");
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the machine still holds output the chest should have received");
        helper.assertTrue(pipe.getBufferCount() == 0,
                "the pipe still holds " + pipe.getBufferCount() + " dust");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ProcessingBlockEntity placeMachine(GameTestHelper helper, BlockPos pos) {
        return placeProcessing(helper, pos, ModBlocks.FAST_CRUSHER);
    }

    private static ProcessingBlockEntity placeProcessing(
            GameTestHelper helper, BlockPos pos,
            net.neoforged.neoforge.registries.DeferredBlock<
                    com.industrialcore.block.ProcessingBlock> block) {
        helper.setBlock(pos, block.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail("processing block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static void tickMachine(GameTestHelper helper, ProcessingBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
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

    private static Container placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof Container container)) {
            helper.fail("chest block entity missing at " + pos);
            return null;
        }
        return container;
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }

    private static int count(Container container, Item item) {
        return container == null ? 0 : container.countItem(item);
    }
}
