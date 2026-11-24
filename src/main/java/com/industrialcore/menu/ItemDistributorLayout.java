package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the item distributor GUI, shared by the menu and the screen.
 *
 * <p>Phase 43: the buffer well and the six output rows keep their own body. The state now lives in
 * the shared status strip, the distribution mode has its own row above the "outputs" header (both
 * are long in English and collide when they share a baseline), and the click hint closes the body
 * above the shared inventory block.</p>
 *
 * <pre>
 *   y  58  internal buffer well (label right of it)
 *   y  88  distribution mode
 *   y 100  "outputs" header, next side right aligned
 *   y 114  six output rows, one per face, 10 px apart
 *   y 178  click hint
 *   y 194  body bottom
 * </pre>
 */
public final class ItemDistributorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 194;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip (the state line no longer floats in the body). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;


    /** Internal buffer slot. */
    public static final Rect BUFFER_SLOT = new Rect(20, 62, 16, 16);
    /** Baseline of the buffer label next to the slot. */
    public static final int BUFFER_TEXT_Y = 66;
    public static final int BUFFER_TEXT_X = 44;

    /** Baseline of the always-visible distribution mode line. */
    public static final int MODE_TEXT_Y = 88;
    public static final int MODE_TEXT_X = GuiMetrics.SIDE;
    /** Baseline of the "outputs" header (the next-side read-out is right aligned on the row). */
    public static final int OUTPUT_HEADER_Y = 100;
    public static final int OUTPUT_HEADER_X = GuiMetrics.SIDE;
    /** Right edge of the next-side read-out. */
    public static final int NEXT_X = GuiMetrics.contentRight();

    /** Baseline of the first output row; rows are {@link #FACE_ROW_HEIGHT} apart. */
    public static final int FACE_ROW_Y = 114;
    /** Height of one clickable output row (one font line plus two pixels of air). */
    public static final int FACE_ROW_HEIGHT = 10;
    /** Left edge of the state colour square of a row. */
    public static final int FACE_COLOR_X = 14;
    /** Left edge of the side name. */
    public static final int FACE_NAME_X = 26;
    /** Left edge of the state text (keeps the longest Japanese state inside the panel). */
    public static final int FACE_STATE_X = 104;

    /** Baseline of the click hint. */
    public static final int HINT_TEXT_Y = 178;
    public static final int HINT_TEXT_X = GuiMetrics.SIDE;

    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    /** Clickable rectangle of output row {@code index} (direction order). */
    public static Rect faceRow(int index) {
        return new Rect(GuiMetrics.SIDE, FACE_ROW_Y + Math.max(0, index) * FACE_ROW_HEIGHT,
                GuiMetrics.CONTENT, FACE_ROW_HEIGHT);
    }

    /** Rectangle of a player inventory slot (0..35; 0..26 are the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private ItemDistributorLayout() {}
}
