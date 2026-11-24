package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the large energy buffer GUI, shared by the menu and the screen.
 *
 * <p>Phase 43: the buffer has no item slots of its own, so the body is built around one tall FE
 * bar — the amount of energy is the whole point of the machine — with the numbers underneath.</p>
 *
 * <pre>
 *   y  58  FE bar (22 px, the largest gauge in the mod)
 *   y  92  stored / capacity
 *   y 106  push rate
 *   y 118  hint
 *   y 132  body bottom
 * </pre>
 */
public final class EnergyBufferLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 132;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip (the state line no longer floats in the body). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;


    /** FE bar: as tall as a bay, because the charge is the machine's only content. */
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 62, GuiMetrics.CONTENT, 22);
    /** Baseline of the stored / capacity text. */
    public static final int ENERGY_TEXT_Y = 92;
    /** Baseline of the push rate text. */
    public static final int PUSH_TEXT_Y = 106;
    /** Baseline of the hint text. */
    public static final int HINT_TEXT_Y = 118;
    public static final int HINT_X = GuiMetrics.SIDE;

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

    private EnergyBufferLayout() {}
}
