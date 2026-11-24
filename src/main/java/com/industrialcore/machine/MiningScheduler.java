package com.industrialcore.machine;

/**
 * Server-wide safety valve for mining work and for visual (block entity) update traffic.
 *
 * <p>Two problems grow with the number of machines and cannot be fixed by optimising a single
 * machine: (1) N running quarries could each process their own per-machine cap every tick, so
 * total block work would grow without bound, and (2) N running machines would each send their
 * own visual update packets. This class bounds both.</p>
 *
 * <p>The mining budget is divided evenly between running machines, so a large factory slows
 * down gracefully instead of stalling the server. Requested work that is not granted is kept in
 * the machine's own accumulator (bounded by its per-tick cap), so progress is neither lost nor
 * duplicated - it is simply spread over more ticks.</p>
 *
 * <p>All state is touched from the server thread only. No Minecraft classes are used here so the
 * maths can be unit tested.</p>
 */
public final class MiningScheduler {
    /** Budget value that means "no global limit". */
    public static final int UNLIMITED = 0;

    private static long currentTick = Long.MIN_VALUE;
    private static int budgetThisTick = UNLIMITED;
    private static int usedThisTick;
    private static int activeMachines;
    private static int budgetOverride = -1;

    private static long requestedTotal;
    private static long grantedTotal;
    private static long throttledClaims;

    private MiningScheduler() {}

    /** Called by every machine each tick; resets the per-tick budget when the game tick changes. */
    public static void beginTick(long gameTime, int configuredBudget) {
        if (gameTime != currentTick) {
            currentTick = gameTime;
            usedThisTick = 0;
            budgetThisTick = Math.max(0, configuredBudget);
        }
    }

    /** Registers a machine that is now running (mining). */
    public static void onMachineStarted() {
        activeMachines++;
    }

    /** Unregisters a machine that stopped, completed, or was removed/unloaded. */
    public static void onMachineStopped() {
        if (activeMachines > 0) {
            activeMachines--;
        }
    }

    public static int activeMachines() {
        return activeMachines;
    }

    /** The per-tick work budget currently in effect (0 = unlimited; diagnostics/tests only). */
    public static int currentBudget() {
        return budgetOverride >= 0 ? budgetOverride : budgetThisTick;
    }

    /** Operations this machine may perform this tick before the global budget is applied. */
    public static int share() {
        int budget = effectiveBudget();
        if (budget <= UNLIMITED) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, budget / Math.max(1, activeMachines));
    }

    /**
     * Claims up to {@code requested} operations from the global per-tick budget.
     *
     * @return the number of operations actually granted (0..requested)
     */
    public static int claim(int requested) {
        if (requested <= 0) {
            return 0;
        }
        int budget = effectiveBudget();
        int granted = requested;
        if (budget > UNLIMITED) {
            int alreadyUsed = usedThisTick;
            int remaining = Math.max(0, budget - alreadyUsed);
            granted = Math.min(requested, Math.min(share(), remaining));
            if (granted > 0) {
                usedThisTick = alreadyUsed + granted;
            }
        }
        requestedTotal += requested;
        grantedTotal += granted;
        if (granted < requested) {
            throttledClaims++;
        }
        return granted;
    }

    /**
     * Interval in ticks between two visual updates of one running machine.
     *
     * <p>Machines up to {@code fullRateMachines} keep the shortest interval; above that the
     * interval is stretched (up to {@code maxInterval}) so the total packet rate stays roughly
     * constant as factories grow, while a handful of machines stay visually smooth.</p>
     */
    public static int visualSyncInterval(int minInterval, int maxInterval, int fullRateMachines) {
        int min = Math.max(1, minInterval);
        int max = Math.max(min, maxInterval);
        int fullRate = Math.max(1, fullRateMachines);
        int active = Math.max(1, activeMachines);
        int groups = (active + fullRate - 1) / fullRate;
        long interval = (long) min * groups;
        return (int) Math.min(max, Math.max(min, interval));
    }

    /** Diagnostic counter: operations requested by machines since start. */
    public static long requestedOperations() {
        return requestedTotal;
    }

    /** Diagnostic counter: operations actually granted since start. */
    public static long grantedOperations() {
        return grantedTotal;
    }

    /** Diagnostic counter: claims that were cut short by the global budget. */
    public static long throttledClaims() {
        return throttledClaims;
    }

    // ------------------------------------------------------------------
    // Testing hooks
    // ------------------------------------------------------------------

    /** Overrides the global budget regardless of the config; -1 restores the config value. */
    public static void setBudgetOverrideForTesting(int budget) {
        budgetOverride = budget;
    }

    /** Clears all scheduler state (tests only). */
    public static void resetForTesting() {
        currentTick = Long.MIN_VALUE;
        budgetThisTick = UNLIMITED;
        usedThisTick = 0;
        activeMachines = 0;
        budgetOverride = -1;
        requestedTotal = 0;
        grantedTotal = 0;
        throttledClaims = 0;
    }

    private static int effectiveBudget() {
        return budgetOverride >= 0 ? budgetOverride : budgetThisTick;
    }
}
