package com.industrialcore.machine;

/**
 * Pure rules for the pipe display updates.
 *
 * <p>The pipe moves its stack over several ticks on the server, but the client only needs a new
 * snapshot when the stack, its entry/exit side or its blocked state changes. This rule keeps an
 * idle or steadily moving network from sending one packet per tick.</p>
 *
 * <p>No Minecraft classes are used so the timing can be unit tested directly.</p>
 */
public final class VisualSyncRules {
    private VisualSyncRules() {}

    /**
     * Decides whether a logistics block entity should broadcast its visual state now.
     *
     * <p>A block that has nothing to show and did not change sends nothing at all. The first
     * update is always sent; later updates need the minimum interval, and a periodic refresh is
     * optional (the pipe uses 0 because the client extrapolates its progress).</p>
     *
     * @param now             monotonic tick counter of the block entity
     * @param lastSync        tick of the previous broadcast, or a negative value when never sent
     * @param dirty           the visual state changed since the last broadcast
     * @param hasContent      the block currently holds at least one item
     * @param minInterval     minimum ticks between two broadcasts (>= 1)
     * @param refreshInterval periodic re-send while content exists; <= 0 disables it
     */
    public static boolean dueForSync(long now, long lastSync, boolean dirty, boolean hasContent,
                                     int minInterval, int refreshInterval) {
        if (!dirty && !hasContent) {
            return false;
        }
        int minimum = Math.max(1, minInterval);
        if (lastSync < 0) {
            return true;
        }
        long since = Math.max(0L, now - lastSync);
        if (since < minimum) {
            return false;
        }
        if (dirty) {
            return true;
        }
        return hasContent && refreshInterval > 0 && since >= refreshInterval;
    }
}
