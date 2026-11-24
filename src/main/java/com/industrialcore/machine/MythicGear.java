package com.industrialcore.machine;

/**
 * Stats of the Phase 24 equipment, kept free of Minecraft classes so they can be unit tested.
 *
 * <p>The mod deliberately does not put a low fixed ceiling on its gear: adamantite is the durable
 * set, mithril the fast one and orichalcum the high-performance one, and their numbers are the
 * single source of truth of {@code com.industrialcore.registry.ModArmorMaterials} and
 * {@code ModItemTiers}. An enchantment is never capped by the material - the material only decides
 * the durability, the mining speed and the enchantability that an anvil and a table start from.</p>
 *
 * <p>Reference points of the vanilla materials: iron has a durability base of 15, diamond 33 and
 * netherite 37; a diamond pickaxe has 1,561 uses and a speed of 8.0.</p>
 */
public final class MythicGear {
    /**
     * One armour material.
     *
     * @param durabilityBase armour points are multiplied by {@code ArmorItem.Type.getDurability};
     *                       this is the multiplier every material shares
     * @param helmet         armour points of the helmet
     * @param chestplate     armour points of the chestplate
     * @param leggings       armour points of the leggings
     * @param boots          armour points of the boots
     * @param enchantment    enchantability (higher is better)
     * @param toughness      armour toughness
     * @param knockback      knockback resistance (0.0 - 1.0)
     */
    public record ArmorSet(int durabilityBase, int helmet, int chestplate, int leggings, int boots,
                           int enchantment, float toughness, float knockback) {
        /** Total armour points of the four pieces. */
        public int totalArmor() {
            return this.helmet + this.chestplate + this.leggings + this.boots;
        }
    }

    /**
     * One tool material.
     *
     * @param uses         durability of a pickaxe (a sword uses the same base)
     * @param speed        mining speed multiplier
     * @param attackBonus  attack damage added on top of the tool's own value
     * @param harvestTag   block tag of the blocks the tool cannot harvest, i.e. the harvest level
     *                     ({@code minecraft:incorrect_for_diamond_tool} behaves like diamond)
     * @param enchantment  enchantability
     */
    public record ToolTier(int uses, float speed, float attackBonus, String harvestTag,
                           int enchantment) {}

    /**
     * Adamantite armour: the defensive set. 24 armour points (diamond: 20), toughness 3.0 and a
     * little knockback resistance, paid for with 45 durability units per slot.
     */
    public static final ArmorSet ADAMANTITE_ARMOR =
            new ArmorSet(45, 4, 9, 7, 4, 18, 3.0F, 0.1F);

    /**
     * Mithril tools: faster than diamond (9.0 against 8.0) with more enchantability, but less
     * durability than netherite. The "work efficiency" set.
     */
    public static final ToolTier MITHRIL_TIER = new ToolTier(1_800, 9.0F, 2.5F,
            "minecraft:incorrect_for_diamond_tool", 24);

    /**
     * Orichalcum tools: the high-performance set. Speed 12.0, 2,600 uses and the highest
     * enchantability of the mod.
     */
    public static final ToolTier ORICHALCUM_TIER = new ToolTier(2_600, 12.0F, 3.5F,
            "minecraft:incorrect_for_netherite_tool", 25);

    /** Attack damage of a pickaxe with the given material (vanilla pickaxe base is 1). */
    public static float pickaxeDamage(ToolTier tier) {
        return 1.0F + tier.attackBonus();
    }

    /** Attack damage of a sword with the given material (vanilla sword base is 3). */
    public static float swordDamage(ToolTier tier) {
        return 3.0F + tier.attackBonus();
    }

    private MythicGear() {}
}
