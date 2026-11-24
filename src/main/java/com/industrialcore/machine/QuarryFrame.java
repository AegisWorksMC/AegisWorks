package com.industrialcore.machine;

/**
 * World-space geometry of a quarry's mining area.
 *
 * <p>This is the single source of truth for both the server (which block is mined) and the client
 * (which frame is drawn). The mining area starts one block in front of the machine, extends
 * {@code width} blocks to its right, {@code depth} blocks forward and {@code height} blocks
 * downwards from the machine's Y level.</p>
 *
 * <p>The old renderer computed the X extent only from the "right" direction and the Z extent only
 * from the "forward" direction. That is correct for north/south but collapses an east/west frame
 * onto the wrong axes (the width of an east-facing quarry runs along Z, not X). Deriving both
 * extents from both step vectors, exactly like {@link #target}, makes that class of bug
 * impossible and is covered by unit tests for every horizontal facing.</p>
 *
 * <p>Directions use the vanilla index order: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST.
 * No Minecraft classes are used, so all coordinates can be tested directly.</p>
 */
public final class QuarryFrame {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1};
    private static final int[] STEP_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] STEP_Z = {0, 0, -1, 1, 0, 0};

    private QuarryFrame() {}

    /** An axis-aligned box using max-exclusive coordinates, like {@code AABB}. */
    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
    }

    /** A world block position. */
    public record Point(int x, int y, int z) {
    }

    /** True for the four horizontal facings a quarry block can have. */
    public static boolean isHorizontal(int facing) {
        return facing == NORTH || facing == SOUTH || facing == WEST || facing == EAST;
    }

    public static int stepX(int direction) {
        return STEP_X[checkDirection(direction)];
    }

    public static int stepY(int direction) {
        return STEP_Y[checkDirection(direction)];
    }

    public static int stepZ(int direction) {
        return STEP_Z[checkDirection(direction)];
    }

    /** Direction to the machine's right (clockwise when seen from above). */
    public static int rightOf(int facing) {
        return switch (checkFacing(facing)) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException();
        };
    }

    /** First block of the area: one block in front of the machine. */
    public static Point origin(int machineX, int machineY, int machineZ, int facing) {
        int checked = checkFacing(facing);
        return new Point(machineX + STEP_X[checked], machineY, machineZ + STEP_Z[checked]);
    }

    /**
     * Box that contains exactly the blocks the quarry will mine.
     *
     * <p>The maximum coordinates are exclusive: a width of 1 covers one full block.</p>
     */
    public static Bounds bounds(int machineX, int machineY, int machineZ, int facing,
                                int width, int depth, int height) {
        int checked = checkFacing(facing);
        int safeWidth = Math.max(1, width);
        int safeDepth = Math.max(1, depth);
        int safeHeight = Math.max(1, height);
        Point origin = origin(machineX, machineY, machineZ, checked);
        int right = rightOf(checked);

        // Both axes contribute to X and Z. This is the part the old renderer got wrong for
        // east/west, where the width axis is Z and the depth axis is X.
        int deltaX = STEP_X[right] * (safeWidth - 1) + STEP_X[checked] * (safeDepth - 1);
        int deltaZ = STEP_Z[right] * (safeWidth - 1) + STEP_Z[checked] * (safeDepth - 1);

        double minX = origin.x() + Math.min(0, deltaX);
        double maxX = origin.x() + Math.max(0, deltaX) + 1.0D;
        double minZ = origin.z() + Math.min(0, deltaZ);
        double maxZ = origin.z() + Math.max(0, deltaZ) + 1.0D;
        double minY = origin.y() - (safeHeight - 1);
        double maxY = origin.y() + 1.0D;
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * World block for a linear cursor position. Mirrors {@link MiningPlan}'s local mapping so the
     * server's next target and the drawn target box cannot disagree.
     */
    public static Point target(int machineX, int machineY, int machineZ, int facing,
                               int width, int depth, int height, int index) {
        return target(machineX, machineY, machineZ, facing, new MiningPlan(width, depth, height), index);
    }

    /**
     * Same mapping with an already built plan, so a machine that mines block after block does not
     * allocate a {@link MiningPlan} for every target (Phase 20 large quarry).
     */
    public static Point target(int machineX, int machineY, int machineZ, int facing,
                               MiningPlan plan, int index) {
        int checked = checkFacing(facing);
        int safeIndex = Math.max(0, Math.min(index, plan.volume() - 1));
        Point origin = origin(machineX, machineY, machineZ, checked);
        int right = rightOf(checked);
        return new Point(
                origin.x() + STEP_X[right] * plan.localX(safeIndex) + STEP_X[checked] * plan.localZ(safeIndex),
                origin.y() - plan.localY(safeIndex),
                origin.z() + STEP_Z[right] * plan.localX(safeIndex) + STEP_Z[checked] * plan.localZ(safeIndex));
    }

    /** Y coordinate of the top face of a layer (layer 0 is the machine's Y level). */
    public static double layerTopY(int machineY, int layer) {
        return machineY + 1.0D - Math.max(0, layer);
    }

    /** Deepest layer index a machine at {@code machineY} can mine without leaving the world. */
    public static int maxHeight(int machineY, int minBuildHeight) {
        return Math.max(1, machineY - minBuildHeight + 1);
    }

    private static int checkDirection(int direction) {
        if (direction < 0 || direction > EAST) {
            throw new IllegalArgumentException("direction " + direction);
        }
        return direction;
    }

    private static int checkFacing(int facing) {
        if (!isHorizontal(facing)) {
            throw new IllegalArgumentException("horizontal facing expected, got " + facing);
        }
        return facing;
    }
}
