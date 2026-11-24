package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the factory monitor panel (Phase 82).
 *
 * <p>The terminal has no item slots: it is a read-out, so the body is a column of label/value rows
 * on the shared {@link GuiMetrics} grid, a full-width FE gauge and one alert line. Heights are
 * multiples of {@link #ROW_STEP} so a longer translation can never push a row into the gauge.</p>
 *
 * <pre>
 *   y  58  Machines        n
 *   y  72  Working         n (p %)
 *   y  86  Idle / stopped  n / n
 *   y 100  Generators      running / total
 *   y 114  Pipes           n (blocked b)
 *   y 128  Output full     n
 *   y 148  FE gauge (7 px)
 *   y 158  stored / capacity (+trend)
 *   y 172  alert line
 *   y 198  body bottom
 * </pre>
 */
public final class MonitorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 198;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    /** First row and the distance between rows; every row is one 9 px text line. */
    public static final int FIRST_ROW_Y = 58;
    public static final int ROW_STEP = 14;
    public static final int ROW_COUNT = 6;
    /** Right edge of the value column, measured from the panel's left edge. */
    public static final int VALUE_RIGHT = WIDTH - GuiMetrics.SIDE;

    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 148, GuiMetrics.CONTENT, 7);
    public static final int ENERGY_TEXT_Y = 158;
    public static final int ALERT_TEXT_Y = 172;

    private MonitorLayout() {
    }

    /** Baseline top of row {@code index} (0-based). */
    public static int rowY(int index) {
        int safe = Math.max(0, Math.min(ROW_COUNT - 1, index));
        return FIRST_ROW_Y + safe * ROW_STEP;
    }
}
