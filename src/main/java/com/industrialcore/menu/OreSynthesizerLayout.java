package com.industrialcore.menu;

import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the synthesizer GUI.
 *
 * <p>The machine has a 4x3 ingredient grid: the ultimate synthetic ore needs ten different stacks
 * at once (the unstable ore, the eight additives and the phase core), which no three-slot layout
 * can express.</p>
 *
 * <p>Phase 43 puts the panel on {@link GuiMetrics}: a 30 px header band, the state in the shared
 * status strip, then one recessed machine bay holding the FE column, the ingredient grid, the
 * progress conduit and the result well, left to right. The slot rectangles here are the single
 * authority for both the menu (hit boxes) and the screen (drawing), so the two can never drift
 * apart.</p>
 *
 * <pre>
 *   y  58  bay top
 *   y  66  ingredient grid row 1        FE column 14..24, y 64..124
 *   y  84  ingredient grid row 2
 *   y  86  progress conduit 124..150, result well 158..174
 *   y 102  ingredient grid row 3
 *   y 128  stored / capacity FE
 *   y 142  bay bottom
 *   y 146  body bottom
 * </pre>
 */
public final class OreSynthesizerLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    /** Bottom of the machine body; the shared chrome takes over below it. */
    public static final int BODY_BOTTOM = 182;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 3;

    /** First ingredient slot; the grid grows by 18 px to the right and downwards. */
    public static final Rect INPUT_ORIGIN = new Rect(40, 66, 16, 16);
    /** Result slot, to the right of the progress conduit. */
    public static final Rect OUTPUT_SLOT = new Rect(158, 82, 16, 16);

    /** Frame around the whole 4x3 grid, drawn as one grouped bay. */
    public static final Rect INPUT_GROUP = new Rect(36, 62, 82, 62);

    /**
     * Vertical FE column on the left of the machine bay: it ends on the same row as the
     * ingredient grid, so the shelf under them is one straight line.
     */
    public static final Rect ENERGY_BAR = new Rect(14, 64, 10, 54);
    /** Horizontal progress conduit between the grid and the output slot. */
    public static final Rect PROGRESS_BAR = new Rect(124, 86, 26, 14);

    /** Machine bay that holds every read-out (screen-only decoration). */
    public static final Rect BAY = new Rect(6, 58, 184, 84);
    /** Shelf that separates the chamber row from the read-out row underneath. */
    public static final int SHELF_Y = 124;

    /** Baseline top of the stored / capacity text, under the grid. */
    public static final int ENERGY_TEXT_X = 40;
    public static final int ENERGY_TEXT_Y = 128;

    /** Phase 87: centered Motor & Battery upgrade row below the recessed bay. */
    public static final int UPGRADE_LABEL_Y = 148;
    public static final Rect MOTOR_SLOT = new Rect(44, 160, 16, 16);
    public static final int MOTOR_CENTER_X = MOTOR_SLOT.x() + 8;
    public static final Rect BATTERY_SLOT = new Rect(126, 160, 16, 16);
    public static final int BATTERY_CENTER_X = BATTERY_SLOT.x() + 8;

    private OreSynthesizerLayout() {
    }

    /** One cell of the ingredient grid. */
    public static Rect inputSlot(int index) {
        int column = index % GRID_COLUMNS;
        int row = index / GRID_COLUMNS;
        return new Rect(INPUT_ORIGIN.x() + column * 18, INPUT_ORIGIN.y() + row * 18, 16, 16);
    }

    /** True when the index is inside the grid. */
    public static boolean isInput(int index) {
        return index >= 0 && index < SynthesisBalance.INPUT_SLOTS;
    }

    /** Player inventory slot rectangle (0-26 main inventory, 27-35 hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
