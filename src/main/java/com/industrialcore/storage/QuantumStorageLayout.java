package com.industrialcore.storage;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Pure-Java GUI geometry for the Quantum Storage Core dashboard.
 *
 * <p>Main panel is authored at 278x262 (crisp 1.0x native scale at 1280x720 guiScale 2) featuring:
 * <ul>
 *   <li>Header with live type usage &amp; luxury color-coded per-type cap + Skill Tree button</li>
 *   <li>Single-row toolbar: filter tabs, right-click-clearable search, JEI sync, sort, JP/EN unit</li>
 *   <li>Wide 13x6 (78 visible cells) scrollable storage grid + draggable scrollbar</li>
 *   <li>Side-by-side lower bay: 3x3 auto-refill crafting bay (left) + 9x4 player inventory (right)</li>
 *   <li>Collapsible right-side Capacity Core dock (2x4 slots, 52px wide) reported to JEI via extra areas</li>
 * </ul></p>
 */
public final class QuantumStorageLayout {
    public static final int WIDTH = 278;
    public static final int HEIGHT = 262;

    // Header band (y = 0..24)
    public static final int HEADER_HEIGHT = 24;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 8;
    public static final Rect HEADER_STATS = new Rect(106, 5, 148, 14);
    public static final Rect SKILL_TREE_BUTTON = new Rect(WIDTH - 20, 5, 14, 14);

    // Top toolbar row (y = 28..42, exact full Japanese labels with zero ellipsis)
    public static final Rect TAB_ALL = new Rect(8, 28, 32, 14);
    public static final Rect TAB_ITEMS = new Rect(42, 28, 40, 14);
    public static final Rect TAB_FLUIDS = new Rect(84, 28, 24, 14);
    public static final Rect SEARCH_BOX = new Rect(111, 28, 74, 14);
    public static final Rect JEI_SYNC_BUTTON = new Rect(188, 28, 22, 14);
    public static final Rect SORT_BUTTON = new Rect(213, 28, 28, 14);
    public static final Rect UNIT_BUTTON = new Rect(244, 28, 26, 14);

    // Wide scrollable main quantum storage grid (13 cols x 6 rows = 78 visible cells)
    public static final int GRID_X = 12;
    public static final int GRID_Y = 48;
    public static final int GRID_COLS = 13;
    public static final int GRID_ROWS = 6;
    public static final int GRID_VISIBLE = GRID_COLS * GRID_ROWS; // 78
    public static final int SLOT_STRIDE = 18;
    public static final Rect GRID_VIEWPORT = new Rect(GRID_X - 2, GRID_Y - 2,
            GRID_COLS * SLOT_STRIDE + 2, GRID_ROWS * SLOT_STRIDE + 2); // (10, 46, 236, 110)
    public static final Rect SCROLLBAR = new Rect(254, 46, 14, 110);

    // Lower-left built-in 3x3 auto-refill crafting bay (x = 8..104, y = 162..250)
    public static final int CRAFT_LABEL_X = 8;
    public static final int CRAFT_LABEL_Y = 162;
    public static final int CRAFT_X = 10;
    public static final int CRAFT_Y = 174;
    public static final Rect CRAFT_GROUP = new Rect(CRAFT_X - 2, CRAFT_Y - 2, 56, 56);
    public static final Rect CRAFT_ARROW = new Rect(66, 195, 13, 10);
    public static final Rect CRAFT_OUTPUT = new Rect(84, 192, 16, 16);
    public static final Rect CLEAR_CRAFT_BUTTON = new Rect(8, 234, 46, 16);
    public static final Rect DEPOSIT_ALL_BUTTON = new Rect(56, 234, 48, 16);

    // Lower-right player inventory (x = 110..270, y = 162..250)
    public static final int PLAYER_INV_X = 110;
    public static final int PLAYER_INV_LABEL_Y = 162;
    public static final int PLAYER_INV_Y = 174;
    public static final int HOTBAR_Y = 234;

    // Collapsible right-side Capacity Core dock (attached at x = WIDTH, outside main 278px frame)
    public static final Rect CORE_DOCK = new Rect(WIDTH, 24, 52, 134);
    public static final Rect CORE_TAB_OPEN = new Rect(WIDTH + 4, 28, 44, 14);
    public static final Rect CORE_TAB_COLLAPSED = new Rect(WIDTH, 28, 18, 46);
    public static final int CORE_GRID_X = WIDTH + 9;
    public static final int CORE_GRID_Y = 46;
    public static final int CORE_COLS = 2;
    public static final int CORE_ROWS = 4;
    public static final Rect USAGE_BAR = new Rect(WIDTH + 6, 124, 40, 6);
    public static final int CORE_COUNT_Y = 136;

    // Phase 96: Frequency Network side tab & dock (attached cleanly outside main 278px frame)
    public static final int DOCK_CLOSED = 0;
    public static final int DOCK_CORE = 1;
    public static final int DOCK_FREQ = 2;

    public static final Rect FREQ_TAB_COLLAPSED = new Rect(WIDTH, 78, 18, 46);
    public static final Rect FREQ_TAB_BELOW_CORE = new Rect(WIDTH, 160, 52, 16);

