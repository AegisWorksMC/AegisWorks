package com.industrialcore.machine;

/**
 * Visual role of one casing block inside a formed transporter shell (Phase 62).
 *
 * <p>Before Phase 62 every casing block of a shell rendered the same cube, so a formed machine
 * looked like a wall of identical tiles with one detailed controller. A casing now carries the
 * part of the machine it represents, and the controller stamps that part onto every shell cell
 * when the structure validates, so the 64 blocks of the 4x4x4 shell read as one large machine:
 * a plinth, a header beam, two conveyor mouths, a central drive housing and paneled sides.</p>
 *
 * <p>The part only drives the model. Collision, hardness, drops and the structure check itself
 * are identical for every part, so a re-skin can never change how the machine behaves.</p>
 */
public enum TransporterCasingPart {
    /** Standalone cube: the block as placed by a player, outside any formed structure. */
    PLAIN("plain"),
    /** Bottom row of every vertical face: the plinth the machine stands on. */
    SILL("sill"),
    /** Top row of every vertical face: the load-bearing header beam. */
    HEADER("header"),
    /** One of the two vertical conveyor mouths on the front face. */
    CONVEYOR("conveyor"),
    /** Lower-left quadrant of the 2x2 drive housing - the quadrant the controller lives in. */
    DRIVE_HUB("drive_hub"),
    /** Lower-right quadrant of the drive housing. */
    DRIVE_RIGHT("drive_right"),
    /** Upper-left quadrant of the drive housing. */
    DRIVE_TOP("drive_top"),
    /** Upper-right quadrant of the drive housing. */
    DRIVE_CORNER("drive_corner"),
    /** Middle cells of the left and right faces. */
    PANEL("panel"),
    /** Middle cells of the back face. */
    BACK("back"),
    /** Centre 2x2 of the top face. */
    DECK("deck"),
    /** Centre 2x2 of the bottom face. */
    BASE("base");

    private final String modelName;

    TransporterCasingPart(String modelName) {
        this.modelName = modelName;
    }

    /** Name of the block model file this part renders ({@code block/advanced_casing_<name>}). */
    public String modelName() {
        return modelName;
    }

    /**
     * Ordinal stored in the block state. Minecraft's enum properties require {@code
     * StringRepresentable}, which would drag Minecraft classes into this pure geometry enum (and
     * out of reach of the unit tests), so the block state stores the id and the blockstate JSON
     * is generated from it.
     */
    public int id() {
        return ordinal();
    }

    /** Part for a stored id; unknown ids fall back to {@link #PLAIN}. */
    public static TransporterCasingPart byId(int id) {
        TransporterCasingPart[] parts = values();
        return id >= 0 && id < parts.length ? parts[id] : PLAIN;
    }
}
