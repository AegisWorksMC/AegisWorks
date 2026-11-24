package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.TubeProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 79 verification: model-accurate shapes, occlusion and the block item display.
 *
 * <h2>What is proved here</h2>
 *
 * <p>The shapes are not grepped: the test re-measures the shipped block models, so a machine that
 * gains a new overhang fails until the generated table is refreshed. Every open model (anything
 * whose six faces are not completely closed) must be registered with {@code .noOcclusion()} in
 * {@link com.industrialcore.registry.ModBlocks}, which is what stops the neighbours' faces from
 * being culled (the "X-ray" bug). Closed shells keep their occlusion, so this cannot silently turn
 * every machine into a light leak.</p>
 */
class Phase79Test {
    private static final Path ROOT = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCKSTATES = ROOT.resolve("blockstates");
    private static final Path BLOCK_MODELS = ROOT.resolve("models/block");
    private static final Path ITEM_MODELS = ROOT.resolve("models/item");
    private static final Path MOD_BLOCKS = Path.of(
            "src/main/java/com/industrialcore/registry/ModBlocks.java");
    private static final Path GUI_DOPAMINE = Path.of(
            "src/main/java/com/industrialcore/client/GuiDopamine.java");
    private static final double EPS = 1.0e-6;

    private static final Set<String> CONDUITS = Set.of(
            "cable_basic", "cable_improved", "cable_advanced", "cable_ultimate",
            "item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate");

