package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.client.GuiDopamine;
import com.industrialcore.client.SoundRhythm;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.TubeProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 78 verification: seamless conduits, the 24 step Shepard riser and the item flash.
 *
 * <h2>Conduits</h2>
 *
 * <p>Every "mysterious outline" in game came from a static model: a {@code #collar} ring, a core that
 * was 1 px wider than the tube, a glass pane on a core face an arm already opened, or an end face on
 * the block boundary. These checks read the shipped JSON and fail if any of the four returns.</p>
 *
 * <p>The block states are checked by simulating them, not by grepping: for all 64 combinations of
 * the six connection booleans the test collects the models the multipart applies and proves that a
 * straight run applies exactly its one straight model, and that a branch applies one core, one arm
 * per connection and one cap per armless face. That is what keeps a straight run free of the extra
 * parts a composition would bring.</p>
 */
class Phase78Test {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final List<String> PROPS = List.of("north", "east", "south", "west", "up", "down");
    private static final List<String> DIRS = List.of("down", "up", "north", "south", "west", "east");
    private static final String[] AXIS_PAIR = {"west,east", "down,up", "north,south"};

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<double[]> boxes(Path modelPath) throws IOException {
        List<double[]> out = new ArrayList<>();
        for (JsonElement el : json(modelPath).getAsJsonArray("elements")) {
            JsonObject o = el.getAsJsonObject();
            JsonArray lo = o.getAsJsonArray("from");
            JsonArray hi = o.getAsJsonArray("to");
            out.add(new double[]{lo.get(0).getAsDouble(), lo.get(1).getAsDouble(), lo.get(2).getAsDouble(),
                    hi.get(0).getAsDouble(), hi.get(1).getAsDouble(), hi.get(2).getAsDouble()});
        }
        return out;
    }

    private static boolean intersects(double[] a, double[] b) {
        boolean ox = a[3] > b[0] + 1e-6 && b[3] > a[0] + 1e-6;
        boolean oy = a[4] > b[1] + 1e-6 && b[4] > a[1] + 1e-6;
        boolean oz = a[5] > b[2] + 1e-6 && b[5] > a[2] + 1e-6;
        return ox && oy && oz;
    }

    /**
     * The eight conduits: tier, core model, arm model format, straight prefix, cap prefix, blockstate.
     * Cables first, then pipes.
     */
    private static List<String[]> conduits() {
        List<String[]> out = new ArrayList<>();
        String[] cableStates = {"cable_basic", "cable_improved", "cable_advanced", "cable_ultimate"};
        int index = 0;
        for (String t : List.of("b", "i", "a", "u")) {
            out.add(new String[]{t, "cable_core_" + t, "cable_arm_%s_" + t, "cb_str", "cb_cap",
                    cableStates[index++]});
        }
        String[][] pipes = {{"b", "item_pipe_core"}, {"i", "pipe_core_i"}, {"a", "pipe_core_a"}, {"u", "pipe_core_u"}};
        String[] armStem = {"item_pipe_arm_%s", "pipe_arm_%s_i", "pipe_arm_%s_a", "pipe_arm_%s_u"};
        String[] pipeStates = {"item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate"};
        for (int i = 0; i < 4; i++) {
            out.add(new String[]{pipes[i][0], pipes[i][1], armStem[i], "pp_str", "pp_cap", pipeStates[i]});
        }
        return out;
    }

    @Test
    void armsAndStraightsHaveNoCollarAndNoFaceOnABlockBoundary() throws IOException {
        int checked = 0;
        for (String[] c : conduits()) {
            String t = c[0];
            List<String> models = new ArrayList<>();
            for (String d : DIRS) {
                models.add(String.format(c[2], d));
            }
            for (String axis : List.of("x", "y", "z")) {
                models.add(c[3] + "_" + axis + "_" + t);
            }
            for (String name : models) {
                Path path = ASSETS.resolve("models/block/" + name + ".json");
                assertTrue(Files.exists(path), "missing model " + name);
                JsonObject model = json(path);
                assertFalse(model.getAsJsonObject("textures").has("collar"),
                        name + " still declares a collar texture");
                for (JsonElement el : model.getAsJsonArray("elements")) {
                    JsonObject element = el.getAsJsonObject();
                    JsonObject faces = element.getAsJsonObject("faces");
                    for (String side : faces.keySet()) {
                        assertFalse(faces.getAsJsonObject(side).get("texture").getAsString().contains("collar"),
                                name + " still draws a collar element");
                        JsonArray lo = element.getAsJsonArray("from");
                        JsonArray hi = element.getAsJsonArray("to");
                        int axis = switch (side) {
                            case "west", "east" -> 0;
                            case "down", "up" -> 1;
                            default -> 2;
                        };
                        boolean low = side.equals("west") || side.equals("down") || side.equals("north");
                        double plane = low ? lo.get(axis).getAsDouble() : hi.get(axis).getAsDouble();
                        assertTrue(plane != 0.0D && plane != 16.0D,
                                name + " has an end face on the block boundary (" + side + "=" + plane + ")");
                    }
                }
                checked++;
            }
        }
        assertEquals(72, checked, "48 arms + 24 straight models");
    }

    @Test
    void coreIsFlatAndExactlyAsWideAsTheTubeWithNoArmIntersection() throws IOException {
        assertEquals(0.0D, TubeProfile.CORE_OVER_TUBE, 1e-9D, "the core must no longer stand proud");
        for (PipeTier tier : PipeTier.values()) {
            assertEquals(TubeProfile.pipeTube(tier), TubeProfile.core(TubeProfile.pipeTube(tier)), 1e-9D);
        }
        for (CableTier tier : CableTier.values()) {
            assertEquals(TubeProfile.cableTube(tier), TubeProfile.core(TubeProfile.cableTube(tier)), 1e-9D);
        }
        for (String[] c : conduits()) {
            Path corePath = ASSETS.resolve("models/block/" + c[1] + ".json");
            List<double[]> coreBoxes = boxes(corePath);
            double width = 0.0D;
            double low = 99.0D;
            double high = -99.0D;
            for (double[] b : coreBoxes) {
                low = Math.min(low, b[0]);
                high = Math.max(high, b[3]);
            }
            width = high - low;
            assertTrue(coreBoxes.size() >= 10, c[1] + " must keep the twelve rail junction cell");
            for (String d : DIRS) {
                List<double[]> arm = boxes(ASSETS.resolve("models/block/" + String.format(c[2], d) + ".json"));
                double armLow = 99.0D;
                double armHigh = -99.0D;
                for (double[] b : arm) {
                    armLow = Math.min(armLow, Math.min(b[1], Math.min(b[0], b[2])));
                    armHigh = Math.max(armHigh, Math.max(b[4], Math.max(b[3], b[5])));
                }
                for (double[] a : coreBoxes) {
                    for (double[] b : arm) {
                        assertFalse(intersects(a, b), c[1] + " intersects its " + d + " arm");
                    }
                }
                // the arm's own envelope has to be the tube width on both cross axes
                double cross = 0.0D;
                double crossLow = 99.0D;
                double crossHigh = -99.0D;
                int axis = switch (d) {
                    case "west", "east" -> 0;
                    case "down", "up" -> 1;
                    default -> 2;
                };
                int u = (axis + 1) % 3;
                for (double[] b : arm) {
                    crossLow = Math.min(crossLow, b[u]);
                    crossHigh = Math.max(crossHigh, b[u + 3]);
                }
                cross = crossHigh - crossLow;
                assertTrue(Math.abs(cross - width) < 1e-6,
                        c[1] + " core width " + width + " does not match the " + d + " arm width " + cross);
            }
        }
    }

    @Test
    void conduitTexturesAreSeamlessAndGlassKeepsTheTranslucentBand() throws IOException {
        List<String> names = new ArrayList<>(List.of("conduit_frame"));
        for (String t : List.of("b", "i", "a", "u")) {
            names.add("cable_core_" + t);
            names.add("pipe_glass_" + t);
        }
        for (String name : names) {
            BufferedImage img = ImageIO.read(ASSETS.resolve("textures/block/" + name + ".png").toFile());
            double edge = 0.0D;
            double inner = 0.0D;
            int edgeCount = 0;
            int innerCount = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double lum = luminance(img.getRGB(x, y));
                    boolean border = x == 0 || y == 0 || x == 15 || y == 15;
                    if (border) {
                        edge += lum;
                        edgeCount++;
                    } else {
                        inner += lum;
                        innerCount++;
                    }
                }
            }
            double edgeMean = edge / edgeCount;
            double innerMean = inner / innerCount;
            assertTrue(edgeMean >= innerMean * 0.75,
                    name + " still has a dark border line (edge " + edgeMean + " vs inner " + innerMean + ")");
            // The artwork is periodic with 8 px, so a tile copied beside another one is continuous:
            // the seam at 16 px is exactly the same transition as the one at 8 px, never a line.
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(img.getRGB(x, y), img.getRGB(x + 8, y),
                            name + " is not horizontally tileable at " + x + "," + y);
                    assertEquals(img.getRGB(y, x), img.getRGB(y, x + 8),
                            name + " is not vertically tileable at " + y + "," + x);
                }
            }
            for (int i = 0; i < 16; i++) {
                double seam = Math.abs(luminance(img.getRGB(0, i)) - luminance(img.getRGB(15, i)));
                double internal = 0.0D;
                for (int x = 0; x < 15; x++) {
                    internal = Math.max(internal,
                            Math.abs(luminance(img.getRGB(x, i)) - luminance(img.getRGB(x + 1, i))));
                }
                assertTrue(seam <= internal + 1e-6,
                        name + " row " + i + " has a seam brighter than an internal edge ("
                                + seam + " > " + internal + ")");
                seam = Math.abs(luminance(img.getRGB(i, 0)) - luminance(img.getRGB(i, 15)));
                internal = 0.0D;
                for (int y = 0; y < 15; y++) {
                    internal = Math.max(internal,
                            Math.abs(luminance(img.getRGB(i, y)) - luminance(img.getRGB(i, y + 1))));
                }
                assertTrue(seam <= internal + 1e-6,
                        name + " column " + i + " has a seam brighter than an internal edge ("
                                + seam + " > " + internal + ")");
            }
        }
        for (String t : List.of("b", "i", "a", "u")) {
            BufferedImage glass = ImageIO.read(
                    ASSETS.resolve("textures/block/pipe_glass_" + t + ".png").toFile());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int alpha = (glass.getRGB(x, y) >>> 24) & 0xFF;
                    assertTrue(alpha >= 50 && alpha <= 75,
                            "pipe_glass_" + t + " alpha " + alpha + " outside the 50..75 window");
                }
            }
        }
    }

    @Test
    void everyStateAppliesExactlyOneShapeSixBooleansAnd64States() throws IOException {
        Set<String> expected = new HashSet<>(PROPS);
        for (String[] c : conduits()) {
            String blockstate = c[5];
            JsonObject bs = json(ASSETS.resolve("blockstates/" + blockstate + ".json"));
            JsonArray multipart = bs.getAsJsonArray("multipart");
            Set<String> used = new HashSet<>();
            for (JsonElement el : multipart) {
                JsonObject when = el.getAsJsonObject().getAsJsonObject("when");
                if (when != null) {
                    used.addAll(when.keySet());
                }
            }
            assertEquals(expected, used, blockstate + " must select on the six connection booleans only");

            for (int bits = 0; bits < 64; bits++) {
                List<String> applied = new ArrayList<>();
                for (JsonElement el : multipart) {
                    JsonObject entry = el.getAsJsonObject();
                    JsonObject when = entry.getAsJsonObject("when");
                    if (when == null || !matches(when, bits)) {
                        continue;
                    }
                    String model = entry.getAsJsonObject("apply").get("model").getAsString();
                    applied.add(model.substring(model.lastIndexOf('/') + 1));
                }
                int straightAxis = straightAxis(bits);
                int connected = 0;
                for (int i = 0; i < 6; i++) {
                    connected += (bits >> i) & 1;
                }
                if (straightAxis >= 0) {
                    assertEquals(1, applied.size(), blockstate + " straight state " + bits + " applies " + applied);
                    assertTrue(applied.get(0).contains("_str_"), blockstate + " straight state applies " + applied);
                } else {
                    long straights = applied.stream().filter(n -> n.contains("_str_")).count();
                    long cores = applied.stream().filter(n -> n.contains("_core")).count();
                    long arms = applied.stream().filter(n -> n.contains("_arm_")).count();
                    long caps = applied.stream().filter(n -> n.contains("_cap_")).count();
                    assertEquals(0, straights, blockstate + " branch state " + bits + " has a straight model");
                    assertEquals(1, cores, blockstate + " branch state " + bits + " core count");
                    assertEquals(connected, arms, blockstate + " branch state " + bits + " arm count");
                    assertEquals(6 - connected, caps, blockstate + " branch state " + bits + " cap count");
                }
            }
        }
    }

    @Test
    void twentyFourStraightsAndFortyEightCapsAreShipped() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS.resolve("models/block"))) {
            List<String> names = files.map(p -> p.getFileName().toString()).toList();
            assertEquals(24, names.stream().filter(n -> n.contains("_str_")).count(), "straight models");
            assertEquals(48, names.stream().filter(n -> n.contains("_cap_")).count(), "cap models");
            assertEquals(48, names.stream().filter(n -> n.contains("_arm_")).count(), "arm models");
        }
    }

    @Test
    void shepardRiserStaysInsideTwoOctavesAndWrapsWithoutAJump() {
        for (int step = 0; step <= 96; step++) {
            float low = SoundRhythm.shepardLowPitch(step);
            float high = SoundRhythm.shepardHighPitch(step);
            assertTrue(low >= 0.5f && low < 1.0f, "low voice out of range at " + step + ": " + low);
            assertTrue(high >= 1.0f && high < 2.0f, "high voice out of range at " + step + ": " + high);
            assertEquals(1.0f, SoundRhythm.shepardLowWeight(step) + SoundRhythm.shepardHighWeight(step),
                    1e-4f, "bell weights must stay a constant power pair at " + step);
        }
        // the low voice of step 23 lands on the high voice of step 24: the ear hears no wrap
        assertTrue(SoundRhythm.shepardLowPitch(23) > 0.96f);
        assertTrue(SoundRhythm.shepardLowWeight(23) > 0.99f);
        assertEquals(1.0f, SoundRhythm.shepardHighPitch(24), 1e-6f);
        assertEquals(1.0f, SoundRhythm.shepardHighWeight(24), 1e-6f);
        assertEquals(SoundRhythm.shepardTheta(0), SoundRhythm.shepardTheta(24), 1e-6f);
    }

    @Test
    void reachZoneDoublesThePulseRateAndWorldStaysQuiet() {
        assertEquals(4, SoundRhythm.pulsePeriod(0.10));
        assertEquals(4, SoundRhythm.pulsePeriod(0.779));
        assertEquals(2, SoundRhythm.pulsePeriod(0.78));
        assertEquals(2, SoundRhythm.pulsePeriod(0.99));
        assertFalse(SoundRhythm.isReachZone(0.5));
        assertTrue(SoundRhythm.isReachZone(0.95));

        assertEquals(20, SoundRhythm.WORLD_AMBIENT_TICKS, "one ambient tick per second");
        assertEquals(1, SoundRhythm.MAX_WORLD_SOUNDS_PER_BEAT, "at most one world sound");
        assertEquals(8, SoundRhythm.WORLD_RADIUS);
        assertTrue(SoundRhythm.WORLD_VOLUME_MAX <= 0.025f + 1e-6f, "world ambience must stay faint");
        assertTrue(SoundRhythm.WORLD_VOLUME_MIN >= 0.012f - 1e-6f);
        assertEquals(0.0f, SoundRhythm.worldVolume(8.0), 1e-6f);
        assertTrue(SoundRhythm.worldVolume(0.0) > SoundRhythm.worldVolume(4.0));
        assertTrue(SoundRhythm.worldVolume(4.0) >= SoundRhythm.WORLD_VOLUME_MIN);
        assertTrue(SoundRhythm.WORLD_PITCH_MIN < SoundRhythm.WORLD_PITCH_MAX
                && SoundRhythm.WORLD_PITCH_MAX < 1.0f, "world hums must stay low pitched");
    }

    @Test
    void rarityTiersFollowCountsAndStagesAndTheMythicTierShimmers() {
        assertEquals(1, GuiDopamine.rarityTier(1, 0));
        assertEquals(1, GuiDopamine.rarityTier(3, 0));
        assertEquals(2, GuiDopamine.rarityTier(4, 0));
        assertEquals(2, GuiDopamine.rarityTier(7, 0));
        assertEquals(3, GuiDopamine.rarityTier(8, 0));
        assertEquals(3, GuiDopamine.rarityTier(15, 0));
        assertEquals(4, GuiDopamine.rarityTier(16, 0));
        assertEquals(5, GuiDopamine.rarityTier(32, 0));
        assertEquals(5, GuiDopamine.rarityTier(64, 0));
        assertEquals(2, GuiDopamine.rarityTier(1, 4));
        assertEquals(3, GuiDopamine.rarityTier(1, 7));
        assertEquals(4, GuiDopamine.rarityTier(1, 11));
        assertEquals(5, GuiDopamine.rarityTier(1, 14));

        assertEquals(0x5CFFA0, GuiDopamine.tierColor(1, 0L));
        assertEquals(0x3BE2FF, GuiDopamine.tierColor(2, 0L));
        assertTrue(GuiDopamine.tierColor(5, 0L) != GuiDopamine.tierColor(5, 700L),
                "the mythic tier must shimmer over time");

        GuiDopamine.reset();
        GuiDopamine.triggerCompletion(50, 40, 64, 15);
        assertEquals(1, GuiDopamine.getSheenCount(), "a completion sweeps the item icon");
        assertEquals(1, GuiDopamine.getPopupCount());
        GuiDopamine.reset();
        assertEquals(0, GuiDopamine.getSheenCount());
    }

    @Test
    void shepardCountersReachPastTheTwentyFourStepOctave() {
        SoundRhythm.resetCounters();
        for (int i = 0; i < 26; i++) {
            SoundRhythm.shepardAdvance();
        }
        assertEquals(26, SoundRhythm.shepardStep());
        assertTrue(SoundRhythm.getShepardMaxStepReached() >= 25,
                "the riser has to pass 24 without resetting");
        SoundRhythm.recordReachPulse();
        SoundRhythm.recordReachPulse();
        assertEquals(2, SoundRhythm.getReachRollCount());
        assertEquals(2, SoundRhythm.getPulseCount());
        SoundRhythm.recordComplete(64, 1);
        assertEquals(1, SoundRhythm.getJackpotFanfareCount());
        SoundRhythm.recordComplete(4, 1);
        assertEquals(1, SoundRhythm.getMidArpeggioCount());
        SoundRhythm.recordComplete(1, 1);
        assertEquals(3, SoundRhythm.getChimeCount());
        assertTrue(SoundRhythm.audible(0.5f));
        assertFalse(SoundRhythm.audible(0.01f));
        SoundRhythm.resetShepard();
        assertEquals(0, SoundRhythm.shepardStep());
    }

    // ------------------------------------------------------------------

    private static double luminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    /** Bit order matches {@link #PROPS}: north, east, south, west, up, down. */
    private static boolean matches(JsonObject when, int bits) {
        for (String key : when.keySet()) {
            boolean flag = ((bits >> PROPS.indexOf(key)) & 1) != 0;
            String value = when.get(key).getAsString();
            if (flag != value.equals("true")) {
                return false;
            }
        }
        return true;
    }

    private static int straightAxis(int bits) {
        for (int axis = 0; axis < 3; axis++) {
            String[] pair = AXIS_PAIR[axis].split(",");
            boolean both = ((bits >> PROPS.indexOf(pair[0])) & 1) != 0
                    && ((bits >> PROPS.indexOf(pair[1])) & 1) != 0;
            boolean others = true;
            for (String p : PROPS) {
                if (p.equals(pair[0]) || p.equals(pair[1])) {
                    continue;
                }
                if (((bits >> PROPS.indexOf(p)) & 1) != 0) {
                    others = false;
                }
            }
            if (both && others) {
                return axis;
            }
        }
        return -1;
    }
}
