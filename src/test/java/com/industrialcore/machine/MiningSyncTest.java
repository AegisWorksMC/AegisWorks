package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9: the quarry frame and the real mining progress must come from one model.
 *
 * <p>These tests pin the properties the fix relies on:</p>
 * <ul>
 *   <li>the visual interval shrinks with speed so the drawn position cannot trail far,</li>
 *   <li>the drill index advances continuously and only crosses an integer when a position is
 *       processed,</li>
 *   <li>the frame is derived from the same index and can never be ahead of the server,</li>
 *   <li>restarting an area never sweeps the head backwards across the old area.</li>
 * </ul>
 */
class MiningSyncTest {

    @Test
    void slowMachineKeepsTheFactoryWideInterval() {
        // 0.25 positions/tick covers 2.5 positions in 10 ticks, well inside the bound.
        assertEquals(10, MiningSync.syncInterval(10, 0.25D, 4));
        // Faster machines shorten it only as far as the bound requires.
        assertEquals(8, MiningSync.syncInterval(10, 0.5D, 4));
        assertEquals(4, MiningSync.syncInterval(10, 1.0D, 4));
    }

    @Test
    void fastMachineShortensTheIntervalSoTheFrameStaysClose() {
        // 2 positions per tick may cover at most 4 per update -> every 2 ticks.
        assertEquals(2, MiningSync.syncInterval(10, 2.0D, 4));
        // 8 positions per tick must sync every tick.
        assertEquals(1, MiningSync.syncInterval(10, 8.0D, 4));
        // A bigger bound relaxes the interval again, but never beyond the factory interval.
        assertEquals(5, MiningSync.syncInterval(10, 2.0D, 10));
        assertEquals(10, MiningSync.syncInterval(10, 2.0D, 40));
    }

    @Test
    void intervalIsAlwaysAtLeastOneTickForBrokenInput() {
        assertEquals(1, MiningSync.syncInterval(1, 0.0D, 4));
        assertEquals(10, MiningSync.syncInterval(10, 0.0D, 4));
        assertEquals(10, MiningSync.syncInterval(10, Double.NaN, 4));
        assertEquals(10, MiningSync.syncInterval(10, Double.POSITIVE_INFINITY, 4));
        assertEquals(10, MiningSync.syncInterval(10, -5.0D, 4));
        // A zero base interval still means "every tick" and a zero bound is raised to one.
        assertEquals(1, MiningSync.syncInterval(0, 8.0D, 0));
    }

    @Test
    void drillIndexAdvancesContinuouslyWithinOneBlock() {
        // Four ticks at 0.25 ops: 0.25, 0.5, 0.75 then a full block is processed.
        assertEquals(3.25D, MiningSync.drillIndex(3, 0.25D), 1.0E-9D);
        assertEquals(3.5D, MiningSync.drillIndex(3, 0.5D), 1.0E-9D);
        assertEquals(3.75D, MiningSync.drillIndex(3, 0.75D), 1.0E-9D);
        // The next position after the block was processed, without a backwards jump.
        assertEquals(4.0D, MiningSync.drillIndex(4, 0.0D), 1.0E-9D);
    }

    @Test
    void drillIndexClampsBrokenFractions() {
        assertEquals(7.0D, MiningSync.drillIndex(7, -3.0D), 1.0E-9D);
        assertEquals(8.0D, MiningSync.drillIndex(7, 42.0D), 1.0E-9D);
        assertEquals(7.0D, MiningSync.drillIndex(7, Double.NaN), 1.0E-9D);
        assertEquals(0.0D, MiningSync.drillIndex(-4, 0.5D), 1.0E-9D);
    }

    @Test
    void frameAndDrillShareTheSameProgress() {
        // Frame sits on the position the drill is working on, never beyond the confirmed cursor.
        assertEquals(3, MiningSync.frameIndex(MiningSync.drillIndex(3, 0.9D), 3, 100));
        assertEquals(3, MiningSync.frameIndex(MiningSync.drillIndex(3, 0.0D), 3, 100));
        // The frame only changes when the cursor does, i.e. when a position was processed.
        assertEquals(4, MiningSync.frameIndex(MiningSync.drillIndex(4, 0.1D), 4, 100));
        assertEquals(3, MiningSync.frameIndex(3.99D, 3, 100));
    }

