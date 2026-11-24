package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.SolarShape;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 74.3: pins the solar generator model against the three-box {@link SolarShape},
 * 0.5-px grid alignment, cyan-only small-area glow palette, single SOUTH POWER (v=-1.0) port,
 * and 7x7 keepout clearance.
 */
class SolarGeneratorModelTest {
    private static final String MODEL = "assets/industrialcore/models/block/solar_generator.json";
    private static final double EPS = 1.0e-6;

    @Test
    void modelBoundsElementsAndTierTopsFitSolarShapeThreeBoxes() {
        double[] modelBounds = modelBoundsPx();
        double[] shapeBounds = shapeBoundsPx(SolarShape.BOXES);
        assertEquals(0.0, shapeBounds[0], EPS);
        assertEquals(0.0, shapeBounds[1], EPS);
        assertEquals(0.0, shapeBounds[2], EPS);
        assertEquals(16.0, shapeBounds[3], EPS);
        assertEquals(13.5, shapeBounds[4], EPS);
        assertEquals(16.0, shapeBounds[5], EPS);
        for (int i = 0; i < 6; i++) {
            assertEquals(shapeBounds[i], modelBounds[i], EPS, "bound mismatch at index " + i);
        }

        JsonArray elements = model().getAsJsonArray("elements");
        TreeSet<Double> xs = new TreeSet<>();
        TreeSet<Double> ys = new TreeSet<>();
        TreeSet<Double> zs = new TreeSet<>();
        for (JsonElement el : elements) {
            JsonObject box = el.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            xs.add(from.get(0).getAsDouble());
            xs.add(to.get(0).getAsDouble());
            ys.add(from.get(1).getAsDouble());
            ys.add(to.get(1).getAsDouble());
            zs.add(from.get(2).getAsDouble());
            zs.add(to.get(2).getAsDouble());
        }
        Double[] xa = xs.toArray(Double[]::new);
        Double[] ya = ys.toArray(Double[]::new);
        Double[] za = zs.toArray(Double[]::new);

        boolean reachesFrontTop = false;
        boolean reachesMidTop = false;
        boolean reachesRearTop = false;

        for (JsonElement el : elements) {
            JsonObject box = el.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();

            if (containedIn(x0, y0, z0, x1, y1, z1, SolarShape.FRONT_TIER) && Math.abs(y1 - 7.5) < EPS) {
                reachesFrontTop = true;
            }
            if (containedIn(x0, y0, z0, x1, y1, z1, SolarShape.MID_TIER) && Math.abs(y1 - 10.5) < EPS) {
                reachesMidTop = true;
            }
            if (containedIn(x0, y0, z0, x1, y1, z1, SolarShape.REAR_TIER) && Math.abs(y1 - 13.5) < EPS) {
                reachesRearTop = true;
            }

            for (int i = 0; i < xa.length - 1; i++) {
                if (xa[i + 1] <= x0 + EPS || xa[i] >= x1 - EPS) continue;
                for (int j = 0; j < ya.length - 1; j++) {
                    if (ya[j + 1] <= y0 + EPS || ya[j] >= y1 - EPS) continue;
                    for (int k = 0; k < za.length - 1; k++) {
                        if (za[k + 1] <= z0 + EPS || za[k] >= z1 - EPS) continue;
                        boolean insideAny = false;
                        for (SolarShape.Box sb : SolarShape.BOXES) {
                            if (containedIn(xa[i], ya[j], za[k], xa[i + 1], ya[j + 1], za[k + 1], sb)) {
                                insideAny = true;
                                break;
                            }
                        }
                        assertTrue(insideAny, "element escapes SolarShape.BOXES at " + from + " -> " + to);
                    }
                }
            }
        }
        assertTrue(reachesFrontTop, "FRONT_TIER must reach y=7.5");
        assertTrue(reachesMidTop, "MID_TIER must reach y=10.5");
        assertTrue(reachesRearTop, "REAR_TIER must reach y=13.5");
    }

