package com.industrialcore.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 25 unit tests: the quantum compressor's batch arithmetic, its energy accounting and the
 * data files that describe the machine.
 *
 * <p>The machine in a world is exercised by {@code QuantumCompressorGameTests}; this test pins the
 * pure logic (so a rounding change or a broken batch cap fails in seconds) and the JSON, so a
 * drifted recipe or a missing asset is caught by {@code ./gradlew test}.</p>
 */
class QuantumCompressorBalanceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path LOOT = RESOURCES.resolve("data/industrialcore/loot_table/blocks");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");

    /** The crushing recipe every number below is derived from. */
    private static final String CRUSH_RECIPE = "crush_raw_iron";
    private static final int RECIPE_TIME = 10;
    private static final int RECIPE_ENERGY = 10;
    private static final int RECIPE_OUTPUT = 2;

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    // ------------------------------------------------------------------
    // 1. The documented numbers
    // ------------------------------------------------------------------

    @Test
    void theShippedCrushingRecipeMatchesTheDocumentedValues() throws IOException {
        JsonObject recipe = json(RECIPES.resolve(CRUSH_RECIPE + ".json"));
        assertEquals("industrialcore:crushing", recipe.get("type").getAsString());
        assertEquals(RECIPE_TIME, recipe.get("time").getAsInt(),
                "the compressor's FE per item is derived from this value");
        assertEquals(RECIPE_ENERGY, recipe.get("energy").getAsInt());
        assertEquals(RECIPE_OUTPUT, recipe.getAsJsonObject("result").get("count").getAsInt());
        // The ore line's raw entrance runs at the unified 10 tick tempo of the line; the crusher
        // default (ProcessingBalance) still mirrors the ore-block crush recipes at 120 ticks, and
        // every shipped crushing recipe carries its own time, so the default never applies here.
        assertEquals(10, RECIPE_TIME,
                "the raw entrance must keep the unified ore-line time");
        assertEquals(ProcessingBalance.CRUSHER_ENERGY_PER_TICK, RECIPE_ENERGY,
                "the shipped crushing FE per tick must stay the crusher default");
    }

    @Test
    void theMachineConstantsAreTheDocumentedOnes() {
        assertEquals(64, QuantumCompressorBalance.BATCH_CAP);
        assertEquals(240, QuantumCompressorBalance.CYCLE_TICKS);
        assertEquals(4, QuantumCompressorBalance.OUTPUT_SLOTS);
        // A full batch of a 2-dust recipe is 128 items, so one output slot can never be enough.
        assertTrue(QuantumCompressorBalance.OUTPUT_SLOTS * QuantumCompressorBalance.SLOT_LIMIT
                        >= QuantumCompressorBalance.BATCH_CAP * RECIPE_OUTPUT,
                "the output slots must hold a whole batch");
        // The biggest cycle must fit into the buffer, and the buffer into an int.
        assertTrue(QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY,
                QuantumCompressorBalance.BATCH_CAP) <= QuantumCompressorBalance.CAPACITY,
                "a full cycle does not fit into the machine buffer");
        assertTrue(QuantumCompressorBalance.averageEnergyPerTick(
                        QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY,
                                QuantumCompressorBalance.BATCH_CAP),
                        QuantumCompressorBalance.CYCLE_TICKS)
                        <= QuantumCompressorBalance.MAX_RECEIVE,
                "the machine cannot be fed fast enough for a full batch");
    }

    @Test
    void oneItemCostsExactlyWhatTheCrusherCosts() {
        // Both machines read the same shipped recipe (crush_raw_iron: 10 ticks x 10 FE/t), so one
        // item costs the same everywhere; the in-game equality is pinned by
        // QuantumCompressorGameTests#theCrusherAndTheCompressorCostTheSamePerItem.
        long shippedCost = QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, 1);
        assertEquals(100L, shippedCost, "one crushed raw iron costs 100 FE");
        assertEquals(shippedCost, QuantumCompressorBalance.costPerItem(RECIPE_TIME, RECIPE_ENERGY));
    }

    @Test
    void theCycleCostsScaleWithTheBatch() {
        assertEquals(100L, QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, 1));
        assertEquals(800L, QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, 8));
        assertEquals(3_200L, QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, 32));
        assertEquals(6_400L, QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, 64));
        // The FE per item never changes, whatever the batch is.
        for (int batch : new int[] {1, 2, 3, 8, 16, 32, 63, 64}) {
            assertEquals(100L * batch,
                    QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, batch));
        }
    }

    @Test
    void theAverageDrawMatchesTheDocumentedTable() {
        long[][] table = {
                // batch, total FE, FE per tick (rounded up over the 240 tick cycle)
                {1, 100L, 1L},
                {8, 800L, 4L},
                {32, 3_200L, 14L},
                {64, 6_400L, 27L},
        };
        for (long[] row : table) {
            int batch = (int) row[0];
            long total = QuantumCompressorBalance.totalEnergy(RECIPE_TIME, RECIPE_ENERGY, batch);
            assertEquals(row[1], total, "batch " + batch + " total");
            assertEquals((int) row[2], QuantumCompressorBalance.averageEnergyPerTick(total,
                    QuantumCompressorBalance.CYCLE_TICKS), "batch " + batch + " FE/t");
        }
    }

    /** 100 FE over 240 ticks does not divide evenly; a recipe that does not must still pay exactly. */
    @Test
    void everyTickPaysExactlyItsShareOfTheCycle() {
        for (long total : new long[] {100L, 800L, 3_200L, 6_400L, 1_007L, 5L, 1L}) {
            long paid = 0;
            for (int tick = 1; tick <= QuantumCompressorBalance.CYCLE_TICKS; tick++) {
                long payment = QuantumCompressorBalance.paymentFor(total,
                        tick, QuantumCompressorBalance.CYCLE_TICKS);
                assertTrue(payment >= 0, "a negative payment for " + total);
                paid += payment;
            }
            assertEquals(total, paid, "the cycle paid " + paid + " instead of " + total);
        }
    }

    @Test
    void theDueEnergyIsMonotonicAndStopsAtTheTotal() {
        long total = 76_800L;
        long previous = 0;
        for (int tick = 1; tick <= QuantumCompressorBalance.CYCLE_TICKS; tick++) {
            long due = QuantumCompressorBalance.energyDueBy(total, tick,
                    QuantumCompressorBalance.CYCLE_TICKS);
            assertTrue(due >= previous, "the due energy went backwards at tick " + tick);
            assertTrue(due <= total, "the machine owes more than the cycle costs at tick " + tick);
            previous = due;
        }
        assertEquals(total, previous);
        // Beyond the cycle (never reached in game) the value must not grow any further.
        assertEquals(total, QuantumCompressorBalance.energyDueBy(total,
                QuantumCompressorBalance.CYCLE_TICKS + 500, QuantumCompressorBalance.CYCLE_TICKS));
        assertEquals(0, QuantumCompressorBalance.energyDueBy(total, 0,
                QuantumCompressorBalance.CYCLE_TICKS));
        assertEquals(0, QuantumCompressorBalance.energyDueBy(0,
                QuantumCompressorBalance.CYCLE_TICKS, QuantumCompressorBalance.CYCLE_TICKS));
    }

    // ------------------------------------------------------------------
    // 2. The batch decision
    // ------------------------------------------------------------------

    @Test
    void theBatchIsTheMinimumOfItemsRoomAndCap() {
        long roomy = 4L * QuantumCompressorBalance.SLOT_LIMIT;
        assertEquals(64, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, roomy),
                "a full input stack must use the whole batch cap");
        assertEquals(7, QuantumCompressorBalance.batchSize(7, RECIPE_OUTPUT, roomy));
        assertEquals(0, QuantumCompressorBalance.batchSize(0, RECIPE_OUTPUT, roomy));
        // 100 items of a 2-dust recipe would need 200 slots worth of room; only 64 is free.
        assertEquals(32, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT,
                QuantumCompressorBalance.SLOT_LIMIT));
        // Five free spaces cannot hold a single 2-dust result.
        assertEquals(2, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, 5));
        assertEquals(0, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, 1));
        assertEquals(0, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, 0));
        assertEquals(0, QuantumCompressorBalance.batchSize(64, 0, roomy));
    }

    @Test
    void aFullBatchNeedsTwoOutputSlots() {
        long perBatch = QuantumCompressorBalance.outputCount(RECIPE_OUTPUT,
                QuantumCompressorBalance.BATCH_CAP);
        assertEquals(128L, perBatch);
        // Two empty slots exactly take a 64 batch, so the whole batch fits.
        long twoSlots = QuantumCompressorBalance.freeSpaceFor(new int[] {0, 0},
                new boolean[] {false, false}, QuantumCompressorBalance.SLOT_LIMIT);
        assertEquals(perBatch, twoSlots);
        assertEquals(64, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, twoSlots),
                "two free slots must take the whole batch");
        // One empty slot only takes 32 items of the 2-dust recipe, so the batch shrinks.
        long oneSlot = QuantumCompressorBalance.freeSpaceFor(new int[] {0},
                new boolean[] {false}, QuantumCompressorBalance.SLOT_LIMIT);
        assertEquals(32, QuantumCompressorBalance.batchSize(64, RECIPE_OUTPUT, oneSlot),
                "one free slot must halve the batch instead of blocking the machine");
    }

    @Test
    void freeSpaceIgnoresOtherItemsAndPartialStacks() {
        int[] counts = {0, 60, 64, 5};
        boolean[] same = {false, true, true, false};
        // slot 0 empty -> 64, slot 1 same item -> 4, slot 2 full -> 0, slot 3 other item -> 0.
        assertEquals(68L, QuantumCompressorBalance.freeSpaceFor(counts, same,
                QuantumCompressorBalance.SLOT_LIMIT));
        // The same layout but every slot holds the planned result (the empty slot stays 64).
        assertEquals(64L + 4L + 0L + 59L,
                QuantumCompressorBalance.freeSpaceFor(counts, new boolean[] {true, true, true, true},
                        QuantumCompressorBalance.SLOT_LIMIT));
    }

    // ------------------------------------------------------------------
    // 3. Overflow safety
    // ------------------------------------------------------------------

    @Test
    void aHugeDatapackValueNeverWrapsIntoASmallNumber() {
        assertEquals(Long.MAX_VALUE,
                QuantumCompressorBalance.totalEnergy(Integer.MAX_VALUE, Integer.MAX_VALUE, 64));
        assertEquals(Long.MAX_VALUE,
                QuantumCompressorBalance.saturatingMultiply(Long.MAX_VALUE / 2, 4));
        assertEquals(0, QuantumCompressorBalance.saturatingMultiply(0, Long.MAX_VALUE));
        assertEquals(0, QuantumCompressorBalance.saturatingMultiply(-5, 3));
        assertEquals(Integer.MAX_VALUE,
                QuantumCompressorBalance.clampToInt(Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, QuantumCompressorBalance.averageEnergyPerTick(
                Long.MAX_VALUE, 1));
        assertEquals(0, QuantumCompressorBalance.clampToInt(-10));
        // The batch itself can never exceed the cap, whatever the input count is.
        assertEquals(QuantumCompressorBalance.BATCH_CAP, QuantumCompressorBalance.batchSize(
                Integer.MAX_VALUE, 1, Long.MAX_VALUE));
    }

    // ------------------------------------------------------------------
    // 4. The data files
    // ------------------------------------------------------------------

    @Test
    void theMachineCostsOneOfEveryTopTierItem() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("quantum_compressor.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonObject keys = recipe.getAsJsonObject("key");
        List<String> used = new ArrayList<>();
        for (String symbol : keys.keySet()) {
            used.add(keys.getAsJsonObject(symbol).get("item").getAsString());
        }
        // The three Phase 24 materials named by the design, plus the frame and plate lines.
        for (String required : List.of("industrialcore:quantum_shard",
                "industrialcore:high_power_motor", "industrialcore:phase_core",
                "industrialcore:steel_plate", "industrialcore:reinforced_machine_frame")) {
            assertTrue(used.contains(required),
                    "the quantum compressor recipe does not use " + required);
        }
        assertEquals("industrialcore:quantum_compressor",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());

        // Count the symbols in the pattern: the recipe must consume as much as it looks like.
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        int shards = 0;
        int motors = 0;
        int cores = 0;
        for (var row : pattern) {
            for (char symbol : row.getAsString().toCharArray()) {
                String id = keys.getAsJsonObject(String.valueOf(symbol)).get("item").getAsString();
                switch (id) {
                    case "industrialcore:quantum_shard" -> shards++;
                    case "industrialcore:high_power_motor" -> motors++;
                    case "industrialcore:phase_core" -> cores++;
                    default -> { }
                }
            }
        }
        assertEquals(2, shards, "the shell must consume two quantum shards");
        assertEquals(2, motors, "the shell must consume two high-performance motors");
        assertEquals(1, cores, "the shell must consume exactly one phase core");
    }

    /** The machine needs a reactor product, so it cannot be built before the phase reactor. */
    @Test
    void theMachineIsGatedBehindThePhaseReactor() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("quantum_compressor.json"));
        JsonObject keys = recipe.getAsJsonObject("key");
        boolean gated = false;
        for (String symbol : keys.keySet()) {
            String id = keys.getAsJsonObject(symbol).get("item").getAsString();
            if (id.equals("industrialcore:phase_core")
                    || id.equals("industrialcore:high_power_motor")) {
                gated = true;
            }
        }
        assertTrue(gated, "the compressor must require a phase reactor product");
        // The crushing recipes themselves are untouched: the compressor adds no recipe type.
        assertFalse(Files.exists(RECIPES.resolve("quantum_crushing.json")),
                "the compressor must reuse industrialcore:crushing, not add recipes");
    }

    @Test
    void theBlockHasItsAssetsAndTags() throws IOException {
        Path blockstate = ASSETS.resolve("blockstates/quantum_compressor.json");
        Path blockModel = ASSETS.resolve("models/block/quantum_compressor.json");
        Path itemModel = ASSETS.resolve("models/item/quantum_compressor.json");
        for (Path path : List.of(blockstate, blockModel, itemModel)) {
            assertTrue(Files.exists(path), "missing asset: " + path);
        }
        // No loot table file on purpose: its default path is exactly 260 characters on Windows and
        // a 1.21.1 loot table key cannot be overridden, so the block drops itself from getDrops().
        assertFalse(Files.exists(LOOT.resolve("qc.json")),
                "an unused loot table would suggest the block still uses one");
        String blockSource = Files.readString(Path.of(
                "src/main/java/com/industrialcore/block/QuantumCompressorBlock.java"));
        assertTrue(blockSource.contains("getDrops"),
                "the block must drop itself from getDrops");
        assertTrue(blockSource.contains("EXPLOSION_RADIUS"),
                "the drop must keep the survives-explosion behaviour");
        // getLootTable() is final in 1.21.1, so the block must not try to override it.
        assertFalse(blockSource.contains("getLootTable() {"),
                "getLootTable() is final in 1.21.1 and cannot be overridden");

        JsonObject state = json(blockstate);
        assertEquals(8, state.getAsJsonObject("variants").size(),
                "the machine must have one lit=false/true variant per horizontal facing");
        // Since Phase 47 the machine has a purpose-built 3D model instead of the flat orientable
        // cube; the invariants that matter are a real model, its own texture set and a rotation set.
        JsonObject model = json(blockModel);
        assertTrue(model.has("elements"), "the machine must have a 3D block model");
        assertTrue(model.getAsJsonArray("elements").size() >= 10,
                "the machine model must be built from real parts, not one box");
        assertEquals("industrialcore:block/qc_top",
                model.getAsJsonObject("textures").get("top").getAsString(),
                "the model must use the machine's own textures");
        assertEquals("industrialcore:block/quantum_compressor",
                json(itemModel).get("parent").getAsString(),
                "the item model must reuse the block model");

        String pickaxe = Files.readString(RESOURCES.resolve(
                "data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(pickaxe.contains("\"industrialcore:quantum_compressor\""),
                "the machine must be mined with a pickaxe");
    }

    @Test
    void bothLanguageFilesNameTheMachine() throws IOException {
        for (String lang : List.of("en_us", "ja_jp")) {
            JsonObject translations = json(ASSETS.resolve("lang/" + lang + ".json"));
            for (String key : List.of("block.industrialcore.quantum_compressor",
                    "gui.industrialcore.quantum_compressor.input",
                    "gui.industrialcore.quantum_compressor.outputs",
                    "gui.industrialcore.quantum_compressor.batch",
                    "gui.industrialcore.quantum_compressor.idle")) {
                assertTrue(translations.has(key), lang + " is missing " + key);
                assertFalse(translations.get(key).getAsString().isBlank(),
                        lang + " has an empty value for " + key);
            }
        }
    }

    /** The batch line of the GUI has to be a format string with two arguments. */
    @Test
    void theBatchTextTakesTheBatchAndTheDraw() throws IOException {
        String text = json(ASSETS.resolve("lang/en_us.json"))
                .get("gui.industrialcore.quantum_compressor.batch").getAsString();
        assertEquals(2, text.chars().filter(c -> c == '%').count(),
                "the batch line needs a batch and an FE/t argument: " + text);
    }
}
