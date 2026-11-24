package com.industrialcore.machine;

/**
 * Balance constants and formulas of the Phase 21 seed line (seed cultivator + matter amplifier).
 *
 * <p>Everything here is free of Minecraft classes so a plain unit test can pin the numbers that
 * the shipped recipe files must match. The two machines are separate block entities, but their
 * formulas belong together because the 12x / 16x totals are built from both.</p>
 *
 * <h2>Batch arithmetic of the cultivator</h2>
 * <p>One billet costs {@link #FEED_PER_UNIT} dust of feed and {@link #CULTIVATOR_ENERGY_PER_TICK}
 * FE per tick for {@link #CULTIVATOR_TIME_TICKS} ticks (24,000 FE). A batch of {@code N} billets
 * costs {@code 4N} feed, {@code 24,000 x N} FE and takes {@code 600 + 150 x (N - 1)} ticks, so the
 * average FE per tick rises with the batch but never reaches 160
 * ({@link #requiredFePerTick(int, int)} of the largest batch is 153).</p>
 *
 * <p>Because one batch consumes {@code 4N} feed while one slot holds 64 items, the largest batch
 * a single stack can pay for is {@code 16}. {@link #MAX_BATCH} stays 64 so the formula (and the
 * design table) keeps its documented upper bound; the machine clamps to what the slot really
 * holds. See {@code docs/PHASE21_SEED_AMPLIFIER.md} §2.2.</p>
 *
 * <h2>Line totals</h2>
 * <ul>
 *   <li>12x (seed to 12 ingots): 57,200 FE / 3,260 ticks.</li>
 *   <li>16x (seed to 16 ingots, through the amplifier): 132,400 FE / 4,960 ticks.</li>
 *   <li>16x from a billet (no cultivation): 108,400 FE / 4,360 ticks.</li>
 * </ul>
 * <p>All three are sums of the shipped recipe numbers, not free parameters; the unit test
 * re-reads the JSON and fails when a recipe drifts.</p>
 */
public final class SeedLineBalance {
    // ------------------------------------------------------------------
    // Seed cultivator
    // ------------------------------------------------------------------

    /** Ticks one billet takes at the default recipe settings (N = 1). */
    public static final int CULTIVATOR_TIME_TICKS = 600;
    /** FE per tick of the cultivator (24,000 FE per billet at the default time). */
    public static final int CULTIVATOR_ENERGY_PER_TICK = 40;
    /** Dust consumed per billet; a batch of N consumes 4N in four instalments of N. */
    public static final int FEED_PER_UNIT = 4;
    /** Largest batch the formula supports (a single stack can pay for 16, see the class comment). */
    public static final int MAX_BATCH = 64;
    /** Instalments of feed one batch is paid in (start, 25%, 50%, 75%). */
    public static final int FEED_INSTALMENTS = 4;

    /** Total FE of one billet at the default settings. */
    public static final long CULTIVATOR_ENERGY_PER_BILLET =
            (long) CULTIVATOR_TIME_TICKS * CULTIVATOR_ENERGY_PER_TICK;

    // ------------------------------------------------------------------
    // Matter amplifier
    // ------------------------------------------------------------------

    /** Ticks one amplification run takes. */
    public static final int AMPLIFIER_TIME_TICKS = 1200;
    /** FE per tick of the amplifier (72,000 FE per run). */
    public static final int AMPLIFIER_ENERGY_PER_TICK = 60;
    /** Ore blocks one run produces. */
    public static final int AMPLIFIER_ORE_COUNT = 2;
    /** Dust one run produces as a by-product. */
    public static final int AMPLIFIER_DUST_COUNT = 4;
    /** Total FE of one amplification run. */
    public static final long AMPLIFIER_ENERGY_PER_RUN =
            (long) AMPLIFIER_TIME_TICKS * AMPLIFIER_ENERGY_PER_TICK;

    // ------------------------------------------------------------------
    // Shared machine shell
    // ------------------------------------------------------------------

    /** Internal FE buffer of both machines. */
    public static final int MACHINE_CAPACITY = 20_000;
    /** Maximum FE accepted per tick. The largest batch asks for 153, so this is never the limit. */
    public static final int MACHINE_MAX_RECEIVE = 500;

    // ------------------------------------------------------------------
    // Downstream recipe numbers the line totals are built from
    // ------------------------------------------------------------------

    /** Billet -> exposed ore: the recipe added to the existing precision crusher. */
    public static final int BILLET_PRECISION_TIME_TICKS = 300;
    public static final int BILLET_PRECISION_ENERGY_PER_TICK = 20;
    /** Exposed ore -> 2 pure ore (shipped Phase 12 recipe). */
    public static final int EXPOSED_ELECTROLYSIS_PER_RUN = 6_000;
    /** Pure ore -> 3 dust (shipped Phase 12 recipe). */
    public static final int PURE_CRUSH_PER_RUN = 1_400;
    /** Dust -> ingot in the electric furnace (100 ticks at 8 FE). */
    public static final int SMELT_PER_INGOT = 800;
    /** Ore block -> exposed ore (shipped Phase 12 recipe, 200 ticks at 15 FE). */
    public static final int ORE_PRECISION_PER_RUN = 3_000;

    private SeedLineBalance() {}

