package com.industrialcore.trap;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layouts for Phase 92 Trap Branch wave 1:
 * {@code mob_fan}, {@code rotary_drill}, and {@code item_vacuum}.
 *
 * <p>Strictly adheres to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * dedicated Motor &amp; Battery upgrade slots, and clean spacing from all labels and player slots.</p>
 */
public final class TrapLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196

    // Shared energy & progress bars (strictly below GuiMetrics.BODY_TOP = 58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 81, GuiMetrics.CONTENT, 8);
    public static final int INFO_TEXT_Y = 92;

    // ------------------------------------------------------------------
    // 1. Mob Fan (telemetry card + Motor/Battery)
    // ------------------------------------------------------------------
    public static final int FAN_BODY_BOTTOM = 166;
    public static final int FAN_HEIGHT = GuiMetrics.height(FAN_BODY_BOTTOM); // 274
    public static final Rect FAN_STATS_BOX = new Rect(12, 95, 114, 64);
    public static final int FAN_STATS_TEXT_X = 18;
    public static final int FAN_ROW_1_Y = 101;
    public static final int FAN_ROW_2_Y = 115;
    public static final int FAN_ROW_3_Y = 129;
    public static final int FAN_ROW_4_Y = 143;
    public static final int FAN_UPGRADE_CENTER_X = 158;
    public static final int FAN_MOTOR_LABEL_Y = 98;
    public static final Rect FAN_MOTOR_SLOT = new Rect(150, 108, 16, 16);
    public static final int FAN_BATTERY_LABEL_Y = 130;
    public static final Rect FAN_BATTERY_SLOT = new Rect(150, 140, 16, 16);

    // ------------------------------------------------------------------
    // 2. Rotary Drill (left weapon slot + center telemetry card + right Motor/Battery)
    // ------------------------------------------------------------------
    public static final int DRILL_BODY_BOTTOM = 170;
    public static final int DRILL_HEIGHT = GuiMetrics.height(DRILL_BODY_BOTTOM); // 278
    public static final int DRILL_WEAPON_LABEL_X = 12;
    public static final int DRILL_WEAPON_LABEL_Y = 104;
    public static final Rect DRILL_WEAPON_SLOT = new Rect(16, 118, 16, 16);
    public static final Rect DRILL_WEAPON_GROUP = new Rect(13, 115, 22, 22);
    public static final Rect DRILL_STATS_BOX = new Rect(44, 95, 84, 66);
    public static final int DRILL_STATS_TEXT_X = 49;
    public static final int DRILL_ROW_1_Y = 101;
    public static final int DRILL_ROW_2_Y = 115;
    public static final int DRILL_ROW_3_Y = 129;
    public static final int DRILL_ROW_4_Y = 143;
    public static final int DRILL_UPGRADE_CENTER_X = 160;
    public static final int DRILL_MOTOR_LABEL_Y = 98;
    public static final Rect DRILL_MOTOR_SLOT = new Rect(152, 108, 16, 16);
    public static final int DRILL_BATTERY_LABEL_Y = 130;
    public static final Rect DRILL_BATTERY_SLOT = new Rect(152, 140, 16, 16);

    // ------------------------------------------------------------------
    // 3. Item Vacuum (4x3 output grid = 12 slots + right Motor/Battery)
    // ------------------------------------------------------------------
    public static final int VACUUM_BODY_BOTTOM = 176;
    public static final int VACUUM_HEIGHT = GuiMetrics.height(VACUUM_BODY_BOTTOM); // 284
    public static final int VACUUM_OUTPUT_LABEL_X = 18;
    public static final int VACUUM_OUTPUT_LABEL_Y = 104;
    public static final int VACUUM_OUTPUT_X = 18;
    public static final int VACUUM_OUTPUT_Y = 116;
    public static final int VACUUM_OUTPUT_STRIDE = 18;
    public static final int VACUUM_OUTPUT_COLS = 4;
    public static final int VACUUM_OUTPUT_ROWS = 3;
    public static final int VACUUM_OUTPUT_COUNT = VACUUM_OUTPUT_COLS * VACUUM_OUTPUT_ROWS; // 12
    public static final Rect VACUUM_OUTPUT_GROUP = new Rect(VACUUM_OUTPUT_X - 3, VACUUM_OUTPUT_Y - 3, 76, 58);
    public static final int VACUUM_UPGRADE_CENTER_X = 148;
    public static final int VACUUM_MOTOR_LABEL_Y = 104;
    public static final Rect VACUUM_MOTOR_SLOT = new Rect(VACUUM_UPGRADE_CENTER_X - 8, 116, 16, 16);
    public static final int VACUUM_BATTERY_LABEL_Y = 138;
    public static final Rect VACUUM_BATTERY_SLOT = new Rect(VACUUM_UPGRADE_CENTER_X - 8, 148, 16, 16);

    private TrapLayout() {}

    public static Rect fanPlayerSlot(int index) {
        return GuiMetrics.playerSlot(FAN_BODY_BOTTOM, index);
    }

    public static Rect drillPlayerSlot(int index) {
        return GuiMetrics.playerSlot(DRILL_BODY_BOTTOM, index);
    }

    public static Rect vacuumOutputSlot(int index) {
        int col = index % VACUUM_OUTPUT_COLS;
        int row = index / VACUUM_OUTPUT_COLS;
        return new Rect(VACUUM_OUTPUT_X + col * VACUUM_OUTPUT_STRIDE,
                VACUUM_OUTPUT_Y + row * VACUUM_OUTPUT_STRIDE, 16, 16);
    }

    public static Rect vacuumPlayerSlot(int index) {
        return GuiMetrics.playerSlot(VACUUM_BODY_BOTTOM, index);
    }
}
