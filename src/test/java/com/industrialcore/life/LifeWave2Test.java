package com.industrialcore.life;

import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.life.LifeWave2Logic.ChunkCoord;
import com.industrialcore.life.LifeWave2Logic.WarpEntry;
import com.industrialcore.life.LifeWave2Logic.WarpRegistry;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for Phase 95 Branch D (Building &amp; Life) wave 2:
 * {@code chunk_loader}, {@code climate_controller}, and {@code warp_pad}.
 */
class LifeWave2Test {
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
    void allThreeLifeWave2LayoutsHaveZeroOverlapsAndStayInsidePanelBounds() {
        // 1. Chunk Loader
        List<Rect> cl = new ArrayList<>();
        cl.add(LifeWave2Layout.ENERGY_BAR);
        cl.add(LifeWave2Layout.CHUNK_BTN_TOGGLE);
        cl.add(LifeWave2Layout.CHUNK_BTN_R_DEC);
        cl.add(LifeWave2Layout.CHUNK_BTN_R_INC);
        cl.add(LifeWave2Layout.CHUNK_STATS_BOX);
        cl.add(LifeWave2Layout.CHUNK_MOTOR_SLOT);
        cl.add(LifeWave2Layout.CHUNK_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            cl.add(LifeWave2Layout.chunkPlayerSlot(i));
        }
        assertInsideAndNonOverlapping("chunk_loader", LifeWave2Layout.WIDTH, LifeWave2Layout.CHUNK_HEIGHT, cl);

        // 2. Climate Controller
        List<Rect> cc = new ArrayList<>();
        cc.add(LifeWave2Layout.ENERGY_BAR);
        cc.add(LifeWave2Layout.CLIMATE_BTN_CLEAR);
        cc.add(LifeWave2Layout.CLIMATE_BTN_RAIN);
        cc.add(LifeWave2Layout.CLIMATE_BTN_THUNDER);
        cc.add(LifeWave2Layout.CLIMATE_BTN_DAY);
        cc.add(LifeWave2Layout.CLIMATE_BTN_NIGHT);
        cc.add(LifeWave2Layout.CLIMATE_BTN_LOCK_CLEAR);
        cc.add(LifeWave2Layout.CLIMATE_BTN_LOCK_DAY);
        cc.add(LifeWave2Layout.CLIMATE_INFO_BOX);
        cc.add(LifeWave2Layout.CLIMATE_MOTOR_SLOT);
        cc.add(LifeWave2Layout.CLIMATE_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            cc.add(LifeWave2Layout.climatePlayerSlot(i));
        }
        assertInsideAndNonOverlapping("climate_controller", LifeWave2Layout.WIDTH, LifeWave2Layout.CLIMATE_HEIGHT, cc);

        // 3. Warp Pad
        List<Rect> wp = new ArrayList<>();
        wp.add(LifeWave2Layout.ENERGY_BAR);
        wp.add(LifeWave2Layout.WARP_NAME_BOX);
        wp.add(LifeWave2Layout.WARP_BTN_SAVE);
        wp.add(LifeWave2Layout.WARP_BTN_PUBLIC);
        wp.add(LifeWave2Layout.WARP_LIST_BOX);
        wp.add(LifeWave2Layout.WARP_BTN_SCROLL_UP);
        wp.add(LifeWave2Layout.WARP_BTN_SCROLL_DOWN);
        wp.add(LifeWave2Layout.WARP_BTN_WARP);
        wp.add(LifeWave2Layout.WARP_MOTOR_SLOT);
        wp.add(LifeWave2Layout.WARP_BATTERY_SLOT);
        for (int i = 0; i < 36; i++) {
            wp.add(LifeWave2Layout.warpPlayerSlot(i));
        }
        assertInsideAndNonOverlapping("warp_pad", LifeWave2Layout.WIDTH, LifeWave2Layout.WARP_HEIGHT, wp);

        List<Rect> rows = new ArrayList<>();
        for (int i = 0; i < LifeWave2Layout.WARP_VISIBLE_ROWS; i++) {
            Rect row = LifeWave2Layout.warpListRow(i);
            assertTrue(row.x() >= LifeWave2Layout.WARP_LIST_BOX.x()
                    && row.right() <= LifeWave2Layout.WARP_LIST_BOX.right()
                    && row.y() >= LifeWave2Layout.WARP_LIST_BOX.y()
                    && row.bottom() <= LifeWave2Layout.WARP_LIST_BOX.bottom(),
                    "warp row #" + i + " outside list box");
            rows.add(row);
        }
        assertInsideAndNonOverlapping("warp_pad_rows", LifeWave2Layout.WIDTH, LifeWave2Layout.WARP_HEIGHT, rows);

        // Body starts strictly below GuiMetrics.BODY_TOP (header + skill button clear)
        assertTrue(LifeWave2Layout.ENERGY_BAR.y() >= GuiMetrics.BODY_TOP);
    }

