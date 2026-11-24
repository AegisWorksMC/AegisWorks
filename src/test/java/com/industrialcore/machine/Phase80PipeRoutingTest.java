package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 80 routing rules: pipe-to-pipe faces open without a wrench, stacks are spread over every
 * accepting sink, a sink that refuses is skipped (reroute) and a fully blocked line keeps the
 * stack instead of voiding it.
 *
 * <p>The tests stay free of Minecraft classes: they drive the same
 * {@link PipeConnectionRules} / {@link LogisticsRules} decision tables the block entity uses, so
 * the distribution and reroute behaviour can be checked headless. The block entity adds only the
 * capability lookups and the ownership move on top of these decisions.</p>
 */
class Phase80PipeRoutingTest {

    /** Directions in the vanilla order used by the rules. */
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    @Test
    void pipeFacesOpenWithoutAWrench() {
        // A middle pipe is surrounded by pipes: every unconfigured face transports.
        for (PipeConnectionMode declared : new PipeConnectionMode[]{
                PipeConnectionMode.NONE, PipeConnectionMode.NORMAL,
                PipeConnectionMode.PUSH, PipeConnectionMode.PULL}) {
            PipeConnectionMode effective = PipeConnectionRules.effective(declared, true);
            assertTrue(PipeConnectionRules.canTraverse(effective),
                    "a pipe-to-pipe face must transport, declared=" + declared);
        }
        assertTrue(PipeConnectionRules.isAutoOpen(PipeConnectionMode.NONE, true));
        assertFalse(PipeConnectionRules.isAutoOpen(PipeConnectionMode.PUSH, true),
                "an explicit mode is not an auto-open face");

        // The same unconfigured face next to an inventory stays closed until the wrench sets it.
        PipeConnectionMode chest = PipeConnectionRules.effective(PipeConnectionMode.NONE, false);
        assertEquals(PipeConnectionMode.NONE, chest);
        assertFalse(PipeConnectionRules.canTraverse(chest));
        assertFalse(PipeConnectionRules.canPush(chest));
        assertFalse(PipeConnectionRules.canPull(chest));

        // The two decisions the player still makes are exactly the inventory faces.
        assertTrue(PipeConnectionRules.canPull(
                PipeConnectionRules.effective(PipeConnectionMode.PULL, false)));
        assertTrue(PipeConnectionRules.canPush(
                PipeConnectionRules.effective(PipeConnectionMode.PUSH, false)));
    }

    @Test
    void twoAutoOpenPipesHandOffInBothDirections() {
        PipeConnectionMode a = PipeConnectionRules.effective(PipeConnectionMode.NONE, true);
        PipeConnectionMode b = PipeConnectionRules.effective(PipeConnectionMode.NONE, true);
        assertTrue(PipeConnectionRules.canHandOff(a, b));
        // One side switched off is still a wall, auto-open or not.
        assertFalse(PipeConnectionRules.canHandOff(a, PipeConnectionMode.NONE));
        assertFalse(PipeConnectionRules.canHandOff(PipeConnectionMode.NONE, b));
    }

    @Test
    void rotatedOrderKeepsEveryCandidateAndExcludesTheArrivalSide() {
        int[] order = LogisticsRules.rotatedForwardOrder(SOUTH, 0);
        assertEquals(5, order.length, "five sides remain after excluding the arrival");
        for (int direction = 0; direction < 6; direction++) {
            boolean present = false;
            for (int candidate : order) {
                present |= candidate == direction;
            }
            assertEquals(direction != SOUTH, present,
                    "direction " + direction + " candidate presence");
        }
        // Rotating never loses or duplicates a candidate, it only changes the start.
        for (int cursor = 0; cursor < 12; cursor++) {
            int[] rotated = LogisticsRules.rotatedForwardOrder(SOUTH, cursor);
            assertEquals(5, rotated.length);
            assertEquals(order[cursor % order.length], rotated[0]);
        }
    }

