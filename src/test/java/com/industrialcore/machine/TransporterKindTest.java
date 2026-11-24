package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the four-tier transporter table (Phases 26-29).
 *
 * <p>These numbers <em>are</em> the phase specification, so this is the test that fails when a
 * tier silently becomes a clone of another one: the average throughput must strictly increase
 * from 26 to 29, every tier must charge 0 FE for a transfer and lose 0 percent of moved FE, and
 * only the last two tiers may have an FE buffer at all.</p>
 */
class TransporterKindTest {

    @Test
    void theFourTiersKeepTheirSpecifiedPerformance() {
        assertEquals(20, TransporterKind.BASIC.transferIntervalTicks(), "Phase 26: every 20 ticks");
        assertEquals(1, TransporterKind.BASIC.maxItemsPerTransfer(), "Phase 26: 1 item per run");
        assertEquals(2, TransporterKind.BASIC.slotCount());
        assertEquals(3, TransporterKind.BASIC.structureSize(), "Phase 26: 3x3x3 shell");

        assertEquals(5, TransporterKind.ADVANCED.transferIntervalTicks(), "Phase 27: every 5 ticks");
        assertEquals(64, TransporterKind.ADVANCED.maxItemsPerTransfer(), "Phase 27: 64 items");
        assertEquals(8, TransporterKind.ADVANCED.slotCount());
        assertEquals(4, TransporterKind.ADVANCED.structureSize(), "Phase 27: 4x4x4 shell");

        assertEquals(1, TransporterKind.QUANTUM.transferIntervalTicks(), "Phase 28: every tick");
        assertEquals(64, TransporterKind.QUANTUM.maxItemsPerTransfer(),
                "Phase 28: one stack per tick");
        assertEquals(16, TransporterKind.QUANTUM.slotCount());
        assertEquals(0, TransporterKind.QUANTUM.structureSize(), "Phase 28: single block");

        assertEquals(1, TransporterKind.ARRAY.transferIntervalTicks(), "Phase 29: every tick");
        assertEquals(4096, TransporterKind.ARRAY.maxItemsPerTransfer(),
                "Phase 29: 4096 items per tick");
        assertEquals(64, TransporterKind.ARRAY.slotCount(), "64 stacks = one full batch");
        assertEquals(5, TransporterKind.ARRAY.structureSize(), "Phase 29: 5x5x5 shell");
    }

    @Test
    void transfersNeverCostFeAndNeverLoseAPercent() {
        for (TransporterKind kind : TransporterKind.values()) {
            assertEquals(0, kind.feCostPerTransfer(),
                    kind + " must not charge FE for a transfer");
            assertEquals(TransporterKind.FE_COST_PER_TRANSFER, kind.feCostPerTransfer());
            assertEquals(0, kind.powerLossPercent(), kind + " must not lose FE");
            assertEquals(TransporterKind.POWER_LOSS_PERCENT, kind.powerLossPercent());
        }
    }

    @Test
    void onlyTheLastTwoTiersTouchFeAtAll() {
        assertFalse(TransporterKind.BASIC.supportsPower());
        assertFalse(TransporterKind.ADVANCED.supportsPower());
        assertEquals(0, TransporterKind.BASIC.powerTransferPerTick());
        assertEquals(0, TransporterKind.ADVANCED.energyCapacity());

        assertTrue(TransporterKind.QUANTUM.supportsPower());
        assertEquals(50_000, TransporterKind.QUANTUM.powerTransferPerTick());
        assertEquals(100_000, TransporterKind.QUANTUM.energyCapacity());
        assertEquals(50_000, TransporterKind.QUANTUM.energyMaxReceive());
        assertEquals(50_000, TransporterKind.QUANTUM.powerPushPerTick());

        assertTrue(TransporterKind.ARRAY.supportsPower());
        assertEquals(1_000_000, TransporterKind.ARRAY.powerTransferPerTick(),
                "Phase 29: one million FE per tick");
        assertEquals(2_000_000, TransporterKind.ARRAY.energyCapacity());
        assertEquals(1_000_000, TransporterKind.ARRAY.energyMaxReceive());
        assertEquals(1_000_000, TransporterKind.ARRAY.powerPushPerTick());
    }

    @Test
    void everyTierIsStrictlyFasterThanThePreviousOne() {
        TransporterKind[] progression = {
                TransporterKind.BASIC, TransporterKind.ADVANCED,
                TransporterKind.QUANTUM, TransporterKind.ARRAY};
        double previous = 0;
        for (TransporterKind kind : progression) {
            double itemsPerTick = (double) kind.maxItemsPerTransfer()
                    / kind.transferIntervalTicks();
            assertTrue(itemsPerTick > previous,
                    kind + " must move more items per tick than the previous tier");
            previous = itemsPerTick;
        }
        assertTrue(previous >= 4096, "the top tier moves at least 4096 items per tick");
    }

    @Test
    void powerTiersGetABufferThatCoversTheirThroughput() {
        for (TransporterKind kind : TransporterKind.values()) {
            if (!kind.supportsPower()) {
                continue;
            }
            assertTrue(kind.energyCapacity() >= kind.powerTransferPerTick(),
                    kind + " must be able to hold at least one full batch");
            assertEquals(kind.powerTransferPerTick(), kind.powerPushPerTick(),
                    kind + " pushes out at its transfer rate so nothing pools inside");
        }
    }

    @Test
    void gridRowsFollowTheEightColumnBufferGrid() {
        assertEquals(1, TransporterKind.BASIC.gridRows());
        assertEquals(1, TransporterKind.ADVANCED.gridRows());
        assertEquals(2, TransporterKind.QUANTUM.gridRows());
        assertEquals(8, TransporterKind.ARRAY.gridRows());
    }

    @Test
    void machineIdsRoundTripAndUnknownIdsStayNull() {
        for (TransporterKind kind : TransporterKind.values()) {
            assertEquals(kind, TransporterKind.byMachineId(kind.machineId()));
        }
        assertNull(TransporterKind.byMachineId("quantum_compressor"),
                "a non transporter id must not resolve to a tier");
        assertNull(TransporterKind.byMachineId(""));
    }
}
