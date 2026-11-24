package com.industrialcore.machine.port;

/**
 * Horizontal facing of a placed machine, matching the blockstate's {@code y} rotation.
 *
 * <p>{@link #rotate(double, double, double)} uses exactly the transform of
 * {@code MachineShapes.rotate}, so a port, the collision boxes and the drawn model can never
 * disagree about where "the front" is.</p>
 */
public enum PortFacing {
    NORTH(0),
    EAST(1),
    SOUTH(2),
    WEST(3);

    private final int steps;

    PortFacing(int steps) {
        this.steps = steps;
    }

    /** Number of 90 degree clockwise turns applied to a north-facing model. */
    public int steps() {
        return steps;
    }

    /**
     * Rotates a point given in model space (0..1 on each axis) about the block centre.
     *
     * @return a new array {@code {x, y, z}}; the input is not modified
     */
    public double[] rotate(double x, double y, double z) {
        return rotateInto(x, y, z, new double[3]);
    }

    /**
     * Same transform as {@link #rotate(double, double, double)}, writing into {@code out} so the
     * render path can call it every frame without allocating.
     *
     * @return {@code out}, for chaining
     */
    public double[] rotateInto(double x, double y, double z, double[] out) {
        switch (this) {
            case EAST -> {
                out[0] = 1.0 - z;
                out[1] = y;
                out[2] = x;
            }
            case SOUTH -> {
                out[0] = 1.0 - x;
                out[1] = y;
                out[2] = 1.0 - z;
            }
            case WEST -> {
                out[0] = z;
                out[1] = y;
                out[2] = 1.0 - x;
            }
            case NORTH -> {
                out[0] = x;
                out[1] = y;
                out[2] = z;
            }
        }
        return out;
    }

    public PortSide rotate(PortSide side) {
        return side.rotate(this);
    }
}