    @Test
    void chunkLoaderRadiusMotorUnlocksCoordinatesAndEnergyDiscountAreExact() {
        assertEquals(0, LifeWave2Logic.maxUnlockedChunkRadius(0));
        assertEquals(0, LifeWave2Logic.maxUnlockedChunkRadius(3));
        assertEquals(1, LifeWave2Logic.maxUnlockedChunkRadius(4));
        assertEquals(1, LifeWave2Logic.maxUnlockedChunkRadius(15));
        assertEquals(2, LifeWave2Logic.maxUnlockedChunkRadius(16));
        assertEquals(2, LifeWave2Logic.maxUnlockedChunkRadius(64));

        assertEquals(0, LifeWave2Logic.effectiveChunkRadius(2, 0));
        assertEquals(1, LifeWave2Logic.effectiveChunkRadius(2, 4));
        assertEquals(2, LifeWave2Logic.effectiveChunkRadius(2, 16));
        assertEquals(0, LifeWave2Logic.effectiveChunkRadius(-1, 64));

        assertEquals(1, LifeWave2Logic.chunkCountForRadius(0));
        assertEquals(9, LifeWave2Logic.chunkCountForRadius(1));
        assertEquals(25, LifeWave2Logic.chunkCountForRadius(2));

        List<ChunkCoord> r1 = LifeWave2Logic.chunkCoords(10, -5, 1);
        assertEquals(9, r1.size());
        assertEquals(9, new HashSet<>(r1).size(), "all chunk coords must be distinct");
        assertTrue(r1.contains(new ChunkCoord(10, -5)));
        assertTrue(r1.contains(new ChunkCoord(9, -6)));
        assertTrue(r1.contains(new ChunkCoord(11, -4)));

        List<ChunkCoord> r2 = LifeWave2Logic.chunkCoords(0, 0, 2);
        assertEquals(25, r2.size());
        assertEquals(25, new HashSet<>(r2).size());

        int base9 = LifeWave2Logic.chunkEnergyPerTick(9, 0, 1);
        int disc9 = LifeWave2Logic.chunkEnergyPerTick(9, 64, 1);
        assertEquals(9 * LifeWave2Logic.CHUNK_BASE_FE_PER_CHUNK, base9);
        assertTrue(disc9 < base9 && disc9 >= 9, "64 batteries must reduce FE/t without zeroing it");
        assertEquals(0, LifeWave2Logic.chunkEnergyPerTick(0, 0, 1));
    }

