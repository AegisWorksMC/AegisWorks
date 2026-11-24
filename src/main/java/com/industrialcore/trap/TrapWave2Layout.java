package com.industrialcore.trap;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layouts for Phase 93 Trap Branch wave 2:
 * {@code xp_collector}, {@code spawn_suppressor}, {@code laser_turret}, and {@code mob_duplicator}.
 *
 * <p>Strictly adheres to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * dedicated Motor &amp; Battery upgrade slots, and clean non-overlapping geometry.</p>
 */
public final class TrapWave2Layout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196

    // Shared energy & progress bars (strictly below GuiMetrics.BODY_TOP = 58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 81, GuiMetrics.CONTENT, 8);

    // ------------------------------------------------------------------
    // 1. XP Collector (+1/+10/All buttons + bottle input/3x output + upgrades)
    // ------------------------------------------------------------------
    public static final int XP_BODY_BOTTOM = 190;
    public static final int XP_HEIGHT = GuiMetrics.height(XP_BODY_BOTTOM); // 298
    public static final int XP_INFO_TEXT_Y = 92;

    public static final Rect XP_BTN_1LV = new Rect(12, 104, 54, 14);
    public static final Rect XP_BTN_10LV = new Rect(71, 104, 54, 14);
    public static final Rect XP_BTN_ALL = new Rect(130, 104, 54, 14);

    public static final int XP_INPUT_LABEL_X = 14;
    public static final int XP_INPUT_LABEL_Y = 124;
    public static final Rect XP_INPUT_GROUP = new Rect(18, 138, 22, 22);
    public static final Rect XP_INPUT_SLOT = new Rect(21, 141, 16, 16);

    public static final int XP_OUTPUT_LABEL_X = 58;
    public static final int XP_OUTPUT_LABEL_Y = 124;
    public static final int XP_OUTPUT_COUNT = 3;
    public static final Rect XP_OUTPUT_GROUP = new Rect(58, 138, 60, 22);

    public static final int XP_BOTTLED_TEXT_Y = 168;
    public static final int XP_UPGRADE_CENTER_X = 156;
    public static final int XP_MOTOR_LABEL_Y = 122;
    public static final Rect XP_MOTOR_SLOT = new Rect(148, 132, 16, 16);
    public static final int XP_BATTERY_LABEL_Y = 153;
    public static final Rect XP_BATTERY_SLOT = new Rect(148, 163, 16, 16);

    // ------------------------------------------------------------------
    // 2. Spawn Suppressor (telemetry card + Motor/Battery)
    // ------------------------------------------------------------------
    public static final int SUP_BODY_BOTTOM = 166;
    public static final int SUP_HEIGHT = GuiMetrics.height(SUP_BODY_BOTTOM); // 274
    public static final Rect SUP_STATS_BOX = new Rect(12, 95, 114, 64);
    public static final int SUP_STATS_TEXT_X = 18;
    public static final int SUP_ROW_1_Y = 101;
    public static final int SUP_ROW_2_Y = 115;
    public static final int SUP_ROW_3_Y = 129;
    public static final int SUP_ROW_4_Y = 143;
    public static final int SUP_UPGRADE_CENTER_X = 158;
    public static final int SUP_MOTOR_LABEL_Y = 98;
    public static final Rect SUP_MOTOR_SLOT = new Rect(150, 108, 16, 16);
    public static final int SUP_BATTERY_LABEL_Y = 130;
    public static final Rect SUP_BATTERY_SLOT = new Rect(150, 140, 16, 16);

    // ------------------------------------------------------------------
    // 3. Laser Turret (telemetry card + Motor/Battery)
    // ------------------------------------------------------------------
    public static final int TUR_BODY_BOTTOM = 166;
    public static final int TUR_HEIGHT = GuiMetrics.height(TUR_BODY_BOTTOM); // 274
    public static final Rect TUR_STATS_BOX = new Rect(12, 95, 114, 64);
    public static final int TUR_STATS_TEXT_X = 18;
    public static final int TUR_ROW_1_Y = 101;
    public static final int TUR_ROW_2_Y = 115;
    public static final int TUR_ROW_3_Y = 129;
    public static final int TUR_ROW_4_Y = 143;
    public static final int TUR_UPGRADE_CENTER_X = 158;
    public static final int TUR_MOTOR_LABEL_Y = 98;
    public static final Rect TUR_MOTOR_SLOT = new Rect(150, 108, 16, 16);
    public static final int TUR_BATTERY_LABEL_Y = 130;
    public static final Rect TUR_BATTERY_SLOT = new Rect(150, 140, 16, 16);

    // ------------------------------------------------------------------
    // 4. Mob Duplicator (left capsule slot + center stats + right upgrades)
    // ------------------------------------------------------------------
    public static final int DUP_BODY_BOTTOM = 172;
    public static final int DUP_HEIGHT = GuiMetrics.height(DUP_BODY_BOTTOM); // 280
    public static final int DUP_CAPSULE_LABEL_X = 12;
    public static final int DUP_CAPSULE_LABEL_Y = 104;
    public static final Rect DUP_CAPSULE_GROUP = new Rect(13, 115, 22, 22);
    public static final Rect DUP_CAPSULE_SLOT = new Rect(16, 118, 16, 16);
    public static final Rect DUP_STATS_BOX = new Rect(44, 95, 86, 68);
    public static final int DUP_STATS_TEXT_X = 49;
    public static final int DUP_ROW_1_Y = 101;
    public static final int DUP_ROW_2_Y = 116;
    public static final int DUP_ROW_3_Y = 131;
    public static final int DUP_ROW_4_Y = 146;
    public static final int DUP_UPGRADE_CENTER_X = 160;
    public static final int DUP_MOTOR_LABEL_Y = 98;
    public static final Rect DUP_MOTOR_SLOT = new Rect(152, 108, 16, 16);
    public static final int DUP_BATTERY_LABEL_Y = 130;
    public static final Rect DUP_BATTERY_SLOT = new Rect(152, 140, 16, 16);

    private TrapWave2Layout() {}

    public static Rect xpOutputSlot(int index) {
        return new Rect(61 + index * 18, 141, 16, 16);
    }

    public static Rect xpPlayerSlot(int index) {
        return GuiMetrics.playerSlot(XP_BODY_BOTTOM, index);
    }

    public static Rect supPlayerSlot(int index) {
        return GuiMetrics.playerSlot(SUP_BODY_BOTTOM, index);
    }

    public static Rect turPlayerSlot(int index) {
        return GuiMetrics.playerSlot(TUR_BODY_BOTTOM, index);
    }

    public static Rect dupPlayerSlot(int index) {
        return GuiMetrics.playerSlot(DUP_BODY_BOTTOM, index);
    }
}
