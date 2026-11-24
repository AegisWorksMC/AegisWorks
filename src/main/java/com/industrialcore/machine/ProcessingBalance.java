package com.industrialcore.machine;

/**
 * Balance constants of the Phase 7 processing machines.
 *
 * <p>All values are FE or ticks and can be unit tested without a world. The defaults are exposed
 * through {@link com.industrialcore.Config} so a pack can tune them, but the numbers used by the
 * code default to these constants (single source of truth).</p>
 *
 * <p>Reference points of the existing machines: a fuel generator produces 40 FE/tick, a Basic
 * cable moves 128 FE/tick, the quarry spends 100 FE per mined block and the auto crafter spends
 * 120 FE per craft. One crushed ore therefore costs 1,200 FE (crusher) plus 2 × 800 FE (two
 * smelts) = 2,800 FE for two ingots, against 100 FE for the single vanilla ingot of the same ore.
 * That is a deliberate, documented 2x yield for iron, copper and gold only.</p>
 */
public final class ProcessingBalance {
    /** Ticks one crushing operation takes at the default recipe settings. */
    public static final int CRUSHER_TIME_TICKS = 120;
    /** FE per tick while crushing. */
    public static final int CRUSHER_ENERGY_PER_TICK = 10;
    /** Ticks a vanilla furnace needs for one smelt (used to prove the speed-up). */
    public static final int VANILLA_FURNACE_TIME_TICKS = 200;
    /** Default speed of the electric furnace as a percentage of the vanilla cooking time. */
    public static final int FURNACE_SPEED_PERCENT = 50;
    /** FE per tick while smelting. */
    public static final int FURNACE_ENERGY_PER_TICK = 8;
    /** Internal FE buffer of both machines. */
    public static final int MACHINE_CAPACITY = 20_000;
    /** Maximum FE accepted per tick from generators/cables. */
    public static final int MACHINE_MAX_RECEIVE = 500;
    /** Backoff before an idle machine re-checks its recipe (missing recipe / blocked output). */
    public static final int RETRY_INTERVAL_TICKS = 10;

    // ------------------------------------------------------------------
    // Phase 12 machine defaults, still used by the electrolyzer and the
    // precision crusher themselves (the 3x/6x cost helpers are gone).
    // ------------------------------------------------------------------

    /**
     * Ticks one electrolysis run takes when a recipe omits its own time. The electrolyzer splits a
     * dust into the pure ore of the Phase 31 ore line, so it is deliberately slower than plain
     * crushing.
     */
    public static final int ELECTROLYZER_TIME_TICKS = 240;
    /** FE per tick of the electrolyzer when a recipe omits its own value. */
    public static final int ELECTROLYZER_ENERGY_PER_TICK = 20;
    /** Ticks one precision crushing run takes (pure ore -> exposed ore). */
    public static final int PRECISION_TIME_TICKS = 200;
    /** FE per tick of the precision crusher (3,000 FE per run at the default time). */
    public static final int PRECISION_ENERGY_PER_TICK = 15;

    // ------------------------------------------------------------------
    // Phase 23: fast crusher (batch processing)
    // ------------------------------------------------------------------

    /**
     * Items one fast crusher cycle processes at most. One cycle takes the crushing recipe's own
     * time (10 ticks for the ore-line raw entrance, 120 ticks for the ore-block crush recipes) and
     * draws the recipe's FE per tick multiplied by
     * the batch: eight raw iron cost 800 FE per cycle = 100 FE per item, exactly like the
     * normal crusher, but at 80 FE/t instead of 10 FE/t. The throughput scales with the batch;
     * the FE per item never changes, so the machine is an investment in speed and power
     * infrastructure, not in free energy.
     */
    public static final int FAST_CRUSH_BATCH = 8;

    // ------------------------------------------------------------------
    // Phase 17 machine parts line: metal press
    // ------------------------------------------------------------------

    /**
     * Ticks one pressing run takes at the default recipe settings. A recipe may override it;
     * the shipped plate recipes use this number (480 FE per plate).
     */
    public static final int METAL_PRESS_TIME_TICKS = 60;
    /** FE per tick of the metal press (480 FE per plate at the default time). */
    public static final int METAL_PRESS_ENERGY_PER_TICK = 8;
    /** Ticks one pressing run of an alloy plate (steel / brass) takes. */
    public static final int ALLOY_PRESS_TIME_TICKS = 80;
    /** FE per tick when pressing an alloy plate (800 FE per plate). */
    public static final int ALLOY_PRESS_ENERGY_PER_TICK = 10;

    /**
     * Total FE of the 2x line: 1,200 (crush) + 2 x 800 (smelt) = 2,800 FE for two ingots.
     */
    public static long twoTimesCost() {
        return totalEnergy(CRUSHER_TIME_TICKS, CRUSHER_ENERGY_PER_TICK)
                + 2L * totalEnergy(furnaceTime(VANILLA_FURNACE_TIME_TICKS, FURNACE_SPEED_PERCENT),
                FURNACE_ENERGY_PER_TICK);
    }

    private ProcessingBalance() {}

    /**
     * Cooking time of the electric furnace for a vanilla recipe.
     *
     * @param vanillaTicks the {@code cookingtime} of the smelting recipe (200 for vanilla)
     * @param speedPercent configured speed as a percentage; 50 means twice as fast
     */
    public static int furnaceTime(int vanillaTicks, int speedPercent) {
        int base = Math.max(1, vanillaTicks);
        int percent = Math.max(1, Math.min(1000, speedPercent));
        long scaled = (long) base * percent;
        return (int) Math.max(1L, (scaled + 99L) / 100L);
    }

    /** Total FE one operation costs. */
    public static long totalEnergy(int timeTicks, int energyPerTick) {
        return (long) Math.max(0, timeTicks) * Math.max(0, energyPerTick);
    }

    /** Speed multiplier relative to a vanilla furnace (2.0 means twice as fast). */
    public static double speedMultiplier(int vanillaTicks, int machineTicks) {
        if (machineTicks <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, vanillaTicks) / (double) machineTicks;
    }

    /** True when the machine is faster than a vanilla furnace for the same recipe. */
    public static boolean isFasterThanVanillaFurnace(int machineTicks) {
        return machineTicks > 0 && machineTicks < VANILLA_FURNACE_TIME_TICKS;
    }
}