    @Test
    void consecutiveStacksSpreadOverEveryAcceptingSink() {
        // Three chests that all accept; 12 stacks must end up 4 / 4 / 4, not 12 / 0 / 0.
        Map<Integer, Integer> free = new LinkedHashMap<>();
        free.put(NORTH, 64);
        free.put(WEST, 64);
        free.put(EAST, 64);
        Outcome outcome = run(12, free, Integer.MAX_VALUE);

        assertEquals(12, outcome.delivered);
        assertEquals(0, outcome.held);
        assertEquals(4, outcome.perDirection.get(NORTH));
        assertEquals(4, outcome.perDirection.get(WEST));
        assertEquals(4, outcome.perDirection.get(EAST));
    }

    @Test
    void aFullSinkIsSkippedAndTheStackReroutes() {
        // NORTH is already full: every stack must go to the other two, none is lost.
        Map<Integer, Integer> free = new LinkedHashMap<>();
        free.put(NORTH, 0);
        free.put(WEST, 10);
        free.put(EAST, 10);
        Outcome outcome = run(12, free, Integer.MAX_VALUE);

        assertEquals(12, outcome.delivered);
        assertEquals(0, outcome.held);
        assertEquals(0, outcome.perDirection.get(NORTH));
        assertEquals(6, outcome.perDirection.get(WEST));
        assertEquals(6, outcome.perDirection.get(EAST));
    }

    @Test
    void everythingFullKeepsTheStackInsideThePipe() {
        Map<Integer, Integer> free = new LinkedHashMap<>();
        free.put(NORTH, 0);
        free.put(WEST, 0);
        free.put(EAST, 0);
        Outcome outcome = run(5, free, Integer.MAX_VALUE);

        assertEquals(0, outcome.delivered, "nothing may disappear into a full network");
        assertEquals(5, outcome.held, "the pipe keeps every stack it could not deliver");
    }

    @Test
    void partialSinkTakesWhatFitsAndTheRestReroutes() {
        // NORTH accepts two, WEST one; the remaining seven go to EAST.
        Map<Integer, Integer> free = new LinkedHashMap<>();
        free.put(NORTH, 2);
        free.put(WEST, 1);
        free.put(EAST, 64);
        Outcome outcome = run(10, free, Integer.MAX_VALUE);

        assertEquals(10, outcome.delivered);
        assertEquals(2, outcome.perDirection.get(NORTH));
        assertEquals(1, outcome.perDirection.get(WEST));
        assertEquals(7, outcome.perDirection.get(EAST));
    }

    @Test
    void hopBudgetStillStopsEndlessCirculation() {
        assertTrue(LogisticsRules.canForward(0, 24));
        assertTrue(LogisticsRules.canForward(23, 24));
        assertFalse(LogisticsRules.canForward(24, 24));
        assertFalse(LogisticsRules.canForward(25, 24));
    }

    // ------------------------------------------------------------------
    // Simulation of one pipe's delivery loop
    // ------------------------------------------------------------------

    private record Outcome(int delivered, int held, Map<Integer, Integer> perDirection) {
    }

    /**
     * Mirrors {@code ItemPipeBlockEntity.chooseDestination}: walk the rotated forward order, keep
     * only the sides that can still take a stack, and deliver to the one that was used least
     * recently (ties in rotated order). A stack nothing accepts stays in the pipe.
     */
    private static Outcome run(int stacks, Map<Integer, Integer> free, int maxStacks) {
        Map<Integer, Integer> accepted = new LinkedHashMap<>();
        for (int direction : free.keySet()) {
            accepted.put(direction, 0);
        }
        int[] lastUsed = new int[6];
        int clock = 0;
        int cursor = 0;
        int delivered = 0;
        int held = 0;
        int total = Math.min(stacks, maxStacks);
        for (int stack = 0; stack < total; stack++) {
            int[] order = LogisticsRules.rotatedForwardOrder(UP, cursor);
            int best = -1;
            int bestStamp = Integer.MAX_VALUE;
            for (int direction : order) {
                Integer room = free.get(direction);
                if (room == null || room <= 0) {
                    continue;
                }
                if (lastUsed[direction] < bestStamp) {
                    bestStamp = lastUsed[direction];
                    best = direction;
                }
            }
            if (best >= 0) {
                free.put(best, free.get(best) - 1);
                accepted.merge(best, 1, Integer::sum);
                lastUsed[best] = ++clock;
                cursor++;
                delivered++;
            } else {
                held++;
            }
        }
        return new Outcome(delivered, held, accepted);
    }
}
