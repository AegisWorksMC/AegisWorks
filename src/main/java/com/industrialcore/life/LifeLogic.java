package com.industrialcore.life;

import com.industrialcore.machine.UpgradeMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java gameplay math and rules for Phase 94 Branch D (Building &amp; Life) wave 1:
 * <ul>
 *   <li>{@code auto_builder} (Tier 1: FLOOR / WALL / HOLLOW_BOX automated block builder)</li>
 *   <li>{@code fluid_pump} (Tier 2: 8..16 radius, depth 32 fluid pump with 64,000 mB tank)</li>
 *   <li>{@code wireless_charger} (Tier 3: 32..64 radius, 2,000..32,000 FE/t player wireless charger)</li>
 * </ul>
 *
 * <p>Zero Minecraft imports so every formula, coordinate sequence, and energy conservation law is
 * verified deterministically in unit tests.</p>
 */
public final class LifeLogic {
    // --- 1. Auto Builder (Tier 1) ---
    public enum BuildMode {
        FLOOR(0, "gui.industrialcore.auto_builder.mode.floor"),
        WALL(1, "gui.industrialcore.auto_builder.mode.wall"),
        HOLLOW_BOX(2, "gui.industrialcore.auto_builder.mode.hollow_box");

        private final int id;
        private final String translationKey;

