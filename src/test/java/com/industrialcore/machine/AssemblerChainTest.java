package com.industrialcore.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.menu.AssemblerLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 18 data test: the assembler line must be complete, loop free and buildable.
 *
 * <p>Reads the real JSON that ships with the mod, so a changed number, a missing asset or an item
 * without a use is caught without starting a server. The line under test:</p>
 * <pre>
 *   copper plate + gold plate + redstone --assembler--> 2 electronic components
 *   machine frame + steel plate + brass plate --assembler--> reinforced machine frame
 *   both assembled parts + steel plates + redstone block --> reinforced energy buffer
 *   Phase 17 parts only -------------------crafting-----> assembler (no cycle)
 * </pre>
 */
class AssemblerChainTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");
    private static final Path DATA = RESOURCES.resolve("data/industrialcore");

    /** Every item the phase adds, with the recipe that consumes it. */
    private static final Map<String, String> CONSUMERS = new LinkedHashMap<>();

    static {
        CONSUMERS.put("electronic_component", "energy_buffer_mk2.json");
        CONSUMERS.put("reinforced_machine_frame", "energy_buffer_mk2.json");
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

    private static int intOf(JsonObject recipe, String key, int fallback) {
        JsonElement value = recipe.get(key);
        return value == null ? fallback : value.getAsInt();
    }

    private static String ingredientItem(JsonElement ingredient) {
        if (ingredient == null || !ingredient.isJsonObject()) {
            return "";
        }
        JsonElement item = ingredient.getAsJsonObject().get("item");
        return item == null ? "" : item.getAsString();
    }

    private static List<String> assemblingIngredients(JsonObject recipe) {
        List<String> ingredients = new ArrayList<>();
        JsonElement list = recipe.get("ingredients");
        if (list == null || !list.isJsonArray()) {
            return ingredients;
        }
        for (JsonElement element : list.getAsJsonArray()) {
            ingredients.add(ingredientItem(element));
        }
        return ingredients;
    }

    // ------------------------------------------------------------------
    // assembling recipes
    // ------------------------------------------------------------------

    @Test
    void assemblingRecipesCarryThreeIngredientsAndTheBalanceNumbers() throws IOException {
        JsonObject component = json(RECIPES.resolve("assemble_electronic_component.json"));
        assertEquals("industrialcore:assembling", typeOf(component));
        assertEquals(List.of("industrialcore:copper_plate", "industrialcore:gold_plate",
                        "minecraft:redstone"),
                assemblingIngredients(component));
        assertEquals("industrialcore:electronic_component", resultId(component));
        assertEquals(2, resultCount(component), "one assembly run makes two components");
        assertEquals(AssemblerBalance.COMPONENT_TIME_TICKS, intOf(component, "time", 0));
        assertEquals(AssemblerBalance.COMPONENT_ENERGY_PER_TICK, intOf(component, "energy", 0));

        JsonObject frame = json(RECIPES.resolve("assemble_reinforced_machine_frame.json"));
        assertEquals("industrialcore:assembling", typeOf(frame));
        assertEquals(List.of("industrialcore:machine_frame", "industrialcore:steel_plate",
                        "industrialcore:brass_plate"),
                assemblingIngredients(frame));
        assertEquals("industrialcore:reinforced_machine_frame", resultId(frame));
        assertEquals(1, resultCount(frame));
        assertEquals(AssemblerBalance.REINFORCED_FRAME_TIME_TICKS, intOf(frame, "time", 0));
        assertEquals(AssemblerBalance.REINFORCED_FRAME_ENERGY_PER_TICK,
                intOf(frame, "energy", 0));
    }

    /** An assembly run costs more FE than pressing a plate, so the parts are a real investment. */
    @Test
    void anAssemblyRunCostsMoreThanOnePlate() {
        int plate = ProcessingBalance.METAL_PRESS_TIME_TICKS
                * ProcessingBalance.METAL_PRESS_ENERGY_PER_TICK;
        int component = AssemblerBalance.COMPONENT_TIME_TICKS
                * AssemblerBalance.COMPONENT_ENERGY_PER_TICK;
        int frame = AssemblerBalance.REINFORCED_FRAME_TIME_TICKS
                * AssemblerBalance.REINFORCED_FRAME_ENERGY_PER_TICK;
        assertEquals(480, plate);
        assertEquals(1_200, component);
        assertEquals(3_000, frame);
        assertTrue(component > plate, "assembling must cost more than pressing one plate");
        assertTrue(frame > component, "the reinforced frame must cost more than a component");
    }

    /** No assembling recipe may consume its own result, and every result needs a consumer. */
    @Test
    void assemblingRecipesAreLoopFreeAndEveryPartIsUsed() throws IOException {
        for (JsonObject recipe : allRecipes()) {
            if (!"industrialcore:assembling".equals(typeOf(recipe))) {
                continue;
            }
            String result = resultId(recipe);
            assertFalse(assemblingIngredients(recipe).contains(result),
                    "an assembling recipe uses its own result: " + recipe);
        }
        for (Map.Entry<String, String> entry : CONSUMERS.entrySet()) {
            String id = "industrialcore:" + entry.getKey();
            Path consumer = RECIPES.resolve(entry.getValue());
            assertTrue(Files.exists(consumer), "missing consumer recipe " + consumer);
            assertTrue(Files.readString(consumer).contains(id),
                    "no recipe consumes " + id + " (expected in " + entry.getValue() + ")");
        }
    }

    /** The assembler must be buildable from Phase 17 parts only: no cycle through its own output. */
    @Test
    void theAssemblerRecipeNeverNeedsAssembledParts() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("assembler.json"));
        List<String> ingredients = new ArrayList<>();
        JsonElement key = recipe.get("key");
        for (Map.Entry<String, JsonElement> entry : key.getAsJsonObject().entrySet()) {
            ingredients.add(ingredientItem(entry.getValue()));
        }
        assertFalse(ingredients.contains("industrialcore:assembler"),
                "the assembler recipe contains itself: " + recipe);
        for (String part : CONSUMERS.keySet()) {
            assertFalse(ingredients.contains("industrialcore:" + part),
                    "the assembler recipe needs " + part + ", which the assembler makes");
        }
        assertTrue(ingredients.contains("industrialcore:machine_frame"),
                "the assembler should be built from a machine frame");
        assertTrue(ingredients.contains("industrialcore:steel_plate"),
                "the assembler should need the press parts of Phase 17");
    }

    @Test
    void theReinforcedBufferConsumesBothAssembledParts() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("energy_buffer_mk2.json"));
        assertEquals("minecraft:crafting_shaped", typeOf(recipe));
        String text = Files.readString(RECIPES.resolve("energy_buffer_mk2.json"));
        for (String part : CONSUMERS.keySet()) {
            assertTrue(text.contains("industrialcore:" + part),
                    "the reinforced buffer does not use " + part);
        }
        assertEquals("industrialcore:energy_buffer_mk2", resultId(recipe));
    }

    // ------------------------------------------------------------------
    // buffer tiers
    // ------------------------------------------------------------------

    @Test
    void theReinforcedBufferTierIsStrictlyLarger() {
        assertEquals(3, BufferTier.values().length);
        assertEquals(1, BufferTier.BASIC.capacityMultiplier());
        assertEquals(1, BufferTier.BASIC.pushMultiplier());
        assertEquals(4, BufferTier.REINFORCED.capacityMultiplier());
        assertEquals(2, BufferTier.REINFORCED.receiveMultiplier());
        assertEquals(2, BufferTier.REINFORCED.pushMultiplier());
        assertTrue(BufferTier.REINFORCED.capacityMultiplier()
                > BufferTier.BASIC.capacityMultiplier());
        // Phase 30: the high-density bank the top amplifier tiers drain.
        assertEquals(16, BufferTier.HIGH_DENSITY.capacityMultiplier());
        assertEquals(8, BufferTier.HIGH_DENSITY.receiveMultiplier());
        assertEquals(16, BufferTier.HIGH_DENSITY.pushMultiplier());
        assertTrue(BufferTier.HIGH_DENSITY.capacityMultiplier()
                > BufferTier.REINFORCED.capacityMultiplier());
        assertTrue(BufferTier.HIGH_DENSITY.pushMultiplier()
                > BufferTier.REINFORCED.pushMultiplier());
        assertEquals(BufferTier.BASIC, BufferTier.byIndex(-3));
        assertEquals(BufferTier.HIGH_DENSITY, BufferTier.byIndex(99));
    }

    @Test
    void bufferTierScalingNeverOverflows() {
        assertEquals(1_000_000, BufferTier.scaled(250_000, 4));
        assertEquals(1_024, BufferTier.scaled(512, 2));
        assertEquals(0, BufferTier.scaled(0, 4));
        assertEquals(0, BufferTier.scaled(-100, 4));
        assertEquals(Integer.MAX_VALUE, BufferTier.scaled(Integer.MAX_VALUE, 2));
    }

    // ------------------------------------------------------------------
    // assets and translations
    // ------------------------------------------------------------------

    @Test
    void everyNewBlockAndItemHasModelsTexturesAndDrops() throws IOException {
        for (String block : List.of("assembler", "energy_buffer_mk2")) {
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
        // Short texture names keep every path inside the Windows MAX_PATH limit.
        for (String texture : List.of("as_front", "as_side", "as_top", "eb2_front", "eb2_side",
                "eb2_top")) {
            assertTrue(Files.exists(ASSETS.resolve("textures/block/" + texture + ".png")),
                    "missing machine texture " + texture);
        }
    }

    @Test
    void bothLanguagesCarryTheNewKeys() throws IOException {
        List<String> keys = List.of(
                "block.industrialcore.assembler",
                "block.industrialcore.energy_buffer_mk2",
                "item.industrialcore.electronic_component",
                "item.industrialcore.reinforced_machine_frame",
                "gui.industrialcore.tooltip.assembler",
                "gui.industrialcore.tooltip.electronic_component",
                "gui.industrialcore.tooltip.reinforced_machine_frame",
                "gui.industrialcore.tooltip.energy_buffer_mk2",
                "gui.industrialcore.assembler.inputs",
                "gui.industrialcore.assembler.output");
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = json(ASSETS.resolve("lang/" + language + ".json"));
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
            }
        }
    }

    // ------------------------------------------------------------------
    // GUI geometry
    // ------------------------------------------------------------------

    @Test
    void assemblerLayoutHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(AssemblerLayout.ENERGY_BAR);
        for (int index = 0; index < 3; index++) {
            rects.add(AssemblerLayout.inputSlot(index));
        }
        rects.add(AssemblerLayout.PROGRESS_BAR);
        rects.add(AssemblerLayout.OUTPUT_SLOT);
        for (int index = 0; index < 36; index++) {
            rects.add(AssemblerLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "assembler");
        assertInsidePanel(rects, AssemblerLayout.WIDTH, AssemblerLayout.HEIGHT, "assembler");

        // The three ingredient slots must not overlap each other or the bars.
        for (int first = 0; first < 3; first++) {
            for (int second = first + 1; second < 3; second++) {
                assertFalse(AssemblerLayout.inputSlot(first).intersects(
                        AssemblerLayout.inputSlot(second)), "ingredient slots overlap");
            }
            assertFalse(AssemblerLayout.inputSlot(first).intersects(AssemblerLayout.PROGRESS_BAR));
            assertFalse(AssemblerLayout.inputSlot(first).intersects(AssemblerLayout.OUTPUT_SLOT));
        }
        assertTrue(AssemblerLayout.ENERGY_BAR.bottom() < AssemblerLayout.INPUT_SLOT_1.y(),
                "the energy bar must stay above the ingredient slots");
        assertTrue(AssemblerLayout.STATUS_TEXT_Y + 8 < AssemblerLayout.INPUT_SLOT_1.y(),
                "the status strip must sit above the ingredient slots");
        // The two label lines are drawn 12 pixels below the status baseline.
        assertTrue(AssemblerLayout.STATUS_TEXT_Y + 21 < AssemblerLayout.PLAYER_INV_Y,
                "the slot labels must not collide with the player inventory");
        assertTrue(AssemblerLayout.PLAYER_INV_Y > AssemblerLayout.STATUS_TEXT_Y + 8,
                "the player inventory must not collide with the status line");
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
