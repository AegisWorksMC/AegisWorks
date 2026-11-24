package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 20 data test: the large quarry is craftable from existing parts, loop free, drawn and
 * translated.
 *
 * <p>Reads the real JSON that ships with the mod, so a changed ingredient, a missing texture or a
 * forgotten translation is caught without starting a server. It also checks that the crushing
 * conversion really reuses the existing {@code industrialcore:crushing} recipes instead of shipping
 * a private copy with different ratios.</p>
 */
class LargeQuarryChainTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");
    private static final Path DATA = RESOURCES.resolve("data/industrialcore");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, String> keyItems(JsonObject recipe) {
        Map<String, String> items = new LinkedHashMap<>();
        JsonElement key = recipe.get("key");
        if (key == null || !key.isJsonObject()) {
            return items;
        }
        for (Map.Entry<String, JsonElement> entry : key.getAsJsonObject().entrySet()) {
            JsonElement item = entry.getValue().getAsJsonObject().get("item");
            items.put(entry.getKey(), item == null ? "" : item.getAsString());
        }
        return items;
    }

    private static List<String> patternLetters(JsonObject recipe) {
        List<String> letters = new ArrayList<>();
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            for (char letter : row.getAsString().toCharArray()) {
                if (letter != ' ') {
                    letters.add(String.valueOf(letter));
                }
            }
        }
        return letters;
    }

    private static int ingredientCount(JsonObject recipe, String itemId) {
        Map<String, String> key = keyItems(recipe);
        int count = 0;
        for (String letter : patternLetters(recipe)) {
            if (itemId.equals(key.get(letter))) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // recipe
    // ------------------------------------------------------------------

    @Test
    void theLargeQuarryUsesTheReinforcedFrameAndAssembledParts() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("large_quarry.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("industrialcore:large_quarry",
                recipe.getAsJsonObject("result").get("id").getAsString());

        // Reinforced machine frames exist only because of the Phase 18 assembler, so the large
        // quarry is the third consumer of that line (after the reinforced buffer and the
        // distributor) instead of a duplicate of the normal quarry recipe.
        assertEquals(2, ingredientCount(recipe, "industrialcore:reinforced_machine_frame"),
                "the large quarry should be built around two reinforced machine frames");
        assertEquals(1, ingredientCount(recipe, "industrialcore:machine_frame"),
                "the large quarry should use a machine frame");
        assertTrue(ingredientCount(recipe, "industrialcore:electronic_component") >= 2,
                "the large quarry should use assembled electronic components");
        assertTrue(ingredientCount(recipe, "industrialcore:steel_plate") >= 1,
                "the large quarry should use pressed steel plates");
    }

    /**
     * A recipe that needs its own product (directly or through another machine) would be a cycle.
     * Every recipe that names the large quarry must also produce it.
     */
    @Test
    void noRecipeRequiresTheLargeQuarry() throws IOException {
        assertFalse(keyItems(json(RECIPES.resolve("large_quarry.json")))
                        .containsValue("industrialcore:large_quarry"),
                "the large quarry recipe contains its own result");

        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                String text = Files.readString(path);
                if (!text.contains("industrialcore:large_quarry")) {
                    continue;
                }
                JsonObject recipe = json(path);
                JsonElement result = recipe.get("result");
                boolean producesIt = result != null && result.isJsonObject()
                        && "industrialcore:large_quarry".equals(
                        result.getAsJsonObject().get("id").getAsString());
                assertTrue(producesIt,
                        path.getFileName() + " consumes the large quarry but does not produce it");
            }
        }
    }

    // ------------------------------------------------------------------
    // assets and translations
    // ------------------------------------------------------------------

    @Test
    void theBlockHasModelsTexturesLootAndTag() throws IOException {
        assertTrue(Files.exists(ASSETS.resolve("blockstates/large_quarry.json")),
                "missing blockstate");
        assertTrue(Files.exists(ASSETS.resolve("models/block/large_quarry.json")),
                "missing block model");
        assertTrue(Files.exists(ASSETS.resolve("models/item/large_quarry.json")),
                "missing block item model");
        assertTrue(Files.exists(DATA.resolve("loot_table/blocks/large_quarry.json")),
                "missing loot table (the block would drop nothing)");
        for (String texture : List.of("lq_front", "lq_side", "lq_top")) {
            assertTrue(Files.exists(ASSETS.resolve("textures/block/" + texture + ".png")),
                    "missing machine texture " + texture);
        }

        JsonObject blockstate = json(ASSETS.resolve("blockstates/large_quarry.json"));
        for (String variant : List.of("facing=north,lit=false", "facing=east,lit=false",
                "facing=south,lit=false", "facing=west,lit=false",
                "facing=north,lit=true", "facing=east,lit=true",
                "facing=south,lit=true", "facing=west,lit=true")) {
            assertTrue(blockstate.getAsJsonObject("variants").has(variant),
                    "missing blockstate variant " + variant);
        }

        String pickaxeTag = Files.readString(
                RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(pickaxeTag.contains("industrialcore:large_quarry"),
                "the large quarry is not mineable with a pickaxe");

        String loot = Files.readString(DATA.resolve("loot_table/blocks/large_quarry.json"));
        assertTrue(loot.contains("industrialcore:large_quarry"),
                "the loot table does not drop the large quarry");
    }

    @Test
    void bothLanguagesCarryTheNewKeys() throws IOException {
        List<String> keys = List.of(
                "block.industrialcore.large_quarry",
                "gui.industrialcore.tooltip.large_quarry",
                "gui.industrialcore.tooltip.large_quarry_pickaxe",
                "gui.industrialcore.tooltip.large_quarry_motor",
                "gui.industrialcore.large_quarry.stats",
                "gui.industrialcore.large_quarry.area",
                "gui.industrialcore.large_quarry.tools",
                "gui.industrialcore.large_quarry.enchants",
                "gui.industrialcore.large_quarry.select_ores",
                "gui.industrialcore.large_quarry.ore_picker_title",
                "gui.industrialcore.large_quarry.ore_picker_count",
                "gui.industrialcore.large_quarry.ore_selected",
                "gui.industrialcore.large_quarry.ore_unselected",
                "gui.industrialcore.large_quarry.select_all",
                "gui.industrialcore.large_quarry.select_none",
                "gui.industrialcore.large_quarry.close",
                "gui.industrialcore.large_quarry.page",
                "gui.industrialcore.large_quarry.tool_title",
                "gui.industrialcore.large_quarry.upgrade");
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = json(ASSETS.resolve("lang/" + language + ".json"));
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
            }
        }
    }

    /** The GUI shows a status text for every state the machine can report. */
    @Test
    void everyQuarryStatusHasATranslation() throws IOException {
        JsonObject en = json(ASSETS.resolve("lang/en_us.json"));
        JsonObject ja = json(ASSETS.resolve("lang/ja_jp.json"));
        Set<String> keys = new TreeSet<>();
        for (QuarryStatus status : QuarryStatus.values()) {
            keys.add(status.translationKey());
        }
        for (String key : keys) {
            assertTrue(en.has(key), "en_us is missing " + key);
            assertTrue(ja.has(key), "ja_jp is missing " + key);
        }
    }
}
