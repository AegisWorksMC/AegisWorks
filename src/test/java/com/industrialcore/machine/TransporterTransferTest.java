package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the transfer math (Phases 26-29).
 *
 * <p>Everything runs against a small array-backed {@link TransporterTransfer.Buffer}, so the
 * no-loss/no-duplication contract is checked on a plain JVM: whole stacks move in one slot
 * operation (the work counter proves a 4096-item batch never degrades into 4096 steps), a
 * destination that over-promises free space causes a refund instead of a loss, and the FE math
 * respects cap, room and source, including the overflow edge.</p>
 */
class TransporterTransferTest {

    /** Array-backed buffer with a per-slot limit; tokens are plain strings. */
    private static class FakeBuffer implements TransporterTransfer.Buffer {
        private final Object[] tokens;
        private final int[] counts;
        private final int limit;
        int extracts;
        int inserts;

        FakeBuffer(int slots, int limit) {
            this.tokens = new Object[slots];
            this.counts = new int[slots];
            this.limit = limit;
        }

        static FakeBuffer with(int slotCount, int limit, Object... pairs) {
            FakeBuffer buffer = new FakeBuffer(slotCount, limit);
            for (int index = 0; index + 1 < pairs.length; index += 2) {
                buffer.tokens[index / 2] = pairs[index];
                buffer.counts[index / 2] = (Integer) pairs[index + 1];
            }
            return buffer;
        }

        @Override
        public int slots() {
            return counts.length;
        }

        @Override
        public int count(int slot) {
            return counts[slot];
        }

        @Override
        public Object token(int slot) {
            return counts[slot] > 0 ? tokens[slot] : null;
        }

        @Override
        public int space(int slot, Object token) {
            if (counts[slot] == 0) {
                return limit;
            }
            return tokens[slot].equals(token) ? limit - counts[slot] : 0;
        }

        @Override
        public int extract(int slot, int amount) {
            extracts++;
            int taken = Math.min(amount, counts[slot]);
            counts[slot] -= taken;
            if (counts[slot] == 0) {
                tokens[slot] = null;
            }
            return taken;
        }

        @Override
        public int insert(int slot, Object token, int amount) {
            inserts++;
            int room = space(slot, token);
            int accepted = Math.min(room, Math.max(0, amount));
            if (accepted > 0) {
                if (counts[slot] == 0) {
                    tokens[slot] = token;
                }
                counts[slot] += accepted;
            }
            return accepted;
        }

        int total() {
            int sum = 0;
            for (int value : counts) {
                sum += value;
            }
            return sum;
        }

        int totalOf(String token) {
            int sum = 0;
            for (int slot = 0; slot < counts.length; slot++) {
                if (counts[slot] > 0 && token.equals(tokens[slot])) {
                    sum += counts[slot];
                }
            }
            return sum;
        }

        List<String> stackDump() {
            List<String> dump = new ArrayList<>();
            for (int slot = 0; slot < counts.length; slot++) {
                if (counts[slot] > 0) {
                    dump.add(tokens[slot] + "x" + counts[slot]);
                }
            }
            return dump;
        }
    }

    /** Destination that promises far more room than it really has. */
    private static final class LyingBuffer extends FakeBuffer {
        LyingBuffer(int slots) {
            super(slots, 64);
        }

        @Override
        public int space(int slot, Object token) {
            return 1_000_000; // always claims to accept anything
        }

        @Override
        public int insert(int slot, Object token, int amount) {
            // ...but really behaves like a normal 64-limit slot.
            return super.insert(slot, token, Math.min(amount, 64 - super.count(slot)) );
        }
    }

    @Test
    void wholeStacksMoveInOneOperation() {
        FakeBuffer source = FakeBuffer.with(4, 64, "cobble", 64);
        FakeBuffer dest = new FakeBuffer(4, 64);
        int moved = TransporterTransfer.moveBatch(source, dest, 64);
        assertEquals(64, moved);
        assertEquals(0, source.total());
        assertEquals(64, dest.totalOf("cobble"));
        assertTrue(source.extracts + dest.inserts <= 4,
                "one stack must move with a handful of slot operations, not 64 of them");
    }

