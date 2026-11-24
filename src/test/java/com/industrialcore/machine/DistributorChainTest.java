package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.menu.ItemDistributorLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 19 data test: the distributor is craftable, loop free, drawn and translated.
 *
 * <p>Reads the real JSON that ships with the mod, so a changed ingredient, a missing texture or a
 * forgotten translation is caught without starting a server.</p>
 */
class DistributorChainTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path RECIPES = RESOURCES.resolve("data/industrialcore/recipe");
    private static final Path ASSETS = RESOURCES.resolve("assets/industrialcore");
    private static final Path DATA = RESOURCES.resolve("data/industrialcore");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String resultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null || !result.isJsonObject()) {
            return "";
        }
        JsonElement id = result.getAsJsonObject().get("id");
        return id == null ? "" : id.getAsString();
    }

    /** Item ids of the shaped recipe key, mapped by their pattern letter. */
    private static Map<String, String> keyItems(JsonObject recipe) {
        Map<String, String> items = new java.util.LinkedHashMap<>();
        JsonElement key = recipe.get("key");
        if (key == null || !key.isJsonObject()) {
            return items;
        }
        for (Map.Entry<String, JsonElement> entry : key.getAsJsonObject().entrySet()) {
            JsonElement item = entry.getValue().getAsJsonObject().get("item");
            items.put(entry.getKey(), item == null ? "" : item.getAsString());
        }
        return items;
    }

    /** All pattern letters, so a letter used twice counts twice. */
    private static List<String> patternLetters(JsonObject recipe) {
        List<String> letters = new ArrayList<>();
        for (JsonElement row : recipe.getAsJsonArray("pattern")) {
            for (char letter : row.getAsString().toCharArray()) {
                if (letter != ' ') {
                    letters.add(String.valueOf(letter));
                }
            }
        }
        return letters;
    }

    private static int ingredientCount(JsonObject recipe, String itemId) {
        Map<String, String> key = keyItems(recipe);
        int count = 0;
        for (String letter : patternLetters(recipe)) {
            if (itemId.equals(key.get(letter))) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // recipe
    // ------------------------------------------------------------------

    @Test
    void theDistributorIsCraftableFromAssembledParts() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("item_distributor.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("industrialcore:item_distributor", resultId(recipe));

        // The second consumer of the electronic component: one assembler run (two components)
        // is exactly enough for one distributor.
        assertEquals(2, ingredientCount(recipe, "industrialcore:electronic_component"),
                "the distributor should use two electronic components");
        assertEquals(1, ingredientCount(recipe, "industrialcore:machine_frame"),
                "the distributor should use a machine frame");
        assertTrue(ingredientCount(recipe, "industrialcore:iron_plate") >= 2,
                "the distributor should use pressed iron plates");
    }

    /** No cycle: neither the distributor nor any machine it needs may require a distributor. */
    @Test
    void theDistributorNeverRequiresItself() throws IOException {
        JsonObject recipe = json(RECIPES.resolve("item_distributor.json"));
        assertFalse(keyItems(recipe).containsValue("industrialcore:item_distributor"),
                "the distributor recipe contains its own result");

        for (String machine : List.of("assembler.json", "energy_buffer_mk2.json",
                "machine_frame.json", "electronic_component.json")) {
            Path path = RECIPES.resolve(machine);
            if (!Files.exists(path)) {
                continue;
            }
            assertFalse(Files.readString(path).contains("industrialcore:item_distributor"),
                    machine + " requires a distributor, which would be a cycle");
        }
    }

    // ------------------------------------------------------------------
    // assets and translations
    // ------------------------------------------------------------------

    @Test
    void theBlockHasModelsTexturesAndDrops() {
        assertTrue(Files.exists(ASSETS.resolve("blockstates/item_distributor.json")),
                "missing blockstate");
        assertTrue(Files.exists(ASSETS.resolve("models/block/item_distributor.json")),
                "missing block model");
        assertTrue(Files.exists(ASSETS.resolve("models/item/item_distributor.json")),
                "missing block item model");
        assertTrue(Files.exists(DATA.resolve("loot_table/blocks/item_distributor.json")),
                "missing loot table (the block would drop nothing)");
        // Short texture names keep every path inside the Windows MAX_PATH limit.
        for (String texture : List.of("ds_front", "ds_side", "ds_top")) {
            assertTrue(Files.exists(ASSETS.resolve("textures/block/" + texture + ".png")),
                    "missing machine texture " + texture);
        }
    }

    @Test
    void bothLanguagesCarryTheNewKeys() throws IOException {
        List<String> keys = List.of(
                "block.industrialcore.item_distributor",
                "gui.industrialcore.tooltip.item_distributor",
                "gui.industrialcore.distributor.buffer",
                "gui.industrialcore.distributor.outputs",
                "gui.industrialcore.distributor.mode",
                "gui.industrialcore.distributor.mode.round_robin",
                "gui.industrialcore.distributor.next",
                "gui.industrialcore.distributor.hint",
                "gui.industrialcore.distributor.status.waiting",
                "gui.industrialcore.distributor.status.working",
                "gui.industrialcore.distributor.status.blocked");
        for (String language : List.of("en_us", "ja_jp")) {
            JsonObject lang = json(ASSETS.resolve("lang/" + language + ".json"));
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
            }
        }
        JsonObject en = json(ASSETS.resolve("lang/en_us.json"));
        JsonObject ja = json(ASSETS.resolve("lang/ja_jp.json"));
        for (DistributorOutputState state : DistributorOutputState.values()) {
            assertTrue(en.has(state.translationKey()), "en_us is missing " + state.translationKey());
            assertTrue(ja.has(state.translationKey()), "ja_jp is missing " + state.translationKey());
        }
    }

    // ------------------------------------------------------------------
    // state contract
    // ------------------------------------------------------------------

    @Test
    void outputStateOrdinalsAreStable() {
        // The ordinal travels to the client through the container data: append, never reorder.
        DistributorOutputState[] values = DistributorOutputState.values();
        assertEquals(5, values.length);
        assertEquals(DistributorOutputState.DISABLED, values[0]);
        assertEquals(DistributorOutputState.READY, values[1]);
        assertEquals(DistributorOutputState.FULL, values[2]);
        assertEquals(DistributorOutputState.UNCONNECTED, values[3]);
        assertEquals(DistributorOutputState.INTAKE, values[4]);
        Set<String> keys = new LinkedHashSet<>();
        for (DistributorOutputState state : values) {
            assertTrue(keys.add(state.translationKey()), "duplicate translation key " + state);
        }
        assertTrue(DistributorOutputState.FULL.isProblem());
        assertFalse(DistributorOutputState.READY.isProblem());
        assertFalse(DistributorOutputState.INTAKE.isProblem());
    }

    // ------------------------------------------------------------------
    // GUI geometry
    // ------------------------------------------------------------------

    @Test
    void distributorLayoutHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(ItemDistributorLayout.BUFFER_SLOT);
        for (int side = 0; side < 6; side++) {
            rects.add(ItemDistributorLayout.faceRow(side));
        }
        for (int index = 0; index < 36; index++) {
            rects.add(ItemDistributorLayout.playerSlot(index));
        }
        assertNoOverlaps(rects);
        assertInsidePanel(rects);

        assertTrue(ItemDistributorLayout.STATUS_TEXT_Y + 8
                        < ItemDistributorLayout.BUFFER_SLOT.y(),
                "the status strip must sit above the buffer slot");
        assertTrue(ItemDistributorLayout.STATUS_TEXT_Y < ItemDistributorLayout.MODE_TEXT_Y);
        assertTrue(ItemDistributorLayout.MODE_TEXT_Y <= ItemDistributorLayout.OUTPUT_HEADER_Y);
        assertTrue(ItemDistributorLayout.OUTPUT_HEADER_Y < ItemDistributorLayout.FACE_ROW_Y);
        assertTrue(ItemDistributorLayout.faceRow(5).bottom() < ItemDistributorLayout.HINT_TEXT_Y,
                "the last output row must stay above the hint");
        assertTrue(ItemDistributorLayout.HINT_TEXT_Y + 9 <= ItemDistributorLayout.PLAYER_INV_Y,
                "the hint must not collide with the player inventory");
        assertTrue(ItemDistributorLayout.playerSlot(35).bottom() <= ItemDistributorLayout.HEIGHT,
                "the hotbar must fit inside the panel");
    }

    private static void assertNoOverlaps(List<Rect> rects) {
        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                Rect a = rects.get(first);
                Rect b = rects.get(second);
                assertFalse(a.intersects(b), a + " overlaps " + b);
            }
        }
    }

    private static void assertInsidePanel(List<Rect> rects) {
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0, rect + " starts outside the panel");
            assertTrue(rect.right() <= ItemDistributorLayout.WIDTH,
                    rect + " exceeds the panel width");
            assertTrue(rect.bottom() <= ItemDistributorLayout.HEIGHT,
                    rect + " exceeds the panel height");
        }
    }
}
