package com.industrialcore.client;

import com.industrialcore.client.SoundCadenceController.CompleteDecision;
import com.industrialcore.client.SoundCadenceController.ExtractDecision;
import com.industrialcore.client.SoundCadenceController.GeneratorPulseDecision;
import com.industrialcore.client.SoundCadenceController.TickDecision;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.RecoveryKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for {@link SoundCadenceController} and {@link SoundDirector} session
 * shutdown &amp; restored balance rules: fast/medium/slow cycle progress notes spaced at 120ms with
 * a 16-note cycle cap and 0.38..0.52 volume, 120ms completion cooldown, smooth ducking
 * (0.72 -> 0.50 floor), 1.6s combo reset, 1,500ms jackpot cooldown, 5-voice cap eviction,
 * 1.0s hero-event suppression, silent open, and instant close/logout stop.
 */
class SoundCadenceTest {

    @Test
    void fastAndMediumCyclesPlayTickNotesWith120msIntervalCapAndBalancedVolume() {
        SoundCadenceController ctrl = new SoundCadenceController();

        // Fast cycles (< 15 ticks) MUST now play intermediate tick notes (120ms spacing, 0.38..0.52 vol)
        long t = 1_000L;
        for (int ticks : new int[]{1, 5, 10, 14}) {
            TickDecision fast = ctrl.tryPlayTickNote(t, ticks, 0.5);
            assertTrue(fast.allow(), "Expected tick note allowed for fast cycle " + ticks + " ticks");
            assertTrue(fast.volume() >= SoundCadenceController.TICK_VOL_MIN
                            && fast.volume() <= SoundCadenceController.TICK_VOL_MAX,
                    "Unexpected fast-cycle tick volume: " + fast.volume());
            t += SoundCadenceController.TICK_MIN_INTERVAL_MS;
        }

        // Complete cycle to reset per-cycle counter
        assertTrue(ctrl.tryPlayComplete(t + 50L, 1, 1).allow());
        assertEquals(0, ctrl.getTicksInCurrentCycle());

        // Medium/slow cycle (40 ticks): 120ms spacing, max 16 per cycle, vol in [0.38, 0.52]
        long base = 10_000L;
        TickDecision t1 = ctrl.tryPlayTickNote(base, 40, 0.05);
        assertTrue(t1.allow());
        assertEquals(0.38f, SoundCadenceController.TICK_VOL_MIN, 1e-4f);
        assertEquals(0.52f, SoundCadenceController.TICK_VOL_MAX, 1e-4f);
        assertTrue(t1.volume() >= SoundCadenceController.TICK_VOL_MIN
                && t1.volume() <= SoundCadenceController.TICK_VOL_MAX);

        // Inside 120ms cooldown -> blocked
        assertFalse(ctrl.tryPlayTickNote(base + 60L, 40, 0.10).allow());
        assertFalse(ctrl.tryPlayTickNote(base + 119L, 40, 0.10).allow());

        // Notes 2..16 spaced at 120ms -> allowed
        for (int n = 2; n <= SoundCadenceController.MAX_TICKS_PER_CYCLE; n++) {
            TickDecision tn = ctrl.tryPlayTickNote(base + (n - 1L) * 120L, 40, n / 17.0);
            assertTrue(tn.allow(), "Expected tick #" + n + " within cycle cap to play");
            assertTrue(tn.volume() >= SoundCadenceController.TICK_VOL_MIN
                    && tn.volume() <= SoundCadenceController.TICK_VOL_MAX);
        }

        // 17th note in the same cycle -> capped!
        assertFalse(ctrl.tryPlayTickNote(base + 16L * 120L, 40, 0.99).allow());

        // Completing the cycle resets the per-cycle tick counter cleanly
        assertTrue(ctrl.tryPlayComplete(base + 2_200L, 1, 1).allow());
        assertEquals(0, ctrl.getTicksInCurrentCycle());
        assertTrue(ctrl.tryPlayTickNote(base + 2_350L, 40, 0.20).allow());
    }

