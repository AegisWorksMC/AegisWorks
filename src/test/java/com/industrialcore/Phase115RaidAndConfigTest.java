package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.level.AegisAdvancements;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.raid.DimensionalRaidLogic.RewardBundle;
import com.industrialcore.raid.DimensionalRaidLogic.SpawnOffset;
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
 * Phase 115 verification suite:
 * - Dimensional Rift Raid wave specs, [18..26] spawn ring, start guards, BossBar, timeout &amp; rewards
 * - 5-category Modpack TOML balance config defaults, clamps, NaN/Inf guards &amp; scaling math
 * - Singularity Reactor Rift Challenge card &amp; button zero-overlap geometry
 * - 29th Advancement ({@code dimension_defender}) JSON &amp; bilingual localization parity
 */
class Phase115RaidAndConfigTest {
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void dimensionalRaidWavesSpawnRingGuardsAndRewardsAreDeterministic() {
        assertEquals(3, DimensionalRaidLogic.TOTAL_WAVES);

        WaveSpec w1 = DimensionalRaidLogic.waveSpec(1);
        WaveSpec w2 = DimensionalRaidLogic.waveSpec(2);
        WaveSpec w3 = DimensionalRaidLogic.waveSpec(3);
        assertFalse(w1.hasBoss());
        assertFalse(w2.hasBoss());
        assertTrue(w3.hasBoss(), "Wave 3 must spawn the Dimensional Warden boss");
        assertTrue(w1.totalCount() >= 5);
        assertTrue(w2.totalCount() >= w1.totalCount());
        assertTrue(w3.totalCount() >= 7);

        // Spawn ring strictly within [18, 26] blocks across all 3 waves
        for (int wave = 1; wave <= 3; wave++) {
            WaveSpec spec = DimensionalRaidLogic.waveSpec(wave);
            List<SpawnOffset> ring = DimensionalRaidLogic.computeSpawnRing(spec.totalCount(), wave * 7);
            assertEquals(spec.totalCount(), ring.size());
            for (SpawnOffset off : ring) {
                double d = off.distance();
                assertTrue(d >= DimensionalRaidLogic.SPAWN_MIN_RADIUS - 0.5D
                        && d <= DimensionalRaidLogic.SPAWN_MAX_RADIUS + 0.5D,
                        "Spawn offset out of ring bounds: " + off + " dist=" + d);
            }
        }

        // All 6 StartDenial branches in priority order
        assertEquals(StartDenial.DISABLED_BY_CONFIG,
                DimensionalRaidLogic.checkStart(false, true, false, 0, 1_000_000L, 500_000L));
        assertEquals(StartDenial.NOT_FORMED,
                DimensionalRaidLogic.checkStart(true, false, false, 0, 1_000_000L, 500_000L));
        assertEquals(StartDenial.ALREADY_ACTIVE,
                DimensionalRaidLogic.checkStart(true, true, true, 0, 1_000_000L, 500_000L));
        assertEquals(StartDenial.ON_COOLDOWN,
                DimensionalRaidLogic.checkStart(true, true, false, 120, 1_000_000L, 500_000L));
        assertEquals(StartDenial.INSUFFICIENT_ENERGY,
                DimensionalRaidLogic.checkStart(true, true, false, 0, 499_999L, 500_000L));
        assertEquals(StartDenial.ALLOWED,
                DimensionalRaidLogic.checkStart(true, true, false, 0, 500_000L, 500_000L));

        // BossBar progress & timeout boundaries
        assertEquals(0.0F, DimensionalRaidLogic.bossBarProgress(0, 5), 1.0E-6F);
        assertEquals(0.5F, DimensionalRaidLogic.bossBarProgress(3, 6), 1.0E-6F);
        assertEquals(1.0F, DimensionalRaidLogic.bossBarProgress(9, 6), 1.0E-6F);
        assertFalse(DimensionalRaidLogic.isTimedOut(DimensionalRaidLogic.TIMEOUT_TICKS - 1));
        assertTrue(DimensionalRaidLogic.isTimedOut(DimensionalRaidLogic.TIMEOUT_TICKS));

        // Difficulty scaling + NaN guard
        assertEquals(180.0D, DimensionalRaidLogic.scaledHealth(180.0D, Double.NaN), 1.0E-6D);
        assertEquals(360.0D, DimensionalRaidLogic.scaledHealth(180.0D, 2.0D), 1.0E-6D);
        assertEquals(24.0D, DimensionalRaidLogic.scaledAttack(12.0D, 2.0D), 1.0E-6D);

        // Victory reward bundle
        RewardBundle base = DimensionalRaidLogic.victoryRewards(1.0D);
        assertTrue(base.specializationModules() >= 4);
        assertTrue(base.phaseCores() >= 2);
        assertTrue(base.machineXp() >= 25_000L);
        RewardBundle hard = DimensionalRaidLogic.victoryRewards(2.0D);
        assertTrue(hard.specializationModules() > base.specializationModules());
        assertTrue(hard.machineXp() > base.machineXp());
    }

