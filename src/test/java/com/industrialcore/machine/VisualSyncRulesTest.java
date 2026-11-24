package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the pipe visual-update throttle. */
class VisualSyncRulesTest {

    @Test
    void emptyAndUnchangedBlocksNeverSync() {
        for (long now = 0; now <= 500; now += 7) {
            assertFalse(VisualSyncRules.dueForSync(now, now - 5, false, false, 2, 10),
                    "an empty, unchanged block must not send at tick " + now);
        }
    }

    @Test
    void firstChangeOrFirstContentIsSentImmediately() {
        assertTrue(VisualSyncRules.dueForSync(0, -1, true, false, 2, 10));
        assertTrue(VisualSyncRules.dueForSync(0, -1, false, true, 2, 10));
        assertFalse(VisualSyncRules.dueForSync(0, -1, false, false, 2, 10));
    }

    @Test
    void minimumIntervalThrottlesBursts() {
        assertFalse(VisualSyncRules.dueForSync(10, 9, true, true, 2, 10), "1 tick is below the interval");
        assertTrue(VisualSyncRules.dueForSync(11, 9, true, true, 2, 10), "2 ticks reach the interval");
        assertTrue(VisualSyncRules.dueForSync(10, 9, true, true, 1, 10));
        assertFalse(VisualSyncRules.dueForSync(0, 0, true, true, 0, 10));
        assertTrue(VisualSyncRules.dueForSync(1, 0, true, true, -3, 10));
    }

    @Test
    void periodicRefreshCanBeDisabled() {
        for (int tick = 0; tick < 500; tick += 3) {
            assertFalse(VisualSyncRules.dueForSync(tick, 0, false, true, 2, 0),
                    "a pipe must not refresh on a timer");
            assertFalse(VisualSyncRules.dueForSync(tick, 0, false, true, 2, -5));
        }
        assertTrue(VisualSyncRules.dueForSync(50, 0, true, true, 2, 0), "a change is still sent");
    }
}
