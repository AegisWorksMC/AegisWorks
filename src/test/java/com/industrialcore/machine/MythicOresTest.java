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
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 24 unit tests: the eight ores, their world generation, their loot and their uses.
 *
 * <p>Every claim is read back from the real resource files, so a hand-edited JSON that drifts away
 * from {@link MythicOres} fails the build instead of silently rebalancing the world. The table
 * below is the contract:</p>
 *
 * <ul>
 *   <li>eight ores, most common first, each one rarer than the one before it,</li>
 *   <li>each ore generates inside the overworld height range, in stone and in deepslate,</li>
 *   <li>each ore is in {@code c:ores} and mineable with a pickaxe,</li>
 *   <li>each ore drops its raw material (or crystal) and keeps itself with silk touch,</li>
 *   <li>each metal smelts and blasts into exactly one ingot,</li>
 *   <li>every material has at least two recipes that consume it.</li>
 * </ul>
 *
 * <p>What this test cannot see: whether a vein actually looks good in a real world (that needs a
 * client), and whether a tool feels right in the hand. Those stay marked NOT RUN in the docs.</p>
 */
class MythicOresTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path LOOT = RESOURCES.resolve("data/industrialcore/loot_table/blocks");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");
    private static final Path CONFIGURED =
            RESOURCES.resolve("data/industrialcore/worldgen/configured_feature");
    private static final Path PLACED =
            RESOURCES.resolve("data/industrialcore/worldgen/placed_feature");
    private static final Path BIOME_MODIFIER =
            RESOURCES.resolve("data/industrialcore/neoforge/biome_modifier/ores.json");

    /** The eight material ids of the specification (docs/PHASE24_MYTHIC_ORES.md). */
    private static final Set<String> SPEC_IDS = new TreeSet<>(List.of(
            "adamantite", "mithril", "orichalcum", "astral_silver", "void_iron",
            "nebula_crystal", "quantum_ore", "aether_crystal"));

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

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

    private static String itemOf(JsonObject object) {
        if (object == null) {
            return "";
        }
        JsonElement item = object.get("item");
        return item == null ? "" : item.getAsString();
    }

    private static int intOf(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static Set<String> values(Path path) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (JsonElement entry : json(path).getAsJsonArray("values")) {
            found.add(entry.getAsString());
        }
        return found;
    }

    /** Every item id a recipe consumes, including a shaped recipe's key table. */
    private static Set<String> inputsOf(JsonObject recipe) {
        Set<String> inputs = new LinkedHashSet<>();
        String single = itemOf(recipe.getAsJsonObject("ingredient"));
        if (!single.isEmpty()) {
            inputs.add(single);
        }
        for (String key : List.of("first", "second", "catalyst", "feed", "material", "crystal")) {
            String id = itemOf(recipe.getAsJsonObject(key));
            if (!id.isEmpty()) {
                inputs.add(id);
            }
        }
        JsonElement arrayElement = recipe.get("ingredients");
        if (arrayElement != null && arrayElement.isJsonArray()) {
            for (JsonElement element : arrayElement.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    String id = itemOf(element.getAsJsonObject());
                    if (!id.isEmpty()) {
                        inputs.add(id);
                    }
                }
            }
        }
        JsonObject keys = recipe.getAsJsonObject("key");
        if (keys != null) {
            for (String symbol : keys.keySet()) {
                String id = itemOf(keys.getAsJsonObject(symbol));
                if (!id.isEmpty()) {
                    inputs.add(id);
                }
            }
        }
        return inputs;
    }

    private static String textureOf(JsonObject model) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null || !textures.has("layer0")) {
            return "";
        }
        String layer = textures.get("layer0").getAsString();
        int colon = layer.indexOf(':');
        return (colon < 0 ? layer : layer.substring(colon + 1));
    }

    // ------------------------------------------------------------------
    // 1. The table itself
    // ------------------------------------------------------------------

    @Test
    void theEightOresAreTheSpecifiedOnes() {
        assertEquals(8, MythicOres.ALL.size(), "the specification names exactly eight ores");
        Set<String> ids = new TreeSet<>();
        Set<String> blocks = new TreeSet<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            ids.add(ore.id());
            blocks.add(ore.block());
            assertTrue(ore.dropItem().startsWith("industrialcore:"),
                    ore.id() + " must drop a mod item, was " + ore.dropItem());
            if (ore.ingot() == null) {
                assertTrue(ore.isCrystal(), ore.id() + " is a crystal without an ingot");
                // a crystal drops itself, except quantum_ore whose block id is taken by the block
                assertTrue(ore.dropItem().endsWith(ore.id())
                                || ore.dropItem().equals("industrialcore:quantum_shard"),
                        "a crystal must drop its own item: " + ore.id() + " -> " + ore.dropItem());
            } else {
                assertEquals("industrialcore:" + ore.id() + "_ingot", ore.ingot(),
                        ore.id() + " must smelt into its own ingot");
                assertEquals("industrialcore:raw_" + ore.id(), ore.dropItem(),
                        ore.id() + " must drop a raw ore");
            }
        }
        assertEquals(SPEC_IDS, ids, "the material ids of the specification changed");
        assertEquals(8, blocks.size(), "two ores share an ore block");
    }

    @Test
    void rarerOresAreNeverMoreCommon() {
        for (int first = 0; first < MythicOres.ALL.size(); first++) {
            for (int second = first + 1; second < MythicOres.ALL.size(); second++) {
                MythicOres.Ore common = MythicOres.ALL.get(first);
                MythicOres.Ore rare = MythicOres.ALL.get(second);
                assertTrue(common.veinsPerChunk() >= rare.veinsPerChunk(),
                        common.id() + " must not be rarer than " + rare.id());
                if (common.veinsPerChunk() == rare.veinsPerChunk()) {
                    assertTrue(common.veinSize() >= rare.veinSize(),
                            common.id() + " and " + rare.id() + " have the same vein count but "
                                    + "the rarer one has the bigger vein");
                }
            }
        }
        MythicOres.Ore rarest = MythicOres.ALL.get(MythicOres.ALL.size() - 1);
        assertEquals("void_iron", rarest.id(), "void iron must stay the rarest ore");
        assertEquals(1, rarest.veinsPerChunk(), "void iron is one vein per chunk");
    }

    @Test
    void generationBandsAreInsideTheWorld() {
        for (MythicOres.Ore ore : MythicOres.ALL) {
            assertTrue(MythicOres.validBand(ore),
                    ore.id() + " generates outside " + MythicOres.WORLD_MIN_Y + ".."
                            + MythicOres.WORLD_MAX_Y);
            assertTrue(ore.discardOnAir() >= 0.0D && ore.discardOnAir() < 1.0D,
                    ore.id() + " has an impossible air discard chance");
            assertTrue(ore.veinSize() > 0 && ore.veinsPerChunk() > 0,
                    ore.id() + " has an empty vein");
        }
        for (String deep : List.of("adamantite", "void_iron", "orichalcum")) {
            MythicOres.Ore ore = MythicOres.byId(deep);
            assertNotNull(ore);
            assertTrue(ore.maxY() <= 0, deep + " must stay in the deep layers, maxY " + ore.maxY());
        }
        MythicOres.Ore aether = MythicOres.byId("aether_crystal");
        assertNotNull(aether);
        assertTrue(aether.minY() >= 60, "aether crystal is a high-altitude ore");
        MythicOres.Ore mithril = MythicOres.byId("mithril");
        assertNotNull(mithril);
        assertFalse(mithril.needsDiamond(), "mithril is the early ore of the set");
    }

    // ------------------------------------------------------------------
    // 2. Tags and loot
    // ------------------------------------------------------------------

    @Test
    void everyOreIsInTheOreTagAndMineable() throws IOException {
        Set<String> ores = values(RESOURCES.resolve("data/c/tags/block/ores.json"));
        Set<String> pickaxe = values(
                RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));
        for (MythicOres.Ore ore : MythicOres.ALL) {
            assertTrue(ores.contains(ore.blockId()),
                    ore.blockId() + " is missing from c:ores");
            assertTrue(pickaxe.contains(ore.blockId()),
                    ore.blockId() + " is missing from mineable/pickaxe");
        }
        assertTrue(pickaxe.contains("industrialcore:phase_reactor"),
                "the phase reactor must be mineable with a pickaxe");
    }

    @Test
    void toolTierTagsMatchTheOreTable() throws IOException {
        Set<String> iron = values(
                RESOURCES.resolve("data/minecraft/tags/block/needs_iron_tool.json"));
        Set<String> diamond = values(
                RESOURCES.resolve("data/minecraft/tags/block/needs_diamond_tool.json"));
        for (MythicOres.Ore ore : MythicOres.ALL) {
            if (ore.needsDiamond()) {
                assertTrue(diamond.contains(ore.blockId()),
                        ore.blockId() + " must need a diamond pickaxe");
                assertFalse(iron.contains(ore.blockId()),
                        ore.blockId() + " is in both tool tags");
            } else {
                assertTrue(iron.contains(ore.blockId()),
                        ore.blockId() + " must need an iron pickaxe");
                assertFalse(diamond.contains(ore.blockId()),
                        ore.blockId() + " is in both tool tags");
            }
        }
    }

    @Test
    void everyOreDropsItsItemAndSilkTouchKeepsTheBlock() throws IOException {
        for (MythicOres.Ore ore : MythicOres.ALL) {
            JsonObject loot = json(LOOT.resolve(ore.block() + ".json"));
            String text = loot.toString();
            assertTrue(text.contains(ore.dropItem()),
                    ore.block() + " never drops " + ore.dropItem());
            assertTrue(text.contains(ore.blockId()),
                    ore.block() + " cannot be kept with silk touch");
            assertTrue(text.contains("minecraft:silk_touch"),
                    ore.block() + " has no silk touch branch");
            assertTrue(text.contains("minecraft:fortune"),
                    ore.block() + " has no fortune bonus");
        }
    }

    // ------------------------------------------------------------------
    // 3. Processing
    // ------------------------------------------------------------------

    @Test
    void everyMetalSmeltsAndBlastsIntoItsIngot() throws IOException {
        List<JsonObject> recipes = recipes();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            if (ore.ingot() == null) {
                continue;
            }
            for (String ingredient : List.of(ore.dropItem(), ore.blockId())) {
                assertEquals(1, countProcessing(recipes, "minecraft:smelting", ingredient,
                                ore.ingot(), 200),
                        ore.id() + ": " + ingredient + " must smelt into exactly one ingot");
                assertEquals(1, countProcessing(recipes, "minecraft:blasting", ingredient,
                                ore.ingot(), 100),
                        ore.id() + ": " + ingredient + " must blast into exactly one ingot");
            }
        }
    }

    private static int countProcessing(List<JsonObject> recipes, String type, String ingredient,
                                       String result, int cookingTime) {
        int found = 0;
        for (JsonObject recipe : recipes) {
            if (!typeOf(recipe).equals(type)) {
                continue;
            }
            if (!itemOf(recipe.getAsJsonObject("ingredient")).equals(ingredient)) {
                continue;
            }
            JsonObject output = recipe.getAsJsonObject("result");
            if (!idOf(output).equals(result) || intOf(output, "count", 1) != 1) {
                continue;
            }
            if (intOf(recipe, "cookingtime", -1) != cookingTime) {
                continue;
            }
            found++;
        }
        return found;
    }

    // ------------------------------------------------------------------
    // 4. World generation
    // ------------------------------------------------------------------

    @Test
    void worldGenerationMatchesTheOreTable() throws IOException {
        List<String> placedIds = new ArrayList<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            Path configuredFile = findConfiguredFor(ore);
            assertNotNull(configuredFile,
                    ore.blockId() + " has no configured feature that places it");
            JsonObject configured = json(configuredFile);
            JsonObject config = configured.getAsJsonObject("config");
            assertEquals(ore.veinSize(), config.get("size").getAsInt(),
                    ore.id() + " vein size drifted");
            assertEquals(ore.discardOnAir(),
                    config.get("discard_chance_on_air_exposure").getAsDouble(), 1.0E-9D,
                    ore.id() + " air discard drifted");
            String targets = config.getAsJsonArray("targets").toString();
            assertTrue(targets.contains("minecraft:stone_ore_replaceables"),
                    ore.id() + " is not generated in stone");
            assertTrue(targets.contains("minecraft:deepslate_ore_replaceables"),
                    ore.id() + " is not generated in deepslate");

            String featureId = "industrialcore:" + fileName(configuredFile);
            Path placedFile = findPlacedFor(featureId);
            assertNotNull(placedFile, ore.id() + " has no placed feature");
            JsonObject placement = json(placedFile).getAsJsonArray("placement").get(0)
                    .getAsJsonObject();
            assertEquals(ore.veinsPerChunk(), placement.get("count").getAsInt(),
                    ore.id() + " veins per chunk drifted");
            JsonObject height = json(placedFile).getAsJsonArray("placement").get(2)
                    .getAsJsonObject().getAsJsonObject("height");
            assertEquals(ore.minY(), height.getAsJsonObject("min_inclusive").get("absolute")
                    .getAsInt(), ore.id() + " min height drifted");
            assertEquals(ore.maxY(), height.getAsJsonObject("max_inclusive").get("absolute")
                    .getAsInt(), ore.id() + " max height drifted");
            placedIds.add("industrialcore:" + fileName(placedFile));
        }

        JsonObject modifier = json(BIOME_MODIFIER);
        assertEquals("neoforge:add_features", modifier.get("type").getAsString());
        assertEquals("#minecraft:is_overworld", modifier.get("biomes").getAsString());
        assertEquals("underground_ores", modifier.get("step").getAsString());
        Set<String> features = new TreeSet<>();
        for (JsonElement element : modifier.getAsJsonArray("features")) {
            features.add(element.getAsString());
        }
        assertEquals(new TreeSet<>(placedIds), features,
                "the biome modifier must place exactly the eight ores");
    }

    private static Path findConfiguredFor(MythicOres.Ore ore) throws IOException {
        try (Stream<Path> files = Files.list(CONFIGURED)) {
            for (Path path : files.sorted().toList()) {
                if (Files.readString(path).contains("\"" + ore.blockId() + "\"")) {
                    return path;
                }
            }
        }
        return null;
    }

    private static Path findPlacedFor(String featureId) throws IOException {
        try (Stream<Path> files = Files.list(PLACED)) {
            for (Path path : files.sorted().toList()) {
                if (json(path).get("feature").getAsString().equals(featureId)) {
                    return path;
                }
            }
        }
        return null;
    }

    private static String fileName(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    // ------------------------------------------------------------------
    // 5. Assets and language files
    // ------------------------------------------------------------------

    @Test
    void everyNewItemHasAModelAndATexture() throws IOException {
        List<String> items = new ArrayList<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            items.add(ore.dropItem().substring("industrialcore:".length()));
            if (ore.ingot() != null) {
                items.add(ore.ingot().substring("industrialcore:".length()));
            }
        }
        items.addAll(List.of("resonant_catalyst", "durable_machine_frame", "high_power_motor",
                "phase_core", "adamantite_helmet", "adamantite_chestplate",
                "adamantite_leggings", "adamantite_boots", "mithril_pickaxe", "mithril_sword",
                "orichalcum_pickaxe", "orichalcum_sword"));
        for (String item : items) {
            Path model = ASSETS.resolve("models/item/" + item + ".json");
            assertTrue(Files.exists(model), "missing item model for " + item);
            String texture = textureOf(json(model));
            assertFalse(texture.isEmpty(), item + " has no layer0 texture");
            assertTrue(Files.exists(ASSETS.resolve("textures/" + texture + ".png")),
                    "missing texture " + texture + " of " + item);
        }
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/ad_layer_1.png")),
                "missing the adamantite armour layer 1");
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/ad_layer_2.png")),
                "missing the adamantite armour layer 2");
    }

    @Test
    void everyNewBlockHasAModelBlockstateAndLootTable() throws IOException {
        List<String> blocks = new ArrayList<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            blocks.add(ore.block());
        }
        blocks.add("phase_reactor");
        for (String block : blocks) {
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + block + ".json")),
                    "missing blockstate for " + block);
            assertTrue(Files.exists(ASSETS.resolve("models/block/" + block + ".json")),
                    "missing block model for " + block);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + block + ".json")),
                    "missing block item model for " + block);
            assertTrue(Files.exists(LOOT.resolve(block + ".json")),
                    "missing loot table for " + block);
        }
    }

    @Test
    void bothLanguagesCoverTheNewContent() throws IOException {
        List<String> keys = new ArrayList<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            keys.add("block.industrialcore." + ore.block());
            keys.add("item.industrialcore." + ore.dropItem().substring("industrialcore:".length()));
            if (ore.ingot() != null) {
                keys.add("item.industrialcore." + ore.id() + "_ingot");
            }
        }
        for (String item : List.of("resonant_catalyst", "durable_machine_frame",
                "high_power_motor", "phase_core", "adamantite_helmet", "adamantite_chestplate",
                "adamantite_leggings", "adamantite_boots", "mithril_pickaxe", "mithril_sword",
                "orichalcum_pickaxe", "orichalcum_sword")) {
            keys.add("item.industrialcore." + item);
        }
        keys.add("block.industrialcore.phase_reactor");
        for (String key : List.of("inputs", "output", "rule")) {
            keys.add("gui.industrialcore.phase_reactor." + key);
        }
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = json(ASSETS.resolve("lang/" + language + ".json"));
            Set<String> missing = new TreeSet<>();
            for (String key : keys) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    // ------------------------------------------------------------------
    // 6. Uses and loops
    // ------------------------------------------------------------------

    @Test
    void everyMaterialHasAtLeastTwoUses() throws IOException {
        Map<String, List<String>> users = new LinkedHashMap<>();
        List<JsonObject> recipes = recipes();
        for (int index = 0; index < recipes.size(); index++) {
            for (String input : inputsOf(recipes.get(index))) {
                users.computeIfAbsent(input, key -> new ArrayList<>()).add(Integer.toString(index));
            }
        }
        for (MythicOres.Ore ore : MythicOres.ALL) {
            List<String> dropUsers = users.getOrDefault(ore.dropItem(), List.of());
            assertTrue(dropUsers.size() >= 2,
                    ore.dropItem() + " is only used by " + dropUsers.size() + " recipe");
            if (ore.ingot() != null) {
                List<String> ingotUsers = users.getOrDefault(ore.ingot(), List.of());
                assertTrue(ingotUsers.size() >= 2,
                        ore.ingot() + " is only used by " + ingotUsers.size() + " recipe");
            } else {
                // a crystal is its own material, so the drop check above already covers it
                assertTrue(dropUsers.size() >= 2, ore.id() + " has fewer than two uses");
            }
        }
    }

    @Test
    void everyReactorProductIsUsedByAMachineRecipe() throws IOException {
        for (String product : List.of("industrialcore:resonant_catalyst",
                "industrialcore:durable_machine_frame", "industrialcore:high_power_motor",
                "industrialcore:phase_core")) {
            boolean used = false;
            for (JsonObject recipe : recipes()) {
                if (typeOf(recipe).equals("minecraft:crafting_shaped")
                        && inputsOf(recipe).contains(product)) {
                    used = true;
                    break;
                }
            }
            assertTrue(used, product + " is produced but never used to build anything");
        }
    }

    /**
     * No new material may be turned back into its own source: that is the shape of an infinite
     * multiplier. The intended loops of the mod stay the Phase 21 dust loop only.
     */
    @Test
    void noNewMaterialRecipeFeedsItself() throws IOException {
        Set<String> newItems = new TreeSet<>();
        for (MythicOres.Ore ore : MythicOres.ALL) {
            newItems.add(ore.dropItem());
            newItems.add(ore.blockId());
            if (ore.ingot() != null) {
                newItems.add(ore.ingot());
            }
        }
        newItems.addAll(List.of("industrialcore:resonant_catalyst",
                "industrialcore:durable_machine_frame", "industrialcore:high_power_motor",
                "industrialcore:phase_core"));
        for (JsonObject recipe : recipes()) {
            Set<String> inputs = inputsOf(recipe);
            Set<String> outputs = new LinkedHashSet<>();
            for (String key : List.of("result", "secondary")) {
                String id = idOf(recipe.getAsJsonObject(key));
                if (!id.isEmpty()) {
                    outputs.add(id);
                }
            }
            for (String output : outputs) {
                assertFalse(inputs.contains(output),
                        "a recipe turns an item back into itself: " + output + " in " + recipe);
            }
            if (!typeOf(recipe).equals("industrialcore:phase_reaction")) {
                continue;
            }
            for (String output : outputs) {
                assertFalse(output.endsWith("_ore"),
                        "a phase reaction may not produce an ore block: " + recipe);
                assertFalse(output.startsWith("industrialcore:raw_"),
                        "a phase reaction may not produce a raw ore: " + recipe);
            }
        }
    }
}
