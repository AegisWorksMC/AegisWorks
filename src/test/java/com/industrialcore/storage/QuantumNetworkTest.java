package com.industrialcore.storage;

import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.storage.QuantumNetLogic.Offset3;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetLogic.QuantumNetworkRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 96 pure-Java unit tests for Quantum Storage Networking:
 * frequency channels, permissions, aggregate node capacity &amp; zero-loss capacity drop guard,
 * importer/exporter 6-direction math, filters, conservation law, and layouts.
 */
class QuantumNetworkTest {

    @Test
    void channelCreateDeletePermissionsAndBigIntegerSharingWorkCleanly() {
        QuantumNetworkRegistry<String, String> reg = new QuantumNetworkRegistry<>();
        QuantumChannel<String, String> pubCh = reg.createChannel("メイン工場", "owner-1", true);
        QuantumChannel<String, String> privCh = reg.createChannel("秘密倉庫", "owner-1", false);
        assertEquals(2, reg.size());

        // Public visible to everyone; private visible only to owner
        assertEquals(2, reg.visibleChannelsFor("owner-1").size());
        List<QuantumChannel<String, String>> otherView = reg.visibleChannelsFor("owner-2");
        assertEquals(1, otherView.size());
        assertEquals(pubCh.channelId(), otherView.get(0).channelId());
        assertFalse(privCh.canAccess("owner-2"));
        assertTrue(privCh.canAccess("owner-1"));

        // Non-owner cannot toggle public or delete
        assertFalse(reg.togglePublic(privCh.channelId(), "owner-2"));
        assertFalse(reg.deleteChannel(privCh.channelId(), "owner-2"));
        assertNotNull(reg.get(privCh.channelId()));

        // Owner toggles private -> public
        assertTrue(reg.togglePublic(privCh.channelId(), "owner-1"));
        assertEquals(2, reg.visibleChannelsFor("owner-2").size());

        // BigInteger shared stock across nodes
        BigInteger huge = BigInteger.TEN.pow(68);
        StorageCoreTier[] mk5Node = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
        mk5Node[0] = StorageCoreTier.MK5;
        reg.updateNode(pubCh.channelId(), "overworld@0,64,0", mk5Node);
        assertEquals(huge, pubCh.storage().insertItem("minecraft:diamond", huge, false));
        assertEquals(huge, pubCh.storage().getItemCount("minecraft:diamond"));

        // Owner deletes private channel cleanly
        assertTrue(reg.deleteChannel(privCh.channelId(), "owner-1"));
        assertNull(reg.get(privCh.channelId()));
        assertEquals(1, reg.size());
    }

    @Test
    void nodeCapacityAggregationAndCapacityReductionNeverLoseExistingStock() {
        QuantumNetworkRegistry<String, String> reg = new QuantumNetworkRegistry<>();
        QuantumChannel<String, String> ch = reg.createChannel("共有ストレージ", "owner-1", true);

        StorageCoreTier[] nodeA = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
        nodeA[0] = StorageCoreTier.MK2; // +512 types, +10,000,000 per type
        StorageCoreTier[] nodeB = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
        nodeB[0] = StorageCoreTier.MK1; // +64 types, +65,536 per type

        reg.updateNode(ch.channelId(), "overworld@10,64,10", nodeA);
        reg.updateNode(ch.channelId(), "the_nether@20,70,20", nodeB);

        int expectedTypes = StorageCoreTier.totalMaxTypes(nodeA) + StorageCoreTier.totalMaxTypes(nodeB);
        BigInteger expectedPer = StorageCoreTier.totalMaxPerType(nodeA)
                .add(StorageCoreTier.totalMaxPerType(nodeB));
        assertEquals(expectedTypes, ch.storage().maxTypes());
        assertEquals(expectedPer, ch.storage().maxPerType());

        // Insert 5,000,000 iron ingots (far above base 16,384)
        BigInteger fiveMillion = BigInteger.valueOf(5_000_000L);
        assertEquals(fiveMillion, ch.storage().insertItem("minecraft:iron_ingot", fiveMillion, false));

        // Remove BOTH core nodes (e.g. player breaks both Quantum Storage Cores)
        reg.removeNodeFromAll("overworld@10,64,10");
        reg.removeNodeFromAll("the_nether@20,70,20");
        assertEquals(0, ch.nodeCount());
        assertEquals(StorageCoreTier.BASE_MAX_TYPES, ch.storage().maxTypes());
        assertEquals(StorageCoreTier.BASE_MAX_PER_TYPE, ch.storage().maxPerType());

        // CRITICAL INVARIANT: existing 5,000,000 items are 100% preserved, NOT deleted!
        assertEquals(fiveMillion, ch.storage().getItemCount("minecraft:iron_ingot"));
        // New inserts of iron_ingot are safely blocked while over reduced capacity
        assertEquals(BigInteger.ZERO, ch.storage().insertItem("minecraft:iron_ingot", BigInteger.TEN, false));
        // Extraction still works cleanly with exact arithmetic!
        assertEquals(BigInteger.valueOf(64L),
                ch.storage().extractItem("minecraft:iron_ingot", BigInteger.valueOf(64L), false));
        assertEquals(fiveMillion.subtract(BigInteger.valueOf(64L)),
                ch.storage().getItemCount("minecraft:iron_ingot"));
    }

