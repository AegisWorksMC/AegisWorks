package com.industrialcore.machine;

/**
 * The four original energy recovery and storage devices (Phase 33). None of them is a
 * conventional generator: they store FE, convert a byproduct that already exists, or give back
 * a percentage of FE that a machine already spent - never more.
 *
 * <p>They share one block entity type, one menu and one screen; the block instance carries the
 * kind, exactly like the buffer tiers and the processing machines.</p>
 */
public enum RecoveryKind {
    /**
     * フライホイール: stores surplus FE slowly (200 FE/t receive, 95% kept) and bursts it back
     * at up to 2,048 FE/t per neighbour - the peak shaver of the system.
     */
    FLYWHEEL("flywheel", 300_000, 2_048, 2_048, true),
    /**
     * 圧力回収設備: converts the pooled exhaust pressure of an adjacent thermal or nuclear
     * generator at 85%; useless without an exhaust source, at most 120 FE/t.
     */
    PRESSURE("pressure_recovery", 50_000, 120, 256, false),
    /**
     * 廃熱回収発電: converts waste heat - a percentage of FE adjacent machines already spent,
     * plus the heat pool of a combustion generator - at 85%, at most 100 FE/t.
     */
    HEAT("heat_recovery", 50_000, 100, 256, false),
    /**
     * 回生発電: credits a small percentage of a machine's spend when it stops (regenerative
     * braking) and feeds it back, at most 40 FE/t in total across all faces.
     */
    REGEN("regen_generator", 10_000, 40, 40, false);

    private final String id;
    private final int capacity;
    private final int maxOutput;
    private final int pushPerTick;
    private final boolean acceptsFe;

    RecoveryKind(String id, int capacity, int maxOutput, int pushPerTick, boolean acceptsFe) {
        this.id = id;
        this.capacity = capacity;
        this.maxOutput = maxOutput;
        this.pushPerTick = pushPerTick;
        this.acceptsFe = acceptsFe;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return "block.industrialcore." + this.id;
    }

    /** Internal FE buffer of the device. */
    public int capacity() {
        return this.capacity;
    }

    /** Hard cap of FE this device may output per tick (design limit shown in the GUI). */
    public int maxOutput() {
        return this.maxOutput;
    }

    /** FE pushed into one neighbour per tick (for the regenerative generator: the total). */
    public int pushPerTick() {
        return this.pushPerTick;
    }

    /** True when the device accepts FE from the grid (only the flywheel stores grid FE). */
    public boolean acceptsFe() {
        return this.acceptsFe;
    }

    public boolean isSharedPush() {
        return this == REGEN;
    }
}