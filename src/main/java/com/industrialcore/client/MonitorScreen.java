package com.industrialcore.client;

import com.industrialcore.machine.FactoryStats;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.MonitorLayout;
import com.industrialcore.menu.MonitorMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * Factory monitor panel (Phase 82).
 *
 * <p>Six label/value rows, one FE gauge and one alert line. All coordinates come from
 * {@link MonitorLayout}, the text is clipped to the content width so a long Japanese value cannot
 * run out of the panel, and the whole panel scales through {@link AdaptiveContainerScreen} like
 * every other machine.</p>
 */
public class MonitorScreen extends AdaptiveContainerScreen<MonitorMenu> {
    private static final int LABEL_COLOR = MachineTheme.TEXT;
    private static final int VALUE_COLOR = MachineTheme.CYAN_BRIGHT;
    private static final int MUTED = MachineTheme.MUTED;
    private static final int WARN = MachineTheme.STATUS_WARN;

    public MonitorScreen(MonitorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MonitorLayout.WIDTH;
        this.imageHeight = MonitorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        FactoryStats stats = this.menu.getStats();
        MachineTheme.chrome(graphics, left, top, MonitorLayout.HEIGHT, MonitorLayout.BODY_BOTTOM,
                stats.hasAlert() ? MachineTheme.STATUS_WARN : MachineTheme.CYAN);

        Rect bar = MonitorLayout.ENERGY_BAR;
        long rawStored = Math.max(0L, stats.energyStored());
        long rawCap = Math.max(0L, stats.energyCapacity());
        int barStored;
        int barCap;
        if (rawCap > Integer.MAX_VALUE) {
            barCap = 10_000;
            barStored = (int) Math.min(10_000L, Math.round((double) rawStored / (double) rawCap * 10_000.0D));
        } else {
            barCap = (int) rawCap;
            barStored = (int) Math.min(rawCap, rawStored);
        }
        MachineTheme.energyBar(graphics, left + bar.x(), top + bar.y(), bar.width(), bar.height(),
                barStored, barCap);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        FactoryStats stats = this.menu.getStats();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(stats.hasAlert()
                        ? "gui.industrialcore.monitor.state.alert"
                        : "gui.industrialcore.monitor.state.ok"),
                stats.hasAlert() ? WARN : MachineTheme.STATUS_OK);
        MachineTheme.statusValue(this.font, graphics, 0, 0,
                Component.literal(stats.activePercent() + "%"), VALUE_COLOR);

        row(graphics, 0, "gui.industrialcore.monitor.machines",
                Component.literal(Integer.toString(stats.machines())));
        row(graphics, 1, "gui.industrialcore.monitor.working",
                Component.literal(stats.working() + " (" + stats.activePercent() + "%)"));
        row(graphics, 2, "gui.industrialcore.monitor.idle_stopped",
                Component.literal(stats.idle() + " / " + stats.stopped()));
        row(graphics, 3, "gui.industrialcore.monitor.generators",
                Component.literal(stats.generatorsRunning() + " / " + stats.generators()));
        row(graphics, 4, "gui.industrialcore.monitor.pipes",
                Component.literal(stats.pipes() + " (" + stats.blockedPipes() + ")"));
        row(graphics, 5, "gui.industrialcore.monitor.full_outputs",
                Component.literal(Integer.toString(stats.fullOutputs())));

        String energyLine = Component.translatable("gui.industrialcore.monitor.energy",
                format(stats.energyStored()), format(stats.energyCapacity()),
                trend(stats.energyTrend())).getString();
        graphics.drawString(this.font,
                this.font.plainSubstrByWidth(energyLine, GuiMetrics.CONTENT),
                GuiMetrics.SIDE, MonitorLayout.ENERGY_TEXT_Y, VALUE_COLOR, false);

        Component alert = alertLine(stats);
        graphics.drawString(this.font,
                this.font.plainSubstrByWidth(alert.getString(), GuiMetrics.CONTENT),
                GuiMetrics.SIDE, MonitorLayout.ALERT_TEXT_Y,
                stats.hasAlert() ? WARN : MUTED, false);
    }

    private void row(GuiGraphics graphics, int index, String labelKey, Component value) {
        int y = MonitorLayout.rowY(index);
        // The value is right aligned, so the label only gets the space that is left over; with a
        // long Japanese translation both strings used to be printed over each other.
        String text = this.font.plainSubstrByWidth(value.getString(), 96);
        int valueX = MonitorLayout.VALUE_RIGHT - this.font.width(text);
        int labelRoom = Math.max(0, valueX - 4 - GuiMetrics.SIDE);
        String label = this.font.plainSubstrByWidth(
                Component.translatable(labelKey).getString(), labelRoom);
        graphics.drawString(this.font, label, GuiMetrics.SIDE, y, LABEL_COLOR, false);
        graphics.drawString(this.font, text, valueX, y, VALUE_COLOR, false);
    }

    private Component alertLine(FactoryStats stats) {
        if (!stats.hasAlert()) {
            return Component.translatable("gui.industrialcore.monitor.alerts.none");
        }
        return Component.translatable("gui.industrialcore.monitor.alerts",
                stats.blockedPipes(), stats.fullOutputs());
    }

    private static String trend(int perSecond) {
        return (perSecond >= 0 ? "+" : "") + format(perSecond);
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected int skillTreeDefaultTab() {
        return com.industrialcore.level.SkillTreeLayout.TAB_INFRA;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
