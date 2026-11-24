package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.level.MachineSkillTree.Node;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.level.SkillTreeFilterLogic;
import com.industrialcore.level.SkillTreeFilterLogic.DependencyRoutes;
import com.industrialcore.level.SkillTreeFilterLogic.DirectedEdge;
import com.industrialcore.level.SkillTreeFilterLogic.FilterMode;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.level.SkillTreeLayout.Rect;
import com.industrialcore.reactor.ReactorGuideLogic;
import com.industrialcore.reactor.ReactorGuideLogic.CellSample;
import com.industrialcore.reactor.ReactorGuideLogic.CellState;
import com.industrialcore.reactor.ReactorGuideLogic.GuideProgress;
import com.industrialcore.reactor.ReactorMultiblockLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.Facing2D;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideCell;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideRole;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.PartKind;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
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
 * Phase 108 pure-Java unit tests covering:
 * <ul>
 *   <li>Factory-Sim Multiblock Guide variable sizing (3..5 Nuclear, 5..7 Singularity),
 *       layer-by-layer slicing, misplaced-block detection, and missing-block aggregation</li>
 *   <li>MMO Skill Tree search, 4-mode state filtering, unlockable frontier detection,
 *       upstream/downstream dependency route extraction, and zero-overlap layout geometry</li>
 *   <li>Complete JP/EN translation symmetry for all new Phase 108 keys</li>
 * </ul>
 */
class Phase108UiUxTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void multiblockGuideSizingAndLayerSlicingPartitionEveryCellCleanly() {
        IntPos ctrl = new IntPos(10, 65, 20);

        // Nuclear default = 3x3x3 (matches legacy buildMinGuide3x3x3 cell-for-cell: 26 cells)
        assertEquals(3, ReactorGuideLogic.defaultGuideSize(ReactorType.NUCLEAR));
        assertEquals(5, ReactorGuideLogic.defaultGuideSize(ReactorType.SINGULARITY));
        assertEquals(4, ReactorGuideLogic.nextGuideSize(ReactorType.NUCLEAR, 3));
        assertEquals(5, ReactorGuideLogic.nextGuideSize(ReactorType.NUCLEAR, 4));
        assertEquals(3, ReactorGuideLogic.nextGuideSize(ReactorType.NUCLEAR, 5));
        assertEquals(6, ReactorGuideLogic.nextGuideSize(ReactorType.SINGULARITY, 5));
        assertEquals(7, ReactorGuideLogic.nextGuideSize(ReactorType.SINGULARITY, 6));
        assertEquals(5, ReactorGuideLogic.nextGuideSize(ReactorType.SINGULARITY, 7));

        List<GuideCell> minLegacy = ReactorMultiblockLogic.buildMinGuide3x3x3(ctrl, Facing2D.NORTH);
        List<GuideCell> guide3 = ReactorGuideLogic.buildGuide(ctrl, Facing2D.NORTH, 3);
        assertEquals(minLegacy.size(), guide3.size());
        assertEquals(new HashSet<>(minLegacy), new HashSet<>(guide3));

        // 5x5x5 = 124 cells (125 - controller), 7x7x7 = 342 cells (343 - controller)
        List<GuideCell> guide5 = ReactorGuideLogic.buildGuide(ctrl, Facing2D.NORTH, 5);
        List<GuideCell> guide7 = ReactorGuideLogic.buildGuide(ctrl, Facing2D.SOUTH, 7);
        assertEquals(124, guide5.size());
        assertEquals(342, guide7.size());

        // Layer slicing partitions 100% of cells across Layer 0..size-1 with zero duplicates or omissions
        int minY = ReactorGuideLogic.guideMinY(ctrl.y());
        assertEquals(guide5.size(), ReactorGuideLogic.filterByLayer(guide5, minY, ReactorGuideLogic.ALL_LAYERS).size());
        Set<GuideCell> union = new HashSet<>();
        for (int layer = 0; layer < ReactorGuideLogic.layerCount(5); layer++) {
            List<GuideCell> slice = ReactorGuideLogic.filterByLayer(guide5, minY, layer);
            assertFalse(slice.isEmpty(), "Layer " + layer + " must not be empty");
            for (GuideCell c : slice) {
                assertEquals(layer, ReactorGuideLogic.layerIndexOf(c.y(), minY));
                assertTrue(union.add(c), "Duplicate cell across layers: " + c);
            }
        }
        assertEquals(guide5.size(), union.size());

