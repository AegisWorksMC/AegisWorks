package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningPlanTest {
    @Test
    void volumeIsTheProductOfTheDimensions() {
        assertEquals(24, new MiningPlan(3, 2, 4).volume());
        assertEquals(1, new MiningPlan(1, 1, 1).volume());
    }

    @Test
    void everyOffsetIsUniqueAndInsideTheArea() {
        MiningPlan plan = new MiningPlan(4, 3, 2);
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < plan.volume(); index++) {
            int x = plan.localX(index);
            int y = plan.localY(index);
            int z = plan.localZ(index);
            assertTrue(x >= 0 && x < 4, "x out of range: " + x);
            assertTrue(y >= 0 && y < 2, "y out of range: " + y);
            assertTrue(z >= 0 && z < 3, "z out of range: " + z);
            assertTrue(seen.add(x + ":" + y + ":" + z), "duplicate offset at index " + index);
        }
        assertEquals(plan.volume(), seen.size());
    }

    @Test
    void rowsAreMinedInASerpentineOrder() {
        MiningPlan plan = new MiningPlan(3, 2, 1);
        int[] expectedX = {0, 1, 2, 2, 1, 0};
        int[] expectedZ = {0, 0, 0, 1, 1, 1};
        for (int index = 0; index < 6; index++) {
            assertEquals(expectedX[index], plan.localX(index), "x at " + index);
            assertEquals(expectedZ[index], plan.localZ(index), "z at " + index);
        }
    }

    @Test
    void layersGoFromTopToBottom() {
        MiningPlan plan = new MiningPlan(2, 2, 4);
        assertEquals(0, plan.localY(0));
        assertEquals(1, plan.localY(4));
        assertEquals(3, plan.localY(12));
    }

    @Test
    void indexOfIsTheInverseOfTheCursorMapping() {
        MiningPlan plan = new MiningPlan(5, 3, 2);
        for (int index = 0; index < plan.volume(); index++) {
            int x = plan.localX(index);
            int y = plan.localY(index);
            int z = plan.localZ(index);
            assertEquals(index, plan.indexOf(x, y, z), "round trip failed at " + index);
        }
    }

    @Test
    void invalidDimensionsAreClamped() {
        MiningPlan plan = new MiningPlan(0, -7, 999_999);
        assertEquals(1, plan.width());
        assertEquals(1, plan.depth());
        assertEquals(MiningPlan.MAX_DIMENSION, plan.height());
        assertEquals(MiningPlan.MAX_DIMENSION, plan.volume());
    }

    @Test
    void indexesOutsideTheAreaAreClampedInsteadOfThrowing() {
        MiningPlan plan = new MiningPlan(2, 2, 2);
        assertEquals(plan.localX(0), plan.localX(-5));
        assertEquals(plan.localX(plan.volume() - 1), plan.localX(plan.volume() + 100));
        assertEquals(-1, plan.indexOf(9, 9, 9));
    }
}
