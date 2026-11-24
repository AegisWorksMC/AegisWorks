package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.MiningSync;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 9: the quarry frame and the real mining work share one progress model.
 *
 * <p>These tests run the real server block ticker on real blocks and assert the invariants the
 * fix depends on: the cursor only advances together with processed positions, the drawn drill
 * index never separates from it, pausing stops both, and nothing is broken twice or skipped
 * silently when reloaded.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarrySyncGameTests {
    private static final int Y = 6;

    private QuarrySyncGameTests() {}

    /**
     * With many motors the machine processes several positions per tick. The drill index must stay
     * within one position of the cursor and the frame must never point past it.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fastQuarryKeepsFrameAndMiningTogether(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        fillStone(helper, 3, 3, 1);
        configure(quarry, 3, 3, 1);
        // 80 motors (0.05 operations each) lift the machine above one position per tick, which is
        // the regime the reported desync came from. One motor is installed per tick.
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT,
                new net.minecraft.world.item.ItemStack(com.industrialcore.registry.ModItems.MOTOR.get(), 80));

        int previousCursor = quarry.getCursor();
        boolean sawFraction = false;
        for (int tick = 0; tick < 200; tick++) {
            tick(helper, quarry);
            double drill = quarry.getDrillIndex();
            int cursor = quarry.getCursor();
            helper.assertTrue(drill >= cursor, "the drill index fell behind its cursor");
            helper.assertTrue(drill < cursor + 1.0001D,
                    "the drill index " + drill + " separated from cursor " + cursor);
            helper.assertTrue(cursor >= previousCursor, "the cursor moved backwards while mining");
            previousCursor = cursor;
            if (quarry.getWorkFraction() > 0.0F && quarry.getCursor() < quarry.getVolume()) {
                sawFraction = true;
            }
            int volume = quarry.getVolume();
            int frame = MiningSync.frameIndex(drill, cursor, volume);
            helper.assertTrue(frame <= cursor, "the drawn frame was ahead of the mining cursor");
        }

        IndustrialCore.LOGGER.info(
                "[perf] quarry sync: motors={} cursor={} mined={} skipped={} interval={} fraction={}",
                quarry.getMotorCount(), quarry.getCursor(), quarry.getBlocksMined(),
                quarry.getBlocksSkipped(), quarry.getVisualSyncInterval(), quarry.getWorkFraction());
        helper.assertTrue(quarry.getCursor() == 9,
                "the whole area was not processed: cursor=" + quarry.getCursor());
        helper.assertTrue(quarry.getBlocksMined() == 9,
                "expected 9 mined blocks, got " + quarry.getBlocksMined());
        helper.assertTrue(quarry.getBlocksSkipped() == 0,
                "the stone should not have been skipped: " + quarry.getBlocksSkipped());
        helper.assertTrue(quarry.getVisualSyncInterval() == 1,
                "a fast quarry must sync every tick, was " + quarry.getVisualSyncInterval());
        helper.assertTrue(sawFraction, "no within-block progress was ever published");
        helper.assertTrue(quarry.getMotorCount() == 80, "the motors were not installed: "
                + quarry.getMotorCount());
        helper.succeed();
    }

    /** Without FE neither the frame nor the blocks may advance. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void noEnergyStopsFrameAndMining(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        fillStone(helper, 2, 2, 1);
        quarry.applyRange(2, 2, 1);
        quarry.setRunning(true);
        quarry.setEnergyStored(0);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                            "expected PAUSED_NO_ENERGY but was " + quarry.getStatus());
                    helper.assertTrue(quarry.getCursor() == 0,
                            "the cursor advanced without FE: " + quarry.getCursor());
                    helper.assertTrue(quarry.getBlocksMined() == 0,
                            "blocks were mined without FE: " + quarry.getBlocksMined());
                    IndustrialCore.LOGGER.info(
                            "[perf] quarry no-energy: status={} cursor={} mined={} energy={} drill={}",
                            quarry.getStatus(), quarry.getCursor(), quarry.getBlocksMined(),
                            quarry.getEnergyStoredForTesting(), quarry.getDrillIndex());
                    helper.assertTrue(quarry.getDrillIndex() == quarry.getCursor(),
                            "the drill/frame advanced without FE: drill=" + quarry.getDrillIndex()
                                    + " cursor=" + quarry.getCursor());
                })
                .thenExecute(() -> quarry.setEnergyStored(20_000))
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getBlocksMined() == 4,
                            "mining did not resume: " + quarry.getBlocksMined() + " of 4");
                    helper.assertTrue(quarry.getCursor() == 4,
                            "cursor after resuming was " + quarry.getCursor());
                })
                .thenSucceed();
    }

    /** Unbreakable blocks are skipped, never counted as mined, and the cursor still advances. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void unbreakableBlocksAreSkippedNotMined(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        // Bedrock has a negative hardness, like the protected blocks of a protected area.
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.BEDROCK);
        configure(quarry, 1, 1, 1);

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getBlocksMined() == 0,
                            "bedrock was reported as mined: " + quarry.getBlocksMined());
                    helper.assertTrue(quarry.getBlocksSkipped() == 1,
                            "bedrock was not counted as skipped: " + quarry.getBlocksSkipped());
                    helper.assertTrue(quarry.getCursor() == 1,
                            "the cursor did not move past the skipped block: " + quarry.getCursor());
                    helper.assertTrue(helper.getBlockState(new BlockPos(3, Y, 2)).is(Blocks.BEDROCK),
                            "the quarry destroyed a protected block");
                })
                .thenSucceed();
    }

    /** A block is destroyed once: re-mining the same area never duplicates its drops. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void blockIsBrokenExactlyOnce(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.IRON_ORE);
        configure(quarry, 1, 1, 1);

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getBlocksMined() == 1,
                            "expected one mined block, got " + quarry.getBlocksMined());
                    helper.assertTrue(quarry.getStatus() == QuarryStatus.COMPLETE,
                            "expected COMPLETE but was " + quarry.getStatus());
                    helper.assertItemEntityNotPresent(Items.RAW_IRON);
                })
                .thenExecute(() -> {
                    // Restarting after completion re-scans the (now empty) area. Nothing may drop.
                    quarry.setRunning(true);
                    for (int tick = 0; tick < 40; tick++) {
                        tick(helper, quarry);
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getBlocksMined() == 0,
                            "the re-scan mined a phantom block: " + quarry.getBlocksMined());
                    helper.assertTrue(quarry.getCursor() == 1,
                            "the re-scan cursor was " + quarry.getCursor());
                    helper.assertItemEntityNotPresent(Items.RAW_IRON);
                })
                .thenSucceed();
    }

    /** Cursor, within-block progress and the derived drill index survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void progressSurvivesSaveAndLoad(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity original = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        fillStone(helper, 3, 3, 1);
        configure(original, 3, 3, 1);
        for (int tick = 0; tick < 5; tick++) {
            tick(helper, original);
        }
        int cursorBefore = original.getCursor();
        float workBefore = original.getWorkFraction();
        double drillBefore = original.getDrillIndex();
        helper.assertTrue(cursorBefore > 0, "the test did not mine anything before saving");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        QuarryBlockEntity restored = placeQuarry(helper, new BlockPos(2, Y, 8), Direction.EAST);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getCursor() == cursorBefore,
                "cursor restored as " + restored.getCursor() + " instead of " + cursorBefore);
        helper.assertTrue(Math.abs(restored.getWorkFraction() - workBefore) <= 1.0F / 64.0F,
                "work fraction restored as " + restored.getWorkFraction() + " instead of " + workBefore);
        helper.assertTrue(Math.abs(restored.getDrillIndex() - drillBefore) <= 1.0D / 64.0D,
                "drill index restored as " + restored.getDrillIndex() + " instead of " + drillBefore);
        helper.assertTrue(restored.getRangeWidth() == 3 && restored.getRangeDepth() == 3,
                "the range was not restored");
        helper.succeed();
    }

    /** The frame stays inside the configured area at its edges and layers. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void frameStaysInsideTheAreaAtEdges(GameTestHelper helper) {
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(4, Y, 4), Direction.WEST);
        // West-facing: the area starts at x=3 and extends to -Z (right of west is north).
        helper.setBlock(new BlockPos(3, Y, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(3, Y - 1, 4), Blocks.STONE);
        configure(quarry, 1, 1, 2);

        int baseY = helper.absolutePos(new BlockPos(0, Y, 0)).getY();
        for (int tick = 0; tick < 60; tick++) {
            tick(helper, quarry);
            double drill = quarry.getDrillIndex();
            int frame = MiningSync.frameIndex(drill, quarry.getCursor(), quarry.getVolume());
            BlockPos expected = quarry.targetFor(Math.min(frame, quarry.getVolume() - 1));
            helper.assertTrue(expected.getY() >= baseY - 1 && expected.getY() <= baseY,
                    "frame left the area vertically: " + expected);
        }
        helper.assertTrue(quarry.getBlocksMined() == 2,
                "the two edge blocks were not mined: " + quarry.getBlocksMined());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

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

    /** Fills the area in front of a quarry at (2, Y, 2) facing EAST with stone. */
    private static void fillStone(GameTestHelper helper, int width, int depth, int height) {
        for (int x = 3; x < 3 + depth; x++) {
            for (int z = 2; z < 2 + width; z++) {
                for (int y = Y; y > Y - height; y--) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static void configure(QuarryBlockEntity quarry, int width, int depth, int height) {
        quarry.setEnergyStored(20_000);
        quarry.applyRange(width, depth, height);
        quarry.setRunning(true);
    }

    private static void tick(GameTestHelper helper, QuarryBlockEntity quarry) {
        BlockState state = quarry.getBlockState();
        QuarryBlockEntity.serverTick(helper.getLevel(), quarry.getBlockPos(), state, quarry);
    }
}
