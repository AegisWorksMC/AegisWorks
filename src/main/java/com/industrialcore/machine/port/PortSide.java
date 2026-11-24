package com.industrialcore.machine.port;

/**
 * One of the six faces of a machine, in <em>model space</em>.
 *
 * <p>Model space is the coordinate system every machine model is authored in: the machine faces
 * north, {@code x}/{@code y}/{@code z} run 0..16 and {@code north} is the {@code -Z} face. The
 * blockstate then rotates the whole model by 0/90/180/270 degrees. Keeping port definitions in
 * model space means a machine has exactly one port table, no matter which way it is placed, and
 * {@link #rotate(PortFacing)} derives the placed orientation.</p>
 *
 * <p>This enum is deliberately free of Minecraft types so the rotation tables can be unit tested
 * without bootstrapping a game.</p>
 */
public enum PortSide {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    PortSide(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public boolean isHorizontal() {
        return stepY == 0;
    }

    public PortSide opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** Faces in the order a north-facing model visits them when it is turned clockwise. */
    private static final PortSide[] CLOCKWISE = {NORTH, EAST, SOUTH, WEST};

    /** Position in {@link #CLOCKWISE}, or -1 for the vertical faces. */
    private int clockwiseIndex() {
        return switch (this) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> -1;
        };
    }

    /** Rotates this face the same way the blockstate rotates the model. */
    public PortSide rotate(PortFacing facing) {
        int index = clockwiseIndex();
        return index < 0 ? this : CLOCKWISE[(index + facing.steps()) & 3];
    }
}
