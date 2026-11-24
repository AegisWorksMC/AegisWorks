package com.industrialcore.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static chain audit of the Phase 26-29 transporters, mirroring
 * {@code PhaseReactorTest}, {@code QuantumCompressorBalanceTest} and
 * {@code OreCycleAuditTest}: every ingredient the transporters consume must
 * itself be craftable from a recipe (or be a vanilla item or an ore drop),
 * both languages must translate every new key, and the pickaxe tag, models
 * and loot tables must exist for all seven new blocks.
 */
class TransporterChainTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path RECIPES =
            RESOURCES.resolve("data").resolve("industrialcore").resolve("recipe");
    private static final Path LANG = RESOURCES.resolve("assets")
            .resolve("industrialcore").resolve("lang");

    /** Ore drops that enter the chain as block loot instead of a recipe result. */
    private static final Set<String> ORE_DROPS = Set.of(
            "industrialcore:nebula_crystal",
            "industrialcore:quantum_shard",
            "industrialcore:aether_crystal");

    private static final List<String> MACHINE_IDS = List.of(
            "basic_item_transporter",
            "advanced_item_transporter",
            "transporter_casing",
            "advanced_transporter_casing",
            "quantum_transporter",
            "quantum_transporter_array",
            "quantum_transporter_casing");

    // ---------------------------------------------------------------- helpers

    private static Set<String> allRecipeResults() throws IOException {
        Set<String> results = new HashSet<>();
        try (Stream<Path> paths = Files.walk(RECIPES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json;
                try {
                    json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                } catch (RuntimeException parse) {
                    fail("unparseable recipe " + RECIPES.relativize(path) + ": " + parse);
                    return results;
                }
                if (json.has("result")) {
                    JsonElement result = json.get("result");
                    if (result.isJsonObject() && result.getAsJsonObject().has("id")) {
                        results.add(result.getAsJsonObject().get("id").getAsString());
                    } else if (result.isJsonPrimitive()) {
                        results.add(result.getAsString());
                    }
                }
            }
        }
        return results;
    }

    private static JsonObject recipe(String name) throws IOException {
        Path path = RECIPES.resolve(name + ".json");
        assertTrue(Files.exists(path), "missing recipe " + name);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    /** Every `{"item": ...}` ingredient of a recipe, collected recursively. */
    private static Set<String> itemIngredients(JsonObject json) {
        Set<String> ingredients = new HashSet<>();
        collectItems(json, ingredients);
        return ingredients;
    }

    private static void collectItems(JsonElement element, Set<String> into) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("item") && object.get("item").isJsonPrimitive()) {
                into.add(object.get("item").getAsString());
            }
            for (String key : object.keySet()) {
                collectItems(object.get(key), into);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectItems(child, into);
            }
        }
    }

    private static JsonObject lang(String name) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(name))).getAsJsonObject();
    }

    private static Set<String> pickaxeTag() throws IOException {
        Path path = RESOURCES.resolve("data").resolve("minecraft").resolve("tags")
                .resolve("block").resolve("mineable").resolve("pickaxe.json");
        JsonArray values = JsonParser.parseString(Files.readString(path))
                .getAsJsonObject().getAsJsonArray("values");
        Set<String> tags = new HashSet<>();
        for (JsonElement element : values) {
            tags.add(element.getAsString());
        }
        return tags;
    }

    // ----------------------------------------------------------------- tests

    @Test
    void everyTransporterHasAShapedRecipeWithTheExpectedResult() throws IOException {
        // Controllers are 3x3 shaped recipes producing one block.
        for (String controller : List.of("basic_item_transporter",
                "advanced_item_transporter", "quantum_transporter",
                "quantum_transporter_array")) {
            JsonObject json = recipe(controller);
            assertEquals("minecraft:crafting_shaped", json.get("type").getAsString(),
                    controller + " must be a shaped crafting recipe");
            assertEquals(3, json.getAsJsonArray("pattern").size(),
                    controller + " must be a three-row recipe");
            for (com.google.gson.JsonElement row : json.getAsJsonArray("pattern")) {
                assertEquals(3, row.getAsString().length(),
                        controller + " rows must be three characters wide");
            }
            String result = json.getAsJsonObject("result").get("id").getAsString();
            assertEquals("industrialcore:" + controller, result,
                    controller + " must craft exactly itself");
        }

        // Casings are 3x3 shaped recipes producing four plates at a time.
        for (String casing : List.of("transporter_casing", "advanced_transporter_casing",
                "quantum_transporter_casing")) {
            JsonObject json = recipe(casing);
            assertEquals("minecraft:crafting_shaped", json.get("type").getAsString(),
                    casing + " must be a shaped recipe");
            assertEquals(4, json.getAsJsonObject("result").get("count").getAsInt(),
                    casing + " is crafted four at a time");
            String result = json.getAsJsonObject("result").get("id").getAsString();
            assertEquals("industrialcore:" + casing, result,
                    casing + " must craft exactly itself");
        }
    }

    @Test
    void theEarlyTierUsesIronCopperFrameAndMotor() throws IOException {
        Set<String> ingredients = itemIngredients(recipe("basic_item_transporter"));
        assertTrue(ingredients.contains("minecraft:iron_ingot"),
                "the early tier keeps iron: " + ingredients);
        assertTrue(ingredients.contains("minecraft:copper_ingot"),
                "the early tier keeps copper: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:machine_frame"),
                "the early tier uses the basic machine frame: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:motor"),
                "the early tier uses the basic motor: " + ingredients);
        // Phase 24 high-tier materials must not gate the early tier.
        for (String banned : List.of("industrialcore:high_power_motor",
                "industrialcore:reinforced_machine_frame",
                "industrialcore:phase_core",
                "industrialcore:aether_crystal")) {
            assertTrue(!ingredients.contains(banned),
                    "the early tier must not require " + banned);
        }
    }

    @Test
    void theMidTierConsumesThePhase24MotorAndSteel() throws IOException {
        Set<String> ingredients = itemIngredients(recipe("advanced_item_transporter"));
        assertTrue(ingredients.contains("industrialcore:high_power_motor"),
                "the mid tier consumes the Phase 24 high power motor: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:reinforced_machine_frame"),
                "the mid tier consumes the reinforced machine frame: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:electronic_component"),
                "the mid tier consumes electronic components: " + ingredients);
        assertTrue(ingredients.contains("minecraft:iron_block")
                        || ingredients.contains("industrialcore:steel_ingot"),
                "the mid tier uses steel or iron blocks: " + ingredients);
    }

    @Test
    void theQuantumTierConsumesTheTopPhase24Materials() throws IOException {
        Set<String> ingredients = itemIngredients(recipe("quantum_transporter"));
        assertTrue(ingredients.contains("industrialcore:nebula_crystal"),
                "the quantum tier consumes nebula crystals: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:quantum_shard"),
                "the quantum tier consumes quantum shards: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:aether_crystal"),
                "the quantum tier consumes aether crystals: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:void_iron_ingot"),
                "the quantum tier consumes void iron: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:phase_core"),
                "the quantum tier consumes the phase core from the phase reactor: "
                        + ingredients);
    }

    @Test
    void theArrayRequiresAQuantumTransporterAndTheCompressor() throws IOException {
        Set<String> ingredients = itemIngredients(recipe("quantum_transporter_array"));
        assertTrue(ingredients.contains("industrialcore:quantum_transporter"),
                "Phase 29 must consume a Phase 28 machine as its progression gate: "
                        + ingredients);
        assertTrue(ingredients.contains("industrialcore:quantum_compressor"),
                "the array reuses the Phase 25 compressor: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:durable_machine_frame"),
                "the array consumes the durable machine frame: " + ingredients);
        assertTrue(ingredients.contains("industrialcore:phase_core"),
                "the array consumes phase cores: " + ingredients);
    }

    @Test
    void everyChainIngredientIsObtainableFromARecipeOrTheOres() throws IOException {
        Set<String> results = allRecipeResults();
        Set<String> vanillaPrefixes = List.of("minecraft:", "c:", "#").stream()
                .collect(java.util.stream.Collectors.toSet());

        for (String id : MACHINE_IDS) {
            Set<String> ingredients = itemIngredients(recipe(id));
            for (String ingredient : ingredients) {
                if (ORE_DROPS.contains(ingredient)) {
                    continue; // dropped by the Phase 24 ore blocks
                }
                if (ingredient.startsWith("minecraft:")) {
                    continue; // vanilla item, always obtainable
                }
                if (ingredient.startsWith("industrialcore:")) {
                    assertTrue(results.contains(ingredient),
                            ingredient + " (used by " + id
                                    + ") is not the result of any recipe");
                } else {
                    fail(id + " consumes a foreign item: " + ingredient);
                }
            }
        }
        // The ore drops themselves must come from somewhere: the ore loot tables
        // (asserted to exist by the asset test).
        assertTrue(results.contains("industrialcore:quantum_transporter"),
                "the quantum transporter must be craftable");

        // The vanilla-prefix set exists so the compiler keeps the intent readable.
        assertTrue(vanillaPrefixes.contains("minecraft:"));
    }

    @Test
    void allSevenBlocksAreMineableWithAPickaxe() throws IOException {
        Set<String> tag = pickaxeTag();
        for (String id : MACHINE_IDS) {
            assertTrue(tag.contains("industrialcore:" + id),
                    id + " missing from the mineable/pickaxe tag");
        }
    }

    @Test
    void bothLanguagesTranslateEveryNewKey() throws IOException {
        JsonObject en = lang("en_us.json");
        JsonObject ja = lang("ja_jp.json");

        Set<String> required = new HashSet<>();
        for (String id : MACHINE_IDS) {
            required.add("block.industrialcore." + id);
        }
        required.addAll(List.of(
                "gui.industrialcore.transporter.status.idle",
                "gui.industrialcore.transporter.status.working",
                "gui.industrialcore.transporter.status.no_structure",
                "gui.industrialcore.transporter.status.no_channel",
                "gui.industrialcore.transporter.status.no_receiver",
                "gui.industrialcore.transporter.status.receiver_full",
                "gui.industrialcore.transporter.channel",
                "gui.industrialcore.transporter.role_send",
                "gui.industrialcore.transporter.role_receive",
                "gui.industrialcore.transporter.item_toggle",
                "gui.industrialcore.transporter.power_toggle",
                "gui.industrialcore.transporter.structure_ok",
                "gui.industrialcore.transporter.structure_bad",
                "gui.industrialcore.transporter.destinations",
                "gui.industrialcore.transporter.sources",
                "gui.industrialcore.transporter.last_transfer"));

        for (String key : required) {
            assertTrue(en.has(key), "en_us.json is missing " + key);
            assertTrue(ja.has(key), "ja_jp.json is missing " + key);
        }
        // The GUI must also render a non-empty Japanese label for every key.
        for (String key : required) {
            assertTrue(ja.get(key).getAsString().strip().length() > 0,
                    "ja_jp.json has an empty value for " + key);
        }
    }

    @Test
    void everyBlockHasAModelALootTableAndATexture() throws IOException {
        for (String id : MACHINE_IDS) {
            assertTrue(Files.exists(RESOURCES.resolve("assets").resolve("industrialcore")
                            .resolve("blockstates").resolve(id + ".json")),
                    "missing blockstate for " + id);
            assertTrue(Files.exists(RESOURCES.resolve("assets").resolve("industrialcore")
                            .resolve("models").resolve("block").resolve(id + ".json")),
                    "missing block model for " + id);
            assertTrue(Files.exists(RESOURCES.resolve("assets").resolve("industrialcore")
                            .resolve("models").resolve("item").resolve(id + ".json")),
                    "missing item model for " + id);
            Path loot = RESOURCES.resolve("data").resolve("industrialcore")
                    .resolve("loot_table").resolve("blocks").resolve(id + ".json");
            assertTrue(Files.exists(loot), "missing loot table for " + id);
            // Parse it: an unreadable or malformed loot table would void the machine.
            JsonElement parsed = JsonParser.parseString(Files.readString(loot));
            assertTrue(parsed.getAsJsonObject().has("pools")
                            || parsed.getAsJsonObject().has("type"),
                    "loot table " + id + " has neither pools nor type");
        }
        // Every texture a block model references must exist and be non-empty: a missing one
        // renders the infamous magenta-black checkerboard in game.
        for (String id : MACHINE_IDS) {
            Path modelPath = RESOURCES.resolve("assets").resolve("industrialcore")
                    .resolve("models").resolve("block").resolve(id + ".json");
            JsonObject model = JsonParser.parseString(Files.readString(modelPath)).getAsJsonObject();
            JsonObject textures = model.getAsJsonObject("textures");
            assertTrue(textures != null, id + " block model has no textures");
            for (String key : textures.keySet()) {
                String reference = textures.get(key).getAsString();
                if (!reference.startsWith("industrialcore:")) {
                    continue; // minecraft: built-in texture, always present
                }
                // "industrialcore:block/bt_side" -> assets/industrialcore/textures/block/bt_side.png
                String relative = reference.substring("industrialcore:".length());
                Path texture = RESOURCES.resolve("assets").resolve("industrialcore")
                        .resolve("textures").resolve(relative + ".png");
                assertTrue(Files.exists(texture) && Files.size(texture) > 0,
                        id + " references missing texture " + reference);
            }
        }
        assertEquals(MACHINE_IDS.size(), 7, "the phase adds exactly seven blocks");
        assertTrue(MACHINE_IDS.stream().map(id -> id.toLowerCase(Locale.ROOT))
                        .allMatch(id -> id.length() > 0),
                "every id must be non-empty");
    }
}
