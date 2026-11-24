package com.industrialcore.machine;

import java.util.List;

/**
 * Round-robin planning for the Phase 19 item distributor.
 *
 * <p>The distributor holds one stack in an internal buffer and pushes it into up to six adjacent
 * inventories. The plan answers one question per tick: <em>how many items of the buffer go to
 * which output?</em> The answers are computed here, without Minecraft classes, so the arithmetic
 * can be unit tested directly.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>outputs are visited in the order given, starting at {@code cursor} and wrapping once,</li>
 *   <li>each output gets its share of what is still unassigned, never more than its free space,</li>
 *   <li>a full output is skipped and its share is handed to the following outputs instead,</li>
 *   <li>the sum of a plan is never larger than the offered amount, so an item can never be
 *       created, and whatever does not fit stays in the buffer.</li>
 * </ul>
 *
 * <p>With one item offered and two outputs the plan alternates (A, B, A, B, ...) because the
 * caller advances the cursor past the output that received items. With a full stack the same rule
 * splits the stack evenly (8 items over two outputs become 4 + 4), which is what a player expects
 * from a distributor.</p>
 */
public final class DistributionPlan {
    /**
     * @param side     direction index 0..5 (vanilla order) of the output
     * @param capacity items the output can still accept right now (0 = full)
     */
    public record Output(int side, int capacity) {
        public Output {
            capacity = Math.max(0, capacity);
        }
    }

    private DistributionPlan() {}

    /**
     * Plans one distribution pass.
     *
     * @param outputs candidate outputs in side order; must already be filtered to outputs that
     *                accept the buffered item
     * @param items   items in the buffer
     * @param cursor  first output index to try (an index into {@code outputs})
     * @return one amount per output, in the same order as {@code outputs}
     */
    public static int[] plan(List<Output> outputs, int items, int cursor) {
        int size = outputs == null ? 0 : outputs.size();
        int[] result = new int[size];
        if (size == 0) {
            return result;
        }
        int available = Math.max(0, items);
        if (available == 0) {
            return result;
        }
        int start = Math.floorMod(cursor, size);
        int remaining = available;
        for (int step = 0; step < size && remaining > 0; step++) {
            int index = (start + step) % size;
            int remainingOutputs = size - step;
            // Ceiling division: the leftovers of a split go to the next outputs, never away.
            int share = (remaining + remainingOutputs - 1) / remainingOutputs;
            int amount = Math.min(Math.min(share, remaining), outputs.get(index).capacity());
            result[index] = amount;
            remaining -= amount;
        }
        // A share can be too large for the outputs that were visited before it, which leaves items
        // unassigned even though earlier outputs still have room. Hand those out in one extra
        // sweep so a partially full lane never strands the rest of the stack.
        for (int pass = 0; pass < size && remaining > 0; pass++) {
            boolean progressed = false;
            for (int step = 0; step < size && remaining > 0; step++) {
                int index = (start + step) % size;
                int free = outputs.get(index).capacity() - result[index];
                if (free <= 0) {
                    continue;
                }
                int amount = Math.min(free, remaining);
                result[index] += amount;
                remaining -= amount;
                progressed = true;
            }
            if (!progressed) {
                break;
            }
        }
        return result;
    }

    /** Sum of a plan; never larger than the offered amount. */
    public static int total(int[] plan) {
        if (plan == null) {
            return 0;
        }
        int sum = 0;
        for (int value : plan) {
            sum += Math.max(0, value);
        }
        return sum;
    }

    /**
     * Index of the last output that received items, or -1 when the plan moves nothing.
     *
     * <p>The caller advances its round-robin cursor past this output so the next item starts at
     * the next lane.</p>
     */
    public static int lastUsedIndex(int[] plan) {
        if (plan == null) {
            return -1;
        }
        for (int index = plan.length - 1; index >= 0; index--) {
            if (plan[index] > 0) {
                return index;
            }
        }
        return -1;
    }
}
