package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 10: the pipe mode decision table. */
class PipeConnectionRulesTest {

    @Test
    void noneNeverTransportsAnything() {
        assertFalse(PipeConnectionRules.canTraverse(PipeConnectionMode.NONE));
        assertFalse(PipeConnectionRules.canPull(PipeConnectionMode.NONE));
        assertFalse(PipeConnectionRules.canPush(PipeConnectionMode.NONE));
        assertFalse(PipeConnectionRules.canHandOff(PipeConnectionMode.NONE, PipeConnectionMode.NORMAL));
        assertFalse(PipeConnectionRules.canHandOff(PipeConnectionMode.NORMAL, PipeConnectionMode.NONE));
    }

    @Test
    void normalIsARouteButNeverTouchesAnInventory() {
        assertTrue(PipeConnectionRules.canTraverse(PipeConnectionMode.NORMAL));
        assertFalse(PipeConnectionRules.canPull(PipeConnectionMode.NORMAL));
        assertFalse(PipeConnectionRules.canPush(PipeConnectionMode.NORMAL));
        assertTrue(PipeConnectionRules.canHandOff(PipeConnectionMode.NORMAL, PipeConnectionMode.NORMAL));
        // PUSH/PULL towards another pipe still work as a route, because a pipe is no inventory.
        assertTrue(PipeConnectionRules.canHandOff(PipeConnectionMode.PUSH, PipeConnectionMode.NORMAL));
        assertTrue(PipeConnectionRules.canHandOff(PipeConnectionMode.NORMAL, PipeConnectionMode.PULL));
    }

    @Test
    void pushAndPullAreExclusive() {
        assertTrue(PipeConnectionRules.canPush(PipeConnectionMode.PUSH));
        assertFalse(PipeConnectionRules.canPull(PipeConnectionMode.PUSH));
        assertTrue(PipeConnectionRules.canPull(PipeConnectionMode.PULL));
        assertFalse(PipeConnectionRules.canPush(PipeConnectionMode.PULL));
        assertTrue(PipeConnectionRules.canTraverse(PipeConnectionMode.PUSH));
        assertTrue(PipeConnectionRules.canTraverse(PipeConnectionMode.PULL));
    }

    @Test
    void pullCandidatesKeepPriorityOrderAndSkipEverythingElse() {
        PipeConnectionMode[] modes = {
                PipeConnectionMode.NORMAL,  // DOWN
                PipeConnectionMode.PUSH,    // UP
                PipeConnectionMode.PULL,    // NORTH
                PipeConnectionMode.NONE,    // SOUTH
                PipeConnectionMode.PULL,    // WEST
                PipeConnectionMode.NORMAL   // EAST
        };
        int[] order = {0, 1, 2, 3, 4, 5};
        assertArrayEquals(new int[] {2, 4}, PipeConnectionRules.pullCandidates(modes, order));
        assertArrayEquals(new int[] {4, 2},
                PipeConnectionRules.pullCandidates(modes, new int[] {4, 2, 0}));
        assertArrayEquals(new int[0], PipeConnectionRules.pullCandidates(null, order));
        assertArrayEquals(new int[0], PipeConnectionRules.pullCandidates(modes, null));
        // Out-of-range indices from a broken caller are ignored instead of throwing.
        assertArrayEquals(new int[0], PipeConnectionRules.pullCandidates(modes, new int[] {-1, 9}));
    }

