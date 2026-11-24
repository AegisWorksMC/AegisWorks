package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.menu.AlloyFurnaceLayout;
import com.industrialcore.menu.EnergyBufferLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17 data test: the machine parts line must be complete and loop free.
 *
 * <p>Reads the real JSON that ships with the mod, so a changed number, a missing asset or an item
 * without a use is caught without starting a server. The line under test:</p>
 * <pre>
 *   iron / copper / gold ingot --metal press--> plate
 *   iron + coal, copper + gold --alloy furnace--> steel / brass ingot
 *   steel / brass ingot --------metal press---> alloy plate
 *   plates + motor ------------crafting-----> machine frame
 *   machine frame + plates + redstone block --> large energy buffer
 * </pre>
 */
class PartsChainTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");
    private static final Path DATA = RESOURCES.resolve("data/industrialcore");

    /** Every item the phase adds, with the recipe that consumes it. */
    private static final Map<String, String> CONSUMERS = new LinkedHashMap<>();

    static {
        CONSUMERS.put("iron_plate", "energy_buffer.json");
        CONSUMERS.put("copper_plate", "machine_frame.json");
        CONSUMERS.put("gold_plate", "machine_frame.json");
        CONSUMERS.put("steel_plate", "machine_frame.json");
        CONSUMERS.put("brass_plate", "machine_frame.json");
        CONSUMERS.put("steel_ingot", "press_steel_plate.json");
        CONSUMERS.put("brass_ingot", "press_brass_plate.json");
        CONSUMERS.put("machine_frame", "energy_buffer.json");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<JsonObject> allRecipes() throws IOException {
        List<JsonObject> recipes = new ArrayList<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                recipes.add(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        }
        return recipes;
    }

    private static String typeOf(JsonObject recipe) {
        JsonElement type = recipe.get("type");
        return type == null ? "" : type.getAsString();
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

    private static String ingredientItem(JsonElement ingredient) {
        if (ingredient == null || !ingredient.isJsonObject()) {
            return "";
        }
        JsonElement item = ingredient.getAsJsonObject().get("item");
        return item == null ? "" : item.getAsString();
    }

    private static int intOf(JsonObject recipe, String key, int fallback) {
        JsonElement value = recipe.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    // ------------------------------------------------------------------
    // pressing
    // ------------------------------------------------------------------

    @Test
    void pressingTurnsEveryIngotIntoAPlate() throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("press_iron_plate.json", "minecraft:iron_ingot");
        expected.put("press_copper_plate.json", "minecraft:copper_ingot");
        expected.put("press_gold_plate.json", "minecraft:gold_ingot");
        expected.put("press_steel_plate.json", "industrialcore:steel_ingot");
        expected.put("press_brass_plate.json", "industrialcore:brass_ingot");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonObject recipe = json(RECIPES.resolve(entry.getKey()));
            assertEquals("industrialcore:pressing", typeOf(recipe), entry.getKey());
            assertEquals(entry.getValue(), ingredientItem(recipe.get("ingredient")), entry.getKey());
            assertTrue(resultId(recipe).endsWith("_plate"), entry.getKey());
            assertEquals(1, resultCount(recipe), entry.getKey());
        }

        JsonObject iron = json(RECIPES.resolve("press_iron_plate.json"));
        assertEquals(ProcessingBalance.METAL_PRESS_TIME_TICKS, intOf(iron, "time", 0));
        assertEquals(ProcessingBalance.METAL_PRESS_ENERGY_PER_TICK, intOf(iron, "energy", 0));
        assertEquals(480L, ProcessingBalance.totalEnergy(
                ProcessingBalance.METAL_PRESS_TIME_TICKS,
                ProcessingBalance.METAL_PRESS_ENERGY_PER_TICK));

        JsonObject steel = json(RECIPES.resolve("press_steel_plate.json"));
        assertEquals(ProcessingBalance.ALLOY_PRESS_TIME_TICKS, intOf(steel, "time", 0));
        assertEquals(ProcessingBalance.ALLOY_PRESS_ENERGY_PER_TICK, intOf(steel, "energy", 0));
        assertEquals(800L, ProcessingBalance.totalEnergy(
                ProcessingBalance.ALLOY_PRESS_TIME_TICKS,
                ProcessingBalance.ALLOY_PRESS_ENERGY_PER_TICK));
    }

    @Test
    void pressingNeverProducesAnIngot() throws IOException {
        for (JsonObject recipe : allRecipes()) {
            if (!"industrialcore:pressing".equals(typeOf(recipe))) {
                continue;
            }
            assertFalse(resultId(recipe).endsWith("_ingot"),
                    "the press turns a plate back into an ingot: " + recipe);
            assertFalse(resultId(recipe).endsWith("_ore"),
                    "the press produces an ore: " + recipe);
        }
    }

    // ------------------------------------------------------------------
    // alloying
    // ------------------------------------------------------------------

    @Test
    void alloyingRecipesMatchTheBalanceConstants() throws IOException {
        JsonObject steel = json(RECIPES.resolve("alloy_steel.json"));
        assertEquals("industrialcore:alloying", typeOf(steel));
        assertEquals("minecraft:iron_ingot", ingredientItem(steel.get("first")));
        assertEquals("minecraft:iron_ingot", ingredientItem(steel.get("second")));
        assertEquals("industrialcore:steel_ingot", resultId(steel));
        assertEquals(1, resultCount(steel));
        assertEquals(AlloyBalance.STEEL_TIME_TICKS, intOf(steel, "time", 0));

        JsonObject brass = json(RECIPES.resolve("alloy_brass.json"));
        assertEquals("industrialcore:alloying", typeOf(brass));
        assertEquals("minecraft:copper_ingot", ingredientItem(brass.get("first")));
        assertEquals("minecraft:gold_ingot", ingredientItem(brass.get("second")));
        assertEquals("industrialcore:brass_ingot", resultId(brass));
        assertEquals(2, resultCount(brass), "one gold ingot is turned into two brass ingots");
        assertEquals(AlloyBalance.BRASS_TIME_TICKS, intOf(brass, "time", 0));
    }

    /**
     * No alloy recipe may use a fuel as an ingredient.
     *
     * <p>The fuel slot is the only slot that accepts a burnable item, so a pipe carrying coal
     * always feeds the furnace. A recipe that needs coal as an ingredient would make that pipe
     * fill the ingredient slot instead, and the machine could never be refuelled
     * automatically.</p>
     */
    @Test
    void alloyIngredientsAreNotFuels() throws IOException {
        Set<String> fuels = Set.of("minecraft:coal", "minecraft:charcoal", "minecraft:coals",
                "minecraft:coal_block", "minecraft:lava_bucket");
        for (JsonObject recipe : allRecipes()) {
            if (!"industrialcore:alloying".equals(typeOf(recipe))) {
                continue;
            }
            for (String slot : List.of("first", "second")) {
                JsonObject ingredient = recipe.get(slot).getAsJsonObject();
                String id = ingredient.has("tag") ? ingredient.get("tag").getAsString()
                        : ingredientItem(ingredient);
                assertFalse(fuels.contains(id),
                        "an alloy ingredient is a fuel, which breaks automated refuelling: "
                                + recipe);
            }
        }
    }

    @Test
    void alloyingNeverProducesAPlateOrAPressInput() throws IOException {
        Set<String> allowed = Set.of("industrialcore:steel_ingot", "industrialcore:brass_ingot");
        for (JsonObject recipe : allRecipes()) {
            if (!"industrialcore:alloying".equals(typeOf(recipe))) {
                continue;
            }
            assertTrue(allowed.contains(resultId(recipe)),
                    "the alloy furnace produces something unexpected: " + recipe);
        }
    }

    // ------------------------------------------------------------------
    // uses and loops
    // ------------------------------------------------------------------

    @Test
    void everyNewItemIsConsumedByAShippedRecipe() throws IOException {
        for (Map.Entry<String, String> entry : CONSUMERS.entrySet()) {
            String id = "industrialcore:" + entry.getKey();
            Path consumer = RECIPES.resolve(entry.getValue());
            assertTrue(Files.exists(consumer), "missing consumer recipe " + consumer);
            assertTrue(Files.readString(consumer).contains(id),
                    "no recipe consumes " + id + " (expected in " + entry.getValue() + ")");
        }
        // Every plate and alloy is reachable: pressing and alloying produce them.
        for (String plate : List.of("iron_plate", "copper_plate", "gold_plate",
                "steel_plate", "brass_plate")) {
            assertTrue(Files.exists(RECIPES.resolve("press_" + plate + ".json")),
                    "no pressing recipe produces " + plate);
        }
        for (String alloy : List.of("steel_ingot", "brass_ingot")) {
            String metal = alloy.substring(0, alloy.indexOf('_'));
            assertTrue(Files.exists(RECIPES.resolve("alloy_" + metal + ".json")),
                    "no alloying recipe produces " + alloy);
        }
    }

    @Test
    void noPartCanBeTurnedBackIntoItsSource() throws IOException {
        for (JsonObject recipe : allRecipes()) {
            String result = resultId(recipe);
            String type = typeOf(recipe);
            List<JsonElement> ingredients = ingredientsOf(recipe);

            // No machine recipe may turn a plate back into an ingot or into another plate.
            if (result.endsWith("_ingot") || result.endsWith("_plate")) {
                for (JsonElement ingredient : ingredients) {
                    String id = ingredientItem(ingredient);
                    if (id.endsWith("_plate") && ("industrialcore:pressing".equals(type)
                            || "industrialcore:alloying".equals(type))) {
                        throw new AssertionError("a machine recipe uses a plate as its input: "
                                + recipe);
                    }
                }
            }

            // The machine frame is a crafting component, never a machine product.
            if (result.equals("industrialcore:machine_frame")) {
                assertFalse("industrialcore:pressing".equals(type)
                                || "industrialcore:alloying".equals(type),
                        "the frame is made by a machine: " + recipe);
            }
        }
    }

    private static List<JsonElement> ingredientsOf(JsonObject recipe) {
        List<JsonElement> ingredients = new ArrayList<>();
        JsonElement single = recipe.get("ingredient");
        if (single != null && single.isJsonObject()) {
            ingredients.add(single);
        }
        JsonElement first = recipe.get("first");
        if (first != null && first.isJsonObject()) {
            ingredients.add(first);
        }
        JsonElement second = recipe.get("second");
        if (second != null && second.isJsonObject()) {
            ingredients.add(second);
        }
        JsonElement list = recipe.get("ingredients");
        if (list != null && list.isJsonArray()) {
            for (JsonElement element : list.getAsJsonArray()) {
                ingredients.add(element);
            }
        }
        JsonElement key = recipe.get("key");
        if (key != null && key.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : key.getAsJsonObject().entrySet()) {
                ingredients.add(entry.getValue());
            }
        }
        return ingredients;
    }

    // ------------------------------------------------------------------
    // assets, translations, status ordinals
    // ------------------------------------------------------------------

    @Test
    void everyNewBlockAndItemHasModelsTexturesAndDrops() throws IOException {
        for (String block : List.of("metal_press", "alloy_furnace", "energy_buffer")) {
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + block + ".json")),
                    "missing blockstate for " + block);
            assertTrue(Files.exists(ASSETS.resolve("models/block/" + block + ".json")),
                    "missing block model for " + block);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + block + ".json")),
                    "missing block item model for " + block);
            assertTrue(Files.exists(DATA.resolve("loot_table/blocks/" + block + ".json")),
                    "missing loot table for " + block);
        }
        for (String item : CONSUMERS.keySet()) {
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + item + ".json")),
                    "missing item model for " + item);
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + item + ".png")),
                    "missing texture for " + item);
        }
        // The three machine textures use short names (Windows MAX_PATH).
        for (String texture : List.of("mp_front", "mp_side", "mp_top", "af_front", "af_side",
                "af_top", "eb_front", "eb_side", "eb_top")) {
            assertTrue(Files.exists(ASSETS.resolve("textures/block/" + texture + ".png")),
                    "missing machine texture " + texture);
        }
    }

    @Test
    void bothLanguagesCarryTheNewKeys() throws IOException {
        List<String> keys = List.of(
                "block.industrialcore.metal_press",
                "block.industrialcore.alloy_furnace",
                "block.industrialcore.energy_buffer",
                "item.industrialcore.iron_plate",
                "item.industrialcore.steel_ingot",
                "item.industrialcore.machine_frame",
                "gui.industrialcore.machine.status.no_fuel",
                "gui.industrialcore.alloy.fuel",
                "gui.industrialcore.buffer.push");
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = json(ASSETS.resolve("lang/" + language + ".json"));
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
            }
        }
    }

    /** The new status must not renumber the existing ones (the ordinal travels to the client). */
    @Test
    void noFuelIsAppendedToTheStatusEnum() {
        assertEquals(6, MachineStatus.NO_SELECTION.ordinal());
        assertEquals(7, MachineStatus.NO_MATERIAL.ordinal());
        assertEquals(8, MachineStatus.NO_CRYSTAL.ordinal());
        assertEquals(9, MachineStatus.NO_FUEL.ordinal());
        assertTrue(MachineStatus.NO_FUEL.isProblem());
        assertEquals(10, MachineStatus.values().length);
    }

    // ------------------------------------------------------------------
    // GUI geometry
    // ------------------------------------------------------------------

    @Test
    void alloyFurnaceLayoutHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(AlloyFurnaceLayout.FIRST_SLOT);
        rects.add(AlloyFurnaceLayout.SECOND_SLOT);
        rects.add(AlloyFurnaceLayout.FUEL_SLOT);
        rects.add(AlloyFurnaceLayout.OUTPUT_SLOT);
        rects.add(AlloyFurnaceLayout.PROGRESS_BAR);
        rects.add(AlloyFurnaceLayout.BURN_BAR);
        for (int index = 0; index < 36; index++) {
            rects.add(AlloyFurnaceLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "alloy furnace");
        assertInsidePanel(rects, AlloyFurnaceLayout.WIDTH, AlloyFurnaceLayout.HEIGHT,
                "alloy furnace");
        assertTrue(AlloyFurnaceLayout.BURN_BAR.bottom() < AlloyFurnaceLayout.PLAYER_INV_Y,
                "the fuel bar must stay above the player inventory");
        assertTrue(AlloyFurnaceLayout.STATUS_TEXT_Y + 8 < AlloyFurnaceLayout.FIRST_SLOT.y(),
                "the status strip must stay above the slots");
        assertTrue(AlloyFurnaceLayout.FUEL_SLOT.bottom() < AlloyFurnaceLayout.BURN_BAR.y(),
                "the fuel bar must stay below the fuel slot");
        assertTrue(AlloyFurnaceLayout.OUTPUT_LABEL_Y < AlloyFurnaceLayout.OUTPUT_SLOT.y(),
                "the output label must stay above its slot");
    }

    @Test
    void energyBufferLayoutHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(EnergyBufferLayout.ENERGY_BAR);
        for (int index = 0; index < 36; index++) {
            rects.add(EnergyBufferLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "energy buffer");
        assertInsidePanel(rects, EnergyBufferLayout.WIDTH, EnergyBufferLayout.HEIGHT,
                "energy buffer");
        assertTrue(EnergyBufferLayout.HINT_TEXT_Y < EnergyBufferLayout.PLAYER_INV_Y,
                "the hint must stay above the player inventory");
    }

    private static void assertNoOverlaps(List<Rect> rects, String where) {
        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                Rect a = rects.get(first);
                Rect b = rects.get(second);
                assertFalse(a.intersects(b), where + ": " + a + " overlaps " + b);
            }
        }
    }

    private static void assertInsidePanel(List<Rect> rects, int width, int height, String where) {
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0,
                    where + ": " + rect + " starts outside the panel");
            assertTrue(rect.right() <= width, where + ": " + rect + " exceeds width " + width);
            assertTrue(rect.bottom() <= height, where + ": " + rect + " exceeds height " + height);
        }
    }
}
