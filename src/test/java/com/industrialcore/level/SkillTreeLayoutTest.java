package com.industrialcore.level;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.level.SkillTreeLayout.InfraEntry;
import com.industrialcore.level.SkillTreeLayout.NodeSlot;
import com.industrialcore.level.SkillTreeLayout.NodeVisibility;
import com.industrialcore.level.SkillTreeLayout.NodeVisualState;
import com.industrialcore.level.SkillTreeLayout.OreLineStage;
import com.industrialcore.level.SkillTreeLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 85 pure-Java unit tests for SkillTreeLayout geometry, 32,768x ore-line data,
 * infrastructure guide keys, and LevelToastQueue coalescing & cooldown protection.
 */
class SkillTreeLayoutTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void all38NodesHaveUniqueNonOverlappingCanvasCoordinatesPerBranch() {
        List<NodeSlot> allSlots = SkillTreeLayout.nodeSlots();
        assertEquals(MachineSkillTree.totalNodes(), allSlots.size());
        assertEquals(38, allSlots.size());

        Set<String> seenAll = new HashSet<>();
        for (MachineSkillTree.Branch branch : MachineSkillTree.Branch.values()) {
            List<NodeSlot> slots = SkillTreeLayout.nodeSlots(branch);
            assertEquals(MachineSkillTree.nodesInBranch(branch).size(), slots.size(),
                    "Slot count mismatch for branch " + branch);
            for (int i = 0; i < slots.size(); i++) {
                NodeSlot a = slots.get(i);
                assertTrue(seenAll.add(a.machineId()), "Duplicate slot: " + a.machineId());
                MachineSkillTree.Node node = MachineSkillTree.byId(a.machineId());
                assertNotNull(node, "Layout slot not in MachineSkillTree: " + a.machineId());
                assertEquals(node.tier(), a.tier(), "Tier mismatch for " + a.machineId());
                assertEquals(branch, node.branch(), "Branch mismatch for " + a.machineId());

                Rect ra = a.rect();
                assertTrue(ra.x() >= 0 && ra.right() <= SkillTreeLayout.CANVAS_W,
                        "Node out of horizontal canvas bounds: " + a.machineId());
                assertTrue(ra.y() >= 0 && ra.bottom() <= SkillTreeLayout.CANVAS_H,
                        "Node out of vertical canvas bounds: " + a.machineId());

                for (int j = i + 1; j < slots.size(); j++) {
                    NodeSlot b = slots.get(j);
                    assertFalse(ra.overlaps(b.rect()),
                            "Nodes overlap in branch " + branch + ": " + a.machineId() + " and " + b.machineId());
                }
            }
        }
        assertEquals(38, seenAll.size());
    }

    @Test
    void shellHeaderTabsBranchBarViewportsAndDetailPanelFitWithinPanelWithoutOverlap() {
        Rect panel = new Rect(0, 0, SkillTreeLayout.WIDTH, SkillTreeLayout.HEIGHT);
        assertInside(panel, SkillTreeLayout.HEADER, "HEADER");
        assertInside(panel, SkillTreeLayout.CLOSE_BUTTON, "CLOSE_BUTTON");
        assertInside(panel, SkillTreeLayout.TREE_VIEWPORT, "TREE_VIEWPORT");
        assertInside(panel, SkillTreeLayout.DETAIL_PANEL, "DETAIL_PANEL");
        assertInside(panel, SkillTreeLayout.GUIDE_VIEWPORT, "GUIDE_VIEWPORT");
        assertInside(panel, SkillTreeLayout.BRANCH_HEADER_BAR, "BRANCH_HEADER_BAR");
        assertInside(panel, SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON, "BRANCH_LIMIT_BREAK_BUTTON");
        assertFalse(SkillTreeLayout.BRANCH_HEADER_BAR.overlaps(SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON),
                "Branch header bar and limit-break button must not overlap");
        assertFalse(SkillTreeLayout.DETAIL_PANEL.overlaps(SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON),
                "Limit-break button and detail panel must not overlap");

        assertFalse(SkillTreeLayout.TREE_VIEWPORT.overlaps(SkillTreeLayout.DETAIL_PANEL),
                "Tree viewport and detail panel must not overlap");
        assertFalse(SkillTreeLayout.BRANCH_HEADER_BAR.overlaps(SkillTreeLayout.TREE_VIEWPORT),
                "Branch header bar and tree viewport must not overlap");
        assertFalse(SkillTreeLayout.BRANCH_HEADER_BAR.overlaps(SkillTreeLayout.DETAIL_PANEL),
                "Branch header bar and detail panel must not overlap");

        List<Rect> tabs = SkillTreeLayout.TAB_RECTS;
        assertEquals(3, tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            Rect t = tabs.get(i);
            assertInside(panel, t, "TAB_" + i);
            assertFalse(t.overlaps(SkillTreeLayout.HEADER), "Tab overlaps header: " + i);
            assertFalse(t.overlaps(SkillTreeLayout.TREE_VIEWPORT), "Tab overlaps tree viewport: " + i);
            assertFalse(t.overlaps(SkillTreeLayout.DETAIL_PANEL), "Tab overlaps detail panel: " + i);
            assertFalse(t.overlaps(SkillTreeLayout.GUIDE_VIEWPORT), "Tab overlaps guide viewport: " + i);
            for (int j = i + 1; j < tabs.size(); j++) {
                assertFalse(t.overlaps(tabs.get(j)), "Tabs overlap: " + i + " and " + j);
            }
        }

        List<Rect> branchTabs = SkillTreeLayout.BRANCH_TAB_RECTS;
        assertEquals(4, branchTabs.size());
        for (int i = 0; i < branchTabs.size(); i++) {
            Rect bt = branchTabs.get(i);
            assertInside(panel, bt, "BRANCH_TAB_" + i);
            assertFalse(bt.overlaps(SkillTreeLayout.BRANCH_HEADER_BAR), "Branch tab overlaps header bar: " + i);
            assertFalse(bt.overlaps(SkillTreeLayout.TREE_VIEWPORT), "Branch tab overlaps viewport: " + i);
            for (int j = i + 1; j < branchTabs.size(); j++) {
                assertFalse(bt.overlaps(branchTabs.get(j)), "Branch tabs overlap: " + i + " and " + j);
            }
        }
    }

    @Test
    void panClampHitTestAndVisualStatesWorkAccurately() {
        assertEquals(0.0D, SkillTreeLayout.clampPanX(-50.0D));
        assertEquals(SkillTreeLayout.maxPanX(), SkillTreeLayout.clampPanX(9999.0D));

        // Hit-test crusher at Tier 1 with panX = 0
        NodeSlot crusher = SkillTreeLayout.slotFor("crusher");
        assertNotNull(crusher);
        double px = SkillTreeLayout.TREE_VIEWPORT.x() + crusher.rect().centerX();
        double py = SkillTreeLayout.TREE_VIEWPORT.y() + crusher.rect().centerY();
        assertEquals("crusher", SkillTreeLayout.nodeAt(px, py, 0.0D, 0.0D));
        assertNull(SkillTreeLayout.nodeAt(SkillTreeLayout.DETAIL_PANEL.centerX(),
                SkillTreeLayout.DETAIL_PANEL.centerY(), 0.0D, 0.0D));

        // Centering Tier 5 ore_synthesizer brings it into view and clickable
        double panT5 = SkillTreeLayout.centerPanXFor("ore_synthesizer");
        NodeSlot synth = SkillTreeLayout.slotFor("ore_synthesizer");
        assertNotNull(synth);
        double spx = SkillTreeLayout.TREE_VIEWPORT.x() + synth.rect().centerX() - panT5;
        double spy = SkillTreeLayout.TREE_VIEWPORT.y() + synth.rect().centerY();
        assertEquals("ore_synthesizer", SkillTreeLayout.nodeAt(spx, spy, panT5, 0.0D));

        // Tab hit-testing
        assertEquals(SkillTreeLayout.TAB_TREE, SkillTreeLayout.tabAt(
                SkillTreeLayout.TAB_TREE_RECT.centerX(), SkillTreeLayout.TAB_TREE_RECT.centerY()));
        assertEquals(SkillTreeLayout.TAB_ORELINE, SkillTreeLayout.tabAt(
                SkillTreeLayout.TAB_ORELINE_RECT.centerX(), SkillTreeLayout.TAB_ORELINE_RECT.centerY()));
        assertEquals(SkillTreeLayout.TAB_INFRA, SkillTreeLayout.tabAt(
                SkillTreeLayout.TAB_INFRA_RECT.centerX(), SkillTreeLayout.TAB_INFRA_RECT.centerY()));
        assertEquals(-1, SkillTreeLayout.tabAt(0, 0));

        // Visual states
        PlayerMachineProgress progress = new PlayerMachineProgress();
        assertEquals(NodeVisualState.UNLOCKED,
                SkillTreeLayout.visualState(MachineSkillTree.byId("crusher"), progress::getLevel, false));
        assertEquals(NodeVisualState.LOCKED,
                SkillTreeLayout.visualState(MachineSkillTree.byId("electrolyzer"), progress::getLevel, false));
        progress.setLevel("crusher", MachineSkillTree.highestRequiredLevelAsParent("crusher"));
        assertEquals(NodeVisualState.MASTERED,
                SkillTreeLayout.visualState(MachineSkillTree.byId("crusher"), progress::getLevel, false));
        progress.setLimitBroken(true);
        progress.setLevel("crusher", 150);
        assertEquals(NodeVisualState.LIMIT_BROKEN,
                SkillTreeLayout.visualState(MachineSkillTree.byId("crusher"), progress::getLevel, true));
    }

    @Test
    void oreLineGuideCoversAll15StagesUpTo32768xAndAllLangKeysExist() throws IOException {
        List<OreLineStage> stages = SkillTreeLayout.oreLineStages();
        assertEquals(15, stages.size());
        for (int i = 0; i < stages.size(); i++) {
            OreLineStage st = stages.get(i);
            assertEquals(i + 1, st.stage());
            assertEquals(1 << (i + 1), st.cumulativeMultiplier());
            assertEquals(i + 1 >= 7, st.isSynthesizerStage());
            assertTrue(MachineSkillTree.isTracked(st.machineId()));
        }
        assertEquals(32_768, stages.get(14).cumulativeMultiplier());

        for (String file : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG_DIR.resolve(file))).getAsJsonObject();
            for (MachineSkillTree.Node n : MachineSkillTree.nodes()) {
                assertTrue(lang.has(n.guideKey()) && !lang.get(n.guideKey()).getAsString().isBlank(),
                        file + " missing guide key " + n.guideKey());
            }
            for (InfraEntry e : SkillTreeLayout.infraEntries()) {
                String blockKey = "block.industrialcore." + e.blockId();
                String secKey = "gui.industrialcore.infra.section." + e.sectionKey();
                String specKey = "gui.industrialcore.infra.spec." + e.specKey();
                String rateKey = "gui.industrialcore.infra.rate." + e.specKey();
                assertTrue(lang.has(blockKey), file + " missing " + blockKey);
                assertTrue(lang.has(secKey), file + " missing " + secKey);
                assertTrue(lang.has(specKey), file + " missing " + specKey);
                assertTrue(lang.has(rateKey), file + " missing " + rateKey);
            }
        }
    }

    @Test
    void toastQueueCoalescesParallelLevelUpsRespectsCooldownAndPrioritizesLimitBreak() {
        LevelToastQueue q = new LevelToastQueue();
        long t = 10_000L;

        // First level-up plays SE and queues 1 entry
        assertTrue(q.pushLevelUp("crusher", 3, 99, 0, t));
        assertEquals(1, q.activeEntries(t).size());
        assertEquals(0, q.activeEntries(t).get(0).extraMachines());

        // Second machine levels up 100ms later -> coalesced into same entry, SE throttled by cooldown
        assertFalse(q.pushLevelUp("metal_press", 4, 99, 0, t + 100L));
        List<LevelToastQueue.Entry> merged = q.activeEntries(t + 100L);
        assertEquals(1, merged.size());
        assertEquals("metal_press", merged.get(0).primaryMachineId());
        assertEquals(1, merged.get(0).extraMachines());

        // After cooldown elapses, SE plays again
        assertTrue(q.pushLevelUp("electrolyzer", 2, 99, 0, t + LevelToastQueue.SOUND_COOLDOWN_MS + 10L));

        // Limit break banner takes top slot and never gets evicted by normal toasts
        q.pushLimitBreak(t + 1500L);
        q.pushLevelUp("quarry", 5, 1000, 0, t + 2600L);
        q.pushLevelUp("crafter", 6, 1000, 0, t + 3700L);
        List<LevelToastQueue.Entry> capped = q.activeEntries(t + 3700L);
        assertTrue(capped.size() <= LevelToastQueue.MAX_VISIBLE);
        assertTrue(capped.get(0).limitBreak(), "Limit break banner must remain prioritized at index 0");

        // Everything expires cleanly after duration
        assertTrue(q.activeEntries(t + 20_000L).isEmpty());

        // Phase 86: NEW_UNLOCK toast integrates cleanly and preserves priority over LEVEL_UP
        assertTrue(q.pushNewUnlock(1, t + 25_000L));
        List<LevelToastQueue.Entry> unlockEntries = q.activeEntries(t + 25_000L);
        assertEquals(1, unlockEntries.size());
        assertTrue(unlockEntries.get(0).newUnlock());
        assertEquals(1, unlockEntries.get(0).newLevel());
    }

    @Test
    void fogOfWarVisibilityHidesTwoStepsAheadAndTransitionsOnParentReveal() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        Set<String> starterRevealed = progress.snapshotRevealedNodes();

        // Tier 1 -> REVEALED, Tier 2 -> NEXT_TARGET, Tier 3..5 -> MYSTERY
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            NodeVisibility vis = SkillTreeLayout.visibilityOf(node, starterRevealed);
            if (node.tier() == 1) {
                assertEquals(NodeVisibility.REVEALED, vis, "Tier 1 must be REVEALED: " + node.machineId());
            } else if (node.tier() == 2) {
                assertEquals(NodeVisibility.NEXT_TARGET, vis, "Tier 2 must be NEXT_TARGET: " + node.machineId());
            } else {
                assertEquals(NodeVisibility.MYSTERY, vis, "Tier 3-5 must start MYSTERY: " + node.machineId());
            }
        }

        // Revealing electrolyzer uncovers precision_crusher & reconstructor from MYSTERY -> NEXT_TARGET
        Set<String> afterElectrolyzer = new HashSet<>(starterRevealed);
        afterElectrolyzer.add("electrolyzer");
        List<String> uncovered = SkillTreeLayout.newlyUncoveredChildren(
                "electrolyzer", starterRevealed, afterElectrolyzer);
        assertTrue(uncovered.contains("precision_crusher"));
        assertTrue(uncovered.contains("reconstructor"));
        assertEquals(NodeVisibility.REVEALED, SkillTreeLayout.visibilityOf("electrolyzer", afterElectrolyzer));
        assertEquals(NodeVisibility.NEXT_TARGET, SkillTreeLayout.visibilityOf("precision_crusher", afterElectrolyzer));
        assertEquals(NodeVisibility.NEXT_TARGET, SkillTreeLayout.visibilityOf("reconstructor", afterElectrolyzer));
        // Unrelated Tier 3+ nodes remain shrouded in fog
        assertEquals(NodeVisibility.MYSTERY, SkillTreeLayout.visibilityOf("assembler", afterElectrolyzer));
        assertEquals(NodeVisibility.MYSTERY, SkillTreeLayout.visibilityOf("ore_synthesizer", afterElectrolyzer));
    }

    @Test
    void revealAnimationWalksFourPhasesWithIntroHoldAndTierScalingAndSupportsSkip() {
        Set<String> initial = Set.of("crusher", "electric_furnace", "metal_press");
        long t0 = 1_000L;
        SkillTreeRevealAnim anim = new SkillTreeRevealAnim(
                initial, List.of("electrolyzer", "alloy_furnace"), t0);

        // 1. Starts in INTRO_HOLD with 700ms duration for the first node
        assertTrue(anim.isActive());
        assertEquals(SkillTreeRevealAnim.Phase.INTRO_HOLD, anim.phase());
        assertEquals("electrolyzer", anim.currentTargetId());
        assertEquals(SkillTreeRevealAnim.INTRO_FIRST_MS, anim.currentPhaseDuration());
        assertEquals(700L, anim.currentPhaseDuration());
        assertFalse(anim.effectiveRevealed().contains("electrolyzer"));

        // Still in INTRO_HOLD at 690ms
        anim.tick(t0 + 690L);
        assertEquals(SkillTreeRevealAnim.Phase.INTRO_HOLD, anim.phase());

        // 2. Advances past 700ms -> WIRE_PULSE (Tier 2: 450ms)
        long tWire = t0 + 700L;
        SkillTreeRevealAnim.StepEvent evWire = anim.tick(tWire);
        assertTrue(evWire.enteredWirePulse());
        assertEquals(2, evWire.targetTier());
        assertEquals(SkillTreeRevealAnim.Phase.WIRE_PULSE, anim.phase());
        assertEquals(450L, anim.currentPhaseDuration());
        assertFalse(anim.effectiveRevealed().contains("electrolyzer"));
        assertEquals(NodeVisibility.MYSTERY,
                SkillTreeLayout.visibilityOf("precision_crusher", anim.effectiveRevealed()));

        // 3. Advances past WIRE_PULSE -> UNLOCK_BURST & reveals electrolyzer
        long tBurst = tWire + 450L;
        SkillTreeRevealAnim.StepEvent evBurst = anim.tick(tBurst);
        assertTrue(evBurst.enteredUnlockBurst());
        assertEquals("electrolyzer", evBurst.targetMachineId());
        assertEquals(SkillTreeRevealAnim.Phase.UNLOCK_BURST, anim.phase());
        assertTrue(anim.effectiveRevealed().contains("electrolyzer"));
        assertTrue(anim.uncoveringChildren().contains("precision_crusher"));

        // 4. Advances past UNLOCK_BURST -> FOG_CLEAR
        long tFog = tBurst + 450L;
        SkillTreeRevealAnim.StepEvent evFog = anim.tick(tFog);
        assertTrue(evFog.enteredFogClear());
        assertEquals(SkillTreeRevealAnim.Phase.FOG_CLEAR, anim.phase());

        // 5. Advances past FOG_CLEAR -> chains to alloy_furnace INTRO_HOLD with 350ms duration!
        long tChain = tFog + 350L;
        SkillTreeRevealAnim.StepEvent evChain = anim.tick(tChain);
        assertFalse(evChain.finishedAll());
        assertEquals("alloy_furnace", anim.currentTargetId());
        assertEquals(SkillTreeRevealAnim.Phase.INTRO_HOLD, anim.phase());
        assertEquals(SkillTreeRevealAnim.INTRO_CHAIN_MS, anim.currentPhaseDuration());
        assertEquals(350L, anim.currentPhaseDuration());

        // Skip during INTRO_HOLD finishes everything cleanly
        List<String> skipped = anim.skipAll();
        assertEquals(List.of("electrolyzer", "alloy_furnace"), skipped);
        assertFalse(anim.isActive());
        assertTrue(anim.isCompleted());
        assertTrue(anim.effectiveRevealed().contains("alloy_furnace"));
    }

    @Test
    void tierStylesScaleMonotonicallyFromTier2ToTier5WithSafeFlashCap() {
        SkillTreeRevealAnim.TierStyle t2 = SkillTreeRevealAnim.styleForTier(2);
        SkillTreeRevealAnim.TierStyle t3 = SkillTreeRevealAnim.styleForTier(3);
        SkillTreeRevealAnim.TierStyle t4 = SkillTreeRevealAnim.styleForTier(4);
        SkillTreeRevealAnim.TierStyle t5 = SkillTreeRevealAnim.styleForTier(5);

        // Durations strictly increase across Tiers 2..5
        assertTrue(t2.wirePulseMs() < t3.wirePulseMs()
                && t3.wirePulseMs() < t4.wirePulseMs()
                && t4.wirePulseMs() < t5.wirePulseMs());
        assertTrue(t2.unlockBurstMs() < t3.unlockBurstMs()
                && t3.unlockBurstMs() < t4.unlockBurstMs()
                && t4.unlockBurstMs() < t5.unlockBurstMs());

        // Shockwave rings, star counts & spark particles grow monotonically
        assertTrue(t2.shockwaveRings() <= t3.shockwaveRings()
                && t3.shockwaveRings() <= t4.shockwaveRings()
                && t4.shockwaveRings() < t5.shockwaveRings());
        assertTrue(t2.starCount() < t3.starCount()
                && t3.starCount() < t4.starCount()
                && t4.starCount() < t5.starCount());
        assertTrue(t2.sparkParticles() < t3.sparkParticles()
                && t3.sparkParticles() < t4.sparkParticles()
                && t4.sparkParticles() < t5.sparkParticles());

        // Shake & rays activate for Tier 4+, full-screen flash only Tier 5 and strictly <= 0.22
        assertFalse(t2.radialRays());
        assertFalse(t3.radialRays());
        assertTrue(t4.radialRays());
        assertTrue(t5.radialRays());
        assertEquals(0.0D, t2.maxShakePx());
        assertEquals(0.0D, t3.maxShakePx());
        assertTrue(t4.maxShakePx() > 0.0D && t5.maxShakePx() > t4.maxShakePx());
        assertEquals(0.0D, t4.maxFlashAlpha());
        assertTrue(t5.maxFlashAlpha() > 0.0D && t5.maxFlashAlpha() <= 0.22D);
    }

    @Test
    void progressPollingFiresExactlyEvery20TicksAndAll17MachinesSupportBothUpgrades() {
        assertEquals(20, SkillTreeLayout.SYNC_POLL_INTERVAL_TICKS);
        assertFalse(SkillTreeLayout.shouldPollProgress(0));
        assertFalse(SkillTreeLayout.shouldPollProgress(1));
        assertFalse(SkillTreeLayout.shouldPollProgress(19));
        assertTrue(SkillTreeLayout.shouldPollProgress(20));
        assertFalse(SkillTreeLayout.shouldPollProgress(21));
        assertTrue(SkillTreeLayout.shouldPollProgress(40));
        assertTrue(SkillTreeLayout.shouldPollProgress(60));
        assertFalse(SkillTreeLayout.shouldPollProgress(-20));

        // Phase 87/97: all 38 industrial & utility machines equip both motor and battery slots
        assertEquals(38, MachineSkillTree.nodes().size());
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            assertTrue(node.hasMotorSlot(),
                    "Missing motor slot flag for " + node.machineId());
            assertTrue(node.hasBatterySlot(),
                    "Missing battery slot flag for " + node.machineId());
        }
    }

    private static void assertInside(Rect outer, Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                label + " exceeds outer bounds: " + inner + " not in " + outer);
    }
}
