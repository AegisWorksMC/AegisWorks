package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.registry.ModBlocks;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * GameTests of the Phase 19 item distributor.
 *
 * <p>Every test drives the real server tick, the real block capabilities and real chests:</p>
 * <pre>
 *   chest / pipe --push--> distributor --round robin--> chest, chest, auto crafter
 * </pre>
 *
 * <p>The cases cover the contract of the phase: two and three outputs, a full output, a removed
 * output, a save and load, and the guarantee that no item is created or voided.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DistributorGameTests {
    private static final int Y = 6;

    private DistributorGameTests() {}

    /** With two outputs the single items alternate: west, east, west, east. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void twoOutputsAlternate(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container west = placeChest(helper, new BlockPos(1, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));

        for (int item = 0; item < 4; item++) {
            ItemStack leftover = distributor.getItemHandler(Direction.UP)
                    .insertItem(ItemDistributorBlockEntity.BUFFER_SLOT,
                            new ItemStack(Items.IRON_INGOT, 1), false);
            helper.assertTrue(leftover.isEmpty(),
                    "the distributor refused an item on pass " + item);
            tick(helper, distributor);
            helper.assertTrue(distributor.getBufferCount() == 0,
                    "the distributor kept an item on pass " + item
                            + " (status " + distributor.getStatus() + ")");
        }

        helper.assertTrue(count(west, Items.IRON_INGOT) == 2,
                "the west chest holds " + count(west, Items.IRON_INGOT) + " instead of 2");
        helper.assertTrue(count(east, Items.IRON_INGOT) == 2,
                "the east chest holds " + count(east, Items.IRON_INGOT) + " instead of 2");
        // One idle tick reports the empty buffer.
        tick(helper, distributor);
        helper.assertTrue(distributor.getStatus() == MachineStatus.NO_INPUT,
                "an empty distributor reports " + distributor.getStatus());
        helper.succeed();
    }

    /** With three outputs a stack is spread over all of them in one pass. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void threeOutputsAreFilledInTurn(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container west = placeChest(helper, new BlockPos(1, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));
        Container south = placeChest(helper, new BlockPos(2, Y, 3));

        insert(distributor, new ItemStack(Items.COPPER_INGOT, 6), helper);
        tick(helper, distributor);

        helper.assertTrue(count(west, Items.COPPER_INGOT) == 2
                        && count(east, Items.COPPER_INGOT) == 2
                        && count(south, Items.COPPER_INGOT) == 2,
                "the stack was not spread evenly: west=" + count(west, Items.COPPER_INGOT)
                        + " east=" + count(east, Items.COPPER_INGOT)
                        + " south=" + count(south, Items.COPPER_INGOT));
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor still holds " + distributor.getBufferCount() + " items");
        helper.succeed();
    }

    /** A full output is skipped and the next one receives the whole stack. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void aFullOutputIsSkipped(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container west = placeChest(helper, new BlockPos(1, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));
        fillCompletely(west);

        insert(distributor, new ItemStack(Items.IRON_INGOT, 4), helper);
        tick(helper, distributor);
        tick(helper, distributor);

        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor kept items although the east chest was empty");
        helper.assertTrue(count(east, Items.IRON_INGOT) == 4,
                "the east chest holds " + count(east, Items.IRON_INGOT) + " instead of 4");
        helper.assertTrue(distributor.getStatus() == MachineStatus.NO_INPUT,
                "the distributor reports " + distributor.getStatus());
        helper.succeed();
    }

    /** When every output is full the items stay inside: nothing is voided or dropped. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void allOutputsFullKeepsTheItemsInside(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container west = placeChest(helper, new BlockPos(1, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));
        fillCompletely(west);
        fillCompletely(east);

        insert(distributor, new ItemStack(Items.IRON_INGOT, 8), helper);
        for (int tick = 0; tick < 10; tick++) {
            tick(helper, distributor);
        }

        helper.assertTrue(distributor.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + distributor.getStatus());
        helper.assertTrue(distributor.getBufferCount() == 8,
                "the buffer holds " + distributor.getBufferCount() + " instead of 8");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);

        // Free one slot: the buffered items must leave without a new insertion.
        west.setItem(0, ItemStack.EMPTY);
        for (int tick = 0; tick < 10; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor did not resume after a slot was freed");
        helper.assertTrue(count(west, Items.IRON_INGOT) == 8,
                "the freed chest holds " + count(west, Items.IRON_INGOT) + " instead of 8");
        helper.succeed();
    }

    /** Removing an output keeps the items; placing it again resumes the delivery. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void aRemovedOutputIsRecovered(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));
        insert(distributor, new ItemStack(Items.GOLD_INGOT, 3), helper);
        tick(helper, distributor);
        helper.assertTrue(count(east, Items.GOLD_INGOT) == 3,
                "the first delivery failed");

        // Break the chest: the cached capability must not keep writing into a removed block.
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.AIR);
        insert(distributor, new ItemStack(Items.GOLD_INGOT, 5), helper);
        for (int tick = 0; tick < 5; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 5,
                "the distributor lost " + (5 - distributor.getBufferCount())
                        + " items into the removed chest");
        helper.assertTrue(distributor.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + distributor.getStatus());
        // Breaking a chest drops its contents (vanilla `preRemoveSideEffects`), so the three
        // delivered ingots lie on the ground here; the five new ones must still be in the buffer.
        helper.assertItemEntityPresent(Items.GOLD_INGOT, new BlockPos(3, Y, 2), 2.0D);

        Container restored = placeChest(helper, new BlockPos(3, Y, 2));
        for (int tick = 0; tick < 5; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor did not resume after the chest was placed again");
        helper.assertTrue(count(restored, Items.GOLD_INGOT) == 5,
                "the new chest holds " + count(restored, Items.GOLD_INGOT) + " instead of 5");
        helper.succeed();
    }

    /** Each face keeps its own rule: NONE blocks everything, INPUT only receives, OUTPUT only sends. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void facesRespectTheirMode(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));
        MachineFaceConfig config = distributor.getFaceConfig();
        int eastSide = Direction.EAST.get3DDataValue();
        IItemHandler eastFace = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                distributor.getBlockPos(), Direction.EAST);
        helper.assertTrue(eastFace != null, "the east capability is missing");

        // NONE: neither an input nor an output.
        config.setMode(eastSide, FaceMode.NONE);
        ItemStack refused = eastFace.insertItem(ItemDistributorBlockEntity.BUFFER_SLOT,
                new ItemStack(Items.IRON_INGOT, 2), false);
        helper.assertTrue(refused.getCount() == 2, "a NONE face accepted items");
        insert(distributor, new ItemStack(Items.IRON_INGOT, 2), helper);
        for (int tick = 0; tick < 8; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 2,
                "a NONE face was used as an output");
        helper.assertTrue(count(east, Items.IRON_INGOT) == 0,
                "a NONE face delivered items");

        // OUTPUT: pushes out, but never receives from automation.
        config.setMode(eastSide, FaceMode.OUTPUT);
        ItemStack notAccepted = eastFace.insertItem(ItemDistributorBlockEntity.BUFFER_SLOT,
                new ItemStack(Items.GOLD_INGOT, 1), false);
        helper.assertTrue(notAccepted.getCount() == 1,
                "an OUTPUT face accepted items from automation");
        for (int tick = 0; tick < 8; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(count(east, Items.IRON_INGOT) == 2,
                "an OUTPUT face did not deliver the buffered items");
        helper.assertTrue(distributor.getBufferCount() == 0, "the buffer was not emptied");

        // INPUT: receives, but is not an output.
        config.setMode(eastSide, FaceMode.INPUT);
        ItemStack accepted = eastFace.insertItem(ItemDistributorBlockEntity.BUFFER_SLOT,
                new ItemStack(Items.GOLD_INGOT, 1), false);
        helper.assertTrue(accepted.isEmpty(), "an INPUT face refused items");
        for (int tick = 0; tick < 8; tick++) {
            tick(helper, distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 1,
                "an INPUT face was used as an output");

        // Automation can never drain the buffer of a distributor.
        helper.assertTrue(distributor.getItemHandler(Direction.UP)
                        .extractItem(ItemDistributorBlockEntity.BUFFER_SLOT, 1, true).isEmpty(),
                "automation extracted from the buffer");
        helper.succeed();
    }

    /** The buffer, the cursor and the face rules survive a save and load (chunk reload). */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void distributorSurvivesSaveAndLoad(GameTestHelper helper) {
        ItemDistributorBlockEntity original = placeDistributor(helper, new BlockPos(2, Y, 2));
        original.getFaceConfig().setMode(Direction.NORTH.get3DDataValue(), FaceMode.INPUT);
        original.getFaceConfig().setMode(Direction.EAST.get3DDataValue(), FaceMode.OUTPUT);
        insert(original, new ItemStack(Items.IRON_INGOT, 8), helper);
        // One blocked pass moves the round-robin cursor and reports the reason.
        tick(helper, original);
        int savedCursor = original.getNextSide();
        helper.assertTrue(original.getBufferCount() == 8,
                "the buffer was not filled before the save");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        ItemDistributorBlockEntity restored = placeDistributor(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        // A chunk reload drops the cached neighbours; the next tick has to resolve them again.
        restored.onChunkUnloaded();

        helper.assertTrue(restored.getBufferCount() == 8,
                "the restored buffer holds " + restored.getBufferCount() + " instead of 8");
        helper.assertTrue(restored.getNextSide() == savedCursor,
                "the restored cursor is " + restored.getNextSide() + " instead of " + savedCursor);
        helper.assertTrue(restored.getFaceConfig().mode(Direction.NORTH.get3DDataValue())
                        == FaceMode.INPUT,
                "the face mode was not restored");
        helper.assertTrue(restored.getFaceConfig().mode(Direction.EAST.get3DDataValue())
                        == FaceMode.OUTPUT,
                "the output face mode was not restored");

        Container east = placeChest(helper, new BlockPos(6, Y, 2));
        for (int tick = 0; tick < 5; tick++) {
            tick(helper, restored);
        }
        helper.assertTrue(count(east, Items.IRON_INGOT) == 8,
                "the restored distributor delivered " + count(east, Items.IRON_INGOT)
                        + " items instead of 8");
        helper.succeed();
    }

    /** Nothing is created or voided: chests plus buffer always hold exactly what went in. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void noItemIsLostOrDuplicated(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        Container west = placeChest(helper, new BlockPos(1, Y, 2));
        Container east = placeChest(helper, new BlockPos(3, Y, 2));

        insert(distributor, new ItemStack(Items.IRON_INGOT, 64), helper);
        for (int tick = 0; tick < 30; tick++) {
            tick(helper, distributor);
        }

        int total = count(west, Items.IRON_INGOT) + count(east, Items.IRON_INGOT)
                + distributor.getBufferCount();
        helper.assertTrue(total == 64,
                "the line holds " + total + " items instead of 64");
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor still holds " + distributor.getBufferCount() + " items");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.succeed();
    }

    /**
     * A real pipe feeds the distributor, which feeds the real auto crafter: the existing logistics
     * blocks and the new machine cooperate through the normal item handler capability.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pipeFeedsTheDistributorAndTheDistributorFeedsTheCrafter(GameTestHelper helper) {
        // Exactly one pipe load: the pipe pulls 8 items and the whole line must deliver them.
        Container source = placeChest(helper, new BlockPos(0, Y, 2));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(1, Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(3, Y, 2));

        for (int tick = 0; tick < 60; tick++) {
            tickPipe(helper, pipe);
            tick(helper, distributor);
        }

        int delivered = count(crafter.getItemHandler(), Items.IRON_INGOT);
        helper.assertTrue(delivered == 8,
                "the crafter received " + delivered + " items instead of 8"
                        + " (pipe buffer " + pipe.getBufferCount()
                        + ", distributor buffer " + distributor.getBufferCount() + ")");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds items");
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor still holds " + distributor.getBufferCount() + " items");
        helper.succeed();
    }

    /** The other direction: the distributor hands its stack to a pipe, which carries it on. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void distributorFeedsAPipe(GameTestHelper helper) {
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(2, Y, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.NORMAL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        Container target = placeChest(helper, new BlockPos(4, Y, 2));

        insert(distributor, new ItemStack(Items.COPPER_INGOT, 4), helper);
        tick(helper, distributor);
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor did not hand the stack to the pipe:" +
                        " buffer " + distributor.getBufferCount()
                        + ", pipe " + pipe.getBufferCount()
                        + ", status " + distributor.getStatus());

        for (int tick = 0; tick < 20; tick++) {
            tickPipe(helper, pipe);
            tick(helper, distributor);
        }
        helper.assertTrue(count(target, Items.COPPER_INGOT) == 4,
                "the chest holds " + count(target, Items.COPPER_INGOT) + " instead of 4");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds items");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void insert(ItemDistributorBlockEntity distributor, ItemStack stack,
                               GameTestHelper helper) {
        ItemStack leftover = distributor.getItemHandler(Direction.UP)
                .insertItem(ItemDistributorBlockEntity.BUFFER_SLOT, stack, false);
        if (!leftover.isEmpty()) {
            helper.fail("the distributor refused " + leftover);
        }
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int count(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void fillCompletely(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    private static ItemDistributorBlockEntity placeDistributor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_DISTRIBUTOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ItemDistributorBlockEntity distributor)) {
            helper.fail("item distributor block entity missing at " + pos);
            return null;
        }
        return distributor;
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

    private static void tick(GameTestHelper helper, ItemDistributorBlockEntity distributor) {
        BlockState state = distributor.getBlockState();
        ItemDistributorBlockEntity.serverTick(helper.getLevel(), distributor.getBlockPos(), state,
                distributor);
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }
}
