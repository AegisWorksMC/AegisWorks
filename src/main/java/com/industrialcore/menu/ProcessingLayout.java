package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the six processing machine GUIs (crusher, electric furnace, electrolyzer,
 * precision crusher, metal press, fast crusher).
 *
 * <p>Phase 87 removes the 6-face configuration row and places balanced Motor and Battery upgrade
 * slots directly beneath the input and output wells, keeping vertical axis alignment and generous
 * breathing room for labels.</p>
 *
 * <pre>
 *   y  60  FE bar, full content width
 *   y  68  stored / capacity FE
 *   y  92  ingredient well (x 44) -> progress conduit -> result well (x 126)
 *   y 120  "Motor" (cx 52)                  "Battery" (cx 134)
 *   y 132  motor slot (x 44)                battery slot (x 126)
 *   y 154  body bottom: separator, inventory, hotbar
 * </pre>
 */
public final class ProcessingLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    /** Bottom of the machine body; the shared chrome takes over below it. */
    public static final int BODY_BOTTOM = 154;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip. */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;

    /** FE bar across the top of the body. */
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 7);
    /** Baseline of the stored / capacity text. */
    public static final int ENERGY_TEXT_Y = 68;

    /** Ingredient well (purple = what goes in). */
    public static final Rect INPUT_SLOT = new Rect(44, 92, 16, 16);
    /** Result well (cyan = what comes out). */
    public static final Rect OUTPUT_SLOT = new Rect(126, 92, 16, 16);
    /** Progress conduit between the two wells. */
    public static final Rect PROGRESS_BAR = new Rect(72, 97, 46, 6);

    /** Phase 87: Motor & Battery upgrade row aligned cleanly beneath input and output wells. */
    public static final int UPGRADE_LABEL_Y = 120;
    public static final Rect MOTOR_SLOT = new Rect(44, 132, 16, 16);
    public static final Rect BATTERY_SLOT = new Rect(126, 132, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    private ProcessingLayout() {}

    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
