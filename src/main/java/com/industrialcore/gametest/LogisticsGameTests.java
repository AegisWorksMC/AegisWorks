package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.ConveyorBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for the Phase 4/5.1 item pipes and the quarry integration.
 *
 * <p>The pipe now really spends time inside each block, so these tests count ticks and assert
 * exact totals. Every delivery test also checks that source + pipes + sink still hold exactly the
 * original amount, which catches both loss and duplication.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LogisticsGameTests {
    private static final int Y = 6;

    private LogisticsGameTests() {}

    /** Legacy world safety: a belt drops its stack and becomes a Basic pipe on its first tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void legacyConveyorDropsContentsAndBecomesPipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, Y, 2);
        helper.setBlock(pos, ModBlocks.CONVEYOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ConveyorBlockEntity conveyor)) {
            helper.fail("legacy conveyor block entity missing");
            return;
        }

        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("Stack", new ItemStack(Items.IRON_INGOT, 5)
                .save(helper.getLevel().registryAccess()));
        items.add(entry);
        tag.put("Items", items);
        conveyor.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(conveyor.getLegacyItemCount() == 1,
                "the legacy belt did not load its saved stack");

        ConveyorBlockEntity.serverTick(helper.getLevel(), conveyor.getBlockPos(),
                conveyor.getBlockState(), conveyor);

        helper.assertTrue(helper.getBlockState(pos).is(ModBlocks.ITEM_PIPE.get()),
                "the legacy belt was not replaced by a Basic Item Pipe");
        helper.assertItemEntityPresent(Items.IRON_INGOT, pos, 3.0D);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /** Chest -> pipe -> chest and chest -> pipe -> pipe -> chest. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void pipeMovesItemsBetweenInventories(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        // Phase 10: a pipe only moves items on faces that were explicitly configured.
        pull(pipe, Direction.WEST);
        push(pipe, Direction.EAST);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 32));

        for (int tick = 0; tick < 300 && count(sink, Items.IRON_INGOT) < 32; tick++) {
            tickPipe(helper, pipe);
        }
        IndustrialCore.LOGGER.info("[perf] pipe direct: source={} sink={} pipeBuffer={}",
                count(source, Items.IRON_INGOT), count(sink, Items.IRON_INGOT), pipe.getBufferCount());
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 32,
                "the sink received " + count(sink, Items.IRON_INGOT) + " of 32 iron");
        helper.assertTrue(count(source, Items.IRON_INGOT) == 0, "the source still holds iron");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds " + pipe.getBufferCount() + " iron");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);

        // two pipes in a row
        Container chainSource = placeChest(helper, new BlockPos(1, Y, 4));
        ItemPipeBlockEntity first = placePipe(helper, new BlockPos(2, Y, 4), ModBlocks.ITEM_PIPE);
        ItemPipeBlockEntity second = placePipe(helper, new BlockPos(3, Y, 4), ModBlocks.ITEM_PIPE);
        Container chainSink = placeChest(helper, new BlockPos(4, Y, 4));
        pull(first, Direction.WEST);
        normal(first, Direction.EAST);
        normal(second, Direction.WEST);
        push(second, Direction.EAST);
        chainSource.setItem(0, new ItemStack(Items.GOLD_INGOT, 16));

        for (int tick = 0; tick < 500 && count(chainSink, Items.GOLD_INGOT) < 16; tick++) {
            tickPipe(helper, second);
            tickPipe(helper, first);
        }
        helper.assertTrue(count(chainSink, Items.GOLD_INGOT) == 16,
                "the chained sink received " + count(chainSink, Items.GOLD_INGOT) + " of 16 gold");
        helper.assertTrue(count(chainSource, Items.GOLD_INGOT) == 0, "the chained source still holds gold");
        helper.assertTrue(first.getBufferCount() == 0 && second.getBufferCount() == 0, "the pipes still hold gold");
        helper.assertItemEntityNotPresent(Items.GOLD_INGOT);
        helper.succeed();
    }

    /**
     * Tier 1 is intentionally slower than the old one-tick pipe, and every tier is faster than the
     * previous one. This measures the real server transit, not a display value.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pipeTiersHaveDifferentRealTransitTimes(GameTestHelper helper) {
        int[] measured = new int[PipeTier.values().length];
        for (int index = 0; index < PipeTier.values().length; index++) {
            int z = 1 + index * 2;
            Container source = placeChest(helper, new BlockPos(1, Y, z));
            ItemPipeBlockEntity pipe = placePipeAtTier(helper, new BlockPos(2, Y, z), PipeTier.byIndex(index));
            Container sink = placeChest(helper, new BlockPos(3, Y, z));
            pull(pipe, Direction.WEST);
            push(pipe, Direction.EAST);
            source.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
            int ticks = 0;
            while (ticks < 200 && count(sink, Items.IRON_INGOT) == 0) {
                tickPipe(helper, pipe);
                ticks++;
            }
            helper.assertTrue(count(sink, Items.IRON_INGOT) == 1,
                    "tier " + index + " did not deliver a single item within 200 ticks");
            measured[index] = ticks;
        }
        IndustrialCore.LOGGER.info("[perf] pipe tier transit ticks: {}", java.util.Arrays.toString(measured));
        helper.assertTrue(measured[0] >= PipeTier.BASIC.travelTicks(),
                "Basic must spend at least " + PipeTier.BASIC.travelTicks() + " ticks in one pipe");
        for (int index = 1; index < measured.length; index++) {
            helper.assertTrue(measured[index] < measured[index - 1],
                    "tier " + index + " was not faster than tier " + (index - 1)
                            + " (" + measured[index] + " vs " + measured[index - 1] + ")");
        }
        helper.assertTrue(measured[0] > 1, "the basic pipe must be slower than the old instant pipe");
        helper.succeed();
    }

    /** Different tiers connect and forward items through the same implementation. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void differentPipeTiersConnectAndTransfer(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity basic = placePipeAtTier(helper, new BlockPos(2, Y, 1), PipeTier.BASIC);
        ItemPipeBlockEntity ultimate = placePipeAtTier(helper, new BlockPos(3, Y, 1), PipeTier.ULTIMATE);
        Container sink = placeChest(helper, new BlockPos(4, Y, 1));
        pull(basic, Direction.WEST);
        normal(basic, Direction.EAST);
        normal(ultimate, Direction.WEST);
        push(ultimate, Direction.EAST);
        source.setItem(0, new ItemStack(Items.GOLD_INGOT, 16));

        BlockState state = helper.getBlockState(new BlockPos(2, Y, 1));
        BlockState recalculated = ItemPipeBlock.recalculate(state, helper.getLevel(),
                helper.absolutePos(new BlockPos(2, Y, 1)));
        helper.assertTrue(recalculated.getValue(ItemPipeBlock.EAST), "Basic pipe did not connect east to Ultimate");

        for (int tick = 0; tick < 500 && count(sink, Items.GOLD_INGOT) < 16; tick++) {
            tickPipe(helper, ultimate);
            tickPipe(helper, basic);
        }
        helper.assertTrue(count(sink, Items.GOLD_INGOT) == 16,
                "mixed-tier line delivered " + count(sink, Items.GOLD_INGOT) + " of 16 gold");
        helper.assertTrue(count(source, Items.GOLD_INGOT) == 0
                        && basic.getBufferCount() == 0 && ultimate.getBufferCount() == 0,
                "mixed-tier line lost or retained items");
        helper.succeed();
    }

    /** A blocked network keeps its items, is bounded by the hop limit and never oscillates. */
    @GameTest(template = "quarry_test", timeoutTicks = 700)
    public static void pipeStopsAtBlockedNetworkWithoutDuplication(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity first = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        ItemPipeBlockEntity second = placePipe(helper, new BlockPos(3, Y, 1), ModBlocks.ITEM_PIPE);
        pull(first, Direction.WEST);
        normal(first, Direction.EAST);
        normal(second, Direction.WEST);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        for (int tick = 0; tick < 300; tick++) {
            tickPipe(helper, second);
            tickPipe(helper, first);
        }
        int held = count(source, Items.IRON_INGOT) + first.getBufferCount() + second.getBufferCount();
        helper.assertTrue(held == 8, "a blocked network lost or duplicated items: " + held + " of 8");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.assertTrue(first.getHops() <= Config.pipeMaxHops() && second.getHops() <= Config.pipeMaxHops(),
                "the hop limit was exceeded");
        helper.assertTrue(second.getBufferCount() == 8, "the item did not stop in the last pipe");
        helper.assertTrue(second.isBlocked(), "the last pipe does not report the blocked exit");

        // A source and a sink around one pipe must drain the source and then stay quiet.
        Container churnSource = placeChest(helper, new BlockPos(1, Y, 9));
        ItemPipeBlockEntity churnPipe = placePipe(helper, new BlockPos(2, Y, 9), ModBlocks.ITEM_PIPE);
        Container churnSink = placeChest(helper, new BlockPos(3, Y, 9));
        pull(churnPipe, Direction.WEST);
        push(churnPipe, Direction.EAST);
        churnSource.setItem(0, new ItemStack(Items.GOLD_INGOT, 8));

        for (int tick = 0; tick < 300; tick++) {
            tickPipe(helper, churnPipe);
        }
        helper.assertTrue(count(churnSink, Items.GOLD_INGOT) == 8,
                "the sink received " + count(churnSink, Items.GOLD_INGOT) + " of 8 gold");
        helper.assertTrue(count(churnSource, Items.GOLD_INGOT) == 0, "the source still holds gold");

        for (int tick = 0; tick < 200; tick++) {
            tickPipe(helper, churnPipe);
        }
        helper.assertTrue(count(churnSink, Items.GOLD_INGOT) == 8 && count(churnSource, Items.GOLD_INGOT) == 0
                        && churnPipe.getBufferCount() == 0,
                "items kept circulating between the two chests");
        helper.assertItemEntityNotPresent(Items.GOLD_INGOT);
        helper.succeed();
    }

    /** Placing a sink later must work, and breaking a pipe must drop what it holds. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void pipeHandlesTopologyChangesAndRemoval(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        pull(pipe, Direction.WEST);
        push(pipe, Direction.EAST);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 4));

        for (int tick = 0; tick < 80; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.getBufferCount() == 4,
                "the pipe did not hold the blocked items: " + pipe.getBufferCount());
        helper.assertTrue(count(source, Items.IRON_INGOT) == 0, "the source still holds iron");

        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        for (int tick = 0; tick < 100 && pipe.getBufferCount() > 0; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 4,
                "a chest placed after the pipe was not discovered: " + count(sink, Items.IRON_INGOT));

        breakByPlayer(helper, new BlockPos(3, Y, 1));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        for (int tick = 0; tick < 80; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.getBufferCount() == 3, "the pipe did not pick up the new items");
        breakByPlayer(helper, new BlockPos(2, Y, 1));
        helper.assertTrue(pipe.getBufferCount() == 0, "the broken pipe still holds items");
        helper.assertItemEntityPresent(Items.IRON_INGOT, new BlockPos(2, Y, 1), 4.0D);
        helper.succeed();
    }

    /** A loop must never duplicate or void items, even when it has no sink. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void pipeLoopKeepsExactItemCount(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity first = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        ItemPipeBlockEntity second = placePipe(helper, new BlockPos(2, Y, 2), ModBlocks.ITEM_PIPE);
        ItemPipeBlockEntity third = placePipe(helper, new BlockPos(3, Y, 2), ModBlocks.ITEM_PIPE);
        ItemPipeBlockEntity fourth = placePipe(helper, new BlockPos(3, Y, 1), ModBlocks.ITEM_PIPE);
        for (ItemPipeBlockEntity loopPipe : new ItemPipeBlockEntity[] {first, second, third, fourth}) {
            for (Direction direction : Direction.values()) {
                normal(loopPipe, direction);
            }
        }
        pull(first, Direction.WEST);
        source.setItem(0, new ItemStack(Items.DIAMOND, 8));

        for (int tick = 0; tick < 600; tick++) {
            tickPipe(helper, fourth);
            tickPipe(helper, third);
            tickPipe(helper, second);
            tickPipe(helper, first);
        }
        int total = count(source, Items.DIAMOND) + first.getBufferCount() + second.getBufferCount()
                + third.getBufferCount() + fourth.getBufferCount();
        helper.assertTrue(total == 8, "the pipe loop duplicated or lost items: " + total + " of 8");
        helper.assertItemEntityNotPresent(Items.DIAMOND);
        helper.succeed();
    }

    /** The buffer and the transit progress survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void pipeTransitSurvivesSaveAndLoad(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipeAtTier(helper, new BlockPos(2, Y, 1), PipeTier.IMPROVED);
        pull(pipe, Direction.WEST);
        source.setItem(0, new ItemStack(Items.DIAMOND, 5));
        for (int tick = 0; tick < 3; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.getBufferCount() == 5, "the pipe did not pick up the diamonds");
        float progressBefore = pipe.getProgress();

        CompoundTag tag = pipe.saveWithoutMetadata(helper.getLevel().registryAccess());
        ItemPipeBlockEntity restored = placePipeAtTier(helper, new BlockPos(5, Y, 1), PipeTier.IMPROVED);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getBufferCount() == 5,
                "the restored pipe holds " + restored.getBufferCount() + " instead of 5 diamonds");
        helper.assertTrue(Math.abs(restored.getProgress() - progressBefore) <= 1.0F / PipeTier.IMPROVED.travelTicks(),
                "the restored progress differs: " + restored.getProgress() + " vs " + progressBefore);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Integration and performance
    // ------------------------------------------------------------------

    /** A quarry feeds a pipe without any change to the quarry itself. */
    @GameTest(template = "quarry_test", timeoutTicks = 700)
    public static void quarryFeedsPipeIntoChest(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 5), Direction.EAST);
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(1, Y, 5), ModBlocks.ITEM_PIPE);
        Container chest = placeChest(helper, new BlockPos(0, Y, 5));
        pull(pipe, Direction.EAST);
        push(pipe, Direction.WEST);
        helper.setBlock(new BlockPos(3, Y, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(4, Y, 5), Blocks.STONE);
        configureQuarry(quarry, 1, 2, 1);

        for (int tick = 0; tick < 700; tick++) {
            tickQuarry(helper, quarry);
            tickPipe(helper, pipe);
        }

        IndustrialCore.LOGGER.info(
                "[perf] quarry+pipe: mined={} status={} outputHeld={} pipe={} chest={}",
                quarry.getBlocksMined(), quarry.getStatus(), !quarryOutputEmpty(quarry),
                pipe.getBufferCount(), count(chest, Items.COBBLESTONE));
        helper.assertTrue(count(chest, Items.COBBLESTONE) == 2,
                "the pipe delivered " + count(chest, Items.COBBLESTONE) + " of 2 cobblestone");
        helper.assertTrue(quarryOutputEmpty(quarry), "the quarry still holds items although a pipe drains it");
        helper.assertTrue(quarry.getBlocksMined() == 2,
                "the quarry mined " + quarry.getBlocksMined() + " blocks");
        helper.assertItemEntityNotPresent(Items.COBBLESTONE);
        helper.succeed();
    }

    /** Many idle pipes must cost almost nothing per tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void idlePipesTickAreCheap(GameTestHelper helper) {
        BlockPos[] pipeSpots = {
                new BlockPos(1, Y, 1), new BlockPos(1, Y, 3), new BlockPos(1, Y, 5), new BlockPos(1, Y, 7)
        };
        ItemPipeBlockEntity[] pipes = new ItemPipeBlockEntity[pipeSpots.length];
        for (int i = 0; i < pipes.length; i++) {
            pipes[i] = placePipe(helper, pipeSpots[i], ModBlocks.ITEM_PIPE);
        }

        for (int warm = 0; warm < 100; warm++) {
            for (ItemPipeBlockEntity pipe : pipes) {
                tickPipe(helper, pipe);
            }
        }

        int iterations = 1_000;
        long start = System.nanoTime();
        for (int tick = 0; tick < iterations; tick++) {
            for (ItemPipeBlockEntity pipe : pipes) {
                tickPipe(helper, pipe);
            }
        }
        double microsPerTick = (System.nanoTime() - start) / (double) (iterations * pipes.length) / 1_000.0D;
        IndustrialCore.LOGGER.info("[perf] {} idle pipes: {} us/pipe-tick",
                pipes.length, String.format("%.3f", microsPerTick));
        helper.assertTrue(microsPerTick < 50.0D,
                "an idle pipe tick should stay far below 50 us but was " + microsPerTick + " us");
        for (ItemPipeBlockEntity pipe : pipes) {
            helper.assertTrue(pipe.getBufferCount() == 0, "an idle pipe holds items");
            helper.assertTrue(pipe.getKnownHandlers() == 0, "an isolated pipe found an inventory");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void pull(ItemPipeBlockEntity pipe, Direction direction) {
        pipe.setMode(direction, PipeConnectionMode.PULL);
    }

    private static void push(ItemPipeBlockEntity pipe, Direction direction) {
        pipe.setMode(direction, PipeConnectionMode.PUSH);
    }

    private static void normal(ItemPipeBlockEntity pipe, Direction direction) {
        pipe.setMode(direction, PipeConnectionMode.NORMAL);
    }

    private static ItemPipeBlockEntity placePipe(GameTestHelper helper, BlockPos pos,
                                                 net.neoforged.neoforge.registries.DeferredBlock<ItemPipeBlock> block) {
        helper.setBlock(pos, block.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ItemPipeBlockEntity pipe)) {
            helper.fail("item pipe block entity missing at " + pos);
            return null;
        }
        return pipe;
    }

    private static ItemPipeBlockEntity placePipeAtTier(GameTestHelper helper, BlockPos pos, PipeTier tier) {
        return switch (tier) {
            case BASIC -> placePipe(helper, pos, ModBlocks.ITEM_PIPE);
            case IMPROVED -> placePipe(helper, pos, ModBlocks.IMPROVED_ITEM_PIPE);
            case ADVANCED -> placePipe(helper, pos, ModBlocks.ADVANCED_ITEM_PIPE);
            case ULTIMATE -> placePipe(helper, pos, ModBlocks.ULTIMATE_ITEM_PIPE);
        };
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

    private static QuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos, Direction facing) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        helper.setBlock(pos, ModBlocks.QUARRY.get().defaultBlockState().setValue(QuarryBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            helper.fail("quarry block entity missing at " + pos);
            return null;
        }
        return quarry;
    }

    private static void configureQuarry(QuarryBlockEntity quarry, int width, int depth, int height) {
        quarry.setEnergyStored(20_000);
        quarry.applyRange(width, depth, height);
        quarry.setRunning(true);
    }

    private static boolean quarryOutputEmpty(QuarryBlockEntity quarry) {
        for (int slot = QuarryBlockEntity.OUTPUT_START; slot < QuarryBlockEntity.TOTAL_SLOTS; slot++) {
            if (!quarry.getInventory().getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }

    private static void tickQuarry(GameTestHelper helper, QuarryBlockEntity quarry) {
        BlockState state = quarry.getBlockState();
        QuarryBlockEntity.serverTick(helper.getLevel(), quarry.getBlockPos(), state, quarry);
    }

    private static void breakByPlayer(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        state.getBlock().playerWillDestroy(helper.getLevel(), absolute, state, player);
        helper.getLevel().removeBlock(absolute, false);
    }

    private static int count(Container container, Item item) {
        return container == null ? 0 : container.countItem(item);
    }
}
