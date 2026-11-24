package com.industrialcore.machine;

/**
 * Fixed design values and formulas of the large quarry.
 *
 * <p>Phase 22 redefined the machine: it is no longer a "quarry that also crushes". It scans its
 * fixed area for ore blocks only, mines the ores the player selected in the GUI, and keeps every
 * other block (stone, deepslate, dirt, ...) untouched. Installed pickaxes decide what the machine
 * recovers, and their FE cost grows with the installed tools and the merged enchantment levels.</p>
 *
 * <p>Everything here is plain arithmetic on purpose: the block entity asks these methods per mined
 * block, and unit tests can check them without a server. All products are computed in {@code long}
 * and saturate at {@link Integer#MAX_VALUE}, so hostile NBT or absurd enchantment totals can never
 * overflow into a negative cost or a negative drop count.</p>
 */
public final class LargeQuarryBalance {
    /** Default fixed working area: 7 x 7 blocks wide/deep, 5 blocks downwards (245 positions). */
    public static final int DEFAULT_WIDTH = 7;
    public static final int DEFAULT_DEPTH = 7;
    public static final int DEFAULT_HEIGHT = 5;

    /** Hard limit of one area dimension, independent of the configurable gameplay value. */
    public static final int MAX_AREA_DIMENSION = 32;

    /**
     * Base scanning speed in positions per tick. Only positions whose block is a selected ore cost
     * anything, so a mostly stone area is scanned quickly without breaking it.
     */
    public static final double OPERATIONS_PER_TICK = 0.5D;
    /** Hard per-tick cap; also the cap of the efficiency speed bonus. */
    public static final double MAX_OPERATIONS_PER_TICK = 2.0D;

    /** Base FE per mined ore block. Enchantments and extra tools add to this; see {@link #miningCost}. */
    public static final int ENERGY_PER_BLOCK = 100;
    /** Extra FE per pickaxe beyond the first: more arms mean more mechanical work. */
    public static final int ENERGY_PER_EXTRA_TOOL = 25;
    /** FE per merged Efficiency level (speed is already a benefit; this keeps it priced). */
    public static final int ENERGY_PER_EFFICIENCY = 15;
    /** FE per merged Fortune level: each level multiplies the recovered raw drops. */
    public static final int ENERGY_PER_FORTUNE = 30;
    /** FE per merged Silk Touch level: recovering whole ore blocks is the strongest effect. */
    public static final int ENERGY_PER_SILK_TOUCH = 60;
    /**
     * Divisor of the superlinear term {@code totalLevels^2 / divisor}. It makes the very high level
     * range noticeably more expensive without ever forbidding it.
     */
    public static final int ENERGY_LEVEL_QUADRATIC_DIVISOR = 10;

    public static final int CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 2_000;

    /** Output slots: two rows of nine, like the normal quarry. */
    public static final int OUTPUT_SLOTS = 18;

    /** One built-in pickaxe arm; each installed motor adds one more. */
    public static final int BASE_TOOL_SLOTS = 1;
    /**
     * Physical limit of the current GUI (two rows of four slots) and of the per-block FE product.
     * It is a layout limit, not a balance cap: a future GUI can raise it without touching the
     * formulas.
     */
    public static final int MAX_TOOL_SLOTS = 8;

    private LargeQuarryBalance() {}

    /** Clamps one configured area dimension into the supported range. */
    public static int clampDimension(int value) {
        return Math.max(1, Math.min(MAX_AREA_DIMENSION, value));
    }

    /** Clamps a persisted tool slot count into the supported range. */
    public static int clampToolSlots(int value) {
        return Math.max(BASE_TOOL_SLOTS, Math.min(MAX_TOOL_SLOTS, value));
    }

    /** Number of positions in the fixed area (saturating, never negative). */
    public static int volume(int width, int depth, int height) {
        return new MiningPlan(width, depth, height).volume();
    }