    @Test
    void importerAndExporterDirectionsFiltersScalingAndConservationAreExact() {
        // 6 unique facing target offsets
        Set<Offset3> offsets = new HashSet<>();
        for (int d = 0; d < 6; d++) {
            offsets.add(QuantumNetLogic.facingTargetOffset(d));
        }
        assertEquals(6, offsets.size(), "All 6 directions must map to distinct unit vectors");

        // Whitelist vs Blacklist filters
        List<String> empty = List.of();
        assertTrue(QuantumNetLogic.matchesFilter("minecraft:cobblestone", empty, true),
                "Empty blacklist must accept everything");
        assertFalse(QuantumNetLogic.matchesFilter("minecraft:cobblestone", empty, false),
                "Empty whitelist must reject everything");

        List<String> list = List.of("minecraft:iron_ingot", "minecraft:gold_ingot");
        assertTrue(QuantumNetLogic.matchesFilter("minecraft:iron_ingot", list, false));
        assertFalse(QuantumNetLogic.matchesFilter("minecraft:dirt", list, false));
        assertFalse(QuantumNetLogic.matchesFilter("minecraft:iron_ingot", list, true));
        assertTrue(QuantumNetLogic.matchesFilter("minecraft:dirt", list, true));

        // Motor & Battery scaling
        assertEquals(QuantumNetLogic.IO_BASE_INTERVAL_TICKS, QuantumNetLogic.ioIntervalTicks(0, 1));
        assertEquals(QuantumNetLogic.IO_MIN_INTERVAL_TICKS, QuantumNetLogic.ioIntervalTicks(64, 1));
        assertEquals(QuantumNetLogic.IO_BASE_MAX_ITEMS, QuantumNetLogic.ioMaxItemsPerCycle(0));
        assertEquals(QuantumNetLogic.IO_MAX_ITEMS, QuantumNetLogic.ioMaxItemsPerCycle(64));
        assertEquals(QuantumNetLogic.IO_BASE_MAX_FLUID_MB, QuantumNetLogic.ioMaxFluidMbPerCycle(0));
        assertEquals(QuantumNetLogic.IO_MAX_FLUID_MB, QuantumNetLogic.ioMaxFluidMbPerCycle(64));
        assertTrue(QuantumNetLogic.ioEnergyPerOp(64, 1) < QuantumNetLogic.ioEnergyPerOp(0, 1));

        // Strict conservation when target is full or partial
        assertEquals(0, QuantumNetLogic.computeSafeTransfer(64, 256, 0));
        assertEquals(12, QuantumNetLogic.computeSafeTransfer(64, 256, 12));
        assertEquals(16, QuantumNetLogic.computeSafeTransfer(64, 16, 100));
        assertEquals(0, QuantumNetLogic.computeSafeTransfer(-5, 16, 100));
    }

