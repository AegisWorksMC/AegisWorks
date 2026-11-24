package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 10: the wrench must change the face the player aimed at, not a random one. */
class PipeWrenchTargetTest {

    @Test
    void clickingAnArmEndSelectsThatArm() {
        // Block at 0,0,0 with the center at 0.5. Clicking near the east edge selects EAST even
        // though the geometric face of the block would be the top.
        assertEquals(PipeWrenchTarget.EAST,
                PipeWrenchTarget.sideFor(0.98D, 0.62D, 0.5D, 0, 0, 0, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.WEST,
                PipeWrenchTarget.sideFor(0.02D, 0.38D, 0.5D, 0, 0, 0, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.UP,
                PipeWrenchTarget.sideFor(0.5D, 0.97D, 0.44D, 0, 0, 0, PipeWrenchTarget.NORTH));
        assertEquals(PipeWrenchTarget.DOWN,
                PipeWrenchTarget.sideFor(0.5D, 0.03D, 0.6D, 0, 0, 0, PipeWrenchTarget.EAST));
        assertEquals(PipeWrenchTarget.NORTH,
                PipeWrenchTarget.sideFor(0.48D, 0.5D, 0.02D, 0, 0, 0, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.SOUTH,
                PipeWrenchTarget.sideFor(0.52D, 0.5D, 0.97D, 0, 0, 0, PipeWrenchTarget.UP));
    }

    @Test
    void clickingTheCoreUsesTheReportedBlockFace() {
        assertEquals(PipeWrenchTarget.EAST,
                PipeWrenchTarget.sideFor(0.5D, 0.5D, 0.5D, 0, 0, 0, PipeWrenchTarget.EAST));
        assertEquals(PipeWrenchTarget.SOUTH,
                PipeWrenchTarget.sideFor(0.52D, 0.48D, 0.54D, 0, 0, 0, PipeWrenchTarget.SOUTH));
        // Inside the default radius of 0.2 on every axis.
        assertEquals(PipeWrenchTarget.NORTH,
                PipeWrenchTarget.sideFor(0.65D, 0.55D, 0.35D, 0, 0, 0, PipeWrenchTarget.NORTH));
    }

    @Test
    void dominantAxisWinsForDiagonalClicks() {
        assertEquals(PipeWrenchTarget.EAST,
                PipeWrenchTarget.sideFor(0.9D, 0.7D, 0.6D, 0, 0, 0, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.UP,
                PipeWrenchTarget.sideFor(0.7D, 0.95D, 0.6D, 0, 0, 0, PipeWrenchTarget.EAST));
    }

    @Test
    void relativeCoordinatesWorkForAnyBlockPosition() {
        assertEquals(PipeWrenchTarget.EAST,
                PipeWrenchTarget.sideFor(100.97D, -20.55D, 55.5D, 100, -21, 55, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.SOUTH,
                PipeWrenchTarget.sideFor(-8.5D, 3.5D, -4.03D, -9, 3, -5, PipeWrenchTarget.UP));
    }

    @Test
    void brokenInputFallsBackToTheReportedFace() {
        assertEquals(PipeWrenchTarget.UP,
                PipeWrenchTarget.sideFor(Double.NaN, 0.5D, 0.5D, 0, 0, 0, PipeWrenchTarget.UP));
        assertEquals(PipeWrenchTarget.UP,
                PipeWrenchTarget.sideFor(0.5D, Double.NaN, 0.5D, 0, 0, 0, -7));
        assertEquals(PipeWrenchTarget.UP,
                PipeWrenchTarget.sideFor(0.5D, 0.5D, 0.5D, 0, 0, 0, 9));
    }

    @Test
    void sideTranslationKeysAreDefinedForAllDirections() {
        for (int direction = 0; direction < 6; direction++) {
            String key = PipeWrenchTarget.sideKey(direction);
            org.junit.jupiter.api.Assertions.assertTrue(key.startsWith("gui.industrialcore.side."), key);
        }
    }
}
