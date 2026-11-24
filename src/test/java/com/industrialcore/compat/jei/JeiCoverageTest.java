package com.industrialcore.compat.jei;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard of the JEI coverage table.
 *
 * <p>JEI shows nothing for a recipe type that nobody registers a category for, and it shows a
 * wrong picture when a category points at an item that does not exist. Neither failure is loud:
 * the game simply starts and the machine is missing from the recipe viewer. These checks run
 * without Minecraft, so they hold even when the client cannot be started:</p>
 *
 * <ol>
 *   <li>the recipe types the mod registers, the recipe types the shipped data uses and the types
 *       the JEI table covers are the same set — no machine left out, no category for a machine
 *       that does not exist;</li>
 *   <li>every recipe type of the table has a case in the plugin, which is what actually builds the
 *       category and feeds it the recipes;</li>
 *   <li>every icon and every catalyst of the table is a shipped item, and every title is
 *       translated in both language files;</li>
 *   <li>the layout of every category is large enough for its slots, its arrow and its text;</li>
 *   <li>no class outside this package mentions JEI, so the mod keeps loading without it.</li>
 * </ol>
 */
class JeiCoverageTest {
    private static final Gson GSON = new Gson();

    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path LANG = Path.of("src/main/resources/assets/industrialcore/lang");
    private static final Path ITEM_MODELS =
            Path.of("src/main/resources/assets/industrialcore/models/item");
    private static final Path SOURCES = Path.of("src/main/java/com/industrialcore");
    private static final Path COMPAT = Path.of("src/main/java/com/industrialcore/compat/jei");
    private static final Path PLUGIN = COMPAT.resolve("IndustrialCoreJeiPlugin.java");
    private static final Path RECIPE_REGISTRY =
            Path.of("src/main/java/com/industrialcore/registry/ModRecipes.java");

    /** Every recipe type the mod registers. */
    private static final int RECIPE_TYPES = 11;

    /** Minecraft's default font line height, used to place the lines of a category. */
    private static final int LINE_HEIGHT = 10;

    /** {@code RECIPE_TYPES.register("name", ...)} in the registry class. */
    private static final Pattern REGISTERED_TYPE =
            Pattern.compile("RECIPE_TYPES\\.register\\(\\s*\"([a-z_0-9]+)\"", Pattern.DOTALL);

    /** {@code case "name" ->} in the plugin's definition switch. */
    private static final Pattern PLUGIN_CASE = Pattern.compile("case \"([a-z_0-9]+)\" ->");

    @Test
    void everyRegisteredRecipeTypeHasACategoryAndNothingElse() throws IOException {
        Set<String> registered = registeredRecipeTypes();
        Set<String> shipped = shippedRecipeTypes();
        Set<String> covered = new TreeSet<>();
        for (JeiCoverage.Category category : JeiCoverage.categories()) {
            covered.add(category.recipeTypeId());
        }

        assertEquals(RECIPE_TYPES, registered.size(),
                "ModRecipes registers " + registered.size() + " recipe types; update this test's "
                        + "count and the JEI table when a machine is added: " + registered);
        assertEquals(registered, shipped,
                "the shipped recipe data and the registered recipe types disagree; a recipe file "
                        + "of an unregistered type never loads, and a registered type without data "
                        + "shows an empty JEI category");
        assertEquals(registered, covered,
                "the JEI table must cover every recipe type exactly once");
    }

    @Test
    void everyCoveredTypeIsBuiltByThePlugin() throws IOException {
        String plugin = Files.readString(PLUGIN);
        Set<String> cases = new TreeSet<>();
        Matcher matcher = PLUGIN_CASE.matcher(plugin);
        while (matcher.find()) {
            cases.add(matcher.group(1));
        }
        Set<String> covered = new TreeSet<>();
        for (JeiCoverage.Category category : JeiCoverage.categories()) {
            covered.add(category.recipeTypeId());
        }
        assertEquals(covered, cases,
                "a type without a case in IndustrialCoreJeiPlugin gets no category and no recipes");
    }

