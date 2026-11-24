package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 109 verification suite for the 3-tier "Living Sci-Fi Industrial Plant" hybrid visual system:
 * <ul>
 *   <li>Tier 1: every {@code .png.mcmeta} has valid JSON, positive {@code frametime}, exact integer-multiple
 *       16x16 vertical frame strips, and actual pixel motion across frames.</li>
 *   <li>Tier 2: BER distance culling ({@code 32..40} blocks) and smooth counter-rotation / bobbing curves.</li>
 *   <li>Tier 3: 24-block distance cap and {@code ParticleStatus.MINIMAL} skip guard.</li>
 * </ul>
 */
class Phase109LivingFactoryTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path BLOCKSTATES = ASSETS.resolve("blockstates");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");

    private static final List<String> REQUIRED_ANIMATED_TEXTURES = List.of(
            "crusher_front_on.png",
            "ef_front_on.png",
            "af_front_on.png",
            "mp_front_on.png",
            "as_front_on.png",
            "el_front_on.png",
            "pr_front_on.png",
            "os_front_on.png",
            "quantum_storage_front.png",
            "hyper_energy_conduit.png",
            "hyper_item_conduit.png",
            "singularity_reactor_front_on.png",
            "nuclear_generator_front_on.png"
    );

    @Test
    void allTier1McmetaAnimationsHaveValidJsonAndExactSquareFrameStrips() throws IOException {
        for (String pngName : REQUIRED_ANIMATED_TEXTURES) {
            Path png = BLOCK_TEX.resolve(pngName);
            Path meta = BLOCK_TEX.resolve(pngName + ".mcmeta");
            assertTrue(Files.exists(png), "Missing animated texture PNG: " + pngName);
            assertTrue(Files.exists(meta), "Missing .mcmeta sidecar: " + pngName + ".mcmeta");
        }

        List<Path> allMcmetas;
        try (Stream<Path> s = Files.list(BLOCK_TEX)) {
            allMcmetas = s.filter(p -> p.getFileName().toString().endsWith(".png.mcmeta")).sorted().toList();
        }
        assertTrue(allMcmetas.size() >= REQUIRED_ANIMATED_TEXTURES.size(),
                "Expected at least " + REQUIRED_ANIMATED_TEXTURES.size() + " block .mcmeta files");

        for (Path metaPath : allMcmetas) {
            String metaFile = metaPath.getFileName().toString();
            JsonObject root = JsonParser.parseString(Files.readString(metaPath)).getAsJsonObject();
            assertTrue(root.has("animation") && root.get("animation").isJsonObject(),
                    metaFile + " must define an 'animation' object");
            JsonObject anim = root.getAsJsonObject("animation");
            assertTrue(anim.has("frametime") && anim.get("frametime").getAsInt() >= 1,
                    metaFile + " must specify frametime >= 1");

            String pngName = metaFile.substring(0, metaFile.length() - ".mcmeta".length());
            Path pngPath = BLOCK_TEX.resolve(pngName);
            assertTrue(Files.exists(pngPath), metaFile + " has no matching PNG " + pngName);

            BufferedImage img = ImageIO.read(pngPath.toFile());
            int w = img.getWidth();
            int h = img.getHeight();
            assertEquals(16, w, pngName + " frame width must stay 16px");
            assertTrue(h >= w * 2, pngName + " must have at least 2 animation frames, height=" + h);
            assertEquals(0, h % w, pngName + " height " + h + " must be an exact multiple of width " + w);

            // Verify animation actually changes pixels between frames (not a frozen duplicate strip)
            int diffPixels = 0;
            int frames = h / w;
            int midFrame = frames / 2;
            for (int y = 0; y < w; y++) {
                for (int x = 0; x < w; x++) {
                    if (img.getRGB(x, y) != img.getRGB(x, midFrame * w + y)) {
                        diffPixels++;
                    }
                }
            }
            assertTrue(diffPixels >= 4, pngName + " frames appear static (diffPixels=" + diffPixels + ")");
        }
    }

    @Test
    void processingLineBlockstatesSwitchToLitAnimatedModels() throws IOException {
        Map<String, String> expectedFronts = Map.of(
                "crusher", "industrialcore:block/crusher_front_on",
                "electric_furnace", "industrialcore:block/ef_front_on",
                "alloy_furnace", "industrialcore:block/af_front_on",
                "metal_press", "industrialcore:block/mp_front_on",
                "assembler", "industrialcore:block/as_front_on",
                "electrolyzer", "industrialcore:block/el_front_on",
                "phase_reactor", "industrialcore:block/pr_front_on",
                "ore_synthesizer", "industrialcore:block/os_front_on"
        );
        for (Map.Entry<String, String> entry : expectedFronts.entrySet()) {
            String id = entry.getKey();
            JsonObject bs = JsonParser.parseString(Files.readString(BLOCKSTATES.resolve(id + ".json"))).getAsJsonObject();
            JsonObject variants = bs.getAsJsonObject("variants");
            assertTrue(variants.has("facing=north,lit=false"), id + " missing lit=false variant");
            assertTrue(variants.has("facing=north,lit=true"), id + " missing lit=true variant");
            assertEquals("industrialcore:block/" + id,
                    variants.getAsJsonObject("facing=north,lit=false").get("model").getAsString());
            assertEquals("industrialcore:block/" + id + "_on",
                    variants.getAsJsonObject("facing=north,lit=true").get("model").getAsString());

            JsonObject onModel = JsonParser.parseString(
                    Files.readString(BLOCK_MODELS.resolve(id + "_on.json"))).getAsJsonObject();
            assertEquals(entry.getValue(),
                    onModel.getAsJsonObject("textures").get("front").getAsString(),
                    id + "_on front texture mismatch");
        }
    }

    @Test
    void berDistanceCullingAndParticleThrottlingRespectLimits() {
        assertTrue(LivingFactoryAnimLogic.BER_VIEW_DISTANCE >= 32
                        && LivingFactoryAnimLogic.BER_VIEW_DISTANCE <= 40,
                "Hero BER view distance must stay within 32..40 blocks");

        int vd = LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
        assertTrue(LivingFactoryAnimLogic.shouldRenderBer(0.0D, vd));
        assertTrue(LivingFactoryAnimLogic.shouldRenderBer((double) vd * vd, vd));
        assertFalse(LivingFactoryAnimLogic.shouldRenderBer((double) vd * vd + 0.01D, vd));
        assertFalse(LivingFactoryAnimLogic.shouldRenderBer(-1.0D, vd));
        assertFalse(LivingFactoryAnimLogic.shouldRenderBer(Double.NaN, vd));

        assertEquals(24.0D, LivingFactoryAnimLogic.PARTICLE_MAX_DISTANCE, 1e-6);
        assertTrue(LivingFactoryAnimLogic.shouldSpawnParticle(0.0D, false));
        assertTrue(LivingFactoryAnimLogic.shouldSpawnParticle(24.0D * 24.0D, false));
        assertFalse(LivingFactoryAnimLogic.shouldSpawnParticle(24.0D * 24.0D + 0.1D, false));
        assertFalse(LivingFactoryAnimLogic.shouldSpawnParticle(4.0D, true),
                "MINIMAL particle setting must suppress all Tier 3 factory particles");
    }

    @Test
    void heroBerAnimationCurvesMoveSmoothlyAndStayBounded() {
        // Singularity rings counter-rotate
        float out0 = LivingFactoryAnimLogic.singularityOuterRingDeg(0.25F);
        float out1 = LivingFactoryAnimLogic.singularityOuterRingDeg(1.25F);
        assertTrue(out1 > out0, "Outer singularity ring should advance clockwise");

        float in0 = LivingFactoryAnimLogic.singularityInnerRingDeg(0.25F);
        float in1 = LivingFactoryAnimLogic.singularityInnerRingDeg(1.25F);
        assertNotEquals(in0, in1, "Inner singularity ring must rotate");
        // Moving from 0.25s (-22 -> 338 deg) to 1.25s (-110 -> 250 deg): decreasing angle
        assertTrue(in1 < in0, "Inner singularity ring should counter-rotate opposite outer ring");

        // Singularity pulse & Nuclear bob/glow stay strictly bounded
        float minBob = Float.MAX_VALUE;
        float maxBob = -Float.MAX_VALUE;
        for (int i = 0; i < 100; i++) {
            float t = i * 0.08F;
            float sPulse = LivingFactoryAnimLogic.singularityCorePulse(t);
            assertTrue(sPulse >= 0.83F && sPulse <= 1.13F, "singularityCorePulse out of bounds: " + sPulse);

            float bob = LivingFactoryAnimLogic.nuclearCoreBobY(t);
            assertTrue(bob >= -0.111F && bob <= 0.111F, "nuclearCoreBobY out of bounds: " + bob);
            minBob = Math.min(minBob, bob);
            maxBob = Math.max(maxBob, bob);

            float nGlow = LivingFactoryAnimLogic.nuclearGlowPulse(t);
            assertTrue(nGlow >= 0.67F && nGlow <= 1.01F, "nuclearGlowPulse out of bounds: " + nGlow);
        }
        assertTrue(maxBob > 0.09F && minBob < -0.09F, "nuclearCoreBobY must visibly oscillate");

        // Mob Fan high-speed spin (720 deg/sec -> 180 deg in 0.25s)
        assertEquals(0.0F, LivingFactoryAnimLogic.mobFanBladeDeg(0.0F), 1e-4F);
        assertEquals(180.0F, LivingFactoryAnimLogic.mobFanBladeDeg(0.25F), 1e-4F);
        assertEquals(0.0F, LivingFactoryAnimLogic.mobFanBladeDeg(0.5F), 1e-4F);

        // Crusher twin rollers counter-rotate symmetrically
        float rA = LivingFactoryAnimLogic.crusherRollerPrimaryDeg(0.2F);
        float rB = LivingFactoryAnimLogic.crusherRollerCounterDeg(0.2F);
        assertEquals(360.0F, rA + rB, 1e-3F, "Crusher twin rollers must counter-rotate symmetrically");
    }
}
