package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 82: the monitor's aggregation must be exact and safe for empty factories. */
class FactoryStatsTest {

    @Test
    void anEmptyFactoryIsAllZeroAndHealthy() {
        FactoryStats empty = FactoryStats.builder().build();
        assertEquals(FactoryStats.EMPTY.machines(), empty.machines());
        assertEquals(0, empty.activePercent());
        assertEquals(0, empty.energyPercent());
        assertFalse(empty.hasAlert());
        assertEquals(0L, empty.energyStored());
    }

    @Test
    void machinesAreBucketedIntoWorkingIdleAndStopped() {
        FactoryStats.Builder builder = FactoryStats.builder();
        builder.addMachine(FactoryStats.MachineState.WORKING);
        builder.addMachine(FactoryStats.MachineState.WORKING);
        builder.addMachine(FactoryStats.MachineState.IDLE);
        builder.addMachine(FactoryStats.MachineState.STOPPED);
        builder.addMachine(null);
        FactoryStats stats = builder.build();
        assertEquals(5, stats.machines());
        assertEquals(2, stats.working());
        assertEquals(2, stats.idle(), "a null state must fall into idle, not vanish");
        assertEquals(1, stats.stopped());
        assertEquals(40, stats.activePercent());
    }

    @Test
    void generatorsPipesAndFullOutputsAreCountedSeparately() {
        FactoryStats.Builder builder = FactoryStats.builder();
        builder.addGenerator(true);
        builder.addGenerator(false);
        builder.addPipe(false);
        builder.addPipe(true);
        builder.addPipe(true);
        builder.addFullOutput();
        FactoryStats stats = builder.build();
        assertEquals(2, stats.generators());
        assertEquals(1, stats.generatorsRunning());
        assertEquals(3, stats.pipes());
        assertEquals(2, stats.blockedPipes());
        assertEquals(1, stats.fullOutputs());
        assertTrue(stats.hasAlert());
    }

    @Test
    void energySumsAndPercentagesAreClamped() {
        FactoryStats.Builder builder = FactoryStats.builder();
        builder.addEnergy(5_000L, 10_000L);
        builder.addEnergy(2_000L, 2_000L);
        builder.addEnergy(-4L, -1L);
        FactoryStats stats = builder.build();
        assertEquals(7_000L, stats.energyStored());
        assertEquals(12_000L, stats.energyCapacity());
        assertEquals(58, stats.energyPercent());
    }

    @Test
    void aFullBufferCannotExceedOneHundredPercent() {
        FactoryStats.Builder builder = FactoryStats.builder();
        builder.addEnergy(30_000L, 10_000L);
        assertEquals(100, builder.build().energyPercent());
    }

    @Test
    void theTrendIsCarriedIntoTheSnapshotWithoutChangingTheCounts() {
        FactoryStats base = FactoryStats.builder().build();
        FactoryStats trended = base.withEnergyTrend(-1_234);
        assertEquals(-1_234, trended.energyTrend());
        assertEquals(base.machines(), trended.machines());
        assertEquals(base.energyStored(), trended.energyStored());
        assertFalse(trended.hasAlert());
    }

    @Test
    void theScanCadenceIsOneSecondAndTheRadiusIsBounded() {
        assertEquals(20, FactoryStats.SCAN_INTERVAL_TICKS);
        assertEquals(24, FactoryStats.SCAN_RADIUS);
    }
}
