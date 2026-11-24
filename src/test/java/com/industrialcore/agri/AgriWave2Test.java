package com.industrialcore.agri;

import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 91 pure-Java unit tests for the Agriculture &amp; Forestry branch wave 2:
 * {@link AgriWave2Layout} zero-overlap geometry, {@link AgriWave2Logic} rules,
 * {@link PlayerMachineProgress} XP/level compatibility, and shipped assets.
 */
class AgriWave2Test {

    @Test
    void allThreeWave2LayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        Rect headerBtn = new Rect(
                SkillTreeLayout.MACHINE_HEADER_BUTTON.x(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.y(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.width(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.height());

        // 1. Ranch Harvester layout
        List<Rect> ranchElems = new ArrayList<>();
        ranchElems.add(headerBtn);
        ranchElems.add(AgriWave2Layout.ENERGY_BAR);
        ranchElems.add(new Rect(GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        ranchElems.add(AgriWave2Layout.PROGRESS_BAR);
        ranchElems.add(new Rect(GuiMetrics.SIDE, AgriWave2Layout.INFO_TEXT_Y, GuiMetrics.CONTENT, 8));
        ranchElems.add(new Rect(AgriWave2Layout.RANCH_FEED_LABEL_X, AgriWave2Layout.RANCH_FEED_LABEL_Y, 44, 8));
        ranchElems.add(AgriWave2Layout.RANCH_FEED_GROUP);
        ranchElems.add(new Rect(AgriWave2Layout.RANCH_OUTPUT_LABEL_X, AgriWave2Layout.RANCH_OUTPUT_LABEL_Y, 54, 8));
        ranchElems.add(AgriWave2Layout.RANCH_OUTPUT_GROUP);
        ranchElems.add(new Rect(AgriWave2Layout.RANCH_UPGRADE_CENTER_X - 24, AgriWave2Layout.RANCH_MOTOR_LABEL_Y, 48, 8));
        ranchElems.add(AgriWave2Layout.RANCH_MOTOR_SLOT);
        ranchElems.add(new Rect(AgriWave2Layout.RANCH_UPGRADE_CENTER_X - 24, AgriWave2Layout.RANCH_BATTERY_LABEL_Y, 48, 8));
        ranchElems.add(AgriWave2Layout.RANCH_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            ranchElems.add(AgriWave2Layout.ranchPlayerSlot(i));
        }
        assertNoOverlaps(ranchElems, "ranch_harvester");
        assertInsidePanel(ranchElems, AgriWave2Layout.WIDTH, AgriWave2Layout.RANCH_HEIGHT);

        List<Rect> ranchFeedSlots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Rect s = AgriWave2Layout.ranchFeedSlot(i);
            assertInside(AgriWave2Layout.RANCH_FEED_GROUP, s, "ranchFeed " + i);
            ranchFeedSlots.add(s);
        }
        assertNoOverlaps(ranchFeedSlots, "ranchFeed");

        List<Rect> ranchOutSlots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Rect s = AgriWave2Layout.ranchOutputSlot(i);
            assertInside(AgriWave2Layout.RANCH_OUTPUT_GROUP, s, "ranchOutput " + i);
            ranchOutSlots.add(s);
        }
        assertNoOverlaps(ranchOutSlots, "ranchOutput");

        // 2. Growth Accelerator layout (no item slots except Motor & Battery + telemetry card)
        List<Rect> growthElems = new ArrayList<>();
        growthElems.add(headerBtn);
        growthElems.add(AgriWave2Layout.ENERGY_BAR);
        growthElems.add(new Rect(GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        growthElems.add(AgriWave2Layout.PROGRESS_BAR);
        growthElems.add(AgriWave2Layout.GROWTH_STATS_BOX);
        growthElems.add(new Rect(AgriWave2Layout.GROWTH_UPGRADE_CENTER_X - 24, AgriWave2Layout.GROWTH_MOTOR_LABEL_Y, 48, 8));
        growthElems.add(AgriWave2Layout.GROWTH_MOTOR_SLOT);
        growthElems.add(new Rect(AgriWave2Layout.GROWTH_UPGRADE_CENTER_X - 24, AgriWave2Layout.GROWTH_BATTERY_LABEL_Y, 48, 8));
        growthElems.add(AgriWave2Layout.GROWTH_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            growthElems.add(AgriWave2Layout.growthPlayerSlot(i));
        }
        assertNoOverlaps(growthElems, "growth_accelerator");
        assertInsidePanel(growthElems, AgriWave2Layout.WIDTH, AgriWave2Layout.GROWTH_HEIGHT);

        // Verify all 4 stat rows fit cleanly inside GROWTH_STATS_BOX without colliding with each other
        int[] statYs = {
                AgriWave2Layout.GROWTH_ROW_1_Y, AgriWave2Layout.GROWTH_ROW_2_Y,
                AgriWave2Layout.GROWTH_ROW_3_Y, AgriWave2Layout.GROWTH_ROW_4_Y
        };
        List<Rect> statRows = new ArrayList<>();
        for (int sy : statYs) {
            Rect r = new Rect(AgriWave2Layout.GROWTH_STATS_TEXT_X, sy, 102, 8);
            assertInside(AgriWave2Layout.GROWTH_STATS_BOX, r, "growth stat row y=" + sy);
            statRows.add(r);
        }
        assertNoOverlaps(statRows, "growth stat rows");

        // 3. Bio Feeder Beacon layout
        List<Rect> feederElems = new ArrayList<>();
        feederElems.add(headerBtn);
        feederElems.add(AgriWave2Layout.ENERGY_BAR);
        feederElems.add(new Rect(GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        feederElems.add(AgriWave2Layout.PROGRESS_BAR);
        feederElems.add(new Rect(GuiMetrics.SIDE, AgriWave2Layout.INFO_TEXT_Y, GuiMetrics.CONTENT, 8));
        feederElems.add(new Rect(AgriWave2Layout.FEEDER_FOOD_LABEL_X, AgriWave2Layout.FEEDER_FOOD_LABEL_Y, 64, 8));
        feederElems.add(AgriWave2Layout.FEEDER_FOOD_GROUP);
        feederElems.add(new Rect(AgriWave2Layout.FEEDER_UPGRADE_CENTER_X - 24, AgriWave2Layout.FEEDER_MOTOR_LABEL_Y, 48, 8));
        feederElems.add(AgriWave2Layout.FEEDER_MOTOR_SLOT);
        feederElems.add(new Rect(AgriWave2Layout.FEEDER_UPGRADE_CENTER_X - 24, AgriWave2Layout.FEEDER_BATTERY_LABEL_Y, 48, 8));
        feederElems.add(AgriWave2Layout.FEEDER_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            feederElems.add(AgriWave2Layout.feederPlayerSlot(i));
        }
        assertNoOverlaps(feederElems, "bio_feeder");
        assertInsidePanel(feederElems, AgriWave2Layout.WIDTH, AgriWave2Layout.FEEDER_HEIGHT);

        List<Rect> feederSlots = new ArrayList<>();
        for (int i = 0; i < AgriWave2Layout.FEEDER_FOOD_COUNT; i++) {
            Rect s = AgriWave2Layout.feederFoodSlot(i);
            assertInside(AgriWave2Layout.FEEDER_FOOD_GROUP, s, "feederFood " + i);
            feederSlots.add(s);
        }
        assertNoOverlaps(feederSlots, "feederFood");
    }

    @Test
    void ranchHarvesterRangeBreedCapAndUpgradesAreExact() {
        assertTrue(AgriWave2Logic.isInRanchRange(0, 0, 0));
        assertTrue(AgriWave2Logic.isInRanchRange(-4, -2, 4));
        assertTrue(AgriWave2Logic.isInRanchRange(4, 4, -4));
        assertFalse(AgriWave2Logic.isInRanchRange(5, 0, 0));
        assertFalse(AgriWave2Logic.isInRanchRange(0, -3, 0));
        assertFalse(AgriWave2Logic.isInRanchRange(0, 5, 0));

        assertEquals(12, AgriWave2Logic.RANCH_BREED_CAP);
        assertTrue(AgriWave2Logic.canBreedSpecies(0));
        assertTrue(AgriWave2Logic.canBreedSpecies(11));
        assertFalse(AgriWave2Logic.canBreedSpecies(12));
        assertFalse(AgriWave2Logic.canBreedSpecies(20));

        assertEquals(32, AgriWave2Logic.ranchCycleInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(32, 64), AgriWave2Logic.ranchCycleInterval(64, 1));
        assertTrue(AgriWave2Logic.ranchCycleInterval(32, 1) < AgriWave2Logic.ranchCycleInterval(0, 1));

        assertEquals(220, AgriWave2Logic.ranchWorkEnergy(0, 1));
        assertEquals(UpgradeMath.discountedEnergy(220, 64), AgriWave2Logic.ranchWorkEnergy(64, 1));
        assertEquals(136, AgriWave2Logic.ranchWorkEnergy(64, 1));
    }

    @Test
    void growthAcceleratorScalingParticleCapAndUpgradesAreExact() {
        assertTrue(AgriWave2Logic.isInGrowthRange(0, 0, 0));
        assertTrue(AgriWave2Logic.isInGrowthRange(-4, -1, 4));
        assertTrue(AgriWave2Logic.isInGrowthRange(4, 3, -4));
        assertFalse(AgriWave2Logic.isInGrowthRange(5, 0, 0));
        assertFalse(AgriWave2Logic.isInGrowthRange(0, -2, 0));
        assertFalse(AgriWave2Logic.isInGrowthRange(0, 4, 0));

        assertEquals(40, AgriWave2Logic.growthPulseInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(40, 64), AgriWave2Logic.growthPulseInterval(64, 1));

        assertEquals(3, AgriWave2Logic.growthPulseTargets(0));
        assertEquals(9, AgriWave2Logic.growthPulseTargets(32));
        assertEquals(16, AgriWave2Logic.growthPulseTargets(64));

        assertEquals(0, AgriWave2Logic.growthParticleBudget(0));
        assertEquals(4, AgriWave2Logic.growthParticleBudget(4));
        assertEquals(6, AgriWave2Logic.growthParticleBudget(16));

        assertEquals(350, AgriWave2Logic.growthPulseEnergy(0, 1));
        assertEquals(UpgradeMath.discountedEnergy(350, 64), AgriWave2Logic.growthPulseEnergy(64, 1));
    }

    @Test
    void bioFeederRadiusNutritionSafetyAndUpgradesWorkTogether() {
        assertEquals(48, AgriWave2Logic.feederRadius(0));
        assertEquals(72, AgriWave2Logic.feederRadius(32));
        assertEquals(96, AgriWave2Logic.feederRadius(64));

        assertTrue(AgriWave2Logic.isInFeederRange(48 * 48, 0));
        assertFalse(AgriWave2Logic.isInFeederRange(49 * 49, 0));
        assertTrue(AgriWave2Logic.isInFeederRange(96 * 96, 64));
        assertFalse(AgriWave2Logic.isInFeederRange(97 * 97, 64));

        assertEquals(20, AgriWave2Logic.feederCheckInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(20, 64), AgriWave2Logic.feederCheckInterval(64, 1));
        assertEquals(UpgradeMath.discountedEnergy(500, 64), AgriWave2Logic.feederFeedEnergy(64, 1));

        assertTrue(AgriWave2Logic.needsFeeding(19, 15.0F));
        assertTrue(AgriWave2Logic.needsFeeding(20, 9.9F));
        assertFalse(AgriWave2Logic.needsFeeding(20, 10.0F));

        // Nutrition clamps food at 20 and saturation at resulting food level
        AgriWave2Logic.FoodResult r1 = AgriWave2Logic.applyNutrition(8, 1.0F, 8, 12.8F);
        assertEquals(16, r1.foodLevel());
        assertEquals(13.8F, r1.saturationLevel(), 1e-4F);

        AgriWave2Logic.FoodResult r2 = AgriWave2Logic.applyNutrition(18, 15.0F, 8, 12.8F);
        assertEquals(20, r2.foodLevel());
        assertEquals(20.0F, r2.saturationLevel(), 1e-4F);

        assertTrue(AgriWave2Logic.isSafeFood(true, false));
        assertFalse(AgriWave2Logic.isSafeFood(true, true));
        assertFalse(AgriWave2Logic.isSafeFood(false, false));
    }

    @Test
    void playerMachineProgressTracksWave2IdsWithoutBreakingSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must stay intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("ranch_harvester", "growth_accelerator", "bio_feeder")) {
            assertEquals(1, progress.getLevel(id));
            progress.addXp(id, 5_000L);
            assertTrue(progress.getLevel(id) > 1, id + " must level up cleanly");
            assertTrue(progress.getUpgradeLimit(id) > 1, id + " upgrade cap must expand with level");
        }
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), progress.snapshotRevealedNodes());
        assertEquals(progress.getXp("ranch_harvester"), restored.getXp("ranch_harvester"));
        assertEquals(progress.getXp("growth_accelerator"), restored.getXp("growth_accelerator"));
        assertEquals(progress.getXp("bio_feeder"), restored.getXp("bio_feeder"));
    }

    @Test
    void allThreeWave2AssetsRecipesAndLootTablesExist() {
        Path res = Path.of("src/main/resources");
        for (String id : List.of("ranch_harvester", "growth_accelerator", "bio_feeder")) {
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

    private static void assertInsidePanel(List<Rect> rects, int width, int height) {
        Rect panel = new Rect(0, 0, width, height);
        for (Rect r : rects) {
            assertInside(panel, r, "panel");
        }
    }
}
