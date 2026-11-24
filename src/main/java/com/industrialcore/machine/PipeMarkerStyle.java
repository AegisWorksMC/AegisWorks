package com.industrialcore.machine;

/**
 * Colour of the pipe face marker drawn by the block entity renderer.
 *
 * <p>The marker answers "what did the wrench set on this face?" without putting the mode into the
 * block state. A pipe has six connection booleans; adding six four-valued mode properties would
 * multiply the state space by 4^6 = 4,096 per pipe, and the client bakes a model for every block
 * state at start-up (Phase 22 briefly did this: 262,144 states per pipe, 1,048,576 for the four
 * tiers). Rendering the marker from the synced block entity data keeps a pipe at 64 states.</p>
 *
 * <p>Only the three configured states get a marker; {@link PipeConnectionMode#NORMAL} is the plain
 * pipe route and stays unmarked, so a normal factory is not covered in colours.</p>
 *
 * <p>Minecraft-free so the mapping can be unit tested; the renderer reads the numbers from here.</p>
 */
public final class PipeMarkerStyle {
    /** Marker colour per mode as 0xRRGGBB, or 0 when the face is not marked. */
    public static final int NORMAL_COLOR = 0;
    public static final int PULL_COLOR = 0x3ED694;
    public static final int PUSH_COLOR = 0xFFAE4A;
    public static final int CLOSED_COLOR = 0x808C98;

    /**
     * Distance from the block boundary to the marker's near edge, in block units. The marker is a
     * thin collar around the arm, so it never shares a plane with the arm (no z-fighting) and is
     * still inside the pipe for the four side faces.
     */
    public static final float COLLAR_NEAR = 0.02F;
    /** Distance from the block boundary to the marker's far edge. */
    public static final float COLLAR_FAR = 0.12F;
    /** Half width of the collar; slightly larger than the 5.0 px arm so it wraps it cleanly. */
    public static final float COLLAR_HALF = 0.20F;

    private PipeMarkerStyle() {}

    /** 0xRRGGBB colour of the given mode, or 0 when that mode draws nothing. */
    public static int color(PipeConnectionMode mode) {
        if (mode == null) {
            return NORMAL_COLOR;
        }
        return switch (mode) {
            case NORMAL -> NORMAL_COLOR;
            case PULL -> PULL_COLOR;
            case PUSH -> PUSH_COLOR;
            case NONE -> CLOSED_COLOR;
        };
    }

    /** True when the mode has a visible marker. */
    public static boolean isMarked(PipeConnectionMode mode) {
        return color(mode) != 0;
    }
}
