package com.industrialcore.client;

import com.industrialcore.agri.AgriLayout;
import com.industrialcore.blockentity.TreeHarvesterBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.TreeHarvesterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 90: Adaptive GUI for the Automatic Tree Harvester ({@code tree_harvester}).
 */
public class TreeHarvesterScreen extends AdaptiveContainerScreen<TreeHarvesterMenu> {

    public TreeHarvesterScreen(TreeHarvesterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AgriLayout.WIDTH;
        this.imageHeight = AgriLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(g, left, top, AgriLayout.HEIGHT, AgriLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect eb = AgriLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect pb = AgriLayout.PROGRESS_BAR;
        MachineTheme.progressBar(g, left + pb.x(), top + pb.y(), pb.width(), pb.height(),
                this.menu.getProgress(), this.menu.getTotalTime());

        Rect ig = AgriLayout.INPUT_GROUP_3;
        MachineTheme.ingredientGroup(g, left, top, ig.x(), ig.y(), ig.width(), ig.height());

        Rect og = AgriLayout.OUTPUT_GROUP;
        g.fill(left + og.x(), top + og.y(), left + og.right(), top + og.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + og.x(), top + og.y(), og.width(), og.height(),
                MachineTheme.CYAN_DIM, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0,
                Component.translatable(st.translationKey()), MachineTheme.statusColor(st));

        int total = this.menu.getTotalTime();
        int pct = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.machine.progress", pct), MachineTheme.MUTED);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, AgriLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Component info = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.tree_harvester.info",
                        this.menu.getTreesFelled()),
                GuiMetrics.CONTENT);
        g.drawString(this.font, info, GuiMetrics.SIDE, AgriLayout.INFO_TEXT_Y, 0xFF7BE8A8, false);

        g.drawString(this.font, MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.tree_harvester.saplings"), 36),
                AgriLayout.INPUT_LABEL_X, AgriLayout.INPUT_LABEL_Y, MachineTheme.MUTED, false);
        g.drawString(this.font, MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.agri.output"), 58),
                AgriLayout.OUTPUT_LABEL_X, AgriLayout.OUTPUT_LABEL_Y, MachineTheme.MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                AgriLayout.UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                AgriLayout.MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                AgriLayout.UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                AgriLayout.BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0, AgriLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AgriLayout.MOTOR_SLOT, AgriLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return TreeHarvesterBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
