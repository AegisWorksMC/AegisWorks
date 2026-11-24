package com.industrialcore.energy;

/**
 * Balance values of the ten power devices (Phase 33).
 *
 * <p>Every value here obeys one rule: a device never creates energy. Fuel is valued at
 * {@link #FUEL_FE_PER_BURN_TICK} FE per vanilla burn tick (one coal = 1,600 x 40 = 64,000 FE,
 * exactly what the original Fuel Generator produces from one coal), and only
 * {@link #DIRECT_PERCENT}% of that content becomes direct FE; the remaining
 * {@link #BYPRODUCT_PERCENT}% becomes exhaust pressure or waste heat, which the recovery
 * devices convert at {@link #RECOVERY_CONVERSION_PERCENT}% - so the sum can never exceed the
 * fuel's content. Machine byproducts are percentages of FE a machine already spent, so heat and
 * regeneration together always return less than the machine consumed.</p>
 */
public final class GenerationBalance {
    /** FE one vanilla furnace burn tick is worth. Coal (1,600 ticks) = 64,000 FE. */
    public static final int FUEL_FE_PER_BURN_TICK = 40;

    /** Percent of a fuel's content that becomes direct FE while it burns. */
    public static final int DIRECT_PERCENT = 90;

    /** Percent of a fuel's content pooled as exhaust pressure / waste heat for recovery. */
    public static final int BYPRODUCT_PERCENT = 10;

    /** Percent of a pooled exhaust/heat amount a recovery device converts into FE. */
    public static final int RECOVERY_CONVERSION_PERCENT = 85;

    /** Percent of a machine's FE spend credited as heat while it works. */
    public static final int MACHINE_HEAT_PERCENT = 15;

    /** Percent of a machine's FE spend credited when it stops. */
    public static final int REGEN_PERCENT = 5;

    /** Percent of incoming FE a flywheel keeps (the missing 5% is friction loss). */
    public static final int FLYWHEEL_STORE_PERCENT = 95;

    /** FE content of one fuel can (the liquid fuel of the combustion generator). */
    public static final int FUEL_CAN_FE = 160_000;

    /**
     * FE content of one fuel rod (the nuclear generator's crafted fuel, Phase 34.1).
     * The rod's recipe (2 copper + 1 iron) costs about half a fuel can (6 copper + 1 iron),
     * so it holds half the content: 80,000 FE = 72,000 direct FE at the 90% share plus
     * 8,000 exhaust units for the pressure recovery. In the nuclear generator the content
     * lasts {@code 72,000 / 400 = 180} burn ticks - burn length stays derived through
     * {@link #burnTicksFor}, never hardcoded.
     */
    public static final int FUEL_ROD_FE = 80_000;

    /**
     * Phase 105.6: FE content of one processed Uranium Fuel Rod ({@code uranium_fuel_rod}),
     * the flagship primary fuel of the Nuclear Generator multiblock.
     * Holds 800,000 FE = 720,000 direct FE (1,800 ticks = 90s at base 400 FE/t, scaling further
     * with multiblock coolant blocks) plus 80,000 byproduct units.
     */
    public static final int URANIUM_FUEL_ROD_FE = 800_000;

    /**
     * Wind turbine output with one / all fifteen air blocks open above it. A blocked turbine (no
     * open block above) produces nothing at all: Phase 42 made an enclosed placement actually
     * stop the device instead of paying the old 18 FE/t floor.
     */
    public static final int WIND_MIN_OUTPUT = 18;
    public static final int WIND_MAX_OUTPUT = 70;
    public static final int WIND_AIR_COLUMNS = 15;

    /** Ticks between two water/daylight re-checks (cheap periodic validation). */
    public static final int CONDITION_RECHECK_TICKS = 20;
    /** Ticks between two sky-accessibility re-checks. */
    public static final int SKY_RECHECK_TICKS = 100;

    /** Cap of the heat pool one heat recovery device holds (excess heat dissipates). */
    public static final long HEAT_POOL_CAP = 100_000L;

    private GenerationBalance() {
    }

    /**
     * Wind output for {@code airAbove} open blocks in the fifteen scanned above the turbine.
     * Zero open blocks mean zero output (a buried or roofed turbine does not turn); every open
     * block from the first up scales the output between {@link #WIND_MIN_OUTPUT} and
     * {@link #WIND_MAX_OUTPUT}.
     */
    public static int windOutput(int airAbove) {
        int open = Math.max(0, Math.min(WIND_AIR_COLUMNS, airAbove));
        if (open == 0) {
            return 0;
        }
        return WIND_MIN_OUTPUT
                + (WIND_MAX_OUTPUT - WIND_MIN_OUTPUT) * open / WIND_AIR_COLUMNS;
    }

    /** Burn ticks one fuel lasts in a fuel generator with the given rated output. */
    public static int burnTicksFor(int contentFe, int ratedOutput) {
        if (ratedOutput <= 0 || contentFe <= 0) {
            return 0;
        }
        long direct = (long) contentFe * DIRECT_PERCENT / 100;
        long ticks = direct / ratedOutput;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, ticks));
    }

    /** Exhaust/heat units one fuel's content pools for the recovery devices. */
    public static long byproductFor(int contentFe) {
        return (long) Math.max(0, contentFe) * BYPRODUCT_PERCENT / 100;
    }
}