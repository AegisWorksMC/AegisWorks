package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeMathTest {
    private static final double BASE_OPS = 0.25D;
    private static final double OPS_PER_MOTOR = 0.05D;
    private static final double MAX_OPS = 8.0D;

    @Test
    void baseSpeedIsUsedWithoutMotors() {
        assertEquals(0.25D, UpgradeMath.operationsPerTick(0, BASE_OPS, OPS_PER_MOTOR, MAX_OPS), 1.0E-9D);
    }

    @Test
    void eachMotorAddsSpeedUntilTheSafetyCap() {
        assertEquals(0.5D, UpgradeMath.operationsPerTick(5, BASE_OPS, OPS_PER_MOTOR, MAX_OPS), 1.0E-9D);
        assertEquals(MAX_OPS, UpgradeMath.operationsPerTick(10_000_000, BASE_OPS, OPS_PER_MOTOR, MAX_OPS), 1.0E-9D);
    }

    @Test
    void operationsPerTickNeverReturnsInvalidNumbers() {
        assertEquals(BASE_OPS, UpgradeMath.operationsPerTick(-20, BASE_OPS, OPS_PER_MOTOR, MAX_OPS), 1.0E-9D);
        assertEquals(0.0D, UpgradeMath.operationsPerTick(100, BASE_OPS, OPS_PER_MOTOR, 0.0D), 1.0E-9D);
        // An invalid per-motor bonus is ignored, leaving only the base speed.
        assertEquals(BASE_OPS, UpgradeMath.operationsPerTick(100, BASE_OPS, Double.NaN, MAX_OPS), 1.0E-9D);
        // An invalid base value falls back to zero, so only the motor bonus remains.
        assertEquals(5.0D, UpgradeMath.operationsPerTick(100, Double.POSITIVE_INFINITY, OPS_PER_MOTOR, MAX_OPS),
                1.0E-9D);
        double result = UpgradeMath.operationsPerTick(Integer.MAX_VALUE, BASE_OPS, 1.0D, MAX_OPS);
        assertTrue(Double.isFinite(result) && result <= MAX_OPS, "result was not capped: " + result);
    }

    @Test
    void saturatingAddNeverOverflows() {
        assertEquals(8, UpgradeMath.saturatingAdd(5, 3, 100));
        assertEquals(100, UpgradeMath.saturatingAdd(99, 5, 100));
        assertEquals(100, UpgradeMath.saturatingAdd(Integer.MAX_VALUE, 1, 100));
        assertEquals(0, UpgradeMath.saturatingAdd(-50, 0, 100));
        assertEquals(0, UpgradeMath.saturatingAdd(0, -5, 100));
    }

    @Test
    void clampCountRejectsNegativeAndHugeValues() {
        assertEquals(0, UpgradeMath.clampCount(-1, 1_000));
        assertEquals(7, UpgradeMath.clampCount(7, 1_000));
        assertEquals(1_000, UpgradeMath.clampCount(9_999, 1_000));
    }

    @Test
    void batteriesIncreaseCapacityUpToTheSafetyCap() {
        assertEquals(20_000, UpgradeMath.capacity(0, 20_000, 10_000, 2_000_000_000));
        assertEquals(50_000, UpgradeMath.capacity(3, 20_000, 10_000, 2_000_000_000));
        assertEquals(2_000_000_000,
                UpgradeMath.capacity(Integer.MAX_VALUE, 20_000, 10_000, 2_000_000_000));
        assertEquals(20_000, UpgradeMath.capacity(-5, 20_000, 10_000, 2_000_000_000));
        assertEquals(1, UpgradeMath.capacity(0, 0, 0, 2_000_000_000));
    }

    @Test
    void batteriesAlsoCutTheEnergyCostUpToAFullStack() {
        assertEquals(64, UpgradeMath.MAX_UPGRADE_STACK);
        assertEquals(1.0D, UpgradeMath.energyCostMultiplier(0), 1.0E-9D);
        assertEquals(1.0D, UpgradeMath.energyCostMultiplier(-5), 1.0E-9D);
        // Phase 113 hyperbolic diminishing returns: 1.0 - 0.55 * n / (n + 28)
        assertEquals(1.0D - 0.55D * 64.0D / 92.0D, UpgradeMath.energyCostMultiplier(64), 1.0E-9D);
        assertTrue(UpgradeMath.energyCostMultiplier(5_000) > 0.45D && UpgradeMath.energyCostMultiplier(5_000) < 0.46D);
        // Every battery must strictly improve efficiency monotonically with diminishing marginal gains.
        double previous = UpgradeMath.energyCostMultiplier(0);
        for (int count = 1; count <= 100; count++) {
            double value = UpgradeMath.energyCostMultiplier(count);
            assertTrue(value < previous, "battery " + count + " did not improve the efficiency");
            previous = value;
        }
    }

    @Test
    void discountedEnergyRoundsUpAndStaysPositive() {
        assertEquals(100, UpgradeMath.discountedEnergy(100, 0));
        assertEquals(62, UpgradeMath.discountedEnergy(100, 64));
        assertEquals(1, UpgradeMath.discountedEnergy(1, 64), "a nonzero cost must never become free");
        assertEquals(0, UpgradeMath.discountedEnergy(0, 64));
        assertEquals(0, UpgradeMath.discountedEnergy(-10, 64));
    }

    @Test
    void progressPercentIsSafeForAllInputs() {
        assertEquals(0, UpgradeMath.progressPercent(0, 0));
        assertEquals(0, UpgradeMath.progressPercent(5, 0));
        assertEquals(50, UpgradeMath.progressPercent(50, 100));
        assertEquals(100, UpgradeMath.progressPercent(150, 100));
        assertEquals(0, UpgradeMath.progressPercent(-5, 100));
    }

    @Test
    void phase87UnifiedMotorSpeedBatteryCapacityAndFuelEfficiencyWork() {
        // Phase 113 hyperbolic motor speed multiplier: 1.0 + 4.0 * n / (n + 24)
        assertEquals(1.0D, UpgradeMath.processingSpeedMultiplier(0), 1.0E-9D);
        assertEquals(3.0D, UpgradeMath.processingSpeedMultiplier(24), 1.0E-9D);
        assertEquals(1.0D + 4.0D * 64.0D / 88.0D, UpgradeMath.processingSpeedMultiplier(64), 1.0E-9D);
        assertTrue(UpgradeMath.processingSpeedMultiplier(999) > 4.8D && UpgradeMath.processingSpeedMultiplier(999) < 5.0D);
        assertEquals(20, UpgradeMath.acceleratedTime(20, 0));
        assertEquals(6, UpgradeMath.acceleratedTime(20, 64));
        assertEquals(1, UpgradeMath.acceleratedTime(2, 64), "Accelerated time must stay >= 1 tick");

        // Phase 113 hyperbolic battery capacity scaling: base * (1.0 + 6.0 * n / (n + 32))
        assertEquals(20_000, UpgradeMath.scaledCapacity(20_000, 0));
        assertEquals(80_000, UpgradeMath.scaledCapacity(20_000, 32));
        assertEquals(100_000, UpgradeMath.scaledCapacity(20_000, 64));
        assertEquals(0, UpgradeMath.batterySavingPercent(0));
        assertEquals(38, UpgradeMath.batterySavingPercent(64));

        // Fuel-driven Alloy Furnace battery efficiency: extends burn time smoothly
        assertEquals(1600, UpgradeMath.extendedBurnTime(1600, 0));
        assertTrue(UpgradeMath.extendedBurnTime(1600, 32) > 1600);
        assertTrue(UpgradeMath.extendedBurnTime(1600, 64) > UpgradeMath.extendedBurnTime(1600, 32));
        assertEquals(2592, UpgradeMath.extendedBurnTime(1600, 64));
        assertEquals(0, UpgradeMath.extendedBurnTime(0, 64));
    }
}
