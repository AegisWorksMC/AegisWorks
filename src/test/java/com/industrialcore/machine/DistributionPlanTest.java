package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 19: the distributor must alternate, skip full outputs and never lose an item.
 *
 * <p>The planner is free of Minecraft classes, so every case can be checked directly. The lists
 * below use real side indices (2 = north, 3 = south, 4 = west, 5 = east).</p>
 */
class DistributionPlanTest {
    private static DistributionPlan.Output out(int side, int capacity) {
        return new DistributionPlan.Output(side, capacity);
    }

    /** Single items alternate between two outputs, which is the documented default behaviour. */
    @Test
    void twoOutputsAlternateForSingleItems() {
        List<DistributionPlan.Output> outputs = List.of(out(4, 64), out(5, 64));
        int cursor = 0;
        boolean[] seen = new boolean[2];
        for (int pass = 0; pass < 6; pass++) {
            int[] plan = DistributionPlan.plan(outputs, 1, cursor);
            int used = DistributionPlan.lastUsedIndex(plan);
            assertEquals(1, DistributionPlan.total(plan), "one item must be moved per pass");
            assertEquals(pass % 2, used, "pass " + pass + " used output " + used);
            seen[used] = true;
            cursor = used + 1;
        }
        assertEquals(true, seen[0]);
        assertEquals(true, seen[1]);
    }

    /** Three outputs are visited in order and the order repeats after the last one. */
    @Test
    void threeOutputsGoInACircle() {
        List<DistributionPlan.Output> outputs = List.of(out(2, 64), out(3, 64), out(5, 64));
        int cursor = 0;
        int[] expected = {0, 1, 2, 0, 1, 2, 0};
        for (int pass = 0; pass < expected.length; pass++) {
            int[] plan = DistributionPlan.plan(outputs, 1, cursor);
            int used = DistributionPlan.lastUsedIndex(plan);
            assertEquals(expected[pass], used, "pass " + pass + " used output " + used);
            cursor = (used + 1) % outputs.size();
        }
    }

    /** A full output is skipped and its share goes to the next output. */
    @Test
    void aFullOutputIsSkipped() {
        int[] plan = DistributionPlan.plan(List.of(out(4, 0), out(5, 64)), 4, 0);
        assertArrayEquals(new int[] {0, 4}, plan);

        int[] three = DistributionPlan.plan(List.of(out(2, 0), out(3, 3), out(5, 64)), 9, 0);
        assertArrayEquals(new int[] {0, 3, 6}, three);
        assertEquals(9, DistributionPlan.total(three));
    }

    /** A whole stack is split evenly instead of being dumped into the first output. */
    @Test
    void aStackIsSplitEvenly() {
        assertArrayEquals(new int[] {4, 4},
                DistributionPlan.plan(List.of(out(4, 64), out(5, 64)), 8, 0));
        assertArrayEquals(new int[] {3, 3, 3},
                DistributionPlan.plan(List.of(out(2, 64), out(3, 64), out(5, 64)), 9, 0));
        // Starting at a full output hands the first share to the next one instead.
        assertArrayEquals(new int[] {0, 5, 4},
                DistributionPlan.plan(List.of(out(2, 0), out(3, 64), out(5, 64)), 9, 0));
    }

    /** An uneven output only takes what fits; the rest is handed on, never dropped. */
    @Test
    void smallOutputsPassTheRemainderOn() {
        int[] plan = DistributionPlan.plan(List.of(out(4, 1), out(5, 4), out(2, 64)), 9, 0);
        assertEquals(9, DistributionPlan.total(plan));
        assertArrayEquals(new int[] {1, 4, 4}, plan);
    }

    /** The plan can never create items or overfill a target. */
    @Test
    void planNeverExceedsTheOfferOrTheCapacity() {
        for (int items = 0; items <= 64; items++) {
            int[] plan = DistributionPlan.plan(
                    List.of(out(2, 10), out(3, 20), out(5, 0)), items, 1);
            assertEquals(Math.min(items, 30), DistributionPlan.total(plan),
                    items + " items produced " + DistributionPlan.total(plan));
            assertEquals(0, plan[2], "a full output received items");
        }
    }

    /** A cursor outside the list is wrapped instead of throwing or silently aiming elsewhere. */
    @Test
    void cursorWrapsAndClamps() {
        List<DistributionPlan.Output> outputs = List.of(out(4, 64), out(5, 64));
        assertArrayEquals(new int[] {1, 0}, DistributionPlan.plan(outputs, 1, -4));
        assertArrayEquals(new int[] {1, 0}, DistributionPlan.plan(outputs, 1, 2));
        assertArrayEquals(new int[] {1, 0}, DistributionPlan.plan(outputs, 1, Integer.MAX_VALUE - 1));
    }

    /** No outputs, no items and no free space all produce an empty plan. */
    @Test
    void emptyInputsProduceAnEmptyPlan() {
        assertEquals(0, DistributionPlan.plan(List.of(), 8, 0).length);
        assertArrayEquals(new int[] {0, 0},
                DistributionPlan.plan(List.of(out(4, 64), out(5, 64)), 0, 0));
        assertArrayEquals(new int[] {0, 0},
                DistributionPlan.plan(List.of(out(4, 0), out(5, 0)), 8, 0));
        assertEquals(0, DistributionPlan.total(null));
        assertEquals(-1, DistributionPlan.lastUsedIndex(new int[] {0, 0}));
        assertEquals(-1, DistributionPlan.lastUsedIndex(null));
    }

    /** Negative capacities (a broken neighbour report) are clamped to zero. */
    @Test
    void capacitiesAreClamped() {
        assertEquals(0, out(2, -5).capacity());
        assertArrayEquals(new int[] {0, 3},
                DistributionPlan.plan(List.of(out(2, -5), out(5, 3)), 8, 0));
    }
}