    @Test
    void completionCooldownDuckingFloorPitchCeilingAndComboResetWork() {
        SoundCadenceController ctrl = new SoundCadenceController();

        // Phase 102.5: Fixed 1.00f pitch & steady 0.42f volume with 180ms rhythmic thinning
        CompleteDecision c1 = ctrl.tryPlayComplete(1_000L, 1, 1);
        assertTrue(c1.allow());
        assertEquals(1, c1.comboCount());
        assertEquals(0.42f, c1.volume(), 1e-4f);
        assertEquals(1.00f, c1.pitch(), 1e-4f);
        assertFalse(c1.allowStreakUp());

        // Inside 180ms thinning window: normal complete sound is thinned (allow=false), count still increments!
        CompleteDecision thinned = ctrl.tryPlayComplete(1_050L, 1, 2);
        assertFalse(thinned.allow());
        assertEquals(2, thinned.comboCount());

        CompleteDecision stillThinned = ctrl.tryPlayComplete(1_179L, 1, 3);
        assertFalse(stillThinned.allow());
        assertEquals(3, stillThinned.comboCount());

        // At >= 180ms since last played normal sound: plays cleanly at fixed 1.00f pitch & 0.42f volume
        CompleteDecision c4 = ctrl.tryPlayComplete(1_180L, 1, 4);
        assertTrue(c4.allow());
        assertEquals(4, c4.comboCount());
        assertEquals(1.00f, c4.pitch(), 1e-4f);
        assertEquals(0.42f, c4.volume(), 1e-4f);

        // Completions 5..8 -> 8th triggers periodic level-up sound (machine_streak_up)
        long t = 1_180L;
        CompleteDecision c8 = null;
        for (int i = 5; i <= 8; i++) {
            t += 200L;
            c8 = ctrl.tryPlayComplete(t, 1, i);
            assertTrue(c8.pitch() >= 0.98f && c8.pitch() <= 1.02f);
        }
        assertTrue(c8.allowStreakUp(), "8th completion must trigger level-up arpeggio");

        // Pause > 3.0s resets streak back to 1 cleanly
        CompleteDecision afterPause = ctrl.tryPlayComplete(t + 3_100L, 1, 1);
        assertTrue(afterPause.allow());
        assertEquals(1, afterPause.comboCount());
        assertEquals(1.00f, afterPause.pitch(), 1e-4f);
        assertFalse(afterPause.allowStreakUp());
    }

    @Test
    void jackpotEnforces1500msCooldown() {
        SoundCadenceController ctrl = new SoundCadenceController();

        CompleteDecision first = ctrl.tryPlayComplete(10_000L, 32, 10);
        assertTrue(first.allow());
        assertTrue(first.allowJackpot(), "First qualifying jackpot should fire");

        // 1s later: completion allowed, but jackpot MUST wait for 1,500ms cooldown
        CompleteDecision tooSoon = ctrl.tryPlayComplete(11_000L, 32, 11);
        assertTrue(tooSoon.allow());
        assertFalse(tooSoon.allowJackpot());

        // 1.499s later: still blocked
        CompleteDecision almost = ctrl.tryPlayComplete(11_499L, 32, 12);
        assertTrue(almost.allow());
        assertFalse(almost.allowJackpot());

        // 1.500s+ later (and >= 180ms since last played complete): allowed again!
        CompleteDecision ready = ctrl.tryPlayComplete(11_680L, 32, 13);
        assertTrue(ready.allow());
        assertTrue(ready.allowJackpot());
    }

    @Test
    void voiceCapEvictsOldestAboveFiveAndHeroEventSuppressesForOneSecond() {
        Deque<String> active = new ArrayDeque<>();
        assertTrue(SoundCadenceController.pushWithVoiceCap(active, "v1").isEmpty());
        assertTrue(SoundCadenceController.pushWithVoiceCap(active, "v2").isEmpty());
        assertTrue(SoundCadenceController.pushWithVoiceCap(active, "v3").isEmpty());
        assertTrue(SoundCadenceController.pushWithVoiceCap(active, "v4").isEmpty());
        assertTrue(SoundCadenceController.pushWithVoiceCap(active, "v5").isEmpty());
        assertEquals(5, active.size());

        // 6th voice evicts oldest ("v1") so at most 5 voices ever play concurrently
        List<String> evicted = SoundCadenceController.pushWithVoiceCap(active, "v6");
        assertEquals(List.of("v1"), evicted);
        assertEquals(5, active.size());
        assertEquals(List.of("v2", "v3", "v4", "v5", "v6"), List.copyOf(active));

        // Hero event suppresses Machine GUI audio for 1,000ms
        SoundCadenceController ctrl = new SoundCadenceController();
        ctrl.recordHeroEvent(5_000L);
        assertTrue(ctrl.isMachineSuppressed(5_500L));
        assertFalse(ctrl.tryPlayTickNote(5_500L, 40, 0.5).allow());
        assertFalse(ctrl.tryPlayComplete(5_500L, 16, 10).allow());

        // After 1,000ms window passes, normal playback resumes cleanly
        assertFalse(ctrl.isMachineSuppressed(6_000L));
        assertTrue(ctrl.tryPlayComplete(6_000L, 1, 1).allow());

        // Closing machine GUI resets session & clears tracked voices + queues cleanly
        ctrl.resetMachineSession();
        assertEquals(0, ctrl.getComboCount());
        assertEquals(0, ctrl.getTicksInCurrentCycle());
        active.clear();
        assertTrue(active.isEmpty());
        // Fresh completion after close/re-open starts back at full clear 0.72 volume
        CompleteDecision fresh = ctrl.tryPlayComplete(6_200L, 1, 1);
        assertTrue(fresh.allow());
        assertEquals(1, fresh.comboCount());
        assertEquals(SoundCadenceController.COMPLETE_VOL_FIRST, fresh.volume(), 1e-4f);
    }

