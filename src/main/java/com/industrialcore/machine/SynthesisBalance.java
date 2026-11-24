package com.industrialcore.machine;

/**
 * Balance of the synthetic ore synthesizer.
 *
 * <p>The machine turns the Phase 24 ores into the nine synthetic ores that the high amplifier tiers
 * use as catalysts. Its recipes carry their own time and FE, so those two values are documented
 * here only for the tests and for the JSON the data generation writes.</p>
 *
 * <table>
 *   <caption>Synthesis runs</caption>
 *   <tr><th>Output</th><th>Ticks</th><th>FE/t</th><th>Total FE</th></tr>
 *   <tr><td>Synthetic ore I - VI</td><td>10</td><td>20</td><td>200</td></tr>
 *   <tr><td>High-density synthetic ore</td><td>10</td><td>25</td><td>250</td></tr>
 *   <tr><td>Unstable ultimate synthetic ore</td><td>10</td><td>40</td><td>400</td></tr>
 *   <tr><td>Ultimate synthetic ore</td><td>10</td><td>60</td><td>600</td></tr>
 *   <tr><td>Resonant catalyst (bulk, 4 per craft)</td><td>200</td><td>20</td><td>4,000</td></tr>
 * </table>
 *
 * <p>Every ore-line ladder recipe runs at the unified 10 tick tempo of the line; only the bulk
 * resonant catalyst (a seed-line catalyst, not part of the ore ladder) keeps 200 ticks.</p>
 */
public final class SynthesisBalance {
    /** Internal FE buffer; the machine only draws 20-60 FE/t, so the standard buffer is plenty. */
    public static final int CAPACITY = 40_000;
    /** Default FE accepted per tick; the machine never needs more than 60. */
    public static final int MAX_RECEIVE = 1_000;
    /** Retry backoff of an idle synthesizer (same value as the other machines). */
    public static final int RETRY_INTERVAL_TICKS = ProcessingBalance.RETRY_INTERVAL_TICKS;

    /**
     * Ingredient slots of the machine: a 4x3 grid, because the ultimate synthetic ore lists ten
     * different stacks in one craft.
     */
    public static final int INPUT_SLOTS = 12;

    /** Synthetic ore I - VI: 10 ticks at 20 FE/t. */
    public static final int TIME_TICKS = 10;
    public static final int ENERGY_PER_TICK = 20;

    /** High-density synthetic ore: 10 ticks at 25 FE/t. */
    public static final int HIGH_DENSITY_TIME_TICKS = 10;
    public static final int HIGH_DENSITY_ENERGY_PER_TICK = 25;

    /** Unstable ultimate synthetic ore: 10 ticks at 40 FE/t. */
    public static final int UNSTABLE_TIME_TICKS = 10;
    public static final int UNSTABLE_ENERGY_PER_TICK = 40;

    /** Ultimate synthetic ore: 10 ticks at 60 FE/t. */
    public static final int ULTIMATE_TIME_TICKS = 10;
    public static final int ULTIMATE_ENERGY_PER_TICK = 60;

    /** FE of one run. */
    public static long totalEnergy(int timeTicks, int energyPerTick) {
        return ProcessingBalance.totalEnergy(timeTicks, energyPerTick);
    }

    private SynthesisBalance() {}
}
