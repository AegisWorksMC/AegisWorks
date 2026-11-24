package com.industrialcore.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.client.SoundRhythm;
import com.industrialcore.machine.BeatScanner;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.TransporterCasingPart;
import com.industrialcore.machine.TransporterShellStyle;
import com.industrialcore.machine.TransporterStructure;
import com.industrialcore.machine.TubeProfile;
import com.industrialcore.machine.port.PortTables;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 77 comprehensive verification test suite (Parts 1, 2, 4 & 5):
 * - 28 smart translucent pipes & 28 smart cables (zero arm-core volume overlap)
 * - 8 smart conduit item models & 64-state multipart blockstates
 * - 24 tc/qtc casing models & 48-variant blockstates + sizes 3/4/5 shell styling
 * - PortTables coverage across all generators, recovery devices & 4 transporters
 * - 100% ModBlocks coverage in minecraft:mineable/pickaxe.json
 * - 16x16 JAPPA texture color count & translucent pipe glass checks
 */
class Phase77Test {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA_PICKAXE = Path.of(
            "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json");
    private static final Path MOD_BLOCKS_JAVA = Path.of(
            "src/main/java/com/industrialcore/registry/ModBlocks.java");
    private static final List<String> DIRS = List.of("down", "up", "north", "south", "west", "east");

    private static JsonObject readJson(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static List<double[]> readBoxes(Path modelPath) throws IOException {
        JsonObject root = readJson(modelPath);
        List<double[]> out = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray lo = obj.getAsJsonArray("from");
            JsonArray hi = obj.getAsJsonArray("to");
            out.add(new double[] {
                    lo.get(0).getAsDouble(), lo.get(1).getAsDouble(), lo.get(2).getAsDouble(),
                    hi.get(0).getAsDouble(), hi.get(1).getAsDouble(), hi.get(2).getAsDouble()
            });
        }
        return out;
    }

    private static void assertZeroVolumeOverlap(List<double[]> aList, List<double[]> bList, String context) {
        for (double[] a : aList) {
            for (double[] b : bList) {
                boolean ox = a[3] > b[0] + 1e-6 && b[3] > a[0] + 1e-6;
                boolean oy = a[4] > b[1] + 1e-6 && b[4] > a[1] + 1e-6;
                boolean oz = a[5] > b[2] + 1e-6 && b[5] > a[2] + 1e-6;
                assertFalse(ox && oy && oz, context + " has core-arm AABB overlap");
            }
        }
    }

    @Test
    void all28PipeModelsAreTranslucentWithZeroCoreArmOverlap() throws IOException {
        String[][] specs = {
                {"item_pipe_core", "item_pipe_arm", ""},
                {"pipe_core_i", "pipe_arm", "_i"},
                {"pipe_core_a", "pipe_arm", "_a"},
                {"pipe_core_u", "pipe_arm", "_u"},
        };
        for (String[] sp : specs) {
            Path corePath = ASSETS.resolve("models/block/" + sp[0] + ".json");
            JsonObject coreObj = readJson(corePath);
            assertEquals("minecraft:translucent", coreObj.get("render_type").getAsString(), sp[0]);
            List<double[]> coreBoxes = readBoxes(corePath);
            assertTrue(coreBoxes.size() >= 10, sp[0] + " must be detailed multi-element glass chamber");

            for (String d : DIRS) {
                Path armPath = ASSETS.resolve("models/block/" + sp[1] + "_" + d + sp[2] + ".json");
                JsonObject armObj = readJson(armPath);
                assertEquals("minecraft:translucent", armObj.get("render_type").getAsString(), armPath.getFileName().toString());
                List<double[]> armBoxes = readBoxes(armPath);
                assertTrue(armBoxes.size() >= 5, armPath.getFileName() + " must use multi-element rail + glass walls");
                assertZeroVolumeOverlap(coreBoxes, armBoxes, sp[0] + " + " + d);
            }
        }
    }

    @Test
    void all28CableModelsHaveMultiElementRailsAndZeroCoreArmOverlap() throws IOException {
        for (String sfx : List.of("b", "i", "a", "u")) {
            Path corePath = ASSETS.resolve("models/block/cable_core_" + sfx + ".json");
            List<double[]> coreBoxes = readBoxes(corePath);
            assertTrue(coreBoxes.size() >= 10, "cable_core_" + sfx + " must use detailed rail + glow core");
            for (String d : DIRS) {
                Path armPath = ASSETS.resolve("models/block/cable_arm_" + d + "_" + sfx + ".json");
                List<double[]> armBoxes = readBoxes(armPath);
                assertTrue(armBoxes.size() >= 5, "cable_arm_" + d + "_" + sfx + " must use rail + collar + core");
                assertZeroVolumeOverlap(coreBoxes, armBoxes, "cable_" + sfx + " + " + d);
            }
        }
    }

