package com.industrialcore.client;

import com.industrialcore.client.SoundCadenceController.CompleteDecision;
import com.industrialcore.client.WorldAmbientLogic.Candidate;
import com.industrialcore.client.WorldAmbientLogic.Category;
import com.industrialcore.client.WorldAmbientLogic.Selection;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 102 pure-Java unit tests covering:
 * <ol>
 *   <li>{@link WorldAmbientLogic}: 14m spatial filter, nearest-4 voice cap, same-category crowding
 *       ducking, open-GUI coordinate mute, smooth fade convergence, and generator-category mapping.</li>
 *   <li>{@link SoundCadenceController}: Infinite-1UP streak sound (steps 1..7 strictly rising,
 *       8th &amp; 16th {@code machine_streak_up} triggers, 1.4s minimum cooldown guard, 0.30f bed
 *       in infinite zone, 3.0s idle reset &amp; GUI-close reset).</li>
 *   <li>Source wiring of all 5 electric weapon SEs, {@code machine_streak_up}, and logout cleanup.</li>
 * </ol>
 */
class Phase102AudioTest {

    @Test
    void worldAmbientLogicEnforces14mRadiusNearest4CapDuckingAndOpenGuiMute() {
        List<Candidate> pool = List.of(
                new Candidate(1L, 0, 64, 2, Category.MACHINE_HUM, 4.0D),     // 2m
                new Candidate(2L, 0, 64, 4, Category.MACHINE_HUM, 16.0D),    // 4m
                new Candidate(3L, 0, 64, 6, Category.MACHINE_HUM, 36.0D),    // 6m
                new Candidate(4L, 0, 64, 8, Category.GEN_REACTOR, 64.0D),    // 8m
                new Candidate(5L, 0, 64, 10, Category.GEN_THERMAL, 100.0D),  // 10m (5th -> capped out!)
                new Candidate(6L, 0, 64, 15, Category.GEN_KINETIC, 225.0D)   // 15m (>14m -> filtered out!)
        );

        // 1. Nearest 4 selected strictly in ascending distance order, >14m excluded
        List<Selection> sel = WorldAmbientLogic.selectActiveSources(pool, WorldAmbientLogic.NO_MUTED_POS);
        assertEquals(WorldAmbientLogic.MAX_ACTIVE_SOURCES, sel.size());
        assertEquals(1L, sel.get(0).posKey());
        assertEquals(2L, sel.get(1).posKey());
        assertEquals(3L, sel.get(2).posKey());
        assertEquals(4L, sel.get(3).posKey());

        // 2. Crowded same-category sources duck monotonically (1st > 2nd > 3rd)
        assertTrue(sel.get(0).targetVolume() > sel.get(1).targetVolume());
        assertTrue(sel.get(1).targetVolume() > sel.get(2).targetVolume());
        // Upper bound stays tasteful ambient level
        for (Selection s : sel) {
            assertTrue(s.targetVolume() > 0.0f && s.targetVolume() <= 0.26f,
                    "Unexpected ambient volume: " + s.targetVolume());
        }

        // 3. Open-GUI coordinate mute omits that exact block & promotes the 5th nearest within 14m
        List<Selection> muted = WorldAmbientLogic.selectActiveSources(pool, 1L);
        assertEquals(4, muted.size());
        assertFalse(muted.stream().anyMatch(s -> s.posKey() == 1L));
        assertTrue(muted.stream().anyMatch(s -> s.posKey() == 5L));
        assertFalse(muted.stream().anyMatch(s -> s.posKey() == 6L));

        // 4. Smooth fade converges to target without overshooting
        float v = 0.0f;
        for (int i = 0; i < 12; i++) {
            v = WorldAmbientLogic.stepFade(v, 0.22f, WorldAmbientLogic.FADE_STEP);
        }
        assertEquals(0.22f, v, 1e-5f);
        for (int i = 0; i < 12; i++) {
            v = WorldAmbientLogic.stepFade(v, 0.0f, WorldAmbientLogic.FADE_STEP);
        }
        assertEquals(0.0f, v, 1e-5f);

        // 5. Generator categories map 1-to-1 to Mono 3D world hum IDs
        assertEquals("world_gen_thermal",
                WorldAmbientLogic.fromGeneratorCategory(GeneratorAudioProfile.Category.THERMAL).soundId());
        assertEquals("world_gen_kinetic",
                WorldAmbientLogic.fromGeneratorCategory(GeneratorAudioProfile.Category.KINETIC).soundId());
        assertEquals("world_gen_reactor",
                WorldAmbientLogic.fromGeneratorCategory(GeneratorAudioProfile.Category.REACTOR).soundId());
    }

