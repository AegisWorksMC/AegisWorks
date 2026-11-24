package com.industrialcore.endgame;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Phase 99: Pure-Java non-overlapping GUI layout for the Singularity Reactor Generator
 * ({@code singularity_reactor}).
 */
public final class SingularityReactorLayout {
    public static final int WIDTH = GuiMetrics.WIDTH; // 196
    public static final int BODY_BOTTOM = 132;
    public static final int PLAYER_INV_Y = 146;
    public static final int HOTBAR_Y = 204;
    public static final int HEIGHT = 228;

    /** Energy bar (sits above MachineTheme's status strip at y=35..47). */
    public static final Rect ENERGY_BAR = new Rect(8, 27, 160, 8);

    /** Slot coordinates (top-left of 16x16 item area; 18x18 framed box). */
    public static final int FUEL_SLOT_X = 12;
    public static final int FUEL_SLOT_Y = 55;
    public static final int MOTOR_SLOT_X = 12;
    public static final int MOTOR_SLOT_Y = 85;
    public static final int BATTERY_SLOT_X = 36;
    public static final int BATTERY_SLOT_Y = 85;

    public static final Rect FUEL_SLOT_BOX = new Rect(FUEL_SLOT_X - 1, FUEL_SLOT_Y - 1, 18, 18);
    public static final Rect MOTOR_SLOT_BOX = new Rect(MOTOR_SLOT_X - 1, MOTOR_SLOT_Y - 1, 18, 18);
    public static final Rect BATTERY_SLOT_BOX = new Rect(BATTERY_SLOT_X - 1, BATTERY_SLOT_Y - 1, 18, 18);

    /** Right-hand reactor telemetry panel. */
    public static final Rect TELEMETRY_PANEL = new Rect(62, 52, 122, 58);
    public static final Rect BURN_BAR = new Rect(67, 83, 112, 6);

    /** Phase 103: Multiblock status strip + 3D holographic guide toggle button. */
    public static final Rect MULTIBLOCK_BAR = new Rect(12, 114, 132, 14);
    public static final Rect GUIDE_BUTTON = new Rect(148, 114, 36, 14);
    /** Phase 114: High-Output Overdrive toggle button inside telemetry panel above burn bar. */
    public static final Rect OVERDRIVE_BUTTON = new Rect(138, 67, 42, 13);

    /** Phase 115 / 116: Attached right-side Dimensional Rift Challenge card with Tier 1..10 selector. */
    public static final int RIFT_CARD_GAP = 4;
    public static final int RIFT_CARD_WIDTH = 88;
    public static final Rect RIFT_CARD = new Rect(WIDTH + RIFT_CARD_GAP, 16, RIFT_CARD_WIDTH, 122);
    public static final Rect RIFT_TIER_PREV = new Rect(RIFT_CARD.x() + 5, RIFT_CARD.y() + 21, 14, 14);
    public static final Rect RIFT_TIER_NEXT = new Rect(RIFT_CARD.right() - 19, RIFT_CARD.y() + 21, 14, 14);
    public static final Rect RIFT_BUTTON = new Rect(RIFT_CARD.x() + 6, RIFT_CARD.y() + 77, RIFT_CARD_WIDTH - 12, 18);

    private SingularityReactorLayout() {}
}
