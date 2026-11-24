package com.industrialcore.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 80: the fixed machine panel must fit every window, aspect ratio and GUI scale.
 *
 * <p>The fit is a pure function, so the rules can be pinned exactly: never enlarge, shrink only
 * when needed, keep the minimum usable scale, centre the result and keep it inside the screen.</p>
 */
class GuiFitTest {
    private static final int MARGIN = 6;

    @Test
    void normalWindowKeepsTheAuthoredSize() {
        // 1280x800 at GUI scale 2 is 640x400 logical: every panel still fits at 1.0.
        GuiFit fit = GuiFit.fit(640, 400, GuiMetrics.WIDTH, 370, MARGIN);
        assertEquals(1.0F, fit.scale(), 1.0E-4F);
        assertFalse(fit.shrunk());
        assertEquals((640 - GuiMetrics.WIDTH) / 2, fit.x());
        assertEquals((400 - 370) / 2, fit.y());
    }

    @Test
    void tallPanelShrinksOnAShortWindow() {
        // A 320x240 logical window cannot hold the 370 px panel; it has to shrink.
        GuiFit fit = GuiFit.fit(320, 240, GuiMetrics.WIDTH, 370, MARGIN);
        assertEquals((240 - 2 * MARGIN) / 370.0F, fit.scale(), 1.0E-3F);
        assertTrue(fit.shrunk());
        assertTrue(Math.round(370 * fit.scale()) <= 240, "the scaled panel must fit vertically");
        assertTrue(fit.y() >= 0);
    }

    @Test
    void ultraWideWindowDoesNotEnlargeOrDistort() {
        GuiFit fit = GuiFit.fit(1600, 400, GuiMetrics.WIDTH, 300, MARGIN);
        assertEquals(1.0F, fit.scale(), 1.0E-4F);
        assertEquals((1600 - GuiMetrics.WIDTH) / 2, fit.x(), "the panel stays centred");
    }

    @Test
    void narrowWindowUsesTheWidthLimit() {
        GuiFit fit = GuiFit.fit(150, 600, GuiMetrics.WIDTH, 250, MARGIN);
        assertEquals((150 - 2 * MARGIN) / (float) GuiMetrics.WIDTH, fit.scale(), 1.0E-3F);
        assertTrue(Math.round(GuiMetrics.WIDTH * fit.scale()) <= 150);
    }

    @Test
    void scaleNeverDropsBelowTheUsableMinimum() {
        GuiFit fit = GuiFit.fit(40, 40, GuiMetrics.WIDTH, 420, MARGIN);
        assertEquals(GuiFit.MIN_SCALE, fit.scale(), 1.0E-4F);
        // Centred as far as the screen allows; the transform never produces a negative origin.
        assertTrue(fit.x() >= 0);
        assertTrue(fit.y() >= 0);
    }

    @Test
    void everyShippedPanelFitsTheSmallestSupportedWindow() {
        // 320x240 logical is a 640x480 window at GUI scale 2: the smallest realistic target.
        int[] heights = {GuiMetrics.height(122), GuiMetrics.height(136), GuiMetrics.height(170),
                GuiMetrics.height(210), 400};
        for (int height : heights) {
            GuiFit fit = GuiFit.fit(320, 240, GuiMetrics.WIDTH, height, MARGIN);
            assertTrue(fit.scale() >= GuiFit.MIN_SCALE,
                    "height " + height + " fell below the usable scale");
            assertTrue(Math.round(GuiMetrics.WIDTH * fit.scale()) <= 320,
                    "height " + height + " is wider than the window");
            assertTrue(Math.round(height * fit.scale()) <= 240,
                    "height " + height + " is taller than the window");
        }
    }

    @Test
    void zeroOrNegativeScreenIsHandledWithoutDivisionByZero() {
        GuiFit fit = GuiFit.fit(0, 0, GuiMetrics.WIDTH, 300, MARGIN);
        assertEquals(GuiFit.MIN_SCALE, fit.scale(), 1.0E-4F);
        assertTrue(fit.x() >= 0 && fit.y() >= 0);
        GuiFit invalid = GuiFit.fit(640, 400, 0, 0, MARGIN);
        assertEquals(GuiFit.MAX_SCALE, invalid.scale(), 1.0E-4F);
    }
}
