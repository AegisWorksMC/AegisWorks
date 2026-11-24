package com.industrialcore.trap;

import com.industrialcore.machine.UpgradeMath;

import java.util.Set;

/**
 * Pure-Java gameplay rules &amp; math for the Phase 93 Trap Branch wave 2:
 * <ul>
 *   <li>{@code xp_collector} (Tier 3: 10..20 block orb suction, 10M XP buffer, +1/+10/All withdrawal, auto-bottling)</li>
 *   <li>{@code spawn_suppressor} (Tier 3: 32..64 block natural hostile spawn suppression without blocking SPAWNER)</li>
 *   <li>{@code laser_turret} (Tier 4: 16..28 block LOS laser turret, enemy-only strict safety filter)</li>
 *   <li>{@code mob_duplicator} + {@code mob_capsule} (Tier 5: mob DNA capture, boss block, 16-mob crowd cap)</li>
 * </ul>
 *
 * <p>Zero Minecraft imports so every formula and safety rule is deterministically unit tested.</p>
 */
public final class TrapWave2Logic {
    // --- 1. XP Collector (Tier 3) ---
    public static final int XP_BASE_RADIUS = 10;
    public static final int XP_MAX_RADIUS = 20;
    public static final long XP_MAX_STORAGE = 10_000_000L;
    public static final int XP_PER_BOTTLE = 7;
    public static final int XP_BASE_SCAN_TICKS = 10;
    public static final int XP_BASE_BOTTLE_TICKS = 40;
    public static final int XP_BASE_COLLECT_ENERGY = 15;
    public static final int XP_BASE_BOTTLE_ENERGY = 60;
    public static final int XP_BASE_CAPACITY = 50_000;
    public static final int XP_MAX_RECEIVE = 2_000;
    public static final int XP_MAX_PARTICLES = 6;

    public static final int XP_ACTION_TAKE_1_LV = 0;
    public static final int XP_ACTION_TAKE_10_LV = 1;
    public static final int XP_ACTION_TAKE_ALL = 2;

    // --- 2. Spawn Suppressor (Tier 3) ---
    public static final int SUP_BASE_RADIUS = 32;
    public static final int SUP_MAX_RADIUS = 64;
    public static final int SUP_BASE_ENERGY_PER_TICK = 24;
    public static final int SUP_BASE_CAPACITY = 80_000;
    public static final int SUP_MAX_RECEIVE = 3_200;

    // --- 3. Laser Turret (Tier 4 — Phase 113 Pulse Laser + Specialization) ---
    public static final int TURRET_BASE_RANGE = 16;
    public static final int TURRET_MAX_RANGE = 36;
    public static final int TURRET_BASE_INTERVAL_TICKS = 8;
    public static final float TURRET_BASE_DAMAGE = 6.0F;
    public static final float TURRET_MAX_MOTOR_BONUS_DAMAGE = 16.0F;
    public static final int TURRET_BASE_SHOT_ENERGY = 120;
    public static final int TURRET_BASE_CAPACITY = 120_000;
    public static final int TURRET_MAX_RECEIVE = 4_800;
    public static final int TURRET_MAX_BEAM_POINTS = 20;

    // --- 4. Mob Capsule & Mob Duplicator (Tier 5) ---
    public static final int DUPLICATOR_BASE_INTERVAL_TICKS = 100;
    public static final int DUPLICATOR_MIN_INTERVAL_TICKS = 20;
    public static final int DUPLICATOR_BASE_BATCH = 1;
    public static final int DUPLICATOR_MAX_BATCH = 4;
    public static final int DUPLICATOR_CROWD_LIMIT = 16;
    public static final int DUPLICATOR_SPAWN_HALF_XZ = 3; // 7x7 spawn area
    public static final int DUPLICATOR_CROWD_HALF_XZ = 4; // 9x9 crowd check area
    public static final int DUPLICATOR_BASE_ENERGY = 1_200;
    public static final int DUPLICATOR_BASE_CAPACITY = 200_000;
    public static final int DUPLICATOR_MAX_RECEIVE = 8_000;

    public static final int DUP_STATE_IDLE = 0;
    public static final int DUP_STATE_WORKING = 1;
    public static final int DUP_STATE_NO_CAPSULE = 2;
    public static final int DUP_STATE_CROWDED = 3;
    public static final int DUP_STATE_NO_ENERGY = 4;