        BuildMode(int id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public int id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public BuildMode next() {
            BuildMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static BuildMode byId(int id) {
            BuildMode[] vals = values();
            return (id >= 0 && id < vals.length) ? vals[id] : FLOOR;
        }
    }

    public static final int BUILDER_MIN_W = 3;
    public static final int BUILDER_MAX_W = 16;
    public static final int BUILDER_DEFAULT_W = 5;

    public static final int BUILDER_MIN_D = 3;
    public static final int BUILDER_MAX_D = 16;
    public static final int BUILDER_DEFAULT_D = 5;

    public static final int BUILDER_MIN_H = 1;
    public static final int BUILDER_MAX_H = 8;
    public static final int BUILDER_DEFAULT_H = 3;

    public static final int BUILDER_BASE_CAPACITY = 40_000;
    public static final int BUILDER_MAX_RECEIVE = 4_000;
    public static final int BUILDER_BASE_ENERGY = 60;
    public static final int BUILDER_BASE_INTERVAL_TICKS = 10;
    public static final int BUILDER_MAX_BATCH = 4;

    public static final int BUILDER_STATE_IDLE = 0;
    public static final int BUILDER_STATE_BUILDING = 1;
    public static final int BUILDER_STATE_NO_MATERIAL = 2;
    public static final int BUILDER_STATE_COMPLETED = 3;
    public static final int BUILDER_STATE_NO_ENERGY = 4;

    // --- 2. Fluid Pump (Tier 2) ---
    public static final int PUMP_BASE_CAPACITY = 60_000;
    public static final int PUMP_MAX_RECEIVE = 6_000;
    public static final int PUMP_TANK_CAPACITY = 64_000;
    public static final int PUMP_BUCKET_MB = 1_000;
    public static final int PUMP_BASE_RADIUS = 8;
    public static final int PUMP_MAX_RADIUS = 16;
    public static final int PUMP_MAX_DEPTH = 32;
    public static final int PUMP_BASE_INTERVAL_TICKS = 20;
    public static final int PUMP_BASE_ENERGY = 120;

    public static final int PUMP_STATE_IDLE = 0;
    public static final int PUMP_STATE_PUMPING = 1;
    public static final int PUMP_STATE_TANK_FULL = 2;
    public static final int PUMP_STATE_NO_FLUID = 3;
    public static final int PUMP_STATE_NO_ENERGY = 4;

    // --- 3. Wireless Charger (Tier 3) ---
    public static final int CHARGER_BASE_CAPACITY = 100_000;
    public static final int CHARGER_MAX_RECEIVE = 64_000;
    public static final int CHARGER_BASE_RADIUS = 32;
    public static final int CHARGER_MAX_RADIUS = 64;
    public static final int CHARGER_MIN_RATE = 2_000;
    public static final int CHARGER_MAX_RATE = 32_000;
    public static final int CHARGER_BASE_FIELD_COST = 20;

    public static final int CHARGER_STATE_IDLE = 0;
    public static final int CHARGER_STATE_CHARGING = 1;
    public static final int CHARGER_STATE_NO_ENERGY = 2;

    /** Local builder coordinate relative to machine facing: right, up, forward (>= 1). */
    public record LocalOffset(int right, int up, int forward) {}

    /** World delta coordinate relative to a machine's BlockPos. */
    public record WorldDelta(int dx, int dy, int dz) {}

    private LifeLogic() {}

    private static int clampMotors(int motors) {
        return Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
    }

    // ------------------------------------------------------------------
    // 1. Auto Builder logic
    // ------------------------------------------------------------------

    public static int clampWidth(int w) {
        return Math.max(BUILDER_MIN_W, Math.min(BUILDER_MAX_W, w));
    }

    public static int clampDepth(int d) {
        return Math.max(BUILDER_MIN_D, Math.min(BUILDER_MAX_D, d));
    }

    public static int clampHeight(int h) {
        return Math.max(BUILDER_MIN_H, Math.min(BUILDER_MAX_H, h));
    }

    /**
     * Generates the deterministic ordered build coordinate list for {@code mode} and dimensions
     * {@code (width, depth, height)} strictly in front of the builder ({@code forward >= 1}).
     */
    public static List<LocalOffset> patternOffsets(BuildMode mode, int width, int depth, int height) {
        BuildMode m = mode == null ? BuildMode.FLOOR : mode;
        int w = clampWidth(width);
        int d = clampDepth(depth);
        int h = clampHeight(height);
        int rightStart = -(w / 2);
        List<LocalOffset> out = new ArrayList<>();
        switch (m) {
            case FLOOR -> {
                for (int fwd = 1; fwd <= d; fwd++) {
                    for (int col = 0; col < w; col++) {
                        out.add(new LocalOffset(rightStart + col, 0, fwd));
                    }
                }
            }
            case WALL -> {
                for (int up = 0; up < h; up++) {
                    for (int col = 0; col < w; col++) {
                        out.add(new LocalOffset(rightStart + col, up, 1));
                    }
                }
            }
            case HOLLOW_BOX -> {
                for (int up = 0; up < h; up++) {
                    boolean isFloorOrRoof = (up == 0 || up == h - 1);
                    for (int fwd = 1; fwd <= d; fwd++) {
                        boolean isFrontOrBack = (fwd == 1 || fwd == d);
                        for (int col = 0; col < w; col++) {
                            boolean isSideWall = (col == 0 || col == w - 1);
                            if (isFloorOrRoof || isFrontOrBack || isSideWall) {
                                out.add(new LocalOffset(rightStart + col, up, fwd));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Exact block count for a build pattern without allocating the full list. */
    public static int patternCount(BuildMode mode, int width, int depth, int height) {
        BuildMode m = mode == null ? BuildMode.FLOOR : mode;
        int w = clampWidth(width);
        int d = clampDepth(depth);
        int h = clampHeight(height);
        return switch (m) {
            case FLOOR -> w * d;
            case WALL -> w * h;
            case HOLLOW_BOX -> {
                int total = w * d * h;
                int innerW = Math.max(0, w - 2);
                int innerD = Math.max(0, d - 2);
                int innerH = Math.max(0, h - 2);
                yield total - innerW * innerD * innerH;
            }
        };
    }

    /**
     * Converts a local {@code (right, up, forward)} offset to world {@code (dx, dy, dz)} given
     * horizontal facing unit vector {@code (facingDx, facingDz)}.
     */
    public static WorldDelta toWorldDelta(int facingDx, int facingDz, LocalOffset local) {
        // Right vector when looking along (facingDx, facingDz) in Minecraft XZ plane (X=east, Z=south):
        // North (0, -1) -> Right (+1, 0) East
        // East  (+1, 0) -> Right (0, +1) South
        // South (0, +1) -> Right (-1, 0) West
        // West  (-1, 0) -> Right (0, -1) North
        int rightDx = -facingDz;
        int rightDz = facingDx;
        int dx = facingDx * local.forward() + rightDx * local.right();
        int dz = facingDz * local.forward() + rightDz * local.right();
        return new WorldDelta(dx, local.up(), dz);
    }

    /** Ticks between builder placement actions: 10 at 0 motors down to 1 at >= 24 motors. */
    public static int builderInterval(int motors, int ownerLevel) {
        int c = clampMotors(motors);
        if (c >= 24) {
            return 1;
        }
        int base = BUILDER_BASE_INTERVAL_TICKS - (c * 9) / 24;
        return Math.max(1, UpgradeMath.applyMasteryTime(Math.max(1, base), ownerLevel));
    }

    /** Blocks placed per builder action: 1 up to 4 at high motor counts. */
    public static int builderBatchSize(int motors) {
        int c = clampMotors(motors);
        if (c >= 48) {
            return BUILDER_MAX_BATCH;
        }
        if (c >= 32) {
            return 3;
        }
        if (c >= 16) {
            return 2;
        }
        return 1;
    }

    public static int builderEnergyPerBlock(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(BUILDER_BASE_ENERGY, batteries), ownerLevel);
    }

    // ------------------------------------------------------------------
    // 2. Fluid Pump logic
    // ------------------------------------------------------------------

    /** Effective horizontal pumping radius (8 at 0 motors -> 16 at 64 motors). */
    public static int pumpRadius(int motors) {
        int c = clampMotors(motors);
        return PUMP_BASE_RADIUS + (c * (PUMP_MAX_RADIUS - PUMP_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Ticks between pump extraction cycles (20 at 0 motors down to 2 at 64 motors + mastery). */
    public static int pumpInterval(int motors, int ownerLevel) {
        int motorTime = UpgradeMath.acceleratedTime(PUMP_BASE_INTERVAL_TICKS, motors);
        return Math.max(2, UpgradeMath.applyMasteryTime(motorTime, ownerLevel));
    }

    public static int pumpEnergyPerBucket(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(PUMP_BASE_ENERGY, batteries), ownerLevel);
    }

    /** Total horizontal+vertical grid positions scanned in one full pass of the pump cylinder. */
    public static int pumpScanVolume(int radius, int maxDepth) {
        int r = Math.max(1, Math.min(PUMP_MAX_RADIUS, radius));
        int d = Math.max(1, Math.min(PUMP_MAX_DEPTH, maxDepth));
        int side = 2 * r + 1;
        return side * side * d;
    }

    /**
     * Maps a linear top-down scan index {@code [0, pumpScanVolume)} to {@code (dx, dy, dz)} where
     * {@code dy in [-1, -maxDepth]}. Caller skips positions outside {@code dx*dx + dz*dz <= r*r}.
     */
    public static WorldDelta pumpDeltaForIndex(int index, int radius, int maxDepth) {
        int r = Math.max(1, Math.min(PUMP_MAX_RADIUS, radius));
        int d = Math.max(1, Math.min(PUMP_MAX_DEPTH, maxDepth));
        int side = 2 * r + 1;
        int layerArea = side * side;
        int total = layerArea * d;
        int wrapped = Math.floorMod(index, total);
        int layer = wrapped / layerArea;
        int rem = wrapped % layerArea;
        int row = rem / side;
        int col = rem % side;
        return new WorldDelta(col - r, -(layer + 1), row - r);
    }

    public static boolean isWithinPumpRadius(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** True when the 64,000 mB tank has room for another full 1,000 mB source block. */
    public static boolean canAcceptBucket(int storedMb) {
        return storedMb >= 0 && storedMb + PUMP_BUCKET_MB <= PUMP_TANK_CAPACITY;
    }

    /** Infinite water source rule: true when water has at least 2 adjacent horizontal water sources. */
    public static boolean isInfiniteWaterSource(boolean isWater, int adjacentWaterSources) {
        return isWater && adjacentWaterSources >= 2;
    }

    // ------------------------------------------------------------------
    // 3. Wireless Charger logic
    // ------------------------------------------------------------------

    /** Effective wireless radius in blocks (32 at 0 motors -> 64 at 64 motors). */
    public static int chargerRadius(int motors) {
        int c = clampMotors(motors);
        return CHARGER_BASE_RADIUS + (c * (CHARGER_MAX_RADIUS - CHARGER_BASE_RADIUS)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    public static boolean isInChargerRange(double distSq, int motors) {
        double r = chargerRadius(motors);
        return distSq >= 0.0D && distSq <= r * r;
    }

    /** Max FE/tick wireless output rate (2,000 at 0 motors -> 32,000 at 64 motors). */
    public static int chargerRatePerTick(int motors) {
        int c = clampMotors(motors);
        return CHARGER_MIN_RATE + (c * (CHARGER_MAX_RATE - CHARGER_MIN_RATE)) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Active wireless field overhead cost per charging tick (reduced up to 35% by batteries). */
    public static int chargerFieldCost(int batteries, int ownerLevel) {
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(CHARGER_BASE_FIELD_COST, batteries), ownerLevel);
    }

    /**
     * Conservatively distributes wireless FE across multiple needy items without ever creating
     * energy or producing negative values. Guarantees:
     * <ul>
     *   <li>{@code 0 <= result[i] <= max(0, itemNeeds[i])}</li>
     *   <li>{@code sum(result) <= min(max(0, availableEnergy), max(0, maxRatePerTick))}</li>
     * </ul>
     */
    public static int[] distributeCharge(int availableEnergy, int maxRatePerTick, int[] itemNeeds) {
        if (itemNeeds == null || itemNeeds.length == 0) {
            return new int[0];
        }
        int[] out = new int[itemNeeds.length];
        int budget = Math.min(Math.max(0, availableEnergy), Math.max(0, maxRatePerTick));
        if (budget <= 0) {
            return out;
        }
        int activeCount = 0;
        for (int need : itemNeeds) {
            if (need > 0) {
                activeCount++;
            }
        }
        if (activeCount == 0) {
            return out;
        }
        // Fair round-robin equal share pass, then remainder pass so no budget is wasted
        int share = budget / activeCount;
        int remaining = budget;
        if (share > 0) {
            for (int i = 0; i < itemNeeds.length && remaining > 0; i++) {
                int need = Math.max(0, itemNeeds[i]);
                if (need <= 0) {
                    continue;
                }
                int give = Math.min(need, Math.min(share, remaining));
                out[i] = give;
                remaining -= give;
            }
        }
        for (int i = 0; i < itemNeeds.length && remaining > 0; i++) {
            int remNeed = Math.max(0, itemNeeds[i]) - out[i];
            if (remNeed <= 0) {
                continue;
            }
            int give = Math.min(remNeed, remaining);
            out[i] += give;
            remaining -= give;
        }
        return out;
    }
}
