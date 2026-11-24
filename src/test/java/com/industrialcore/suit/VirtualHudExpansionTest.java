package com.industrialcore.suit;

import com.industrialcore.suit.SquadLogic.SquadCandidate;
import com.industrialcore.suit.SquadLogic.SquadMember;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 100 pure-Java unit tests for:
 * <ul>
 *   <li>3-layer depth parallax multipliers &amp; hard-cap clamping</li>
 *   <li>Tactical proximity squad selection (self exclusion, dimension filter, 96m radius, max 2)</li>
 *   <li>Virtual Hotbar selector glide &amp; non-overlapping EXP/Hotbar vertical layout</li>
 *   <li>Virtual Inventory 150ms unfold easing &amp; background parallax clamping</li>
 * </ul>
 */
class VirtualHudExpansionTest {

    @Test
    void threeLayerDepthParallaxOrdersStrictlyAndRespectsHardCaps() {
        assertTrue(VirtualHudMath.DEPTH_BACK < VirtualHudMath.DEPTH_MID);
        assertTrue(VirtualHudMath.DEPTH_MID < VirtualHudMath.DEPTH_FRONT);

        float base = 6.0F;
        float back = VirtualHudMath.layerOffset(base, VirtualHudMath.DEPTH_BACK, VirtualHudMath.MAX_LAYER_OFFSET_X);
        float mid = VirtualHudMath.layerOffset(base, VirtualHudMath.DEPTH_MID, VirtualHudMath.MAX_LAYER_OFFSET_X);
        float front = VirtualHudMath.layerOffset(base, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X);

        assertEquals(3.6F, back, 1.0E-4F);
        assertEquals(6.0F, mid, 1.0E-4F);
        assertEquals(8.1F, front, 1.0E-4F);
        assertTrue(back < mid && mid < front);

        // Hard cap protects against extreme base values on both signs
        assertEquals(VirtualHudMath.MAX_LAYER_OFFSET_X,
                VirtualHudMath.layerOffset(100.0F, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X),
                1.0E-5F);
        assertEquals(-VirtualHudMath.MAX_LAYER_OFFSET_Y,
                VirtualHudMath.layerOffset(-100.0F, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y),
                1.0E-5F);
    }

    @Test
    void squadSelectionFiltersSelfDimensionRadiusSortsAscendingAndCapsAtTwo() {
        UUID selfId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID near1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID near2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID mid3 = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID farOut = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID otherDim = UUID.fromString("00000000-0000-0000-0000-000000000006");

        List<SquadCandidate> pool = List.of(
                new SquadCandidate(selfId, "Self", "minecraft:overworld", 0, 64, 0, 20, 20, true, 40_000_000L, 40_000_000L),
                new SquadCandidate(otherDim, "NetherMate", "minecraft:the_nether", 2, 64, 0, 20, 20, true, 40_000_000L, 40_000_000L),
                new SquadCandidate(farOut, "FarMate", "minecraft:overworld", 96.5, 64, 0, 20, 20, true, 40_000_000L, 40_000_000L),
                new SquadCandidate(mid3, "ThirdMate", "minecraft:overworld", 40, 64, 0, 18, 20, false, 0L, 0L),
                new SquadCandidate(near2, "AegisTwo", "minecraft:overworld", 18, 64, 0, 15, 20, false, 0L, 0L),
                new SquadCandidate(near1, "AegisOne", "minecraft:overworld", 6, 64, 0, 19, 20, true, 30_000_000L, 40_000_000L)
        );

        List<SquadMember> selected = SquadLogic.selectNearbySquad(
                selfId, "minecraft:overworld", 0, 64, 0, pool);

        assertEquals(2, selected.size(), "Must cap nearby teammates at 2 (max 3 total with self)");
        assertEquals(near1, selected.get(0).uuid(), "Closest player must come first");
        assertEquals("AegisOne", selected.get(0).name());
        assertEquals(0.75D, selected.get(0).energyRatio(), 1.0E-6D);
        assertEquals(0.95F, selected.get(0).hpRatio(), 1.0E-5F);
        assertEquals(6.0D, selected.get(0).distanceTo(0, 64, 0), 1.0E-5D);

        assertEquals(near2, selected.get(1).uuid(), "Second closest player must come second");
        assertFalse(selected.get(1).suitEquipped());
        assertEquals(0.0D, selected.get(1).energyRatio(), 1.0E-6D);
    }