    private static final Set<String> DISALLOWED_CAPTURE_IDS = Set.of(
            "minecraft:ender_dragon",
            "minecraft:wither",
            "minecraft:warden",
            "minecraft:elder_guardian",
            "minecraft:player",
            "minecraft:armor_stand"
    );

    private TrapWave2Logic() {}

    private static int clampMotors(int motors) {
        return Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
    }

    // ------------------------------------------------------------------
    // 1. XP Collector math
    // ------------------------------------------------------------------

    /** Effective XP orb collection radius in blocks (10 at 0 motors, scaling linearly to 20 at 64 motors). */
    public static int xpRadius(int motors) {
        int c = clampMotors(motors);
        return XP_BASE_RADIUS + (c * (XP_MAX_RADIUS - XP_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    public static boolean isInXpRange(double distSq, int motors) {
        double r = xpRadius(motors);
        return distSq >= 0.0D && distSq <= r * r;
    }

    /** Orb suction scan interval in ticks (10 at 0 motors down to 3 at 64 motors + mastery). */
    public static int xpScanInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(XP_BASE_SCAN_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Auto-bottling duration per Experience Bottle (40 at 0 motors down to 10 at 64 motors + mastery). */
    public static int xpBottleTime(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(XP_BASE_BOTTLE_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    public static int xpCollectEnergy(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(XP_BASE_COLLECT_ENERGY, batteries), ownerLevel);
    }

    public static int xpBottleEnergy(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(XP_BASE_BOTTLE_ENERGY, batteries), ownerLevel);
    }

    /** Exact vanilla XP required to advance from {@code level} to {@code level + 1}. */
    public static int xpForNextLevel(int level) {
        int l = Math.max(0, level);
        if (l >= 30) {
            return 112 + (l - 30) * 9;
        }
        if (l >= 15) {
            return 37 + (l - 15) * 5;
        }
        return 7 + l * 2;
    }

    /** Total XP needed to advance {@code levelsToGain} full levels from {@code (currentLevel, currentProgress)}. */
    public static long xpNeededForLevels(int currentLevel, float currentProgress, int levelsToGain) {
        if (levelsToGain <= 0) {
            return 0L;
        }
        int baseLevel = Math.max(0, currentLevel);
        double clampedProg = Math.max(0.0D, Math.min(0.9999D, currentProgress));
        int firstCap = xpForNextLevel(baseLevel);
        long firstRem = Math.max(1L, firstCap - Math.round(clampedProg * firstCap));
        long total = firstRem;
        for (int i = 1; i < levelsToGain; i++) {
            total += xpForNextLevel(baseLevel + i);
        }
        return total;
    }

    /** Computes exact XP amount to withdraw from storage for a GUI withdrawal action. */
    public static int computeXpWithdrawal(long storedXp, int playerLevel, float playerProgress, int action) {
        if (storedXp <= 0L) {
            return 0;
        }
        long desired = switch (action) {
            case XP_ACTION_TAKE_1_LV -> xpNeededForLevels(playerLevel, playerProgress, 1);
            case XP_ACTION_TAKE_10_LV -> xpNeededForLevels(playerLevel, playerProgress, 10);
            case XP_ACTION_TAKE_ALL -> storedXp;
            default -> 0L;
        };
        if (desired <= 0L) {
            return 0;
        }
        long grant = Math.min(storedXp, Math.min(desired, Integer.MAX_VALUE));
        return (int) Math.max(0L, grant);
    }

    // ------------------------------------------------------------------
    // 2. Spawn Suppressor math
    // ------------------------------------------------------------------

    /** Effective spawn suppression radius in blocks (32 at 0 motors up to 64 at 64 motors). */
    public static int suppressorRadius(int motors) {
        int c = clampMotors(motors);
        return SUP_BASE_RADIUS + (c * (SUP_MAX_RADIUS - SUP_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    public static boolean isInSuppressorRange(double distSq, int radius) {
        double r = Math.max(0, radius);
        return distSq >= 0.0D && distSq <= r * r;
    }

    public static int suppressorEnergyPerTick(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(SUP_BASE_ENERGY_PER_TICK, batteries), ownerLevel);
    }

    /**
     * Suppresses ONLY natural hostile mob spawns so {@code mob_duplicator} ({@code SPAWNER}),
     * spawn eggs, commands, and passive/animal spawns remain completely unaffected.
     */
    public static boolean shouldSuppressSpawn(boolean isNaturalSpawn, boolean isHostileMob) {
        return isNaturalSpawn && isHostileMob;
    }

    // ------------------------------------------------------------------
    // 3. Laser Turret math
    // ------------------------------------------------------------------

    /** Effective turret engagement range in blocks: 16 + round(20 * specials / (specials + 24)). */
    public static int turretRange(int specials) {
        return TURRET_BASE_RANGE + UpgradeMath.turretRangeBonus(specials);
    }

    public static boolean isInTurretRange(double distSq, int specials) {
        double r = turretRange(specials);
        return distSq >= 0.0D && distSq <= r * r;
    }

    /** Pulse firing interval in ticks (8 at 0 motors down to 2 with motors + mastery). */
    public static int turretFireInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(TURRET_BASE_INTERVAL_TICKS, motors);
        return Math.max(2, UpgradeMath.applyMasteryTime(motorTime, ownerLevel));
    }

    /** Pulse laser damage per shot: 6.0 * (1 + 2.5 * specials / (specials + 24)). */
    public static float turretDamage(int specials) {
        return (float) (TURRET_BASE_DAMAGE * UpgradeMath.turretDamageMultiplier(specials));
    }

    public static int turretShotEnergy(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(TURRET_BASE_SHOT_ENERGY, batteries), ownerLevel);
    }

    /** Number of dust beam particle samples along a laser shot, hard-capped to avoid lag. */
    public static int turretBeamPoints(double distance) {
        int raw = (int) Math.ceil(Math.max(0.0D, distance) * 1.5D);
        return Math.max(4, Math.min(TURRET_MAX_BEAM_POINTS, raw));
    }

    /**
     * Strict turret target filter: engages ONLY living {@code Enemy} mobs, never players,
     * villagers, golems, tamed/owned pets, or armor stands.
     */
    public static boolean canTurretTarget(boolean isEnemy, boolean isPlayer, boolean isVillagerOrGolem,
                                          boolean isTamedOrOwned, boolean isArmorStand, boolean isAlive) {
        return isAlive && isEnemy && !isPlayer && !isVillagerOrGolem && !isTamedOrOwned && !isArmorStand;
    }

    // ------------------------------------------------------------------
    // 4. Mob Capsule & Mob Duplicator math
    // ------------------------------------------------------------------

    /** Strict capture guard: blocks bosses, players, armor stands, and untagged invalid targets. */
    public static boolean canCaptureEntity(String entityTypeId, boolean isPlayer,
                                            boolean hasBossOrNoCaptureTag) {
        if (entityTypeId == null || entityTypeId.isBlank() || isPlayer || hasBossOrNoCaptureTag) {
            return false;
        }
        return !DISALLOWED_CAPTURE_IDS.contains(entityTypeId);
    }

    /** Spawn cycle interval in ticks (100 at 0 motors scaling linearly down to 20 at 64 motors + mastery). */
    public static int duplicatorInterval(int motors, int ownerLevel) {
        int c = clampMotors(motors);
        int span = DUPLICATOR_BASE_INTERVAL_TICKS - DUPLICATOR_MIN_INTERVAL_TICKS;
        int motorTime = Math.max(DUPLICATOR_MIN_INTERVAL_TICKS,
                DUPLICATOR_BASE_INTERVAL_TICKS - (span * c) / UpgradeMath.MAX_UPGRADE_STACK);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Mobs spawned per cycle (1 at 0 motors up to 4 at 64 motors). */
    public static int duplicatorBatchCount(int motors) {
        int c = clampMotors(motors);
        return DUPLICATOR_BASE_BATCH
                + ((DUPLICATOR_MAX_BATCH - DUPLICATOR_BASE_BATCH) * c) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    public static int duplicatorSpawnEnergy(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(DUPLICATOR_BASE_ENERGY, batteries), ownerLevel);
    }

    public static boolean isDuplicatorCrowded(int nearbyMobCount) {
        return nearbyMobCount >= DUPLICATOR_CROWD_LIMIT;
    }

    /** Remaining mobs allowed in the current cycle before hitting the 16-mob crowd limit. */
    public static int duplicatorSpawnRoom(int nearbyMobCount, int batchCount) {
        int room = DUPLICATOR_CROWD_LIMIT - Math.max(0, nearbyMobCount);
        if (room <= 0) {
            return 0;
        }
        return Math.min(Math.max(1, batchCount), room);
    }
}
