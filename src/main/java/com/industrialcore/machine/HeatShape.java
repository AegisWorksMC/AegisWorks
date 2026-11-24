package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the waste-heat recovery device model in model pixels (Phase 75).
 *
 * <p>Horizontally symmetric (90-degree rotational symmetry around {@code x=8, z=8}) with a
 * full-cell finned absorber chassis and an upper thermoelectric converter hood reaching
 * {@code y = 17.5 px}. Pure Java without Minecraft types.</p>
 */
public final class HeatShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Skid base, thermal absorber core, and 4 corner multi-tier heatsink fin pillars. */
    public static final Box CELL = new Box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    /** Upper thermoelectric Peltier converter hood reaching y = 17.5. */
    public static final Box HOOD = new Box(2.5, 16.0, 2.5, 13.5, 17.5, 13.5);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(CELL, HOOD);

    private HeatShape() {
    }
}
