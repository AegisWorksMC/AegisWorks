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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard of the ingredient-list bound of the mod's data driven recipes, on the data and on the code.
 *
 * <p>A recipe is sent to a joining player inside {@code clientbound/minecraft:update_recipes}. Its
 * ingredient list is written through {@code ByteBufCodecs.list(n)}, which throws an
 * {@code EncoderException} as soon as the list is longer than {@code n}. That happens while the
 * packet is built, so the affected recipe never reaches the player's log as a recipe error: the
 * connection fails with {@code 10 elements exceeded max size of: 8} and the world cannot be
 * entered. The bound is now one shared constant, and these checks keep it that way:</p>
 *
 * <ol>
 *   <li>no shipped recipe lists more entries than {@link RecipeLimits#MAX_INGREDIENTS}, and the
 *       per-type maxima the design relies on are pinned;</li>
 *   <li>the constant is bigger than the largest shipped recipe, so the limit is a safety net and
 *       not a second design table;</li>
 *   <li>every ingredient list codec of the mod reads {@link RecipeLimits#MAX_INGREDIENTS} — a
 *       hard-coded list size, the shape of the original bug, fails the run.</li>
 * </ol>
 *
 * <p>The companion {@code RecipeSyncGameTests} runs the same recipes through the real codecs.</p>
 */
class RecipeIngredientLimitTest {
    private static final Gson GSON = new Gson();
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path SOURCES = Path.of("src/main/java/com/industrialcore");

    /**
     * The ingredient lists that grow with the progression: their largest shipped recipe, by design.
     * The phase 30 synthesis line ends at ten entries ({@code synthesis_ultimate}); the assembler
     * and the phase reactor always use their three slots.
     */
    private static final Map<String, Integer> LARGEST_SHIPPED = Map.of(
            "industrialcore:synthesis", 10,
            "industrialcore:assembling", 3,
            "industrialcore:phase_reaction", 3);

    /** The recipe kinds whose JSON codec and network codec must share the bound. */
    private static final List<String> BOUNDED_RECIPE_CLASSES = List.of(
            "machine/AssemblerRecipe.java",
            "machine/PhaseReactionRecipe.java",
            "machine/SynthesisRecipe.java");

    // ------------------------------------------------------------------
    // the data
    // ------------------------------------------------------------------

    private static List<Path> recipeFiles() throws IOException {
        try (Stream<Path> stream = Files.list(RECIPES)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    /** Reads a recipe file; a file that cannot be read or parsed fails the run, never skipped. */
    private static JsonObject parse(Path path) throws IOException {
        JsonObject recipe = GSON.fromJson(Files.readString(path), JsonObject.class);
        assertTrue(recipe != null && recipe.has("type"),
                path.getFileName() + " is not a recipe object");
        return recipe;
    }

    private static String typeOf(JsonObject recipe) {
        JsonElement type = recipe.get("type");
        return type == null ? "" : type.getAsString();
    }

    @Test
    void noShippedRecipeExceedsTheIngredientLimit() throws IOException {
        Map<String, Integer> largest = new TreeMap<>();
        Map<String, String> largestFile = new TreeMap<>();
        List<String> offenders = new ArrayList<>();

        for (Path path : recipeFiles()) {
            JsonObject recipe = parse(path);
            String type = typeOf(recipe);
            if (!LARGEST_SHIPPED.containsKey(type)) {
                continue;
            }
            JsonElement ingredients = recipe.get("ingredients");
            assertTrue(ingredients != null && ingredients.isJsonArray(),
                    path.getFileName() + " has no ingredients array");
            int size = ingredients.getAsJsonArray().size();
            if (size > largest.getOrDefault(type, 0)) {
                largest.put(type, size);
                largestFile.put(type, path.getFileName().toString());
            }
            if (size > RecipeLimits.MAX_INGREDIENTS) {
                offenders.add(path.getFileName() + " lists " + size + " ingredients");
            }
        }

        assertTrue(offenders.isEmpty(),
                "a recipe above the shared limit cannot be sent to a client at all, so it must "
                        + "not load either: " + offenders);
        assertEquals(new TreeMap<>(LARGEST_SHIPPED), largest,
                "the largest shipped ingredient list per type changed; the largest is " + largestFile);
    }

    @Test
    void theLimitLeavesRoomForTheLargestRecipe() {
        assertEquals(16, RecipeLimits.MAX_INGREDIENTS,
                "the bound is a safety net above the design table, not a second design table");
        for (Map.Entry<String, Integer> entry : LARGEST_SHIPPED.entrySet()) {
            assertTrue(RecipeLimits.MAX_INGREDIENTS > entry.getValue(),
                    entry.getKey() + " ships " + entry.getValue()
                            + " ingredients, which leaves no room under "
                            + RecipeLimits.MAX_INGREDIENTS);
        }
    }

    // ------------------------------------------------------------------
    // the code
    // ------------------------------------------------------------------

    /**
     * The source without its comments: the example of the bug in a javadoc is documentation, not a
     * hard-coded bound, so the scan has to look at the code alone.
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static List<Path> sourceFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /**
     * The recurrence guard: an ingredient list bound may only come from
     * {@link RecipeLimits#MAX_INGREDIENTS}. Every list codec of the mod is checked for a numeric
     * size, and the three recipe types have to feed both of their codecs from the shared constant —
     * the JSON codec through {@link RecipeLimits#ingredientList} and the network codec through
     * {@code ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)}.
     */
    @Test
    void bothCodecsOfEveryRecipeUseTheSharedLimit() throws IOException {
        List<String> hardCoded = new ArrayList<>();
        Map<String, String> recipes = new LinkedHashMap<>();

        for (Path path : sourceFiles()) {
            String source = withoutComments(Files.readString(path));
            if (source.matches("(?s).*ByteBufCodecs\\.(list|collection)\\(\\s*[0-9].*")) {
                hardCoded.add(path.getFileName() + " sizes a list codec with a literal");
            }
            for (String name : BOUNDED_RECIPE_CLASSES) {
                if (path.endsWith(name)) {
                    recipes.put(name, source);
                }
            }
        }

        assertTrue(hardCoded.isEmpty(),
                "a literal list size is what silently disagreed with the JSON codec and broke the "
                        + "recipe sync; use RecipeLimits.MAX_INGREDIENTS: " + hardCoded);
        assertEquals(new java.util.TreeSet<>(BOUNDED_RECIPE_CLASSES),
                new java.util.TreeSet<>(recipes.keySet()),
                "the recipe classes with an ingredient list were renamed or moved");

        for (Map.Entry<String, String> entry : recipes.entrySet()) {
            String source = entry.getValue();
            String name = entry.getKey();
            assertTrue(source.contains("RecipeLimits.ingredientList("),
                    name + " must read its JSON ingredient list through RecipeLimits");
            assertTrue(source.contains("ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)"),
                    name + " must use the shared bound on its network codec");
            assertFalse(source.contains("listOf().fieldOf(\"ingredients\")"),
                    name + " must not decode an unbounded JSON ingredient list");
        }
    }

    /** The constant is documented where it is used, so the next reader knows why it exists. */
    @Test
    void theLimitIsDocumented() throws IOException {
        String source = Files.readString(SOURCES.resolve("machine/RecipeLimits.java"));
        assertTrue(source.contains("update_recipes"),
                "RecipeLimits must say which packet makes the bound mandatory");
        assertTrue(source.contains("writeCount") || source.contains("ByteBufCodecs"),
                "RecipeLimits must name the codec that enforces the bound");
    }
}
