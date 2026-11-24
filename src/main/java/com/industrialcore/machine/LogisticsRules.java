package com.industrialcore.machine;

/**
 * Pure routing rules for the item pipes.
 *
 * <p>Kept free of Minecraft classes so the rules (which direction is tried first, when a pipe may
 * keep forwarding) can be unit tested and documented exactly.
 * Directions are plain indices in the vanilla order: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST,
 * 5=EAST - the same order as {@code Direction.values()} and {@code Direction#get3DDataValue()}.</p>
 */
public final class LogisticsRules {
    /** Number of block directions (DOWN, UP, NORTH, SOUTH, WEST, EAST). */
    public static final int DIRECTION_COUNT = 6;
    /** Value used when an item has no arrival direction yet. */
    public static final int NO_DIRECTION = -1;

    /** Directions in the fixed priority order used for extraction and insertion. */
    private static final int[] PRIORITY = {0, 1, 2, 3, 4, 5};

    /** Candidate directions for "continue on", indexed by {@code lastFrom + 1}. */
    private static final int[][] FORWARD = new int[DIRECTION_COUNT + 1][];

    static {
        FORWARD[0] = PRIORITY.clone();
        for (int from = 0; from < DIRECTION_COUNT; from++) {
            int[] candidates = new int[DIRECTION_COUNT - 1];
            int index = 0;
            for (int candidate : PRIORITY) {
                if (candidate != from) {
                    candidates[index++] = candidate;
                }
            }
            FORWARD[from + 1] = candidates;
        }
    }

    private LogisticsRules() {}

    /** Fixed priority order (DOWN, UP, NORTH, SOUTH, WEST, EAST). Do not modify the result. */
    public static int[] priorityOrder() {
        return PRIORITY;
    }

    /**
     * Directions to try when handing an item on, with the direction it arrived from removed.
     * The removal is what stops a two-block ping-pong. Returns a shared array.
     *
     * @param lastFrom arrival direction index, or {@link #NO_DIRECTION}
     */
    public static int[] forwardOrder(int lastFrom) {
        if (lastFrom < 0 || lastFrom >= DIRECTION_COUNT) {
            return FORWARD[0];
        }
        return FORWARD[lastFrom + 1];
    }

    /**
     * A pipe may forward an item if it has not used up its hop budget. The hop counter is the
     * number of pipe-to-pipe handoffs since the item left its source inventory; it bounds routing
     * on a network without a sink (no infinite circulation, no unbounded search).
     */
    public static boolean canForward(int hops, int maxHops) {
        return hops < Math.max(1, maxHops);
    }

    /**
     * Writes {@link #forwardOrder(int)} rotated by {@code cursor} into {@code out} (Phase 80).
     *
     * <p>The rotation is what spreads stacks over several equally valid sinks: a pipe that always
     * tried the same side first would fill one chest while the other three stayed empty. The
     * cursor is a per-pipe counter that advances after every delivery, so consecutive stacks
     * start at different sides. Rotation never adds or removes a candidate, and the arrival side
     * stays excluded, so the two-block ping-pong guard is unchanged.</p>
     *
     * @param lastFrom arrival direction index, or {@link #NO_DIRECTION}
     * @param cursor   monotonically growing per-pipe counter (any int is accepted)
     * @param out      array of at least {@code DIRECTION_COUNT - 1} entries
     * @return number of candidates written
     */
    public static int fillRotatedForwardOrder(int lastFrom, int cursor, int[] out) {
        int[] base = forwardOrder(lastFrom);
        int count = Math.min(base.length, out.length);
        if (count == 0) {
            return 0;
        }
        int shift = Math.floorMod(cursor, base.length);
        for (int index = 0; index < count; index++) {
            out[index] = base[(index + shift) % base.length];
        }
        return count;
    }

    /** Convenience wrapper of {@link #fillRotatedForwardOrder} that allocates its result. */
    public static int[] rotatedForwardOrder(int lastFrom, int cursor) {
        int[] out = new int[DIRECTION_COUNT - 1];
        int count = fillRotatedForwardOrder(lastFrom, cursor, out);
        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

}
