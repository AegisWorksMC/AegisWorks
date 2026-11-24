package com.industrialcore.level;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.level.MachineSkillTree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 97 pure-Java unit tests for:
 * <ul>
 *   <li>4-Branch, 38-Machine unified {@link MachineSkillTree} DAG integrity & legacy compat</li>
 *   <li>Per-branch Lv.1000 Limit Break rules & isolation</li>
 *   <li>{@link CraftUnlockLogic} prerequisite gating, Creative bypass, and config bypass</li>
 *   <li>4-Branch {@link SkillTreeLayout} tabs, per-branch hit-testing, and localization completeness</li>
 * </ul>
 */
class SkillTree4BranchTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void fourBranchesCoverAll38MachinesWithZeroCyclesOrOrphansAndPreserveLegacy17Ids() {
        assertEquals(38, MachineSkillTree.totalNodes());
        assertEquals(19, MachineSkillTree.nodesInBranch(Branch.PRODUCTION).size());
        assertEquals(6, MachineSkillTree.nodesInBranch(Branch.NATURE).size());
        assertEquals(7, MachineSkillTree.nodesInBranch(Branch.TRAP).size());
        assertEquals(6, MachineSkillTree.nodesInBranch(Branch.LIFE).size());

        List<String> legacy17 = List.of(
                "crusher", "electric_furnace", "metal_press",
                "alloy_furnace", "electrolyzer", "crafter", "quarry",
                "assembler", "precision_crusher", "reconstructor",
                "fast_crusher", "seed_cultivator", "large_quarry", "matter_amplifier",
                "phase_reactor", "quantum_compressor", "ore_synthesizer"
        );
        for (String id : legacy17) {
            Node n = MachineSkillTree.byId(id);
            assertNotNull(n, "Legacy node missing: " + id);
            assertEquals(Branch.PRODUCTION, n.branch(), "Legacy node must remain in PRODUCTION: " + id);
        }

        // Verify Quantum IO nodes live in PRODUCTION under assembler (Tier 4, req Lv.8)
        for (String qNode : List.of("quantum_importer", "quantum_exporter")) {
            Node n = MachineSkillTree.byId(qNode);
            assertNotNull(n, "Missing Quantum IO node: " + qNode);
            assertEquals(Branch.PRODUCTION, n.branch());
            assertEquals(4, n.tier());
            assertEquals(8, n.requiredLevel());
            assertEquals(List.of("assembler"), n.parents());
        }

