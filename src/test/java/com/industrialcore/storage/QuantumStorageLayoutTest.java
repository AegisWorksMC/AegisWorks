package com.industrialcore.storage;

import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry guard for the {@link QuantumStorageLayout}: zero overlapping
 * controls, wide 13x6 grid viewport, draggable scrollbar, side-by-side 3x3 crafting bay &amp;
 * player inventory, and collapsible right-side Capacity Core dock (outside main panel bounds).
 */
class QuantumStorageLayoutTest {

    @Test
    void allMainPanelControlsSlotsAndPanelsAreNonOverlappingAndInsideBounds() {
        List<Rect> rects = new ArrayList<>();
        // Header strip
        rects.add(new Rect(QuantumStorageLayout.TITLE_X, QuantumStorageLayout.TITLE_Y, 94, 9));
        rects.add(QuantumStorageLayout.HEADER_STATS);
        rects.add(QuantumStorageLayout.SKILL_TREE_BUTTON);

        // Top toolbar
        rects.add(QuantumStorageLayout.TAB_ALL);
        rects.add(QuantumStorageLayout.TAB_ITEMS);
        rects.add(QuantumStorageLayout.TAB_FLUIDS);
        rects.add(QuantumStorageLayout.SEARCH_BOX);
        rects.add(QuantumStorageLayout.JEI_SYNC_BUTTON);
        rects.add(QuantumStorageLayout.SORT_BUTTON);
        rects.add(QuantumStorageLayout.UNIT_BUTTON);

        // Wide 13x6 storage grid viewport + draggable scrollbar
        rects.add(QuantumStorageLayout.GRID_VIEWPORT);
        rects.add(QuantumStorageLayout.SCROLLBAR);

        // Lower-left built-in 3x3 crafting bay + arrow + output + buttons
        rects.add(new Rect(QuantumStorageLayout.CRAFT_LABEL_X, QuantumStorageLayout.CRAFT_LABEL_Y, 64, 9));
        rects.add(QuantumStorageLayout.CRAFT_GROUP);
        rects.add(QuantumStorageLayout.CRAFT_ARROW);
        rects.add(new Rect(QuantumStorageLayout.CRAFT_OUTPUT.x() - 4,
                QuantumStorageLayout.CRAFT_OUTPUT.y() - 4, 24, 24));
        rects.add(QuantumStorageLayout.CLEAR_CRAFT_BUTTON);
        rects.add(QuantumStorageLayout.DEPOSIT_ALL_BUTTON);

        // Lower-right player inventory label + 36 slots
        rects.add(new Rect(QuantumStorageLayout.PLAYER_INV_X,
                QuantumStorageLayout.PLAYER_INV_LABEL_Y, 80, 9));
        for (int i = 0; i < 36; i++) {
            rects.add(QuantumStorageLayout.playerSlot(i));
        }

        assertNoOverlaps(rects, "main panel");
        assertInsidePanel(rects);
    }

    @Test
    void storageGridCraftSlotsAndRightCoreDockAreNonOverlappingAndExact() {
        // 78 visible storage cells inside GRID_VIEWPORT (13 cols x 6 rows)
        assertEquals(78, QuantumStorageLayout.GRID_VISIBLE);
        List<Rect> gridCells = new ArrayList<>();
        for (int i = 0; i < QuantumStorageLayout.GRID_VISIBLE; i++) {
            Rect r = QuantumStorageLayout.gridCell(i);
            assertInside(QuantumStorageLayout.GRID_VIEWPORT, r, "gridCell " + i);
            gridCells.add(r);
        }
        assertNoOverlaps(gridCells, "storage grid cells");

        // 9 craft slots inside CRAFT_GROUP
        List<Rect> craftSlots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Rect r = QuantumStorageLayout.craftSlot(i);
            assertInside(QuantumStorageLayout.CRAFT_GROUP, r, "craftSlot " + i);
            craftSlots.add(r);
        }
        assertNoOverlaps(craftSlots, "craft 3x3 slots");

        // Collapsible right-side Capacity Core dock sits cleanly outside main panel (>= WIDTH)
        Rect mainPanel = new Rect(0, 0, QuantumStorageLayout.WIDTH, QuantumStorageLayout.HEIGHT);
        assertFalse(mainPanel.intersects(QuantumStorageLayout.CORE_DOCK),
                "CORE_DOCK must attach cleanly outside main panel");
        assertFalse(mainPanel.intersects(QuantumStorageLayout.CORE_TAB_COLLAPSED),
                "CORE_TAB_COLLAPSED must attach cleanly outside main panel");

