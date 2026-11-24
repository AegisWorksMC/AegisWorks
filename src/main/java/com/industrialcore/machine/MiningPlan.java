package com.industrialcore.machine;

/**
 * Pure, deterministic mapping from a linear mining cursor to local block offsets.
 *
 * <p>Layout: the first axis is width (local X), the second is depth (local Z) and the
 * third is height (local Y, counted downwards). Layers are mined from top to bottom and
 * every layer is mined in a serpentine pattern, which matches the moving drill head
 * shown by the client renderer.</p>
 *
 * <p>No Minecraft classes are used here so the ordering can be unit tested directly.</p>
 */
public final class MiningPlan {
    /** Hard structural limit, independent of the configurable gameplay limits. */
    public static final int MAX_DIMENSION = 256;

    private final int width;
    private final int depth;
    private final int height;

    public MiningPlan(int width, int depth, int height) {
        this.width = clampDimension(width);
        this.depth = clampDimension(depth);
        this.height = clampDimension(height);
    }

    private static int clampDimension(int value) {
        return Math.max(1, Math.min(MAX_DIMENSION, value));
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int height() {
        return height;
    }

    /**
     * Total number of positions in this plan. Uses long math and saturates to avoid
     * overflow for maliciously large inputs.
     */
    public int volume() {
        long total = (long) width * depth * height;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    public int localX(int index) {
        int clamped = clampIndex(index);
        int row = (clamped / width) % depth;
        int column = clamped % width;
        return (row & 1) == 0 ? column : width - 1 - column;
    }

    public int localZ(int index) {
        int clamped = clampIndex(index);
        return (clamped / width) % depth;
    }

    public int localY(int index) {
        return clampIndex(index) / (width * depth);
    }

    /**
     * Inverse mapping, mainly useful for tests and debugging.
     *
     * @return the cursor index for the given local offset, or -1 if it is out of bounds
     */
    public int indexOf(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            return -1;
        }
        int column = (z & 1) == 0 ? x : width - 1 - x;
        return (y * depth + z) * width + column;
    }

    private int clampIndex(int index) {
        int volume = volume();
        if (index < 0) {
            return 0;
        }
        return Math.min(index, volume - 1);
    }
}
