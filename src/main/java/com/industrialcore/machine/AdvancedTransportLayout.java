package com.industrialcore.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * Footprint of the Phase 63 Advanced Transporter (pure geometry, no Minecraft classes).
 *
 * <p>The machine is 3 wide, 2 high and 3 deep - 18 cells - and the controller is the front,
 * bottom, centre cell {@code (0, 0, 0)}. Every other cell is a part block whose block state
 * stores its own index, so the part can always compute where its controller is without a UUID, a
 * block entity or a world scan.</p>
 *
 * <p>Local axes match the rest of the mod: {@code x} runs to the machine's right, {@code y} runs
 * up and {@code z} runs backwards from the front face into the machine (against the facing).</p>
 *
 * <p>Index order is {@code (z * HEIGHT + y) * WIDTH + (x + 1)}, which makes the controller index 4
 * and gives every cell a stable, position-derived identity that the block states and the models
 * are generated from.</p>
 */
public final class AdvancedTransportLayout {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 2;
    public static final int DEPTH = 3;
    public static final int CELL_COUNT = WIDTH * HEIGHT * DEPTH;

    /** One cell of the machine: local coordinate, block-state index and whether it is the drive. */
    public record Cell(int x, int y, int z, int index, boolean controller) {
        /** Distance from the front face, 0 = front row. */
        public boolean front() {
            return z == 0;
        }

        public boolean back() {
            return z == DEPTH - 1;
        }

        public boolean top() {
            return y == HEIGHT - 1;
        }

        public boolean bottom() {
            return y == 0;
        }

        public boolean left() {
            return x == -1;
        }

        public boolean right() {
            return x == 1;
        }
    }

    private static final List<Cell> CELLS = build();

    private AdvancedTransportLayout() {}

    private static List<Cell> build() {
        List<Cell> cells = new ArrayList<>(CELL_COUNT);
        for (int z = 0; z < DEPTH; z++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = -1; x <= 1; x++) {
                    int index = indexOf(x, y, z);
                    boolean controller = x == 0 && y == 0 && z == 0;
                    cells.add(new Cell(x, y, z, index, controller));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** Every cell in index order (index 0 .. 17). */
    public static List<Cell> cells() {
        return CELLS;
    }

    /** Index stored in a part block's block state. */
    public static int indexOf(int x, int y, int z) {
        if (x < -1 || x > 1 || y < 0 || y > HEIGHT - 1 || z < 0 || z > DEPTH - 1) {
            throw new IllegalArgumentException("cell " + x + "," + y + "," + z + " is outside the machine");
        }
        return (z * HEIGHT + y) * WIDTH + (x + 1);
    }

    /** Cell for a stored index; throws for an index outside 0 .. 17. */
    public static Cell cell(int index) {
        if (index < 0 || index >= CELL_COUNT) {
            throw new IllegalArgumentException("part index " + index);
        }
        return CELLS.get(index);
    }

    /** True when the index belongs to the controller cell (never a part block). */
    public static boolean isControllerIndex(int index) {
        return index == indexOf(0, 0, 0);
    }

    /** World position of a cell for a controller at {@code (cx, cy, cz)} with a horizontal facing. */
    public static GridPos world(int cx, int cy, int cz, int facing, Cell cell) {
        int right = QuarryFrame.rightOf(facing);
        int back = facing ^ 1;
        return new GridPos(
                cx + QuarryFrame.stepX(right) * cell.x() + QuarryFrame.stepX(back) * cell.z(),
                cy + cell.y(),
                cz + QuarryFrame.stepZ(right) * cell.x() + QuarryFrame.stepZ(back) * cell.z());
    }

    /**
     * Controller position implied by a part: the inverse of {@link #world}. A part that was moved,
     * rotated or left behind by a broken machine resolves to a position that does not hold a
     * valid controller, which is exactly how orphans are detected.
     */
    public static GridPos controllerOf(int partX, int partY, int partZ, int facing, Cell cell) {
        int right = QuarryFrame.rightOf(facing);
        int back = facing ^ 1;
        return new GridPos(
                partX - QuarryFrame.stepX(right) * cell.x() - QuarryFrame.stepX(back) * cell.z(),
                partY - cell.y(),
                partZ - QuarryFrame.stepZ(right) * cell.x() - QuarryFrame.stepZ(back) * cell.z());
    }
}
