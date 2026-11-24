package com.industrialcore.machine;

import com.industrialcore.level.MachineLevelMath;

/**
 * Phase 113: Diminishing-Returns (hyperbolic saturation) upgrade math.
 * Kept strictly free of Minecraft classes so every formula is unit testable on a plain JVM.
 *
 * <p>All upgrade curves follow the bounded hyperbolic saturation curve {@code f(n, k) = n / (n + k)}:
 * every additional upgrade strictly improves performance with zero hard cliff, while marginal
 * gains taper smoothly so even 99+ or 1000 installed modules never break game balance.</p>
 */
public final class UpgradeMath {
    /** Legacy single-slot stack reference kept for vanilla ItemStack slot safety. */
    public static final int MAX_UPGRADE_STACK = 64;
    /** Default FE buffer added per legacy battery calculation when explicit perBattery is requested. */
    public static final int DEFAULT_CAPACITY_PER_BATTERY = 10_000;

    // Phase 113 hyperbolic saturation constants: f(n, k) = n / (n + k)
    public static final double MOTOR_HALF_SAT = 24.0D;
    public static final double MOTOR_MAX_SPEED_BONUS = 4.0D; // up to 5.0x speed
    /** Backwards-compatible alias kept for suit/legacy callers. */
    public static final double MAX_PROCESSING_SPEED_BONUS = MOTOR_MAX_SPEED_BONUS;

    public static final double BATTERY_COST_HALF_SAT = 28.0D;
    public static final double BATTERY_MAX_SAVING = 0.55D; // up to 55% FE saving (0.45x cost)
    /** Backwards-compatible alias kept for legacy callers. */
    public static final double MAX_BATTERY_ENERGY_SAVING = BATTERY_MAX_SAVING;

    public static final double BATTERY_CAP_HALF_SAT = 32.0D;
    public static final double BATTERY_MAX_CAP_BONUS = 6.0D; // up to 7.0x capacity

    public static final double SPECIAL_TURRET_HALF_SAT = 24.0D;
    public static final double SPECIAL_TURRET_MAX_DMG_BONUS = 2.5D; // up to 3.5x damage
    public static final double SPECIAL_TURRET_MAX_RANGE_BONUS = 20.0D; // up to +20 blocks

    public static final double SPECIAL_YIELD_HALF_SAT = 30.0D;
    public static final double SPECIAL_MAX_YIELD_CHANCE = 0.60D; // up to 60% bonus +1 output

    public static final double SPECIAL_GEN_HALF_SAT = 30.0D;
    public static final double SPECIAL_MAX_GEN_BONUS = 2.0D; // up to 3.0x FE/t output

    public static final double SPECIAL_AREA_HALF_SAT = 25.0D;
    public static final double SPECIAL_MAX_AREA_BONUS = 16.0D; // up to +16 blocks radius

    public static final double SPECIAL_THROUGHPUT_HALF_SAT = 28.0D;
    public static final double SPECIAL_MAX_THROUGHPUT_BONUS = 3.0D; // up to 4.0x throughput/cap

    private UpgradeMath() {}

    /** Core hyperbolic saturation curve {@code f(n, k) = n / (n + k)} in {@code [0, 1)}. */
    public static double diminishingFraction(int count, double halfSatK) {
        if (count <= 0 || !Double.isFinite(halfSatK) || halfSatK <= 0.0D) {
            return 0.0D;
        }
        double n = count;
        return n / (n + halfSatK);
    }

    /** Saturating addition for stored upgrade counts. */
    public static int saturatingAdd(int current, int delta, int limit) {
        int safeCurrent = clampCount(current, limit);
        if (delta <= 0) {
            return safeCurrent;
        }
        long sum = (long) safeCurrent + delta;
        return sum >= limit ? limit : (int) sum;
    }

