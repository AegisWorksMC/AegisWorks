package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the transporter connection cache (Phases 26-29).
 *
 * <p>The cache is what keeps the network off the per-tick world scan, so these tests pin its two
 * contracts: an entry exists exactly once (re-registering and re-configuring never duplicates a
 * device), and candidates iterate in a fixed (x, y, z) order so destination choice is
 * deterministic rather than random.</p>
 */
class ChannelIndexTest {

    private static final GridPos A = new GridPos(5, 6, 1);
    private static final GridPos B = new GridPos(2, 6, 1);
    private static final GridPos C = new GridPos(5, 6, 9);

    @Test
    void registrationIsIdempotentAndKeepsOneEntryPerPosition() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(A, "ore", TransportRole.SEND);
        assertEquals(1, index.size(), "re-registering must not duplicate a device");
        assertEquals(1, index.senderCount("ore"));
        assertEquals(0, index.receiverCount("ore"));
        assertEquals(new ChannelIndex.Assignment("ore", TransportRole.SEND),
                index.assignmentOf(A));
    }

    @Test
    void changingTheChannelMovesTheEntryInsteadOfLeavingStaleData() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.RECEIVE);
        index.upsert(A, "factory", TransportRole.RECEIVE);

        assertEquals(0, index.receiverCount("ore"), "the old channel must be empty");
        assertEquals(1, index.receiverCount("factory"), "the new channel must hold it");
        assertEquals(1, index.size(), "one position, one assignment");
        assertEquals("factory", index.assignmentOf(A).channelKey());
    }

    @Test
    void changingTheRoleMovesTheEntryBetweenBuckets() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(A, "ore", TransportRole.RECEIVE);

        assertEquals(0, index.senderCount("ore"));
        assertEquals(1, index.receiverCount("ore"));
        assertEquals(TransportRole.RECEIVE, index.assignmentOf(A).role());
    }

    @Test
    void removalIsCleanAndSafeToRepeat() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(B, "ore", TransportRole.SEND);
        assertTrue(index.remove(B));
        assertFalse(index.remove(B), "removing twice is a no-op, not an error");
        assertNull(index.assignmentOf(B));
        assertEquals(0, index.size());
        assertTrue(index.receivers("ore").isEmpty());
        assertTrue(index.senders("ore").isEmpty());
    }

    @Test
    void candidatesIterateInPositionOrderNotInInsertionOrder() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(C, "ore", TransportRole.RECEIVE);   // (5, 6, 9)
        index.upsert(A, "ore", TransportRole.RECEIVE);   // (5, 6, 1)
        index.upsert(B, "ore", TransportRole.RECEIVE);   // (2, 6, 1)

        List<GridPos> order = new ArrayList<>(index.receivers("ore"));
        assertEquals(List.of(B, A, C), order,
                "receivers must be tried in (x, y, z) order - never randomly");
    }

    @Test
    void emptyAndUnknownChannelsAreSilentlyEmpty() {
        ChannelIndex index = new ChannelIndex();
        assertTrue(index.receivers("").isEmpty());
        assertTrue(index.senders("").isEmpty());
        assertTrue(index.receivers("nobody").isEmpty());
        assertEquals(0, index.receiverCount("nobody"));
        assertEquals(0, index.senderCount("nobody"));
    }

    @Test
    void anEmptyChannelKeyActsAsRemoval() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(A, "", TransportRole.SEND);
        assertNull(index.assignmentOf(A), "an empty channel must unregister the device");
        assertEquals(0, index.size());
    }

    @Test
    void sendersAndReceiversNeverShareABucket() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(B, "ore", TransportRole.RECEIVE);
        index.upsert(C, "ore", TransportRole.SEND);
        assertEquals(2, index.senderCount("ore"));
        assertEquals(1, index.receiverCount("ore"));
        assertEquals(List.of(B), new ArrayList<>(index.receivers("ore")));
    }

    @Test
    void clearForgetsEverything() {
        ChannelIndex index = new ChannelIndex();
        index.upsert(A, "ore", TransportRole.SEND);
        index.upsert(B, "ore2", TransportRole.RECEIVE);
        index.clear();
        assertEquals(0, index.size());
        assertNull(index.assignmentOf(A));
        assertTrue(index.receivers("ore2").isEmpty());
    }
}
