package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the reconstructor GUI, shared by the menu and the screen.
 *
 * <p>Phase 87 removes the 6-face configuration cells (giving the selected-mineral line full width)
 * and appends a clean Motor &amp; Battery upgrade row below the unlocked-mineral buttons.</p>
 */
public final class ReconstructorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 234;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;

    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 7);
    public static final int ENERGY_TEXT_Y = 68;
    public static final int SLOT_LABEL_Y = 78;
    public static final int ANALYSIS_LABEL_X = 16;
    public static final int MATERIAL_LABEL_X = 56;
    public static final int CRYSTAL_LABEL_X = 108;

    public static final Rect ANALYSIS_SLOT = new Rect(16, 92, 16, 16);
    public static final Rect MATERIAL_SLOT = new Rect(60, 92, 16, 16);
    public static final Rect CRYSTAL_SLOT = new Rect(104, 92, 16, 16);
    public static final Rect OUTPUT_SLOT = new Rect(148, 92, 16, 16);

    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 118, GuiMetrics.CONTENT, 6);
    public static final int PROGRESS_TEXT_Y = 130;
    public static final int PROGRESS_TEXT_X = GuiMetrics.SIDE;
    public static final int PROGRESS_TEXT_MAX = GuiMetrics.CONTENT;

    public static final Rect ANALYZE_BUTTON = new Rect(16, 148, 118, 16);
    public static final Rect CYCLE_BUTTON = new Rect(138, 148, 42, 16);

    public static final int MINERAL_BUTTON_WIDTH = 54;
    public static final int MINERAL_BUTTON_HEIGHT = 12;
    public static final int MINERAL_COLUMNS = 3;
    public static final int MINERAL_ROWS = 2;
    public static final int MINERAL_BUTTON_COUNT = MINERAL_COLUMNS * MINERAL_ROWS;

    /** Phase 87: Motor & Battery upgrade slots below the unlocked mineral buttons. */
    public static final int UPGRADE_LABEL_Y = 200;
    public static final Rect MOTOR_SLOT = new Rect(44, 212, 16, 16);
    public static final Rect BATTERY_SLOT = new Rect(126, 212, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    public static Rect mineralButton(int index) {
        int column = index % MINERAL_COLUMNS;
        int row = index / MINERAL_COLUMNS;
        return new Rect(15 + column * 56, 170 + row * 14, MINERAL_BUTTON_WIDTH,
                MINERAL_BUTTON_HEIGHT);
    }

    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private ReconstructorLayout() {}
}