    @Test
    void conduitItemModelsAreFullHeightSmartSegments() throws IOException {
        // Phase 79: the item model now inherits the shipped vertical straight model instead of
        // duplicating its elements. The check follows the parent chain, so the segment the player
        // sees in the inventory is still the full-height conduit the 3D block uses.
        for (String name : List.of("item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate",
                "cable_basic", "cable_improved", "cable_advanced", "cable_ultimate")) {
            Path p = ASSETS.resolve("models/item/" + name + ".json");
            assertTrue(Files.exists(p), "missing item model " + name);
            JsonObject obj = readJson(p);
            assertTrue(obj.has("display"), name + " missing display transforms");
            assertTrue(obj.has("parent"), name + " must inherit the vertical straight conduit");
            String parent = obj.get("parent").getAsString();
            assertTrue(parent.contains("_str_y_"), name + " must point at a vertical straight model");
            Path modelPath = ASSETS.resolve("models/"
                    + parent.substring(parent.indexOf(':') + 1) + ".json");
            assertTrue(Files.exists(modelPath), name + " parent model is missing");
            JsonObject model = readJson(modelPath);
            if (name.contains("pipe")) {
                assertEquals("minecraft:translucent", model.get("render_type").getAsString());
            }
            double minY = 99, maxY = -99;
            for (double[] b : readBoxes(modelPath)) {
                minY = Math.min(minY, b[1]);
                maxY = Math.max(maxY, b[4]);
            }
            assertEquals(0.0, minY, 1e-4, name + " item minY");
            assertEquals(16.0, maxY, 1e-4, name + " item maxY");
        }
    }

    @Test
    void conduitBlockstatesPreserveExact64StateSixBooleanContract() throws IOException {
        Set<String> expectedProps = Set.of("north", "east", "south", "west", "up", "down");
        for (String name : List.of("item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate",
                "cable_basic", "cable_improved", "cable_advanced", "cable_ultimate")) {
            JsonObject bs = readJson(ASSETS.resolve("blockstates/" + name + ".json"));
            Set<String> found = new HashSet<>();
            for (JsonElement el : bs.getAsJsonArray("multipart")) {
                JsonObject entry = el.getAsJsonObject();
                if (entry.has("when")) {
                    found.addAll(entry.getAsJsonObject("when").keySet());
                }
            }
            assertEquals(expectedProps, found, name + " blockstate must use strictly 6 boolean properties");
        }
    }

    @Test
    void transporterCasing48VariantsAnd24ModelsValidAndNonIntersecting() throws IOException {
        for (String fam : List.of("tc", "qtc")) {
            for (int pid = 0; pid < 12; pid++) {
                Path mp = ASSETS.resolve("models/block/" + fam + "_" + pid + ".json");
                assertTrue(Files.exists(mp), "missing casing model " + mp.getFileName());
                List<double[]> boxes = readBoxes(mp);
                assertTrue(!boxes.isEmpty());
                for (int i = 0; i < boxes.size(); i++) {
                    for (int j = i + 1; j < boxes.size(); j++) {
                        double[] a = boxes.get(i);
                        double[] b = boxes.get(j);
                        boolean ox = a[3] > b[0] + 1e-6 && b[3] > a[0] + 1e-6;
                        boolean oy = a[4] > b[1] + 1e-6 && b[4] > a[1] + 1e-6;
                        boolean oz = a[5] > b[2] + 1e-6 && b[5] > a[2] + 1e-6;
                        assertFalse(ox && oy && oz, fam + "_" + pid + " intersection");
                    }
                }
            }
        }
        for (String bsName : List.of("transporter_casing", "quantum_transporter_casing")) {
            JsonObject bs = readJson(ASSETS.resolve("blockstates/" + bsName + ".json"));
            JsonObject vars = bs.getAsJsonObject("variants");
            assertEquals(48, vars.size(), bsName + " must define 48 (12 parts x 4 facings) variants");
            for (int pid = 0; pid < 12; pid++) {
                for (String facing : List.of("north", "east", "south", "west")) {
                    assertTrue(vars.has("part=" + pid + ",facing=" + facing), bsName + " missing part=" + pid);
                }
            }
        }
    }

    @Test
    void transporterShellStyleCoversSizes3And4And5Cleanly() {
        for (int size : List.of(3, 4, 5)) {
            int count = 0;
            for (TransporterStructure.ShellCell cell : TransporterStructure.shellCells(size, 0, 0, 0, 2)) {
                TransporterShellStyle.CasingStyle st = TransporterShellStyle.styleFor(
                        size, cell.localX(), cell.localY(), cell.localZ(), 2);
                if (cell.controller()) {
                    assertEquals(null, st, "controller must return null for size " + size);
                } else {
                    assertNotNull(st, "casing must get style for size " + size);
                    count++;
                }
            }
            int expected = size == 3 ? 25 : (size == 4 ? 55 : 97);
            assertEquals(expected, count, "shell casing count for size " + size);
        }
    }

