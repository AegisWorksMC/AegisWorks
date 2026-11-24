package com.industrialcore.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 104: Pure-Java geometry &amp; placement validation for auto-expanding large machines
 * backed by {@code machine_bounding} proxy blocks.
 *
 * <p>Zero Minecraft imports so every machine's footprint, 4-direction rotation, proxy count,
 * world-height bounds, and obstacle rejection are proven deterministically by unit tests.</p>
 */
public final class LargeMachineLogic {
    public record IntPos(int x, int y, int z) {}

    /**
     * Horizontal facing of the master block (facing outward toward the player who placed it).
     * Local axes: {@code r} = right (looking at front from inside, or lateral right),
     * {@code b} = back (inward away from facing), {@code y} = up.
     */
    public enum Facing2D {
        NORTH(0, -1, 1, 0),
        SOUTH(0, 1, -1, 0),
        WEST(-1, 0, 0, -1),
        EAST(1, 0, 0, 1);

        private final int fx;
        private final int fz;
        private final int rx;
        private final int rz;

        Facing2D(int fx, int fz, int rx, int rz) {
            this.fx = fx;
            this.fz = fz;
            this.rx = rx;
            this.rz = rz;
        }

        public int backX() {
            return -fx;
        }

        public int backZ() {
            return -fz;
        }

        public int rightX() {
            return rx;
        }

        public int rightZ() {
            return rz;
        }
    }

    /**
     * Auto-expanding footprint specifications for the 5 upgraded high-tier machines.
     * Local coordinates {@code (r, y, b)} are relative to the master block at {@code (0, 0, 0)}.
     */
    public enum MachineSpec {
        /** 2x3x2 Bio-Cloning Tank: master at front-left bottom (r in [0..1], y in [0..2], b in [0..1]). */
        MOB_DUPLICATOR("mob_duplicator", 2, 3, 2, 0, 1, 0, 2, 0, 1, false),
        /** 3x1x3 Walkable Warp Gate Pedestal: master at exact center (r in [-1..1], y=0, b in [-1..1]). */
        WARP_PAD("warp_pad", 3, 1, 3, -1, 1, 0, 0, -1, 1, true),
        /** 1x3x1 Towering Weather Antenna Spire: master at bottom (y in [0..2]). */
        CLIMATE_CONTROLLER("climate_controller", 1, 3, 1, 0, 0, 0, 2, 0, 0, false),
        /** 1x2x1 Quantum Mainframe Server Rack: master at bottom (y in [0..1]). */
        QUANTUM_STORAGE_CORE("quantum_storage_core", 1, 2, 1, 0, 0, 0, 1, 0, 0, false),
        /** 1x2x1 Holographic Suit &amp; Weapon Modification Bay: master at bottom (y in [0..1]). */
        SUIT_MODIFIER("suit_modifier", 1, 2, 1, 0, 0, 0, 1, 0, 0, false);

        private final String blockId;
        private final int width;
        private final int height;
        private final int depth;
        private final int minR;
        private final int maxR;
        private final int minY;
        private final int maxY;
        private final int minB;
        private final int maxB;
        private final boolean lowPad;

        MachineSpec(String blockId, int width, int height, int depth,
                    int minR, int maxR, int minY, int maxY, int minB, int maxB, boolean lowPad) {
            this.blockId = blockId;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.minR = minR;
            this.maxR = maxR;
            this.minY = minY;
            this.maxY = maxY;
            this.minB = minB;
            this.maxB = maxB;
            this.lowPad = lowPad;
        }

        public String blockId() {
            return blockId;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int depth() {
            return depth;
        }

        public int totalBlocks() {
            return width * height * depth;
        }

        public int proxyCount() {
            return totalBlocks() - 1;
        }

        public boolean isLowPad() {
            return lowPad;
        }

        public int minR() { return minR; }
        public int maxR() { return maxR; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public int minB() { return minB; }
        public int maxB() { return maxB; }
    }

    @FunctionalInterface
    public interface CellPredicate {
        boolean test(int x, int y, int z);
    }

    private LargeMachineLogic() {}

    /** Computes all world positions occupied by the machine (master + all proxies). */
    public static List<IntPos> allPositions(MachineSpec spec, IntPos master, Facing2D facing) {
        if (spec == null || master == null || facing == null) {
            return List.of();
        }
        int rx = facing.rightX();
        int rz = facing.rightZ();
        int bx = facing.backX();
        int bz = facing.backZ();

        List<IntPos> list = new ArrayList<>(spec.totalBlocks());
        for (int dy = spec.minY(); dy <= spec.maxY(); dy++) {
            for (int dr = spec.minR(); dr <= spec.maxR(); dr++) {
                for (int db = spec.minB(); db <= spec.maxB(); db++) {
                    int x = master.x() + rx * dr + bx * db;
                    int y = master.y() + dy;
                    int z = master.z() + rz * dr + bz * db;
                    list.add(new IntPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    /** Computes all proxy positions occupied by the machine (strictly excluding the master cell). */
    public static List<IntPos> proxyPositions(MachineSpec spec, IntPos master, Facing2D facing) {
        if (spec == null || master == null || facing == null) {
            return List.of();
        }
        int rx = facing.rightX();
        int rz = facing.rightZ();
        int bx = facing.backX();
        int bz = facing.backZ();

        List<IntPos> list = new ArrayList<>(spec.proxyCount());
        for (int dy = spec.minY(); dy <= spec.maxY(); dy++) {
            for (int dr = spec.minR(); dr <= spec.maxR(); dr++) {
                for (int db = spec.minB(); db <= spec.maxB(); db++) {
                    if (dy == 0 && dr == 0 && db == 0) {
                        continue;
                    }
                    int x = master.x() + rx * dr + bx * db;
                    int y = master.y() + dy;
                    int z = master.z() + rz * dr + bz * db;
                    list.add(new IntPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Validates that every occupied cell (master + proxies) stays inside world vertical limits
     * {@code [minBuildY, maxBuildY)} and satisfies {@code isReplaceable}.
     */
    public static boolean canPlace(MachineSpec spec, IntPos master, Facing2D facing,
                                   int minBuildY, int maxBuildY, CellPredicate isReplaceable) {
        if (spec == null || master == null || facing == null || isReplaceable == null) {
            return false;
        }
        for (IntPos p : allPositions(spec, master, facing)) {
            if (p.y() < minBuildY || p.y() >= maxBuildY) {
                return false;
            }
            if (!isReplaceable.test(p.x(), p.y(), p.z())) {
                return false;
            }
        }
        return true;
    }
}