    @Test
    void climateControllerNextDayTimeNeverRewindsAndScalingIsMonotonic() {
        // Morning (1000) from various points of the day: always strictly forward, exact tick mod 24000
        long[] samples = {0L, 500L, 1000L, 1001L, 12999L, 13000L, 15000L, 23999L, 48500L, 120000L};
        for (long cur : samples) {
            long nextDay = LifeWave2Logic.nextDayTime(cur, LifeWave2Logic.DAY_TIME_MORNING);
            assertTrue(nextDay > cur, "nextDayTime(morning) must strictly advance from " + cur);
            assertEquals(LifeWave2Logic.DAY_TIME_MORNING, Math.floorMod(nextDay, LifeWave2Logic.TICKS_PER_DAY));
            assertTrue(nextDay - cur <= LifeWave2Logic.TICKS_PER_DAY, "must not skip more than 1 full day");

            long nextNight = LifeWave2Logic.nextDayTime(cur, LifeWave2Logic.DAY_TIME_NIGHT);
            assertTrue(nextNight > cur, "nextDayTime(night) must strictly advance from " + cur);
            assertEquals(LifeWave2Logic.DAY_TIME_NIGHT, Math.floorMod(nextNight, LifeWave2Logic.TICKS_PER_DAY));
            assertTrue(nextNight - cur <= LifeWave2Logic.TICKS_PER_DAY);
        }
        // Specific sanity: night 15000 -> day 1 morning 25000 (day count never rewinds)
        assertEquals(25_000L, LifeWave2Logic.nextDayTime(15_000L, LifeWave2Logic.DAY_TIME_MORNING));

        int wCost0 = LifeWave2Logic.climateActionCost(true, 0, 1);
        int wCost64 = LifeWave2Logic.climateActionCost(true, 64, 1);
        assertEquals(LifeWave2Logic.CLIMATE_WEATHER_BASE_COST, wCost0);
        assertTrue(wCost64 < wCost0);

        int hold0 = LifeWave2Logic.climateHoldCostPerTick(2, 0, 0, 1);
        int hold64 = LifeWave2Logic.climateHoldCostPerTick(2, 64, 64, 1);
        assertTrue(hold64 < hold0 && hold64 > 0);
        assertEquals(0, LifeWave2Logic.climateHoldCostPerTick(0, 0, 0, 1));

        int cd0 = LifeWave2Logic.climateCooldownTicks(0, 1);
        int cd64 = LifeWave2Logic.climateCooldownTicks(64, 1);
        assertEquals(LifeWave2Logic.CLIMATE_BASE_COOLDOWN_TICKS, cd0);
        assertEquals(LifeWave2Logic.CLIMATE_MIN_COOLDOWN_TICKS, cd64);
    }