    @Test
    void modpackBalanceConfigHelperAndTomlSpecSectionsAreSafeAndExact() throws IOException {
        // Pure-Java helper clamping & NaN/Inf protection
        assertEquals(1.0D, ConfigBalanceHelper.clampMultiplier(Double.NaN, 0.1D, 100.0D, 1.0D), 1.0E-6D);
        assertEquals(1.0D, ConfigBalanceHelper.clampMultiplier(Double.POSITIVE_INFINITY, 0.1D, 100.0D, 1.0D), 1.0E-6D);
        assertEquals(0.1D, ConfigBalanceHelper.clampMultiplier(-5.0D, 0.1D, 100.0D, 1.0D), 1.0E-6D);
        assertEquals(100.0D, ConfigBalanceHelper.clampMultiplier(999.0D, 0.1D, 100.0D, 1.0D), 1.0E-6D);

        assertEquals(200, ConfigBalanceHelper.scalePositiveInt(100, 2.0D));
        assertEquals(0, ConfigBalanceHelper.scalePositiveInt(0, 2.0D));
        assertEquals(500_000L, ConfigBalanceHelper.scalePositiveLong(250_000L, 2.0D));

        // Processing speed inverse scaling & zero-cost pack option
        assertEquals(100, ConfigBalanceHelper.scaleProcessingTime(100, 1.0D));
        assertEquals(50, ConfigBalanceHelper.scaleProcessingTime(100, 2.0D));
        assertEquals(200, ConfigBalanceHelper.scaleProcessingTime(100, 0.5D));
        assertEquals(1, ConfigBalanceHelper.scaleProcessingTime(5, 100.0D));
        assertEquals(0, ConfigBalanceHelper.scaleEnergyConsumption(40, 0.0D));
        assertEquals(80, ConfigBalanceHelper.scaleEnergyConsumption(40, 2.0D));

        // Configured upgrade limits at Lv.1, Lv.99, Lv.1000
        assertEquals(4, ConfigBalanceHelper.configuredUpgradeLimit(1, 4, 96));
        assertEquals(96, ConfigBalanceHelper.configuredUpgradeLimit(99, 4, 96));
        assertEquals(1000, ConfigBalanceHelper.configuredUpgradeLimit(1000, 4, 96));
        assertEquals(8, ConfigBalanceHelper.configuredUpgradeLimit(1, 8, 128));
        assertEquals(128, ConfigBalanceHelper.configuredUpgradeLimit(99, 8, 128));

        // Verify all documented default constants match the Phase 115 specification
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_GENERATION_MULT, 1.0E-6D);
        assertEquals(1.45D, ConfigBalanceHelper.DEFAULT_OVERDRIVE_MULT, 1.0E-6D);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_PROCESSING_SPEED_MULT, 1.0E-6D);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_ENERGY_CONSUMPTION_MULT, 1.0E-6D);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_TURRET_DAMAGE_MULT, 1.0E-6D);
        assertEquals(16, ConfigBalanceHelper.DEFAULT_TURRET_BASE_RANGE);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_WEAPON_DAMAGE_MULT, 1.0E-6D);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_MACHINE_XP_MULT, 1.0E-6D);
        assertEquals(4, ConfigBalanceHelper.DEFAULT_BASE_UPGRADE_LIMIT);
        assertEquals(96, ConfigBalanceHelper.DEFAULT_MAX_STANDARD_UPGRADE_LIMIT);
        assertTrue(ConfigBalanceHelper.DEFAULT_ENABLE_DIMENSIONAL_RAID);
        assertEquals(500_000, ConfigBalanceHelper.DEFAULT_RAID_ACTIVATION_COST_FE);
        assertEquals(1.0D, ConfigBalanceHelper.DEFAULT_RAID_DIFFICULTY_MULT, 1.0E-6D);

        // Verify Config.java declares all 5 TOML sections and all 13 Modpack balance keys
        String configSrc = Files.readString(Path.of("src/main/java/com/industrialcore/Config.java"));
        for (String section : List.of(
                "push(\"generation\")",
                "push(\"processing\")",
                "push(\"defense_and_weapons\")",
                "push(\"upgrades_and_leveling\")",
                "push(\"dimensional_raid\")")) {
            assertTrue(configSrc.contains(section), "Missing TOML section in Config.java: " + section);
        }
        for (String key : List.of(
                "\"generationOutputMultiplier\"",
                "\"overdriveOutputMultiplier\"",
                "\"processingSpeedMultiplier\"",
                "\"energyConsumptionMultiplier\"",
                "\"turretDamageMultiplier\"",
                "\"turretBaseRange\"",
                "\"weaponDamageMultiplier\"",
                "\"machineXpMultiplier\"",
                "\"baseUpgradeLimit\"",
                "\"maxStandardUpgradeLimit\"",
                "\"enableDimensionalRaid\"",
                "\"raidActivationCostFe\"",
                "\"raidDifficultyMultiplier\"")) {
            assertTrue(configSrc.contains(key), "Missing TOML balance key in Config.java: " + key);
        }
    }

    @Test
    void singularityRiftCardLayoutAnd29thAdvancementAreCleanAndComplete() throws IOException {
        Rect card = SingularityReactorLayout.RIFT_CARD;
        Rect btn = SingularityReactorLayout.RIFT_BUTTON;
        assertTrue(card.x() >= SingularityReactorLayout.WIDTH + SingularityReactorLayout.RIFT_CARD_GAP,
                "Rift card must sit cleanly outside the main reactor panel");
        assertTrue(btn.x() > card.x() && btn.right() < card.right(), "Rift button must fit horizontally inside card");
        assertTrue(btn.y() > card.y() && btn.bottom() < card.bottom(), "Rift button must fit vertically inside card");
        assertFalse(card.intersects(SingularityReactorLayout.TELEMETRY_PANEL));
        assertFalse(card.intersects(SingularityReactorLayout.MULTIBLOCK_BAR));
        assertFalse(card.intersects(SingularityReactorLayout.GUIDE_BUTTON));

        assertTrue(AegisAdvancements.ALL_PATHS.contains(AegisAdvancements.DIMENSION_DEFENDER));
        Path advPath = RES.resolve("data/industrialcore/advancement/aegisworks/dimension_defender.json");
        assertTrue(Files.isRegularFile(advPath));
        JsonObject adv = JsonParser.parseString(Files.readString(advPath)).getAsJsonObject();
        assertEquals("industrialcore:aegisworks/singularity_reactor", adv.get("parent").getAsString());
        assertEquals("challenge", adv.getAsJsonObject("display").get("frame").getAsString());

        JsonObject ja = JsonParser.parseString(
                Files.readString(RES.resolve("assets/industrialcore/lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(
                Files.readString(RES.resolve("assets/industrialcore/lang/en_us.json"))).getAsJsonObject();
        String[] requiredKeys = {
                "advancement.industrialcore.dimension_defender.title",
                "advancement.industrialcore.dimension_defender.desc",
                "bossbar.industrialcore.rift_raid",
                "entity.industrialcore.rift_vanguard",
                "entity.industrialcore.rift_ranger",
                "entity.industrialcore.rift_juggernaut",
                "entity.industrialcore.dimensional_warden",
                "message.industrialcore.rift_raid_started",
                "message.industrialcore.rift_raid_next_wave",
                "message.industrialcore.rift_raid_boss_spawn",
                "message.industrialcore.rift_raid_victory",
                "gui.industrialcore.singularity_reactor.rift_title",
                "gui.industrialcore.singularity_reactor.rift_btn",
                "gui.industrialcore.singularity_reactor.rift_safe_badge"
        };
        for (String key : requiredKeys) {
            assertTrue(ja.has(key) && !ja.get(key).getAsString().isBlank(), "Missing ja_jp key: " + key);
            assertTrue(en.has(key) && !en.get(key).getAsString().isBlank(), "Missing en_us key: " + key);
        }
    }
}
