package com.industrialcore.agri;

import com.industrialcore.machine.UpgradeMath;

/**
 * Pure-Java gameplay rules &amp; math for the Phase 90 Agriculture &amp; Forestry branch:
 * <ul>
 *   <li>{@code crop_harvester} (9x9, y -1..+2 crop harvesting &amp; auto-replanting)</li>
 *   <li>{@code tree_harvester} (9x9, y 0..+24 3-block sapling grid &amp; bounded BFS felling)</li>
 *   <li>{@code auto_fisher} (adjacent/below water detection, motor + Lure speed, Luck of the Sea)</li>
 * </ul>
 *
 * <p>Zero Minecraft imports so every calculation is deterministically unit tested.</p>
 */
public final class AgriLogic {
    // --- Crop Harvester ---
    public static final int CROP_RANGE_RADIUS = 4;        // 9x9 horizontal (-4..+4)
    public static final int CROP_FORWARD_CENTER = 5;      // 1..9 blocks directly ahead
    public static final int CROP_MIN_DY = -1;
    public static final int CROP_MAX_DY = 2;
    public static final int CROP_BASE_INTERVAL_TICKS = 16;
    public static final int CROP_BASE_ENERGY_PER_HARVEST = 120;
    public static final int CROP_BASE_CAPACITY = 40_000;
    public static final int CROP_MAX_RECEIVE = 1_600;

    // --- Tree Harvester ---
    public static final int TREE_RANGE_RADIUS = 4;        // 9x9 horizontal (-4..+4)
    public static final int TREE_FORWARD_CENTER = 5;      // 1..9 blocks directly ahead
    public static final int TREE_MAX_HEIGHT = 24;         // up to +24 blocks above machine Y
    public static final int TREE_BFS_HORIZ_CAP = 7;       // max lateral reach from plot center
    public static final int TREE_MAX_FELL_BLOCKS = 256;   // hard safety cap per felling cycle
    public static final int TREE_PLANT_SPACING = 3;       // 3-block sapling grid (9 spots in 9x9)
    public static final int TREE_BASE_INTERVAL_TICKS = 40;
    public static final int TREE_BASE_FELL_ENERGY = 400;
    public static final int TREE_BASE_PLANT_ENERGY = 25;
    public static final int TREE_BASE_CAPACITY = 60_000;
    public static final int TREE_MAX_RECEIVE = 2_400;

    // --- Auto Fisher ---
    public static final int FISHER_BASE_TIME_TICKS = 160;
    public static final int FISHER_LURE_REDUCTION_PER_LEVEL = 20;
    public static final int FISHER_MIN_LURE_BASE_TICKS = 40;
    public static final int FISHER_BASE_ENERGY_PER_TICK = 25;
    public static final int FISHER_BASE_CAPACITY = 50_000;
    public static final int FISHER_MAX_RECEIVE = 2_000;

    private AgriLogic() {}

    // ------------------------------------------------------------------
    // Crop Harvester math
    // ------------------------------------------------------------------

    /** Total scan positions in the 9x9x4 front harvest volume (324 positions). */
    public static int cropScanVolume() {
        int side = CROP_RANGE_RADIUS * 2 + 1;
        int height = CROP_MAX_DY - CROP_MIN_DY + 1;
        return side * side * height;
    }

    /** True when relative offset {@code (dx, dy, dz)} from plot center lies inside the 9x9 (-1..+2) range. */
    public static boolean isInCropRange(int relX, int relY, int relZ) {
        return Math.abs(relX) <= CROP_RANGE_RADIUS
                && Math.abs(relZ) <= CROP_RANGE_RADIUS
                && relY >= CROP_MIN_DY && relY <= CROP_MAX_DY;
    }

