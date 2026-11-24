package com.industrialcore.agri;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layout for the Phase 90 Agriculture &amp; Forestry machines
 * ({@code crop_harvester}, {@code tree_harvester}, {@code auto_fisher}).
 *
 * <p>Follows {@link GuiMetrics} (196px width, body starting at y=58) and Phase 87 conventions:
 * zero 6-face buttons, left input/rod column, centered 3x3 (9-slot) output grid, dedicated right
 * Motor &amp; Battery upgrade column, and standard player inventory below.</p>
 */
public final class AgriLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196
    public static final int BODY_BOTTOM = 176;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM); // 284

    // Energy & progress bars (strictly below GuiMetrics.BODY_TOP = 58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 81, GuiMetrics.CONTENT, 8);
    public static final int INFO_TEXT_Y = 92;

    // Left input column (3 vertical seed/sapling slots; 1 centered fishing rod slot for fisher)
    public static final int INPUT_LABEL_X = 14;
    public static final int INPUT_LABEL_Y = 104;
    public static final int INPUT_X = 18;
    public static final int INPUT_Y = 116;
    public static final int INPUT_STRIDE = 18;
    public static final Rect INPUT_GROUP_3 = new Rect(INPUT_X - 3, INPUT_Y - 3, 22, 58);

    public static final int ROD_LABEL_X = 14;
    public static final int ROD_LABEL_Y = 120;
    public static final Rect ROD_SLOT = new Rect(18, 134, 16, 16);
    public static final Rect ROD_GROUP = new Rect(ROD_SLOT.x() - 3, ROD_SLOT.y() - 3, 22, 22);

    // Center 3x3 output grid (9 slots)
    public static final int OUTPUT_LABEL_X = 60;
    public static final int OUTPUT_LABEL_Y = 104;
    public static final int OUTPUT_X = 60;
    public static final int OUTPUT_Y = 116;
    public static final int OUTPUT_COLS = 3;
    public static final int OUTPUT_ROWS = 3;
    public static final int OUTPUT_COUNT = OUTPUT_COLS * OUTPUT_ROWS; // 9
    public static final Rect OUTPUT_GROUP = new Rect(OUTPUT_X - 3, OUTPUT_Y - 3, 58, 58);

    // Right Motor & Battery upgrade column (strictly separated from 3x3 output grid)
    public static final int UPGRADE_CENTER_X = 156;
    public static final int MOTOR_LABEL_Y = 104;
    public static final Rect MOTOR_SLOT = new Rect(UPGRADE_CENTER_X - 8, 116, 16, 16);
    public static final int BATTERY_LABEL_Y = 138;
    public static final Rect BATTERY_SLOT = new Rect(UPGRADE_CENTER_X - 8, 148, 16, 16);

    private AgriLayout() {}

    /** One of the 3 vertical input slots (seeds or saplings) for crop/tree harvesters. */
    public static Rect inputSlot3(int index) {
        return new Rect(INPUT_X, INPUT_Y + index * INPUT_STRIDE, 16, 16);
    }

    /** One of the 9 output slots in the center 3x3 grid. */
    public static Rect outputSlot(int index) {
        int col = index % OUTPUT_COLS;
        int row = index / OUTPUT_COLS;
        return new Rect(OUTPUT_X + col * INPUT_STRIDE, OUTPUT_Y + row * INPUT_STRIDE, 16, 16);
    }

    /** Player inventory slot (0..35) using shared {@link GuiMetrics}. */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
