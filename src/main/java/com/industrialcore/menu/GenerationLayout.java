package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the shared panel of the power devices (seven generators and the four recovery
 * devices), which all use one {@code GenerationMenu} and one {@code GenerationScreen}.
 *
 * <p>Phase 43 moves the panel onto {@link GuiMetrics}: 196 px wide, a 30 px header band, the state
 * line in the shared status strip, then a body that holds the three read-outs a player needs to
 * answer "why is it not producing?" — the current output rate, the stored FE and the fuel (or the
 * source condition) of the device.</p>
 *
 * <p>The FE gauge is a vertical column on the right edge, so the rate and energy text on the left
 * never have to share a row with a bar. Fuel devices show one well and a burn gauge; a condition
 * device (hydro, wind, solar, geothermal) draws its source plate on the same well position, so the
 * panel keeps one shape across all eleven devices.</p>
 *
 * <pre>
 *   y  58  output rate (left)
 *   y  70  stored / capacity FE (left)          FE column on the right, y 60..108
 *   y  84  "Fuel" / source label
 *   y  92  fuel well (36..52) and burn gauge (58..124), or the source plate
 *   y 116  body bottom
 * </pre>
 */
public final class GenerationLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 116;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    /** Vertical FE column on the right edge of the body. */
    public static final Rect ENERGY_COLUMN = new Rect(168, 60, 10, 46);

    /** Baseline top of the "output: n FE/t" line. */
    public static final int RATE_TEXT_Y = 60;
    /** Baseline top of the stored / capacity line. */
    public static final int ENERGY_TEXT_Y = 72;

    /** The one slot a fuel device has (fuel generators only). */
    public static final Rect FUEL_SLOT = new Rect(36, 92, 16, 16);
    /** Label above the fuel well ("Fuel" / "燃料"). */
    public static final int SLOT_LABEL_X = 36;
    public static final int SLOT_LABEL_Y = 82;
    /** Burn gauge to the right of the fuel well, on the well's own row. */
    public static final Rect BURN_BAR = new Rect(60, 97, 64, 6);

    /**
     * Source plate of a condition device: the same rectangle as the fuel well, so a generator
     * panel always has one "input" position, whichever kind of input it is.
     */
    public static final Rect SOURCE_PLATE = FUEL_SLOT;
    /** Label to the right of the source plate ("Lava linked" / "No water"). */
    public static final int SOURCE_LABEL_X = 58;
    public static final int SOURCE_LABEL_Y = 96;

    /** Phase 105.6: Nuclear coolant water-bucket slot, burn bar, vertical coolant column & guide button. */
    public static final Rect NUCLEAR_BUCKET_SLOT = new Rect(12, 92, 16, 16);
    public static final int NUCLEAR_BUCKET_LABEL_X = 12;
    public static final Rect NUCLEAR_BURN_BAR = new Rect(58, 97, 50, 6);
    public static final Rect NUCLEAR_GUIDE_BTN = new Rect(113, 93, 34, 14);
    public static final Rect NUCLEAR_COOLANT_COLUMN = new Rect(152, 60, 10, 46);
    /** Phase 114: High-Output Overdrive toggle button (cleanly left of coolant column & above energy line). */
    public static final Rect NUCLEAR_OVERDRIVE_BTN = new Rect(106, 58, 42, 13);

    private GenerationLayout() {
    }

    /** Player inventory slot rectangle (0..35; 0..26 rows, 27..35 hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }
}