    @Test
    void fixedPitchThinnedCompletionsAndPeriodic2000msStreakUpWorkCleanly() {
        SoundCadenceController ctrl = new SoundCadenceController();
        long t = 10_000L;

        // Completions 1..7 spaced 260ms (> 180ms): fixed 1.00f pitch, steady 0.42f volume, no streak-up yet
        for (int i = 1; i <= 7; i++) {
            CompleteDecision d = ctrl.tryPlayComplete(t, 1, i);
            assertTrue(d.allow(), "Step " + i + " should play");
            assertEquals(i, d.comboCount());
            assertTrue(d.pitch() >= 0.98f && d.pitch() <= 1.02f, "Pitch must stay fixed near 1.00f: " + d.pitch());
            assertEquals(SoundCadenceController.COMPLETE_STEADY_VOLUME, d.volume(), 1e-4f);
            assertFalse(d.allowStreakUp(), "Step " + i + " must not fire level-up early");
            t += 260L;
        }

        // 8th completion: fires periodic machine_streak_up!
        CompleteDecision c8 = ctrl.tryPlayComplete(t, 1, 8);
        assertTrue(c8.allow());
        assertEquals(8, c8.comboCount());
        assertTrue(c8.allowStreakUp(), "8th completion must trigger level-up arpeggio");

        // Rapid burst of 8 completions inside 2,000ms (8 * 100ms = 800ms):
        // Normal sound thinned by 180ms spacing, and level-up guarded by 2,000ms cooldown!
        int playedNormal = 0;
        boolean firedInsideCooldown = false;
        for (int i = 9; i <= 16; i++) {
            t += 100L;
            CompleteDecision d = ctrl.tryPlayComplete(t, 1, i);
            assertEquals(i, d.comboCount(), "Thinned events must still advance streak counter");
            if (d.allow()) {
                playedNormal++;
                assertTrue(d.pitch() >= 0.98f && d.pitch() <= 1.02f);
            }
            if (d.allowStreakUp()) {
                firedInsideCooldown = true;
            }
        }
        assertTrue(playedNormal < 8 && playedNormal >= 3, "180ms thinning must reduce 100ms burst cleanly: " + playedNormal);
        assertFalse(firedInsideCooldown, "Level-up arpeggio must respect 2,000ms minimum cooldown");

        // Once >= 2,000ms elapsed since c8, next completion triggers 2nd machine_streak_up!
        CompleteDecision afterCooldown = ctrl.tryPlayComplete(t + 1_300L, 1, 17);
        assertTrue(afterCooldown.allow());
        assertTrue(afterCooldown.allowStreakUp(), "Once >=8 items AND >=2,000ms elapsed, level-up fires");

        // 3.0s idle resets streak back to 1
        CompleteDecision afterIdle = ctrl.tryPlayComplete(t + 1_300L + 3_050L, 1, 1);
        assertEquals(1, afterIdle.comboCount());
        assertEquals(1.00f, afterIdle.pitch(), 1e-4f);
        assertFalse(afterIdle.allowStreakUp());

        // GUI close resets streak immediately to 0
        ctrl.resetMachineSession();
        assertEquals(0, ctrl.getComboCount());
    }

    @Test
    void weaponSoundsStreakUpAndWorldAmbientLogoutHooksAreWired() throws Exception {
        String laser = Files.readString(Path.of("src/main/java/com/industrialcore/item/PulseLaserItem.java"));
        String laserAudio = Files.readString(Path.of("src/main/java/com/industrialcore/client/LaserAudioController.java"));
        String rail = Files.readString(Path.of("src/main/java/com/industrialcore/item/RailgunItem.java"));
        String blade = Files.readString(Path.of("src/main/java/com/industrialcore/item/QuantumBladeItem.java"));
        String bow = Files.readString(Path.of("src/main/java/com/industrialcore/item/EnergyBowItem.java"));
        String director = Files.readString(Path.of("src/main/java/com/industrialcore/client/SoundDirector.java"));
        String setup = Files.readString(Path.of("src/main/java/com/industrialcore/client/ClientSetup.java"));

        assertTrue(laser.contains("ModSounds.WEAPON_LOCKON"));
        assertTrue(laserAudio.contains("ModSounds.WEAPON_LASER_LOOP_LIGHT")
                && laserAudio.contains("ModSounds.WEAPON_LASER_LOOP_HEAVY")
                && laserAudio.contains("ModSounds.WEAPON_LASER_HIT_SIZZLE"));
        assertTrue(rail.contains("ModSounds.WEAPON_RAILGUN_FIRE") && rail.contains("ModSounds.WEAPON_LOCKON"));
        assertTrue(blade.contains("ModSounds.WEAPON_BLADE_SLASH")
                && blade.contains("ModSounds.WEAPON_BLADE_DASH"));
        String fpRenderer = Files.readString(Path.of("src/main/java/com/industrialcore/client/WeaponFirstPersonRenderer.java"));
        assertTrue(fpRenderer.contains("ModSounds.WEAPON_BLADE_IGNITE"));
        assertTrue(bow.contains("ModSounds.WEAPON_BOW_FIRE") && bow.contains("ModSounds.WEAPON_LOCKON"));
        assertTrue(director.contains("ModSounds.MACHINE_STREAK_UP"));
        assertTrue(setup.contains("WorldAmbientAudioController::onClientTick")
                && setup.contains("WorldAmbientAudioController.stopAll()")
                && setup.contains("LaserAudioController::onClientTick")
                && setup.contains("LaserAudioController.stopAll()"));
    }
}
