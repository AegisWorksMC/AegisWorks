package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the Phase 62 casing part layout.
 *
 * <p>The layout is what makes the 4x4x4 Advanced shell read as one machine instead of 56 tiles,
 * and it is stamped onto 55 live blocks, so it is pinned here: the exact part counts of a shell,
 * the controller slot staying empty, the outward facing of every face, and the "no layout for
 * other sizes" rule that keeps Basic and the Quantum Array untouched.</p>
 */
class TransporterShellStyleTest {

    private static final int NORTH = QuarryFrame.NORTH;

    @Test
    void theControllerCellHasNoCasingStyle() {
        // Controller sits at local (offset, offset, 0) = (1, 1, 0) of a 4x4x4 shell.
        assertNull(TransporterShellStyle.styleFor(4, 1, 1, 0, NORTH));
    }

    @Test
    void everyCasingCellOfTheFourByFourShellGetsExactlyOnePart() {
        Map<TransporterCasingPart, Integer> counts = new HashMap<>();
        List<TransporterStructure.ShellCell> cells =
                TransporterStructure.shellCells(4, 0, 0, 0, NORTH);
        assertEquals(56, cells.size(), "26 shell cells of a 4x4x4 cube, controller included");
        for (TransporterStructure.ShellCell cell : cells) {
            if (cell.controller()) {
                continue;
            }
            TransporterShellStyle.CasingStyle style = TransporterShellStyle.styleFor(4,
                    cell.localX(), cell.localY(), cell.localZ(), NORTH);
            assertTrue(style != null, "every casing cell must get a style");
            counts.merge(style.part(), 1, Integer::sum);
        }
        // 4 sill/header cells per vertical face (front, back, left, right) = 12 each.
        assertEquals(12, counts.get(TransporterCasingPart.SILL));
        assertEquals(12, counts.get(TransporterCasingPart.HEADER));
        // Two conveyor mouths, two cells tall, one on each side of the drive housing.
        assertEquals(4, counts.get(TransporterCasingPart.CONVEYOR));
        // The 2x2 drive housing: the controller plus these three quadrants.
        assertEquals(1, counts.get(TransporterCasingPart.DRIVE_RIGHT));
        assertEquals(1, counts.get(TransporterCasingPart.DRIVE_TOP));
        assertEquals(1, counts.get(TransporterCasingPart.DRIVE_CORNER));
        // 2x2 of each side face, the 2x4 back plate, and the two 2x2 horizontal plates.
        assertEquals(8, counts.get(TransporterCasingPart.PANEL));
        assertEquals(8, counts.get(TransporterCasingPart.BACK));
        assertEquals(4, counts.get(TransporterCasingPart.DECK));
        assertEquals(4, counts.get(TransporterCasingPart.BASE));
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(55, total, "55 casings + 1 controller = the 56 shell cells");
    }

    @Test
    void eachFacePointsOutwards() {
        // Front parts look the way the controller looks, back parts the other way.
        assertEquals(NORTH, TransporterShellStyle.styleFor(4, 0, 1, 0, NORTH).facing());
        assertEquals(QuarryFrame.SOUTH, TransporterShellStyle.styleFor(4, 0, 1, 3, NORTH).facing());
        // Left column (local x = 0) looks away from the machine's right (east), i.e. west.
        assertEquals(QuarryFrame.WEST, TransporterShellStyle.styleFor(4, 0, 1, 1, NORTH).facing());
        assertEquals(QuarryFrame.EAST, TransporterShellStyle.styleFor(4, 3, 1, 1, NORTH).facing());
        // Rotating the machine rotates all of that with it.
        assertEquals(QuarryFrame.EAST, TransporterShellStyle.styleFor(4, 0, 1, 0, QuarryFrame.EAST)
                .facing());
        assertEquals(QuarryFrame.NORTH, TransporterShellStyle.styleFor(4, 0, 1, 1, QuarryFrame.EAST)
                .facing(), "left of an east-facing machine is north");
    }

    @Test
    void unsupportedSizesKeepThePlainCasingWhile3And5StyleAllShellCells() {
        for (int y = 0; y < 2; y++) {
            assertEquals(TransporterCasingPart.PLAIN,
                    TransporterShellStyle.styleFor(2, 0, y, 0, NORTH).part(),
                    "size 2 must stay PLAIN");
        }
        // Basic 3x3x3 shell: controller (1,1,0) null, remaining 25 cells styled
        assertNull(TransporterShellStyle.styleFor(3, 1, 1, 0, NORTH));
        int basicCount = 0;
        for (TransporterStructure.ShellCell cell : TransporterStructure.shellCells(3, 0, 0, 0, NORTH)) {
            if (cell.controller()) {
                continue;
            }
            assertTrue(TransporterShellStyle.styleFor(3, cell.localX(), cell.localY(), cell.localZ(), NORTH) != null);
            basicCount++;
        }
        assertEquals(25, basicCount);

        // Quantum Array 5x5x5 shell: controller (2,2,0) null, 8-cell quantum gate ring + remaining casings styled
        assertNull(TransporterShellStyle.styleFor(5, 2, 2, 0, NORTH));
        int cornerRing = 0;
        int sideRing = 0;
        int arrayCount = 0;
        for (TransporterStructure.ShellCell cell : TransporterStructure.shellCells(5, 0, 0, 0, NORTH)) {
            if (cell.controller()) {
                continue;
            }
            TransporterShellStyle.CasingStyle st = TransporterShellStyle.styleFor(
                    5, cell.localX(), cell.localY(), cell.localZ(), NORTH);
            assertTrue(st != null);
            arrayCount++;
            if (st.part() == TransporterCasingPart.DRIVE_CORNER) cornerRing++;
            if (st.part() == TransporterCasingPart.DRIVE_RIGHT || st.part() == TransporterCasingPart.DRIVE_TOP) sideRing++;
        }
        assertEquals(97, arrayCount);
        assertEquals(4, cornerRing);
        assertEquals(4, sideRing);
    }
}
