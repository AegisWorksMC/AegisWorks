package com.industrialcore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 81: motor / battery inserts climb a low-to-high ladder and finish on a full stack. */
class Phase81SoundLadderTest {

    @Test
    void theLadderRisesFromLowToTheTopOfOneStack() {
        assertEquals(64, SoundRhythm.UPGRADE_STACK);
        float first = SoundRhythm.upgradePitch(1);
        float last = SoundRhythm.upgradePitch(SoundRhythm.UPGRADE_STACK);
        assertEquals(0.70F, first, 1.0E-4F);
        assertTrue(last > 1.9F && last <= 1.98F, "the top of the ladder is " + last);
        assertTrue(last > first * 2.0F, "the ladder did not climb two octaves-ish");
    }

    @Test
    void theLadderNeverDropsAtAnOctaveBoundary() {
        float previous = 0.0F;
        for (int count = 1; count <= SoundRhythm.UPGRADE_STACK; count++) {
            float pitch = SoundRhythm.upgradePitch(count);
            assertTrue(pitch >= previous, "insert " + count + " dropped from " + previous + " to " + pitch);
            previous = pitch;
        }
    }

    @Test
    void everyFourthInsertIsAudiblyHigher() {
        for (int count = 5; count <= SoundRhythm.UPGRADE_STACK; count += 4) {
            assertTrue(SoundRhythm.upgradePitch(count) > SoundRhythm.upgradePitch(count - 4),
                    "insert " + count + " is not higher than " + (count - 4));
        }
    }

    @Test
    void outOfRangeCountsAreClamped() {
        assertEquals(SoundRhythm.upgradePitch(1), SoundRhythm.upgradePitch(-5), 1.0E-6F);
        assertEquals(SoundRhythm.upgradePitch(1), SoundRhythm.upgradePitch(0), 1.0E-6F);
        assertEquals(SoundRhythm.upgradePitch(SoundRhythm.UPGRADE_STACK),
                SoundRhythm.upgradePitch(500), 1.0E-6F);
    }

    @Test
    void theFanfareOnlyPlaysAtAFullStack() {
        assertFalse(SoundRhythm.upgradeComplete(63));
        assertTrue(SoundRhythm.upgradeComplete(64));
        assertTrue(SoundRhythm.upgradeComplete(65));
    }

    @Test
    void ultraFastCompletionsAreThrottled() {
        // A 6 tick gate at 20 TPS means at most ~3 completion chords per second, however fast the
        // machine crafts; the queue cap keeps scheduled arpeggios bounded as well.
        assertEquals(6, SoundRhythm.COMPLETION_MIN_TICKS);
        assertTrue(SoundRhythm.MAX_SCHEDULED_NOTES <= 16);
        assertTrue(SoundRhythm.GUI_REACH_TICKS >= 2, "the reach pulse must never be faster than 2 ticks");
    }
}
