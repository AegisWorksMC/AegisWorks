package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsRulesTest {

    @Test
    void priorityOrderIsFixedAndComplete() {
        int[] order = LogisticsRules.priorityOrder();
        assertEquals(LogisticsRules.DIRECTION_COUNT, order.length);
        // 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
        assertEquals(0, order[0]);
        assertEquals(1, order[1]);
        assertEquals(2, order[2]);
        assertEquals(3, order[3]);
        assertEquals(4, order[4]);
        assertEquals(5, order[5]);
    }

    @Test
    void forwardOrderExcludesTheArrivalDirection() {
        for (int from = 0; from < LogisticsRules.DIRECTION_COUNT; from++) {
            int[] order = LogisticsRules.forwardOrder(from);
            assertEquals(LogisticsRules.DIRECTION_COUNT - 1, order.length);
            for (int candidate : order) {
                assertFalse(candidate == from, "the arrival direction must not be tried again");
            }
        }
    }

    @Test
    void forwardOrderWithoutArrivalDirectionUsesAllDirections() {
        int[] order = LogisticsRules.forwardOrder(LogisticsRules.NO_DIRECTION);
        assertEquals(LogisticsRules.DIRECTION_COUNT, order.length);
        assertEquals(0, order[0]);
        // Out-of-range values must behave like "no arrival direction" instead of throwing.
        assertEquals(LogisticsRules.DIRECTION_COUNT, LogisticsRules.forwardOrder(99).length);
        assertEquals(LogisticsRules.DIRECTION_COUNT, LogisticsRules.forwardOrder(-7).length);
    }

    @Test
    void canForwardRespectsTheHopBudget() {
        assertTrue(LogisticsRules.canForward(0, 4));
        assertTrue(LogisticsRules.canForward(3, 4));
        assertFalse(LogisticsRules.canForward(4, 4));
        assertFalse(LogisticsRules.canForward(999, 4));
        // A zero/negative budget is clamped to one hop instead of blocking everything.
        assertTrue(LogisticsRules.canForward(0, 0));
        assertFalse(LogisticsRules.canForward(1, 0));
    }

}
