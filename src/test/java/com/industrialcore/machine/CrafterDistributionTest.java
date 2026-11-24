package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arriving stacks must be spread over the recipe slots, never piled into one. */
class CrafterDistributionTest {

    private static CrafterDistribution.Slot slot(int index, int count, int free) {
        return new CrafterDistribution.Slot(index, count, free);
    }

    private static List<CrafterDistribution.Slot> emptySlots(int count, int free) {
        List<CrafterDistribution.Slot> slots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            slots.add(slot(index, 0, free));
        }
        return slots;
    }

    @Test
    void splitsOneStackOverTheRecipeSlots() {
        // The reported bug: 16 planks arriving from a pipe ended up in slot 0 only.
        int[] plan = CrafterDistribution.plan(emptySlots(4, 64), 16);
        assertEquals(16, CrafterDistribution.total(plan));
        assertEquals(4, plan[0]);
        assertEquals(4, plan[1]);
        assertEquals(4, plan[2]);
        assertEquals(4, plan[3]);
    }

    @Test
    void keepsTheCountsWithinOne() {
        for (int amount = 1; amount <= 64; amount++) {
            int[] plan = CrafterDistribution.plan(emptySlots(3, 64), amount);
            assertEquals(amount, CrafterDistribution.total(plan), "amount " + amount);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int value : plan) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            assertTrue(max - min <= 1,
                    "amount " + amount + " produced " + java.util.Arrays.toString(plan));
        }
    }

    @Test
    void respectsExistingCountsAndFullSlots() {
        List<CrafterDistribution.Slot> slots = List.of(
                slot(0, 64, 0),
                slot(1, 10, 54),
                slot(2, 2, 62),
                slot(3, 0, 64));
        int[] plan = CrafterDistribution.plan(slots, 8);
        assertEquals(0, plan[0], "a full slot received items");
        assertEquals(8, CrafterDistribution.total(plan));
        // The empty slot catches up first, the 2 item slot is levelled afterwards.
        assertTrue(plan[3] > plan[1], "the distribution ignored the existing counts: "
                + java.util.Arrays.toString(plan));
    }

    @Test
    void oneFullSlotDoesNotStopTheOthers() {
        List<CrafterDistribution.Slot> slots = List.of(
                slot(0, 64, 0),
                slot(1, 0, 64),
                slot(2, 0, 64));
        int[] plan = CrafterDistribution.plan(slots, 20);
        assertEquals(0, plan[0]);
        assertEquals(20, CrafterDistribution.total(plan));
        assertTrue(plan[1] == 10 && plan[2] == 10,
                "a full slot disturbed the split: " + java.util.Arrays.toString(plan));
    }

    @Test
    void limitedStackSizeIsHandledWithoutOverflow() {
        List<CrafterDistribution.Slot> slots = List.of(
                slot(0, 0, 16),
                slot(1, 0, 16),
                slot(2, 0, 16));
        int[] plan = CrafterDistribution.plan(slots, 40);
        assertEquals(40, CrafterDistribution.total(plan));
        for (int index = 0; index < plan.length; index++) {
            assertTrue(plan[index] <= 16, "slot " + index + " got " + plan[index] + " items");
        }
    }

    @Test
    void neverInsertsMoreThanOfferedOrFits() {
        List<CrafterDistribution.Slot> slots = List.of(slot(0, 60, 4), slot(1, 60, 4));
        int[] plan = CrafterDistribution.plan(slots, 64);
        assertEquals(8, CrafterDistribution.total(plan));
        assertTrue(plan[0] <= 4 && plan[1] <= 4);
        assertTrue(CrafterDistribution.total(plan) <= 64);
    }

    @Test
    void brokenInputIsSafe() {
        assertEquals(0, CrafterDistribution.plan(null, 10).length);
        assertEquals(0, CrafterDistribution.plan(List.of(), 10).length);
        assertEquals(0, CrafterDistribution.total(CrafterDistribution.plan(emptySlots(2, 8), 0)));
        assertEquals(0, CrafterDistribution.total(CrafterDistribution.plan(emptySlots(2, 8), -5)));
        assertEquals(0, CrafterDistribution.total(CrafterDistribution.plan(emptySlots(2, 0), 8)));
        assertEquals(0, CrafterDistribution.total(null));
    }

    @Test
    void everyPlanIsNonNegative() {
        List<CrafterDistribution.Slot> slots = List.of(slot(0, -4, -2), slot(1, 3, 1));
        int[] plan = CrafterDistribution.plan(slots, 5);
        for (int value : plan) {
            assertTrue(value >= 0);
        }
    }
}
