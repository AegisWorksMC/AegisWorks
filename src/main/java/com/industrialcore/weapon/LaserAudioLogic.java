package com.industrialcore.weapon;

/**
 * Pure-Java real-time audio mixing math for the dual-layer continuous energy beam
 * crossfading Pulse Laser loop + Hit Sizzle feedback.
 *
 * <ul>
 *   <li><b>Light layer</b>: sharp, focused base beam hum ({@code 0.38 -> 0.25} vol, {@code 0.95 -> 1.35} pitch).</li>
 *   <li><b>Heavy layer</b>: deep ominous low-end sub-bass growl that swells dramatically with charge
 *       ({@code 0.05 -> 0.82} vol, {@code 0.68 -> 1.08} pitch).</li>
 *   <li><b>Hit Sizzle layer</b>: tactile plasma-burn crackle layered ONLY while actively damaging a
 *       target ({@code 0.35 -> 0.55} vol, {@code 0.0} when not hitting).</li>
 * </ul>
 */
public final class LaserAudioLogic {
    public static final float LIGHT_VOL_MIN_CHARGE = 0.38F;
    public static final float LIGHT_VOL_MAX_CHARGE = 0.25F;
    public static final float LIGHT_PITCH_MIN = 0.95F;
    public static final float LIGHT_PITCH_MAX = 1.35F;

    public static final float HEAVY_VOL_MIN_CHARGE = 0.05F;
    public static final float HEAVY_VOL_MAX_CHARGE = 0.82F;
    public static final float HEAVY_PITCH_MIN = 0.68F;
    public static final float HEAVY_PITCH_MAX = 1.08F;

    public static final float SIZZLE_VOL_MIN = 0.35F;
    public static final float SIZZLE_VOL_MAX = 0.55F;
    public static final float SIZZLE_PITCH_MIN = 0.94F;
    public static final float SIZZLE_PITCH_MAX = 1.22F;

    public static final float FADE_IN_STEP = 0.14F;
    public static final float FADE_OUT_STEP = 0.22F;
    public static final float PITCH_SMOOTH_ALPHA = 0.35F;

    public record LaserMix(
            boolean active,
            float lightVolume,
            float lightPitch,
            float heavyVolume,
            float heavyPitch,
            float sizzleVolume,
            float sizzlePitch
    ) {
        public static final LaserMix SILENT = new LaserMix(
                false, 0.0F, LIGHT_PITCH_MIN, 0.0F, HEAVY_PITCH_MIN, 0.0F, SIZZLE_PITCH_MIN);
    }

    private LaserAudioLogic() {}

    /**
     * Determines whether continuous Pulse Laser audio loops should currently be active.
     * Immediately returns {@code false} on weapon release, slot switch, overheat, or FE depletion.
     */
    public static boolean shouldBeActive(boolean holdingPulseLaser,
                                         boolean firingNow,
                                         boolean overheated,
                                         boolean hasPowerOrCreative) {
        return holdingPulseLaser && firingNow && !overheated && hasPowerOrCreative;
    }

    /**
     * Computes exact target volume &amp; pitch for all 3 Pulse Laser audio layers given current
     * firing state, {@code charge [0..1]}, and whether the beam is actively damaging an entity.
     */
    public static LaserMix computeMix(boolean active, float charge, boolean hittingTarget) {
        if (!active) {
            return LaserMix.SILENT;
        }
        float c = clamp01(charge);
        float lightVol = lerp(LIGHT_VOL_MIN_CHARGE, LIGHT_VOL_MAX_CHARGE, c);
        float lightPitch = lerp(LIGHT_PITCH_MIN, LIGHT_PITCH_MAX, c);

        // Convex curve so Heavy sub-bass growl stays whisper-quiet near 0% and dominates near 100%
        float heavyCurve = (float) Math.pow(c, 1.35D);
        float heavyVol = lerp(HEAVY_VOL_MIN_CHARGE, HEAVY_VOL_MAX_CHARGE, heavyCurve);
        float heavyPitch = lerp(HEAVY_PITCH_MIN, HEAVY_PITCH_MAX, c);

        float sizzleVol = hittingTarget ? lerp(SIZZLE_VOL_MIN, SIZZLE_VOL_MAX, c) : 0.0F;
        float sizzlePitch = lerp(SIZZLE_PITCH_MIN, SIZZLE_PITCH_MAX, c);

        return new LaserMix(true, lightVol, lightPitch, heavyVol, heavyPitch, sizzleVol, sizzlePitch);
    }

    /** Clickless linear envelope step (smooth fade-in, rapid crisp fade-out). */
    public static float stepVolume(float current, float target) {
        float cur = Math.max(0.0F, current);
        float tgt = Math.max(0.0F, target);
        if (tgt > cur) {
            return Math.min(tgt, cur + FADE_IN_STEP);
        }
        if (tgt < cur) {
            return Math.max(tgt, cur - FADE_OUT_STEP);
        }
        return cur;
    }

    /** Smooth pitch interpolation to eliminate zipper artifacts during rapid charge sweeps. */
    public static float stepPitch(float current, float target) {
        float next = current + (target - current) * PITCH_SMOOTH_ALPHA;
        return Math.max(0.5F, Math.min(2.0F, next));
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
