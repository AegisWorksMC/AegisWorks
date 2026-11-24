package com.industrialcore.machine;

/**
 * Transport rules of the Phase 10 pipe connection modes, kept free of Minecraft classes so the
 * decision table can be unit tested.
 *
 * <p>Every question the block entity asks about a side goes through this class:</p>
 * <ul>
 *   <li>may this side take part in pipe-to-pipe transport ({@link #canTraverse})?</li>
 *   <li>may the pipe extract from the inventory on this side ({@link #canPull})?</li>
 *   <li>may the pipe insert into the inventory on this side ({@link #canPush})?</li>
 * </ul>
 *
 * <p>An adjacent pipe is never treated as an inventory, so {@code PUSH}/{@code PULL} towards a
 * pipe fall back to plain transport instead of being ignored.</p>
 */
public final class PipeConnectionRules {
    public static final int DIRECTION_COUNT = 6;

    private PipeConnectionRules() {}

    /**
     * The mode a face actually transports with (Phase 80).
     *
     * <p>A face that was never configured ({@code NONE}) opens automatically when the neighbour is
     * another item pipe: a pipe-to-pipe joint is part of the route, not a separate decision the
     * player has to make with a wrench. Towards anything else (chest, machine, conveyor, air) an
     * unconfigured face stays {@code NONE}, so a freshly placed pipe still moves nothing on its
     * own; {@code PULL} and {@code PUSH} remain the only wrench decisions, and they only ever
     * matter next to a real inventory.</p>
     *
     * <p>The declared mode wins whenever it already transports: an explicit {@code PULL} side
     * facing a pipe behaves like {@code NORMAL}, exactly as before.</p>
     *
     * @param declared      the mode stored for the face
     * @param pipeNeighbour true when the neighbour is another item pipe
     */
    public static PipeConnectionMode effective(PipeConnectionMode declared, boolean pipeNeighbour) {
        PipeConnectionMode mode = declared == null ? PipeConnectionMode.NONE : declared;
        if (mode.isTransport()) {
            return mode;
        }
        return pipeNeighbour ? PipeConnectionMode.NORMAL : PipeConnectionMode.NONE;
    }

    /** True when a face is open only because it joins another pipe (no wrench was needed). */
    public static boolean isAutoOpen(PipeConnectionMode declared, boolean pipeNeighbour) {
        return pipeNeighbour && (declared == null || declared.isNone());
    }

    /** True when the side is part of the network (everything except {@code NONE}). */
    public static boolean canTraverse(PipeConnectionMode mode) {
        return mode != null && mode.isTransport();
    }

    /** True when an item may be pulled out of an adjacent inventory on this side. */
    public static boolean canPull(PipeConnectionMode mode) {
        return mode == PipeConnectionMode.PULL;
    }

    /** True when the held stack may be inserted into an adjacent inventory on this side. */
    public static boolean canPush(PipeConnectionMode mode) {
        return mode == PipeConnectionMode.PUSH;
    }

    /**
     * True when a hand-off from {@code sending} to {@code receiving} is possible. Both ends must be
     * part of the network; a single {@code NONE} side is a wall.
     */
    public static boolean canHandOff(PipeConnectionMode sending, PipeConnectionMode receiving) {
        return canTraverse(sending) && canTraverse(receiving);
    }

    /**
     * Directions the pipe may extract from, in the supplied priority order.
     *
     * @param modes  six modes indexed by {@code Direction#get3DDataValue()}; may be shorter
     * @param order  candidate direction indices in priority order
     * @return a new array of direction indices whose mode is {@code PULL}
     */
    public static int[] pullCandidates(PipeConnectionMode[] modes, int[] order) {
        if (modes == null || order == null) {
            return new int[0];
        }
        int[] result = new int[order.length];
        int count = collectPullCandidates(modes, order, result);
        int[] trimmed = new int[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * Fills {@code out} with the direction indices whose mode is {@code PULL}, in priority order.
     *
     * <p>The item pipe asks this question every tick while its buffer is empty, so the allocation
     * free variant lets a machine reuse one scratch array instead of creating two per tick.</p>
     *
     * @param out array of at least {@code order.length} entries
     * @return how many entries were written (the first {@code n} entries are the candidates)
     */
    public static int collectPullCandidates(PipeConnectionMode[] modes, int[] order, int[] out) {
        if (modes == null || order == null || out == null) {
            return 0;
        }
        int count = 0;
        for (int direction : order) {
            if (count >= out.length) {
                break;
            }
            if (direction >= 0 && direction < modes.length && canPull(modes[direction])) {
                out[count++] = direction;
            }
        }
        return count;
    }

    /**
     * Cycle result for one face. Kept here (instead of in the item) so the wrench and any future
     * GUI button share exactly one order.
     *
     * @param current the mode before the click
     * @param reverse true when the player is sneaking
     */
    public static PipeConnectionMode cycle(PipeConnectionMode current, boolean reverse) {
        return (current == null ? PipeConnectionMode.NONE : current).next(reverse);
    }
}
