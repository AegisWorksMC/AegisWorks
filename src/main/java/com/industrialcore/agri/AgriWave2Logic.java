package com.industrialcore.agri;

import com.industrialcore.machine.UpgradeMath;

/**
 * Pure-Java gameplay rules &amp; math for the Phase 91 Agriculture &amp; Forestry branch wave 2:
 * <ul>
 *   <li>{@code ranch_harvester} (Tier 3: 9x9 y=-2..+4 sheep shearing, cow/mooshroom milking, and 12-cap breeding)</li>
 *   <li>{@code growth_accelerator} (Tier 4: 9x9 y=-1..+3 motor-scaled growth pulses + capped particle budget)</li>
 *   <li>{@code bio_feeder} (Tier 5: 48..96 radius beacon feeding survival players automatically)</li>
 * </ul>
 *
 * <p>Zero Minecraft imports so every calculation is deterministically unit tested.</p>
 */
public final class AgriWave2Logic {
    // --- Ranch Harvester (Tier 3) ---
    public static final int RANCH_RADIUS = 4;               // 9x9 centered on machine (-4..+4)
    public static final int RANCH_MIN_DY = -2;
    public static final int RANCH_MAX_DY = 4;
    public static final int RANCH_BREED_CAP = 12;           // lag-prevention species cap
    public static final int RANCH_BASE_INTERVAL_TICKS = 32;
    public static final int RANCH_BASE_WORK_ENERGY = 220;
    public static final int RANCH_BASE_CAPACITY = 80_000;
    public static final int RANCH_MAX_RECEIVE = 3_200;

    // --- Growth Accelerator (Tier 4) ---
    public static final int GROWTH_RADIUS = 4;              // 9x9 centered on machine (-4..+4)
    public static final int GROWTH_MIN_DY = -1;
    public static final int GROWTH_MAX_DY = 3;
    public static final int GROWTH_BASE_INTERVAL_TICKS = 40;
    public static final int GROWTH_MIN_TARGETS = 3;
    public static final int GROWTH_MAX_TARGETS = 16;
    public static final int GROWTH_MAX_PARTICLES_PER_CYCLE = 6;
    public static final int GROWTH_BASE_PULSE_ENERGY = 350;
    public static final int GROWTH_BASE_CAPACITY = 160_000;
    public static final int GROWTH_MAX_RECEIVE = 6_400;

    // --- Bio Feeder Beacon (Tier 5) ---
    public static final int FEEDER_BASE_RADIUS = 48;
    public static final int FEEDER_MAX_RADIUS = 96;
    public static final int FEEDER_BASE_INTERVAL_TICKS = 20;
    public static final int FEEDER_PLAYER_COOLDOWN_TICKS = 20;
    public static final int FEEDER_BASE_FEED_ENERGY = 500;
    public static final int FEEDER_BASE_CAPACITY = 300_000;
    public static final int FEEDER_MAX_RECEIVE = 12_000;
    public static final int FEEDER_MAX_FOOD = 20;
    public static final float FEEDER_MIN_SATURATION = 10.0F;

    /** Immutable nutrition outcome computed by {@link #applyNutrition}. */
    public record FoodResult(int foodLevel, float saturationLevel) {}

    private AgriWave2Logic() {}

    // ------------------------------------------------------------------
    // Ranch Harvester math
    // ------------------------------------------------------------------

    /** True when offset {@code (dx, dy, dz)} from the machine lies inside the 9x9 (y -2..+4) pen volume. */
    public static boolean isInRanchRange(int dx, int dy, int dz) {
        return Math.abs(dx) <= RANCH_RADIUS
                && Math.abs(dz) <= RANCH_RADIUS
                && dy >= RANCH_MIN_DY && dy <= RANCH_MAX_DY;
    }

    /** True when the species headcount inside range is strictly below the 12-animal anti-lag breeding cap. */
    public static boolean canBreedSpecies(int speciesCount) {
        return speciesCount < RANCH_BREED_CAP;
    }

    /** Scan cycle duration in ticks shortened by installed motors &amp; mastery level. */
    public static int ranchCycleInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(RANCH_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** FE cost per ranch action discounted by batteries (up to 35%) &amp; mastery level. */
    public static int ranchWorkEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(RANCH_BASE_WORK_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    // ------------------------------------------------------------------
    // Growth Accelerator math
    // ------------------------------------------------------------------

    /** True when offset {@code (dx, dy, dz)} lies inside the 9x9 (y -1..+3) growth volume. */
    public static boolean isInGrowthRange(int dx, int dy, int dz) {
        return Math.abs(dx) <= GROWTH_RADIUS
                && Math.abs(dz) <= GROWTH_RADIUS
                && dy >= GROWTH_MIN_DY && dy <= GROWTH_MAX_DY;
    }

    /** Growth pulse interval in ticks shortened by installed motors &amp; mastery level. */
    public static int growthPulseInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(GROWTH_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Number of blocks pulsed per cycle scaled smoothly from 3 (0 motors) to 16 (64 motors). */
    public static int growthPulseTargets(int motors) {
        int c = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        return GROWTH_MIN_TARGETS
                + (c * (GROWTH_MAX_TARGETS - GROWTH_MIN_TARGETS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Capped particle spawn count per pulse cycle to prevent network/client spam. */
    public static int growthParticleBudget(int grownCount) {
        return Math.max(0, Math.min(grownCount, GROWTH_MAX_PARTICLES_PER_CYCLE));
    }

    /** FE cost per growth pulse discounted by batteries (up to 35%) &amp; mastery level. */
    public static int growthPulseEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(GROWTH_BASE_PULSE_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    // ------------------------------------------------------------------
    // Bio Feeder Beacon math
    // ------------------------------------------------------------------

    /** Effective feeding radius scaled linearly from 48 blocks (0 motors) to 96 blocks (64 motors). */
    public static int feederRadius(int motors) {
        int c = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        return FEEDER_BASE_RADIUS
                + (c * (FEEDER_MAX_RADIUS - FEEDER_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** True when squared Euclidean distance falls within {@link #feederRadius(int)}. */
    public static boolean isInFeederRange(double distSq, int motors) {
        double r = feederRadius(motors);
        return distSq >= 0.0D && distSq <= r * r;
    }

    /** Check interval in ticks shortened by installed motors &amp; mastery level. */
    public static int feederCheckInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(FEEDER_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** FE cost per feeding action discounted by batteries (up to 35%) &amp; mastery level. */
    public static int feederFeedEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(FEEDER_BASE_FEED_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /** True when the player's food level (&lt; 20) or saturation (&lt; 10.0) warrants automatic feeding. */
    public static boolean needsFeeding(int foodLevel, float saturationLevel) {
        return foodLevel < FEEDER_MAX_FOOD || saturationLevel < FEEDER_MIN_SATURATION;
    }

    /**
     * Vanilla-faithful clamped nutrition update: caps food at 20 and clamps saturation to the
     * resulting {@code foodLevel}.
     */
    public static FoodResult applyNutrition(int curFood, float curSat, int addFood, float addSat) {
        int nf = Math.min(FEEDER_MAX_FOOD, Math.max(0, curFood) + Math.max(0, addFood));
        float ns = Math.min((float) nf, Math.max(0.0F, curSat) + Math.max(0.0F, addSat));
        return new FoodResult(nf, ns);
    }

    /** True only for edible items without harmful status effects (avoids spider eyes, rotten flesh, etc.). */
    public static boolean isSafeFood(boolean hasFoodComponent, boolean hasHarmfulEffect) {
        return hasFoodComponent && !hasHarmfulEffect;
    }
}
