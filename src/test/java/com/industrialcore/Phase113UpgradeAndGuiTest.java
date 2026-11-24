package com.industrialcore;

import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.UpgradeDockLayout;
import com.industrialcore.trap.TrapWave2Logic;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 113 verification suite:
 * - Hyperbolic diminishing-returns upgrade curves (Motor, Battery, 6 Specialization formulas)
 * - Machine-level-linked unbounded virtual upgrade cap (Lv.1 -> 4, Lv.99 -> 96, Lv.1000 -> 1000)
 * - Laser turret real-time Yaw/Pitch tracking, shortest-arc wrap, pulse interval, and zero server beam particles
 * - 8 non-processing GUIs freed of repurposed progress bars + Upgrade Dock zero-overlap geometry
 * - Specialization Module assets, recipe, creative tab registration, and dedicated-server safety
 */
class Phase113UpgradeAndGuiTest {
    private static final Path CLIENT_DIR = Path.of("src/main/java/com/industrialcore/client");
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/industrialcore");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void diminishingReturnsCurvesAreMonotonicConcaveAndAsymptoticallyBounded() {
        // Core f(n, k) = n / (n + k): strictly increasing, strictly decreasing marginal gain
        double prev = 0.0D;
        double prevDelta = Double.POSITIVE_INFINITY;
        for (int n = 1; n <= 1000; n++) {
            double cur = UpgradeMath.diminishingFraction(n, 24.0D);
            double delta = cur - prev;
            assertTrue(delta > 0.0D, "diminishingFraction must strictly increase at n=" + n);
            assertTrue(delta < prevDelta, "marginal gain must strictly diminish at n=" + n);
            assertTrue(cur < 1.0D, "diminishingFraction must remain strictly below 1.0 at n=" + n);
            prev = cur;
            prevDelta = delta;
        }
        assertEquals(0.5D, UpgradeMath.diminishingFraction(24, 24.0D), 1.0E-9D);

        // Motor speed: 1.0x -> 3.0x (n=24) -> ~4.22x (n=99) -> <5.0x (n=1000)
        assertEquals(1.0D, UpgradeMath.processingSpeedMultiplier(0), 1.0E-9D);
        assertEquals(3.0D, UpgradeMath.processingSpeedMultiplier(24), 1.0E-9D);
        assertTrue(UpgradeMath.processingSpeedMultiplier(99) > 4.20D && UpgradeMath.processingSpeedMultiplier(99) < 4.25D);
        assertTrue(UpgradeMath.processingSpeedMultiplier(1000) > 4.90D && UpgradeMath.processingSpeedMultiplier(1000) < 5.00D);

        // Battery FE cost: 1.0x -> 0.725x (n=28) -> ~0.571x (n=99) -> >0.45x (n=1000)
        assertEquals(1.0D, UpgradeMath.energyCostMultiplier(0), 1.0E-9D);
        assertEquals(0.725D, UpgradeMath.energyCostMultiplier(28), 1.0E-9D);
        assertTrue(UpgradeMath.energyCostMultiplier(99) > 0.56D && UpgradeMath.energyCostMultiplier(99) < 0.58D);
        assertTrue(UpgradeMath.energyCostMultiplier(1000) > 0.45D && UpgradeMath.energyCostMultiplier(1000) < 0.47D);

        // Specialization formulas across all 6 roles
        assertEquals(1.0D, UpgradeMath.turretDamageMultiplier(0), 1.0E-9D);
        assertEquals(2.25D, UpgradeMath.turretDamageMultiplier(24), 1.0E-9D);
        assertTrue(UpgradeMath.turretDamageMultiplier(1000) < 3.50D);

        assertEquals(0, UpgradeMath.turretRangeBonus(0));
        assertEquals(10, UpgradeMath.turretRangeBonus(24));
        assertTrue(UpgradeMath.turretRangeBonus(1000) <= 20);

        assertEquals(0.0D, UpgradeMath.processingBonusYieldChance(0), 1.0E-9D);
        assertEquals(0.30D, UpgradeMath.processingBonusYieldChance(30), 1.0E-9D);
        assertTrue(UpgradeMath.processingBonusYieldChance(1000) < 0.60D);

        assertEquals(1.0D, UpgradeMath.generatorOutputMultiplier(0), 1.0E-9D);
        assertEquals(2.0D, UpgradeMath.generatorOutputMultiplier(30), 1.0E-9D);
        assertTrue(UpgradeMath.generatorOutputMultiplier(1000) < 3.00D);

