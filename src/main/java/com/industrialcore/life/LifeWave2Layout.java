package com.industrialcore.life;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layouts for Phase 95 Branch D (Building &amp; Life) wave 2:
 * {@code chunk_loader}, {@code climate_controller}, and {@code warp_pad}.
 *
 * <p>Strictly adheres to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * dedicated Motor &amp; Battery upgrade slots, and clean non-overlapping geometry.</p>
 */
public final class LifeWave2Layout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196

    // Shared energy bar strictly below GuiMetrics.BODY_TOP (58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;

    // ------------------------------------------------------------------
    // 1. Chunk Loader (ON/OFF + range -/+ + telemetry card + upgrades)
    // ------------------------------------------------------------------
    public static final int CHUNK_BODY_BOTTOM = 176;
    public static final int CHUNK_HEIGHT = GuiMetrics.height(CHUNK_BODY_BOTTOM); // 284

    public static final Rect CHUNK_BTN_TOGGLE = new Rect(12, 82, 58, 14);
    public static final Rect CHUNK_BTN_R_DEC = new Rect(74, 82, 14, 14);
    public static final int CHUNK_R_VAL_CX = 102;
    public static final Rect CHUNK_BTN_R_INC = new Rect(116, 82, 14, 14);

    public static final Rect CHUNK_STATS_BOX = new Rect(12, 102, 118, 66);
    public static final int CHUNK_STATS_TEXT_X = 18;
    public static final int CHUNK_ROW_1_Y = 108;
    public static final int CHUNK_ROW_2_Y = 123;
    public static final int CHUNK_ROW_3_Y = 138;
    public static final int CHUNK_ROW_4_Y = 153;

    public static final int CHUNK_UPGRADE_CENTER_X = 160;
    public static final int CHUNK_MOTOR_LABEL_Y = 92;
    public static final Rect CHUNK_MOTOR_SLOT = new Rect(152, 102, 16, 16);
    public static final int CHUNK_BATTERY_LABEL_Y = 128;
    public static final Rect CHUNK_BATTERY_SLOT = new Rect(152, 138, 16, 16);

    // ------------------------------------------------------------------
    // 2. Climate Controller (weather 3 btns + time 2 btns + lock 2 toggles + info + upgrades)
    // ------------------------------------------------------------------
    public static final int CLIMATE_BODY_BOTTOM = 192;
    public static final int CLIMATE_HEIGHT = GuiMetrics.height(CLIMATE_BODY_BOTTOM); // 300
    public static final int CLIMATE_STATUS_LINE_Y = 81;

    public static final Rect CLIMATE_BTN_CLEAR = new Rect(12, 93, 38, 13);
    public static final Rect CLIMATE_BTN_RAIN = new Rect(52, 93, 38, 13);
    public static final Rect CLIMATE_BTN_THUNDER = new Rect(92, 93, 38, 13);

    public static final Rect CLIMATE_BTN_DAY = new Rect(12, 110, 58, 13);
    public static final Rect CLIMATE_BTN_NIGHT = new Rect(72, 110, 58, 13);

    public static final Rect CLIMATE_BTN_LOCK_CLEAR = new Rect(12, 127, 58, 13);
    public static final Rect CLIMATE_BTN_LOCK_DAY = new Rect(72, 127, 58, 13);

    public static final Rect CLIMATE_INFO_BOX = new Rect(12, 145, 118, 41);
    public static final int CLIMATE_INFO_TEXT_X = 17;
    public static final int CLIMATE_INFO_ROW_1_Y = 151;
    public static final int CLIMATE_INFO_ROW_2_Y = 163;
    public static final int CLIMATE_INFO_ROW_3_Y = 174;

    public static final int CLIMATE_UPGRADE_CENTER_X = 160;
    public static final int CLIMATE_MOTOR_LABEL_Y = 98;
    public static final Rect CLIMATE_MOTOR_SLOT = new Rect(152, 108, 16, 16);
    public static final int CLIMATE_BATTERY_LABEL_Y = 136;
    public static final Rect CLIMATE_BATTERY_SLOT = new Rect(152, 146, 16, 16);

    // ------------------------------------------------------------------
    // 3. Warp Pad (name edit + save + public toggle + scrollable list + warp btn + upgrades)
    // ------------------------------------------------------------------
    public static final int WARP_BODY_BOTTOM = 212;
    public static final int WARP_HEIGHT = GuiMetrics.height(WARP_BODY_BOTTOM); // 320

    public static final Rect WARP_NAME_BOX = new Rect(12, 81, 90, 14);
    public static final Rect WARP_BTN_SAVE = new Rect(105, 81, 35, 14);
    public static final Rect WARP_BTN_PUBLIC = new Rect(143, 81, 41, 14);

    public static final int WARP_VISIBLE_ROWS = 4;
    public static final int WARP_ROW_HEIGHT = 21;
    public static final Rect WARP_LIST_BOX = new Rect(12, 99, 118, WARP_VISIBLE_ROWS * WARP_ROW_HEIGHT); // y=99..183
    public static final Rect WARP_BTN_SCROLL_UP = new Rect(132, 99, 12, 14);
    public static final Rect WARP_BTN_SCROLL_DOWN = new Rect(132, 169, 12, 14);
    public static final Rect WARP_BTN_WARP = new Rect(12, 188, 132, 15);

    public static final int WARP_UPGRADE_CENTER_X = 167;
    public static final int WARP_MOTOR_LABEL_Y = 104;
    public static final Rect WARP_MOTOR_SLOT = new Rect(159, 114, 16, 16);
    public static final int WARP_BATTERY_LABEL_Y = 140;
    public static final Rect WARP_BATTERY_SLOT = new Rect(159, 150, 16, 16);

    private LifeWave2Layout() {}

    public static Rect warpListRow(int visibleIndex) {
        return new Rect(
                WARP_LIST_BOX.x() + 1,
                WARP_LIST_BOX.y() + visibleIndex * WARP_ROW_HEIGHT + 1,
                WARP_LIST_BOX.width() - 2,
                WARP_ROW_HEIGHT - 2);
    }

    public static Rect chunkPlayerSlot(int index) {
        return GuiMetrics.playerSlot(CHUNK_BODY_BOTTOM, index);
    }

    public static Rect climatePlayerSlot(int index) {
        return GuiMetrics.playerSlot(CLIMATE_BODY_BOTTOM, index);
    }

    public static Rect warpPlayerSlot(int index) {
        return GuiMetrics.playerSlot(WARP_BODY_BOTTOM, index);
    }
}
