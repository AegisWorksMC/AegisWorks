package com.industrialcore.registry;

import com.industrialcore.machine.MythicGear;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Tool materials of the Phase 24 equipment.
 *
 * <p>Mithril is the fast set (speed 9.0, 1,800 uses, enchantability 24) and orichalcum the high
 * performance one (speed 12.0, 2,600 uses, enchantability 25). Both stay below netherite durability
 * on purpose: the mod is about machines and lines, not about replacing the vanilla end-game gear
 * with a single ore. The values come from {@link MythicGear} and are unit tested.</p>
 */
public enum ModItemTiers implements Tier {
    MITHRIL(MythicGear.MITHRIL_TIER, () -> Ingredient.of(ModItems.MITHRIL_INGOT.get())),
    ORICHALCUM(MythicGear.ORICHALCUM_TIER, () -> Ingredient.of(ModItems.ORICHALCUM_INGOT.get()));

    private final MythicGear.ToolTier stats;
    private final Supplier<Ingredient> repair;

    ModItemTiers(MythicGear.ToolTier stats, Supplier<Ingredient> repair) {
        this.stats = stats;
        this.repair = repair;
    }

    /** The pure stat record, for tests and for the item attribute builders. */
    public MythicGear.ToolTier stats() {
        return this.stats;
    }

    @Override
    public int getUses() {
        return this.stats.uses();
    }

    @Override
    public float getSpeed() {
        return this.stats.speed();
    }

    @Override
    public float getAttackDamageBonus() {
        return this.stats.attackBonus();
    }

    /**
     * Harvest level: the blocks this material cannot mine at all. Mithril behaves like diamond
     * (it fails on netherite-only blocks), orichalcum like netherite.
     */
    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.parse(this.stats.harvestTag()));
    }

    @Override
    public int getEnchantmentValue() {
        return this.stats.enchantment();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repair.get();
    }
}
