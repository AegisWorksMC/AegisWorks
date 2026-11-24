package com.industrialcore.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 102: Pure-Java spatial selection, nearest-4 voice cap, category crowding ducking,
 * open-GUI coordinate muting, and smooth volume fading logic for 3D World Ambient Machine Hums.
 *
 * <p>Zero Minecraft imports so every spatial, ducking, and lifecycle rule is verified
 * deterministically in JUnit 5 tests.</p>
 */
public final class WorldAmbientLogic {
    /** Maximum audible radius around the player for 3D world hums (blocks). */
    public static final double MAX_RADIUS = 14.0D;
    public static final double MAX_RADIUS_SQR = MAX_RADIUS * MAX_RADIUS;
    /** Hard cap on simultaneous 3D world hum sound instances to prevent factory loudness &amp; voice starvation. */
    public static final int MAX_ACTIVE_SOURCES = 4;
    /** Client scan interval in ticks (6 ticks = 0.3s). */
    public static final int SCAN_INTERVAL_TICKS = 6;
    /** Sentinel key meaning no machine GUI position is currently muted. */
    public static final long NO_MUTED_POS = Long.MIN_VALUE;
    /** Smooth per-tick fade step (fades between 0 and ~0.24 in ~8 ticks). */
    public static final float FADE_STEP = 0.032f;

    /** Gentle ducking multipliers when multiple active sources belong to the same category. */
    private static final float[] SAME_CATEGORY_DUCK = {1.00f, 0.78f, 0.62f, 0.50f};

    public enum Category {
        GEN_THERMAL("world_gen_thermal", 0.23f),
        GEN_KINETIC("world_gen_kinetic", 0.21f),
        GEN_REACTOR("world_gen_reactor", 0.25f),
        MACHINE_HUM("world_machine_hum", 0.19f);

        private final String soundId;
        private final float baseVolume;

        Category(String soundId, float baseVolume) {
            this.soundId = soundId;
            this.baseVolume = baseVolume;
        }

        public String soundId() {
            return soundId;
        }

        public float baseVolume() {
            return baseVolume;
        }
    }

    public record Candidate(long posKey, double x, double y, double z, Category category, double distSqr) {}

    public record Selection(long posKey, double x, double y, double z, Category category, float targetVolume) {}

    private WorldAmbientLogic() {}

    /**
     * Selects up to {@value #MAX_ACTIVE_SOURCES} nearest active world machines within
     * {@value #MAX_RADIUS} blocks, excluding {@code mutedGuiPosKey} (if a machine GUI is open
     * for that coordinate) and gently ducking crowded same-category sources.
     */
    public static List<Selection> selectActiveSources(List<Candidate> candidates, long mutedGuiPosKey) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Candidate> eligible = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c == null || c.category() == null) {
                continue;
            }
            if (mutedGuiPosKey != NO_MUTED_POS && c.posKey() == mutedGuiPosKey) {
                continue;
            }
            if (c.distSqr() < 0.0D || c.distSqr() > MAX_RADIUS_SQR) {
                continue;
            }
            eligible.add(c);
        }
        if (eligible.isEmpty()) {
            return List.of();
        }
        eligible.sort(Comparator.comparingDouble(Candidate::distSqr)
                .thenComparingLong(Candidate::posKey));

        int limit = Math.min(MAX_ACTIVE_SOURCES, eligible.size());
        Map<Category, Integer> catSeen = new EnumMap<>(Category.class);
        List<Selection> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Candidate c = eligible.get(i);
            int idx = catSeen.getOrDefault(c.category(), 0);
            catSeen.put(c.category(), idx + 1);
            float duck = SAME_CATEGORY_DUCK[Math.min(idx, SAME_CATEGORY_DUCK.length - 1)];
            float distFactor = distanceFactor(Math.sqrt(c.distSqr()));
            float vol = c.category().baseVolume() * duck * distFactor;
            if (vol > 0.004f) {
                out.add(new Selection(c.posKey(), c.x(), c.y(), c.z(), c.category(), vol));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Gentle distance rolloff complement (1.0 within 2m, smoothly tapering to 0.0 at 14m) paired
     * with OpenAL LINEAR attenuation so entering/leaving the 14m boundary never pops abruptly.
     */
    public static float distanceFactor(double dist) {
        if (dist <= 2.0D) {
            return 1.0f;
        }
        if (dist >= MAX_RADIUS) {
            return 0.0f;
        }
        double u = (dist - 2.0D) / (MAX_RADIUS - 2.0D);
        // Smooth ease-out curve so nearby presence stays warm and boundary fades silently to 0
        return (float) Math.max(0.0D, Math.min(1.0D, 1.0D - u * u * 0.85D));
    }

    /** Advances {@code current} smoothly toward {@code target} by at most {@code maxStep}. */
    public static float stepFade(float current, float target, float maxStep) {
        float step = Math.max(0.001f, maxStep);
        if (Math.abs(target - current) <= step) {
            return target;
        }
        return current < target ? current + step : current - step;
    }

    /** Maps a {@link GeneratorAudioProfile.Category} directly to its 3D Mono world hum category. */
    public static Category fromGeneratorCategory(GeneratorAudioProfile.Category genCat) {
        if (genCat == null) {
            return Category.GEN_THERMAL;
        }
        return switch (genCat) {
            case THERMAL -> Category.GEN_THERMAL;
            case KINETIC -> Category.GEN_KINETIC;
            case REACTOR -> Category.GEN_REACTOR;
        };
    }
}
