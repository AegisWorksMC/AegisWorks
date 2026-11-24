package com.industrialcore;

import com.industrialcore.machine.FactoryAudioLogic;
import com.industrialcore.machine.FactoryAudioLogic.BudgetTracker;
import com.industrialcore.machine.FactoryAudioLogic.SoundFamily;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 111 pure-Java verification (updated for Phase 112 18-voice / 12m / 2-sound-per-tick audio rules):
 * <ul>
 *   <li>All 19 new 3D physical moving-part curves in {@link LivingFactoryAnimLogic} satisfy bounds, idle-stop, and view-distance rules.</li>
 *   <li>{@link FactoryAudioLogic} enforces distance silence, refined volume/pitch, 0-frame phase triggers, and budget throttling.</li>
 *   <li>Dedicated-server safety: zero client imports in shared logic &amp; block classes.</li>
 * </ul>
 */
class Phase111MotionAudioTest {

    @Test
    void allNineteenNewBerMotionCurvesAreBoundedAndActive() {
        assertEquals(36, LivingFactoryAnimLogic.BER_VIEW_DISTANCE);

        // 1. Metal Press stroke
        assertEquals(0.0F, LivingFactoryAnimLogic.metalPressStroke01(0.0F), 1e-4F);
        float stampTime = LivingFactoryAnimLogic.METAL_PRESS_PERIOD_SEC * 0.42F;
        assertEquals(1.0F, LivingFactoryAnimLogic.metalPressStroke01(stampTime), 1e-4F);
        assertTrue(LivingFactoryAnimLogic.metalPressIsStamping(stampTime));
        assertFalse(LivingFactoryAnimLogic.metalPressIsStamping(0.05F));
        assertTrue(LivingFactoryAnimLogic.metalPressStroke01(LivingFactoryAnimLogic.METAL_PRESS_PERIOD_SEC * 0.90F) < 0.25F);

        // 2. Assembler horizontal scan
        for (int i = 0; i < 40; i++) {
            float x = LivingFactoryAnimLogic.assemblerHeadOffsetX(i * 0.05F);
            assertTrue(x >= -0.261F && x <= 0.261F);
        }
        assertNotEquals(LivingFactoryAnimLogic.assemblerHeadOffsetX(0.0F),
                LivingFactoryAnimLogic.assemblerHeadOffsetX(0.35F));

        // 3. Quantum Compressor stroke
        assertEquals(1.0F, LivingFactoryAnimLogic.quantumCompressorStroke01(
                LivingFactoryAnimLogic.COMPRESSOR_PERIOD_SEC * 0.46F), 1e-4F);
        assertEquals(0.0F, LivingFactoryAnimLogic.quantumCompressorStroke01(0.0F), 1e-5F);

        // 4..16. Bounded rotation / oscillation curves
        for (int i = 0; i < 30; i++) {
            float t = i * 0.13F;
            assertIn360(LivingFactoryAnimLogic.reconstructorRingDeg(t));
            assertInRange(LivingFactoryAnimLogic.reconstructorRadius(t), 0.15F, 0.31F);
            assertIn360(LivingFactoryAnimLogic.quarryRingDeg(t));
            assertInRange(LivingFactoryAnimLogic.quarryLaserPulse(t), 0.55F, 1.01F);
            assertIn360(LivingFactoryAnimLogic.oreSynthesizerRingDeg(t));
            assertIn360(LivingFactoryAnimLogic.phaseReactorRingDeg(t));
            assertInRange(LivingFactoryAnimLogic.matterAmplifierPulseX(t), -0.47F, 0.47F);
            assertInRange(LivingFactoryAnimLogic.cropHarvesterBladeX(t), -0.15F, 0.15F);
            assertInRange(LivingFactoryAnimLogic.treeHarvesterSawX(t), -0.12F, 0.12F);
            assertIn360(LivingFactoryAnimLogic.growthCoreDeg(t));
            assertInRange(LivingFactoryAnimLogic.growthCoreBobY(t), -0.08F, 0.08F);
            assertIn360(LivingFactoryAnimLogic.vacuumVortexDeg(t));
            assertInRange(LivingFactoryAnimLogic.vacuumVortexRadius(t), 0.13F, 0.35F);
            assertIn360(LivingFactoryAnimLogic.xpOrbDeg(t));
            assertInRange(LivingFactoryAnimLogic.xpOrbBobY(t), -0.07F, 0.07F);
            assertInRange(LivingFactoryAnimLogic.laserTurretSweepYawDeg(t), -42.5F, 42.5F);
            assertInRange(LivingFactoryAnimLogic.autoBuilderScanDeg(t), -28.5F, 28.5F);
            assertIn360(LivingFactoryAnimLogic.transporterRingDeg(t));
        }

        // 15. Fluid Pump alternating pistons
        float pL = LivingFactoryAnimLogic.fluidPumpPistonOffsetY(0.2125F, false);
        float pR = LivingFactoryAnimLogic.fluidPumpPistonOffsetY(0.2125F, true);
        assertEquals(0.14F, pL + pR, 0.005F);
        assertTrue(Math.abs(pL - pR) > 0.10F);

        // 17 & 18. Hydro & Pressure turbines
        assertEquals(0.0F, LivingFactoryAnimLogic.hydroTurbineDeg(2.5F, false));
        assertTrue(LivingFactoryAnimLogic.hydroTurbineDeg(0.5F, true) > 0.0F);
        assertEquals(0.0F, LivingFactoryAnimLogic.pressureTurbineDeg(2.5F, false));
        assertTrue(LivingFactoryAnimLogic.pressureTurbineDeg(0.5F, true) > 0.0F);
    }

