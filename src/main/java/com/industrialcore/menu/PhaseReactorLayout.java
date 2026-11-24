package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the phase reactor GUI, shared by the menu and the screen.
 *
 * <p>Phase 43: same row shape as the assembler (three ingredients, a progress conduit, one result)
 * with the labels above the wells. The reactor keeps its own language through colour, not through a
 * different grid — every machine in the mod now shares this body rhythm.</p>
 *
 * <pre>
 *   y  58  FE bar
 *   y  71  stored / capacity
 *   y  78  "ingredients" / "result" labels
 *   y  92  three ingredient wells -> progress conduit -> result well
 *   y 120  body bottom
 * </pre>
 */
public final class PhaseReactorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 154;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip (the state line no longer floats in the body). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;


    /** FE bar. */
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 7);
    /** Baseline of the energy text. */
    public static final int ENERGY_TEXT_Y = 68;

    /** First ingredient slot. */
    public static final Rect INPUT_SLOT_1 = new Rect(44, 92, 16, 16);
    /** Second ingredient slot. */
    public static final Rect INPUT_SLOT_2 = new Rect(62, 92, 16, 16);
    /** Third ingredient slot. */
    public static final Rect INPUT_SLOT_3 = new Rect(80, 92, 16, 16);
    /** Progress bar pointing from the ingredients to the result. */
    public static final Rect PROGRESS_BAR = new Rect(104, 97, 30, 6);
    /** Result slot. */
    public static final Rect OUTPUT_SLOT = new Rect(140, 92, 16, 16);
    /** Baseline of the slot labels above the wells. */
    public static final int SLOT_LABEL_Y = 78;

    /** Phase 87: centered Motor & Battery upgrade row below the reaction row. */
    public static final int UPGRADE_LABEL_Y = 120;
    public static final Rect MOTOR_SLOT = new Rect(44, 132, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final Rect BATTERY_SLOT = new Rect(126, 132, 16, 16);
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    /** Top edge of the player inventory. */
    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    /** Vertical distance between two inventory rows. */
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    /** Top edge of the hotbar. */
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    /** Rectangle of ingredient slot {@code index} (0..2). */
    public static Rect inputSlot(int index) {
        return switch (index) {
            case 0 -> INPUT_SLOT_1;
            case 1 -> INPUT_SLOT_2;
            default -> INPUT_SLOT_3;
        };
    }

    /** Rectangle of a player inventory slot (0..35; 0..26 are the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private PhaseReactorLayout() {}
}
