package com.industrialcore.machine;

/**
 * Balance of the Phase 25 quantum compressor.
 *
 * <p>The quantum compressor is the end-game sibling of the crusher and the fast crusher: it reads
 * the very same {@code industrialcore:crushing} recipes, but one cycle takes a fixed
 * {@link #CYCLE_TICKS} ticks and processes up to {@link #BATCH_CAP} items at once. The FE per item
 * is exactly the recipe's own cost ({@code time x energy} of the recipe file), so the machine is an
 * investment in throughput and power infrastructure, never in free energy:</p>
 *
 * <table>
 *   <caption>Shipped crushing recipe (10 ticks, 10 FE/t = 100 FE per item)</caption>
 *   <tr><th>Batch</th><th>Output</th><th>Total FE</th><th>FE/t over 240 ticks</th></tr>
 *   <tr><td>1</td><td>2 dust</td><td>100</td><td>1</td></tr>
 *   <tr><td>8</td><td>16 dust</td><td>800</td><td>4</td></tr>
 *   <tr><td>32</td><td>64 dust</td><td>3,200</td><td>14</td></tr>
 *   <tr><td>64</td><td>128 dust</td><td>6,400</td><td>27</td></tr>
 * </table>
 *
 * <p>Everything here is plain arithmetic on purpose: the block entity, the GUI and the unit tests
 * all use these methods, so a rounding change cannot make the machine spend more or less than the
 * documented cost. The batch is always the minimum of the batch cap, the items in the input slot
 * and the room left in the four output slots.</p>
 *
 * <p>Energy accounting: rather than an integer FE-per-tick value (which would round 100 FE over
 * 240 ticks to 0.42 and would drift for a recipe that does not divide evenly), the cycle pays the
 * exact cumulative amount {@code floor(total x elapsed / cycle)}. After the last tick exactly
 * {@code total} FE have been spent, no matter how the cycle was interrupted by a power cut.</p>
 */
public final class QuantumCompressorBalance {
    /** Items one cycle processes at most. */
    public static final int BATCH_CAP = 64;
    /** Ticks one cycle takes, independent of the recipe's own time. */
    public static final int CYCLE_TICKS = 240;
    /** FE buffer: far more than two full 64 batches of the shipped recipe (2 x 6,400 FE). */
    public static final int CAPACITY = 200_000;
    /** Maximum FE accepted per tick; a full batch draws at most 27 FE/t with the shipped recipe. */
    public static final int MAX_RECEIVE = 4_096;
    /** Output slots: a 64 batch of a 2-dust recipe yields 128 items, so one slot is not enough. */
    public static final int OUTPUT_SLOTS = 4;
    /** Stack limit used by the output slots of this machine. */
    public static final int SLOT_LIMIT = 64;
    /** Backoff of an idle machine before it re-checks its recipe (same as the other machines). */
    public static final int RETRY_INTERVAL_TICKS = ProcessingBalance.RETRY_INTERVAL_TICKS;

    /** FE one item of that recipe costs: the recipe's own time multiplied by its FE per tick. */
    public static long costPerItem(int recipeTimeTicks, int recipeEnergyPerTick) {
        return (long) Math.max(0, recipeTimeTicks) * Math.max(0, recipeEnergyPerTick);
    }

    /** FE one cycle of {@code batch} items costs. Saturates instead of wrapping. */
    public static long totalEnergy(int recipeTimeTicks, int recipeEnergyPerTick, int batch) {
        return saturatingMultiply(costPerItem(recipeTimeTicks, recipeEnergyPerTick),
                Math.max(0, batch));
    }

    /**
     * FE the cycle must have paid after {@code elapsedTicks} ticks.
     *
     * <p>{@code floor(total x elapsed / cycle)}: the value is monotonically increasing, reaches
     * exactly {@code total} at the last tick and never exceeds it. Interruptions (a power cut, a
     * full output) simply stop the counter, so a resumed cycle pays the remainder only.</p>
     */
    public static long energyDueBy(long total, int elapsedTicks, int cycleTicks) {
        if (total <= 0 || elapsedTicks <= 0 || cycleTicks <= 0) {
            return 0;
        }
        long elapsed = Math.min(elapsedTicks, cycleTicks);
        if (total <= Long.MAX_VALUE / elapsed) {
            // The exact integer path: no rounding at all, so the sum of the per-tick payments is
            // exactly the cycle cost.
            return (total * elapsed) / cycleTicks;
        }
        // Only reachable with a datapack value far beyond anything the machine can buffer.
        return (long) Math.floor((double) total * (double) elapsed / (double) cycleTicks);
    }

    /**
     * FE the tick that brings the cycle to {@code elapsedTicks} has to pay. The sum of
     * {@code paymentFor(total, 1..cycle)} is exactly {@code total}.
     */
    public static long paymentFor(long total, int elapsedTicks, int cycleTicks) {
        return energyDueBy(total, elapsedTicks, cycleTicks)
                - energyDueBy(total, elapsedTicks - 1, cycleTicks);
    }

    /**
     * Average FE per tick of a cycle, rounded up. Display only: the machine pays the exact
     * cumulative amount from {@link #energyDueBy}, not this average.
     */
    public static int averageEnergyPerTick(long total, int cycleTicks) {
        if (total <= 0 || cycleTicks <= 0) {
            return 0;
        }
        long average = (total + cycleTicks - 1L) / cycleTicks;
        return (int) Math.min(Integer.MAX_VALUE, average);
    }

    /**
     * Items one cycle may process: the minimum of the cap, the input count and what the output
     * slots still take. Zero means "wait for space" (or for input).
     */
    public static int batchSize(int inputCount, int perItemOutput, long freeSpace) {
        if (inputCount <= 0 || perItemOutput <= 0 || freeSpace <= 0) {
            return 0;
        }
        long bySpace = freeSpace / perItemOutput;
        long batch = Math.min(Math.min((long) inputCount, BATCH_CAP), bySpace);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, batch));
    }

    /**
     * Items of the planned result that still fit into the output slots.
     *
     * @param slotCounts current count of every output slot ({@code 0} for an empty slot)
     * @param sameItem   {@code true} where the slot already holds the planned result
     * @param slotLimit  stack limit of the machine's output slots
     */
    public static long freeSpaceFor(int[] slotCounts, boolean[] sameItem, int slotLimit) {
        int limit = Math.max(1, slotLimit);
        long free = 0;
        for (int index = 0; index < slotCounts.length; index++) {
            int count = slotCounts[index];
            if (count <= 0) {
                free += limit;
            } else if (index < sameItem.length && sameItem[index]) {
                free += Math.max(0, limit - count);
            }
        }
        return free;
    }

    /** Items the whole cycle produces: recipe output count multiplied by the batch. */
    public static long outputCount(int resultPerItem, int batch) {
        return saturatingMultiply(Math.max(0, resultPerItem), Math.max(0, batch));
    }

    /** Clamps a long into the int range for a field that stores FE or counts. */
    public static int clampToInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    /** {@code left x right} clamped to {@link Long#MAX_VALUE} instead of wrapping. */
    public static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private QuantumCompressorBalance() {}
}
