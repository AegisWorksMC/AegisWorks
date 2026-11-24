package com.industrialcore.client;

import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.RecoveryKind;

/**
 * Pure-Java mapping from every Industrial Core power generator / recovery device to its
 * method-specific ambient hum and harmonizing energy pulse profile.
 *
 * <p>Zero Minecraft imports so every mapping, volume range, pulse interval, and active-generation
 * predicate can be verified deterministically in unit tests.</p>
 */
public final class GeneratorAudioProfile {
    /** Common energy-pulse sound ID shared across all generator categories. */
    public static final String POWER_PULSE_SOUND_ID = "gen_power_pulse";

    public enum Category {
        /** Coal / fuel / combustion / heat-exhaust thermal generators. */
        THERMAL("gen_thermal_hum", 0.27f, 0.35f, 1500L),
        /** Solar / wind / hydro / geothermal / kinetic recovery generators. */
        KINETIC("gen_kinetic_hum", 0.25f, 0.34f, 1600L),
        /** Nuclear / high-output reactor generators. */
        REACTOR("gen_reactor_hum", 0.29f, 0.38f, 1400L);

        private final String humSoundId;
        private final float humVolume;
        private final float pulseVolume;
        private final long pulseIntervalMs;

        Category(String humSoundId, float humVolume, float pulseVolume, long pulseIntervalMs) {
            this.humSoundId = humSoundId;
            this.humVolume = humVolume;
            this.pulseVolume = pulseVolume;
            this.pulseIntervalMs = pulseIntervalMs;
        }

        public String humSoundId() {
            return humSoundId;
        }

        public float humVolume() {
            return humVolume;
        }

        public float pulseVolume() {
            return pulseVolume;
        }

        public long pulseIntervalMs() {
            return pulseIntervalMs;
        }

        public String pulseSoundId() {
            return POWER_PULSE_SOUND_ID;
        }
    }

    public record Profile(Category category, float pulsePitch) {}

    private GeneratorAudioProfile() {}

    /** Maps every {@link GenerationKind} to its method-specific hum and harmonizing pulse pitch. */
    public static Profile forGeneration(GenerationKind kind) {
        if (kind == null) {
            return new Profile(Category.THERMAL, 1.00f);
        }
        return switch (kind) {
            case THERMAL -> new Profile(Category.THERMAL, 0.94f);
            case COMBUSTION -> new Profile(Category.THERMAL, 1.00f);
            case HYDRO -> new Profile(Category.KINETIC, 1.06f);
            case WIND -> new Profile(Category.KINETIC, 1.1225f);
            case SOLAR -> new Profile(Category.KINETIC, 1.1892f);
            case GEOTHERMAL -> new Profile(Category.KINETIC, 0.97f);
            case NUCLEAR -> new Profile(Category.REACTOR, 1.00f);
        };
    }

    /** Maps every {@link RecoveryKind} to its matching generator audio profile. */
    public static Profile forRecovery(RecoveryKind kind) {
        if (kind == null) {
            return new Profile(Category.KINETIC, 1.00f);
        }
        return switch (kind) {
            case PRESSURE -> new Profile(Category.THERMAL, 1.00f);
            case HEAT -> new Profile(Category.THERMAL, 0.97f);
            case FLYWHEEL -> new Profile(Category.KINETIC, 1.06f);
            case REGEN -> new Profile(Category.KINETIC, 1.1225f);
        };
    }

    /**
     * True only while the generator is actively producing power ({@code rate > 0} and either
     * actively burning fuel or in the {@code RUNNING} state). Returns {@code false} immediately
     * when fuel runs out, conditions fail (night / no water / no lava / no air), or idle.
     */
    public static boolean isActivelyGenerating(int rate, int burnProgress, boolean statusRunning) {
        return rate > 0 && (burnProgress > 0 || statusRunning);
    }
}
