package com.industrialcore.reactor;

/**
 * Phase 114: Pure-Java risk-reward math for Reactor High-Output Overdrive Mode
 * ({@code nuclear_generator} &amp; {@code singularity_reactor}) and multiblock activation ritual timing.
 *
 * <p>Overdrive boosts FE/t output to {@code 1.45x} and machine XP gain to {@code 1.25x}, at the
 * cost of {@code 1.60x} fuel burn speed and {@code 2.00x} coolant consumption. Running dry during
 * Overdrive triggers a non-destructive safety emergency cooldown ({@code 200 ticks = 10s}) instead
 * of base-destroying explosions.</p>
 */
public final class ReactorOverdriveLogic {
    /** FE/t generation multiplier during Overdrive (+45%). */
    public static final double OUTPUT_MULTIPLIER = 1.45D;
    /** Machine XP gain multiplier during Overdrive (+25%). */
    public static final double XP_MULTIPLIER = 1.25D;
    /** Fuel consumption speed multiplier during Overdrive (+60%). */
    public static final double FUEL_BURN_SPEED_MULTIPLIER = 1.60D;
    /** Cooling water consumption speed multiplier during Overdrive (2.0x). */
    public static final double COOLANT_CONSUMPTION_MULTIPLIER = 2.00D;

    /** Emergency automatic shutdown cooldown duration (10 seconds = 200 ticks). */
    public static final int EMERGENCY_COOLDOWN_TICKS = 200;

    /** Multiblock formation ritual charge ("tame") duration before shockwave release (0.75s = 15 ticks). */
    public static final int RITUAL_CHARGE_TICKS = 15;

    /** 3D BER ring angular speed multiplier during Overdrive. */
    public static final float OVERDRIVE_RING_SPEED_MULT = 2.15F;
    /** 3D BER core glow boost multiplier during Overdrive. */
    public static final float OVERDRIVE_GLOW_BOOST = 1.28F;

    private ReactorOverdriveLogic() {}

    /** Computes effective {@code long} FE/t output accounting for Overdrive and emergency cooldown. */
    public static long effectiveOutput(long baseOutput, boolean overdrive, int cooldownTicks) {
        if (cooldownTicks > 0 || baseOutput <= 0L) {
            return 0L;
        }
        if (!overdrive) {
            return baseOutput;
        }
        double scaled = baseOutput * OUTPUT_MULTIPLIER;
        if (!Double.isFinite(scaled) || scaled >= Long.MAX_VALUE / 2.0D) {
            return Long.MAX_VALUE / 2L;
        }
        return Math.max(baseOutput, Math.round(scaled));
    }

    /** Computes effective {@code int} FE/t output accounting for Overdrive and emergency cooldown. */
    public static int effectiveIntOutput(int baseOutput, boolean overdrive, int cooldownTicks) {
        if (cooldownTicks > 0 || baseOutput <= 0) {
            return 0;
        }
        if (!overdrive) {
            return baseOutput;
        }
        long scaled = Math.round(baseOutput * OUTPUT_MULTIPLIER);
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(baseOutput, scaled));
    }

    /** Computes effective XP awarded during Overdrive (+25%). */
    public static long effectiveXp(long baseXp, boolean overdrive) {
        if (baseXp <= 0L) {
            return 0L;
        }
        return overdrive ? Math.max(baseXp, Math.round(baseXp * XP_MULTIPLIER)) : baseXp;
    }

    /** Computes fuel burn progress advance per tick (1.60x in Overdrive). */
    public static int burnProgressDelta(int basePerTick, boolean overdrive) {
        int safe = Math.max(1, basePerTick);
        if (!overdrive) {
            return safe;
        }
        return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(safe + 1L, Math.round(safe * FUEL_BURN_SPEED_MULTIPLIER)));
    }

    /** Computes coolant consumption interval in ticks (halved = 2.0x consumption speed in Overdrive). */
    public static int coolantConsumeInterval(int baseIntervalTicks, boolean overdrive) {
        int safe = Math.max(1, baseIntervalTicks);
        if (!overdrive) {
            return safe;
        }
        return Math.max(1, (int) Math.round(safe / COOLANT_CONSUMPTION_MULTIPLIER));
    }

    /** True when Overdrive must enter safe emergency shutdown cooldown because coolant/catalyst ran dry. */
    public static boolean shouldTriggerEmergencyShutdown(boolean overdrive, boolean activeBurn, boolean resourceDepleted) {
        return overdrive && activeBurn && resourceDepleted;
    }

    /** Ritual charge normalized progress {@code 0.0 -> 1.0} over the 15-tick buildup. */
    public static float ritualChargeProgress(int remainingTicks) {
        int clamped = Math.max(0, Math.min(RITUAL_CHARGE_TICKS, remainingTicks));
        return 1.0F - (clamped / (float) RITUAL_CHARGE_TICKS);
    }

    /** Rising charge pitch from {@code 0.72f} up to {@code 1.58f} during the 15-tick buildup. */
    public static float ritualChargePitch(int remainingTicks) {
        return 0.72F + 0.86F * ritualChargeProgress(remainingTicks);
    }

    public static float ringSpeedMultiplier(boolean overdrive) {
        return overdrive ? OVERDRIVE_RING_SPEED_MULT : 1.0F;
    }

    public static float glowPulseBoost(boolean overdrive) {
        return overdrive ? OVERDRIVE_GLOW_BOOST : 1.0F;
    }
}
