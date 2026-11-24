package com.industrialcore.level;

/**
 * Phase 99: Pure-Java conservation-safe parallel batch planner for Limit-Broken (Lv.100..1000)
 * processing machines.
 *
 * <p>Clamps {@code actualBatch} strictly to the minimum of:
 * <ol>
 *   <li>{@link MachineLevelMath#parallelBatchSize(int, boolean)} (1..64)</li>
 *   <li>Available input items divided by per-operation input count</li>
 *   <li>Remaining output slot capacity divided by per-operation output count</li>
 *   <li>Available stored FE divided by per-operation total FE cost ({@code long} arithmetic)</li>
 * </ol>
 * guaranteeing zero item duplication, zero item loss, and zero integer overflow.</p>
 */
public final class ParallelExecutionLogic {

    public record BatchPlan(
            int actualBatch,
            int totalInputCount,
            int totalOutputCount,
            long totalEnergyCost,
            boolean outputFull,
            boolean insufficientEnergy
    ) {}

    private ParallelExecutionLogic() {}

    public static BatchPlan computeBatch(int parallelLimit,
                                         int inputCount,
                                         int inputPerOp,
                                         int resultPerOp,
                                         int outputExisting,
                                         int outputMaxStack,
                                         long unitTotalEnergyCost,
                                         long availableEnergy) {
        int cap = Math.max(1, parallelLimit);
        int reqIn = Math.max(1, inputPerOp);
        int resOut = Math.max(1, resultPerOp);
        int maxByInput = Math.max(0, inputCount) / reqIn;
        if (maxByInput <= 0) {
            return new BatchPlan(0, 0, 0, 0L, false, false);
        }

        int freeSpace = Math.max(0, outputMaxStack - Math.max(0, outputExisting));
        int maxByOutput = freeSpace / resOut;
        if (maxByOutput <= 0) {
            return new BatchPlan(0, 0, 0, 0L, true, false);
        }

        long safeUnitCost = Math.max(0L, unitTotalEnergyCost);
        long safeAvail = Math.max(0L, availableEnergy);
        int maxByEnergy;
        if (safeUnitCost <= 0L) {
            maxByEnergy = cap;
        } else {
            long afford = safeAvail / safeUnitCost;
            if (afford <= 0L) {
                // Keep a 1-op plan flagged as insufficientEnergy so machine waits cleanly in NO_ENERGY.
                return new BatchPlan(1, reqIn, resOut, safeUnitCost, false, true);
            }
            maxByEnergy = (int) Math.min((long) cap, afford);
        }

        int actual = Math.min(cap, Math.min(maxByInput, Math.min(maxByOutput, maxByEnergy)));
        actual = Math.max(1, actual);

        long totalIn = (long) actual * reqIn;
        long totalOut = (long) actual * resOut;
        long totalFe = safeUnitCost > 0L && actual > Long.MAX_VALUE / safeUnitCost
                ? Long.MAX_VALUE
                : (long) actual * safeUnitCost;

        return new BatchPlan(
                actual,
                (int) Math.min(Integer.MAX_VALUE, totalIn),
                (int) Math.min(Integer.MAX_VALUE, totalOut),
                totalFe,
                false,
                false
        );
    }
}
