package com.industrialcore.menu;

import com.industrialcore.machine.LargeQuarryBalance;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry test of the large quarry GUI (Phase 20, extended in Phase 22).
 *
 * <p>The actual drawing still needs an in-game check, but the numbers can be tested: no slot may
 * overlap another slot, a button or a text row, and every element must stay inside the panel.</p>
 */
class LargeQuarryLayoutTest {

    @Test
    void slotsDoNotOverlapAndStayInsideThePanel() {
        List<Rect> rects = new ArrayList<>();
        for (int index = 0; index < LargeQuarryBalance.OUTPUT_SLOTS; index++) {
            rects.add(LargeQuarryLayout.outputSlot(index));
        }
        for (int index = 0; index < LargeQuarryBalance.MAX_TOOL_SLOTS; index++) {
            rects.add(LargeQuarryLayout.toolSlot(index));
        }
        rects.add(LargeQuarryLayout.UPGRADE_SLOT);
        rects.add(LargeQuarryLayout.BATTERY_SLOT);
        rects.add(new Rect(LargeQuarryLayout.BATTERY_LABEL_X, LargeQuarryLayout.BATTERY_LABEL_Y, 64, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(LargeQuarryLayout.playerSlot(index));
        }
        assertNoOverlaps(rects);
        assertInsidePanel(rects);
    }

    @Test
    void theOutputRowsAreSeparatedFromTheToolRowAndThePlayerInventory() {
        Rect lastOutput = LargeQuarryLayout.outputSlot(17);
        Rect firstTool = LargeQuarryLayout.toolSlot(0);
        assertTrue(lastOutput.bottom() < firstTool.y(),
                "the second output row must stay above the pickaxes");

        Rect lastTool = LargeQuarryLayout.toolSlot(LargeQuarryBalance.MAX_TOOL_SLOTS - 1);
        assertTrue(lastTool.bottom() < LargeQuarryLayout.playerSlot(0).y(),
                "the pickaxe row must stay above the player inventory");
        assertTrue(LargeQuarryLayout.UPGRADE_SLOT.y() >= LargeQuarryLayout.toolSlot(0).y(),
                "the motor slot shares the pickaxe row");
        assertFalse(LargeQuarryLayout.UPGRADE_SLOT.intersects(lastTool),
                "the motor slot must not overlap the last pickaxe slot");

        Rect lastHotbar = LargeQuarryLayout.playerSlot(35);
        assertTrue(lastHotbar.bottom() <= LargeQuarryLayout.HEIGHT,
                "the hotbar must fit inside the panel");
        assertTrue(LargeQuarryLayout.playerSlot(27).y()
                        > LargeQuarryLayout.playerSlot(26).bottom(),
                "the hotbar must not overlap the third inventory row");
    }

    @Test
    void textRowsFollowTheBarsWithoutCollidingWithTheSlots() {
        assertTrue(LargeQuarryLayout.ENERGY_TEXT_Y >= LargeQuarryLayout.ENERGY_BAR.bottom(),
                "the FE text must stay below the FE bar");
        // Phase 43: the state moved into the shared status strip above the body.
        assertTrue(LargeQuarryLayout.STATUS_TEXT_Y + 8 <= LargeQuarryLayout.ENERGY_BAR.y(),
                "the status strip must stay above the FE bar");
        assertTrue(LargeQuarryLayout.START_BUTTON.y() >= GuiMetrics.STATUS_TOP
                        + GuiMetrics.STATUS_HEIGHT,
                "the control row must start below the status strip");
        assertTrue(LargeQuarryLayout.PAUSED_HINT_Y > LargeQuarryLayout.ENERGY_TEXT_Y,
                "the paused hint must be below the FE text");
        assertTrue(LargeQuarryLayout.PAUSED_HINT_Y + 9 <= LargeQuarryLayout.PROGRESS_BAR.y(),
                "the paused hint must not run into the progress bar");
        assertTrue(LargeQuarryLayout.PROGRESS_BAR.bottom() <= LargeQuarryLayout.PROGRESS_TEXT_Y,
                "the progress text must stay below the progress bar");
        assertTrue(LargeQuarryLayout.PROGRESS_TEXT_Y < LargeQuarryLayout.TOOLS_TEXT_Y);
        assertTrue(LargeQuarryLayout.TOOLS_TEXT_Y < LargeQuarryLayout.ENCHANT_TEXT_Y);
        assertTrue(LargeQuarryLayout.ENCHANT_TEXT_Y + 9 <= LargeQuarryLayout.LOOT_TITLE_Y,
                "the summaries must stay above the loot header");
        assertTrue(LargeQuarryLayout.ORE_BUTTON.bottom() <= LargeQuarryLayout.ENERGY_BAR.y(),
                "the ore button must stay above the FE bar");
        assertTrue(LargeQuarryLayout.ORE_BUTTON.bottom() < LargeQuarryLayout.LOOT_TITLE_Y,
                "the ore button must stay above the loot header");
        assertTrue(LargeQuarryLayout.LOOT_TITLE_Y + 9 <= LargeQuarryLayout.LOOT_FIRST_ROW_Y,
                "the loot header must not run into the first slot row");
        assertTrue(LargeQuarryLayout.outputSlot(17).bottom()
                        < LargeQuarryLayout.TOOL_TITLE_Y,
                "the output slots must stay above the pickaxe labels");
        assertTrue(LargeQuarryLayout.TOOL_TITLE_Y + 9 <= LargeQuarryLayout.TOOL_FIRST_ROW_Y,
                "the pickaxe label must not run into the slots");
        assertTrue(LargeQuarryLayout.toolSlot(0).bottom()
                        < LargeQuarryLayout.playerSlot(0).y(),
                "the pickaxe slots must stay above the player inventory");
    }

    @Test
    void theStartButtonDoesNotCoverTheTitleOrTheSlots() {
        Rect button = LargeQuarryLayout.START_BUTTON;
        assertTrue(button.right() <= LargeQuarryLayout.ORE_BUTTON.x(),
                "the start button must stay left of the ore button");
        assertTrue(button.right() <= LargeQuarryLayout.WIDTH,
                "the button must stay inside the panel");
        assertTrue(button.y() >= GuiMetrics.TITLE_Y + 9,
                "the button must stay below the title text");
        assertTrue(button.y() < LargeQuarryLayout.ENERGY_BAR.y(),
                "the button must stay above the FE bar");
        for (int index = 0; index < LargeQuarryBalance.OUTPUT_SLOTS; index++) {
            assertFalse(button.intersects(LargeQuarryLayout.outputSlot(index)));
        }
        for (int index = 0; index < 36; index++) {
            assertFalse(button.intersects(LargeQuarryLayout.playerSlot(index)));
        }
        assertFalse(button.intersects(LargeQuarryLayout.ORE_BUTTON));
    }

    /** The ore picker button spans the panel, so a long translation still has room. */
    @Test
    void theOreButtonHasRoomForItsLabel() {
        Rect button = LargeQuarryLayout.ORE_BUTTON;
        // "Choose ores to mine" is ~100 px in the vanilla font; "鉱石を狙って選択" ~72 px.
        assertTrue(button.width() >= 120, "the ore button is too narrow for its label");
        assertTrue(button.x() >= 8 && button.right() <= LargeQuarryLayout.WIDTH - 8);
        assertTrue(button.height() >= 18, "the ore button is too small to click comfortably");
        assertTrue(button.width() > LargeQuarryLayout.START_BUTTON.width(),
                "the ore button is the primary action and must be the wider one");
    }

    // ------------------------------------------------------------------
    // Phase 23: the ore picker grid - a click must name the drawn cell
    // ------------------------------------------------------------------

    @Test
    void theOrePickerGridFitsThePanelAndStaysAboveItsButtons() {
        assertTrue(LargeQuarryLayout.ORE_PICKER_GRID_X
                        + LargeQuarryLayout.ORE_PICKER_COLUMNS * LargeQuarryLayout.ORE_PICKER_CELL
                        <= LargeQuarryLayout.ORE_PICKER_WIDTH,
                "the grid must fit into the panel width");
        int gridBottom = LargeQuarryLayout.ORE_PICKER_GRID_Y
                + LargeQuarryLayout.ORE_PICKER_ROWS * LargeQuarryLayout.ORE_PICKER_CELL;
        assertTrue(gridBottom <= LargeQuarryLayout.ORE_PICKER_PAGE_ROW_Y,
                "the grid must stay above the page buttons but ended at " + gridBottom);
        assertTrue(LargeQuarryLayout.ORE_PICKER_PAGE_ROW_Y + 16
                        <= LargeQuarryLayout.ORE_PICKER_ACTION_ROW_Y,
                "the two picker button rows must not touch");
        assertTrue(LargeQuarryLayout.ORE_PICKER_ACTION_ROW_Y + 18
                        <= LargeQuarryLayout.ORE_PICKER_HEIGHT,
                "the action row must fit inside the picker panel");
        assertTrue(LargeQuarryLayout.ORE_PICKER_GRID_X >= 8
                        && LargeQuarryLayout.ORE_PICKER_GRID_X
                        + LargeQuarryLayout.ORE_PICKER_COLUMNS * LargeQuarryLayout.ORE_PICKER_CELL
                        <= LargeQuarryLayout.ORE_PICKER_WIDTH - 8,
                "the ore grid must stay inside the content margin");
        assertTrue(LargeQuarryLayout.ORE_PICKER_PAGE_SIZE
                == LargeQuarryLayout.ORE_PICKER_COLUMNS * LargeQuarryLayout.ORE_PICKER_ROWS);
    }

    @Test
    void orePickerClicksNameTheDrawnCellOnEveryPage() {
        int left = 100;
        int top = 50;
        int gridX = left + LargeQuarryLayout.ORE_PICKER_GRID_X;
        int gridY = top + LargeQuarryLayout.ORE_PICKER_GRID_Y;
        int cell = LargeQuarryLayout.ORE_PICKER_CELL;
        int columns = LargeQuarryLayout.ORE_PICKER_COLUMNS;
        int rows = LargeQuarryLayout.ORE_PICKER_ROWS;
        int pageSize = LargeQuarryLayout.ORE_PICKER_PAGE_SIZE;

        // first cell of page 0, and its last cell
        assertEquals(0, LargeQuarryLayout.orePickerIndexAt(gridX + 1, gridY + 1, left, top, 0, 54));
        int lastOnPage = (rows - 1) * columns + (columns - 1);
        assertEquals(lastOnPage, LargeQuarryLayout.orePickerIndexAt(
                gridX + (columns - 1) * cell + 1, gridY + (rows - 1) * cell + 1,
                left, top, 0, 54));

        // one pixel outside every edge must not hit a cell
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(gridX - 1, gridY + 1, left, top, 0, 54));
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(gridX + 1, gridY - 1, left, top, 0, 54));
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(gridX + columns * cell, gridY + 1,
                left, top, 0, 54));
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(gridX + 1, gridY + rows * cell,
                left, top, 0, 54));

        // the same cell on page 1 names the next block of the list
        assertEquals(pageSize, LargeQuarryLayout.orePickerIndexAt(gridX + 1, gridY + 1,
                left, top, 1, 200));

        // a partially filled last page: cell 6 of page 1 exists only with 61 candidates
        int x = gridX + 6 * cell + 1;
        int y = gridY + 1;
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(x, y, left, top, 1, 60));
        assertEquals(pageSize + 6, LargeQuarryLayout.orePickerIndexAt(x, y, left, top, 1, 61));

        // an empty list never resolves
        assertEquals(-1, LargeQuarryLayout.orePickerIndexAt(gridX + 1, gridY + 1,
                left, top, 0, 0));
    }

    private static void assertNoOverlaps(List<Rect> rects) {
        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                Rect a = rects.get(first);
                Rect b = rects.get(second);
                assertFalse(a.intersects(b), a + " overlaps " + b);
            }
        }
    }

    private static void assertInsidePanel(List<Rect> rects) {
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0, rect + " starts outside the panel");
            assertTrue(rect.right() <= LargeQuarryLayout.WIDTH,
                    rect + " exceeds the panel width");
            assertTrue(rect.bottom() <= LargeQuarryLayout.HEIGHT,
                    rect + " exceeds the panel height");
        }
    }
}
