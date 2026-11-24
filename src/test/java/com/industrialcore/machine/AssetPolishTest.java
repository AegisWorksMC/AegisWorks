package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.client.GuiDopamine;
import com.industrialcore.client.SoundRhythm;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 76 Part 1, 3, 4 & 5 quality gates:
 * localization polish & consistency, all 376 textures color richness & dimensions,
 * SoundDirector pentatonic progression/telemetry, and GuiDopamine beat/effect mechanics.
 */
class AssetPolishTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final List<String> MINERALS = List.of(
            "copper", "iron", "gold", "astral_silver", "void_iron",
            "mithril", "orichalcum", "adamantite", "nebula", "quantum", "aether");
    private static final List<String> STAGES = List.of(
            "dust", "pure", "exposed", "washed", "sorted", "concentrate", "refined",
            "separated", "high_purity", "micronized", "crystallized", "homogenized",
            "compressed", "stabilized");

    private static JsonObject readLang(String file) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + file))).getAsJsonObject();
    }

    private static int countPlaceholders(String s) {
        int count = 0;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '%' && (s.charAt(i + 1) == 's' || s.charAt(i + 1) == 'd')) {
                count++;
            }
        }
        return count;
    }

    @Test
    void localizationKeysPlaceholdersAndOreStageTagsComplete() throws IOException {
        JsonObject ja = readLang("ja_jp.json");
        JsonObject en = readLang("en_us.json");
        assertEquals(1589, ja.size(), "ja_jp key count");
        assertEquals(ja.keySet(), en.keySet(), "ja_jp and en_us keysets must match");

        for (Map.Entry<String, JsonElement> e : ja.entrySet()) {
            String key = e.getKey();
            String jv = e.getValue().getAsString();
            String ev = en.get(key).getAsString();
            assertFalse(jv.isBlank(), "Empty ja_jp value for " + key);
            assertFalse(ev.isBlank(), "Empty en_us value for " + key);
            assertEquals(countPlaceholders(jv), countPlaceholders(ev),
                    "Placeholder mismatch on " + key);
        }

        // All 154 ore-chain items carry [I]..[XIV] stage badges in both locales
        String[] numerals = {"[I]", "[II]", "[III]", "[IV]", "[V]", "[VI]", "[VII]",
                "[VIII]", "[IX]", "[X]", "[XI]", "[XII]", "[XIII]", "[XIV]"};
        for (String m : MINERALS) {
            for (int i = 0; i < STAGES.size(); i++) {
                String st = STAGES.get(i);
                String path = (st.equals("exposed") || st.equals("pure"))
                        ? st + "_" + m : m + "_" + st;
                String key = "item.industrialcore." + path;
                assertTrue(ja.get(key).getAsString().contains(numerals[i]), key + " missing ja tag");
                assertTrue(en.get(key).getAsString().contains(numerals[i]), key + " missing en tag");
            }
        }
    }

    @Test
    void legacyInconsistentJapaneseTermsRemovedFromLocalization() throws IOException {
        String jaText = Files.readString(ASSETS.resolve("lang/ja_jp.json"));
        for (String banned : List.of("増幅筐体", "駆動ユニット", "信号ハーネス")) {
            assertFalse(jaText.contains(banned), "Banned legacy term still present: " + banned);
        }
    }

    @Test
    void all376TexturesMeetDimensionsAndItemColorRichness() throws IOException {
        List<Path> itemPngs;
        try (Stream<Path> s = Files.list(ASSETS.resolve("textures/item"))) {
            itemPngs = s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
        Set<String> hdItems32 = Set.of(
                "virtual_helmet.png", "virtual_chestplate.png", "virtual_leggings.png", "virtual_boots.png",
                "pulse_laser.png", "railgun.png", "quantum_blade.png", "energy_bow.png");
        assertEquals(249, itemPngs.size(), "Expected 249 item PNGs (248 through Phase 112 + 1 Phase 113 specialization_module)");
        int hdItemCount = 0;
        for (Path p : itemPngs) {
            BufferedImage img = ImageIO.read(p.toFile());
            String fn = p.getFileName().toString();
            int expectedDim = hdItems32.contains(fn) ? 32 : 16;
            if (expectedDim == 32) {
                hdItemCount++;
            }
            assertEquals(expectedDim, img.getWidth(), fn + " width");
            assertEquals(expectedDim, img.getHeight(), fn + " height");
            Set<Integer> cols = new HashSet<>();
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    if (((argb >>> 24) & 0xFF) >= 128) {
                        cols.add(argb & 0x00FFFFFF);
                    }
                }
            }
            assertTrue(cols.size() >= 6, fn + " too flat: " + cols.size() + " colors");
        }
        assertEquals(8, hdItemCount, "Expected exactly 8 HD 32x32 items (4 virtual armor + 4 electric weapons)");

        List<Path> blockPngs;
        try (Stream<Path> s = Files.list(ASSETS.resolve("textures/block"))) {
            blockPngs = s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
        assertEquals(315, blockPngs.size(), "Expected 315 block PNGs (288 through Phase 109 + 27 Phase 110 animated fronts & regen pulse)");
        for (Path p : blockPngs) {
            BufferedImage img = ImageIO.read(p.toFile());
            assertEquals(16, img.getWidth(), p.getFileName() + " width");
            Path mcmeta = p.resolveSibling(p.getFileName().toString() + ".mcmeta");
            if (Files.exists(mcmeta)) {
                assertTrue(img.getHeight() >= 32 && img.getHeight() % 16 == 0,
                        p.getFileName() + " animated strip height must be a positive multiple of 16 (>=32), got " + img.getHeight());
            } else {
                assertEquals(16, img.getHeight(), p.getFileName() + " height");
            }
        }

        List<Path> armorPngs;
        try (Stream<Path> s = Files.list(ASSETS.resolve("textures/models/armor"))) {
            armorPngs = s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
        Set<String> hdArmor128 = Set.of(
                "va_layer_1.png", "va_layer_2.png",
                "va_layer_1_emissive.png", "va_layer_2_emissive.png");
        assertEquals(6, armorPngs.size(),
                "Expected 6 armor PNGs (adamantite 64x32 + virtual_aegis 128x64 base & emissive masks)");
        int hdArmorCount = 0;
        for (Path p : armorPngs) {
            BufferedImage img = ImageIO.read(p.toFile());
            String fn = p.getFileName().toString();
            boolean hd = hdArmor128.contains(fn);
            if (hd) {
                hdArmorCount++;
            }
            assertEquals(hd ? 128 : 64, img.getWidth(), fn + " armor width");
            assertEquals(hd ? 64 : 32, img.getHeight(), fn + " armor height");
            if (fn.endsWith("_emissive.png")) {
                int litPx = 0;
                int transparentPx = 0;
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int a = (img.getRGB(x, y) >>> 24) & 0xFF;
                        if (a == 0) {
                            transparentPx++;
                        } else if (a >= 100) {
                            litPx++;
                        }
                    }
                }
                assertTrue(litPx >= 20, fn + " emissive mask must have glowing regions, got " + litPx);
                assertTrue(transparentPx >= 7000, fn + " emissive mask must keep non-glow areas transparent");
            }
        }
        assertEquals(4, hdArmorCount, "Expected 4 HD 128x64 virtual_aegis armor layers (base + emissive)");

        // Virtual Aegis seamless neon shimmer glint texture (64x64)
        BufferedImage vaGlintImg = ImageIO.read(ASSETS.resolve("textures/misc/va_neon_glint.png").toFile());
        assertEquals(64, vaGlintImg.getWidth(), "va_neon_glint width");
        assertEquals(64, vaGlintImg.getHeight(), "va_neon_glint height");

        // Sci-fi Pulse Laser additive beam & flare textures
        BufferedImage coreImg = ImageIO.read(ASSETS.resolve("textures/misc/laser_beam_core.png").toFile());
        assertEquals(64, coreImg.getWidth(), "laser_beam_core width");
        assertEquals(256, coreImg.getHeight(), "laser_beam_core height");
        BufferedImage auraImg = ImageIO.read(ASSETS.resolve("textures/misc/laser_beam_aura.png").toFile());
        assertEquals(64, auraImg.getWidth(), "laser_beam_aura width");
        assertEquals(256, auraImg.getHeight(), "laser_beam_aura height");
        BufferedImage flareImg = ImageIO.read(ASSETS.resolve("textures/misc/laser_flare.png").toFile());
        assertEquals(64, flareImg.getWidth(), "laser_flare width");
        assertEquals(64, flareImg.getHeight(), "laser_flare height");
        BufferedImage slashArcImg = ImageIO.read(ASSETS.resolve("textures/misc/blade_slash_arc.png").toFile());
        assertEquals(128, slashArcImg.getWidth(), "blade_slash_arc width");
        assertEquals(128, slashArcImg.getHeight(), "blade_slash_arc height");
        BufferedImage railCannonImg = ImageIO.read(ASSETS.resolve("textures/misc/railgun_cannon_beam.png").toFile());
        assertEquals(128, railCannonImg.getWidth(), "railgun_cannon_beam width");
        assertEquals(256, railCannonImg.getHeight(), "railgun_cannon_beam height");
        BufferedImage bowRingImg = ImageIO.read(ASSETS.resolve("textures/misc/bow_charge_ring.png").toFile());
        assertEquals(128, bowRingImg.getWidth(), "bow_charge_ring width");
        assertEquals(128, bowRingImg.getHeight(), "bow_charge_ring height");
    }

    @Test
    void machineGlowTexturesHaveRichMultiColorCoreGradients() throws IOException {
        for (String col : List.of("cyan", "orange", "purple", "green")) {
            BufferedImage img = ImageIO.read(
                    ASSETS.resolve("textures/block/machine_glow_" + col + ".png").toFile());
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    colors.add(img.getRGB(x, y) & 0x00FFFFFF);
                }
            }
            assertTrue(colors.size() >= 6,
                    "machine_glow_" + col + " gradient too flat: " + colors.size());
        }
    }

    @Test
    void soundDirectorPentatonicProgressionAndTelemetryCountersWork() {
        for (int i = 1; i < SoundRhythm.PENTATONIC.length; i++) {
            assertTrue(SoundRhythm.PENTATONIC[i] > SoundRhythm.PENTATONIC[i - 1],
                    "PENTATONIC scale must strictly ascend");
        }
        SoundRhythm.resetCounters();
        SoundRhythm.recordClick();
        SoundRhythm.recordPulse();
        SoundRhythm.recordPulse();
        SoundRhythm.recordComplete(1, 1);
        SoundRhythm.recordComplete(64, 16);
        SoundRhythm.recordOpenClose();
        SoundRhythm.recordHoverTick();
        SoundRhythm.recordSlotAction();
        SoundRhythm.recordMilestone();

        assertEquals(1, SoundRhythm.getClickCount());
        assertEquals(2, SoundRhythm.getPulseCount());
        assertEquals(2, SoundRhythm.getChimeCount());
        assertEquals(1, SoundRhythm.getComboArpeggioCount());
        assertEquals(1, SoundRhythm.getOpenCloseCount());
        assertEquals(1, SoundRhythm.getHoverTickCount());
        assertEquals(1, SoundRhythm.getSlotActionCount());
        assertEquals(1, SoundRhythm.getMilestoneCount());
        assertTrue(SoundRhythm.pentatonicPitch(1.0, 0) > SoundRhythm.pentatonicPitch(0.0, 0));
    }

    @Test
    void guiDopamineBeatTimingShockwavesSparksAndComboWork() {
        assertEquals(0.0, GuiDopamine.beatPhaseAt(0L, 150.0), 1e-6);
        assertEquals(0.5, GuiDopamine.beatPhaseAt(200L, 150.0), 1e-6);
        assertTrue(GuiDopamine.beatPulseAt(0L, 150.0) > GuiDopamine.beatPulseAt(200L, 150.0));

        GuiDopamine.reset();
        GuiDopamine.triggerCompletion(100, 50, 1);
        assertEquals(1, GuiDopamine.getComboStreak());
        assertEquals(2, GuiDopamine.getShockwaveCount());
        assertTrue(GuiDopamine.getActiveSparkCount() >= 16);
        assertEquals(1, GuiDopamine.getPopupCount());

        GuiDopamine.triggerCompletion(100, 50, 64);
        assertEquals(2, GuiDopamine.getComboStreak());
        assertEquals(4, GuiDopamine.getShockwaveCount());
        assertEquals(2, GuiDopamine.getPopupCount());

        GuiDopamine.reset();
        assertEquals(0, GuiDopamine.getShockwaveCount());
        assertEquals(0, GuiDopamine.getActiveSparkCount());
        assertEquals(0, GuiDopamine.getPopupCount());
    }
}
