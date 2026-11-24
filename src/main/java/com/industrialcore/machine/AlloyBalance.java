package com.industrialcore.machine;

/**
 * Balance constants of the Phase 17 alloy furnace.
 *
 * <p>The alloy furnace burns ordinary furnace fuel instead of FE, so it has no energy numbers: a
 * recipe only carries a duration. One unit of work consumes one tick of the burning fuel, which
 * means a coal (1,600 ticks) covers eight steel runs of 200 ticks.</p>
 *
 * <p>Reference points: a fuel generator produces 40 FE/tick and the metal press spends 480 FE per
 * iron plate. The alloy furnace is therefore the early-game machine of the parts line: it works
 * before any cable or generator is built.</p>
 */
public final class AlloyBalance {
    /** Default duration of an alloying run; a recipe may override it. */
    public static final int TIME_TICKS = 200;
    /** Duration of the steel recipe (iron + coal). */
    public static final int STEEL_TIME_TICKS = 200;
    /** Duration of the brass recipe (copper + gold). */
    public static final int BRASS_TIME_TICKS = 160;
    /** Backoff before an idle furnace re-checks its recipes. */
    public static final int RETRY_INTERVAL_TICKS = 10;

    private AlloyBalance() {}
}
