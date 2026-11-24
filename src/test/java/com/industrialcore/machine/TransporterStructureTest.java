package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the multiblock shell geometry (Phases 26, 27, 29).
 *
 * <p>The 3x3x3 case is checked against a fully hand-written list of the 25 world positions, so a
 * change of the shared direction math cannot silently redefine "valid structure" for everything
 * at once; the 4x4x4 and 5x5x5 cases pin the counts, the controller identity and spot-checked
 * cells. Directions use the vanilla indices (2=NORTH, 3=SOUTH, 4=WEST, 5=EAST).</p>
 */
class TransporterStructureTest {

    @Test
    void cellCountsMatchTheSpecification() {
        // 3x3x3: 26 shell cells (1 controller + 25 casings) + 1 air cell.
        assertEquals(26, TransporterStructure.shellCount(3));
        assertEquals(25, TransporterStructure.expectedCasingCount(3));
        assertEquals(1, TransporterStructure.expectedAirCount(3));
        // 4x4x4: 56 shell cells (1 + 55 casings) + 8 air cells.
        assertEquals(56, TransporterStructure.shellCount(4));
        assertEquals(55, TransporterStructure.expectedCasingCount(4));
        assertEquals(8, TransporterStructure.expectedAirCount(4));
        // 5x5x5: 98 shell cells (1 + 97 casings) + 27 air cells.
        assertEquals(98, TransporterStructure.shellCount(5));
        assertEquals(97, TransporterStructure.expectedCasingCount(5));
        assertEquals(27, TransporterStructure.expectedAirCount(5));
    }

    @Test
    void theControllerSitsInTheMiddleOfTheFrontFace() {
        assertEquals(1, TransporterStructure.controllerOffset(3));
        assertEquals(1, TransporterStructure.controllerOffset(4));
        assertEquals(2, TransporterStructure.controllerOffset(5));
        assertThrows(IllegalArgumentException.class, () -> TransporterStructure.controllerOffset(2));
    }

    @Test
    void theThreeByThreeShellMatchesAHandWrittenCellList() {
        // Controller at (10, 64, 20) looking NORTH: local x runs east, local z runs south.
        List<GridPos> casings = TransporterStructure.casingCells(3, 10, 64, 20, 2);
        Set<GridPos> actual = new HashSet<>(casings);
        assertEquals(casings.size(), actual.size(), "no cell may be produced twice");

        Set<GridPos> expected = new HashSet<>();
        // Front face (z = 20): the full 3x3 minus the controller cell (10, 64, 20).
        expected.add(new GridPos(9, 63, 20));
        expected.add(new GridPos(10, 63, 20));
        expected.add(new GridPos(11, 63, 20));
        expected.add(new GridPos(9, 64, 20));
        expected.add(new GridPos(11, 64, 20));
        expected.add(new GridPos(9, 65, 20));
        expected.add(new GridPos(10, 65, 20));
        expected.add(new GridPos(11, 65, 20));
        // Middle layer (z = 21): the 8 ring cells around the air cell (10, 64, 21).
        expected.add(new GridPos(9, 63, 21));
        expected.add(new GridPos(10, 63, 21));
        expected.add(new GridPos(11, 63, 21));
        expected.add(new GridPos(9, 64, 21));
        expected.add(new GridPos(11, 64, 21));
        expected.add(new GridPos(9, 65, 21));
        expected.add(new GridPos(10, 65, 21));
        expected.add(new GridPos(11, 65, 21));
        // Back face (z = 22): all nine cells.
        for (int x = 9; x <= 11; x++) {
            for (int y = 63; y <= 65; y++) {
                expected.add(new GridPos(x, y, 22));
            }
        }

        assertEquals(expected, actual, "the 3x3x3 shell must match the hand-computed cells");
        assertFalse(actual.contains(new GridPos(10, 64, 20)),
                "the controller cell is not a casing");
        assertFalse(actual.contains(new GridPos(10, 64, 21)),
                "the interior cell is not a casing");
    }

    @Test
    void theInteriorCellIsExactlyTheCentre() {
        List<GridPos> air = TransporterStructure.airCells(3, 10, 64, 20, 2);
        assertEquals(List.of(new GridPos(10, 64, 21)), air,
                "a 3x3x3 shell has one interior cell, one block behind the controller");
    }