    @Test
    void shapeBoxesDoNotOverlapStayInUnitCellAndAlignToHalfGrid() {
        List<SolarShape.Box> boxes = SolarShape.BOXES;
        assertEquals(3, boxes.size());
        for (int a = 0; a < boxes.size(); a++) {
            SolarShape.Box ba = boxes.get(a);
            assertHalfValue(ba.x0());
            assertHalfValue(ba.y0());
            assertHalfValue(ba.z0());
            assertHalfValue(ba.x1());
            assertHalfValue(ba.y1());
            assertHalfValue(ba.z1());
            assertTrue(ba.x0() >= -EPS && ba.x1() <= 16.0 + EPS);
            assertTrue(ba.y0() >= -EPS && ba.y1() <= 16.0 + EPS);
            assertTrue(ba.z0() >= -EPS && ba.z1() <= 16.0 + EPS);
            for (int b = a + 1; b < boxes.size(); b++) {
                SolarShape.Box bb = boxes.get(b);
                assertFalse(overlaps(ba.x0(), ba.y0(), ba.z0(), ba.x1(), ba.y1(), ba.z1(),
                        bb.x0(), bb.y0(), bb.z0(), bb.x1(), bb.y1(), bb.z1()),
                        "shape boxes " + a + " and " + b + " intersect");
            }
        }
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
        assertEquals("industrialcore:block/solar_generator_front", textures.get("front").getAsString());
        assertEquals("industrialcore:block/solar_generator_top", textures.get("top").getAsString());
        assertEquals("industrialcore:block/solar_generator_side", textures.get("side").getAsString());

        Set<String> used = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (String side : faces.keySet()) {
                String tex = faces.getAsJsonObject(side).get("texture").getAsString();
                assertNotEquals("#base", tex, "machine_base forbidden on visible faces");
                assertNotEquals("#port", tex, "#port forbidden");
                assertFalse(tex.contains("machine_port"), "machine_port forbidden");
                assertFalse(tex.contains("glow_orange"), "solar generator must use cyan glow only");
                assertFalse(tex.contains("glow_purple"), "solar generator must use cyan glow only");
                assertFalse(tex.contains("glow_green"), "solar generator must use cyan glow only");
                used.add(tex);
            }
        }
        assertTrue(used.contains("#front"));
        assertTrue(used.contains("#top"));
        assertTrue(used.contains("#side"));
        assertTrue(used.contains("#glow_cyan"));
    }

    @Test
    void portTableHasOnlySingleSouthPowerPortWithLowProfileOffset() {
        PortLayout layout = PortTables.layout("solar_generator");
        assertNotNull(layout, "solar_generator must have a port table");
        assertEquals(1, layout.ports().size(), "solar_generator must expose only SOUTH POWER port");
        PortSpec spec = layout.ports().get(0);
        assertEquals(PortSide.SOUTH, spec.side());
        assertEquals(PortKind.POWER, spec.kind());
        assertEquals(0.0, spec.u(), EPS);
        assertEquals(-1.0, spec.v(), EPS);
    }

    @Test
    void southPortKeepoutRemainsClear() {
        // SOUTH (8, 7, 16) (u=0, v=-1.0): x in 4.5..11.5, y in 3.5..10.5, z > 15.0 empty
        JsonArray elements = model().getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, 3.5, 15.0, 11.5, 10.5, 16.0),
                    "SOUTH keepout violated by " + from + " -> " + to);
        }
    }

    private static boolean containedIn(double x0, double y0, double z0, double x1, double y1, double z1,
                                       SolarShape.Box box) {
        return x0 >= box.x0() - EPS && x1 <= box.x1() + EPS
                && y0 >= box.y0() - EPS && y1 <= box.y1() + EPS
                && z0 >= box.z0() - EPS && z1 <= box.z1() + EPS;
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
            assertHalfValue(coords.get(i).getAsDouble());
        }
    }

    private static void assertHalfValue(double v) {
        double doubled = v * 2.0;
        assertEquals(Math.rint(doubled), doubled, EPS, "Non-0.5-px coordinate: " + v);
    }

    private static double[] modelBoundsPx() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (JsonElement element : model().getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            minX = Math.min(minX, from.get(0).getAsDouble());
            minY = Math.min(minY, from.get(1).getAsDouble());
            minZ = Math.min(minZ, from.get(2).getAsDouble());
            maxX = Math.max(maxX, to.get(0).getAsDouble());
            maxY = Math.max(maxY, to.get(1).getAsDouble());
            maxZ = Math.max(maxZ, to.get(2).getAsDouble());
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static double[] shapeBoundsPx(List<SolarShape.Box> boxes) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (SolarShape.Box b : boxes) {
            minX = Math.min(minX, b.x0());
            minY = Math.min(minY, b.y0());
            minZ = Math.min(minZ, b.z0());
            maxX = Math.max(maxX, b.x1());
            maxY = Math.max(maxY, b.y1());
            maxZ = Math.max(maxZ, b.z1());
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static JsonObject model() {
        InputStream stream = SolarGeneratorModelTest.class.getClassLoader().getResourceAsStream(MODEL);
        assertNotNull(stream, "Missing classpath model: " + MODEL);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
