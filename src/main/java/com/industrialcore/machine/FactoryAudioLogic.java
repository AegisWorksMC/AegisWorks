package com.industrialcore.machine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Phase 112: Pure-Java motion-synchronized factory SFX specification, per-machine mapping,
 * strict 12-block distance cutoff, and OpenAL audio budget limiter.
 *
 * <p>Strictly free of Minecraft client/server imports so unit tests can verify every single
 * machine-to-sound binding, silent passive block, distance cutoff, and budget guard on a plain JVM.</p>
 */
public final class FactoryAudioLogic {
    /** Hard distance silence cutoff (blocks): strictly silent beyond 12.0 blocks. */
    public static final double MAX_AUDIBLE_DISTANCE = 12.0D;
    public static final double MAX_AUDIBLE_DIST_SQ = MAX_AUDIBLE_DISTANCE * MAX_AUDIBLE_DISTANCE;

    /** Maximum total factory SFX allowed to start in any single client tick (base comfort cap). */
    public static final int MAX_SOUNDS_PER_TICK = 2;

    /** Subtle pitch jitter bounds around each machine's base pitch so identical machines never phase-lock. */
    public static final float MIN_PITCH_JITTER = 0.96F;
    public static final float MAX_PITCH_JITTER = 1.04F;

    /** 18 distinct noise-free pure-synthesis mono factory sound voices. */
    public enum SoundFamily {
        WIND_BLADE("machine_wind_blade", 0.13F, 48),
        HYDRO_TURBINE("machine_hydro_turbine", 0.13F, 44),
        BOILER_FIRE("machine_boiler_fire", 0.12F, 44),
        ENGINE_PISTON("machine_engine_piston", 0.14F, 36),
        STEAM_TURBINE("machine_steam_turbine", 0.13F, 42),
        NUCLEAR_CORE("machine_nuclear_core", 0.14F, 56),
        SINGULARITY_PULSE("machine_singularity_pulse", 0.16F, 56),
        PRESS_IMPACT("machine_press_impact", 0.26F, 20),
        CRUSHER_JAW("machine_crusher_jaw", 0.20F, 24),
        FURNACE_ARC("machine_furnace_arc", 0.14F, 40),
        ELECTROLYZER_BUBBLE("machine_electrolyzer_bubble", 0.17F, 32),
        ASSEMBLER_SERVO("machine_assembler_servo", 0.21F, 24),
        DRILL_BORE("machine_drill_bore", 0.20F, 24),
        COMPRESSOR_SLAM("machine_compressor_slam", 0.25F, 22),
        HARVESTER_BLADE("machine_harvester_blade", 0.19F, 26),
        WINCH_REEL("machine_winch_reel", 0.18F, 20),
        FAN_AIRFLOW("machine_fan_airflow", 0.13F, 40),
        QUANTUM_CHIME("machine_quantum_chime", 0.14F, 44);

        private final String soundId;
        private final float baseVolume;
        private final int cooldownTicks;

        SoundFamily(String soundId, float baseVolume, int cooldownTicks) {
            this.soundId = soundId;
            this.baseVolume = baseVolume;
            this.cooldownTicks = cooldownTicks;
        }

        public String soundId() {
            return soundId;
        }

        public float baseVolume() {
            return baseVolume;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }
    }

    /** Exact acoustic tuning profile assigned to one machine block ID. */
    public record MachineAudioProfile(SoundFamily family, float volume, float basePitch, int cooldownTicks) {}

