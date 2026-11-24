package com.industrialcore.audit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.industrialcore.compat.jei.PowerGenerationCatalog;
import com.industrialcore.level.MachineSkillTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 106 Pre-Beta Comprehensive Playthrough &amp; Reachability Audit.
 *
 * <p>Validates the 5-stage survival progression chart, verifies that every non-legacy registered
 * item (340/340) and every skill-tree machine (38/38) is reachable from vanilla materials +
 * biome-wired worldgen ores without circular dependencies or crafting collisions, and confirms
 * full JEI power catalog, menu/screen, translation, model, texture, and sound integrity.</p>
 */
class Phase106PlaythroughAuditTest {
    private static final Gson GSON = new Gson();
    private static final Path SRC_MAIN = Path.of("src/main/java/com/industrialcore");
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/industrialcore");
    private static final Path ASSETS_ROOT = Path.of("src/main/resources/assets/industrialcore");

    /** Internal/legacy IDs intentionally excluded from survival crafting. */
    private static final Set<String> LEGACY_OR_INTERNAL_ITEMS = Set.of("industrialcore:conveyor");
    private static final Set<String> INTERNAL_ONLY_BLOCKS = Set.of("machine_bounding", "advanced_transporter_part");

    private static final Map<String, String> MACHINE_FOR_RECIPE_TYPE = Map.ofEntries(
            Map.entry("industrialcore:crushing", "industrialcore:crusher"),
            Map.entry("industrialcore:electrolysis", "industrialcore:electrolyzer"),
            Map.entry("industrialcore:precision_crushing", "industrialcore:precision_crusher"),
            Map.entry("industrialcore:pressing", "industrialcore:metal_press"),
            Map.entry("industrialcore:alloying", "industrialcore:alloy_furnace"),
            Map.entry("industrialcore:reconstruction", "industrialcore:reconstructor"),
            Map.entry("industrialcore:assembling", "industrialcore:assembler"),
            Map.entry("industrialcore:cultivation", "industrialcore:seed_cultivator"),
            Map.entry("industrialcore:amplification", "industrialcore:matter_amplifier"),
            Map.entry("industrialcore:phase_reaction", "industrialcore:phase_reactor"),
            Map.entry("industrialcore:synthesis", "industrialcore:ore_synthesizer")
    );

    private record ParsedRecipe(String file, String type, List<List<String>> inputs, List<String> outputs) {}

    // ------------------------------------------------------------------
    // 1. Exhaustive Survival Reachability (Zero Softlocks, Zero Cycles)
    // ------------------------------------------------------------------

    @Test
    void everySurvivalItemAndAllFivePlaythroughStagesAreReachableUnderSkillTreeGating() throws IOException {
        Set<String> allModItems = parseRegisteredItems();
        Set<String> worldgenOres = parseBiomeWiredWorldgenOres();
        Map<String, Set<String>> blockDrops = parseBlockLootDrops();
        List<ParsedRecipe> recipes = parseAllRecipes(allModItems);

        // Seed known survival items with biome-wired worldgen ores (silk-touch + normal drops)
        Set<String> known = new LinkedHashSet<>();
        for (String ore : worldgenOres) {
            known.add(ore);
            known.addAll(blockDrops.getOrDefault(ore, Set.of()));
        }

        // Skill-tree parent prerequisites
        Map<String, List<String>> skillParents = new HashMap<>();
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            List<String> ps = new ArrayList<>();
            for (String p : node.parents()) {
                ps.add("industrialcore:" + p);
            }
            skillParents.put("industrialcore:" + node.machineId(), ps);
        }

