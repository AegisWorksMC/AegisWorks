package com.industrialcore.machine;

/**
 * One progress model shared by the server (which block is mined) and the client (where the drill
 * head and the target frame are drawn).
 *
 * <p>Phase 9 fixes a real desync: the quarry used to publish only its integer cursor, and the
 * client drew the target box at the newest cursor while the drill head glided from the previous
 * cursor over the whole visual interval. At high motor counts the cursor advances up to
 * {@code maxOperationsPerTick} blocks per tick, so a ten tick interval covered dozens of blocks:
 * the box jumped ahead of the head, and the frame moved at a different rate than the blocks
 * actually being destroyed. Air and unbreakable blocks made it worse, because they advance the
 * cursor without breaking anything.</p>
 *
 * <p>The single value used from now on is the <em>drill index</em>
 * ({@link #drillIndex(int, double)}): the confirmed cursor plus the fraction of work already
 * banked towards the next position. The head is drawn at that index, the frame is drawn at
 * {@link #frameIndex(double, int, int)} of the same index, and the server breaks a block exactly
 * when the index crosses an integer (that is what increments the cursor). Head and frame
 * therefore cannot move at different rates: both are functions of the same number.</p>
 *
 * <p>The index is only ever built from server-confirmed values. The client interpolates between
 * two confirmed indices and never extrapolates beyond the newest one.</p>
 *
 * <p>No Minecraft classes are used, so the timing and the frame/drill agreement can be unit
 * tested directly.</p>
 */
public final class MiningSync {
    /** Default upper bound for how many positions one visual update may cover. */
    public static final int DEFAULT_MAX_BLOCKS_PER_UPDATE = 4;

    private MiningSync() {}

    /**
     * Interval between visual updates, shortened as the machine gets faster.
     *
     * <p>{@code baseInterval} is the factory wide interval from {@link MiningScheduler}; it keeps
     * traffic bounded when many machines run. On top of that a machine may not cover more than
     * {@code maxBlocksPerUpdate} positions per update, otherwise the drawn frame would trail the
     * real mining position by more than that at high motor counts. A machine mining at 8
     * positions per tick therefore syncs every tick, while a slow machine keeps the base
     * interval.</p>
     *
     * @param baseInterval        ticks between updates chosen by the scheduler (>= 1)
     * @param opsPerTick          current mining operations per tick (may be fractional)
     * @param maxBlocksPerUpdate  positions one update may cover (>= 1)
     * @return 1..baseInterval
     */
    public static int syncInterval(int baseInterval, double opsPerTick, int maxBlocksPerUpdate) {
        int base = Math.max(1, baseInterval);
        int maxBlocks = Math.max(1, maxBlocksPerUpdate);
        if (!Double.isFinite(opsPerTick) || opsPerTick <= 0.0D) {
            return base;
        }
        long needed = (long) Math.ceil(maxBlocks / opsPerTick - 1.0E-9D);
        int interval = (int) Math.max(1L, Math.min(base, needed));
        return interval;
    }

    /**
     * Fractional position of the drill.
     *
     * @param cursor       confirmed cursor (index of the next position to process)
     * @param workFraction banked work towards the next position, clamped to 0..1
     */
    public static double drillIndex(int cursor, double workFraction) {
        double fraction = workFraction;
        if (!Double.isFinite(fraction)) {
            fraction = 0.0D;
        }
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        if (cursor < 0) {
            return 0.0D;
        }
        return cursor + fraction;
    }

    /**
     * Interpolates between two confirmed drill indices. A restart (the index moved backwards
     * because a new area was applied) is drawn at the new value instead of sweeping backwards
     * through the old area.
     */
    public static double interpolate(double previousIndex, double currentIndex, float progress) {
        if (!Double.isFinite(previousIndex)) {
            previousIndex = currentIndex;
        }
        if (!Double.isFinite(currentIndex)) {
            currentIndex = previousIndex;
        }
        if (currentIndex < previousIndex) {
            return currentIndex;
        }
        if (progress <= 0.0F || !Float.isFinite(progress)) {
            return previousIndex;
        }
        if (progress >= 1.0F) {
            return currentIndex;
        }
        return previousIndex + (currentIndex - previousIndex) * (double) progress;
    }

    /**
     * Index of the block the frame is drawn around, derived from the same drill index.
     *
     * <p>The block is never further ahead than the confirmed cursor, so the frame cannot show a
     * position the server has not reached yet.</p>
     */
    public static int frameIndex(double drillIndex, int cursor, int volume) {
        int max = Math.max(0, volume - 1);
        int safeCursor = Math.max(0, Math.min(cursor, max));
        double value = Double.isFinite(drillIndex) ? Math.floor(drillIndex) : safeCursor;
        int index = (int) Math.max(0.0D, Math.min(max, value));
        return Math.min(index, safeCursor);
    }

    /**
     * True when the integer cursor and the drawn frame describe the same position, i.e. the frame
     * is not lagging. Used by diagnostics and tests.
     */
    public static boolean frameMatchesCursor(double drillIndex, int cursor) {
        return frameIndex(drillIndex, cursor, Integer.MAX_VALUE) == Math.max(0, cursor);
    }
}
