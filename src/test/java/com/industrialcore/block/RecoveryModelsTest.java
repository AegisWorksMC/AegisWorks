package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.HeatShape;
import com.industrialcore.machine.PressureShape;
import com.industrialcore.machine.RegenShape;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 75: verifies all four recovery device models against their shape outlines,
 * 0.5-px grid alignment, 90-degree horizontal rotational symmetry, texture/glow rules,
 * four-way horizontal POWER ports, and all 16 keepout zones without booting Minecraft.
 */
class RecoveryModelsTest {
    private static final String[] RECOVERY_IDS = {
            "pressure_recovery", "heat_recovery", "flywheel", "regen_generator"
    };
    private static final String[] DIRECTIONS = {
            "north", "south", "east", "west", "up", "down"
    };
    private static final double EPS = 1.0e-6;

    private record PxBox(double x0, double y0, double z0, double x1, double y1, double z1) {}

    private record TileKey(String side, int a0, int a1, int b0, int b1, int fixed, String tex) {}

    @Test
    void pressureRecoveryFitsTwoBoxShapeAndReachesAllBounds() {
        List<PxBox> boxes = pressureBoxes();
        assertModelMatchesBoxes("pressure_recovery", boxes, 0.0, 16.0, 0.0, 20.0, 0.0, 16.0);
        assertBoxEdgesReached("pressure_recovery", PressureShape.TOWER.x0(), PressureShape.TOWER.y0(),
                PressureShape.TOWER.z0(), PressureShape.TOWER.x1(), PressureShape.TOWER.y1(),
                PressureShape.TOWER.z1());
    }

    @Test
    void heatRecoveryFitsTwoBoxShapeAndReachesAllBounds() {
        List<PxBox> boxes = heatBoxes();
        assertModelMatchesBoxes("heat_recovery", boxes, 0.0, 16.0, 0.0, 17.5, 0.0, 16.0);
        assertBoxEdgesReached("heat_recovery", HeatShape.HOOD.x0(), HeatShape.HOOD.y0(),
                HeatShape.HOOD.z0(), HeatShape.HOOD.x1(), HeatShape.HOOD.y1(),
                HeatShape.HOOD.z1());
    }

