package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the geothermal generator model in model pixels (Phase 74.4).
 *
 * <p>Authored north-facing with the magma heat-exchanger front on {@code -Z} and an upper
 * thermoelectric condenser crown reaching {@code y = 19.0 px}, matching {@code MachineShapes}
 * and the port tables. Pure Java without Minecraft types so unit tests can pin the model
 * bounds against these two disjoint boxes.</p>
 */
public final class GeoShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Primary magma collector base, four corner pillars, and heat-exchange core. */
    public static final Box CELL = new Box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    /** Upper thermoelectric condenser turret and radiator fins reaching y = 19.0. */
    public static final Box CROWN = new Box(2.5, 16.0, 2.5, 13.5, 19.0, 13.5);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(CELL, CROWN);

    private GeoShape() {
    }
}
