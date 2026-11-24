package com.industrialcore.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks the generated pipe and cable models against {@link TubeProfile} (Phase 49).
 *
 * <p>The models are authored by {@code docs/model_tools/gen_pipes.py}; the collision shapes and the
 * connector renderer read their sizes from {@code TubeProfile}. If the two disagree, a tube would be
 * drawn wider than the box the player can click, or the connector would not match the arm it
 * finishes. This test reads the shipped JSON back and compares the real sizes. It only uses the
 * model files and the plain profile numbers, so it runs without the game on the classpath.</p>
 *
 * <p>It also pins the per-direction arms: the old generator wrote one north-facing box for every
 * face, so an up connection was drawn on the north side. Every direction is checked for the right
 * axis, and no two directions may share the same box.</p>
 */
class PipeModelProfileTest {
    private static final String MODEL_ROOT = "assets/industrialcore/models/block/";
    private static final double EPSILON = 0.001D;

    /** name, axis (0=x 1=y 2=z), true when the block boundary sits on the low end of the axis. */
    private record Axis(String name, int index, boolean boundaryLow) {
    }

    private static final Axis[] DIRECTIONS = {
            new Axis("down", 1, true),
            new Axis("up", 1, false),
            new Axis("north", 2, true),
            new Axis("south", 2, false),
            new Axis("west", 0, true),
            new Axis("east", 0, false),
    };

