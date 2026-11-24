package com.industrialcore.machine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Format lint of every shipped crafting recipe.
 *
 * <p>1.21.1 reads the amount of an ingredient nowhere: {@code Ingredient} names an item or a tag
 * and a {@code count} inside it is ignored, so a recipe written with one silently costs a single
 * item (and the recipe book, which fills the grid the same way, agrees with that wrong reading).
 * Several recipes shipped that way and the game was cheaper than the documented economy. The only
 * correct encoding is one ingredient entry per item, which is what
 * {@code tools/gen_phase30_data.py} emits and what {@code seed_iron.json} has always done.</p>
 *
 * <p>This test therefore reads every recipe file as text and fails on a leftover {@code count}
 * (value 1 included, since a "harmless" one hides the next real one), on a recipe that needs more
 * items than the nine slot grid holds, on a shaped recipe whose pattern and key disagree, and on a
 * file that cannot be read — a file that fails to load must fail the run, never be skipped. The
 * companion {@code CraftingRecipeGameTests} runs the same expectations through the real recipe
 * manager, so both the raw format and the parsed result are covered.</p>
 */
class RecipeCraftingFormatTest {
    private static final Gson GSON = new Gson();
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");

    /** The crafting grid, and the recipe book that fills it, hold nine items. */
    private static final int MAX_GRID_ITEMS = 9;

    /** The crafting recipes that ship after Phase 31 removed the fifteen ore amplifier builds. */
    // 56 + 12 Phase 33 + geothermal + Phase 63 + 6 P89 + 3 P90 + 3 P91 + 3 P92 + 5 P93 + 3 P94 + 3 P95 + 3 P96 + 13 P98 + 3 P99 + 16 P101 + 5 P103 + 2 P105.6 + 1 P113
    private static final int CRAFTING_RECIPES = 139;

    /** The recipes whose amounts the Phase 30 count fix restored. */
    private static final List<String> RESTORED = List.of(
            "machine_frame", "reconstruction_crystal", "energy_buffer_mk3", "ore_synthesizer");

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    private static List<Path> recipeFiles() throws IOException {
        try (Stream<Path> stream = Files.list(RECIPES)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static JsonObject parse(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path), JsonObject.class);
    }

    private static String typeOf(JsonObject recipe) {
        JsonElement type = recipe.get("type");
        return type == null ? "" : type.getAsString();
    }

    private static boolean isCrafting(JsonObject recipe) {
        return typeOf(recipe).contains("crafting");
    }

    private static String name(Path path) {
        return path.getFileName().toString().replace(".json", "");
    }

