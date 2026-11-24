package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the pressure recovery device model in model pixels (Phase 75).
 *
 * <p>Horizontally symmetric (90-degree rotational symmetry around {@code x=8, z=8}) with a
 * primary cell housing and an upper pressurized turbine/scrubber tower reaching {@code y = 20.0 px},
 * matching {@code MachineShapes} and the 4-way horizontal port table. Pure Java without Minecraft
 * types so unit tests can verify the model bounds without booting the game.</p>
 */
public final class PressureShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Primary pressure chamber, anchor base, and corner intake columns. */
    public static final Box CELL = new Box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    /** Upper turbine expansion chamber and scrubber relief tower reaching y = 20.0. */
    public static final Box TOWER = new Box(3.5, 16.0, 3.5, 12.5, 20.0, 12.5);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(CELL, TOWER);

    private PressureShape() {
    }
}
