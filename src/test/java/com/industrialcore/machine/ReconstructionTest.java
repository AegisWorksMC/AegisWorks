package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.ReconstructorLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the Phase 15 matter reconstructor.
 *
 * <p>The tests cover the parts that can be checked without a running server:</p>
 * <ul>
 *   <li>the unlocked mineral set (add, duplicate, switch, reject of a locked mineral),</li>
 *   <li>the shipped recipe files against the design table,</li>
 *   <li>the loop protection: the result is always raw ore, no raw ore is an input and there is no
 *       recipe that turns ingots or dust into a reconstruction crystal,</li>
 *   <li>the GUI geometry (no overlap, nothing outside the panel),</li>
 *   <li>the status enum (the new states are appended, so the old ordinals do not move) and the
 *       action ids of the reconstructor's own payload.</li>
 * </ul>
 *
 * <p>What is NOT covered here: the behaviour of the block entity in a world (tick order, energy
 * consumption, save and load). That is covered by the GameTests in
 * {@code com.industrialcore.gametest.ReconstructorGameTests}.</p>
 */
class ReconstructionTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");

    /** One row of the design table of the Phase 15 specification. */
    private record Design(String mineral, String sample, String material, int materialCount,
                          int time, int energy, String result) {}

    private static final List<Design> DESIGNS = List.of(
            new Design("iron", "industrialcore:pure_iron", "minecraft:stone", 8, 200, 20,
                    "minecraft:raw_iron"),
            new Design("copper", "industrialcore:pure_copper", "minecraft:stone", 8, 200, 20,
                    "minecraft:raw_copper"),
            new Design("gold", "industrialcore:pure_gold", "minecraft:deepslate", 8, 400, 30,
                    "minecraft:raw_gold"));

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static JsonObject read(Path folder, String name) throws IOException {
        return JsonParser.parseString(Files.readString(folder.resolve(name))).getAsJsonObject();
    }

    private static String ingredientId(JsonObject recipe, String field) {
        JsonElement ingredient = recipe.get(field);
        if (ingredient == null || !ingredient.isJsonObject()) {
            return "";
        }
        JsonObject object = ingredient.getAsJsonObject();
        JsonElement item = object.get("item");
        if (item != null) {
            return item.getAsString();
        }
        JsonElement tag = object.get("tag");
        return tag == null ? "" : "#" + tag.getAsString();
    }

    private static String resultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null || !result.isJsonObject()) {
            return "";
        }
        JsonElement id = result.getAsJsonObject().get("id");
        return id == null ? "" : id.getAsString();
    }

    private static int resultCount(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null || !result.isJsonObject()) {
            return 1;
        }
        JsonElement count = result.getAsJsonObject().get("count");
        return count == null ? 1 : count.getAsInt();
    }

    private static int intOf(JsonObject recipe, String field) {
        JsonElement value = recipe.get(field);
        return value == null ? 0 : value.getAsInt();
    }

    // ------------------------------------------------------------------
    // The unlocked mineral set
    // ------------------------------------------------------------------

    @Test
    void unlockedMineralsKeepTheUnlockOrder() {
        ReconstructorMinerals minerals = new ReconstructorMinerals();
        assertTrue(minerals.unlock("iron"));
        assertTrue(minerals.unlock("copper"));
        assertTrue(minerals.unlock("gold"));
        assertEquals(List.of("iron", "copper", "gold"), minerals.unlocked());
        assertEquals(3, minerals.size());
        assertFalse(minerals.isEmpty());
    }

    @Test
    void theSameMineralIsOnlyUnlockedOnce() {
        ReconstructorMinerals minerals = new ReconstructorMinerals();
        assertTrue(minerals.unlock("iron"));
        assertFalse(minerals.unlock("iron"), "the same mineral must not be unlocked twice");
        assertFalse(minerals.unlock(""), "an empty key must be rejected");
        assertEquals(1, minerals.size());
        assertTrue(minerals.isUnlocked("iron"));
    }

    @Test
    void onlyUnlockedMineralsCanBeSelected() {
        ReconstructorMinerals minerals = new ReconstructorMinerals();
        minerals.unlock("iron");
        assertFalse(minerals.select("gold"), "a locked mineral must not be selectable");
        assertEquals("", minerals.selected());
        assertTrue(minerals.select("iron"));
        assertEquals("iron", minerals.selected());
    }

    @Test
    void cycleWalksThroughTheUnlockedMinerals() {
        ReconstructorMinerals minerals = new ReconstructorMinerals();
        assertEquals("", minerals.cycle(), "an empty machine has nothing to cycle to");
        minerals.unlock("iron");
        minerals.unlock("copper");
        assertEquals("iron", minerals.cycle());
        assertEquals("copper", minerals.cycle());
        assertEquals("iron", minerals.cycle(), "the cycle must wrap around");
    }

    @Test
    void restoreDropsAnUnknownSelection() {
        ReconstructorMinerals minerals = new ReconstructorMinerals();
        minerals.restore(List.of("iron", "copper"), "gold");
        assertEquals(List.of("iron", "copper"), minerals.unlocked());
        assertEquals("", minerals.selected(), "a selection outside the restored set must be dropped");
        minerals.restore(List.of("iron", "copper"), "copper");
        assertEquals("copper", minerals.selected());
    }

    // ------------------------------------------------------------------
    // The shipped recipe files
    // ------------------------------------------------------------------

    @Test
    void shippedRecipesMatchTheDesignTable() throws IOException {
        for (Design design : DESIGNS) {
            JsonObject recipe = read(RECIPES, "reconstruct_" + design.mineral() + ".json");
            assertEquals("industrialcore:reconstruction", recipe.get("type").getAsString());
            assertEquals(design.mineral(), recipe.get("mineral").getAsString());
            assertEquals(design.sample(), ingredientId(recipe, "analysis_sample"));
            assertEquals(design.material(), ingredientId(recipe, "material"));
            assertEquals(design.materialCount(), intOf(recipe, "material_count"));
            assertEquals("industrialcore:reconstruction_crystal", ingredientId(recipe, "crystal"));
            assertEquals(1, intOf(recipe, "crystal_count"));
            assertEquals(design.result(), resultId(recipe));
            assertEquals(1, resultCount(recipe));
            assertEquals(design.time(), intOf(recipe, "time"));
            assertEquals(design.energy(), intOf(recipe, "energy"));
        }
    }

    @Test
    void theBalanceConstantsMatchTheDesignTable() {
        assertEquals(20_000, ReconstructorBalance.CAPACITY);
        assertEquals(500, ReconstructorBalance.MAX_RECEIVE);
        assertEquals(8, ReconstructorBalance.MATERIAL_COUNT);
        assertEquals(1, ReconstructorBalance.CRYSTAL_COUNT);

        assertEquals(200, ReconstructorBalance.IRON_TIME_TICKS);
        assertEquals(20, ReconstructorBalance.IRON_ENERGY_PER_TICK);
        assertEquals(200, ReconstructorBalance.COPPER_TIME_TICKS);
        assertEquals(20, ReconstructorBalance.COPPER_ENERGY_PER_TICK);
        assertEquals(400, ReconstructorBalance.GOLD_TIME_TICKS);
        assertEquals(30, ReconstructorBalance.GOLD_ENERGY_PER_TICK);

        assertEquals(4_000, ReconstructorBalance.totalEnergy(
                ReconstructorBalance.IRON_TIME_TICKS, ReconstructorBalance.IRON_ENERGY_PER_TICK));
        assertEquals(4_000, ReconstructorBalance.totalEnergy(
                ReconstructorBalance.COPPER_TIME_TICKS, ReconstructorBalance.COPPER_ENERGY_PER_TICK));
        assertEquals(12_000, ReconstructorBalance.totalEnergy(
                ReconstructorBalance.GOLD_TIME_TICKS, ReconstructorBalance.GOLD_ENERGY_PER_TICK));
    }

    @Test
    void everyResultIsRawOre() throws IOException {
        for (Design design : DESIGNS) {
            JsonObject recipe = read(RECIPES, "reconstruct_" + design.mineral() + ".json");
            assertTrue(resultId(recipe).startsWith("minecraft:raw_"),
                    "the result must be raw ore: " + resultId(recipe));
        }
    }

    /** Loop protection: no raw ore may be an input of the reconstructor. */
    @Test
    void noRawOreIsAnInputOfTheReconstructor() throws IOException {
        for (Design design : DESIGNS) {
            JsonObject recipe = read(RECIPES, "reconstruct_" + design.mineral() + ".json");
            for (String field : List.of("analysis_sample", "material", "crystal")) {
                String ingredient = ingredientId(recipe, field);
                assertFalse(ingredient.startsWith("minecraft:raw_"),
                        "a raw ore is an input of the reconstructor: " + field + " = " + ingredient);
            }
        }
    }

    /** Anti growth: there must be no recipe that makes a crystal out of ingots or dust. */
    @Test
    void noRecipeTurnsIngotsOrDustIntoACrystal() throws IOException {
        JsonObject crystal = read(RECIPES, "reconstruction_crystal.json");
        String text = crystal.toString();
        assertFalse(text.contains("_ingot"), "the crystal recipe uses an ingot: " + text);
        assertFalse(text.contains("_dust"), "the crystal recipe uses dust: " + text);
        assertFalse(text.contains("_nugget"), "the crystal recipe uses a nugget: " + text);
        assertFalse(text.contains("raw_"), "the crystal recipe uses a raw ore: " + text);
        assertEquals("industrialcore:reconstruction_crystal", resultId(crystal));
        assertEquals(1, resultCount(crystal));
    }

    /** The machine and the crystal must be craftable. */
    @Test
    void theMachineAndTheCrystalHaveACraftingRecipe() throws IOException {
        JsonObject machine = read(RECIPES, "reconstructor.json");
        assertEquals("minecraft:crafting_shaped", machine.get("type").getAsString());
        assertEquals("industrialcore:reconstructor", resultId(machine));
        assertEquals(1, resultCount(machine));

        // Every symbol of the pattern has to be a blank or a key entry, otherwise the server
        // drops the recipe while it loads the data pack.
        JsonObject key = machine.getAsJsonObject("key");
        List<String> pattern = new ArrayList<>();
        for (JsonElement row : machine.getAsJsonArray("pattern")) {
            pattern.add(row.getAsString());
        }
        assertFalse(pattern.isEmpty(), "the recipe of the reconstructor has no pattern");
        for (String row : pattern) {
            assertEquals(pattern.get(0).length(), row.length(),
                    "the rows of the pattern must all have the same width: " + pattern);
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') {
                    continue;
                }
                assertTrue(key.has(String.valueOf(symbol)),
                        "the pattern uses the symbol '" + symbol + "' but the key does not define it");
            }
        }
        for (String symbol : key.keySet()) {
            assertTrue(pattern.stream().anyMatch(row -> row.contains(symbol)),
                    "the key defines the symbol '" + symbol + "' but the pattern does not use it");
        }

        JsonObject crystal = read(RECIPES, "reconstruction_crystal.json");
        assertEquals("minecraft:crafting_shapeless", crystal.get("type").getAsString());
    }

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    @Test
    void everyPhase15AssetExists() {
        for (String name : List.of("blockstates/reconstructor.json",
                "models/block/reconstructor.json",
                "models/item/reconstructor.json",
                "models/item/rcrystal.json",
                "textures/block/rc_front.png",
                "textures/block/rc_side.png",
                "textures/block/rc_top.png",
                "textures/item/rcrystal.png")) {
            assertTrue(Files.exists(ASSETS.resolve(name)), "missing asset: " + name);
        }
        assertTrue(Files.exists(DATA.resolve("loot_table/blocks/reconstructor.json")),
                "missing loot table of the reconstructor");
    }

    /** The textures must be real 16x16 PNG files, not a placeholder. */
    @Test
    void theTexturesAreSixteenPixelPngFiles() throws IOException {
        for (String name : List.of("textures/block/rc_front.png",
                "textures/block/rc_side.png",
                "textures/block/rc_top.png",
                "textures/item/rcrystal.png")) {
            byte[] data = Files.readAllBytes(ASSETS.resolve(name));
            assertTrue(data.length > 100, "the texture " + name + " is nearly empty");
            assertEquals((byte) 0x89, data[0], "the texture " + name + " is not a PNG");
            assertEquals('P', data[1]);
            assertEquals('N', data[2]);
            assertEquals('G', data[3]);
            // IHDR: the width and the height are stored as two big endian int32.
            int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16)
                    | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16)
                    | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
            assertEquals(16, width, "the texture " + name + " is not 16 pixels wide");
            assertEquals(16, height, "the texture " + name + " is not 16 pixels high");
        }
    }

    /** Both language files must carry the same keys, otherwise a client sees a raw key. */
    @Test
    void bothLanguageFilesCarryTheSameKeys() throws IOException {
        JsonObject en = read(ASSETS.resolve("lang"), "en_us.json");
        JsonObject ja = read(ASSETS.resolve("lang"), "ja_jp.json");
        assertEquals(en.keySet(), ja.keySet(), "the two language files have different keys");
        for (String key : List.of("block.industrialcore.reconstructor",
                "item.industrialcore.reconstruction_crystal",
                "gui.industrialcore.reconstructor.analyze",
                "gui.industrialcore.reconstructor.cycle",
                "gui.industrialcore.reconstructor.slot.analysis",
                "gui.industrialcore.reconstructor.slot.material",
                "gui.industrialcore.reconstructor.slot.crystal",
                "gui.industrialcore.reconstructor.slot.output",
                "gui.industrialcore.machine.status.no_selection",
                "gui.industrialcore.machine.status.no_material",
                "gui.industrialcore.machine.status.no_crystal")) {
            assertTrue(en.has(key), "the English file misses " + key);
            assertTrue(ja.has(key), "the Japanese file misses " + key);
        }
    }

    // ------------------------------------------------------------------
    // GUI geometry
    // ------------------------------------------------------------------

    @Test
    void theReconstructorGuiHasNoOverlappingElements() {
        List<Rect> rects = new ArrayList<>();
        rects.add(ReconstructorLayout.ENERGY_BAR);
        rects.add(ReconstructorLayout.ANALYSIS_SLOT);
        rects.add(ReconstructorLayout.MATERIAL_SLOT);
        rects.add(ReconstructorLayout.CRYSTAL_SLOT);
        rects.add(ReconstructorLayout.OUTPUT_SLOT);
        rects.add(ReconstructorLayout.PROGRESS_BAR);
        rects.add(ReconstructorLayout.ANALYZE_BUTTON);
        rects.add(ReconstructorLayout.CYCLE_BUTTON);
        rects.add(ReconstructorLayout.MOTOR_SLOT);
        rects.add(ReconstructorLayout.BATTERY_SLOT);
        rects.add(new Rect(ReconstructorLayout.MOTOR_CENTER_X - 28,
                ReconstructorLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(ReconstructorLayout.BATTERY_CENTER_X - 28,
                ReconstructorLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < ReconstructorLayout.MINERAL_BUTTON_COUNT; index++) {
            rects.add(ReconstructorLayout.mineralButton(index));
        }
        for (int index = 0; index < 36; index++) {
            rects.add(ReconstructorLayout.playerSlot(index));
        }

        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                Rect a = rects.get(first);
                Rect b = rects.get(second);
                assertFalse(a.intersects(b), "reconstructor gui: " + a + " overlaps " + b);
            }
        }
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0, "starts outside the panel: " + rect);
            assertTrue(rect.right() <= ReconstructorLayout.WIDTH,
                    "exceeds the panel width: " + rect);
            assertTrue(rect.bottom() <= ReconstructorLayout.HEIGHT,
                    "exceeds the panel height: " + rect);
        }
    }

    @Test
    void theReconstructorGuiKeepsTheOrderOfTheRows() {
        assertTrue(ReconstructorLayout.ENERGY_BAR.bottom() < ReconstructorLayout.ENERGY_TEXT_Y);
        assertTrue(ReconstructorLayout.STATUS_TEXT_Y < ReconstructorLayout.ENERGY_TEXT_Y);
        assertTrue(ReconstructorLayout.ENERGY_TEXT_Y < ReconstructorLayout.SLOT_LABEL_Y);
        assertTrue(ReconstructorLayout.SLOT_LABEL_Y < ReconstructorLayout.ANALYSIS_SLOT.y());
        assertTrue(ReconstructorLayout.OUTPUT_SLOT.bottom() < ReconstructorLayout.PROGRESS_BAR.y());
        assertTrue(ReconstructorLayout.PROGRESS_BAR.bottom() < ReconstructorLayout.ANALYZE_BUTTON.y());
        assertTrue(ReconstructorLayout.mineralButton(5).bottom()
                < ReconstructorLayout.UPGRADE_LABEL_Y);
        assertTrue(ReconstructorLayout.UPGRADE_LABEL_Y + 9 <= ReconstructorLayout.MOTOR_SLOT.y());
        assertTrue(ReconstructorLayout.MOTOR_SLOT.bottom() < ReconstructorLayout.PLAYER_INV_Y);
        assertTrue(ReconstructorLayout.BATTERY_SLOT.bottom() < ReconstructorLayout.PLAYER_INV_Y);
        assertTrue(ReconstructorLayout.playerSlot(26).bottom() < ReconstructorLayout.HOTBAR_Y);
    }

    // ------------------------------------------------------------------
    // Status enum and action ids
    // ------------------------------------------------------------------

    @Test
    void theNewStatesAreAppendedToTheStatusEnum() {
        assertEquals(6, MachineStatus.NO_SELECTION.ordinal());
        assertEquals(7, MachineStatus.NO_MATERIAL.ordinal());
        assertEquals(8, MachineStatus.NO_CRYSTAL.ordinal());
        assertEquals(0, MachineStatus.NO_INPUT.ordinal());
        assertEquals(1, MachineStatus.NO_RECIPE.ordinal());
        assertEquals(2, MachineStatus.NO_ENERGY.ordinal());
        assertEquals(3, MachineStatus.OUTPUT_FULL.ordinal());
        assertEquals(4, MachineStatus.WORKING.ordinal());
        assertEquals(5, MachineStatus.IDLE.ordinal());
    }

    @Test
    void theActionIdsOfTheReconstructorAreValid() {
        assertTrue(ReconstructorActions.isValid(ReconstructorActions.ANALYZE));
        assertTrue(ReconstructorActions.isValid(ReconstructorActions.CYCLE_MINERAL));
        assertTrue(ReconstructorActions.isValid(ReconstructorActions.SELECT_MINERAL));
        assertFalse(ReconstructorActions.isValid(-1));
        assertFalse(ReconstructorActions.isValid(3));
    }
}
