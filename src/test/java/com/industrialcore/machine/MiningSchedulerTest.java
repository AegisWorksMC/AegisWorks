package com.industrialcore.machine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSchedulerTest {
    @BeforeEach
    void setUp() {
        MiningScheduler.resetForTesting();
    }

    @Test
    void zeroBudgetMeansUnlimited() {
        MiningScheduler.beginTick(1L, MiningScheduler.UNLIMITED);
        MiningScheduler.onMachineStarted();
        assertEquals(Integer.MAX_VALUE, MiningScheduler.share());
        assertEquals(64, MiningScheduler.claim(64));
        assertEquals(0L, MiningScheduler.throttledClaims());
    }

    @Test
    void budgetIsSharedFairlyBetweenRunningMachines() {
        MiningScheduler.beginTick(1L, 8);
        MiningScheduler.onMachineStarted();
        MiningScheduler.onMachineStarted();
        assertEquals(4, MiningScheduler.share());
        assertEquals(4, MiningScheduler.claim(8));
        assertEquals(4, MiningScheduler.claim(8));
        // The per-tick budget is now exhausted.
        assertEquals(0, MiningScheduler.claim(8));
        // All three claims asked for more than they were granted.
        assertEquals(3L, MiningScheduler.throttledClaims());
    }

    @Test
    void budgetResetsOnTheNextServerTick() {
        MiningScheduler.beginTick(1L, 4);
        assertEquals(4, MiningScheduler.claim(4));
        assertEquals(0, MiningScheduler.claim(4));
        MiningScheduler.beginTick(2L, 4);
        assertEquals(4, MiningScheduler.claim(4));
    }

    @Test
    void stoppedMachinesDoNotReduceTheShareOfWorkingMachines() {
        MiningScheduler.beginTick(1L, 8);
        MiningScheduler.onMachineStarted();
        MiningScheduler.onMachineStarted();
        MiningScheduler.onMachineStarted();
        MiningScheduler.onMachineStopped();
        MiningScheduler.onMachineStopped();
        assertEquals(1, MiningScheduler.activeMachines());
        assertEquals(8, MiningScheduler.share());
    }

    @Test
    void stoppingMoreMachinesThanStartedNeverGoesNegative() {
        MiningScheduler.onMachineStopped();
        MiningScheduler.onMachineStopped();
        assertEquals(0, MiningScheduler.activeMachines());
    }

    @Test
    void visualSyncIntervalIsShortestForSmallFactories() {
        assertEquals(4, MiningScheduler.visualSyncInterval(4, 20, 16));
        startMachines(16);
        assertEquals(4, MiningScheduler.visualSyncInterval(4, 20, 16));
    }

    @Test
    void visualSyncIntervalStretchesWithMachineCountAndIsCapped() {
        startMachines(17);
        assertEquals(8, MiningScheduler.visualSyncInterval(4, 20, 16));
        MiningScheduler.resetForTesting();
        startMachines(100);
        assertEquals(20, MiningScheduler.visualSyncInterval(4, 20, 1));
    }

    @Test
    void visualSyncIntervalNeverReturnsLessThanOne() {
        assertTrue(MiningScheduler.visualSyncInterval(0, 0, 0) >= 1);
    }

    private static void startMachines(int count) {
        for (int i = 0; i < count; i++) {
            MiningScheduler.onMachineStarted();
        }
    }

    @Test
    void overrideForTestingReplacesTheConfiguredBudget() {
        MiningScheduler.beginTick(1L, 64);
        MiningScheduler.setBudgetOverrideForTesting(2);
        assertEquals(2, MiningScheduler.claim(64));
        MiningScheduler.setBudgetOverrideForTesting(-1);
        MiningScheduler.beginTick(2L, 64);
        assertEquals(64, MiningScheduler.claim(64));
    }
}
