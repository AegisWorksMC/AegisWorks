package com.industrialcore.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests validating custom remastered Ogg Vorbis audio assets (including Vorbis
 * sample rate and Stereo/Mono channel layout), {@code sounds.json}, subtitle localization parity,
 * and dedicated-server safety of {@code ModSounds}.
 */
class ModSoundsTest {
    private static final String MOD_ID = "industrialcore";
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path SOUNDS_JSON = ASSETS.resolve("sounds.json");
    private static final Path SOUNDS_DIR = ASSETS.resolve("sounds");
    private static final Path MOD_SOUNDS_SRC = Path.of(
            "src/main/java/com/industrialcore/registry/ModSounds.java");
    private static final Pattern REGISTER_CALL = Pattern.compile("register\\(\"([a-z0-9_]+)\"\\)");
    /** 3D world-spatializable machine &amp; weapon SEs remain Mono per Minecraft/OpenAL attenuation rules. */
    private static final Set<String> MONO_WORLD_SOUNDS = Set.of(
            "machine_tick_note", "machine_jackpot",
            "world_gen_thermal", "world_gen_kinetic", "world_gen_reactor", "world_machine_hum",
            "weapon_laser_beam", "weapon_laser_loop_light", "weapon_laser_loop_heavy", "weapon_laser_hit_sizzle",
            "weapon_railgun_fire", "weapon_blade_slash", "weapon_blade_ignite", "weapon_blade_dash",
            "weapon_bow_fire", "weapon_lockon",
            "machine_wind_blade", "machine_hydro_turbine", "machine_boiler_fire",
            "machine_engine_piston", "machine_steam_turbine", "machine_nuclear_core",
            "machine_singularity_pulse", "machine_press_impact", "machine_crusher_jaw",
            "machine_furnace_arc", "machine_electrolyzer_bubble", "machine_assembler_servo",
            "machine_drill_bore", "machine_compressor_slam", "machine_harvester_blade",
            "machine_winch_reel", "machine_fan_airflow", "machine_quantum_chime",
            "rift_portal_horn", "rift_stalker_blink", "void_sentinel_cannon",
            "phase_juggernaut_slam", "rift_shield_deflect", "sovereign_roar_overdrive",
            "sovereign_defeat_collapse", "rift_raid_victory_fanfare"
    );

