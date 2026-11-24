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
 * Phase 118 verification suite for full AegisWorks Unified Sci-Fi Design Language rollout
 * across all remaining processing, quantum storage, generator/buffer/recovery/reactor parts,
 * agriculture, trap/defense, utility/suit, logistics, and world ore blocks.
 */
class Phase118FullVisualUnificationTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path ITEM_TEX = ASSETS.resolve("textures/item");
    private static final Path CLIENT_SRC = Path.of("src/main/java/com/industrialcore/client");

    private static final List<String> REBUILT_MULTI_PART_MODELS = List.of(
            // Group 1: Remaining processing, production & quantum storage
            "fast_crusher", "fast_crusher_on",
            "electrolyzer", "electrolyzer_on",
            "crafter", "crafter_on",
            "quarry", "quarry_on",
            "large_quarry", "large_quarry_on",
            "reconstructor", "reconstructor_on",
            "seed_cultivator", "seed_cultivator_on",
            "matter_amplifier", "matter_amplifier_on",
            "ore_synthesizer", "ore_synthesizer_on",
            "phase_reactor", "phase_reactor_on",
            "quantum_storage_core",
            "quantum_importer", "quantum_importer_on",
            "quantum_exporter", "quantum_exporter_on",
            // Group 2: Power buffers
            "energy_buffer", "energy_buffer_mk2", "energy_buffer_mk3",
            // Group 3: Agriculture, traps, utility, suit, logistics
            "crop_harvester", "crop_harvester_on",
            "tree_harvester", "tree_harvester_on",
            "auto_fisher", "auto_fisher_on",
            "ranch_harvester", "ranch_harvester_on",
            "growth_accelerator", "growth_accelerator_on",
            "bio_feeder", "bio_feeder_on",
            "mob_fan", "mob_fan_on",
            "rotary_drill", "rotary_drill_on",
            "item_vacuum", "item_vacuum_on",
            "xp_collector", "xp_collector_on",
            "spawn_suppressor", "spawn_suppressor_on",
            "mob_duplicator", "mob_duplicator_on",
            "fluid_pump", "fluid_pump_on",
            "auto_builder", "auto_builder_on",
            "wireless_charger", "wireless_charger_on",
            "chunk_loader", "chunk_loader_on",
            "climate_controller", "climate_controller_on",
            "warp_pad", "warp_pad_on",
            "factory_monitor",
            "suit_modifier", "suit_modifier_on",
            "item_distributor"
    );

    private static final List<String> TRANSLUCENT_GLASS_MODELS = List.of(
            "fast_crusher", "fast_crusher_on",
            "electrolyzer", "electrolyzer_on",
            "seed_cultivator", "seed_cultivator_on",
            "quantum_storage_core",
            "energy_buffer", "energy_buffer_mk2", "energy_buffer_mk3",
            "growth_accelerator", "growth_accelerator_on",
            "bio_feeder", "bio_feeder_on",
            "xp_collector", "xp_collector_on",
            "mob_duplicator", "mob_duplicator_on",
            "fluid_pump", "fluid_pump_on",
            "flywheel", "pressure_recovery", "heat_recovery",
            "reactor_glass"
    );

    private static final List<String> REACTOR_CONDUIT_ORE_TEXTURES = List.of(
            "reactor_casing.png", "reactor_glass.png", "reactor_coolant_block.png",
            "reactor_port.png", "reactor_core_cell.png", "reactor_core_cell_lit.png",
            "solar_generator_top.png", "combustion_generator_front.png", "geo_front.png",
            "flywheel_front.png", "pressure_recovery_front.png", "heat_recovery_front.png",
            "fluid_pipe.png", "hyper_energy_conduit.png", "hyper_item_conduit.png",
            "ad_ore.png", "ae_ore.png", "as_ore.png", "mi_ore.png", "nc_ore.png",
            "or_ore.png", "qu_ore.png", "vi_ore.png", "uranium_ore.png", "deepslate_uranium_ore.png"
    );

    @Test
    void allSixtyEightRemainingMachineModelsAreExposedMultiPartWithoutRotationsOrMonoliths() throws IOException {
        assertEquals(68, REBUILT_MULTI_PART_MODELS.size());
        for (String modelName : REBUILT_MULTI_PART_MODELS) {
            Path path = BLOCK_MODELS.resolve(modelName + ".json");
            assertTrue(Files.exists(path), "Missing Phase 118 model: " + modelName);
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertTrue(root.has("elements") && root.get("elements").isJsonArray(),
                    modelName + " must have explicit multi-part elements");
            JsonArray elements = root.getAsJsonArray("elements");
            assertTrue(elements.size() >= 12,
                    modelName + " must have >= 12 exposed-frame elements, found " + elements.size());

            for (JsonElement elem : elements) {
                JsonObject obj = elem.getAsJsonObject();
                assertFalse(obj.has("rotation"), modelName + " must not use rotated elements");
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                boolean isMonolith =
                        from.get(0).getAsDouble() <= 0.01 && from.get(1).getAsDouble() <= 0.01 && from.get(2).getAsDouble() <= 0.01
                                && to.get(0).getAsDouble() >= 15.99 && to.get(1).getAsDouble() >= 15.99 && to.get(2).getAsDouble() >= 15.99;
                assertFalse(isMonolith, modelName + " must not contain a 0..16 monolith cube");
            }
        }
    }

    @Test
    void glassWindowedMachinesAndRecoveryDevicesUseTranslucentRenderType() throws IOException {
        for (String modelName : TRANSLUCENT_GLASS_MODELS) {
            JsonObject root = JsonParser.parseString(
                    Files.readString(BLOCK_MODELS.resolve(modelName + ".json"))).getAsJsonObject();
            assertTrue(root.has("render_type"), modelName + " missing render_type");
            String rt = root.get("render_type").getAsString();
            assertTrue("translucent".equals(rt) || "minecraft:translucent".equals(rt),
                    modelName + " must use translucent render_type, found " + rt);
        }
    }

    @Test
    void unifiedReactorConduitOreAndStorageCoreTexturesHaveRichDetail() throws IOException {
        for (String tex : REACTOR_CONDUIT_ORE_TEXTURES) {
            assertRich16pxTexture(BLOCK_TEX.resolve(tex), tex);
        }
        for (int mk = 1; mk <= 5; mk++) {
            String name = "storage_core_mk" + mk + ".png";
            assertRich16pxTexture(ITEM_TEX.resolve(name), name);
        }
    }

    @Test
    void phase118BerEnhancementsAreWiredWithDistanceCulling() throws IOException {
        String crusherBer = Files.readString(CLIENT_SRC.resolve("CrusherRollerRenderer.java"));
        assertTrue(crusherBer.contains("ProcessingKind.ELECTROLYZER")
                        && crusherBer.contains("renderElectrolyzer")
                        && crusherBer.contains("BER_VIEW_DISTANCE"),
                "CrusherRollerRenderer must render active Electrolyzer electrodes & rising bubbles");

        String nuclearBer = Files.readString(CLIENT_SRC.resolve("NuclearReactorRenderer.java"));
        assertTrue(nuclearBer.contains("GenerationKind.COMBUSTION")
                        && nuclearBer.contains("renderCombustionEngine")
                        && nuclearBer.contains("BER_VIEW_DISTANCE"),
                "NuclearReactorRenderer must render reciprocating Combustion Engine pistons & crankshaft");

        String flywheelBer = Files.readString(CLIENT_SRC.resolve("FlywheelRenderer.java"));
        assertTrue(flywheelBer.contains("RecoveryKind.HEAT")
                        && flywheelBer.contains("renderHeatExchanger")
                        && flywheelBer.contains("TEX_AMBER")
                        && flywheelBer.contains("BER_VIEW_DISTANCE"),
                "FlywheelRenderer must render upgraded flywheel counterweights, 6-blade pressure turbine, and heat exchanger");
    }

    private static void assertRich16pxTexture(Path png, String label) throws IOException {
        assertTrue(Files.exists(png), "Missing unified texture: " + label);
        BufferedImage img = ImageIO.read(png.toFile());
        assertEquals(16, img.getWidth(), label + " width must be 16px");
        assertEquals(0, img.getHeight() % 16, label + " height must be a multiple of 16px");
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                colors.add(img.getRGB(x, y));
            }
        }
        assertTrue(colors.size() >= 6, label + " lacks panel detail (distinct RGBA=" + colors.size() + ")");
    }
}
