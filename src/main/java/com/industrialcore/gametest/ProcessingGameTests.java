package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.ItemFilterMode;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.ProcessingBalance;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.menu.CrusherMenu;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
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
 * GameTests for the Phase 7 electric crusher and electric furnace.
 *
 * <p>All tests run the real server tick logic, the real recipe manager (vanilla smelting and the
 * mod's {@code industrialcore:crushing} recipes) and the real item capability, and assert exact
 * item totals. That catches item loss and duplication, a progress bar that advances without FE,
 * and a machine that consumes an unsupported item.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessingGameTests {
    private static final int Y = 6;

    private ProcessingGameTests() {}

    // ------------------------------------------------------------------
    // Electric crusher
    // ------------------------------------------------------------------

    /** One iron ore becomes exactly two iron dust and the operating cost is paid. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void crusherProcessesOreIntoDust(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_ORE, 1));
        crusher.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getOperationTime() == 120,
                            "the crushing recipe time is " + crusher.getOperationTime() + " instead of 120");
                    helper.assertTrue(crusher.getStatus() == MachineStatus.WORKING,
                            "the crusher is not working: " + crusher.getStatus());
                })
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                                    ModItems.IRON_DUST.get()) == 2,
                            "expected 2 iron dust but found "
                                    + count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, ModItems.IRON_DUST.get()));
                    helper.assertTrue(crusher.getInventory()
                                    .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                            "the ore was not consumed");
                    helper.assertTrue(crusher.getEnergyStoredForTesting()
                                    == 20_000 - 120 * ProcessingBalance.CRUSHER_ENERGY_PER_TICK,
                            "the crusher spent " + (20_000 - crusher.getEnergyStoredForTesting())
                                    + " FE instead of 1200");
                    helper.assertItemEntityNotPresent(ModItems.IRON_DUST.get());
                })
                .thenSucceed();
    }

    /** An item without a crushing recipe is never consumed and no dust appears. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void crusherDoesNotConsumeUnsupportedItems(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.DIAMOND, 3));
        crusher.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getStatus() == MachineStatus.NO_RECIPE,
                            "expected NO_RECIPE but was " + crusher.getStatus());
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.DIAMOND) == 3,
                            "the unsupported item was consumed: "
                                    + count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.DIAMOND));
                    helper.assertTrue(crusher.getProgress() == 0, "progress advanced without a recipe");
                    helper.assertTrue(crusher.getEnergyStoredForTesting() == 20_000,
                            "an idle crusher consumed FE");
                })
                .thenSucceed();
    }

    /** Without FE the progress stays at zero and the ore is kept; refuelling resumes the work. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void crusherWaitsForEnergyAndResumes(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.COPPER_ORE, 2));
        crusher.setEnergyStored(0);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getStatus() == MachineStatus.NO_ENERGY,
                            "expected NO_ENERGY but was " + crusher.getStatus());
                    helper.assertTrue(crusher.getProgress() == 0,
                            "progress advanced without FE: " + crusher.getProgress());
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.COPPER_ORE) == 2,
                            "ore was consumed without FE");
                    helper.assertTrue(crusher.getInventory()
                                    .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                            "an item appeared without FE");
                })
                .thenExecute(() -> crusher.setEnergyStored(20_000))
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                                    ModItems.COPPER_DUST.get()) == 2,
                            "the crusher did not produce copper dust after refuelling");
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.COPPER_ORE) == 1,
                            "the crusher consumed a second ore while only one should be processed");
                })
                .thenSucceed();
    }

    /** A full output slot freezes the machine and keeps the ore; emptying it resumes the work. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void crusherKeepsInputWhenOutputIsFull(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.GOLD_ORE, 1));
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STONE, 64));
        crusher.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getStatus() == MachineStatus.OUTPUT_FULL,
                            "expected OUTPUT_FULL but was " + crusher.getStatus());
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.GOLD_ORE) == 1,
                            "the ore was lost while the output was full");
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, Items.STONE) == 64,
                            "the blocked machine changed the full output stack");
                })
                .thenExecute(() -> crusher.getInventory()
                        .setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY))
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                                    ModItems.GOLD_DUST.get()) == 2,
                            "the crusher did not resume after the output was emptied");
                    helper.assertTrue(crusher.getInventory()
                                    .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                            "the ore was not consumed after resuming");
                })
                .thenSucceed();
    }

    /** Three ores are processed back to back without waiting for a manual restart. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void crusherProcessesContinuously(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_ORE, 3));
        crusher.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(420)
                .thenExecute(() -> {
                    helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                                    ModItems.IRON_DUST.get()) == 6,
                            "expected 6 dust from 3 ores but found "
                                    + count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, ModItems.IRON_DUST.get()));
                    helper.assertTrue(crusher.getInventory()
                                    .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                            "the crusher did not consume all three ores");
                    helper.assertTrue(crusher.getEnergyStoredForTesting() == 20_000 - 3 * 1_200,
                            "the machine spent " + (20_000 - crusher.getEnergyStoredForTesting()) + " FE");
                })
                .thenSucceed();
    }

    /** Progress, locked input and FE survive a save/load round trip and the work finishes. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void crusherSurvivesSaveAndLoad(GameTestHelper helper) {
        ProcessingBlockEntity original = placeMachine(helper, new BlockPos(2, Y, 3), ProcessingKind.CRUSHER);
        original.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_ORE, 1));
        original.setEnergyStored(20_000);
        for (int tick = 0; tick < 40; tick++) {
            tickMachine(helper, original);
        }
        int savedProgress = original.getProgress();
        int savedEnergy = original.getEnergyStoredForTesting();
        helper.assertTrue(savedProgress > 0 && savedProgress < 120,
                "unexpected progress before saving: " + savedProgress);

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        ProcessingBlockEntity restored = placeMachine(helper, new BlockPos(5, Y, 3), ProcessingKind.CRUSHER);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getProgress() == savedProgress,
                "progress was not restored: " + restored.getProgress() + " instead of " + savedProgress);
        helper.assertTrue(restored.getEnergyStoredForTesting() == savedEnergy,
                "FE was not restored: " + restored.getEnergyStoredForTesting() + " instead of " + savedEnergy);
        helper.assertTrue(count(restored, ProcessingBlockEntity.INPUT_SLOT, Items.IRON_ORE) == 1,
                "the input was not restored");

        for (int tick = 0; tick < 200
                && restored.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(); tick++) {
            tickMachine(helper, restored);
        }
        helper.assertTrue(count(restored, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 2,
                "the restored crusher did not finish its work");
        helper.assertTrue(count(restored, ProcessingBlockEntity.INPUT_SLOT, Items.IRON_ORE) == 0,
                "the restored crusher consumed nothing");
        helper.succeed();
    }

    /** Shift-click moves ore into the input and dust out of the output, without duplication. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void crusherMenuMovesItemsBothWays(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_ORE, 1));
        CrusherMenu menu = new CrusherMenu(0, player.getInventory(), crusher, crusher.getContainerData());

        // 0/1 are the machine slots, 2..28 the main inventory and 29..37 the hotbar.
        menu.quickMoveStack(player, 29);
        helper.assertTrue(count(crusher, ProcessingBlockEntity.INPUT_SLOT, Items.IRON_ORE) == 1,
                "the menu did not move the ore into the input slot");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_ORE) == 0,
                "the player kept the ore after the shift-click");

        crusher.setEnergyStored(20_000);
        for (int tick = 0; tick < 200; tick++) {
            tickMachine(helper, crusher);
        }
        helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 2,
                "the crusher did not produce dust");
        menu.quickMoveStack(player, ProcessingBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(crusher.getInventory()
                        .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the menu did not empty the output slot");
        helper.assertTrue(player.getInventory().countItem(ModItems.IRON_DUST.get()) == 2,
                "the player holds " + player.getInventory().countItem(ModItems.IRON_DUST.get())
                        + " dust instead of 2");
        helper.assertItemEntityNotPresent(ModItems.IRON_DUST.get());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Electric furnace
    // ------------------------------------------------------------------

    /** Vanilla smelting time 200 is halved to 100 ticks and 8 FE/tick are spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void electricFurnaceSmeltsAtDoubleSpeed(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_ORE, 1));
        furnace.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getOperationTime() == 100,
                            "the electric furnace needs " + furnace.getOperationTime()
                                    + " ticks for a 200-tick recipe instead of 100");
                    helper.assertTrue(ProcessingBalance.isFasterThanVanillaFurnace(furnace.getOperationTime()),
                            "the electric furnace is not faster than a vanilla furnace");
                })
                .thenIdle(130)
                .thenExecute(() -> {
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.IRON_INGOT) == 1,
                            "the electric furnace produced "
                                    + count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.IRON_INGOT)
                                    + " iron ingots instead of 1");
                    helper.assertTrue(furnace.getEnergyStoredForTesting()
                                    == 20_000 - 100 * ProcessingBalance.FURNACE_ENERGY_PER_TICK,
                            "the furnace spent " + (20_000 - furnace.getEnergyStoredForTesting())
                                    + " FE instead of 800");
                    helper.assertItemEntityNotPresent(Items.IRON_INGOT);
                })
                .thenSucceed();
    }

    /** Any vanilla smelting recipe works, not just the mod's dusts. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void electricFurnaceReusesVanillaRecipes(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.COBBLESTONE, 2));
        furnace.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(260)
                .thenExecute(() -> {
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.STONE) == 2,
                            "cobblestone was not smelted into stone: "
                                    + count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.STONE));
                    helper.assertTrue(furnace.getInventory()
                                    .getStackInSlot(ProcessingBlockEntity.INPUT_SLOT).isEmpty(),
                            "the furnace did not consume the cobblestone");
                    helper.assertTrue(furnace.getEnergyStoredForTesting() == 20_000 - 2 * 800,
                            "two smelts cost " + (20_000 - furnace.getEnergyStoredForTesting()) + " FE");
                })
                .thenSucceed();
    }

    /** The crusher's dust is accepted by the furnace and smelted into an ingot. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void electricFurnaceSmeltsCrusherDust(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.COPPER_DUST.get(), 2));
        furnace.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(260)
                .thenExecute(() -> {
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.COPPER_INGOT) == 2,
                            "the furnace did not smelt the crusher dust: "
                                    + count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.COPPER_INGOT));
                })
                .thenSucceed();
    }

    /** An unsupported item is kept, no progress is made and no FE is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void electricFurnaceDoesNotConsumeUnsupportedItems(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.DIAMOND, 2));
        furnace.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getStatus() == MachineStatus.NO_RECIPE,
                            "expected NO_RECIPE but was " + furnace.getStatus());
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.INPUT_SLOT, Items.DIAMOND) == 2,
                            "the unsupported item was consumed");
                    helper.assertTrue(furnace.getProgress() == 0, "progress advanced without a recipe");
                    helper.assertTrue(furnace.getEnergyStoredForTesting() == 20_000,
                            "an idle furnace consumed FE");
                })
                .thenSucceed();
    }

    /** No FE: no progress, no loss. Refuelling finishes the smelt. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void electricFurnaceWaitsForEnergyAndResumes(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.SAND, 1));
        furnace.setEnergyStored(0);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getStatus() == MachineStatus.NO_ENERGY,
                            "expected NO_ENERGY but was " + furnace.getStatus());
                    helper.assertTrue(furnace.getProgress() == 0, "progress advanced without FE");
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.INPUT_SLOT, Items.SAND) == 1,
                            "the input was consumed without FE");
                })
                .thenExecute(() -> furnace.setEnergyStored(20_000))
                .thenIdle(130)
                .thenExecute(() -> helper.assertTrue(
                        count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.GLASS) == 1,
                        "the furnace did not resume after refuelling"))
                .thenSucceed();
    }

    /** Full output: the input and the finished work are kept until there is room. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void electricFurnaceKeepsInputWhenOutputIsFull(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 2),
                ProcessingKind.ELECTRIC_FURNACE);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.SAND, 1));
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STONE, 64));
        furnace.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getStatus() == MachineStatus.OUTPUT_FULL,
                            "expected OUTPUT_FULL but was " + furnace.getStatus());
                    helper.assertTrue(count(furnace, ProcessingBlockEntity.INPUT_SLOT, Items.SAND) == 1,
                            "the input was lost while the output was full");
                })
                .thenExecute(() -> furnace.getInventory()
                        .setStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY))
                // The blocked machine had no progress yet (it refuses to start into a full
                // output), so the full 100-tick smelt has to run after the output was cleared.
                .thenIdle(130)
                .thenExecute(() -> helper.assertTrue(
                        count(furnace, ProcessingBlockEntity.OUTPUT_SLOT, Items.GLASS) == 1,
                        "the furnace did not finish after the output was emptied (status="
                                + furnace.getStatus() + ")"))
                .thenSucceed();
    }

    /** Inventory, progress and FE survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void electricFurnaceSurvivesSaveAndLoad(GameTestHelper helper) {
        ProcessingBlockEntity original = placeMachine(helper, new BlockPos(2, Y, 3),
                ProcessingKind.ELECTRIC_FURNACE);
        original.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.GOLD_DUST.get(), 1));
        original.setEnergyStored(15_000);
        for (int tick = 0; tick < 50; tick++) {
            tickMachine(helper, original);
        }
        int savedProgress = original.getProgress();

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        ProcessingBlockEntity restored = placeMachine(helper, new BlockPos(5, Y, 3),
                ProcessingKind.ELECTRIC_FURNACE);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "progress was not restored: " + restored.getProgress() + " instead of " + savedProgress);
        helper.assertTrue(restored.getEnergyStoredForTesting() == original.getEnergyStoredForTesting(),
                "FE was not restored");
        helper.assertTrue(restored.canAccept(
                        restored.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT),
                        new ItemStack(Items.GOLD_INGOT, 1)),
                "the restored furnace cannot accept its result");

        for (int tick = 0; tick < 200
                && restored.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(); tick++) {
            tickMachine(helper, restored);
        }
        helper.assertTrue(count(restored, ProcessingBlockEntity.OUTPUT_SLOT, Items.GOLD_INGOT) == 1,
                "the restored furnace did not finish its smelt");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Production lines
    // ------------------------------------------------------------------

    /** Chest -> pipe -> crusher -> pipe -> furnace -> pipe -> chest, with energy for both machines. */
    @GameTest(template = "quarry_test", timeoutTicks = 1200)
    public static void pipelineCrushesAndSmelts(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity feed = placePipe(helper, new BlockPos(2, Y, 1));
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(3, Y, 1), ProcessingKind.CRUSHER);
        ItemPipeBlockEntity transfer = placePipe(helper, new BlockPos(3, Y, 2));
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(3, Y, 3),
                ProcessingKind.ELECTRIC_FURNACE);
        ItemPipeBlockEntity output = placePipe(helper, new BlockPos(4, Y, 3));
        Container sink = placeChest(helper, new BlockPos(5, Y, 3));
        feed.setMode(Direction.WEST, PipeConnectionMode.PULL);
        feed.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        transfer.setMode(Direction.NORTH, PipeConnectionMode.PULL);
        transfer.setMode(Direction.SOUTH, PipeConnectionMode.PUSH);
        output.setMode(Direction.WEST, PipeConnectionMode.PULL);
        output.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        source.setItem(0, new ItemStack(Items.IRON_ORE, 1));
        crusher.setEnergyStored(20_000);
        furnace.setEnergyStored(20_000);

        for (int tick = 0; tick < 900 && count(sink, Items.IRON_INGOT) < 2; tick++) {
            tickPipe(helper, feed);
            tickMachine(helper, crusher);
            tickPipe(helper, transfer);
            tickMachine(helper, furnace);
            tickPipe(helper, output);
        }
        IndustrialCore.LOGGER.info("[perf] pipeline sink ingots={} crusher={} furnace={}",
                count(sink, Items.IRON_INGOT), crusher.getStatus(), furnace.getStatus());
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 2,
                "the pipeline delivered " + count(sink, Items.IRON_INGOT) + " of 2 ingots");
        helper.assertTrue(count(source, Items.IRON_ORE) == 0, "the source still holds the ore");
        helper.assertTrue(feed.getBufferCount() == 0 && transfer.getBufferCount() == 0
                        && output.getBufferCount() == 0,
                "a pipe still holds an item");
        helper.assertTrue(crusher.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the crusher output was not emptied");
        helper.assertTrue(furnace.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the furnace output was not emptied");
        helper.assertItemEntityNotPresent(Items.IRON_ORE);
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.succeed();
    }

    /** Electric furnace -> pipe -> auto crafter: a smelted log becomes charcoal and then torches. */
    @GameTest(template = "quarry_test", timeoutTicks = 1200)
    public static void pipelineFeedsAutoCrafter(GameTestHelper helper) {
        ProcessingBlockEntity furnace = placeMachine(helper, new BlockPos(2, Y, 1),
                ProcessingKind.ELECTRIC_FURNACE);
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, Y, 1));
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(4, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        furnace.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.OAK_LOG, 1));
        furnace.setEnergyStored(20_000);
        // The torch pattern is a vertical 1x2 recipe: coal in grid cell 0, stick in cell 3.
        // Phase 81 made the shape authoritative, so the stick has to sit in its own cell.
        crafter.getInventory().setStackInSlot(3, new ItemStack(Items.STICK, 1));
        crafter.setSelectedRecipe(ResourceLocation.withDefaultNamespace("torch"));
        crafter.setEnergyStored(20_000);
        helper.assertTrue("minecraft:torch".equals(crafter.getSelectedRecipeId()),
                "the torch recipe could not be selected: " + crafter.getSelectedRecipeId());

        for (int tick = 0; tick < 900 && crafter.getInventory()
                .getStackInSlot(CrafterBlockEntity.OUTPUT_SLOT).isEmpty(); tick++) {
            tickMachine(helper, furnace);
            tickPipe(helper, pipe);
            CrafterBlockEntity.serverTick(helper.getLevel(), crafter.getBlockPos(),
                    crafter.getBlockState(), crafter);
        }
        helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.TORCH) == 4,
                "the crafter produced " + count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.TORCH)
                        + " torches instead of 4");
        helper.assertTrue(furnace.getInventory().getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the furnace output was not collected by the pipe");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds an item");
        helper.assertTrue(count(crafter, 3, Items.STICK) == 0, "the stick was not consumed");
        helper.succeed();
    }

    /** Generator -> cable -> crusher: the machine really runs on transmitted FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void generatorPowersCrusherThroughCable(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(1, Y, 1);
        helper.setBlock(generatorPos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                .setValue(GenerationBlock.FACING, Direction.EAST));
        BlockEntity generatorEntity = helper.getLevel().getBlockEntity(helper.absolutePos(generatorPos));
        if (!(generatorEntity instanceof GenerationBlockEntity generator)) {
            helper.fail("generator block entity missing");
            return;
        }
        CableBlockEntity cable = placeCable(helper, new BlockPos(2, Y, 1));
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(3, Y, 1), ProcessingKind.CRUSHER);
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_ORE, 1));
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 2));

        for (int tick = 0; tick < 700 && crusher.getInventory()
                .getStackInSlot(ProcessingBlockEntity.OUTPUT_SLOT).isEmpty(); tick++) {
            GenerationBlockEntity.serverTick(helper.getLevel(), generator.getBlockPos(),
                    generator.getBlockState(), generator);
            CableBlockEntity.serverTick(helper.getLevel(), cable.getBlockPos(), cable.getBlockState(), cable);
            tickMachine(helper, crusher);
        }
        helper.assertTrue(generator.getBurnTime() > 0, "the generator did not burn fuel");
        helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 2,
                "the cable network did not power the crusher (FE="
                        + crusher.getEnergyStoredForTesting() + ", cable="
                        + cable.getEnergyStoredForTesting() + ")");
        helper.succeed();
    }    /**
     * Regression: both machines must interpret the same GUI action ids the same way, so the
     * processing machine's "mode" button cycles the mode and not the item filter.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void guiActionsChangeOnlyTheirOwnSetting(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeMachine(helper, new BlockPos(2, Y, 2), ProcessingKind.CRUSHER);
        int up = Direction.UP.get3DDataValue();
        MachineFaceConfig config = crusher.getFaceConfig();

        helper.assertTrue(crusher.applyFaceAction(up, MachineActions.FACE_MODE, ItemStack.EMPTY),
                "the mode action was rejected");
        helper.assertTrue(config.mode(up) == FaceMode.INPUT,
                "the mode button produced " + config.mode(up));
        helper.assertTrue(config.filterMode(up) == ItemFilterMode.OFF,
                "the mode button changed the filter to " + config.filterMode(up));

        helper.assertTrue(crusher.applyFaceAction(up, MachineActions.FACE_FILTER, ItemStack.EMPTY),
                "the filter action was rejected");
        helper.assertTrue(config.filterMode(up) == ItemFilterMode.ALLOW,
                "the filter button produced " + config.filterMode(up));
        helper.assertTrue(config.mode(up) == FaceMode.INPUT,
                "the filter button changed the mode to " + config.mode(up));

        helper.assertTrue(crusher.applyFaceAction(up, MachineActions.FACE_SLOT, ItemStack.EMPTY),
                "the slot action was rejected");
        helper.assertTrue(config.slot(up) == ProcessingBlockEntity.INPUT_SLOT,
                "the slot button skipped the input slot: " + config.slot(up));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ProcessingBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, ProcessingKind kind) {
        helper.setBlock(pos, (kind == ProcessingKind.CRUSHER
                ? ModBlocks.CRUSHER.get()
                : ModBlocks.ELECTRIC_FURNACE.get()).defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail(kind + " block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static CableBlockEntity placeCable(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CABLE_BASIC.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof CableBlockEntity cable)) {
            helper.fail("cable block entity missing at " + pos);
            return null;
        }
        return cable;
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

    private static CrafterBlockEntity placeCrafter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRAFTER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof CrafterBlockEntity crafter)) {
            helper.fail("auto crafter block entity missing at " + pos);
            return null;
        }
        return crafter;
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

    private static void tickMachine(GameTestHelper helper, ProcessingBlockEntity machine) {
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }

    private static int count(ProcessingBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(CrafterBlockEntity crafter, int slot, Item item) {
        ItemStack stack = crafter.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(Container container, Item item) {
        return container == null ? 0 : container.countItem(item);
    }
}