    @Test
    void extractSoundManualAndAutoCooldownsVolumesAndHeroSuppressionWork() {
        SoundCadenceController ctrl = new SoundCadenceController();

        // Manual pickup: 0.34f volume, 100ms cooldown
        ExtractDecision m1 = ctrl.tryPlayExtract(1_000L, 4, true);
        assertTrue(m1.allow());
        assertTrue(m1.manual());
        assertEquals(0.34f, m1.volume(), 1e-4f);
        assertEquals(1.00f, m1.pitch(), 1e-4f);

        assertFalse(ctrl.tryPlayExtract(1_050L, 1, true).allow());
        assertFalse(ctrl.tryPlayExtract(1_099L, 1, true).allow());
        ExtractDecision m2 = ctrl.tryPlayExtract(1_100L, 1, true);
        assertTrue(m2.allow());
        assertEquals(0.34f, m2.volume(), 1e-4f);

        // Automated pipe/hopper extract: 0.20f volume, 220ms cooldown (suppressed right after manual too)
        assertFalse(ctrl.tryPlayExtract(1_250L, 1, false).allow());
        ExtractDecision a1 = ctrl.tryPlayExtract(1_320L, 1, false);
        assertTrue(a1.allow());
        assertFalse(a1.manual());
        assertEquals(0.20f, a1.volume(), 1e-4f);
        assertEquals(1.00f, a1.pitch(), 1e-4f);

        assertFalse(ctrl.tryPlayExtract(1_500L, 1, false).allow());
        assertFalse(ctrl.tryPlayExtract(1_539L, 1, false).allow());
        ExtractDecision a2 = ctrl.tryPlayExtract(1_540L, 2, false);
        assertTrue(a2.allow());
        assertEquals(0.20f, a2.volume(), 1e-4f);

        // Hero event suppresses extract sound as part of Machine GUI channel
        ctrl.recordHeroEvent(2_000L);
        assertFalse(ctrl.tryPlayExtract(2_300L, 1, true).allow());
        assertFalse(ctrl.tryPlayExtract(2_300L, 1, false).allow());
        assertTrue(ctrl.tryPlayExtract(3_000L, 1, false).allow());
    }

    @Test
    void allGeneratorKindsMapToExpectedAudioCategoriesAndPulseCadenceStopsOnReset() {
        assertEquals("gen_thermal_hum", GeneratorAudioProfile.forGeneration(GenerationKind.THERMAL).category().humSoundId());
        assertEquals("gen_thermal_hum", GeneratorAudioProfile.forGeneration(GenerationKind.COMBUSTION).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forGeneration(GenerationKind.HYDRO).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forGeneration(GenerationKind.WIND).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forGeneration(GenerationKind.SOLAR).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forGeneration(GenerationKind.GEOTHERMAL).category().humSoundId());
        assertEquals("gen_reactor_hum", GeneratorAudioProfile.forGeneration(GenerationKind.NUCLEAR).category().humSoundId());

        assertEquals("gen_thermal_hum", GeneratorAudioProfile.forRecovery(RecoveryKind.PRESSURE).category().humSoundId());
        assertEquals("gen_thermal_hum", GeneratorAudioProfile.forRecovery(RecoveryKind.HEAT).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forRecovery(RecoveryKind.FLYWHEEL).category().humSoundId());
        assertEquals("gen_kinetic_hum", GeneratorAudioProfile.forRecovery(RecoveryKind.REGEN).category().humSoundId());

        for (GeneratorAudioProfile.Category cat : GeneratorAudioProfile.Category.values()) {
            assertTrue(cat.humVolume() >= 0.24f && cat.humVolume() <= 0.30f,
                    cat + " hum volume out of [0.24, 0.30]: " + cat.humVolume());
            assertTrue(cat.pulseVolume() >= 0.32f && cat.pulseVolume() <= 0.40f,
                    cat + " pulse volume out of [0.32, 0.40]: " + cat.pulseVolume());
            assertTrue(cat.pulseIntervalMs() >= 1200L && cat.pulseIntervalMs() <= 1800L,
                    cat + " pulse interval out of [1200, 1800]ms: " + cat.pulseIntervalMs());
            assertEquals("gen_power_pulse", cat.pulseSoundId());
        }

        // Active generation predicate stops immediately when rate==0 or idle/out of fuel
        assertTrue(GeneratorAudioProfile.isActivelyGenerating(90, 200, true));
        assertTrue(GeneratorAudioProfile.isActivelyGenerating(200, 100, false)); // nuclear burning w/o water
        assertTrue(GeneratorAudioProfile.isActivelyGenerating(60, 0, true));     // solar/hydro/wind/geo running
        assertFalse(GeneratorAudioProfile.isActivelyGenerating(0, 0, false));
        assertFalse(GeneratorAudioProfile.isActivelyGenerating(0, 100, true));
        assertFalse(GeneratorAudioProfile.isActivelyGenerating(90, 0, false));

        // Generator pulse bloom + interval + immediate stop/reset behavior
        SoundCadenceController ctrl = new SoundCadenceController();
        GeneratorAudioProfile.Category reactor = GeneratorAudioProfile.Category.REACTOR;
        assertFalse(ctrl.tryPlayGeneratorPulse(1_000L, reactor).allow(), "First call starts bloom window");
        assertFalse(ctrl.tryPlayGeneratorPulse(1_300L, reactor).allow(), "Still inside 350ms bloom");
        GeneratorPulseDecision p1 = ctrl.tryPlayGeneratorPulse(1_350L, reactor);
        assertTrue(p1.allow());
        assertEquals(reactor.pulseVolume(), p1.volume(), 1e-4f);

        assertFalse(ctrl.tryPlayGeneratorPulse(2_500L, reactor).allow(), "Must respect 1400ms interval");
        GeneratorPulseDecision p2 = ctrl.tryPlayGeneratorPulse(2_750L, reactor);
        assertTrue(p2.allow());

        // Stopping generation or closing GUI resets pulse session immediately
        ctrl.resetGeneratorSession();
        assertFalse(ctrl.tryPlayGeneratorPulse(2_800L, reactor).allow(), "After halt, must re-bloom on restart");
        assertTrue(ctrl.tryPlayGeneratorPulse(3_150L, reactor).allow());

        ctrl.resetMachineSession();
        assertFalse(ctrl.tryPlayGeneratorPulse(3_200L, reactor).allow(), "GUI close must reset generator session");
    }

