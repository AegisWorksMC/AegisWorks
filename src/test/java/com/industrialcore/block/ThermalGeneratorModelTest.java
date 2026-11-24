package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 72: verifies the shipped thermal generator model against the single-cell 0.5-px grid
 * constraints, dedicated texture usage, and 7x7 port keepout rules without booting Minecraft.
 */
class ThermalGeneratorModelTest {
    private static final String MODEL = "assets/industrialcore/models/block/thermal_generator.json";
    private static final double EPS = 1.0e-6;

    @Test
    void modelBoundsStayStrictlyInsideSingleCell() {
        double[] bounds = modelBoundsPx(); // minX, maxX, minY, maxY, minZ, maxZ
        assertTrue(bounds[0] >= 0.0 - EPS && bounds[1] <= 16.0 + EPS,
                "X bounds out of 0..16: " + bounds[0] + ".." + bounds[1]);
        assertEquals(0.0, bounds[2], EPS, "minY must touch 0.0");
        assertEquals(16.0, bounds[3], EPS, "maxY must reach 16.0");
        assertTrue(bounds[4] >= 0.0 - EPS && bounds[5] <= 16.0 + EPS,
                "Z bounds out of 0..16: " + bounds[4] + ".." + bounds[5]);
    }

    @Test
    void allCoordinatesAlignToHalfPixelGrid() {
        JsonArray elements = model().getAsJsonArray("elements");
        assertFalse(elements.isEmpty(), "model must not be empty");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            assertHalfGrid(box.getAsJsonArray("from"), "from");
            assertHalfGrid(box.getAsJsonArray("to"), "to");
        }
    }

    @Test
    void everyElementHasDrawableFacesAndNoRotation() {
        JsonArray elements = model().getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            assertFalse(box.has("rotation"), "rotation is forbidden at " + box.getAsJsonArray("from"));
            assertTrue(box.has("faces"), "element without faces: " + box.getAsJsonArray("from"));
            int faces = box.getAsJsonObject("faces").size();
            assertTrue(faces >= 1 && faces <= 6, "faces=" + faces + " at " + box.getAsJsonArray("from"));
        }
    }

    @Test
    void dedicatedTexturesAreDeclaredAndUsedOnVisibleFaces() {
        JsonObject root = model();
        JsonObject textures = root.getAsJsonObject("textures");
        assertNotNull(textures);
        assertEquals("industrialcore:block/thermal_generator_front", textures.get("front").getAsString());
        assertEquals("industrialcore:block/thermal_generator_top", textures.get("top").getAsString());
        assertEquals("industrialcore:block/thermal_generator_side", textures.get("side").getAsString());

        Set<String> used = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (String side : faces.keySet()) {
                String tex = faces.getAsJsonObject(side).get("texture").getAsString();
                assertNotEquals("#base", tex, "machine_base must not appear on visible faces");
                assertNotEquals("#port", tex, "#port is forbidden");
                assertFalse(tex.contains("machine_port"), "machine_port is forbidden");
                used.add(tex);
            }
        }
        assertTrue(used.contains("#front"), "#front must be used on at least one visible face");
        assertTrue(used.contains("#top"), "#top must be used on at least one visible face");
        assertTrue(used.contains("#side"), "#side must be used on at least one visible face");
    }

    @Test
    void portsAreExactlySouthPowerAndUpItem() {
        PortLayout layout = PortTables.layout("thermal_generator");
        assertNotNull(layout, "thermal_generator must have a port table");
        assertEquals(2, layout.ports().size());
        Set<PortSide> sides = EnumSet.noneOf(PortSide.class);
        for (PortSpec spec : layout.ports()) {
            sides.add(spec.side());
        }
        assertEquals(EnumSet.of(PortSide.SOUTH, PortSide.UP), sides);
        for (PortSpec spec : layout.ports()) {
            switch (spec.side()) {
                case SOUTH -> {
                    assertEquals(PortKind.POWER, spec.kind());
                    assertEquals(0.0, spec.u(), EPS);
                    assertEquals(0.0, spec.v(), EPS);
                }
                case UP -> {
                    assertEquals(PortKind.ITEM, spec.kind());
                    assertEquals(0.0, spec.u(), EPS);
                    assertEquals(2.5, spec.v(), EPS);
                }
                default -> throw new AssertionError("unexpected port on " + spec.side());
            }
        }
    }

    @Test
    void portKeepoutZonesRemainClear() {
        // UP port at (8.0, 16.0, 10.5): x in 4.5..11.5, z in 7.0..14.0 must keep y <= 15.0
        // SOUTH port at (8.0, 8.0, 16.0): x in 4.5..11.5, y in 4.5..11.5 must keep z <= 15.0
        JsonArray elements = model().getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble();
            double y0 = from.get(1).getAsDouble();
            double z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble();
            double y1 = to.get(1).getAsDouble();
            double z1 = to.get(2).getAsDouble();

            assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, 15.0, 7.0, 11.5, 16.0, 14.0),
                    "UP keepout violated by element " + from + " -> " + to);
            assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, 4.5, 15.0, 11.5, 11.5, 16.0),
                    "SOUTH keepout violated by element " + from + " -> " + to);
        }
    }

    private static boolean overlaps(double ax0, double ay0, double az0, double ax1, double ay1, double az1,
                                    double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        return ax0 < bx1 - EPS && bx0 < ax1 - EPS
                && ay0 < by1 - EPS && by0 < ay1 - EPS
                && az0 < bz1 - EPS && bz0 < az1 - EPS;
    }

    private static void assertHalfGrid(JsonArray coords, String label) {
        for (int i = 0; i < 3; i++) {
            double v = coords.get(i).getAsDouble();
            assertEquals(Math.round(v * 2.0) / 2.0, v, EPS,
                    label + "[" + i + "] is not on the 0.5-px grid: " + v);
        }
    }

    private static double[] modelBoundsPx() {
        JsonArray elements = model().getAsJsonArray("elements");
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

    private static JsonObject model() {
        InputStream stream = ThermalGeneratorModelTest.class.getClassLoader().getResourceAsStream(MODEL);
        assertNotNull(stream, "missing model " + MODEL);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("cannot read " + MODEL, exception);
        }
    }
}
