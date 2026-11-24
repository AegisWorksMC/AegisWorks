package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the solar generator model in model pixels (Phase 74.3).
 *
 * <p>Authored north-facing with the low step on {@code -Z} and three ascending photovoltaic
 * tiers ({@code 7.5 / 10.5 / 13.5 px}), matching {@code MachineShapes} and the port tables.
 * Pure Java without Minecraft types so unit tests can pin the model bounds against these three
 * disjoint boxes.</p>
 */
public final class SolarShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Front photovoltaic module tier and low support frame (y <= 7.5). */
    public static final Box FRONT_TIER = new Box(0.0, 0.0, 0.0, 16.0, 7.5, 5.5);
    /** Middle photovoltaic module tier and tilt truss (y <= 10.5). */
    public static final Box MID_TIER = new Box(0.0, 0.0, 5.5, 16.0, 10.5, 11.0);
    /** Rear photovoltaic module tier, MPPT inverter cabinet, and SOUTH port face (y <= 13.5). */
    public static final Box REAR_TIER = new Box(0.0, 0.0, 11.0, 16.0, 13.5, 16.0);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(FRONT_TIER, MID_TIER, REAR_TIER);

    private SolarShape() {
    }
}
