package com.industrialcore.machine;

/**
 * The transfer math of Phases 26-29, written against a minimal {@link Buffer} abstraction so the
 * no-loss/no-duplication contract can be unit tested on a plain JVM, without a Minecraft stack.
 *
 * <p>Two rules shape the algorithm:</p>
 * <ul>
 *   <li><b>Stacks, never single items.</b> The loops walk slots and move whole stacks (or the
 *       remaining cap, whichever is smaller); a 4096-item transfer is a bounded number of slot
 *       operations, never 4096 iterations.</li>
 *   <li><b>Insert first, withdraw after.</b> The sender only loses the exact number of items the
 *       receiver actually took, and any remainder goes straight back into the sender. A receiver
 *       that lies about its free space can therefore never duplicate an item, and a failed insert
 *       can never destroy one.</li>
 * </ul>
 */
public final class TransporterTransfer {

    private TransporterTransfer() {}

    /**
     * A slot buffer as the transfer sees it. The block entity implements this directly (no
     * per-tick adapter objects); unit tests implement it over arrays.
     *
     * <p>{@link #space(int, Object)} must answer for the exact token: free slots return what one
     * stack of that token can hold (stack size included), occupied slots return the remaining
     * room when the token matches the contents and 0 when it does not.</p>
     */
    public interface Buffer {
        int slots();

        int count(int slot);

        /** Free room for this exact token in this slot; 0 when it cannot be inserted. */
        int space(int slot, Object token);

        /** Identity of the stack in the slot, or {@code null} when the slot is empty. */
        Object token(int slot);

        /** Removes up to {@code amount} and returns how much was actually removed. */
        int extract(int slot, int amount);

        /** Inserts up to {@code amount} of the token and returns how much was actually inserted. */
        int insert(int slot, Object token, int amount);
    }

    /**
     * Moves up to {@code cap} items from {@code source} into {@code dest} and returns how many
     * arrived.
     *
     * <p>Source items are withdrawn slot by slot, only after the destination accepted them;
     * anything the destination refused is inserted back into the source. With honest buffers the
     * total item count of both buffers is therefore identical before and after every call, and
     * the destination never receives more than {@code cap} items or more than a slot can hold.</p>
     */
    public static int moveBatch(Buffer source, Buffer dest, int cap) {
        if (cap <= 0 || source == null || dest == null) {
            return 0;
        }
        int remaining = cap;
        int moved = 0;
        for (int slot = 0; slot < source.slots() && remaining > 0; slot++) {
            Object token = source.token(slot);
            if (token == null) {
                continue;
            }
            int tryCount = Math.min(source.count(slot), remaining);
            if (tryCount <= 0) {
                continue;
            }
            int accepted = Math.min(tryCount, simulatedAccepts(dest, token));
            if (accepted <= 0) {
                continue;
            }
            int taken = source.extract(slot, accepted);
            if (taken <= 0) {
                continue;
            }
            int inserted = insertGreedy(dest, token, taken);
            int leftover = taken - inserted;
            if (leftover > 0) {
                // The source just freed at least this many items, so the refund always fits.
                insertGreedy(source, token, leftover);
            }
            moved += inserted;
            remaining -= inserted;
        }
        return moved;
    }

    /**
     * FE one channel power transfer may move: the tier's cap, limited by what the source really
     * holds and by the room left in the destination. Never negative, never overflowing - the
     * room is computed in {@code long} so a destination near {@link Integer#MAX_VALUE} cannot
     * wrap around into a huge transfer.
     */
    public static int powerMove(int sourceStored, int destStored, int destCapacity, int cap) {
        if (cap <= 0 || sourceStored <= 0 || destCapacity <= 0) {
            return 0;
        }
        long stored = Math.max(0, destStored);
        if (stored >= destCapacity) {
            return 0;
        }
        long room = (long) destCapacity - stored;
        long amount = Math.min((long) cap, Math.min((long) sourceStored, room));
        return (int) Math.max(0L, amount);
    }

    /** How many of {@code token} the destination would take right now (simulated, no change). */
    private static int simulatedAccepts(Buffer dest, Object token) {
        int total = 0;
        for (int slot = 0; slot < dest.slots(); slot++) {
            total += Math.max(0, dest.space(slot, token));
        }
        return total;
    }

    /** Inserts up to {@code amount} of the token into any accepting slot; returns the total. */
    private static int insertGreedy(Buffer dest, Object token, int amount) {
        int left = amount;
        for (int slot = 0; slot < dest.slots() && left > 0; slot++) {
            int room = dest.space(slot, token);
            if (room <= 0) {
                continue;
            }
            int chunk = Math.min(room, left);
            int inserted = dest.insert(slot, token, chunk);
            if (inserted > 0) {
                left -= inserted;
            }
        }
        return amount - left;
    }
}
