package com.industrialcore.machine;

import java.util.List;

/**
 * Outline of the nuclear generator multiblock controller in model pixels (Phase 105).
 *
 * <p>Flush {@code 0..16} armored reactor casing exterior so the controller integrates seamlessly
 * into a {@code 3x3x3..5x5x5} {@code reactor_casing} wall with a recessed front control console.</p>
 */
public final class NuclearShape {
    /** One axis-aligned box in pixels: {@code x0,y0,z0} to {@code x1,y1,z1}. */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
                throw new IllegalArgumentException("empty box: " + x0 + "," + y0 + "," + z0
                        + " - " + x1 + "," + y1 + "," + z1);
            }
        }
    }

    /** Flush 16x16x16 armored reactor controller body seamlessly matching {@code reactor_casing}. */
    public static final Box CELL = new Box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    /** Every box, in a fixed order. */
    public static final List<Box> BOXES = List.of(CELL);

    private NuclearShape() {
    }
}
