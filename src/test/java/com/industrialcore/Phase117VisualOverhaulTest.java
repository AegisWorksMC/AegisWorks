package com.industrialcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 117 verification suite for the Unified Sci-Fi Design Language & 12-Flagship Visual Overhaul:
 * <ul>
 *   <li>All 13 flagship block models (plus active {@code _on} variants) are exposed-frame multi-part
 *       3D models with {@code >= 12} elements and zero single-cube monoliths.</li>
 *   <li>Unified SF palette textures (shared chassis/accent parts and flagship fronts/sides/tops)
 *       maintain crisp 16px pixel density ({@code >= 6} distinct RGBA values) and exact dimensions.</li>
 *   <li>All upgraded articulated BERs preserve {@code BER_VIEW_DISTANCE} culling and register
 *       {@code FurnaceCoreRenderer} for {@code alloy_furnace} alongside the existing flagship BERs.</li>
 * </ul>
 */
class Phase117VisualOverhaulTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path CLIENT_SRC = Path.of("src/main/java/com/industrialcore/client");

    private static final List<String> FLAGSHIP_MODELS = List.of(
            "metal_press", "metal_press_on",
            "crusher", "crusher_on",
            "precision_crusher", "precision_crusher_on",
            "electric_furnace", "electric_furnace_on",
            "alloy_furnace", "alloy_furnace_on",
            "assembler", "assembler_on",
            "quantum_compressor", "quantum_compressor_on",
            "laser_turret", "laser_turret_on",
            "nuclear_generator", "nuclear_generator_on",
            "singularity_reactor", "singularity_reactor_on",
            "wind_generator",
            "hydro_generator",
            "thermal_generator"
    );

    private static final List<String> UNIFIED_PALETTE_TEXTURES = List.of(
            "parts/part_chassis_frame.png",
            "parts/part_vent_grille.png",
            "parts/part_conveyor_tray.png",
            "parts/part_accent_amber.png",
            "parts/part_accent_cyan.png",
            "parts/part_accent_purple.png",
            "parts/part_accent_green.png",
            "machine_steel.png",
            "machine_trim.png",
            "machine_grate.png",
            "machine_glass_dark.png",
            "reactor_casing.png",
            "mp_front.png", "mp_front_on.png",
            "crusher_front.png", "crusher_front_on.png",
            "ef_front.png", "ef_front_on.png",
            "af_front.png", "af_front_on.png",
            "as_front.png", "as_front_on.png",
            "qc_front.png", "qc_front_on.png",
            "laser_turret_front.png", "laser_turret_top.png",
            "nuclear_generator_front.png", "nuclear_generator_front_on.png",
            "singularity_reactor_front.png", "singularity_reactor_front_on.png",
            "wind_generator_front.png", "hydro_generator_front.png", "thermal_generator_front.png"
    );

    @Test
    void allFlagshipModelsAreExposedFrameMultiPartWithoutFullCubeMonoliths() throws IOException {
        for (String modelName : FLAGSHIP_MODELS) {
            Path path = BLOCK_MODELS.resolve(modelName + ".json");
            assertTrue(Files.exists(path), "Missing flagship model JSON: " + modelName);
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertTrue(root.has("elements") && root.get("elements").isJsonArray(),
                    modelName + " must declare explicit 3D elements array");
            JsonArray elements = root.getAsJsonArray("elements");
            assertTrue(elements.size() >= 12,
                    modelName + " must have >= 12 exposed-frame elements, found " + elements.size());

            for (JsonElement elem : elements) {
                JsonObject obj = elem.getAsJsonObject();
                assertFalse(obj.has("rotation"), modelName + " must not use rotated block elements");
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                boolean isFullUnitCube =
                        from.get(0).getAsDouble() <= 0.01 && from.get(1).getAsDouble() <= 0.01 && from.get(2).getAsDouble() <= 0.01
                                && to.get(0).getAsDouble() >= 15.99 && to.get(1).getAsDouble() >= 15.99 && to.get(2).getAsDouble() >= 15.99;
                assertFalse(isFullUnitCube, modelName + " must not contain a full 0..16 monolith cube");
            }
        }
    }

    @Test
    void unifiedSciFiTexturesHaveRichDetailAndExactDimensions() throws IOException {
        for (String relPath : UNIFIED_PALETTE_TEXTURES) {
            Path png = BLOCK_TEX.resolve(relPath);
            assertTrue(Files.exists(png), "Missing unified SF texture: " + relPath);
            BufferedImage img = ImageIO.read(png.toFile());
            int w = img.getWidth();
            int h = img.getHeight();
            assertEquals(16, w, relPath + " width must be 16px");
            assertEquals(0, h % 16, relPath + " height must be a multiple of 16px, got " + h);

            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    colors.add(img.getRGB(x, y));
                }
            }
            assertTrue(colors.size() >= 6,
                    relPath + " frame 0 lacks panel detail (distinct RGBA=" + colors.size() + ")");
        }
    }

    @Test
    void phase117ArticulatedRenderersAndRegistrationsAreWired() throws IOException {
        String clientSetup = Files.readString(CLIENT_SRC.resolve("ClientSetup.java"));
        assertTrue(clientSetup.contains("ModBlockEntities.ALLOY_FURNACE")
                        && clientSetup.contains("FurnaceCoreRenderer::new"),
                "ClientSetup must register FurnaceCoreRenderer for ALLOY_FURNACE");

        String crusherBer = Files.readString(CLIENT_SRC.resolve("CrusherRollerRenderer.java"));
        assertTrue(crusherBer.contains("renderInductionFurnaceCore"),
                "CrusherRollerRenderer must provide shared induction furnace BER rendering");
        assertTrue(crusherBer.contains("BER_VIEW_DISTANCE"),
                "CrusherRollerRenderer must enforce BER_VIEW_DISTANCE");

        String furnaceBer = Files.readString(CLIENT_SRC.resolve("FurnaceCoreRenderer.java"));
        assertTrue(furnaceBer.contains("renderInductionFurnaceCore") && furnaceBer.contains("BER_VIEW_DISTANCE"),
                "FurnaceCoreRenderer must delegate to renderInductionFurnaceCore and enforce BER_VIEW_DISTANCE");

        String prodBer = Files.readString(CLIENT_SRC.resolve("ProductionHeroRenderer.java"));
        assertTrue(prodBer.contains("renderAssembler") && prodBer.contains("renderQuantumCompressor"),
                "ProductionHeroRenderer must render articulated assembler gantry & opposing compressor rams");

        String nuclearBer = Files.readString(CLIENT_SRC.resolve("NuclearReactorRenderer.java"));
        assertTrue(nuclearBer.contains("renderThermalBoiler") && nuclearBer.contains("renderWindTurbine")
                        && nuclearBer.contains("renderHydroTurbine") && nuclearBer.contains("ROD_OFFSETS_LARGE"),
                "NuclearReactorRenderer must render nuclear rods, wind blades, hydro wheel, and thermal flywheel");

        String singularityBer = Files.readString(CLIENT_SRC.resolve("SingularityReactorRenderer.java"));
        assertTrue(singularityBer.contains("energyRingXZ(pose, cyan, outerR")
                        && singularityBer.contains("energyRingXZ(pose, purple, midR")
                        && singularityBer.contains("energyRingXZ(pose, cyan, innerR"),
                "SingularityReactorRenderer must render 3 interlocking gimbal rings around the event horizon");

        String trapBer = Files.readString(CLIENT_SRC.resolve("TrapHeroRenderer.java"));
        assertTrue(trapBer.contains("recoilZ") && trapBer.contains("0.78F"),
                "TrapHeroRenderer must mount recoiling twin Pulse Laser barrels on the armored tripod slewing ring");
    }
}
