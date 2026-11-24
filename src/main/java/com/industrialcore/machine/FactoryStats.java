package com.industrialcore.machine;

/**
 * One aggregated scan of the factory around a monitor (Phase 82).
 *
 * <p>Every value is a plain number so the panel can show the whole factory in a handful of rows:
 * how many machines exist and what they are doing, how much FE the grid holds and whether it is
 * filling up, how many pipes are stuck and which machines report a full output. The class is free
 * of Minecraft types, so the aggregation and the percentages are unit tested directly.</p>
 *
 * <p>The block entity rebuilds a snapshot every {@link #SCAN_INTERVAL_TICKS} ticks; the panel only
 * renders that snapshot, so opening ten monitors costs a scan every second, never per frame.</p>
 */
public record FactoryStats(int machines, int working, int idle, int stopped,
                           int generators, int generatorsRunning,
                           int pipes, int blockedPipes, int fullOutputs,
                           long energyStored, long energyCapacity, int energyTrend) {
    public static final FactoryStats EMPTY =
            new FactoryStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0);
    /** Radius in blocks that one monitor observes. */
    public static final int SCAN_RADIUS = 24;
    /** One scan per second: cheap enough for a large base, fast enough for a useful trend. */
    public static final int SCAN_INTERVAL_TICKS = 20;

    public static Builder builder() {
        return new Builder();
    }

    /** State of one observed machine. */
    public enum MachineState {
        WORKING,
        IDLE,
        STOPPED
    }

    /** Working share of all machines, 0..100. */
    public int activePercent() {
        return machines <= 0 ? 0 : (int) Math.min(100L, (long) working * 100L / machines);
    }

    /** Fill share of the observed FE buffers, 0..100 (overflow-safe up to Long.MAX_VALUE). */
    public int energyPercent() {
        if (energyCapacity <= 0L || energyStored <= 0L) {
            return 0;
        }
        if (energyStored >= energyCapacity) {
            return 100;
        }
        return (int) Math.max(0L, Math.min(100L,
                (long) ((double) energyStored * 100.0D / (double) energyCapacity)));
    }

    /** True when something needs the player's attention (a stuck pipe or a full output). */
    public boolean hasAlert() {
        return blockedPipes > 0 || fullOutputs > 0;
    }

    public FactoryStats withEnergyTrend(int trend) {
        return new FactoryStats(machines, working, idle, stopped, generators, generatorsRunning,
                pipes, blockedPipes, fullOutputs, energyStored, energyCapacity, trend);
    }

    /** Mutable accumulator used while one scan walks the block entities. */
    public static final class Builder {
        private int machines;
        private int working;
        private int idle;
        private int stopped;
        private int generators;
        private int generatorsRunning;
        private int pipes;
        private int blockedPipes;
        private int fullOutputs;
        private long energyStored;
        private long energyCapacity;

        private Builder() {
        }

        public void addMachine(MachineState state) {
            machines++;
            if (state == null) {
                idle++;
                return;
            }
            switch (state) {
                case WORKING -> working++;
                case STOPPED -> stopped++;
                default -> idle++;
            }
        }

        public void addGenerator(boolean running) {
            generators++;
            if (running) {
                generatorsRunning++;
            }
        }

        public void addPipe(boolean blocked) {
            pipes++;
            if (blocked) {
                blockedPipes++;
            }
        }

        public void addFullOutput() {
            fullOutputs++;
        }

        public void addEnergy(long stored, long capacity) {
            long s = Math.max(0L, stored);
            long c = Math.max(0L, capacity);
            energyStored = (Long.MAX_VALUE - energyStored < s) ? Long.MAX_VALUE : energyStored + s;
            energyCapacity = (Long.MAX_VALUE - energyCapacity < c) ? Long.MAX_VALUE : energyCapacity + c;
        }

        public FactoryStats build() {
            return new FactoryStats(machines, working, idle, stopped, generators, generatorsRunning,
                    pipes, blockedPipes, fullOutputs, energyStored, energyCapacity, 0);
        }
    }
}
