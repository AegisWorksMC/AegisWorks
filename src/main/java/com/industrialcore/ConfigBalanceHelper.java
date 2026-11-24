package com.industrialcore;

/**
 * Phase 115: Pure-Java boundary guards and scaling helpers for the 5-category Modpack TOML
 * balance config ({@code industrialcore-server.toml}).
 *
 * <p>Kept strictly free of Minecraft/NeoForge classes so every clamp, default, and scaling rule
 * is directly unit testable on a plain JVM.</p>
 */
public final class ConfigBalanceHelper {
    public static final double DEFAULT_GENERATION_MULT = 1.0D;
    public static final double DEFAULT_OVERDRIVE_MULT = 1.45D;
    public static final double DEFAULT_PROCESSING_SPEED_MULT = 1.0D;
    public static final double DEFAULT_ENERGY_CONSUMPTION_MULT = 1.0D;
    public static final double DEFAULT_TURRET_DAMAGE_MULT = 1.0D;
    public static final int DEFAULT_TURRET_BASE_RANGE = 16;
    public static final double DEFAULT_WEAPON_DAMAGE_MULT = 1.0D;
    public static final double DEFAULT_MACHINE_XP_MULT = 1.0D;
    public static final int DEFAULT_BASE_UPGRADE_LIMIT = 4;
    public static final int DEFAULT_MAX_STANDARD_UPGRADE_LIMIT = 96;
    public static final boolean DEFAULT_ENABLE_DIMENSIONAL_RAID = true;
    public static final int DEFAULT_RAID_ACTIVATION_COST_FE = 500_000;
    public static final double DEFAULT_RAID_DIFFICULTY_MULT = 1.0D;

    private ConfigBalanceHelper() {}

    public static double clampMultiplier(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Scales an integer FE/t output by a positive multiplier without overflow. */
    public static int scalePositiveInt(int base, double multiplier) {
        if (base <= 0) {
            return 0;
        }
        double mult = clampMultiplier(multiplier, 0.0D, 100.0D, 1.0D);
        if (mult <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(1L, Math.round(base * mult)));
    }

    /** Scales a long FE/t output by a positive multiplier without overflow. */
    public static long scalePositiveLong(long base, double multiplier) {
        if (base <= 0L) {
            return 0L;
        }
        double mult = clampMultiplier(multiplier, 0.0D, 100.0D, 1.0D);
        if (mult <= 0.0D) {
            return 0L;
        }
        double scaled = base * mult;
        if (!Double.isFinite(scaled) || scaled >= Long.MAX_VALUE / 2.0D) {
            return Long.MAX_VALUE / 2L;
        }
        return Math.max(1L, Math.round(scaled));
    }

    /** Adjusts processing cycle time inversely by speed multiplier (always >= 1 tick). */
    public static int scaleProcessingTime(int baseTicks, double speedMultiplier) {
        int safe = Math.max(1, baseTicks);
        double mult = clampMultiplier(speedMultiplier, 0.1D, 100.0D, 1.0D);
        if (Math.abs(mult - 1.0D) < 1.0E-9D) {
            return safe;
        }
        return Math.max(1, (int) Math.ceil(safe / mult - 1.0E-9D));
    }

    /** Adjusts energy consumption per tick by consumption multiplier (0.0 allows free-power packs). */
    public static int scaleEnergyConsumption(int baseFePerTick, double consumptionMultiplier) {
        if (baseFePerTick <= 0) {
            return 0;
        }
        double mult = clampMultiplier(consumptionMultiplier, 0.0D, 100.0D, 1.0D);
        if (mult <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(1L, Math.round(baseFePerTick * mult)));
    }

    /** Adjusts Lv.1..99 virtual upgrade cap using configured base &amp; standard ceiling. */
    public static int configuredUpgradeLimit(int level, int baseLimit, int maxStandardLimit) {
        int lo = clampInt(baseLimit, 1, 64);
        int hi = Math.max(lo, clampInt(maxStandardLimit, 4, 512));
        if (level <= 1) {
            return lo;
        }
        if (level <= 99) {
            return lo + (int) Math.floor((double) (hi - lo) * (level - 1) / 98.0D);
        }
        int clamped = Math.min(1000, level);
        int lbCap = Math.max(hi, 1000);
        return hi + (int) Math.floor((double) (lbCap - hi) * (clamped - 99) / 901.0D);
    }
}