    private static Box boundsOf(List<Box> boxes) {
        double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Box b : boxes) {
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], b.from[a]);
                max[a] = Math.max(max[a], b.to[a]);
            }
        }
        return new Box(min, max);
    }

    @Test
    void everyArmMeetsItsCoreCleanlyWithMatchingEnvelopeOnItsAxis() {
        for (Axis direction : DIRECTIONS) {
            for (String[] pipe : new String[][]{{"item_pipe_arm", ""}, {"pipe_arm", "_i"},
                    {"pipe_arm", "_a"}, {"pipe_arm", "_u"}, {"cable_arm", "_b"},
                    {"cable_arm", "_i"}, {"cable_arm", "_a"}, {"cable_arm", "_u"}}) {
                String name = pipe[0] + "_" + direction.name() + pipe[1];
                List<Box> boxes = boxes(name);
                assertTrue(boxes.size() >= 5,
                        name + " must be a multi-element 3D rail + conduit assembly");
                Box env = boundsOf(boxes);
                double from = env.from[direction.index()];
                double to = env.to[direction.index()];
                double near = direction.boundaryLow() ? from : 16.0D - to;
                double far = direction.boundaryLow() ? to : 16.0D - from;
                // Phase 78: the arm runs from the boundary to the flat core, which starts at the
                // tube radius (4.0 px cable -> 6.0, 5.0 px pipe -> 5.5).
                double expectedArmEnd = name.startsWith("cable_arm") ? 6.0D : 5.5D;
                assertEquals(0.0D, near, EPSILON, name + " does not start at its boundary");
                assertEquals(expectedArmEnd, far, EPSILON, name + " does not stop flush at core");
                double crossWidth = widthOf(env, (direction.index() + 1) % 3);
                assertEquals(crossWidth, widthOf(env, (direction.index() + 2) % 3),
                        EPSILON, name + " arm envelope is not square");
                assertEquals(expectedTube(name), crossWidth, EPSILON,
                        name + " arm width does not match TubeProfile");
            }
        }
    }

    @Test
    void noTwoDirectionsShareTheirEnvelope() {
        List<Box> seen = new ArrayList<>();
        for (Axis direction : DIRECTIONS) {
            Box env = boundsOf(boxes("item_pipe_arm_" + direction.name()));
            for (Box other : seen) {
                boolean same = true;
                for (int a = 0; a < 3; a++) {
                    if (Math.abs(env.from[a] - other.from[a]) > EPSILON
                            || Math.abs(env.to[a] - other.to[a]) > EPSILON) {
                        same = false;
                        break;
                    }
                }
                assertTrue(!same, "two directions share envelope: " + direction.name());
            }
            seen.add(env);
        }
    }

    @Test
    void pipeWidthsMatchTheirTier() {
        for (PipeTier tier : PipeTier.values()) {
            String suffix = switch (tier) {
                case BASIC -> "";
                case IMPROVED -> "_i";
                case ADVANCED -> "_a";
                case ULTIMATE -> "_u";
            };
            double expected = TubeProfile.pipeTube(tier);
            String stem = tier == PipeTier.BASIC ? "item_pipe_arm" : "pipe_arm";
            for (Axis direction : DIRECTIONS) {
                Box env = boundsOf(boxes(stem + "_" + direction.name() + suffix));
                assertEquals(expected, widthOf(env, (direction.index() + 1) % 3), EPSILON,
                        "pipe " + tier + " " + direction.name() + " width");
            }
            Box core = boundsOf(boxes((tier == PipeTier.BASIC ? "item_pipe_core" : "pipe_core") + suffix));
            assertEquals(TubeProfile.core(expected), widthOf(core, 0), EPSILON,
                    "pipe " + tier + " core width");
        }
    }

    @Test
    void cableWidthsMatchTheirTierAndStayThinnerThanPipes() {
        for (CableTier tier : CableTier.values()) {
            String suffix = switch (tier) {
                case BASIC -> "_b";
                case IMPROVED -> "_i";
                case ADVANCED -> "_a";
                case ULTIMATE -> "_u";
            };
            double expected = TubeProfile.cableTube(tier);
            for (Axis direction : DIRECTIONS) {
                Box env = boundsOf(boxes("cable_arm_" + direction.name() + suffix));
                assertEquals(expected, widthOf(env, (direction.index() + 1) % 3), EPSILON,
                        "cable " + tier + " " + direction.name() + " width");
                assertTrue(expected < TubeProfile.pipeTube(PipeTier.values()[tier.ordinal()]),
                        "cable " + tier + " should be thinner than the same pipe tier");
            }
        }
    }

    @Test
    void junctionCoresUseSmartMultiElementRailsAndNoCollar() {
        for (String stem : new String[]{"pipe_core", "cable_core", "item_pipe_core"}) {
            for (String suffix : new String[]{"", "_i", "_a", "_u"}) {
                String name = stem + suffix;
                if (!exists(name)) {
                    continue;
                }
                JsonObject model = model(name);
                JsonArray elements = model.getAsJsonArray("elements");
                assertTrue(elements.size() >= 10, name + " must keep the twelve rail junction cell");
                JsonObject textures = model.getAsJsonObject("textures");
                assertTrue(textures.has("frame"), name + " missing #frame");
                assertTrue(!textures.has("collar"), name + " must not declare a #collar texture");
                if (stem.contains("pipe")) {
                    assertEquals("minecraft:translucent", model.get("render_type").getAsString());
                    assertTrue(textures.has("glass"), name + " missing #glass");
                } else {
                    assertTrue(textures.has("core"), name + " missing #core");
                }
            }
        }
    }

    @Test
    void coreWidthMatchesTubeProfileAndAddsNoStep() {
        assertEquals(0.0D, TubeProfile.CORE_OVER_TUBE, EPSILON);
        assertEquals(5.0D, TubeProfile.core(TubeProfile.pipeTube(PipeTier.BASIC)), EPSILON);
        assertEquals(4.0D, TubeProfile.core(TubeProfile.cableTube(CableTier.BASIC)), EPSILON);
    }

    /** Tube width of an arm model, read from its file name. */
    private static double expectedTube(String name) {
        if (name.startsWith("cable_arm")) {
            String suffix = name.substring(name.lastIndexOf('_') + 1);
            CableTier tier = switch (suffix) {
                case "i" -> CableTier.IMPROVED;
                case "a" -> CableTier.ADVANCED;
                case "u" -> CableTier.ULTIMATE;
                default -> CableTier.BASIC;
            };
            return TubeProfile.cableTube(tier);
        }
        if (name.startsWith("item_pipe_arm")) {
            return TubeProfile.pipeTube(PipeTier.BASIC);
        }
        String suffix = name.substring(name.lastIndexOf('_') + 1);
        PipeTier tier = switch (suffix) {
            case "i" -> PipeTier.IMPROVED;
            case "a" -> PipeTier.ADVANCED;
            case "u" -> PipeTier.ULTIMATE;
            default -> PipeTier.BASIC;
        };
        return TubeProfile.pipeTube(tier);
    }

    // ------------------------------------------------------------------
    // model reading
    // ------------------------------------------------------------------

    private static boolean exists(String name) {
        return PipeModelProfileTest.class.getClassLoader()
                .getResource(MODEL_ROOT + name + ".json") != null;
    }

    private static JsonObject model(String name) {
        String path = MODEL_ROOT + name + ".json";
        InputStream stream = PipeModelProfileTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing model " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            fail("cannot read " + path + ": " + exception.getMessage());
            return null;
        }
    }

    private record Box(double[] from, double[] to) {
    }

    private static List<Box> boxes(String name) {
        JsonArray elements = model(name).getAsJsonArray("elements");
        List<Box> boxes = new ArrayList<>();
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            boxes.add(new Box(readArray(box.getAsJsonArray("from")), readArray(box.getAsJsonArray("to"))));
        }
        return boxes;
    }

    private static double[] readArray(JsonArray array) {
        double[] values = new double[3];
        for (int index = 0; index < 3; index++) {
            values[index] = array.get(index).getAsDouble();
        }
        return values;
    }

    private static double widthOf(Box box, int axis) {
        return box.to[axis] - box.from[axis];
    }
}
