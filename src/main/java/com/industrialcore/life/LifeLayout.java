package com.industrialcore.life;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layouts for Phase 94 Branch D (Building &amp; Life) wave 1:
 * {@code auto_builder}, {@code fluid_pump}, and {@code wireless_charger}.
 *
 * <p>Strictly adheres to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * dedicated Motor &amp; Battery upgrade slots, and clean non-overlapping geometry.</p>
 */
public final class LifeLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196

    // Shared energy bar strictly below GuiMetrics.BODY_TOP (58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;

    // ------------------------------------------------------------------
    // 1. Auto Builder (progress + mode/toggle/reset + W/D/H + 3x3 materials + upgrades)
    // ------------------------------------------------------------------
    public static final int BUILDER_BODY_BOTTOM = 202;
    public static final int BUILDER_HEIGHT = GuiMetrics.height(BUILDER_BODY_BOTTOM); // 310
    public static final Rect BUILDER_PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 80, GuiMetrics.CONTENT, 6);
    public static final int BUILDER_INFO_Y = 89;

    public static final Rect BUILDER_BTN_TOGGLE = new Rect(12, 100, 52, 13);
    public static final Rect BUILDER_BTN_MODE = new Rect(68, 100, 68, 13);
    public static final Rect BUILDER_BTN_RESET = new Rect(140, 100, 44, 13);

    public static final int BUILDER_DIM_Y = 117;
    public static final int BUILDER_W_LABEL_X = 12;
    public static final Rect BUILDER_W_DEC = new Rect(22, 117, 12, 12);
    public static final int BUILDER_W_VAL_CX = 44;
    public static final Rect BUILDER_W_INC = new Rect(54, 117, 12, 12);

    public static final int BUILDER_D_LABEL_X = 70;
    public static final Rect BUILDER_D_DEC = new Rect(80, 117, 12, 12);
    public static final int BUILDER_D_VAL_CX = 102;
    public static final Rect BUILDER_D_INC = new Rect(112, 117, 12, 12);

    public static final int BUILDER_H_LABEL_X = 128;
    public static final Rect BUILDER_H_DEC = new Rect(138, 117, 12, 12);
    public static final int BUILDER_H_VAL_CX = 160;
    public static final Rect BUILDER_H_INC = new Rect(170, 117, 12, 12);

    public static final int BUILDER_MAT_COUNT = 9;
    public static final int BUILDER_MAT_LABEL_X = 14;
    public static final int BUILDER_MAT_LABEL_Y = 132;
    public static final Rect BUILDER_MAT_GROUP = new Rect(12, 141, 58, 56);

    public static final Rect BUILDER_STATS_BOX = new Rect(74, 141, 64, 56);
    public static final int BUILDER_STATS_TEXT_X = 78;
    public static final int BUILDER_STATS_ROW_1_Y = 146;
    public static final int BUILDER_STATS_ROW_2_Y = 160;
    public static final int BUILDER_STATS_ROW_3_Y = 174;

    public static final int BUILDER_UPGRADE_CENTER_X = 163;
    public static final int BUILDER_MOTOR_LABEL_Y = 135;
    public static final Rect BUILDER_MOTOR_SLOT = new Rect(155, 145, 16, 16);
    public static final int BUILDER_BATTERY_LABEL_Y = 167;
    public static final Rect BUILDER_BATTERY_SLOT = new Rect(155, 177, 16, 16);

    // ------------------------------------------------------------------
    // 2. Fluid Pump (tank gauge + stats + bucket fill slots + upgrades)
    // ------------------------------------------------------------------
    public static final int PUMP_BODY_BOTTOM = 176;
    public static final int PUMP_HEIGHT = GuiMetrics.height(PUMP_BODY_BOTTOM); // 284

    public static final Rect PUMP_TANK_BAR = new Rect(12, 82, 118, 16);
    public static final int PUMP_INFO_Y = 102;
    public static final int PUMP_FLUID_TEXT_Y = 114;

    public static final int PUMP_BUCKET_LABEL_Y = 128;
    public static final int PUMP_BUCKET_IN_LABEL_X = 14;
    public static final Rect PUMP_BUCKET_IN_GROUP = new Rect(18, 139, 22, 22);
    public static final Rect PUMP_BUCKET_IN_SLOT = new Rect(21, 142, 16, 16);

    public static final int PUMP_ARROW_X = 48;
    public static final int PUMP_ARROW_Y = 146;

    public static final int PUMP_BUCKET_OUT_LABEL_X = 72;
    public static final Rect PUMP_BUCKET_OUT_GROUP = new Rect(76, 139, 22, 22);
    public static final Rect PUMP_BUCKET_OUT_SLOT = new Rect(79, 142, 16, 16);

    public static final int PUMP_UPGRADE_CENTER_X = 158;
    public static final int PUMP_MOTOR_LABEL_Y = 92;
    public static final Rect PUMP_MOTOR_SLOT = new Rect(150, 102, 16, 16);
    public static final int PUMP_BATTERY_LABEL_Y = 128;
    public static final Rect PUMP_BATTERY_SLOT = new Rect(150, 138, 16, 16);

    // ------------------------------------------------------------------
    // 3. Wireless Charger (telemetry card + Motor/Battery)
    // ------------------------------------------------------------------
    public static final int CHARGER_BODY_BOTTOM = 166;
    public static final int CHARGER_HEIGHT = GuiMetrics.height(CHARGER_BODY_BOTTOM); // 274

    public static final Rect CHARGER_STATS_BOX = new Rect(12, 84, 118, 74);
    public static final int CHARGER_STATS_TEXT_X = 18;
    public static final int CHARGER_ROW_1_Y = 90;
    public static final int CHARGER_ROW_2_Y = 106;
    public static final int CHARGER_ROW_3_Y = 122;
    public static final int CHARGER_ROW_4_Y = 138;

    public static final int CHARGER_UPGRADE_CENTER_X = 160;
    public static final int CHARGER_MOTOR_LABEL_Y = 90;
    public static final Rect CHARGER_MOTOR_SLOT = new Rect(152, 100, 16, 16);
    public static final int CHARGER_BATTERY_LABEL_Y = 124;
    public static final Rect CHARGER_BATTERY_SLOT = new Rect(152, 134, 16, 16);

    private LifeLayout() {}

    /** 3x3 material slot inside the Auto Builder material bay (index 0..8). */
    public static Rect builderMaterialSlot(int index) {
        int row = index / 3;
        int col = index % 3;
        return new Rect(14 + col * 18, 143 + row * 18, 16, 16);
    }

    public static Rect builderPlayerSlot(int index) {
        return GuiMetrics.playerSlot(BUILDER_BODY_BOTTOM, index);
    }

    public static Rect pumpPlayerSlot(int index) {
        return GuiMetrics.playerSlot(PUMP_BODY_BOTTOM, index);
    }

    public static Rect chargerPlayerSlot(int index) {
        return GuiMetrics.playerSlot(CHARGER_BODY_BOTTOM, index);
    }
}
