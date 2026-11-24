package com.industrialcore.agri;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java unified industrial GUI layouts for Phase 91 Agriculture &amp; Forestry branch wave 2:
 * {@code ranch_harvester}, {@code growth_accelerator}, and {@code bio_feeder}.
 *
 * <p>Strictly adheres to {@link GuiMetrics} (196px width, body below y=58), zero 6-face buttons,
 * dedicated Motor &amp; Battery upgrade slots, and clean separation from all labels and player slots.</p>
 */
public final class AgriWave2Layout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196

    // Shared energy & progress bars (strictly below GuiMetrics.BODY_TOP = 58)
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 8);
    public static final int ENERGY_TEXT_Y = 70;
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 81, GuiMetrics.CONTENT, 8);
    public static final int INFO_TEXT_Y = 92;

    // ------------------------------------------------------------------
    // 1. Ranch Harvester (3 vertical feed/container slots, 3x3 output, Motor/Battery)
    // ------------------------------------------------------------------
    public static final int RANCH_BODY_BOTTOM = 176;
    public static final int RANCH_HEIGHT = GuiMetrics.height(RANCH_BODY_BOTTOM); // 284
    public static final int RANCH_FEED_LABEL_X = 12;
    public static final int RANCH_FEED_LABEL_Y = 104;
    public static final Rect RANCH_FEED_GROUP = AgriLayout.INPUT_GROUP_3;
    public static final int RANCH_OUTPUT_LABEL_X = AgriLayout.OUTPUT_LABEL_X;
    public static final int RANCH_OUTPUT_LABEL_Y = AgriLayout.OUTPUT_LABEL_Y;
    public static final Rect RANCH_OUTPUT_GROUP = AgriLayout.OUTPUT_GROUP;
    public static final int RANCH_UPGRADE_CENTER_X = AgriLayout.UPGRADE_CENTER_X;
    public static final int RANCH_MOTOR_LABEL_Y = AgriLayout.MOTOR_LABEL_Y;
    public static final Rect RANCH_MOTOR_SLOT = AgriLayout.MOTOR_SLOT;
    public static final int RANCH_BATTERY_LABEL_Y = AgriLayout.BATTERY_LABEL_Y;
    public static final Rect RANCH_BATTERY_SLOT = AgriLayout.BATTERY_SLOT;

    // ------------------------------------------------------------------
    // 2. Growth Accelerator (telemetry card + Motor/Battery only, no item bay)
    // ------------------------------------------------------------------
    public static final int GROWTH_BODY_BOTTOM = 166;
    public static final int GROWTH_HEIGHT = GuiMetrics.height(GROWTH_BODY_BOTTOM); // 274
    public static final Rect GROWTH_STATS_BOX = new Rect(12, 95, 114, 64);
    public static final int GROWTH_STATS_TEXT_X = 18;
    public static final int GROWTH_ROW_1_Y = 101;
    public static final int GROWTH_ROW_2_Y = 115;
    public static final int GROWTH_ROW_3_Y = 129;
    public static final int GROWTH_ROW_4_Y = 143;
    public static final int GROWTH_UPGRADE_CENTER_X = 158;
    public static final int GROWTH_MOTOR_LABEL_Y = 98;
    public static final Rect GROWTH_MOTOR_SLOT = new Rect(150, 108, 16, 16);
    public static final int GROWTH_BATTERY_LABEL_Y = 130;
    public static final Rect GROWTH_BATTERY_SLOT = new Rect(150, 140, 16, 16);

    // ------------------------------------------------------------------
    // 3. Bio Feeder Beacon (centered-left 3x3 food grid + Motor/Battery)
    // ------------------------------------------------------------------
    public static final int FEEDER_BODY_BOTTOM = 176;
    public static final int FEEDER_HEIGHT = GuiMetrics.height(FEEDER_BODY_BOTTOM); // 284
    public static final int FEEDER_FOOD_LABEL_X = 28;
    public static final int FEEDER_FOOD_LABEL_Y = 104;
    public static final int FEEDER_FOOD_X = 28;
    public static final int FEEDER_FOOD_Y = 116;
    public static final int FEEDER_FOOD_STRIDE = 18;
    public static final int FEEDER_FOOD_COUNT = 9;
    public static final Rect FEEDER_FOOD_GROUP = new Rect(FEEDER_FOOD_X - 3, FEEDER_FOOD_Y - 3, 58, 58);
    public static final int FEEDER_UPGRADE_CENTER_X = 148;
    public static final int FEEDER_MOTOR_LABEL_Y = 104;
    public static final Rect FEEDER_MOTOR_SLOT = new Rect(FEEDER_UPGRADE_CENTER_X - 8, 116, 16, 16);
    public static final int FEEDER_BATTERY_LABEL_Y = 138;
    public static final Rect FEEDER_BATTERY_SLOT = new Rect(FEEDER_UPGRADE_CENTER_X - 8, 148, 16, 16);

    private AgriWave2Layout() {}

    public static Rect ranchFeedSlot(int index) {
        return AgriLayout.inputSlot3(index);
    }

    public static Rect ranchOutputSlot(int index) {
        return AgriLayout.outputSlot(index);
    }

    public static Rect ranchPlayerSlot(int index) {
        return GuiMetrics.playerSlot(RANCH_BODY_BOTTOM, index);
    }

    public static Rect growthPlayerSlot(int index) {
        return GuiMetrics.playerSlot(GROWTH_BODY_BOTTOM, index);
    }

    public static Rect feederFoodSlot(int index) {
        int col = index % 3;
        int row = index / 3;
        return new Rect(FEEDER_FOOD_X + col * FEEDER_FOOD_STRIDE,
                FEEDER_FOOD_Y + row * FEEDER_FOOD_STRIDE, 16, 16);
    }

    public static Rect feederPlayerSlot(int index) {
        return GuiMetrics.playerSlot(FEEDER_BODY_BOTTOM, index);
    }
}
