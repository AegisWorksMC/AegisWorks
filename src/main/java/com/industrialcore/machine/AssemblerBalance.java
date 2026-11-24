package com.industrialcore.machine;

/**
 * Balance constants of the Phase 18 assembler.
 *
 * <p>An assembly recipe carries its own duration and FE per tick; these constants are the defaults
 * for a datapack recipe that omits them and the values the shipped recipes are checked against.</p>
 *
 * <p>Reference points: the metal press spends 480 FE per plate and the alloy furnace burns fuel
 * instead of FE. The assembler turns three parts into a higher part, so one assembly run is
 * deliberately more expensive than pressing a single plate.</p>
 */
public final class AssemblerBalance {
    /** Default duration of one assembly run; a recipe may override it. */
    public static final int TIME_TICKS = 120;
    /** Default FE per tick of the assembler; a recipe may override it. */
    public static final int ENERGY_PER_TICK = 10;
    /** Internal FE buffer (same as the other processing machines). */
    public static final int CAPACITY = ProcessingBalance.MACHINE_CAPACITY;
    /** Maximum FE accepted per tick (same as the other processing machines). */
    public static final int MAX_RECEIVE = ProcessingBalance.MACHINE_MAX_RECEIVE;
    /** Backoff before an idle assembler re-checks its recipes. */
    public static final int RETRY_INTERVAL_TICKS = ProcessingBalance.RETRY_INTERVAL_TICKS;

    /** Duration of the electronic component recipe (Phase 18). */
    public static final int COMPONENT_TIME_TICKS = 120;
    /** FE per tick of the electronic component recipe (1,200 FE per run). */
    public static final int COMPONENT_ENERGY_PER_TICK = 10;
    /** Duration of the reinforced machine frame recipe. */
    public static final int REINFORCED_FRAME_TIME_TICKS = 200;
    /** FE per tick of the reinforced machine frame recipe (3,000 FE per run). */
    public static final int REINFORCED_FRAME_ENERGY_PER_TICK = 15;

    private AssemblerBalance() {}
}