    @Test
    void virtualHotbarSelectorGlidesSmoothlyAndNeverOverlapsExpBar() {
        float pos = 0.0F;
        for (int i = 0; i < 40; i++) {
            float next = VirtualHudMath.stepHotbarSelector(pos, 7, 0.30F);
            assertTrue(next >= pos && next <= 7.0F);
            pos = next;
        }
        assertEquals(7.0F, pos, 1.0E-5F);

        // Clamps out-of-range slots safely
        assertEquals(8.0F, VirtualHudMath.stepHotbarSelector(8.0F, 99, 1.0F), 1.0E-5F);
        assertEquals(0.0F, VirtualHudMath.stepHotbarSelector(0.0F, -5, 1.0F), 1.0E-5F);

        // Non-overlapping EXP bar & Virtual Hotbar across all common GUI heights
        for (int screenH : new int[] {240, 360, 480, 720, 1080}) {
            int hbTop = VirtualHudMath.hotbarTop(screenH);
            int hbBot = hbTop + VirtualHudMath.HOTBAR_HEIGHT;
            int expTop = VirtualHudMath.expBarTop(screenH);
            int expBot = VirtualHudMath.expBarBottom(screenH);

            assertTrue(hbBot <= screenH, "Hotbar fits within screen height " + screenH);
            assertTrue(expTop >= 0, "EXP bar stays inside top of screen " + screenH);
            assertTrue(expBot <= hbTop - VirtualHudMath.EXP_HOTBAR_GAP,
                    "EXP bar must stay strictly above Virtual Hotbar at height " + screenH);
        }
    }

    @Test
    void virtualInventoryUnfoldAnimationAndBackParallaxClampCleanly() {
        assertEquals(0.0D, VirtualHudMath.inventoryOpenProgress(-5L), 1.0E-6D);
        assertEquals(0.0D, VirtualHudMath.inventoryOpenProgress(0L), 1.0E-6D);
        double mid = VirtualHudMath.inventoryOpenProgress(75L);
        assertTrue(mid > 0.5D && mid < 1.0D, "Ease-out cubic midpoint must be > 0.5");
        assertEquals(1.0D, VirtualHudMath.inventoryOpenProgress(VirtualHudMath.INV_OPEN_DURATION_MS), 1.0E-6D);
        assertEquals(1.0D, VirtualHudMath.inventoryOpenProgress(999L), 1.0E-6D);

        // Centered cursor -> 0 parallax; extreme cursor -> clamped to +/- maxOffset
        assertEquals(0.0F,
                VirtualHudMath.inventoryBackParallax(100.0D, 100.0D, 88.0D, VirtualHudMath.INV_MAX_PARALLAX_X),
                1.0E-5F);
        assertEquals(-VirtualHudMath.INV_MAX_PARALLAX_X,
                VirtualHudMath.inventoryBackParallax(9999.0D, 100.0D, 88.0D, VirtualHudMath.INV_MAX_PARALLAX_X),
                1.0E-5F);
        assertEquals(VirtualHudMath.INV_MAX_PARALLAX_Y,
                VirtualHudMath.inventoryBackParallax(-9999.0D, 100.0D, 83.0D, VirtualHudMath.INV_MAX_PARALLAX_Y),
                1.0E-5F);
    }