        // Layer cycling wraps cleanly for both G-key and Shift+Right-Click modes
        assertEquals(0, ReactorGuideLogic.nextLayerMode(ReactorGuideLogic.ALL_LAYERS, 3));
        assertEquals(1, ReactorGuideLogic.nextLayerMode(0, 3));
        assertEquals(2, ReactorGuideLogic.nextLayerMode(1, 3));
        assertEquals(ReactorGuideLogic.ALL_LAYERS, ReactorGuideLogic.nextLayerMode(2, 3));

        assertEquals(0, ReactorGuideLogic.nextShiftClickLayerOrOff(ReactorGuideLogic.ALL_LAYERS, 3));
        assertEquals(Integer.MIN_VALUE, ReactorGuideLogic.nextShiftClickLayerOrOff(2, 3));

        // HUD visibility range boundary (16 blocks)
        assertTrue(ReactorGuideLogic.isWithinHudRange(16.0 * 16.0));
        assertFalse(ReactorGuideLogic.isWithinHudRange(16.1 * 16.1));
    }

    @Test
    void multiblockGuideCountsMissingBlocksAndDetectsMisplacedBlocksAccurately() {
        IntPos ctrl = new IntPos(0, 64, 0);
        List<GuideCell> cells = ReactorGuideLogic.buildGuide(ctrl, Facing2D.NORTH, 3);

        // 1. All air -> 20 casing, 5 glass, 1 core missing, 0 misplaced, 0%
        GuideProgress empty = ReactorGuideLogic.evaluate(cells, (x, y, z) -> new CellSample(true, PartKind.AIR));
        assertEquals(26, empty.totalCells());
        assertEquals(0, empty.satisfiedCells());
        assertEquals(20, empty.missingCasing());
        assertEquals(5, empty.missingGlass());
        assertEquals(1, empty.missingCore());
        assertEquals(0, empty.misplacedCount());
        assertEquals(0, empty.completionPercent());
        assertFalse(empty.isComplete());

        // 2. Partial placement: satisfy 6 casings + 1 glass + 1 core, AND place 2 wrong non-air blocks (dirt)
        Map<IntPos, CellSample> world = new HashMap<>();
        int placedCasings = 0;
        int placedGlass = 0;
        int placedWrong = 0;
        for (GuideCell c : cells) {
            IntPos p = new IntPos(c.x(), c.y(), c.z());
            if (c.role() == GuideRole.INTERIOR_CORE) {
                world.put(p, new CellSample(false, PartKind.CORE_CELL));
            } else if (c.role() == GuideRole.FRAME_CASING && placedCasings < 6) {
                world.put(p, new CellSample(false, PartKind.CASING));
                placedCasings++;
            } else if (c.role() == GuideRole.WALL_GLASS && placedGlass < 1) {
                world.put(p, new CellSample(false, PartKind.PORT)); // Port is a valid wall replacement!
                placedGlass++;
            } else if (placedWrong < 2) {
                world.put(p, new CellSample(false, PartKind.OTHER)); // Misplaced non-air block!
                placedWrong++;
            } else {
                world.put(p, new CellSample(true, PartKind.AIR));
            }
        }
        assertEquals(CellState.MISPLACED, ReactorGuideLogic.classifyCell(
                GuideRole.FRAME_CASING, new CellSample(false, PartKind.OTHER)));
        assertEquals(CellState.MISSING, ReactorGuideLogic.classifyCell(
                GuideRole.FRAME_CASING, new CellSample(true, PartKind.AIR)));
        assertEquals(CellState.SATISFIED, ReactorGuideLogic.classifyCell(
                GuideRole.WALL_GLASS, new CellSample(false, PartKind.PORT)));

        GuideProgress partial = ReactorGuideLogic.evaluate(cells,
                (x, y, z) -> world.getOrDefault(new IntPos(x, y, z), new CellSample(true, PartKind.AIR)));
        assertEquals(8, partial.satisfiedCells());
        assertEquals(14, partial.missingCasing());
        assertEquals(4, partial.missingGlass());
        assertEquals(0, partial.missingCore());
        assertEquals(2, partial.misplacedCount());
        assertFalse(partial.isComplete());
        assertEquals(31, partial.completionPercent());

        // 3. All satisfied -> 100% complete, 0 missing, 0 misplaced
        GuideProgress complete = ReactorGuideLogic.evaluate(cells, (x, y, z) -> {
            for (GuideCell c : cells) {
                if (c.x() == x && c.y() == y && c.z() == z) {
                    return switch (c.role()) {
                        case FRAME_CASING -> new CellSample(false, PartKind.CASING);
                        case WALL_GLASS -> new CellSample(false, PartKind.GLASS);
                        case INTERIOR_CORE -> new CellSample(false, PartKind.CORE_CELL);
                    };
                }
            }
            return new CellSample(true, PartKind.AIR);
        });
        assertTrue(complete.isComplete());
        assertEquals(100, complete.completionPercent());
        assertEquals(0, complete.missingTotal());
        assertEquals(0, complete.misplacedCount());
    }

    @Test
    void skillTreeSearchFilterUnlockableFrontierAndRoutesWorkAccurately() {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        Node crusher = MachineSkillTree.byId("crusher");
        Node electrolyzer = MachineSkillTree.byId("electrolyzer");
        Node assembler = MachineSkillTree.byId("assembler");
        assertNotNull(crusher);
        assertNotNull(electrolyzer);
        assertNotNull(assembler);

        // Fresh player: Tier 1 roots are UNLOCKABLE frontier (unlocked && 0 XP); Tier 2+ are LOCKED
        assertTrue(SkillTreeFilterLogic.isUnlockableFrontier(crusher, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.matchesFilter(crusher, FilterMode.UNLOCKABLE, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.matchesFilter(crusher, FilterMode.UNLOCKED, progress::getLevel, progress::getXp));
        assertFalse(SkillTreeFilterLogic.matchesFilter(crusher, FilterMode.LOCKED, progress::getLevel, progress::getXp));

        assertFalse(SkillTreeFilterLogic.isUnlockableFrontier(electrolyzer, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.matchesFilter(electrolyzer, FilterMode.LOCKED, progress::getLevel, progress::getXp));

        // After leveling crusher to Lv.3 (with XP > 0): crusher leaves UNLOCKABLE frontier and electrolyzer ENTERS it!
        progress.setLevel("crusher", 3);
        assertFalse(SkillTreeFilterLogic.isUnlockableFrontier(crusher, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.matchesFilter(crusher, FilterMode.UNLOCKED, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.isUnlockableFrontier(electrolyzer, progress::getLevel, progress::getXp));
        assertTrue(SkillTreeFilterLogic.matchesFilter(electrolyzer, FilterMode.UNLOCKABLE, progress::getLevel, progress::getXp));

        // Keyword search: JP name, EN/ID, space-separated ID, category/branch name, and Stage tag
        assertTrue(SkillTreeFilterLogic.matchesQuery(crusher, "粉砕", "粗粉砕クラッシャー", "生産・加工"));
        assertTrue(SkillTreeFilterLogic.matchesQuery(crusher, "CRUSH", "粗粉砕クラッシャー", "生産・加工"));
        assertTrue(SkillTreeFilterLogic.matchesQuery(crusher, "生産", "粗粉砕クラッシャー", "生産・加工"));
        assertTrue(SkillTreeFilterLogic.matchesQuery(crusher, "stage 1", "粗粉砕クラッシャー", "生産・加工"));
        assertFalse(SkillTreeFilterLogic.matchesQuery(crusher, "レーザー", "粗粉砕クラッシャー", "生産・加工"));

        // Match count by branch
        Map<Branch, Integer> pumpCounts = SkillTreeFilterLogic.countMatchesByBranch(
                "fluid_pump", FilterMode.ALL, progress::getLevel, progress::getXp,
                Node::machineId, Branch::id);
        assertEquals(1, pumpCounts.get(Branch.LIFE));
        assertEquals(0, pumpCounts.get(Branch.PRODUCTION));
        assertEquals(0, pumpCounts.get(Branch.NATURE));
        assertEquals(0, pumpCounts.get(Branch.TRAP));

        // Upstream & Downstream DAG route extraction for "assembler"
        DependencyRoutes routes = SkillTreeFilterLogic.extractRoutes("assembler");
        assertEquals("assembler", routes.focusId());
        assertTrue(routes.upstreamNodes().contains("alloy_furnace"));
        assertTrue(routes.upstreamNodes().contains("metal_press"));
        assertTrue(routes.isUpstreamEdge("alloy_furnace", "assembler"));
        assertTrue(routes.isUpstreamEdge("metal_press", "alloy_furnace"));

        assertTrue(routes.downstreamNodes().contains("fast_crusher"));
        assertTrue(routes.downstreamNodes().contains("quantum_importer"));
        assertTrue(routes.downstreamNodes().contains("quantum_exporter"));
        assertTrue(routes.downstreamNodes().contains("ore_synthesizer"));
        assertTrue(routes.isDownstreamEdge("assembler", "fast_crusher"));
        assertTrue(routes.isDownstreamEdge("assembler", "quantum_importer"));
        assertFalse(routes.isUpstreamEdge("assembler", "fast_crusher"));
        assertEquals(DependencyRoutes.EMPTY, SkillTreeFilterLogic.extractRoutes("nonexistent_machine"));
    }

    @Test
    void skillTreeSearchAndFilterRectsHaveZeroOverlapAndAccurateHitTesting() {
        Rect panel = new Rect(0, 0, SkillTreeLayout.WIDTH, SkillTreeLayout.HEIGHT);
        assertInside(panel, SkillTreeLayout.SEARCH_BOX, "SEARCH_BOX");
        assertInside(SkillTreeLayout.SEARCH_BOX, SkillTreeLayout.SEARCH_CLEAR_BUTTON, "SEARCH_CLEAR_BUTTON");

        assertFalse(SkillTreeLayout.SEARCH_BOX.overlaps(SkillTreeLayout.BRANCH_HEADER_BAR));
        assertFalse(SkillTreeLayout.SEARCH_BOX.overlaps(SkillTreeLayout.TREE_VIEWPORT));
        assertFalse(SkillTreeLayout.SEARCH_BOX.overlaps(SkillTreeLayout.DETAIL_PANEL));
        for (Rect bt : SkillTreeLayout.BRANCH_TAB_RECTS) {
            assertFalse(SkillTreeLayout.SEARCH_BOX.overlaps(bt));
        }

        List<Rect> filters = SkillTreeLayout.FILTER_RECTS;
        assertEquals(4, filters.size());
        FilterMode[] modes = FilterMode.values();
        for (int i = 0; i < filters.size(); i++) {
            Rect fr = filters.get(i);
            assertInside(panel, fr, "FILTER_" + i);
            assertFalse(fr.overlaps(SkillTreeLayout.SEARCH_BOX));
            assertFalse(fr.overlaps(SkillTreeLayout.BRANCH_HEADER_BAR));
            assertFalse(fr.overlaps(SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON));
            assertFalse(fr.overlaps(SkillTreeLayout.TREE_VIEWPORT));
            assertFalse(fr.overlaps(SkillTreeLayout.DETAIL_PANEL));
            for (int j = i + 1; j < filters.size(); j++) {
                assertFalse(fr.overlaps(filters.get(j)), "Filter buttons overlap: " + i + " & " + j);
            }
            assertEquals(modes[i], SkillTreeLayout.filterAt(fr.centerX(), fr.centerY()));
        }
        assertNull(SkillTreeLayout.filterAt(0, 0));
    }

    @Test
    void allPhase108TranslationKeysExistInBothJapaneseAndEnglish() throws IOException {
        JsonObject ja = JsonParser.parseString(Files.readString(LANG_DIR.resolve("ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(LANG_DIR.resolve("en_us.json"))).getAsJsonObject();

        List<String> requiredKeys = List.of(
                "key.industrialcore.reactor_guide_layer",
                "gui.industrialcore.reactor.guide_tooltip_controls",
                "message.industrialcore.reactor.guide_on_v2",
                "message.industrialcore.reactor.guide_layer_all",
                "message.industrialcore.reactor.guide_layer_single",
                "message.industrialcore.reactor.guide_size",
                "message.industrialcore.reactor.guide_complete",
                "gui.industrialcore.reactor.hud_title",
                "gui.industrialcore.reactor.hud_layer_all",
                "gui.industrialcore.reactor.hud_layer_single",
                "gui.industrialcore.reactor.hud_remain",
                "gui.industrialcore.reactor.hud_remain_layer",
                "gui.industrialcore.reactor.hud_done",
                "gui.industrialcore.reactor.hud_misplaced",
                "gui.industrialcore.reactor.hud_controls",
                "gui.industrialcore.skill_tree.search_placeholder",
                "gui.industrialcore.skill_tree.filter.all",
                "gui.industrialcore.skill_tree.filter.unlockable",
                "gui.industrialcore.skill_tree.filter.unlocked",
                "gui.industrialcore.skill_tree.filter.locked",
                "gui.industrialcore.skill_tree.state_unlockable",
                "gui.industrialcore.skill_tree.stage_badge",
                "gui.industrialcore.skill_tree.role_header",
                "gui.industrialcore.skill_tree.ingredients_header",
                "gui.industrialcore.skill_tree.ingredients_none"
        );
        for (String key : requiredKeys) {
            assertTrue(ja.has(key) && !ja.get(key).getAsString().isBlank(), "Missing ja_jp key: " + key);
            assertTrue(en.has(key) && !en.get(key).getAsString().isBlank(), "Missing en_us key: " + key);
        }
    }

    private static void assertInside(Rect outer, Rect inner, String name) {
        assertTrue(inner.x() >= outer.x() && inner.right() <= outer.right()
                        && inner.y() >= outer.y() && inner.bottom() <= outer.bottom(),
                name + " (" + inner + ") must fit inside " + outer);
    }
}
