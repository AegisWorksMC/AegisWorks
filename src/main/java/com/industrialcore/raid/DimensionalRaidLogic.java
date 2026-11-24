package com.industrialcore.raid;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 115 / 116: Pure-Java core math and state rules for the voluntary RPG depth-tiered
 * factory defense challenge "Dimensional Rift Raid" (Tier 1..10) triggered from a formed
 * Singularity Reactor.
 *
 * <p>Strictly free of Minecraft classes so Tier 1..10 unlock rules, original 4-enemy wave
 * compositions ({@code rift_stalker}, {@code void_sentinel}, {@code phase_juggernaut},
 * {@code dimensional_sovereign}), [18..26] spawn ring geometry, start guards, BossBar progress,
 * timeout rules, and Tier-scaled rewards are 100% unit testable on a plain JVM.</p>
 */
public final class DimensionalRaidLogic {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 10;
    public static final int TOTAL_WAVES = 3;
    public static final int SPAWN_MIN_RADIUS = 18;
    public static final int SPAWN_MAX_RADIUS = 26;
    /** Cooldown between consecutive raids on the same reactor (30 seconds = 600 ticks). */
    public static final int COOLDOWN_TICKS = 600;
    /** Safety timeout so a lost/stuck mob never locks a reactor indefinitely (10 minutes). */
    public static final int TIMEOUT_TICKS = 12_000;
    /** Inter-wave portal charge delay (2 seconds = 40 ticks). */
    public static final int WAVE_DELAY_TICKS = 40;
    /** Base Tier 1 machine XP bonus awarded for Singularity Reactor on victory. */
    public static final long VICTORY_XP_BONUS = 25_000L;

    /** Persistent entity tag marking all Dimensional Rift Raid invaders (for zero-griefing &amp; tracking). */
    public static final String INVADER_TAG = "aegisworks_rift_invader";
    public static final String BOSS_TAG = "aegisworks_rift_boss";

    public record WaveSpec(
            int waveNumber,
            int tier,
            int stalkerCount,
            int sentinelCount,
            int juggernautCount,
            boolean hasBoss
    ) {
        public int totalCount() {
            return stalkerCount + sentinelCount + juggernautCount + (hasBoss ? 1 : 0);
        }
    }

    public record SpawnOffset(int dx, int dz) {
        public double distance() {
            return Math.hypot(dx, dz);
        }
    }

    public record RewardBundle(
            int specializationModules,
            int phaseCores,
            int quantumShards,
            int nebulaCrystals,
            int diamonds,
            long machineXp
    ) {}

    public enum StartDenial {
        ALLOWED,
        DISABLED_BY_CONFIG,
        NOT_FORMED,
        ALREADY_ACTIVE,
        ON_COOLDOWN,
        INSUFFICIENT_ENERGY,
        TIER_LOCKED
    }

    private DimensionalRaidLogic() {}

    /** Clamps any requested Rift depth tier cleanly into {@code [1, 10]}. */
    public static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    /**
     * Computes the new highest unlocked Rift Tier after clearing {@code clearedTier}.
     * Clearing your current max unlocked tier unlocks {@code currentUnlocked + 1} (up to Tier 10).
     */
    public static int nextUnlockedTier(int currentUnlocked, int clearedTier) {
        int cur = clampTier(currentUnlocked);
        int clr = clampTier(clearedTier);
        if (clr >= cur && cur < MAX_TIER) {
            return cur + 1;
        }
        return cur;
    }

    /** Activation FE cost scaled smoothly by selected Tier (Tier 1 = 1.00x, Tier 10 = 2.35x). */
    public static long activationCostForTier(long baseCostFe, int tier) {
        long safeBase = Math.max(0L, baseCostFe);
        int t = clampTier(tier);
        return Math.round(safeBase * (1.0D + 0.15D * (t - 1)));
    }