        // All dock children (close tab, 8 core slots, usage bar, count label) fit inside CORE_DOCK without overlap
        List<Rect> dockElems = new ArrayList<>();
        dockElems.add(QuantumStorageLayout.CORE_TAB_OPEN);
        for (int i = 0; i < StorageCoreTier.CORE_SLOT_COUNT; i++) {
            dockElems.add(QuantumStorageLayout.coreSlot(i));
        }
        dockElems.add(QuantumStorageLayout.USAGE_BAR);
        dockElems.add(new Rect(QuantumStorageLayout.CORE_DOCK.x() + 6,
                QuantumStorageLayout.CORE_COUNT_Y, 40, 9));
        for (Rect r : dockElems) {
            assertInside(QuantumStorageLayout.CORE_DOCK, r, "coreDock child");
        }
        assertNoOverlaps(dockElems, "core dock internals");
    }

    @Test
    void gridHitTestingScrollBoundsAndDragMappingAreExact() {
        Rect first = QuantumStorageLayout.gridCell(0);
        assertEquals(0, QuantumStorageLayout.gridCellAt(first.x() + 4, first.y() + 4));
        Rect last = QuantumStorageLayout.gridCell(QuantumStorageLayout.GRID_VISIBLE - 1);
        assertEquals(QuantumStorageLayout.GRID_VISIBLE - 1,
                QuantumStorageLayout.gridCellAt(last.x() + 4, last.y() + 4));
        assertEquals(-1, QuantumStorageLayout.gridCellAt(0, 0));

        assertEquals(0, QuantumStorageLayout.maxScrollRows(0));
        assertEquals(0, QuantumStorageLayout.maxScrollRows(78));
        assertEquals(1, QuantumStorageLayout.maxScrollRows(79));
        assertEquals(2, QuantumStorageLayout.maxScrollRows(92));

        // Draggable scrollbar mapping clamps cleanly to [0, maxScrollRows]
        assertEquals(0, QuantumStorageLayout.scrollRowForLocalY(
                QuantumStorageLayout.SCROLLBAR.y(), 200));
        assertEquals(QuantumStorageLayout.maxScrollRows(200),
                QuantumStorageLayout.scrollRowForLocalY(QuantumStorageLayout.SCROLLBAR.bottom(), 200));
        assertEquals(QuantumStorageLayout.CORE_DOCK, QuantumStorageLayout.activeSideArea(true));
        assertEquals(QuantumStorageLayout.CORE_TAB_COLLAPSED, QuantumStorageLayout.activeSideArea(false));
    }

    @Test
    void frequencyDockAndSideTabsStayOutsideMainPanelWithoutInternalOverlaps() {
        Rect mainPanel = new Rect(0, 0, QuantumStorageLayout.WIDTH, QuantumStorageLayout.HEIGHT);
        assertFalse(mainPanel.intersects(QuantumStorageLayout.FREQ_DOCK));
        assertFalse(mainPanel.intersects(QuantumStorageLayout.FREQ_TAB_COLLAPSED));
        assertFalse(mainPanel.intersects(QuantumStorageLayout.FREQ_TAB_BELOW_CORE));
        assertFalse(QuantumStorageLayout.CORE_DOCK.intersects(QuantumStorageLayout.FREQ_TAB_BELOW_CORE));
        assertFalse(QuantumStorageLayout.CORE_TAB_COLLAPSED.intersects(QuantumStorageLayout.FREQ_TAB_COLLAPSED));

        List<Rect> freqElems = List.of(
                QuantumStorageLayout.FREQ_TAB_TO_CORE,
                QuantumStorageLayout.FREQ_TAB_CLOSE,
                QuantumStorageLayout.FREQ_CURRENT_PILL,
                QuantumStorageLayout.FREQ_BTN_DISCONNECT,
                QuantumStorageLayout.FREQ_LIST_BOX,
                QuantumStorageLayout.FREQ_BTN_UP,
                QuantumStorageLayout.FREQ_BTN_DOWN,
                QuantumStorageLayout.FREQ_BTN_CONNECT,
                QuantumStorageLayout.FREQ_BTN_PUBLIC,
                QuantumStorageLayout.FREQ_BTN_DELETE,
                QuantumStorageLayout.FREQ_NAME_BOX,
                QuantumStorageLayout.FREQ_BTN_CREATE,
                QuantumStorageLayout.FREQ_HINT_BAR);
        for (Rect r : freqElems) {
            assertInside(QuantumStorageLayout.FREQ_DOCK, r, "freqDock child");
        }
        assertNoOverlaps(freqElems, "freq dock internals");

        List<Rect> rows = new ArrayList<>();
        for (int i = 0; i < QuantumStorageLayout.FREQ_VISIBLE_ROWS; i++) {
            Rect row = QuantumStorageLayout.freqListRow(i);
            assertInside(QuantumStorageLayout.FREQ_LIST_BOX, row, "freqRow " + i);
            rows.add(row);
        }
        assertNoOverlaps(rows, "freq list rows");

        // JEI extra area covers active dock in every mode (block core & portable)
        assertEquals(QuantumStorageLayout.FREQ_DOCK,
                QuantumStorageLayout.activeSideArea(QuantumStorageLayout.DOCK_FREQ, false));
        assertEquals(QuantumStorageLayout.FREQ_DOCK,
                QuantumStorageLayout.activeSideArea(QuantumStorageLayout.DOCK_FREQ, true));
        assertTrue(QuantumStorageLayout.activeSideArea(QuantumStorageLayout.DOCK_CORE, false)
                .bottom() >= QuantumStorageLayout.FREQ_TAB_BELOW_CORE.bottom());
    }

    private static void assertNoOverlaps(List<Rect> rects, String where) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(rects.get(i).intersects(rects.get(j)),
                        where + ": " + rects.get(i) + " overlaps " + rects.get(j));
            }
        }
    }

    private static void assertInside(Rect outer, Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                label + ": " + inner + " escapes " + outer);
    }

    private static void assertInsidePanel(List<Rect> rects) {
        Rect panel = new Rect(0, 0, QuantumStorageLayout.WIDTH, QuantumStorageLayout.HEIGHT);
        for (Rect r : rects) {
            assertInside(panel, r, "panel");
        }
    }
}