        // Verify every node has valid branch, valid same-branch parents, non-decreasing tiers, and no cycles
        Set<String> seen = new HashSet<>();
        Map<String, Integer> color = new HashMap<>();
        for (Node node : MachineSkillTree.nodes()) {
            assertTrue(seen.add(node.machineId()), "Duplicate node id: " + node.machineId());
            assertNotNull(node.branch());
            if (node.tier() == 1) {
                assertTrue(node.parents().isEmpty(), "Tier 1 root must have no parents: " + node.machineId());
            } else {
                assertFalse(node.parents().isEmpty(), "Non-root node must have parent(s): " + node.machineId());
            }
            for (String parentId : node.parents()) {
                Node parent = MachineSkillTree.byId(parentId);
                assertNotNull(parent, "Orphan parent ref: " + parentId + " -> " + node.machineId());
                assertEquals(node.branch(), parent.branch(),
                        "Parent must be in the same branch: " + parentId + " -> " + node.machineId());
                assertTrue(parent.tier() <= node.tier(),
                        "Parent tier must be <= child tier: " + parentId + " -> " + node.machineId());
            }
            assertFalse(hasCycle(node.machineId(), color), "Cycle at " + node.machineId());
        }
    }

    @Test
    void perBranchLimitBreakIsStrictlyScopedAndSurvivesSnapshotRoundTrip() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (Branch b : Branch.values()) {
            assertFalse(progress.isBranchLimitBroken(b));
            assertFalse(progress.canLimitBreakBranch(b));
        }

        // Raise 5 of 6 Nature nodes to Lv.99, leave bio_feeder at Lv.98 -> still ineligible
        for (Node n : MachineSkillTree.nodesInBranch(Branch.NATURE)) {
            progress.setLevel(n.machineId(), "bio_feeder".equals(n.machineId()) ? 98 : 99);
        }
        assertEquals(6, MachineSkillTree.unlockedCountInBranch(Branch.NATURE, progress::getLevel));
        assertEquals(5, MachineSkillTree.maxedCountInBranch(Branch.NATURE, progress::getLevel));
        assertFalse(progress.canLimitBreakBranch(Branch.NATURE));
        assertFalse(progress.tryLimitBreakBranch(Branch.NATURE));

        // Raise bio_feeder to Lv.99 -> Nature eligible and activates!
        progress.setLevel("bio_feeder", 99);
        assertEquals(6, MachineSkillTree.maxedCountInBranch(Branch.NATURE, progress::getLevel));
        assertTrue(progress.canLimitBreakBranch(Branch.NATURE));
        assertTrue(progress.tryLimitBreakBranch(Branch.NATURE));
        assertFalse(progress.tryLimitBreakBranch(Branch.NATURE), "Second activation must return false");

        assertTrue(progress.isBranchLimitBroken(Branch.NATURE));
        assertFalse(progress.isBranchLimitBroken(Branch.PRODUCTION));
        assertFalse(progress.isBranchLimitBroken(Branch.TRAP));
        assertFalse(progress.isBranchLimitBroken(Branch.LIFE));

        // Nature machines can now level past 99 up to 1000, others remain strictly capped at 99
        assertEquals(1000, progress.getMaxLevel("crop_harvester"));
        assertEquals(1000, progress.getMaxLevel("bio_feeder"));
        assertEquals(99, progress.getMaxLevel("crusher"));
        assertEquals(99, progress.getMaxLevel("mob_fan"));
        assertEquals(99, progress.getMaxLevel("auto_builder"));

        progress.addXp("crop_harvester",
                MachineLevelMath.xpForLevel(250) - progress.getXp("crop_harvester"));
        progress.addXp("mob_fan", MachineLevelMath.xpForLevel(250));
        assertEquals(250, progress.getLevel("crop_harvester"));
        assertEquals(99, progress.getLevel("mob_fan"));

        // Snapshot round-trip preserves exact per-branch flags & Lv.250 XP
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(),
                progress.snapshotXp(),
                progress.snapshotRevealedNodes(),
                progress.snapshotLimitBrokenBranchIds());
        assertTrue(restored.isBranchLimitBroken(Branch.NATURE));
        assertFalse(restored.isBranchLimitBroken(Branch.TRAP));
        assertEquals(250, restored.getLevel("crop_harvester"));
        assertEquals(99, restored.getLevel("mob_fan"));

        // Legacy pre-Phase 97 save with limitBroken=true and null branch set -> migrates all 4 branches
        PlayerMachineProgress legacyAll = PlayerMachineProgress.fromSnapshot(
                true, progress.snapshotXp(), progress.snapshotRevealedNodes(), null);
        for (Branch b : Branch.values()) {
            assertTrue(legacyAll.isBranchLimitBroken(b), "Legacy global limit break must migrate branch " + b);
        }
    }

    @Test
    void craftUnlockLogicGatesTier2To5RespectsRootsCreativeAndConfigToggle() {
        PlayerMachineProgress progress = new PlayerMachineProgress();

        // All 6 Tier 1 roots & untracked items are always craftable at Lv.1
        for (String freeId : List.of("crusher", "electric_furnace", "metal_press",
                "crop_harvester", "mob_fan", "auto_builder", "quantum_storage_core", "stone")) {
            CraftUnlockLogic.CraftCheckResult r = CraftUnlockLogic.evaluate(freeId, progress::getLevel, false, true);
            assertTrue(r.allowed(), "Root/untracked must be craftable at Lv.1: " + freeId);
            assertTrue(r.missing().isEmpty());
        }

        // Tier 2 Nature machine (tree_harvester) requires crop_harvester Lv.3
        CraftUnlockLogic.CraftCheckResult lockedTree = CraftUnlockLogic.evaluate(
                "tree_harvester", progress::getLevel, false, true);
        assertFalse(lockedTree.allowed());
        assertEquals(1, lockedTree.missing().size());
        assertEquals("crop_harvester", lockedTree.missing().get(0).parentId());
        assertEquals(3, lockedTree.missing().get(0).requiredLevel());
        assertEquals(1, lockedTree.missing().get(0).currentLevel());

        // Creative mode bypasses gate
        assertTrue(CraftUnlockLogic.evaluate("tree_harvester", progress::getLevel, true, true).allowed());
        assertTrue(CraftUnlockLogic.evaluate("ore_synthesizer", progress::getLevel, true, true).allowed());

        // Config gateEnabled=false bypasses gate
        assertTrue(CraftUnlockLogic.evaluate("tree_harvester", progress::getLevel, false, false).allowed());
        assertTrue(CraftUnlockLogic.evaluate("warp_pad", progress::getLevel, false, false).allowed());

        // Level crop_harvester to Lv.2 -> still blocked; Lv.3 -> unlocked!
        progress.setLevel("crop_harvester", 2);
        assertFalse(CraftUnlockLogic.evaluate("tree_harvester", progress::getLevel, false, true).allowed());
        progress.setLevel("crop_harvester", 3);
        assertTrue(CraftUnlockLogic.evaluate("tree_harvester", progress::getLevel, false, true).allowed());

        // Multi-parent Tier 5 node (warp_pad requires climate_controller Lv.12 AND wireless_charger Lv.12)
        progress.setLevel("climate_controller", 12);
        progress.setLevel("wireless_charger", 11);
        CraftUnlockLogic.CraftCheckResult warpLocked = CraftUnlockLogic.evaluate(
                "warp_pad", progress::getLevel, false, true);
        assertFalse(warpLocked.allowed());
        assertEquals(1, warpLocked.missing().size());
        assertEquals("wireless_charger", warpLocked.missing().get(0).parentId());

        progress.setLevel("wireless_charger", 12);
        assertTrue(CraftUnlockLogic.evaluate("warp_pad", progress::getLevel, false, true).allowed());
    }

    @Test
    void layoutBranchTabsPerBranchHitTestingAndPhase97LangKeysAreComplete() throws IOException {
        // 4 branch tabs hit-test cleanly to each Branch enum in order
        Branch[] branches = Branch.values();
        assertEquals(4, branches.length);
        for (int i = 0; i < branches.length; i++) {
            SkillTreeLayout.Rect r = SkillTreeLayout.branchTabRect(branches[i]);
            assertEquals(branches[i], SkillTreeLayout.branchTabAt(r.centerX(), r.centerY()));
        }
        assertNull(SkillTreeLayout.branchTabAt(0, 0));

        // Per-branch nodeAt hits the active branch's nodes without cross-branch collision
        for (Branch branch : branches) {
            for (SkillTreeLayout.NodeSlot slot : SkillTreeLayout.nodeSlots(branch)) {
                double panX = SkillTreeLayout.centerPanXFor(slot.machineId());
                double px = SkillTreeLayout.TREE_VIEWPORT.x() + slot.rect().centerX() - panX;
                double py = SkillTreeLayout.TREE_VIEWPORT.y() + slot.rect().centerY();
                assertEquals(slot.machineId(), SkillTreeLayout.nodeAt(branch, px, py, panX, 0.0D),
                        "Hit-test failed for " + slot.machineId() + " in branch " + branch);
            }
        }

        // Verify all Phase 97 lang keys exist and are non-blank in BOTH ja_jp.json and en_us.json
        List<String> requiredKeys = List.of(
                "gui.industrialcore.skill_tree.branch.production",
                "gui.industrialcore.skill_tree.branch.nature",
                "gui.industrialcore.skill_tree.branch.trap",
                "gui.industrialcore.skill_tree.branch.life",
                "gui.industrialcore.skill_tree.branches_broken_badge",
                "gui.industrialcore.skill_tree.branch_progress",
                "gui.industrialcore.skill_tree.branch_limit_broken",
                "gui.industrialcore.skill_tree.branch_limit_btn",
                "gui.industrialcore.skill_tree.branch_limit_locked",
                "message.industrialcore.branch_limit_break_unlocked",
                "message.industrialcore.craft_locked",
                "tooltip.industrialcore.craft_unlocked",
                "tooltip.industrialcore.craft_locked_req"
        );
        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG_DIR.resolve(langFile)))
                    .getAsJsonObject();
            for (String key : requiredKeys) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                        langFile + " missing Phase 97 key " + key);
            }
            for (Node node : MachineSkillTree.nodes()) {
                assertTrue(lang.has(node.translationKey()), langFile + " missing " + node.translationKey());
                assertTrue(lang.has(node.guideKey()) && !lang.get(node.guideKey()).getAsString().isBlank(),
                        langFile + " missing " + node.guideKey());
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
        Node node = MachineSkillTree.byId(id);
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