    private static List<String> registeredIdsFromSource() throws IOException {
        String src = Files.readString(MOD_SOUNDS_SRC);
        Matcher m = REGISTER_CALL.matcher(src);
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    @Test
    void everyRegisteredSoundIdExistsInSoundsJsonWithValidOggVorbisFile() throws IOException {
        assertTrue(Files.isRegularFile(SOUNDS_JSON), "sounds.json must exist");
        JsonObject root = JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();

        List<String> registered = registeredIdsFromSource();
        assertEquals(59, registered.size(), "Expected 59 custom sound events in ModSounds");
        Set<String> expectedIds = new HashSet<>(registered);
        assertEquals(59, expectedIds.size(), "Duplicate sound ID in ModSounds");
        assertEquals(expectedIds, root.keySet(),
                "ModSounds registered IDs and sounds.json keys must match 1-to-1");

        for (String id : registered) {
            JsonObject entry = root.getAsJsonObject(id);
            assertTrue(entry.has("sounds"), id + " missing sounds array");
            JsonArray arr = entry.getAsJsonArray("sounds");
            assertFalse(arr.isEmpty(), id + " sounds array must not be empty");

            for (JsonElement elem : arr) {
                String ref = elem.isJsonObject()
                        ? elem.getAsJsonObject().get("name").getAsString()
                        : elem.getAsString();
                String prefix = MOD_ID + ":";
                assertTrue(ref.startsWith(prefix),
                        "Sound reference must use industrialcore namespace: " + ref);
                String relPath = ref.substring(prefix.length());
                Path ogg = SOUNDS_DIR.resolve(relPath + ".ogg");
                assertTrue(Files.isRegularFile(ogg), "Missing .ogg asset for " + id + ": " + ogg);

                byte[] bytes = Files.readAllBytes(ogg);
                assertTrue(bytes.length > 512, "Empty or truncated .ogg asset: " + ogg);
                assertEquals('O', (char) bytes[0], ogg + " missing OggS magic[0]");
                assertEquals('g', (char) bytes[1], ogg + " missing OggS magic[1]");
                assertEquals('g', (char) bytes[2], ogg + " missing OggS magic[2]");
                assertEquals('S', (char) bytes[3], ogg + " missing OggS magic[3]");

                // Verify Vorbis identification header ("\x01vorbis") and exact channel / sample rate
                String headerSlice = new String(bytes, 0, Math.min(bytes.length, 96),
                        StandardCharsets.ISO_8859_1);
                int vorbisIdx = headerSlice.indexOf("\u0001vorbis");
                assertTrue(vorbisIdx >= 0, ogg + " is not an Ogg Vorbis audio stream");
                // Vorbis ID header layout after 7-byte "\x01vorbis":
                //   [+7..+10] vorbis_version (uint32 LE == 0)
                //   [+11]     audio_channels (uint8: 1=Mono, 2=Stereo)
                //   [+12..+15] audio_sample_rate (uint32 LE == 44100)
                int idBase = vorbisIdx + 7;
                assertTrue(bytes.length >= idBase + 9, ogg + " truncated Vorbis ID header");
                int version = ByteBuffer.wrap(bytes, idBase, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int channels = bytes[idBase + 4] & 0xFF;
                int sampleRate = ByteBuffer.wrap(bytes, idBase + 5, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int expectedChannels = MONO_WORLD_SOUNDS.contains(id) ? 1 : 2;
                assertEquals(0, version, ogg + " unexpected Vorbis version");
                assertEquals(expectedChannels, channels,
                        ogg + " expected " + (expectedChannels == 1 ? "Mono (1ch)" : "Stereo (2ch)"));
                assertEquals(44100, sampleRate, ogg + " expected 44100 Hz sample rate");
            }
        }
    }

    @Test
    void allSoundSubtitlesExistAndAreNonBlankInBothLocales() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();
        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json")))
                .getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json")))
                .getAsJsonObject();

        for (String id : registeredIdsFromSource()) {
            JsonObject entry = root.getAsJsonObject(id);
            assertTrue(entry.has("subtitle"), id + " missing subtitle key in sounds.json");
            String subKey = entry.get("subtitle").getAsString();
            assertTrue(subKey.startsWith("subtitles.industrialcore."),
                    "Unexpected subtitle key namespace: " + subKey);
            assertTrue(ja.has(subKey) && !ja.get(subKey).getAsString().isBlank(),
                    "ja_jp.json missing subtitle " + subKey);
            assertTrue(en.has(subKey) && !en.get(subKey).getAsString().isBlank(),
                    "en_us.json missing subtitle " + subKey);
        }
    }

    /** Pure-Java Ogg page scanner returning EOS granule_position (exact PCM frame count). */
    private static long parseOggEosGranulePosition(byte[] bytes, String id) {
        int pos = 0;
        boolean sawBos = false;
        boolean sawEos = false;
        long eosGranule = -1L;
        while (pos + 27 <= bytes.length) {
            assertEquals('O', (char) bytes[pos], id + " OggS[0] at " + pos);
            assertEquals('g', (char) bytes[pos + 1], id + " OggS[1] at " + pos);
            assertEquals('g', (char) bytes[pos + 2], id + " OggS[2] at " + pos);
            assertEquals('S', (char) bytes[pos + 3], id + " OggS[3] at " + pos);
            int headerType = bytes[pos + 5] & 0xFF;
            long granule = ByteBuffer.wrap(bytes, pos + 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
            int segCount = bytes[pos + 26] & 0xFF;
            assertTrue(pos + 27 + segCount <= bytes.length, id + " truncated Ogg segment table");
            int bodySize = 0;
            for (int i = 0; i < segCount; i++) {
                bodySize += bytes[pos + 27 + i] & 0xFF;
            }
            if ((headerType & 0x02) != 0) {
                sawBos = true;
            }
            if ((headerType & 0x04) != 0) {
                sawEos = true;
                eosGranule = granule;
            }
            pos += 27 + segCount + bodySize;
        }
        assertTrue(sawBos, id + " missing Ogg BOS page");
        assertTrue(sawEos, id + " missing Ogg EOS page");
        assertTrue(eosGranule > 0L, id + " invalid EOS granule position: " + eosGranule);
        return eosGranule;
    }

    /** Decodes Ogg Vorbis to interleaved float32 PCM via ffmpeg for clipping &amp; zero-crossing checks. */
    private static float[] decodeOggFloatPcm(Path ogg, int channels) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(
                "ffmpeg", "-v", "error", "-i", ogg.toAbsolutePath().toString(),
                "-f", "f32le", "-ar", "44100", "-ac", Integer.toString(channels), "-")
                .redirectErrorStream(false)
                .start();
        byte[] raw;
        try (InputStream in = proc.getInputStream()) {
            raw = in.readAllBytes();
        }
        byte[] err;
        try (InputStream errIn = proc.getErrorStream()) {
            err = errIn.readAllBytes();
        }
        int exit = proc.waitFor();
        assertEquals(0, exit, "ffmpeg failed decoding " + ogg + ": "
                + new String(err, StandardCharsets.UTF_8));
        assertTrue(raw.length >= 4 * channels * 1000, "Decoded PCM unexpectedly short for " + ogg);
        int count = raw.length / 4;
        float[] pcm = new float[count];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            pcm[i] = buf.getFloat();
        }
        return pcm;
    }

    @Test
    void allRefinedAndNewOggAssetsDecodeCleanlyWithZeroClippingAndZeroCrossing() throws Exception {
        Map<String, double[]> durationBoundsSec = Map.ofEntries(
                Map.entry("machine_complete", new double[]{0.18, 0.22}),
                Map.entry("machine_extract", new double[]{0.12, 0.18}),
                Map.entry("machine_streak_up", new double[]{0.55, 0.65}),
                Map.entry("gen_thermal_hum", new double[]{1.90, 2.10}),
                Map.entry("gen_kinetic_hum", new double[]{1.90, 2.10}),
                Map.entry("gen_reactor_hum", new double[]{1.90, 2.10}),
                Map.entry("gen_power_pulse", new double[]{0.35, 0.55}),
                Map.entry("world_gen_thermal", new double[]{1.90, 2.10}),
                Map.entry("world_gen_kinetic", new double[]{1.90, 2.10}),
                Map.entry("world_gen_reactor", new double[]{1.90, 2.10}),
                Map.entry("world_machine_hum", new double[]{1.90, 2.10}),
                Map.entry("weapon_laser_beam", new double[]{0.28, 0.45}),
                Map.entry("weapon_laser_loop_light", new double[]{1.70, 1.90}),
                Map.entry("weapon_laser_loop_heavy", new double[]{1.70, 1.90}),
                Map.entry("weapon_laser_hit_sizzle", new double[]{0.90, 1.10}),
                Map.entry("weapon_railgun_fire", new double[]{2.00, 2.40}),
                Map.entry("weapon_blade_slash", new double[]{0.25, 0.45}),
                Map.entry("weapon_blade_ignite", new double[]{0.42, 0.62}),
                Map.entry("weapon_blade_dash", new double[]{0.32, 0.52}),
                Map.entry("weapon_bow_fire", new double[]{0.25, 0.45}),
                Map.entry("weapon_lockon", new double[]{0.10, 0.24})
        );

        for (String id : registeredIdsFromSource()) {
            Path ogg = SOUNDS_DIR.resolve(id + ".ogg");
            int expectedChannels = MONO_WORLD_SOUNDS.contains(id) ? 1 : 2;
            byte[] bytes = Files.readAllBytes(ogg);
            long framesFromOgg = parseOggEosGranulePosition(bytes, id);

            float[] pcm = decodeOggFloatPcm(ogg, expectedChannels);
            float maxAbs = 0.0f;
            for (float s : pcm) {
                float a = Math.abs(s);
                if (a > maxAbs) {
                    maxAbs = a;
                }
            }
            assertTrue(maxAbs > 0.05f, id + " decoded audio is nearly silent: " + maxAbs);
            assertTrue(maxAbs <= 0.99f, id + " clipped above 0.99 full scale: " + maxAbs);

            if (durationBoundsSec.containsKey(id)) {
                double[] bounds = durationBoundsSec.get(id);
                double durSec = framesFromOgg / 44100.0;
                assertTrue(durSec >= bounds[0] && durSec <= bounds[1],
                        id + " duration " + durSec + "s outside [" + bounds[0] + ", " + bounds[1] + "]");
                for (int ch = 0; ch < expectedChannels; ch++) {
                    float first = Math.abs(pcm[ch]);
                    float last = Math.abs(pcm[pcm.length - expectedChannels + ch]);
                    assertTrue(first < 0.02f, id + " ch" + ch + " non-zero start edge: " + first);
                    assertTrue(last < 0.02f, id + " ch" + ch + " non-zero end edge: " + last);
                }
            }
        }
    }

    @Test
    void modSoundsHasNoClientOnlyImportsForDedicatedServerSafety() throws IOException {
        String src = Files.readString(MOD_SOUNDS_SRC);
        assertFalse(src.contains("net.minecraft.client"),
                "ModSounds must never import net.minecraft.client");
        assertFalse(src.contains("com.industrialcore.client"),
                "ModSounds must never import com.industrialcore.client");
    }
}