    @Test
    void portTablesIncludeAllGeneratorsRecoveryAndFourTransporters() {
        List<String> expected = List.of(
                "thermal_generator", "hydro_generator", "wind_generator", "solar_generator",
                "combustion_generator", "nuclear_generator", "geothermal_generator",
                "flywheel", "pressure_recovery", "heat_recovery", "regen_generator",
                "quantum_transporter", "basic_item_transporter",
                "advanced_item_transporter", "quantum_transporter_array"
        );
        for (String name : expected) {
            assertNotNull(PortTables.layout(name), "PortTables missing " + name);
            assertFalse(PortTables.layout(name).isEmpty(), name + " port layout empty");
        }
    }

    @Test
    void everyRegisteredBlockInModBlocksIsCoveredByPickaxeTag() throws IOException {
        String javaSrc = Files.readString(MOD_BLOCKS_JAVA);
        Set<String> registered = new HashSet<>();
        Matcher m1 = Pattern.compile(
                "BLOCKS\\.register\\(\"([a-z0-9_]+)\"|register(?:Pipe|Cable|Ore|Casing|Power|Recovery)\\(\"([a-z0-9_]+)\"")
                .matcher(javaSrc);
        while (m1.find()) {
            String id = m1.group(1) != null ? m1.group(1) : m1.group(2);
            registered.add("industrialcore:" + id);
        }
        assertTrue(registered.size() >= 55, "Parsed ModBlocks block count too small: " + registered.size());

        JsonArray values = readJson(DATA_PICKAXE).getAsJsonArray("values");
        Set<String> tagSet = new HashSet<>();
        for (JsonElement el : values) {
            tagSet.add(el.getAsString());
        }
        for (String blockId : registered) {
            assertTrue(tagSet.contains(blockId), blockId + " missing from minecraft:mineable/pickaxe.json");
        }
    }

    @Test
    void allTexturesMeet16x16DimensionMinimum7ColorsAndTranslucentPipeGlass() throws IOException {
        Set<String> hdItems32 = Set.of(
                "virtual_helmet.png", "virtual_chestplate.png", "virtual_leggings.png", "virtual_boots.png",
                "pulse_laser.png", "railgun.png", "quantum_blade.png", "energy_bow.png");
        try (Stream<Path> s = Files.list(ASSETS.resolve("textures/item"))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".png")).toList()) {
                BufferedImage img = ImageIO.read(p.toFile());
                int expected = hdItems32.contains(p.getFileName().toString()) ? 32 : 16;
                assertEquals(expected, img.getWidth(), p.getFileName().toString());
                assertEquals(expected, img.getHeight(), p.getFileName().toString());
                Set<Integer> cols = new HashSet<>();
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        int argb = img.getRGB(x, y);
                        if (((argb >>> 24) & 0xFF) >= 128) {
                            cols.add(argb & 0x00FFFFFF);
                        }
                    }
                }
                assertTrue(cols.size() >= 7, p.getFileName() + " needs >= 7 opaque colors, got " + cols.size());
            }
        }
        for (String sfx : List.of("b", "i", "a", "u")) {
            BufferedImage glass = ImageIO.read(
                    ASSETS.resolve("textures/block/pipe_glass_" + sfx + ".png").toFile());
            boolean hasTranslucent = false;
            boolean inBand = true;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int a = (glass.getRGB(x, y) >>> 24) & 0xFF;
                    if (a > 0 && a < 128) hasTranslucent = true;
                    // Phase 78: the glass stays inside the 50..75 alpha window everywhere, so a
                    // continuous tube has no bright rim line at a block boundary.
                    if (a < 50 || a > 75) inBand = false;
                }
            }
            assertTrue(hasTranslucent, "pipe_glass_" + sfx + " must have translucent window pixels");
            assertTrue(inBand, "pipe_glass_" + sfx + " must stay inside the 50..75 alpha band");
        }
    }

    @Test
    void soundRhythmExtendedCountersBeatScannerAndTubeProfileValid() {
        assertEquals(5.0D, TubeProfile.pipeTube(PipeTier.BASIC), 1e-6);
        assertEquals(4.0D, TubeProfile.cableTube(CableTier.BASIC), 1e-6);
        assertEquals(5.0D, TubeProfile.core(TubeProfile.pipeTube(PipeTier.BASIC)), 1e-6);
        assertEquals(4.0D, TubeProfile.core(TubeProfile.cableTube(CableTier.BASIC)), 1e-6);
        assertEquals(12, BeatScanner.SCAN_RADIUS);

        SoundRhythm.resetCounters();
        SoundRhythm.recordOpenClose();
        SoundRhythm.recordHoverTick();
        SoundRhythm.recordSlotAction();
        SoundRhythm.recordMilestone();
        SoundRhythm.recordWorldBeat();
        assertEquals(1, SoundRhythm.getOpenCloseCount());
        assertEquals(1, SoundRhythm.getHoverTickCount());
        assertEquals(1, SoundRhythm.getSlotActionCount());
        assertEquals(1, SoundRhythm.getMilestoneCount());
        assertEquals(1, SoundRhythm.getWorldBeatCount());
    }
}
