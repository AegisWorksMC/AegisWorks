package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrafterSpeedTest {

    @Test
    void theCurveRunsToAFullStackAndKeepsShorteningTheCraft() {
        assertEquals(64, CrafterSpeed.MAX_LEVEL, "one stack of motors must be installable");
        int previousTime = Integer.MAX_VALUE;
        for (int level = 0; level <= CrafterSpeed.MAX_LEVEL; level++) {
            int time = CrafterSpeed.craftTime(60, level);
            assertTrue(time <= previousTime, "level " + level + " did not shorten the craft");
            assertTrue(time >= 1, "level " + level + " produced a zero tick craft");
            previousTime = time;
        }
        assertEquals(60, CrafterSpeed.craftTime(60, 0));
        assertEquals(25, CrafterSpeed.craftTime(60, 4));
        assertEquals(3, CrafterSpeed.craftTime(60, 64));
        assertEquals(CrafterSpeed.craftTime(60, 64), CrafterSpeed.craftTime(60, 200),
                "levels above one stack must clamp to the stack level");
        assertTrue(CrafterSpeed.craftTime(1, 64) >= 1, "the craft must never reach zero ticks");
    }

    @Test
    void energyPerTickRisesWithEveryLevel() {
        int previous = -1;
        for (int level = 0; level <= CrafterSpeed.MAX_LEVEL; level++) {
            int energy = CrafterSpeed.energyPerTick(2, level);
            assertTrue(energy > previous, "level " + level + " did not draw more FE/t");
            previous = energy;
        }
        assertEquals(2, CrafterSpeed.energyPerTick(2, 0));
        assertEquals(6, CrafterSpeed.energyPerTick(2, 4));
        assertEquals(66, CrafterSpeed.energyPerTick(2, 64));
    }

    @Test
    void totalEnergyPerCraftGrowsOnlyGently() {
        long base = CrafterSpeed.totalEnergy(60, 2, 0);
        long full = CrafterSpeed.totalEnergy(60, 2, 64);
        assertEquals(120L, base);
        assertEquals(198L, full);
        assertTrue(full >= base, "a faster craft must not become free");
    }

    @Test
    void levelsAreClampedAndZeroCostStaysZero() {
        assertEquals(60, CrafterSpeed.craftTime(60, -10));
        assertEquals(CrafterSpeed.craftTime(60, 64), CrafterSpeed.craftTime(60, 999));
        assertEquals(0, CrafterSpeed.energyPerTick(0, 3));
        assertTrue(CrafterSpeed.craftTime(1, 64) >= 1);
    }
}
