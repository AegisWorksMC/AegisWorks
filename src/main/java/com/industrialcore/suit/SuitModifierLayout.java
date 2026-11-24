package com.industrialcore.suit;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

import java.util.List;

/**
 * Phase 98 / 101: Pure-Java non-overlapping GUI layout for the Suit &amp; Weapon Modifier
 * ({@code suit_modifier}).
 *
 * <p>Expanded to 3 spacious module/attachment rows in Phase 101 so both Virtual Aegis armor pieces
 * (2 modules each) and Electric Weapons (3 attachments each) can be modified, toggled ON/OFF,
 * removed, and rapidly charged in one unified machine.</p>
 */
public final class SuitModifierLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 176
    public static final int HEIGHT = 230;
    public static final int BODY_BOTTOM = 134;
    public static final int PLAYER_INV_Y = 148;
    public static final int HOTBAR_Y = 206;

    /** Energy bar & text line. */
    public static final Rect ENERGY_BAR = new Rect(8, 27, 160, 8);
    public static final int ENERGY_TEXT_Y = 38;

    /** Slot coordinates (top-left of 16x16 item area; drawn with 1px slot border -> 18x18). */
    public static final int SUIT_SLOT_X = 11;
    public static final int SUIT_SLOT_Y = 53;
    public static final int MODULE_SLOT_X = 11;
    public static final int MODULE_SLOT_Y = 75;
    public static final int MOTOR_SLOT_X = 37;
    public static final int MOTOR_SLOT_Y = 53;
    public static final int BATTERY_SLOT_X = 37;
    public static final int BATTERY_SLOT_Y = 75;

    public static final Rect SUIT_SLOT_BOX = new Rect(SUIT_SLOT_X - 1, SUIT_SLOT_Y - 1, 18, 18);
    public static final Rect MODULE_SLOT_BOX = new Rect(MODULE_SLOT_X - 1, MODULE_SLOT_Y - 1, 18, 18);
    public static final Rect MOTOR_SLOT_BOX = new Rect(MOTOR_SLOT_X - 1, MOTOR_SLOT_Y - 1, 18, 18);
    public static final Rect BATTERY_SLOT_BOX = new Rect(BATTERY_SLOT_X - 1, BATTERY_SLOT_Y - 1, 18, 18);

    /** Install action button below the 4 left-side slots. */
    public static final Rect INSTALL_BUTTON = new Rect(8, 96, 56, 14);

    /** Compact Pulse Laser aim-range cycle button below INSTALL_BUTTON (shown only for pulse_laser). */
    public static final Rect LASER_RANGE_BUTTON = new Rect(8, 114, 56, 16);

    /** Right-side module/attachment management panel (3 rows: 2 for armor, 3 for weapons). */
    public static final Rect MODULE_PANEL = new Rect(68, 50, 100, 82);
    public static final int VISIBLE_ROWS = 3;

    public static final Rect ROW_0 = new Rect(71, 53, 94, 24);
    public static final Rect ROW_1 = new Rect(71, 79, 94, 24);
    public static final Rect ROW_2 = new Rect(71, 105, 94, 24);
    public static final List<Rect> MODULE_ROWS = List.of(ROW_0, ROW_1, ROW_2);

    public static final Rect TOGGLE_0 = new Rect(119, 64, 28, 11);
    public static final Rect TOGGLE_1 = new Rect(119, 90, 28, 11);
    public static final Rect TOGGLE_2 = new Rect(119, 116, 28, 11);
    public static final List<Rect> TOGGLE_BUTTONS = List.of(TOGGLE_0, TOGGLE_1, TOGGLE_2);

    public static final Rect REMOVE_0 = new Rect(149, 64, 14, 11);
    public static final Rect REMOVE_1 = new Rect(149, 90, 14, 11);
    public static final Rect REMOVE_2 = new Rect(149, 116, 14, 11);
    public static final List<Rect> REMOVE_BUTTONS = List.of(REMOVE_0, REMOVE_1, REMOVE_2);

    private SuitModifierLayout() {}

    /** Returns row index (0..2) whose toggle button contains (lx, ly), or -1. */
    public static int toggleButtonAt(int lx, int ly) {
        for (int i = 0; i < TOGGLE_BUTTONS.size(); i++) {
            if (TOGGLE_BUTTONS.get(i).contains(lx, ly)) {
                return i;
            }
        }
        return -1;
    }

    /** Returns row index (0..2) whose remove button contains (lx, ly), or -1. */
    public static int removeButtonAt(int lx, int ly) {
        for (int i = 0; i < REMOVE_BUTTONS.size(); i++) {
            if (REMOVE_BUTTONS.get(i).contains(lx, ly)) {
                return i;
            }
        }
        return -1;
    }
}
