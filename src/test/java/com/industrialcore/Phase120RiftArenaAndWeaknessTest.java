package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.raid.RiftArenaLogic;
import com.industrialcore.raid.RiftArenaLogic.JuggernautHitResult;
import com.industrialcore.raid.RiftArenaLogic.PillarSpec;
import com.industrialcore.raid.RiftArenaLogic.ReturnPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 120 pure-Java verification suite:
 * <ul>
 *   <li>Dedicated Void Arena dimension JSONs ({@code industrialcore:rift_arena}), 1024-block instance
 *       offsets, 4 elevated sniper pillars, and safe return-point sanitization.</li>
 *   <li>Countdown timer durations ({@code 180s / 210s / 240s}), {@code MM:SS} formatting, 30s warning
 *       band, expiry trigger, and non-lethal 15%-HP void-fall rescue penalty.</li>
 *   <li>Tactical weakness math: Juggernaut 95% ranged block + Quantum Blade Shield Break (2.2x),
 *       Sentinel open-core ranged 2.5x critical + crash stun, and Sovereign 60%/25% Absolute Barrier
 *       (90% cut) shattered by destroying 3 Anchor Crystals (2.0x exposed stun).</li>
 * </ul>
 */
class Phase120RiftArenaAndWeaknessTest {
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");

    @Test
    void riftArenaDimensionJsonsOffsetsPillarsAndReturnSafetyAreValid() throws IOException {
        Path dimTypePath = DATA.resolve("dimension_type/rift_arena.json");
        Path dimPath = DATA.resolve("dimension/rift_arena.json");
        assertTrue(Files.exists(dimTypePath), "Missing dimension_type/rift_arena.json");
        assertTrue(Files.exists(dimPath), "Missing dimension/rift_arena.json");

        JsonObject dimType = JsonParser.parseString(Files.readString(dimTypePath)).getAsJsonObject();
        assertFalse(dimType.get("bed_works").getAsBoolean(), "Beds must not explode/work in rift_arena");
        assertFalse(dimType.get("has_raids").getAsBoolean());
        assertEquals("minecraft:the_end", dimType.get("effects").getAsString());
        assertEquals(18000, dimType.get("fixed_time").getAsInt());

        JsonObject dim = JsonParser.parseString(Files.readString(dimPath)).getAsJsonObject();
        assertEquals(RiftArenaLogic.ARENA_DIMENSION_ID, dim.get("type").getAsString());
        JsonObject gen = dim.getAsJsonObject("generator");
        assertEquals("minecraft:flat", gen.get("type").getAsString());
        assertEquals("minecraft:the_void", gen.getAsJsonObject("settings").get("biome").getAsString());

        // 1024-block instance stride prevents multiplayer arena overlap
        assertEquals(0, RiftArenaLogic.arenaCenterX(0));
        assertEquals(1024, RiftArenaLogic.arenaCenterX(1));
        assertEquals(2048, RiftArenaLogic.arenaCenterX(2));
        assertEquals(0, RiftArenaLogic.arenaCenterX(-5));

        // 4 elevated sniper pillars (+6..+9 height)
        assertEquals(4, RiftArenaLogic.SNIPER_PILLARS.size());
        for (PillarSpec p : RiftArenaLogic.SNIPER_PILLARS) {
            assertTrue(p.height() >= 6 && p.height() <= 9, "Pillar height must be +6..+9, got " + p.height());
        }

        // ReturnPoint never strands players in rift_arena or NaN coordinates
        ReturnPoint safe = new ReturnPoint(RiftArenaLogic.ARENA_DIMENSION_ID, Double.NaN, Double.POSITIVE_INFINITY, 10.0D, 0.0F, 0.0F);
        assertEquals("minecraft:overworld", safe.dimensionId());
        assertTrue(Double.isFinite(safe.x()) && Double.isFinite(safe.y()) && Double.isFinite(safe.z()));
    }

    @Test
    void countdownTimerWarningExpiryAndVoidFallSafetyAreExact() {
        assertEquals(180, RiftArenaLogic.timeLimitSeconds(1));
        assertEquals(180, RiftArenaLogic.timeLimitSeconds(3));
        assertEquals(210, RiftArenaLogic.timeLimitSeconds(4));
        assertEquals(210, RiftArenaLogic.timeLimitSeconds(7));
        assertEquals(240, RiftArenaLogic.timeLimitSeconds(8));
        assertEquals(240, RiftArenaLogic.timeLimitSeconds(10));

        assertEquals("03:00", RiftArenaLogic.formatCountdown(3600));
        assertEquals("00:30", RiftArenaLogic.formatCountdown(600));
        assertEquals("00:01", RiftArenaLogic.formatCountdown(1));
        assertEquals("00:00", RiftArenaLogic.formatCountdown(0));

        assertFalse(RiftArenaLogic.isWarningPeriod(601));
        assertTrue(RiftArenaLogic.isWarningPeriod(600));
        assertTrue(RiftArenaLogic.isWarningPeriod(1));
        assertFalse(RiftArenaLogic.isWarningPeriod(0));

        assertFalse(RiftArenaLogic.isTimeExpired(1));
        assertTrue(RiftArenaLogic.isTimeExpired(0));

        // Void fall rescue deals 15% max HP, never lethal (always leaves >= 1.0 HP)
        assertEquals(3.0F, RiftArenaLogic.voidFallPenaltyDamage(20.0F, 20.0F), 1e-4F);
        assertEquals(1.0F, RiftArenaLogic.voidFallPenaltyDamage(2.0F, 20.0F), 1e-4F);
        assertEquals(0.0F, RiftArenaLogic.voidFallPenaltyDamage(1.0F, 20.0F), 1e-4F);
    }

