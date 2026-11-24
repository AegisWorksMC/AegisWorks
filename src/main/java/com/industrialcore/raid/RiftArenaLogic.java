package com.industrialcore.raid;

import java.util.List;

/**
 * Phase 120: Pure-Java logic for the dedicated Void Arena dimension ({@code industrialcore:rift_arena}),
 * countdown timer, safe return-coordinate management, and "Overwhelming Strength x Clear Weakness"
 * tactical combat multipliers.
 */
public final class RiftArenaLogic {
    public static final String ARENA_DIMENSION_ID = "industrialcore:rift_arena";
    public static final int ARENA_SPACING = 1024;
    public static final int ARENA_Y = 80;
    public static final int ARENA_RADIUS = 28;
    public static final int VOID_RESCUE_Y = ARENA_Y - 12;
    public static final int VICTORY_RETURN_GRACE_TICKS = 200; // 10 seconds to collect drops

    // Countdown timer durations by Rift Tier
    public static final int TIME_LIMIT_LOW_SEC = 180;   // Tier 1..3
    public static final int TIME_LIMIT_MID_SEC = 210;   // Tier 4..7
    public static final int TIME_LIMIT_HIGH_SEC = 240;  // Tier 8..10
    public static final int WARNING_THRESHOLD_TICKS = 600; // 30 seconds

    // Void fall penalty (15% max HP, non-lethal rescue to center)
    public static final float VOID_FALL_HP_FRACTION = 0.15F;

    // Tactical weakness balance constants
    public static final float JUGGERNAUT_RANGED_SHIELD_MULT = 0.05F;  // 95% cut against ranged while shields up
    public static final float JUGGERNAUT_MELEE_NON_BLADE_MULT = 0.35F;
    public static final int JUGGERNAUT_BREAK_STUN_TICKS = 70;         // 3.5 seconds stun
    public static final float JUGGERNAUT_BROKEN_WEAKNESS_MULT = 2.20F;

    public static final float SENTINEL_CLOSED_DAMAGE_MULT = 0.55F;
    public static final float SENTINEL_OPEN_CORE_RANGED_MULT = 2.50F; // 2.5x weakpoint critical
    public static final int SENTINEL_CRASH_STUN_TICKS = 50;           // 2.5 seconds grounded stun

    public static final int STALKER_JAM_HIT_THRESHOLD = 3;
    public static final int STALKER_JAM_WINDOW_TICKS = 40;
    public static final int STALKER_JAM_DURATION_TICKS = 60;          // 3.0 seconds phase jam

    public static final float BOSS_BARRIER_HP_THRESHOLD_1 = 0.60F;
    public static final float BOSS_BARRIER_HP_THRESHOLD_2 = 0.25F;
    public static final float BOSS_BARRIER_DAMAGE_MULT = 0.10F;       // 90% cut while barrier active
    public static final int BOSS_EXPOSED_STUN_TICKS = 100;            // 5.0 seconds exposed-core stun
    public static final float BOSS_EXPOSED_WEAKNESS_MULT = 2.00F;
    public static final int BOSS_ANCHOR_CRYSTAL_COUNT = 3;

    /** Persistent saved return point for a player transported into {@code industrialcore:rift_arena}. */
    public record ReturnPoint(
            String dimensionId,
            double x,
            double y,
            double z,
            float yRot,
            float xRot
    ) {
        public ReturnPoint {
            if (dimensionId == null || dimensionId.isBlank() || ARENA_DIMENSION_ID.equals(dimensionId)) {
                dimensionId = "minecraft:overworld";
            }
            if (!Double.isFinite(x)) x = 0.0D;
            if (!Double.isFinite(y)) y = 80.0D;
            if (!Double.isFinite(z)) z = 0.0D;
            if (!Float.isFinite(yRot)) yRot = 0.0F;
            if (!Float.isFinite(xRot)) xRot = 0.0F;
        }
    }

    /** Pillar position offset relative to arena center (dx, dy, dz). */
    public record PillarSpec(int dx, int height, int dz) {}

    /** 4 elevated sniper pillars (+6..+9 height) favoring Void Sentinel high-ground fire &amp; boss anchor crystals. */
    public static final List<PillarSpec> SNIPER_PILLARS = List.of(
            new PillarSpec(17, 7, 17),
            new PillarSpec(-17, 8, 17),
            new PillarSpec(17, 9, -17),
            new PillarSpec(-17, 6, -17)
    );

    private RiftArenaLogic() {}

    /** Computes non-overlapping arena center X coordinate (1024-block stride per instance). */
    public static int arenaCenterX(int instanceIndex) {
        int safe = Math.max(0, instanceIndex);
        return safe * ARENA_SPACING;
    }

    /** Returns raid countdown time limit in seconds for Rift Tier 1..10. */
    public static int timeLimitSeconds(int tier) {
        int t = DimensionalRaidLogic.clampTier(tier);
        if (t <= 3) return TIME_LIMIT_LOW_SEC;
        if (t <= 7) return TIME_LIMIT_MID_SEC;
        return TIME_LIMIT_HIGH_SEC;
    }

    /** Returns raid countdown time limit in server ticks (20 ticks/sec). */
    public static int timeLimitTicks(int tier) {
        return timeLimitSeconds(tier) * 20;
    }

