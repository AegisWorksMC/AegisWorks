package com.industrialcore.reactor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.menu.GenerationLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.reactor.ReactorMultiblockLogic.Facing2D;
import com.industrialcore.reactor.ReactorMultiblockLogic.FailReason;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideCell;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideRole;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.PartKind;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import com.industrialcore.reactor.ReactorMultiblockLogic.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 103 pure-Java unit tests for variable-size multiblock reactor validation, failure reasons,
 * performance scaling, overflow safety, 3D guide geometry, GUI non-overlap, and shipped assets.
 */
class ReactorMultiblockTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");

    /** Builds a clean WxHxD reactor grid whose front-center controller sits at (0,0,0) facing NORTH. */
    private static Map<IntPos, PartKind> buildNorthReactor(int w, int h, int d, PartKind ctrlKind,
                                                           int coreCount, int coolantCount,
                                                           boolean addPortOnEastWall) {
        Map<IntPos, PartKind> grid = new HashMap<>();
        int minX = -(w / 2);
        int maxX = minX + w - 1;
        int minY = -(h / 2);
        int maxY = minY + h - 1;
        int minZ = 0;
        int maxZ = d - 1;

        int placedCore = 0;
        int placedCool = 0;
        for (int y = minY; y <= maxY; y++) {
            boolean onY = (y == minY || y == maxY);
            for (int x = minX; x <= maxX; x++) {
                boolean onX = (x == minX || x == maxX);
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onZ = (z == minZ || z == maxZ);
                    int bc = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);
                    PartKind k;
                    if (x == 0 && y == 0 && z == 0) {
                        k = ctrlKind;
                    } else if (bc >= 2) {
                        k = PartKind.CASING;
                    } else if (bc == 1) {
                        if (addPortOnEastWall && x == maxX && y == 0 && z == 1) {
                            k = PartKind.PORT;
                        } else {
                            k = PartKind.GLASS;
                        }
                    } else {
                        if (placedCore < coreCount) {
                            k = PartKind.CORE_CELL;
                            placedCore++;
                        } else if (placedCool < coolantCount) {
                            k = PartKind.COOLANT;
                            placedCool++;
                        } else {
                            k = PartKind.AIR;
                        }
                    }
                    grid.put(new IntPos(x, y, z), k);
                }
            }
        }
        return grid;
    }

    private static ValidationResult runValidate(ReactorType type, Map<IntPos, PartKind> grid,
                                                Facing2D facing) {
        return ReactorMultiblockLogic.validate(
                type, new IntPos(0, 0, 0), facing,
                (x, y, z) -> grid.getOrDefault(new IntPos(x, y, z), PartKind.AIR),
                (x, y, z) -> false);
    }

    @Test
    void minimal3x3x3Nuclear5x5x5AndSingularity7x7x7ValidateCleanly() {
        // 1. Minimal 3x3x3 Nuclear with 1 core, 0 coolant, 1 port
        Map<IntPos, PartKind> g3 = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 1, 0, true);
        ValidationResult r3 = runValidate(ReactorType.NUCLEAR, g3, Facing2D.NORTH);
        assertTrue(r3.formed(), "3x3x3 nuclear must form: " + r3.reason());
        assertEquals(3, r3.width());
        assertEquals(3, r3.height());
        assertEquals(3, r3.depth());
        assertEquals(1, r3.coreCells());
        assertEquals(0, r3.coolantBlocks());
        assertEquals(1, r3.portPositions().size());
        assertEquals(1.0D, ReactorMultiblockLogic.outputMultiplier(r3.coreCells(), r3.coolantBlocks()), 1e-6);

        // 2. Max 5x5x5 Nuclear with 15 cores + 12 coolants -> exact 9.10x output multiplier
        Map<IntPos, PartKind> g5 = buildNorthReactor(5, 5, 5, PartKind.NUCLEAR_CTRL, 15, 12, true);
        ValidationResult r5 = runValidate(ReactorType.NUCLEAR, g5, Facing2D.NORTH);
        assertTrue(r5.formed(), "5x5x5 nuclear must form: " + r5.reason());
        assertEquals(5, r5.width());
        assertEquals(5, r5.height());
        assertEquals(5, r5.depth());
        assertEquals(15, r5.coreCells());
        assertEquals(12, r5.coolantBlocks());
        assertEquals(9.10D, ReactorMultiblockLogic.outputMultiplier(r5.coreCells(), r5.coolantBlocks()), 1e-6);

        // 3. Max 7x7x7 Singularity with 90 cores + 35 coolants -> 46.30x (> 30x!)
        Map<IntPos, PartKind> g7 = buildNorthReactor(7, 7, 7, PartKind.SINGULARITY_CTRL, 90, 35, true);
        ValidationResult r7 = runValidate(ReactorType.SINGULARITY, g7, Facing2D.NORTH);
        assertTrue(r7.formed(), "7x7x7 singularity must form: " + r7.reason());
        assertEquals(7, r7.width());
        assertEquals(7, r7.height());
        assertEquals(7, r7.depth());
        assertEquals(90, r7.coreCells());
        assertEquals(35, r7.coolantBlocks());
        assertTrue(ReactorMultiblockLogic.outputMultiplier(r7.coreCells(), r7.coolantBlocks()) > 30.0D);
    }

    @Test
    void allFailureReasonsAreReportedAccurately() {
        // 1. Nuclear 6x6x6 exceeds 5x5x5 cap -> SIZE_OUT_OF_RANGE
        Map<IntPos, PartKind> g6 = buildNorthReactor(6, 6, 6, PartKind.NUCLEAR_CTRL, 10, 0, false);
        ValidationResult rSize = runValidate(ReactorType.NUCLEAR, g6, Facing2D.NORTH);
        assertFalse(rSize.formed());
        assertEquals(FailReason.SIZE_OUT_OF_RANGE, rSize.reason());

        // 2. Glass on a corner instead of reactor_casing -> INCOMPLETE_FRAME
        Map<IntPos, PartKind> gCorner = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 1, 0, false);
        gCorner.put(new IntPos(-1, -1, 0), PartKind.GLASS);
        ValidationResult rCorner = runValidate(ReactorType.NUCLEAR, gCorner, Facing2D.NORTH);
        assertFalse(rCorner.formed());
        assertEquals(FailReason.INCOMPLETE_FRAME, rCorner.reason());

        // 3. Zero core cells inside -> NO_CORE_CELL
        Map<IntPos, PartKind> gNoCore = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 0, 1, false);
        ValidationResult rNoCore = runValidate(ReactorType.NUCLEAR, gNoCore, Facing2D.NORTH);
        assertFalse(rNoCore.formed());
        assertEquals(FailReason.NO_CORE_CELL, rNoCore.reason());

        // 4. Duplicate controller on back wall -> DUPLICATE_CONTROLLER
        Map<IntPos, PartKind> gDup = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 1, 0, false);
        gDup.put(new IntPos(0, 0, 2), PartKind.NUCLEAR_CTRL);
        ValidationResult rDup = runValidate(ReactorType.NUCLEAR, gDup, Facing2D.NORTH);
        assertFalse(rDup.formed());
        assertEquals(FailReason.DUPLICATE_CONTROLLER, rDup.reason());

        // 5. Invalid block on outer wall -> INVALID_WALL
        Map<IntPos, PartKind> gWall = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 1, 0, false);
        gWall.put(new IntPos(0, 0, 2), PartKind.OTHER);
        ValidationResult rWall = runValidate(ReactorType.NUCLEAR, gWall, Facing2D.NORTH);
        assertFalse(rWall.formed());
        assertEquals(FailReason.INVALID_WALL, rWall.reason());

        // 6. Invalid block in interior -> INVALID_INTERIOR
        Map<IntPos, PartKind> gInt = buildNorthReactor(4, 4, 4, PartKind.NUCLEAR_CTRL, 1, 0, false);
        gInt.put(new IntPos(0, 0, 2), PartKind.OTHER);
        ValidationResult rInt = runValidate(ReactorType.NUCLEAR, gInt, Facing2D.NORTH);
        assertFalse(rInt.formed());
        assertEquals(FailReason.INVALID_INTERIOR, rInt.reason());

        // 7. Overlapping another already-formed reactor -> OVERLAPPING_REACTOR
        Map<IntPos, PartKind> gOvl = buildNorthReactor(3, 3, 3, PartKind.NUCLEAR_CTRL, 1, 0, false);
        ValidationResult rOvl = ReactorMultiblockLogic.validate(
                ReactorType.NUCLEAR, new IntPos(0, 0, 0), Facing2D.NORTH,
                (x, y, z) -> gOvl.getOrDefault(new IntPos(x, y, z), PartKind.AIR),
                (x, y, z) -> x == 1 && y == 1 && z == 2);
        assertFalse(rOvl.formed());
        assertEquals(FailReason.OVERLAPPING_REACTOR, rOvl.reason());
    }

    @Test
    void scalingIsStrictlyMonotonicAndOverflowSafe() {
        double prevOut = 0.0D;
        double prevCap = 0.0D;
        for (int cores = 1; cores <= 125; cores++) {
            double out = ReactorMultiblockLogic.outputMultiplier(cores, 0);
            double cap = ReactorMultiblockLogic.capacityMultiplier(cores);
            assertTrue(out > prevOut, "outputMultiplier must strictly increase with cores");
            assertTrue(cap > prevCap, "capacityMultiplier must strictly increase with cores");
            prevOut = out;
            prevCap = cap;
        }
        double prevEff = 0.0D;
        for (int cool = 0; cool <= 40; cool++) {
            double eff = ReactorMultiblockLogic.burnEfficiencyMultiplier(cool);
            assertTrue(eff >= prevEff && eff <= ReactorMultiblockLogic.MAX_COOLANT_EFFICIENCY + 1e-9);
            prevEff = eff;
        }
        assertEquals(3.0D, ReactorMultiblockLogic.burnEfficiencyMultiplier(100), 1e-6);

        // Extreme values saturate cleanly without negative wrap-around
        assertEquals(ReactorMultiblockLogic.SINGULARITY_MAX_LONG_CAPACITY,
                ReactorMultiblockLogic.scaleLongSaturating(Long.MAX_VALUE / 2L, 50.0D,
                        ReactorMultiblockLogic.SINGULARITY_MAX_LONG_CAPACITY));
        assertEquals(Integer.MAX_VALUE,
                ReactorMultiblockLogic.scaleIntSaturating(Integer.MAX_VALUE, 10.0D, Integer.MAX_VALUE));
    }

    @Test
    void guide3x3x3GeometryAndGuiLayoutsHaveZeroOverlaps() {
        for (Facing2D f : Facing2D.values()) {
            List<GuideCell> cells = ReactorMultiblockLogic.buildMinGuide3x3x3(new IntPos(10, 64, 10), f);
            assertEquals(26, cells.size(), "3x3x3 guide excluding controller must have 26 cells");
            long casings = cells.stream().filter(c -> c.role() == GuideRole.FRAME_CASING).count();
            long walls = cells.stream().filter(c -> c.role() == GuideRole.WALL_GLASS).count();
            long cores = cells.stream().filter(c -> c.role() == GuideRole.INTERIOR_CORE).count();
            assertEquals(ReactorMultiblockLogic.MIN_GUIDE_CASINGS, casings, "3x3x3 frame has 20 edge/corner casings");
            assertEquals(ReactorMultiblockLogic.MIN_GUIDE_WALLS, walls, "3x3x3 excluding controller has 5 wall face panes");
            assertEquals(ReactorMultiblockLogic.MIN_GUIDE_CORES, cores, "3x3x3 interior has 1 center core cell");
            // Verify each guide cell maps to its exact real ghost block id (20 casing / 5 glass / 1 core_cell)
            long idCasings = cells.stream().filter(c -> "reactor_casing".equals(c.expectedBlockId())).count();
            long idGlass = cells.stream().filter(c -> "reactor_glass".equals(c.expectedBlockId())).count();
            long idCore = cells.stream().filter(c -> "reactor_core_cell".equals(c.expectedBlockId())).count();
            assertEquals(20L, idCasings);
            assertEquals(5L, idGlass);
            assertEquals(1L, idCore);
        }

        // Skill Tree Advanced Multiblock Guide banner & modal stay strictly inside panel bounds without overlap
        com.industrialcore.level.SkillTreeLayout.Rect stPanel =
                new com.industrialcore.level.SkillTreeLayout.Rect(0, 0,
                        com.industrialcore.level.SkillTreeLayout.WIDTH,
                        com.industrialcore.level.SkillTreeLayout.HEIGHT);
        com.industrialcore.level.SkillTreeLayout.Rect banner =
                com.industrialcore.level.SkillTreeLayout.INFRA_MULTIBLOCK_BANNER;
        com.industrialcore.level.SkillTreeLayout.Rect modal =
                com.industrialcore.level.SkillTreeLayout.REACTOR_GUIDE_MODAL;
        com.industrialcore.level.SkillTreeLayout.Rect close =
                com.industrialcore.level.SkillTreeLayout.REACTOR_GUIDE_CLOSE;
        assertTrue(banner.x() >= com.industrialcore.level.SkillTreeLayout.GUIDE_VIEWPORT.x()
                && banner.right() <= com.industrialcore.level.SkillTreeLayout.GUIDE_VIEWPORT.right());
        assertTrue(modal.x() >= stPanel.x() && modal.right() <= stPanel.right()
                && modal.y() >= stPanel.y() && modal.bottom() <= stPanel.bottom());
        assertTrue(close.x() >= modal.x() && close.right() <= modal.right()
                && close.y() >= modal.y() && close.bottom() <= modal.bottom());

        // Nuclear guide button in GenerationLayout must not overlap fuel slot, bucket slot, nuclear burn bar, coolant column, or FE column
        Rect btn = GenerationLayout.NUCLEAR_GUIDE_BTN;
        assertFalse(btn.intersects(GenerationLayout.NUCLEAR_BUCKET_SLOT));
        assertFalse(btn.intersects(GenerationLayout.FUEL_SLOT));
        assertFalse(btn.intersects(GenerationLayout.NUCLEAR_BURN_BAR));
        assertFalse(btn.intersects(GenerationLayout.NUCLEAR_COOLANT_COLUMN));
        assertFalse(btn.intersects(GenerationLayout.ENERGY_COLUMN));
        assertTrue(btn.bottom() <= GenerationLayout.BODY_BOTTOM);

        // SingularityReactorLayout multiblock bar & guide button must not overlap each other or slots
        assertFalse(SingularityReactorLayout.MULTIBLOCK_BAR.intersects(SingularityReactorLayout.GUIDE_BUTTON));
        assertFalse(SingularityReactorLayout.MULTIBLOCK_BAR.intersects(SingularityReactorLayout.TELEMETRY_PANEL));
        assertFalse(SingularityReactorLayout.MULTIBLOCK_BAR.intersects(SingularityReactorLayout.MOTOR_SLOT_BOX));
        assertFalse(SingularityReactorLayout.GUIDE_BUTTON.intersects(SingularityReactorLayout.TELEMETRY_PANEL));
        assertTrue(SingularityReactorLayout.GUIDE_BUTTON.bottom() <= SingularityReactorLayout.BODY_BOTTOM);
    }

    @Test
    void allFiveReactorBlocksHaveCompleteAssetsRecipesLootTablesTagsAndTranslations() throws IOException {
        String pickaxe = Files.readString(Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));
        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();

        for (String id : List.of(
                "reactor_casing", "reactor_glass", "reactor_core_cell",
                "reactor_coolant_block", "reactor_port")) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/" + id + ".png")), "Missing PNG: " + id);
            assertTrue(Files.isRegularFile(ASSETS.resolve("blockstates/" + id + ".json")), "Missing blockstate: " + id);
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/" + id + ".json")), "Missing block model: " + id);
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")), "Missing item model: " + id);
            assertTrue(Files.isRegularFile(DATA.resolve("recipe/" + id + ".json")), "Missing recipe: " + id);
            assertTrue(Files.isRegularFile(DATA.resolve("loot_table/blocks/" + id + ".json")), "Missing loot: " + id);
            assertTrue(pickaxe.contains("\"industrialcore:" + id + "\""), "Missing pickaxe tag: " + id);
            assertTrue(ja.has("block.industrialcore." + id), "Missing ja_jp block name: " + id);
            assertTrue(en.has("block.industrialcore." + id), "Missing en_us block name: " + id);
        }
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/reactor_core_cell_lit.png")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/reactor_core_cell_lit.json")));
    }
}
