package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tier constants are the balance contract, so their ordering is tested directly. */
class TierProgressionTest {

    @Test
    void pipeTiersGetFasterAndCarryMore() {
        PipeTier[] tiers = PipeTier.values();
        assertEquals(4, tiers.length);
        int previousTravel = Integer.MAX_VALUE;
        int previousAmount = 0;
        for (PipeTier tier : tiers) {
            assertTrue(tier.travelTicks() >= 1, tier + " must spend at least one tick in a pipe");
            assertTrue(tier.travelTicks() < previousTravel, tier + " is not faster than the previous tier");
            int amount = tier.itemsPerTransfer(8);
            assertTrue(amount > previousAmount, tier + " does not carry more than the previous tier");
            assertTrue(amount <= 64, tier + " must not exceed one stack per transfer");
            previousTravel = tier.travelTicks();
            previousAmount = amount;
        }
        assertEquals(10, PipeTier.BASIC.travelTicks());
        assertEquals(1, PipeTier.ULTIMATE.travelTicks());
        assertEquals(8, PipeTier.BASIC.itemsPerTransfer(8));
        assertEquals(64, PipeTier.ULTIMATE.itemsPerTransfer(8));
    }

    @Test
    void pipeUpgradeAmountNeverOverflows() {
        for (PipeTier tier : PipeTier.values()) {
            assertTrue(tier.itemsPerTransfer(0) >= 1, tier + " must still move at least one item");
            assertEquals(64, tier.itemsPerTransfer(Integer.MAX_VALUE));
            assertTrue(tier.itemsPerTransfer(8) >= 1);
        }
    }

    @Test
    void cableTiersGetMoreThroughputAndBuffer() {
        CableTier[] tiers = CableTier.values();
        assertEquals(4, tiers.length);
        int previousThroughput = 0;
        int previousBuffer = 0;
        for (CableTier tier : tiers) {
            assertTrue(tier.throughput() > previousThroughput, tier + " is not faster than the previous tier");
            assertTrue(tier.bufferCapacity() >= tier.throughput(), tier + " buffer is smaller than one tick");
            assertTrue(tier.bufferCapacity() > previousBuffer, tier + " buffer did not grow");
            previousThroughput = tier.throughput();
            previousBuffer = tier.bufferCapacity();
        }
        assertEquals(128, CableTier.BASIC.throughput());
        assertEquals(8_192, CableTier.ULTIMATE.throughput());
        assertEquals(512, CableTier.BASIC.bufferCapacity());
        assertEquals(32_768, CableTier.ULTIMATE.bufferCapacity());
    }

    @Test
    void tierLookupClampsOutOfRangeIndices() {
        assertEquals(PipeTier.BASIC, PipeTier.byIndex(-5));
        assertEquals(PipeTier.ULTIMATE, PipeTier.byIndex(99));
        assertEquals(CableTier.BASIC, CableTier.byIndex(-5));
        assertEquals(CableTier.ULTIMATE, CableTier.byIndex(99));
    }
}
