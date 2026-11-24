package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World-coordinate tests for the quarry frame. The frame and the mining target must be derived
 * from the same mapping; these tests compare the box with every block {@link MiningPlan} selects.
 */
class QuarryFrameTest {
    private static final int X = 10;
    private static final int Y = 64;
    private static final int Z = 10;

    @Test
    void frameContainsExactlyTheMinedBlocksForEveryFacing() {
        int[][] sizes = {{1, 1, 1}, {2, 3, 1}, {3, 2, 4}, {5, 1, 2}};
        for (int facing : new int[]{QuarryFrame.NORTH, QuarryFrame.SOUTH, QuarryFrame.WEST, QuarryFrame.EAST}) {
            for (int[] size : sizes) {
                assertFrameMatchesPlan(facing, size[0], size[1], size[2]);
            }
        }
    }

    @Test
    void eastAndWestFramesExtendAlongTheCorrectAxes() {
        // Facing east, width runs north/south (Z), depth runs east (X).
        QuarryFrame.Bounds east = QuarryFrame.bounds(X, Y, Z, QuarryFrame.EAST, 4, 3, 2);
        assertEquals(X + 1, east.minX(), 1.0E-9, "east frame starts one block in front");
        assertEquals(X + 4, east.maxX(), 1.0E-9, "east frame depth runs along +X");
        assertEquals(Z, east.minZ(), 1.0E-9, "east frame width runs along +Z (right of east)");
        assertEquals(Z + 4, east.maxZ(), 1.0E-9);
        assertEquals(Y - 1, east.minY(), 1.0E-9);
        assertEquals(Y + 1, east.maxY(), 1.0E-9);

        // Facing west, width runs north/south and depth runs west.
        QuarryFrame.Bounds west = QuarryFrame.bounds(X, Y, Z, QuarryFrame.WEST, 4, 3, 2);
        assertEquals(X - 3, west.minX(), 1.0E-9, "west frame depth runs along -X");
        assertEquals(X, west.maxX(), 1.0E-9);
        assertEquals(Z - 3, west.minZ(), 1.0E-9, "west frame width runs along -Z (right of west)");
        assertEquals(Z + 1, west.maxZ(), 1.0E-9);
    }

    @Test
    void targetIsAlwaysInsideTheFrame() {
        for (int facing : new int[]{QuarryFrame.NORTH, QuarryFrame.SOUTH, QuarryFrame.WEST, QuarryFrame.EAST}) {
            int width = 3;
            int depth = 4;
            int height = 2;
            QuarryFrame.Bounds bounds = QuarryFrame.bounds(X, Y, Z, facing, width, depth, height);
            MiningPlan plan = new MiningPlan(width, depth, height);
            for (int index = 0; index < plan.volume(); index++) {
                QuarryFrame.Point point = QuarryFrame.target(X, Y, Z, facing, width, depth, height, index);
                assertTrue(point.x() >= bounds.minX() && point.x() + 1 <= bounds.maxX(),
                        "target x outside frame: " + point + " / " + bounds);
                assertTrue(point.y() >= bounds.minY() && point.y() + 1 <= bounds.maxY(),
                        "target y outside frame: " + point + " / " + bounds);
                assertTrue(point.z() >= bounds.minZ() && point.z() + 1 <= bounds.maxZ(),
                        "target z outside frame: " + point + " / " + bounds);
            }
        }
    }

    @Test
    void bottomOfWorldIncludesTheLowestBlock() {
        // At y=-60 with min build height -64 there are five minable layers: -60..-64.
        assertEquals(5, QuarryFrame.maxHeight(-60, -64));
        assertEquals(1, QuarryFrame.maxHeight(-64, -64));
    }

    private static void assertFrameMatchesPlan(int facing, int width, int depth, int height) {
        QuarryFrame.Bounds bounds = QuarryFrame.bounds(X, Y, Z, facing, width, depth, height);
        MiningPlan plan = new MiningPlan(width, depth, height);
        boolean[][] seen = new boolean[width][depth];
        for (int index = 0; index < plan.volume(); index++) {
            QuarryFrame.Point point = QuarryFrame.target(X, Y, Z, facing, width, depth, height, index);
            int row = (index / width) % depth;
            int expectedX = plan.localX(index);
            seen[expectedX][row] = true;
            assertTrue(point.x() + 1 > bounds.minX() && point.x() < bounds.maxX(),
                    "facing " + facing + " target x outside frame");
            assertTrue(point.z() + 1 > bounds.minZ() && point.z() < bounds.maxZ(),
                    "facing " + facing + " target z outside frame");
            assertTrue(point.y() + 1 > bounds.minY() && point.y() < bounds.maxY(),
                    "facing " + facing + " target y outside frame");
        }
        for (boolean[] column : seen) {
            for (boolean value : column) {
                assertTrue(value, "the plan did not visit every width/depth cell");
            }
        }
        // The box must not be wider than the area: its volume equals width*depth*height blocks.
        double volume = (bounds.maxX() - bounds.minX()) * (bounds.maxZ() - bounds.minZ())
                * (bounds.maxY() - bounds.minY());
        assertEquals((double) width * depth * height, volume, 1.0E-9);
        assertFalse(bounds.maxX() <= bounds.minX() || bounds.maxZ() <= bounds.minZ());
    }
}
