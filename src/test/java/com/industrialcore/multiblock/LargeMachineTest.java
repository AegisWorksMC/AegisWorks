package com.industrialcore.multiblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.multiblock.LargeMachineLogic.Facing2D;
import com.industrialcore.multiblock.LargeMachineLogic.IntPos;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 104 unit tests: pure-Java geometry &amp; placement validation for auto-expanding
 * large machines ({@link LargeMachineLogic}) plus {@code machine_bounding} resource integrity.
 */
class LargeMachineTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void allFiveSpecsHaveExactDimensionsAndProxyCounts() {
        assertEquals(5, MachineSpec.values().length, "Exactly 5 upgraded large machines");

        assertEquals(12, MachineSpec.MOB_DUPLICATOR.totalBlocks());
        assertEquals(11, MachineSpec.MOB_DUPLICATOR.proxyCount());
        assertFalse(MachineSpec.MOB_DUPLICATOR.isLowPad());

        assertEquals(9, MachineSpec.WARP_PAD.totalBlocks());
        assertEquals(8, MachineSpec.WARP_PAD.proxyCount());
        assertTrue(MachineSpec.WARP_PAD.isLowPad());

        assertEquals(3, MachineSpec.CLIMATE_CONTROLLER.totalBlocks());
        assertEquals(2, MachineSpec.CLIMATE_CONTROLLER.proxyCount());
        assertFalse(MachineSpec.CLIMATE_CONTROLLER.isLowPad());

        assertEquals(2, MachineSpec.QUANTUM_STORAGE_CORE.totalBlocks());
        assertEquals(1, MachineSpec.QUANTUM_STORAGE_CORE.proxyCount());
        assertFalse(MachineSpec.QUANTUM_STORAGE_CORE.isLowPad());

