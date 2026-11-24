package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the large quarry GUI (Phase 20, extended in Phase 22, re-gridded in Phase 43),
 * shared by the menu (slot positions) and the screen (drawing and buttons).
 *
 * <p>Phase 43 puts the panel on {@link GuiMetrics}: the machine name in the 30 px header band, the
 * run state in the shared status strip, then a body that keeps one row per question a player asks —
 * how much FE, how far along, what is selected, what comes out, what goes in.</p>
 *
 * <pre>
 *   y  58  [Start/Stop] [Choose ores to mine]
 *   y  82  FE bar                    y  92  stored / capacity
 *   y 104  paused hint (only while paused)
 *   y 116  progress bar              y 126  progress (left) and cost (right)
 *   y 140  ores / pickaxes selected  y 152  enchants
 *   y 168  "Collected items"         y 178  two rows of nine output slots (178, 196)
 *   y 220  "Pickaxes" / "Motor"      y 230  eight pickaxe slots and the motor slot
 *   y 250  body bottom
 * </pre>
 */
public final class LargeQuarryLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;
    /** Bottom of the machine body; the shared chrome takes over below it. */
    public static final int BODY_BOTTOM = 276;
    public static final int HEIGHT = GuiMetrics.height(BODY_BOTTOM);

    /** Always visible: starts or stops the current run. */
    public static final Rect START_BUTTON = new Rect(GuiMetrics.SIDE, 58, 44, 18);

    public static final Rect ENERGY_BAR = new Rect(GuiMetrics.SIDE, 82, GuiMetrics.CONTENT, 7);
    public static final int ENERGY_TEXT_Y = 92;
    /** Baseline top of the state line (the shared status strip). */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;
    /** Second status line, drawn only while the machine is paused. */
    public static final int PAUSED_HINT_Y = 104;
    public static final Rect PROGRESS_BAR = new Rect(GuiMetrics.SIDE, 116, GuiMetrics.CONTENT, 6);
    public static final int PROGRESS_TEXT_Y = 126;
    public static final int TOOLS_TEXT_Y = 140;
    public static final int ENCHANT_TEXT_Y = 152;

    /** Opens the paginated ore picker; it does not change anything by itself. */
    public static final Rect ORE_BUTTON = new Rect(60, 58, 124, 18);

    public static final int LOOT_TITLE_Y = 168;
    public static final int LOOT_FIRST_ROW_Y = 178;
    public static final int LOOT_ROW_SPACING = 18;
    public static final int LOOT_COLUMNS = 9;
    public static final int LOOT_ROWS = 2;
    public static final int LOOT_SLOT_X = GuiMetrics.PLAYER_X;
    public static final int LOOT_SLOT_SPACING = 18;

    public static final int TOOL_TITLE_Y = 220;
    public static final int TOOL_FIRST_ROW_Y = 230;
    public static final int TOOL_SLOT_X = GuiMetrics.SIDE;
    public static final int TOOL_SLOT_SPACING = 18;
    public static final int TOOL_SLOTS_PER_ROW = 8;

    /** Motor slot: each consumed motor unlocks one more pickaxe arm. */
    public static final Rect UPGRADE_SLOT = new Rect(168, 230, 16, 16);
    /** Label above the motor slot; clear of the "Pickaxes" title on the same row. */
    public static final int UPGRADE_LABEL_X = 126;

    /** Phase 87: Battery slot placed cleanly below the pickaxe row with inline label. */
    public static final Rect BATTERY_SLOT = new Rect(GuiMetrics.SIDE, 254, 16, 16);
    public static final int BATTERY_LABEL_X = BATTERY_SLOT.right() + 6;
    public static final int BATTERY_LABEL_Y = BATTERY_SLOT.y() + 4;

    private LargeQuarryLayout() {
    }

    /** Rectangle of an output slot (index within the output inventory). */
    public static Rect outputSlot(int index) {
        int row = index / LOOT_COLUMNS;
        int column = index % LOOT_COLUMNS;
        return new Rect(
                LOOT_SLOT_X + column * LOOT_SLOT_SPACING,
                LOOT_FIRST_ROW_Y + row * LOOT_ROW_SPACING,
                16,
                16);
    }

    /** Rectangle of a pickaxe slot (index within the pickaxe inventory). */
    public static Rect toolSlot(int index) {
        int row = index / TOOL_SLOTS_PER_ROW;
        int column = index % TOOL_SLOTS_PER_ROW;
        return new Rect(
                TOOL_SLOT_X + column * TOOL_SLOT_SPACING,
                TOOL_FIRST_ROW_Y + row * LOOT_ROW_SPACING,
                16,
                16);
    }

    /** Rectangle of a player inventory slot (0..35; 0..26 are the rows, 27..35 the hotbar). */
    public static Rect playerSlot(int index) {
        return GuiMetrics.playerSlot(BODY_BOTTOM, index);
    }

    // ------------------------------------------------------------------
    // Ore picker screen (Phase 22; geometry fixed and made testable in Phase 23)
    // ------------------------------------------------------------------

    /** Panel size of the ore picker screen. */
    public static final int ORE_PICKER_WIDTH = GuiMetrics.WIDTH;
    public static final int ORE_PICKER_HEIGHT = 204;

    /** Grid of one page: 9 x 6 cells of 18 px, starting at (17, 46) inside the panel. */
    public static final int ORE_PICKER_COLUMNS = 9;
    public static final int ORE_PICKER_ROWS = 6;
    public static final int ORE_PICKER_PAGE_SIZE = ORE_PICKER_COLUMNS * ORE_PICKER_ROWS;
    public static final int ORE_PICKER_GRID_X = 17;
    public static final int ORE_PICKER_GRID_Y = 46;
    public static final int ORE_PICKER_CELL = 18;
    /** Baseline top of the "selected n / m" line, right under the header band. */
    public static final int ORE_PICKER_COUNT_Y = 34;
    /** Row of the two page arrows. */
    public static final int ORE_PICKER_PAGE_ROW_Y = 156;
    /** Row of the All / None / Close buttons. */
    public static final int ORE_PICKER_ACTION_ROW_Y = 178;

    /**
     * Index of the candidate block under a mouse position, or {@code -1} when the position is not
     * on a filled cell. Drawing and clicking share this geometry, so a click always names the
     * block whose icon is drawn there - on every page, including the last, partially filled one.
     *
     * @param screenLeft     left edge of the panel on screen
     * @param screenTop      top edge of the panel on screen
     * @param page           currently shown page (0 based)
     * @param candidateCount number of candidates the list currently has
     */
    public static int orePickerIndexAt(double mouseX, double mouseY, int screenLeft, int screenTop,
                                       int page, int candidateCount) {
        double x = mouseX - screenLeft - ORE_PICKER_GRID_X;
        double y = mouseY - screenTop - ORE_PICKER_GRID_Y;
        if (x < 0.0D || y < 0.0D || page < 0) {
            return -1;
        }
        int column = (int) (x / ORE_PICKER_CELL);
        int row = (int) (y / ORE_PICKER_CELL);
        if (column >= ORE_PICKER_COLUMNS || row >= ORE_PICKER_ROWS) {
            return -1;
        }
        int index = page * ORE_PICKER_PAGE_SIZE + row * ORE_PICKER_COLUMNS + column;
        return index < candidateCount ? index : -1;
    }
}