    @Test
    void theFourByFourShellMirrorsEastCorrectly() {
        // Controller at the origin looking EAST (vanilla index 5): local x runs south,
        // local z runs west (against the facing).
        Set<GridPos> casings = new HashSet<>(TransporterStructure.casingCells(4, 0, 0, 0, 5));
        assertEquals(55, casings.size());
        // The controller cell maps back onto the controller position and is excluded.
        assertFalse(casings.contains(new GridPos(0, 0, 0)));
        // Local (3, 3, 3): back-top-right corner -> west 3, up 2, south 2.
        assertTrue(casings.contains(new GridPos(-3, 2, 2)), "back corner of an east facing shell");
        // Local (2, 1, 0): front face, one column right of the controller -> south 1.
        assertTrue(casings.contains(new GridPos(0, 0, 1)), "front face next to the controller");
        // Local (3, 0, 0): front-bottom-right corner -> south 2 (local x 3 - offset 1), down 1.
        assertTrue(casings.contains(new GridPos(0, -1, 2)), "front corner of an east facing shell");

        Set<GridPos> air = new HashSet<>(TransporterStructure.airCells(4, 0, 0, 0, 5));
        assertEquals(8, air.size());
        assertFalse(air.contains(new GridPos(0, 0, 0)));
    }

    @Test
    void theFiveByFiveShellStaysInsideItsBoxForEveryFacing() {
        // Vanilla direction indices: 2=NORTH, 3=SOUTH, 5=EAST, 4=WEST.
        int[][] facings = {{2, 1, 0}, {3, 6, -4}, {5, -2, 9}, {4, 30, 30}};
        for (int[] testCase : facings) {
            int facing = testCase[0];
            int cx = testCase[1];
            int cz = testCase[2];
            int cy = 64;
            List<GridPos> casings = TransporterStructure.casingCells(5, cx, cy, cz, facing);
            List<GridPos> air = TransporterStructure.airCells(5, cx, cy, cz, facing);
            assertEquals(97, casings.size(), "facing " + facing);
            assertEquals(27, air.size(), "facing " + facing);
            Set<GridPos> all = new HashSet<>(casings);
            assertTrue(all.addAll(air), "shell and interior must not overlap");
            assertEquals(124, all.size(), "97 casings + 27 air = every cell but the controller");
            int back = facing ^ 1;
            for (GridPos cell : all) {
                int dy = cell.y() - cy;
                // Along the back axis the shell runs from the controller's front face (0)
                // to the back face (4); on the other horizontal axis it is centred (+/-2).
                int along;
                int perp;
                if (back == QuarryFrame.NORTH) {
                    along = cz - cell.z();
                    perp = cell.x() - cx;
                } else if (back == QuarryFrame.SOUTH) {
                    along = cell.z() - cz;
                    perp = cell.x() - cx;
                } else if (back == QuarryFrame.WEST) {
                    along = cx - cell.x();
                    perp = cell.z() - cz;
                } else {
                    along = cell.x() - cx;
                    perp = cell.z() - cz;
                }
                assertTrue(along >= 0 && along <= 4 && Math.abs(perp) <= 2
                                && Math.abs(dy) <= 2,
                        "cell " + cell + " left the 5x5x5 shell box of controller (" + cx + ","
                                + cy + "," + cz + ") facing " + facing);
            }
            assertFalse(all.contains(new GridPos(cx, cy, cz)),
                    "the controller cell belongs to neither list (facing " + facing + ")");
        }
    }

    @Test
    void gridPositionsHaveADeterministicOrder() {
        assertTrue(new GridPos(1, 9, 9).compareTo(new GridPos(2, 0, 0)) < 0, "x decides first");
        assertTrue(new GridPos(2, 1, 9).compareTo(new GridPos(2, 2, 0)) < 0, "y decides second");
        assertTrue(new GridPos(2, 1, 1).compareTo(new GridPos(2, 1, 2)) < 0, "z decides last");
        assertEquals(0, new GridPos(4, 5, 6).compareTo(new GridPos(4, 5, 6)));
    }
}
