package com.industrialcore.machine;

/**
 * Plain three-dimensional block position of the Phase 26-29 transporter system.
 *
 * <p>{@link TransporterStructure} and {@link ChannelIndex} must be unit testable without loading a
 * single Minecraft class (the project's unit tests run on a plain JVM), so they exchange positions
 * as this record instead of {@code BlockPos}. The block entity converts at its boundary.</p>
 */
public record GridPos(int x, int y, int z) implements Comparable<GridPos> {

    /**
     * Deterministic order: X, then Y, then Z. Every transporter lookup walks its candidates in
     * exactly this order, so two receivers on the same channel are always tried in the same order
     * and a transfer never picks a different destination at random.
     */
    @Override
    public int compareTo(GridPos other) {
        int result = Integer.compare(this.x, other.x);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(this.y, other.y);
        return result != 0 ? result : Integer.compare(this.z, other.z);
    }
}
