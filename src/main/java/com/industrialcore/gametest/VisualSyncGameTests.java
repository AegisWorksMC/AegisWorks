package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for the pipe display data added in Phase 5.1.
 *
 * <p>The renderer itself cannot be tested headless. These tests verify what the renderer receives:
 * the stack, entry/exit sides, server progress, travel time and blocked flag, that an emptied pipe
 * clears the display, that display data never leaks into the saved chunk data and that idle pipes
 * send nothing. The actual smoothness and the in-game look remain NOT RUN.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisualSyncGameTests {
    private static final int Y = 6;

    private static final String TAG_VISUAL = "Visual";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_FROM = "From";
    private static final String TAG_TO = "To";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_BLOCKED = "Blocked";
    private static final String TAG_TRAVEL = "Travel";

    private VisualSyncGameTests() {}

    /** The display tag describes the stack, both sides and a progress strictly inside the block. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void pipeVisualTagDescribesTransit(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);

        for (int tick = 0; tick < 4; tick++) {
            tickPipe(helper, pipe);
        }
        CompoundTag visual = pipe.getUpdateTag(helper.getLevel().registryAccess()).getCompound(TAG_VISUAL);
        ItemStack drawn = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                visual.getCompound(TAG_STACK));
        helper.assertTrue(drawn.is(Items.IRON_INGOT) && drawn.getCount() == 8,
                "the pipe tag describes " + drawn + " instead of 8 iron ingots");
        helper.assertTrue(visual.getInt(TAG_FROM) == Direction.WEST.get3DDataValue(),
                "the pipe tag reports arrival side " + visual.getInt(TAG_FROM) + " instead of west");
        helper.assertTrue(visual.getInt(TAG_TO) == Direction.EAST.get3DDataValue(),
                "the pipe tag reports exit side " + visual.getInt(TAG_TO) + " instead of east");
        helper.assertTrue(visual.getFloat(TAG_PROGRESS) > 0.0F && visual.getFloat(TAG_PROGRESS) < 1.0F,
                "progress " + visual.getFloat(TAG_PROGRESS) + " is not inside the pipe");
        helper.assertTrue(visual.getInt(TAG_TRAVEL) == PipeTier.BASIC.travelTicks(),
                "the tag reports travel " + visual.getInt(TAG_TRAVEL));

        for (int tick = 0; tick < 30 && pipe.getBufferCount() > 0; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(sink.countItem(Items.IRON_INGOT) == 8,
                "the sink received " + sink.countItem(Items.IRON_INGOT) + " of 8 iron");
        CompoundTag after = pipe.getUpdateTag(helper.getLevel().registryAccess()).getCompound(TAG_VISUAL);
        helper.assertTrue(!after.contains(TAG_STACK),
                "the pipe still advertises an item it handed over");
        helper.assertTrue(pipe.getVisualSyncCount() > 0, "the pipe never broadcast a visual update");
        helper.succeed();
    }

    /** A blocked pipe reports the wait and pins the item at the exit. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void pipeVisualTagReportsBlockedExit(GameTestHelper helper) {
        Container full = placeChest(helper, new BlockPos(3, Y, 1));
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        source.setItem(0, new ItemStack(Items.DIAMOND, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);

        for (int tick = 0; tick < 40; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.isBlocked(), "the pipe does not report the blocked exit");
        helper.assertTrue(pipe.getBufferCount() == 2, "a blocked pipe lost items on the way");
        CompoundTag visual = pipe.getUpdateTag(helper.getLevel().registryAccess()).getCompound(TAG_VISUAL);
        helper.assertTrue(visual.getBoolean(TAG_BLOCKED), "the visual tag does not report the wait");
        helper.assertTrue(Math.abs(visual.getFloat(TAG_PROGRESS) - 1.0F) < 1.0E-4F,
                "a blocked stack should sit at the exit but reports progress " + visual.getFloat(TAG_PROGRESS));
        helper.succeed();
    }

    /** An empty pipe must not broadcast anything at all. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void idlePipeSendsNoVisualUpdates(GameTestHelper helper) {
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(5, Y, 5), ModBlocks.ITEM_PIPE);
        for (int tick = 0; tick < 100; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.getVisualSyncCount() == 0,
                "an idle pipe sent " + pipe.getVisualSyncCount() + " visual updates");
        helper.succeed();
    }

    /** A moving pipe sends only on change, not every tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pipeVisualUpdatesAreThrottled(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        source.setItem(0, new ItemStack(Items.DIAMOND, 8));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        for (int tick = 0; tick < 100; tick++) {
            tickPipe(helper, pipe);
        }
        int synced = pipe.getVisualSyncCount();
        IndustrialCore.LOGGER.info("[perf] blocking pipe visual updates: {} packets in 100 ticks", synced);
        helper.assertTrue(synced >= 1, "the pipe never told the client about its item");
        // The real block ticker also runs during the test, so allow a small margin while still
        // proving that the pipe does not send one packet per tick.
        helper.assertTrue(synced <= 10,
                "the pipe sent " + synced + " updates in 100 ticks, which is not throttled");
        helper.succeed();
    }

    /** Visual data must never leak into the saved chunk data (and vice versa). */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void visualDataIsSeparateFromSavedData(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        for (int tick = 0; tick < 5; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(pipe.getBufferCount() == 3, "the pipe did not pick up the iron");

        CompoundTag saved = pipe.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(!saved.contains(TAG_VISUAL), "the pipe saved its display data into the chunk");
        helper.assertTrue(saved.contains("Buffer"), "the pipe no longer saves its buffer");
        CompoundTag update = pipe.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(update.contains(TAG_VISUAL) && !update.contains("Buffer"),
                "the pipe update tag carries the wrong data set");
        helper.assertTrue(pipe.getClientVisual() == null,
                "the server pipe holds a client display snapshot");
        helper.succeed();
    }

    /**
     * The face markers read the modes from the display tag, so the modes must arrive even when the
     * pipe carries no stack. A configured but idle pipe is the normal case in a factory.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void anIdlePipeStillSyncsItsFaceModes(GameTestHelper helper) {
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1), ModBlocks.ITEM_PIPE);
        placeChest(helper, new BlockPos(1, Y, 1));
        placeChest(helper, new BlockPos(3, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe should start empty");

        CompoundTag tag = pipe.getUpdateTag(helper.getLevel().registryAccess()).getCompound(TAG_VISUAL);
        helper.assertTrue(tag.contains("Modes"), "an idle pipe does not send its face modes");
        var visual = ItemPipeBlockEntity.ClientVisual.parse(tag,
                helper.getLevel().registryAccess(), helper.getLevel().getGameTime());
        helper.assertTrue(visual.mode(Direction.WEST) == PipeConnectionMode.PULL,
                "the west face should be PULL, got " + visual.mode(Direction.WEST));
        helper.assertTrue(visual.mode(Direction.EAST) == PipeConnectionMode.PUSH,
                "the east face should be PUSH, got " + visual.mode(Direction.EAST));
        helper.assertTrue(visual.mode(Direction.UP) == PipeConnectionMode.NONE,
                "an unconfigured face should be NONE");
        helper.assertTrue(visual.isEmpty(), "an idle pipe must not report a travelling stack");
        helper.succeed();
    }

    /**
     * Phase 22 regression guard: a pipe must not mirror its wrench modes into the block state.
     *
     * <p>Phase 22 briefly did that (six booleans of connection plus six four-valued mode
     * properties), which meant 2^6 * 4^6 = 262,144 states per pipe. The client bakes a model for
     * every block state at start-up, so the four pipe tiers alone added over a million baked
     * states and could stall or exhaust the heap before the main menu. The face markers are drawn
     * by the block entity renderer now, and this test keeps the state count where it belongs.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 40)
    public static void pipeBlockStatesStaySmall(GameTestHelper helper) {
        var pipes = java.util.List.of(ModBlocks.ITEM_PIPE.get(), ModBlocks.IMPROVED_ITEM_PIPE.get(),
                ModBlocks.ADVANCED_ITEM_PIPE.get(), ModBlocks.ULTIMATE_ITEM_PIPE.get());
        long total = 0L;
        for (var block : pipes) {
            int states = block.getStateDefinition().getPossibleStates().size();
            IndustrialCore.LOGGER.info("[pipe-state-budget] {} -> {} block states",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block), states);
            helper.assertTrue(states <= 128,
                    "pipe block state budget exceeded: " + states + " states (max 128)");
            total += states;
        }
        IndustrialCore.LOGGER.info("[pipe-state-budget] four pipe tiers total {} states; "
                        + "registry total {} states", total,
                net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size());
        helper.assertTrue(total <= 512, "the four pipe tiers use " + total + " states (max 512)");
        helper.succeed();
    }

    private static ItemPipeBlockEntity placePipe(GameTestHelper helper, BlockPos pos,
                                                 net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        helper.setBlock(pos, block.get().defaultBlockState());
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
}