    /** Scan cycle duration in ticks shortened by installed motors &amp; mastery level. */
    public static int cropScanInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(CROP_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** FE cost per harvested crop discounted by batteries (up to 35%) &amp; mastery level. */
    public static int cropHarvestEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(CROP_BASE_ENERGY_PER_HARVEST, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /**
     * Replant rule: replants when at least one matching seed is available either from the crop's
     * own harvested drops or from the harvester's seed buffer slots.
     */
    public static boolean canReplant(boolean hasSeedInDrops, boolean hasSeedInBuffer) {
        return hasSeedInDrops || hasSeedInBuffer;
    }

    // ------------------------------------------------------------------
    // Tree Harvester math
    // ------------------------------------------------------------------

    /**
     * True when {@code (relX, relZ)} inside the 9x9 plot is one of the 9 spaced planting spots
     * ({@code -3, 0, +3} on both axes), keeping 2 blocks between saplings so trees grow freely.
     */
    public static boolean isTreePlantingSpot(int relX, int relZ) {
        if (Math.abs(relX) > TREE_RANGE_RADIUS || Math.abs(relZ) > TREE_RANGE_RADIUS) {
            return false;
        }
        return (relX % TREE_PLANT_SPACING == 0) && (relZ % TREE_PLANT_SPACING == 0);
    }

    /**
     * True when a candidate block at offset {@code (relX, relY, relZ)} from the farm plot center
     * is permitted inside the bounded BFS felling volume (never strips distant buildings/forests).
     */
    public static boolean isWithinTreeFellBounds(int relX, int relY, int relZ) {
        return Math.abs(relX) <= TREE_BFS_HORIZ_CAP
                && Math.abs(relZ) <= TREE_BFS_HORIZ_CAP
                && relY >= 0 && relY <= TREE_MAX_HEIGHT;
    }

    /** Work cycle duration in ticks shortened by motors &amp; mastery level. */
    public static int treeCycleInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(TREE_BASE_INTERVAL_TICKS, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Subtle sapling growth-pulse count granted per cycle by installed motors (0..4). */
    public static int treeGrowthPulses(int motors) {
        int clamped = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        if (clamped <= 0) {
            return 0;
        }
        return Math.min(4, 1 + clamped / 20);
    }

    /** FE cost for a felling cycle discounted by batteries (up to 35%) &amp; mastery. */
    public static int treeFellEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(TREE_BASE_FELL_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    /** FE cost for planting one sapling discounted by batteries &amp; mastery. */
    public static int treePlantEnergy(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(TREE_BASE_PLANT_ENERGY, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }

    // ------------------------------------------------------------------
    // Auto Fisher math
    // ------------------------------------------------------------------

    /**
     * Fishing duration in ticks combining Lure enchantment ({@code -20 ticks/level}, floor 40)
     * with Motor acceleration (up to 4.0x at 64 motors) and post-limit-break mastery.
     */
    public static int fisherDurationTicks(int motors, int lureLevel, int ownerLevel) {
        int safeLure = Math.max(0, Math.min(5, lureLevel));
        int lureBase = Math.max(FISHER_MIN_LURE_BASE_TICKS,
                FISHER_BASE_TIME_TICKS - safeLure * FISHER_LURE_REDUCTION_PER_LEVEL);
        int motorTime = UpgradeMath.acceleratedTime(lureBase, motors);
        return UpgradeMath.applyMasteryTime(motorTime, ownerLevel);
    }

    /** Effective fishing luck applied to {@code BuiltInLootTables.FISHING}. */
    public static float fisherEffectiveLuck(int luckOfTheSeaLevel, int ownerLevel) {
        int safeLuck = Math.max(0, Math.min(10, luckOfTheSeaLevel));
        float masteryBonus = ownerLevel >= 100 ? 1.0F : 0.0F;
        return safeLuck + masteryBonus;
    }

    /** FE/tick running cost discounted by batteries (up to 35%) &amp; mastery level. */
    public static int fisherEnergyPerTick(int batteries, int ownerLevel) {
        int battCost = UpgradeMath.discountedEnergy(FISHER_BASE_ENERGY_PER_TICK, batteries);
        return UpgradeMath.applyMasteryEnergy(battCost, ownerLevel);
    }
}