    /**
     * FE charged for mining one selected ore block with the current tool setup.
     *
     * <pre>
     *   cost = base
     *        + 25 * (installedTools - 1)
     *        + 15 * efficiency + 30 * fortune + 60 * silkTouch
     *        + totalLevels^2 / 10
     * </pre>
     *
     * <p>The last term is what makes the high level range expensive: a setup with 40 merged levels
     * pays 160 FE per block on top of the linear part, while a plain pickaxe pays nothing extra.
     * The cost does not depend on the number of drops (that is already the result of the
     * enchantments) so a single block is charged once.</p>
     *
     * @param base       configured base cost per block (never negative)
     * @param stats      merged tool statistics
     * @param toolCount  installed pickaxes, clamped to the supported slot range
     */
    public static int miningCost(int base, PickaxeStats stats, int toolCount) {
        PickaxeStats safe = stats == null ? PickaxeStats.EMPTY : stats;
        long tools = clampToolSlots(toolCount);
        long cost = Math.max(0, base);
        cost += ENERGY_PER_EXTRA_TOOL * Math.max(0L, tools - 1L);
        cost += ENERGY_PER_EFFICIENCY * (long) safe.efficiency();
        cost += ENERGY_PER_FORTUNE * (long) safe.fortune();
        cost += ENERGY_PER_SILK_TOUCH * (long) safe.silkTouch();
        long levels = Math.max(0L, safe.efficiency())
                + Math.max(0L, safe.fortune())
                + Math.max(0L, safe.silkTouch());
        if (levels > 0L) {
            // 2^62 / 10 still fits in a long, but levels are ints; the guard keeps the math total.
            long squared = levels > 3_000_000_000L ? Long.MAX_VALUE / 2 : levels * levels;
            cost += squared / ENERGY_LEVEL_QUADRATIC_DIVISOR;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, cost));
    }

    /**
     * Positions the machine may process per tick: Efficiency merges into scanning speed as well as
     * into drop yield, at 5% per merged level, always capped by the hard per-tick limit. The cap is
     * a safety limit (one tick must not process an unbounded area), not an enchantment cap.
     */
    public static double operationsPerTick(double configuredOps, double hardCap, PickaxeStats stats) {
        return operationsPerTick(configuredOps, hardCap, stats, 0);
    }

    /**
     * Same as {@link #operationsPerTick(double, double, PickaxeStats)}, with the motor count taken
     * into account. The first motors already unlocked pickaxe arms; every motor beyond that still
     * drives the frame faster (+3 % each, up to one full stack), which is what makes a stack of
     * 64 motors keep improving instead of silently stopping at the eight-arm layout limit. The
     * hard per-tick cap always wins, so a corrupted count cannot process an unbounded area.
     */
    public static double operationsPerTick(double configuredOps, double hardCap, PickaxeStats stats,
                                           int motors) {
        double base = sanitize(configuredOps);
        double cap = Math.max(1.0D, sanitize(hardCap));
        double levels = stats == null ? 0.0D : Math.max(0, stats.efficiency());
        double value = base * (1.0D + 0.05D * levels) * motorSpeedMultiplier(motors);
        if (!Double.isFinite(value) || value > cap) {
            return cap;
        }
        return Math.max(0.0D, value);
    }

    /** Speed multiplier of installed motors: 1.0 with none, about 2.9 with a full stack. */
    public static double motorSpeedMultiplier(int motors) {
        int used = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        return 1.0D + 0.03D * used;
    }

    /**
     * Ore blocks recovered from one mined block when Silk Touch is installed.
     *
     * <p>Fortune does not fight Silk Touch in this machine: it raises the number of whole ore
     * blocks, one extra block per two merged Fortune levels. Both enchantments therefore stay
     * meaningful when they are installed together, and the machine never both drops the block and
     * its raw contents (that would be a double drop of the same ore).</p>
     */
    public static int silkBlockCount(PickaxeStats stats) {
        int fortune = stats == null ? 0 : Math.max(0, stats.fortune());
        long count = 1L + fortune / 2L;
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /**
     * Multiplier of the ordinary (non Silk Touch) drops: {@code 1 + merged Fortune levels}.
     *
     * <p>The machine cannot use vanilla's random bonus roll because a single tick must stay
     * deterministic and testable; the expected value of the vanilla roll is reproduced with a fixed
     * multiplier instead. The multiplier is unbounded and grows with every merged level.</p>
     */
    public static int rawDropMultiplier(PickaxeStats stats) {
        int fortune = stats == null ? 0 : Math.max(0, stats.fortune());
        return (int) Math.min(Integer.MAX_VALUE, 1L + fortune);
    }

    /** Multiplies an item count with saturation; used for the raw drop multiplier. */
    public static int saturatedCount(int count, int multiplier) {
        if (count <= 0 || multiplier <= 0) {
            return 0;
        }
        long product = (long) count * multiplier;
        return (int) Math.min(Integer.MAX_VALUE, product);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
