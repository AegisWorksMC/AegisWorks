package com.industrialcore.machine.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The port table of one machine, authored in model space and queried for a placed facing.
 *
 * <p>A machine has one layout; {@link #portOn(PortFacing, PortSide)} rotates the table on the fly
 * instead of keeping four copies, so a new facing can never be forgotten. Only one port per face
 * is allowed: two sockets on one face would overlap, and the pipe/cable arm still arrives at the
 * face centre, so the ambiguity is rejected at construction.</p>
 *
 * <p>The class is pure Java: the block registry only stores layouts, and the whole rotation and
 * lookup table is covered by unit tests.</p>
 */
public final class PortLayout {
    private static final PortLayout EMPTY = new PortLayout(List.of());

    private final List<PortSpec> specs;
    private final Map<PortSide, PortSpec> bySide = new EnumMap<>(PortSide.class);

    public PortLayout(List<PortSpec> specs) {
        List<PortSpec> copy = List.copyOf(specs);
        for (PortSpec spec : copy) {
            PortSpec previous = bySide.put(spec.side(), spec);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate port on " + spec.side()
                        + ": " + previous + " and " + spec);
            }
        }
        this.specs = copy;
    }

    /** A layout without ports; used for machines that are still unported. */
    public static PortLayout empty() {
        return EMPTY;
    }

    /** Every port, in model space. */
    public List<PortSpec> ports() {
        return specs;
    }

    /** Every defined face, in model space. */
    public java.util.Set<PortSide> sides() {
        return Collections.unmodifiableSet(bySide.keySet());
    }

    public boolean isEmpty() {
        return specs.isEmpty();
    }

    /**
     * Finds the port that ends up on {@code worldSide} when the machine is placed with
     * {@code facing}.
     *
     * @return the port, or {@code null} if that world face has none
     */
    public PortSpec portOn(PortFacing facing, PortSide worldSide) {
        if (facing == null || worldSide == null) {
            return null;
        }
        for (PortSpec spec : specs) {
            if (spec.worldSide(facing) == worldSide) {
                return spec;
            }
        }
        return null;
    }

    /** The same table rotated into world space, mostly for diagnostics and logging. */
    public List<PortSpec> rotated(PortFacing facing) {
        List<PortSpec> result = new ArrayList<>(specs.size());
        for (PortSpec spec : specs) {
            result.add(spec.withSide(spec.worldSide(facing)));
        }
        return result;
    }

    /**
     * Centre of the port on {@code worldSide} in block units (0..1).
     *
     * @return {@code {x, y, z}}, or {@code null} if that world face has no port
     */
    public double[] centerOn(PortFacing facing, PortSide worldSide) {
        PortSpec spec = portOn(facing, worldSide);
        return spec == null ? null : spec.centerRotated(facing);
    }
}
