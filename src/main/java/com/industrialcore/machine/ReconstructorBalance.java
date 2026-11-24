package com.industrialcore.machine;

/**
 * Balance numbers of the reconstructor (Phase 15).
 *
 * <p>The values mirror the design table of the Phase 15 specification. The shipped recipe files
 * carry the same numbers, and the unit test compares both, so a change in one place without the
 * other fails the build.</p>
 */
public final class ReconstructorBalance {
    /** Internal FE buffer of the reconstructor. */
    public static final int CAPACITY = 20_000;
    /** FE accepted per tick from generators and cables. */
    public static final int MAX_RECEIVE = 500;

    /** Processing time of the iron recipe in ticks. */
    public static final int IRON_TIME_TICKS = 200;
    /** FE per tick of the iron recipe (200 x 20 = 4,000 FE in total). */
    public static final int IRON_ENERGY_PER_TICK = 20;

    /** Processing time of the copper recipe in ticks. */
    public static final int COPPER_TIME_TICKS = 200;
    /** FE per tick of the copper recipe (200 x 20 = 4,000 FE in total). */
    public static final int COPPER_ENERGY_PER_TICK = 20;

    /** Processing time of the gold recipe in ticks. */
    public static final int GOLD_TIME_TICKS = 400;
    /** FE per tick of the gold recipe (400 x 30 = 12,000 FE in total). */
    public static final int GOLD_ENERGY_PER_TICK = 30;

    /** Material items consumed per reconstruction (stone or deepslate). */
    public static final int MATERIAL_COUNT = 8;
    /** Crystals consumed per reconstruction. */
    public static final int CRYSTAL_COUNT = 1;

    /** Total FE of one reconstruction. */
    public static int totalEnergy(int timeTicks, int energyPerTick) {
        return Math.max(0, timeTicks) * Math.max(0, energyPerTick);
    }

    private ReconstructorBalance() {}
}
