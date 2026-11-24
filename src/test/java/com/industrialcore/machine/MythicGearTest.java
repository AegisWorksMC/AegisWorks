package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 24 unit tests of the equipment numbers.
 *
 * <p>The values live in {@link MythicGear} and are read by {@code ModArmorMaterials} and
 * {@code ModItemTiers}, so the item registry never holds a second copy of them. What this test
 * cannot see: the actual in-game armour points and mining behaviour (those need a running game)
 * and the armour texture (a client-side concern).</p>
 */
class MythicGearTest {

    @Test
    void theAdamantiteSetIsDefensiveButNotFree() {
        MythicGear.ArmorSet set = MythicGear.ADAMANTITE_ARMOR;
        assertEquals(24, set.totalArmor(), "four pieces must add up to 24 armour points");
        assertEquals(4, set.helmet());
        assertEquals(9, set.chestplate());
        assertEquals(7, set.leggings());
        assertEquals(4, set.boots());
        assertTrue(set.totalArmor() > 20, "adamantite must beat a full diamond set");
        assertTrue(set.toughness() >= 3.0F, "the defensive set needs serious toughness");
        assertTrue(set.knockback() > 0.0F, "the defensive set resists knockback");
        assertTrue(set.enchantment() >= 15, "the enchantability must stay useful");
        // Armour durability: the base is multiplied by the slot factor of the game.
        assertEquals(45, set.durabilityBase());
    }

    @Test
    void orichalcumOutclassesMithrilAndMithrilOutclassesDiamond() {
        MythicGear.ToolTier mithril = MythicGear.MITHRIL_TIER;
        MythicGear.ToolTier orichalcum = MythicGear.ORICHALCUM_TIER;
        assertTrue(mithril.speed() > 8.0F, "mithril must be faster than a diamond tool (8.0)");
        assertTrue(orichalcum.speed() > mithril.speed(), "orichalcum is the fastest material");
        assertTrue(orichalcum.uses() > mithril.uses(), "orichalcum lasts longer");
        assertTrue(mithril.uses() > 1_561, "mithril must beat a diamond pickaxe (1,561 uses)");
        assertTrue(orichalcum.enchantment() > mithril.enchantment(),
                "the top material is the most enchantable");
        // Both are tagged, not hard coded to a level number.
        assertEquals("minecraft:incorrect_for_diamond_tool", mithril.harvestTag());
        assertEquals("minecraft:incorrect_for_netherite_tool", orichalcum.harvestTag());
    }

    @Test
    void toolDamageFollowsTheVanillaBases() {
        // A vanilla pickaxe does 1 damage, a vanilla sword 3, before the material bonus.
        assertEquals(3.5F, MythicGear.pickaxeDamage(MythicGear.MITHRIL_TIER));
        assertEquals(4.5F, MythicGear.pickaxeDamage(MythicGear.ORICHALCUM_TIER));
        assertEquals(5.5F, MythicGear.swordDamage(MythicGear.MITHRIL_TIER));
        assertEquals(6.5F, MythicGear.swordDamage(MythicGear.ORICHALCUM_TIER));
    }
}
