package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the quantum compressor GUI, shared by the menu and the screen.
 *
 * <p>Phase 43: one input on the left, the 2x2 result block on the right, the progress conduit
 * between them. Labels sit above their wells, the state and the batch line are read-only text, so
 * the panel still fits a single glance: what goes in, how far, what came out.</p>
 *
 * <pre>
 *   y  58  FE bar
 *   y  71  stored / capacity
 *   y  78  "input" / "outputs" labels
 *   y  92  input well -> progress conduit -> 2x2 result block (second row at 110)
 *   y 136  batch line
 *   y 148  body bottom
 * </pre>
 */
public final class QuantumCompressorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 184;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);
    /** Baseline of the shared status strip (the state line no longer floats in the body). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;


    /** FE bar. */
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 60, GuiMetrics.CONTENT, 7);
    /** Baseline of the energy text. */
    public static final int ENERGY_TEXT_Y = 68;
    /** Baseline of the slot labels. */
    public static final int SLOT_LABEL_Y = 78;

    /** The single input slot. */
    public static final Rect INPUT_SLOT = new Rect(44, 92, 16, 16);
    /** Progress bar pointing from the input to the outputs. */
    public static final Rect PROGRESS_BAR = new Rect(68, 97, 44, 6);
    /** First output slot (top left of the 2 x 2 output block). */
    public static final Rect OUTPUT_SLOT_1 = new Rect(124, 92, 16, 16);
    /** Second output slot (top right). */
    public static final Rect OUTPUT_SLOT_2 = new Rect(142, 92, 16, 16);
    /** Third output slot (bottom left). */
    public static final Rect OUTPUT_SLOT_3 = new Rect(124, 110, 16, 16);
    /** Fourth output slot (bottom right). */
    public static final Rect OUTPUT_SLOT_4 = new Rect(142, 110, 16, 16);

    /** Baseline of the batch and FE line. */
    public static final int BATCH_TEXT_Y = 136;

    /** Phase 87: centered Motor & Battery upgrade row below the batch readout. */
    public static final int UPGRADE_LABEL_Y = 150;
    public static final Rect MOTOR_SLOT = new Rect(44, 162, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final Rect BATTERY_SLOT = new Rect(126, 162, 16, 16);
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    /** Top edge of the player inventory. */
    public static final int PLAYER_INV_Y = GuiMetrics.playerInvY(BODY_BOTTOM);
    /** Vertical distance between two inventory rows. */
    public static final int PLAYER_ROW_STRIDE = GuiMetrics.PLAYER_ROW_STRIDE;
    /** Top edge of the hotbar. */
    public static final int HOTBAR_Y = GuiMetrics.hotbarY(BODY_BOTTOM);

    /** Rectangle of output slot {@code index} (0..3). */
    public static Rect outputSlot(int index) {
        return switch (index) {
            case 0 -> OUTPUT_SLOT_1;
            case 1 -> OUTPUT_SLOT_2;
            case 2 -> OUTPUT_SLOT_3;
            default -> OUTPUT_SLOT_4;
        };
    }

    /** Rectangle of a player inventory slot (0..35; 0..26 are the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    private QuantumCompressorLayout() {}
}