    @Test
    void flywheelMatchesUnitCubeStrictly() {
        List<PxBox> boxes = List.of(new PxBox(0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
        assertModelMatchesBoxes("flywheel", boxes, 0.0, 16.0, 0.0, 16.0, 0.0, 16.0);
    }

    @Test
    void regenGeneratorFitsTwoBoxShapeAndReachesAllBounds() {
        List<PxBox> boxes = regenBoxes();
        assertModelMatchesBoxes("regen_generator", boxes, 0.0, 16.0, 0.0, 14.0, 0.0, 16.0);
        assertBoxEdgesReached("regen_generator", RegenShape.COIL.x0(), RegenShape.COIL.y0(),
                RegenShape.COIL.z0(), RegenShape.COIL.x1(), RegenShape.COIL.y1(),
                RegenShape.COIL.z1());
    }

    @Test
    void allShapeBoxesAreDisjointHalfGridAndRotationallySymmetric() {
        for (List<PxBox> boxes : List.of(pressureBoxes(), heatBoxes(), regenBoxes())) {
            assertEquals(2, boxes.size());
            for (int i = 0; i < boxes.size(); i++) {
                PxBox b = boxes.get(i);
                assertHalfPx(b.x0());
                assertHalfPx(b.y0());
                assertHalfPx(b.z0());
                assertHalfPx(b.x1());
                assertHalfPx(b.y1());
                assertHalfPx(b.z1());
                // 90-degree symmetry around (8, 8): rotated box matches itself
                assertEquals(16.0 - b.z1(), b.x0(), EPS);
                assertEquals(16.0 - b.z0(), b.x1(), EPS);
                assertEquals(b.x0(), b.z0(), EPS);
                assertEquals(b.x1(), b.z1(), EPS);
                for (int j = i + 1; j < boxes.size(); j++) {
                    PxBox o = boxes.get(j);
                    boolean overlap = b.x0() < o.x1() - EPS && o.x0() < b.x1() - EPS
                            && b.y0() < o.y1() - EPS && o.y0() < b.y1() - EPS
                            && b.z0() < o.z1() - EPS && o.z0() < b.z1() - EPS;
                    assertFalse(overlap, "shape boxes must not overlap: " + b + " vs " + o);
                }
            }
        }
    }

    @Test
    void allFourModelsHaveExactNinetyDegreeHorizontalSurfaceSymmetry() {
        for (String id : RECOVERY_IDS) {
            JsonObject root = loadModel(id);
            JsonArray elements = root.getAsJsonArray("elements");
            TreeSet<Double> xGrid = new TreeSet<>();
            TreeSet<Double> yGrid = new TreeSet<>();
            TreeSet<Double> zGrid = new TreeSet<>();
            for (JsonElement el : elements) {
                JsonObject obj = el.getAsJsonObject();
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                xGrid.add(from.get(0).getAsDouble());
                xGrid.add(to.get(0).getAsDouble());
                yGrid.add(from.get(1).getAsDouble());
                yGrid.add(to.get(1).getAsDouble());
                zGrid.add(from.get(2).getAsDouble());
                zGrid.add(to.get(2).getAsDouble());
            }
            List<Double> xs = List.copyOf(xGrid);
            List<Double> ys = List.copyOf(yGrid);
            List<Double> zs = List.copyOf(zGrid);

            Set<TileKey> tiles = new HashSet<>();
            for (JsonElement el : elements) {
                JsonObject obj = el.getAsJsonObject();
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
                double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
                int ix0 = indexOf(xs, x0), ix1 = indexOf(xs, x1);
                int iy0 = indexOf(ys, y0), iy1 = indexOf(ys, y1);
                int iz0 = indexOf(zs, z0), iz1 = indexOf(zs, z1);
                JsonObject faces = obj.getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                    String side = entry.getKey();
                    String tex = entry.getValue().getAsJsonObject().get("texture").getAsString();
                    switch (side) {
                        case "north", "south" -> {
                            int fixed = px2(side.equals("north") ? z0 : z1);
                            for (int i = ix0; i < ix1; i++) {
                                for (int j = iy0; j < iy1; j++) {
                                    tiles.add(new TileKey(side, px2(xs.get(i)), px2(xs.get(i + 1)),
                                            px2(ys.get(j)), px2(ys.get(j + 1)), fixed, tex));
                                }
                            }
                        }
                        case "west", "east" -> {
                            int fixed = px2(side.equals("west") ? x0 : x1);
                            for (int k = iz0; k < iz1; k++) {
                                for (int j = iy0; j < iy1; j++) {
                                    tiles.add(new TileKey(side, px2(zs.get(k)), px2(zs.get(k + 1)),
                                            px2(ys.get(j)), px2(ys.get(j + 1)), fixed, tex));
                                }
                            }
                        }
                        default -> {
                            int fixed = px2(side.equals("down") ? y0 : y1);
                            for (int i = ix0; i < ix1; i++) {
                                for (int k = iz0; k < iz1; k++) {
                                    tiles.add(new TileKey(side, px2(xs.get(i)), px2(xs.get(i + 1)),
                                            px2(zs.get(k)), px2(zs.get(k + 1)), fixed, tex));
                                }
                            }
                        }
                    }
                }
            }

            Set<TileKey> rotated = new HashSet<>();
            for (TileKey t : tiles) {
                rotated.add(rotateTileCw(t));
            }
            assertEquals(tiles, rotated, id + " failed 90-degree horizontal rotational symmetry");
        }
    }

    @Test
    void allFourModelsAlignToHalfPixelGridWithValidFacesAndNoRotation() {
        for (String id : RECOVERY_IDS) {
            JsonArray elements = loadModel(id).getAsJsonArray("elements");
            assertFalse(elements.isEmpty(), id + " has no elements");
            for (JsonElement el : elements) {
                JsonObject obj = el.getAsJsonObject();
                assertFalse(obj.has("rotation"), id + " must not use element rotation");
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                for (int c = 0; c < 3; c++) {
                    assertHalfPx(from.get(c).getAsDouble());
                    assertHalfPx(to.get(c).getAsDouble());
                }
                JsonObject faces = obj.getAsJsonObject("faces");
                assertTrue(faces.size() >= 1 && faces.size() <= 6, id + " invalid face count");
            }
        }
    }

    @Test
    void dedicatedTexturesNoVanillaRefsAndAllowedGlowPalettes() {
        assertModelTextures("pressure_recovery",
                Set.of("#front", "#side", "#top", "#glow_cyan"),
                Set.of("#front", "#side", "#top", "#steel", "#trim", "#grate", "#glass", "#glow_cyan"));
        assertModelTextures("heat_recovery",
                Set.of("#front", "#side", "#top", "#glow_orange", "#glow_cyan"),
                Set.of("#front", "#side", "#top", "#steel", "#trim", "#grate", "#glass", "#glow_orange", "#glow_cyan"));
        assertModelTextures("flywheel",
                Set.of("#front", "#side", "#top", "#glow_cyan"),
                Set.of("#front", "#side", "#top", "#steel", "#trim", "#grate", "#glass", "#glow_cyan"));

        JsonObject regen = loadModel("regen_generator");
        assertEquals("minecraft:block/block", regen.get("parent").getAsString(),
                "regen_generator must not inherit minecraft:block/orientable");
        assertModelTextures("regen_generator",
                Set.of("#steel", "#trim", "#grate", "#glass", "#glow_purple", "#glow_cyan"),
                Set.of("#steel", "#trim", "#grate", "#glass", "#glow_purple", "#glow_cyan"));
    }

    @Test
    void portTablesExposeFourHorizontalPowerPortsOnly() {
        for (String id : RECOVERY_IDS) {
            PortLayout layout = PortTables.layout(id);
            assertNotNull(layout, "missing PortTables entry for " + id);
            assertEquals(4, layout.ports().size(), id + " must expose exactly 4 ports");
            double expectedV = id.equals("regen_generator") ? -1.0 : 0.0;
            double expectedCenterY = id.equals("regen_generator") ? 7.0 : 8.0;

            for (PortSide side : List.of(PortSide.NORTH, PortSide.SOUTH, PortSide.WEST, PortSide.EAST)) {
                PortSpec spec = null;
                for (PortSpec candidate : layout.ports()) {
                    if (candidate.side() == side) {
                        spec = candidate;
                        break;
                    }
                }
                assertNotNull(spec, id + " missing port on " + side);
                assertEquals(PortKind.POWER, spec.kind(), id + " port must be POWER");
                assertEquals(0.0, spec.u(), EPS);
                assertEquals(expectedV, spec.v(), EPS);
                assertEquals(expectedCenterY, spec.centerPx()[1], EPS);
            }
            assertNull(layout.portOn(com.industrialcore.machine.port.PortFacing.NORTH, PortSide.UP));
            assertNull(layout.portOn(com.industrialcore.machine.port.PortFacing.NORTH, PortSide.DOWN));
        }
    }

    @Test
    void allSixteenHorizontalKeepoutZonesRemainClear() {
        for (String id : RECOVERY_IDS) {
            double ky0 = id.equals("regen_generator") ? 3.5 : 4.5;
            double ky1 = id.equals("regen_generator") ? 10.5 : 11.5;
            JsonArray elements = loadModel(id).getAsJsonArray("elements");
            for (JsonElement el : elements) {
                JsonObject obj = el.getAsJsonObject();
                JsonArray from = obj.getAsJsonArray("from");
                JsonArray to = obj.getAsJsonArray("to");
                double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
                double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();

                assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, ky0, 0.0, 11.5, ky1, 1.0),
                        id + " NORTH keepout obstructed by " + obj);
                assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 4.5, ky0, 15.0, 11.5, ky1, 16.0),
                        id + " SOUTH keepout obstructed by " + obj);
                assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 0.0, ky0, 4.5, 1.0, ky1, 11.5),
                        id + " WEST keepout obstructed by " + obj);
                assertFalse(overlaps(x0, y0, z0, x1, y1, z1, 15.0, ky0, 4.5, 16.0, ky1, 11.5),
                        id + " EAST keepout obstructed by " + obj);
            }
        }
    }

    private static void assertModelTextures(String id, Set<String> required, Set<String> allowed) {
        JsonObject model = loadModel(id);
        JsonObject textures = model.getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            String val = entry.getValue().getAsString();
            assertFalse(val.startsWith("minecraft:"), id + " uses vanilla texture " + val);
            assertFalse(val.contains("machine_port"), id + " references machine_port");
            assertFalse(val.contains("machine_base"), id + " references machine_base");
        }
        Set<String> used = new HashSet<>();
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject faces = el.getAsJsonObject().getAsJsonObject("faces");
            for (String dir : DIRECTIONS) {
                if (!faces.has(dir)) {
                    continue;
                }
                String ref = faces.getAsJsonObject(dir).get("texture").getAsString();
                assertTrue(allowed.contains(ref), id + " uses forbidden texture ref " + ref);
                used.add(ref);
            }
        }
        assertTrue(used.containsAll(required), id + " missing required textures: expected " + required + ", used " + used);
    }

    private static void assertModelMatchesBoxes(
            String id, List<PxBox> allowedBoxes,
            double expX0, double expX1, double expY0, double expY1, double expZ0, double expZ1) {
        JsonArray elements = loadModel(id).getAsJsonArray("elements");
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        TreeSet<Double> xs = new TreeSet<>(), ys = new TreeSet<>(), zs = new TreeSet<>();
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray from = obj.getAsJsonArray("from"), to = obj.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            minX = Math.min(minX, x0); minY = Math.min(minY, y0); minZ = Math.min(minZ, z0);
            maxX = Math.max(maxX, x1); maxY = Math.max(maxY, y1); maxZ = Math.max(maxZ, z1);
            xs.add(x0); xs.add(x1); ys.add(y0); ys.add(y1); zs.add(z0); zs.add(z1);
        }
        assertEquals(expX0, minX, EPS); assertEquals(expX1, maxX, EPS);
        assertEquals(expY0, minY, EPS); assertEquals(expY1, maxY, EPS);
        assertEquals(expZ0, minZ, EPS); assertEquals(expZ1, maxZ, EPS);

        List<Double> xList = List.copyOf(xs), yList = List.copyOf(ys), zList = List.copyOf(zs);
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray from = obj.getAsJsonArray("from"), to = obj.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            for (int i = 0; i < xList.size() - 1; i++) {
                if (xList.get(i + 1) <= x0 || xList.get(i) >= x1) continue;
                for (int j = 0; j < yList.size() - 1; j++) {
                    if (yList.get(j + 1) <= y0 || yList.get(j) >= y1) continue;
                    for (int k = 0; k < zList.size() - 1; k++) {
                        if (zList.get(k + 1) <= z0 || zList.get(k) >= z1) continue;
                        double cx0 = xList.get(i), cx1 = xList.get(i + 1);
                        double cy0 = yList.get(j), cy1 = yList.get(j + 1);
                        double cz0 = zList.get(k), cz1 = zList.get(k + 1);
                        boolean inside = allowedBoxes.stream().anyMatch(b ->
                                cx0 >= b.x0() - EPS && cx1 <= b.x1() + EPS
                                        && cy0 >= b.y0() - EPS && cy1 <= b.y1() + EPS
                                        && cz0 >= b.z0() - EPS && cz1 <= b.z1() + EPS);
                        assertTrue(inside, id + " cell escapes shape boxes: "
                                + cx0 + "," + cy0 + "," + cz0 + "->" + cx1 + "," + cy1 + "," + cz1);
                    }
                }
            }
        }
    }

    private static void assertBoxEdgesReached(
            String id, double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        JsonArray elements = loadModel(id).getAsJsonArray("elements");
        boolean hitX0 = false, hitX1 = false, hitY1 = false, hitZ0 = false, hitZ1 = false;
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray from = obj.getAsJsonArray("from"), to = obj.getAsJsonArray("to");
            double x0 = from.get(0).getAsDouble(), y0 = from.get(1).getAsDouble(), z0 = from.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
            if (x0 >= bx0 - EPS && x1 <= bx1 + EPS && y0 >= by0 - EPS && y1 <= by1 + EPS
                    && z0 >= bz0 - EPS && z1 <= bz1 + EPS) {
                hitX0 |= Math.abs(x0 - bx0) < EPS;
                hitX1 |= Math.abs(x1 - bx1) < EPS;
                hitY1 |= Math.abs(y1 - by1) < EPS;
                hitZ0 |= Math.abs(z0 - bz0) < EPS;
                hitZ1 |= Math.abs(z1 - bz1) < EPS;
            }
        }
        assertTrue(hitX0 && hitX1 && hitY1 && hitZ0 && hitZ1,
                id + " upper box bounds not all reached");
    }

    private static TileKey rotateTileCw(TileKey t) {
        return switch (t.side()) {
            case "north" -> new TileKey("east", t.a0(), t.a1(), t.b0(), t.b1(), 32 - t.fixed(), t.tex());
            case "east" -> new TileKey("south", 32 - t.a1(), 32 - t.a0(), t.b0(), t.b1(), t.fixed(), t.tex());
            case "south" -> new TileKey("west", t.a0(), t.a1(), t.b0(), t.b1(), 32 - t.fixed(), t.tex());
            case "west" -> new TileKey("north", 32 - t.a1(), 32 - t.a0(), t.b0(), t.b1(), t.fixed(), t.tex());
            default -> new TileKey(t.side(), 32 - t.b1(), 32 - t.b0(), t.a0(), t.a1(), t.fixed(), t.tex());
        };
    }

    private static List<PxBox> pressureBoxes() {
        return PressureShape.BOXES.stream().map(b -> new PxBox(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1())).toList();
    }

    private static List<PxBox> heatBoxes() {
        return HeatShape.BOXES.stream().map(b -> new PxBox(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1())).toList();
    }

    private static List<PxBox> regenBoxes() {
        return RegenShape.BOXES.stream().map(b -> new PxBox(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1())).toList();
    }

    private static int px2(double v) {
        return (int) Math.round(v * 2.0);
    }

    private static int indexOf(List<Double> list, double target) {
        for (int i = 0; i < list.size(); i++) {
            if (Math.abs(list.get(i) - target) < EPS) return i;
        }
        throw new IllegalStateException("grid coordinate missing: " + target);
    }

    private static boolean overlaps(double ax0, double ay0, double az0, double ax1, double ay1, double az1,
                                    double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        return ax0 < bx1 - EPS && bx0 < ax1 - EPS
                && ay0 < by1 - EPS && by0 < ay1 - EPS
                && az0 < bz1 - EPS && bz0 < az1 - EPS;
    }

    private static void assertHalfPx(double v) {
        assertEquals(Math.round(v * 2.0), v * 2.0, EPS, "non-0.5px coordinate: " + v);
    }

    private static JsonObject loadModel(String id) {
        String path = "assets/industrialcore/models/block/" + id + ".json";
        InputStream stream = RecoveryModelsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing model " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("failed reading " + path, e);
        }
    }
}