    @Test
    void phase1025ApexVisualHierarchyAndSelectedItemNameNeverOverlap() {
        // 1. Primary Combat Vitals (Shield & HP) match in height & are at least 2x thicker than slim Food/O2
        assertEquals(VirtualHudMath.SHIELD_BAR_HEIGHT, VirtualHudMath.HP_BAR_HEIGHT);
        assertTrue(VirtualHudMath.SHIELD_BAR_HEIGHT >= 7 && VirtualHudMath.SHIELD_BAR_HEIGHT <= 8);
        assertTrue(VirtualHudMath.HP_BAR_HEIGHT >= VirtualHudMath.FOOD_BAR_HEIGHT * 2,
                "HP bar must be >= 2x Food bar height");
        assertTrue(VirtualHudMath.FOOD_BAR_HEIGHT >= 3 && VirtualHudMath.FOOD_BAR_HEIGHT <= 4);
        assertEquals(VirtualHudMath.FOOD_BAR_HEIGHT, VirtualHudMath.O2_BAR_HEIGHT);

        // 2. Tight grouping between Shield & HP (2px), clear visual separation before Food (> shieldHpGap)
        assertEquals(2, VirtualHudMath.shieldHpGap());
        assertTrue(VirtualHudMath.hpFoodGap() > VirtualHudMath.shieldHpGap(),
                "Gap between HP and Food must exceed gap between Shield and HP");

        // 3. Selected hotbar item name sits strictly >= 8px above EXP bar across all resolutions
        for (int screenH : new int[] {240, 360, 480, 720, 1080}) {
            int expTop = VirtualHudMath.expBarTop(screenH);
            int nameBottom = VirtualHudMath.selectedItemNameBottom(screenH);
            int nameTop = VirtualHudMath.selectedItemNameTop(screenH);
            assertTrue(nameBottom <= expTop - 8,
                    "Selected item name must leave >= 8px gap above EXP bar at screenH=" + screenH);
            assertTrue(nameTop >= 0 && nameTop < nameBottom);
        }

        // 4. Squad teammate cards stack cleanly below Self Card without any overlap
        int selfTop = 100;
        int selfH = VirtualHudMath.SELF_CARD_HEIGHT_O2;
        int mate0Top = VirtualHudMath.squadCardTop(selfTop, selfH, 0);
        int mate1Top = VirtualHudMath.squadCardTop(selfTop, selfH, 1);
        assertTrue(mate0Top >= selfTop + selfH + VirtualHudMath.SQUAD_CARD_GAP);
        assertTrue(mate1Top >= mate0Top + VirtualHudMath.SQUAD_CARD_HEIGHT + VirtualHudMath.SQUAD_CARD_GAP);

        // 5. Item name fade alpha envelope
        assertEquals(1.0F, VirtualHudMath.selectedItemNameAlpha(0L), 1e-5F);
        assertEquals(1.0F, VirtualHudMath.selectedItemNameAlpha(VirtualHudMath.ITEM_NAME_HOLD_MS), 1e-5F);
        assertEquals(0.0F, VirtualHudMath.selectedItemNameAlpha(
                VirtualHudMath.ITEM_NAME_HOLD_MS + VirtualHudMath.ITEM_NAME_FADE_MS), 1e-5F);
    }