        assertEquals(0, UpgradeMath.areaRangeBonus(0));
        assertEquals(8, UpgradeMath.areaRangeBonus(25));
        assertTrue(UpgradeMath.areaRangeBonus(1000) <= 16);

        assertEquals(1.0D, UpgradeMath.throughputMultiplier(0), 1.0E-9D);
        assertEquals(2.5D, UpgradeMath.throughputMultiplier(28), 1.0E-9D);
        assertTrue(UpgradeMath.throughputMultiplier(1000) < 4.00D);
    }

    @Test
    void machineLevelTotalUpgradeCapScalesFrom4To96To1000() {
        assertEquals(4, MachineLevelMath.totalUpgradeLimitForLevel(1));
        assertEquals(50, MachineLevelMath.totalUpgradeLimitForLevel(50));
        assertEquals(96, MachineLevelMath.totalUpgradeLimitForLevel(99));
        assertEquals(97, MachineLevelMath.totalUpgradeLimitForLevel(100));
        assertEquals(1000, MachineLevelMath.totalUpgradeLimitForLevel(1000));
        assertEquals(1000, MachineLevelMath.totalUpgradeLimitForLevel(9999));

        int prev = 0;
        for (int lv = 1; lv <= 1000; lv++) {
            int cap = MachineLevelMath.totalUpgradeLimitForLevel(lv);
            assertTrue(cap >= prev, "totalUpgradeLimitForLevel must be non-decreasing at Lv." + lv);
            prev = cap;
        }

        // Unowned and Creative keep full 1000 cap; Survival owner respects machine level
        assertEquals(1000, MachineLevelMath.effectiveTotalUpgradeLimit(false, 1, false));
        assertEquals(1000, MachineLevelMath.effectiveTotalUpgradeLimit(true, 1, true));
        assertEquals(4, MachineLevelMath.effectiveTotalUpgradeLimit(true, 1, false));
        assertEquals(96, MachineLevelMath.effectiveTotalUpgradeLimit(true, 99, false));
    }

    @Test
    void laserTurretTrackingAnglesShortestArcAndPulseConfigAreAccurate() throws IOException {
        // Horizontal directions -> exact OpenGL Axis.YP yaw where local -Z is barrel forward
        assertEquals(0.0F, LivingFactoryAnimLogic.turretAimYawDeg(0.0D, -10.0D), 1.0E-3F);
        assertEquals(90.0F, LivingFactoryAnimLogic.turretAimYawDeg(-10.0D, 0.0D), 1.0E-3F);
        assertEquals(-90.0F, LivingFactoryAnimLogic.turretAimYawDeg(10.0D, 0.0D), 1.0E-3F);
        assertEquals(180.0F, Math.abs(LivingFactoryAnimLogic.turretAimYawDeg(0.0D, 10.0D)), 1.0E-3F);

        // Elevation angle -> positive pitch up, clamped safely [-55, +65]
        assertEquals(45.0F, LivingFactoryAnimLogic.turretAimPitchDeg(10.0D, 10.0D, 0.0D), 1.0E-2F);
        assertEquals(-45.0F, LivingFactoryAnimLogic.turretAimPitchDeg(0.0D, -10.0D, 10.0D), 1.0E-2F);
        assertTrue(LivingFactoryAnimLogic.turretAimPitchDeg(0.01D, 100.0D, 0.0D) <= 65.0F);
        assertTrue(LivingFactoryAnimLogic.turretAimPitchDeg(0.01D, -100.0D, 0.0D) >= -55.0F);

        // Shortest-arc angle wrap across +/-180 boundary
        float wrapped = LivingFactoryAnimLogic.lerpAngleDeg(175.0F, -175.0F, 0.5F);
        assertTrue(Math.abs(Math.abs(wrapped) - 180.0F) < 1.0E-2F,
                "lerpAngleDeg(175, -175, 0.5) must cross +/-180, got " + wrapped);

        // Rapid pulse firing (base 8t -> 2t with high motors)
        assertEquals(8, TrapWave2Logic.TURRET_BASE_INTERVAL_TICKS);
        assertEquals(8, TrapWave2Logic.turretFireInterval(0, 1));
        assertTrue(TrapWave2Logic.turretFireInterval(64, 1) <= 3);

        // LaserTurretBlockEntity must NOT emit server-side beam particles (3D client pulse beam instead)
        String turretBeSrc = Files.readString(MAIN_JAVA.resolve("blockentity/LaserTurretBlockEntity.java"));
        assertFalse(turretBeSrc.contains("sendParticles("),
                "LaserTurretBlockEntity must render 3D client pulse beam instead of server sendParticles");
    }

    @Test
    void eightNonProcessingScreensRemovedProgressBarsAndAttachUpgradeDock() throws IOException {
        List<String> nonProcessingScreens = List.of(
                "LaserTurretScreen.java",
                "SpawnSuppressorScreen.java",
                "MobFanScreen.java",
                "GrowthAcceleratorScreen.java",
                "ItemVacuumScreen.java",
                "XpCollectorScreen.java",
                "BioFeederScreen.java",
                "RotaryDrillScreen.java"
        );
        for (String name : nonProcessingScreens) {
            String src = Files.readString(CLIENT_DIR.resolve(name));
            assertFalse(src.contains("MachineTheme.progressBar("),
                    name + " must not draw a repurposed processing progressBar");
            assertFalse(src.contains("gui.industrialcore.machine.progress"),
                    name + " must not display processing percent header text");
            assertTrue(src.contains("upgradeDockPos()"),
                    name + " must attach the right-side 3-slot Upgrade & Level Dock");
        }
    }

    @Test
    void upgradeDockGeometryHasZeroOverlapAndSpecializationAssetsExist() throws IOException {
        Rect dock = new Rect(0, 0, UpgradeDockLayout.DOCK_WIDTH, UpgradeDockLayout.DOCK_HEIGHT);
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect(UpgradeDockLayout.PAD_X, UpgradeDockLayout.TITLE_Y, UpgradeDockLayout.DOCK_WIDTH - 12, 8));
        rects.add(new Rect(UpgradeDockLayout.PAD_X, UpgradeDockLayout.LEVEL_TEXT_Y, UpgradeDockLayout.DOCK_WIDTH - 12, 8));
        rects.add(UpgradeDockLayout.XP_BAR);
        rects.add(new Rect(UpgradeDockLayout.PAD_X, UpgradeDockLayout.CAP_TEXT_Y, UpgradeDockLayout.DOCK_WIDTH - 12, 8));
        for (int i = 0; i < 3; i++) {
            Rect row = UpgradeDockLayout.rowRect(i);
            Rect slot = UpgradeDockLayout.slotRect(i);
            assertTrue(slot.x() >= row.x() && slot.y() >= row.y()
                    && slot.right() <= row.right() && slot.bottom() <= row.bottom(),
                    "Dock slot " + i + " must stay inside row " + i);
            rects.add(row);
        }
        for (int i = 0; i < rects.size(); i++) {
            Rect a = rects.get(i);
            assertTrue(a.x() >= dock.x() && a.y() >= dock.y()
                    && a.right() <= dock.right() && a.bottom() <= dock.bottom(),
                    "Dock element escapes panel: " + a);
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(a.intersects(rects.get(j)),
                        "Dock elements overlap: " + a + " vs " + rects.get(j));
            }
        }

        // Specialization Module texture, model, recipe, and dedicated-server safety
        Path tex = RES.resolve("assets/industrialcore/textures/item/specialization_module.png");
        assertTrue(Files.isRegularFile(tex));
        BufferedImage img = ImageIO.read(tex.toFile());
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        assertTrue(Files.isRegularFile(RES.resolve("assets/industrialcore/models/item/specialization_module.json")));
        assertTrue(Files.isRegularFile(RES.resolve("data/industrialcore/recipe/specialization_module.json")));

        List<String> commonFiles = List.of(
                "machine/UpgradeMath.java",
                "machine/VirtualUpgradeBank.java",
                "machine/IUpgradeableMachine.java",
                "level/MachineLevelMath.java",
                "network/UpgradeBankActionPayload.java",
                "network/UpgradeBankSyncPayload.java",
                "blockentity/LaserTurretBlockEntity.java"
        );
        for (String rel : commonFiles) {
            String src = Files.readString(MAIN_JAVA.resolve(rel));
            assertFalse(src.contains("net.minecraft.client."),
                    rel + " must remain dedicated-server safe (no client imports)");
        }
    }
}
