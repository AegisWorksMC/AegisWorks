package com.industrialcore.menu;

/**
 * Geometry of the quarry GUI, shared by the menu (slot positions) and the screen (drawing and
 * click areas).
 *
 * <p>Keeping the numbers in one place means a slot cannot silently drift under a text field again,
 * and the non-overlap rules can be unit tested without starting a client (see
 * {@code GuiLayoutTest}). Coordinates are relative to the top-left corner of the GUI.</p>
 *
 * <p>Phase 43 puts the tabbed panel on {@link GuiMetrics}: the machine name and the state stay in
 * the shared header band and status strip, the two page tabs and the always-visible run / preview
 * buttons get their own rows underneath, and the body below them belongs to the selected tab.
 * The player inventory is fixed at the bottom, so switching tabs never moves the hand.</p>
 *
 * <pre>
 *   y  34  status strip (state of the machine, on both tabs)
 *   y  58  [Settings] [Status & loot]        two 84 px tabs
 *   y  80  [Start/Stop] [Preview]
 *   y 104  tab body
 *   y 208  body bottom
 * </pre>
 */
public final class QuarryLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    public static final int BODY_BOTTOM = 208;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    public static final int TAB_SETTINGS = 0;
    public static final int TAB_STATUS = 1;

    /** Tabs: two 84 px tabs under the header, right below the status strip. */
    public static final Rect SETTINGS_TAB = new Rect(GuiMetrics.SIDE, 58, 84, 18);
    public static final Rect STATUS_TAB = new Rect(100, 58, 84, 18);

    /** Always visible on both tabs: run/pause and the area-frame toggle. */
    public static final Rect START_BUTTON = new Rect(GuiMetrics.SIDE, 80, 80, 18);
    public static final Rect PREVIEW_BUTTON = new Rect(100, 80, 84, 18);

    // ------------------------------------------------------------------
    // Settings tab
    // ------------------------------------------------------------------
    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 104, GuiMetrics.CONTENT, 7);
    public static final int ENERGY_TEXT_Y = 114;
    public static final int FIELD_LABEL_Y = 128;

    /** Three 44 px fields in one row, each with a label above it. */
    public static final Rect WIDTH_FIELD = new Rect(GuiMetrics.SIDE, 138, 44, 14);
    public static final Rect DEPTH_FIELD = new Rect(72, 138, 44, 14);
    public static final Rect HEIGHT_FIELD = new Rect(132, 138, 44, 14);
    public static final Rect APPLY_BUTTON = new Rect(GuiMetrics.SIDE, 158, GuiMetrics.CONTENT, 18);

    /** Upgrade slots, well below the range fields and their apply button. */
    public static final Rect MOTOR_SLOT = new Rect(30, 192, 16, 16);
    public static final Rect BATTERY_SLOT = new Rect(108, 192, 16, 16);
    /** Short labels to the right of each upgrade slot. */
    public static final int MOTOR_LABEL_X = 50;
    public static final int BATTERY_LABEL_X = 128;
    public static final int SLOT_LABEL_Y = 196;

    // ------------------------------------------------------------------
    // Status tab
    // ------------------------------------------------------------------
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 104, GuiMetrics.CONTENT, 6);
    public static final int PROGRESS_TEXT_Y = 112;
    public static final int STATS_TEXT_Y = 126;
    public static final int LOOT_TITLE_Y = 140;
    /** First of two loot rows; the second row is {@link #LOOT_ROW_SPACING} pixels lower. */
    public static final int LOOT_FIRST_ROW_Y = 150;
    public static final int LOOT_ROW_SPACING = 18;
    public static final int LOOT_COLUMNS = 9;
    public static final int LOOT_ROWS = 2;
    public static final int LOOT_SLOT_X = GuiMetrics.PLAYER_X;
    public static final int LOOT_SLOT_SPACING = 18;
    /** Shown only while the machine is paused, in the free band under the loot rows. */
    public static final int PAUSED_HINT_Y = 194;

    private QuarryLayout() {
    }

    /** Position of the motor slot for the current page, or {@code null} when it is hidden. */
    public static Rect upgradeSlot(int slotIndex, int tab) {
        if (tab != TAB_SETTINGS) {
            return null;
        }
        if (slotIndex == 0) {
            return MOTOR_SLOT;
        }
        if (slotIndex == 1) {
            return BATTERY_SLOT;
        }
        return null;
    }

    /** Position of an output slot (index within the output inventory), or {@code null}. */
    public static Rect outputSlot(int outputIndex, int tab) {
        if (tab != TAB_STATUS || outputIndex < 0 || outputIndex >= LOOT_COLUMNS * LOOT_ROWS) {
            return null;
        }
        int row = outputIndex / LOOT_COLUMNS;
        int column = outputIndex % LOOT_COLUMNS;
        return new Rect(
                LOOT_SLOT_X + column * LOOT_SLOT_SPACING,
                LOOT_FIRST_ROW_Y + row * LOOT_ROW_SPACING,
                16,
                16);
    }

    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    /** Axis-aligned GUI rectangle. */
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public boolean intersects(Rect other) {
            return this.x < other.right() && other.x < this.right()
                    && this.y < other.bottom() && other.y < this.bottom();
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= this.x && pointX < this.right()
                    && pointY >= this.y && pointY < this.bottom();
        }
    }
}
