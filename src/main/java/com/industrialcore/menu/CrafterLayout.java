package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the auto crafter GUI, shared by the menu and the screen.
 *
 * <p>Phase 43 puts the panel on {@link GuiMetrics}: 196 px wide, a 30 px header band, the crafting
 * state in the shared status strip, then the body. Phase 83 restructures the body into a clear
 * left-to-right flow with golden-ratio proportions (1 : 1.618) and explicit clearance around every
 * slot frame and label:</p>
 *
 * <pre>
 *   y  58  FE bar                 y  68  stored / capacity
 *   y  80  selected recipe
 *   y  92  [Select recipe (104)] [Register (64)]        (104 : 64 = 1.625 ~ phi)
 *   y 120  3x3 input group (12..72) | progress conduit (84..130) | "Output" (y 122) + output well (y 142)
 *   y 182   "Battery" (cx 42)       |                            | "Motor" (cx 154)
 *   y 197   battery slot (y 197)    |                            | motor slot (y 197)
 *   y 220   body bottom
 * </pre>
 */
public final class CrafterLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    /** Bottom of the machine body; the shared chrome takes over below it. */
    public static final int BODY_BOTTOM = 220;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    /** FE bar across the top of the body. */
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 58, GuiMetrics.CONTENT, 7);
    /** Baseline top of the stored / capacity text. */
    public static final int ENERGY_TEXT_Y = 68;
    /** Baseline top of the selected-recipe line. */
    public static final int RECIPE_TEXT_Y = 80;

    /** Golden-ratio button split: 104 px + 4 px gap + 64 px = 172 px (104 / 64 = 1.625). */
    public static final Rect RECIPE_BUTTON = new Rect(GuiMetrics.SIDE, 92, 104, 20);
    /** Phase 11.2: register the recipe arranged in the 3x3 input slots. */
    public static final Rect REGISTER_BUTTON = new Rect(120, 92, 64, 20);

    /** Purple ingredient frame around the 3x3 pattern grid (4 px inset to the 16x16 slot cells). */
    public static final Rect INPUT_GROUP = new Rect(GuiMetrics.SIDE, 120, 60, 60);
    public static final int INPUT_X = INPUT_GROUP.x() + 4;
    public static final int INPUT_Y = INPUT_GROUP.y() + 4;
    public static final int INPUT_STRIDE = 18;

    /** Horizontal progress conduit centred between the 3x3 input group and the output well (12 px each side). */
    public static final Rect PROGRESS_BAR = new Rect(84, 147, 46, 6);
    /** Horizontal centre for the compact percentage read-out below the progress conduit. */
    public static final int PROGRESS_TEXT_X = PROGRESS_BAR.x() + PROGRESS_BAR.width() / 2;
    public static final int PROGRESS_TEXT_Y = 158;

    /** Shared vertical axis for the right-hand column ("Output", result well, "Motor", motor slot). */
    public static final int RIGHT_CENTER_X = 154;
    /** Maximum pixel width of a centred right-column label before ellipsis clipping. */
    public static final int LABEL_MAX_WIDTH = 56;

    /** Result well aligned vertically with row 1 of the 3x3 grid and the progress conduit (centre y = 150). */
    public static final Rect OUTPUT_SLOT = new Rect(RIGHT_CENTER_X - 8, 142, 16, 16);
    /** Baseline top of the centred "Output" label; sits 7 px above the 24x24 cyan ring (4 px above glow). */
    public static final int OUTPUT_LABEL_Y = 122;

    /** Dedicated motor slot aligned directly beneath the output well on {@link #RIGHT_CENTER_X}. */
    public static final Rect UPGRADE_SLOT = new Rect(RIGHT_CENTER_X - 8, 197, 16, 16);
    /** Baseline top of the centred "Motor" label; sits 4 px above the motor slot rim and 17 px below output glow. */
    public static final int UPGRADE_LABEL_Y = 182;

    /** Phase 87: Battery slot aligned directly beneath the 3x3 input group centre (x = 42). */
    public static final int LEFT_CENTER_X = INPUT_GROUP.x() + INPUT_GROUP.width() / 2;
    public static final Rect BATTERY_SLOT = new Rect(LEFT_CENTER_X - 8, 197, 16, 16);
    public static final int BATTERY_LABEL_Y = UPGRADE_LABEL_Y;

    /** Vanilla font line height used for label bounding-box clearance checks. */
    public static final int LABEL_HEIGHT = 9;

    private CrafterLayout() {
    }

    public static Rect inputSlot(int index) {
        int column = index % 3;
        int row = index / 3;
        return new Rect(INPUT_X + column * INPUT_STRIDE, INPUT_Y + row * INPUT_STRIDE, 16, 16);
    }

    /** Outer 20x20 bezel drawn by {@code MachineTheme.slotWell} around a 16x16 slot. */
    public static Rect slotWellBounds(Rect slot) {
        return new Rect(slot.x() - 2, slot.y() - 2, slot.width() + 4, slot.height() + 4);
    }

    /** Outer 24x24 cyan ring drawn by {@code MachineTheme.outputSlot} around {@link #OUTPUT_SLOT}. */
    public static Rect outputWellBounds() {
        return new Rect(OUTPUT_SLOT.x() - 4, OUTPUT_SLOT.y() - 4,
                OUTPUT_SLOT.width() + 8, OUTPUT_SLOT.height() + 8);
    }

    /** Outermost 30x30 neon bloom envelope around {@link #OUTPUT_SLOT} (3 px pad outside the cyan ring). */
    public static Rect outputGlowBounds() {
        return new Rect(OUTPUT_SLOT.x() - 7, OUTPUT_SLOT.y() - 7,
                OUTPUT_SLOT.width() + 14, OUTPUT_SLOT.height() + 14);
    }

    /** Bounding box reserved for the centred "Output" label above {@link #OUTPUT_SLOT}. */
    public static Rect outputLabelBounds() {
        return new Rect(RIGHT_CENTER_X - LABEL_MAX_WIDTH / 2, OUTPUT_LABEL_Y,
                LABEL_MAX_WIDTH, LABEL_HEIGHT);
    }

    /** Bounding box reserved for the centred "Motor" label above {@link #UPGRADE_SLOT}. */
    public static Rect upgradeLabelBounds() {
        return new Rect(RIGHT_CENTER_X - LABEL_MAX_WIDTH / 2, UPGRADE_LABEL_Y,
                LABEL_MAX_WIDTH, LABEL_HEIGHT);
    }

    /** Bounding box reserved for the centred "Battery" label above {@link #BATTERY_SLOT}. */
    public static Rect batteryLabelBounds() {
        return new Rect(LEFT_CENTER_X - LABEL_MAX_WIDTH / 2, BATTERY_LABEL_Y,
                LABEL_MAX_WIDTH, LABEL_HEIGHT);
    }

    /** Bounding box reserved for the centred progress percentage below {@link #PROGRESS_BAR}. */
    public static Rect progressTextBounds() {
        return new Rect(PROGRESS_TEXT_X - 18, PROGRESS_TEXT_Y, 36, LABEL_HEIGHT);
    }

    /**
     * Ghost suppression rule (Phase 83): a ghost/preview item is rendered only when the slot has
     * no real item, preventing double-drawn items on pattern and output slots.
     */
    public static boolean shouldRenderGhost(boolean slotHasRealItem, boolean hasGhostStack) {
        return !slotHasRealItem && hasGhostStack;
    }

    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
