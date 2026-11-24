package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the Phase 22 enchantment merging rule.
 *
 * <p>Installed pickaxes add their levels: Fortune II + II = IV, Silk Touch I + I = II, Efficiency
 * IV + IV = VIII. No mod-specific cap exists; only the int storage saturates.</p>
 */
class PickaxeStatsTest {

    @Test
    void levelsOfInstalledPickaxesAreAdded() {
        PickaxeStats stats = PickaxeStats.EMPTY
                .plus(2, 2, 0)
                .plus(2, 2, 0);
        assertEquals(2, stats.tools());
        assertEquals(4, stats.efficiency());
        assertEquals(4, stats.fortune());
        assertEquals(0, stats.silkTouch());

        PickaxeStats silky = PickaxeStats.EMPTY
                .plus(0, 0, 1)
                .plus(0, 0, 1);
        assertEquals(2, silky.silkTouch(), "Silk Touch I + I must merge to II");
        assertTrue(silky.hasSilkTouch());
    }

    @Test
    void differentEnchantmentsCoexist() {
        PickaxeStats stats = PickaxeStats.EMPTY.plus(4, 3, 1);
        assertEquals(4, stats.efficiency());
        assertEquals(3, stats.fortune());
        assertEquals(1, stats.silkTouch());
        assertTrue(stats.hasSilkTouch(), "silk touch must not be dropped because of fortune");
        assertEquals(8, stats.totalLevels());
    }

    @Test
    void thereIsNoModSpecificLevelCap() {
        PickaxeStats stats = PickaxeStats.EMPTY.plus(0, 300, 0);
        assertEquals(300, stats.fortune(), "vanilla's cap must not be applied by the machine");
        assertFalse(stats.hasSilkTouch());
    }

    @Test
    void absurdLevelsSaturateInsteadOfWrappingAround() {
        PickaxeStats stats = new PickaxeStats(1, Integer.MAX_VALUE, Integer.MAX_VALUE, 1);
        assertEquals(Integer.MAX_VALUE, stats.efficiency());
        assertEquals(Integer.MAX_VALUE, stats.totalLevels());
        PickaxeStats added = stats.plus(5, 5, 5);
        assertEquals(Integer.MAX_VALUE, added.efficiency());
        assertEquals(Integer.MAX_VALUE, added.fortune());
        assertEquals(6, added.silkTouch(), "small totals still add normally next to saturated ones");
        assertEquals(10, PickaxeStats.saturatingAdd(6, 4));
        assertEquals(1, PickaxeStats.saturatingAdd(-3, 1), "a negative total is treated as zero");
        assertEquals(Integer.MAX_VALUE, PickaxeStats.saturatingAdd(Integer.MAX_VALUE, -5),
                "a negative delta never reduces a stored total");
    }
}
