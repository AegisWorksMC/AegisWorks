package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the regenerative generator model in model pixels (Phase 75).
 *
 * <p>Compact, low-profile horizontally symmetric chassis (max height {@code y = 14.0 px}) with a
 * stepped upper toroidal capacitor crown, replacing the placeholder vanilla orientable cube. Pure
 * Java without Minecraft types.</p>
 */
public final class RegenShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Lower chassis, 4 corner electromagnetic surge inductors, and terminal plates. */
    public static final Box BASE = new Box(0.0, 0.0, 0.0, 16.0, 11.5, 16.0);
    /** Upper toroidal regenerative capacitor crown reaching y = 14.0. */
    public static final Box COIL = new Box(2.5, 11.5, 2.5, 13.5, 14.0, 13.5);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(BASE, COIL);

    private RegenShape() {
    }
}
