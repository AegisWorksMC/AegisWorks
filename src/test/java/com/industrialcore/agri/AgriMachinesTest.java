package com.industrialcore.agri;

import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.machine.UpgradeMath;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 90 pure-Java unit tests for the Agriculture &amp; Forestry branch wave 1:
 * {@link AgriLayout} zero-overlap geometry (all 3 machines, Motor/Battery slots, no 6-face buttons),
 * {@link AgriLogic} math, {@link PlayerMachineProgress} XP/level compatibility, and shipped assets.
 */
class AgriMachinesTest {

    @Test
    void harvesterAndFisherLayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        // 1. Crop & Tree Harvester layout (3 vertical input slots + 3x3 output + Motor/Battery + 36 player)
        List<Rect> harvesterElems = commonElems();
        harvesterElems.add(new Rect(AgriLayout.INPUT_LABEL_X, AgriLayout.INPUT_LABEL_Y, 36, 8));
        harvesterElems.add(AgriLayout.INPUT_GROUP_3);
        assertNoOverlaps(harvesterElems, "harvester top-level");
        assertInsidePanel(harvesterElems);

        List<Rect> inputSlots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Rect s = AgriLayout.inputSlot3(i);
            assertInside(AgriLayout.INPUT_GROUP_3, s, "inputSlot " + i);
            inputSlots.add(s);
        }
        assertNoOverlaps(inputSlots, "input slots");

        // 2. Auto Fisher layout (1 centered fishing rod slot + 3x3 output + Motor/Battery + 36 player)
        List<Rect> fisherElems = commonElems();
        fisherElems.add(new Rect(AgriLayout.ROD_LABEL_X, AgriLayout.ROD_LABEL_Y, 36, 8));
        fisherElems.add(AgriLayout.ROD_GROUP);
        assertNoOverlaps(fisherElems, "auto_fisher top-level");
        assertInsidePanel(fisherElems);
        assertInside(AgriLayout.ROD_GROUP, AgriLayout.ROD_SLOT, "rodSlot");

        // 3. 3x3 output grid cells stay strictly inside OUTPUT_GROUP without overlap
        List<Rect> outSlots = new ArrayList<>();
        for (int i = 0; i < AgriLayout.OUTPUT_COUNT; i++) {
            Rect s = AgriLayout.outputSlot(i);
            assertInside(AgriLayout.OUTPUT_GROUP, s, "outputSlot " + i);
            outSlots.add(s);
        }
        assertNoOverlaps(outSlots, "3x3 output slots");
    }

    @Test
    void cropHarvesterRangeReplantAndUpgradeMathAreExact() {
        assertEquals(324, AgriLogic.cropScanVolume());
        assertTrue(AgriLogic.isInCropRange(0, 0, 0));
        assertTrue(AgriLogic.isInCropRange(-4, -1, 4));
        assertTrue(AgriLogic.isInCropRange(4, 2, -4));
        assertFalse(AgriLogic.isInCropRange(5, 0, 0));
        assertFalse(AgriLogic.isInCropRange(0, 3, 0));
        assertFalse(AgriLogic.isInCropRange(0, -2, 0));

        assertEquals(16, AgriLogic.cropScanInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(16, 64), AgriLogic.cropScanInterval(64, 1));
        assertTrue(AgriLogic.cropScanInterval(32, 1) < AgriLogic.cropScanInterval(0, 1));

        assertEquals(120, AgriLogic.cropHarvestEnergy(0, 1));
        assertEquals(UpgradeMath.discountedEnergy(120, 64), AgriLogic.cropHarvestEnergy(64, 1));
        assertTrue(AgriLogic.cropHarvestEnergy(64, 1) <= 78);

        assertTrue(AgriLogic.canReplant(true, false));
        assertTrue(AgriLogic.canReplant(false, true));
        assertFalse(AgriLogic.canReplant(false, false));
    }

    @Test
    void treeHarvesterPlantingGridSafetyBoundsAndGrowthPulsesAreExact() {
        int spots = 0;
        for (int dz = -AgriLogic.TREE_RANGE_RADIUS; dz <= AgriLogic.TREE_RANGE_RADIUS; dz++) {
            for (int dx = -AgriLogic.TREE_RANGE_RADIUS; dx <= AgriLogic.TREE_RANGE_RADIUS; dx++) {
                if (AgriLogic.isTreePlantingSpot(dx, dz)) {
                    spots++;
                }
            }
        }
        assertEquals(9, spots, "9x9 plot with 3-block spacing must yield exactly 9 planting spots");
        assertTrue(AgriLogic.isTreePlantingSpot(0, 0));
        assertTrue(AgriLogic.isTreePlantingSpot(-3, 3));
        assertFalse(AgriLogic.isTreePlantingSpot(1, 0));
        assertFalse(AgriLogic.isTreePlantingSpot(6, 0));

        assertTrue(AgriLogic.isWithinTreeFellBounds(0, 0, 0));
        assertTrue(AgriLogic.isWithinTreeFellBounds(7, 24, -7));
        assertFalse(AgriLogic.isWithinTreeFellBounds(8, 5, 0));
        assertFalse(AgriLogic.isWithinTreeFellBounds(0, 25, 0));
        assertFalse(AgriLogic.isWithinTreeFellBounds(0, -1, 0));
        assertEquals(256, AgriLogic.TREE_MAX_FELL_BLOCKS);

        assertEquals(0, AgriLogic.treeGrowthPulses(0));
        assertEquals(1, AgriLogic.treeGrowthPulses(1));
        assertEquals(4, AgriLogic.treeGrowthPulses(64));
        assertEquals(40, AgriLogic.treeCycleInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(40, 64), AgriLogic.treeCycleInterval(64, 1));
        assertEquals(UpgradeMath.discountedEnergy(400, 64), AgriLogic.treeFellEnergy(64, 1));
    }

    @Test
    void autoFisherMotorLureSpeedLuckAndBatterySavingWorkTogether() {
        assertEquals(160, AgriLogic.fisherDurationTicks(0, 0, 1));
        assertEquals(100, AgriLogic.fisherDurationTicks(0, 3, 1));
        assertEquals(UpgradeMath.acceleratedTime(160, 64), AgriLogic.fisherDurationTicks(64, 0, 1));
        assertEquals(UpgradeMath.acceleratedTime(100, 64), AgriLogic.fisherDurationTicks(64, 3, 1));
        assertTrue(AgriLogic.fisherDurationTicks(64, 3, 200) < AgriLogic.fisherDurationTicks(64, 3, 1));

        assertEquals(0.0F, AgriLogic.fisherEffectiveLuck(0, 1), 1e-4F);
        assertEquals(3.0F, AgriLogic.fisherEffectiveLuck(3, 1), 1e-4F);
        assertEquals(4.0F, AgriLogic.fisherEffectiveLuck(3, 100), 1e-4F);

        assertEquals(25, AgriLogic.fisherEnergyPerTick(0, 1));
        assertEquals(UpgradeMath.discountedEnergy(25, 64), AgriLogic.fisherEnergyPerTick(64, 1));
        assertEquals(16, AgriLogic.fisherEnergyPerTick(64, 1));
    }

    @Test
    void playerMachineProgressTracksNewAgriIdsWithoutBreakingSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must stay intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("crop_harvester", "tree_harvester", "auto_fisher")) {
            assertEquals(1, progress.getLevel(id));
            progress.addXp(id, 5_000L);
            assertTrue(progress.getLevel(id) > 1, id + " must level up cleanly");
            assertTrue(progress.getUpgradeLimit(id) > 1, id + " upgrade cap must expand with level");
        }
        // Snapshot round-trip preserves all 3 new machine XPs
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), progress.snapshotRevealedNodes());
        assertEquals(progress.getXp("crop_harvester"), restored.getXp("crop_harvester"));
        assertEquals(progress.getXp("tree_harvester"), restored.getXp("tree_harvester"));
        assertEquals(progress.getXp("auto_fisher"), restored.getXp("auto_fisher"));
    }

    @Test
    void allThreeAgriMachineAssetsRecipesAndLootTablesExist() {
        Path res = Path.of("src/main/resources");
        for (String id : List.of("crop_harvester", "tree_harvester", "auto_fisher")) {
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/blockstates/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/block/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/item/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_front.png")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_side.png")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_top.png")));
            assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/loot_table/blocks/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/recipe/" + id + ".json")));
        }
    }

    private static List<Rect> commonElems() {
        List<Rect> rects = new ArrayList<>();
        rects.add(AgriLayout.ENERGY_BAR);
        rects.add(new Rect(GuiMetrics.SIDE, AgriLayout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        rects.add(AgriLayout.PROGRESS_BAR);
        rects.add(new Rect(GuiMetrics.SIDE, AgriLayout.INFO_TEXT_Y, GuiMetrics.CONTENT, 8));
        rects.add(new Rect(AgriLayout.OUTPUT_LABEL_X, AgriLayout.OUTPUT_LABEL_Y, 54, 8));
        rects.add(AgriLayout.OUTPUT_GROUP);
        rects.add(new Rect(AgriLayout.UPGRADE_CENTER_X - 24, AgriLayout.MOTOR_LABEL_Y, 48, 8));
        rects.add(AgriLayout.MOTOR_SLOT);
        rects.add(new Rect(AgriLayout.UPGRADE_CENTER_X - 24, AgriLayout.BATTERY_LABEL_Y, 48, 8));
        rects.add(AgriLayout.BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            rects.add(AgriLayout.playerSlot(i));
        }
        return rects;
    }

    private static void assertNoOverlaps(List<Rect> rects, String where) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(rects.get(i).intersects(rects.get(j)),
                        where + ": " + rects.get(i) + " overlaps " + rects.get(j));
            }
        }
    }

    private static void assertInside(Rect outer, Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                label + ": " + inner + " escapes " + outer);
    }

    private static void assertInsidePanel(List<Rect> rects) {
        Rect panel = new Rect(0, 0, AgriLayout.WIDTH, AgriLayout.HEIGHT);
        for (Rect r : rects) {
            assertInside(panel, r, "panel");
        }
    }
}
