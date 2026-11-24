package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the alloy furnace GUI, shared by the menu and the screen.
 *
 * <p>Phase 43: two interchangeable ingredients sit side by side in one purple group (they are the
 * same "slot A / slot B" pair the recipe uses), the result well is on the right, the fuel well and
 * the burn gauge close the body. The panel is 32 px taller than a single-input machine because the
 * burn row needs its own line — a taller body is honest, a squeezed one is not.</p>
 *
 * <pre>
 *   y  58  ingredient labels (one group label, not one per well)
 *   y  72  ingredient A and B wells, result well
 *   y  77  progress conduit
 *   y 102  fuel well (label to its right)
 *   y 124  burn bar, y 134 burn text
 *   y 146  body bottom
 * </pre>
 */
public final class AlloyFurnaceLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 182;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip (the state line no longer floats in the body). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;


    /** First ingredient slot (right of it: the "A" label). */
    public static final Rect FIRST_SLOT = new Rect(40, 72, 16, 16);
    /** Second ingredient slot (right of it: the "B" label). */
    public static final Rect SECOND_SLOT = new Rect(72, 72, 16, 16);
    /** Fuel slot (right of it: the fuel label). */
    public static final Rect FUEL_SLOT = new Rect(40, 102, 16, 16);
    /** Result slot; its label sits above the slot. */
    public static final Rect OUTPUT_SLOT = new Rect(140, 72, 16, 16);
    /** Progress bar between the ingredients and the output. */
    public static final Rect PROGRESS_BAR = new Rect(94, 77, 38, 6);

    /** Baseline of the output slot label (above the slot, clear of the well ring). */
    public static final int OUTPUT_LABEL_Y = 58;
    /** Baseline of the single "ingredients A / B" label above the pair. */
    public static final int INPUT_LABEL_Y = 58;
    /** Horizontal offset of the labels that sit to the right of a slot. */
    public static final int SLOT_LABEL_OFFSET_X = 22;
    /** Vertical offset of the labels that sit to the right of a slot. */
    public static final int SLOT_LABEL_OFFSET_Y = 4;

    /** Remaining fuel, drawn as a horizontal bar. */
    public static final Rect BURN_BAR = new Rect(GuiMetrics.SIDE, 124, GuiMetrics.CONTENT, 6);
    /** Baseline of the burn text. */
    public static final int BURN_TEXT_Y = 134;
    public static final int BURN_TEXT_X = GuiMetrics.SIDE;

    /** Phase 87: centered Motor & Battery upgrade row below the burn readout. */
    public static final int UPGRADE_LABEL_Y = 148;
    public static final Rect MOTOR_SLOT = new Rect(44, 160, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final Rect BATTERY_SLOT = new Rect(126, 160, 16, 16);
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    /** Top edge of the player inventory. */
    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    /** Vertical distance between two inventory rows. */
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    /** Top edge of the hotbar. */
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    /** Rectangle of a player inventory slot (0..35; 0..26 are the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private AlloyFurnaceLayout() {}
}
