package com.industrialcore.client;

import com.industrialcore.agri.AgriWave2Layout;
import com.industrialcore.blockentity.GrowthAcceleratorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GrowthAcceleratorMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 91: Adaptive GUI for the Growth Accelerator Pulse Machine ({@code growth_accelerator}).
 */
public class GrowthAcceleratorScreen extends AdaptiveContainerScreen<GrowthAcceleratorMenu> {

    public GrowthAcceleratorScreen(GrowthAcceleratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AgriWave2Layout.WIDTH;
        this.imageHeight = AgriWave2Layout.GROWTH_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineStatus st = this.menu.getStatus();
        int accent = st == MachineStatus.WORKING ? 0xFF4CFF9E : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, AgriWave2Layout.GROWTH_HEIGHT, AgriWave2Layout.GROWTH_BODY_BOTTOM, accent);

        Rect eb = AgriWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean pulse-field divider (no repurposed processing progress bar)
        int divY = top + AgriWave2Layout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAA4CFF9E : 0x553B5568);

        // Recessed telemetry box for range, pulse interval, concurrent targets, and total pulses
        Rect box = AgriWave2Layout.GROWTH_STATS_BOX;
        g.fill(left + box.x(), top + box.y(), left + box.right(), top + box.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + box.x(), top + box.y(), box.width(), box.height(),
                0xFF39D98A, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.growth_accelerator.pulsing"), 0xFF4CFF9E);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        int total = this.menu.getTotalTime();
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.growth_accelerator.interval_short", total),
                0xFF8EF5B8);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        int maxLineW = AgriWave2Layout.GROWTH_STATS_BOX.width() - 12;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.growth_accelerator.range"),
                AgriWave2Layout.GROWTH_STATS_TEXT_X, AgriWave2Layout.GROWTH_ROW_1_Y, maxLineW, 0xFF8EF5B8);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.growth_accelerator.interval", total),
                AgriWave2Layout.GROWTH_STATS_TEXT_X, AgriWave2Layout.GROWTH_ROW_2_Y, maxLineW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.growth_accelerator.targets", this.menu.getTargetsPerPulse()),
                AgriWave2Layout.GROWTH_STATS_TEXT_X, AgriWave2Layout.GROWTH_ROW_3_Y, maxLineW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.growth_accelerator.pulses", this.menu.getPulsesTotal()),
                AgriWave2Layout.GROWTH_STATS_TEXT_X, AgriWave2Layout.GROWTH_ROW_4_Y, maxLineW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                AgriWave2Layout.GROWTH_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                AgriWave2Layout.GROWTH_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                AgriWave2Layout.GROWTH_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                AgriWave2Layout.GROWTH_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                AgriWave2Layout.GROWTH_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AgriWave2Layout.GROWTH_MOTOR_SLOT, AgriWave2Layout.GROWTH_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected BlockPos upgradeDockPos() {
        return this.menu.getBlockPos();
    }

    @Override
    protected String skillTreeMachineId() {
        return GrowthAcceleratorBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