        assertEquals(2, MachineSpec.SUIT_MODIFIER.totalBlocks());
        assertEquals(1, MachineSpec.SUIT_MODIFIER.proxyCount());
        assertFalse(MachineSpec.SUIT_MODIFIER.isLowPad());
    }

    @Test
    void fourDirectionalFootprintsAreUniqueContainMasterAndExcludeMasterFromProxies() {
        IntPos master = new IntPos(10, 64, -20);
        for (MachineSpec spec : MachineSpec.values()) {
            for (Facing2D facing : Facing2D.values()) {
                List<IntPos> all = LargeMachineLogic.allPositions(spec, master, facing);
                List<IntPos> proxies = LargeMachineLogic.proxyPositions(spec, master, facing);

                assertEquals(spec.totalBlocks(), all.size(), spec + " " + facing + " total size");
                assertEquals(spec.proxyCount(), proxies.size(), spec + " " + facing + " proxy size");
                assertEquals(all.size(), new HashSet<>(all).size(), spec + " " + facing + " duplicate cell");
                assertTrue(all.contains(master), spec + " " + facing + " must include master");
                assertFalse(proxies.contains(master), spec + " " + facing + " proxies must exclude master");
                assertEquals(new HashSet<>(all).size(), new HashSet<>(proxies).size() + 1);
            }
        }
    }

    @Test
    void mobDuplicatorRotatesCleanlyInAllFourFacings() {
        IntPos master = new IntPos(0, 64, 0);
        // Facing NORTH (outward -Z): right=+X, back=+Z -> x in [0,1], y in [64..66], z in [0,1]
        List<IntPos> north = LargeMachineLogic.allPositions(MachineSpec.MOB_DUPLICATOR, master, Facing2D.NORTH);
        assertTrue(north.contains(new IntPos(1, 66, 1)));
        assertFalse(north.contains(new IntPos(-1, 64, 0)));

        // Facing SOUTH (outward +Z): right=-X, back=-Z -> x in [-1,0], y in [64..66], z in [-1,0]
        List<IntPos> south = LargeMachineLogic.allPositions(MachineSpec.MOB_DUPLICATOR, master, Facing2D.SOUTH);
        assertTrue(south.contains(new IntPos(-1, 66, -1)));
        assertFalse(south.contains(new IntPos(1, 64, 0)));

        // Facing WEST (outward -X): right=-Z, back=+X -> x in [0,1], y in [64..66], z in [-1,0]
        List<IntPos> west = LargeMachineLogic.allPositions(MachineSpec.MOB_DUPLICATOR, master, Facing2D.WEST);
        assertTrue(west.contains(new IntPos(1, 66, -1)));

        // Facing EAST (outward +X): right=+Z, back=-X -> x in [-1,0], y in [64..66], z in [0,1]
        List<IntPos> east = LargeMachineLogic.allPositions(MachineSpec.MOB_DUPLICATOR, master, Facing2D.EAST);
        assertTrue(east.contains(new IntPos(-1, 66, 1)));
    }

    @Test
    void warpPadIsCentered3x1x3AcrossAllFacings() {
        IntPos master = new IntPos(5, 70, 5);
        Set<IntPos> expected = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                expected.add(new IntPos(5 + dx, 70, 5 + dz));
            }
        }
        for (Facing2D facing : Facing2D.values()) {
            assertEquals(expected, new HashSet<>(
                    LargeMachineLogic.allPositions(MachineSpec.WARP_PAD, master, facing)));
        }
    }

    @Test
    void canPlaceRejectsBlockedProxyCellAndWorldHeightOverflow() {
        IntPos master = new IntPos(0, 64, 0);
        assertTrue(LargeMachineLogic.canPlace(
                MachineSpec.MOB_DUPLICATOR, master, Facing2D.NORTH, -64, 320, (x, y, z) -> true));

        // Blocking any single proxy cell cancels placement
        for (IntPos blocked : LargeMachineLogic.proxyPositions(MachineSpec.MOB_DUPLICATOR, master, Facing2D.NORTH)) {
            assertFalse(LargeMachineLogic.canPlace(
                    MachineSpec.MOB_DUPLICATOR, master, Facing2D.NORTH, -64, 320,
                    (x, y, z) -> !(x == blocked.x() && y == blocked.y() && z == blocked.z())),
                    "Blocked proxy cell " + blocked + " must reject placement");
        }

        // World ceiling guard: height 3 at y=318 reaches y=320 (outside [min, max))
        assertFalse(LargeMachineLogic.canPlace(
                MachineSpec.CLIMATE_CONTROLLER, new IntPos(0, 318, 0), Facing2D.NORTH,
                -64, 320, (x, y, z) -> true));
        assertTrue(LargeMachineLogic.canPlace(
                MachineSpec.CLIMATE_CONTROLLER, new IntPos(0, 317, 0), Facing2D.NORTH,
                -64, 320, (x, y, z) -> true));

        // World floor guard
        assertFalse(LargeMachineLogic.canPlace(
                MachineSpec.WARP_PAD, new IntPos(0, -65, 0), Facing2D.NORTH,
                -64, 320, (x, y, z) -> true));
    }

    @Test
    void machineBoundingResourcesEmptyLootTableAndHiddenFromCreativeTab() throws IOException {
        assertTrue(Files.exists(ASSETS.resolve("blockstates/machine_bounding.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/block/machine_bounding.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/item/machine_bounding.json")));

        // Empty loot table ensures proxy block never drops a duplicate item
        JsonObject loot = JsonParser.parseString(
                Files.readString(DATA.resolve("industrialcore/loot_table/blocks/machine_bounding.json")))
                .getAsJsonObject();
        assertTrue(loot.has("pools") && loot.getAsJsonArray("pools").isEmpty(),
                "machine_bounding loot table must have empty pools");

        // Included in mineable/pickaxe tag
        String pickaxeTag = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(pickaxeTag.contains("industrialcore:machine_bounding"));

        // Must NOT be exposed in ModCreativeTabs or ModItems
        String creativeTabs = Files.readString(Path.of("src/main/java/com/industrialcore/registry/ModCreativeTabs.java"));
        assertFalse(creativeTabs.contains("MACHINE_BOUNDING"), "machine_bounding must stay hidden from creative tab");
        String modItems = Files.readString(Path.of("src/main/java/com/industrialcore/registry/ModItems.java"));
        assertFalse(modItems.contains("MACHINE_BOUNDING"), "machine_bounding must have no BlockItem");

        // Localization keys present in both ja_jp and en_us
        for (String lang : List.of("ja_jp.json", "en_us.json")) {
            JsonObject json = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + lang))).getAsJsonObject();
            assertTrue(json.has("block.industrialcore.machine_bounding"), lang + " missing machine_bounding");
            assertTrue(json.has("message.industrialcore.large_machine.no_space"), lang + " missing no_space");
        }
    }
}