    @Test
    void frameIsClampedToTheKnownVolume() {
        assertEquals(0, MiningSync.frameIndex(-12.0D, 0, 10));
        assertEquals(9, MiningSync.frameIndex(500.0D, 500, 10));
        // A broken index falls back to the confirmed cursor instead of jumping to zero.
        assertEquals(3, MiningSync.frameIndex(Double.NaN, 3, 10));
        assertEquals(0, MiningSync.frameIndex(5.0D, 5, 0));
    }

    @Test
    void interpolationNeverShowsUnconfirmedProgress() {
        assertEquals(10.0D, MiningSync.interpolate(10.0D, 14.0D, 0.0F), 1.0E-9D);
        assertEquals(12.0D, MiningSync.interpolate(10.0D, 14.0D, 0.5F), 1.0E-9D);
        assertEquals(14.0D, MiningSync.interpolate(10.0D, 14.0D, 1.0F), 1.0E-9D);
        assertEquals(14.0D, MiningSync.interpolate(10.0D, 14.0D, 9.0F), 1.0E-9D);
        assertTrue(MiningSync.interpolate(10.0D, 14.0D, 0.5F) <= 14.0D);
    }

    @Test
    void restartingAnAreaDoesNotSweepBackwards() {
        // A new area resets the cursor from 900 to 0: the head must appear at 0 immediately.
        assertEquals(0.0D, MiningSync.interpolate(900.0D, 0.0D, 0.0F), 1.0E-9D);
        assertEquals(0.0D, MiningSync.interpolate(900.0D, 0.0D, 0.5F), 1.0E-9D);
    }

    @Test
    void brokenInterpolationInputIsTolerated() {
        assertEquals(5.0D, MiningSync.interpolate(Double.NaN, 5.0D, 0.5F), 1.0E-9D);
        assertEquals(5.0D, MiningSync.interpolate(5.0D, Double.NaN, 0.5F), 1.0E-9D);
        assertEquals(5.0D, MiningSync.interpolate(5.0D, 5.0D, Float.NaN), 1.0E-9D);
    }

    /**
     * At every speed the drawn frame stays within {@code maxBlocksPerUpdate} positions of the
     * server, which is what "the frame and the mined blocks move at the same rate" means in
     * practice. The old fixed 10 tick interval covered up to 80 positions at 8 ops/tick.
     */
    @Test
    void frameNeverTrailsFurtherThanTheConfiguredBound() {
        double[] speeds = {0.25D, 0.5D, 1.0D, 2.0D, 5.0D, 8.0D};
        int bound = MiningSync.DEFAULT_MAX_BLOCKS_PER_UPDATE;
        int factoryInterval = 10;
        for (double speed : speeds) {
            int interval = MiningSync.syncInterval(factoryInterval, speed, bound);
            double coverage = speed * interval;
            assertTrue(coverage <= bound + 1.0E-9D || interval == 1,
                    "speed " + speed + " covers " + coverage + " positions per update");
            assertTrue(interval <= factoryInterval, "interval grew beyond the factory interval");
        }
    }

    /**
     * Simulates the old failure: with a fixed interval and a fast machine the frame (newest
     * cursor) was dozens of blocks ahead of the drawn head. With the shared index the distance is
     * bounded by the interpolation window instead.
     */
    @Test
    void frameAndHeadStayTogetherAtHighMotorCounts() {
        int cursor = 0;
        double work = 0.0D;
        int bound = MiningSync.DEFAULT_MAX_BLOCKS_PER_UPDATE;
        for (int tick = 0; tick < 200; tick++) {
            work += 8.0D;
            int processed = (int) Math.floor(work);
            work -= processed;
            cursor += processed;
            double index = MiningSync.drillIndex(cursor, work);
            int frame = MiningSync.frameIndex(index, cursor, 100_000);
            assertTrue(frame <= cursor, "frame overtook the confirmed cursor");
            assertTrue(cursor - frame <= 1, "frame separated from the drill position");
            int interval = MiningSync.syncInterval(10, 8.0D, bound);
            assertTrue(interval * 8.0D <= bound + 8.0D);
        }
        assertFalse(cursor == 0);
    }
}
