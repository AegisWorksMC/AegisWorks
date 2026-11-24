package com.industrialcore.trap;

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
 * Phase 92 pure-Java unit tests for Trap Branch wave 1:
 * {@link TrapLayout} zero-overlap geometry, {@link TrapLogic} 6-direction boxes, safety guards &amp;
 * upgrade math, {@link PlayerMachineProgress} XP/level compatibility, and shipped assets.
 */
class TrapMachinesTest {

    @Test
    void allThreeTrapLayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        Rect headerBtn = new Rect(
                SkillTreeLayout.MACHINE_HEADER_BUTTON.x(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.y(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.width(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.height());

        // 1. Mob Fan
        List<Rect> fanElems = new ArrayList<>();
        fanElems.add(headerBtn);
        fanElems.add(TrapLayout.ENERGY_BAR);
        fanElems.add(new Rect(GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        fanElems.add(TrapLayout.PROGRESS_BAR);
        fanElems.add(TrapLayout.FAN_STATS_BOX);
        fanElems.add(new Rect(TrapLayout.FAN_UPGRADE_CENTER_X - 24, TrapLayout.FAN_MOTOR_LABEL_Y, 48, 8));
        fanElems.add(TrapLayout.FAN_MOTOR_SLOT);
        fanElems.add(new Rect(TrapLayout.FAN_UPGRADE_CENTER_X - 24, TrapLayout.FAN_BATTERY_LABEL_Y, 48, 8));
        fanElems.add(TrapLayout.FAN_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            fanElems.add(TrapLayout.fanPlayerSlot(i));
        }
        assertNoOverlaps(fanElems, "mob_fan");
        assertInsidePanel(fanElems, TrapLayout.WIDTH, TrapLayout.FAN_HEIGHT);

        int[] fanRows = {TrapLayout.FAN_ROW_1_Y, TrapLayout.FAN_ROW_2_Y, TrapLayout.FAN_ROW_3_Y, TrapLayout.FAN_ROW_4_Y};
        List<Rect> fanRowRects = new ArrayList<>();
        for (int ry : fanRows) {
            Rect r = new Rect(TrapLayout.FAN_STATS_TEXT_X, ry, TrapLayout.FAN_STATS_BOX.width() - 12, 8);
            assertInside(TrapLayout.FAN_STATS_BOX, r, "mob_fan stat row y=" + ry);
            fanRowRects.add(r);
        }
        assertNoOverlaps(fanRowRects, "mob_fan stat rows");

        // 2. Rotary Drill (weapon slot + center telemetry + right upgrades)
        List<Rect> drillElems = new ArrayList<>();
        drillElems.add(headerBtn);
        drillElems.add(TrapLayout.ENERGY_BAR);
        drillElems.add(new Rect(GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        drillElems.add(TrapLayout.PROGRESS_BAR);
        drillElems.add(new Rect(TrapLayout.DRILL_WEAPON_LABEL_X, TrapLayout.DRILL_WEAPON_LABEL_Y, 30, 8));
        drillElems.add(TrapLayout.DRILL_WEAPON_GROUP);
        drillElems.add(TrapLayout.DRILL_STATS_BOX);
        drillElems.add(new Rect(TrapLayout.DRILL_UPGRADE_CENTER_X - 24, TrapLayout.DRILL_MOTOR_LABEL_Y, 48, 8));
        drillElems.add(TrapLayout.DRILL_MOTOR_SLOT);
        drillElems.add(new Rect(TrapLayout.DRILL_UPGRADE_CENTER_X - 24, TrapLayout.DRILL_BATTERY_LABEL_Y, 48, 8));
        drillElems.add(TrapLayout.DRILL_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            drillElems.add(TrapLayout.drillPlayerSlot(i));
        }
        assertNoOverlaps(drillElems, "rotary_drill");
        assertInsidePanel(drillElems, TrapLayout.WIDTH, TrapLayout.DRILL_HEIGHT);
        assertInside(TrapLayout.DRILL_WEAPON_GROUP, TrapLayout.DRILL_WEAPON_SLOT, "drillWeaponSlot");

        int[] drillRows = {TrapLayout.DRILL_ROW_1_Y, TrapLayout.DRILL_ROW_2_Y, TrapLayout.DRILL_ROW_3_Y, TrapLayout.DRILL_ROW_4_Y};
        List<Rect> drillRowRects = new ArrayList<>();
        for (int ry : drillRows) {
            Rect r = new Rect(TrapLayout.DRILL_STATS_TEXT_X, ry, TrapLayout.DRILL_STATS_BOX.width() - 10, 8);
            assertInside(TrapLayout.DRILL_STATS_BOX, r, "rotary_drill stat row y=" + ry);
            drillRowRects.add(r);
        }
        assertNoOverlaps(drillRowRects, "rotary_drill stat rows");

        // 3. Item Vacuum (4x3 output grid + right upgrades)
        List<Rect> vacuumElems = new ArrayList<>();
        vacuumElems.add(headerBtn);
        vacuumElems.add(TrapLayout.ENERGY_BAR);
        vacuumElems.add(new Rect(GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        vacuumElems.add(TrapLayout.PROGRESS_BAR);
        vacuumElems.add(new Rect(GuiMetrics.SIDE, TrapLayout.INFO_TEXT_Y, GuiMetrics.CONTENT, 8));
        vacuumElems.add(new Rect(TrapLayout.VACUUM_OUTPUT_LABEL_X, TrapLayout.VACUUM_OUTPUT_LABEL_Y, 76, 8));
        vacuumElems.add(TrapLayout.VACUUM_OUTPUT_GROUP);
        vacuumElems.add(new Rect(TrapLayout.VACUUM_UPGRADE_CENTER_X - 24, TrapLayout.VACUUM_MOTOR_LABEL_Y, 48, 8));
        vacuumElems.add(TrapLayout.VACUUM_MOTOR_SLOT);
        vacuumElems.add(new Rect(TrapLayout.VACUUM_UPGRADE_CENTER_X - 24, TrapLayout.VACUUM_BATTERY_LABEL_Y, 48, 8));
        vacuumElems.add(TrapLayout.VACUUM_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            vacuumElems.add(TrapLayout.vacuumPlayerSlot(i));
        }
        assertNoOverlaps(vacuumElems, "item_vacuum");
        assertInsidePanel(vacuumElems, TrapLayout.WIDTH, TrapLayout.VACUUM_HEIGHT);

        List<Rect> vacSlots = new ArrayList<>();
        for (int i = 0; i < TrapLayout.VACUUM_OUTPUT_COUNT; i++) {
            Rect s = TrapLayout.vacuumOutputSlot(i);
            assertInside(TrapLayout.VACUUM_OUTPUT_GROUP, s, "vacuumOutput " + i);
            vacSlots.add(s);
        }
        assertNoOverlaps(vacSlots, "vacuumOutput");
    }

    @Test
    void mobFanSixDirectionBoxesRangePushAndSneakImmunityAreExact() {
        assertEquals(8, TrapLogic.fanRange(0));
        assertEquals(12, TrapLogic.fanRange(32));
        assertEquals(16, TrapLogic.fanRange(64));

        assertEquals(0.14D, TrapLogic.fanPushStrength(0), 1e-6);
        assertEquals(0.28D, TrapLogic.fanPushStrength(64), 1e-6);
        assertEquals(100, TrapLogic.fanPowerPercent(0));
        assertEquals(200, TrapLogic.fanPowerPercent(64));
        assertEquals(UpgradeMath.discountedEnergy(12, 64), TrapLogic.fanEnergyPerTick(64, 1));

        // Check all 6 directions for volume extents (block at 10, 20, 30; range 8)
        assertEquals(new TrapLogic.Box6(9, 19, 22, 12, 22, 30), TrapLogic.fanVolume(10, 20, 30, 0, 0, -1, 0));
        assertEquals(new TrapLogic.Box6(9, 19, 31, 12, 22, 39), TrapLogic.fanVolume(10, 20, 30, 0, 0, 1, 0));
        assertEquals(new TrapLogic.Box6(11, 19, 29, 19, 22, 32), TrapLogic.fanVolume(10, 20, 30, 1, 0, 0, 0));
        assertEquals(new TrapLogic.Box6(2, 19, 29, 10, 22, 32), TrapLogic.fanVolume(10, 20, 30, -1, 0, 0, 0));
        assertEquals(new TrapLogic.Box6(9, 21, 29, 12, 29, 32), TrapLogic.fanVolume(10, 20, 30, 0, 1, 0, 0));
        assertEquals(new TrapLogic.Box6(9, 12, 29, 12, 20, 32), TrapLogic.fanVolume(10, 20, 30, 0, -1, 0, 0));
        // Upgraded range 16
        assertEquals(new TrapLogic.Box6(9, 21, 29, 12, 37, 32), TrapLogic.fanVolume(10, 20, 30, 0, 1, 0, 64));

        // Sneaking player immunity
        assertFalse(TrapLogic.shouldPushEntity(true, true, false, true));
        assertTrue(TrapLogic.shouldPushEntity(true, false, false, true));
        assertTrue(TrapLogic.shouldPushEntity(false, true, false, true)); // mobs still pushed
        assertFalse(TrapLogic.shouldPushEntity(true, false, true, true)); // spectator immune
    }

    @Test
    void rotaryDrillSixDirectionVolumeSpeedDamageAndSafetyGuardAreExact() {
        assertEquals(new TrapLogic.Box6(9, 19, 28, 12, 22, 30), TrapLogic.drillVolume(10, 20, 30, 0, 0, -1));
        assertEquals(new TrapLogic.Box6(9, 21, 29, 12, 23, 32), TrapLogic.drillVolume(10, 20, 30, 0, 1, 0));
        assertEquals(new TrapLogic.Box6(9, 18, 29, 12, 20, 32), TrapLogic.drillVolume(10, 20, 30, 0, -1, 0));

        assertEquals(20, TrapLogic.drillAttackInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(20, 64), TrapLogic.drillAttackInterval(64, 1));
        assertEquals(6.0F, TrapLogic.drillBaseDamage(0), 1e-4F);
        assertEquals(18.0F, TrapLogic.drillBaseDamage(64), 1e-4F);
        assertEquals(UpgradeMath.discountedEnergy(180, 64), TrapLogic.drillAttackEnergy(64, 1));

        // Strict safety guard: never harm players, pets/owned, or armor stands
        assertTrue(TrapLogic.canDrillAttack(false, false, false, true));
        assertFalse(TrapLogic.canDrillAttack(true, false, false, true));
        assertFalse(TrapLogic.canDrillAttack(false, true, false, true));
        assertFalse(TrapLogic.canDrillAttack(false, false, true, true));
        assertFalse(TrapLogic.canDrillAttack(false, false, false, false));
    }

    @Test
    void itemVacuumRadiusIntervalParticlesAndBatterySavingWorkTogether() {
        assertEquals(8, TrapLogic.vacuumRadius(0));
        assertEquals(12, TrapLogic.vacuumRadius(32));
        assertEquals(16, TrapLogic.vacuumRadius(64));

        assertTrue(TrapLogic.isInVacuumRange(8 * 8, 0));
        assertFalse(TrapLogic.isInVacuumRange(9 * 9, 0));
        assertTrue(TrapLogic.isInVacuumRange(16 * 16, 64));
        assertFalse(TrapLogic.isInVacuumRange(17 * 17, 64));

        assertEquals(16, TrapLogic.vacuumInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(16, 64), TrapLogic.vacuumInterval(64, 1));
        assertEquals(UpgradeMath.discountedEnergy(30, 64), TrapLogic.vacuumPickupEnergy(64, 1));
        assertEquals(0, TrapLogic.vacuumParticleBudget(0));
        assertEquals(4, TrapLogic.vacuumParticleBudget(4));
        assertEquals(6, TrapLogic.vacuumParticleBudget(15));
    }

    @Test
    void playerMachineProgressTracksTrapIdsWithoutBreakingSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must stay intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("mob_fan", "rotary_drill", "item_vacuum")) {
            assertEquals(1, progress.getLevel(id));
            progress.addXp(id, 5_000L);
            assertTrue(progress.getLevel(id) > 1, id + " must level up cleanly");
            assertTrue(progress.getUpgradeLimit(id) > 1, id + " upgrade cap must expand with level");
        }
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), progress.snapshotRevealedNodes());
        assertEquals(progress.getXp("mob_fan"), restored.getXp("mob_fan"));
        assertEquals(progress.getXp("rotary_drill"), restored.getXp("rotary_drill"));
        assertEquals(progress.getXp("item_vacuum"), restored.getXp("item_vacuum"));
    }

    @Test
    void allThreeTrapAssetsSixDirectionStatesRecipesAndLootTablesExist() throws Exception {
        Path res = Path.of("src/main/resources");
        for (String id : List.of("mob_fan", "rotary_drill", "item_vacuum")) {
            Path bs = res.resolve("assets/industrialcore/blockstates/" + id + ".json");
            assertTrue(Files.isRegularFile(bs));
            String bsJson = Files.readString(bs);
            if (!id.equals("item_vacuum")) {
                assertTrue(bsJson.contains("facing=up,lit=true"), id + " must expose 6-direction up variant");
                assertTrue(bsJson.contains("facing=down,lit=false"), id + " must expose 6-direction down variant");
            }
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