    @Test
    void collectPullCandidatesFillsAReusableArray() {
        PipeConnectionMode[] modes = {
                PipeConnectionMode.NORMAL,  // DOWN
                PipeConnectionMode.PUSH,    // UP
                PipeConnectionMode.PULL,    // NORTH
                PipeConnectionMode.NONE,    // SOUTH
                PipeConnectionMode.PULL,    // WEST
                PipeConnectionMode.NORMAL   // EAST
        };
        int[] order = {0, 1, 2, 3, 4, 5};
        int[] scratch = {-1, -1, -1, -1, -1, -1};
        int count = PipeConnectionRules.collectPullCandidates(modes, order, scratch);
        assertEquals(2, count);
        assertEquals(2, scratch[0]);
        assertEquals(4, scratch[1]);
        // Entries past the count are left alone, so a shorter result never reads stale indices.
        assertEquals(-1, scratch[2]);
        // The allocation free variant returns exactly what the allocating one does.
        int[] allocating = PipeConnectionRules.pullCandidates(modes, order);
        assertEquals(allocating.length, count);
        for (int i = 0; i < count; i++) {
            assertEquals(allocating[i], scratch[i]);
        }
        assertEquals(0, PipeConnectionRules.collectPullCandidates(null, order, scratch));
        assertEquals(0, PipeConnectionRules.collectPullCandidates(modes, null, scratch));
        assertEquals(0, PipeConnectionRules.collectPullCandidates(modes, order, null));
    }

    @Test
    void collectPullCandidatesNeverOverflowsASmallerScratchArray() {
        PipeConnectionMode[] modes = new PipeConnectionMode[6];
        java.util.Arrays.fill(modes, PipeConnectionMode.PULL);
        int[] scratch = new int[2];
        assertEquals(2, PipeConnectionRules.collectPullCandidates(modes,
                new int[] {5, 4, 3, 2, 1, 0}, scratch));
        assertArrayEquals(new int[] {5, 4}, scratch);
    }

    @Test
    void wrenchCycleGoesBothWaysAndWraps() {
        assertEquals(PipeConnectionMode.NORMAL, PipeConnectionRules.cycle(PipeConnectionMode.NONE, false));
        assertEquals(PipeConnectionMode.PUSH, PipeConnectionMode.NORMAL.next(false));
        assertEquals(PipeConnectionMode.PULL, PipeConnectionMode.PUSH.next(false));
        assertEquals(PipeConnectionMode.NONE, PipeConnectionMode.PULL.next(false));
        assertEquals(PipeConnectionMode.PULL, PipeConnectionRules.cycle(PipeConnectionMode.NONE, true));
        assertEquals(PipeConnectionMode.NONE, PipeConnectionMode.NORMAL.next(true));
        assertEquals(PipeConnectionMode.NORMAL, PipeConnectionRules.cycle(null, false));
        assertEquals(PipeConnectionMode.PULL, PipeConnectionRules.cycle(null, true));
    }

    @Test
    void modeIdsAndTranslationKeysAreStable() {
        assertEquals("none", PipeConnectionMode.NONE.id());
        assertEquals("normal", PipeConnectionMode.NORMAL.id());
        assertEquals("push", PipeConnectionMode.PUSH.id());
        assertEquals("pull", PipeConnectionMode.PULL.id());
        assertEquals("gui.industrialcore.pipe_mode.push", PipeConnectionMode.PUSH.translationKey());
        assertEquals("gui.industrialcore.pipe_mode.pull.desc", PipeConnectionMode.PULL.descriptionKey());
        assertEquals(PipeConnectionMode.NONE, PipeConnectionMode.byIndex(-10));
        assertEquals(PipeConnectionMode.PULL, PipeConnectionMode.byIndex(99));
    }

    @Test
    void defaultConstructionIsNoneForEveryFace() {
        // The block entity starts every face at NONE; this is what makes a new pipe inert.
        PipeConnectionMode[] fresh = new PipeConnectionMode[6];
        java.util.Arrays.fill(fresh, PipeConnectionMode.NONE);
        assertArrayEquals(new int[0], PipeConnectionRules.pullCandidates(fresh, new int[] {0, 1, 2, 3, 4, 5}));
        for (PipeConnectionMode mode : fresh) {
            assertFalse(PipeConnectionRules.canPush(mode));
            assertFalse(PipeConnectionRules.canTraverse(mode));
        }
    }
}
