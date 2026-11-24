package com.industrialcore.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of the transporter multiblock shells (Phases 26, 27 and 29).
 *
 * <p>The shell of an {@code size x size x size} cube is every cell whose local coordinate touches
 * a face; all remaining cells are interior and must be air. The controller occupies the centre of
 * the front face - local {@code ((size-1)/2, (size-1)/2, 0)} - which for the odd sizes 3 and 5 is
 * the exact centre of the face, and for the even size 4 the upper-left of the central 2x2 (Phase
 * 27 does not pin a controller position, so the same formula covers all three sizes).</p>
 *
 * <p>Local axes, derived from the controller's horizontal facing exactly like the machines do:
 * local x runs along {@link QuarryFrame#rightOf(int)}, local y runs up, local z runs against the
 * facing (from the front face into the shell). Reusing {@link QuarryFrame}'s tested direction math
 * keeps the two geometries from disagreeing.</p>
 *
 * <p>Cell counts (the spec's own numbers):</p>
 * <pre>
 *   size 3: 27 cells = 26 shell (1 controller + 25 casings) + 1 air
 *   size 4: 64 cells = 56 shell (1 controller + 55 casings) + 8 air
 *   size 5: 125 cells = 98 shell (1 controller + 97 casings) + 27 air
 * </pre>
 */
public final class TransporterStructure {

    private TransporterStructure() {}

    /** One shell cell with both its world position and its local coordinate (Phase 62 styling). */
    public record ShellCell(GridPos world, int localX, int localY, int localZ,
                            boolean controller) {
    }

    /**
     * Every shell cell with its local coordinate, in a deterministic order. Callers that need to
     * know which part of the machine a casing renders walk this instead of recomputing the local
     * mapping from world offsets.
     */
    public static List<ShellCell> shellCells(int size, int controllerX, int controllerY,
                                             int controllerZ, int facing) {
        List<ShellCell> cells = new ArrayList<>(shellCount(size));
        forEachCellWithLocal(size, controllerX, controllerY, controllerZ, facing,
                (x, y, z, localX, localY, localZ, controller) -> cells.add(new ShellCell(
                        new GridPos(x, y, z), localX, localY, localZ, controller)));
        return cells;
    }

    /** Local coordinate of the controller on the front face's two central axes. */
    public static int controllerOffset(int size) {
        if (size < 3) {
            throw new IllegalArgumentException("structure size " + size);
        }
        return (size - 1) / 2;
    }

    /** World positions of every casing cell (the shell minus the controller cell). */
    public static List<GridPos> casingCells(int size, int controllerX, int controllerY,
                                            int controllerZ, int facing) {
        int expected = expectedCasingCount(size);
        List<GridPos> cells = new ArrayList<>(expected);
        forEachCell(size, controllerX, controllerY, controllerZ, facing, (x, y, z, controller) -> {
            if (!controller) {
                cells.add(new GridPos(x, y, z));
            }
        });
        if (cells.size() != expected) {
            throw new IllegalStateException("expected " + expected + " casings, built "
                    + cells.size() + " for size " + size);
        }
        return cells;
    }

    /** World positions of every interior cell; all of them must be air. */
    public static List<GridPos> airCells(int size, int controllerX, int controllerY,
                                         int controllerZ, int facing) {
        int expected = expectedAirCount(size);
        List<GridPos> cells = new ArrayList<>(expected);
        int offset = controllerOffset(size);
        int right = QuarryFrame.rightOf(facing);
        // Horizontal facings: opposite = north<->south, west<->east (vanilla direction bits).
        int back = facing ^ 1;
        for (int localZ = 1; localZ < size - 1; localZ++) {
            for (int localY = 1; localY < size - 1; localY++) {
                for (int localX = 1; localX < size - 1; localX++) {
                    cells.add(new GridPos(
                            controllerX + QuarryFrame.stepX(right) * (localX - offset)
                                    + QuarryFrame.stepX(back) * localZ,
                            controllerY + (localY - offset),
                            controllerZ + QuarryFrame.stepZ(right) * (localX - offset)
                                    + QuarryFrame.stepZ(back) * localZ));
                }
            }
        }
        if (cells.size() != expected) {
            throw new IllegalStateException("expected " + expected + " air cells, built "
                    + cells.size() + " for size " + size);
        }
        return cells;
    }

    /** Number of casing cells of a shell (the shell minus the controller cell). */
    public static int expectedCasingCount(int size) {
        return shellCount(size) - 1;
    }

    /** Number of interior cells that must be air. */
    public static int expectedAirCount(int size) {
        int inner = size - 2;
        return inner * inner * inner;
    }

    /** Number of shell cells (everything that is not interior). */
    public static int shellCount(int size) {
        int inner = size - 2;
        return size * size * size - inner * inner * inner;
    }

    private interface CellConsumer {
        void accept(int x, int y, int z, boolean controller);
    }

    private interface LocalCellConsumer {
        void accept(int x, int y, int z, int localX, int localY, int localZ,
                    boolean controller);
    }

    private static void forEachCellWithLocal(int size, int controllerX, int controllerY,
                                             int controllerZ, int facing,
                                             LocalCellConsumer consumer) {
        int offset = controllerOffset(size);
        int right = QuarryFrame.rightOf(facing);
        int back = facing ^ 1;
        for (int localZ = 0; localZ < size; localZ++) {
            boolean faceShell = localZ == 0 || localZ == size - 1;
            for (int localY = 0; localY < size; localY++) {
                boolean rowShell = localY == 0 || localY == size - 1;
                for (int localX = 0; localX < size; localX++) {
                    boolean columnShell = localX == 0 || localX == size - 1;
                    if (!(faceShell || rowShell || columnShell)) {
                        continue; // interior
                    }
                    boolean controller = localX == offset && localY == offset && localZ == 0;
                    consumer.accept(
                            controllerX + QuarryFrame.stepX(right) * (localX - offset)
                                    + QuarryFrame.stepX(back) * localZ,
                            controllerY + (localY - offset),
                            controllerZ + QuarryFrame.stepZ(right) * (localX - offset)
                                    + QuarryFrame.stepZ(back) * localZ,
                            localX, localY, localZ, controller);
                }
            }
        }
    }

    private static void forEachCell(int size, int controllerX, int controllerY, int controllerZ,
                                    int facing, CellConsumer consumer) {
        int offset = controllerOffset(size);
        int right = QuarryFrame.rightOf(facing);
        int back = facing ^ 1;
        for (int localZ = 0; localZ < size; localZ++) {
            boolean faceShell = localZ == 0 || localZ == size - 1;
            for (int localY = 0; localY < size; localY++) {
                boolean rowShell = localY == 0 || localY == size - 1;
                for (int localX = 0; localX < size; localX++) {
                    boolean columnShell = localX == 0 || localX == size - 1;
                    if (!(faceShell || rowShell || columnShell)) {
                        continue; // interior
                    }
                    boolean controller = localX == offset && localY == offset && localZ == 0;
                    consumer.accept(
                            controllerX + QuarryFrame.stepX(right) * (localX - offset)
                                    + QuarryFrame.stepX(back) * localZ,
                            controllerY + (localY - offset),
                            controllerZ + QuarryFrame.stepZ(right) * (localX - offset)
                                    + QuarryFrame.stepZ(back) * localZ,
                            controller);
                }
            }
        }
    }
}
