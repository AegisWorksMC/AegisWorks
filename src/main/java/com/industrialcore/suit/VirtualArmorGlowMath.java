package com.industrialcore.suit;

/**
 * Phase 105.7: Pure-Java animation &amp; color math for {@code VirtualArmorGlowLayer}.
 *
 * <p>Controls the breathing emissive pulse (0.85..1.0) and subtle additive neon shimmer
 * intensity (0.22..0.30) + diagonal UV scrolling so the Virtual Aegis powered armor gleams
 * vividly in darkness without washing out its dark-titanium silhouette.</p>
 */
public final class VirtualArmorGlowMath {
    public static final float EMISSIVE_MIN = 0.85F;
    public static final float EMISSIVE_MAX = 1.00F;
    public static final float SHIMMER_MIN = 0.22F;
    public static final float SHIMMER_MAX = 0.30F;
    public static final float SHIMMER_SPEED_U = 0.011F;
    public static final float SHIMMER_SPEED_V = 0.0075F;

    private VirtualArmorGlowMath() {
    }

    /** Subtle breathing luminance multiplier for the emissive visor/core layer in {@code [0.85, 1.0]}. */
    public static float emissivePulse(float ageTicks) {
        double wave = 0.5D + 0.5D * Math.sin(ageTicks * 0.14D);
        return (float) (EMISSIVE_MIN + (EMISSIVE_MAX - EMISSIVE_MIN) * wave);
    }

    /** Controlled additive intensity for the neon cyan shimmer in {@code [0.22, 0.30]}. */
    public static float shimmerIntensity(float ageTicks) {
        double wave = 0.5D + 0.5D * Math.sin(ageTicks * 0.09D + 0.7D);
        return (float) (SHIMMER_MIN + (SHIMMER_MAX - SHIMMER_MIN) * wave);
    }

    /** Normalized diagonal horizontal UV scroll offset in {@code [0.0, 1.0)}. */
    public static float shimmerScrollU(float ageTicks) {
        float u = (ageTicks * SHIMMER_SPEED_U) % 1.0F;
        return u < 0.0F ? u + 1.0F : u;
    }

    /** Normalized diagonal vertical UV scroll offset in {@code [0.0, 1.0)}. */
    public static float shimmerScrollV(float ageTicks) {
        float v = (ageTicks * SHIMMER_SPEED_V) % 1.0F;
        return v < 0.0F ? v + 1.0F : v;
    }

    /** Packs normalized {@code [0, 1]} ARGB channels into a 32-bit vertex color int. */
    public static int packArgb(float a, float r, float g, float b) {
        int ai = Math.max(0, Math.min(255, Math.round(a * 255.0F)));
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0F)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0F)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0F)));
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
