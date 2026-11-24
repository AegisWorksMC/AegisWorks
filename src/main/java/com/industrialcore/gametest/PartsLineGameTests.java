package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ProcessingBalance;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
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

/**
 * GameTests of the Phase 17 machine parts line.
 *
 * <p>Every test drives the real server tick and the real recipe manager:</p>
 * <pre>
 *   iron ingot --metal press (FE)--> iron plate
 *   steel ingot --metal press (FE)--> steel plate
 *   iron ingot + coal --alloy furnace (fuel)--> steel ingot
 *   copper ingot + gold ingot --alloy furnace (fuel)--> brass ingot x2
 *   large energy buffer --push--> an adjacent machine
 * </pre>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartsLineGameTests {
    private static final int Y = 6;
    private static final int START_ENERGY = 20_000;
    /** 60 ticks x 8 FE of the shipped plate recipe. */
    private static final int PLATE_ENERGY = ProcessingBalance.METAL_PRESS_TIME_TICKS
            * ProcessingBalance.METAL_PRESS_ENERGY_PER_TICK;

    private PartsLineGameTests() {}

    // ------------------------------------------------------------------
    // metal press
    // ------------------------------------------------------------------

    /** One iron ingot becomes one iron plate and exactly 480 FE are spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void metalPressMakesAPlate(GameTestHelper helper) {
        ProcessingBlockEntity press = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.METAL_PRESS);
        press.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        press.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 200; tick++) {
            tickMachine(helper, press);
        }

        helper.assertTrue(count(press, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_PLATE.get()) == 1,
                "the press produced "
                        + count(press, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_PLATE.get()) + " iron plates instead of 1");
        helper.assertTrue(count(press, ProcessingBlockEntity.INPUT_SLOT, Items.IRON_INGOT) == 0,
                "the press did not consume the ingot");
        helper.assertTrue(press.getEnergyStoredForTesting() == START_ENERGY - PLATE_ENERGY,
                "the press spent "
                        + (START_ENERGY - press.getEnergyStoredForTesting())
                        + " FE instead of " + PLATE_ENERGY);
        helper.assertItemEntityNotPresent(ModItems.IRON_PLATE.get());
        helper.succeed();
    }

    /** A raw ore has no pressing recipe: nothing is consumed and no FE is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void metalPressRejectsARawOre(GameTestHelper helper) {
        ProcessingBlockEntity press = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.METAL_PRESS);
        press.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 2));
        press.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 60; tick++) {
            tickMachine(helper, press);
        }

        helper.assertTrue(press.getStatus() == MachineStatus.NO_RECIPE,
                "expected NO_RECIPE but was " + press.getStatus());
        helper.assertTrue(press.getEnergyStoredForTesting() == START_ENERGY,
                "the press spent FE on an item without a recipe");
        helper.assertTrue(count(press, ProcessingBlockEntity.INPUT_SLOT, Items.RAW_IRON) == 2,
                "the press consumed the raw ore");
        helper.assertTrue(press.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the press produced an output without a recipe");
        helper.succeed();
    }

    /** An alloy plate needs the alloy ingot, so the press cannot skip the alloy furnace. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void metalPressTurnsSteelIntoASteelPlate(GameTestHelper helper) {
        ProcessingBlockEntity press = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.METAL_PRESS);
        press.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.STEEL_INGOT.get(), 1));
        press.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 200; tick++) {
            tickMachine(helper, press);
        }

        helper.assertTrue(count(press, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_PLATE.get()) == 1,
                "the press did not produce a steel plate");
        int spent = START_ENERGY - press.getEnergyStoredForTesting();
        int expected = ProcessingBalance.ALLOY_PRESS_TIME_TICKS
                * ProcessingBalance.ALLOY_PRESS_ENERGY_PER_TICK;
        helper.assertTrue(spent == expected, "the steel plate cost " + spent
                + " FE instead of " + expected);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // alloy furnace
    // ------------------------------------------------------------------

    /** Two iron ingots become steel; the fuel slot only ever takes fuel. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void alloyFurnaceMakesSteel(GameTestHelper helper) {
        AlloyFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(2, Y, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 2));

        for (int tick = 0; tick < 400; tick++) {
            tickFurnace(helper, furnace);
        }

        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) == 1,
                "the furnace produced "
                        + count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) + " steel instead of 1");
        helper.assertTrue(furnace.getInventory()
                        .getStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT).isEmpty(),
                "the iron was not consumed");
        helper.assertTrue(furnace.getInventory()
                        .getStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT).isEmpty(),
                "the second iron was not consumed");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.FUEL_SLOT, Items.COAL) == 1,
                "the furnace did not burn exactly one fuel item");
        helper.assertItemEntityNotPresent(ModItems.STEEL_INGOT.get());
        helper.succeed();
    }

    /** Copper plus gold becomes two brass ingots. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void alloyFurnaceMakesBrass(GameTestHelper helper) {
        AlloyFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(2, Y, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.COPPER_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.GOLD_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));

        for (int tick = 0; tick < 400; tick++) {
            tickFurnace(helper, furnace);
        }

        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.BRASS_INGOT.get()) == 2,
                "the furnace produced "
                        + count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.BRASS_INGOT.get()) + " brass instead of 2");
        helper.succeed();
    }

    /** Without fuel nothing happens and nothing is consumed. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void alloyFurnaceNeedsFuel(GameTestHelper helper) {
        AlloyFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(2, Y, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));

        for (int tick = 0; tick < 100; tick++) {
            tickFurnace(helper, furnace);
        }

        helper.assertTrue(furnace.getStatus() == MachineStatus.NO_FUEL,
                "expected NO_FUEL but was " + furnace.getStatus());
        helper.assertTrue(furnace.getProgress() == 0,
                "the furnace advanced " + furnace.getProgress() + " ticks without fuel");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.FIRST_SLOT, Items.IRON_INGOT) == 1,
                "the iron was consumed without fuel");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.SECOND_SLOT, Items.IRON_INGOT) == 1,
                "the second iron was consumed without fuel");
        helper.assertTrue(furnace.getInventory()
                        .getStackInSlot(AlloyFurnaceBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the furnace produced an alloy without fuel");

        // Adding fuel lets the same machine continue on its own.
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));
        for (int tick = 0; tick < 400; tick++) {
            tickFurnace(helper, furnace);
        }
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) == 1,
                "the furnace did not resume after fuel was added");
        helper.succeed();
    }

    /** The two ingredients are matched in either order, so a pipe cannot break a recipe. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void alloyFurnaceAcceptsIngredientsInEitherOrder(GameTestHelper helper) {
        AlloyFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(2, Y, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.GOLD_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.COPPER_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));

        for (int tick = 0; tick < 400; tick++) {
            tickFurnace(helper, furnace);
        }

        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.BRASS_INGOT.get()) == 2,
                "the swapped ingredients did not produce brass");
        helper.succeed();
    }

    /** A full output stops the furnace before it burns fuel or eats the ingredients. */
    @GameTest(template = "quarry_test", timeoutTicks = 700)
    public static void alloyFurnaceStopsWhenTheOutputIsFull(GameTestHelper helper) {
        AlloyFurnaceBlockEntity furnace = placeFurnace(helper, new BlockPos(2, Y, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 2));
        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.STEEL_INGOT.get(), 64));

        for (int tick = 0; tick < 100; tick++) {
            tickFurnace(helper, furnace);
        }

        helper.assertTrue(furnace.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + furnace.getStatus());
        helper.assertTrue(furnace.getProgress() == 0, "the blocked furnace advanced its progress");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.FIRST_SLOT,
                        Items.IRON_INGOT) == 1, "the iron was eaten while the output was full");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.FUEL_SLOT, Items.COAL) == 2,
                "fuel was burnt while the output was full");
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) == 64, "the full output stack changed");

        furnace.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        for (int tick = 0; tick < 400; tick++) {
            tickFurnace(helper, furnace);
        }
        helper.assertTrue(count(furnace, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) == 1,
                "the furnace did not resume after the output was emptied");
        helper.succeed();
    }

    /** Inventory, progress and burn state survive a save and load. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void alloyFurnaceSurvivesSaveAndLoad(GameTestHelper helper) {
        AlloyFurnaceBlockEntity original = placeFurnace(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        original.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_SLOT,
                new ItemStack(Items.IRON_INGOT, 1));
        original.getInventory().setStackInSlot(AlloyFurnaceBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 2));
        for (int tick = 0; tick < 50; tick++) {
            tickFurnace(helper, original);
        }
        int savedProgress = original.getProgress();
        int savedBurn = original.getBurnTime();
        helper.assertTrue(savedProgress > 0, "the furnace did not start working");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        AlloyFurnaceBlockEntity restored = placeFurnace(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getBurnTime() == savedBurn,
                "the burn time was restored as " + restored.getBurnTime()
                        + " instead of " + savedBurn);
        helper.assertTrue(count(restored, AlloyFurnaceBlockEntity.FUEL_SLOT, Items.COAL) == 1,
                "the fuel was not restored");

        for (int tick = 0; tick < 400
                && count(restored, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                ModItems.STEEL_INGOT.get()) == 0; tick++) {
            tickFurnace(helper, restored);
        }
        helper.assertTrue(count(restored, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                        ModItems.STEEL_INGOT.get()) == 1,
                "the restored furnace did not finish its run");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // large energy buffer
    // ------------------------------------------------------------------

    /** A charged buffer pushes its FE into the adjacent machine. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void energyBufferFeedsANeighbour(GameTestHelper helper) {
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(2, Y, 2));
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(3, Y, 2),
                ProcessingKind.CRUSHER);
        buffer.setEnergyStored(10_000);

        tickBuffer(helper, buffer);

        int expected = Math.min(Config.energyBufferPushPerTick(), 10_000);
        helper.assertTrue(crusher.getEnergyStoredForTesting() == expected,
                "the crusher received " + crusher.getEnergyStoredForTesting()
                        + " FE instead of " + expected);
        helper.assertTrue(buffer.getEnergyStoredForTesting() == 10_000 - expected,
                "the buffer holds " + buffer.getEnergyStoredForTesting()
                        + " FE instead of " + (10_000 - expected));
        helper.succeed();
    }

    /** The stored FE survives a save and load. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void energyBufferSurvivesSaveAndLoad(GameTestHelper helper) {
        EnergyBufferBlockEntity original = placeBuffer(helper, new BlockPos(2, Y, 2));
        original.setEnergyStored(123_456);

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        EnergyBufferBlockEntity restored = placeBuffer(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getEnergyStoredForTesting() == 123_456,
                "the buffer was restored with " + restored.getEnergyStoredForTesting() + " FE");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ProcessingBlockEntity placeMachine(GameTestHelper helper, BlockPos pos,
                                                      ProcessingKind kind) {
        var block = switch (kind) {
            case METAL_PRESS -> ModBlocks.METAL_PRESS.get();
            case FAST_CRUSHER -> ModBlocks.FAST_CRUSHER.get();
            case CRUSHER -> ModBlocks.CRUSHER.get();
            case ELECTRIC_FURNACE -> ModBlocks.ELECTRIC_FURNACE.get();
            case ELECTROLYZER -> ModBlocks.ELECTROLYZER.get();
            case PRECISION_CRUSHER -> ModBlocks.PRECISION_CRUSHER.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail("processing block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static AlloyFurnaceBlockEntity placeFurnace(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ALLOY_FURNACE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof AlloyFurnaceBlockEntity furnace)) {
            helper.fail("alloy furnace block entity missing at " + pos);
            return null;
        }
        return furnace;
    }

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof EnergyBufferBlockEntity buffer)) {
            helper.fail("energy buffer block entity missing at " + pos);
            return null;
        }
        return buffer;
    }

    private static void tickMachine(GameTestHelper helper, ProcessingBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
    }

    private static void tickFurnace(GameTestHelper helper, AlloyFurnaceBlockEntity furnace) {
        BlockState state = furnace.getBlockState();
        AlloyFurnaceBlockEntity.serverTick(helper.getLevel(), furnace.getBlockPos(), state, furnace);
    }

    private static void tickBuffer(GameTestHelper helper, EnergyBufferBlockEntity buffer) {
        BlockState state = buffer.getBlockState();
        EnergyBufferBlockEntity.serverTick(helper.getLevel(), buffer.getBlockPos(), state, buffer);
    }

    private static int count(ProcessingBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(AlloyFurnaceBlockEntity furnace, int slot, Item item) {
        ItemStack stack = furnace.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }
}
