package com.industrialcore.machine;

/**
 * Lightweight, allocation-free mining profiler.
 *
 * <p>Every running quarry adds its own tick duration here. The numbers are only used for
 * diagnostics (GameTests and an optional periodic log line), never for control flow, so the
 * profiler cannot change gameplay behaviour.</p>
 *
 * <p>Measured values are the mining tick itself (energy check, block break, drops, inventory
 * simulation), not the whole server tick. Server TPS/FPS must be measured externally.</p>
 */
public final class MiningProfiler {
    private static long ticks;
    private static long runningTicks;
    private static long totalNanos;
    private static long maxNanos;
    private static long blocks;
    private static long visualSyncs;

    private static final long LOG_INTERVAL_TICKS = 600L;
    private static long nextLogTick = -1L;

    private MiningProfiler() {}

    /** Immutable diagnostic snapshot. */
    public record Snapshot(long ticks, long runningTicks, long totalNanos, long maxNanos, long blocks) {
        public double averageMicros() {
            return ticks <= 0L ? 0.0D : (totalNanos / (double) ticks) / 1_000.0D;
        }

        public double maxMicros() {
            return maxNanos / 1_000.0D;
        }

        public double blocksPerTick() {
            return ticks <= 0L ? 0.0D : blocks / (double) ticks;
        }

        public double averageMicrosPerRunningTick() {
            return runningTicks <= 0L ? 0.0D : (totalNanos / (double) runningTicks) / 1_000.0D;
        }
    }

    public static void record(long nanos, int blocksMined, boolean running) {
        if (nanos < 0L) {
            return;
        }
        ticks++;
        totalNanos += nanos;
        if (nanos > maxNanos) {
            maxNanos = nanos;
        }
        if (blocksMined > 0) {
            blocks += blocksMined;
        }
        if (running) {
            runningTicks++;
        }
    }

    /** Optional periodic summary; returns true when a line was logged. */
    public static boolean maybeLog(long gameTime) {
        if (nextLogTick < 0L) {
            nextLogTick = gameTime + LOG_INTERVAL_TICKS;
            return false;
        }
        if (gameTime < nextLogTick) {
            return false;
        }
        nextLogTick = gameTime + LOG_INTERVAL_TICKS;
        if (ticks == 0L) {
            return false;
        }
        Snapshot snapshot = snapshot();
        com.industrialcore.IndustrialCore.LOGGER.info(
                "[profiling] quarry ticks={} running={} avg={}us max={}us blocks/tick={}",
                snapshot.ticks(), snapshot.runningTicks(),
                String.format("%.2f", snapshot.averageMicros()),
                String.format("%.2f", snapshot.maxMicros()),
                String.format("%.3f", snapshot.blocksPerTick()));
        return true;
    }

    /** Counts visual (block entity) updates sent by quarries; used to verify traffic bounds. */
    public static void recordVisualSync() {
        visualSyncs++;
    }

    public static long visualSyncs() {
        return visualSyncs;
    }

    public static Snapshot snapshot() {
        return new Snapshot(ticks, runningTicks, totalNanos, maxNanos, blocks);
    }

    public static void reset() {
        ticks = 0L;
        runningTicks = 0L;
        totalNanos = 0L;
        maxNanos = 0L;
        blocks = 0L;
        visualSyncs = 0L;
        nextLogTick = -1L;
    }
}
