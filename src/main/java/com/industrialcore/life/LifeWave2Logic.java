package com.industrialcore.life;

import com.industrialcore.machine.UpgradeMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java gameplay math, coordinate builders, permission filters, and persistence data model
 * for Phase 95 Branch D (Building &amp; Life) wave 2:
 * <ul>
 *   <li>{@code chunk_loader} (Tier 3: 1x1 / 3x3 / 5x5 chunk forcing with zero ghost chunks)</li>
 *   <li>{@code climate_controller} (Tier 4: Clear/Rain/Thunder, Day/Night, optional lock modes)</li>
 *   <li>{@code warp_pad} (Tier 5: cross-dimension persistent warp network with public/private access)</li>
 * </ul>
 */
public final class LifeWave2Logic {
    // --- 1. Chunk Loader (Tier 3) ---
    public static final int CHUNK_BASE_CAPACITY = 80_000;
    public static final int CHUNK_MAX_RECEIVE = 8_000;
    public static final int CHUNK_BASE_FE_PER_CHUNK = 16;
    public static final int CHUNK_MAX_RADIUS = 2; // 0=1x1, 1=3x3, 2=5x5
    public static final int CHUNK_MOTORS_FOR_3X3 = 4;
    public static final int CHUNK_MOTORS_FOR_5X5 = 16;

    public static final int CHUNK_STATE_IDLE = 0;
    public static final int CHUNK_STATE_ACTIVE = 1;
    public static final int CHUNK_STATE_NO_ENERGY = 2;

    // --- 2. Climate Controller (Tier 4) ---
    public static final int CLIMATE_BASE_CAPACITY = 100_000;
    public static final int CLIMATE_MAX_RECEIVE = 10_000;
    public static final int CLIMATE_WEATHER_BASE_COST = 12_000;
    public static final int CLIMATE_TIME_BASE_COST = 10_000;
    public static final int CLIMATE_HOLD_BASE_FE_PER_TICK = 40;
    public static final int CLIMATE_BASE_COOLDOWN_TICKS = 60;
    public static final int CLIMATE_MIN_COOLDOWN_TICKS = 10;
    public static final long DAY_TIME_MORNING = 1_000L;
    public static final long DAY_TIME_NIGHT = 13_000L;
    public static final long TICKS_PER_DAY = 24_000L;

    public static final int CLIMATE_STATE_IDLE = 0;
    public static final int CLIMATE_STATE_HOLDING = 1;
    public static final int CLIMATE_STATE_NO_ENERGY = 2;
    public static final int CLIMATE_STATE_UNSUPPORTED = 3;

    // --- 3. Warp Pad (Tier 5) ---
    public static final int WARP_BASE_CAPACITY = 100_000;
    public static final int WARP_MAX_RECEIVE = 16_000;
    public static final int WARP_BASE_COST = 1_500;
    public static final int WARP_COST_PER_BLOCK = 6;
    public static final int WARP_CROSS_DIM_SURCHARGE = 18_000;
    public static final int WARP_MAX_RAW_COST = 80_000;
    public static final int WARP_BASE_COOLDOWN_TICKS = 80;
    public static final int WARP_MIN_COOLDOWN_TICKS = 10;
    public static final int WARP_MAX_NAME_LEN = 24;

    public static final int WARP_STATE_IDLE = 0;
    public static final int WARP_STATE_COOLDOWN = 1;
    public static final int WARP_STATE_NO_ENERGY = 2;

    /** Chunk X/Z pair for deterministic square chunk-loading regions. */
    public record ChunkCoord(int cx, int cz) {}

    /** Persistent warp pad registration record (pure Java for deterministic tests &amp; SavedData). */
    public record WarpEntry(String padId, String dimensionId, int x, int y, int z,
                            String name, String ownerUuid, boolean isPublic) {
        public WarpEntry withName(String newName) {
            return new WarpEntry(padId, dimensionId, x, y, z, newName, ownerUuid, isPublic);
        }

        public WarpEntry withPublic(boolean pub) {
            return new WarpEntry(padId, dimensionId, x, y, z, name, ownerUuid, pub);
        }
    }

    private LifeWave2Logic() {}

    private static int clampMotors(int motors) {
        return Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
    }

    // ------------------------------------------------------------------
    // 1. Chunk Loader logic
    // ------------------------------------------------------------------