    /** The item id of one ingredient entry, without the namespace. */
    private static String itemOf(JsonElement ingredient) {
        if (ingredient == null || !ingredient.isJsonObject()) {
            return "";
        }
        JsonElement item = ingredient.getAsJsonObject().get("item");
        if (item == null) {
            return "";
        }
        String id = item.getAsString();
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** The amount of every ingredient of a recipe, read as one entry per item. */
    private static Map<String, Integer> amountsOf(JsonObject recipe) {
        Map<String, Integer> amounts = new TreeMap<>();
        JsonElement ingredients = recipe.get("ingredients");
        if (ingredients != null && ingredients.isJsonArray()) {
            for (JsonElement entry : ingredients.getAsJsonArray()) {
                amounts.merge(itemOf(entry), 1, Integer::sum);
            }
        }
        JsonElement key = recipe.get("key");
        if (key != null && key.isJsonObject()) {
            for (Map.Entry<String, JsonElement> symbol : key.getAsJsonObject().entrySet()) {
                amounts.merge(itemOf(symbol.getValue()), 1, Integer::sum);
            }
        }
        return amounts;
    }

    // ------------------------------------------------------------------
    // the format itself
    // ------------------------------------------------------------------

    @Test
    void everyRecipeFileCanBeReadAndParsed() throws IOException {
        List<Path> files = recipeFiles();
        assertFalse(files.isEmpty(), "no recipe file was found under " + RECIPES);

        List<String> broken = new ArrayList<>();
        for (Path path : files) {
            try {
                if (parse(path) == null) {
                    broken.add(path.getFileName() + " (empty)");
                }
            } catch (IOException | RuntimeException exception) {
                // Sorted out below; an unreadable file is a failure, never a skipped file.
                broken.add(path.getFileName() + " (" + exception + ")");
            }
        }
        assertTrue(broken.isEmpty(),
                "a recipe file that cannot be read or parsed is dropped silently by the game, so "
                        + "it has to fail here: " + broken);
    }

    /**
     * The regression guard of the whole fix: an ingredient entry must not carry a {@code count} at
     * all. The check is on the key, not on the value, because a leftover {@code "count": 1} is the
     * shape a later real amount would be added in.
     */
    @Test
    void noCraftingIngredientCarriesACount() throws IOException {
        Map<String, String> offenders = new TreeMap<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = parse(path);
            if (recipe == null || !isCrafting(recipe)) {
                continue;
            }
            JsonElement ingredients = recipe.get("ingredients");
            if (ingredients != null && ingredients.isJsonArray()) {
                JsonArray array = ingredients.getAsJsonArray();
                for (int index = 0; index < array.size(); index++) {
                    JsonElement entry = array.get(index);
                    if (entry.isJsonObject() && entry.getAsJsonObject().has("count")) {
                        offenders.put(path.getFileName() + " ingredients[" + index + "]",
                                entry.getAsJsonObject().get("count").getAsString());
                    }
                }
            }
            JsonElement key = recipe.get("key");
            if (key != null && key.isJsonObject()) {
                for (Map.Entry<String, JsonElement> symbol : key.getAsJsonObject().entrySet()) {
                    JsonElement entry = symbol.getValue();
                    if (entry.isJsonObject() && entry.getAsJsonObject().has("count")) {
                        offenders.put(path.getFileName() + " key[" + symbol.getKey() + "]",
                                entry.getAsJsonObject().get("count").getAsString());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "1.21.1 ignores a count inside an Ingredient, so these recipes would cost one item "
                        + "each instead of the documented amount; write one entry per item "
                        + "(see seed_iron.json): " + offenders);
    }

    @Test
    void everyCraftingRecipeFitsTheNineSlotGrid() throws IOException {
        List<String> oversized = new ArrayList<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = parse(path);
            if (recipe == null || !isCrafting(recipe)) {
                continue;
            }
            if (typeOf(recipe).endsWith("shapeless")) {
                JsonElement ingredients = recipe.get("ingredients");
                int size = ingredients == null ? 0 : ingredients.getAsJsonArray().size();
                if (size == 0) {
                    oversized.add(path.getFileName() + ": no ingredients");
                } else if (size > MAX_GRID_ITEMS) {
                    oversized.add(path.getFileName() + ": " + size + " items");
                }
            } else {
                int items = 0;
                int widest = 0;
                JsonArray pattern = recipe.getAsJsonArray("pattern");
                for (JsonElement row : pattern) {
                    String text = row.getAsString();
                    widest = Math.max(widest, text.length());
                    for (char symbol : text.toCharArray()) {
                        if (symbol != ' ') {
                            items++;
                        }
                    }
                }
                if (pattern.size() > 3 || widest > 3 || items > MAX_GRID_ITEMS) {
                    oversized.add(path.getFileName() + ": pattern " + pattern);
                }
            }
        }
        assertTrue(oversized.isEmpty(),
                "a recipe that needs more than " + MAX_GRID_ITEMS + " items can never be crafted, "
                        + "in the grid or through the recipe book: " + oversized);
    }

    @Test
    void shapedPatternsAndKeysAgree() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = parse(path);
            if (recipe == null || !isCrafting(recipe) || typeOf(recipe).endsWith("shapeless")) {
                continue;
            }
            assertNotNull(recipe.get("pattern"), path.getFileName() + " has no pattern");
            JsonObject key = recipe.getAsJsonObject("key");
            assertNotNull(key, path.getFileName() + " has no key");

            StringBuilder pattern = new StringBuilder();
            for (JsonElement row : recipe.getAsJsonArray("pattern")) {
                pattern.append(row.getAsString());
            }
            for (int index = 0; index < pattern.length(); index++) {
                char symbol = pattern.charAt(index);
                if (symbol != ' ' && !key.has(String.valueOf(symbol))) {
                    broken.add(path.getFileName() + ": pattern uses '" + symbol + "'");
                }
            }
            for (String symbol : key.keySet()) {
                if (pattern.indexOf(symbol) < 0) {
                    broken.add(path.getFileName() + ": key defines unused '" + symbol + "'");
                }
            }
        }
        assertTrue(broken.isEmpty(), "a pattern and its key must describe the same recipe: " + broken);
    }

    @Test
    void theShippedCraftingRecipesAreAllPresent() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = parse(path);
            if (recipe != null && isCrafting(recipe)) {
                names.add(name(path));
            }
        }        assertEquals(CRAFTING_RECIPES, names.size(), "crafting recipes shipped: " + names);
        for (String expected : RESTORED) {
            assertTrue(names.contains(expected), "the count fix must keep " + expected);
        }
    }
}
