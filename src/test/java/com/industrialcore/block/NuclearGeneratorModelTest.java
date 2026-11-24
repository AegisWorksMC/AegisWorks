package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.NuclearShape;
import com.industrialcore.machine.port.PortKind;
import com.industrialcore.machine.port.PortLayout;
import com.industrialcore.machine.port.PortSide;
import com.industrialcore.machine.port.PortSpec;
import com.industrialcore.machine.port.PortTables;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 105: pins the redesigned flush multiblock nuclear generator controller
 * ({@code nuclear_generator.json} and {@code nuclear_generator_on.json}) against
 * {@link NuclearShape}, 0.5-px grid alignment, flush {@code 0..16} reactor casing integration,
 * recessed front console, and high-pressure green/amber active glow palette.
 */
class NuclearGeneratorModelTest {
    private static final String MODEL_OFF = "assets/industrialcore/models/block/nuclear_generator.json";
    private static final String MODEL_ON = "assets/industrialcore/models/block/nuclear_generator_on.json";
    private static final double EPS = 1.0e-6;

    @Test
    void modelBoundsFitFlushMultiblockReactorWallAndHaveRecessedFrontConsole() {
        for (String path : List.of(MODEL_OFF, MODEL_ON)) {
            JsonObject root = loadModel(path);
            double[] bounds = modelBoundsPx(root);
            double[] shape = shapeBoundsPx(NuclearShape.BOXES);
            for (int i = 0; i < 6; i++) {
                assertEquals(shape[i], bounds[i], EPS, path + " bound mismatch at index " + i);
            }

            JsonArray elements = root.getAsJsonArray("elements");
            assertTrue(elements.size() >= 10, path + " must have detailed recessed console elements");
            boolean hasRecessedConsole = false;
            for (JsonElement el : elements) {
                JsonObject box = el.getAsJsonObject();
                JsonArray from = box.getAsJsonArray("from");
                JsonArray to = box.getAsJsonArray("to");
                double z0 = from.get(2).getAsDouble();
                double z1 = to.get(2).getAsDouble();
                if (z0 >= 0.5 - EPS && z1 <= 2.0 + EPS) {
                    hasRecessedConsole = true;
                }
            }
            assertTrue(hasRecessedConsole, path + " must contain recessed front console geometry");
        }
    }

    @Test
    void shapeBoxesStayFlushInsideUnitCellAndAlignToHalfGrid() {
        List<NuclearShape.Box> boxes = NuclearShape.BOXES;
        assertEquals(1, boxes.size());
        NuclearShape.Box cell = boxes.get(0);
        assertEquals(0.0, cell.x0(), EPS);
        assertEquals(0.0, cell.y0(), EPS);
        assertEquals(0.0, cell.z0(), EPS);
        assertEquals(16.0, cell.x1(), EPS);
        assertEquals(16.0, cell.y1(), EPS);
        assertEquals(16.0, cell.z1(), EPS);
    }

    @Test
    void allElementCoordinatesAlignToHalfPixelGrid() {
        for (String path : List.of(MODEL_OFF, MODEL_ON)) {
            JsonArray elements = loadModel(path).getAsJsonArray("elements");
            assertFalse(elements.isEmpty());
            for (JsonElement element : elements) {
                JsonObject box = element.getAsJsonObject();
                assertHalfGrid(box.getAsJsonArray("from"), "from");
                assertHalfGrid(box.getAsJsonArray("to"), "to");
            }
        }
    }

    @Test
    void everyElementHasDrawableFacesAndNoRotation() {
        for (String path : List.of(MODEL_OFF, MODEL_ON)) {
            JsonArray elements = loadModel(path).getAsJsonArray("elements");
            for (JsonElement element : elements) {
                JsonObject box = element.getAsJsonObject();
                assertFalse(box.has("rotation"), "rotation forbidden at " + box.getAsJsonArray("from"));
                assertTrue(box.has("faces"), "element missing faces at " + box.getAsJsonArray("from"));
                int count = box.getAsJsonObject("faces").size();
                assertTrue(count >= 1 && count <= 6, "faces=" + count);
            }
        }
    }

