package com.industrialcore.client;

import com.industrialcore.blockentity.SpawnSuppressorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.SpawnSuppressorMenu;
import com.industrialcore.trap.TrapWave2Layout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 93: Adaptive GUI for the Spawn Suppressor Beacon ({@code spawn_suppressor}).
 */
public class SpawnSuppressorScreen extends AdaptiveContainerScreen<SpawnSuppressorMenu> {

    public SpawnSuppressorScreen(SpawnSuppressorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapWave2Layout.WIDTH;
        this.imageHeight = TrapWave2Layout.SUP_HEIGHT;
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
        int accent = st == MachineStatus.WORKING ? 0xFFFFD166 : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, TrapWave2Layout.SUP_HEIGHT, TrapWave2Layout.SUP_BODY_BOTTOM, accent);

        Rect eb = TrapWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean field status divider (no repurposed processing progress bar)
        int divY = top + TrapWave2Layout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAAFFD166 : 0x553B5568);

        Rect box = TrapWave2Layout.SUP_STATS_BOX;
        g.fill(left + box.x(), top + box.y(), left + box.right(), top + box.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + box.x(), top + box.y(), box.width(), box.height(),
                0xFFFFD166, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.spawn_suppressor.active"), 0xFFFFD166);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.spawn_suppressor.radius_short",
                        this.menu.getRadiusBlocks()),
                MachineTheme.CYAN_BRIGHT);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        int maxLineW = TrapWave2Layout.SUP_STATS_BOX.width() - 12;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.spawn_suppressor.radius",
                        this.menu.getRadiusBlocks()),
                TrapWave2Layout.SUP_STATS_TEXT_X, TrapWave2Layout.SUP_ROW_1_Y, maxLineW, 0xFFFFE599);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.spawn_suppressor.scope"),
                TrapWave2Layout.SUP_STATS_TEXT_X, TrapWave2Layout.SUP_ROW_2_Y, maxLineW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.spawn_suppressor.power",
                        this.menu.getEnergyPerTick()),
                TrapWave2Layout.SUP_STATS_TEXT_X, TrapWave2Layout.SUP_ROW_3_Y, maxLineW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.spawn_suppressor.blocked",
                        this.menu.getBlockedTotal()),
                TrapWave2Layout.SUP_STATS_TEXT_X, TrapWave2Layout.SUP_ROW_4_Y, maxLineW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapWave2Layout.SUP_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapWave2Layout.SUP_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapWave2Layout.SUP_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapWave2Layout.SUP_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapWave2Layout.SUP_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapWave2Layout.SUP_MOTOR_SLOT, TrapWave2Layout.SUP_BATTERY_SLOT,
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
        return SpawnSuppressorBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
