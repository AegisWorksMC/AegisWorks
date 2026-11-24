package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.QuantumCompressorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ProcessingBalance;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.QuantumCompressorBalance;
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
 * GameTests of the Phase 25 quantum compressor.
 *
 * <p>Every test drives the real server tick, the real recipe manager and the shipped
 * {@code industrialcore:crushing} recipe (1 raw iron -> 2 iron dust, 10 ticks x 10 FE/t = 100 FE
 * per item):</p>
 *
 * <pre>
 *   batch  1 ->  2 dust in 240 ticks,    100 FE
 *   batch  8 -> 16 dust in 240 ticks,    800 FE
 *   batch 32 -> 64 dust in 240 ticks,  3,200 FE
 *   batch 64 -> 128 dust in 240 ticks,  6,400 FE
 * </pre>
 *
 * <p>The FE per item never changes, whatever the batch is; that is the property these tests pin
 * down, together with "no FE and no items are spent when the cycle cannot complete".</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuantumCompressorGameTests {
    private static final int Y = 6;
    /** Enough for two full batches of the shipped recipe. */
    private static final int START_ENERGY = 180_000;
    /** One item of the shipped crushing recipe (10 ticks x 10 FE/t). */
    private static final int ITEM_COST = 100;
    private static final int CYCLE = QuantumCompressorBalance.CYCLE_TICKS;

    private QuantumCompressorGameTests() {}

    // ------------------------------------------------------------------
    // 1. Batch sizes and cost
    // ------------------------------------------------------------------

    /** One item keeps the crusher's own cost, spread over the fixed 240 tick cycle. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void oneItemCostsWhatTheCrusherCosts(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < CYCLE - 1; tick++) {
            tickCompressor(helper, compressor);
        }
        helper.assertTrue(compressor.getProgress() == CYCLE - 1,
                "the cycle stopped at " + compressor.getProgress() + " of " + CYCLE);
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 0,
                "the compressor produced its result before the cycle was finished");

        tickCompressor(helper, compressor);
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 2,
                "one raw iron produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(compressor.getInventory()
                        .getStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT).isEmpty(),
                "the compressor did not consume the raw iron");
        helper.assertTrue(START_ENERGY - compressor.getEnergyStoredForTesting() == ITEM_COST,
                "the item cost " + (START_ENERGY - compressor.getEnergyStoredForTesting())
                        + " FE instead of " + ITEM_COST);
        helper.assertItemEntityNotPresent(ModItems.IRON_DUST.get());
        helper.succeed();
    }

    /** A full 64 batch produces 128 dust for exactly 64 times the per-item cost. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void aFullBatchCrushesSixtyFourItems(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 64));
        compressor.setEnergyStored(START_ENERGY);

        tickCompressor(helper, compressor);
        helper.assertTrue(compressor.getBatch() == 64,
                "the cycle locked a batch of " + compressor.getBatch() + " instead of 64");

        for (int tick = 0; tick < CYCLE - 1; tick++) {
            tickCompressor(helper, compressor);
        }
        tickCompressor(helper, compressor);

        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 128,
                "64 raw iron produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(compressor.getInventory()
                        .getStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT).isEmpty(),
                "the compressor did not consume the whole batch");
        int spent = START_ENERGY - compressor.getEnergyStoredForTesting();
        helper.assertTrue(spent == 64 * ITEM_COST,
                "the batch cost " + spent + " FE instead of " + 64 * ITEM_COST);
        // 128 dust cannot fit into one slot, so the result must be spread over several.
        int slots = 0;
        for (int index = 0; index < QuantumCompressorBlockEntity.OUTPUT_COUNT; index++) {
            if (!compressor.getInventory()
                    .getStackInSlot(QuantumCompressorBlockEntity.OUTPUT_START + index).isEmpty()) {
                slots++;
            }
        }
        helper.assertTrue(slots >= 2, "the batch result used only " + slots + " output slot(s)");
        helper.succeed();
    }

    /** Eight items cost eight times one item and take the same 240 ticks. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void eightItemsTakeOneCycle(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < CYCLE; tick++) {
            tickCompressor(helper, compressor);
        }

        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 16,
                "8 raw iron produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        int spent = START_ENERGY - compressor.getEnergyStoredForTesting();
        helper.assertTrue(spent == 8 * ITEM_COST,
                "the 8 batch cost " + spent + " FE instead of " + 8 * ITEM_COST);
        helper.succeed();
    }

    /** With room for only one free output slot the batch shrinks instead of blocking. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void aHalfFreeOutputHalvesTheBatch(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        // Three output slots hold a different item, so only one slot (64 dust = 32 items) is free.
        for (int index = 0; index < 3; index++) {
            compressor.getInventory().setStackInSlot(
                    QuantumCompressorBlockEntity.OUTPUT_START + index,
                    new ItemStack(Items.STONE, 64));
        }
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 64));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < CYCLE; tick++) {
            tickCompressor(helper, compressor);
        }

        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 64,
                "the limited output produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(remaining(compressor, Items.RAW_IRON) == 32,
                "the compressor consumed "
                        + (64 - remaining(compressor, Items.RAW_IRON)) + " items instead of 32");
        int spent = START_ENERGY - compressor.getEnergyStoredForTesting();
        helper.assertTrue(spent == 32 * ITEM_COST,
                "the half batch cost " + spent + " FE instead of " + 32 * ITEM_COST);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. Blocked states never spend
    // ------------------------------------------------------------------

    /** A full output stops the machine before any item or FE is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aFullOutputSpendsNothing(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        for (int index = 0; index < QuantumCompressorBlockEntity.OUTPUT_COUNT; index++) {
            compressor.getInventory().setStackInSlot(
                    QuantumCompressorBlockEntity.OUTPUT_START + index,
                    new ItemStack(ModItems.IRON_DUST.get(), 64));
        }
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 200; tick++) {
            tickCompressor(helper, compressor);
        }

        helper.assertTrue(compressor.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + compressor.getStatus());
        helper.assertTrue(compressor.getProgress() == 0,
                "the blocked compressor advanced " + compressor.getProgress() + " ticks");
        helper.assertTrue(compressor.getEnergyStoredForTesting() == START_ENERGY,
                "the blocked compressor spent FE");
        helper.assertTrue(remaining(compressor, Items.RAW_IRON) == 8,
                "the blocked compressor ate the input");
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 4 * 64,
                "the full output changed");

        // Emptying one slot frees 64 dust of room, so the 8 waiting items can run on their own.
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.OUTPUT_START,
                ItemStack.EMPTY);
        for (int tick = 0; tick < CYCLE + 5; tick++) {
            tickCompressor(helper, compressor);
        }
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 3 * 64 + 16,
                "the compressor resumed with " + count(compressor, ModItems.IRON_DUST.get())
                        + " dust instead of " + (3 * 64 + 16));
        helper.assertTrue(remaining(compressor, Items.RAW_IRON) == 0,
                "the resumed batch left " + remaining(compressor, Items.RAW_IRON) + " items");
        helper.succeed();
    }

    /** A power cut freezes the progress and the cycle resumes where it stopped. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void aPowerCutKeepsTheProgress(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        compressor.setEnergyStored(6_000);

        for (int tick = 0; tick < 100; tick++) {
            tickCompressor(helper, compressor);
        }
        int progressBefore = compressor.getProgress();
        int storedBefore = compressor.getEnergyStoredForTesting();
        helper.assertTrue(progressBefore > 0, "the compressor never started");

        // Drain the buffer: the next tick cannot be paid, so nothing may happen.
        compressor.setEnergyStored(0);
        for (int tick = 0; tick < 20; tick++) {
            tickCompressor(helper, compressor);
        }
        helper.assertTrue(compressor.getStatus() == MachineStatus.NO_ENERGY,
                "expected NO_ENERGY but was " + compressor.getStatus());
        helper.assertTrue(compressor.getProgress() == progressBefore,
                "the progress moved from " + progressBefore + " to " + compressor.getProgress()
                        + " without energy");
        helper.assertTrue(remaining(compressor, Items.RAW_IRON) == 8,
                "the compressor ate the input while it had no power");
        helper.assertTrue(compressor.getEnergyStoredForTesting() == 0,
                "FE appeared out of nowhere");

        // Refill with exactly the remainder of the cycle and let it finish.
        int paid = 6_000 - storedBefore;
        int remainingCost = 8 * ITEM_COST - paid;
        compressor.setEnergyStored(remainingCost);
        for (int tick = 0; tick < CYCLE; tick++) {
            tickCompressor(helper, compressor);
        }
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 16,
                "the resumed cycle produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(compressor.getEnergyStoredForTesting() == 0,
                "the cycle spent "
                        + (remainingCost - compressor.getEnergyStoredForTesting())
                        + " FE instead of " + remainingCost);
        helper.succeed();
    }

    /** Removing the input mid-cycle aborts the plan instead of paying out a result. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void takingTheInputAwayAbortsTheCycle(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 100; tick++) {
            tickCompressor(helper, compressor);
        }
        int storedMidCycle = compressor.getEnergyStoredForTesting();
        // A pipe or a player takes the batch away (and a machine that needs the items cannot).
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                ItemStack.EMPTY);
        for (int tick = 0; tick < 200; tick++) {
            tickCompressor(helper, compressor);
        }

        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 0,
                "the aborted cycle still produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(compressor.getEnergyStoredForTesting() == storedMidCycle,
                "the aborted cycle kept spending FE");
        helper.assertTrue(compressor.getBatch() == 0,
                "the aborted cycle kept its batch of " + compressor.getBatch());
        helper.succeed();
    }

    /** An item without a crushing recipe never starts a cycle. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void anItemWithoutARecipeDoesNothing(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.STONE, 8));
        compressor.setEnergyStored(START_ENERGY);

        for (int tick = 0; tick < 100; tick++) {
            tickCompressor(helper, compressor);
        }

        helper.assertTrue(compressor.getStatus() == MachineStatus.NO_RECIPE,
                "expected NO_RECIPE but was " + compressor.getStatus());
        helper.assertTrue(compressor.getEnergyStoredForTesting() == START_ENERGY,
                "the compressor spent FE on an item without a recipe");
        helper.assertTrue(remaining(compressor, Items.STONE) == 8,
                "the compressor consumed the item without a recipe");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. Persistence and automation
    // ------------------------------------------------------------------

    /** The batch, the progress and the energy survive a save and load. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void theCycleSurvivesSaveAndLoad(GameTestHelper helper) {
        QuantumCompressorBlockEntity original = place(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8));
        original.setEnergyStored(START_ENERGY);
        for (int tick = 0; tick < 100; tick++) {
            tickCompressor(helper, original);
        }
        int savedProgress = original.getProgress();
        int savedBatch = original.getBatch();
        int savedEnergy = original.getEnergyStoredForTesting();
        helper.assertTrue(savedProgress > 0 && savedBatch == 8,
                "the cycle did not lock a batch of 8");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        QuantumCompressorBlockEntity restored = place(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getBatch() == savedBatch,
                "the batch was restored as " + restored.getBatch()
                        + " instead of " + savedBatch);
        helper.assertTrue(restored.getEnergyStoredForTesting() == savedEnergy,
                "the energy was restored as " + restored.getEnergyStoredForTesting()
                        + " instead of " + savedEnergy);

        for (int tick = 0; tick < CYCLE && count(restored, ModItems.IRON_DUST.get()) == 0; tick++) {
            tickCompressor(helper, restored);
        }
        helper.assertTrue(count(restored, ModItems.IRON_DUST.get()) == 16,
                "the restored cycle produced " + count(restored, ModItems.IRON_DUST.get()) + " dust");
        helper.assertTrue(START_ENERGY - restored.getEnergyStoredForTesting() == 8 * ITEM_COST,
                "the restored cycle spent " + (START_ENERGY - restored
                        .getEnergyStoredForTesting()) + " FE instead of " + 8 * ITEM_COST);
        helper.succeed();
    }

    /** A pipe may only push into the input and only pull out of the four outputs. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void automationOnlyTouchesTheAllowedSlots(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        var handler = compressor.getItemHandler();

        ItemStack leftover = handler.insertItem(QuantumCompressorBlockEntity.OUTPUT_START,
                new ItemStack(Items.RAW_IRON, 8), false);
        helper.assertTrue(leftover.getCount() == 8,
                "a pipe could insert into an output slot");
        helper.assertTrue(handler.extractItem(QuantumCompressorBlockEntity.INPUT_SLOT, 8,
                false).isEmpty(), "a pipe could pull the ingredients back out");

        ItemStack accepted = handler.insertItem(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 8), false);
        helper.assertTrue(accepted.isEmpty(),
                "the input slot refused " + accepted.getCount() + " items");

        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.OUTPUT_START,
                new ItemStack(ModItems.IRON_DUST.get(), 12));
        ItemStack pulled = handler.extractItem(QuantumCompressorBlockEntity.OUTPUT_START, 5, false);
        helper.assertTrue(pulled.getCount() == 5 && pulled.is(ModItems.IRON_DUST.get()),
                "a pipe could not pull the result out");
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 7,
                "the extraction left " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.succeed();
    }

    /** The outputs are read-only for players: a manual fill is rejected. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void thePlayerCannotFillAnOutputSlot(GameTestHelper helper) {
        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(2, Y, 2));
        helper.assertTrue(compressor.getInventory().isItemValid(
                        QuantumCompressorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON)),
                "the input slot rejected an ingredient");
        for (int index = 0; index < QuantumCompressorBlockEntity.OUTPUT_COUNT; index++) {
            helper.assertFalse(compressor.getInventory().isItemValid(
                            QuantumCompressorBlockEntity.OUTPUT_START + index,
                            new ItemStack(Items.RAW_IRON)),
                    "output slot " + index + " accepts items from the outside");
        }
        helper.succeed();
    }

    /**
     * The same item costs the same FE in the normal crusher: the compressor must not double-charge
     * (the crusher pays 10 ticks x 10 FE/t, the compressor 100 FE over its own 240 ticks).
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void theCrusherAndTheCompressorCostTheSamePerItem(GameTestHelper helper) {
        ProcessingBlockEntity crusher = placeCrusher(helper, new BlockPos(2, Y, 2));
        crusher.getInventory().setStackInSlot(ProcessingBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        // The crusher's buffer is smaller than START_ENERGY, and setEnergyStored clamps, so fill it
        // to its own capacity and subtract from that value.
        int crusherStart = crusher.getEnergyStorage().getMaxEnergyStored();
        crusher.setEnergyStored(crusherStart);
        for (int tick = 0; tick < ProcessingBalance.CRUSHER_TIME_TICKS + 5; tick++) {
            ProcessingBlockEntity.serverTick(helper.getLevel(), crusher.getBlockPos(),
                    crusher.getBlockState(), crusher);
        }
        int crusherSpent = crusherStart - crusher.getEnergyStoredForTesting();
        helper.assertTrue(crusherSpent == ITEM_COST,
                "the crusher spent " + crusherSpent + " FE for one item");

        QuantumCompressorBlockEntity compressor = place(helper, new BlockPos(5, Y, 2));
        compressor.getInventory().setStackInSlot(QuantumCompressorBlockEntity.INPUT_SLOT,
                new ItemStack(Items.RAW_IRON, 1));
        compressor.setEnergyStored(START_ENERGY);
        for (int tick = 0; tick < CYCLE; tick++) {
            tickCompressor(helper, compressor);
        }
        int compressorSpent = START_ENERGY - compressor.getEnergyStoredForTesting();
        helper.assertTrue(compressorSpent == crusherSpent,
                "the compressor spent " + compressorSpent + " FE but the crusher spent "
                        + crusherSpent);
        helper.assertTrue(count(compressor, ModItems.IRON_DUST.get()) == 2,
                "the compressor produced " + count(compressor, ModItems.IRON_DUST.get()) + " dust");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static QuantumCompressorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.QUANTUM_COMPRESSOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof QuantumCompressorBlockEntity compressor)) {
            helper.fail("quantum compressor block entity missing at " + pos);
            return null;
        }
        return compressor;
    }

    private static ProcessingBlockEntity placeCrusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRUSHER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity crusher)
                || crusher.getKind() != ProcessingKind.CRUSHER) {
            helper.fail("crusher block entity missing at " + pos);
            return null;
        }
        return crusher;
    }

    private static void tickCompressor(GameTestHelper helper, QuantumCompressorBlockEntity machine) {
        BlockState state = machine.getBlockState();
        QuantumCompressorBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state,
                machine);
    }

    /** Items of the given type over all output slots. */
    private static int count(QuantumCompressorBlockEntity machine, Item item) {
        int total = 0;
        for (int index = 0; index < QuantumCompressorBlockEntity.OUTPUT_COUNT; index++) {
            ItemStack stack = machine.getInventory()
                    .getStackInSlot(QuantumCompressorBlockEntity.OUTPUT_START + index);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int remaining(QuantumCompressorBlockEntity machine, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(
                QuantumCompressorBlockEntity.INPUT_SLOT);
        return stack.is(item) ? stack.getCount() : 0;
    }
}
