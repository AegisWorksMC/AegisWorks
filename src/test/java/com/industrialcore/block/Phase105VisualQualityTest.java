package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 105 visual quality gate:
 * <ul>
 *   <li>Zero broken texture references across all block models and item models (zero purple-black missing textures).</li>
 *   <li>All UV coordinates across all block models stay strictly within {@code [0, 16]}.</li>
 *   <li>Zero flat cube functional machines remain (all agri, trap, life, quantum, monitor, and reactor controllers are 3D).</li>
 *   <li>Both multiblock reactor controllers ({@code nuclear_generator}, {@code singularity_reactor}) stay flush
 *       in {@code [0..16]} with recessed consoles and active {@code _on} variants.</li>
 * </ul>
 */
class Phase105VisualQualityTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path ITEM_MODELS = ASSETS.resolve("models/item");
    private static final Path BLOCKSTATES = ASSETS.resolve("blockstates");
    private static final Path TEXTURES = ASSETS.resolve("textures");

    private static final Set<String> ALLOWED_FLAT_BLOCKS = Set.of(
            "adamantite_ore", "aether_ore", "astral_silver_ore", "mithril_ore",
            "nebula_ore", "orichalcum_ore", "quantum_ore", "void_iron_ore",
            "uranium_ore", "deepslate_uranium_ore",
            "reactor_casing", "reactor_coolant_block", "reactor_core_cell",
            "reactor_core_cell_lit", "reactor_glass", "reactor_port",
            "transporter_casing", "advanced_transporter_casing", "quantum_transporter_casing",
            "conveyor", "machine_bounding"
    );

    @Test
    void everyBlockAndItemModelReferencesExistingTexturesAndValidParents() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path dir : List.of(BLOCK_MODELS, ITEM_MODELS)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path file : s.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (root.has("parent")) {
                        String parent = root.get("parent").getAsString();
                        if (parent.startsWith("industrialcore:")) {
                            Path target = ASSETS.resolve("models/" + parent.substring("industrialcore:".length()) + ".json");
                            if (!Files.exists(target)) {
                                broken.add(file.getFileName() + " -> missing parent " + parent);
                            }
                        }
                    }
                    if (root.has("textures")) {
                        JsonObject texObj = root.getAsJsonObject("textures");
                        for (Map.Entry<String, JsonElement> e : texObj.entrySet()) {
                            String val = e.getValue().getAsString();
                            if (val.startsWith("#")) {
                                continue;
                            }
                            if (val.startsWith("industrialcore:")) {
                                Path png = TEXTURES.resolve(val.substring("industrialcore:".length()) + ".png");
                                if (!Files.exists(png)) {
                                    broken.add(file.getFileName() + " -> missing texture " + val);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "Broken model parents/textures: " + broken);
    }

    @Test
    void allBlockModelUvsStayWithinZeroToSixteen() throws IOException {
        List<String> badUvs = new ArrayList<>();
        try (Stream<Path> s = Files.list(BLOCK_MODELS)) {
            for (Path file : s.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!root.has("elements")) {
                    continue;
                }
                for (JsonElement el : root.getAsJsonArray("elements")) {
                    JsonObject faces = el.getAsJsonObject().getAsJsonObject("faces");
                    for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                        JsonObject fObj = f.getValue().getAsJsonObject();
                        if (!fObj.has("uv")) {
                            continue;
                        }
                        JsonArray uv = fObj.getAsJsonArray("uv");
                        for (int i = 0; i < uv.size(); i++) {
                            double v = uv.get(i).getAsDouble();
                            if (v < -1e-6 || v > 16.0 + 1e-6) {
                                badUvs.add(file.getFileName() + "." + f.getKey() + " uv=" + uv);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(badUvs.isEmpty(), "Out-of-range UV coordinates: " + badUvs);
    }

    @Test
    void zeroFlatCubeFunctionalMachinesRemain() throws IOException {
        List<String> flatMachines = new ArrayList<>();
        try (Stream<Path> s = Files.list(BLOCK_MODELS)) {
            for (Path file : s.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String base = file.getFileName().toString().replace(".json", "");
                if (ALLOWED_FLAT_BLOCKS.contains(base)) {
                    continue;
                }
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!root.has("elements") || root.getAsJsonArray("elements").isEmpty()) {
                    flatMachines.add(base);
                }
            }
        }
        assertTrue(flatMachines.isEmpty(), "Functional machines still flat cubes: " + flatMachines);
    }

    @Test
    void multiblockControllersAreFlushWithReactorCasingAndSupportLitState() throws IOException {
        for (String id : List.of("nuclear_generator", "singularity_reactor")) {
            JsonObject state = JsonParser.parseString(
                    Files.readString(BLOCKSTATES.resolve(id + ".json"))).getAsJsonObject();
            JsonObject variants = state.getAsJsonObject("variants");
            assertTrue(variants.has("facing=north,lit=false"), id + " missing lit=false");
            assertTrue(variants.has("facing=north,lit=true"), id + " missing lit=true");
            assertEquals("industrialcore:block/" + id,
                    variants.getAsJsonObject("facing=north,lit=false").get("model").getAsString());
            assertEquals("industrialcore:block/" + id + "_on",
                    variants.getAsJsonObject("facing=north,lit=true").get("model").getAsString());

            for (String m : List.of(id, id + "_on")) {
                JsonObject model = JsonParser.parseString(
                        Files.readString(BLOCK_MODELS.resolve(m + ".json"))).getAsJsonObject();
                JsonArray els = model.getAsJsonArray("elements");
                assertTrue(els.size() >= 10, m + " should have >= 10 sculpted console elements");
                for (JsonElement el : els) {
                    JsonArray lo = el.getAsJsonObject().getAsJsonArray("from");
                    JsonArray hi = el.getAsJsonObject().getAsJsonArray("to");
                    for (int a = 0; a < 3; a++) {
                        assertTrue(lo.get(a).getAsDouble() >= 0.0 && hi.get(a).getAsDouble() <= 16.0,
                                m + " must stay strictly flush inside [0..16] to integrate with reactor_casing");
                    }
                }
            }
        }
    }

    @Test
    void zeroDistortedUvTexelDensityAcrossAllBlockModels() throws IOException {
        List<String> distorted = new ArrayList<>();
        try (Stream<Path> s = Files.list(BLOCK_MODELS)) {
            for (Path file : s.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!root.has("elements")) {
                    continue;
                }
                for (JsonElement el : root.getAsJsonArray("elements")) {
                    JsonObject obj = el.getAsJsonObject();
                    JsonArray lo = obj.getAsJsonArray("from");
                    JsonArray hi = obj.getAsJsonArray("to");
                    double dx = hi.get(0).getAsDouble() - lo.get(0).getAsDouble();
                    double dy = hi.get(1).getAsDouble() - lo.get(1).getAsDouble();
                    double dz = hi.get(2).getAsDouble() - lo.get(2).getAsDouble();

                    JsonObject faces = obj.getAsJsonObject("faces");
                    for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
                        JsonObject fObj = f.getValue().getAsJsonObject();
                        if (!fObj.has("uv")) {
                            continue;
                        }
                        double fw, fh;
                        switch (f.getKey()) {
                            case "north", "south" -> { fw = dx; fh = dy; }
                            case "east", "west" -> { fw = dz; fh = dy; }
                            default -> { fw = dx; fh = dz; }
                        }
                        JsonArray uv = fObj.getAsJsonArray("uv");
                        double uw = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
                        double vh = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
                        double rw = fw > 1e-6 ? uw / fw : 1.0;
                        double rh = fh > 1e-6 ? vh / fh : 1.0;
                        if (rw < 0.65 || rw > 1.55 || rh < 0.65 || rh > 1.55) {
                            distorted.add(file.getFileName() + "." + f.getKey()
                                    + " rw=" + String.format("%.2f", rw)
                                    + " rh=" + String.format("%.2f", rh));
                        }
                    }
                }
            }
        }
        assertTrue(distorted.isEmpty(),
                "UV texel distortion found (ratio outside [0.65..1.55]): "
                        + distorted.subList(0, Math.min(10, distorted.size())));
    }

    @Test
    void dedicatedSubPartTexturesExistAndAre16x16() throws IOException {
        Path partsDir = TEXTURES.resolve("block/parts");
        for (String name : List.of(
                "part_chassis_frame.png", "part_vent_grille.png", "part_conveyor_tray.png",
                "part_accent_cyan.png", "part_accent_amber.png", "part_accent_green.png", "part_accent_purple.png")) {
            Path p = partsDir.resolve(name);
            assertTrue(Files.exists(p), "Missing dedicated part texture: " + name);
            var img = javax.imageio.ImageIO.read(p.toFile());
            assertEquals(16, img.getWidth(), name + " width");
            assertEquals(16, img.getHeight(), name + " height");
        }
    }
}