    /**
     * Tier HP multiplier:
     * Tier 1-2 (Intro/Mid): 1.00x / 1.35x
     * Tier 3-5 (Aegis Recommended): 1.90x / 2.60x / 3.45x
     * Tier 6-10 (Endgame Max Gear): 4.50x .. 10.50x
     */
    public static double tierHealthMultiplier(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 1.00D;
            case 2 -> 1.35D;
            case 3 -> 1.90D;
            case 4 -> 2.60D;
            case 5 -> 3.45D;
            case 6 -> 4.50D;
            case 7 -> 5.75D;
            case 8 -> 7.15D;
            case 9 -> 8.75D;
            default -> 10.50D;
        };
    }

    /** Tier attack damage multiplier (1.00x at Tier 1 -> 4.00x at Tier 10). */
    public static double tierAttackMultiplier(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 1.00D;
            case 2 -> 1.20D;
            case 3 -> 1.45D;
            case 4 -> 1.72D;
            case 5 -> 2.00D;
            case 6 -> 2.35D;
            case 7 -> 2.72D;
            case 8 -> 3.12D;
            case 9 -> 3.55D;
            default -> 4.00D;
        };
    }

    /** Tier movement speed multiplier (1.00x at Tier 1 -> 1.32x at Tier 10). */
    public static double tierSpeedMultiplier(int tier) {
        int t = clampTier(tier);
        return 1.00D + (t - 1) * 0.035D;
    }

    /** Evaluates whether a Dimensional Rift Raid can be started right now (legacy Tier 1 overload). */
    public static StartDenial checkStart(boolean enabledByConfig, boolean multiblockFormed,
                                         boolean alreadyActive, int cooldownTicks,
                                         long storedEnergy, long activationCost) {
        return checkStart(enabledByConfig, multiblockFormed, alreadyActive, cooldownTicks,
                storedEnergy, activationCost, 1, 1);
    }

    /** Evaluates whether a Dimensional Rift Raid at {@code selectedTier} can be started right now. */
    public static StartDenial checkStart(boolean enabledByConfig, boolean multiblockFormed,
                                         boolean alreadyActive, int cooldownTicks,
                                         long storedEnergy, long activationCost,
                                         int selectedTier, int unlockedMaxTier) {
        if (!enabledByConfig) {
            return StartDenial.DISABLED_BY_CONFIG;
        }
        if (!multiblockFormed) {
            return StartDenial.NOT_FORMED;
        }
        if (alreadyActive) {
            return StartDenial.ALREADY_ACTIVE;
        }
        if (cooldownTicks > 0) {
            return StartDenial.ON_COOLDOWN;
        }
        int safeSelected = clampTier(selectedTier);
        int safeUnlocked = clampTier(unlockedMaxTier);
        if (safeSelected > safeUnlocked) {
            return StartDenial.TIER_LOCKED;
        }
        if (storedEnergy < Math.max(0L, activationCost)) {
            return StartDenial.INSUFFICIENT_ENERGY;
        }
        return StartDenial.ALLOWED;
    }

    /** Default Tier 1 wave composition overload. */
    public static WaveSpec waveSpec(int waveNumber) {
        return waveSpec(waveNumber, 1);
    }

    /**
     * Returns the original 4-enemy wave composition for {@code waveNumber} (1..3) at {@code tier} (1..10).
     * {@code phase_juggernaut} joins from Tier 3 onward; {@code dimensional_sovereign} descends in Wave 3.
     */
    public static WaveSpec waveSpec(int waveNumber, int tier) {
        int t = clampTier(tier);
        int extraTier = (t >= 6) ? 2 : (t >= 3 ? 1 : 0);
        int jugBase = (t >= 3) ? (t >= 7 ? 3 : (t >= 5 ? 2 : 1)) : 0;

        return switch (waveNumber) {
            case 1 -> new WaveSpec(1, t,
                    3 + extraTier,
                    2 + (t >= 4 ? 1 : 0),
                    t >= 6 ? 1 : 0,
                    false);
            case 2 -> new WaveSpec(2, t,
                    3 + extraTier,
                    2 + extraTier,
                    jugBase,
                    false);
            default -> new WaveSpec(3, t,
                    3 + extraTier,
                    2 + (t >= 5 ? 2 : 1),
                    jugBase,
                    true);
        };
    }

    /**
     * Generates evenly distributed horizontal spawn offsets in the safe perimeter ring
     * {@code [SPAWN_MIN_RADIUS, SPAWN_MAX_RADIUS]} (18..26 blocks) around the reactor.
     */
    public static List<SpawnOffset> computeSpawnRing(int count, int waveSeed) {
        int safeCount = Math.max(1, count);
        List<SpawnOffset> out = new ArrayList<>(safeCount);
        double baseAngle = (waveSeed * 1.17D) % (Math.PI * 2.0D);
        for (int i = 0; i < safeCount; i++) {
            double ang = baseAngle + i * (Math.PI * 2.0D / safeCount);
            double radius = SPAWN_MIN_RADIUS + ((i * 3 + waveSeed * 2) % (SPAWN_MAX_RADIUS - SPAWN_MIN_RADIUS + 1));
            int dx = (int) Math.round(Math.cos(ang) * radius);
            int dz = (int) Math.round(Math.sin(ang) * radius);
            double d = Math.hypot(dx, dz);
            if (d < SPAWN_MIN_RADIUS) {
                double scale = (SPAWN_MIN_RADIUS + 0.5D) / Math.max(1.0D, d);
                dx = (int) Math.round(dx * scale);
                dz = (int) Math.round(dz * scale);
            } else if (d > SPAWN_MAX_RADIUS) {
                double scale = (SPAWN_MAX_RADIUS - 0.5D) / d;
                dx = (int) Math.round(dx * scale);
                dz = (int) Math.round(dz * scale);
            }
            out.add(new SpawnOffset(dx, dz));
        }
        return out;
    }

    /** Scales invader max health safely by multiplier (clamped to [0.25, 40.0] for Tier 10 endgame bosses). */
    public static double scaledHealth(double baseHp, double multiplier) {
        double safeBase = Math.max(4.0D, baseHp);
        double mult = Double.isFinite(multiplier)
                ? Math.max(0.25D, Math.min(40.0D, multiplier)) : 1.0D;
        return Math.max(4.0D, Math.round(safeBase * mult * 10.0D) / 10.0D);
    }

    /** Scales invader attack damage safely by multiplier (clamped to [0.25, 20.0]). */
    public static double scaledAttack(double baseAtk, double multiplier) {
        double safeBase = Math.max(1.0D, baseAtk);
        double mult = Double.isFinite(multiplier)
                ? Math.max(0.25D, Math.min(20.0D, multiplier)) : 1.0D;
        return Math.max(1.0D, Math.round(safeBase * mult * 10.0D) / 10.0D);
    }

    /** Normalized BossBar progress {@code [0.0f, 1.0f]} for the active wave. */
    public static float bossBarProgress(int aliveCount, int waveTotal) {
        if (waveTotal <= 0 || aliveCount <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) aliveCount / (float) waveTotal));
    }

    /** True if the raid has exceeded its safety timeout. */
    public static boolean isTimedOut(int elapsedTicks) {
        return elapsedTicks >= TIMEOUT_TICKS;
    }

    /** Tier 1 legacy overload. */
    public static RewardBundle victoryRewards(double difficultyMultiplier) {
        return victoryRewards(1, difficultyMultiplier);
    }

    /**
     * Computes RPG depth-scaled victory rewards:
     * - Tier 1: 4 Specialization Modules, 2 Phase Cores, 25,000 XP
     * - Tier 5: 13 Specialization Modules, 5 Phase Cores, 115,000 XP
     * - Tier 10: 28 Specialization Modules, 11 Phase Cores, 250,000 XP
     */
    public static RewardBundle victoryRewards(int tier, double difficultyMultiplier) {
        int t = clampTier(tier);
        double diff = Double.isFinite(difficultyMultiplier)
                ? Math.max(0.5D, Math.min(5.0D, difficultyMultiplier)) : 1.0D;
        // Smooth RPG reward progression across Tier 1..10
        int baseSpecs = switch (t) {
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 8;
            case 4 -> 10;
            case 5 -> 13;
            case 6 -> 16;
            case 7 -> 19;
            case 8 -> 22;
            case 9 -> 25;
            default -> 28;
        };
        long baseXp = switch (t) {
            case 1 -> 25_000L;
            case 2 -> 42_000L;
            case 3 -> 62_000L;
            case 4 -> 86_000L;
            case 5 -> 115_000L;
            case 6 -> 142_000L;
            case 7 -> 170_000L;
            case 8 -> 198_000L;
            case 9 -> 225_000L;
            default -> 250_000L;
        };
        int specs = Math.max(2, (int) Math.round(baseSpecs * diff));
        int phaseCores = Math.max(1, (int) Math.round((1 + t) * diff));
        int quantumShards = Math.max(4, (int) Math.round((5 + t * 3) * diff));
        int nebulaCrystals = Math.max(4, (int) Math.round((5 + t * 3) * diff));
        int diamonds = Math.max(8, (int) Math.round((8 + t * 4) * diff));
        long xp = Math.max(VICTORY_XP_BONUS, Math.round(baseXp * diff));
        return new RewardBundle(specs, phaseCores, quantumShards, nebulaCrystals, diamonds, xp);
    }

    // -------------------------------------------------------------------------
    // Phase 119: Pure-Java Rift Boss Overdrive, Phase Shield & FX Clamp Helpers
    // -------------------------------------------------------------------------

    /** Hard cap on per-event client particle bursts for Rift combat &amp; portal FX. */
    public static final int MAX_PARTICLES_PER_FX_BURST = 12;

    /** Directional frontal phase shield impact outcome for {@code phase_juggernaut}. */
    public enum ShieldHitOutcome {
        FRONT_DEFLECT(0.40F),
        FRONT_PIERCE(0.75F),
        FLANK_EXPOSED(1.00F);

        private final float damageMultiplier;

        ShieldHitOutcome(float damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
        }

        public float damageMultiplier() {
            return damageMultiplier;
        }
    }

    /** Classifies a hit against {@code phase_juggernaut}'s front ±72° phase shield (dot &gt; 0.30). */
    public static ShieldHitOutcome evaluateShieldHit(double facingDot, boolean piercing) {
        if (facingDot > 0.30D) {
            return piercing ? ShieldHitOutcome.FRONT_PIERCE : ShieldHitOutcome.FRONT_DEFLECT;
        }
        return ShieldHitOutcome.FLANK_EXPOSED;
    }

    /** Returns {@code true} when {@code dimensional_sovereign} should transition to Overdrive Phase 2 (HP &le; 50%). */
    public static boolean shouldEnterOverdrive(float currentHp, float maxHp, boolean alreadyOverdrive) {
        return !alreadyOverdrive && maxHp > 0.0F && currentHp > 0.0F && currentHp <= maxHp * 0.50F;
    }

    /** Clamps per-event client FX particle bursts while obeying {@code ParticleStatus.MINIMAL}. */
    public static int clampFxParticleCount(int requested, boolean minimalSetting) {
        if (minimalSetting || requested <= 0) {
            return 0;
        }
        return Math.min(MAX_PARTICLES_PER_FX_BURST, requested);
    }
}
