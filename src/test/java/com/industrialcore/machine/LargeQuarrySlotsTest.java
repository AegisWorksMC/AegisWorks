package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout invariants of the large quarry inventory.
 *
 * <p>The machine crashed with {@code Slot 18 not in valid range - [0,18)} because a Phase 20 save
 * (18 output slots, no pickaxes) restored a smaller array than the current code addressed. These
 * checks pin the ranges down so a future change to any of them is a deliberate, tested edit.</p>
 */
class LargeQuarrySlotsTest {
    @Test
    void theRangesAreContiguousAndDoNotOverlap() {
        assertEquals(0, LargeQuarrySlots.OUTPUT_START);
        assertEquals(LargeQuarrySlots.OUTPUT_START + LargeQuarrySlots.OUTPUT_SLOTS,
                LargeQuarrySlots.TOOL_START,
                "the pickaxe range must start right after the outputs");
        assertEquals(LargeQuarrySlots.TOOL_START + LargeQuarrySlots.TOOL_CAPACITY,
                LargeQuarrySlots.UPGRADE_SLOT,
                "the motor slot must follow the pickaxe range");
        assertEquals(LargeQuarrySlots.UPGRADE_SLOT + 1, LargeQuarrySlots.BATTERY_SLOT,
                "the battery slot must follow the motor slot");
        assertEquals(LargeQuarrySlots.BATTERY_SLOT + 1, LargeQuarrySlots.TOTAL_SLOTS);
        assertEquals(18, LargeQuarrySlots.OUTPUT_SLOTS);
        assertEquals(8, LargeQuarrySlots.TOOL_CAPACITY);
        assertEquals(28, LargeQuarrySlots.TOTAL_SLOTS);
    }

    @Test
    void everySlotBelongsToExactlyOneRange() {
        for (int slot = 0; slot < LargeQuarrySlots.TOTAL_SLOTS; slot++) {
            int matches = 0;
            if (LargeQuarrySlots.isOutputSlot(slot)) {
                matches++;
            }
            if (LargeQuarrySlots.isToolSlot(slot)) {
                matches++;
            }
            if (LargeQuarrySlots.isUpgradeSlot(slot)) {
                matches++;
            }
            if (LargeQuarrySlots.isBatterySlot(slot)) {
                matches++;
            }
            assertEquals(1, matches, "slot " + slot + " belongs to " + matches + " ranges");
            assertTrue(LargeQuarrySlots.isInRange(slot));
        }
        assertFalse(LargeQuarrySlots.isInRange(-1));
        assertFalse(LargeQuarrySlots.isInRange(LargeQuarrySlots.TOTAL_SLOTS));
    }

    @Test
    void theLastPickaxeSlotIsInsideTheInventory() {
        int last = LargeQuarrySlots.toolSlot(LargeQuarrySlots.TOOL_CAPACITY - 1);
        assertEquals(25, last);
        assertTrue(LargeQuarrySlots.isInRange(last), "the last pickaxe slot must be addressable");
        assertEquals(LargeQuarrySlots.TOOL_CAPACITY - 1, LargeQuarrySlots.toolIndex(last));
        assertEquals(-1, LargeQuarrySlots.toolIndex(LargeQuarrySlots.UPGRADE_SLOT),
                "the motor slot is not a pickaxe slot");
        assertEquals(LargeQuarrySlots.TOOL_START, LargeQuarrySlots.toolSlot(0));
    }

    /**
     * The Phase 20 inventory ended exactly where the pickaxe range starts today. That collision is
     * the whole bug: it must stay documented, and the migration must keep the outputs.
     */
    @Test
    void theLegacySizeCollidesWithTheToolStartAndStillMigrates() {
        assertEquals(LargeQuarrySlots.TOOL_START, LargeQuarrySlots.LEGACY_TOTAL_SLOTS,
                "the legacy size is no longer the tool start; re-check the migration");
        for (int slot = 0; slot < LargeQuarrySlots.LEGACY_TOTAL_SLOTS; slot++) {
            assertEquals(slot, LargeQuarrySlots.migrateSlot(slot, LargeQuarrySlots.LEGACY_TOTAL_SLOTS),
                    "a legacy output must keep its slot");
        }
        assertEquals(-1,
                LargeQuarrySlots.migrateSlot(LargeQuarrySlots.LEGACY_TOTAL_SLOTS,
                        LargeQuarrySlots.LEGACY_TOTAL_SLOTS),
                "a slot that only exists in the new layout must not be taken from an old save");
        assertEquals(LargeQuarrySlots.UPGRADE_SLOT,
                LargeQuarrySlots.migrateSlot(LargeQuarrySlots.UPGRADE_SLOT,
                        LargeQuarrySlots.TOTAL_SLOTS),
                "a current save keeps its motor slot");
    }

    @Test
    void theActiveToolCountIsAlwaysInsideTheRange() {
        assertEquals(LargeQuarryBalance.BASE_TOOL_SLOTS,
                LargeQuarrySlots.activeToolSlots(0));
        assertEquals(LargeQuarryBalance.BASE_TOOL_SLOTS,
                LargeQuarrySlots.activeToolSlots(-5));
        assertEquals(4, LargeQuarrySlots.activeToolSlots(4));
        assertEquals(LargeQuarrySlots.TOOL_CAPACITY,
                LargeQuarrySlots.activeToolSlots(10_000));
        // Whatever the count is, every reachable slot is inside the inventory.
        for (int configured = -3; configured <= 40; configured++) {
            int active = LargeQuarrySlots.activeToolSlots(configured);
            for (int index = 0; index < active; index++) {
                assertTrue(LargeQuarrySlots.isInRange(LargeQuarrySlots.toolSlot(index)),
                        "active count " + active + " reaches outside the inventory");
            }
        }
    }
}
