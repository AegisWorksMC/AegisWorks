package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The face marker is drawn by a block entity renderer now, so what can be tested headless is the
 * mapping from a wrench mode to a visible marker and the collar geometry the renderer uses.
 */
class PipeMarkerStyleTest {
    @Test
    void theThreeConfiguredModesAreVisiblyDifferent() {
        int pull = PipeMarkerStyle.color(PipeConnectionMode.PULL);
        int push = PipeMarkerStyle.color(PipeConnectionMode.PUSH);
        int closed = PipeMarkerStyle.color(PipeConnectionMode.NONE);
        assertNotEquals(0, pull, "PULL must be marked");
        assertNotEquals(0, push, "PUSH must be marked");
        assertNotEquals(0, closed, "a closed face must be marked");
        assertNotEquals(pull, push, "input and output must not share a colour");
        assertNotEquals(pull, closed, "input and a closed face must not share a colour");
        assertNotEquals(push, closed, "output and a closed face must not share a colour");
    }

    @Test
    void aNormalFaceStaysUnmarked() {
        assertEquals(0, PipeMarkerStyle.color(PipeConnectionMode.NORMAL),
                "a plain route must not be painted");
        assertFalse(PipeMarkerStyle.isMarked(PipeConnectionMode.NORMAL));
    }

    @Test
    void unknownModesFallBackToNoMarker() {
        assertEquals(0, PipeMarkerStyle.color(null));
        assertFalse(PipeMarkerStyle.isMarked(null));
    }

    @Test
    void theCollarWrapsTheArmWithoutLeavingTheBlock() {
        // The arm is 6 px wide (0.3125 .. 0.6875); the collar must be wider so it is visible from
        // the side, and it must stay inside the block so it never clips into the neighbour.
        assertTrue(PipeMarkerStyle.COLLAR_HALF > 0.1875F,
                "the collar must be wider than the arm's half width");
        assertTrue(PipeMarkerStyle.COLLAR_HALF <= 0.25F,
                "the collar must stay inside the block");
        assertTrue(PipeMarkerStyle.COLLAR_NEAR > 0.0F,
                "the collar must not share a plane with the block boundary (z-fighting)");
        assertTrue(PipeMarkerStyle.COLLAR_FAR > PipeMarkerStyle.COLLAR_NEAR,
                "the collar needs a thickness");
        assertTrue(PipeMarkerStyle.COLLAR_FAR < 0.5F,
                "the collar must not reach the block centre");
    }
}
