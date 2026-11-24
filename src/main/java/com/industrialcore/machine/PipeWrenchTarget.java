package com.industrialcore.machine;

/**
 * Which pipe face a wrench click refers to.
 *
 * <p>A pipe block is mostly empty space around a small core with up to six arms. The face of the
 * block that was clicked is therefore not always the face the player aimed at: clicking the top
 * of a horizontal arm still reports the block's {@code UP} face. The side is instead derived from
 * the click position relative to the block center, so clicking the end of an arm selects that arm.
 * Only a click close to the center falls back to the reported block face.</p>
 *
 * <p>Pure maths, no Minecraft classes, so the behaviour is unit tested for every arm.</p>
 */
public final class PipeWrenchTarget {
    /** Click positions closer than this (in blocks) to the center use the clicked block face. */
    public static final double CENTER_RADIUS = 0.2D;

    /** Vanilla direction indices. */
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private PipeWrenchTarget() {}

    /**
     * @param hitX          world X of the click
     * @param hitY          world Y of the click
     * @param hitZ          world Z of the click
     * @param blockX        block X
     * @param blockY        block Y
     * @param blockZ        block Z
     * @param clickedFace   direction index reported by the block hit (0..5)
     * @return the direction index of the face the player aimed at (0..5)
     */
    public static int sideFor(double hitX, double hitY, double hitZ,
                              int blockX, int blockY, int blockZ,
                              int clickedFace) {
        return sideFor(hitX, hitY, hitZ, blockX, blockY, blockZ, clickedFace, CENTER_RADIUS);
    }

    /** Same as {@link #sideFor(double, double, double, int, int, int, int)} with a custom radius. */
    public static int sideFor(double hitX, double hitY, double hitZ,
                              int blockX, int blockY, int blockZ,
                              int clickedFace, double centerRadius) {
        int fallback = clickedFace >= 0 && clickedFace < 6 ? clickedFace : UP;
        if (!Double.isFinite(hitX) || !Double.isFinite(hitY) || !Double.isFinite(hitZ)) {
            return fallback;
        }
        double dx = hitX - (blockX + 0.5D);
        double dy = hitY - (blockY + 0.5D);
        double dz = hitZ - (blockZ + 0.5D);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        double radius = Math.max(0.0D, centerRadius);
        double largest = Math.max(ax, Math.max(ay, az));
        if (largest <= radius) {
            return fallback;
        }
        if (ay >= ax && ay >= az) {
            return dy >= 0.0D ? UP : DOWN;
        }
        if (ax >= az) {
            return dx >= 0.0D ? EAST : WEST;
        }
        return dz >= 0.0D ? SOUTH : NORTH;
    }

    /** Translation key of a direction, for the action bar message. */
    public static String sideKey(int direction) {
        return switch (direction) {
            case DOWN -> "gui.industrialcore.side.down";
            case UP -> "gui.industrialcore.side.up";
            case NORTH -> "gui.industrialcore.side.north";
            case SOUTH -> "gui.industrialcore.side.south";
            case WEST -> "gui.industrialcore.side.west";
            default -> "gui.industrialcore.side.east";
        };
    }
}
