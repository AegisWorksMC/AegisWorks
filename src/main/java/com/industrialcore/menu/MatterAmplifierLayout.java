package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the matter amplifier GUI, shared by the menu and the screen.
 *
 * <p>Phase 87 removes the 6-face configuration row and places balanced Motor &amp; Battery upgrade
 * slots cleanly below the conversion line.</p>
 */
public final class MatterAmplifierLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 180;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;

    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 7);
    public static final int ENERGY_TEXT_Y = 68;
    public static final int SLOT_LABEL_Y = 78;

    /** Billet input. */
    public static final Rect INPUT_SLOT = new Rect(32, 92, 16, 16);
    /** Reconstruction crystal (catalyst). */
    public static final Rect CATALYST_SLOT = new Rect(76, 92, 16, 16);
    /** Ore block output. */
    public static final Rect OUTPUT_SLOT = new Rect(124, 92, 16, 16);
    /** Dust output. */
    public static final Rect DUST_OUTPUT_SLOT = new Rect(156, 92, 16, 16);

    /** Progress bar of the running amplification. */
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 118, GuiMetrics.CONTENT, 6);
    /** Baseline of the conversion text. */
    public static final int CONVERSION_TEXT_Y = 132;
    public static final int CONVERSION_TEXT_X = GuiMetrics.SIDE;

    /** Phase 87: Motor & Battery upgrade slots below the conversion line. */
    public static final int UPGRADE_LABEL_Y = 146;
    public static final Rect MOTOR_SLOT = new Rect(44, 158, 16, 16);
    public static final Rect BATTERY_SLOT = new Rect(126, 158, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    /** Rectangle of a player inventory slot (0..35; 0..26 the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private MatterAmplifierLayout() {}
}