    @Test
    void bottomRightWeaponTacticalCardClearsCenterHotbarExpAndRightColumnAcrossAllResolutions() {
        int[][] resolutions = {
                {427, 240},   // 854x480 @ guiScale 2
                {640, 360},   // 1280x720 @ guiScale 2
                {960, 540},   // 1920x1080 @ guiScale 2
                {1920, 1080}  // 1920x1080 @ guiScale 1
        };
        for (int[] res : resolutions) {
            int sw = res[0];
            int sh = res[1];
            VirtualHudMath.HudRect center = VirtualHudMath.crosshairSafeZone(sw, sh);
            VirtualHudMath.HudRect hotbar = VirtualHudMath.hotbarRect(sw, sh);
            VirtualHudMath.HudRect expBar = VirtualHudMath.expBarRect(sw, sh);

            for (boolean isLaser : new boolean[] {true, false}) {
                VirtualHudMath.HudRect weapon = VirtualHudMath.weaponCardRect(sw, sh, isLaser);
                VirtualHudMath.HudRect mods = VirtualHudMath.rightModulesRect(sw, sh, true, isLaser);
                VirtualHudMath.HudRect scan = VirtualHudMath.rightScanRect(sw, sh, true, isLaser);

                // Strictly inside screen bounds
                assertTrue(weapon.x() >= 0 && weapon.right() <= sw && weapon.y() >= 0 && weapon.bottom() <= sh,
                        "Weapon card out of bounds at " + sw + "x" + sh);
                assertTrue(mods.y() >= 0 && scan.y() >= 0, "Right stack out of top bounds at " + sw + "x" + sh);

                // Strictly clear of center crosshair, virtual hotbar, and EXP bar!
                assertFalse(weapon.overlaps(center), "Weapon card overlaps center crosshair at " + sw + "x" + sh);
                assertFalse(weapon.overlaps(hotbar), "Weapon card overlaps Virtual Hotbar at " + sw + "x" + sh);
                assertFalse(weapon.overlaps(expBar), "Weapon card overlaps EXP bar at " + sw + "x" + sh);

                // Strictly non-overlapping vertical stacking in the right column
                assertFalse(weapon.overlaps(mods), "Weapon card overlaps Active Modules at " + sw + "x" + sh);
                assertFalse(mods.overlaps(scan), "Active Modules overlaps Target Scan at " + sw + "x" + sh);
                assertFalse(weapon.overlaps(scan), "Weapon card overlaps Target Scan at " + sw + "x" + sh);
                assertTrue(mods.bottom() <= weapon.y() - VirtualHudMath.RIGHT_PANEL_GAP);
                assertTrue(scan.bottom() <= mods.y() - VirtualHudMath.RIGHT_PANEL_GAP);
            }
        }
    }

    @Test
    void pulseLaserRightCrosshairHeatArcStaysRightOfCenterWithoutOverlap() {
        int cx = 320;
        int cy = 180;
        float r = VirtualHudMath.LASER_ARC_RADIUS;
        assertTrue(r >= 14.0F && r <= 16.0F);

        float prevY = Float.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float t = i / 20.0F;
            VirtualHudMath.ArcPoint pt = VirtualHudMath.laserHeatArcPoint(cx, cy, r, t);
            // Strictly right of crosshair center and never overlapping center pixel
            assertTrue(pt.x() > cx + 5.0F, "Arc point must sit strictly right of crosshair at t=" + t);
            double dist = Math.hypot(pt.x() - cx, pt.y() - cy);
            assertEquals(r, dist, 0.05D, "Arc point must stay on semicircle radius");
            // Sweeps monotonically upward from bottom (+65°) to top (-65°)
            assertTrue(pt.y() < prevY, "Heat arc must sweep smoothly upward from bottom to top");
            prevY = pt.y();
        }
        var bottom = VirtualHudMath.laserHeatArcPoint(cx, cy, r, 0.0F);
        var mid = VirtualHudMath.laserHeatArcPoint(cx, cy, r, 0.5F);
        var top = VirtualHudMath.laserHeatArcPoint(cx, cy, r, 1.0F);
        assertTrue(bottom.y() > cy && top.y() < cy);
        assertEquals(cx + r, mid.x(), 0.05F);
        assertEquals(cy, mid.y(), 0.05F);

        // Dynamic color progression: low cyan != mid amber != high red, overheat flashes
        int cLow = VirtualHudMath.laserHeatArcColor(0.15F, false, 0L);
        int cMid = VirtualHudMath.laserHeatArcColor(0.75F, false, 0L);
        int cHigh = VirtualHudMath.laserHeatArcColor(0.98F, false, 0L);
        assertTrue(cLow != cMid && cMid != cHigh);
        int ovBright = VirtualHudMath.laserHeatArcColor(1.0F, true, 0L);
        int ovDim = VirtualHudMath.laserHeatArcColor(1.0F, true, 120L);
        assertTrue(ovBright != ovDim, "Overheated arc must flash dynamically");
    }
}
