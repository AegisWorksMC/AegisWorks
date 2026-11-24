package com.industrialcore.client;

/**
 * Frame maths for the stack travelling through an item pipe (Phase 81).
 *
 * <p>Two things made the old rendering look jerky even though the server had the right position:</p>
 * <ol>
 *   <li>The path was two straight lines that met at the tube centre, so a corner changed direction
 *       instantly and the item appeared to snap sideways.</li>
 *   <li>Every visual update restarted the drawn position exactly at the reported value. A packet
 *       that arrived late (or an extrapolated position that had already run past the new sample)
 *       produced a visible jump backwards.</li>
 * </ol>
 *
 * <p>The path is now a quadratic Bezier with the control point in the centre of the block: it is
 * the same straight run for a pipe that goes straight through, and a smooth curve for a corner,
 * with a continuous direction at every point. The drawn position is additionally pulled towards
 * the target with an exponential filter whose time constant is expressed in ticks, so the result
 * is independent of the frame rate and of the sync interval: a 2-tick update and a 20-tick update
 * both glide instead of stepping.</p>
 *
 * <p>Minecraft-free on purpose, so the blend and the curve can be unit tested exactly.</p>
 */
public final class PipeSmoothMotion {
    /** Time constant of the filter in ticks; roughly one and a bit ticks of catch-up. */
    public static final double SMOOTHING_TAU_TICKS = 1.1D;
    /** Blend used when the caller cannot supply a time delta (first frame, paused level). */
    public static final float FALLBACK_BLEND = 0.30F;

    private PipeSmoothMotion() {
    }

    /** Fraction of the remaining distance to close for a frame that lasted {@code deltaTicks}. */
    public static float blend(double deltaTicks) {
        if (!Double.isFinite(deltaTicks) || deltaTicks <= 0.0D) {
            return FALLBACK_BLEND;
        }
        double value = 1.0D - Math.exp(-deltaTicks / SMOOTHING_TAU_TICKS);
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    /** Moves {@code current} towards {@code target}; a non-finite value snaps instead of poisoning. */
    public static float approach(float current, float target, double deltaTicks) {
        if (!Float.isFinite(current)) {
            return target;
        }
        if (!Float.isFinite(target)) {
            return current;
        }
        float alpha = blend(deltaTicks);
        return current + (target - current) * alpha;
    }

    /**
     * Quadratic Bezier point: {@code (1-t)^2 start + 2(1-t)t control + t^2 end}, with
     * {@code t} clamped to 0..1.
     */
    public static float path(float start, float control, float end, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        float inverse = 1.0F - t;
        return inverse * inverse * start + 2.0F * inverse * t * control + t * t * end;
    }
}
