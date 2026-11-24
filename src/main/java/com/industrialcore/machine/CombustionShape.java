package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the combustion generator model in model pixels (Phase 71).
 *
 * <p>Authored north-facing with the front on {@code -Z}, exactly like {@code MachineShapes} and the
 * port tables, so {@code MachineShapes#combustion} can rotate it with the same helper the other
 * large machines use. The class is deliberately free of Minecraft types: the unit test compares
 * these boxes against {@code models/block/combustion_generator.json} and would fail the build if the
 * model ever grew past its collision shape.</p>
 *
 * <p>Three boxes are enough because the cell itself is solid and only the flywheel and the exhaust
 * stack leave it.</p>
 */
public final class CombustionShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** The whole cell; every part of the machine that stays inside it is covered by this box. */
    public static final Box CELL = new Box(0, 0, 0, 16, 16, 16);
    /** Flywheel and hub, 2 px in front of the north face. */
    public static final Box FLYWHEEL = new Box(3, 2.4, -2, 10.4, 9.2, 0);
    /** Exhaust stack and its cap, 6 px above the top of the cell. */
    public static final Box STACK = new Box(0.6, 16, 12.2, 3.8, 22, 15.8);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(CELL, FLYWHEEL, STACK);

    private CombustionShape() {
    }
}
