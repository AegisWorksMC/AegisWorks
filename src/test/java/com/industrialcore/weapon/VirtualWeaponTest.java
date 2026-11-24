package com.industrialcore.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.suit.SuitModifierLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 101 pure-Java unit tests for all 4 electric weapons, 12 attachments, laser heat &amp;
 * targeting math, railgun charge/recoil, safe blink search, multi-lock &amp; gravity pull,
 * 3-row {@link SuitModifierLayout}, and shipped assets/recipes/translations.
 */
class VirtualWeaponTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");

    @Test
    void attachmentCompatibilityInstallDuplicateGuardToggleAndUninstallAreExact() {
        assertEquals(4, WeaponKind.values().length);
        assertEquals(12, WeaponAttachment.values().length);
        for (WeaponKind kind : WeaponKind.values()) {
            assertEquals(3, WeaponAttachment.forWeapon(kind).size(),
                    "Every electric weapon must have exactly 3 compatible attachments: " + kind);
        }
        assertEquals(3, SuitModifierLayout.VISIBLE_ROWS, "Unified suit_modifier must expose 3 rows for weapon attachments");

        Map<String, Boolean> laserAtts = new LinkedHashMap<>();
        // Incompatible attachment rejected
        assertFalse(VirtualWeaponLogic.canInstall(WeaponKind.PULSE_LASER, WeaponAttachment.FAST_CAPACITOR, laserAtts));
        assertFalse(VirtualWeaponLogic.install(WeaponKind.PULSE_LASER, WeaponAttachment.FAST_CAPACITOR, laserAtts));
        assertTrue(laserAtts.isEmpty());

        // Compatible install enabled by default + duplicate guard
        assertTrue(VirtualWeaponLogic.install(WeaponKind.PULSE_LASER, WeaponAttachment.AUTO_AIM, laserAtts));
        assertTrue(VirtualWeaponLogic.isAttachmentEnabled(WeaponAttachment.AUTO_AIM, laserAtts));
        assertFalse(VirtualWeaponLogic.canInstall(WeaponKind.PULSE_LASER, WeaponAttachment.AUTO_AIM, laserAtts));
        assertFalse(VirtualWeaponLogic.install(WeaponKind.PULSE_LASER, WeaponAttachment.AUTO_AIM, laserAtts));

        // Toggle OFF -> ON -> Uninstall
        assertTrue(VirtualWeaponLogic.toggle(WeaponAttachment.AUTO_AIM, laserAtts));
        assertFalse(VirtualWeaponLogic.isAttachmentEnabled(WeaponAttachment.AUTO_AIM, laserAtts));
        assertTrue(VirtualWeaponLogic.toggle(WeaponAttachment.AUTO_AIM, laserAtts));
        assertTrue(VirtualWeaponLogic.isAttachmentEnabled(WeaponAttachment.AUTO_AIM, laserAtts));
        assertTrue(VirtualWeaponLogic.uninstall(WeaponAttachment.AUTO_AIM, laserAtts));
        assertFalse(VirtualWeaponLogic.toggle(WeaponAttachment.AUTO_AIM, laserAtts));
        assertFalse(VirtualWeaponLogic.uninstall(WeaponAttachment.AUTO_AIM, laserAtts));
    }

    @Test
    void pulseLaserHeatOverheatLatchCryoRampAutoAimAndPrismChainsWorkCleanly() {
        // Heat builds to 100% and latches overheated until cooled <= 25%
        float heat = 0.0F;
        boolean ov = false;
        int ticks = 0;
        while (!ov && ticks < 200) {
            var next = VirtualWeaponLogic.stepLaserHeat(heat, ov, true, false);
            heat = next.heat();
            ov = next.overheated();
            ticks++;
        }
        assertTrue(ov, "Continuous firing must overheat");
        assertEquals(100.0F, heat, 1.0E-4F);

        // Even if trying to fire while overheated, stays overheated & cools down until <= 25%
        var midCool = VirtualWeaponLogic.stepLaserHeat(60.0F, true, true, false);
        assertTrue(midCool.overheated());
        assertTrue(midCool.heat() < 60.0F);
        var unlocked = VirtualWeaponLogic.stepLaserHeat(26.0F, true, true, false);
        assertFalse(unlocked.overheated(), "Overheat latch must release at <= 25%");

        // Cryo Core cuts heat gain sharply and ramps damage up to 2.2x
        var baseStep = VirtualWeaponLogic.stepLaserHeat(0.0F, false, true, false);
        var cryoStep = VirtualWeaponLogic.stepLaserHeat(0.0F, false, true, true);
        assertTrue(cryoStep.heat() < baseStep.heat() * 0.4F);
        assertEquals(VirtualWeaponLogic.LASER_BASE_DAMAGE_PER_TICK,
                VirtualWeaponLogic.laserTickDamage(50, false), 1.0E-5F);
        assertEquals(VirtualWeaponLogic.LASER_BASE_DAMAGE_PER_TICK * VirtualWeaponLogic.LASER_CRYO_MAX_MULT,
                VirtualWeaponLogic.laserTickDamage(VirtualWeaponLogic.LASER_CRYO_RAMP_TICKS, true), 1.0E-4F);

        // Auto-aim selects hostile inside front cone, ignores behind & out-of-range
        List<VirtualWeaponLogic.TargetCandidate> cands = List.of(
                new VirtualWeaponLogic.TargetCandidate(1, 0, 0, -10), // behind
                new VirtualWeaponLogic.TargetCandidate(2, 0, 0, 40),  // too far (>28m)
                new VirtualWeaponLogic.TargetCandidate(3, 1.0, 0, 12), // inside cone
                new VirtualWeaponLogic.TargetCandidate(4, 0.1, 0, 10)  // dead-center & closer
        );
        var locked = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1,
                VirtualWeaponLogic.LASER_RANGE, VirtualWeaponLogic.LASER_AUTO_AIM_COS, cands);
        assertNotNull(locked);
        assertEquals(4, locked.id());

        // Prism Lens chains to at most 2 nearest mobs within 6m excluding primary
        List<VirtualWeaponLogic.TargetCandidate> prismPool = List.of(
                new VirtualWeaponLogic.TargetCandidate(10, 0, 0, 10),   // primary
                new VirtualWeaponLogic.TargetCandidate(11, 2, 0, 10),   // 2m
                new VirtualWeaponLogic.TargetCandidate(12, 4, 0, 10),   // 4m
                new VirtualWeaponLogic.TargetCandidate(13, 5, 0, 10),   // 5m (3rd, capped)
                new VirtualWeaponLogic.TargetCandidate(14, 9, 0, 10)    // 9m (too far)
        );
        var chains = VirtualWeaponLogic.selectPrismChains(10, 0, 0, 10, 6.0D, 2, prismPool);
        assertEquals(2, chains.size());
        assertEquals(11, chains.get(0).id());
        assertEquals(12, chains.get(1).id());
    }

    @Test
    void railgunChargeFastCapacitorDamageAndGyroRecoilAreExact() {
        assertTrue(VirtualWeaponLogic.railgunChargeTicks(true) < VirtualWeaponLogic.railgunChargeTicks(false) * 0.5);
        assertTrue(VirtualWeaponLogic.railgunShotEnergyCost(true) < VirtualWeaponLogic.railgunShotEnergyCost(false));
        assertEquals(0.0F, VirtualWeaponLogic.railgunDamage(0.10F), 1.0E-5F);
        assertEquals(VirtualWeaponLogic.RAILGUN_BASE_DAMAGE, VirtualWeaponLogic.railgunDamage(1.0F), 1.0E-5F);
        assertTrue(VirtualWeaponLogic.railgunDamage(0.5F) > 0.0F
                && VirtualWeaponLogic.railgunDamage(0.5F) < VirtualWeaponLogic.RAILGUN_BASE_DAMAGE);
        assertEquals(0.0D, VirtualWeaponLogic.railgunRecoilSpeed(true), 1.0E-6D);
        assertEquals(0.0F, VirtualWeaponLogic.railgunPitchKick(true), 1.0E-6F);
        assertTrue(VirtualWeaponLogic.railgunRecoilSpeed(false) > 0.0D);
    }

    @Test
    void quantumBladePowerFallbackSafeBlinkEnergyBowMultiLockAndGravityPullAreExact() {
        // Powered vs 0 FE melee damage
        assertEquals(VirtualWeaponLogic.BLADE_DAMAGE_POWERED, VirtualWeaponLogic.bladeMeleeDamage(50_000, false), 1.0E-5F);
        assertEquals(VirtualWeaponLogic.BLADE_DAMAGE_UNPOWERED, VirtualWeaponLogic.bladeMeleeDamage(0, false), 1.0E-5F);
        assertEquals(VirtualWeaponLogic.BLADE_DAMAGE_POWERED, VirtualWeaponLogic.bladeMeleeDamage(0, true), 1.0E-5F);

        // Safe blink coordinate search picks farthest safe spot and never picks blocked wall indices
        assertEquals(3, VirtualWeaponLogic.findSafeBlinkIndex(new boolean[] {true, true, true, true, false, false}));
        assertEquals(0, VirtualWeaponLogic.findSafeBlinkIndex(new boolean[] {true, false, false}));
        assertEquals(-1, VirtualWeaponLogic.findSafeBlinkIndex(new boolean[] {false, false, false}));

        // Energy bow draw speed & multi-lock capped at 4
        assertTrue(VirtualWeaponLogic.bowFullDrawTicks(true) < VirtualWeaponLogic.bowFullDrawTicks(false));
        assertEquals(1.0F, VirtualWeaponLogic.bowDrawRatio(8, true), 1.0E-5F);
        assertTrue(VirtualWeaponLogic.bowDrawRatio(8, false) < 0.5F);

        List<VirtualWeaponLogic.TargetCandidate> swarm = List.of(
                new VirtualWeaponLogic.TargetCandidate(1, 0, 0, 10),
                new VirtualWeaponLogic.TargetCandidate(2, 1, 0, 12),
                new VirtualWeaponLogic.TargetCandidate(3, -1, 0, 14),
                new VirtualWeaponLogic.TargetCandidate(4, 2, 0, 16),
                new VirtualWeaponLogic.TargetCandidate(5, -2, 0, 18),
                new VirtualWeaponLogic.TargetCandidate(6, 0, 0, -5) // behind
        );
        var locked = VirtualWeaponLogic.selectMultiLockTargets(
                0, 0, 0, 0, 0, 1,
                VirtualWeaponLogic.BOW_MULTI_LOCK_RANGE, VirtualWeaponLogic.BOW_MULTI_LOCK_COS,
                VirtualWeaponLogic.BOW_MULTI_LOCK_MAX, swarm);
        assertEquals(4, locked.size());
        assertFalse(locked.stream().anyMatch(c -> c.id() == 6));

        // Gravity singularity pulls inward toward center, zero outside radius & zero at center (no NaN)
        var pull = VirtualWeaponLogic.gravityPullVector(0, 64, 0, 4, 64, 0, VirtualWeaponLogic.GRAVITY_RADIUS);
        assertTrue(pull.x() < -0.05D, "Mob at +X must be pulled toward 0 (-X)");
        assertTrue(Math.abs(pull.x()) <= VirtualWeaponLogic.GRAVITY_MAX_PULL);
        var outside = VirtualWeaponLogic.gravityPullVector(0, 64, 0, 12, 64, 0, VirtualWeaponLogic.GRAVITY_RADIUS);
        assertEquals(0.0D, outside.x(), 1.0E-6D);
        var center = VirtualWeaponLogic.gravityPullVector(0, 64, 0, 0, 64, 0, VirtualWeaponLogic.GRAVITY_RADIUS);
        assertFalse(Double.isNaN(center.x()) || Double.isNaN(center.y()) || Double.isNaN(center.z()));
    }

    @Test
    void allPhase101AssetsModelsRecipesAndTranslationsExistAndAreSymmetric() throws IOException {
        List<String> allItems = List.of(
                "pulse_laser", "railgun", "quantum_blade", "energy_bow",
                "att_auto_aim", "att_cryo_core", "att_prism_lens",
                "att_fast_capacitor", "att_wall_scanner", "att_recoil_gyro",
                "att_plasma_wave", "att_blink_drive", "att_quantum_loot",
                "att_multi_lock", "att_gravity_arrow", "att_auto_draw"
        );
        for (String id : allItems) {
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + id + ".png")), "Missing texture: " + id);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")), "Missing model: " + id);
            assertTrue(Files.exists(DATA.resolve("recipe/" + id + ".json")), "Missing recipe: " + id);
        }

        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(
                    Files.readString(ASSETS.resolve("lang/" + langFile))).getAsJsonObject();
            for (String id : allItems) {
                assertTrue(lang.has("item.industrialcore." + id), langFile + " missing item." + id);
            }
            assertTrue(lang.has("message.industrialcore.weapon.overheated"), langFile + " missing overheated msg");
            assertTrue(lang.has("entity.industrialcore.energy_arrow"), langFile + " missing energy_arrow entity name");
        }
        assertNull(WeaponKind.byId("unknown_weapon"));
        assertNull(WeaponAttachment.byId("unknown_att"));
    }

    @Test
    void phase1025LaserShortestAngleWrapAimSnapAndTargetFilterAreExact() throws IOException {
        // 1. Shortest-angle delta crosses +/-180 seam cleanly without spinning backward
        assertEquals(2.0F, VirtualWeaponLogic.shortestAngleDelta(179.0F, -179.0F), 1e-4F);
        assertEquals(-2.0F, VirtualWeaponLogic.shortestAngleDelta(-179.0F, 179.0F), 1e-4F);
        assertEquals(0.0F, VirtualWeaponLogic.shortestAngleDelta(45.0F, 45.0F), 1e-4F);

        // 2. Look angles & aim-snap step converge along shortest path and clamp pitch strictly
        var lookNorth = VirtualWeaponLogic.computeLookAngles(0, 65, 0, 0, 65, -10);
        assertEquals(180.0F, Math.abs(lookNorth.yawDeg()), 1e-3F);
        assertEquals(0.0F, lookNorth.pitchDeg(), 1e-3F);

        var lookSouth = VirtualWeaponLogic.computeLookAngles(0, 65, 0, 0, 65, 10);
        assertEquals(0.0F, lookSouth.yawDeg(), 1e-3F);

        var snapped = VirtualWeaponLogic.stepAimRotation(179.0F, 0.0F, -179.0F, 10.0F, 0.5F, 24.0F);
        assertEquals(180.0F, snapped.yawDeg(), 1e-3F, "179 -> -179 must move +1 deg, never -179 deg");
        assertEquals(5.0F, snapped.pitchDeg(), 1e-3F);

        var pitchClamped = VirtualWeaponLogic.stepAimRotation(0.0F, 85.0F, 0.0F, 120.0F, 1.0F, 30.0F);
        assertTrue(pitchClamped.pitchDeg() <= 89.5F && pitchClamped.pitchDeg() >= -89.5F);

        // 3. LaserTargetMode filter semantics: HOSTILE_ONLY vs ALL_MOBS
        assertEquals(LaserTargetMode.HOSTILE_ONLY, LaserTargetMode.byId(null));
        assertEquals(LaserTargetMode.ALL_MOBS, LaserTargetMode.HOSTILE_ONLY.next());
        assertEquals(LaserTargetMode.HOSTILE_ONLY, LaserTargetMode.ALL_MOBS.next());

        // Hostile zombie -> matched in both modes
        assertTrue(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.HOSTILE_ONLY, true, false, true, false, false, false));
        assertTrue(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.ALL_MOBS, true, false, true, false, false, false));
        // Angry neutral mob -> matched in both modes
        assertTrue(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.HOSTILE_ONLY, false, true, true, false, false, false));
        // Peaceful cow (isMob=true, not enemy/angry) -> EXCLUDED in HOSTILE_ONLY, INCLUDED in ALL_MOBS!
        assertFalse(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.HOSTILE_ONLY, false, false, true, false, false, false));
        assertTrue(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.ALL_MOBS, false, false, true, false, false, false));
        // Players, spectators, invulnerable -> ALWAYS excluded in both modes
        assertFalse(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.ALL_MOBS, false, false, false, true, false, false));
        assertFalse(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.ALL_MOBS, true, false, true, false, true, false));
        assertFalse(VirtualWeaponLogic.matchesTargetFilter(LaserTargetMode.ALL_MOBS, true, false, true, false, false, true));

        // 4. Straight-beam raycast verification in PulseLaserItem source
        String laserSrc = Files.readString(Path.of("src/main/java/com/industrialcore/item/PulseLaserItem.java"));
        assertTrue(laserSrc.contains("findDirectRayTarget(sl, sp, eye, endPos, autoAim)"),
                "PulseLaserItem must always fire straight along look vector");
        assertTrue(laserSrc.contains("LaserTargetMode"), "PulseLaserItem must persist LaserTargetMode");
        assertTrue(laserSrc.contains("LaserAimRange"), "PulseLaserItem must persist LaserAimRange");
    }

    @Test
    void pulseLaserHitAccumulationChargeDamage5SecondHoldDecayAndBeamWidthAreExact() {
        // 1. Damage scales monotonically from low 0.40 at 0% up to 2.00 at 100% (2.20 with Cryo Core)
        float d0 = VirtualWeaponLogic.laserChargedDamage(0.0F, false);
        float d50 = VirtualWeaponLogic.laserChargedDamage(0.5F, false);
        float d100 = VirtualWeaponLogic.laserChargedDamage(1.0F, false);
        float d100Cryo = VirtualWeaponLogic.laserChargedDamage(1.0F, true);
        assertEquals(0.40F, d0, 1e-5F);
        assertEquals(1.20F, d50, 1e-5F);
        assertEquals(2.00F, d100, 1e-5F);
        assertTrue(d0 < d50 && d50 < d100 && d100 < d100Cryo);

        // 2. Consecutive hits accumulate charge to 1.00; Cryo Core accelerates buildup by +35%
        var baseGain = VirtualWeaponLogic.stepLaserCharge(0.0F, 50, true, false);
        var cryoGain = VirtualWeaponLogic.stepLaserCharge(0.0F, 50, true, true);
        assertEquals(0, baseGain.ticksSinceLastHit(), "Hit resets idle counter to 0");
        assertTrue(cryoGain.charge() > baseGain.charge() * 1.30F);

        float c = 0.0F;
        int idle = 0;
        for (int i = 0; i < 80; i++) {
            var st = VirtualWeaponLogic.stepLaserCharge(c, idle, true, false);
            c = st.charge();
            idle = st.ticksSinceLastHit();
        }
        assertEquals(1.0F, c, 1e-5F, "Sustained hits must reach full 100% charge");
        assertEquals(0, idle);

        // 3. Exact 5-second (100-tick) hold with ZERO decay!
        for (int t = 1; t <= VirtualWeaponLogic.LASER_CHARGE_HOLD_TICKS; t++) {
            var st = VirtualWeaponLogic.stepLaserCharge(c, idle, false, false);
            c = st.charge();
            idle = st.ticksSinceLastHit();
            assertEquals(1.0F, c, 1e-6F, "Charge must NOT decay during 5s hold at tick " + t);
            assertEquals(t, idle);
        }

        // 4. Tick 101 begins gentle decay (-0.005/tick) and stops cleanly at 0.0f
        var tick101 = VirtualWeaponLogic.stepLaserCharge(c, idle, false, false);
        assertEquals(1.0F - VirtualWeaponLogic.LASER_CHARGE_DECAY_PER_TICK, tick101.charge(), 1e-5F);
        c = tick101.charge();
        idle = tick101.ticksSinceLastHit();
        for (int t = 0; t < 250; t++) {
            var st = VirtualWeaponLogic.stepLaserCharge(c, idle, false, false);
            assertTrue(st.charge() <= c && st.charge() >= 0.0F);
            c = st.charge();
            idle = st.ticksSinceLastHit();
        }
        assertEquals(0.0F, c, 1e-6F, "Decayed charge must floor cleanly at 0.0f");

        // 5. Beam width factor climbs strictly from 0.60x (thin) -> ~1.35x -> 2.40x (massive)
        float w0 = VirtualWeaponLogic.laserBeamWidthFactor(0.0F);
        float w50 = VirtualWeaponLogic.laserBeamWidthFactor(0.5F);
        float w100 = VirtualWeaponLogic.laserBeamWidthFactor(1.0F);
        assertEquals(0.60F, w0, 1e-5F);
        assertTrue(w50 > 1.25F && w50 < 1.50F);
        assertEquals(2.40F, w100, 1e-5F);
        assertTrue(w0 < w50 && w50 < w100);
    }

    @Test
    void pulseLaserPrecisionSnapLeadHysteresisAndAimRangePresetsAreExact() throws IOException {
        // 1. Exact 0.0-deg snap adsorption when error <= 6.0 deg (including across +/-180 seam!)
        var exactSnap = VirtualWeaponLogic.stepAimRotation(10.0F, 5.0F, 14.0F, 8.0F, 0.5F, 36.0F);
        assertEquals(0.0F, VirtualWeaponLogic.shortestAngleDelta(exactSnap.yawDeg(), 14.0F), 1e-4F,
                "5.0-deg combined error (<= 6 deg) must snap to exact 0.0 deg yaw error");
        assertEquals(8.0F, exactSnap.pitchDeg(), 1e-4F,
                "5.0-deg combined error (<= 6 deg) must snap to exact 0.0 deg pitch error");

        var seamSnap = VirtualWeaponLogic.stepAimRotation(178.0F, 0.0F, -179.0F, 1.5F, 0.5F, 36.0F);
        assertEquals(0.0F, VirtualWeaponLogic.shortestAngleDelta(seamSnap.yawDeg(), -179.0F), 1e-4F,
                "3.35-deg error across +/-180 seam must snap directly onto -179 deg");
        assertEquals(1.5F, seamSnap.pitchDeg(), 1e-4F);

        // 2. Moving target velocity lead prediction
        var predicted = VirtualWeaponLogic.predictTargetPosition(10.0, 65.0, 20.0, 0.4, 0.0, -0.2,
                VirtualWeaponLogic.LASER_LEAD_TICKS);
        assertEquals(10.0 + 0.4 * VirtualWeaponLogic.LASER_LEAD_TICKS, predicted.x(), 1e-6);
        assertEquals(65.0, predicted.y(), 1e-6);
        assertEquals(20.0 - 0.2 * VirtualWeaponLogic.LASER_LEAD_TICKS, predicted.z(), 1e-6);

        // 3. LaserAimRange enum cycling & default (WIDE)
        assertEquals(LaserAimRange.WIDE, LaserAimRange.byId(null));
        assertEquals(LaserAimRange.WIDE, LaserAimRange.NARROW.next());
        assertEquals(LaserAimRange.FULL_360, LaserAimRange.WIDE.next());
        assertEquals(LaserAimRange.NARROW, LaserAimRange.FULL_360.next());

        // Helper: player at origin looking along +Z (0, 0, 1)
        double r = 10.0;
        var front10 = new VirtualWeaponLogic.TargetCandidate(1,
                r * Math.sin(Math.toRadians(10)), 0, r * Math.cos(Math.toRadians(10)));
        var side45 = new VirtualWeaponLogic.TargetCandidate(2,
                r * Math.sin(Math.toRadians(45)), 0, r * Math.cos(Math.toRadians(45)));
        var behind150 = new VirtualWeaponLogic.TargetCandidate(3,
                r * Math.sin(Math.toRadians(150)), 0, r * Math.cos(Math.toRadians(150)));
        var behind170Near = new VirtualWeaponLogic.TargetCandidate(4,
                5.0 * Math.sin(Math.toRadians(170)), 0, 5.0 * Math.cos(Math.toRadians(170)));

        // 4. NARROW (±25°): locks 10° front, excludes 45° side
        var narrowHit = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.NARROW, -1,
                List.of(front10, side45));
        assertNotNull(narrowHit);
        assertEquals(1, narrowHit.id());
        assertNull(VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.NARROW, -1,
                List.of(side45, behind150)), "NARROW must exclude 45° side and 150° rear targets");

        // 5. WIDE (±60°): locks 45° side, excludes 150° behind
        var wideHit = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.WIDE, -1,
                List.of(side45, behind150));
        assertNotNull(wideHit);
        assertEquals(2, wideHit.id());
        assertNull(VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.WIDE, -1,
                List.of(behind150, behind170Near)), "WIDE must exclude 150°/170° rear targets");

        // 6. FULL_360 (±180°): locks nearest target even at 170° behind (5m beats 10m front)
        var fullHit = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.FULL_360, -1,
                List.of(front10, behind150, behind170Near));
        assertNotNull(fullHit);
        assertEquals(4, fullHit.id(), "FULL_360 must prioritize nearest target even at 170° behind");

        // Turning toward 170° rear target converges to 0° error cleanly within a few steps
        var rearAngles = VirtualWeaponLogic.computeLookAngles(0, 0, 0,
                behind170Near.x(), behind170Near.y(), behind170Near.z());
        float yaw = 0.0F;
        float pitch = 0.0F;
        for (int i = 0; i < 6; i++) {
            var st = VirtualWeaponLogic.stepAimRotation(yaw, pitch,
                    rearAngles.yawDeg(), rearAngles.pitchDeg(), 0.72F, 55.0F);
            yaw = st.yawDeg();
            pitch = st.pitchDeg();
        }
        assertEquals(0.0F, VirtualWeaponLogic.shortestAngleDelta(yaw, rearAngles.yawDeg()), 1e-3F,
                "360° counter-turn must lock directly onto 170° rear target");

        // 7. Lock hysteresis: keeps locked target (side45 in WIDE) even when front10 enters view,
        //    and switches immediately once locked target is gone
        var kept = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.WIDE, side45.id(),
                List.of(front10, side45));
        assertNotNull(kept);
        assertEquals(side45.id(), kept.id(), "Hysteresis must keep valid locked target without target-hopping");
        var switched = VirtualWeaponLogic.selectAutoAimTarget(
                0, 0, 0, 0, 0, 1, VirtualWeaponLogic.LASER_RANGE, LaserAimRange.WIDE, side45.id(),
                List.of(front10));
        assertNotNull(switched);
        assertEquals(front10.id(), switched.id(), "Once locked target is gone, must switch immediately");

        // 8. Translation key symmetry for new range & keybind keys
        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(
                    Files.readString(ASSETS.resolve("lang/" + langFile))).getAsJsonObject();
            for (String key : List.of(
                    "key.industrialcore.laser_range",
                    "tooltip.industrialcore.weapon.laser_range",
                    "tooltip.industrialcore.weapon.laser_range_hint",
                    "tooltip.industrialcore.weapon.laser_range.narrow",
                    "tooltip.industrialcore.weapon.laser_range.wide",
                    "tooltip.industrialcore.weapon.laser_range.full_360",
                    "message.industrialcore.weapon.laser_range_changed")) {
                assertTrue(lang.has(key), langFile + " missing key: " + key);
            }
        }
    }

    @Test
    void laserAudioLogicCrossfadeHeavyDominanceHitSizzleAndStopAreExact() {
        // 1. Active gating: immediately false on release, slot switch, overheat, or FE loss
        assertTrue(LaserAudioLogic.shouldBeActive(true, true, false, true));
        assertFalse(LaserAudioLogic.shouldBeActive(false, true, false, true), "Slot switch stops loops");
        assertFalse(LaserAudioLogic.shouldBeActive(true, false, false, true), "Stop firing stops loops");
        assertFalse(LaserAudioLogic.shouldBeActive(true, true, true, true), "Overheat stops loops");
        assertFalse(LaserAudioLogic.shouldBeActive(true, true, false, false), "No FE stops loops");

        var silent = LaserAudioLogic.computeMix(false, 1.0F, true);
        assertFalse(silent.active());
        assertEquals(0.0F, silent.lightVolume(), 1e-6F);
        assertEquals(0.0F, silent.heavyVolume(), 1e-6F);
        assertEquals(0.0F, silent.sizzleVolume(), 1e-6F);

        // 2. Real-time crossfade across charge 0.0 / 0.5 / 1.0
        var m0 = LaserAudioLogic.computeMix(true, 0.0F, false);
        var m50 = LaserAudioLogic.computeMix(true, 0.5F, false);
        var m100 = LaserAudioLogic.computeMix(true, 1.0F, false);

        // Light layer trims slightly in volume while rising in pitch
        assertEquals(0.38F, m0.lightVolume(), 1e-4F);
        assertEquals(0.25F, m100.lightVolume(), 1e-4F);
        assertTrue(m0.lightVolume() > m50.lightVolume() && m50.lightVolume() > m100.lightVolume());
        assertEquals(0.95F, m0.lightPitch(), 1e-4F);
        assertEquals(1.35F, m100.lightPitch(), 1e-4F);
        assertTrue(m0.lightPitch() < m50.lightPitch() && m50.lightPitch() < m100.lightPitch());

        // Heavy OW-style sub-bass growl starts whisper-quiet (0.05) and dominates at 100% (0.82)
        assertEquals(0.05F, m0.heavyVolume(), 1e-4F);
        assertEquals(0.82F, m100.heavyVolume(), 1e-4F);
        assertTrue(m0.heavyVolume() < m50.heavyVolume() && m50.heavyVolume() < m100.heavyVolume());
        assertTrue(m0.heavyVolume() < m0.lightVolume() * 0.25F, "Heavy must be tiny at 0% charge");
        assertTrue(m100.heavyVolume() > m100.lightVolume() * 3.0F, "Heavy must dominate mix at 100% charge");
        assertEquals(0.68F, m0.heavyPitch(), 1e-4F);
        assertEquals(1.08F, m100.heavyPitch(), 1e-4F);
        assertTrue(m0.heavyPitch() < m50.heavyPitch() && m50.heavyPitch() < m100.heavyPitch());

        // 3. Hit Sizzle plays ONLY when actively hitting a target
        assertEquals(0.0F, m100.sizzleVolume(), 1e-6F);
        var hit0 = LaserAudioLogic.computeMix(true, 0.0F, true);
        var hit100 = LaserAudioLogic.computeMix(true, 1.0F, true);
        assertEquals(0.35F, hit0.sizzleVolume(), 1e-4F);
        assertEquals(0.55F, hit100.sizzleVolume(), 1e-4F);
        assertTrue(hit100.sizzleVolume() > hit0.sizzleVolume());

        // 4. Envelope fades down cleanly to exact 0.0
        float vol = 0.82F;
        for (int i = 0; i < 6; i++) {
            vol = LaserAudioLogic.stepVolume(vol, 0.0F);
        }
        assertEquals(0.0F, vol, 1e-6F);

        // 5. All 3 procedural sci-fi laser textures exist
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/laser_beam_core.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/laser_beam_aura.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/laser_flare.png")));
    }

    @Test
    void railgunWaveCannonAutoChargeAndQuantumBladePhaseDashCorridorAreExact() throws IOException {
        // 1. Railgun Wave Cannon passive auto-charge: 1,200 ticks (60s) base, 400 ticks (20s) Fast Capacitor
        assertEquals(1_200, VirtualWeaponLogic.railgunAutoChargeTicks(false));
        assertEquals(400, VirtualWeaponLogic.railgunAutoChargeTicks(true));
        assertFalse(VirtualWeaponLogic.RAILGUN_WAVE_DESTROYS_TERRAIN, "Wave Cannon must NEVER destroy terrain");
        assertTrue(VirtualWeaponLogic.RAILGUN_WAVE_DAMAGE >= 60.0F && VirtualWeaponLogic.RAILGUN_WAVE_DAMAGE <= 80.0F);
        assertTrue(VirtualWeaponLogic.RAILGUN_RANGE >= 64.0D && VirtualWeaponLogic.RAILGUN_RANGE <= 80.0D);

        // Pauses cleanly when unpowered
        assertEquals(0.25F, VirtualWeaponLogic.stepRailgunAutoCharge(0.25F, false, false), 1e-6F);

        // Base 1,200 ticks: not fireable at tick 1,199, fireable at tick 1,200
        float cBase = 0.0F;
        for (int t = 1; t < 1_200; t++) {
            cBase = VirtualWeaponLogic.stepRailgunAutoCharge(cBase, false, true);
        }
        assertFalse(VirtualWeaponLogic.canFireRailgunWave(cBase), "Base Railgun must not fire before 1,200 ticks");
        assertEquals(1, VirtualWeaponLogic.railgunRemainingSeconds(cBase, false));
        cBase = VirtualWeaponLogic.stepRailgunAutoCharge(cBase, false, true);
        assertTrue(VirtualWeaponLogic.canFireRailgunWave(cBase), "Base Railgun must reach 100% at tick 1,200");
        assertEquals(0, VirtualWeaponLogic.railgunRemainingSeconds(cBase, false));

        // Fast Capacitor 400 ticks: not fireable at tick 399, fireable at tick 400
        float cFast = 0.0F;
        for (int t = 1; t < 400; t++) {
            cFast = VirtualWeaponLogic.stepRailgunAutoCharge(cFast, true, true);
        }
        assertFalse(VirtualWeaponLogic.canFireRailgunWave(cFast), "Fast Railgun must not fire before 400 ticks");
        cFast = VirtualWeaponLogic.stepRailgunAutoCharge(cFast, true, true);
        assertTrue(VirtualWeaponLogic.canFireRailgunWave(cFast), "Fast Railgun must reach 100% at tick 400");

        // 2. Quantum Blade Phase Dash corridor target detection (multiple enemies in path, off-axis ignored)
        List<VirtualWeaponLogic.TargetCandidate> mobs = List.of(
                new VirtualWeaponLogic.TargetCandidate(10, 0.3, 65, 8.0),   // 8m ahead, 0.3m off-axis -> HIT (2nd)
                new VirtualWeaponLogic.TargetCandidate(11, -0.5, 65, 3.5),  // 3.5m ahead, 0.5m off-axis -> HIT (1st)
                new VirtualWeaponLogic.TargetCandidate(12, 4.0, 65, 6.0),   // 4.0m sideways -> MISS
                new VirtualWeaponLogic.TargetCandidate(13, 0.0, 65, -3.0),  // behind start -> MISS
                new VirtualWeaponLogic.TargetCandidate(14, 0.2, 65, 12.0)   // 12m ahead -> HIT (3rd)
        );
        var sliced = VirtualWeaponLogic.findCorridorTargets(
                0, 65, 0, 0, 65, 13.5, VirtualWeaponLogic.BLADE_SWIFT_CORRIDOR_HALF_WIDTH, mobs);
        assertEquals(3, sliced.size(), "Phase Dash must slice all 3 enemies along the 13.5m corridor");
        assertEquals(11, sliced.get(0).id());
        assertEquals(10, sliced.get(1).id());
        assertEquals(14, sliced.get(2).id());

        // Safe stop before wall index
        assertEquals(2, VirtualWeaponLogic.findSafeBlinkIndex(new boolean[] {true, true, true, false, false}));

        // Katana 3-hit combo progression & idle reset
        assertEquals(1, VirtualWeaponLogic.nextBladeComboStage(0, 6L));
        assertEquals(2, VirtualWeaponLogic.nextBladeComboStage(1, 8L));
        assertEquals(0, VirtualWeaponLogic.nextBladeComboStage(2, 10L));
        assertEquals(0, VirtualWeaponLogic.nextBladeComboStage(1, 50L), "Idle > 32t resets combo to Kesagiri (0)");

        // 3. All 3 new additive weapon rework PNG textures exist
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/blade_slash_arc.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/railgun_cannon_beam.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/misc/bow_charge_ring.png")));

        // Railgun source never breaks blocks
        String railSrc = Files.readString(Path.of("src/main/java/com/industrialcore/item/RailgunItem.java"));
        assertFalse(railSrc.contains("destroyBlock"), "Wave Cannon must never destroy terrain");
    }

    @Test
    void energyBowOpticalScopeFovAndDistanceDamageScalingAreExact() throws IOException {
        // 1. Distance damage multiplier: 0..15m = 1.0x, 30m = 1.6x, 45m = 2.2x, >=60m = 2.8x (clamped)
        assertEquals(1.0F, VirtualWeaponLogic.bowDistanceDamageMultiplier(-5.0D), 1e-4F);
        assertEquals(1.0F, VirtualWeaponLogic.bowDistanceDamageMultiplier(0.0D), 1e-4F);
        assertEquals(1.0F, VirtualWeaponLogic.bowDistanceDamageMultiplier(10.0D), 1e-4F);
        assertEquals(1.0F, VirtualWeaponLogic.bowDistanceDamageMultiplier(15.0D), 1e-4F);
        assertEquals(1.6F, VirtualWeaponLogic.bowDistanceDamageMultiplier(30.0D), 1e-4F);
        assertEquals(2.2F, VirtualWeaponLogic.bowDistanceDamageMultiplier(45.0D), 1e-4F);
        assertEquals(2.8F, VirtualWeaponLogic.bowDistanceDamageMultiplier(60.0D), 1e-4F);
        assertEquals(2.8F, VirtualWeaponLogic.bowDistanceDamageMultiplier(100.0D), 1e-4F);

        // Strictly monotonic non-decreasing across 0..100m
        float prevMult = 1.0F;
        for (int d = 0; d <= 100; d++) {
            float m = VirtualWeaponLogic.bowDistanceDamageMultiplier(d);
            assertTrue(m >= prevMult - 1e-6F, "Multiplier must be non-decreasing at " + d + "m");
            prevMult = m;
        }

        // 2. Optical Scope FOV zoom multiplier: 0.0 -> 1.0F, 1.0 -> 0.36F (approx 2.78x within [2.5x, 3.0x])
        float fov0 = VirtualWeaponLogic.bowScopeFovMultiplier(0.0F);
        float fov50 = VirtualWeaponLogic.bowScopeFovMultiplier(0.5F);
        float fov100 = VirtualWeaponLogic.bowScopeFovMultiplier(1.0F);
        assertEquals(1.0F, fov0, 1e-4F);
        assertEquals(VirtualWeaponLogic.BOW_SCOPE_MAX_ZOOM_FOV_MULT, fov100, 1e-4F);
        assertTrue(fov0 > fov50 && fov50 > fov100, "Scope FOV must decrease smoothly with draw ratio");
        float maxZoomFactor = 1.0F / fov100;
        assertTrue(maxZoomFactor >= 2.5F && maxZoomFactor <= 3.0F,
                "Full draw zoom must be 2.5x..3.0x, got " + maxZoomFactor);

        // 3. Long Shot localization key exists in both ja_jp and en_us
        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        assertTrue(ja.has("message.industrialcore.weapon.bow_long_shot"), "Missing bow_long_shot in ja_jp");
        assertTrue(en.has("message.industrialcore.weapon.bow_long_shot"), "Missing bow_long_shot in en_us");
    }
}
