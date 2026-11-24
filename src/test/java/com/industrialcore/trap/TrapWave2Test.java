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
 * Phase 93 pure-Java unit tests for Trap Branch wave 2:
 * {@link TrapWave2Layout} zero-overlap geometry for all 4 machines, {@link TrapWave2Logic}
 * XP math, natural-spawn-only suppression filter, laser turret safety filter, boss/player capture
 * blacklist, 16-mob crowd cap, {@link PlayerMachineProgress} compatibility, and shipped assets.
 */
class TrapWave2Test {

    @Test
    void allFourTrapWave2LayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        Rect headerBtn = new Rect(
                SkillTreeLayout.MACHINE_HEADER_BUTTON.x(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.y(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.width(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.height());

        // 1. XP Collector
        List<Rect> xpElems = new ArrayList<>();
        xpElems.add(headerBtn);
        xpElems.add(TrapWave2Layout.ENERGY_BAR);
        xpElems.add(new Rect(GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        xpElems.add(TrapWave2Layout.PROGRESS_BAR);
        xpElems.add(new Rect(GuiMetrics.SIDE, TrapWave2Layout.XP_INFO_TEXT_Y, GuiMetrics.CONTENT, 8));
        xpElems.add(TrapWave2Layout.XP_BTN_1LV);
        xpElems.add(TrapWave2Layout.XP_BTN_10LV);
        xpElems.add(TrapWave2Layout.XP_BTN_ALL);
        xpElems.add(new Rect(TrapWave2Layout.XP_INPUT_LABEL_X, TrapWave2Layout.XP_INPUT_LABEL_Y, 40, 8));
        xpElems.add(TrapWave2Layout.XP_INPUT_GROUP);
        xpElems.add(new Rect(TrapWave2Layout.XP_OUTPUT_LABEL_X, TrapWave2Layout.XP_OUTPUT_LABEL_Y, 64, 8));
        xpElems.add(TrapWave2Layout.XP_OUTPUT_GROUP);
        xpElems.add(new Rect(TrapWave2Layout.XP_INPUT_LABEL_X, TrapWave2Layout.XP_BOTTLED_TEXT_Y, 110, 8));
        xpElems.add(new Rect(TrapWave2Layout.XP_UPGRADE_CENTER_X - 24, TrapWave2Layout.XP_MOTOR_LABEL_Y, 48, 8));
        xpElems.add(TrapWave2Layout.XP_MOTOR_SLOT);
        xpElems.add(new Rect(TrapWave2Layout.XP_UPGRADE_CENTER_X - 24, TrapWave2Layout.XP_BATTERY_LABEL_Y, 48, 8));
        xpElems.add(TrapWave2Layout.XP_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            xpElems.add(TrapWave2Layout.xpPlayerSlot(i));
        }
        assertNoOverlaps(xpElems, "xp_collector");
        assertInsidePanel(xpElems, TrapWave2Layout.WIDTH, TrapWave2Layout.XP_HEIGHT);
        assertInside(TrapWave2Layout.XP_INPUT_GROUP, TrapWave2Layout.XP_INPUT_SLOT, "xpInputSlot");
        List<Rect> xpOuts = new ArrayList<>();
        for (int i = 0; i < TrapWave2Layout.XP_OUTPUT_COUNT; i++) {
            Rect s = TrapWave2Layout.xpOutputSlot(i);
            assertInside(TrapWave2Layout.XP_OUTPUT_GROUP, s, "xpOutputSlot " + i);
            xpOuts.add(s);
        }
        assertNoOverlaps(xpOuts, "xpOutputSlots");

        // 2. Spawn Suppressor
        List<Rect> supElems = new ArrayList<>();
        supElems.add(headerBtn);
        supElems.add(TrapWave2Layout.ENERGY_BAR);
        supElems.add(new Rect(GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        supElems.add(TrapWave2Layout.PROGRESS_BAR);
        supElems.add(TrapWave2Layout.SUP_STATS_BOX);
        supElems.add(new Rect(TrapWave2Layout.SUP_UPGRADE_CENTER_X - 24, TrapWave2Layout.SUP_MOTOR_LABEL_Y, 48, 8));
        supElems.add(TrapWave2Layout.SUP_MOTOR_SLOT);
        supElems.add(new Rect(TrapWave2Layout.SUP_UPGRADE_CENTER_X - 24, TrapWave2Layout.SUP_BATTERY_LABEL_Y, 48, 8));
        supElems.add(TrapWave2Layout.SUP_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            supElems.add(TrapWave2Layout.supPlayerSlot(i));
        }
        assertNoOverlaps(supElems, "spawn_suppressor");
        assertInsidePanel(supElems, TrapWave2Layout.WIDTH, TrapWave2Layout.SUP_HEIGHT);
        assertStatRowsInsideBox(TrapWave2Layout.SUP_STATS_BOX, TrapWave2Layout.SUP_STATS_TEXT_X, 12,
                new int[]{TrapWave2Layout.SUP_ROW_1_Y, TrapWave2Layout.SUP_ROW_2_Y,
                        TrapWave2Layout.SUP_ROW_3_Y, TrapWave2Layout.SUP_ROW_4_Y},
                "spawn_suppressor");

        // 3. Laser Turret
        List<Rect> turElems = new ArrayList<>();
        turElems.add(headerBtn);
        turElems.add(TrapWave2Layout.ENERGY_BAR);
        turElems.add(new Rect(GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        turElems.add(TrapWave2Layout.PROGRESS_BAR);
        turElems.add(TrapWave2Layout.TUR_STATS_BOX);
        turElems.add(new Rect(TrapWave2Layout.TUR_UPGRADE_CENTER_X - 24, TrapWave2Layout.TUR_MOTOR_LABEL_Y, 48, 8));
        turElems.add(TrapWave2Layout.TUR_MOTOR_SLOT);
        turElems.add(new Rect(TrapWave2Layout.TUR_UPGRADE_CENTER_X - 24, TrapWave2Layout.TUR_BATTERY_LABEL_Y, 48, 8));
        turElems.add(TrapWave2Layout.TUR_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            turElems.add(TrapWave2Layout.turPlayerSlot(i));
        }
        assertNoOverlaps(turElems, "laser_turret");
        assertInsidePanel(turElems, TrapWave2Layout.WIDTH, TrapWave2Layout.TUR_HEIGHT);
        assertStatRowsInsideBox(TrapWave2Layout.TUR_STATS_BOX, TrapWave2Layout.TUR_STATS_TEXT_X, 12,
                new int[]{TrapWave2Layout.TUR_ROW_1_Y, TrapWave2Layout.TUR_ROW_2_Y,
                        TrapWave2Layout.TUR_ROW_3_Y, TrapWave2Layout.TUR_ROW_4_Y},
                "laser_turret");

        // 4. Mob Duplicator
        List<Rect> dupElems = new ArrayList<>();
        dupElems.add(headerBtn);
        dupElems.add(TrapWave2Layout.ENERGY_BAR);
        dupElems.add(new Rect(GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, 8));
        dupElems.add(TrapWave2Layout.PROGRESS_BAR);
        dupElems.add(new Rect(TrapWave2Layout.DUP_CAPSULE_LABEL_X, TrapWave2Layout.DUP_CAPSULE_LABEL_Y, 30, 8));
        dupElems.add(TrapWave2Layout.DUP_CAPSULE_GROUP);
        dupElems.add(TrapWave2Layout.DUP_STATS_BOX);
        dupElems.add(new Rect(TrapWave2Layout.DUP_UPGRADE_CENTER_X - 24, TrapWave2Layout.DUP_MOTOR_LABEL_Y, 48, 8));
        dupElems.add(TrapWave2Layout.DUP_MOTOR_SLOT);
        dupElems.add(new Rect(TrapWave2Layout.DUP_UPGRADE_CENTER_X - 24, TrapWave2Layout.DUP_BATTERY_LABEL_Y, 48, 8));
        dupElems.add(TrapWave2Layout.DUP_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            dupElems.add(TrapWave2Layout.dupPlayerSlot(i));
        }
        assertNoOverlaps(dupElems, "mob_duplicator");
        assertInsidePanel(dupElems, TrapWave2Layout.WIDTH, TrapWave2Layout.DUP_HEIGHT);
        assertInside(TrapWave2Layout.DUP_CAPSULE_GROUP, TrapWave2Layout.DUP_CAPSULE_SLOT, "dupCapsuleSlot");
        assertStatRowsInsideBox(TrapWave2Layout.DUP_STATS_BOX, TrapWave2Layout.DUP_STATS_TEXT_X, 10,
                new int[]{TrapWave2Layout.DUP_ROW_1_Y, TrapWave2Layout.DUP_ROW_2_Y,
                        TrapWave2Layout.DUP_ROW_3_Y, TrapWave2Layout.DUP_ROW_4_Y},
                "mob_duplicator");
    }

    @Test
    void xpCollectorRadiusBottlingAndLevelWithdrawMathAreExact() {
        assertEquals(10, TrapWave2Logic.xpRadius(0));
        assertEquals(15, TrapWave2Logic.xpRadius(32));
        assertEquals(20, TrapWave2Logic.xpRadius(64));
        assertTrue(TrapWave2Logic.isInXpRange(20 * 20, 64));
        assertFalse(TrapWave2Logic.isInXpRange(21 * 21, 64));

        assertEquals(40, TrapWave2Logic.xpBottleTime(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(40, 64), TrapWave2Logic.xpBottleTime(64, 1));
        assertEquals(UpgradeMath.discountedEnergy(60, 64), TrapWave2Logic.xpBottleEnergy(64, 1));

        // Vanilla level XP ladder checks
        assertEquals(7, TrapWave2Logic.xpForNextLevel(0));
        assertEquals(37, TrapWave2Logic.xpForNextLevel(15));
        assertEquals(112, TrapWave2Logic.xpForNextLevel(30));

        // +1 Lv from Lv.0 with 0 progress = 7 XP
        assertEquals(7, TrapWave2Logic.computeXpWithdrawal(1000L, 0, 0.0f, TrapWave2Logic.XP_ACTION_TAKE_1_LV));
        // +10 Lv from Lv.0 = sum_{i=0..9} (7 + 2i) = 70 + 90 = 160 XP
        assertEquals(160, TrapWave2Logic.computeXpWithdrawal(1000L, 0, 0.0f, TrapWave2Logic.XP_ACTION_TAKE_10_LV));
        // All returns up to storedXp (or 0 when empty)
        assertEquals(500, TrapWave2Logic.computeXpWithdrawal(500L, 10, 0.25f, TrapWave2Logic.XP_ACTION_TAKE_ALL));
        assertEquals(5, TrapWave2Logic.computeXpWithdrawal(5L, 0, 0.0f, TrapWave2Logic.XP_ACTION_TAKE_1_LV));
        assertEquals(0, TrapWave2Logic.computeXpWithdrawal(0L, 0, 0.0f, TrapWave2Logic.XP_ACTION_TAKE_ALL));
    }

    @Test
    void spawnSuppressorRadiusAndNaturalHostileOnlyFilterAreExact() {
        assertEquals(32, TrapWave2Logic.suppressorRadius(0));
        assertEquals(48, TrapWave2Logic.suppressorRadius(32));
        assertEquals(64, TrapWave2Logic.suppressorRadius(64));

        assertTrue(TrapWave2Logic.isInSuppressorRange(32 * 32, 32));
        assertFalse(TrapWave2Logic.isInSuppressorRange(33 * 33, 32));
        assertTrue(TrapWave2Logic.isInSuppressorRange(64 * 64, 64));

        // Strictly cancels NATURAL hostile spawns only!
        assertTrue(TrapWave2Logic.shouldSuppressSpawn(true, true));
        // Passive natural spawns allowed
        assertFalse(TrapWave2Logic.shouldSuppressSpawn(true, false));
        // Mob Duplicator (SPAWNER), spawn eggs, commands ALWAYS allowed!
        assertFalse(TrapWave2Logic.shouldSuppressSpawn(false, true));
        assertFalse(TrapWave2Logic.shouldSuppressSpawn(false, false));

        assertEquals(UpgradeMath.discountedEnergy(24, 64), TrapWave2Logic.suppressorEnergyPerTick(64, 1));
    }

    @Test
    void laserTurretRangeIntervalDamageBeamBudgetAndSafetyGuardAreExact() {
        // Phase 113: Specialization module scales range & pulse damage with diminishing returns;
        // rapid pulse interval base 8t accelerated by motors.
        assertEquals(16, TrapWave2Logic.turretRange(0));
        assertEquals(16 + UpgradeMath.turretRangeBonus(32), TrapWave2Logic.turretRange(32));
        assertEquals(16 + UpgradeMath.turretRangeBonus(64), TrapWave2Logic.turretRange(64));

        assertEquals(TrapWave2Logic.TURRET_BASE_INTERVAL_TICKS, TrapWave2Logic.turretFireInterval(0, 1));
        assertEquals(UpgradeMath.acceleratedTime(TrapWave2Logic.TURRET_BASE_INTERVAL_TICKS, 64),
                TrapWave2Logic.turretFireInterval(64, 1));
        assertEquals(TrapWave2Logic.TURRET_BASE_DAMAGE, TrapWave2Logic.turretDamage(0), 1e-4f);
        assertEquals((float) (TrapWave2Logic.TURRET_BASE_DAMAGE * UpgradeMath.turretDamageMultiplier(64)),
                TrapWave2Logic.turretDamage(64), 1e-3f);
        assertEquals(UpgradeMath.discountedEnergy(TrapWave2Logic.TURRET_BASE_SHOT_ENERGY, 64),
                TrapWave2Logic.turretShotEnergy(64, 1));

        assertTrue(TrapWave2Logic.turretBeamPoints(1.0) >= 4);
        assertEquals(TrapWave2Logic.TURRET_MAX_BEAM_POINTS, TrapWave2Logic.turretBeamPoints(100.0));

        // Strict target filter: living Enemy only; NEVER players/villagers/golems/pets/armor stands
        assertTrue(TrapWave2Logic.canTurretTarget(true, false, false, false, false, true));
        assertFalse(TrapWave2Logic.canTurretTarget(false, false, false, false, false, true));
        assertFalse(TrapWave2Logic.canTurretTarget(true, true, false, false, false, true));
        assertFalse(TrapWave2Logic.canTurretTarget(true, false, true, false, false, true));
        assertFalse(TrapWave2Logic.canTurretTarget(true, false, false, true, false, true));
        assertFalse(TrapWave2Logic.canTurretTarget(true, false, false, false, true, true));
        assertFalse(TrapWave2Logic.canTurretTarget(true, false, false, false, false, false));
    }

    @Test
    void mobCapsuleBlacklistAndDuplicatorCrowdingIntervalAndBatchAreExact() {
        // Bosses & players MUST be blocked
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:ender_dragon", false, false));
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:wither", false, false));
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:warden", false, false));
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:elder_guardian", false, false));
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:zombie", true, false));
        assertFalse(TrapWave2Logic.canCaptureEntity("minecraft:zombie", false, true));
        assertFalse(TrapWave2Logic.canCaptureEntity("", false, false));

        // Standard mobs allowed
        assertTrue(TrapWave2Logic.canCaptureEntity("minecraft:zombie", false, false));
        assertTrue(TrapWave2Logic.canCaptureEntity("minecraft:skeleton", false, false));
        assertTrue(TrapWave2Logic.canCaptureEntity("minecraft:creeper", false, false));

        // Interval 100..20, batch 1..4
        assertEquals(100, TrapWave2Logic.duplicatorInterval(0, 1));
        assertEquals(60, TrapWave2Logic.duplicatorInterval(32, 1));
        assertEquals(20, TrapWave2Logic.duplicatorInterval(64, 1));
        assertEquals(1, TrapWave2Logic.duplicatorBatchCount(0));
        assertEquals(4, TrapWave2Logic.duplicatorBatchCount(64));

        // 16-mob crowd limit guard
        assertFalse(TrapWave2Logic.isDuplicatorCrowded(15));
        assertTrue(TrapWave2Logic.isDuplicatorCrowded(16));
        assertTrue(TrapWave2Logic.isDuplicatorCrowded(20));
        assertEquals(4, TrapWave2Logic.duplicatorSpawnRoom(10, 4));
        assertEquals(2, TrapWave2Logic.duplicatorSpawnRoom(14, 4));
        assertEquals(0, TrapWave2Logic.duplicatorSpawnRoom(16, 4));
    }

    @Test
    void playerMachineProgressTracksWave2TrapIdsWithoutBreakingSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must stay intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("xp_collector", "spawn_suppressor", "laser_turret", "mob_duplicator")) {
            assertEquals(1, progress.getLevel(id));
            progress.addXp(id, 5_000L);
            assertTrue(progress.getLevel(id) > 1, id + " must level up cleanly");
            assertTrue(progress.getUpgradeLimit(id) > 1, id + " upgrade cap must expand with level");
        }
        PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(
                progress.isLimitBroken(), progress.snapshotXp(), progress.snapshotRevealedNodes());
        assertEquals(progress.getXp("xp_collector"), restored.getXp("xp_collector"));
        assertEquals(progress.getXp("spawn_suppressor"), restored.getXp("spawn_suppressor"));
        assertEquals(progress.getXp("laser_turret"), restored.getXp("laser_turret"));
        assertEquals(progress.getXp("mob_duplicator"), restored.getXp("mob_duplicator"));
    }

    @Test
    void allWave2TrapAssetsPickaxeTagRecipesAndLootTablesExist() throws Exception {
        Path res = Path.of("src/main/resources");
        String pickaxe = Files.readString(res.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));
        for (String id : List.of("xp_collector", "spawn_suppressor", "laser_turret", "mob_duplicator")) {
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/blockstates/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/block/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/item/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_front.png")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_side.png")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/block/" + id + "_top.png")));
            assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/loot_table/blocks/" + id + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/recipe/" + id + ".json")));
            assertTrue(pickaxe.contains("industrialcore:" + id), id + " missing from pickaxe tag");
        }
        assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/item/mob_capsule.json")));
        assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/item/mob_capsule.png")));
        assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/recipe/mob_capsule.json")));
    }

    private static void assertStatRowsInsideBox(Rect box, int textX, int padRight, int[] ys, String label) {
        List<Rect> rows = new ArrayList<>();
        for (int ry : ys) {
            Rect r = new Rect(textX, ry, box.width() - padRight, 8);
            assertInside(box, r, label + " row y=" + ry);
            rows.add(r);
        }
        assertNoOverlaps(rows, label + " stat rows");
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