    /**
     * Ticks a batch of {@code count} billets takes.
     *
     * <p>{@code time + (time / 4) x (count - 1)}, clamped to {@code 1..}{@link #MAX_BATCH}. The
     * per-billet share falls from 600 to 157 ticks, which is the whole point of batching.</p>
     */
    public static int batchTime(int baseTime, int count) {
        int batch = clampBatch(count);
        int base = Math.max(1, baseTime);
        return base + (base / 4) * (batch - 1);
    }

    /** Ticks a batch of {@code count} billets takes at the default settings. */
    public static int billetTime(int count) {
        return batchTime(CULTIVATOR_TIME_TICKS, count);
    }

    /**
     * Total FE a batch of {@code count} billets costs.
     *
     * <p>{@code energy x baseTime x count}; the whole batch is linear in FE, so batching never
     * wastes or saves energy. Returned as {@code long} because a large batch would overflow an
     * {@code int} if the formula were ever raised.</p>
     */
    public static long batchEnergy(int baseTime, int energyPerTick, int count) {
        return (long) Math.max(1, baseTime) * Math.max(0, energyPerTick) * clampBatch(count);
    }

    /** Total FE a batch of {@code count} billets costs at the default settings. */
    public static long billetEnergy(int count) {
        return batchEnergy(CULTIVATOR_TIME_TICKS, CULTIVATOR_ENERGY_PER_TICK, count);
    }

    /** Feed dust a batch of {@code count} billets consumes in total. */
    public static int feedFor(int count) {
        return clampBatch(count) * FEED_PER_UNIT;
    }

    /**
     * FE per tick needed to finish a batch with the remaining energy in the remaining ticks.
     *
     * <p>Rounded up, so the run never finishes short of its documented total. This is what the
     * block entity asks the buffer for every tick; the value stays below 160 for every batch.</p>
     */
    public static int requiredFePerTick(long remainingEnergy, int remainingTicks) {
        if (remainingEnergy <= 0) {
            return 0;
        }
        int ticks = Math.max(1, remainingTicks);
        long cost = (remainingEnergy + ticks - 1L) / ticks;
        return (int) Math.min(Integer.MAX_VALUE, cost);
    }

    /** Largest batch a feed stack of {@code items} can pay for (dust / 4, at least 1). */
    public static int batchFromFeed(int items) {
        return clampBatch(Math.max(1, items / FEED_PER_UNIT));
    }

    /** Clamps a batch size into {@code 1..}{@link #MAX_BATCH}. */
    public static int clampBatch(int count) {
        return Math.max(1, Math.min(MAX_BATCH, count));
    }

    // ------------------------------------------------------------------
    // Line totals
    // ------------------------------------------------------------------

    /**
     * Total FE of the 12x line, starting from one seed and four dust:
     * cultivator (24,000) + billet precision crush (6,000) + two exposed electrolyses (12,000) +
     * four pure crushes (5,600) + twelve smelts (9,600) = 57,200 FE for twelve ingots.
     */
    public static long twelveTimesCost() {
        return CULTIVATOR_ENERGY_PER_BILLET
                + (long) BILLET_PRECISION_TIME_TICKS * BILLET_PRECISION_ENERGY_PER_TICK
                + 2L * EXPOSED_ELECTROLYSIS_PER_RUN
                + 4L * PURE_CRUSH_PER_RUN
                + 12L * SMELT_PER_INGOT;
    }

    /** Ticks of the 12x line (serial): 600 + 300 + 2 x 300 + 4 x 140 + 12 x 100 = 3,260. */
    public static int twelveTimesTicks() {
        return CULTIVATOR_TIME_TICKS + BILLET_PRECISION_TIME_TICKS + 2 * 300 + 4 * 140 + 12 * 100;
    }

    /**
     * Total FE of the 16x line, starting from one seed:
     * cultivator (24,000) + amplifier (72,000) + two ore precision crushes (6,000) + two exposed
     * electrolyses (12,000) + four pure crushes (5,600) + sixteen smelts (12,800)
     * = 132,400 FE for sixteen ingots.
     */
    public static long sixteenTimesCost() {
        return CULTIVATOR_ENERGY_PER_BILLET
                + AMPLIFIER_ENERGY_PER_RUN
                + 2L * ORE_PRECISION_PER_RUN
                + 2L * EXPOSED_ELECTROLYSIS_PER_RUN
                + 4L * PURE_CRUSH_PER_RUN
                + 16L * SMELT_PER_INGOT;
    }

    /** Ticks of the 16x line (serial): 600 + 1,200 + 2 x 200 + 2 x 300 + 4 x 140 + 16 x 100. */
    public static int sixteenTimesTicks() {
        return CULTIVATOR_TIME_TICKS + AMPLIFIER_TIME_TICKS + 2 * 200 + 2 * 300 + 4 * 140 + 16 * 100;
    }

    /** Total FE of the same 16x line when it starts from a billet (no cultivation). */
    public static long sixteenTimesFromBilletCost() {
        return sixteenTimesCost() - CULTIVATOR_ENERGY_PER_BILLET;
    }

    /** Ticks of the 16x line when it starts from a billet. */
    public static int sixteenTimesFromBilletTicks() {
        return sixteenTimesTicks() - CULTIVATOR_TIME_TICKS;
    }

    /** Ingots the 12x line produces from one seed. */
    public static int twelveTimesIngots() {
        return 12;
    }

    /** Ingots the 16x line produces from one seed. */
    public static int sixteenTimesIngots() {
        return 16;
    }
}
