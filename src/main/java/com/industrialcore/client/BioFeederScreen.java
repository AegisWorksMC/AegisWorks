package com.industrialcore.client;

import com.industrialcore.agri.AgriWave2Layout;
import com.industrialcore.blockentity.BioFeederBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.BioFeederMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 91: Adaptive GUI for the Bio Feeder Beacon ({@code bio_feeder}).
 */
public class BioFeederScreen extends AdaptiveContainerScreen<BioFeederMenu> {

    public BioFeederScreen(BioFeederMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AgriWave2Layout.WIDTH;
        this.imageHeight = AgriWave2Layout.FEEDER_HEIGHT;
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
        int accent = switch (st) {
            case WORKING -> 0xFF5CE69E;
            case NO_INPUT -> 0xFFFF7B66;
            default -> MachineTheme.statusColor(st);
        };
        MachineTheme.chrome(g, left, top, AgriWave2Layout.FEEDER_HEIGHT, AgriWave2Layout.FEEDER_BODY_BOTTOM, accent);

        Rect eb = AgriWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean bio-feeder status divider (no repurposed processing progress bar)
        int divY = top + AgriWave2Layout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAA5CE69E : 0x553B5568);

        Rect fg = AgriWave2Layout.FEEDER_FOOD_GROUP;
        MachineTheme.ingredientGroup(g, left, top, fg.x(), fg.y(), fg.width(), fg.height());

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        switch (st) {
            case WORKING -> MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.bio_feeder.feeding"), 0xFF5CE69E);
            case NO_INPUT -> MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.bio_feeder.no_food"), 0xFFFF7B66);
            default -> MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.bio_feeder.radius", this.menu.getRadiusBlocks()),
                0xFF9BE6FF);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Component info = Component.translatable("gui.industrialcore.bio_feeder.info",
                this.menu.getRadiusBlocks(), this.menu.getPlayersInRange(), this.menu.getFedTotal());
        RanchHarvesterScreen.drawFitted(this.font, g, info, GuiMetrics.SIDE,
                AgriWave2Layout.INFO_TEXT_Y, GuiMetrics.CONTENT, 0xFFFF9EC6);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.bio_feeder.food"),
                AgriWave2Layout.FEEDER_FOOD_LABEL_X, AgriWave2Layout.FEEDER_FOOD_LABEL_Y, 64, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                AgriWave2Layout.FEEDER_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                AgriWave2Layout.FEEDER_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                AgriWave2Layout.FEEDER_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                AgriWave2Layout.FEEDER_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                AgriWave2Layout.FEEDER_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AgriWave2Layout.FEEDER_MOTOR_SLOT, AgriWave2Layout.FEEDER_BATTERY_SLOT,
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
        return BioFeederBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