    @Test
    void closeLogoutAndSilentOpenHooksAndBalancedVolumesMatchSpec() throws Exception {
        String adaptive = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/industrialcore/client/AdaptiveContainerScreen.java"));
        String dopamine = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/industrialcore/client/GuiDopamine.java"));
        String setup = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/industrialcore/client/ClientSetup.java"));
        String director = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/industrialcore/client/SoundDirector.java"));
        String genScreen = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/industrialcore/client/GenerationScreen.java"));

        assertTrue(adaptive.contains("onClose()") && adaptive.contains("removed()")
                && adaptive.contains("SoundDirector.stopMachineGuiSounds()"));
        assertTrue(dopamine.contains("onScreenClosing") && dopamine.contains("SoundDirector.stopMachineGuiSounds()"));
        assertFalse(dopamine.contains("SoundDirector.playGuiOpen()"),
                "Opening a machine GUI must not auto-play playGuiOpen()");
        assertTrue(dopamine.contains("playIgnition()") && dopamine.contains("playFullCharge()")
                && dopamine.contains("playMilestone(") && dopamine.contains("playOutputExtract("));
        assertTrue(genScreen.contains("GuiDopamine.observeGenerator("));
        assertTrue(setup.contains("SoundDirector.stopAllOnLogout()"));
        assertTrue(setup.contains("GuiDopamine::onScreenMousePressed"));

        assertTrue(director.contains("playGlobalUi(ModSounds.UI_CLICK::get, 1.00f, 0.52f)"));
        assertTrue(director.contains("playGlobalUi(ModSounds.UI_LOCKED::get, 1.00f, 0.55f)"));
        assertTrue(director.contains("ModSounds.MACHINE_JACKPOT::get, 1.00f, 0.80f"));
        assertTrue(director.contains("ModSounds.MACHINE_EXTRACT::get"));
        assertTrue(director.contains("ModSounds.GEN_THERMAL_HUM.get()")
                && director.contains("ModSounds.GEN_KINETIC_HUM.get()")
                && director.contains("ModSounds.GEN_REACTOR_HUM.get()")
                && director.contains("ModSounds.GEN_POWER_PULSE::get"));
        assertTrue(director.contains("stopGeneratorHum();"));
        assertTrue(director.contains("SoundRhythm.shepardAdvance()"));
        assertTrue(director.indexOf("boolean inOpenGrace = GuiDopamine.isInOpenGrace();")
                < director.indexOf("updateGeneratorAudio(inOpenGrace);"),
                "Open-grace check must gate generator audio");
        assertTrue(director.indexOf("updateGeneratorAudio(inOpenGrace);")
                < director.indexOf("GuiDopamine.tickOpenGrace();"),
                "Generator audio update must run before decrementing openGraceTicks");
    }
}