    @Test
    void theCapIsNeverExceededEvenWithPlentyOfRoom() {
        FakeBuffer source = FakeBuffer.with(2, 64, "cobble", 64, "cobble", 30);
        FakeBuffer dest = new FakeBuffer(4, 64);
        assertEquals(1, TransporterTransfer.moveBatch(source, dest, 1));
        assertEquals(93, source.total(), "only one item may leave for a cap of 1");
        assertEquals(1, dest.total());

        FakeBuffer sourceTwo = FakeBuffer.with(2, 64, "cobble", 64, "cobble", 30);
        FakeBuffer destTwo = new FakeBuffer(4, 64);
        assertEquals(80, TransporterTransfer.moveBatch(sourceTwo, destTwo, 80));
        assertEquals(14, sourceTwo.total(), "64 + 16 of the second stack move for a cap of 80");
        assertEquals(64, destTwo.count(0));
        assertEquals(16, destTwo.count(1));
    }

    @Test
    void aFullOrForeignReceiverChangesNothing() {
        FakeBuffer full = FakeBuffer.with(2, 64, "cobble", 64, "cobble", 64);
        FakeBuffer sourceFull = FakeBuffer.with(1, 64, "cobble", 32);
        assertEquals(0, TransporterTransfer.moveBatch(sourceFull, full, 64));
        assertEquals(32, sourceFull.total(), "a full receiver must not eat the sender's items");

        FakeBuffer foreign = FakeBuffer.with(2, 64, "dirt", 64, "dirt", 64);
        FakeBuffer sourceForeign = FakeBuffer.with(1, 64, "cobble", 32);
        assertEquals(0, TransporterTransfer.moveBatch(sourceForeign, foreign, 64));
        assertEquals(32, sourceForeign.total());
        assertEquals(List.of("dirtx64", "dirtx64"), foreign.stackDump(),
                "the foreign stacks must stay untouched");
    }

    @Test
    void partialRoomIsFilledBeforeEmptySlots() {
        FakeBuffer dest = FakeBuffer.with(3, 64, "cobble", 60);
        FakeBuffer source = FakeBuffer.with(1, 64, "cobble", 64);
        int moved = TransporterTransfer.moveBatch(source, dest, 64);
        assertEquals(64, moved);
        assertEquals(64, dest.count(0), "the partial stack is topped up first");
        assertEquals(60, dest.count(1), "the rest flows into the next slot");
        assertEquals(0, source.total());
    }

    @Test
    void differentStacksNeverMergeIntoEachOther() {
        FakeBuffer dest = FakeBuffer.with(3, 64, "iron", 64, "iron", 10);
        FakeBuffer source = FakeBuffer.with(1, 64, "gold", 64);
        int moved = TransporterTransfer.moveBatch(source, dest, 64);
        assertEquals(64, moved);
        assertEquals(64, dest.count(0), "the full iron stack stays as it is");
        assertEquals(10, dest.count(1), "the partial iron stack is not touched");
        assertEquals(64, dest.count(2), "gold only lands in an empty slot");
        assertEquals(List.of("ironx64", "ironx10", "goldx64"), dest.stackDump());
    }

    @Test
    void aDestinationThatOverPromisesCausesARefundNotALoss() {
        FakeBuffer dest = new LyingBuffer(4);
        FakeBuffer source = FakeBuffer.with(1, 64, "cobble", 64);
        int before = source.total() + dest.total();

        // The simulation believes a million items fit, the real insert refuses most of them:
        // moveBatch must extract only what arrived and refund the rest.
        int moved = TransporterTransfer.moveBatch(source, dest, 64);
        assertEquals(before, source.total() + dest.total(),
                "the total number of items must be identical before and after");
        assertTrue(moved <= 64, "the cap still applies");
        assertTrue(source.total() + moved == 64, "everything not moved is back in the source");
    }