    private static final Map<String, MachineAudioProfile> PROFILES;
    static {
        Map<String, MachineAudioProfile> m = new LinkedHashMap<>();
        // Generators & Energy Recovery
        m.put("wind_generator", new MachineAudioProfile(SoundFamily.WIND_BLADE, 0.13F, 1.00F, 48));
        m.put("hydro_generator", new MachineAudioProfile(SoundFamily.HYDRO_TURBINE, 0.13F, 1.00F, 44));
        m.put("thermal_generator", new MachineAudioProfile(SoundFamily.BOILER_FIRE, 0.12F, 1.00F, 44));
        m.put("geothermal_generator", new MachineAudioProfile(SoundFamily.BOILER_FIRE, 0.12F, 0.85F, 48));
        m.put("heat_recovery", new MachineAudioProfile(SoundFamily.BOILER_FIRE, 0.11F, 1.12F, 50));
        m.put("combustion_generator", new MachineAudioProfile(SoundFamily.ENGINE_PISTON, 0.14F, 1.00F, 36));
        m.put("pressure_recovery", new MachineAudioProfile(SoundFamily.STEAM_TURBINE, 0.13F, 1.00F, 42));
        m.put("flywheel", new MachineAudioProfile(SoundFamily.STEAM_TURBINE, 0.12F, 1.00F, 44));
        m.put("nuclear_generator", new MachineAudioProfile(SoundFamily.NUCLEAR_CORE, 0.14F, 1.00F, 56));
        m.put("regen_generator", new MachineAudioProfile(SoundFamily.NUCLEAR_CORE, 0.12F, 1.15F, 48));
        m.put("singularity_reactor", new MachineAudioProfile(SoundFamily.SINGULARITY_PULSE, 0.16F, 1.00F, 56));
        m.put("phase_reactor", new MachineAudioProfile(SoundFamily.SINGULARITY_PULSE, 0.18F, 1.12F, 32));

        // Processing & Production
        m.put("metal_press", new MachineAudioProfile(SoundFamily.PRESS_IMPACT, 0.26F, 1.00F, 20));
        m.put("crusher", new MachineAudioProfile(SoundFamily.CRUSHER_JAW, 0.20F, 1.00F, 24));
        m.put("fast_crusher", new MachineAudioProfile(SoundFamily.CRUSHER_JAW, 0.20F, 1.04F, 24));
        m.put("precision_crusher", new MachineAudioProfile(SoundFamily.CRUSHER_JAW, 0.20F, 0.96F, 24));
        m.put("electric_furnace", new MachineAudioProfile(SoundFamily.FURNACE_ARC, 0.14F, 1.00F, 40));
        m.put("alloy_furnace", new MachineAudioProfile(SoundFamily.FURNACE_ARC, 0.14F, 0.96F, 40));
        m.put("electrolyzer", new MachineAudioProfile(SoundFamily.ELECTROLYZER_BUBBLE, 0.17F, 1.00F, 32));
        m.put("seed_cultivator", new MachineAudioProfile(SoundFamily.ELECTROLYZER_BUBBLE, 0.14F, 1.10F, 40));
        m.put("bio_feeder", new MachineAudioProfile(SoundFamily.ELECTROLYZER_BUBBLE, 0.14F, 1.10F, 40));
        m.put("assembler", new MachineAudioProfile(SoundFamily.ASSEMBLER_SERVO, 0.21F, 1.00F, 24));
        m.put("crafter", new MachineAudioProfile(SoundFamily.ASSEMBLER_SERVO, 0.18F, 1.00F, 28));
        m.put("auto_builder", new MachineAudioProfile(SoundFamily.ASSEMBLER_SERVO, 0.18F, 1.00F, 28));

        // Mining & High-Tier Synthesis
        m.put("rotary_drill", new MachineAudioProfile(SoundFamily.DRILL_BORE, 0.20F, 1.00F, 24));
        m.put("quarry", new MachineAudioProfile(SoundFamily.DRILL_BORE, 0.19F, 0.88F, 28));
        m.put("large_quarry", new MachineAudioProfile(SoundFamily.DRILL_BORE, 0.19F, 0.88F, 28));
        m.put("quantum_compressor", new MachineAudioProfile(SoundFamily.COMPRESSOR_SLAM, 0.25F, 1.00F, 22));
        m.put("reconstructor", new MachineAudioProfile(SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30));
        m.put("matter_amplifier", new MachineAudioProfile(SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30));
        m.put("ore_synthesizer", new MachineAudioProfile(SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30));

        // Agriculture, Fluid & Airflow
        m.put("crop_harvester", new MachineAudioProfile(SoundFamily.HARVESTER_BLADE, 0.19F, 1.00F, 26));
        m.put("tree_harvester", new MachineAudioProfile(SoundFamily.HARVESTER_BLADE, 0.19F, 0.94F, 26));
        m.put("ranch_harvester", new MachineAudioProfile(SoundFamily.HARVESTER_BLADE, 0.16F, 1.00F, 34));
        m.put("fluid_pump", new MachineAudioProfile(SoundFamily.WINCH_REEL, 0.18F, 1.00F, 20));
        m.put("auto_fisher", new MachineAudioProfile(SoundFamily.WINCH_REEL, 0.16F, 1.00F, 36));
        m.put("mob_fan", new MachineAudioProfile(SoundFamily.FAN_AIRFLOW, 0.13F, 1.00F, 40));

        // Quantum Storage, Transporters & Sci-Fi Fields
        m.put("quantum_storage_core", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44));
        m.put("quantum_importer", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44));
        m.put("quantum_exporter", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44));
        m.put("basic_item_transporter", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.15F, 1.00F, 38));
        m.put("advanced_item_transporter", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.15F, 1.02F, 38));
        m.put("quantum_transporter", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.15F, 1.05F, 38));
        m.put("quantum_transporter_array", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.15F, 1.08F, 38));
        m.put("warp_pad", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56));
        m.put("chunk_loader", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56));
        m.put("spawn_suppressor", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56));
        m.put("wireless_charger", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56));
        m.put("growth_accelerator", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42));
        m.put("xp_collector", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42));
        m.put("item_vacuum", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42));
        m.put("suit_modifier", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48));
        m.put("climate_controller", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48));
        m.put("mob_duplicator", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48));
        m.put("laser_turret", new MachineAudioProfile(SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48));
        PROFILES = Collections.unmodifiableMap(m);
    }

    /** Passive / display / logistics / casing blocks that MUST remain completely silent. */
    public static final Set<String> SILENT_BLOCKS = Set.of(
            "solar_generator",
            "energy_buffer",
            "energy_buffer_mk2",
            "energy_buffer_mk3",
            "factory_monitor",
            "item_distributor",
            "cable_basic",
            "cable_improved",
            "cable_advanced",
            "cable_ultimate",
            "item_pipe",
            "improved_item_pipe",
            "advanced_item_pipe",
            "ultimate_item_pipe",
            "fluid_pipe",
            "hyper_energy_conduit",
            "hyper_item_conduit",
            "reactor_casing",
            "reactor_glass",
            "reactor_coolant_block",
            "reactor_port",
            "advanced_transporter_casing",
            "quantum_transporter_casing",
            "quantum_transporter_array_casing",
            "conveyor"
    );

    private FactoryAudioLogic() {}

    public static Map<String, MachineAudioProfile> allProfiles() {
        return PROFILES;
    }

    /** Returns the assigned audio profile for a machine block ID, or {@code null} if silent. */
    public static MachineAudioProfile profileFor(String blockId) {
        if (blockId == null || SILENT_BLOCKS.contains(blockId)) {
            return null;
        }
        return PROFILES.get(blockId);
    }

    public static boolean isSilentBlock(String blockId) {
        return blockId == null || SILENT_BLOCKS.contains(blockId) || !PROFILES.containsKey(blockId);
    }

    /** Charge-linked pitch for the kinetic flywheel ({@code 0.85..1.20}). */
    public static float flywheelPitch(float chargeRatio) {
        float c = Float.isNaN(chargeRatio) ? 0.0F : Math.max(0.0F, Math.min(1.0F, chargeRatio));
        return 0.85F + 0.35F * c;
    }

    /** True only when {@code distSq} is non-negative and within {@link #MAX_AUDIBLE_DISTANCE} (12m). */
    public static boolean isWithinAudibleRange(double distSq) {
        return !Double.isNaN(distSq) && distSq >= 0.0D && distSq <= MAX_AUDIBLE_DIST_SQ;
    }

    /** Smooth distance-attenuated playback volume; returns {@code 0.0F} beyond 12 blocks. */
    public static float attenuatedVolume(float baseVolume, double distSq) {
        if (!isWithinAudibleRange(distSq) || baseVolume <= 0.0F) {
            return 0.0F;
        }
        double ratio = Math.sqrt(distSq) / MAX_AUDIBLE_DISTANCE;
        double falloff = Math.max(0.0D, 1.0D - ratio * 0.85D);
        return (float) (baseVolume * falloff);
    }

    public static float attenuatedVolume(SoundFamily family, double distSq) {
        if (family == null) {
            return 0.0F;
        }
        return attenuatedVolume(family.baseVolume(), distSq);
    }

    /** Subtle pitch variation around {@code basePitch} (±4% jitter). */
    public static float variedPitch(float basePitch, float random01) {
        float clamped = Math.max(0.0F, Math.min(1.0F, random01));
        float jitter = MIN_PITCH_JITTER + (MAX_PITCH_JITTER - MIN_PITCH_JITTER) * clamped;
        return basePitch * jitter;
    }

    public static float variedPitch(float random01) {
        return variedPitch(1.0F, random01);
    }

    public static float wrap01(float phase) {
        if (Float.isNaN(phase) || Float.isInfinite(phase)) {
            return 0.0F;
        }
        float mod = phase % 1.0F;
        return mod < 0.0F ? mod + 1.0F : mod;
    }

    /**
     * Wrap-aware phase crossing detector so BER motion events trigger on the exact frame
     * {@code triggerPoint01} is passed (even across the 1.0 -&gt; 0.0 wrap boundary).
     */
    public static boolean crossedPhaseTrigger(float prevPhase01, float currPhase01, float triggerPoint01) {
        float p0 = wrap01(prevPhase01);
        float p1 = wrap01(currPhase01);
        float trg = wrap01(triggerPoint01);
        if (p0 == p1) {
            return false;
        }
        if (p0 < p1) {
            return p0 < trg && trg <= p1;
        }
        return p0 < trg || trg <= p1;
    }

    /**
     * Stateful pure-Java playback budget guard shared across the entire client factory:
     * enforces strict 12m cutoff, per-family cooldowns, and {@link #MAX_SOUNDS_PER_TICK} (= 2).
     */
    public static final class BudgetTracker {
        private final long[] lastFamilyTick = new long[SoundFamily.values().length];
        private long activeTick = Long.MIN_VALUE;
        private int soundsInActiveTick = 0;

        public BudgetTracker() {
            reset();
        }

        public void reset() {
            for (int i = 0; i < lastFamilyTick.length; i++) {
                lastFamilyTick[i] = Long.MIN_VALUE / 4;
            }
            activeTick = Long.MIN_VALUE;
            soundsInActiveTick = 0;
        }

        public boolean tryAcquire(SoundFamily family, long currentTick, double distSq) {
            if (family == null) {
                return false;
            }
            return tryAcquire(family, family.cooldownTicks(), currentTick, distSq);
        }

        public boolean tryAcquire(SoundFamily family, int cooldownTicks, long currentTick, double distSq) {
            if (family == null || !isWithinAudibleRange(distSq)) {
                return false;
            }
            if (currentTick != activeTick) {
                activeTick = currentTick;
                soundsInActiveTick = 0;
            }
            if (soundsInActiveTick >= MAX_SOUNDS_PER_TICK) {
                return false;
            }
            int ord = family.ordinal();
            int cd = Math.max(1, cooldownTicks);
            if (currentTick - lastFamilyTick[ord] < cd) {
                return false;
            }
            lastFamilyTick[ord] = currentTick;
            soundsInActiveTick++;
            return true;
        }

        public int soundsInCurrentTick() {
            return soundsInActiveTick;
        }
    }
}