    // ---------------------------------------------------------------------------------------
    // JSON helpers
    // ---------------------------------------------------------------------------------------

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path modelPath(String name) {
        String namespace = "industrialcore";
        String path = name;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            namespace = name.substring(0, colon);
            path = name.substring(colon + 1);
        }
        return Path.of("src/main/resources/assets", namespace, "models", path + ".json");
    }

    private static List<double[]> elements(String modelName) throws IOException {
        Path path = modelPath(modelName);
        if (!Files.exists(path)) {
            // Vanilla cube parents carry no elements; every block using one is a full cube.
            if (modelName.startsWith("minecraft:block/") && !modelName.equals("minecraft:block/block")) {
                return List.of(new double[]{0, 0, 0, 16, 16, 16});
            }
            return List.of();
        }
        JsonObject model = json(path);
        if (model.has("elements")) {
            return boxes(model.getAsJsonArray("elements"));
        }
        if (model.has("parent")) {
            return elements(model.get("parent").getAsString());
        }
        return List.of();
    }

    private static List<double[]> boxes(JsonArray array) {
        List<double[]> out = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            JsonArray from = object.getAsJsonArray("from");
            JsonArray to = object.getAsJsonArray("to");
            out.add(new double[]{
                    from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                    to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()});
        }
        return out;
    }

    private static List<String> blockNames() throws IOException {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(BLOCKSTATES)) {
            stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted()
                    .forEach(names::add);
        }
        return names;
    }

    private static JsonObject blockstate(String name) throws IOException {
        return json(BLOCKSTATES.resolve(name + ".json"));
    }

    /** Union of the boxes of all variants (rotation does not change a union's extents). */
    private static List<double[]> allModelBoxes(JsonObject state) throws IOException {
        List<double[]> out = new ArrayList<>();
        if (state.has("multipart")) {
            for (JsonElement element : state.getAsJsonArray("multipart")) {
                JsonObject part = element.getAsJsonObject();
                out.addAll(elements(part.getAsJsonObject("apply").get("model").getAsString()));
            }
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
            JsonElement value = entry.getValue();
            JsonObject apply = value.isJsonArray()
                    ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
            out.addAll(elements(apply.get("model").getAsString()));
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------
    // Occlusion
    // ---------------------------------------------------------------------------------------

    private static boolean faceCovered(List<double[]> boxes, int axis, double plane) {
        int u = axis == 0 ? 1 : 0;
        int v = axis == 2 ? 1 : 2;
        for (int a = 0; a < 16; a++) {
            for (int b = 0; b < 16; b++) {
                boolean covered = false;
                for (double[] box : boxes) {
                    boolean onPlane = Math.abs(box[axis] - plane) < EPS
                            || Math.abs(box[axis + 3] - plane) < EPS;
                    if (onPlane && box[u] <= a + EPS && box[u + 3] >= a + 1 - EPS
                            && box[v] <= b + EPS && box[v + 3] >= b + 1 - EPS) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isClosedCube(List<double[]> boxes) {
        if (boxes.isEmpty()) {
            return false;
        }
        return faceCovered(boxes, 0, 0) && faceCovered(boxes, 0, 16)
                && faceCovered(boxes, 1, 0) && faceCovered(boxes, 1, 16)
                && faceCovered(boxes, 2, 0) && faceCovered(boxes, 2, 16);
    }

    private static String modBlocksSource() throws IOException {
        return Files.readString(MOD_BLOCKS);
    }

    /** Body of one private helper, so a registration can be followed through to its properties. */
    private static String helperBody(String source, String helper) {
        Matcher matcher = Pattern.compile(
                "private static [\\w<>.\\[\\]]+ " + helper + "\\s*\\([^)]*\\)\\s*\\{").matcher(source);
        if (!matcher.find()) {
            return "";
        }
        int start = matcher.end();
        int depth = 1;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i);
                }
            }
        }
        return source.substring(start);
    }

    private static boolean appliesNoOcclusion(String source, String registration, String body) {
        if (registration.contains(".noOcclusion()")) {
            return true;
        }
        for (String helper : List.of("machineProperties", "registerCable", "registerPipe",
                "registerPower", "registerRecovery", "registerCasing")) {
            if (body.contains(helper)) {
                String helperBody = helperBody(source, helper);
                if (helperBody.contains(".noOcclusion()")
                        || helperBody.contains("machineProperties")) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------
    // Part 2 / Part 3
    // ---------------------------------------------------------------------------------------

    @Test
    void everyOpenModelBlockIsRegisteredWithoutOcclusion() throws IOException {
        String source = modBlocksSource();
        List<String> failures = new ArrayList<>();
        int open = 0;
        for (String name : blockNames()) {
            boolean closed = isClosedCube(allModelBoxes(blockstate(name)));
            if (closed) {
                continue;
            }
            open++;
            int index = source.indexOf("\"" + name + "\"");
            if (index < 0) {
                failures.add(name + " (not registered)");
                continue;
            }
            // Registrations wrap over several lines (e.g. advanced_transporter_drive), so look
            // at the whole statement instead of a single line.
            String registration = source.substring(index, Math.min(source.length(), index + 320));
            if (!appliesNoOcclusion(source, registration, registration)) {
                failures.add(name + " (missing .noOcclusion())");
            }
        }
        assertTrue(open >= 40, "expected every custom model to be open, found " + open);
        assertTrue(failures.isEmpty(), "open models that can cull their neighbours: " + failures);
    }

    @Test
    void vanillaCubeOresRemainOpaque() throws IOException {
        String source = modBlocksSource();
        List<String> failures = new ArrayList<>();
        for (String name : List.of("adamantite_ore", "mithril_ore", "nebula_ore",
                "orichalcum_ore", "quantum_ore", "aether_ore", "void_iron_ore")) {
            assertTrue(isClosedCube(allModelBoxes(blockstate(name))), name + " must be a closed cube");
            for (String line : source.split("\\R")) {
                if (line.contains("registerOre(\"" + name + "\")")) {
                    if (line.contains(".noOcclusion()")) {
                        failures.add(name);
                    }
                    break;
                }
            }
        }
        assertTrue(failures.isEmpty(), "plain ore cubes must keep occluding: " + failures);
    }

    @Test
    void everyModelHasAGeneratedShapeTable() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String name : blockNames()) {
            if (CONDUITS.contains(name)) {
                continue;
            }
            if (ModelShapeData.isDynamic(name)) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(), "models without a shape table: " + missing);
        assertEquals(86, blockNames().stream().filter(n -> !CONDUITS.contains(n)).count(),
                "every non-conduit block must have a measured table");
    }

    @Test
    void shapeTablesFollowTheModelHull() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String name : blockNames()) {
            if (CONDUITS.contains(name)) {
                continue;
            }
            JsonObject state = blockstate(name);
            if (state.has("multipart")) {
                continue;
            }
            Map<String, double[]> modelHulls = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
                JsonElement value = entry.getValue();
                JsonObject apply = value.isJsonArray()
                        ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
                String key = normalize(entry.getKey());
                modelHulls.putIfAbsent(key, hull(elements(apply.get("model").getAsString())));
            }
            for (Map.Entry<String, double[]> expected : modelHulls.entrySet()) {
                double[] hull = expected.getValue();
                double[][] table = ModelShapeData.boxes(name, expected.getKey());
                if (table.length == 0) {
                    String northKey = expected.getKey().isEmpty()
                            ? "facing=north" : normalizeWithFacing("facing=north," + expected.getKey());
                    table = ModelShapeData.boxes(name, northKey);
                }
                if (table.length == 0) {
                    failures.add(name + "[" + expected.getKey() + "] (empty table)");
                    continue;
                }
                double[] tableHull = hull(table);
                for (int i = 0; i < 6; i++) {
                    // Tables are voxelised at one pixel, so a sub-pixel trim may round outwards.
                    if (Math.abs(hull[i] - tableHull[i]) > 1.05) {
                        failures.add(name + "[" + expected.getKey() + "] hull "
                                + java.util.Arrays.toString(tableHull)
                                + " != model " + java.util.Arrays.toString(hull));
                        break;
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "shape tables that drifted from the models: " + failures);
    }

    @Test
    void openModelsNeverFallBackToTheFullCube() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String name : blockNames()) {
            if (CONDUITS.contains(name) || isClosedCube(allModelBoxes(blockstate(name)))) {
                continue;
            }
            JsonObject state = blockstate(name);
            if (state.has("multipart")) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
                String key = normalize(entry.getKey());
                double[][] table = ModelShapeData.boxes(name, key);
                if (table.length == 0) {
                    table = ModelShapeData.boxes(name, normalizeWithFacing(entry.getKey()));
                }
                if (table.length == 0) {
                    failures.add(name + "[" + key + "] (no boxes)");
                    continue;
                }
                double[] hull = hull(table);
                boolean full = hull[0] <= EPS && hull[1] <= EPS && hull[2] <= EPS
                        && hull[3] >= 16 - EPS && hull[4] >= 16 - EPS && hull[5] >= 16 - EPS
                        && table.length == 1;
                if (full) {
                    failures.add(name + "[" + key + "] is still a solid cube");
                }
            }
        }
        assertTrue(failures.isEmpty(), "open models still colliding as a full cube: " + failures);
    }

    @Test
    void facingRotationMatchesTheBlockstateRotation() throws IOException {
        double[] box = {1, 0, 0, 15, 8, 6};
        assertArrayEquals(new double[]{16 - 6, 0, 1, 16 - 0, 8, 15}, ModelShapeData.turn(box, 1));
        assertArrayEquals(new double[]{1, 0, 10, 15, 8, 16}, ModelShapeData.turn(box, 2));
        assertArrayEquals(new double[]{0, 0, 1, 6, 8, 15}, ModelShapeData.turn(box, 3));
        assertArrayEquals(box, ModelShapeData.turn(box, 0));
        assertArrayEquals(box, ModelShapeData.turn(box, 4));
        // The blockstate turns the model with y=90 for east; the shape must turn with it.
        JsonObject quarry = json(BLOCKSTATES.resolve("quarry.json"));
        assertTrue(quarry.getAsJsonObject("variants").getAsJsonObject("facing=east,lit=false")
                .get("y").getAsInt() == 90, "quarry keeps the 90 degree east variant");
    }

    @Test
    void conduitShapesStayOnTheTubeProfile() throws IOException {
        for (CableTier tier : CableTier.values()) {
            assertEquals(4.0, TubeProfile.cableTube(tier), 1.0e-6, "cable arm in game is 4 px");
        }
        for (PipeTier tier : PipeTier.values()) {
            assertEquals(5.0, TubeProfile.pipeTube(tier), 1.0e-6, "pipe body in game is 5 px");
        }
        for (String name : CONDUITS) {
            assertTrue(ModelShapeData.isDynamic(name),
                    name + " must keep the connection-driven TubeShapes table");
            assertFalse(ModelShapeData.isFullCube(name), name + " can never occlude");
        }
        // The straight model of the basic cable is exactly one tube of 4 px in both axes.
        JsonObject straight = json(BLOCK_MODELS.resolve("cb_str_x_b.json"));
        double[] hull = hull(boxes(straight.getAsJsonArray("elements")));
        assertEquals(4.0, hull[4] - hull[1], 1.0e-6, "cable model height matches the shape");
        assertEquals(4.0, hull[5] - hull[2], 1.0e-6, "cable model depth matches the shape");
    }

    // ---------------------------------------------------------------------------------------
    // Part 4
    // ---------------------------------------------------------------------------------------

    @Test
    void everyBlockItemUsesTheIsometricDisplay() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String name : blockNames()) {
            Path path = ITEM_MODELS.resolve(name + ".json");
            if (!Files.exists(path)) {
                failures.add(name + " (no item model)");
                continue;
            }
            JsonObject item = json(path);
            JsonObject display = item.getAsJsonObject("display");
            if (display == null || !display.has("gui")) {
                failures.add(name + " (no display.gui)");
                continue;
            }
            JsonObject gui = display.getAsJsonObject("gui");
            JsonArray rotation = gui.getAsJsonArray("rotation");
            if (rotation.get(0).getAsInt() != 30 || rotation.get(1).getAsInt() != 225
                    || rotation.get(2).getAsInt() != 0) {
                failures.add(name + " (gui rotation " + rotation + ")");
            }
            JsonArray scale = gui.getAsJsonArray("scale");
            double expected = CONDUITS.contains(name) ? 0.85 : 0.625;
            for (JsonElement value : scale) {
                if (Math.abs(value.getAsDouble() - expected) > 1.0e-3) {
                    failures.add(name + " (gui scale " + scale + ")");
                    break;
                }
            }
            for (String pose : List.of("ground", "fixed", "thirdperson_righthand",
                    "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand")) {
                if (!display.has(pose)) {
                    failures.add(name + " (missing " + pose + ")");
                }
            }
            String parent = item.has("parent") ? item.get("parent").getAsString() : "";
            assertFalse(parent.startsWith("minecraft:item/"),
                    name + " still inherits a flat item model");
        }
        assertTrue(failures.isEmpty(), "block items that are not isometric: " + failures);
    }

    @Test
    void conduitItemsUseTheVerticalStraightModel() throws IOException {
        Map<String, String> expected = Map.of(
                "cable_basic", "cb_str_y_b", "cable_improved", "cb_str_y_i",
                "cable_advanced", "cb_str_y_a", "cable_ultimate", "cb_str_y_u",
                "item_pipe", "pp_str_y_b", "pipe_improved", "pp_str_y_i",
                "pipe_advanced", "pp_str_y_a", "pipe_ultimate", "pp_str_y_u");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonObject item = json(ITEM_MODELS.resolve(entry.getKey() + ".json"));
            assertEquals("industrialcore:block/" + entry.getValue(),
                    item.get("parent").getAsString(), entry.getKey() + " item parent");
            assertTrue(Files.exists(BLOCK_MODELS.resolve(entry.getValue() + ".json")),
                    entry.getKey() + " straight model must exist");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Part 1
    // ---------------------------------------------------------------------------------------

    @Test
    void comboBannerIsGoneFromTheGui() throws IOException {
        String source = Files.readString(GUI_DOPAMINE);
        assertFalse(source.contains("\"COMBO x\""), "combo text literal is still drawn");
        assertFalse(source.contains("\"MAX x\""), "combo max literal is still drawn");
        assertFalse(source.contains("streakText"), "the combo banner still builds a string");
        assertFalse(source.contains("drawString(font, streakText"), "the banner is still drawn");
        // The streak itself must survive: it drives the Shepard riser, not the screen.
        assertTrue(source.contains("comboStreak"), "the internal completion streak must remain");
        assertTrue(source.contains("COMBO_WINDOW_MS"), "the streak window must remain");
    }

    @Test
    void guiEffectsSurviveTheBannerRemoval() throws IOException {
        String source = Files.readString(GUI_DOPAMINE);
        for (String effect : List.of("drawSheen", "drawGlint", "drawPopup", "SHEENS", "POPUPS")) {
            assertTrue(source.contains(effect), effect + " was removed with the banner");
        }
    }

    // ---------------------------------------------------------------------------------------
    // small helpers
    // ---------------------------------------------------------------------------------------

    private static String normalize(String variantKey) {
        return normalize(variantKey, true);
    }

    private static String normalizeWithFacing(String variantKey) {
        return normalize(variantKey, false);
    }

    private static String normalize(String variantKey, boolean dropFacing) {
        Set<String> parts = new TreeSet<>();
        for (String chunk : variantKey.split(",")) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty() || !trimmed.contains("=")) {
                continue;
            }
            if (dropFacing && trimmed.startsWith("facing=")) {
                continue;
            }
            parts.add(trimmed);
        }
        return String.join(",", parts);
    }

    private static double[] hull(List<double[]> boxes) {
        double[] hull = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] box : boxes) {
            for (int i = 0; i < 3; i++) {
                hull[i] = Math.min(hull[i], box[i]);
                hull[i + 3] = Math.max(hull[i + 3], box[i + 3]);
            }
        }
        return hull;
    }

    private static double[] hull(double[][] boxes) {
        List<double[]> list = new ArrayList<>();
        for (double[] box : boxes) {
            list.add(box);
        }
        return hull(list);
    }

    private static void assertArrayEquals(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length, "box length");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1.0e-6, "box coordinate " + i);
        }
    }

}