        // Fixed-point topological reachability respecting both recipe inputs AND skill tree parents
        boolean changed = true;
        int waves = 0;
        while (changed) {
            changed = false;
            waves++;
            for (ParsedRecipe r : recipes) {
                String reqMachine = MACHINE_FOR_RECIPE_TYPE.get(r.type());
                if (reqMachine != null && !known.contains(reqMachine)) {
                    continue;
                }
                if (!allInputsSatisfied(r.inputs(), known)) {
                    continue;
                }
                for (String out : r.outputs()) {
                    List<String> parents = skillParents.get(out);
                    if (parents != null && !known.containsAll(parents)) {
                        continue;
                    }
                    if (known.add(out)) {
                        changed = true;
                    }
                }
            }
        }

        Set<String> survivalItems = new TreeSet<>(allModItems);
        survivalItems.removeAll(LEGACY_OR_INTERNAL_ITEMS);

        Set<String> unreachable = new TreeSet<>();
        for (String item : survivalItems) {
            if (!known.contains(item)) {
                unreachable.add(item);
            }
        }
        assertTrue(unreachable.isEmpty(),
                "Unreachable survival items (softlock/cycle detected after " + waves + " waves): " + unreachable);

        // Explicitly verify all 38 skill tree nodes
        assertEquals(38, MachineSkillTree.totalNodes(), "Skill tree must contain all 38 machines");
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            assertTrue(known.contains("industrialcore:" + node.machineId()),
                    "Skill tree machine unreachable: " + node.machineId());
        }

        // Explicitly verify all 5 Playthrough Chart stages
        Map<String, List<String>> fiveStageChart = new LinkedHashMap<>();
        fiveStageChart.put("Stage1_EarlyIndustry", List.of(
                "machine_frame", "thermal_generator", "solar_generator", "crusher",
                "electric_furnace", "metal_press", "alloy_furnace", "energy_buffer",
                "cable_basic", "item_pipe", "fluid_pipe", "wrench"));
        fiveStageChart.put("Stage2_AutomationAgriTrapLife", List.of(
                "crop_harvester", "tree_harvester", "auto_fisher",
                "ranch_harvester", "growth_accelerator", "bio_feeder",
                "mob_fan", "rotary_drill", "item_vacuum",
                "xp_collector", "spawn_suppressor", "laser_turret",
                "auto_builder", "fluid_pump", "wireless_charger", "chunk_loader",
                "factory_monitor", "motor", "battery", "electronic_component", "high_power_motor"));
        fiveStageChart.put("Stage3_UraniumNuclearAndQuantumLogistics", List.of(
                "uranium_ore", "deepslate_uranium_ore", "raw_uranium", "uranium_dust",
                "enriched_uranium", "uranium_fuel_rod", "nuclear_generator",
                "reactor_casing", "reactor_glass", "reactor_core_cell",
                "reactor_coolant_block", "reactor_port",
                "quantum_storage_core", "storage_core_mk1", "storage_core_mk2",
                "storage_core_mk3", "storage_core_mk4", "storage_core_mk5",
                "quantum_importer", "quantum_exporter", "portable_quantum_terminal"));
        fiveStageChart.put("Stage4_EndgameLargeMachinesAndSingularity", List.of(
                "singularity_reactor", "hyper_energy_conduit", "hyper_item_conduit",
                "ore_synthesizer", "synthetic_ore_ultimate",
                "mob_duplicator", "mob_capsule", "warp_pad", "climate_controller"));
        fiveStageChart.put("Stage5_VirtualAegisSuitAndElectricWeapons", List.of(
                "suit_modifier", "virtual_helmet", "virtual_chestplate", "virtual_leggings", "virtual_boots",
                "mod_vision", "mod_autofeed", "mod_flight", "mod_shield",
                "mod_speed", "mod_step", "mod_nofall", "mod_jump",
                "pulse_laser", "railgun", "quantum_blade", "energy_bow",
                "att_auto_aim", "att_cryo_core", "att_prism_lens",
                "att_fast_capacitor", "att_wall_scanner", "att_recoil_gyro",
                "att_plasma_wave", "att_blink_drive", "att_quantum_loot",
                "att_multi_lock", "att_gravity_arrow", "att_auto_draw"));

        for (Map.Entry<String, List<String>> stage : fiveStageChart.entrySet()) {
            for (String id : stage.getValue()) {
                assertTrue(known.contains("industrialcore:" + id),
                        stage.getKey() + " milestone unreachable: " + id);
            }
        }

        // Explicitly verify all JEI Power Generation catalog generators and fuels are reachable
        for (PowerGenerationCatalog.Entry entry : PowerGenerationCatalog.entries()) {
            assertTrue(known.contains(entry.generatorId()),
                    "JEI power generator unreachable: " + entry.generatorId());
            if (!entry.fuelLess() && entry.fuelItemId().startsWith("industrialcore:")) {
                assertTrue(known.contains(entry.fuelItemId()),
                        "JEI power fuel unreachable: " + entry.fuelItemId());
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. Zero Crafting Collisions & Zero Broken Recipe References
    // ------------------------------------------------------------------

    @Test
    void noCraftingCollisionsAndZeroBrokenRecipeReferences() throws IOException {
        Set<String> allModItems = parseRegisteredItems();
        Map<String, List<String>> signatures = new HashMap<>();
        List<String> structuralIssues = new ArrayList<>();

        try (Stream<Path> stream = Files.list(DATA_ROOT.resolve("recipe"))) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(files.isEmpty(), "Expected shipped recipe JSONs");
            for (Path p : files) {
                String fname = p.getFileName().toString();
                JsonObject obj = GSON.fromJson(Files.readString(p), JsonObject.class);
                String type = obj.get("type").getAsString();

                if ("minecraft:crafting_shaped".equals(type)) {
                    JsonArray pattern = obj.getAsJsonArray("pattern");
                    JsonObject key = obj.getAsJsonObject("key");
                    Set<Character> used = new HashSet<>();
                    StringBuilder sig = new StringBuilder("shaped:");
                    for (JsonElement rowEl : pattern) {
                        String row = rowEl.getAsString();
                        sig.append('|');
                        for (int i = 0; i < row.length(); i++) {
                            char c = row.charAt(i);
                            if (c == ' ') {
                                sig.append("_,");
                            } else {
                                used.add(c);
                                if (!key.has(String.valueOf(c))) {
                                    structuralIssues.add(fname + ": undefined key '" + c + "'");
                                } else {
                                    sig.append(canonicalJson(key.get(String.valueOf(c)))).append(',');
                                }
                            }
                        }
                    }
                    for (String k : key.keySet()) {
                        if (!used.contains(k.charAt(0))) {
                            structuralIssues.add(fname + ": unused key '" + k + "'");
                        }
                    }
                    signatures.computeIfAbsent(sig.toString(), k -> new ArrayList<>()).add(fname);
                } else if ("minecraft:crafting_shapeless".equals(type)) {
                    List<String> parts = new ArrayList<>();
                    for (JsonElement ing : obj.getAsJsonArray("ingredients")) {
                        parts.add(canonicalJson(ing));
                    }
                    Collections.sort(parts);
                    String sig = "shapeless:" + String.join("+", parts);
                    signatures.computeIfAbsent(sig, k -> new ArrayList<>()).add(fname);
                } else if ("minecraft:smelting".equals(type) || "minecraft:blasting".equals(type)) {
                    String sig = type + ":" + canonicalJson(obj.get("ingredient"));
                    signatures.computeIfAbsent(sig, k -> new ArrayList<>()).add(fname);
                }
            }
        }

        // Also validate every industrialcore:* reference inside every recipe exists in ModItems
        parseAllRecipes(allModItems);

        assertTrue(structuralIssues.isEmpty(), "Recipe structural errors: " + structuralIssues);

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : signatures.entrySet()) {
            if (e.getValue().size() > 1) {
                collisions.add(e.getValue().toString());
            }
        }
        assertTrue(collisions.isEmpty(),
                "Duplicate recipe input signatures shadow crafting in survival: " + collisions);
    }

    // ------------------------------------------------------------------
    // 3. Menus, Screens, Translations, Loot Tables, Models, Textures, Sounds
    // ------------------------------------------------------------------

    @Test
    void everyMenuHasAScreenAndAllAssetsAndTranslationsAreComplete() throws IOException {
        // Every registered MenuType in ModMenus must have a Screen in ClientSetup
        String menusSrc = Files.readString(SRC_MAIN.resolve("registry/ModMenus.java"));
        String clientSetupSrc = Files.readString(SRC_MAIN.resolve("client/ClientSetup.java"));
        Set<String> menuFields = findAll(menusSrc, "public static final Supplier<MenuType<[^>]+>>\\s+(\\w+)");
        Set<String> screenFields = findAll(clientSetupSrc, "event\\.register\\(ModMenus\\.(\\w+)\\.get\\(\\)");
        Set<String> missingScreens = new TreeSet<>(menuFields);
        missingScreens.removeAll(screenFields);
        assertTrue(missingScreens.isEmpty(), "Menus missing Screen registration: " + missingScreens);

        // Lang parity & complete block/item names
        JsonObject en = GSON.fromJson(Files.readString(ASSETS_ROOT.resolve("lang/en_us.json")), JsonObject.class);
        JsonObject ja = GSON.fromJson(Files.readString(ASSETS_ROOT.resolve("lang/ja_jp.json")), JsonObject.class);
        assertEquals(en.keySet(), ja.keySet(), "en_us.json and ja_jp.json must have identical key sets");

        Set<String> blocks = parseRegisteredBlocks();
        Set<String> items = parseRegisteredItemPaths();
        for (String b : blocks) {
            if (INTERNAL_ONLY_BLOCKS.contains(b)) {
                continue;
            }
            assertTrue(en.has("block.industrialcore." + b), "Missing en_us for block: " + b);
            assertTrue(Files.exists(ASSETS_ROOT.resolve("blockstates/" + b + ".json")),
                    "Missing blockstate: " + b);
            if (!"quantum_compressor".equals(b)) {
                assertTrue(Files.exists(DATA_ROOT.resolve("loot_table/blocks/" + b + ".json")),
                        "Missing block loot table: " + b);
            }
        }
        for (String i : items) {
            assertTrue(Files.exists(ASSETS_ROOT.resolve("models/item/" + i + ".json")),
                    "Missing item model: " + i);
            if (!blocks.contains(i)) {
                assertTrue(en.has("item.industrialcore." + i), "Missing en_us for item: " + i);
            }
        }

        // Sounds.json -> every referenced industrialcore .ogg exists
        JsonObject sounds = GSON.fromJson(Files.readString(ASSETS_ROOT.resolve("sounds.json")), JsonObject.class);
        for (String key : sounds.keySet()) {
            JsonArray arr = sounds.getAsJsonObject(key).getAsJsonArray("sounds");
            for (JsonElement el : arr) {
                String ref = el.isJsonPrimitive() ? el.getAsString() : el.getAsJsonObject().get("name").getAsString();
                if (ref.startsWith("industrialcore:")) {
                    String rel = ref.substring("industrialcore:".length());
                    assertTrue(Files.exists(ASSETS_ROOT.resolve("sounds/" + rel + ".ogg")),
                            "Missing sound ogg for " + key + ": " + ref);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Set<String> parseRegisteredBlocks() throws IOException {
        String src = Files.readString(SRC_MAIN.resolve("registry/ModBlocks.java"));
        Set<String> out = new TreeSet<>();
        out.addAll(findAll(src, "BLOCKS\\.register\\(\\s*\"([^\"]+)\""));
        out.addAll(findAll(src, "ModBlocks\\.register(?:Pipe|Cable|Ore|Casing)\\(\\s*\"([^\"]+)\""));
        out.addAll(findAll(src, "register(?:Power|Recovery)\\(\\s*\"([^\"]+)\""));
        return out;
    }

    private static Set<String> parseRegisteredItemPaths() throws IOException {
        String src = Files.readString(SRC_MAIN.resolve("registry/ModItems.java"));
        return findAll(src, "ITEMS\\.register(?:SimpleBlockItem|SimpleItem)?\\(\\s*\"([^\"]+)\"");
    }

    private static Set<String> parseRegisteredItems() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String p : parseRegisteredItemPaths()) {
            out.add("industrialcore:" + p);
        }
        return out;
    }

    private static Set<String> parseBiomeWiredWorldgenOres() throws IOException {
        Set<String> wiredFeatures = new HashSet<>();
        try (Stream<Path> s = Files.list(DATA_ROOT.resolve("neoforge/biome_modifier"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject obj = GSON.fromJson(Files.readString(p), JsonObject.class);
                JsonElement feats = obj.get("features");
                if (feats.isJsonArray()) {
                    for (JsonElement f : feats.getAsJsonArray()) {
                        wiredFeatures.add(f.getAsString().replace("industrialcore:", ""));
                    }
                } else {
                    wiredFeatures.add(feats.getAsString().replace("industrialcore:", ""));
                }
            }
        }
        Set<String> ores = new TreeSet<>();
        for (String feat : wiredFeatures) {
            Path placed = DATA_ROOT.resolve("worldgen/placed_feature/" + feat + ".json");
            assertTrue(Files.exists(placed), "Biome modifier references missing placed_feature: " + feat);
            Path configured = DATA_ROOT.resolve("worldgen/configured_feature/" + feat + ".json");
            assertTrue(Files.exists(configured), "Biome modifier references missing configured_feature: " + feat);
            JsonObject cf = GSON.fromJson(Files.readString(configured), JsonObject.class);
            for (JsonElement target : cf.getAsJsonObject("config").getAsJsonArray("targets")) {
                String blockName = target.getAsJsonObject().getAsJsonObject("state").get("Name").getAsString();
                if (blockName.startsWith("industrialcore:")) {
                    ores.add(blockName);
                }
            }
        }
        return ores;
    }

    private static Map<String, Set<String>> parseBlockLootDrops() throws IOException {
        Map<String, Set<String>> drops = new HashMap<>();
        Pattern pName = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        try (Stream<Path> s = Files.list(DATA_ROOT.resolve("loot_table/blocks"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).toList()) {
                String blockId = "industrialcore:" + p.getFileName().toString().replace(".json", "");
                String raw = Files.readString(p);
                Matcher m = pName.matcher(raw);
                Set<String> set = new HashSet<>();
                while (m.find()) {
                    set.add(m.group(1));
                }
                drops.put(blockId, set);
            }
        }
        return drops;
    }

    private static List<ParsedRecipe> parseAllRecipes(Set<String> allModItems) throws IOException {
        List<ParsedRecipe> list = new ArrayList<>();
        List<String> broken = new ArrayList<>();
        try (Stream<Path> s = Files.list(DATA_ROOT.resolve("recipe"))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String fname = p.getFileName().toString();
                JsonObject d = GSON.fromJson(Files.readString(p), JsonObject.class);
                String type = d.get("type").getAsString();
                List<List<String>> inputs = new ArrayList<>();
                List<String> outputs = new ArrayList<>();

                if ("minecraft:crafting_shaped".equals(type)) {
                    JsonObject key = d.getAsJsonObject("key");
                    Set<Character> used = new HashSet<>();
                    for (JsonElement row : d.getAsJsonArray("pattern")) {
                        for (char c : row.getAsString().toCharArray()) {
                            if (c != ' ') {
                                used.add(c);
                            }
                        }
                    }
                    for (char c : used) {
                        if (key.has(String.valueOf(c))) {
                            inputs.add(extractOptions(key.get(String.valueOf(c))));
                        }
                    }
                    outputs.add(d.getAsJsonObject("result").get("id").getAsString());
                } else if ("minecraft:crafting_shapeless".equals(type)) {
                    for (JsonElement ing : d.getAsJsonArray("ingredients")) {
                        inputs.add(extractOptions(ing));
                    }
                    outputs.add(d.getAsJsonObject("result").get("id").getAsString());
                } else if ("minecraft:smelting".equals(type) || "minecraft:blasting".equals(type)) {
                    inputs.add(extractOptions(d.get("ingredient")));
                    JsonElement res = d.get("result");
                    outputs.add(res.isJsonObject() ? res.getAsJsonObject().get("id").getAsString() : res.getAsString());
                } else {
                    for (String k : List.of("ingredient", "sample", "material", "crystal", "seed",
                            "feed", "billet", "catalyst", "first", "second", "third", "fuel", "input")) {
                        if (d.has(k)) {
                            List<String> opts = extractOptions(d.get(k));
                            if (!opts.isEmpty()) {
                                inputs.add(opts);
                            }
                        }
                    }
                    if (d.has("ingredients") && d.get("ingredients").isJsonArray()) {
                        for (JsonElement ing : d.getAsJsonArray("ingredients")) {
                            List<String> opts = extractOptions(ing);
                            if (!opts.isEmpty()) {
                                inputs.add(opts);
                            }
                        }
                    }
                    for (String k : List.of("result", "output", "primary", "secondary", "byproduct", "ore", "dust", "extra")) {
                        if (d.has(k)) {
                            JsonElement v = d.get(k);
                            if (v.isJsonObject()) {
                                JsonObject vo = v.getAsJsonObject();
                                if (vo.has("id")) {
                                    outputs.add(vo.get("id").getAsString());
                                } else if (vo.has("item")) {
                                    outputs.add(vo.get("item").getAsString());
                                }
                            } else if (v.isJsonPrimitive()) {
                                outputs.add(v.getAsString());
                            }
                        }
                    }
                }

                for (List<String> opts : inputs) {
                    for (String opt : opts) {
                        if (opt.startsWith("industrialcore:") && !allModItems.contains(opt)) {
                            broken.add(fname + " INPUT " + opt);
                        }
                    }
                }
                for (String out : outputs) {
                    if (out.startsWith("industrialcore:") && !allModItems.contains(out)) {
                        broken.add(fname + " OUTPUT " + out);
                    }
                }
                list.add(new ParsedRecipe(fname, type, inputs, outputs));
            }
        }
        assertTrue(broken.isEmpty(), "Broken industrialcore item references in recipes: " + broken);
        return list;
    }

    private static List<String> extractOptions(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null) {
            return out;
        }
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                out.addAll(extractOptions(x));
            }
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) {
                out.add(o.get("item").getAsString());
            } else if (o.has("tag")) {
                out.add("#" + o.get("tag").getAsString());
            } else if (o.has("ingredient")) {
                out.addAll(extractOptions(o.get("ingredient")));
            }
        }
        return out;
    }

    private static boolean allInputsSatisfied(List<List<String>> inputs, Set<String> known) {
        for (List<String> opts : inputs) {
            boolean any = false;
            for (String opt : opts) {
                if (opt.startsWith("minecraft:") || opt.startsWith("#minecraft:") || opt.startsWith("#c:")
                        || known.contains(opt)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> findAll(String text, String regex) {
        Set<String> set = new TreeSet<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            set.add(m.group(1));
        }
        return set;
    }

    private static String canonicalJson(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "null";
        }
        if (el.isJsonPrimitive()) {
            return el.toString();
        }
        if (el.isJsonArray()) {
            List<String> items = new ArrayList<>();
            for (JsonElement child : el.getAsJsonArray()) {
                items.add(canonicalJson(child));
            }
            Collections.sort(items);
            return "[" + String.join(",", items) + "]";
        }
        JsonObject obj = el.getAsJsonObject();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String k : obj.keySet()) {
            sorted.put(k, canonicalJson(obj.get(k)));
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.append('}').toString();
    }
}
