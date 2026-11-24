package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.AssemblerBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.machine.AssemblerBalance;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.machine.MachineStatus;
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
 * GameTests of the Phase 18 assembler line.
 *
 * <p>Every test drives the real server tick and the real recipe manager:</p>
 * <pre>
 *   copper plate + gold plate + redstone --assembler (FE)--> 2 electronic components
 *   machine frame + steel plate + brass plate --assembler--> reinforced machine frame
 *   reinforced energy buffer --push--> an adjacent machine
 * </pre>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssemblerGameTests {
    private static final int Y = 6;
    private static final int START_ENERGY = 20_000;

    /** 120 ticks x 10 FE of the shipped electronic component recipe. */
    private static final int COMPONENT_ENERGY = AssemblerBalance.COMPONENT_TIME_TICKS
            * AssemblerBalance.COMPONENT_ENERGY_PER_TICK;

    private AssemblerGameTests() {}

    /** The datapack recipes are parsed: both assembling recipes exist and produce the right item. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void assemblingRecipesAreLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(com.industrialcore.registry.ModRecipes.ASSEMBLING_TYPE.get());
        helper.assertTrue(recipes.size() == 2,
                "loaded " + recipes.size() + " assembling recipes instead of 2");
        boolean component = false;
        boolean frame = false;
        for (var holder : recipes) {
            var value = holder.value();
            if (value.result().is(ModItems.ELECTRONIC_COMPONENT.get())) {
                component = true;
                helper.assertTrue(value.result().getCount() == 2,
                        "the component recipe produces " + value.result().getCount());
                helper.assertTrue(value.ingredients().size() == 3,
                        "the component recipe has " + value.ingredients().size()
                                + " ingredients instead of 3");
            }
            if (value.result().is(ModItems.REINFORCED_MACHINE_FRAME.get())) {
                frame = true;
            }
        }
        helper.assertTrue(component, "the electronic component recipe did not load");
        helper.assertTrue(frame, "the reinforced machine frame recipe did not load");
        helper.succeed();
    }

    /** Three ingredients become two electronic components and exactly 1,200 FE are spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void assemblerMakesTwoElectronicComponents(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        fillComponents(assembler);
        assembler.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 200; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) == 2,
                "the assembler produced "
                        + count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) + " components instead of 2");
        helper.assertTrue(assembler.getInventory()
                        .getStackInSlot(AssemblerBlockEntity.INPUT_START).isEmpty(),
                "the copper plate was not consumed");
        helper.assertTrue(assembler.getEnergyStoredForTesting() == START_ENERGY - COMPONENT_ENERGY,
                "the assembler spent "
                        + (START_ENERGY - assembler.getEnergyStoredForTesting())
                        + " FE instead of " + COMPONENT_ENERGY);
        helper.assertItemEntityNotPresent(ModItems.ELECTRONIC_COMPONENT.get());
        helper.succeed();
    }

    /** The ingredient order does not matter, so a pipe cannot break the recipe. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void assemblerAcceptsIngredientsInAnyOrder(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START,
                new ItemStack(Items.REDSTONE, 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.GOLD_PLATE.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 2,
                new ItemStack(ModItems.COPPER_PLATE.get(), 1));
        assembler.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 200; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) == 2,
                "the swapped ingredients did not produce two components");
        helper.succeed();
    }

    /**
     * Two ingredients are not enough: the machine reports NO_INPUT (it waits for the third slot),
     * consumes nothing and spends no FE.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void assemblerNeedsAllThreeInputs(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START,
                new ItemStack(ModItems.COPPER_PLATE.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.GOLD_PLATE.get(), 1));
        assembler.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 100; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(assembler.getStatus() == MachineStatus.NO_INPUT,
                "expected NO_INPUT but was " + assembler.getStatus());
        helper.assertTrue(assembler.getProgress() == 0,
                "the assembler advanced " + assembler.getProgress() + " ticks with two inputs");
        helper.assertTrue(assembler.getEnergyStoredForTesting() == START_ENERGY,
                "the assembler spent FE without a complete recipe");
        helper.assertTrue(assembler.getInventory()
                        .getStackInSlot(AssemblerBlockEntity.INPUT_START).is(
                                ModItems.COPPER_PLATE.get()),
                "the copper plate was consumed without a recipe");
        helper.assertTrue(assembler.getInventory()
                        .getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the assembler produced an output without a recipe");
        helper.succeed();
    }

    /** A full output freezes the machine before any FE is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void assemblerStopsWhenTheOutputIsFull(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        fillComponents(assembler);
        assembler.setEnergyStored(START_ENERGY);
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.ELECTRONIC_COMPONENT.get(), 64));

        for (int tick = 0; tick < 100; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(assembler.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + assembler.getStatus());
        helper.assertTrue(assembler.getProgress() == 0,
                "the blocked assembler advanced its progress");
        helper.assertTrue(assembler.getEnergyStoredForTesting() == START_ENERGY,
                "the blocked assembler spent FE");
        helper.assertTrue(assembler.getInventory()
                        .getStackInSlot(AssemblerBlockEntity.INPUT_START).getCount() == 1,
                "the ingredients were eaten while the output was full");

        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        for (int tick = 0; tick < 200; tick++) {
            tick(helper, assembler);
        }
        helper.assertTrue(count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) == 2,
                "the assembler did not resume after the output was emptied");
        helper.succeed();
    }

    /** Without FE the run waits and keeps its ingredients. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void assemblerNeedsEnergy(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        fillComponents(assembler);

        for (int tick = 0; tick < 100; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(assembler.getStatus() == MachineStatus.NO_ENERGY,
                "expected NO_ENERGY but was " + assembler.getStatus());
        helper.assertTrue(assembler.getProgress() == 0,
                "the assembler advanced without FE");
        helper.assertTrue(count(assembler, AssemblerBlockEntity.INPUT_START,
                        ModItems.COPPER_PLATE.get()) == 1,
                "the copper plate was consumed without FE");

        assembler.setEnergyStored(START_ENERGY);
        for (int tick = 0; tick < 200; tick++) {
            tick(helper, assembler);
        }
        helper.assertTrue(count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) == 2,
                "the assembler did not resume after FE was added");
        helper.assertTrue(assembler.getEnergyStoredForTesting() == START_ENERGY - COMPONENT_ENERGY,
                "the resumed run cost "
                        + (START_ENERGY - assembler.getEnergyStoredForTesting()) + " FE");
        helper.succeed();
    }

    /** The reinforced frame needs the Phase 17 frame, so the press and the alloy line come first. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void assemblerMakesAReinforcedMachineFrame(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START,
                new ItemStack(ModItems.MACHINE_FRAME.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.STEEL_PLATE.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 2,
                new ItemStack(ModItems.BRASS_PLATE.get(), 1));
        assembler.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 300; tick++) {
            tick(helper, assembler);
        }

        helper.assertTrue(count(assembler, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.REINFORCED_MACHINE_FRAME.get()) == 1,
                "the assembler did not produce a reinforced machine frame");
        int expected = AssemblerBalance.REINFORCED_FRAME_TIME_TICKS
                * AssemblerBalance.REINFORCED_FRAME_ENERGY_PER_TICK;
        int spent = START_ENERGY - assembler.getEnergyStoredForTesting();
        helper.assertTrue(spent == expected,
                "the reinforced frame cost " + spent + " FE instead of " + expected);
        helper.succeed();
    }

    /** Changing an ingredient discards the partial progress instead of finishing something else. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void assemblerDiscardsProgressWhenAnIngredientChanges(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));
        fillComponents(assembler);
        assembler.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 60; tick++) {
            tick(helper, assembler);
        }
        helper.assertTrue(assembler.getProgress() > 0, "the assembler did not start working");

        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START,
                new ItemStack(ModItems.IRON_PLATE.get(), 1));
        tick(helper, assembler);

        helper.assertTrue(assembler.getProgress() == 0,
                "the assembler kept " + assembler.getProgress() + " progress after a slot change");
        for (int tick = 0; tick < 20; tick++) {
            tick(helper, assembler);
        }
        helper.assertTrue(assembler.getInventory()
                        .getStackInSlot(AssemblerBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the assembler finished a recipe whose ingredients were replaced");
        helper.succeed();
    }

    /** Inventory, FE, progress and the locked plan survive a save and load. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void assemblerSurvivesSaveAndLoad(GameTestHelper helper) {
        AssemblerBlockEntity original = placeAssembler(helper, new BlockPos(2, Y, 2));
        fillComponents(original);
        original.setEnergyStored(START_ENERGY);
        for (int tick = 0; tick < 60; tick++) {
            tick(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the assembler did not start working");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        AssemblerBlockEntity restored = placeAssembler(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getEnergyStoredForTesting()
                        == original.getEnergyStoredForTesting(),
                "the FE buffer was not restored");

        for (int tick = 0; tick < 200
                && count(restored, AssemblerBlockEntity.OUTPUT_SLOT,
                ModItems.ELECTRONIC_COMPONENT.get()) == 0; tick++) {
            tick(helper, restored);
        }
        helper.assertTrue(count(restored, AssemblerBlockEntity.OUTPUT_SLOT,
                        ModItems.ELECTRONIC_COMPONENT.get()) == 2,
                "the restored assembler did not finish its run");
        helper.succeed();
    }

    /** The automation view rejects items that appear in no assembly recipe. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void assemblerRejectsUnknownIngredients(GameTestHelper helper) {
        AssemblerBlockEntity assembler = placeAssembler(helper, new BlockPos(2, Y, 2));

        ItemStack leftover = assembler.getItemHandler().insertItem(
                AssemblerBlockEntity.INPUT_START, new ItemStack(Items.DIRT, 4), false);
        helper.assertTrue(leftover.getCount() == 4,
                "the assembler accepted dirt as an ingredient");

        ItemStack accepted = assembler.getItemHandler().insertItem(
                AssemblerBlockEntity.INPUT_START, new ItemStack(ModItems.COPPER_PLATE.get(), 4),
                false);
        helper.assertTrue(accepted.isEmpty(),
                "the assembler rejected a copper plate: " + accepted);

        ItemStack extracted = assembler.getItemHandler().extractItem(
                AssemblerBlockEntity.INPUT_START, 1, false);
        helper.assertTrue(extracted.isEmpty(),
                "a pipe could pull an ingredient back out of the assembler");
        helper.assertTrue(assembler.getItemHandler().extractItem(
                AssemblerBlockEntity.OUTPUT_SLOT, 1, true).isEmpty(),
                "the empty output slot returned an item");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // reinforced energy buffer (Phase 18 consumer of both parts)
    // ------------------------------------------------------------------

    /** The reinforced buffer stores four times the basic tier and pushes twice as fast. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void reinforcedBufferFeedsANeighbour(GameTestHelper helper) {
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(2, Y, 2),
                ModBlocks.ENERGY_BUFFER_MK2.get().defaultBlockState());
        // The receiver is another reinforced buffer: a machine's receive limit (500 FE/t) would
        // otherwise cap the 512 FE/t push and hide the tier difference.
        EnergyBufferBlockEntity target = placeBuffer(helper, new BlockPos(3, Y, 2),
                ModBlocks.ENERGY_BUFFER_MK2.get().defaultBlockState());
        helper.assertTrue(buffer.getTier() == BufferTier.REINFORCED,
                "the reinforced buffer reported tier " + buffer.getTier());
        helper.assertTrue(buffer.getEnergyStorage().getMaxEnergyStored()
                        == BufferTier.scaled(Config.energyBufferCapacity(),
                        BufferTier.REINFORCED.capacityMultiplier()),
                "the reinforced buffer capacity is "
                        + buffer.getEnergyStorage().getMaxEnergyStored());
        buffer.setEnergyStored(10_000);

        tick(helper, buffer);

        int expected = Math.min(
                BufferTier.scaled(Config.energyBufferPushPerTick(),
                        BufferTier.REINFORCED.pushMultiplier()), 10_000);
        helper.assertTrue(target.getEnergyStoredForTesting() == expected,
                "the target received " + target.getEnergyStoredForTesting()
                        + " FE instead of " + expected);
        helper.assertTrue(buffer.getEnergyStoredForTesting() == 10_000 - expected,
                "the source holds " + buffer.getEnergyStoredForTesting()
                        + " FE instead of " + (10_000 - expected));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void fillComponents(AssemblerBlockEntity assembler) {
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START,
                new ItemStack(ModItems.COPPER_PLATE.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.GOLD_PLATE.get(), 1));
        assembler.getInventory().setStackInSlot(AssemblerBlockEntity.INPUT_START + 2,
                new ItemStack(Items.REDSTONE, 1));
    }

    private static AssemblerBlockEntity placeAssembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ASSEMBLER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof AssemblerBlockEntity assembler)) {
            helper.fail("assembler block entity missing at " + pos);
            return null;
        }
        return assembler;
    }

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos,
                                                       BlockState state) {
        helper.setBlock(pos, state);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof EnergyBufferBlockEntity buffer)) {
            helper.fail("energy buffer block entity missing at " + pos);
            return null;
        }
        return buffer;
    }

    private static void tick(GameTestHelper helper, AssemblerBlockEntity assembler) {
        BlockState state = assembler.getBlockState();
        AssemblerBlockEntity.serverTick(helper.getLevel(), assembler.getBlockPos(), state, assembler);
    }

    private static void tick(GameTestHelper helper, EnergyBufferBlockEntity buffer) {
        BlockState state = buffer.getBlockState();
        EnergyBufferBlockEntity.serverTick(helper.getLevel(), buffer.getBlockPos(), state, buffer);
    }

    private static int count(AssemblerBlockEntity assembler, int slot, Item item) {
        ItemStack stack = assembler.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }
}
