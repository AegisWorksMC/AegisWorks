package com.industrialcore.machine;

/**
 * Merged enchantment totals of the pickaxes installed in the large quarry.
 *
 * <p>The machine merges the enchantments of every installed pickaxe by <b>adding the levels</b>:
 * Fortune II + Fortune II becomes Fortune IV, Silk Touch I + Silk Touch I becomes Silk Touch II,
 * and Efficiency IV + Efficiency IV becomes Efficiency VIII. There is no mod-specific level cap;
 * only the storage type limits a single total (see {@link #plus}).</p>
 *
 * <p>Keeping the totals in one immutable value object means the block entity can compute them once
 * per tool change and then answer the per-block questions (drop count, FE cost, speed) with plain
 * arithmetic, instead of walking the item stacks for every mined block.</p>
 *
 * <p>This class is deliberately free of Minecraft classes so the arithmetic can be unit tested.</p>
 *
 * @param tools      pickaxes that are actually installed (non empty slots)
 * @param efficiency merged Efficiency levels
 * @param fortune    merged Fortune levels
 * @param silkTouch  merged Silk Touch levels
 */
public record PickaxeStats(int tools, int efficiency, int fortune, int silkTouch) {
    public static final PickaxeStats EMPTY = new PickaxeStats(0, 0, 0, 0);

    /**
     * Merges one pickaxe into a running total.
     *
     * <p>Levels are summed with saturation at {@link Integer#MAX_VALUE}: a total of roughly two
     * billion levels still behaves deterministically instead of overflowing into a negative value,
     * which is the only limit the Java storage type imposes. Vanilla's own enchantment cap (255 in
     * the item component, and the level cap configured per enchantment) is not applied here.</p>
     */
    public PickaxeStats plus(int efficiencyLevel, int fortuneLevel, int silkTouchLevel) {
        return new PickaxeStats(
                saturatingAdd(tools, 1),
                saturatingAdd(efficiency, Math.max(0, efficiencyLevel)),
                saturatingAdd(fortune, Math.max(0, fortuneLevel)),
                saturatingAdd(silkTouch, Math.max(0, silkTouchLevel)));
    }

    /** True when the merged enchantments recover the ore block itself. */
    public boolean hasSilkTouch() {
        return silkTouch > 0;
    }

    /** Merged levels of all three relevant enchantments, saturating. */
    public int totalLevels() {
        long total = (long) efficiency + fortune + silkTouch;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Saturating addition; negative deltas are ignored. */
    public static int saturatingAdd(int current, int delta) {
        if (delta <= 0) {
            return Math.max(0, current);
        }
        long sum = (long) Math.max(0, current) + delta;
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }
}
