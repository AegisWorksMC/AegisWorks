package com.industrialcore.trap;

import com.industrialcore.machine.UpgradeMath;

/**
 * Pure-Java gameplay rules &amp; math for the Phase 92 Trap Branch wave 1:
 * <ul>
 *   <li>{@code mob_fan} (Tier 1: 6-direction airflow 3x3x(8..16), sneak immunity for players)</li>
 *   <li>{@code rotary_drill} (Tier 2: 6-direction 3x3x2 FakePlayer blade damage + weapon enchants, pet/player safe)</li>
 *   <li>{@code item_vacuum} (Tier 2: 8..16 block radius item suction into 12-slot output buffer)</li>
 * </ul>
 *
 * <p>Zero Minecraft imports so every bound and formula is deterministically unit tested.</p>
 */
public final class TrapLogic {
    /** Integer block-aligned bounding box matching Minecraft AABB min (inclusive) and max (exclusive). */
    public record Box6(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    // --- Mob Fan (Tier 1) ---
    public static final int FAN_HALF_WIDTH = 1;                  // 3x3 cross-section
    public static final int FAN_BASE_RANGE = 8;
    public static final int FAN_MAX_RANGE = 16;
    public static final double FAN_BASE_PUSH = 0.14D;
    public static final double FAN_MAX_PUSH = 0.28D;
    public static final int FAN_BASE_ENERGY_PER_TICK = 12;
    public static final int FAN_BASE_CAPACITY = 40_000;
    public static final int FAN_MAX_RECEIVE = 1_600;
    public static final int FAN_MAX_PARTICLES_PER_CYCLE = 2;

    // --- Rotary Drill (Tier 2) ---
    public static final int DRILL_HALF_WIDTH = 1;                // 3x3 cross-section
    public static final int DRILL_DEPTH = 2;                     // 2 blocks ahead
    public static final int DRILL_BASE_INTERVAL_TICKS = 20;
    public static final float DRILL_BASE_DAMAGE = 6.0F;
    public static final float DRILL_MAX_MOTOR_BONUS_DAMAGE = 12.0F;
    public static final int DRILL_BASE_WORK_ENERGY = 180;
    public static final int DRILL_BASE_CAPACITY = 60_000;
    public static final int DRILL_MAX_RECEIVE = 2_400;

    // --- Item Vacuum (Tier 2) ---
    public static final int VACUUM_BASE_RADIUS = 8;
    public static final int VACUUM_MAX_RADIUS = 16;
    public static final int VACUUM_BASE_INTERVAL_TICKS = 16;
    public static final int VACUUM_BASE_ENERGY_PER_PICKUP = 30;
    public static final int VACUUM_BASE_CAPACITY = 50_000;
    public static final int VACUUM_MAX_RECEIVE = 2_000;
    public static final int VACUUM_MAX_PARTICLES_PER_CYCLE = 6;

    private TrapLogic() {}

    private static int clampMotors(int motors) {
        return Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
    }

    /**
     * Computes a block-aligned 6-direction volume in front of block {@code (ox, oy, oz)}
     * facing unit vector {@code (stepX, stepY, stepZ)}.
     */
    public static Box6 forwardVolume(int ox, int oy, int oz,
                                     int stepX, int stepY, int stepZ,
                                     int halfWidth, int depth) {
        int minX = axisMin(ox, stepX, halfWidth, depth);
        int maxX = axisMax(ox, stepX, halfWidth, depth);
        int minY = axisMin(oy, stepY, halfWidth, depth);
        int maxY = axisMax(oy, stepY, halfWidth, depth);
        int minZ = axisMin(oz, stepZ, halfWidth, depth);
        int maxZ = axisMax(oz, stepZ, halfWidth, depth);
        return new Box6(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int axisMin(int o, int step, int halfWidth, int depth) {
        if (step > 0) return o + 1;
        if (step < 0) return o - depth;
        return o - halfWidth;
    }

    private static int axisMax(int o, int step, int halfWidth, int depth) {
        if (step > 0) return o + 1 + depth;
        if (step < 0) return o;
        return o + halfWidth + 1;
    }

    // ------------------------------------------------------------------
    // Mob Fan math
    // ------------------------------------------------------------------

    /** Effective blow range in blocks (8 at 0 motors, scaling linearly to 16 at 64 motors). */
    public static int fanRange(int motors) {
        int c = clampMotors(motors);
        return FAN_BASE_RANGE + (c * (FAN_MAX_RANGE - FAN_BASE_RANGE)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Velocity impulse added per tick along the facing vector (0.14..0.28). */
    public static double fanPushStrength(int motors) {
        int c = clampMotors(motors);
        return FAN_BASE_PUSH + (FAN_MAX_PUSH - FAN_BASE_PUSH) * c / (double) UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** UI airflow strength indicator in percent (100%..200%). */
    public static int fanPowerPercent(int motors) {
        return 100 + (clampMotors(motors) * 100) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** FE/tick running cost discounted by batteries (up to 35%) &amp; mastery level. */
    public static int fanEnergyPerTick(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(FAN_BASE_ENERGY_PER_TICK, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /** 6-direction airflow volume (3x3 cross-section, length {@link #fanRange(int)}). */
    public static Box6 fanVolume(int ox, int oy, int oz, int stepX, int stepY, int stepZ, int motors) {
        return forwardVolume(ox, oy, oz, stepX, stepY, stepZ, FAN_HALF_WIDTH, fanRange(motors));
    }

    /** Sneaking players and spectators are immune to the fan stream so maintenance stays easy. */
    public static boolean shouldPushEntity(boolean isPlayer, boolean isShiftKeyDown,
                                           boolean isSpectator, boolean isAlive) {
        return isAlive && !isSpectator && !(isPlayer && isShiftKeyDown);
    }

    // ------------------------------------------------------------------
    // Rotary Drill math
    // ------------------------------------------------------------------

    /** 6-direction 3x3x2 blade attack volume directly in front of the drill face. */
    public static Box6 drillVolume(int ox, int oy, int oz, int stepX, int stepY, int stepZ) {
        return forwardVolume(ox, oy, oz, stepX, stepY, stepZ, DRILL_HALF_WIDTH, DRILL_DEPTH);
    }

    /** Attack interval in ticks shortened by installed motors (20..5) &amp; mastery level. */
    public static int drillAttackInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(DRILL_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Base kinetic damage without weapon modifiers (6.0 at 0 motors up to 18.0 at 64 motors). */
    public static float drillBaseDamage(int motors) {
        return DRILL_BASE_DAMAGE
                + DRILL_MAX_MOTOR_BONUS_DAMAGE * clampMotors(motors) / (float) UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** FE cost per attack cycle discounted by batteries (up to 35%) &amp; mastery level. */
    public static int drillAttackEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(DRILL_BASE_WORK_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /** Strict safety filter: never attack players, tamed/owned pets, or armor stands. */
    public static boolean canDrillAttack(boolean isPlayer, boolean isTamedOrOwned,
                                         boolean isArmorStand, boolean isAlive) {
        return isAlive && !isPlayer && !isTamedOrOwned && !isArmorStand;
    }

    // ------------------------------------------------------------------
    // Item Vacuum math
    // ------------------------------------------------------------------

    /** Effective item suction radius in blocks (8 at 0 motors up to 16 at 64 motors). */
    public static int vacuumRadius(int motors) {
        int c = clampMotors(motors);
        return VACUUM_BASE_RADIUS + (c * (VACUUM_MAX_RADIUS - VACUUM_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** True when squared Euclidean distance falls within {@link #vacuumRadius(int)}. */
    public static boolean isInVacuumRange(double distSq, int motors) {
        double r = vacuumRadius(motors);
        return distSq >= 0.0D && distSq <= r * r;
    }

    /** Suction scan interval in ticks shortened by installed motors (16..4) &amp; mastery level. */
    public static int vacuumInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(VACUUM_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** FE cost per collected ItemEntity stack discounted by batteries (up to 35%) &amp; mastery level. */
    public static int vacuumPickupEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(VACUUM_BASE_ENERGY_PER_PICKUP, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /** Capped particle burst count per suction cycle to avoid excessive packet spam. */
    public static int vacuumParticleBudget(int collectedCount) {
        return Math.max(0, Math.min(collectedCount, VACUUM_MAX_PARTICLES_PER_CYCLE));
    }
}
