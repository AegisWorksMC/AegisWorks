package com.industrialcore.endgame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.ParallelExecutionLogic;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 99 pure-Java unit tests for:
 * <ul>
 *   <li>{@link SingularityReactorLogic} motor output scaling, {@code long} storage &amp; battery efficiency</li>
 *   <li>{@link HyperConduitLogic} unlimited FE conservation, {@code int} clamp, and item zero-loss rules</li>
 *   <li>{@link MachineLevelMath#parallelBatchSize} &amp; {@link ParallelExecutionLogic} batch clamping</li>
 *   <li>{@link SingularityReactorLayout} geometry and Phase 99 shipped assets</li>
 * </ul>
 */
class EndgameInflationTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");

    @Test
    void singularityReactorScalingLongStorageAndBatteryEfficiencyAreExact() {
        // Output scales from 100k FE/t (0 motors) to 10M FE/t (64 motors) at Lv.1..99
        assertEquals(100_000L, SingularityReactorLogic.fePerTick(0, 1));
        assertEquals(10_000_000L, SingularityReactorLogic.fePerTick(64, 99));
        assertTrue(SingularityReactorLogic.fePerTick(32, 1) > 4_500_000L);
        assertEquals(25_000_000L, SingularityReactorLogic.fePerTick(64, 1000));

        // Long capacity scales from 1G FE to 17G FE without overflow
        assertEquals(1_000_000_000L, SingularityReactorLogic.capacity(0));
        assertEquals(17_000_000_000L, SingularityReactorLogic.capacity(64));
        assertEquals(17_000_000_000L,
                SingularityReactorLogic.addEnergy(16_000_000_000L, Long.MAX_VALUE, 17_000_000_000L));
        assertEquals(Integer.MAX_VALUE, SingularityReactorLogic.clampToInt(17_000_000_000L));
        assertEquals(0, SingularityReactorLogic.clampToInt(-100L));

        // Battery efficiency extends fuel burn duration up to +100% (2.0x at 64)
        assertEquals(1.0D, SingularityReactorLogic.burnEfficiencyMultiplier(0), 1.0E-6D);
        assertEquals(2.0D, SingularityReactorLogic.burnEfficiencyMultiplier(64), 1.0E-6D);
        assertEquals(48_000, SingularityReactorLogic.effectiveBurnTicks(24_000, 64));

        // Valid high-value fuels vs non-fuels
        assertTrue(SingularityReactorLogic.isFuel("minecraft:nether_star"));
        assertTrue(SingularityReactorLogic.isFuel("industrialcore:phase_core"));
        assertTrue(SingularityReactorLogic.isFuel("minecraft:diamond_block"));
        assertFalse(SingularityReactorLogic.isFuel("minecraft:dirt"));
    }

    @Test
    void hyperConduitEnergyConservationIntClampAndItemMoveAreExact() {
        // Energy clamp to Integer.MAX_VALUE with long inputs
        assertEquals(Integer.MAX_VALUE,
                HyperConduitLogic.computeEnergyTransfer(10_000_000_000L, 10_000_000_000L));
        assertEquals(500_000, HyperConduitLogic.computeEnergyTransfer(500_000L, 10_000_000L));
        assertEquals(0, HyperConduitLogic.computeEnergyTransfer(0L, 10_000_000L));

        // Multi-sink lossless distribution
        long[] rooms = {500_000L, 1_500_000L, 3_000_000L};
        long[] alloc = HyperConduitLogic.distributeEnergy(2_000_000L, rooms);
        long sum = 0L;
        for (int i = 0; i < rooms.length; i++) {
            assertTrue(alloc[i] >= 0L && alloc[i] <= rooms[i]);
            sum += alloc[i];
        }
        assertEquals(2_000_000L, sum, "Exact conservation when total room >= supply");

        long[] over = HyperConduitLogic.distributeEnergy(99_000_000L, rooms);
        assertEquals(500_000L, over[0]);
        assertEquals(1_500_000L, over[1]);
        assertEquals(3_000_000L, over[2]);

        // FaceMode cycle & flags
        HyperConduitLogic.FaceMode m = HyperConduitLogic.FaceMode.BOTH;
        assertTrue(m.allowsExtract() && m.allowsInsert() && m.isConnected());
        m = m.next();
        assertEquals(HyperConduitLogic.FaceMode.EXTRACT, m);
        assertTrue(m.allowsExtract() && !m.allowsInsert());
        m = m.next();
        assertEquals(HyperConduitLogic.FaceMode.INSERT, m);
        assertTrue(!m.allowsExtract() && m.allowsInsert());
        m = m.next();
        assertEquals(HyperConduitLogic.FaceMode.DISABLED, m);
        assertFalse(m.isConnected());
        assertEquals(HyperConduitLogic.FaceMode.BOTH, m.next());

        // Item conservation when destination full / partial
        assertEquals(0, HyperConduitLogic.computeItemMove(64, 64, 64));
        assertEquals(14, HyperConduitLogic.computeItemMove(64, 50, 64));
        assertEquals(12, HyperConduitLogic.computeItemMove(12, 0, 64));
    }

    @Test
    void parallelBatchSizeMilestonesAndConservationClampsAreExact() {
        // Lv.1..99 and non-limit-broken are ALWAYS 1
        for (int lvl = 1; lvl <= 99; lvl++) {
            assertEquals(1, MachineLevelMath.parallelBatchSize(lvl, false));
            assertEquals(1, MachineLevelMath.parallelBatchSize(lvl, true));
        }
        assertEquals(1, MachineLevelMath.parallelBatchSize(500, false));
        assertEquals(1, MachineLevelMath.parallelBatchSize(1000, false));

        // Exact milestones for Limit-Broken Lv.100..1000
        assertEquals(2, MachineLevelMath.parallelBatchSize(100, true));
        assertEquals(8, MachineLevelMath.parallelBatchSize(250, true));
        assertEquals(16, MachineLevelMath.parallelBatchSize(500, true));
        assertEquals(32, MachineLevelMath.parallelBatchSize(750, true));
        assertEquals(64, MachineLevelMath.parallelBatchSize(1000, true));

        // Monotonic non-decreasing across entire 100..1000 range
        int prev = 1;
        for (int lvl = 100; lvl <= 1000; lvl++) {
            int cur = MachineLevelMath.parallelBatchSize(lvl, true);
            assertTrue(cur >= prev && cur <= 64, "Non-monotonic parallel batch at Lv." + lvl);
            prev = cur;
        }

        // Limit-broken cycle time stays untouched for Lv.1..99 and reaches 1 tick at Lv.1000 + 64 motors
        assertEquals(20, MachineLevelMath.limitBrokenCycleTime(20, 99, true, 64));
        assertEquals(20, MachineLevelMath.limitBrokenCycleTime(20, 1000, false, 64));
        assertEquals(1, MachineLevelMath.limitBrokenCycleTime(20, 1000, true, 64));
        assertTrue(MachineLevelMath.limitBrokenCycleTime(20, 500, true, 64) < 20);

        // ParallelExecutionLogic clamps by input, output space, and FE cleanly
        ParallelExecutionLogic.BatchPlan full64 = ParallelExecutionLogic.computeBatch(
                64, 64, 1, 1, 0, 64, 200L, 1_000_000L);
        assertEquals(64, full64.actualBatch());
        assertEquals(64, full64.totalInputCount());
        assertEquals(64, full64.totalOutputCount());
        assertEquals(12_800L, full64.totalEnergyCost());

        // Clamped by output space (2x dust output, 40 already in output -> room for 24 -> max 12 ops)
        ParallelExecutionLogic.BatchPlan outClamped = ParallelExecutionLogic.computeBatch(
                64, 64, 1, 2, 40, 64, 200L, 1_000_000L);
        assertEquals(12, outClamped.actualBatch());
        assertEquals(24, outClamped.totalOutputCount());

        // Clamped by available FE (only enough for 5 ops)
        ParallelExecutionLogic.BatchPlan feClamped = ParallelExecutionLogic.computeBatch(
                64, 64, 1, 1, 0, 64, 1_000L, 5_500L);
        assertEquals(5, feClamped.actualBatch());
        assertEquals(5_000L, feClamped.totalEnergyCost());

        // Output full -> 0 batch, outputFull == true
        ParallelExecutionLogic.BatchPlan outFull = ParallelExecutionLogic.computeBatch(
                16, 32, 1, 1, 64, 64, 100L, 100_000L);
        assertTrue(outFull.outputFull());
        assertEquals(0, outFull.actualBatch());

        // Insufficient FE for even 1 op -> flagged cleanly
        ParallelExecutionLogic.BatchPlan noFe = ParallelExecutionLogic.computeBatch(
                16, 32, 1, 1, 0, 64, 500L, 100L);
        assertTrue(noFe.insufficientEnergy());
        assertEquals(1, noFe.actualBatch());
    }

    @Test
    void singularityReactorLayoutHasZeroOverlapsAndAllPhase99AssetsExist() throws IOException {
        // Skill Tree 38-node invariant intact
        assertEquals(38, MachineSkillTree.totalNodes());

        Rect panel = new Rect(0, 0, SingularityReactorLayout.WIDTH, SingularityReactorLayout.HEIGHT);
        Rect headerBtn = new Rect(
                SkillTreeLayout.MACHINE_HEADER_BUTTON.x(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.y(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.width(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.height());
        Rect invArea = new Rect(8, SingularityReactorLayout.PLAYER_INV_Y, 162, 76);

        List<Rect> parts = List.of(
                headerBtn,
                SingularityReactorLayout.ENERGY_BAR,
                SingularityReactorLayout.FUEL_SLOT_BOX,
                SingularityReactorLayout.MOTOR_SLOT_BOX,
                SingularityReactorLayout.BATTERY_SLOT_BOX,
                SingularityReactorLayout.TELEMETRY_PANEL,
                SingularityReactorLayout.MULTIBLOCK_BAR,
                SingularityReactorLayout.GUIDE_BUTTON,
                invArea
        );
        for (int i = 0; i < parts.size(); i++) {
            Rect a = parts.get(i);
            assertInside(panel, a, "Part_" + i);
            for (int j = i + 1; j < parts.size(); j++) {
                assertFalse(overlaps(a, parts.get(j)), "Overlap between " + i + " and " + j);
            }
        }
        assertInside(SingularityReactorLayout.TELEMETRY_PANEL, SingularityReactorLayout.BURN_BAR, "BURN_BAR");

        // Verify all Phase 99 assets, recipes, loot tables, pickaxe tag, and lang keys
        String pickaxe = Files.readString(Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));
        for (String id : List.of("singularity_reactor", "hyper_energy_conduit", "hyper_item_conduit")) {
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + id + ".json")), "Missing blockstate: " + id);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")), "Missing item model: " + id);
            assertTrue(Files.exists(DATA.resolve("loot_table/blocks/" + id + ".json")), "Missing loot: " + id);
            assertTrue(Files.exists(DATA.resolve("recipe/" + id + ".json")), "Missing recipe: " + id);
            assertTrue(pickaxe.contains("\"industrialcore:" + id + "\""), "Missing pickaxe tag: " + id);
        }
        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(
                    Files.readString(ASSETS.resolve("lang/" + langFile))).getAsJsonObject();
            for (String key : List.of(
                    "block.industrialcore.singularity_reactor",
                    "block.industrialcore.hyper_energy_conduit",
                    "block.industrialcore.hyper_item_conduit",
                    "gui.industrialcore.machine.parallel_badge",
                    "message.industrialcore.conduit_face_mode")) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                        langFile + " missing " + key);
            }
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }

    private static void assertInside(Rect outer, Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                label + " out of bounds: " + inner + " not in " + outer);
    }
}