    @Test
    void aFourThousandItemBatchCostsSlotSizedWorkOnly() {
        FakeBuffer source = new FakeBuffer(64, 64);
        for (int slot = 0; slot < 64; slot++) {
            source.insert(slot, "cobble", 64);
        }
        FakeBuffer dest = new FakeBuffer(64, 64);

        int moved = TransporterTransfer.moveBatch(source, dest, 4096);

        assertEquals(4096, moved, "the whole batch moves");
        int operations = source.extracts + source.inserts + dest.extracts + dest.inserts;
        assertTrue(operations <= 4 * 64,
                "4096 items must cost O(slots) operations, not O(items); used " + operations);
        assertEquals(0, source.total());
        assertEquals(4096, dest.total());
    }

    @Test
    void zeroAndNegativeCapsMoveNothing() {
        FakeBuffer source = FakeBuffer.with(1, 64, "cobble", 64);
        FakeBuffer dest = new FakeBuffer(1, 64);
        assertEquals(0, TransporterTransfer.moveBatch(source, dest, 0));
        assertEquals(0, TransporterTransfer.moveBatch(source, dest, -5));
        assertEquals(64, source.total());
    }

    @Test
    void feMoveHonoursCapRoomAndSource() {
        // Everything allows the full amount.
        assertEquals(1_000, TransporterTransfer.powerMove(5_000, 0, 10_000, 1_000));
        // The tier cap is the smallest term.
        assertEquals(100, TransporterTransfer.powerMove(5_000, 0, 10_000, 100));
        // The source is the smallest term.
        assertEquals(7, TransporterTransfer.powerMove(7, 0, 10_000, 100));
        // The room is the smallest term.
        assertEquals(42, TransporterTransfer.powerMove(5_000, 9_958, 10_000, 100));
        // A full destination never receives and never costs the source anything.
        assertEquals(0, TransporterTransfer.powerMove(5_000, 10_000, 10_000, 100));
        // Degenerate inputs stay non-negative.
        assertEquals(0, TransporterTransfer.powerMove(0, 0, 10_000, 100));
        assertEquals(0, TransporterTransfer.powerMove(-5, 0, 10_000, 100));
        assertEquals(0, TransporterTransfer.powerMove(5, 0, 0, 100));
        assertEquals(0, TransporterTransfer.powerMove(5_000, 0, 10_000, 0));
    }

    @Test
    void feMoveCannotOverflowNearIntegerMax() {
        int room = Integer.MAX_VALUE - 5;
        // Naive destStored + cap arithmetic would wrap; the long maths must not.
        assertEquals(5, TransporterTransfer.powerMove(1_000, room, Integer.MAX_VALUE, 1_000),
                "only the five free FE may move");
        assertEquals(1_000, TransporterTransfer.powerMove(1_000, -10, 1_000, 1_000),
                "a nonsense stored value must not invent room");
        assertEquals(Integer.MAX_VALUE - 10,
                TransporterTransfer.powerMove(Integer.MAX_VALUE, 10, Integer.MAX_VALUE,
                        Integer.MAX_VALUE));
    }

    @Test
    void conservationHoldsAcrossAMixedBatch() {
        FakeBuffer source = FakeBuffer.with(4, 64, "cobble", 50, "gold", 17, "iron", 64, "sand", 3);
        FakeBuffer dest = FakeBuffer.with(4, 64, "iron", 60);
        int before = source.total() + dest.total();

        for (int cap = 1; cap <= 200; cap += 17) {
            TransporterTransfer.moveBatch(source, dest, cap);
        }
        assertEquals(before, source.total() + dest.total(),
                "no sequence of caps may create or destroy an item");
        assertEquals(64, dest.count(0), "the iron stack ends exactly at the slot limit");
    }
}
