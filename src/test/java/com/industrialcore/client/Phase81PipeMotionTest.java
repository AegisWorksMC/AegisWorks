package com.industrialcore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 81: the travelling pipe item must follow a continuous path and glide between frames. */
class Phase81PipeMotionTest {

    @Test
    void thePathStartsAndEndsOnTheFaces() {
        assertEquals(1.0F, PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, 0.0F), 1.0E-6F);
        assertEquals(0.0F, PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, 1.0F), 1.0E-6F);
    }

    @Test
    void aStraightRunStaysOnTheAxisAndMovesForward() {
        // entry 1.0 -> centre 0.5 -> exit 0.0 is a straight run through the block.
        float previous = Float.NaN;
        for (int step = 0; step <= 20; step++) {
            float value = PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, step / 20.0F);
            if (!Float.isNaN(previous)) {
                assertTrue(value <= previous + 1.0E-6F, "the item moved backwards at " + step);
            }
            previous = value;
        }
        assertEquals(0.5F, PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, 0.5F), 1.0E-6F);
    }

    @Test
    void aCornerTurnsSmoothlyInsteadOfSnappingAtTheCentre() {
        // Entry on +X, exit on +Z: both halves are symmetric around the centre point.
        float beforeCentre = PipeSmoothMotion.path(1.0F, 0.5F, 0.5F, 0.45F);
        float afterCentre = PipeSmoothMotion.path(1.0F, 0.5F, 0.5F, 0.55F);
        assertTrue(beforeCentre > 0.5F && afterCentre > 0.5F,
                "the corner left the block centre: " + beforeCentre + " / " + afterCentre);
        assertTrue(beforeCentre > afterCentre, "the item did not approach the centre");
    }

    @Test
    void progressIsClamped() {
        assertEquals(1.0F, PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, -3.0F), 1.0E-6F);
        assertEquals(0.0F, PipeSmoothMotion.path(1.0F, 0.5F, 0.0F, 4.0F), 1.0E-6F);
    }

    @Test
    void theBlendIsFrameRateIndependentAndBounded() {
        assertEquals(PipeSmoothMotion.FALLBACK_BLEND, PipeSmoothMotion.blend(0.0D), 1.0E-6F);
        assertEquals(PipeSmoothMotion.FALLBACK_BLEND, PipeSmoothMotion.blend(Double.NaN), 1.0E-6F);
        float one = PipeSmoothMotion.blend(1.0D);
        float two = PipeSmoothMotion.blend(2.0D);
        float quarter = PipeSmoothMotion.blend(0.25D);
        assertTrue(quarter > 0.0F && quarter < one, "a short frame must move less");
        assertTrue(one < two && two < 1.0F, "a long frame must close more of the gap");
        assertEquals(1.0F, PipeSmoothMotion.blend(1_000.0D), 1.0E-6F);
    }

    @Test
    void approachGlidesAndConverges() {
        float value = 0.0F;
        // 60 frames at 1/60 tick each is only one tick of game time, so the filter needs a few
        // hundred frames (about 5 ticks) to close the gap to a fraction of a percent.
        for (int frame = 0; frame < 600; frame++) {
            value = PipeSmoothMotion.approach(value, 1.0F, 1.0D / 60.0D);
        }
        assertEquals(1.0F, value, 1.0E-2F);
        // A jump from one block to the next is smoothed, not teleported.
        float firstFrame = PipeSmoothMotion.approach(0.0F, 1.0F, 1.0D / 60.0D);
        assertTrue(firstFrame > 0.0F && firstFrame < 0.2F,
                "a single 60 fps frame moved " + firstFrame + " of the gap");
    }

    @Test
    void brokenValuesSnapInsteadOfPoisoningThePosition() {
        assertEquals(0.75F, PipeSmoothMotion.approach(Float.NaN, 0.75F, 1.0D), 1.0E-6F);
        assertEquals(0.25F, PipeSmoothMotion.approach(0.25F, Float.NaN, 1.0D), 1.0E-6F);
    }
}