    @Test
    void warpPadCostScalingAndRegistryPermissionsAreExact() {
        int near = LifeWave2Logic.warpCostFe(10.0, false, 0, 0, 1);
        int far = LifeWave2Logic.warpCostFe(500.0, false, 0, 0, 1);
        int crossDim = LifeWave2Logic.warpCostFe(10.0, true, 0, 0, 1);
        assertTrue(far > near, "longer distance must cost more FE");
        assertTrue(crossDim > near, "cross-dimension warp must add surcharge");

        int withMotors = LifeWave2Logic.warpCostFe(500.0, true, 64, 0, 1);
        int withBoth = LifeWave2Logic.warpCostFe(500.0, true, 64, 64, 1);
        int rawBoth = LifeWave2Logic.warpCostFe(500.0, true, 0, 0, 1);
        assertTrue(withMotors < rawBoth && withMotors <= rawBoth * 0.45,
                "64 motors must cut warp FE cost by ~60%");
        assertTrue(withBoth < withMotors && withBoth >= 100,
                "64 batteries must apply additional discount");

        assertEquals(LifeWave2Logic.WARP_BASE_COOLDOWN_TICKS, LifeWave2Logic.warpCooldownTicks(0, 1));
        assertEquals(LifeWave2Logic.WARP_MIN_COOLDOWN_TICKS, LifeWave2Logic.warpCooldownTicks(64, 1));

        // Registry: register, rename, public/private visibility, removal
        WarpRegistry reg = new WarpRegistry();
        String idA = LifeWave2Logic.makePadId("minecraft:overworld", 0, 64, 0);
        String idB = LifeWave2Logic.makePadId("minecraft:overworld", 100, 64, 0);
        String idC = LifeWave2Logic.makePadId("minecraft:the_nether", 12, 70, -8);

        reg.registerOrUpdate(new WarpEntry(idA, "minecraft:overworld", 0, 64, 0, "拠点A", "owner-1", true));
        reg.registerOrUpdate(new WarpEntry(idB, "minecraft:overworld", 100, 64, 0, "秘密基地", "owner-1", false));
        reg.registerOrUpdate(new WarpEntry(idC, "minecraft:the_nether", 12, 70, -8, "ネザー拠点", "owner-2", true));
        assertEquals(3, reg.size());

        // Owner-1 standing at A sees both B (private owned) and C (public), never A itself
        List<WarpEntry> owner1Targets = reg.visibleTargetsFor(idA, "owner-1");
        assertEquals(2, owner1Targets.size());
        assertEquals(Set.of(idB, idC), Set.of(owner1Targets.get(0).padId(), owner1Targets.get(1).padId()));

        // Owner-2 standing at A sees ONLY C (cannot see owner-1's private pad B)
        List<WarpEntry> owner2Targets = reg.visibleTargetsFor(idA, "owner-2");
        assertEquals(1, owner2Targets.size());
        assertEquals(idC, owner2Targets.get(0).padId());
        assertFalse(LifeWave2Logic.canAccessPad(reg.get(idB), "owner-2"));
        assertTrue(LifeWave2Logic.canAccessPad(reg.get(idB), "owner-1"));

        // Toggle B public -> owner-2 can now see B
        assertTrue(reg.setPublic(idB, true));
        assertEquals(2, reg.visibleTargetsFor(idA, "owner-2").size());

        // Rename & blank fallback
        assertTrue(reg.rename(idB, "新・拠点B"));
        assertEquals("新・拠点B", reg.get(idB).name());
        assertTrue(reg.rename(idB, "   "));
        assertEquals("ワープ #100,64,0", reg.get(idB).name());

        // Remove on break
        assertTrue(reg.remove(idB));
        assertNull(reg.get(idB));
        assertNotNull(reg.get(idA));
        assertEquals(2, reg.size());
    }

    @Test
    void machineProgressTracksAllThreeMachinesWithoutAltering17NodeSkillTree() {
        assertEquals(38, MachineSkillTree.nodes().size(), "Unified 4-branch 38-node Skill Tree must stay intact");
        PlayerMachineProgress progress = new PlayerMachineProgress();
        for (String id : List.of("chunk_loader", "climate_controller", "warp_pad")) {
            progress.addXp(id, 250L);
            assertEquals(250L, progress.getXp(id), id + " xp must accumulate");
            assertTrue(progress.getLevel(id) >= 1, id + " level must be valid");
        }
    }

    @Test
    void allPhase95AssetsRecipesLootTablesAndLocalizationsExist() throws IOException {
        Path assets = Path.of("src/main/resources/assets/industrialcore");
        Path data = Path.of("src/main/resources/data/industrialcore");
        String ja = Files.readString(assets.resolve("lang/ja_jp.json"));
        String en = Files.readString(assets.resolve("lang/en_us.json"));
        String pickaxeTag = Files.readString(
                Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));

        for (String id : List.of("chunk_loader", "climate_controller", "warp_pad")) {
            assertTrue(Files.exists(assets.resolve("blockstates/" + id + ".json")), id + " blockstate");
            assertTrue(Files.exists(assets.resolve("models/block/" + id + ".json")), id + " block model");
            assertTrue(Files.exists(assets.resolve("models/item/" + id + ".json")), id + " item model");
            assertTrue(Files.exists(data.resolve("recipe/" + id + ".json")), id + " recipe");
            assertTrue(Files.exists(data.resolve("loot_table/blocks/" + id + ".json")), id + " loot_table");
            assertTrue(pickaxeTag.contains("industrialcore:" + id), id + " pickaxe tag");
            assertTrue(ja.contains("\"block.industrialcore." + id + "\""), id + " ja_jp block name");
            assertTrue(en.contains("\"block.industrialcore." + id + "\""), id + " en_us block name");
        }
    }
}
