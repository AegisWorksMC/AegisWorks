package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests of the large quarry: the fixed area uses the same mapping as the normal quarry, and
 * the Phase 22 cost / drop arithmetic behaves as documented.
 *
 * <p>Mining, storage, chunks and the GUI are covered by {@code LargeQuarryGameTests} on a real
 * server; this class only checks numbers that do not need a world.</p>
 */
class LargeQuarryTest {
    private static final int X = 10;
    private static final int Y = 64;
    private static final int Z = 10;

    @Test
    void theFixedAreaMatchesTheFramePlanForEveryFacing() {
        int width = LargeQuarryBalance.DEFAULT_WIDTH;
        int depth = LargeQuarryBalance.DEFAULT_DEPTH;
        int height = LargeQuarryBalance.DEFAULT_HEIGHT;
        MiningPlan plan = new MiningPlan(width, depth, height);

        for (int facing : new int[]{QuarryFrame.NORTH, QuarryFrame.SOUTH,
                QuarryFrame.WEST, QuarryFrame.EAST}) {
            QuarryFrame.Bounds bounds = QuarryFrame.bounds(X, Y, Z, facing, width, depth, height);
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < plan.volume(); index++) {
                QuarryFrame.Point point = QuarryFrame.target(X, Y, Z, facing, plan, index);
                assertTrue(point.x() >= bounds.minX() && point.x() + 1 <= bounds.maxX(),
                        "target outside the frame on X: " + point + " / " + bounds);
                assertTrue(point.y() >= bounds.minY() && point.y() + 1 <= bounds.maxY(),
                        "target outside the frame on Y: " + point + " / " + bounds);
                assertTrue(point.z() >= bounds.minZ() && point.z() + 1 <= bounds.maxZ(),
                        "target outside the frame on Z: " + point + " / " + bounds);
                assertTrue(seen.add(point.x() + ":" + point.y() + ":" + point.z()),
                        "duplicate target " + point + " at index " + index);
            }
            assertEquals(plan.volume(), seen.size(),
                    "the area must contain exactly one target per plan index");
        }
    }

    @Test
    void theAreaStartsInFrontOfTheMachine() {
        QuarryFrame.Point first = QuarryFrame.target(X, Y, Z, QuarryFrame.EAST,
                new MiningPlan(3, 3, 2), 0);
        assertEquals(X + 1, first.x(), "the area must start one block in front of the machine");
        assertEquals(Y, first.y(), "the first layer is on the machine's Y level");
        assertEquals(Z, first.z(), "the first target is the front-left corner");
    }

    @Test
    void dimensionsAreClampedToTheSupportedRange() {
        assertEquals(1, LargeQuarryBalance.clampDimension(0));
        assertEquals(1, LargeQuarryBalance.clampDimension(-5));
        assertEquals(7, LargeQuarryBalance.clampDimension(7));
        assertEquals(LargeQuarryBalance.MAX_AREA_DIMENSION,
                LargeQuarryBalance.clampDimension(1_000));
        assertTrue(LargeQuarryBalance.DEFAULT_WIDTH >= 1
                        && LargeQuarryBalance.DEFAULT_WIDTH <= LargeQuarryBalance.MAX_AREA_DIMENSION,
                "default width out of range");
        assertTrue(LargeQuarryBalance.DEFAULT_DEPTH >= 1
                        && LargeQuarryBalance.DEFAULT_DEPTH <= LargeQuarryBalance.MAX_AREA_DIMENSION,
                "default depth out of range");
        assertTrue(LargeQuarryBalance.DEFAULT_HEIGHT >= 1
                        && LargeQuarryBalance.DEFAULT_HEIGHT <= LargeQuarryBalance.MAX_AREA_DIMENSION,
                "default height out of range");
    }

    @Test
    void theDefaultAreaIsSmallerThanTheHardBuildLimit() {
        int volume = LargeQuarryBalance.volume(
                LargeQuarryBalance.DEFAULT_WIDTH,
                LargeQuarryBalance.DEFAULT_DEPTH,
                LargeQuarryBalance.DEFAULT_HEIGHT);
        // 7 x 7 x 5 = 245: large enough to be a "large" machine, small enough that a full run is
        // a few minutes and not a whole chunk.
        assertEquals(245, volume);
        int maximum = LargeQuarryBalance.volume(
                LargeQuarryBalance.MAX_AREA_DIMENSION,
                LargeQuarryBalance.MAX_AREA_DIMENSION,
                LargeQuarryBalance.MAX_AREA_DIMENSION);
        assertEquals(32_768, maximum);
    }

    @Test
    void theMiningSpeedStaysBoundedPerTick() {
        assertTrue(LargeQuarryBalance.OPERATIONS_PER_TICK > 0.0D,
                "the machine must make progress");
        assertTrue(LargeQuarryBalance.OPERATIONS_PER_TICK
                        <= LargeQuarryBalance.MAX_OPERATIONS_PER_TICK,
                "the configured speed must not exceed the hard per-tick cap");
        assertTrue(LargeQuarryBalance.MAX_OPERATIONS_PER_TICK <= 16.0D,
                "the hard cap must stay small enough for one tick");
        assertTrue(LargeQuarryBalance.OPERATIONS_PER_TICK > 0.25D);
    }

    @Test
    void theMachineIsNotItsOwnMiningTarget() {
        for (int facing : new int[]{QuarryFrame.NORTH, QuarryFrame.SOUTH,
                QuarryFrame.WEST, QuarryFrame.EAST}) {
            QuarryFrame.Point first = QuarryFrame.target(X, Y, Z, facing,
                    new MiningPlan(1, 1, 1), 0);
            assertFalse(first.x() == X && first.y() == Y && first.z() == Z,
                    "the machine block is inside its own area for facing " + facing);
        }
    }

    // ------------------------------------------------------------------
    // Phase 22 arithmetic
    // ------------------------------------------------------------------

    @Test
    void aPlainPickaxeCostsExactlyTheConfiguredBase() {
        assertEquals(LargeQuarryBalance.ENERGY_PER_BLOCK,
                LargeQuarryBalance.miningCost(LargeQuarryBalance.ENERGY_PER_BLOCK,
                        new PickaxeStats(1, 0, 0, 0), 1));
    }

    @Test
    void everyExtraToolAndEnchantmentRaisesTheCost() {
        int base = 100;
        int plain = LargeQuarryBalance.miningCost(base, new PickaxeStats(1, 0, 0, 0), 1);
        int extraTool = LargeQuarryBalance.miningCost(base, new PickaxeStats(2, 0, 0, 0), 2);
        assertEquals(plain + LargeQuarryBalance.ENERGY_PER_EXTRA_TOOL, extraTool,
                "one extra pickaxe must add its documented surcharge");

        // The quadratic term is part of every level based cost: levels^2 / 10.
        int efficient = LargeQuarryBalance.miningCost(base, new PickaxeStats(1, 5, 0, 0), 1);
        assertEquals(plain + 5 * LargeQuarryBalance.ENERGY_PER_EFFICIENCY
                + 25 / LargeQuarryBalance.ENERGY_LEVEL_QUADRATIC_DIVISOR, efficient);

        int lucky = LargeQuarryBalance.miningCost(base, new PickaxeStats(1, 0, 4, 0), 1);
        assertEquals(plain + 4 * LargeQuarryBalance.ENERGY_PER_FORTUNE
                + 16 / LargeQuarryBalance.ENERGY_LEVEL_QUADRATIC_DIVISOR, lucky);

        int silky = LargeQuarryBalance.miningCost(base, new PickaxeStats(1, 0, 0, 2), 1);
        assertEquals(plain + 2 * LargeQuarryBalance.ENERGY_PER_SILK_TOUCH
                + 4 / LargeQuarryBalance.ENERGY_LEVEL_QUADRATIC_DIVISOR, silky);

        // The superlinear term: 10 merged levels cost 100 / 10 = 10 extra FE.
        int high = LargeQuarryBalance.miningCost(base, new PickaxeStats(1, 4, 4, 2), 1);
        assertEquals(plain + 4 * LargeQuarryBalance.ENERGY_PER_EFFICIENCY
                        + 4 * LargeQuarryBalance.ENERGY_PER_FORTUNE
                        + 2 * LargeQuarryBalance.ENERGY_PER_SILK_TOUCH
                        + 100 / LargeQuarryBalance.ENERGY_LEVEL_QUADRATIC_DIVISOR, high);

        assertTrue(high > plain, "a strong setup must cost more than a plain pickaxe");
    }

    @Test
    void theCostGrowsFasterThanTheLevelsAtTheHighEnd() {
        int base = 100;
        int lowLevels = LargeQuarryBalance.miningCost(base,
                new PickaxeStats(1, 10, 10, 10), 1);
        int highLevels = LargeQuarryBalance.miningCost(base,
                new PickaxeStats(1, 100, 100, 100), 1);
        assertTrue(highLevels > lowLevels * 5,
                "the quadratic term must make very high levels noticeably more expensive: "
                        + lowLevels + " -> " + highLevels);
    }

    @Test
    void theCostSaturatesInsteadOfOverflowing() {
        int cost = LargeQuarryBalance.miningCost(Integer.MAX_VALUE,
                new PickaxeStats(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, cost);
        assertEquals(0, LargeQuarryBalance.miningCost(0, PickaxeStats.EMPTY, 1));
        assertTrue(LargeQuarryBalance.miningCost(-50, PickaxeStats.EMPTY, 1) >= 0,
                "negative configured values must never refund FE");
    }

    @Test
    void efficiencySpeedsTheScanUpButNeverBeyondTheHardCap() {
        double base = 0.5D;
        double cap = 2.0D;
        assertEquals(base, LargeQuarryBalance.operationsPerTick(
                base, cap, new PickaxeStats(1, 0, 0, 0)), 1.0E-9D);
        assertTrue(LargeQuarryBalance.operationsPerTick(
                        base, cap, new PickaxeStats(1, 10, 0, 0)) > base,
                "efficiency must speed the scan up");
        assertEquals(cap, LargeQuarryBalance.operationsPerTick(
                base, cap, new PickaxeStats(1, 100_000, 0, 0)), 1.0E-9D,
                "the per-tick cap must stay a hard safety limit");
        assertEquals(cap, LargeQuarryBalance.operationsPerTick(
                base, cap, new PickaxeStats(1, Integer.MAX_VALUE, 0, 0)), 1.0E-9D);
    }

    @Test
    void silkTouchAndFortuneBothApply() {
        PickaxeStats both = new PickaxeStats(1, 0, 4, 1);
        assertEquals(3, LargeQuarryBalance.silkBlockCount(both),
                "1 + fortune / 2 ore blocks per mined block");
        assertEquals(5, LargeQuarryBalance.rawDropMultiplier(both),
                "without silk touch fortune multiplies the raw drops");
        assertEquals(1, LargeQuarryBalance.silkBlockCount(PickaxeStats.EMPTY));
        assertEquals(1, LargeQuarryBalance.rawDropMultiplier(PickaxeStats.EMPTY));

        // Absurd levels stay positive and saturate; they never wrap around.
        PickaxeStats extreme = new PickaxeStats(8, 0, Integer.MAX_VALUE, 1);
        assertTrue(LargeQuarryBalance.silkBlockCount(extreme) > 0);
        assertEquals(Integer.MAX_VALUE, LargeQuarryBalance.rawDropMultiplier(extreme));
        assertEquals(Integer.MAX_VALUE,
                LargeQuarryBalance.saturatedCount(64, Integer.MAX_VALUE));
        assertEquals(0, LargeQuarryBalance.saturatedCount(0, 10));
    }

    @Test
    void toolSlotsStayInTheSupportedRange() {
        assertEquals(LargeQuarryBalance.BASE_TOOL_SLOTS,
                LargeQuarryBalance.clampToolSlots(0));
        assertEquals(LargeQuarryBalance.BASE_TOOL_SLOTS,
                LargeQuarryBalance.clampToolSlots(-3));
        assertEquals(LargeQuarryBalance.MAX_TOOL_SLOTS,
                LargeQuarryBalance.clampToolSlots(1_000));
        assertTrue(LargeQuarryBalance.BASE_TOOL_SLOTS >= 1);
        assertTrue(LargeQuarryBalance.MAX_TOOL_SLOTS > LargeQuarryBalance.BASE_TOOL_SLOTS);
    }
}
