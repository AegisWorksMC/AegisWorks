package com.industrialcore.level;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.UpgradeMath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 84 unit tests for the per-player machine level system:
 * XP curve (Lv.1..99..1000), overflow protection, upgrade cap (1..64), mastery multipliers,
 * ownerUuid == null backwards compatibility, skill tree auto limit-break, and snapshot round-trip.
 */
class MachineLevelTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void xpCurveIsStrictlyMonotonicFromLevel1To1000WithoutOverflow() {
        assertEquals(0L, MachineLevelMath.xpForLevel(1));
        long prev = 0L;
        for (int level = 2; level <= MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL; level++) {
            long xp = MachineLevelMath.xpForLevel(level);
            assertTrue(xp > prev, "XP must strictly increase at level " + level);
            assertEquals(level, MachineLevelMath.levelForXp(xp, true),
                    "Exact threshold must map back to level " + level);
            assertEquals(level - 1, MachineLevelMath.levelForXp(xp - 1L, true),
                    "Threshold - 1 must map to level " + (level - 1));
            prev = xp;
        }
        assertTrue(MachineLevelMath.xpForLevel(1000) > MachineLevelMath.xpForLevel(99));
        // Overflow-safe saturating addition at Long.MAX_VALUE
        assertEquals(MachineLevelMath.xpForLevel(1000),
                MachineLevelMath.addXp(MachineLevelMath.xpForLevel(500), Long.MAX_VALUE, true));
        assertEquals(MachineLevelMath.xpForLevel(99),
                MachineLevelMath.addXp(MachineLevelMath.xpForLevel(50), Long.MAX_VALUE, false));
    }

    @Test
    void nonLimitBrokenPlayersAreCappedAtLevel99() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        assertFalse(progress.isLimitBroken());
        assertEquals(99, progress.getMaxLevel());

        PlayerMachineProgress.ChangeResult res = progress.addXp("crusher", 10_000_000_000L);
        assertEquals(1, res.oldLevel());
        assertEquals(99, res.newLevel());
        assertTrue(res.leveledUp());
        assertFalse(res.justLimitBroke(), "A single machine at Lv.99 must not clear the whole tree");
        assertEquals(MachineLevelMath.xpForLevel(99), progress.getXp("crusher"));
        assertEquals(99, progress.getLevel("crusher"));

        // Further XP stays capped while limitBroken == false
        PlayerMachineProgress.ChangeResult capped = progress.addXp("crusher", 999_999L);
        assertEquals(99, capped.newLevel());
        assertFalse(capped.leveledUp());
        assertEquals(MachineLevelMath.xpForLevel(99), progress.getXp("crusher"));
    }

    @Test
    void upgradeLimitScalesFrom1To64AndPreservesNullOwnerAndCreativeCompatibility() {
        assertEquals(1, MachineLevelMath.upgradeLimitForLevel(1));
        assertEquals(64, MachineLevelMath.upgradeLimitForLevel(99));
        assertEquals(64, MachineLevelMath.upgradeLimitForLevel(100));
        assertEquals(64, MachineLevelMath.upgradeLimitForLevel(1000));

        int prev = 0;
        for (int lvl = 1; lvl <= 99; lvl++) {
            int cap = MachineLevelMath.upgradeLimitForLevel(lvl);
            assertTrue(cap >= prev, "Upgrade cap must never decrease at level " + lvl);
            assertTrue(cap >= 1 && cap <= 64, "Upgrade cap out of [1, 64] at level " + lvl);
            prev = cap;
        }
        assertEquals(32, MachineLevelMath.upgradeLimitForLevel(50));

        // ownerUuid == null -> backwards compatible cap of 64 (GameTests / unowned machines)
        assertEquals(64, MachineLevelMath.effectiveUpgradeLimit(false, 1, false));
        // Creative operator -> cap of 64 regardless of machine level
        assertEquals(64, MachineLevelMath.effectiveUpgradeLimit(true, 1, true));
        // Owned survival machine -> follows owner's machine level
        assertEquals(1, MachineLevelMath.effectiveUpgradeLimit(true, 1, false));
        assertEquals(32, MachineLevelMath.effectiveUpgradeLimit(true, 50, false));
        assertEquals(64, MachineLevelMath.effectiveUpgradeLimit(true, 99, false));
    }

    @Test
    void masteryBonusIsStrictlyNeutralUpToLevel99AndScalesFrom100To1000() {
        for (int lvl = 1; lvl <= 99; lvl++) {
            assertEquals(1.0D, MachineLevelMath.masterySpeedMultiplier(lvl), 1.0E-9D);
            assertEquals(1.0D, MachineLevelMath.masteryEnergyMultiplier(lvl), 1.0E-9D);
            assertEquals(0, MachineLevelMath.masteryEnergySavingPercent(lvl));
            assertEquals(10.0D, UpgradeMath.applyMasterySpeed(10.0D, 100.0D, lvl), 1.0E-9D);
            assertEquals(60, UpgradeMath.applyMasteryTime(60, lvl));
            assertEquals(200, UpgradeMath.applyMasteryEnergy(200, lvl));
        }

        // Lv.100 begins scaling smoothly, reaching 2.50x speed and 0.70x FE cost at Lv.1000
        assertTrue(MachineLevelMath.masterySpeedMultiplier(100) > 1.0D);
        assertTrue(MachineLevelMath.masteryEnergyMultiplier(100) < 1.0D);
        assertEquals(2.50D, MachineLevelMath.masterySpeedMultiplier(1000), 1.0E-6D);
        assertEquals(0.70D, MachineLevelMath.masteryEnergyMultiplier(1000), 1.0E-6D);
        assertEquals(30, MachineLevelMath.masteryEnergySavingPercent(1000));

        assertEquals(25.0D, UpgradeMath.applyMasterySpeed(10.0D, 100.0D, 1000), 1.0E-6D);
        assertEquals(24, UpgradeMath.applyMasteryTime(60, 1000));
        assertEquals(140, UpgradeMath.applyMasteryEnergy(200, 1000));
    }

    @Test
    void skillTreeDefinitionIsValidAcyclicDagCoveringAll38ElectricMachines() {
        assertEquals(38, MachineSkillTree.totalNodes());
        Set<String> seen = new HashSet<>();
        int maxReq = 0;
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            assertTrue(seen.add(node.machineId()), "Duplicate machine node: " + node.machineId());
            assertTrue(node.tier() >= 1 && node.tier() <= 5, "Invalid tier on " + node.machineId());
            assertTrue(node.requiredLevel() >= 1 && node.requiredLevel() <= 25,
                    "Phase 85/97 required level must stay within comfortable Lv.1..25 curve: " + node.machineId());
            maxReq = Math.max(maxReq, node.requiredLevel());
            for (String parent : node.parents()) {
                MachineSkillTree.Node pNode = MachineSkillTree.byId(parent);
                assertNotNull(pNode, "Missing parent node " + parent + " for " + node.machineId());
                assertTrue(pNode.tier() <= node.tier(),
                        "DAG edge must not point backwards in tier: " + parent + " -> " + node.machineId());
            }
        }
        assertEquals(25, maxReq, "Peak tree unlock requirement should be Lv.25 (ore_synthesizer)");
        // Verify acyclicity with DFS
        Map<String, Integer> color = new HashMap<>();
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            assertFalse(hasCycle(node.machineId(), color), "Cycle detected at " + node.machineId());
        }
    }

    @Test
    void perBranchLimitBreakRequiresAllNodesAtLv99AndUnlocksLv1000ForThatBranchOnly() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        assertEquals(6, MachineSkillTree.unlockedCount(progress::getLevel),
                "Only the 6 Tier 1 starter machines across all 4 branches should be unlocked at Lv.1");
        assertFalse(progress.isLimitBroken());
        assertFalse(progress.canLimitBreakBranch(MachineSkillTree.Branch.PRODUCTION));

        // Raise every Production machine to Lv.99 EXCEPT crafter (keep crafter at Lv.98)
        for (MachineSkillTree.Node node : MachineSkillTree.nodesInBranch(MachineSkillTree.Branch.PRODUCTION)) {
            String id = node.machineId();
            if ("crafter".equals(id)) {
                progress.setLevel(id, 98);
            } else {
                progress.setLevel(id, 99);
            }
        }
        assertFalse(progress.canLimitBreakBranch(MachineSkillTree.Branch.PRODUCTION),
                "Branch limit break must require ALL nodes in the branch at Lv.99+");
        assertFalse(progress.tryLimitBreakBranch(MachineSkillTree.Branch.PRODUCTION));

        // Now raise crafter to Lv.99 via XP -> eligible for explicit Production branch limit break
        progress.addXp("crafter", MachineLevelMath.xpForLevel(99));
        assertEquals(99, progress.getLevel("crafter"));
        assertTrue(progress.canLimitBreakBranch(MachineSkillTree.Branch.PRODUCTION));
        assertTrue(progress.tryLimitBreakBranch(MachineSkillTree.Branch.PRODUCTION));
        assertTrue(progress.isBranchLimitBroken(MachineSkillTree.Branch.PRODUCTION));
        assertFalse(progress.isBranchLimitBroken(MachineSkillTree.Branch.NATURE));
        assertEquals(1000, progress.getMaxLevel("crusher"));
        assertEquals(99, progress.getMaxLevel("crop_harvester"));

        // After Production branch limit break, crusher can climb all the way to Lv.1000
        PlayerMachineProgress.ChangeResult climb = progress.addXp("crusher", MachineLevelMath.xpForLevel(1000));
        assertEquals(1000, climb.newLevel());
        assertEquals(1000, progress.getLevel("crusher"));

        // Nature branch remains capped at Lv.99 until Nature itself breaks the limit
        progress.addXp("crop_harvester", MachineLevelMath.xpForLevel(500));
        assertEquals(99, progress.getLevel("crop_harvester"));
    }

    @Test
    void progressSnapshotRoundTripPreservesLimitBrokenAndLongXp() {
        PlayerMachineProgress original = new PlayerMachineProgress();
        original.setLimitBroken(true);
        original.setLevel("crusher", 1000);
        original.setLevel("quarry", 250);
        original.addXp("crafter", 123_456L);

        Map<String, Long> snapshot = original.snapshotXp();
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(original.isLimitBroken(), snapshot);

        assertTrue(restored.isLimitBroken());
        assertEquals(1000, restored.getLevel("crusher"));
        assertEquals(MachineLevelMath.xpForLevel(1000), restored.getXp("crusher"));
        assertEquals(250, restored.getLevel("quarry"));
        assertEquals(123_456L, restored.getXp("crafter"));
        assertEquals(1, restored.getLevel("metal_press"));

        // Turning limit break off clamps stored XP cleanly back to Lv.99
        restored.setLimitBroken(false);
        assertFalse(restored.isLimitBroken());
        assertEquals(99, restored.getLevel("crusher"));
        assertEquals(MachineLevelMath.xpForLevel(99), restored.getXp("crusher"));
    }

    @Test
    void revealedNodesPersistCleanlySupportLegacySaveCompatAndSortPendingByTier() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        // Initial state: only Tier 1 starters across all 4 branches are revealed, no pending reveals
        Set<String> expectedStarters = Set.of(
                "crusher", "electric_furnace", "metal_press",
                "crop_harvester", "mob_fan", "auto_builder");
        assertEquals(expectedStarters, progress.snapshotRevealedNodes());
        assertTrue(progress.pendingReveals().isEmpty());

        // Reaching prerequisites unlocks multiple nodes across Tier 2 & Tier 3 simultaneously
        progress.setLevel("crusher", 5);
        progress.setLevel("metal_press", 5);
        progress.setLevel("alloy_furnace", 6);
        progress.setLevel("electrolyzer", 8);

        java.util.List<String> pending = progress.pendingReveals();
        assertTrue(pending.contains("alloy_furnace"));
        assertTrue(pending.contains("electrolyzer"));
        assertTrue(pending.contains("crafter"));
        assertTrue(pending.contains("quarry"));
        assertTrue(pending.contains("assembler"));
        assertTrue(pending.contains("precision_crusher"));
        assertTrue(pending.contains("reconstructor"));

        // Must be strictly ordered non-decreasing by Tier (parent before child)
        int prevTier = 0;
        for (String id : pending) {
            int tier = MachineSkillTree.byId(id).tier();
            assertTrue(tier >= prevTier, "pendingReveals must stay in Tier order: " + pending);
            prevTier = tier;
        }

        // Cannot reveal an un-unlocked node
        assertFalse(progress.markRevealed(java.util.List.of("ore_synthesizer")));
        assertFalse(progress.isNodeRevealed("ore_synthesizer"));

        // Marking unlocked nodes revealed persists across explicit snapshot round-trip
        assertTrue(progress.markRevealed(java.util.List.of("alloy_furnace", "electrolyzer")));
        PlayerMachineProgress roundTrip = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), progress.snapshotRevealedNodes());
        assertTrue(roundTrip.isNodeRevealed("alloy_furnace"));
        assertTrue(roundTrip.isNodeRevealed("electrolyzer"));
        assertFalse(roundTrip.isNodeRevealed("assembler"));
        assertTrue(roundTrip.pendingReveals().contains("assembler"));

        // Legacy pre-Phase 86 save (revealed == null): all currently unlocked nodes auto-marked revealed
        PlayerMachineProgress legacyLoaded = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), null);
        assertTrue(legacyLoaded.pendingReveals().isEmpty(),
                "Legacy save without RevealedNodes must not burst pending animations");
        assertTrue(legacyLoaded.isNodeRevealed("assembler"));
        assertFalse(legacyLoaded.isNodeRevealed("ore_synthesizer"));

        // Reset restores revealedNodes strictly to Tier 1 starters
        roundTrip.reset();
        assertEquals(expectedStarters, roundTrip.snapshotRevealedNodes());
        assertTrue(roundTrip.pendingReveals().isEmpty());
    }

    @Test
    void allTreeNodesAndPhase84MessagesExistInBothLanguageFiles() throws IOException {
        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG_DIR.resolve(langFile)))
                    .getAsJsonObject();
            for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
                assertTrue(lang.has(node.translationKey()),
                        langFile + " missing translation for " + node.translationKey());
            }
            for (String key : new String[] {
                    "gui.industrialcore.tooltip.machine_level",
                    "gui.industrialcore.tooltip.machine_mastery",
                    "message.industrialcore.machine_level_up",
                    "message.industrialcore.limit_break_unlocked"
            }) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                        langFile + " missing Phase 84 key " + key);
            }
        }
    }

    private static boolean hasCycle(String id, Map<String, Integer> color) {
        int state = color.getOrDefault(id, 0);
        if (state == 1) {
            return true;
        }
        if (state == 2) {
            return false;
        }
        color.put(id, 1);
        MachineSkillTree.Node node = MachineSkillTree.byId(id);
        if (node != null) {
            for (String parent : node.parents()) {
                if (hasCycle(parent, color)) {
                    return true;
                }
            }
        }
        color.put(id, 2);
        return false;
    }
}
