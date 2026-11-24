package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.item.WrenchItem;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.PipeConnectionRules;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 10: pipe connection modes.
 *
 * <p>Every test starts from the new default: a freshly placed pipe has six {@code NONE} faces and
 * moves nothing. Transport is then enabled explicitly, exactly like a player would with the
 * wrench, and the tests assert exact item counts so a desync between the displayed mode and the
 * real behaviour fails immediately.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeModeGameTests {
    private static final int Y = 6;

    private PipeModeGameTests() {}

    /** A new pipe shows its connections but must not pull or push anything. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void newPipeIsInert(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1));
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 16));

        for (Direction direction : Direction.values()) {
            helper.assertTrue(pipe.getMode(direction) == PipeConnectionMode.NONE,
                    "a new pipe face is " + pipe.getMode(direction) + " instead of NONE");
        }
        // Both neighbours are still connected visually.
        BlockState state = pipe.getBlockState();
        helper.assertTrue(state.getValue(com.industrialcore.block.ItemPipeBlock.WEST),
                "the pipe does not connect to the source chest");
        helper.assertTrue(state.getValue(com.industrialcore.block.ItemPipeBlock.EAST),
                "the pipe does not connect to the sink chest");

        for (int tick = 0; tick < 200; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(count(source, Items.IRON_INGOT) == 16,
                "a new pipe removed items from the chest: " + count(source, Items.IRON_INGOT));
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 0, "a new pipe pushed items into the sink");
        helper.assertTrue(pipe.getBufferCount() == 0, "a new pipe picked up items");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.succeed();
    }

    /** NORMAL is a pipe route; it must not touch an adjacent inventory. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void normalFacesDoNotTouchInventories(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1));
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        for (Direction direction : Direction.values()) {
            pipe.setMode(direction, PipeConnectionMode.NORMAL);
        }
        source.setItem(0, new ItemStack(Items.GOLD_INGOT, 8));

        for (int tick = 0; tick < 200; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(count(source, Items.GOLD_INGOT) == 8,
                "NORMAL pulled from a chest: " + count(source, Items.GOLD_INGOT));
        helper.assertTrue(count(sink, Items.GOLD_INGOT) == 0, "NORMAL pushed into a chest");
        helper.assertTrue(pipe.getBufferCount() == 0, "NORMAL treated a chest as a source");
        helper.succeed();
    }

    /** PULL extracts and PUSH inserts, and the total item count stays exact. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void pullAndPushMoveTheExactAmount(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1));
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 32));

        helper.assertTrue(pipe.getMode(Direction.WEST) == PipeConnectionMode.PULL,
                "the pull mode was not stored on the west face");
        helper.assertTrue(pipe.getMode(Direction.EAST) == PipeConnectionMode.PUSH,
                "the push mode was not stored on the east face");

        for (int tick = 0; tick < 400 && count(sink, Items.IRON_INGOT) < 32; tick++) {
            tickPipe(helper, pipe);
        }
        IndustrialCore.LOGGER.info("[perf] pipe modes: source={} sink={} held={}",
                count(source, Items.IRON_INGOT), count(sink, Items.IRON_INGOT), pipe.getBufferCount());
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 32,
                "the sink received " + count(sink, Items.IRON_INGOT) + " of 32 iron");
        helper.assertTrue(count(source, Items.IRON_INGOT) == 0,
                "the source still holds " + count(source, Items.IRON_INGOT) + " iron");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds items");
        helper.assertItemEntityNotPresent(Items.IRON_INGOT);
        helper.succeed();
    }

    /** A PUSH face never inserts into a full inventory, and nothing is lost. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fullDestinationKeepsTheStack(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1));
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        for (int slot = 0; slot < sink.getContainerSize(); slot++) {
            sink.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        source.setItem(0, new ItemStack(Items.DIAMOND, 5));

        for (int tick = 0; tick < 120; tick++) {
            tickPipe(helper, pipe);
        }
        int total = count(source, Items.DIAMOND) + pipe.getBufferCount();
        helper.assertTrue(total == 5, "a blocked PUSH face lost or duplicated items: " + total + " of 5");
        helper.assertTrue(pipe.isBlocked(), "the pipe does not report the blocked PUSH face");
        helper.assertItemEntityNotPresent(Items.DIAMOND);

        // Freeing a slot must let the pipe continue without a restart.
        sink.setItem(0, ItemStack.EMPTY);
        for (int tick = 0; tick < 120 && pipe.getBufferCount() > 0; tick++) {
            tickPipe(helper, pipe);
        }
        helper.assertTrue(count(sink, Items.DIAMOND) == 5,
                "the pipe did not resume into the freed slot: " + count(sink, Items.DIAMOND));
        helper.succeed();
    }

    /** A pipe-to-pipe hand-off needs a non-NONE face on both sides. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void handOffNeedsBothSidesConfigured(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity first = placePipe(helper, new BlockPos(2, Y, 1));
        ItemPipeBlockEntity second = placePipe(helper, new BlockPos(3, Y, 1));
        Container sink = placeChest(helper, new BlockPos(4, Y, 1));
        first.setMode(Direction.WEST, PipeConnectionMode.PULL);
        first.setMode(Direction.EAST, PipeConnectionMode.NORMAL);
        // The second pipe keeps its west face at NONE: it is a wall.
        second.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        source.setItem(0, new ItemStack(Items.GOLD_INGOT, 8));

        for (int tick = 0; tick < 150; tick++) {
            tickPipe(helper, second);
            tickPipe(helper, first);
        }
        helper.assertTrue(count(sink, Items.GOLD_INGOT) == 0,
                "items passed a NONE face: " + count(sink, Items.GOLD_INGOT));
        helper.assertTrue(count(source, Items.GOLD_INGOT) + first.getBufferCount()
                        + second.getBufferCount() == 8,
                "items were lost at the closed face");

        second.setMode(Direction.WEST, PipeConnectionMode.NORMAL);
        for (int tick = 0; tick < 300 && count(sink, Items.GOLD_INGOT) < 8; tick++) {
            tickPipe(helper, second);
            tickPipe(helper, first);
        }
        helper.assertTrue(count(sink, Items.GOLD_INGOT) == 8,
                "opening the face did not deliver the items: " + count(sink, Items.GOLD_INGOT));
        helper.succeed();
    }

    /** Modes survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void modesSurviveSaveAndLoad(GameTestHelper helper) {
        ItemPipeBlockEntity original = placePipe(helper, new BlockPos(2, Y, 1));
        original.setMode(Direction.NORTH, PipeConnectionMode.PULL);
        original.setMode(Direction.SOUTH, PipeConnectionMode.PUSH);
        original.setMode(Direction.UP, PipeConnectionMode.NORMAL);
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        ItemPipeBlockEntity restored = placePipe(helper, new BlockPos(5, Y, 1));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getMode(Direction.NORTH) == PipeConnectionMode.PULL,
                "north was restored as " + restored.getMode(Direction.NORTH));
        helper.assertTrue(restored.getMode(Direction.SOUTH) == PipeConnectionMode.PUSH,
                "south was restored as " + restored.getMode(Direction.SOUTH));
        helper.assertTrue(restored.getMode(Direction.UP) == PipeConnectionMode.NORMAL,
                "up was restored as " + restored.getMode(Direction.UP));
        helper.assertTrue(restored.getMode(Direction.DOWN) == PipeConnectionMode.NONE,
                "an unused face was not restored as NONE");
        helper.assertTrue(restored.isConfigured(), "the restored pipe reports no transport at all");

        // And the restored configuration really moves items.
        Container source = placeChest(helper, new BlockPos(4, Y, 3));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        ItemPipeBlockEntity outgoing = placePipe(helper, new BlockPos(5, Y, 3));
        outgoing.loadWithComponents(tag, helper.getLevel().registryAccess());
        outgoing.setMode(Direction.WEST, PipeConnectionMode.PULL);
        outgoing.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        Container sink = placeChest(helper, new BlockPos(6, Y, 3));
        for (int tick = 0; tick < 200 && count(sink, Items.IRON_INGOT) < 3; tick++) {
            tickPipe(helper, outgoing);
        }
        helper.assertTrue(count(sink, Items.IRON_INGOT) == 3,
                "the restored pipe did not transport: " + count(sink, Items.IRON_INGOT));
        helper.succeed();
    }

    /** A pipe saved before this phase has no mode data and must load as inert NONE. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void legacyPipeWithoutModeDataDefaultsToNone(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, Y, 1));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 1));
        Container sink = placeChest(helper, new BlockPos(3, Y, 1));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        CompoundTag legacy = pipe.saveWithoutMetadata(helper.getLevel().registryAccess());
        legacy.remove("Modes");

        ItemPipeBlockEntity restored = placePipe(helper, new BlockPos(2, Y, 5));
        restored.loadWithComponents(legacy, helper.getLevel().registryAccess());
        for (Direction direction : Direction.values()) {
            helper.assertTrue(restored.getMode(direction) == PipeConnectionMode.NONE,
                    "a legacy pipe loaded " + restored.getMode(direction) + " on " + direction);
        }
        source.setItem(0, new ItemStack(Items.DIAMOND, 4));
        for (int tick = 0; tick < 100; tick++) {
            tickPipe(helper, restored);
        }
        helper.assertTrue(count(source, Items.DIAMOND) == 4 && pipe.getBufferCount() == 0,
                "a legacy pipe started moving items by itself");
        helper.assertTrue(count(sink, Items.DIAMOND) == 0, "a legacy pipe pushed items into the sink");
        helper.succeed();
    }

    /** The wrench changes exactly the clicked face; sneaking cycles the other way. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void wrenchCyclesOneFace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, Y, 2);
        ItemPipeBlockEntity pipe = placePipe(helper, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WrenchItem wrench = ModItems.WRENCH.get();
        BlockPos absolute = helper.absolutePos(pos);

        // Click the east arm: the dominant axis is X, so exactly the east face changes.
        UseOnContext context = new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new ItemStack(wrench), new BlockHitResult(new Vec3(absolute.getX() + 0.95D,
                absolute.getY() + 0.5D, absolute.getZ() + 0.5D), Direction.UP, absolute, false));
        wrench.useOn(context);

        helper.assertTrue(pipe.getMode(Direction.EAST) == PipeConnectionMode.NORMAL,
                "the wrench did not cycle the east face: " + pipe.getMode(Direction.EAST));
        for (Direction direction : Direction.values()) {
            if (direction != Direction.EAST) {
                helper.assertTrue(pipe.getMode(direction) == PipeConnectionMode.NONE,
                        "the wrench also changed " + direction + " to " + pipe.getMode(direction));
            }
        }

        wrench.useOn(context);
        helper.assertTrue(pipe.getMode(Direction.EAST) == PipeConnectionMode.PUSH,
                "the second click did not advance to PUSH: " + pipe.getMode(Direction.EAST));
        wrench.useOn(context);
        wrench.useOn(context);
        helper.assertTrue(pipe.getMode(Direction.EAST) == PipeConnectionMode.NONE,
                "the cycle did not wrap back to NONE: " + pipe.getMode(Direction.EAST));

        // Directly verify the reverse order used by sneak-clicking (the mock player cannot sneak).
        helper.assertTrue(PipeConnectionRules.cycle(PipeConnectionMode.NONE, true) == PipeConnectionMode.PULL,
                "the reverse cycle does not start at PULL");
        helper.succeed();
    }

    /** The client display snapshot carries the modes, so the GUI can show them. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void visualTagCarriesModes(GameTestHelper helper) {
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 2));
        pipe.setMode(Direction.NORTH, PipeConnectionMode.PULL);
        CompoundTag update = pipe.getUpdateTag(helper.getLevel().registryAccess());
        byte[] modes = update.getCompound("Visual").getByteArray("Modes");
        helper.assertTrue(modes.length == 6, "the visual tag carries " + modes.length + " modes");
        helper.assertTrue(modes[Direction.NORTH.get3DDataValue()] == PipeConnectionMode.PULL.ordinal(),
                "the visual tag lost the north mode");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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

    private static int count(Container container, net.minecraft.world.item.Item item) {
        return container == null ? 0 : container.countItem(item);
    }
}
