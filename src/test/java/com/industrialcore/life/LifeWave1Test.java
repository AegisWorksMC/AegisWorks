package com.industrialcore.life;

import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.life.LifeLogic.BuildMode;
import com.industrialcore.life.LifeLogic.LocalOffset;
import com.industrialcore.life.LifeLogic.WorldDelta;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for Phase 94 Branch D (Building &amp; Life) wave 1:
 * {@code auto_builder}, {@code fluid_pump}, and {@code wireless_charger}.
 */
class LifeWave1Test {
    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right()
                && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static void assertInsideAndNonOverlapping(String name, int width, int height,
                                                      List<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            Rect a = rects.get(i);
            assertTrue(a.x() >= 0 && a.y() >= 0 && a.right() <= width && a.bottom() <= height,
                    name + " rect #" + i + " out of bounds: " + a);
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(overlaps(a, rects.get(j)),
                        name + " overlap between #" + i + " " + a + " and #" + j + " " + rects.get(j));
            }
        }
    }

    @Test
    void allThreeLifeLayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        // 1. Auto Builder
        List<Rect> ab = new ArrayList<>();
        ab.add(LifeLayout.ENERGY_BAR);
        ab.add(LifeLayout.BUILDER_PROGRESS_BAR);
        ab.add(LifeLayout.BUILDER_BTN_TOGGLE);
        ab.add(LifeLayout.BUILDER_BTN_MODE);
        ab.add(LifeLayout.BUILDER_BTN_RESET);
        ab.add(LifeLayout.BUILDER_W_DEC);
        ab.add(LifeLayout.BUILDER_W_INC);
        ab.add(LifeLayout.BUILDER_D_DEC);
        ab.add(LifeLayout.BUILDER_D_INC);
        ab.add(LifeLayout.BUILDER_H_DEC);
        ab.add(LifeLayout.BUILDER_H_INC);
        ab.add(LifeLayout.BUILDER_MAT_GROUP);
        ab.add(LifeLayout.BUILDER_STATS_BOX);
        ab.add(LifeLayout.BUILDER_MOTOR_SLOT);
        ab.add(LifeLayout.BUILDER_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            ab.add(LifeLayout.builderPlayerSlot(i));
        }
        assertInsideAndNonOverlapping("auto_builder", LifeLayout.WIDTH, LifeLayout.BUILDER_HEIGHT, ab);
        // 3x3 material slots sit cleanly inside BUILDER_MAT_GROUP without overlapping each other
        List<Rect> matSlots = new ArrayList<>();
        for (int i = 0; i < LifeLayout.BUILDER_MAT_COUNT; i++) {
            Rect s = LifeLayout.builderMaterialSlot(i);
            assertTrue(s.x() >= LifeLayout.BUILDER_MAT_GROUP.x()
                    && s.right() <= LifeLayout.BUILDER_MAT_GROUP.right()
                    && s.y() >= LifeLayout.BUILDER_MAT_GROUP.y()
                    && s.bottom() <= LifeLayout.BUILDER_MAT_GROUP.bottom(),
                    "material slot #" + i + " outside group");
            matSlots.add(s);
        }
        assertInsideAndNonOverlapping("auto_builder_mats", LifeLayout.WIDTH, LifeLayout.BUILDER_HEIGHT, matSlots);

        // 2. Fluid Pump
        List<Rect> fp = new ArrayList<>();
        fp.add(LifeLayout.ENERGY_BAR);
        fp.add(LifeLayout.PUMP_TANK_BAR);
        fp.add(LifeLayout.PUMP_BUCKET_IN_GROUP);
        fp.add(LifeLayout.PUMP_BUCKET_OUT_GROUP);
        fp.add(LifeLayout.PUMP_MOTOR_SLOT);
        fp.add(LifeLayout.PUMP_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            fp.add(LifeLayout.pumpPlayerSlot(i));
        }
        assertInsideAndNonOverlapping("fluid_pump", LifeLayout.WIDTH, LifeLayout.PUMP_HEIGHT, fp);
        assertTrue(LifeLayout.PUMP_BUCKET_IN_SLOT.x() >= LifeLayout.PUMP_BUCKET_IN_GROUP.x()
                && LifeLayout.PUMP_BUCKET_IN_SLOT.right() <= LifeLayout.PUMP_BUCKET_IN_GROUP.right());
        assertTrue(LifeLayout.PUMP_BUCKET_OUT_SLOT.x() >= LifeLayout.PUMP_BUCKET_OUT_GROUP.x()
                && LifeLayout.PUMP_BUCKET_OUT_SLOT.right() <= LifeLayout.PUMP_BUCKET_OUT_GROUP.right());

        // 3. Wireless Charger
        List<Rect> wc = new ArrayList<>();
        wc.add(LifeLayout.ENERGY_BAR);
        wc.add(LifeLayout.CHARGER_STATS_BOX);
        wc.add(LifeLayout.CHARGER_MOTOR_SLOT);
        wc.add(LifeLayout.CHARGER_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            wc.add(LifeLayout.chargerPlayerSlot(i));
        }
        assertInsideAndNonOverlapping("wireless_charger", LifeLayout.WIDTH, LifeLayout.CHARGER_HEIGHT, wc);

        // Verify all body elements sit cleanly below GuiMetrics.BODY_TOP (header + skill tree button clear)
        assertTrue(LifeLayout.ENERGY_BAR.y() >= GuiMetrics.BODY_TOP);
    }

    @Test
    void autoBuilderPatternsClampingFacingAndUpgradeScalingAreExact() {
        assertEquals(3, LifeLogic.clampWidth(0));
        assertEquals(16, LifeLogic.clampWidth(99));
        assertEquals(3, LifeLogic.clampDepth(-5));
        assertEquals(16, LifeLogic.clampDepth(25));
        assertEquals(1, LifeLogic.clampHeight(0));
        assertEquals(8, LifeLogic.clampHeight(20));

        // FLOOR 5x4 -> 20 unique coords, all up == 0 and forward in [1, 4]
        List<LocalOffset> floor = LifeLogic.patternOffsets(BuildMode.FLOOR, 5, 4, 3);
        assertEquals(20, floor.size());
        assertEquals(20, LifeLogic.patternCount(BuildMode.FLOOR, 5, 4, 3));
        assertEquals(20, new HashSet<>(floor).size());
        for (LocalOffset o : floor) {
            assertEquals(0, o.up());
            assertTrue(o.forward() >= 1 && o.forward() <= 4);
        }

        // WALL 6x4 -> 24 unique coords, all forward == 1 and up in [0, 3]
        List<LocalOffset> wall = LifeLogic.patternOffsets(BuildMode.WALL, 6, 5, 4);
        assertEquals(24, wall.size());
        assertEquals(24, LifeLogic.patternCount(BuildMode.WALL, 6, 5, 4));
        assertEquals(24, new HashSet<>(wall).size());
        for (LocalOffset o : wall) {
            assertEquals(1, o.forward());
            assertTrue(o.up() >= 0 && o.up() <= 3);
        }

        // HOLLOW_BOX 5x5x4 -> outer shell = 100 - 3*3*2 = 82, center interior excluded!
        List<LocalOffset> box = LifeLogic.patternOffsets(BuildMode.HOLLOW_BOX, 5, 5, 4);
        assertEquals(82, box.size());
        assertEquals(82, LifeLogic.patternCount(BuildMode.HOLLOW_BOX, 5, 5, 4));
        assertEquals(82, new HashSet<>(box).size());
        assertFalse(box.contains(new LocalOffset(0, 1, 3)), "Interior of HOLLOW_BOX must remain empty");
        assertTrue(box.contains(new LocalOffset(0, 0, 3)), "Floor of HOLLOW_BOX must be filled");
        assertTrue(box.contains(new LocalOffset(0, 3, 3)), "Roof of HOLLOW_BOX must be filled");

        // World facing projection: facing North (0, -1) -> forward=2, right=+1 -> dx=+1, dz=-2
        WorldDelta north = LifeLogic.toWorldDelta(0, -1, new LocalOffset(1, 2, 3));
        assertEquals(new WorldDelta(1, 2, -3), north);
        // Facing East (+1, 0) -> forward=3, right=+1 (South) -> dx=+3, dz=+1
        WorldDelta east = LifeLogic.toWorldDelta(1, 0, new LocalOffset(1, 0, 3));
        assertEquals(new WorldDelta(3, 0, 1), east);

        // Motor speed & batch scaling + battery 35% discount
        assertEquals(10, LifeLogic.builderInterval(0, 0));
        assertEquals(1, LifeLogic.builderInterval(64, 0));
        assertEquals(1, LifeLogic.builderBatchSize(0));
        assertEquals(2, LifeLogic.builderBatchSize(16));
        assertEquals(3, LifeLogic.builderBatchSize(32));
        assertEquals(4, LifeLogic.builderBatchSize(64));
        int baseE = LifeLogic.builderEnergyPerBlock(0, 0);
        int discE = LifeLogic.builderEnergyPerBlock(64, 0);
        assertEquals(60, baseE);
        assertTrue(discE < baseE && discE >= 38 && discE <= 40, "Expected ~35% battery discount: " + discE);
    }

    @Test
    void fluidPumpRadiusScanCylinderTankCapacityAndInfiniteWaterAreExact() {
        assertEquals(8, LifeLogic.pumpRadius(0));
        assertEquals(12, LifeLogic.pumpRadius(32));
        assertEquals(16, LifeLogic.pumpRadius(64));
        assertEquals(16, LifeLogic.pumpRadius(200));

        assertEquals(20, LifeLogic.pumpInterval(0, 0));
        assertTrue(LifeLogic.pumpInterval(64, 0) < LifeLogic.pumpInterval(0, 0));
        assertTrue(LifeLogic.pumpInterval(64, 99) >= 2);

        // Scan starts directly at top layer dy = -1 and reaches down to dy = -32
        int vol = LifeLogic.pumpScanVolume(8, 32);
        assertEquals(17 * 17 * 32, vol);
        WorldDelta first = LifeLogic.pumpDeltaForIndex(0, 8, 32);
        assertEquals(-1, first.dy());
        WorldDelta last = LifeLogic.pumpDeltaForIndex(vol - 1, 8, 32);
        assertEquals(-32, last.dy());
        assertTrue(LifeLogic.isWithinPumpRadius(0, 0, 8));
        assertTrue(LifeLogic.isWithinPumpRadius(8, 0, 8));
        assertFalse(LifeLogic.isWithinPumpRadius(8, 1, 8));

        // 64,000 mB tank room check & infinite water check
        assertTrue(LifeLogic.canAcceptBucket(0));
        assertTrue(LifeLogic.canAcceptBucket(63_000));
        assertFalse(LifeLogic.canAcceptBucket(63_001));
        assertFalse(LifeLogic.canAcceptBucket(64_000));

        assertTrue(LifeLogic.isInfiniteWaterSource(true, 2));
        assertTrue(LifeLogic.isInfiniteWaterSource(true, 4));
        assertFalse(LifeLogic.isInfiniteWaterSource(true, 1));
        assertFalse(LifeLogic.isInfiniteWaterSource(false, 4));
    }

    @Test
    void wirelessChargerRadiusRateAndEnergyConservationAreExact() {
        assertEquals(32, LifeLogic.chargerRadius(0));
        assertEquals(48, LifeLogic.chargerRadius(32));
        assertEquals(64, LifeLogic.chargerRadius(64));
        assertTrue(LifeLogic.isInChargerRange(32.0 * 32.0, 0));
        assertFalse(LifeLogic.isInChargerRange(33.0 * 33.0, 0));
        assertTrue(LifeLogic.isInChargerRange(64.0 * 64.0, 64));

        assertEquals(2_000, LifeLogic.chargerRatePerTick(0));
        assertEquals(17_000, LifeLogic.chargerRatePerTick(32));
        assertEquals(32_000, LifeLogic.chargerRatePerTick(64));
        assertTrue(LifeLogic.chargerFieldCost(64, 0) < LifeLogic.chargerFieldCost(0, 0));

        // Strict conservation across multiple items (fair split + remainder pass, zero duplication/negatives)
        int[] alloc1 = LifeLogic.distributeCharge(10_000, 3_000, new int[]{2_000, 2_000, 2_000});
        assertEquals(1_000, alloc1[0]);
        assertEquals(1_000, alloc1[1]);
        assertEquals(1_000, alloc1[2]);

        // Uneven needs: item 0 only needs 300, remaining 2,700 flows cleanly to item 1 without waste
        int[] alloc2 = LifeLogic.distributeCharge(50_000, 3_000, new int[]{300, 5_000});
        assertEquals(300, alloc2[0]);
        assertEquals(2_700, alloc2[1]);
        assertEquals(3_000, alloc2[0] + alloc2[1]);

        // Low available energy caps total strictly at availableEnergy
        int[] alloc3 = LifeLogic.distributeCharge(500, 32_000, new int[]{10_000, 10_000});
        assertEquals(500, alloc3[0] + alloc3[1]);
        assertTrue(alloc3[0] >= 0 && alloc3[1] >= 0);

        // Zero/negative inputs never produce negative or duplicated energy
        int[] alloc4 = LifeLogic.distributeCharge(-100, 2_000, new int[]{-50, 1_000});
        assertEquals(0, alloc4[0]);
        assertEquals(0, alloc4[1]);
    }

    @Test
    void playerMachineProgressTracksLifeWave1IdsWithoutBreakingSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must remain intact");
        PlayerMachineProgress prog = new PlayerMachineProgress();
        for (String id : List.of("auto_builder", "fluid_pump", "wireless_charger")) {
            prog.addXp(id, 500L);
            assertTrue(prog.getLevel(id) >= 1, id + " should track level cleanly");
            assertEquals(500L, prog.getXp(id));
        }
    }

    @Test
    void allLifeWave1AssetsPickaxeTagRecipesAndLootTablesExist() throws IOException {
        Path assets = Path.of("src/main/resources/assets/industrialcore");
        Path data = Path.of("src/main/resources/data/industrialcore");
        String pickaxeTag = Files.readString(
                Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));
        String ja = Files.readString(assets.resolve("lang/ja_jp.json"));
        String en = Files.readString(assets.resolve("lang/en_us.json"));

        for (String id : Set.of("auto_builder", "fluid_pump", "wireless_charger")) {
            assertTrue(Files.isRegularFile(assets.resolve("blockstates/" + id + ".json")), id + " blockstate");
            assertTrue(Files.isRegularFile(assets.resolve("models/block/" + id + ".json")), id + " block model");
            assertTrue(Files.isRegularFile(assets.resolve("models/item/" + id + ".json")), id + " item model");
            assertTrue(Files.isRegularFile(assets.resolve("textures/block/" + id + "_front.png")), id + " front tex");
            assertTrue(Files.isRegularFile(assets.resolve("textures/block/" + id + "_side.png")), id + " side tex");
            assertTrue(Files.isRegularFile(assets.resolve("textures/block/" + id + "_top.png")), id + " top tex");
            assertTrue(Files.isRegularFile(data.resolve("recipe/" + id + ".json")), id + " recipe");
            assertTrue(Files.isRegularFile(data.resolve("loot_table/blocks/" + id + ".json")), id + " loot table");
            assertTrue(pickaxeTag.contains("\"industrialcore:" + id + "\""), id + " missing from pickaxe tag");
            assertTrue(ja.contains("\"block.industrialcore." + id + "\""), id + " missing ja_jp");
            assertTrue(en.contains("\"block.industrialcore." + id + "\""), id + " missing en_us");
        }
    }
}