    @Test
    void quantumIoLayoutHasZeroOverlapsAndStaysInsideBounds() {
        List<Rect> rects = new ArrayList<>();
        rects.add(QuantumIoLayout.ENERGY_BAR);
        rects.add(QuantumIoLayout.BTN_CH_PREV);
        rects.add(QuantumIoLayout.CHANNEL_BOX);
        rects.add(QuantumIoLayout.BTN_CH_NEXT);
        rects.add(QuantumIoLayout.BTN_CH_CLEAR);
        rects.add(QuantumIoLayout.FILTER_GROUP);
        rects.add(QuantumIoLayout.BTN_FILTER_MODE);
        rects.add(QuantumIoLayout.BTN_FILTER_CLEAR);
        rects.add(QuantumIoLayout.STATS_BOX);
        rects.add(QuantumIoLayout.MOTOR_SLOT);
        rects.add(QuantumIoLayout.BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            rects.add(QuantumIoLayout.playerSlot(i));
        }
        assertTrue(QuantumIoLayout.ENERGY_BAR.y() >= GuiMetrics.BODY_TOP);
        for (int i = 0; i < rects.size(); i++) {
            Rect a = rects.get(i);
            assertTrue(a.x() >= 0 && a.y() >= 0
                            && a.right() <= QuantumIoLayout.WIDTH && a.bottom() <= QuantumIoLayout.HEIGHT,
                    "quantum_io rect #" + i + " out of bounds: " + a);
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(a.intersects(rects.get(j)),
                        "quantum_io overlap #" + i + " " + a + " vs #" + j + " " + rects.get(j));
            }
        }
        List<Rect> fSlots = new ArrayList<>();
        for (int i = 0; i < QuantumIoLayout.FILTER_COUNT; i++) {
            Rect r = QuantumIoLayout.filterSlot(i);
            assertTrue(r.x() >= QuantumIoLayout.FILTER_GROUP.x()
                    && r.right() <= QuantumIoLayout.FILTER_GROUP.right()
                    && r.y() >= QuantumIoLayout.FILTER_GROUP.y()
                    && r.bottom() <= QuantumIoLayout.FILTER_GROUP.bottom());
            fSlots.add(r);
            assertEquals(i, QuantumIoLayout.filterSlotAt(r.x() + 4, r.y() + 4));
        }
        for (int i = 0; i < fSlots.size(); i++) {
            for (int j = i + 1; j < fSlots.size(); j++) {
                assertFalse(fSlots.get(i).intersects(fSlots.get(j)));
            }
        }
    }

    @Test
    void skillTreeRemains17NodesAndAllPhase96AssetsExist() throws IOException {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must remain intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("quantum_importer", "quantum_exporter")) {
            progress.addXp(id, 200L);
            assertEquals(200L, progress.getXp(id));
            assertTrue(progress.getLevel(id) >= 1);
        }

        Path assets = Path.of("src/main/resources/assets/industrialcore");
        Path data = Path.of("src/main/resources/data/industrialcore");
        String ja = Files.readString(assets.resolve("lang/ja_jp.json"));
        String en = Files.readString(assets.resolve("lang/en_us.json"));
        String pickaxeTag = Files.readString(
                Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));

        for (String id : List.of("quantum_importer", "quantum_exporter")) {
            assertTrue(Files.exists(assets.resolve("blockstates/" + id + ".json")), id + " blockstate");
            assertTrue(Files.exists(assets.resolve("models/block/" + id + ".json")), id + " block model");
            assertTrue(Files.exists(assets.resolve("models/item/" + id + ".json")), id + " item model");
            assertTrue(Files.exists(data.resolve("recipe/" + id + ".json")), id + " recipe");
            assertTrue(Files.exists(data.resolve("loot_table/blocks/" + id + ".json")), id + " loot");
            assertTrue(pickaxeTag.contains("industrialcore:" + id), id + " pickaxe tag");
            assertTrue(ja.contains("\"block.industrialcore." + id + "\""), id + " ja");
            assertTrue(en.contains("\"block.industrialcore." + id + "\""), id + " en");
        }
        assertTrue(Files.exists(assets.resolve("models/item/portable_quantum_terminal.json")));
        assertTrue(Files.exists(assets.resolve("textures/item/portable_quantum_terminal.png")));
        assertTrue(Files.exists(data.resolve("recipe/portable_quantum_terminal.json")));
        assertTrue(ja.contains("\"item.industrialcore.portable_quantum_terminal\""));
        assertTrue(en.contains("\"item.industrialcore.portable_quantum_terminal\""));
    }
}
