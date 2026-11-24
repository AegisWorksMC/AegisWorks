package com.industrialcore.client;

import com.industrialcore.blockentity.MobFanBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.MobFanMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.trap.TrapLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 92: Adaptive GUI for the 6-direction Mob Movement Fan ({@code mob_fan}).
 */
public class MobFanScreen extends AdaptiveContainerScreen<MobFanMenu> {

    public MobFanScreen(MobFanMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapLayout.WIDTH;
        this.imageHeight = TrapLayout.FAN_HEIGHT;
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
        int accent = st == MachineStatus.WORKING ? 0xFF5CE6FF : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, TrapLayout.FAN_HEIGHT, TrapLayout.FAN_BODY_BOTTOM, accent);

        Rect eb = TrapLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean airflow divider (no repurposed processing progress bar)
        int divY = top + TrapLayout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAA5CE6FF : 0x553B5568);

        Rect box = TrapLayout.FAN_STATS_BOX;
        g.fill(left + box.x(), top + box.y(), left + box.right(), top + box.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + box.x(), top + box.y(), box.width(), box.height(),
                0xFF3CC2F5, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.mob_fan.blowing"), 0xFF5CE6FF);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.mob_fan.range_short", this.menu.getRangeBlocks()),
                MachineTheme.CYAN_BRIGHT);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        int maxLineW = TrapLayout.FAN_STATS_BOX.width() - 12;
        Component dirName = Component.translatable("gui.industrialcore.mob_fan.dir."
                + this.menu.getFacing().getName());
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_fan.direction", dirName),
                TrapLayout.FAN_STATS_TEXT_X, TrapLayout.FAN_ROW_1_Y, maxLineW, 0xFF8BEFFF);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_fan.range", this.menu.getRangeBlocks()),
                TrapLayout.FAN_STATS_TEXT_X, TrapLayout.FAN_ROW_2_Y, maxLineW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_fan.power", this.menu.getPowerPercent()),
                TrapLayout.FAN_STATS_TEXT_X, TrapLayout.FAN_ROW_3_Y, maxLineW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_fan.targets", this.menu.getTargetsCount()),
                TrapLayout.FAN_STATS_TEXT_X, TrapLayout.FAN_ROW_4_Y, maxLineW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapLayout.FAN_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapLayout.FAN_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapLayout.FAN_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapLayout.FAN_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapLayout.FAN_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapLayout.FAN_MOTOR_SLOT, TrapLayout.FAN_BATTERY_SLOT,
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
        return MobFanBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
