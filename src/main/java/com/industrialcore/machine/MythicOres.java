package com.industrialcore.machine;

import java.util.List;

/**
 * The eight Phase 24 ores: where they generate, how common they are and what they drop.
 *
 * <p>This class is deliberately free of Minecraft classes, exactly like {@link ProcessingBalance}
 * and {@link MachineFaceConfig}: the world generation JSON, the loot tables, the unit tests that
 * prove "the rarest ore is the rarest one" and {@code docs/PHASE24_MYTHIC_ORES.md} are all checked
 * against these numbers. Changing a height range or a vein count here and forgetting the JSON
 * therefore fails the build instead of silently rebalancing the world.</p>
 *
 * <p>Ordering rule of the progression: the eight ores are sorted from the most common to the
 * rarest, and no ore may be both higher in the list and more abundant than a later one. {@code
 * MythicOresTest} enforces it.</p>
 *
 * <p>The last three ores are crystals, not metals: their block drops the crystal directly, so they
 * have no raw ore and no smelting step. {@code quantum_ore} is the odd one out - the block is
 * called {@code quantum_ore} and it drops {@code quantum_shard}, because an item cannot share the
 * registry id of the block item.</p>
 */
public final class MythicOres {
    /**
     * One ore of the Phase 24 set.
     *
     * @param id             material id ({@code c:ores} entry, drop and smelting source)
     * @param block          id of the ore block; usually {@code <id>_ore}, but the two longest
     *                       material ids use a shorter block name because the generated loot table
     *                       path would otherwise pass the 260 character Windows path limit
     * @param dropItem       id of the item the ore block drops (never the block itself except with
     *                       silk touch)
     * @param ingot          id of the smelted item, or {@code null} for a crystal
     * @param veinsPerChunk  configured vein attempts per chunk
     * @param veinSize       blocks one vein may replace
     * @param minY           lowest generation height (inclusive)
     * @param maxY           highest generation height (inclusive)
     * @param discardOnAir   chance a vein block is skipped when it would touch air
     * @param needsDiamond   {@code true} when a diamond pickaxe (or better) is required to drop it
     */
    public record Ore(String id, String block, String dropItem, String ingot, int veinsPerChunk,
                      int veinSize, int minY, int maxY, double discardOnAir, boolean needsDiamond) {
        /** Registry id of the ore block. */
        public String blockId() {
            return "industrialcore:" + this.block;
        }

        /** Id of the placed feature that generates this ore. */
        public String placedFeature() {
            return "industrialcore:" + this.block + "_placed";
        }

        /** Id of the configured feature of this ore. */
        public String configuredFeature() {
            return "industrialcore:" + this.block;
        }

        /** {@code true} when the block drops a crystal that is not smelted. */
        public boolean isCrystal() {
            return this.ingot == null;
        }
    }

    /**
     * The eight ores, most common first.
     *
     * <p>The height bands deliberately climb from the deep levels to the peaks: adamantite, void
     * iron and orichalcum only exist in the deepslate layers, mithril and astral silver in the
     * middle, the two crystals in the caves and near the surface and aether crystal only above
     * y = 60. A player who has just reached diamonds still has to explore before the top tier is
     * available.</p>
     */
    public static final List<Ore> ALL = List.of(
            new Ore("astral_silver", "astral_silver_ore", "industrialcore:raw_astral_silver",
                    "industrialcore:astral_silver_ingot", 4, 6, -32, 32, 0.2D, false),
            new Ore("mithril", "mithril_ore", "industrialcore:raw_mithril",
                    "industrialcore:mithril_ingot", 3, 5, -48, 8, 0.0D, false),
            new Ore("nebula_crystal", "nebula_ore", "industrialcore:nebula_crystal", null,
                    3, 5, -16, 48, 0.35D, true),
            new Ore("orichalcum", "orichalcum_ore", "industrialcore:raw_orichalcum",
                    "industrialcore:orichalcum_ingot", 2, 4, -56, -24, 0.0D, true),
            new Ore("quantum_ore", "quantum_ore", "industrialcore:quantum_shard", null,
                    2, 4, -40, 16, 0.25D, true),
            new Ore("adamantite", "adamantite_ore", "industrialcore:raw_adamantite",
                    "industrialcore:adamantite_ingot", 2, 4, -64, -16, 0.0D, true),
            new Ore("aether_crystal", "aether_ore", "industrialcore:aether_crystal", null,
                    2, 3, 60, 180, 0.5D, true),
            new Ore("void_iron", "void_iron_ore", "industrialcore:raw_void_iron",
                    "industrialcore:void_iron_ingot", 1, 3, -64, -40, 0.0D, true));

    /** Lowest and highest vanilla overworld height; a generation band must stay inside them. */
    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_MAX_Y = 320;

    private MythicOres() {}

    /** The ore with that material id, or {@code null}. */
    public static Ore byId(String id) {
        for (Ore ore : ALL) {
            if (ore.id().equals(id)) {
                return ore;
            }
        }
        return null;
    }

    /** True when the material is one of the eight Phase 24 ores. */
    public static boolean contains(String id) {
        return byId(id) != null;
    }

    /** True when a generation band lies inside the overworld and is not inverted. */
    public static boolean validBand(Ore ore) {
        return ore.minY() >= WORLD_MIN_Y && ore.maxY() <= WORLD_MAX_Y
                && ore.minY() <= ore.maxY();
    }

    /**
     * Index of the ore in the common-to-rare ordering, used by the balance test.
     */
    public static int rarityRank(Ore ore) {
        return ALL.indexOf(ore);
    }
}
