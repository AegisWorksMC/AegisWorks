package com.industrialcore.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 21 unit tests: the batch formula, the line totals and the shipped recipe files.
 *
 * <p>The numbers are the ones of {@code docs/PHASE21_SEED_AMPLIFIER.md}. Every JSON claim is read
 * back from the real resource files, so a hand-edited recipe that drifts away from
 * {@link SeedLineBalance} fails the build instead of silently changing the 12x / 16x lines.</p>
 *
 * <p>What this test cannot see: the actual machine behaviour (that needs the GameTests), and the
 * crafting recipes' ingredient counts, which are checked only for shape.</p>
 */
class SeedLineTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final List<String> METALS = List.of("iron", "copper", "gold");
    /** Dust one seed costs in the crafting table (2 amethyst + 4 dust + 2 glass). */
    private static final int SEED_CRAFT_DUST = 4;

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(name))).getAsJsonObject();
    }

    private static List<JsonObject> allRecipes() throws IOException {
        List<JsonObject> recipes = new ArrayList<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                recipes.add(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        }
        return recipes;
    }

    private static String typeOf(JsonObject recipe) {
        JsonElement type = recipe.get("type");
        return type == null ? "" : type.getAsString();
    }

    private static String idOf(JsonObject object) {
        if (object == null) {
            return "";
        }
        JsonElement id = object.get("id");
        return id == null ? "" : id.getAsString();
    }

    /** Item id of an ingredient object ({@code ingredient}, {@code feed}, {@code catalyst}). */
    private static String itemOf(JsonObject object) {
        if (object == null) {
            return "";
        }
        JsonElement item = object.get("item");
        return item == null ? "" : item.getAsString();
    }

    private static int countOf(JsonObject object) {
        if (object == null) {
            return 1;
        }
        JsonElement count = object.get("count");
        return count == null ? 1 : count.getAsInt();
    }

    private static String ingredientId(JsonObject recipe) {
        return itemOf(recipe.getAsJsonObject("ingredient"));
    }

    private static String resultId(JsonObject recipe) {
        return idOf(recipe.getAsJsonObject("result"));
    }

    private static int intOf(JsonObject recipe, String key, int fallback) {
        return recipe.has(key) ? recipe.get(key).getAsInt() : fallback;
    }

    // ------------------------------------------------------------------
    // 1. Batch formula
    // ------------------------------------------------------------------

    @Test
    void batchTimeFollowsTheDocumentedFormula() {
        assertEquals(600, SeedLineBalance.billetTime(1));
        assertEquals(2_250, SeedLineBalance.billetTime(12));
        assertEquals(2_850, SeedLineBalance.billetTime(16));
        assertEquals(10_050, SeedLineBalance.billetTime(64));
        // A large batch amortises the start-up: 157 ticks per billet at N = 64.
        assertEquals(10_050 / 64, SeedLineBalance.billetTime(64) / 64);
        // Out of range values are clamped, never negative.
        assertEquals(600, SeedLineBalance.billetTime(0));
        assertEquals(10_050, SeedLineBalance.billetTime(1_000));
    }

    @Test
    void batchEnergyIsLinearAndNeverOverflows() {
        assertEquals(24_000L, SeedLineBalance.billetEnergy(1));
        assertEquals(288_000L, SeedLineBalance.billetEnergy(12));
        assertEquals(384_000L, SeedLineBalance.billetEnergy(16));
        assertEquals(1_536_000L, SeedLineBalance.billetEnergy(64));
        // A formula raised to a huge batch must still be a long, not a wrapped int.
        assertEquals(24_000L * SeedLineBalance.MAX_BATCH,
                SeedLineBalance.billetEnergy(Integer.MAX_VALUE));
    }

    @Test
    void requiredFePerTickKeepsTheDocumentedCeiling() {
        assertEquals(40, SeedLineBalance.requiredFePerTick(SeedLineBalance.billetEnergy(1),
                SeedLineBalance.billetTime(1)));
        assertEquals(128, SeedLineBalance.requiredFePerTick(SeedLineBalance.billetEnergy(12),
                SeedLineBalance.billetTime(12)));
        assertEquals(130, SeedLineBalance.requiredFePerTick(SeedLineBalance.billetEnergy(13),
                SeedLineBalance.billetTime(13)));
        assertEquals(153, SeedLineBalance.requiredFePerTick(SeedLineBalance.billetEnergy(64),
                SeedLineBalance.billetTime(64)));
        assertTrue(SeedLineBalance.requiredFePerTick(SeedLineBalance.billetEnergy(64),
                        SeedLineBalance.billetTime(64)) < 160,
                "a batch must stay below 160 FE/t so the default cable tiers are the only limit");
        assertEquals(0, SeedLineBalance.requiredFePerTick(0, 600));
        // The last tick always pays the remainder, so the run never finishes short.
        assertEquals(5_000, SeedLineBalance.requiredFePerTick(5_000, 1));
    }

    @Test
    void feedIsFourDustPerBilletInFourInstalments() {
        assertEquals(4, SeedLineBalance.FEED_PER_UNIT);
        assertEquals(4, SeedLineBalance.FEED_INSTALMENTS);
        assertEquals(4, SeedLineBalance.feedFor(1));
        assertEquals(256, SeedLineBalance.feedFor(64));
        assertEquals(1, SeedLineBalance.batchFromFeed(4));
        assertEquals(1, SeedLineBalance.batchFromFeed(7));
        assertEquals(16, SeedLineBalance.batchFromFeed(64));
        assertEquals(1, SeedLineBalance.batchFromFeed(0));
        assertEquals(1, SeedLineBalance.clampBatch(-3));
        assertEquals(SeedLineBalance.MAX_BATCH, SeedLineBalance.clampBatch(999));
    }

    // ------------------------------------------------------------------
    // 2. Line totals
    // ------------------------------------------------------------------

    @Test
    void twelveTimesLineAddsUp() {
        assertEquals(57_200L, SeedLineBalance.twelveTimesCost(),
                "12x: 24,000 (cultivate) + 6,000 + 12,000 + 5,600 + 9,600");
        assertEquals(3_260, SeedLineBalance.twelveTimesTicks(),
                "12x: 600 + 300 + 600 + 560 + 1,200 ticks");
        assertEquals(12, SeedLineBalance.twelveTimesIngots());
        assertEquals(72_000L, SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN);
    }

    @Test
    void sixteenTimesLineAddsUp() {
        assertEquals(132_400L, SeedLineBalance.sixteenTimesCost(),
                "16x: 24,000 + 72,000 + 6,000 + 12,000 + 5,600 + 12,800");
        assertEquals(4_960, SeedLineBalance.sixteenTimesTicks(),
                "16x: 600 + 1,200 + 400 + 600 + 560 + 1,600 ticks");
        assertEquals(108_400L, SeedLineBalance.sixteenTimesFromBilletCost(),
                "a billet start saves the cultivation energy only");
        assertEquals(4_360, SeedLineBalance.sixteenTimesFromBilletTicks());
        assertEquals(16, SeedLineBalance.sixteenTimesIngots());
    }

    /** The implemented 2x tier must not move because Phase 21 was added. */
    @Test
    void existingTiersAreUnchanged() {
        assertEquals(2_800L, ProcessingBalance.twoTimesCost());
    }

    /**
     * Phase 31 replaced the 3x/6x lines with the fifteen stage ore ladder, so their cost helpers
     * are gone. This pins what the ladder costs instead: one raw iron walks every stage once and
     * pays 1,006,860 FE over 10,870 ticks for 32,768 ingots, read back from the shipped JSON.
     * Every stage runs 10 ticks since the time unification, so the tick total is 1,087
     * operations x 10 ticks.
     *
     * <p>The test walks the files the way the player does: it looks up the batch the recipe
     * really lists, divides the items carried into that stage by it, and multiplies by the time
     * and FE per tick of that recipe. A hand-edited time, FE or count therefore fails here.</p>
     */
    @Test
    void theOreLineCostsTheDocumentedEnergy() throws IOException {
        long totalEnergy = 0;
        long totalTicks = 0;
        long items = 1;
        for (int stage = 1; stage <= 15; stage++) {
            String file = stage == 1 ? "crush_raw_iron.json" : "oreline_s" + stage + "_iron.json";
            JsonObject recipe = read(file);
            // Stages 1-6 double one item (single "ingredient"); stages 7-15 batch a list.
            int inputCount = recipe.has("ingredients")
                    ? recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject()
                            .get("count").getAsInt()
                    : 1;
            int outputCount = recipe.getAsJsonObject("result").get("count").getAsInt();
            assertEquals(0, items % inputCount,
                    "stage " + stage + " of " + file + " does not divide " + items);
            long crafts = items / inputCount;
            int time = recipe.get("time").getAsInt();
            int energy = recipe.get("energy").getAsInt();
            totalEnergy += crafts * (long) time * energy;
            totalTicks += crafts * time;
            items = crafts * outputCount;
        }

        assertEquals(32_768L, items, "one raw ore must end as 32,768 ingots");
        assertEquals(1_006_860L, totalEnergy,
                "the fifteen stages cost 1,006,860 FE for one raw ore");
        assertEquals(10_870L, totalTicks,
                "the fifteen stages take 10,870 ticks in series for one raw ore");
    }

    // ------------------------------------------------------------------
    // 3. Shipped recipes match the balance constants
    // ------------------------------------------------------------------

    @Test
    void cultivationRecipesMatchTheBalance() throws IOException {
        for (String metal : METALS) {
            JsonObject recipe = read("cultivation_" + metal + ".json");
            assertEquals("industrialcore:cultivation", typeOf(recipe));
            assertEquals("industrialcore:" + metal + "_seed", ingredientId(recipe));
            assertEquals("industrialcore:" + metal + "_dust",
                    itemOf(recipe.getAsJsonObject("feed")));
            assertEquals("industrialcore:" + metal + "_billet", resultId(recipe));
            assertEquals(1, countOf(recipe.getAsJsonObject("result")));
            assertEquals(SeedLineBalance.CULTIVATOR_TIME_TICKS, intOf(recipe, "time", -1),
                    metal + " cultivation time drifted");
            assertEquals(SeedLineBalance.CULTIVATOR_ENERGY_PER_TICK, intOf(recipe, "energy", -1),
                    metal + " cultivation energy drifted");
            assertEquals(SeedLineBalance.FEED_PER_UNIT, intOf(recipe, "feedPerUnit", -1),
                    metal + " feed per unit drifted");
        }
    }

    @Test
    void amplificationRecipesMatchTheBalance() throws IOException {
        for (String metal : METALS) {
            JsonObject recipe = read("amplification_" + metal + ".json");
            assertEquals("industrialcore:amplification", typeOf(recipe));
            assertEquals("industrialcore:" + metal + "_billet", ingredientId(recipe));
            assertEquals("industrialcore:reconstruction_crystal",
                    itemOf(recipe.getAsJsonObject("catalyst")));
            assertEquals("minecraft:" + metal + "_ore", resultId(recipe));
            assertEquals(SeedLineBalance.AMPLIFIER_ORE_COUNT,
                    countOf(recipe.getAsJsonObject("result")),
                    metal + " amplifier must output exactly two ore blocks");
            assertEquals("industrialcore:" + metal + "_dust",
                    idOf(recipe.getAsJsonObject("secondary")));
            assertEquals(SeedLineBalance.AMPLIFIER_DUST_COUNT,
                    countOf(recipe.getAsJsonObject("secondary")),
                    metal + " amplifier must output exactly four dust");
            assertEquals(SeedLineBalance.AMPLIFIER_TIME_TICKS, intOf(recipe, "time", -1));
            assertEquals(SeedLineBalance.AMPLIFIER_ENERGY_PER_TICK, intOf(recipe, "energy", -1));
        }
    }

    /**
     * Every metal has exactly one seed -> billet cultivation and one billet -> ore amplification,
     * and no other machine recipe may consume a billet.
     *
     * <p>Phase 31 removed the billet -> exposed precision route and the pure -> dust crush: those
     * shortcuts were part of the old multiplication line, and the new fifteen stage ore line
     * reuses the same grades in the forward direction, so restoring either would close a material
     * loop. The seed line therefore sinks every billet in the amplifier.</p>
     */
    @Test
    void theSeedLineIsOneToOnePerMetal() throws IOException {
        List<JsonObject> recipes = allRecipes();
        for (String metal : METALS) {
            assertEquals(1, countOfTypeForPair(recipes, "industrialcore:cultivation",
                            "industrialcore:" + metal + "_seed",
                            "industrialcore:" + metal + "_billet"),
                    metal + " billet recipes");
            assertEquals(1, countOfTypeForPair(recipes, "industrialcore:amplification",
                            "industrialcore:" + metal + "_billet",
                            "minecraft:" + metal + "_ore"),
                    metal + " amplification recipes");
        }
        // Phase 31 removed the old recycling route (billet -> exposed, pure -> dust): a billet's
        // only machine sink is the matter amplifier, and pure ore never returns to dust.
        for (JsonObject recipe : recipes) {
            String type = typeOf(recipe);
            if (type.equals("industrialcore:precision_crushing")
                    || type.equals("industrialcore:crushing")
                    || type.equals("industrialcore:electrolysis")) {
                String input = ingredientId(recipe);
                for (String metal : METALS) {
                    assertFalse(input.equals("industrialcore:" + metal + "_billet"),
                            "a billet must only be consumed by the amplifier: " + recipe);
                }
            }
        }
    }

    private static int countOfTypeForPair(List<JsonObject> recipes, String type, String input,
                                          String result) {
        int found = 0;
        for (JsonObject recipe : recipes) {
            if (typeOf(recipe).equals(type) && ingredientId(recipe).equals(input)
                    && resultId(recipe).equals(result)) {
                found++;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // 4. Abuse prevention
    // ------------------------------------------------------------------

    /**
     * The intended loop has one direction only: dust may become a billet through cultivation, but
     * no recipe may short-circuit a stage. Every rule of
     * {@code PHASE21_SEED_AMPLIFIER.md} §6 that a data file could break is checked here.
     */
    @Test
    void noRecipeShortCircuitsTheSeedLine() throws IOException {
        for (JsonObject recipe : allRecipes()) {
            String type = typeOf(recipe);
            String ingredient = ingredientId(recipe);
            String result = resultId(recipe);
            String secondary = idOf(recipe.getAsJsonObject("secondary"));

            assertFalse(type.equals("industrialcore:cultivation") && result.endsWith("_seed"),
                    "a cultivation recipe may not produce a seed: " + recipe);
            assertFalse(type.equals("industrialcore:amplification")
                            && (result.endsWith("_seed") || result.endsWith("_billet")
                            || secondary.endsWith("_seed") || secondary.endsWith("_billet")),
                    "an amplification recipe may not produce a seed or a billet: " + recipe);
            assertFalse(ingredient.endsWith("_billet") && result.endsWith("_seed"),
                    "a billet may not become a seed: " + recipe);
            assertFalse(ingredient.endsWith("_dust") && result.endsWith("_billet"),
                    "dust may not become a billet directly (that is the cultivator's job): "
                            + recipe);
            assertFalse(ingredient.startsWith("minecraft:raw_") && result.endsWith("_billet"),
                    "a raw ore may not become a billet: " + recipe);
            assertFalse(ingredient.endsWith("_seed") && result.endsWith("_seed"),
                    "a seed may not produce another seed: " + recipe);
            if (ingredient.endsWith("reconstruction_crystal")) {
                assertFalse(result.endsWith("_ingot") || result.endsWith("_dust"),
                        "a crystal may not become an ingot or dust: " + recipe);
            }
        }
    }

    /** The amplifier produces ore <em>blocks</em>, never raw ore (that is the reconstructor). */
    @Test
    void theAmplifierNeverOutputsRawOre() throws IOException {
        for (JsonObject recipe : allRecipes()) {
            if (!typeOf(recipe).equals("industrialcore:amplification")) {
                continue;
            }
            for (String key : List.of("result", "secondary")) {
                String id = idOf(recipe.getAsJsonObject(key));
                assertFalse(id.startsWith("minecraft:raw_"),
                        "amplification outputs a raw ore: " + recipe);
            }
        }
    }

    // ------------------------------------------------------------------
    // 5. Assets and language files
    // ------------------------------------------------------------------

    @Test
    void everyNewItemAndMachineHasModelAndTexture() throws IOException {
        for (String metal : METALS) {
            for (String id : List.of(metal + "_seed", metal + "_billet")) {
                assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")),
                        "missing item model for " + id);
            }
            // Models reference short texture names because of the Windows path limit.
            assertTrue(Files.exists(ASSETS.resolve("textures/item/seed_" + metal + ".png")),
                    "missing seed texture for " + metal);
            assertTrue(Files.exists(ASSETS.resolve("textures/item/billet_" + metal + ".png")),
                    "missing billet texture for " + metal);
        }
        for (String machine : List.of("seed_cultivator", "matter_amplifier")) {
            assertTrue(Files.exists(ASSETS.resolve("models/block/" + machine + ".json")),
                    "missing block model for " + machine);
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + machine + ".json")),
                    "missing blockstate for " + machine);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + machine + ".json")),
                    "missing block item model for " + machine);
            assertTrue(Files.exists(Path.of("src/main/resources/data/industrialcore/loot_table/blocks/"
                    + machine + ".json")), "missing loot table for " + machine);
        }
    }

    @Test
    void bothLanguagesCoverTheNewScreens() throws IOException {
        List<String> keys = new ArrayList<>();
        for (String metal : METALS) {
            keys.add("item.industrialcore." + metal + "_seed");
            keys.add("item.industrialcore." + metal + "_billet");
            keys.add("gui.industrialcore.tooltip." + metal + "_seed");
            keys.add("gui.industrialcore.tooltip." + metal + "_billet");
        }
        keys.add("block.industrialcore.seed_cultivator");
        keys.add("block.industrialcore.matter_amplifier");
        for (String key : List.of("slot.seed", "slot.feed", "slot.output", "batch", "idle", "rule")) {
            keys.add("gui.industrialcore.seed_cultivator." + key);
        }
        for (String key : List.of("slot.input", "slot.catalyst", "slot.ore", "slot.dust",
                "conversion")) {
            keys.add("gui.industrialcore.matter_amplifier." + key);
        }
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = JsonParser.parseString(Files.readString(
                    ASSETS.resolve("lang/" + language + ".json"))).getAsJsonObject();
            TreeSet<String> missing = new TreeSet<>();
            for (String key : keys) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    /**
     * The seed costs 2 amethyst + 4 dust + 2 glass (one entry per item, because 1.21.1 shapeless
     * recipes have no {@code count} field). A silent drop to one dust would make the seed four
     * times cheaper than the documented economy, so the amounts are pinned here.
     */
    @Test
    void seedCraftingRecipesUseTheDocumentedAmounts() throws IOException {
        for (String metal : METALS) {
            JsonObject recipe = read("seed_" + metal + ".json");
            assertEquals("minecraft:crafting_shapeless", typeOf(recipe));
            JsonArray ingredients = recipe.getAsJsonArray("ingredients");
            assertEquals(8, ingredients.size(), metal + " seed ingredient count");
            assertEquals(2, countItem(ingredients, "minecraft:amethyst_shard"),
                    metal + " seed amethyst");
            assertEquals(SEED_CRAFT_DUST, countItem(ingredients, "industrialcore:" + metal + "_dust"),
                    metal + " seed dust");
            assertEquals(2, countItem(ingredients, "minecraft:glass"), metal + " seed glass");
            assertEquals("industrialcore:" + metal + "_seed", resultId(recipe));
        }
    }

    private static int countItem(JsonArray ingredients, String id) {
        int found = 0;
        for (JsonElement entry : ingredients) {
            if (id.equals(itemOf(entry.getAsJsonObject()))) {
                found++;
            }
        }
        return found;
    }

    /** The two machine recipes use exactly the materials of the specification (\u00a72.4 / \u00a73.4). */
    @Test
    void machineRecipesUseTheDocumentedMaterials() throws IOException {
        JsonObject cultivator = read("seed_cultivator.json");
        assertEquals(2, symbolCount(cultivator, "P"), "seed cultivator iron plates");
        assertEquals(4, symbolCount(cultivator, "G"), "seed cultivator glass");
        assertEquals(2, symbolCount(cultivator, "R"), "seed cultivator redstone");
        assertEquals(1, symbolCount(cultivator, "F"), "seed cultivator frames");

        JsonObject amplifier = read("matter_amplifier.json");
        assertEquals(4, symbolCount(amplifier, "P"), "amplifier iron plates");
        assertEquals(2, symbolCount(amplifier, "E"), "amplifier electronic components");
        assertEquals(2, symbolCount(amplifier, "D"), "amplifier diamonds");
        assertEquals(1, symbolCount(amplifier, "F"), "amplifier frames");
    }

    private static int symbolCount(JsonObject recipe, String symbol) {
        int found = 0;
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            for (char cell : row.getAsString().toCharArray()) {
                if (String.valueOf(cell).equals(symbol)) {
                    found++;
                }
            }
        }
        return found;
    }

    /** The two machine crafting recipes must be laid out as a full 3x3 pattern. */
    @Test
    void machineRecipesFitTheCraftingGrid() throws IOException {
        for (String machine : List.of("seed_cultivator", "matter_amplifier")) {
            JsonObject recipe = read(machine + ".json");
            assertEquals("minecraft:crafting_shaped", typeOf(recipe));
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            assertEquals(3, pattern.size(), machine + " pattern is not three rows");
            for (JsonElement row : pattern) {
                assertEquals(3, row.getAsString().length(), machine + " pattern row is not 3 wide");
            }
            var keys = recipe.getAsJsonObject("key");
            assertTrue(keys.size() >= 4, machine + " recipe uses too few ingredients");
            for (String symbol : keys.keySet()) {
                assertTrue(pattern.toString().contains(symbol),
                        machine + " key " + symbol + " is never used by the pattern");
            }
        }
    }
}
