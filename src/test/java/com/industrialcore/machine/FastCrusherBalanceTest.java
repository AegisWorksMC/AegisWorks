package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balance contract of the Phase 23 fast crusher: identical FE per item compared with the normal
 * crusher, the recipe's cycle time per batch and an infrastructure that can actually feed it.
 *
 * <p>The constants below mirror the ore-block crushing recipes ({@code crush_*_ore.json}:
 * 120 ticks at 10 FE/t), which is what {@link ProcessingBalance#CRUSHER_TIME_TICKS} and
 * {@link ProcessingBalance#CRUSHER_ENERGY_PER_TICK} document as the machine default. The ore
 * line's raw entrance ({@code crush_raw_*.json}) ships at the unified 10 ticks instead, and the
 * fast crusher derives its cycle from whichever recipe it reads - the FE per item is identical to
 * the normal crusher in both cases (pinned in game by
 * {@code FastCrusherGameTests} and {@code QuantumCompressorGameTests}).</p>
 */
class FastCrusherBalanceTest {

    @Test
    void oneBatchCycleCostsExactlyWhatTheSameNumberOfSingleCrushesCost() {
        long perItem = ProcessingBalance.totalEnergy(
                ProcessingBalance.CRUSHER_TIME_TICKS, ProcessingBalance.CRUSHER_ENERGY_PER_TICK);
        assertEquals(1_200, perItem, "the normal crusher costs 1,200 FE per item");

        int batch = ProcessingBalance.FAST_CRUSH_BATCH;
        long cycle = (long) ProcessingBalance.CRUSHER_TIME_TICKS
                * ProcessingBalance.CRUSHER_ENERGY_PER_TICK * batch;
        assertEquals(perItem * batch, cycle,
                "a batch must cost exactly as much as the same number of single crushes");
        assertEquals(perItem, cycle / batch, "the FE per item must not change with the batch");
    }

    @Test
    void theMachineCanBufferAndReceiveOneFullCycle() {
        int batch = ProcessingBalance.FAST_CRUSH_BATCH;
        long cycleCost = (long) ProcessingBalance.CRUSHER_TIME_TICKS
                * ProcessingBalance.CRUSHER_ENERGY_PER_TICK * batch;
        assertTrue((long) ProcessingBalance.MACHINE_CAPACITY >= cycleCost,
                "the internal buffer must hold at least one full batch cycle");
        assertTrue((long) ProcessingBalance.MACHINE_MAX_RECEIVE
                        >= (long) ProcessingBalance.CRUSHER_ENERGY_PER_TICK * batch,
                "one cable must be able to deliver the batch FE per tick");
    }

    @Test
    void theBatchStaysInSafeBoundsAndUsesTheSharedRecipeFamily() {
        assertTrue(ProcessingBalance.FAST_CRUSH_BATCH >= 2,
                "a batch of one would just be the normal crusher");
        assertTrue(ProcessingBalance.FAST_CRUSH_BATCH <= 64,
                "a batch must never exceed one stack");
        assertNull(ProcessingKind.FAST_CRUSHER.processorFamily(),
                "the fast crusher must read the shared crushing recipes, not a new recipe type");
        assertTrue(ProcessingKind.FAST_CRUSHER.usesIndustrialRecipe(),
                "the fast crusher belongs to the mod's own single-input recipe machines");
    }

    @Test
    void theNewKindIsAppendedSoExistingOrdinalsNeverChange() {
        // Ordinals are read directly: reflective enum lookup would scan every declared method of
        // ProcessingKind, including the ones with Minecraft types that this test classpath lacks.
        int metalPress = ProcessingKind.METAL_PRESS.ordinal();
        int fastCrusher = ProcessingKind.FAST_CRUSHER.ordinal();
        assertTrue(fastCrusher > metalPress,
                "new kinds must be appended; inserting one would renumber saved indexes");
        assertEquals(ProcessingKind.values().length - 1, fastCrusher,
                "the fast crusher must be the last kind of this phase");
    }
}
