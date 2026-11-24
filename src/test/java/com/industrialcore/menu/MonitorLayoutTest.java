package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 82: the monitor rows, gauge and alert line must never overlap or leave the panel. */
class MonitorLayoutTest {

    @Test
    void everyRowFitsInsideTheContentColumn() {
        for (int index = 0; index < MonitorLayout.ROW_COUNT; index++) {
            int y = MonitorLayout.rowY(index);
            assertTrue(y >= GuiMetrics.BODY_TOP, "row " + index + " starts in the chrome");
            assertTrue(y + 9 <= MonitorLayout.ENERGY_BAR.y(),
                    "row " + index + " runs into the FE gauge");
        }
        assertEquals(MonitorLayout.FIRST_ROW_Y, MonitorLayout.rowY(0));
        assertEquals(MonitorLayout.FIRST_ROW_Y + MonitorLayout.ROW_STEP,
                MonitorLayout.rowY(1));
    }

    @Test
    void outOfRangeRowsClampInsteadOfLeavingThePanel() {
        assertEquals(MonitorLayout.rowY(0), MonitorLayout.rowY(-3));
        assertEquals(MonitorLayout.rowY(MonitorLayout.ROW_COUNT - 1), MonitorLayout.rowY(99));
    }

    @Test
    void theGaugeAndTheTextFitBelowTheRows() {
        Rect bar = MonitorLayout.ENERGY_BAR;
        assertFalse(bar.intersects(new Rect(GuiMetrics.SIDE, MonitorLayout.ENERGY_TEXT_Y,
                GuiMetrics.CONTENT, 9)), "the FE text sits on the bar");
        assertTrue(MonitorLayout.ENERGY_TEXT_Y + 9 <= MonitorLayout.ALERT_TEXT_Y,
                "energy text and alert line overlap");
        assertTrue(MonitorLayout.ALERT_TEXT_Y + 9 <= MonitorLayout.BODY_BOTTOM,
                "the alert line leaves the body");
        assertTrue(bar.right() <= MonitorLayout.WIDTH, "the gauge exceeds the panel");
    }

    @Test
    void thePanelFollowsTheSharedGrid() {
        assertEquals(GuiMetrics.WIDTH, MonitorLayout.WIDTH);
        assertEquals(GuiMetrics.height(MonitorLayout.BODY_BOTTOM), MonitorLayout.HEIGHT);
        assertTrue(MonitorLayout.BODY_BOTTOM < MonitorLayout.HEIGHT,
                "the body must end before the panel bottom");
    }
}
