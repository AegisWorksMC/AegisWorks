package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.audio.WorldJoinAudioGuardLogic;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.LivingFactoryAnimLogic.TurretAimState;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.raid.DimensionalRaidLogic.RewardBundle;
import com.industrialcore.raid.DimensionalRaidLogic.StartDenial;
import com.industrialcore.raid.DimensionalRaidLogic.WaveSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 116 verification suite:
 * - 3-layer WorldJoinAudioGuardLogic (60-tick mute, silent initial suit/blade sync, 2/tick + 6-tick cooldown limiter)
 * - Laser turret last-aim orientation retention (zero snap-back to default facing after target elimination)
 * - RPG Depth Tier 1..10 unlocking, wave composition, HP/ATK/speed scaling &amp; Tier-scaled reward progression
 * - 4 original Dimensional Rift entities registration, textures, AI classes &amp; zero-block-destruction guarantees
 */
class Phase116RiftEntitiesAndAudioTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/industrialcore");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void worldJoinAudioGuardBlocksFirst60TicksSilencesInitialSuitSyncAndEnforcesGlobalLimiter() {
        WorldJoinAudioGuardLogic guard = new WorldJoinAudioGuardLogic();
        guard.onWorldJoinOrDimensionChange();
        assertTrue(guard.isInJoinGrace());
        assertEquals(60, guard.graceRemainingTicks());

        // Layer 2: Initial login sync while already wearing full Virtual Aegis & holding Quantum Blade MUST be silent
        assertFalse(guard.shouldPlaySuitBootSound(true), "Initial suit sync on login must be silent");
        assertFalse(guard.shouldPlayBladeIgniteSound(true), "Initial blade sync on login must be silent");

        // Layer 1: All Mod sounds and vanilla armor equip sounds blocked for entire 60-tick grace window
        for (int t = 0; t < 60; t++) {
            assertFalse(guard.tryAllowSound("industrialcore:machine_crusher_jaw", true, false));
            assertFalse(guard.tryAllowSound("minecraft:item.armor.equip_netherite", false, true));
            // Unrelated vanilla sounds (e.g. footsteps) remain allowed
            assertTrue(guard.tryAllowSound("minecraft:block.stone.step", false, false));
            guard.tick();
        }
        assertFalse(guard.isInJoinGrace());
        assertTrue(guard.joinMutedCount() >= 120);
        assertEquals(0, guard.joinPlayedCount());

        // After grace: manual unequip -> re-equip triggers suit boot & blade ignite cleanly
        assertFalse(guard.shouldPlaySuitBootSound(false));
        assertTrue(guard.shouldPlaySuitBootSound(true), "Manual re-equip after grace should play suit boot SE");
        assertFalse(guard.shouldPlayBladeIgniteSound(false));
        assertTrue(guard.shouldPlayBladeIgniteSound(true), "Manual blade switch after grace should play ignite SE");

        // Layer 3: Global limiter — max 2 Mod sounds per tick & min 6 ticks per identical sound ID
        assertTrue(guard.tryAllowSound("industrialcore:sound_a", true, false));
        assertTrue(guard.tryAllowSound("industrialcore:sound_b", true, false));
        assertFalse(guard.tryAllowSound("industrialcore:sound_c", true, false), "3rd Mod sound in same tick must be blocked");

        // Next tick: different IDs allowed, but sound_a still inside 6-tick cooldown
        guard.tick();
        assertFalse(guard.tryAllowSound("industrialcore:sound_a", true, false), "Identical sound ID must respect 6-tick cooldown");
        assertTrue(guard.tryAllowSound("industrialcore:sound_c", true, false));

        // After 5 more ticks (total 6 ticks elapsed since sound_a): sound_a allowed again
        for (int i = 0; i < 5; i++) {
            guard.tick();
        }
        assertTrue(guard.tryAllowSound("industrialcore:sound_a", true, false));
    }

    @Test
    void laserTurretRetainsLastEngagedOrientationWithoutSnappingBackToDefaultFacing() {
        TurretAimState state = new TurretAimState();
        float defaultFacingYaw = 0.0F; // NORTH
        float targetYaw = 135.0F;      // Opposite diagonal direction
        float targetPitch = 22.0F;

        // Lock onto enemy at 135 deg yaw, +22 deg pitch for 25 frames
        for (int i = 0; i < 25; i++) {
            LivingFactoryAnimLogic.stepTurretAim(state, defaultFacingYaw, true, targetYaw, targetPitch, i * 0.05F);
        }
        assertTrue(state.hasEverLocked);
        assertEquals(targetYaw, state.lastAimYaw, 1.0E-4F);
        assertEquals(targetPitch, state.lastAimPitch, 1.0E-4F);
        assertTrue(Math.abs(state.yaw - targetYaw) < 1.5F, "Turret should converge onto target yaw");

        // Target dies (locked = false) — simulate 120 idle frames and confirm ZERO snap-back to 0 deg!
        for (int i = 0; i < 120; i++) {
            LivingFactoryAnimLogic.stepTurretAim(state, defaultFacingYaw, false, 0.0F, 0.0F, i * 0.1F);
            float diffFromLast = Math.abs(((state.yaw - targetYaw + 540.0F) % 360.0F) - 180.0F);
            assertTrue(diffFromLast <= LivingFactoryAnimLogic.TURRET_HOLD_SWEEP_DEG + 1.5F,
                    "Turret drifted away from lastAimYaw toward default facing! yaw=" + state.yaw);
            assertEquals(targetPitch, state.pitch, 1.5F, "Turret pitch must remain held at lastAimPitch");
        }
    }

    @Test
    void rpgDepthTierProgressionWavesScalingAndRewardsWorkAcrossTier1To10() {
        assertEquals(1, DimensionalRaidLogic.MIN_TIER);
        assertEquals(10, DimensionalRaidLogic.MAX_TIER);

        // Sequential tier unlock progression 1 -> 2 -> ... -> 10 (capped at 10)
        PlayerMachineProgress progress = new PlayerMachineProgress();
        assertEquals(1, progress.getUnlockedRiftTier());
        for (int t = 1; t < 10; t++) {
            assertTrue(progress.recordRiftTierCleared(t), "Clearing current max tier " + t + " must unlock " + (t + 1));
            assertEquals(t + 1, progress.getUnlockedRiftTier());
        }
        assertFalse(progress.recordRiftTierCleared(10), "Tier 10 is max; clearing again must stay 10");
        assertEquals(10, progress.getUnlockedRiftTier());

        // Tier lock check in StartDenial
        assertEquals(StartDenial.TIER_LOCKED,
                DimensionalRaidLogic.checkStart(true, true, false, 0, 10_000_000L, 500_000L, 4, 2));
        assertEquals(StartDenial.ALLOWED,
                DimensionalRaidLogic.checkStart(true, true, false, 0, 10_000_000L, 500_000L, 2, 2));

        // Monotonic HP, Attack, Speed, Cost & Reward scaling across Tier 1..10
        for (int t = 1; t < 10; t++) {
            assertTrue(DimensionalRaidLogic.tierHealthMultiplier(t + 1) > DimensionalRaidLogic.tierHealthMultiplier(t));
            assertTrue(DimensionalRaidLogic.tierAttackMultiplier(t + 1) > DimensionalRaidLogic.tierAttackMultiplier(t));
            assertTrue(DimensionalRaidLogic.tierSpeedMultiplier(t + 1) > DimensionalRaidLogic.tierSpeedMultiplier(t));
            assertTrue(DimensionalRaidLogic.activationCostForTier(500_000L, t + 1)
                    > DimensionalRaidLogic.activationCostForTier(500_000L, t));
            RewardBundle rCurr = DimensionalRaidLogic.victoryRewards(t, 1.0D);
            RewardBundle rNext = DimensionalRaidLogic.victoryRewards(t + 1, 1.0D);
            assertTrue(rNext.specializationModules() > rCurr.specializationModules());
            assertTrue(rNext.machineXp() > rCurr.machineXp());
        }

        // Endgame Tier 10 rewards hit specification targets (16..32 Spec Modules, 50K..250K XP)
        RewardBundle t10 = DimensionalRaidLogic.victoryRewards(10, 1.0D);
        assertTrue(t10.specializationModules() >= 16 && t10.specializationModules() <= 32);
        assertTrue(t10.machineXp() >= 200_000L && t10.machineXp() <= 300_000L);

        // Wave composition: Phase Juggernaut joins at Tier 3+; Boss always on Wave 3
        WaveSpec t1w2 = DimensionalRaidLogic.waveSpec(2, 1);
        WaveSpec t3w2 = DimensionalRaidLogic.waveSpec(2, 3);
        WaveSpec t10w3 = DimensionalRaidLogic.waveSpec(3, 10);
        assertEquals(0, t1w2.juggernautCount());
        assertTrue(t3w2.juggernautCount() >= 1, "Phase Juggernaut must spawn from Tier 3+");
        assertTrue(t10w3.hasBoss(), "Wave 3 must summon Dimensional Sovereign");

        // Singularity Reactor Tier Selector buttons sit inside RIFT_CARD without overlapping RIFT_BUTTON
        Rect card = SingularityReactorLayout.RIFT_CARD;
        Rect prev = SingularityReactorLayout.RIFT_TIER_PREV;
        Rect next = SingularityReactorLayout.RIFT_TIER_NEXT;
        Rect btn = SingularityReactorLayout.RIFT_BUTTON;
        assertTrue(prev.x() > card.x() && next.right() < card.right());
        assertFalse(prev.intersects(next));
        assertFalse(prev.intersects(btn));
        assertFalse(next.intersects(btn));
    }

    @Test
    void fourOriginalRiftEntitiesHaveRegistrationTexturesRenderersAndBilingualNames() throws IOException {
        String modEntitiesSrc = Files.readString(MAIN_JAVA.resolve("registry/ModEntities.java"));
        String mainSrc = Files.readString(MAIN_JAVA.resolve("IndustrialCore.java"));
        String clientSetupSrc = Files.readString(MAIN_JAVA.resolve("client/ClientSetup.java"));
        String raidMgrSrc = Files.readString(MAIN_JAVA.resolve("raid/DimensionalRaidManager.java"));

        for (String id : List.of("rift_stalker", "void_sentinel", "phase_juggernaut", "dimensional_sovereign")) {
            assertTrue(modEntitiesSrc.contains("\"" + id + "\""), "ModEntities missing " + id);
            assertTrue(Files.isRegularFile(RES.resolve("assets/industrialcore/textures/entity/" + id + ".png")),
                    "Missing entity texture for " + id);
        }
        assertTrue(modEntitiesSrc.contains("\"rift_plasma_bolt\""));
        assertTrue(mainSrc.contains("registerEntityAttributes"));
        assertTrue(clientSetupSrc.contains("RiftInvaderRenderer"));
        assertTrue(clientSetupSrc.contains("RiftPlasmaBoltRenderer"));
        assertTrue(raidMgrSrc.contains("ModEntities.RIFT_STALKER"));
        assertTrue(raidMgrSrc.contains("ModEntities.VOID_SENTINEL"));
        assertTrue(raidMgrSrc.contains("ModEntities.PHASE_JUGGERNAUT"));
        assertTrue(raidMgrSrc.contains("ModEntities.DIMENSIONAL_SOVEREIGN"));
        assertFalse(raidMgrSrc.contains("EntityType.ZOMBIE"), "Raid must no longer spawn vanilla zombies");
        assertFalse(raidMgrSrc.contains("EntityType.SKELETON"), "Raid must no longer spawn vanilla skeletons");

        // Verify LaserTurretBlockEntity unified beam FX with Pulse Laser (KIND_LASER_BEAM)
        String turretSrc = Files.readString(MAIN_JAVA.resolve("blockentity/LaserTurretBlockEntity.java"));
        assertTrue(turretSrc.contains("KIND_LASER_BEAM"), "LaserTurret must broadcast Pulse Laser beam FX");

        JsonObject ja = JsonParser.parseString(
                Files.readString(RES.resolve("assets/industrialcore/lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(
                Files.readString(RES.resolve("assets/industrialcore/lang/en_us.json"))).getAsJsonObject();
        for (String key : List.of(
                "entity.industrialcore.rift_stalker",
                "entity.industrialcore.void_sentinel",
                "entity.industrialcore.phase_juggernaut",
                "entity.industrialcore.dimensional_sovereign",
                "entity.industrialcore.rift_plasma_bolt",
                "bossbar.industrialcore.rift_raid_tier",
                "message.industrialcore.rift_tier_unlocked")) {
            assertTrue(ja.has(key) && !ja.get(key).getAsString().isBlank(), "Missing ja key: " + key);
            assertTrue(en.has(key) && !en.get(key).getAsString().isBlank(), "Missing en key: " + key);
        }
    }
}