    @Test
    void dedicatedTexturesReactorCasingIntegrationAndActiveGreenAmberGlowAreUsed() {
        JsonObject offRoot = loadModel(MODEL_OFF);
        JsonObject offTex = offRoot.getAsJsonObject("textures");
        assertEquals("industrialcore:block/nuclear_generator_front", offTex.get("front").getAsString());
        assertEquals("industrialcore:block/nuclear_generator_top", offTex.get("top").getAsString());
        assertEquals("industrialcore:block/nuclear_generator_side", offTex.get("side").getAsString());
        assertEquals("industrialcore:block/reactor_casing", offTex.get("casing").getAsString());

        JsonObject onRoot = loadModel(MODEL_ON);
        JsonObject onTex = onRoot.getAsJsonObject("textures");
        assertEquals("industrialcore:block/nuclear_generator_front_on", onTex.get("front").getAsString());
        assertEquals("industrialcore:block/reactor_casing", onTex.get("casing").getAsString());

        Set<String> usedOn = new HashSet<>();
        for (JsonElement element : onRoot.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (String side : faces.keySet()) {
                String tex = faces.getAsJsonObject(side).get("texture").getAsString();
                assertNotEquals("#port", tex, "#port forbidden");
                assertFalse(tex.contains("machine_port"), "machine_port forbidden");
                usedOn.add(tex);
            }
        }
        assertTrue(usedOn.contains("#front"));
        assertTrue(usedOn.contains("#top"));
        assertTrue(usedOn.contains("#side"));
        assertTrue(usedOn.contains("#casing"));
        assertTrue(usedOn.contains("#glow_green"));
        assertTrue(usedOn.contains("#glow_orange"));
    }

    @Test
    void portsAreExactlySouthPowerWestAnyAndUpItem() {
        PortLayout layout = PortTables.layout("nuclear_generator");
        assertNotNull(layout, "nuclear_generator must have a port table");
        assertEquals(3, layout.ports().size());
        Set<PortSide> sides = EnumSet.noneOf(PortSide.class);
        for (PortSpec spec : layout.ports()) {
            sides.add(spec.side());
        }
        assertEquals(EnumSet.of(PortSide.SOUTH, PortSide.WEST, PortSide.UP), sides);
        for (PortSpec spec : layout.ports()) {
            switch (spec.side()) {
                case SOUTH -> assertEquals(PortKind.POWER, spec.kind());
                case WEST -> assertEquals(PortKind.ANY, spec.kind());
                case UP -> assertEquals(PortKind.ITEM, spec.kind());
                default -> throw new AssertionError("unexpected port on " + spec.side());
            }
        }
    }

    private static void assertHalfGrid(JsonArray coords, String label) {
        for (int i = 0; i < 3; i++) {
            double v = coords.get(i).getAsDouble();
            assertEquals(Math.round(v * 2.0) / 2.0, v, EPS, label + "[" + i + "] not on 0.5 grid: " + v);
        }
    }

    private static double[] shapeBoundsPx(List<NuclearShape.Box> boxes) {
        double[] bounds = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (NuclearShape.Box box : boxes) {
            bounds[0] = Math.min(bounds[0], box.x0());
            bounds[1] = Math.max(bounds[1], box.x1());
            bounds[2] = Math.min(bounds[2], box.y0());
            bounds[3] = Math.max(bounds[3], box.y1());
            bounds[4] = Math.min(bounds[4], box.z0());
            bounds[5] = Math.max(bounds[5], box.z1());
        }
        return bounds;
    }

    private static double[] modelBoundsPx(JsonObject root) {
        JsonArray elements = root.getAsJsonArray("elements");
        double[] bounds = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis * 2] = Math.min(bounds[axis * 2], from.get(axis).getAsDouble());
                bounds[axis * 2 + 1] = Math.max(bounds[axis * 2 + 1], to.get(axis).getAsDouble());
            }
        }
        return bounds;
    }

    private static JsonObject loadModel(String path) {
        InputStream stream = NuclearGeneratorModelTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing model " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("cannot read " + path, exception);
        }
    }
}
