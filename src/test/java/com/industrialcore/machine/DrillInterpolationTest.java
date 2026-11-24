package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrillInterpolationTest {
    @Test
    void progressIsCompleteWhenUpdatesArriveEveryTick() {
        assertEquals(1.0F, DrillInterpolation.progress(3L, 0.4F, 1));
    }

    @Test
    void progressRampsFromZeroToOneOverOneInterval() {
        assertEquals(0.0F, DrillInterpolation.progress(0L, 0.0F, 10));
        assertEquals(0.5F, DrillInterpolation.progress(5L, 0.0F, 10));
        assertEquals(0.55F, DrillInterpolation.progress(5L, 0.5F, 10), 1.0E-6F);
        assertEquals(1.0F, DrillInterpolation.progress(10L, 0.0F, 10));
    }

    @Test
    void progressIsClampedForStaleOrBrokenInput() {
        assertEquals(0.0F, DrillInterpolation.progress(-5L, 0.0F, 10));
        assertEquals(1.0F, DrillInterpolation.progress(500L, 0.0F, 10));
        assertEquals(0.0F, DrillInterpolation.progress(0L, Float.NaN, 10));
        // A partial tick larger than 1 is clamped instead of overshooting the interval.
        assertEquals(0.1F, DrillInterpolation.progress(0L, 7.0F, 10), 1.0E-6F);
    }

    @Test
    void blendStaysBetweenTheTwoConfirmedPositions() {
        assertEquals(10.0D, DrillInterpolation.blend(10, 20, 0.0F));
        assertEquals(15.0D, DrillInterpolation.blend(10, 20, 0.5F));
        assertEquals(20.0D, DrillInterpolation.blend(10, 20, 1.0F));
        assertEquals(1_020.0D, DrillInterpolation.blend(1_000, 1_040, 0.5F));
    }

    @Test
    void blendHandlesLargeJumpsAndReversedCursors() {
        assertEquals(1_000.0D, DrillInterpolation.blend(1_000, 1_040, 0.0F));
        assertEquals(1_020.0D, DrillInterpolation.blend(1_000, 1_040, 0.5F));
        assertEquals(1_040.0D, DrillInterpolation.blend(1_040, 1_040, 0.5F));
        // A restarted area (cursor moved backwards) still renders the confirmed value.
        assertTrue(DrillInterpolation.blend(500, 0, 0.5F) >= 0.0D);
    }
}
