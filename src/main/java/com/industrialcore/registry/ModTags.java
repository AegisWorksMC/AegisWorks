package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Data-pack driven tags used by the quarry. */
public final class ModTags {
    /**
     * Blocks the quarry must never break. Populated by
     * {@code data/industrialcore/tags/block/quarry_unbreakable.json} and extendable by data packs.
     */
    public static final TagKey<Block> QUARRY_UNBREAKABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quarry_unbreakable"));

    /**
     * The conventional "all ores" block tag ({@code c:ores}). NeoForge fills it for the vanilla
     * ores and every mod that follows the convention adds its own ores, so the large quarry ore
     * picker needs no per-mod list and never treats a block outside the tag as an ore.
     */
    public static final TagKey<Block> ORES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private ModTags() {}
}
