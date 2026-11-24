package com.industrialcore.machine;

/**
 * Action ids of {@code MachineConfigPayload} (Phase 11).
 *
 * <p>The GUI, the network handler and both machine block entities must agree on these numbers: the
 * screens send one id per click and the machine applies it. They used to be written as literals in
 * two places, which made the auto crafter interpret the processing machines' "cycle mode" click as
 * "cycle filter". Keeping them in one Minecraft-free class makes that impossible to drift and lets
 * a unit test pin the mapping.</p>
 *
 * <p>Id 1 is machine wide ({@code side = -1}); ids 2..6 address one face. Id 0 used to toggle the
 * auto crafter's even-split button; that button was removed in Phase 81, so the id is retired and
 * no longer accepted.</p>
 */
public final class MachineActions {
    /** Machine wide: register the recipe arranged in the auto crafter's grid. */
    public static final int REGISTER_GRID = 1;

    /** Face: cycle the face mode (AUTO / INPUT / OUTPUT / NONE). */
    public static final int FACE_MODE = 2;
    /** Face: cycle the target slot. */
    public static final int FACE_SLOT = 3;
    /** Face: cycle the filter mode (OFF / ALLOW / DENY). */
    public static final int FACE_FILTER = 4;
    /** Face: add or remove the player's carried item from the filter list. */
    public static final int FACE_ADD_ITEM = 5;
    /** Face: clear the filter list. */
    public static final int FACE_CLEAR = 6;

    /** Highest id the network handler accepts. */
    public static final int MAX = FACE_CLEAR;

    private MachineActions() {}

    /** True for the machine wide action that ignores the face. */
    public static boolean isMachineWide(int action) {
        return action == REGISTER_GRID;
    }

    /** True for the face actions. */
    public static boolean isFaceAction(int action) {
        return action >= FACE_MODE && action <= MAX;
    }

    /** True when the network handler should accept this id at all. */
    public static boolean isValid(int action) {
        return action == REGISTER_GRID || isFaceAction(action);
    }
}
