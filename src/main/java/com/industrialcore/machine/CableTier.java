package com.industrialcore.machine;

/**
 * The four FE cable tiers.
 *
 * <p>{@code throughput} is the maximum FE a single cable segment moves per tick and also the
 * amount it accepts from a generator. A connected cable network uses the <em>lowest</em> tier in
 * the network as its bottleneck, so mixing a Basic cable into an Ultimate line correctly limits
 * the whole line. The generator still pushes at most its own configured amount per adjacent
 * storage, so a single generator cannot saturate a high tier by itself.</p>
 *
 * <p>Buffer capacities are four ticks of throughput: enough to absorb a burst between two
 * generator pushes without hiding a real capacity limit.</p>
 */
public enum CableTier {
    BASIC("cable_basic", 128, 512),
    IMPROVED("cable_improved", 512, 2_048),
    ADVANCED("cable_advanced", 2_048, 8_192),
    ULTIMATE("cable_ultimate", 8_192, 32_768);

    private final String id;
    private final int throughput;
    private final int bufferCapacity;

    CableTier(String id, int throughput, int bufferCapacity) {
        this.id = id;
        this.throughput = throughput;
        this.bufferCapacity = bufferCapacity;
    }

    public String id() {
        return id;
    }

    /** FE per tick this segment can move (and accept). */
    public int throughput() {
        return throughput;
    }

    public int bufferCapacity() {
        return bufferCapacity;
    }

    public static CableTier byIndex(int index) {
        CableTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
