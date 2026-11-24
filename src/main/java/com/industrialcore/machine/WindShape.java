package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the wind generator model in model pixels (Phase 74.2).
 *
 * <p>Authored north-facing with the rotor on {@code -Z}, matching {@code MachineShapes} and the
 * port tables. Pure Java without Minecraft types so unit tests can pin the model bounds against
 * these six disjoint boxes.</p>
 */
public final class WindShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Base anchors, low-profile inverter cabinet, rear power riser, and lower pylon mast. */
    public static final Box CELL = new Box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    /** Upper yaw ring, turbine nacelle, anemometer, and rear vertical tail fin. */
    public static final Box NACELLE = new Box(4.5, 16.0, 0.0, 11.5, 23.0, 15.5);
    /** Nose spinner cone protruding to z = -2.0. */
    public static final Box SPINNER = new Box(6.5, 16.5, -2.0, 9.5, 19.5, -1.0);
    /** Central rotor hub and vertical up/down blades (y = 12.0..24.0). */
    public static final Box BLADE_VERT = new Box(6.5, 12.0, -1.0, 9.5, 24.0, 0.0);
    /** Horizontal west blade reaching x = 2.0. */
    public static final Box BLADE_WEST = new Box(2.0, 16.5, -1.0, 6.5, 19.5, 0.0);
    /** Horizontal east blade reaching x = 14.0. */
    public static final Box BLADE_EAST = new Box(9.5, 16.5, -1.0, 14.0, 19.5, 0.0);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(
            CELL, NACELLE, SPINNER, BLADE_VERT, BLADE_WEST, BLADE_EAST);

    private WindShape() {
    }
}
