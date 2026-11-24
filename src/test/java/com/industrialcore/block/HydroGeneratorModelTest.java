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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 74.1: pins the hydro generator model strictly inside [0..16]^3,
 * 0.5-px grid alignment, cyan-only glow palette, single SOUTH POWER port,
 * and 7x7 keepout clearance.
 */
class HydroGeneratorModelTest {
    private static final String MODEL = "assets/industrialcore/models/block/hydro_generator.json";
    private static final double EPS = 1.0e-6;

    @Test
    void modelStaysStrictlyInsideUnitCubeWithExactOuterBounds() {
        JsonArray elements = model().getAsJsonArray("elements");
        assertFalse(elements.isEmpty());
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            assertTrue(x0 >= -EPS && y0 >= -EPS && z0 >= -EPS, "from < 0 at " + from);
            assertTrue(x1 <= 16.0 + EPS && y1 <= 16.0 + EPS && z1 <= 16.0 + EPS, "to > 16 at " + to);
            assertTrue(x0 < x1 && y0 < y1 && z0 < z1, "degenerate box " + from + " -> " + to);
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            minZ = Math.min(minZ, z0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
            maxZ = Math.max(maxZ, z1);
        }
        assertEquals(0.0, minX, EPS);
        assertEquals(0.0, minY, EPS);
        assertEquals(0.0, minZ, EPS);
        assertEquals(16.0, maxX, EPS);
        assertEquals(16.0, maxY, EPS);
        assertEquals(16.0, maxZ, EPS);
    }

    @Test
    void allElementCoordinatesAlignToHalfPixelGrid() {
        JsonArray elements = model().getAsJsonArray("elements");
        assertFalse(elements.isEmpty());
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
            assertFalse(box.has("rotation"), "rotation forbidden at " + box.getAsJsonArray("from"));
            assertTrue(box.has("faces"), "element missing faces at " + box.getAsJsonArray("from"));
            int count = box.getAsJsonObject("faces").size();
            assertTrue(count >= 1 && count <= 6, "faces=" + count);
        }
    }

    @Test
    void dedicatedTexturesAndCyanOnlyGlowPaletteAreUsed() {
        JsonObject root = model();
        JsonObject textures = root.getAsJsonObject("textures");
        assertNotNull(textures);
        assertEquals("industrialcore:block/hydro_generator_front", textures.get("front").getAsString());
        assertEquals("industrialcore:block/hydro_generator_top", textures.get("top").getAsString());
        assertEquals("industrialcore:block/hydro_generator_side", textures.get("side").getAsString());

        Set<String> used = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (String side : faces.keySet()) {
                String tex = faces.getAsJsonObject(side).get("texture").getAsString();
                assertNotEquals("#base", tex, "machine_base forbidden on visible faces");
                assertNotEquals("#port", tex, "#port forbidden");
                assertFalse(tex.contains("machine_port"), "machine_port forbidden");
                assertFalse(tex.contains("glow_orange"), "hydro generator must use cyan glow only");
                assertFalse(tex.contains("glow_purple"), "hydro generator must use cyan glow only");
                assertFalse(tex.contains("glow_green"), "hydro generator must use cyan glow only");
                used.add(tex);
            }
        }
        assertTrue(used.contains("#front"));
        assertTrue(used.contains("#top"));
        assertTrue(used.contains("#side"));
        assertTrue(used.contains("#glow_cyan"));
    }

    @Test
    void portTableHasOnlySingleSouthPowerPort() {
        PortLayout layout = PortTables.layout("hydro_generator");
        assertNotNull(layout, "hydro_generator must have a port table");
        assertEquals(1, layout.ports().size(), "hydro_generator must expose only SOUTH POWER port");
        PortSpec spec = layout.ports().get(0);
        assertEquals(PortSide.SOUTH, spec.side());
        assertEquals(PortKind.POWER, spec.kind());
        assertEquals(0.0, spec.u(), EPS);
        assertEquals(0.0, spec.v(), EPS);
    }

    @Test
    void southPortKeepoutRemainsClear() {
        // SOUTH (8, 8, 16): x in 4.5..11.5, y in 4.5..11.5, z > 15.0 empty
        JsonArray elements = model().getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, 4.5, 15.0, 11.5, 11.5, 16.0),
                    "SOUTH keepout violated by " + from + " -> " + to);
        }
    }

    private static boolean overlaps(double ax0, double ay0, double az0, double ax1, double ay1, double az1,
                                    double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        return ax0 < bx1 - EPS && bx0 < ax1 - EPS
                && ay0 < by1 - EPS && by0 < ay1 - EPS
                && az0 < bz1 - EPS && bz0 < az1 - EPS;
    }

    private static void assertHalfGrid(JsonArray coords, String label) {
        assertEquals(3, coords.size(), label + " must have 3 coordinates");
        for (int i = 0; i < 3; i++) {
            double v = coords.get(i).getAsDouble();
            double doubled = v * 2.0;
            assertEquals(Math.rint(doubled), doubled, EPS, "Non-0.5-px coordinate in " + label + ": " + v);
        }
    }

    private static JsonObject model() {
        InputStream stream = HydroGeneratorModelTest.class.getClassLoader().getResourceAsStream(MODEL);
        assertNotNull(stream, "Missing classpath model: " + MODEL);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
