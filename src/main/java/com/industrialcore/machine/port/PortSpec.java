package com.industrialcore.machine.port;

/**
 * Definition of one connection port of a machine, in model space (see {@link PortSide}).
 *
 * <p>A port lives on a face, may be shifted inside that face by {@code u}/{@code v} pixels, has a
 * socket radius and may stick out of the machine body. {@code u} is "to the right when looking at
 * the face from outside", {@code v} is "up"; both are measured from the face centre, so
 * {@code u = 0, v = 0} is the plain centre that a pipe or cable arm arrives at.</p>
 *
 * <p>The specification is immutable and validated on construction: a socket that leaves its face,
 * a non-positive radius or an offset outside the block is rejected instead of being drawn in a
 * broken position. The class holds no Minecraft types, so the validation and the geometry are
 * unit tested.</p>
 *
 * @param side       face in model space
 * @param u          horizontal offset inside the face, in pixels, negative = left
 * @param v          vertical offset inside the face, in pixels, negative = down
 * @param radius     socket radius in pixels; must be greater than 0
 * @param protrusion how far the socket sticks out of the machine body, in pixels (0..1.5)
 * @param kind       connector family this port accepts
 */
public record PortSpec(PortSide side, double u, double v, double radius, double protrusion, PortKind kind) {
    /** Half the size of a block face, in model pixels. */
    public static final double HALF_FACE = 8.0;
    /** A socket may not reach past the edge of its face. */
    public static final double MAX_RADIUS = 7.0;
    /** The models keep an 8 px overhang budget; a port never exceeds it. */
    public static final double MAX_PROTRUSION = 1.5;

    public PortSpec {
        if (side == null) {
            throw new IllegalArgumentException("port side is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("port kind is required");
        }
        if (!Double.isFinite(u) || !Double.isFinite(v) || !Double.isFinite(radius)
                || !Double.isFinite(protrusion)) {
            throw new IllegalArgumentException("port geometry must be finite: " + side);
        }
        if (radius <= 0.0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("port radius out of range on " + side + ": " + radius);
        }
        if (protrusion < 0.0 || protrusion > MAX_PROTRUSION) {
            throw new IllegalArgumentException("port protrusion out of range on " + side + ": " + protrusion);
        }
        if (Math.abs(u) + radius > HALF_FACE + 0.5 || Math.abs(v) + radius > HALF_FACE + 0.5) {
            throw new IllegalArgumentException("port socket leaves its face on " + side
                    + ": u=" + u + " v=" + v + " r=" + radius);
        }
    }

    /** A centred port with the given kind and a 3.5 px socket, protruding 1 px. */
    public static PortSpec centred(PortSide side, PortKind kind) {
        return new PortSpec(side, 0.0, 0.0, 3.5, 1.0, kind);
    }

    /** The same geometry on another face; used when the block is rotated. */
    public PortSpec withSide(PortSide newSide) {
        return new PortSpec(newSide, u, v, radius, protrusion, kind);
    }

    /**
     * Centre of the port in model space, in pixels (0..16).
     *
     * @return {@code {x, y, z}}
     */
    public double[] centerPx() {
        double a = HALF_FACE + u;
        double b = HALF_FACE + v;
        return switch (side) {
            case NORTH -> new double[]{a, b, 0.0};
            case SOUTH -> new double[]{16.0 - a, b, 16.0};
            case EAST -> new double[]{16.0, b, 16.0 - a};
            case WEST -> new double[]{0.0, b, a};
            case UP -> new double[]{a, 16.0, b};
            case DOWN -> new double[]{a, 0.0, 16.0 - b};
        };
    }

    /** Centre of the port in block units (0..1), rotated by {@code facing}. */
    public double[] centerRotated(PortFacing facing) {
        return centerInto(facing, new double[3]);
    }

    /**
     * Centre of the port in block units (0..1), rotated by {@code facing}, written into
     * {@code out}. The render path uses this so a port can be resolved without allocating.
     *
     * @return {@code out}, for chaining
     */
    public double[] centerInto(PortFacing facing, double[] out) {
        double a = (HALF_FACE + u) / 16.0;
        double b = (HALF_FACE + v) / 16.0;
        double x;
        double y;
        double z;
        switch (side) {
            case NORTH -> {
                x = a;
                y = b;
                z = 0.0;
            }
            case SOUTH -> {
                x = 1.0 - a;
                y = b;
                z = 1.0;
            }
            case EAST -> {
                x = 1.0;
                y = b;
                z = 1.0 - a;
            }
            case WEST -> {
                x = 0.0;
                y = b;
                z = a;
            }
            case UP -> {
                x = a;
                y = 1.0;
                z = b;
            }
            default -> {
                x = a;
                y = 0.0;
                z = 1.0 - b;
            }
        }
        return facing.rotateInto(x, y, z, out);
    }

    /** The face this port ends up on once the machine is placed with {@code facing}. */
    public PortSide worldSide(PortFacing facing) {
        return side.rotate(facing);
    }
}
