package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.FactoryAudioLogic;
import com.industrialcore.machine.FactoryAudioLogic.BudgetTracker;
import com.industrialcore.machine.FactoryAudioLogic.MachineAudioProfile;
import com.industrialcore.machine.FactoryAudioLogic.SoundFamily;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 112 pure-Java acoustic quality gate:
 * <ul>
 *   <li>All 18 resynthesized factory SFX .ogg assets are strictly 1ch Mono / 44.1kHz Ogg Vorbis.</li>
 *   <li>Decoded PCM waveforms have zero clipping (peak &lt;= 0.50), 30ms zero-crossing fades,
 *       and suppressed &gt;1.2kHz high-frequency noise energy (zero hiss/static).</li>
 *   <li>Deprecated Phase 111 noisy OGG assets are removed.</li>
 *   <li>Every active machine in the mod maps to its exact assigned SoundFamily/volume/pitch/cooldown,
 *       and all 8 passive/display/logistics/casing categories remain strictly silent.</li>
 *   <li>Strict 12-block cutoff and 2-sound/tick global budget cap are enforced.</li>
 * </ul>
 */
class Phase112AudioPolishTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/industrialcore");
    private static final Path SOUNDS_DIR = ROOT.resolve("sounds");

    private static final List<String> PHASE_112_SOUNDS = List.of(
            "machine_wind_blade",
            "machine_hydro_turbine",
            "machine_boiler_fire",
            "machine_engine_piston",
            "machine_steam_turbine",
            "machine_nuclear_core",
            "machine_singularity_pulse",
            "machine_press_impact",
            "machine_crusher_jaw",
            "machine_furnace_arc",
            "machine_electrolyzer_bubble",
            "machine_assembler_servo",
            "machine_drill_bore",
            "machine_compressor_slam",
            "machine_harvester_blade",
            "machine_winch_reel",
            "machine_fan_airflow",
            "machine_quantum_chime"
    );

    private static final List<String> DEPRECATED_PHASE_111 = List.of(
            "machine_crusher_grind",
            "machine_furnace_hum",
            "machine_assembler_weld",
            "machine_pump_stroke",
            "machine_compressor_pulse",
            "machine_harvester_shear",
            "machine_turbine_whoosh",
            "machine_reactor_hum"
    );

    @Test
    void allEighteenCleanFactorySfxAreMono44100NoiseFreeAndPeakCapped() throws Exception {
        JsonObject soundsJson = JsonParser.parseString(
                Files.readString(ROOT.resolve("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject jaJp = JsonParser.parseString(
                Files.readString(ROOT.resolve("lang/ja_jp.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject enUs = JsonParser.parseString(
                Files.readString(ROOT.resolve("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(18, SoundFamily.values().length);
        for (String old : DEPRECATED_PHASE_111) {
            assertFalse(Files.exists(SOUNDS_DIR.resolve(old + ".ogg")), "Deprecated OGG still present: " + old);
            assertFalse(soundsJson.has(old), "Deprecated sounds.json key still present: " + old);
        }

        Set<String> tonalBellOrBubble = Set.of(
                "machine_electrolyzer_bubble", "machine_assembler_servo",
                "machine_harvester_blade", "machine_quantum_chime");

        for (String id : PHASE_112_SOUNDS) {
            Path ogg = SOUNDS_DIR.resolve(id + ".ogg");
            assertTrue(Files.exists(ogg), "Missing Phase 112 OGG: " + ogg);
            byte[] bytes = Files.readAllBytes(ogg);
            assertTrue(bytes.length > 512, "OGG unexpectedly tiny: " + id);
            assertEquals('O', bytes[0]);
            assertEquals('g', bytes[1]);
            assertEquals('g', bytes[2]);
            assertEquals('S', bytes[3]);

            int vorbisPos = indexOfVorbisIdHeader(bytes);
            assertTrue(vorbisPos > 0, "Missing Vorbis ID header in " + id);
            int channels = bytes[vorbisPos + 11] & 0xFF;
            int sampleRate = ByteBuffer.wrap(bytes, vorbisPos + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertEquals(1, channels, id + " must be strictly Mono (1ch)");
            assertEquals(44100, sampleRate, id + " must be 44.1kHz");

            assertTrue(soundsJson.has(id), "Missing sounds.json entry: " + id);
            String subKey = "subtitles.industrialcore." + id;
            assertTrue(jaJp.has(subKey) && !jaJp.get(subKey).getAsString().isBlank(), "Missing ja_jp: " + subKey);
            assertTrue(enUs.has(subKey) && !enUs.get(subKey).getAsString().isBlank(), "Missing en_us: " + subKey);

            // Decode PCM and enforce strict peak <= 0.50, zero-cross edges, and suppressed >1.2kHz noise
            float[] pcm = decodeMonoFloatPcm(ogg);
            float peak = 0.0F;
            for (float s : pcm) {
                float a = Math.abs(s);
                if (a > peak) peak = a;
            }
            assertTrue(peak >= 0.15F && peak <= 0.50F,
                    id + " decoded peak must stay strictly in [0.15, 0.50], got " + peak);
            assertTrue(Math.abs(pcm[0]) < 0.02F, id + " non-zero start fade edge: " + pcm[0]);
            assertTrue(Math.abs(pcm[pcm.length - 1]) < 0.02F, id + " non-zero end fade edge: " + pcm[pcm.length - 1]);

            double hfRatio = highFreqEnergyRatio1200Hz(pcm);
            double limit = tonalBellOrBubble.contains(id) ? 0.06D : 0.01D;
            assertTrue(hfRatio < limit,
                    id + " high-frequency (>1.2kHz) noise ratio " + hfRatio + " exceeded " + limit);
        }
    }

    @Test
    void everyMachineMapsToAssignedCleanVoiceAndAllPassiveBlocksStaySilent() {
        // Generators & Energy Recovery
        assertMapping("wind_generator", SoundFamily.WIND_BLADE, 0.13F, 1.00F, 48);
        assertMapping("hydro_generator", SoundFamily.HYDRO_TURBINE, 0.13F, 1.00F, 44);
        assertMapping("thermal_generator", SoundFamily.BOILER_FIRE, 0.12F, 1.00F, 44);
        assertMapping("geothermal_generator", SoundFamily.BOILER_FIRE, 0.12F, 0.85F, 48);
        assertMapping("heat_recovery", SoundFamily.BOILER_FIRE, 0.11F, 1.12F, 50);
        assertMapping("combustion_generator", SoundFamily.ENGINE_PISTON, 0.14F, 1.00F, 36);
        assertMapping("pressure_recovery", SoundFamily.STEAM_TURBINE, 0.13F, 1.00F, 42);
        assertMapping("flywheel", SoundFamily.STEAM_TURBINE, 0.12F, 1.00F, 44);
        assertEquals(0.85F, FactoryAudioLogic.flywheelPitch(0.0F), 1e-4F);
        assertEquals(1.20F, FactoryAudioLogic.flywheelPitch(1.0F), 1e-4F);
        assertMapping("nuclear_generator", SoundFamily.NUCLEAR_CORE, 0.14F, 1.00F, 56);
        assertMapping("regen_generator", SoundFamily.NUCLEAR_CORE, 0.12F, 1.15F, 48);
        assertMapping("singularity_reactor", SoundFamily.SINGULARITY_PULSE, 0.16F, 1.00F, 56);
        assertMapping("phase_reactor", SoundFamily.SINGULARITY_PULSE, 0.18F, 1.12F, 32);

        // Processing, Mining & Synthesis
        assertMapping("metal_press", SoundFamily.PRESS_IMPACT, 0.26F, 1.00F, 20);
        assertMapping("crusher", SoundFamily.CRUSHER_JAW, 0.20F, 1.00F, 24);
        assertMapping("fast_crusher", SoundFamily.CRUSHER_JAW, 0.20F, 1.04F, 24);
        assertMapping("precision_crusher", SoundFamily.CRUSHER_JAW, 0.20F, 0.96F, 24);
        assertMapping("electric_furnace", SoundFamily.FURNACE_ARC, 0.14F, 1.00F, 40);
        assertMapping("alloy_furnace", SoundFamily.FURNACE_ARC, 0.14F, 0.96F, 40);
        assertMapping("electrolyzer", SoundFamily.ELECTROLYZER_BUBBLE, 0.17F, 1.00F, 32);
        assertMapping("seed_cultivator", SoundFamily.ELECTROLYZER_BUBBLE, 0.14F, 1.10F, 40);
        assertMapping("bio_feeder", SoundFamily.ELECTROLYZER_BUBBLE, 0.14F, 1.10F, 40);
        assertMapping("assembler", SoundFamily.ASSEMBLER_SERVO, 0.21F, 1.00F, 24);
        assertMapping("crafter", SoundFamily.ASSEMBLER_SERVO, 0.18F, 1.00F, 28);
        assertMapping("auto_builder", SoundFamily.ASSEMBLER_SERVO, 0.18F, 1.00F, 28);
        assertMapping("rotary_drill", SoundFamily.DRILL_BORE, 0.20F, 1.00F, 24);
        assertMapping("quarry", SoundFamily.DRILL_BORE, 0.19F, 0.88F, 28);
        assertMapping("large_quarry", SoundFamily.DRILL_BORE, 0.19F, 0.88F, 28);
        assertMapping("quantum_compressor", SoundFamily.COMPRESSOR_SLAM, 0.25F, 1.00F, 22);
        assertMapping("reconstructor", SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30);
        assertMapping("matter_amplifier", SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30);
        assertMapping("ore_synthesizer", SoundFamily.COMPRESSOR_SLAM, 0.20F, 1.00F, 30);

        // Agriculture, Fluid, Airflow & Sci-Fi Fields
        assertMapping("crop_harvester", SoundFamily.HARVESTER_BLADE, 0.19F, 1.00F, 26);
        assertMapping("tree_harvester", SoundFamily.HARVESTER_BLADE, 0.19F, 0.94F, 26);
        assertMapping("ranch_harvester", SoundFamily.HARVESTER_BLADE, 0.16F, 1.00F, 34);
        assertMapping("fluid_pump", SoundFamily.WINCH_REEL, 0.18F, 1.00F, 20);
        assertMapping("auto_fisher", SoundFamily.WINCH_REEL, 0.16F, 1.00F, 36);
        assertMapping("mob_fan", SoundFamily.FAN_AIRFLOW, 0.13F, 1.00F, 40);
        assertMapping("quantum_storage_core", SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44);
        assertMapping("quantum_importer", SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44);
        assertMapping("quantum_exporter", SoundFamily.QUANTUM_CHIME, 0.14F, 1.00F, 44);
        assertMapping("basic_item_transporter", SoundFamily.QUANTUM_CHIME, 0.15F, 1.00F, 38);
        assertMapping("advanced_item_transporter", SoundFamily.QUANTUM_CHIME, 0.15F, 1.02F, 38);
        assertMapping("quantum_transporter", SoundFamily.QUANTUM_CHIME, 0.15F, 1.05F, 38);
        assertMapping("quantum_transporter_array", SoundFamily.QUANTUM_CHIME, 0.15F, 1.08F, 38);
        assertMapping("warp_pad", SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56);
        assertMapping("chunk_loader", SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56);
        assertMapping("spawn_suppressor", SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56);
        assertMapping("wireless_charger", SoundFamily.QUANTUM_CHIME, 0.12F, 1.00F, 56);
        assertMapping("growth_accelerator", SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42);
        assertMapping("xp_collector", SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42);
        assertMapping("item_vacuum", SoundFamily.QUANTUM_CHIME, 0.13F, 1.15F, 42);
        assertMapping("suit_modifier", SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48);
        assertMapping("climate_controller", SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48);
        assertMapping("mob_duplicator", SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48);
        assertMapping("laser_turret", SoundFamily.QUANTUM_CHIME, 0.13F, 1.00F, 48);

        // Silent passive blocks MUST produce null profile & never emit audio
        List<String> mustBeSilent = List.of(
                "solar_generator",
                "energy_buffer", "energy_buffer_mk2", "energy_buffer_mk3",
                "factory_monitor", "item_distributor",
                "cable_basic", "cable_improved", "cable_advanced", "cable_ultimate",
                "item_pipe", "improved_item_pipe", "advanced_item_pipe", "ultimate_item_pipe",
                "fluid_pipe", "hyper_energy_conduit", "hyper_item_conduit",
                "reactor_casing", "reactor_glass", "reactor_coolant_block", "reactor_port",
                "conveyor"
        );
        for (String silentId : mustBeSilent) {
            assertNull(FactoryAudioLogic.profileFor(silentId), silentId + " must stay completely silent");
            assertTrue(FactoryAudioLogic.isSilentBlock(silentId), silentId + " must report silent");
        }
    }

    @Test
    void strict12BlockCutoffAnd2SoundsPerTickBudgetEnforced() {
        assertEquals(12.0D, FactoryAudioLogic.MAX_AUDIBLE_DISTANCE, 1e-6D);
        assertEquals(144.0D, FactoryAudioLogic.MAX_AUDIBLE_DIST_SQ, 1e-6D);
        assertEquals(2, FactoryAudioLogic.MAX_SOUNDS_PER_TICK);

        assertTrue(FactoryAudioLogic.isWithinAudibleRange(0.0D));
        assertTrue(FactoryAudioLogic.isWithinAudibleRange(144.0D));
        assertFalse(FactoryAudioLogic.isWithinAudibleRange(144.2D));
        assertEquals(0.0F, FactoryAudioLogic.attenuatedVolume(SoundFamily.PRESS_IMPACT, 145.0D));
        assertTrue(FactoryAudioLogic.attenuatedVolume(SoundFamily.PRESS_IMPACT, 4.0D) > 0.15F);

        BudgetTracker tracker = new BudgetTracker();
        // 18 distinct voices in one tick -> strictly capped at 2
        int played = 0;
        for (SoundFamily f : SoundFamily.values()) {
            if (tracker.tryAcquire(f, 500L, 16.0D)) {
                played++;
            }
        }
        assertEquals(2, played, "Global per-tick sound cap must be strictly 2");

        // Beyond 12 blocks -> strictly rejected even on fresh tick
        assertFalse(tracker.tryAcquire(SoundFamily.WIND_BLADE, 600L, 160.0D));
    }

    private static void assertMapping(String blockId, SoundFamily expectedFamily,
                                      float expectedVol, float expectedPitch, int expectedCd) {
        MachineAudioProfile p = FactoryAudioLogic.profileFor(blockId);
        assertNotNull(p, "Missing audio mapping for " + blockId);
        assertEquals(expectedFamily, p.family(), blockId + " family mismatch");
        assertEquals(expectedVol, p.volume(), 1e-4F, blockId + " volume mismatch");
        assertEquals(expectedPitch, p.basePitch(), 1e-4F, blockId + " basePitch mismatch");
        assertEquals(expectedCd, p.cooldownTicks(), blockId + " cooldown mismatch");
    }

    private static int indexOfVorbisIdHeader(byte[] bytes) {
        for (int i = 0; i <= bytes.length - 16; i++) {
            if (bytes[i] == 0x01 && bytes[i + 1] == 'v' && bytes[i + 2] == 'o'
                    && bytes[i + 3] == 'r' && bytes[i + 4] == 'b' && bytes[i + 5] == 'i'
                    && bytes[i + 6] == 's') {
                return i;
            }
        }
        return -1;
    }

    private static float[] decodeMonoFloatPcm(Path ogg) throws Exception {
        Process proc = new ProcessBuilder(
                "ffmpeg", "-v", "error", "-i", ogg.toAbsolutePath().toString(),
                "-f", "f32le", "-ar", "44100", "-ac", "1", "-")
                .redirectErrorStream(false)
                .start();
        byte[] raw;
        try (InputStream in = proc.getInputStream()) {
            raw = in.readAllBytes();
        }
        int exit = proc.waitFor();
        assertEquals(0, exit, "ffmpeg failed decoding " + ogg);
        int count = raw.length / 4;
        float[] pcm = new float[count];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            pcm[i] = buf.getFloat();
        }
        return pcm;
    }

    /** 2nd-order Butterworth high-pass filter at 1200 Hz to measure high-frequency noise ratio. */
    private static double highFreqEnergyRatio1200Hz(float[] pcm) {
        double w0 = 2.0D * Math.PI * 1200.0D / 44100.0D;
        double cosW0 = Math.cos(w0);
        double sinW0 = Math.sin(w0);
        double alpha = sinW0 / (2.0D * 0.70710678D);
        double a0 = 1.0D + alpha;
        double b0 = ((1.0D + cosW0) * 0.5D) / a0;
        double b1 = (-(1.0D + cosW0)) / a0;
        double b2 = ((1.0D + cosW0) * 0.5D) / a0;
        double a1 = (-2.0D * cosW0) / a0;
        double a2 = (1.0D - alpha) / a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        double eTot = 0, eHi = 0;
        for (float s : pcm) {
            double x0 = s;
            double y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            eTot += x0 * x0;
            eHi += y0 * y0;
            x2 = x1; x1 = x0;
            y2 = y1; y1 = y0;
        }
        return eTot > 1e-12D ? (eHi / eTot) : 0.0D;
    }
}
