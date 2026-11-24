package com.industrialcore.storage;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Phase 96: Pure-Java unified industrial GUI layout for {@code quantum_importer} and
 * {@code quantum_exporter}.
 *
 * <p>Adheres strictly to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * 9-slot ghost filter grid, channel selector strip, telemetry readout, and dedicated Motor &amp;
 * Battery upgrade slots.</p>
 */
public final class QuantumIoLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196
    public static final int BODY_BOTTOM = 196;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM); // 304

    // Energy bar strictly below GuiMetrics.BODY_TOP (58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;

    // Frequency selector row (y = 82..96)
    public static final Rect BTN_CH_PREV = new Rect(12, 82, 14, 14);
    public static final Rect CHANNEL_BOX = new Rect(28, 82, 110, 14);
    public static final Rect BTN_CH_NEXT = new Rect(140, 82, 14, 14);
    public static final Rect BTN_CH_CLEAR = new Rect(156, 82, 28, 14);

    // 3x3 Ghost Filter Grid (left side)
    public static final int FILTER_LABEL_Y = 102;
    public static final Rect FILTER_GROUP = new Rect(12, 112, 56, 56);
    public static final int FILTER_GRID_X = 14;
    public static final int FILTER_GRID_Y = 114;
    public static final int FILTER_COLS = 3;
    public static final int FILTER_COUNT = QuantumNetLogic.FILTER_SLOTS; // 9

    // Filter mode & clear buttons (y = 174..188)
    public static final Rect BTN_FILTER_MODE = new Rect(12, 174, 82, 14);
    public static final Rect BTN_FILTER_CLEAR = new Rect(98, 174, 42, 14);

    // Telemetry card (center, y = 112..168)
    public static final Rect STATS_BOX = new Rect(74, 112, 66, 56);
    public static final int STATS_TEXT_X = 78;
    public static final int STATS_ROW_1_Y = 117;
    public static final int STATS_ROW_2_Y = 130;
    public static final int STATS_ROW_3_Y = 143;
    public static final int STATS_ROW_4_Y = 156;

    // Right upgrade column (Motor & Battery, Phase 87+ standard)
    public static final int UPGRADE_CENTER_X = 166;
    public static final int MOTOR_LABEL_Y = 106;
    public static final Rect MOTOR_SLOT = new Rect(158, 116, 16, 16);
    public static final int BATTERY_LABEL_Y = 142;
    public static final Rect BATTERY_SLOT = new Rect(158, 152, 16, 16);

    private QuantumIoLayout() {}

    public static Rect filterSlot(int index) {
        int col = index % FILTER_COLS;
        int row = index / FILTER_COLS;
        return new Rect(FILTER_GRID_X + col * 18, FILTER_GRID_Y + row * 18, 16, 16);
    }

    public static int filterSlotAt(double localX, double localY) {
        for (int i = 0; i < FILTER_COUNT; i++) {
            Rect r = filterSlot(i);
            if (localX >= r.x() - 1 && localX <= r.right() + 1
                    && localY >= r.y() - 1 && localY <= r.bottom() + 1) {
                return i;
            }
        }
        return -1;
    }

    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
