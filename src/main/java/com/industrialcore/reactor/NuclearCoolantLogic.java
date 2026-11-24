package com.industrialcore.reactor;

/**
 * Phase 105.6: Pure-Java hybrid water-cooling &amp; safety-throttle balance logic for the
 * Nuclear Generator multiblock ({@code nuclear_generator}).
 *
 * <p>Design goals ("いい塩梅" — rewarding without punishing):</p>
 * <ul>
 *   <li>Internal water coolant tank holds {@link #TANK_CAPACITY_MB} mB (16 buckets).</li>
 *   <li>Accepts water via either manual GUI water-bucket insertion ({@code +1,000 mB}, returning
 *       an empty bucket) or automated {@code fluid_pump} &rarr; {@code fluid_pipe} &rarr;
 *       {@code reactor_port} piping.</li>
 *   <li>Consumes {@link #WATER_CONSUME_MB_PER_INTERVAL} mB every
 *       {@link #WATER_CONSUME_INTERVAL_TICKS} ticks while actively burning (1 bucket lasts ~2.8
 *       minutes of continuous operation).</li>
 *   <li>When coolant runs dry ({@code 0 mB}), the reactor NEVER explodes or takes damage; instead
 *       its automatic safety throttle reduces output to {@link #DRY_OUTPUT_PERCENT}% (25%) until
 *       water is restored.</li>
 * </ul>
 */
public final class NuclearCoolantLogic {
    /** Internal water tank capacity (16 buckets = 16,000 mB). */
    public static final int TANK_CAPACITY_MB = 16_000;
    /** Water volume added by one vanilla water bucket. */
    public static final int BUCKET_MB = 1_000;
    /** Server ticks between each coolant consumption pulse while actively burning. */
    public static final int WATER_CONSUME_INTERVAL_TICKS = 20;
    /** Water consumed per pulse while burning (6 mB / 20 ticks = 0.3 mB/tick; 1 bucket ≈ 2m47s). */
    public static final int WATER_CONSUME_MB_PER_INTERVAL = 6;
    /** Output percentage when coolant is dry (0 mB): safety throttle, never explodes. */
    public static final int DRY_OUTPUT_PERCENT = 25;

    private NuclearCoolantLogic() {
    }

    /** Returns {@code true} when at least 1 mB of cooling water is available. */
    public static boolean isCooled(int coolantMb) {
        return coolantMb > 0;
    }

    /**
     * Effective FE/t output for a formed nuclear multiblock: 100% when cooled, 25% safety-throttled
     * when dry (clamped to at least 1 FE/t whenever {@code baseOutput > 0}).
     */
    public static int effectiveOutput(int baseOutput, int coolantMb) {
        if (baseOutput <= 0) {
            return 0;
        }
        if (coolantMb > 0) {
            return baseOutput;
        }
        return Math.max(1, (int) ((long) baseOutput * DRY_OUTPUT_PERCENT / 100L));
    }

    /** Returns {@code true} when a full 1,000 mB water bucket fits cleanly inside the tank. */
    public static boolean canAcceptWaterBucket(int currentCoolantMb) {
        int clamped = Math.max(0, currentCoolantMb);
        return clamped + BUCKET_MB <= TANK_CAPACITY_MB;
    }

    /** Resulting coolant level after pouring one water bucket. */
    public static int afterWaterBucket(int currentCoolantMb) {
        return Math.min(TANK_CAPACITY_MB, Math.max(0, currentCoolantMb) + BUCKET_MB);
    }

    /** Resulting coolant level after one burn-consumption pulse. */
    public static int afterConsumePulse(int currentCoolantMb) {
        return Math.max(0, currentCoolantMb - WATER_CONSUME_MB_PER_INTERVAL);
    }

    /** Exact expected burn ticks covered by a single 1,000 mB water bucket. */
    public static int ticksPerBucket() {
        return BUCKET_MB * WATER_CONSUME_INTERVAL_TICKS / WATER_CONSUME_MB_PER_INTERVAL;
    }

    /** Normalized fill fraction in {@code [0.0, 1.0]} for GUI bar rendering. */
    public static float fillRatio(int currentCoolantMb) {
        if (currentCoolantMb <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) currentCoolantMb / (float) TANK_CAPACITY_MB);
    }
}
