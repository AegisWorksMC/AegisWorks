package com.industrialcore.machine;

/**
 * The four item pipe tiers. All tiers share one transport implementation; these constants are the
 * only place where their real performance is defined.
 *
 * <p>{@code travelTicks} is the time one stack spends inside a single pipe block. The whole route
 * therefore takes {@code travelTicks * pipeBlocks} ticks, and the client can extrapolate the drawn
 * item from the server progress without any per-tick packets. Tier 1 is deliberately slower than
 * the old Phase 4 pipe, which moved a whole stack in one tick.</p>
 */
public enum PipeTier {
    BASIC("item_pipe", 10, 1),
    IMPROVED("pipe_improved", 6, 2),
    ADVANCED("pipe_advanced", 3, 4),
    ULTIMATE("pipe_ultimate", 1, 8);

    private final String id;
    private final int travelTicks;
    private final int transferMultiplier;

    PipeTier(String id, int travelTicks, int transferMultiplier) {
        this.id = id;
        this.travelTicks = travelTicks;
        this.transferMultiplier = transferMultiplier;
    }

    public String id() {
        return id;
    }

    /** Ticks a stack spends inside one pipe block. Always >= 1. */
    public int travelTicks() {
        return travelTicks;
    }

    /** Items pulled/handed over at once, relative to the configured base amount. */
    public int itemsPerTransfer(int baseAmount) {
        int base = Math.max(1, baseAmount);
        long scaled = (long) base * transferMultiplier;
        return (int) Math.min(64L, scaled);
    }

    public static PipeTier byIndex(int index) {
        PipeTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