    @Test
    void everyIconCatalystAndTitleExists() throws IOException {
        Set<String> models = itemModels();
        JsonObject english = lang("en_us");
        JsonObject japanese = lang("ja_jp");

        for (JeiCoverage.Category category : JeiCoverage.categories()) {
            assertTrue(models.contains(path(category.iconItem())),
                    category.recipeTypeId() + " uses " + category.iconItem()
                            + " as its icon, which has no item model");
            assertTrue(english.has(category.titleKey()),
                    category.recipeTypeId() + " has no English title: " + category.titleKey());
            assertTrue(japanese.has(category.titleKey()),
                    category.recipeTypeId() + " has no Japanese title: " + category.titleKey());

            assertTrue(!category.catalysts().isEmpty(),
                    category.recipeTypeId() + " names no machine, so no machine lists its recipes");
            for (String catalyst : category.catalysts()) {
                assertTrue(catalyst.startsWith("industrialcore:"),
                        category.recipeTypeId() + " has a catalyst outside the mod: " + catalyst);
                assertTrue(models.contains(path(catalyst)),
                        category.recipeTypeId() + " lists " + catalyst
                                + " as a machine, which has no item model");
            }
        }

        for (String key : List.of("jei.industrialcore.time", "jei.industrialcore.energy_per_tick",
                "jei.industrialcore.energy_total", "jei.industrialcore.yield",
                "jei.industrialcore.legend")) {
            assertTrue(english.has(key), "missing English translation " + key);
            assertTrue(japanese.has(key), "missing Japanese translation " + key);
        }
    }

    @Test
    void everyLayoutHoldsItsSlotsArrowAndText() {
        for (JeiCoverage.Layout layout : JeiCoverage.Layout.values()) {
            int lastSlotRow = 2 + layout.maxRows * JeiCoverage.SLOT;
            assertTrue(lastSlotRow <= layout.textTop(),
                    layout + ": the ingredient rows overlap the text block");

            int textBottom = layout.textTop() + layout.textLines() * LINE_HEIGHT;
            assertTrue(textBottom <= layout.height,
                    layout + ": the text block ends at " + textBottom + " but the category is only "
                            + layout.height + " high, so JEI clips the last line");

            int arrowRight = layout.arrowX(layout.columns) + JeiCoverage.ARROW_WIDTH;
            assertTrue(arrowRight <= layout.width,
                    layout + ": the arrow ends at " + arrowRight + " but the category is only "
                            + layout.width + " wide");

            int outputRight = layout.outputX(layout.columns)
                    + layout.maxOutputs * JeiCoverage.SLOT;
            assertTrue(outputRight <= layout.width,
                    layout + ": the output slots end at " + outputRight
                            + " but the category is only " + layout.width + " wide");

            assertTrue(layout.textLines() >= 3,
                    layout + ": a machine category always states its duration and its FE");
        }
    }

    @Test
    void theModNeverTouchesJeiOutsideItsCompatPackage() throws IOException {
        List<String> leaks = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(Path.of("src/main/java"))) {
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.startsWith(COMPAT)) {
                    continue;
                }
                String source = Files.readString(file);
                if (source.contains("mezz.jei")) {
                    leaks.add(file.toString());
                }
            }
        }
        assertTrue(leaks.isEmpty(),
                "JEI is optional, so only " + COMPAT + " may reference it, but these files do: "
                        + leaks);
    }

    // ------------------------------------------------------------------
    // reading the project
    // ------------------------------------------------------------------

    private static Set<String> registeredRecipeTypes() throws IOException {
        String source = Files.readString(RECIPE_REGISTRY);
        Set<String> ids = new TreeSet<>();
        Matcher matcher = REGISTERED_TYPE.matcher(source);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    /** The types the shipped recipe files use, without the {@code industrialcore:} namespace. */
    private static Set<String> shippedRecipeTypes() throws IOException {
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> stream = Files.list(RECIPES)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                JsonObject recipe = GSON.fromJson(Files.readString(file), JsonObject.class);
                assertNotNull(recipe, file + " is not a JSON object");
                String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
                if (type.startsWith("industrialcore:")) {
                    ids.add(type.substring("industrialcore:".length()));
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IOException("no machine recipe was found in " + RECIPES);
        }
        return ids;
    }

    private static Set<String> itemModels() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> stream = Files.list(ITEM_MODELS)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json")) {
                    names.add(name.substring(0, name.length() - ".json".length()));
                }
            }
        }
        return names;
    }

    private static JsonObject lang(String locale) throws IOException {
        Path file = LANG.resolve(locale.toLowerCase(Locale.ROOT) + ".json");
        return GSON.fromJson(Files.readString(file), JsonObject.class);
    }

    /** The path of an item id, e.g. {@code industrialcore:ore_amplifier_2} -> {@code ore_amplifier_2}. */
    private static String path(String itemId) {
        int colon = itemId.indexOf(':');
        return colon < 0 ? itemId : itemId.substring(colon + 1);
    }
}
