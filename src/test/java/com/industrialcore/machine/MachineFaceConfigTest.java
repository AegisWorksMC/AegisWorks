package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 11.3-11.5: face modes, target slots and item filters. */
class MachineFaceConfigTest {
    private static final int UP = 1;
    private static final int DOWN = 0;
    private static final int EAST = 5;

    @Test
    void defaultConfigKeepsTheOldBehaviour() {
        MachineFaceConfig config = new MachineFaceConfig();
        assertTrue(config.isDefault());
        for (int side = 0; side < 6; side++) {
            assertEquals(FaceMode.AUTO, config.mode(side));
            assertEquals(MachineFaceConfig.ANY_SLOT, config.slot(side));
            assertEquals(ItemFilterMode.OFF, config.filterMode(side));
            assertTrue(config.canInsert(side, 0, "minecraft:iron_ore"));
            assertTrue(config.canExtract(side, 1, "minecraft:iron_ingot"));
        }
    }

    @Test
    void inputAndOutputFacesAreExclusive() {
        MachineFaceConfig config = new MachineFaceConfig();
        config.setMode(UP, FaceMode.INPUT);
        config.setMode(DOWN, FaceMode.OUTPUT);
        config.setMode(EAST, FaceMode.NONE);

        assertTrue(config.canInsert(UP, 0, "minecraft:iron_ore"));
        assertFalse(config.canExtract(UP, 1, "minecraft:iron_ingot"));
        assertFalse(config.canInsert(DOWN, 0, "minecraft:iron_ore"));
        assertTrue(config.canExtract(DOWN, 1, "minecraft:iron_ingot"));
        assertFalse(config.canInsert(EAST, 0, "minecraft:iron_ore"));
        assertFalse(config.canExtract(EAST, 1, "minecraft:iron_ingot"));
        assertFalse(config.isDefault());
    }

    @Test
    void targetSlotRestrictsInsertAndExtract() {
        MachineFaceConfig config = new MachineFaceConfig();
        config.setMode(UP, FaceMode.INPUT);
        config.setSlot(UP, 3);
        assertTrue(config.canInsert(UP, 3, "minecraft:oak_planks"));
        assertFalse(config.canInsert(UP, 0, "minecraft:oak_planks"),
                "an input face with a target slot accepted another slot");

        config.setMode(DOWN, FaceMode.OUTPUT);
        config.setSlot(DOWN, 9);
        assertTrue(config.canExtract(DOWN, 9, "minecraft:crafting_table"));
        assertFalse(config.canExtract(DOWN, 0, "minecraft:crafting_table"));

        // ANY_SLOT restores the unrestricted behaviour.
        config.setSlot(UP, MachineFaceConfig.ANY_SLOT);
        assertTrue(config.canInsert(UP, 0, "minecraft:oak_planks"));
    }

    @Test
    void allowFilterOnlyLetsListedItemsThrough() {
        MachineFaceConfig config = new MachineFaceConfig();
        config.setFilterMode(DOWN, ItemFilterMode.ALLOW);
        config.setAllowed(DOWN, List.of("minecraft:iron_ore", "minecraft:copper_ore"));

        assertTrue(config.canInsert(DOWN, 0, "minecraft:iron_ore"));
        assertFalse(config.canInsert(DOWN, 0, "minecraft:gold_ore"),
                "an allow list let an unlisted item through");
        assertFalse(config.itemAllowed(DOWN, "minecraft:coal"));
        assertTrue(config.itemAllowed(UP, "minecraft:coal"), "the filter leaked to another face");
    }

    @Test
    void denyFilterBlocksOnlyListedItems() {
        MachineFaceConfig config = new MachineFaceConfig();
        config.setFilterMode(DOWN, ItemFilterMode.DENY);
        config.setAllowed(DOWN, List.of("minecraft:iron_ore"));

        assertFalse(config.canInsert(DOWN, 0, "minecraft:iron_ore"));
        assertTrue(config.canInsert(DOWN, 0, "minecraft:gold_ore"));
    }

    @Test
    void filterModesCycleAndClear() {
        MachineFaceConfig config = new MachineFaceConfig();
        assertEquals(ItemFilterMode.ALLOW, config.cycleFilter(UP));
        assertEquals(ItemFilterMode.DENY, config.cycleFilter(UP));
        assertEquals(ItemFilterMode.OFF, config.cycleFilter(UP));
        config.setFilterMode(UP, ItemFilterMode.ALLOW);
        config.setAllowed(UP, List.of("minecraft:iron_ore"));
        config.setAllowed(UP, List.of());
        assertEquals(0, config.allowedIds(UP).size());
        // An empty allow list blocks everything; turning the filter off restores acceptance.
        assertFalse(config.canInsert(UP, 0, "minecraft:gold_ore"));
        config.setFilterMode(UP, ItemFilterMode.OFF);
        assertTrue(config.canInsert(UP, 0, "minecraft:gold_ore"));
    }

    @Test
    void toggleAllowedAddsAndRemoves() {
        MachineFaceConfig config = new MachineFaceConfig();
        assertTrue(config.toggleAllowed(UP, "minecraft:iron_ore"));
        assertTrue(config.allowedIds(UP).contains("minecraft:iron_ore"));
        assertTrue(config.toggleAllowed(UP, "minecraft:iron_ore"));
        assertTrue(config.allowedIds(UP).isEmpty());
        assertFalse(config.toggleAllowed(UP, null));
    }

    @Test
    void modeCycleAndSlotCycleWrap() {
        MachineFaceConfig config = new MachineFaceConfig();
        assertEquals(FaceMode.INPUT, config.cycleMode(UP));
        assertEquals(FaceMode.OUTPUT, config.cycleMode(UP));
        assertEquals(FaceMode.NONE, config.cycleMode(UP));
        assertEquals(FaceMode.AUTO, config.cycleMode(UP));

        int[] candidates = {0, 1, 2};
        assertEquals(0, config.cycleSlot(UP, candidates));
        assertEquals(1, config.cycleSlot(UP, candidates));
        assertEquals(2, config.cycleSlot(UP, candidates));
        assertEquals(MachineFaceConfig.ANY_SLOT, config.cycleSlot(UP, candidates));
        assertEquals(MachineFaceConfig.ANY_SLOT, config.cycleSlot(UP, new int[0]));
    }

    @Test
    void copyIsIndependent() {
        MachineFaceConfig config = new MachineFaceConfig();
        config.setMode(UP, FaceMode.INPUT);
        config.setAllowed(UP, List.of("minecraft:iron_ore"));
        MachineFaceConfig copy = config.copy();
        copy.setMode(UP, FaceMode.OUTPUT);
        copy.setAllowed(UP, List.of());
        assertEquals(FaceMode.INPUT, config.mode(UP));
        assertEquals(1, config.allowedIds(UP).size());
        assertEquals(FaceMode.OUTPUT, copy.mode(UP));
    }

    @Test
    void sideIndexIsValidated() {
        MachineFaceConfig config = new MachineFaceConfig();
        boolean threw = false;
        try {
            config.mode(6);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "an out-of-range side must be rejected");
        assertTrue(config.describe(UP).contains("auto"));
        config.setMode(UP, FaceMode.INPUT);
        config.setSlot(UP, 4);
        config.setFilterMode(UP, ItemFilterMode.ALLOW);
        assertTrue(config.describe(UP).contains("slot=4"));
    }
}