    /** Maximum unlocked square radius: 0 (1x1), 1 (3x3 at >=4 motors), 2 (5x5 at >=16 motors). */
    public static int maxUnlockedChunkRadius(int motors) {
        int m = clampMotors(motors);
        if (m >= CHUNK_MOTORS_FOR_5X5) {
            return 2;
        }
        if (m >= CHUNK_MOTORS_FOR_3X3) {
            return 1;
        }
        return 0;
    }

    /** Clamps the player's requested radius to the currently unlocked motor limit. */
    public static int effectiveChunkRadius(int requestedRadius, int motors) {
        int maxR = maxUnlockedChunkRadius(motors);
        return Math.max(0, Math.min(maxR, requestedRadius));
    }

    /** Number of chunks in a square region of radius {@code r}: 1, 9, or 25. */
    public static int chunkCountForRadius(int radius) {
        int r = Math.max(0, Math.min(CHUNK_MAX_RADIUS, radius));
        int side = 2 * r + 1;
        return side * side;
    }

    /** Generates all chunk coordinates in the {@code (2r+1)x(2r+1)} square around {@code (centerCx, centerCz)}. */
    public static List<ChunkCoord> chunkCoords(int centerCx, int centerCz, int radius) {
        int r = Math.max(0, Math.min(CHUNK_MAX_RADIUS, radius));
        List<ChunkCoord> out = new ArrayList<>(chunkCountForRadius(r));
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                out.add(new ChunkCoord(centerCx + dx, centerCz + dz));
            }
        }
        return out;
    }

    /** Continuous FE/tick consumed for {@code loadedChunks} with battery &amp; mastery efficiency. */
    public static int chunkEnergyPerTick(int loadedChunks, int batteries, int ownerLevel) {
        if (loadedChunks <= 0) {
            return 0;
        }
        int perChunk = UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(CHUNK_BASE_FE_PER_CHUNK, batteries), ownerLevel);
        return Math.max(1, perChunk) * loadedChunks;
    }

    // ------------------------------------------------------------------
    // 2. Climate Controller logic
    // ------------------------------------------------------------------

    /** Energy cost for a one-shot weather or time change (up to 35% battery discount). */
    public static int climateActionCost(boolean isWeather, int batteries, int ownerLevel) {
        int base = isWeather ? CLIMATE_WEATHER_BASE_COST : CLIMATE_TIME_BASE_COST;
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(base, batteries), ownerLevel);
    }

    /** Continuous FE/tick for active hold toggles (1 or 2 toggles), reduced by motors &amp; batteries. */
    public static int climateHoldCostPerTick(int activeHolds, int motors, int batteries, int ownerLevel) {
        if (activeHolds <= 0) {
            return 0;
        }
        int m = clampMotors(motors);
        double motorFactor = 1.0D - 0.40D * (m / (double) UpgradeMath.MAX_UPGRADE_STACK);
        int motorScaled = Math.max(1, (int) Math.round(CLIMATE_HOLD_BASE_FE_PER_TICK * activeHolds * motorFactor));
        return UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(motorScaled, batteries), ownerLevel);
    }

    /** Activation cooldown in ticks (60 at 0 motors down to 10 at 64 motors). */
    public static int climateCooldownTicks(int motors, int ownerLevel) {
        int m = clampMotors(motors);
        int base = CLIMATE_BASE_COOLDOWN_TICKS
                - ((CLIMATE_BASE_COOLDOWN_TICKS - CLIMATE_MIN_COOLDOWN_TICKS) * m) / UpgradeMath.MAX_UPGRADE_STACK;
        return Math.max(CLIMATE_MIN_COOLDOWN_TICKS, UpgradeMath.applyMasteryTime(base, ownerLevel));
    }

    /**
     * Advances {@code currentDayTime} forward to the next occurrence of {@code targetTickOfDay}
     * (e.g., {@value #DAY_TIME_MORNING} or {@value #DAY_TIME_NIGHT}) without ever rewinding the
     * world day counter.
     */
    public static long nextDayTime(long currentDayTime, long targetTickOfDay) {
        long cur = Math.max(0L, currentDayTime);
        long target = Math.floorMod(targetTickOfDay, TICKS_PER_DAY);
        long dayStart = (cur / TICKS_PER_DAY) * TICKS_PER_DAY;
        long candidate = dayStart + target;
        return candidate > cur ? candidate : candidate + TICKS_PER_DAY;
    }

    // ------------------------------------------------------------------
    // 3. Warp Pad logic & Pure-Java Registry
    // ------------------------------------------------------------------

    public static String makePadId(String dimensionId, int x, int y, int z) {
        return dimensionId + "@" + x + "," + y + "," + z;
    }

    public static String sanitizePadName(String raw, int x, int y, int z) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "ワープ #" + x + "," + y + "," + z;
        }
        if (trimmed.length() > WARP_MAX_NAME_LEN) {
            return trimmed.substring(0, WARP_MAX_NAME_LEN);
        }
        return trimmed;
    }

    /**
     * Energy required to warp {@code distanceBlocks} (or across dimensions). Motors cut warp cost
     * up to 60%, and batteries apply up to 35% additional savings.
     */
    public static int warpCostFe(double distanceBlocks, boolean crossDimension,
                                 int motors, int batteries, int ownerLevel) {
        double dist = Math.max(0.0D, distanceBlocks);
        long raw = WARP_BASE_COST + Math.round(dist * WARP_COST_PER_BLOCK)
                + (crossDimension ? WARP_CROSS_DIM_SURCHARGE : 0L);
        int capped = (int) Math.min(WARP_MAX_RAW_COST, Math.max(WARP_BASE_COST, raw));
        int m = clampMotors(motors);
        double motorFactor = 1.0D - 0.60D * (m / (double) UpgradeMath.MAX_UPGRADE_STACK);
        int afterMotor = Math.max(100, (int) Math.round(capped * motorFactor));
        return Math.max(100, UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(afterMotor, batteries), ownerLevel));
    }

    /** Warp cooldown in ticks (80 at 0 motors down to 10 at 64 motors). */
    public static int warpCooldownTicks(int motors, int ownerLevel) {
        int m = clampMotors(motors);
        int base = WARP_BASE_COOLDOWN_TICKS
                - ((WARP_BASE_COOLDOWN_TICKS - WARP_MIN_COOLDOWN_TICKS) * m) / UpgradeMath.MAX_UPGRADE_STACK;
        return Math.max(WARP_MIN_COOLDOWN_TICKS, UpgradeMath.applyMasteryTime(base, ownerLevel));
    }

    /** Permission check: public pads are accessible to everyone; private pads to owner only. */
    public static boolean canAccessPad(WarpEntry target, String requesterUuid) {
        if (target == null) {
            return false;
        }
        if (target.isPublic()) {
            return true;
        }
        return requesterUuid != null && !requesterUuid.isEmpty()
                && requesterUuid.equals(target.ownerUuid());
    }

    /** Pure-Java persistent warp directory backing {@link WarpNetworkData}. */
    public static final class WarpRegistry {
        private final Map<String, WarpEntry> entries = new LinkedHashMap<>();

        public void registerOrUpdate(WarpEntry entry) {
            if (entry != null && entry.padId() != null && !entry.padId().isEmpty()) {
                entries.put(entry.padId(), entry);
            }
        }

        public boolean remove(String padId) {
            return padId != null && entries.remove(padId) != null;
        }

        public WarpEntry get(String padId) {
            return padId == null ? null : entries.get(padId);
        }

        public boolean rename(String padId, String newName) {
            WarpEntry cur = entries.get(padId);
            if (cur == null) {
                return false;
            }
            entries.put(padId, cur.withName(sanitizePadName(newName, cur.x(), cur.y(), cur.z())));
            return true;
        }

        public boolean setPublic(String padId, boolean isPublic) {
            WarpEntry cur = entries.get(padId);
            if (cur == null) {
                return false;
            }
            entries.put(padId, cur.withPublic(isPublic));
            return true;
        }

        public List<WarpEntry> allEntries() {
            return new ArrayList<>(entries.values());
        }

        public int size() {
            return entries.size();
        }

        /** All other pads accessible to {@code requesterUuid}, sorted deterministically by name. */
        public List<WarpEntry> visibleTargetsFor(String sourcePadId, String requesterUuid) {
            List<WarpEntry> out = new ArrayList<>();
            for (WarpEntry e : entries.values()) {
                if (e.padId().equals(sourcePadId)) {
                    continue;
                }
                if (canAccessPad(e, requesterUuid)) {
                    out.add(e);
                }
            }
            out.sort(Comparator.comparing(WarpEntry::name).thenComparing(WarpEntry::padId));
            return out;
        }
    }
}
