package com.industrialcore.machine;

import java.util.List;

/**
 * How one arriving stack is spread over the auto crafter's input slots.
 *
 * <p>The old behaviour filled the first slot that accepted the item up to its stack limit and only
 * then moved on. With a pipe pushing planks into a 3x3 grid that meant slot 0 received all 64
 * planks while the other recipe slots stayed empty, which looks broken even though the recipe
 * matcher could still craft.</p>
 *
 * <p>Phase 81 removed the GUI toggle: levelling is now the only behaviour, because the shape fix
 * in {@link CrafterRecipePlan} requires the recipe slots to actually hold their ingredients. The
 * planned additions make the per-slot counts differ by at most one, limited by each slot's free
 * space and by the number of items that actually arrived. Full slots are skipped without stopping
 * the others, and the sum of the plan is never larger than the amount offered, so no item can be
 * created.</p>
 *
 * <p>Free of Minecraft classes so the arithmetic can be unit tested directly.</p>
 */
public final class CrafterDistribution {
    /**
     * @param index      slot index (only used by the caller)
     * @param count      items already in the slot (0 for an empty slot)
     * @param freeSpace  how many more items fit into the slot (>= 0)
     */
    public record Slot(int index, int count, int freeSpace) {
        public Slot {
            count = Math.max(0, count);
            freeSpace = Math.max(0, freeSpace);
        }
    }

    private CrafterDistribution() {
    }

    /**
     * Plans the number of items to add to each candidate slot, levelling the resulting counts.
     *
     * @param slots    candidate slots; must already be limited to slots that accept the item
     * @param amount   items offered (negative values are treated as zero)
     * @return one addition per candidate, in the same order as {@code slots}
     */
    public static int[] plan(List<Slot> slots, int amount) {
        if (slots == null || slots.isEmpty()) {
            return new int[0];
        }
        int[] result = new int[slots.size()];
        int offered = Math.max(0, amount);
        if (offered == 0) {
            return result;
        }

        long totalFree = 0L;
        long current = 0L;
        int usable = 0;
        for (Slot slot : slots) {
            totalFree += slot.freeSpace();
            if (slot.freeSpace() > 0) {
                // A full slot must not inflate the level: its 64 items are not part of the split.
                current += slot.count();
                usable++;
            }
        }
        int movable = (int) Math.min(offered, totalFree);
        if (movable <= 0) {
            return result;
        }
        int target = (int) ((current + movable) / Math.max(1, usable));
        int budget = movable;
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            int add = Math.max(0, Math.min(Math.min(slot.freeSpace(), target - slot.count()), budget));
            result[index] = add;
            budget -= add;
        }
        // The remainder is handed out one item at a time to the slot with the fewest items, so the
        // final counts differ by at most one (10 items over three empty slots become 4/3/3, not
        // 4/4/2). A slot with a small stack limit is skipped once it is full.
        while (budget > 0) {
            int best = -1;
            int bestCount = Integer.MAX_VALUE;
            for (int index = 0; index < slots.size(); index++) {
                if (result[index] >= slots.get(index).freeSpace()) {
                    continue;
                }
                int count = slots.get(index).count() + result[index];
                if (count < bestCount) {
                    bestCount = count;
                    best = index;
                }
            }
            if (best < 0) {
                break;
            }
            result[best]++;
            budget--;
        }
        return result;
    }

    /** Sum of a plan (never larger than the offered amount). */
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
}
