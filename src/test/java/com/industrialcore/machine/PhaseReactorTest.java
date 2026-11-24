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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 24 unit tests: the phase reactor's recipe shape, its balance and its matching rules.
 *
 * <p>The machine itself is exercised by {@code PhaseReactorGameTests}; this test pins the data and
 * the pure logic so a drifted JSON or a broken permutation search fails the build in seconds.</p>
 */
class PhaseReactorTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/industrialcore/recipe");
    private static final String TYPE = "industrialcore:phase_reaction";

    /** The four shipped runs: file name -> (ingredients, output, time, FE/t). */
    private static final Map<String, Run> RUNS = new TreeMap<>(Map.of(
            "phase_catalyst", new Run(
                    List.of("industrialcore:nebula_crystal", "industrialcore:nebula_crystal",
                            "industrialcore:orichalcum_ingot"),
                    "industrialcore:resonant_catalyst",
                    PhaseReactorBalance.CATALYST_TIME_TICKS,
                    PhaseReactorBalance.CATALYST_ENERGY_PER_TICK),
            "phase_frame", new Run(
                    List.of("industrialcore:adamantite_ingot", "industrialcore:adamantite_ingot",
                            "industrialcore:astral_silver_ingot"),
                    "industrialcore:durable_machine_frame",
                    PhaseReactorBalance.FRAME_TIME_TICKS,
                    PhaseReactorBalance.FRAME_ENERGY_PER_TICK),
            "phase_motor", new Run(
                    List.of("industrialcore:mithril_ingot", "industrialcore:mithril_ingot",
                            "industrialcore:orichalcum_ingot"),
                    "industrialcore:high_power_motor",
                    PhaseReactorBalance.MOTOR_TIME_TICKS,
                    PhaseReactorBalance.MOTOR_ENERGY_PER_TICK),
            "phase_core", new Run(
                    List.of("industrialcore:void_iron_ingot", "industrialcore:quantum_shard",
                            "industrialcore:aether_crystal"),
                    "industrialcore:phase_core",
                    PhaseReactorBalance.CORE_TIME_TICKS,
                    PhaseReactorBalance.CORE_ENERGY_PER_TICK)));

    private record Run(List<String> ingredients, String result, int time, int energy) {}

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<JsonObject> recipes() throws IOException {
        List<JsonObject> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                found.add(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // 1. The recipe data
    // ------------------------------------------------------------------

    @Test
    void theFourShippedRunsHaveTheDocumentedShape() throws IOException {
        for (Map.Entry<String, Run> entry : RUNS.entrySet()) {
            JsonObject recipe = json(RECIPES.resolve(entry.getKey() + ".json"));
            Run expected = entry.getValue();
            assertEquals(TYPE, recipe.get("type").getAsString(),
                    entry.getKey() + " is not a phase reaction");
            JsonArray ingredients = recipe.getAsJsonArray("ingredients");
            List<String> ids = new ArrayList<>();
            for (JsonElement element : ingredients) {
                ids.add(element.getAsJsonObject().get("item").getAsString());
            }
            assertEquals(3, ids.size(),
                    entry.getKey() + " must have exactly three ingredients (three slots)");
            assertEquals(new TreeMap<>(countByItem(expected.ingredients())),
                    new TreeMap<>(countByItem(ids)),
                    entry.getKey() + " ingredient multiset drifted");
            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals(expected.result(), result.get("id").getAsString());
            assertEquals(1, result.has("count") ? result.get("count").getAsInt() : 1,
                    entry.getKey() + " must produce exactly one item");
            assertEquals(expected.time(), recipe.get("time").getAsInt(),
                    entry.getKey() + " time drifted");
            assertEquals(expected.energy(), recipe.get("energy").getAsInt(),
                    entry.getKey() + " FE per tick drifted");
        }
    }

    private static Map<String, Integer> countByItem(List<String> ids) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String id : ids) {
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void theEnergyTableAddsUp() {
        assertEquals(4_000L, PhaseReactorBalance.catalystCost());
        assertEquals(7_500L, PhaseReactorBalance.frameCost());
        assertEquals(7_500L, PhaseReactorBalance.motorCost());
        assertEquals(16_000L, PhaseReactorBalance.coreCost());
        // Every run must stay inside the machine's own buffer, or it could never start.
        for (long cost : List.of(PhaseReactorBalance.catalystCost(),
                PhaseReactorBalance.frameCost(), PhaseReactorBalance.motorCost(),
                PhaseReactorBalance.coreCost())) {
            assertTrue(cost <= PhaseReactorBalance.CAPACITY,
                    "a single run (" + cost + " FE) exceeds the buffer");
        }
        assertEquals(3, PhaseReactorBalance.INPUT_SLOTS);
    }

    /** A reactor recipe may never consume a reactor product: that is how a self-feeding loop starts. */
    @Test
    void theReactorNeverFeedsOnItsOwnProducts() throws IOException {
        List<String> products = List.of("industrialcore:resonant_catalyst",
                "industrialcore:durable_machine_frame", "industrialcore:high_power_motor",
                "industrialcore:phase_core");
        int seen = 0;
        for (JsonObject recipe : recipes()) {
            if (!recipe.get("type").getAsString().equals(TYPE)) {
                continue;
            }
            seen++;
            for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
                String id = element.getAsJsonObject().get("item").getAsString();
                assertFalse(products.contains(id),
                        "a phase reaction consumes " + id + ": " + recipe);
            }
        }
        assertEquals(4, seen, "the four documented runs must be the only phase reactions");
    }

    /** The machine has to be buildable without anything the machine makes. */
    @Test
    void theReactorIsBuildableWithoutItsOwnProducts() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("phase_reactor.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonObject keys = recipe.getAsJsonObject("key");
        List<String> used = new ArrayList<>();
        for (String symbol : keys.keySet()) {
            used.add(keys.getAsJsonObject(symbol).get("item").getAsString());
        }
        for (String id : used) {
            assertFalse(id.equals("industrialcore:resonant_catalyst")
                            || id.equals("industrialcore:durable_machine_frame")
                            || id.equals("industrialcore:high_power_motor")
                            || id.equals("industrialcore:phase_core"),
                    "the phase reactor needs its own product " + id + " to exist");
        }
        // The three crystals are the second use of each crystal (the first one is a reaction).
        for (String crystal : List.of("industrialcore:nebula_crystal",
                "industrialcore:quantum_shard", "industrialcore:aether_crystal")) {
            assertTrue(used.contains(crystal),
                    crystal + " has no second use outside the reactor recipes");
        }
    }

    // ------------------------------------------------------------------
    // 2. The matching search (pure logic, no Minecraft classes)
    // ------------------------------------------------------------------

    @Test
    void matchingIsOrderIndependent() {
        // ingredient 0 accepts slot 0 only, 1 -> 1, 2 -> 2; every permutation must match.
        for (int[] permutation : new int[][] {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0},
                {2, 0, 1}, {2, 1, 0}}) {
            int[][] table = new int[3][3];
            for (int ingredient = 0; ingredient < 3; ingredient++) {
                table[ingredient][permutation[ingredient]] = 1;
            }
            assertTrue(PhaseReactionRules.match(3, 3, (i, stack) -> table[i][stack] == 1),
                    "permutation " + permutation[0] + permutation[1] + permutation[2]);
        }
    }

    @Test
    void aRepeatedIngredientNeedsTwoDistinctSlots() {
        // Ingredients: A, A, B. Two slots accept A, one accepts B: the run can start.
        boolean[][] enough = {
                {false, true, true},   // ingredient 0 (A)
                {false, true, true},   // ingredient 1 (A)
                {true, false, false},  // ingredient 2 (B)
        };
        assertTrue(PhaseReactionRules.match(3, 3, (i, s) -> enough[i][s]),
                "two A ingredients fit when two slots accept A");

        // Only one slot accepts A: the second A has nowhere to go.
        boolean[][] tooFew = {
                {false, true, false},
                {false, true, false},
                {true, false, true},
        };
        assertFalse(PhaseReactionRules.match(3, 3, (i, s) -> tooFew[i][s]),
                "two A ingredients cannot both use the same A slot");
    }

    @Test
    void aWrongCountIsRejectedWithoutSearching() {
        assertFalse(PhaseReactionRules.match(0, 0, (i, s) -> true));
        assertFalse(PhaseReactionRules.match(3, 2, (i, s) -> true));
        assertFalse(PhaseReactionRules.match(2, 3, (i, s) -> true));
        assertTrue(PhaseReactionRules.match(3, 3, (i, s) -> true));
    }

    /** A greedy matcher would fail here; the search has to backtrack. */
    @Test
    void theSearchBacktracks() {
        // ingredient 0 can use slot 0 or 1, ingredient 1 only slot 0, ingredient 2 only slot 2.
        boolean[][] table = {
                {true, true, false},
                {true, false, false},
                {false, false, true},
        };
        assertTrue(PhaseReactionRules.match(3, 3, (i, s) -> table[i][s]));
    }
}