    @Test
    void tacticalWeaknessDamageMultipliersAndBarrierStagesAreExact() {
        // 1. Phase Juggernaut: 95% ranged block, Quantum Blade triggers Shield Break, 2.2x weakness while broken
        JuggernautHitResult rangedFront = RiftArenaLogic.evaluateJuggernautHit(false, false, true, 0.95D);
        assertEquals(JuggernautHitResult.RANGED_BLOCKED_95, rangedFront);
        assertEquals(0.05F, rangedFront.damageMultiplier(), 1e-5F);
        assertFalse(rangedFront.triggersBreak());

        JuggernautHitResult bladeHit = RiftArenaLogic.evaluateJuggernautHit(false, true, false, 0.95D);
        assertEquals(JuggernautHitResult.TRIGGER_SHIELD_BREAK, bladeHit);
        assertTrue(bladeHit.triggersBreak());

        JuggernautHitResult brokenWeak = RiftArenaLogic.evaluateJuggernautHit(true, false, true, 0.95D);
        assertEquals(JuggernautHitResult.SHIELD_BROKEN_WEAKNESS, brokenWeak);
        assertEquals(2.20F, brokenWeak.damageMultiplier(), 1e-5F);

        // 2. Void Sentinel: closed pod resists (0.55x); open-core charge + ranged = 2.5x critical + crash stun
        assertEquals(0.55F, RiftArenaLogic.evaluateSentinelDamageMultiplier(false, true, false), 1e-5F);
        assertEquals(2.50F, RiftArenaLogic.evaluateSentinelDamageMultiplier(true, true, false), 1e-5F);
        assertTrue(RiftArenaLogic.shouldSentinelCrashStun(true, true, false));
        assertFalse(RiftArenaLogic.shouldSentinelCrashStun(false, true, false));
        assertFalse(RiftArenaLogic.shouldSentinelCrashStun(true, false, false));

        // 3. Dimensional Sovereign: Absolute Barrier at HP <= 60% and HP <= 25% (90% cut -> 0.10x), 2.0x stunned
        assertEquals(0, RiftArenaLogic.nextBossBarrierStage(300.0F, 360.0F, 0, false));
        assertEquals(1, RiftArenaLogic.nextBossBarrierStage(210.0F, 360.0F, 0, false));
        assertEquals(0, RiftArenaLogic.nextBossBarrierStage(150.0F, 360.0F, 1, false));
        assertEquals(2, RiftArenaLogic.nextBossBarrierStage(90.0F, 360.0F, 1, false));
        assertEquals(0, RiftArenaLogic.nextBossBarrierStage(80.0F, 360.0F, 2, false));
        assertEquals(0.10F, RiftArenaLogic.evaluateBossDamageMultiplier(true, false), 1e-5F);
        assertEquals(2.00F, RiftArenaLogic.evaluateBossDamageMultiplier(false, true), 1e-5F);
        assertEquals(1.00F, RiftArenaLogic.evaluateBossDamageMultiplier(false, false), 1e-5F);
    }

    @Test
    void anchorCrystalMechaModelsRelogRescueAndTranslationsAreWired() throws IOException {
        String modEntities = Files.readString(Path.of("src/main/java/com/industrialcore/registry/ModEntities.java"));
        assertTrue(modEntities.contains("\"rift_anchor_crystal\""), "RIFT_ANCHOR_CRYSTAL must be registered");

        String clientSetup = Files.readString(Path.of("src/main/java/com/industrialcore/client/ClientSetup.java"));
        assertTrue(clientSetup.contains("RiftAnchorCrystalRenderer::new"), "RiftAnchorCrystalRenderer must be registered");

        String mainMod = Files.readString(Path.of("src/main/java/com/industrialcore/IndustrialCore.java"));
        assertTrue(mainMod.contains("DimensionalRaidManager::onPlayerLoggedIn"), "Relog rescue listener must be registered");

        String renderer = Files.readString(Path.of("src/main/java/com/industrialcore/client/RiftInvaderRenderer.java"));
        assertTrue(renderer.contains("isPetalOpen")
                && renderer.contains("isShieldBroken")
                && renderer.contains("isAbsoluteBarrierActive")
                && renderer.contains("isPhaseJammed"),
                "RiftInvaderRenderer must drive all 4 mecha/SF-anime state animations");

        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        for (String key : new String[]{
                "entity.industrialcore.rift_anchor_crystal",
                "bossbar.industrialcore.rift_raid_arena",
                "bossbar.industrialcore.rift_raid_victory_return",
                "message.industrialcore.rift_arena_void_rescue",
                "message.industrialcore.rift_weakness_shield_break",
                "message.industrialcore.rift_weakness_sentinel_crit",
                "message.industrialcore.rift_weakness_jammed",
                "message.industrialcore.rift_boss_barrier_up",
                "message.industrialcore.rift_boss_barrier_broken"
        }) {
            assertTrue(ja.has(key) && en.has(key), "Missing bilingual translation key: " + key);
        }
    }
}
