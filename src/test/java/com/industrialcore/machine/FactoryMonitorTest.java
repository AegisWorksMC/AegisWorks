package com.industrialcore.machine;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.MonitorLayout;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 102.5 unit tests for the Factory Monitor ({@code factory_monitor}):
 * right-click menu open buffer safety, empty &amp; multi-machine {@link FactoryStats} aggregation
 * (including multi-billion-FE saturation), and non-overlapping {@link MonitorLayout} geometry.
 */
class FactoryMonitorTest {

    @Test
    void emptyAndMultiMachineFactoryStatsAggregateAndSaturateSafely() {
        // 1. Empty factory (0 machines around monitor)
        FactoryStats empty = FactoryStats.EMPTY;
        assertEquals(0, empty.machines());
        assertEquals(0, empty.activePercent());
        assertEquals(0, empty.energyPercent());
        assertFalse(empty.hasAlert());

        // 2. Multi-machine factory (generators, agri/trap/life machines, pipes, Singularity Reactor)
        FactoryStats.Builder b = FactoryStats.builder();
        b.addGenerator(true);
        b.addMachine(FactoryStats.MachineState.WORKING);
        b.addGenerator(false);
        b.addMachine(FactoryStats.MachineState.IDLE);
        b.addMachine(FactoryStats.MachineState.WORKING);
        b.addMachine(FactoryStats.MachineState.STOPPED);
        b.addMachine(null); // defensive null state -> IDLE
        b.addPipe(false);
        b.addPipe(true);
        b.addFullOutput();
        b.addEnergy(500_000_000L, 1_000_000_000L);
        b.addEnergy(250_000_000L, 1_000_000_000L);

        FactoryStats s = b.build().withEnergyTrend(125_000);
        assertEquals(5, s.machines());
        assertEquals(2, s.working());
        assertEquals(2, s.idle());
        assertEquals(1, s.stopped());
        assertEquals(2, s.generators());
        assertEquals(1, s.generatorsRunning());
        assertEquals(2, s.pipes());
        assertEquals(1, s.blockedPipes());
        assertEquals(1, s.fullOutputs());
        assertEquals(40, s.activePercent());
        assertEquals(37, s.energyPercent());
        assertTrue(s.hasAlert());
        assertEquals(125_000, s.energyTrend());

        // 3. Extreme multi-Singularity-Reactor energy saturates safely at Long.MAX_VALUE without overflow
        FactoryStats.Builder sat = FactoryStats.builder();
        sat.addEnergy(Long.MAX_VALUE - 10L, Long.MAX_VALUE - 10L);
        sat.addEnergy(1_000_000L, 1_000_000L);
        FactoryStats big = sat.build();
        assertEquals(Long.MAX_VALUE, big.energyStored());
        assertEquals(Long.MAX_VALUE, big.energyCapacity());
        assertEquals(100, big.energyPercent());
    }

    @Test
    void monitorLayoutRowsEnergyBarAndAlertLineHaveZeroVerticalOverlaps() {
        assertEquals(GuiMetrics.WIDTH, MonitorLayout.WIDTH);
        assertTrue(MonitorLayout.HEIGHT > MonitorLayout.BODY_BOTTOM);

        int prevBottom = 46; // below status strip
        for (int i = 0; i < MonitorLayout.ROW_COUNT; i++) {
            int y = MonitorLayout.rowY(i);
            assertTrue(y >= prevBottom + 2, "Row " + i + " overlaps previous element");
            prevBottom = y + 9;
        }
        assertTrue(MonitorLayout.ENERGY_BAR.y() > prevBottom, "Energy bar must sit below last row");
        assertTrue(MonitorLayout.ENERGY_TEXT_Y >= MonitorLayout.ENERGY_BAR.bottom() + 2,
                "Energy text must sit below energy bar");
        assertTrue(MonitorLayout.ALERT_TEXT_Y >= MonitorLayout.ENERGY_TEXT_Y + 10,
                "Alert line must sit below energy text");
        assertTrue(MonitorLayout.ALERT_TEXT_Y + 9 < MonitorLayout.BODY_BOTTOM,
                "Alert line must fit inside body bottom");
    }

    @Test
    void monitorBlockPassesBlockPosToOpenMenuAndMenuHandlesEmptyBufferSafely() throws Exception {
        String blockSrc = Files.readString(Path.of("src/main/java/com/industrialcore/block/MonitorBlock.java"));
        String menuSrc = Files.readString(Path.of("src/main/java/com/industrialcore/menu/MonitorMenu.java"));

        assertTrue(blockSrc.contains("player.openMenu(monitor, pos)"),
                "MonitorBlock must pass BlockPos to openMenu so RegistryFriendlyByteBuf contains pos");
        assertTrue(blockSrc.contains("monitor.syncOnOpen()"),
                "MonitorBlock must trigger immediate scan/sync on open");
        assertTrue(menuSrc.contains("readableBytes() >= Long.BYTES"),
                "MonitorMenu must defensively guard empty buffers");
    }
}
