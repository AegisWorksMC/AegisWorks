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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 31 data test: the fifteen stage ore line must add up to a cumulative x32,768.
 *
 * <p>The old one-shot amplifier (one raw ore in, N raw ore out) is gone. The line is now a chain
 * of dedicated machines, one stage per doubling, and every stage reads from the real JSON:</p>
 * <pre>
 *   1 raw ore        -> crusher          -> 2 dust
 *   2 dust           -> electrolyzer     -> 2 pure ore   (x2 per dust)
 *   3 pure ore       -> precision crusher-> 2 exposed    (x4 cumulative)
 *   4 exposed        -> metal press       -> 2 washed    (x8)
 *   5 washed         -> crusher           -> 2 sorted
 *   6 sorted         -> electrolyzer      -> 2 concentrate
 *   7-15 concentrate -> synthesizer       -> 2 refined ... -> 64 ingots per 32 input
 * </pre>
 * <p>Each of the eleven ores walks the same fifteen stages, and every grade smelts 1:1 back into
 * its ingot so a milestone can be banked. The tests pin the per-stage contract, the cumulative
 * multiplier, the smelting ladder, the forward-only grade order (a backward edge would close a
 * material loop) and the art of every intermediate.</p>
 */
class OreChainTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");

    /** The fourteenth grade ids in stage order; grades 2 and 3 are prefixed ({@code pure_x}). */
    private static final String[] GRADE_IDS = {
            "dust", "pure", "exposed", "washed", "sorted", "concentrate", "refined",
            "separated", "high_purity", "micronized", "crystallized", "homogenized",
            "compressed", "stabilized"};

    /** Every ore of the line: key, the item that enters stage 1, the product of stage 15. */
    private record Ore(String key, String entry, String out, Map<Integer, String> existing) {}

    private static final List<Ore> ORES = List.of(
            new Ore("iron", "minecraft:raw_iron", "minecraft:iron_ingot", Map.of(
                    1, "industrialcore:iron_dust",
                    2, "industrialcore:pure_iron",
                    3, "industrialcore:exposed_iron")),
            new Ore("copper", "minecraft:raw_copper", "minecraft:copper_ingot", Map.of(
                    1, "industrialcore:copper_dust",
                    2, "industrialcore:pure_copper",
                    3, "industrialcore:exposed_copper")),
            new Ore("gold", "minecraft:raw_gold", "minecraft:gold_ingot", Map.of(
                    1, "industrialcore:gold_dust",
                    2, "industrialcore:pure_gold",
                    3, "industrialcore:exposed_gold")),
            new Ore("adamantite", "industrialcore:raw_adamantite",
                    "industrialcore:adamantite_ingot", Map.of()),
            new Ore("mithril", "industrialcore:raw_mithril",
                    "industrialcore:mithril_ingot", Map.of()),
            new Ore("orichalcum", "industrialcore:raw_orichalcum",
                    "industrialcore:orichalcum_ingot", Map.of()),
            new Ore("void_iron", "industrialcore:raw_void_iron",
                    "industrialcore:void_iron_ingot", Map.of()),
            new Ore("astral_silver", "industrialcore:raw_astral_silver",
                    "industrialcore:astral_silver_ingot", Map.of()),
            new Ore("aether", "industrialcore:aether_ore",
                    "industrialcore:aether_crystal", Map.of()),
            new Ore("nebula", "industrialcore:nebula_ore",
                    "industrialcore:nebula_crystal", Map.of()),
            new Ore("quantum", "industrialcore:quantum_ore",
                    "industrialcore:quantum_shard", Map.of()));

    /** The design table of one stage: machine type, duration, FE per tick, batch, catalyst. */
    private record Stage(int n, String type, int time, int energy, int batch, String catalyst) {}

    private static final List<Stage> STAGES = List.of(
            // Every stage runs 10 ticks (0.5 s): the uniform ore-line tempo set by the time
            // unification pass. FE per tick still differs per stage, so the total FE of a run
            // stays stage specific.
            new Stage(1, "industrialcore:crushing", 10, 10, 1, null),
            new Stage(2, "industrialcore:electrolysis", 10, 20, 1, null),
            new Stage(3, "industrialcore:precision_crushing", 10, 15, 1, null),
            new Stage(4, "industrialcore:pressing", 10, 8, 1, null),
            new Stage(5, "industrialcore:crushing", 10, 10, 1, null),
            new Stage(6, "industrialcore:electrolysis", 10, 20, 1, null),
            // Only the last stage is catalyst gated: a catalyst is consumed per operation and one
            // operation yields 64 items, so nine catalyst stages cost more metal than the chain
            // produced. Stages 7-14 pay in FE and time, stage 15 in one ultimate synthetic ore.
            new Stage(7, "industrialcore:synthesis", 10, 20, 16, null),
            new Stage(8, "industrialcore:synthesis", 10, 24, 32, null),
            new Stage(9, "industrialcore:synthesis", 10, 30, 32, null),
            new Stage(10, "industrialcore:synthesis", 10, 36, 32, null),
            new Stage(11, "industrialcore:synthesis", 10, 45, 32, null),
            new Stage(12, "industrialcore:synthesis", 10, 56, 32, null),
            new Stage(13, "industrialcore:synthesis", 10, 72, 32, null),
            new Stage(14, "industrialcore:synthesis", 10, 90, 32, null),
            new Stage(15, "industrialcore:synthesis", 10, 120, 32,
                    "industrialcore:synthetic_ore_ultimate"));

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    private static JsonObject read(String file) throws IOException {
        return JsonParser.parseString(Files.readString(RECIPES.resolve(file))).getAsJsonObject();
    }

    private static List<Path> recipeFiles() throws IOException {
        try (Stream<Path> stream = Files.list(RECIPES)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
        }
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
        if (result == null || !result.isJsonObject()
                || !result.getAsJsonObject().has("count")) {
            return 1;
        }
        return result.getAsJsonObject().get("count").getAsInt();
    }

    private static int timeOf(JsonObject recipe) {
        JsonElement time = recipe.get("time");
        return time == null ? 0 : time.getAsInt();
    }

    private static int energyOf(JsonObject recipe) {
        JsonElement energy = recipe.get("energy");
        return energy == null ? -1 : energy.getAsInt();
    }

    /** The first ingredient item, whether the recipe is single-input or a list. */
    private static String mainIngredient(JsonObject recipe) {
        JsonElement ingredient = recipe.get("ingredient");
        if (ingredient != null && ingredient.isJsonObject()) {
            return itemOf(ingredient.getAsJsonObject());
        }
        JsonElement list = recipe.get("ingredients");
        if (list != null && list.isJsonArray() && list.getAsJsonArray().size() > 0) {
            JsonElement first = list.getAsJsonArray().get(0);
            if (first.isJsonObject() && first.getAsJsonObject().has("ingredient")) {
                return itemOf(first.getAsJsonObject().getAsJsonObject("ingredient"));
            }
        }
        return "";
    }

    /** Every ingredient item of a list recipe: [{ingredient, count}, ...] -> ids. */
    private static List<String> ingredientList(JsonObject recipe) {
        List<String> ids = new ArrayList<>();
        JsonElement list = recipe.get("ingredients");
        if (list != null && list.isJsonArray()) {
            for (JsonElement element : list.getAsJsonArray()) {
                if (element.isJsonObject() && element.getAsJsonObject().has("ingredient")) {
                    ids.add(itemOf(element.getAsJsonObject().getAsJsonObject("ingredient")));
                }
            }
        }
        return ids;
    }

    private static String itemOf(JsonObject holder) {
        JsonElement item = holder.get("item");
        return item == null ? "" : item.getAsString();
    }

    private static String gradeId(Ore ore, int grade) {
        String existing = ore.existing().get(grade);
        if (existing != null) {
            return existing.substring("industrialcore:".length());
        }
        String id = GRADE_IDS[grade - 1];
        return grade == 2 || grade == 3 ? id + "_" + ore.key() : ore.key() + "_" + id;
    }

    private static String gradeItem(Ore ore, int grade) {
        return "industrialcore:" + gradeId(ore, grade);
    }

    /** Stage 1 of the three vanilla ores reuses the shipped crush_raw_* recipe. */
    private static String stageFile(Ore ore, int stage) {
        if (stage == 1 && ore.existing().containsKey(1)) {
            return "crush_raw_" + ore.key() + ".json";
        }
        return "oreline_s" + stage + "_" + ore.key() + ".json";
    }

    private static String stageInput(Ore ore, int stage) {
        return stage == 1 ? ore.entry() : gradeItem(ore, stage - 1);
    }

    private static String stageOutput(Ore ore, int stage) {
        return stage == 15 ? ore.out() : gradeItem(ore, stage);
    }

    // ------------------------------------------------------------------
    // 1. the fifteen stages of every ore
    // ------------------------------------------------------------------

    @Test
    void everyOreShipsFifteenConnectedStages() throws IOException {
        for (Ore ore : ORES) {
            for (Stage stage : STAGES) {
                String file = stageFile(ore, stage.n());
                String where = ore.key() + " stage " + stage.n();
                assertTrue(Files.exists(RECIPES.resolve(file)), where + " is missing: " + file);

                JsonObject recipe = read(file);
                assertEquals(stage.type(), typeOf(recipe), where + " must run in the right machine");
                assertEquals(stageInput(ore, stage.n()), mainIngredient(recipe),
                        where + " must consume the previous grade");
                assertEquals(stageOutput(ore, stage.n()), resultId(recipe),
                        where + " must produce the next grade");

                if (stage.n() <= 6) {
                    assertFalse(recipe.has("ingredients"),
                            where + " is a single-input machine recipe");
                    assertEquals(2, resultCount(recipe), where + " must double one input");
                } else {
                    List<String> inputs = ingredientList(recipe);
                    assertEquals(stage.catalyst() == null ? 1 : 2, inputs.size(),
                            where + (stage.catalyst() == null
                                    ? " is a batch recipe with no catalyst"
                                    : " needs its grade and its catalyst"));
                    assertEquals(stage.batch(), recipe.getAsJsonArray("ingredients")
                            .get(0).getAsJsonObject().get("count").getAsInt(),
                            where + " must batch " + stage.batch() + " items");
                    if (stage.catalyst() != null) {
                        assertEquals(stage.catalyst(), inputs.get(1),
                                where + " must consume " + stage.catalyst());
                    }
                    assertEquals(stage.batch() * 2, resultCount(recipe),
                            where + " must double the batch");
                }

                assertEquals(stage.time(), timeOf(recipe), where + " time drifted");
                assertEquals(stage.energy(), energyOf(recipe), where + " FE/t drifted");
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. the cumulative multiplier
    // ------------------------------------------------------------------

    /**
     * The number the whole redesign is about: fifteen stages of x2 multiply to x32,768, and the
     * last stage lands on the ore's ingot (or crystal). The multiplier is read back from the
     * shipped files, not from the design table, so a mistyped result count fails here.
     */
    @Test
    void theCumulativeMultiplierReaches32768() throws IOException {
        for (Ore ore : ORES) {
            long multiplier = 1;
            for (Stage stage : STAGES) {
                JsonObject recipe = read(stageFile(ore, stage.n()));
                int inputCount = stage.n() <= 6 ? 1 : stage.batch();
                int outputCount = resultCount(recipe);
                assertTrue(outputCount % inputCount == 0,
                        ore.key() + " stage " + stage.n() + " does not divide evenly");
                multiplier *= outputCount / inputCount;
            }
            assertEquals(32_768L, multiplier,
                    ore.key() + " must reach x32768 from one entry item");
            assertEquals(ore.out(), resultId(read(stageFile(ore, 15))),
                    ore.key() + " stage 15 must land on its final product");
        }
    }

    // ------------------------------------------------------------------
    // 3. banking: every grade smelts 1:1
    // ------------------------------------------------------------------

    @Test
    void everyGradeSmeltsOneToOneBackIntoItsIngot() throws IOException {
        for (Ore ore : ORES) {
            for (int grade = 1; grade <= 14; grade++) {
                String file = "smelt_" + gradeId(ore, grade) + ".json";
                String where = ore.key() + " grade " + grade + " (" + gradeId(ore, grade) + ")";
                assertTrue(Files.exists(RECIPES.resolve(file)), where + " has no smelting recipe");

                JsonObject recipe = read(file);
                assertEquals("minecraft:smelting", typeOf(recipe), where);
                assertEquals(gradeItem(ore, grade), mainIngredient(recipe), where);
                assertEquals(ore.out(), resultId(recipe), where + " must smelt into its product");
                assertEquals(1, resultCount(recipe), where + " must smelt 1:1");
                assertEquals(200, recipe.get("cookingtime").getAsInt(),
                        where + " must keep the vanilla cooking time");
            }
        }
    }

    // ------------------------------------------------------------------
    // 4. forward only: no grade may feed an earlier one
    // ------------------------------------------------------------------

    /**
     * The chain grades of one ore are numbered 0 (entry) to 15 (final product). A recipe that
     * consumes a higher number and produces a lower one would close a material loop, which is the
     * exploit class this redesign exists to remove. The seed line's own loop (dust - seed - billet
     * - ore) is audited by {@code OreCycleAuditTest} and stays outside this index because its
     * middle items are not chain grades.
     */
    @Test
    void noStageOutputFeedsAnEarlierGrade() throws IOException {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Ore ore : ORES) {
            index.put(ore.entry(), 0);
            for (int grade = 1; grade <= 14; grade++) {
                index.put(gradeItem(ore, grade), grade);
            }
            index.put(ore.out(), 15);
        }

        Set<String> documentedDrop = new TreeSet<>();
        for (String key : List.of("quantum", "aether", "nebula")) {
            documentedDrop.add(mainIngredient(read("oreline_drop_" + key + ".json")));
        }

        List<String> offenders = new ArrayList<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String input : inputIds(recipe)) {
                Integer in = index.get(input);
                if (in == null) {
                    continue;
                }
                for (String output : outputIds(recipe)) {
                    Integer out = index.get(output);
                    if (out == null || out > in) {
                        continue;
                    }
                    // Phase 32: a crystal ore has no ingot, so its stage-15 product is the item
                    // the ore drops and the item the drop entrance accepts. That one edge is
                    // documented (PHASE31_ORE_LINE.md), priced by the catalysts the last stage
                    // consumes, and audited as a ring by OreCycleAuditTest; every other
                    // backward edge still fails here.
                    if (in == 15 && out == 1 && documentedDrop.contains(input)) {
                        continue;
                    }
                    offenders.add(path.getFileName() + ": " + input + " (stage " + in
                            + ") -> " + output + " (stage " + out + ")");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "a recipe feeds an earlier grade of the ore line, which closes a loop: " + offenders);
    }

    /** Every item a recipe consumes, from all the shapes the mod's recipes use. */
    private static List<String> inputIds(JsonObject recipe) {
        List<String> ids = new ArrayList<>();
        for (String key : List.of("ingredient", "feed", "catalyst", "material", "crystal",
                "first", "second")) {
            JsonElement element = recipe.get(key);
            if (element != null && element.isJsonObject()) {
                String id = itemOf(element.getAsJsonObject());
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        ids.addAll(ingredientList(recipe));
        return ids;
    }

    private static List<String> outputIds(JsonObject recipe) {
        List<String> ids = new ArrayList<>();
        for (String key : List.of("result", "secondary")) {
            JsonElement element = recipe.get(key);
            if (element != null && element.isJsonObject()
                    && element.getAsJsonObject().has("id")) {
                ids.add(element.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // 5. art and translations of every intermediate
    // ------------------------------------------------------------------

    @Test
    void everyGradeItemHasArtAndTranslations() throws IOException {
        JsonObject english = lang("en_us");
        JsonObject japanese = lang("ja_jp");

        for (Ore ore : ORES) {
            for (int grade = 1; grade <= 14; grade++) {
                String id = gradeId(ore, grade);
                String nameKey = "item.industrialcore." + id;
                String tipKey = "gui.industrialcore.tooltip." + id;

                Path model = ASSETS.resolve("models/item/" + id + ".json");
                assertTrue(Files.exists(model), "missing item model for " + id);
                assertEquals("industrialcore:item/" + id,
                        readJson(model).getAsJsonObject("textures").get("layer0").getAsString(),
                        id + " must use its own texture");

                Path texture = ASSETS.resolve("textures/item/" + id + ".png");
                assertTrue(Files.exists(texture), "missing texture for " + id);
                byte[] pixels = Files.readAllBytes(texture);
                assertTrue(pixels.length > 100, "the texture of " + id + " is nearly empty");
                assertEquals((byte) 0x89, pixels[0], "the texture of " + id + " is not a PNG");
                assertEquals(16, ((pixels[16] & 0xFF) << 24) | ((pixels[17] & 0xFF) << 16)
                        | ((pixels[18] & 0xFF) << 8) | (pixels[19] & 0xFF),
                        id + " is not 16 px wide");
                assertEquals(16, ((pixels[20] & 0xFF) << 24) | ((pixels[21] & 0xFF) << 16)
                        | ((pixels[22] & 0xFF) << 8) | (pixels[23] & 0xFF),
                        id + " is not 16 px tall");

                assertTrue(english.has(nameKey), "en_us has no name for " + id);
                assertTrue(japanese.has(nameKey), "ja_jp has no name for " + id);
                assertTrue(english.has(tipKey), "en_us has no stage description for " + id);
                assertTrue(japanese.has(tipKey), "ja_jp has no stage description for " + id);
            }
        }

        // The stage machines keep their models: the line reuses them, it does not replace them.
        for (String machine : List.of("crusher", "electrolyzer", "precision_crusher",
                "metal_press", "ore_synthesizer")) {
            assertTrue(Files.exists(ASSETS.resolve("models/block/" + machine + ".json")),
                    "missing block model for " + machine);
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + machine + ".json")),
                    "missing blockstate for " + machine);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + machine + ".json")),
                    "missing block item model for " + machine);
        }
        // The superseded concentrate draft must still be gone.
        assertFalse(Files.exists(RECIPES.resolve("concentrate_iron.json")),
                "the replaced concentrate recipe is still present");
    }

    private static JsonObject lang(String locale) throws IOException {
        return readJson(ASSETS.resolve("lang/" + locale + ".json"));
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    /** A block loot table of this mod, next to the recipe directory. */
    private static JsonObject readLoot(String file) throws IOException {
        return readJson(RECIPES.getParent().resolve("loot_table/blocks").resolve(file));
    }

    // ------------------------------------------------------------------
    // 5b. the crystal ores accept their normal drop (Phase 32)
    // ------------------------------------------------------------------

    /**
     * Phase 32: quantum, aether and nebula used to enter the line only as a silk-touch ore block,
     * and no quarry can supply that - it mines with an unenchanted pickaxe, so the ore gives its
     * normal drop. Both entrances now exist and both turn one item into two dust, so the ladder
     * still starts at x2 whichever one a player uses, and a stockpile of silk-touch blocks keeps
     * working.
     */
    @Test
    void theCrystalOresAcceptTheirNormalDropAsWell() throws IOException {
        record Entrance(String key, String block, String drop) {}
        List<Entrance> entrances = List.of(
                new Entrance("quantum", "industrialcore:quantum_ore",
                        "industrialcore:quantum_shard"),
                new Entrance("aether", "industrialcore:aether_ore",
                        "industrialcore:aether_crystal"),
                new Entrance("nebula", "industrialcore:nebula_ore",
                        "industrialcore:nebula_crystal"));

        for (Entrance entrance : entrances) {
            JsonObject block = read("oreline_s1_" + entrance.key() + ".json");
            JsonObject drop = read("oreline_drop_" + entrance.key() + ".json");
            assertEquals(entrance.block(), mainIngredient(block),
                    entrance.key() + " keeps its block entrance");
            assertEquals(entrance.drop(), mainIngredient(drop),
                    entrance.key() + " accepts its normal drop");

            for (JsonObject recipe : List.of(block, drop)) {
                assertEquals("industrialcore:crushing", typeOf(recipe));
                assertEquals("industrialcore:" + entrance.key() + "_dust", resultId(recipe));
                assertEquals(2, resultCount(recipe),
                        "one entry item must become two dust, never more");
                assertEquals(10, timeOf(recipe), "the drop entrance costs the same time");
                assertEquals(10, energyOf(recipe), "the drop entrance costs the same FE");
            }

            // The drop the recipe accepts has to be the one the ore really gives without silk
            // touch, otherwise the entrance exists on paper only.
            JsonObject loot = readLoot(entrance.key() + "_ore.json");
            List<String> names = new ArrayList<>();
            for (JsonElement child : loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject()
                    .getAsJsonArray("children")) {
                names.add(child.getAsJsonObject().get("name").getAsString());
            }
            assertTrue(names.contains(entrance.block()),
                    entrance.key() + " must still drop its block with silk touch: " + names);
            assertTrue(names.contains(entrance.drop()),
                    entrance.key() + " must drop " + entrance.drop() + " normally: " + names);
        }
    }

    /**
     * Two recipes of one machine may not claim the same input. The machines look their recipe up
     * by the input stack, so a duplicate would silently shadow the other recipe - the crystal
     * ores' new drop entrances must therefore sit next to the block entrances without colliding.
     */
    @Test
    void noMachineRecipeClaimsTheSameInputTwice() throws IOException {
        Map<String, String> owners = new LinkedHashMap<>();
        for (Path path : recipeFiles()) {
            JsonObject recipe = read(path.getFileName().toString());
            String type = typeOf(recipe);
            if (!type.equals("industrialcore:crushing")
                    && !type.equals("industrialcore:electrolysis")
                    && !type.equals("industrialcore:precision_crushing")
                    && !type.equals("industrialcore:pressing")) {
                continue;
            }
            String input = mainIngredient(recipe);
            if (input.isEmpty()) {
                continue;
            }
            String previous = owners.put(type + "|" + input, path.getFileName().toString());
            assertNull(previous, path.getFileName() + " claims " + input + " of " + type
                    + ", which " + previous + " already claims");
        }
    }

    // ------------------------------------------------------------------
    // 6. the old amplifier line must be gone everywhere
    // ------------------------------------------------------------------

    @Test
    void theOldAmplifierLineIsFullyRemoved() throws IOException {
        List<String> leftovers = new ArrayList<>();
        for (Path path : recipeFiles()) {
            String name = path.getFileName().toString();
            if (name.startsWith("amplify_") || name.startsWith("ore_amplifier_")
                    || name.contains("amplifier_chassis") || name.contains("signal_harness")
                    || name.contains("drive_unit")) {
                leftovers.add("recipe " + name);
            }
        }
        for (String locale : List.of("en_us", "ja_jp")) {
            String table = Files.readString(ASSETS.resolve("lang/" + locale + ".json"));
            for (String dead : List.of("ore_amplifier", "amplifier_chassis", "signal_harness",
                    "drive_unit")) {
                if (table.contains(dead)) {
                    leftovers.add(locale + " lang still names " + dead);
                }
            }
        }
        for (String source : List.of("src/main/java/com/industrialcore/registry/ModItems.java",
                "src/main/java/com/industrialcore/registry/ModBlocks.java",
                "src/main/java/com/industrialcore/registry/ModRecipes.java")) {
            String text = Files.readString(Path.of(source));
            for (String dead : List.of("ORE_AMPLIFIER", "AMPLIFIER_CHASSIS", "SIGNAL_HARNESS",
                    "DRIVE_UNIT", "ore_amplification")) {
                if (text.contains(dead)) {
                    leftovers.add(source + " still registers " + dead);
                }
            }
        }
        try (Stream<Path> blockstates = Files.list(ASSETS.resolve("blockstates"))) {
            for (Path path : blockstates.filter(p -> p.getFileName().toString()
                    .startsWith("ore_amplifier")).toList()) {
                leftovers.add("blockstate " + path.getFileName());
            }
        }
        assertTrue(leftovers.isEmpty(),
                "the removed ore amplifier line still has traces: " + leftovers);
    }
}
