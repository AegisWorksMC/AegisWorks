package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.CombustionShape;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 71: the combustion generator is the first power device whose model and collision shape
 * leave the block cell. The unit test sources run without Minecraft on the classpath, so the shape
 * boxes live in {@link CombustionShape} (plain numbers) and this test keeps them honest against the
 * shipped model file and the port table.
 */
class CombustionGeneratorModelTest {
    private static final String MODEL = "assets/industrialcore/models/block/combustion_generator.json";
    private static final double EPS = 1.0e-5;

    @Test
    void shapeBoxesMatchTheModelOutline() {
        double[] model = modelBoundsPx();          // minX, maxX, minY, maxY, minZ, maxZ
        double[] shape = shapeBoundsPx(CombustionShape.BOXES);
        assertEquals(model[2], shape[2], EPS, "model and shape must start at the same height");
        assertEquals(model[4], shape[4], EPS, "flywheel overhang");
        assertEquals(model[3], shape[3], EPS, "exhaust stack height");
        assertTrue(shape[0] <= model[0] + EPS && shape[1] >= model[1] - EPS,
                "the shape must cover the model in x");
        assertTrue(shape[5] >= model[5] - EPS, "the shape must cover the model in z");
        // the cell itself is always solid, so nothing can be walked through
        assertEquals(0.0, shape[0], EPS);
        assertEquals(16.0, shape[1], EPS);
        assertEquals(0.0, shape[2], EPS);
        assertEquals(16.0, shape[5], EPS);
    }

    @Test
    void theOverhangsAreTheOnesTheShapePromises() {
        assertEquals(3, CombustionShape.BOXES.size(), "cell plus two overhangs");
        assertEquals(-2.0, CombustionShape.FLYWHEEL.z0(), EPS);
        assertEquals(22.0, CombustionShape.STACK.y1(), EPS);
        assertEquals(0.0, CombustionShape.FLYWHEEL.z1(), EPS,
                "the flywheel box must reach the cell so the two boxes meet");
        assertEquals(16.0, CombustionShape.STACK.y0(), EPS,
                "the stack must start at the top of the cell");
    }

    @Test
    void portsAreTheThreeDesignedOnes() {
        PortLayout layout = PortTables.layout("combustion_generator");
        assertNotNull(layout, "the combustion generator must have a port table");
        assertEquals(3, layout.ports().size());
        Set<PortSide> sides = EnumSet.noneOf(PortSide.class);
        for (PortSpec spec : layout.ports()) {
            sides.add(spec.side());
        }
        assertEquals(EnumSet.of(PortSide.SOUTH, PortSide.WEST, PortSide.UP), sides);
        for (PortSpec spec : layout.ports()) {
            switch (spec.side()) {
                case SOUTH -> {
                    assertEquals(PortKind.POWER, spec.kind(), "the bus bar is the power port");
                    assertEquals(0.0, spec.u(), EPS);
                    assertEquals(0.0, spec.v(), EPS);
                }
                case WEST -> {
                    assertEquals(PortKind.ANY, spec.kind(), "the left face accepts any connector");
                    assertEquals(0.0, spec.u(), EPS);
                    assertEquals(0.0, spec.v(), EPS);
                }
                case UP -> {
                    assertEquals(PortKind.ITEM, spec.kind(), "the fuel filler is the item port");
                    assertEquals(0.0, spec.u(), EPS);
                    assertEquals(2.5, spec.v(), EPS, "the port sits over the filler, not the centre");
                }
                default -> throw new AssertionError("unexpected port on " + spec.side());
            }
        }
    }

    @Test
    void theShapeBoxesDoNotOverlapAndStayInBudget() {
        java.util.List<CombustionShape.Box> boxes = CombustionShape.BOXES;
        for (int a = 0; a < boxes.size(); a++) {
            for (int b = a + 1; b < boxes.size(); b++) {
                assertTrue(!overlaps(boxes.get(a), boxes.get(b)),
                        "shape boxes " + a + " and " + b + " intersect");
            }
        }
        // the models keep an 8 px overhang budget (docs/MACHINE_COLLISION_DESIGN.md section 5)
        assertTrue(-CombustionShape.FLYWHEEL.z0() <= 8.0, "front overhang over budget");
        assertTrue(CombustionShape.STACK.y1() - 16.0 <= 8.0, "top overhang over budget");
        assertTrue(CombustionShape.FLYWHEEL.x0() >= 0.0 && CombustionShape.FLYWHEEL.x1() <= 16.0,
                "the flywheel must not leave the cell sideways");
        assertTrue(CombustionShape.STACK.x0() >= 0.0 && CombustionShape.STACK.z1() <= 16.0,
                "the stack must not leave the cell sideways");
    }

    @Test
    void everyElementHasDrawableFaces() {
        // A box whose six sides are all covered by its neighbours is invisible; keeping it would
        // make the model fail to load ("Expected between 1 and 6 unique faces, got 0").
        JsonArray elements = model().getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            assertTrue(box.has("faces"), "element without faces: " + box.getAsJsonArray("from"));
            int faces = box.getAsJsonObject("faces").size();
            assertTrue(faces >= 1 && faces <= 6, "faces=" + faces + " at " + box.getAsJsonArray("from"));
        }
    }

    private static boolean overlaps(CombustionShape.Box a, CombustionShape.Box b) {
        return a.x0() < b.x1() && b.x0() < a.x1()
                && a.y0() < b.y1() && b.y0() < a.y1()
                && a.z0() < b.z1() && b.z0() < a.z1();
    }

    /** Outline of the shape boxes in pixels: {minX, maxX, minY, maxY, minZ, maxZ}. */
    private static double[] shapeBoundsPx(java.util.List<CombustionShape.Box> boxes) {
        double[] bounds = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (CombustionShape.Box box : boxes) {
            bounds[0] = Math.min(bounds[0], box.x0());
            bounds[1] = Math.max(bounds[1], box.x1());
            bounds[2] = Math.min(bounds[2], box.y0());
            bounds[3] = Math.max(bounds[3], box.y1());
            bounds[4] = Math.min(bounds[4], box.z0());
            bounds[5] = Math.max(bounds[5], box.z1());
        }
        return bounds;
    }

    /** Model outline in pixels: {minX, maxX, minY, maxY, minZ, maxZ}. */
    private static double[] modelBoundsPx() {
        JsonObject model = model();
        JsonArray elements = model.getAsJsonArray("elements");
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
        InputStream stream = CombustionGeneratorModelTest.class.getClassLoader().getResourceAsStream(MODEL);
        assertNotNull(stream, "missing model " + MODEL);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("cannot read " + MODEL, exception);
        }
    }
}
