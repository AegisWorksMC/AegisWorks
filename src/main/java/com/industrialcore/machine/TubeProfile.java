package com.industrialcore.machine;

/**
 * One place for the physical size of the item pipes and energy cables.
 *
 * <p>The tube and the junction core are derived from the tier here, so the baked block models
 * ({@code docs/model_tools/gen_pipes.py}), the collision and selection shapes
 * ({@link com.industrialcore.block.ItemPipeBlock}, {@link com.industrialcore.block.CableBlock}) and
 * the connector renderers cannot drift apart. {@code PipeModelProfileTest} checks the generated
 * models against these numbers.</p>
 *
 * <p>All values are full widths in model pixels (1 px = 1/16 block). The arm itself stays a plain
 * tube all the way to the block boundary; every gasket or coupling that meets a neighbour is drawn
 * by {@code com.industrialcore.client.ConnectorJoint}, and only where the neighbour is a machine or
 * an inventory - never between two conduits, whose models already meet seamlessly.</p>
 *
 * <p>Phase 78 made the junction core exactly as wide as the tube ({@link #CORE_OVER_TUBE} = 0.0).
 * The core is now only the twelve corner rails of the junction cell, so a rail of an arm, of the
 * core and of the next arm is the same 0.5 px bar in the same place: a straight run of any length
 * has no step, no ring and no ring-shaped shadow anywhere.</p>
 */
public final class TubeProfile {
    /** Pipe tube width, from {@link PipeTier#BASIC} to {@link PipeTier#ULTIMATE} (golden ratio 5.0 px). */
    private static final double[] PIPE_TUBE = {5.0D, 5.0D, 5.0D, 5.0D};
    /** Cable tube width, from {@link CableTier#BASIC} to {@link CableTier#ULTIMATE} (slim 4.0 px). */
    private static final double[] CABLE_TUBE = {4.0D, 4.0D, 4.0D, 4.0D};

    /**
     * The core at a junction is this much wider than the tube: 0.0 since Phase 78, i.e. the junction
     * keeps the exact tube width (5.0 px pipe / 4.0 px cable) and no longer shows a raised collar.
     */
    public static final double CORE_OVER_TUBE = 0.0D;

    private TubeProfile() {
    }

    public static double pipeTube(PipeTier tier) {
        return PIPE_TUBE[clamp(tier == null ? 0 : tier.ordinal())];
    }

    public static double cableTube(CableTier tier) {
        return CABLE_TUBE[clamp(tier == null ? 0 : tier.ordinal())];
    }

    public static double core(double tube) {
        return tube + CORE_OVER_TUBE;
    }

    /** Half width of a pipe arm in block units, for the connector renderer. */
    public static double armHalf(PipeTier tier) {
        return half(pipeTube(tier));
    }

    /** Half width of a cable arm in block units, for the connector renderer. */
    public static double armHalf(CableTier tier) {
        return half(cableTube(tier));
    }

    /** Converts a model-pixel width into a half width in block units. */
    public static double half(double widthPx) {
        return widthPx / 32.0D;
    }

    private static int clamp(int index) {
        return Math.max(0, Math.min(PIPE_TUBE.length - 1, index));
    }
}
