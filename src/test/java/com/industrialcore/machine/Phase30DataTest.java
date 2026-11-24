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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits the shipped Phase 30 synthesis JSON against the approved design table.
 *
 * <p>The files are the single source of truth, so a mistyped count would silently change the
 * progression. The tests read the real files. The fifteen ore amplifier tiers this class used to
 * audit left with the old ore line in Phase 31; the new fifteen stage chain is audited by
 * {@code OreLineAuditTest}.</p>
 */
class Phase30DataTest {
    private static final Gson GSON = new Gson();
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");

    private static final String CATALYST = "industrialcore:resonant_catalyst";
    /**
     * Catalyst of every synthesis ladder step. Phase 31 moved the ladder onto the ore line's dust
     * grades and this crystal: a metal catalyst (a finished ingot or a synthetic catalyst built
     * from crystals) would put the ladder and the ore line on one material cycle.
     */
    private static final String LADDER_CATALYST = "industrialcore:reconstruction_crystal";

    private static List<Path> files(String prefix) throws IOException {
        try (Stream<Path> stream = Files.list(RECIPES)) {
            return stream.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .toList();
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return GSON.fromJson(Files.readString(path), JsonObject.class);
    }

    private static String item(JsonObject holder) {
        return holder == null || holder.get("item") == null || !holder.get("item").isJsonPrimitive()
                ? "" : holder.get("item").getAsString();
    }

    /** All nine synthetic ores plus the bulk catalyst recipe. */
    @Test
    void synthesisRecipesMatchTheDesignTable() throws IOException {
        assertEquals(10, files("synthesis_").size(), "there must be 10 synthesis recipes");
        assertSynthesis("synthesis_1", "industrialcore:synthetic_ore_1", 10, 20, new Object[][] {
                {"industrialcore:adamantite_dust", 1}, {"industrialcore:mithril_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_2", "industrialcore:synthetic_ore_2", 10, 20, new Object[][] {
                {"industrialcore:synthetic_ore_1", 1}, {"industrialcore:orichalcum_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_3", "industrialcore:synthetic_ore_3", 10, 20, new Object[][] {
                {"industrialcore:synthetic_ore_2", 1}, {"industrialcore:quantum_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_4", "industrialcore:synthetic_ore_4", 10, 20, new Object[][] {
                {"industrialcore:synthetic_ore_3", 1}, {"industrialcore:void_iron_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_5", "industrialcore:synthetic_ore_5", 10, 20, new Object[][] {
                {"industrialcore:synthetic_ore_4", 1}, {"industrialcore:aether_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_6", "industrialcore:synthetic_ore_6", 10, 20, new Object[][] {
                {"industrialcore:synthetic_ore_5", 1}, {"industrialcore:nebula_dust", 1},
                {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_high_density", "industrialcore:synthetic_ore_high_density",
                10, 25, new Object[][] {
                        {"industrialcore:synthetic_ore_6", 1}, {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_unstable_ultimate",
                "industrialcore:synthetic_ore_unstable_ultimate", 10, 40, new Object[][] {
                        {"industrialcore:synthetic_ore_high_density", 1},
                        {"industrialcore:astral_silver_dust", 1},
                        {LADDER_CATALYST, 1}});
        assertSynthesis("synthesis_ultimate", "industrialcore:synthetic_ore_ultimate", 10, 60,
                new Object[][] {
                        {"industrialcore:synthetic_ore_unstable_ultimate", 1},
                        {"industrialcore:astral_silver_dust", 1},
                        {"industrialcore:mithril_dust", 1},
                        {"industrialcore:nebula_dust", 1},
                        {"industrialcore:orichalcum_dust", 1},
                        {"industrialcore:quantum_dust", 1},
                        {"industrialcore:adamantite_dust", 1},
                        {"industrialcore:aether_dust", 1},
                        {"industrialcore:void_iron_dust", 1},
                        {LADDER_CATALYST, 1}});
        JsonObject bulk = read(RECIPES.resolve("synthesis_catalyst_bulk.json"));
        assertEquals(4, bulk.getAsJsonObject("result").get("count").getAsInt(),
                "the bulk recipe must yield four catalysts");
    }

    private static void assertSynthesis(String file, String result, int ticks, int energy,
                                        Object[][] entries) throws IOException {
        JsonObject json = read(RECIPES.resolve(file + ".json"));
        assertEquals("industrialcore:synthesis", json.get("type").getAsString());
        assertEquals(result, json.getAsJsonObject("result").get("id").getAsString(), file);
        assertEquals(ticks, json.get("time").getAsInt(), "time of " + file);
        assertEquals(energy, json.get("energy").getAsInt(), "energy of " + file);
        JsonArray ingredients = json.getAsJsonArray("ingredients");
        assertEquals(entries.length, ingredients.size(), "ingredient count of " + file);
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (JsonElement element : ingredients) {
            JsonObject entry = element.getAsJsonObject();
            actual.put(entry.getAsJsonObject("ingredient").get("item").getAsString(),
                    entry.get("count").getAsInt());
        }
        for (Object[] row : entries) {
            assertEquals(row[1], actual.get((String) row[0]), "count of " + row[0] + " in " + file);
        }
    }

    /**
     * The cumulative cost of one ultimate synthetic ore, exactly as documented once the ladder
     * runs on the ore line's dust grades: one dust of each of the eight mythic metals and nine
     * reconstruction crystals (one per ladder craft) - eight metal units in total.
     *
     * <p>That price is what the last stage of the chain can afford: one catalyst is consumed per
     * operation, and one operation yields sixty-four ingots, so a catalyst has to stay well below
     * sixty-four metal units. The ladder used to double its own tier (two synthetic ore VII per
     * synthetic ore VIII) and to gate nine stages, which cost over two thousand metal units per
     * sixty-four ingots; both are fixed here. No finished ingot or crystal is consumed either, so
     * the ladder cannot feed the line it unlocks.</p>
     *
     * <p>The eight dust were reduced from four to one per recipe when the cost of a full
     * x32,768 run turned out to pull ten thousand raw ore of the other metals; the balance the
     * last stage depends on is unchanged, it only stops charging for metal the line never used.</p>
     */
    @Test
    void oneUltimateSyntheticOreCostsTheDocumentedMaterials() throws IOException {
        Map<String, SynthesisRecipeData> recipes = synthesisLadder();

        Map<String, Integer> need = new LinkedHashMap<>();
        Map<String, Integer> materials = new TreeMap<>();
        need.put("industrialcore:synthetic_ore_ultimate", 1);

        int guard = 0;
        while (!need.isEmpty() && guard++ < 50) {
            Map.Entry<String, Integer> entry = need.entrySet().iterator().next();
            String item = entry.getKey();
            int quantity = entry.getValue();
            need.remove(item);
            if (quantity <= 0) {
                continue;
            }
            SynthesisRecipeData recipe = recipes.get(item);
            if (recipe == null) {
                add(materials, item, quantity);
                continue;
            }
            int crafts = quantity / recipe.count;
            if ((long) crafts * recipe.count < quantity) {
                crafts++;
            }
            for (String[] ingredient : recipe.entries) {
                add(need, ingredient[0], crafts * Integer.parseInt(ingredient[1]));
            }
        }

        assertEquals(2, materials.get("industrialcore:adamantite_dust"), "adamantite");
        assertEquals(2, materials.get("industrialcore:mithril_dust"), "mithril");
        assertEquals(2, materials.get("industrialcore:orichalcum_dust"), "orichalcum");
        assertEquals(2, materials.get("industrialcore:quantum_dust"), "quantum");
        assertEquals(2, materials.get("industrialcore:void_iron_dust"), "void iron");
        assertEquals(2, materials.get("industrialcore:aether_dust"), "aether");
        assertEquals(2, materials.get("industrialcore:nebula_dust"), "nebula");
        assertEquals(2, materials.get("industrialcore:astral_silver_dust"), "astral silver");
        assertEquals(9, materials.get(LADDER_CATALYST), "one crystal per ladder craft");
        for (String metal : new String[] {"adamantite", "mithril", "orichalcum", "quantum",
                "void_iron", "aether", "nebula", "astral_silver"}) {
            assertNull(materials.get("industrialcore:" + metal + "_ingot"),
                    "the ladder must not consume finished " + metal);
        }
        assertNull(materials.get(CATALYST), "the ladder no longer needs the resonant catalyst");

        // The balance the last stage depends on: one catalyst is consumed per operation and one
        // operation yields 64 ingots, so the catalyst has to stay below 64 metal units. The old
        // doubling ladder (2x of the previous tier, nine gated stages) cost over 2,000 and made
        // every stage above 12 produce less metal than it ate.
        int catalystMetal = materials.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("_dust"))
                .mapToInt(Map.Entry::getValue)
                .sum();
        assertEquals(16, catalystMetal,
                "one ultimate catalyst costs sixteen metal units: one in its ladder tier and one "
                        + "in the ultimate recipe, per metal");
        assertTrue(catalystMetal < 64,
                "a catalyst must cost less metal than the 64 ingots one operation yields, "
                        + "otherwise the last stage destroys metal: " + catalystMetal);
    }

    /**
     * One x32,768 run of the line needs 512 ultimate catalysts. The ladder yields four per craft,
     * so the run costs 128 craft cycles: 2,048 dust (256 of each of the eight metals), 1,152
     * reconstruction crystals, 313,600 FE and 11,520 ticks. Every ladder recipe runs 10 ticks
     * since the time unification, so one craft is 9 operations x 10 ticks = 90 ticks.
     *
     * <p>Every number comes from the shipped JSON: the ladder is priced once by walking it, then
     * multiplied by the crafts the line really needs. That is the check the earlier "five dust
     * each" number was missing - the per-craft price was fine while the whole run pulled ten
     * thousand raw ore of the other metals.</p>
     */
    @Test
    void oneFullRunNeedsOneHundredTwentyEightCatalystCrafts() throws IOException {
        Map<String, SynthesisRecipeData> recipes = synthesisLadder();
        Map<String, Integer> materials = new TreeMap<>();
        long[] totals = new long[2];
        synthesize("industrialcore:synthetic_ore_ultimate", 1, recipes, materials, totals);

        assertEquals(2_450L, totals[0], "one ultimate catalyst costs 2,450 FE");
        assertEquals(90L, totals[1], "one ultimate catalyst takes 90 ticks");
        assertEquals(9, materials.get(LADDER_CATALYST), "one crystal per ladder craft");
        int metal = materials.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("_dust"))
                .mapToInt(Map.Entry::getValue)
                .sum();
        assertEquals(16, metal, "two dust of each of the eight metals");

        int perCraft = recipes.get("industrialcore:synthetic_ore_ultimate").count();
        assertEquals(4, perCraft, "the ultimate catalyst is crafted four at a time");
        int needed = 512;
        assertEquals(0, needed % perCraft, "the run must consume whole crafts");
        int crafts = needed / perCraft;
        assertEquals(128, crafts, "512 catalysts cost 128 craft cycles");

        assertEquals(2_048L, (long) metal * crafts, "dust of the eight metals");
        assertEquals(1_152L, (long) materials.get(LADDER_CATALYST) * crafts, "crystals");
        assertEquals(313_600L, totals[0] * crafts, "catalyst FE of one full run");
        assertEquals(11_520L, totals[1] * crafts, "catalyst ticks of one full run");
    }

    /** Prices one item through the synthesis ladder, adding FE, ticks and raw materials. */
    private static void synthesize(String item, long quantity,
                                   Map<String, SynthesisRecipeData> recipes,
                                   Map<String, Integer> materials, long[] totals) {
        SynthesisRecipeData recipe = recipes.get(item);
        if (recipe == null) {
            materials.merge(item, (int) quantity, Integer::sum);
            return;
        }
        long crafts = (quantity + recipe.count() - 1) / recipe.count();
        totals[0] += crafts * recipe.time() * (long) recipe.energy();
        totals[1] += crafts * recipe.time();
        for (String[] ingredient : recipe.entries()) {
            synthesize(ingredient[0], crafts * Integer.parseInt(ingredient[1]),
                    recipes, materials, totals);
        }
    }

    /** Every synthesis recipe by output id, with its time and FE per tick. */
    private static Map<String, SynthesisRecipeData> synthesisLadder() throws IOException {
        Map<String, SynthesisRecipeData> recipes = new LinkedHashMap<>();
        for (Path path : files("synthesis_")) {
            JsonObject json = read(path);
            String output = json.getAsJsonObject("result").get("id").getAsString();
            int count = json.getAsJsonObject("result").get("count").getAsInt();
            List<String[]> entries = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("ingredients")) {
                JsonObject entry = element.getAsJsonObject();
                JsonObject ingredient = entry.has("ingredient")
                        ? entry.getAsJsonObject("ingredient") : entry;
                entries.add(new String[] {item(ingredient),
                        String.valueOf(entry.has("count") ? entry.get("count").getAsInt() : 1)});
            }
            recipes.put(output, new SynthesisRecipeData(count, json.get("time").getAsInt(),
                    json.get("energy").getAsInt(), entries));
        }
        return recipes;
    }

    private static void add(Map<String, Integer> map, String key, int amount) {
        map.merge(key, amount, Integer::sum);
    }

    private record SynthesisRecipeData(int count, int time, int energy, List<String[]> entries) {}

    /** The MK3 buffer keeps the reviewed build cost. */
    @Test
    void theHighDensityBufferCostsTheReviewedParts() throws IOException {
        JsonObject json = read(RECIPES.resolve("energy_buffer_mk3.json"));
        Map<String, Integer> counts = ingredientAmounts(json);
        assertEquals(2, counts.get("industrialcore:energy_buffer_mk2"), "mk2");
        assertEquals(1, counts.get("industrialcore:durable_machine_frame"), "durable frame");
        assertEquals(1, counts.get("industrialcore:phase_core"), "phase core");
        assertEquals(2, counts.get(CATALYST), "catalyst");
    }

    /**
     * The amount of every ingredient of a crafting recipe, counted as one entry per item: 1.21.1
     * has no amount inside an ingredient, so a requirement is written as that many separate
     * entries (the parsed recipe agrees, see {@code CraftingRecipeGameTests}).
     */
    private static Map<String, Integer> ingredientAmounts(JsonObject recipe) {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
            amounts.merge(element.getAsJsonObject().get("item").getAsString(), 1, Integer::sum);
        }
        return amounts;
    }
}