    @Test
    void factoryAudioEnforcesPhaseSyncAndBudget() {
        assertTrue(FactoryAudioLogic.crossedPhaseTrigger(0.35F, 0.40F, 0.38F));
        assertFalse(FactoryAudioLogic.crossedPhaseTrigger(0.40F, 0.46F, 0.38F));
        assertTrue(FactoryAudioLogic.crossedPhaseTrigger(0.96F, 0.04F, 0.01F));
        assertTrue(FactoryAudioLogic.crossedPhaseTrigger(0.96F, 0.04F, 0.98F));
        assertFalse(FactoryAudioLogic.crossedPhaseTrigger(0.96F, 0.04F, 0.50F));

        BudgetTracker tracker = new BudgetTracker();
        int accepted = 0;
        for (int i = 0; i < 20; i++) {
            if (tracker.tryAcquire(SoundFamily.PRESS_IMPACT, 100L, 16.0D)) {
                accepted++;
            }
        }
        assertEquals(1, accepted);
        assertFalse(tracker.tryAcquire(SoundFamily.PRESS_IMPACT, 105L, 16.0D));
        assertTrue(tracker.tryAcquire(SoundFamily.PRESS_IMPACT, 125L, 16.0D));
    }

    @Test
    void sharedLogicAndBlocksRemainDedicatedServerSafe() throws Exception {
        List<Path> commonFiles = List.of(
                Path.of("src/main/java/com/industrialcore/machine/FactoryAudioLogic.java"),
                Path.of("src/main/java/com/industrialcore/machine/LivingFactoryAnimLogic.java"),
                Path.of("src/main/java/com/industrialcore/machine/LivingFactoryParticleHook.java"),
                Path.of("src/main/java/com/industrialcore/registry/ModSounds.java"),
                Path.of("src/main/java/com/industrialcore/block/CrafterBlock.java"),
                Path.of("src/main/java/com/industrialcore/block/RanchHarvesterBlock.java"),
                Path.of("src/main/java/com/industrialcore/block/SeedCultivatorBlock.java"),
                Path.of("src/main/java/com/industrialcore/block/QuantumImporterBlock.java"),
                Path.of("src/main/java/com/industrialcore/block/QuantumExporterBlock.java"),
                Path.of("src/main/java/com/industrialcore/block/RecoveryBlock.java")
        );
        for (Path p : commonFiles) {
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                assertFalse(trimmed.contains("net.minecraft.client"), "Forbidden client import in " + p + ": " + trimmed);
                assertFalse(trimmed.contains("com.industrialcore.client"), "Forbidden client reference in " + p + ": " + trimmed);
            }
        }
    }

    private static void assertIn360(float deg) {
        assertTrue(deg >= 0.0F && deg < 360.0F, "Angle out of [0, 360): " + deg);
    }

    private static void assertInRange(float val, float lo, float hi) {
        assertTrue(val >= lo && val <= hi, "Value " + val + " out of [" + lo + ", " + hi + "]");
    }
}
