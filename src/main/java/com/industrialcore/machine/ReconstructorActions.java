package com.industrialcore.machine;

/**
 * Action ids of the reconstructor's own client -&gt; server payload (Phase 15).
 *
 * <p>The reconstructor does not use {@link MachineActions}: those ids are already spoken for by
 * the auto crafter and the processing machines, and a shared table would make a click on the
 * reconstructor change an unrelated machine setting. Keeping the ids in their own class makes the
 * two tables independent and lets a unit test pin the mapping.</p>
 */
public final class ReconstructorActions {
    /** Analyse the sample in the analysis slot and unlock the matching mineral. */
    public static final int ANALYZE = 0;
    /** Switch to the next unlocked mineral. */
    public static final int CYCLE_MINERAL = 1;
    /** Select one unlocked mineral by key. */
    public static final int SELECT_MINERAL = 2;

    /** True when the id belongs to this machine. */
    public static boolean isValid(int action) {
        return action >= ANALYZE && action <= SELECT_MINERAL;
    }

    private ReconstructorActions() {}
}
