package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Phase 7 balance contract.
 *
 * <p>These pin the numbers that the README documents and that the GameTests assert: crushing 120
 * ticks at 10 FE/t, smelting a vanilla 200-tick recipe in 100 ticks at 8 FE/t. Changing a
 * constant therefore fails a test instead of silently drifting from the documentation.</p>
 */
class ProcessingBalanceTest {

    @Test
    void defaultCrushingCostsMatchTheDocumentedValues() {
        assertEquals(120, ProcessingBalance.CRUSHER_TIME_TICKS);
        assertEquals(10, ProcessingBalance.CRUSHER_ENERGY_PER_TICK);
        assertEquals(1_200L, ProcessingBalance.totalEnergy(
                ProcessingBalance.CRUSHER_TIME_TICKS, ProcessingBalance.CRUSHER_ENERGY_PER_TICK));
    }

    @Test
    void electricFurnaceIsTwiceAsFastAtTheDefaultSetting() {
        int time = ProcessingBalance.furnaceTime(ProcessingBalance.VANILLA_FURNACE_TIME_TICKS,
                ProcessingBalance.FURNACE_SPEED_PERCENT);
        assertEquals(100, time);
        assertEquals(2.0D, ProcessingBalance.speedMultiplier(
                ProcessingBalance.VANILLA_FURNACE_TIME_TICKS, time), 1.0E-9D);
        assertTrue(ProcessingBalance.isFasterThanVanillaFurnace(time));
        assertEquals(800L, ProcessingBalance.totalEnergy(time, ProcessingBalance.FURNACE_ENERGY_PER_TICK));
    }

    @Test
    void oneOreCostsTheDocumentedTotal() {
        // 1 ore -> 2 dust -> 2 ingots: one crush plus two smelts.
        long crush = ProcessingBalance.totalEnergy(ProcessingBalance.CRUSHER_TIME_TICKS,
                ProcessingBalance.CRUSHER_ENERGY_PER_TICK);
        long smelt = ProcessingBalance.totalEnergy(100, ProcessingBalance.FURNACE_ENERGY_PER_TICK);
        assertEquals(2_800L, crush + 2L * smelt);
    }

    @Test
    void furnaceTimeRoundsUpAndNeverReachesZero() {
        assertEquals(100, ProcessingBalance.furnaceTime(200, 50));
        assertEquals(200, ProcessingBalance.furnaceTime(200, 100));
        assertEquals(50, ProcessingBalance.furnaceTime(200, 25));
        assertEquals(1, ProcessingBalance.furnaceTime(1, 50));
        assertEquals(1, ProcessingBalance.furnaceTime(0, 50));
        assertEquals(2, ProcessingBalance.furnaceTime(200, 0),
                "a non-positive percentage is clamped to 1%, which is still faster than vanilla");
        assertEquals(2_000, ProcessingBalance.furnaceTime(200, 1000),
                "a 1000% speed setting halves the time ten times over");
    }

    @Test
    void vanillaSpeedIsNotFasterThanAVanillaFurnace() {
        assertFalse(ProcessingBalance.isFasterThanVanillaFurnace(200));
        assertFalse(ProcessingBalance.isFasterThanVanillaFurnace(201));
        assertFalse(ProcessingBalance.isFasterThanVanillaFurnace(0));
        assertTrue(ProcessingBalance.isFasterThanVanillaFurnace(199));
    }

    @Test
    void totalEnergyClampsNegativeInputs() {
        assertEquals(0L, ProcessingBalance.totalEnergy(-5, 10));
        assertEquals(0L, ProcessingBalance.totalEnergy(10, -5));
        assertEquals(50L, ProcessingBalance.totalEnergy(5, 10));
    }

    @Test
    void machineBuffersAcceptAtLeastOneOperation() {
        long crusherOperation = ProcessingBalance.totalEnergy(ProcessingBalance.CRUSHER_TIME_TICKS,
                ProcessingBalance.CRUSHER_ENERGY_PER_TICK);
        long furnaceOperation = ProcessingBalance.totalEnergy(100, ProcessingBalance.FURNACE_ENERGY_PER_TICK);
        assertTrue(ProcessingBalance.MACHINE_CAPACITY > crusherOperation,
                "the crusher buffer must hold more than one operation");
        assertTrue(ProcessingBalance.MACHINE_CAPACITY > furnaceOperation,
                "the furnace buffer must hold more than one operation");
        assertTrue(ProcessingBalance.MACHINE_MAX_RECEIVE
                        >= ProcessingBalance.CRUSHER_ENERGY_PER_TICK,
                "one cable tick must be able to pay for a crushing tick");
    }
}