    public static final Rect FREQ_DOCK = new Rect(WIDTH, 24, 118, 178);
    public static final Rect FREQ_TAB_TO_CORE = new Rect(WIDTH + 4, 28, 38, 14);
    public static final Rect FREQ_TAB_CLOSE = new Rect(WIDTH + 44, 28, 70, 14);
    public static final Rect FREQ_CURRENT_PILL = new Rect(WIDTH + 4, 46, 74, 14);
    public static final Rect FREQ_BTN_DISCONNECT = new Rect(WIDTH + 80, 46, 34, 14);
    public static final int FREQ_VISIBLE_ROWS = 4;
    public static final int FREQ_ROW_HEIGHT = 18;
    public static final Rect FREQ_LIST_BOX = new Rect(WIDTH + 4, 64, 98, FREQ_VISIBLE_ROWS * FREQ_ROW_HEIGHT); // y=64..136
    public static final Rect FREQ_BTN_UP = new Rect(WIDTH + 104, 64, 10, 14);
    public static final Rect FREQ_BTN_DOWN = new Rect(WIDTH + 104, 122, 10, 14);
    public static final Rect FREQ_BTN_CONNECT = new Rect(WIDTH + 4, 140, 36, 14);
    public static final Rect FREQ_BTN_PUBLIC = new Rect(WIDTH + 42, 140, 36, 14);
    public static final Rect FREQ_BTN_DELETE = new Rect(WIDTH + 80, 140, 34, 14);
    public static final Rect FREQ_NAME_BOX = new Rect(WIDTH + 4, 158, 76, 14);
    public static final Rect FREQ_BTN_CREATE = new Rect(WIDTH + 82, 158, 32, 14);
    public static final Rect FREQ_HINT_BAR = new Rect(WIDTH + 4, 176, 110, 20);

    public static final int FILTER_ALL = 0;
    public static final int FILTER_ITEMS = 1;
    public static final int FILTER_FLUIDS = 2;

    public static final int SORT_COUNT = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_MOD = 2;

    private QuantumStorageLayout() {}

    public static Rect gridCell(int visibleIndex) {
        int col = visibleIndex % GRID_COLS;
        int row = visibleIndex / GRID_COLS;
        return new Rect(GRID_X + col * SLOT_STRIDE, GRID_Y + row * SLOT_STRIDE, 16, 16);
    }

    public static Rect coreSlot(int index) {
        int col = index % CORE_COLS;
        int row = index / CORE_COLS;
        return new Rect(CORE_GRID_X + col * SLOT_STRIDE, CORE_GRID_Y + row * SLOT_STRIDE, 16, 16);
    }

    public static Rect craftSlot(int index) {
        int col = index % 3;
        int row = index / 3;
        return new Rect(CRAFT_X + col * SLOT_STRIDE, CRAFT_Y + row * SLOT_STRIDE, 16, 16);
    }

    public static Rect playerSlot(int index) {
        if (index < 27) {
            int col = index % 9;
            int row = index / 9;
            return new Rect(PLAYER_INV_X + col * SLOT_STRIDE,
                    PLAYER_INV_Y + row * SLOT_STRIDE, 16, 16);
        }
        int col = index - 27;
        return new Rect(PLAYER_INV_X + col * SLOT_STRIDE, HOTBAR_Y, 16, 16);
    }

    /** Active extra area rectangle outside the main panel (legacy helper kept for compatibility). */
    public static Rect activeSideArea(boolean coreDockOpen) {
        return coreDockOpen ? CORE_DOCK : CORE_TAB_COLLAPSED;
    }

    /** Phase 96: Active side dock bounding box for JEI collision avoidance and click bounds. */
    public static Rect activeSideArea(int dockMode, boolean portable) {
        if (dockMode == DOCK_FREQ) {
            return FREQ_DOCK;
        }
        if (dockMode == DOCK_CORE && !portable) {
            return new Rect(WIDTH, CORE_DOCK.y(), CORE_DOCK.width(),
                    FREQ_TAB_BELOW_CORE.bottom() - CORE_DOCK.y());
        }
        // Collapsed tab(s)
        if (portable) {
            return FREQ_TAB_COLLAPSED;
        }
        return new Rect(WIDTH, CORE_TAB_COLLAPSED.y(), CORE_TAB_COLLAPSED.width(),
                FREQ_TAB_COLLAPSED.bottom() - CORE_TAB_COLLAPSED.y());
    }

    public static Rect freqListRow(int visibleIndex) {
        return new Rect(
                FREQ_LIST_BOX.x() + 1,
                FREQ_LIST_BOX.y() + visibleIndex * FREQ_ROW_HEIGHT + 1,
                FREQ_LIST_BOX.width() - 2,
                FREQ_ROW_HEIGHT - 2);
    }

    /** Hit-tests a panel-local coordinate against the 78 visible storage grid cells, or -1. */
    public static int gridCellAt(double localX, double localY) {
        for (int i = 0; i < GRID_VISIBLE; i++) {
            Rect r = gridCell(i);
            if (localX >= r.x() - 1 && localX <= r.right() + 1
                    && localY >= r.y() - 1 && localY <= r.bottom() + 1) {
                return i;
            }
        }
        return -1;
    }

    public static int maxScrollRows(int totalEntries) {
        int totalRows = (totalEntries + GRID_COLS - 1) / GRID_COLS;
        return Math.max(0, totalRows - GRID_ROWS);
    }

    /** Maps a panel-local Y position on {@link #SCROLLBAR} to a clamped scroll row index. */
    public static int scrollRowForLocalY(double localY, int totalEntries) {
        int maxScroll = maxScrollRows(totalEntries);
        if (maxScroll <= 0) {
            return 0;
        }
        double trackTop = SCROLLBAR.y() + 6.0D;
        double trackSpan = Math.max(1.0D, SCROLLBAR.height() - 12.0D);
        double ratio = Math.max(0.0D, Math.min(1.0D, (localY - trackTop) / trackSpan));
        return (int) Math.round(ratio * maxScroll);
    }
}
