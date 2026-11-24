package com.industrialcore.machine;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 76 Part 2 quality gate across all 136 block models:
 * zero degenerate elements, zero rotation faults, zero AABB overlaps, zero same-direction
 * coplanar face overlaps, clean combustion_generator outer skin, and upgraded transporter models.
 */
class ModelQualityTest {
    private static final Path BLOCK_MODELS_DIR =
            Path.of("src/main/resources/assets/industrialcore/models/block");

    private static List<Path> allBlockModels() throws IOException {
        try (Stream<Path> s = Files.list(BLOCK_MODELS_DIR)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject parseModel(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static double[] toVec(JsonArray arr) {
        return new double[] {arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble()};
    }

    @Test
    void exactBlockModelCountPresentAndValidJson() throws IOException {
        List<Path> models = allBlockModels();
        assertEquals(312, models.size(),
                "Expected 312 block models (303 through Phase 109 + 9 Phase 110 lit animated processor/production models)");
        for (Path p : models) {
            JsonObject root = parseModel(p);
            assertTrue(root.has("textures"), p.getFileName() + " missing textures");
        }
    }

    @Test
    void noDegenerateOrRotatedElementsInAnyBlockModel() throws IOException {
        for (Path p : allBlockModels()) {
            JsonObject root = parseModel(p);
            if (!root.has("elements")) {
                continue;
            }
            for (JsonElement el : root.getAsJsonArray("elements")) {
                JsonObject obj = el.getAsJsonObject();
                assertFalse(obj.has("rotation"), p.getFileName() + " has unsupported rotation");
                double[] lo = toVec(obj.getAsJsonArray("from"));
                double[] hi = toVec(obj.getAsJsonArray("to"));
                for (int a = 0; a < 3; a++) {
                    assertTrue(lo[a] < hi[a], p.getFileName() + " degenerate axis " + a);
                }
                assertTrue(obj.has("faces") && obj.getAsJsonObject("faces").size() >= 1,
                        p.getFileName() + " element has no faces");
            }
        }
    }

    @Test
    void zeroVolumeIntersectionsAcrossAll136BlockModels() throws IOException {
        for (Path p : allBlockModels()) {
            JsonObject root = parseModel(p);
            if (!root.has("elements")) {
                continue;
            }
            List<double[]> boxes = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("elements")) {
                JsonObject obj = el.getAsJsonObject();
                double[] lo = toVec(obj.getAsJsonArray("from"));
                double[] hi = toVec(obj.getAsJsonArray("to"));
                boxes.add(new double[] {lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]});
            }
            for (int i = 0; i < boxes.size(); i++) {
                double[] a = boxes.get(i);
                for (int j = i + 1; j < boxes.size(); j++) {
                    double[] b = boxes.get(j);
                    boolean overlapX = a[3] > b[0] + 1e-6 && b[3] > a[0] + 1e-6;
                    boolean overlapY = a[4] > b[1] + 1e-6 && b[4] > a[1] + 1e-6;
                    boolean overlapZ = a[5] > b[2] + 1e-6 && b[5] > a[2] + 1e-6;
                    assertFalse(overlapX && overlapY && overlapZ,
                            p.getFileName() + " volume intersection between elements " + i + " and " + j);
                }
            }
        }
    }

    @Test
    void zeroSameDirectionCoplanarFaceOverlapsAcrossAll136BlockModels() throws IOException {
        String[] sides = {"west", "east", "down", "up", "north", "south"};
        int[] axis = {0, 0, 1, 1, 2, 2};
        boolean[] maxSide = {false, true, false, true, false, true};
        int[] uAxis = {2, 2, 0, 0, 0, 0};
        int[] vAxis = {1, 1, 2, 2, 1, 1};

        for (Path p : allBlockModels()) {
            JsonObject root = parseModel(p);
            if (!root.has("elements")) {
                continue;
            }
            JsonArray els = root.getAsJsonArray("elements");
            for (int s = 0; s < sides.length; s++) {
                String side = sides[s];
                List<double[]> rects = new ArrayList<>();
                for (JsonElement el : els) {
                    JsonObject obj = el.getAsJsonObject();
                    JsonObject faces = obj.getAsJsonObject("faces");
                    if (!faces.has(side)) {
                        continue;
                    }
                    double[] lo = toVec(obj.getAsJsonArray("from"));
                    double[] hi = toVec(obj.getAsJsonArray("to"));
                    double plane = maxSide[s] ? hi[axis[s]] : lo[axis[s]];
                    rects.add(new double[] {
                            plane, lo[uAxis[s]], lo[vAxis[s]], hi[uAxis[s]], hi[vAxis[s]]
                    });
                }
                for (int i = 0; i < rects.size(); i++) {
                    double[] a = rects.get(i);
                    for (int j = i + 1; j < rects.size(); j++) {
                        double[] b = rects.get(j);
                        if (Math.abs(a[0] - b[0]) > 1e-6) {
                            continue;
                        }
                        boolean ovU = a[3] > b[1] + 1e-6 && b[3] > a[1] + 1e-6;
                        boolean ovV = a[4] > b[2] + 1e-6 && b[4] > a[2] + 1e-6;
                        assertFalse(ovU && ovV, p.getFileName() + " coplanar " + side + " overlap");
                    }
                }
            }
        }
    }

    @Test
    void combustionGeneratorSkinRepairedAndExpanded() throws IOException {
        JsonObject cg = parseModel(BLOCK_MODELS_DIR.resolve("combustion_generator.json"));
        JsonArray els = cg.getAsJsonArray("elements");
        assertTrue(els.size() >= 190, "Expected >= 190 partitioned elements, got " + els.size());
    }

    @Test
    void transporterModelsUpgradedToDetailedThreeDimensionalForms() throws IOException {
        for (String name : List.of("quantum_transporter", "quantum_transporter_array", "advanced_item_transporter")) {
            JsonObject obj = parseModel(BLOCK_MODELS_DIR.resolve(name + ".json"));
            JsonArray els = obj.getAsJsonArray("elements");
            assertTrue(els.size() >= 100, name + " should have >= 100 elements, got " + els.size());
            for (JsonElement el : els) {
                double[] lo = toVec(el.getAsJsonObject().getAsJsonArray("from"));
                double[] hi = toVec(el.getAsJsonObject().getAsJsonArray("to"));
                for (int a = 0; a < 3; a++) {
                    assertTrue(lo[a] >= 0.0 && hi[a] <= 16.0, name + " coordinate out of [0,16]");
                }
            }
        }
    }
}