    /** Clamps a count loaded from NBT back into a safe range. */
    public static int clampCount(int value, int limit) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, Math.max(0, limit));
    }

    /** Mining operations per tick for a given motor count (capped safely). */
    public static double operationsPerTick(int motors, double base, double perMotor, double max) {
        double safeBase = sanitize(base);
        double safePerMotor = sanitize(perMotor);
        double safeMax = sanitize(max);
        if (safeMax <= 0.0D) {
            return 0.0D;
        }
        double value = safeBase + Math.max(0, motors) * safePerMotor;
        if (!Double.isFinite(value) || value > safeMax) {
            return safeMax;
        }
        return Math.max(0.0D, value);
    }

    /** FE capacity for a given battery count, saturated to the configured safety cap. */
    public static int capacity(int batteries, int baseCapacity, int perBattery, int maxCapacity) {
        long safeMax = Math.max(1, maxCapacity);
        long base = Math.max(0, baseCapacity);
        long per = Math.max(0, perBattery);
        long total = base + Math.max(0, batteries) * per;
        if (total < 0L || total > safeMax) {
            total = safeMax;
        }
        return (int) Math.max(1L, total);
    }

    // -------------------------------------------------------------------------
    // Motor (Speed / Fire Rate) — Diminishing Returns
    // -------------------------------------------------------------------------

    /**
     * Processing / firing speed multiplier: {@code 1.0 + 4.0 * n / (n + 24)}.
     * Examples: n=0 -> 1.00x, n=8 -> 2.00x, n=24 -> 3.00x, n=99 -> 4.22x, limit -> 5.00x.
     */
    public static double processingSpeedMultiplier(int motors) {
        return 1.0D + MOTOR_MAX_SPEED_BONUS * diminishingFraction(motors, MOTOR_HALF_SAT);
    }

    /** Operation duration in ticks shortened by installed motors, always >= 1 tick. */
    public static int acceleratedTime(int baseTicks, int motors) {
        int base = Math.max(1, baseTicks);
        if (motors <= 0) {
            return base;
        }
        return Math.max(1, (int) Math.ceil(base / processingSpeedMultiplier(motors) - 1.0E-9D));
    }

    // -------------------------------------------------------------------------
    // Battery (FE Discount & Capacity Boost) — Diminishing Returns
    // -------------------------------------------------------------------------

    /**
     * FE cost multiplier: {@code 1.0 - 0.55 * n / (n + 28)}.
     * Examples: n=0 -> 1.00x, n=28 -> 0.725x, n=99 -> 0.572x, limit -> 0.45x (55% max saving).
     */
    public static double energyCostMultiplier(int batteries) {
        return 1.0D - BATTERY_MAX_SAVING * diminishingFraction(batteries, BATTERY_COST_HALF_SAT);
    }

    /** A base FE cost after battery efficiency, rounded up and never free unless baseCost == 0. */
    public static int discountedEnergy(int baseCost, int batteries) {
        long base = Math.max(0, baseCost);
        if (base == 0L) {
            return 0;
        }
        double value = base * energyCostMultiplier(batteries);
        return (int) Math.max(1L, Math.ceil(value - 1.0E-9D));
    }

    /**
     * Internal FE capacity scaled by batteries via diminishing returns:
     * {@code baseCapacity * (1.0 + 6.0 * n / (n + 32))}.
     */
    public static int scaledCapacity(int baseCapacity, int batteries) {
        int base = Math.max(1, baseCapacity);
        if (batteries <= 0) {
            return base;
        }
        double mult = 1.0D + BATTERY_MAX_CAP_BONUS * diminishingFraction(batteries, BATTERY_CAP_HALF_SAT);
        double scaled = base * mult;
        long capped = Math.min(Integer.MAX_VALUE / 2, Math.max((long) base, Math.round(scaled)));
        return (int) capped;
    }

    /** Fuel burn duration extended by battery cells for fuel-driven machines (e.g. Alloy Furnace). */
    public static int extendedBurnTime(int baseBurnTicks, int batteries) {
        int base = Math.max(0, baseBurnTicks);
        if (base == 0 || batteries <= 0) {
            return base;
        }
        double mult = energyCostMultiplier(batteries);
        return (int) Math.max(base, Math.round(base / Math.max(0.1D, mult)));
    }

    /** Percentage FE / fuel saving (0..55) granted by installed batteries for tooltip display. */
    public static int batterySavingPercent(int batteries) {
        return (int) Math.round((1.0D - energyCostMultiplier(batteries)) * 100.0D);
    }

    // -------------------------------------------------------------------------
    // Phase 113 Specialization Module (3rd Generic Upgrade) — Diminishing Returns
    // -------------------------------------------------------------------------

    /** Laser Turret pulse damage multiplier: {@code 1.0 + 2.5 * n / (n + 24)} (n=24 -> 2.25x, n=99 -> 3.01x). */
    public static double turretDamageMultiplier(int specials) {
        return 1.0D + SPECIAL_TURRET_MAX_DMG_BONUS * diminishingFraction(specials, SPECIAL_TURRET_HALF_SAT);
    }

    /** Laser Turret extra detection range in blocks: {@code round(20 * n / (n + 24))} (n=24 -> +10m, n=99 -> +16m). */
    public static int turretRangeBonus(int specials) {
        return (int) Math.round(SPECIAL_TURRET_MAX_RANGE_BONUS * diminishingFraction(specials, SPECIAL_TURRET_HALF_SAT));
    }

    /** Processing bonus yield (+1 output) probability: {@code 0.60 * n / (n + 30)} (max 60%). */
    public static double processingBonusYieldChance(int specials) {
        return SPECIAL_MAX_YIELD_CHANCE * diminishingFraction(specials, SPECIAL_YIELD_HALF_SAT);
    }

    /** Processing bonus yield percentage (0..60) for GUI readout. */
    public static int processingBonusYieldPercent(int specials) {
        return (int) Math.round(processingBonusYieldChance(specials) * 100.0D);
    }

    /** Generator / Reactor FE/t output multiplier: {@code 1.0 + 2.0 * n / (n + 30)} (n=30 -> 2.0x, n=99 -> 2.53x). */
    public static double generatorOutputMultiplier(int specials) {
        return 1.0D + SPECIAL_MAX_GEN_BONUS * diminishingFraction(specials, SPECIAL_GEN_HALF_SAT);
    }

    /** Generator FE/t bonus percentage (0..200) for GUI readout. */
    public static int generatorOutputBonusPercent(int specials) {
        return (int) Math.round((generatorOutputMultiplier(specials) - 1.0D) * 100.0D);
    }

    /** Area radius expansion (mining / farming / vacuum / suppressor / charger / fan): {@code round(16 * n / (n + 25))}. */
    public static int areaRangeBonus(int specials) {
        return (int) Math.round(SPECIAL_MAX_AREA_BONUS * diminishingFraction(specials, SPECIAL_AREA_HALF_SAT));
    }

    /** Throughput / storage / pump volume multiplier: {@code 1.0 + 3.0 * n / (n + 28)} (max 4.0x). */
    public static double throughputMultiplier(int specials) {
        return 1.0D + SPECIAL_MAX_THROUGHPUT_BONUS * diminishingFraction(specials, SPECIAL_THROUGHPUT_HALF_SAT);
    }

    /** Progress in percent, clamped to 0..100. */
    public static int progressPercent(int cursor, int total) {
        if (total <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(cursor, total));
        return (int) ((long) clamped * 100L / total);
    }

    // -------------------------------------------------------------------------
    // Post-Limit-Break Mastery Multipliers (Lv.100..1000)
    // -------------------------------------------------------------------------

    public static double applyMasterySpeed(double baseSpeed, double maxSpeed, int machineLevel) {
        double safeBase = sanitize(baseSpeed);
        double safeMax = sanitize(maxSpeed);
        if (safeBase <= 0.0D || safeMax <= 0.0D) {
            return 0.0D;
        }
        double mult = MachineLevelMath.masterySpeedMultiplier(machineLevel);
        double scaled = safeBase * mult;
        if (!Double.isFinite(scaled) || scaled > safeMax) {
            return safeMax;
        }
        return scaled;
    }

    public static int applyMasteryTime(int baseTicks, int machineLevel) {
        int base = Math.max(1, baseTicks);
        double mult = MachineLevelMath.masterySpeedMultiplier(machineLevel);
        if (mult <= 1.0D) {
            return base;
        }
        return Math.max(1, (int) Math.ceil(base / mult - 1.0E-9D));
    }

    public static int applyMasteryEnergy(int baseCost, int machineLevel) {
        long base = Math.max(0, baseCost);
        if (base == 0L) {
            return 0;
        }
        double mult = MachineLevelMath.masteryEnergyMultiplier(machineLevel);
        if (mult >= 1.0D) {
            return (int) Math.min(Integer.MAX_VALUE, base);
        }
        return (int) Math.max(1L, Math.ceil(base * mult - 1.0E-9D));
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
