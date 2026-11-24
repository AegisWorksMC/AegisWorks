package com.industrialcore.machine;

/**
 * Client-side interpolation for the drill head.
 *
 * <p>The server only sends the mining cursor every few ticks (the interval shrinks or grows with
 * the number of running machines). Drawing the raw cursor therefore makes the head jump by the
 * whole distance travelled inside one interval - at high motor counts that is dozens of blocks,
 * which looks like stutter even though the server is not overloaded. Instead the renderer glides
 * from the previously confirmed cursor to the newly confirmed one over one sync interval.</p>
 *
 * <p>Only server-confirmed values are ever drawn: the head lags behind by at most one interval
 * and never shows progress the server has not reported. No Minecraft classes are used here so the
 * maths can be unit tested.</p>
 */
public final class DrillInterpolation {
    private DrillInterpolation() {}

    /**
     * Progress between the previous and the latest confirmed cursor.
     *
     * @param ticksSinceUpdate client ticks elapsed since the update arrived
     * @param partialTick      partial tick of the current frame (0..1)
     * @param intervalTicks    sync interval the server used for this update (>= 1)
     * @return 0..1, where 1 means "the newest confirmed cursor is reached"
     */
    public static float progress(long ticksSinceUpdate, float partialTick, int intervalTicks) {
        if (intervalTicks <= 1) {
            return 1.0F;
        }
        float elapsed = Math.max(0L, ticksSinceUpdate) + clamp01(partialTick);
        return (float) Math.min(1.0D, elapsed / intervalTicks);
    }

    /**
     * Blends two confirmed cursor positions. Kept in double precision because a single interval can
     * cover dozens of blocks at high motor counts.
     */
    public static double blend(int previousCursor, int cursor, float progress) {
        if (progress <= 0.0F) {
            return previousCursor;
        }
        if (progress >= 1.0F || previousCursor == cursor) {
            return cursor;
        }
        return previousCursor + (cursor - previousCursor) * (double) progress;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value) || value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, 1.0F);
    }
}
