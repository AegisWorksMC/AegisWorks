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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 21 audit of the shipped item graph.
 *
 * <p>{@link OreChainTest} pins the numbers of the implemented tiers, but it only checks two
 * special cases ("crushing must not take dust", "an ingot must not become dust"). It therefore
 * cannot answer the questions Phase 21 raises: how many ingots a metal can really reach, whether
 * any loop exists, and what one turn of the intended dust loop costs.</p>
 *
 * <p>What this test guarantees today (recipes that are actually shipped):</p>
 * <ol>
 *   <li>every recipe type in {@code data/industrialcore/recipe} is declared in {@link #KNOWN_TYPES},
 *       so a new type cannot slip in without an audit decision;</li>
 *   <li>the fifteen stage ore line of Phase 31 tops out at exactly 32,768 ingots per raw ore (and
 *       16,384 / 8,192 / 4,096 from dust / pure / exposed): a hidden extra multiplier anywhere in
 *       the machine chain, or a loop through it, fails the test;</li>
 *   <li>the only items that lie on a material cycle are the four per metal of the Phase 21 seed
 *       line (dust, seed, billet and the ore block the amplifier pays out), and - since Phase 32
 *       gave the three crystal ores their normal drop as an entrance - exactly the crystal
 *       chains and the synthesis ladder those chains pay with. Any other loop - a backward edge
 *       of a metal line, a cross-metal loop, a dust-to-dust recipe - fails the test;</li>
 *   <li>every stage of the ore line charges FE and time, so the doubling is never free.</li>
 * </ol>
 *
 * <p>What it <em>cannot</em> see: crafting recipes ({@code crafting_shaped} / {@code
 * crafting_shapeless}) carry their inputs in {@code pattern}/{@code key}, which this reader does
 * not resolve. The seed craft therefore is not part of the parsed graph; its dust cost is carried
 * as an explicit constant in {@link #SEED_CRAFT_DUST} and reported separately.</p>
 *
 * <p>{@link #phase21RecipesAreEitherAbsentOrComplete} keeps the seed line all-or-nothing: the
 * machines and the intermediates must ship together, and the shipped numbers must equal the
 * specification. The loop arithmetic is also exercised on synthetic recipes by
 * {@link #cycleAuditMeasuresTheDocumentedLoop} and {@link #cycleAuditFlagsAnUnboundedLoop}, so a
 * broken checker cannot hide behind the real data.</p>
 */
class OreCycleAuditTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final List<String> METALS = List.of("iron", "copper", "gold");

    private static final String CULTIVATION = "industrialcore:cultivation";
    private static final String AMPLIFICATION = "industrialcore:amplification";

    /** Dust one seed costs to craft (metal dust 4 + glass 2 + amethyst shard 2 in the spec). */
    private static final int SEED_CRAFT_DUST = 4;

    /** Types the reader understands. Adding a type without adding it here fails the audit. */
    private static final Set<String> KNOWN_TYPES = Set.of(
            "industrialcore:crushing",
            "industrialcore:electrolysis",
            "industrialcore:precision_crushing",
            "industrialcore:pressing",
            "industrialcore:alloying",
            "industrialcore:assembling",
            "industrialcore:reconstruction",
            "industrialcore:cultivation",
            "industrialcore:amplification",
            // Phase 24: three ingredients -> one result (phase reactor). No ore, raw ore or dust
            // is produced, so it cannot feed the Phase 21 loop.
            "industrialcore:phase_reaction",
            // Phase 30/31: the synthetic ore ladder (each tier eats two of the tier below) and
            // the fifteen stage ore line (every stage doubles the grade before it). Neither can
            // loop back on itself; every stage charges FE and time, checked by
            // {@link #everyOreLineStageChargesTimeAndEnergy}.
            "industrialcore:synthesis",
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:crafting_shaped",
            "minecraft:crafting_shapeless");

    /** The machine types of the fifteen stage ore line: the only ones allowed to change counts. */
    private static final Set<String> TIER_TYPES = Set.of(
            "industrialcore:crushing",
            "industrialcore:electrolysis",
            "industrialcore:precision_crushing",
            "industrialcore:pressing",
            "industrialcore:synthesis",
            "minecraft:smelting");

    /** Deep enough for the fifteen stages plus the smelting exit of any grade. */
    private static final int MAX_DEPTH = 20;

    /**
     * One recipe file, reduced to the parts the audit needs.
     *
     * @param inputs  item id to amount consumed per operation
     * @param outputs item id to amount produced per operation
     * @param scalars named integers (including the output count) used by the Phase 21 formulas
     */
    private record Recipe(String file, String type, Map<String, Integer> inputs,
                          Map<String, Integer> outputs, Map<String, Integer> scalars,
                          int time, int energy) {

        boolean is(String recipeType) {
            return type.equals(recipeType);
        }

        int scalar(String name, int fallback) {
            return scalars.getOrDefault(name, fallback);
        }

        /** FE one operation costs; the cultivator uses {@code energy × time} for a single billet. */
        long operationEnergy() {
            return (long) Math.max(0, time) * Math.max(0, energy);
        }
    }

    // ------------------------------------------------------------------
    // Recipe reading
    // ------------------------------------------------------------------

    private static List<Recipe> load() throws IOException {
        List<Recipe> recipes = new ArrayList<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                recipes.add(parse(path.getFileName().toString(),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject()));
            }
        }
        return recipes;
    }

    private static Recipe parse(String file, JsonObject json) {
        Map<String, Integer> inputs = new LinkedHashMap<>();
        Map<String, Integer> outputs = new LinkedHashMap<>();
        Map<String, Integer> scalars = new LinkedHashMap<>();

        addItem(inputs, json, "ingredient", 1);
        addItem(inputs, json, "first", 1);
        addItem(inputs, json, "second", 1);
        addItem(inputs, json, "material", integer(json, "material_count", 1));
        addItem(inputs, json, "crystal", integer(json, "crystal_count", 1));
        // Phase 21 recipe shapes (not shipped yet). Declared here so the reader needs no change
        // when the feature lands.
        addItem(inputs, json, "feed", 1);
        addItem(inputs, json, "catalyst", 1);
        if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("ingredients");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String id;
                int amount = 1;
                if (entry.has("ingredient") && entry.get("ingredient").isJsonObject()) {
                    // The list shape of the assembler, phase reactor and synthesis recipes:
                    // {ingredient: {item: ...}, count: N}. The count matters: a stage that eats
                    // sixteen items and pays thirty-two must read as a per-unit doubling.
                    id = str(entry.getAsJsonObject("ingredient"), "item");
                    if (entry.has("count")) {
                        amount = entry.get("count").getAsInt();
                    }
                } else {
                    id = str(entry, "item");
                }
                if (!id.isEmpty()) {
                    inputs.merge(id, Math.max(1, amount), Integer::sum);
                }
            }
        }

        addOutput(outputs, scalars, json, "result");
        addOutput(outputs, scalars, json, "secondary");

        if (json.has("feedPerUnit")) {
            scalars.put("feedPerUnit", json.get("feedPerUnit").getAsInt());
        }
        // Only recorded when the file actually names them: the Phase 21 catalyst is a plain
        // ingredient, so a missing count means one, not "minus one".
        if (json.has("material_count")) {
            scalars.put("material_count", json.get("material_count").getAsInt());
        }
        if (json.has("crystal_count")) {
            scalars.put("crystal_count", json.get("crystal_count").getAsInt());
        }

        int time = integer(json, "time", integer(json, "cookingtime", 0));
        int energy = integer(json, "energy", 0);
        return new Recipe(file, str(json, "type"), inputs, outputs, scalars, time, energy);
    }

    private static void addItem(Map<String, Integer> inputs, JsonObject json, String key, int amount) {
        if (!json.has(key)) {
            return;
        }
        JsonElement element = json.get(key);
        if (!element.isJsonObject()) {
            return;
        }
        String id = str(element.getAsJsonObject(), "item");
        if (!id.isEmpty()) {
            inputs.merge(id, Math.max(1, amount), Integer::sum);
        }
    }

    private static void addOutput(Map<String, Integer> outputs, Map<String, Integer> scalars,
                                  JsonObject json, String key) {
        if (!json.has(key)) {
            return;
        }
        JsonElement element = json.get(key);
        if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        int count = integer(object, "count", 1);
        outputs.merge(object.get("id").getAsString(), count, Integer::sum);
        if (key.equals("result")) {
            scalars.put("count", count);
        }
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    // ------------------------------------------------------------------
    // 1. Type coverage
    // ------------------------------------------------------------------

    @Test
    void everyShippedRecipeTypeIsAudited() throws IOException {
        Set<String> unknown = new TreeSet<>();
        for (Recipe recipe : load()) {
            if (!KNOWN_TYPES.contains(recipe.type())) {
                unknown.add(recipe.type() + " (" + recipe.file() + ")");
            }
        }
        assertTrue(unknown.isEmpty(), () -> "undeclared recipe types " + unknown
                + ": declare them in KNOWN_TYPES and extend this audit before shipping them");
    }

    // ------------------------------------------------------------------
    // 2. The ore line multiplies exactly as documented, and never loops
    // ------------------------------------------------------------------

    /**
     * The cumulative multiplier of Phase 31, read through the graph: one raw ore walks fifteen
     * stages of x2 and reaches exactly 32,768 ingots, one dust reaches 16,384 (stages 2-15), one
     * pure ore 8,192 and one exposed ore 4,096. A sixteenth doubling, a shortcut or a loop fails
     * here; {@code OreChainTest} pins the same numbers file by file.
     */
    @Test
    void theChainReachesExactlyTheDocumentedMultiplier() throws IOException {
        List<Recipe> recipes = load();
        for (String metal : METALS) {
            assertEquals(32_768, maxIngots(recipes, "minecraft:raw_" + metal),
                    "the raw " + metal + " entry must reach exactly 32,768 ingots");
            assertEquals(16_384, maxIngots(recipes, "industrialcore:" + metal + "_dust"),
                    "one " + metal + " dust must reach exactly 16,384 ingots (stages 2-15)");
            assertEquals(8_192, maxIngots(recipes, "industrialcore:pure_" + metal),
                    "one pure " + metal + " must reach exactly 8,192 ingots (stages 3-15)");
            assertEquals(4_096, maxIngots(recipes, "industrialcore:exposed_" + metal),
                    "one exposed " + metal + " must reach exactly 4,096 ingots (stages 4-15)");
        }
    }

    /** Best ingot yield reachable from {@code item} through the ore line machine types. */
    private static int maxIngots(List<Recipe> recipes, String item) {
        return maxIngots(recipes, item, new LinkedHashSet<>(), 0);
    }

    private static int maxIngots(List<Recipe> recipes, String item, Set<String> path, int depth) {
        if (item.endsWith("_ingot")) {
            return 1;
        }
        if (!path.add(item)) {
            fail("the implemented tier chain loops through " + item + " (" + path
                    + "); 2x/3x/6x must stay acyclic");
        }
        if (depth > MAX_DEPTH) {
            fail("the implemented tier chain is deeper than " + MAX_DEPTH + " steps at " + item);
        }
        int best = 0;
        for (Recipe recipe : recipes) {
            if (!TIER_TYPES.contains(recipe.type())) {
                continue;
            }
            Integer consumed = recipe.inputs().get(item);
            if (consumed == null || consumed <= 0) {
                continue;
            }
            for (Map.Entry<String, Integer> produced : recipe.outputs().entrySet()) {
                int perInput = produced.getValue() / consumed;
                if (perInput <= 0) {
                    continue;
                }
                best = Math.max(best, perInput * maxIngots(recipes, produced.getKey(), path, depth + 1));
            }
        }
        path.remove(item);
        return best;
    }

    // ------------------------------------------------------------------
    // 3. The only cycle is the intended Phase 21 dust loop
    // ------------------------------------------------------------------

    /**
     * Phase 21 ships one intended loop, and Phase 31 kept it: the seed line. Dust is crafted into
     * a seed, the cultivator grows the seed into a billet (eating dust as feed), and the matter
     * amplifier turns the billet back into ore blocks and dust. The ore block closes the ring
     * through the crusher.
     *
     * <p>Phase 32 adds a second, equally intended one. Quantum, aether and nebula are crystals,
     * not metals: they have no ingot, so the product of stage 15 <em>is</em> the item the ore
     * drops. Now that the drop is a valid stage-1 entrance, one crystal walks the fifteen stages
     * into 32,768 crystals, and feeding one of them back is a real edge. It is not free - it
     * needs 512 ultimate catalysts, which the synthesis ladder itself builds out of the same
     * dust - so the ring costs exactly what mining a fresh ore block costs. The items of that
     * ring are enumerated below, grade by grade and catalyst by catalyst, so a loop through any
     * <em>other</em> item still fails here.</p>
     */
    @Test
    void onlyTheDocumentedLoopExists() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (String metal : METALS) {
            expected.add("industrialcore:" + metal + "_dust");
            expected.add("industrialcore:" + metal + "_seed");
            expected.add("industrialcore:" + metal + "_billet");
            expected.add("minecraft:" + metal + "_ore");
        }

        // Phase 32: the crystal ores enter with the item stage 15 produces. Their fourteen grades
        // and the drop are the only items of the line that may lie on a cycle...
        String[] grades = {"dust", "pure", "exposed", "washed", "sorted", "concentrate",
                "refined", "separated", "high_purity", "micronized", "crystallized",
                "homogenized", "compressed", "stabilized"};
        Map<String, String> crystals = new LinkedHashMap<>();
        crystals.put("quantum", "industrialcore:quantum_shard");
        crystals.put("aether", "industrialcore:aether_crystal");
        crystals.put("nebula", "industrialcore:nebula_crystal");
        for (Map.Entry<String, String> crystal : crystals.entrySet()) {
            String ore = crystal.getKey();
            expected.add(crystal.getValue());
            for (String grade : grades) {
                expected.add(grade.equals("pure") || grade.equals("exposed")
                        ? "industrialcore:" + grade + "_" + ore
                        : "industrialcore:" + ore + "_" + grade);
            }
        }
        // ...together with the synthesis ladder, because the catalyst the last stage consumes is
        // built from those very dusts and therefore sits on the same ring. The first two tiers
        // are not on it: they are made from adamantite and mithril, whose own lines start at a
        // raw ore that nothing produces, so a ring cannot pass through them.
        for (String synthetic : List.of("3", "4", "5", "6")) {
            expected.add("industrialcore:synthetic_ore_" + synthetic);
        }
        expected.add("industrialcore:synthetic_ore_high_density");
        expected.add("industrialcore:synthetic_ore_unstable_ultimate");
        expected.add("industrialcore:synthetic_ore_ultimate");
        assertEquals(expected, itemsOnCycle(load()),
                "the shipped recipes contain a material loop other than the two documented "
                        + "ones: dust -> seed -> billet -> ore -> dust per metal "
                        + "(PHASE21_SEED_AMPLIFIER.md) and, for the three crystal ores only, "
                        + "crystal -> dust -> ... -> crystal through the synthesis ladder "
                        + "(PHASE31_ORE_LINE.md); no metal line may feed an earlier grade");
    }

    /**
     * Phase 31 replaced the amplifier's self edge with a fifteen stage ladder: each stage turns
     * one grade into two of the next. The ladder never feeds back (the test above), and it is
     * never free: every shipped stage charges both FE and time.
     */
    @Test
    void everyOreLineStageChargesTimeAndEnergy() throws IOException {
        int stages = 0;
        for (Recipe recipe : load()) {
            if (!recipe.file().startsWith("oreline_s") && !recipe.file().startsWith("crush_raw_")) {
                continue;
            }
            stages++;
            assertTrue(recipe.time() > 0 && recipe.energy() > 0,
                    "an ore line stage must charge FE and time: " + recipe.file());
        }
        assertEquals(165, stages,
                "the ore line must ship fifteen stages for eleven ores (162 files plus the "
                        + "three reused crush_raw_* entries)");
    }

    /** Every item that can reach itself through at least one recipe edge. */
    private static Set<String> itemsOnCycle(List<Recipe> recipes) {
        Map<String, Set<String>> edges = new TreeMap<>();
        for (Recipe recipe : recipes) {
            for (String produced : recipe.outputs().keySet()) {
                for (String consumed : recipe.inputs().keySet()) {
                    edges.computeIfAbsent(consumed, key -> new TreeSet<>()).add(produced);
                }
            }
        }
        Set<String> cyclic = new TreeSet<>();
        for (String node : edges.keySet()) {
            if (reaches(edges, node, node)) {
                cyclic.add(node);
            }
        }
        return cyclic;
    }

    private static boolean reaches(Map<String, Set<String>> edges, String start, String target) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> stack = new ArrayList<>(edges.getOrDefault(start, Set.of()));
        while (!stack.isEmpty()) {
            String next = stack.remove(stack.size() - 1);
            if (next.equals(target)) {
                return true;
            }
            if (seen.add(next)) {
                stack.addAll(edges.getOrDefault(next, Set.of()));
            }
        }
        return false;
    }

    private static List<String> findCycle(List<Recipe> recipes) {
        Map<String, List<String>> edges = new TreeMap<>();
        for (Recipe recipe : recipes) {
            for (String produced : recipe.outputs().keySet()) {
                for (String consumed : recipe.inputs().keySet()) {
                    edges.computeIfAbsent(consumed, key -> new ArrayList<>()).add(produced);
                }
            }
        }
        Set<String> finished = new LinkedHashSet<>();
        for (String start : edges.keySet()) {
            List<String> found = walk(start, edges, new ArrayList<>(), finished);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<String> walk(String node, Map<String, List<String>> edges,
                                     List<String> path, Set<String> finished) {
        if (path.contains(node)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
            cycle.add(node);
            return cycle;
        }
        if (finished.contains(node)) {
            return null;
        }
        path.add(node);
        for (String next : edges.getOrDefault(node, List.of())) {
            List<String> found = walk(next, edges, path, finished);
            if (found != null) {
                return found;
            }
        }
        path.remove(path.size() - 1);
        finished.add(node);
        return null;
    }

    // ------------------------------------------------------------------
    // 4. Phase 21: absent today, fully audited once it exists
    // ------------------------------------------------------------------

    @Test
    void phase21RecipesAreEitherAbsentOrComplete() throws IOException {
        List<Recipe> recipes = load();
        boolean hasCultivation = recipes.stream().anyMatch(recipe -> recipe.is(CULTIVATION));
        boolean hasAmplification = recipes.stream().anyMatch(recipe -> recipe.is(AMPLIFICATION));
        Set<String> phase21Items = new TreeSet<>();
        for (Recipe recipe : recipes) {
            for (String id : recipe.outputs().keySet()) {
                if (id.endsWith("_seed") || id.endsWith("_billet")) {
                    phase21Items.add(id);
                }
            }
        }

        if (!hasCultivation && !hasAmplification) {
            assertTrue(phase21Items.isEmpty(), () -> "seed/billet items exist without the Phase 21 "
                    + "machines (" + phase21Items + "): the seed line must ship as a whole");
            return;
        }

        assertTrue(hasCultivation && hasAmplification,
                "cultivation and amplification must ship together (Phase 21 §8.4)");

        Recipe amplification = first(recipes, AMPLIFICATION);
        assertNotNull(amplification);
        for (String produced : amplification.outputs().keySet()) {
            assertTrue(!produced.startsWith("minecraft:raw_"),
                    "the amplifier must output ore blocks, not raw ore: " + amplification.file());
        }
        // Phase 31 removed the billet -> exposed -> pure -> dust recycling shortcuts: they would
        // close a loop through the forward ore line. What remains - seed craft, cultivator and
        // amplifier - is pinned by SeedLineTest and by onlyTheDocumentedLoopExists above.
    }

    // ------------------------------------------------------------------
    // 5. The loop arithmetic itself (synthetic recipes, runnable today)
    // ------------------------------------------------------------------

    @Test
    void cycleAuditMeasuresTheDocumentedLoop() {
        CycleBalance balance = auditLoop(phase21SpecRecipes());
        assertEquals(12, balance.dustOut());
        assertEquals(4, balance.feedDust());
        assertEquals(8, balance.netDustThroughMachines());
        assertEquals(4, balance.netDustIncludingSeed());
        assertEquals(1, balance.crystals());
        assertEquals(47_600L, balance.fe());
    }

    @Test
    void cycleAuditFlagsAnUnboundedLoop() {
        // A crushing recipe that turns 1 dust into 2 dust with no energy is the shape of an
        // infinite multiplier. The audit must reject it instead of reporting a healthy balance.
        List<Recipe> recipes = new ArrayList<>(phase21SpecRecipes());
        recipes.add(new Recipe("unbounded.json", "industrialcore:crushing",
                Map.of("industrialcore:iron_dust", 1),
                Map.of("industrialcore:iron_dust", 2), Map.of("count", 2), 1, 0));
        try {
            auditLoop(recipes);
            fail("the audit accepted a loop that duplicates dust");
        } catch (AssertionError expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains("unbounded"),
                    "unexpected message: " + expected.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Phase 21 loop accounting
    // ------------------------------------------------------------------

    /**
     * Result of one turn of the intended Phase 21 loop, measured in metal dust.
     *
     * <p>Turn: 1 seed + feed dust -> 1 billet -> 2 exposed -> 4 pure -> 12 dust. The seed is
     * crafted (not machine-processed), so its dust cost is reported separately instead of being
     * invented.</p>
     */
    private record CycleBalance(int dustOut, int feedDust, int crystals, long fe) {

        int netDustThroughMachines() {
            return dustOut - feedDust;
        }

        int netDustIncludingSeed() {
            return dustOut - feedDust - SEED_CRAFT_DUST;
        }
    }

    private static CycleBalance auditLoop(List<Recipe> recipes) {
        // A recipe that consumes dust and returns dust would make the loop unbounded no matter
        // what the FE price is: report it as such instead of averaging it into the balance.
        for (Recipe recipe : recipes) {
            for (String consumed : recipe.inputs().keySet()) {
                if (!consumed.endsWith("_dust")) {
                    continue;
                }
                for (String produced : recipe.outputs().keySet()) {
                    if (produced.equals(consumed)) {
                        fail("unbounded loop: " + recipe.file() + " turns " + consumed
                                + " back into itself");
                    }
                }
            }
        }
        if (findCycle(recipes) == null) {
            fail("the recipes under audit do not form the documented loop; "
                    + "expected dust -> seed -> billet -> exposed -> pure -> dust");
        }
        Recipe cultivation = only(recipes, CULTIVATION);
        Recipe billet = only(recipes, "industrialcore:precision_crushing",
                id -> id.endsWith("_billet"));
        Recipe exposed = only(recipes, "industrialcore:electrolysis",
                id -> id.contains(":exposed_"));
        Recipe pure = only(recipes, "industrialcore:crushing", id -> id.contains(":pure_"));
        Recipe amplification = only(recipes, AMPLIFICATION);

        int billets = outputCount(cultivation, id -> id.endsWith("_billet"));
        int exposedPerBillet = outputCount(billet, id -> id.contains(":exposed_"));
        int purePerExposed = outputCount(exposed, id -> id.contains(":pure_"));
        int dustPerPure = outputCount(pure, id -> id.endsWith("_dust"));
        int feed = cultivation.scalar("feedPerUnit", 4) * billets;

        int exposedTotal = billets * exposedPerBillet;
        int pureTotal = exposedTotal * purePerExposed;
        int dustOut = pureTotal * dustPerPure;

        long fe = (long) cultivation.operationEnergy() * billets
                + (long) billet.operationEnergy() * billets
                + (long) exposed.operationEnergy() * exposedTotal
                + (long) pure.operationEnergy() * pureTotal;
        int crystals = amplification.scalar("crystal_count", 1);
        return new CycleBalance(dustOut, feed, crystals, fe);
    }

    private static Recipe only(List<Recipe> recipes, String type) {
        Recipe found = first(recipes, type);
        assertNotNull(found, () -> "missing recipe of type " + type);
        return found;
    }

    private static Recipe only(List<Recipe> recipes, String type, Predicate<String> matchesInput) {
        for (Recipe recipe : recipes) {
            if (!recipe.is(type)) {
                continue;
            }
            for (String consumed : recipe.inputs().keySet()) {
                if (matchesInput.test(consumed)) {
                    return recipe;
                }
            }
        }
        fail("missing " + type + " recipe that consumes the expected input");
        return null;
    }

    private static Recipe first(List<Recipe> recipes, String type) {
        for (Recipe recipe : recipes) {
            if (recipe.is(type)) {
                return recipe;
            }
        }
        return null;
    }

    private static int outputCount(Recipe recipe, Predicate<String> matchesOutput) {
        for (Map.Entry<String, Integer> produced : recipe.outputs().entrySet()) {
            if (matchesOutput.test(produced.getKey())) {
                return produced.getValue();
            }
        }
        fail("recipe " + recipe.file() + " has no output matching the expected item");
        return 0;
    }

    /** The Phase 21 spec numbers, expressed as recipes so the audit can be tested today. */
    private static List<Recipe> phase21SpecRecipes() {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new Recipe("cultivation_iron.json", CULTIVATION,
                Map.of("industrialcore:iron_seed", 1, "industrialcore:iron_dust", 4),
                Map.of("industrialcore:iron_billet", 1),
                Map.of("feedPerUnit", 4, "count", 1), 600, 40));
        recipes.add(new Recipe("precision_billet_iron.json", "industrialcore:precision_crushing",
                Map.of("industrialcore:iron_billet", 1),
                Map.of("industrialcore:exposed_iron", 2), Map.of("count", 2), 300, 20));
        recipes.add(new Recipe("electrolysis_exposed_iron.json", "industrialcore:electrolysis",
                Map.of("industrialcore:exposed_iron", 1),
                Map.of("industrialcore:pure_iron", 2), Map.of("count", 2), 300, 20));
        recipes.add(new Recipe("crush_pure_iron.json", "industrialcore:crushing",
                Map.of("industrialcore:pure_iron", 1),
                Map.of("industrialcore:iron_dust", 3), Map.of("count", 3), 140, 10));
        recipes.add(new Recipe("amplification_iron.json", AMPLIFICATION,
                Map.of("industrialcore:iron_billet", 1,
                        "industrialcore:reconstruction_crystal", 1),
                Map.of("minecraft:iron_ore", 2, "industrialcore:iron_dust", 4),
                Map.of("crystal_count", 1, "count", 2), 1200, 60));
        return recipes;
    }
}
