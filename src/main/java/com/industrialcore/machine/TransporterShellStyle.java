package com.industrialcore.machine;

/**
 * Which visual part of the machine a casing cell of the Advanced Transporter shell represents
 * (Phase 62).
 *
 * <p>The shell of the 4x4x4 machine is 55 casing blocks plus the controller. Instead of stamping
 * the same cube 55 times, the controller asks this class for a part and an outward facing per
 * cell, so the formed structure reads as one large machine:</p>
 *
 * <pre>
 *            front face (local z = 0, 4 wide x 4 tall)
 *   +------+------+------+------+
 *   | hdr  | hdr  | hdr  | hdr  |   row 3: header beam
 *   +------+------+------+------+
 *   | conv | DTOP | DCRN | conv |   row 2
 *   +------+------+------+------+
 *   | conv | [CT] | DRGT | conv |   row 1: 2x2 drive housing, controller lower-left
 *   +------+------+------+------+
 *   | sill | sill | sill | sill |   row 0: plinth
 *   +------+------+------+------+
 * </pre>
 *
 * <p>Everything is a pure function of local shell coordinates, so the mapping is unit-tested and
 * the block entity only has to compare the result with the block state it finds. Every style
 * carries the horizontal direction that block's outward face points at, which is what the
 * blockstate rotation needs.</p>
 *
 * <p>Only the Advanced tier's 4x4x4 shell has a part layout; every other size returns
 * {@link TransporterCasingPart#PLAIN}, which is exactly the pre-Phase-62 look, so Basic and the
 * Quantum Array are untouched.</p>
 */
public final class TransporterShellStyle {

    /** Visual part of one shell cell and the horizontal direction its outward face looks at. */
    public record CasingStyle(TransporterCasingPart part, int facing) {
        @Override
        public boolean equals(Object other) {
            return other instanceof CasingStyle style
                    && style.part == part && style.facing == facing;
        }

        @Override
        public int hashCode() {
            return part.hashCode() * 31 + facing;
        }
    }

    private TransporterShellStyle() {}

    /**
     * Style of a casing cell, or {@code null} for the controller cell itself.
     *
     * @param size        shell edge length (only 4 has a layout)
     * @param localX      local x, 0 = machine's left column (local right is {@code rightOf(front)})
     * @param localY      local y, 0 = bottom row
     * @param localZ      local z, 0 = front face, {@code size-1} = back face
     * @param frontFacing the controller's horizontal facing (vanilla direction index)
     */
    public static CasingStyle styleFor(int size, int localX, int localY, int localZ,
                                       int frontFacing) {
        if (size < 3 || size > 5) {
            return new CasingStyle(TransporterCasingPart.PLAIN, frontFacing);
        }
        int offset = TransporterStructure.controllerOffset(size);
        if (localX == offset && localY == offset && localZ == 0) {
            return null; // the controller cell is the block itself
        }
        int last = size - 1;
        int right = QuarryFrame.rightOf(frontFacing);
        int back = frontFacing ^ 1;
        int outward;

        if (localZ == 0) {
            outward = frontFacing;
            if (localY == 0) {
                return style(TransporterCasingPart.SILL, outward);
            }
            if (localY == last) {
                return style(TransporterCasingPart.HEADER, outward);
            }
            if (localX == 0 || localX == last) {
                return style(TransporterCasingPart.CONVEYOR, outward);
            }
            if (size == 4) {
                if (localX == offset + 1) {
                    return style(localY == offset
                            ? TransporterCasingPart.DRIVE_RIGHT
                            : TransporterCasingPart.DRIVE_CORNER, outward);
                }
                return style(TransporterCasingPart.DRIVE_TOP, outward);
            }
            if (size == 5) {
                // 8-cell quantum gate ring around centre controller (2, 2, 0)
                if (localX != offset && localY != offset) {
                    return style(TransporterCasingPart.DRIVE_CORNER, outward);
                }
                if (localY == offset) {
                    return style(TransporterCasingPart.DRIVE_RIGHT, outward);
                }
                return style(TransporterCasingPart.DRIVE_TOP, outward);
            }
            return style(TransporterCasingPart.PANEL, outward);
        }

        if (localZ == last) {
            outward = back;
            if (localY == 0) {
                return style(TransporterCasingPart.SILL, outward);
            }
            if (localY == last) {
                return style(TransporterCasingPart.HEADER, outward);
            }
            return style(TransporterCasingPart.BACK, outward);
        }

        if (localX == 0 || localX == last) {
            // Side faces look sideways: local x 0 is the machine's left, so it looks away from
            // the machine's right direction.
            outward = localX == 0 ? opposite(right) : right;
            if (localY == 0) {
                return style(TransporterCasingPart.SILL, outward);
            }
            if (localY == last) {
                return style(TransporterCasingPart.HEADER, outward);
            }
            return style(TransporterCasingPart.PANEL, outward);
        }

        if (localY == last) {
            return style(TransporterCasingPart.DECK, outwardOf(frontFacing));
        }
        if (localY == 0) {
            return style(TransporterCasingPart.BASE, outwardOf(frontFacing));
        }
        return style(TransporterCasingPart.PLAIN, outwardOf(frontFacing));
    }

    /** Top and bottom plates are horizontal; their facing is irrelevant, so it is pinned. */
    private static int outwardOf(int frontFacing) {
        return frontFacing;
    }

    private static CasingStyle style(TransporterCasingPart part, int facing) {
        return new CasingStyle(part, facing);
    }

    private static int opposite(int facing) {
        return facing ^ 1;
    }
}