    /** Formats remaining ticks as {@code MM:SS}. */
    public static String formatCountdown(int remainingTicks) {
        int secs = Math.max(0, (remainingTicks + 19) / 20);
        int m = secs / 60;
        int s = secs % 60;
        return String.format("%02d:%02d", m, s);
    }

    /** True during the final 30 seconds of a raid countdown. */
    public static boolean isWarningPeriod(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks <= WARNING_THRESHOLD_TICKS;
    }

    /** True when the raid countdown has expired. */
    public static boolean isTimeExpired(int remainingTicks) {
        return remainingTicks <= 0;
    }

    /** Computes non-lethal void fall rescue penalty (15% of max HP, always leaving >= 1.0 HP). */
    public static float voidFallPenaltyDamage(float currentHp, float maxHp) {
        float raw = Math.max(1.0F, maxHp * VOID_FALL_HP_FRACTION);
        return Math.max(0.0F, Math.min(raw, Math.max(0.0F, currentHp - 1.0F)));
    }

    // -------------------------------------------------------------------------
    // Tactical Weakness Combat Multipliers
    // -------------------------------------------------------------------------

    /** Outcome of a hit against {@code phase_juggernaut}. */
    public enum JuggernautHitResult {
        SHIELD_BROKEN_WEAKNESS(JUGGERNAUT_BROKEN_WEAKNESS_MULT, false),
        TRIGGER_SHIELD_BREAK(1.50F, true),
        RANGED_BLOCKED_95(JUGGERNAUT_RANGED_SHIELD_MULT, false),
        FRONT_MELEE_GUARDED(JUGGERNAUT_MELEE_NON_BLADE_MULT, false),
        FLANK_FULL(1.00F, false);

        private final float damageMultiplier;
        private final boolean triggersBreak;

        JuggernautHitResult(float damageMultiplier, boolean triggersBreak) {
            this.damageMultiplier = damageMultiplier;
            this.triggersBreak = triggersBreak;
        }

        public float damageMultiplier() {
            return damageMultiplier;
        }

        public boolean triggersBreak() {
            return triggersBreak;
        }
    }

    /**
     * Evaluates a hit against {@code phase_juggernaut}:
     * <ul>
     *   <li>If already shield-broken (stunned): ALL weapons deal {@code 2.2x} weakness damage.</li>
     *   <li>If hit by {@code quantum_blade}: immediately triggers SHIELD BREAK (3.5s stun).</li>
     *   <li>If hit from the front by any ranged weapon while shields are up: 95% cut ({@code 0.05x}).</li>
     * </ul>
     */
    public static JuggernautHitResult evaluateJuggernautHit(
            boolean shieldBroken, boolean isQuantumBlade, boolean isRanged, double facingDot) {
        if (shieldBroken) {
            return JuggernautHitResult.SHIELD_BROKEN_WEAKNESS;
        }
        if (isQuantumBlade) {
            return JuggernautHitResult.TRIGGER_SHIELD_BREAK;
        }
        if (facingDot > 0.25D) {
            return isRanged ? JuggernautHitResult.RANGED_BLOCKED_95 : JuggernautHitResult.FRONT_MELEE_GUARDED;
        }
        return JuggernautHitResult.FLANK_FULL;
    }

    /**
     * Evaluates a hit against {@code void_sentinel}:
     * Ranged weapon hit while petal armor is open (charging) deals {@code 2.5x} critical &amp; crash-stuns!
     */
    public static float evaluateSentinelDamageMultiplier(boolean coreExposedOrCharging, boolean isRanged, boolean crashStunned) {
        if (crashStunned) {
            return 1.65F;
        }
        if (coreExposedOrCharging && isRanged) {
            return SENTINEL_OPEN_CORE_RANGED_MULT;
        }
        return coreExposedOrCharging ? 1.15F : SENTINEL_CLOSED_DAMAGE_MULT;
    }

    /** Returns true if hitting {@code void_sentinel} should trigger crash-stun. */
    public static boolean shouldSentinelCrashStun(boolean coreExposedOrCharging, boolean isRanged, boolean alreadyStunned) {
        return !alreadyStunned && coreExposedOrCharging && isRanged;
    }

    /**
     * Evaluates whether {@code dimensional_sovereign} should activate an Absolute Phase Barrier phase
     * (Phase A at HP &le; 60%, Phase B at HP &le; 25%).
     */
    public static int nextBossBarrierStage(float currentHp, float maxHp, int completedBarrierStages, boolean barrierActive) {
        if (barrierActive || maxHp <= 0.0F || currentHp <= 0.0F) {
            return 0;
        }
        float ratio = currentHp / maxHp;
        if (completedBarrierStages < 1 && ratio <= BOSS_BARRIER_HP_THRESHOLD_1) {
            return 1;
        }
        if (completedBarrierStages < 2 && ratio <= BOSS_BARRIER_HP_THRESHOLD_2) {
            return 2;
        }
        return 0;
    }

    /** Damage multiplier for {@code dimensional_sovereign} based on Absolute Barrier / Exposed Stun state. */
    public static float evaluateBossDamageMultiplier(boolean barrierActive, boolean exposedStunned) {
        if (barrierActive) {
            return BOSS_BARRIER_DAMAGE_MULT;
        }
        if (exposedStunned) {
            return BOSS_EXPOSED_WEAKNESS_MULT;
        }
        return 1.00F;
    }
}
