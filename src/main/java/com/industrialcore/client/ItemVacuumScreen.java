package com.industrialcore.client;

import com.industrialcore.blockentity.ItemVacuumBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.ItemVacuumMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.trap.TrapLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 92: Adaptive GUI for the Wide-Area Item Vacuum ({@code item_vacuum}).
 */
public class ItemVacuumScreen extends AdaptiveContainerScreen<ItemVacuumMenu> {

    public ItemVacuumScreen(ItemVacuumMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapLayout.WIDTH;
        this.imageHeight = TrapLayout.VACUUM_HEIGHT;
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
        int accent = st == MachineStatus.WORKING ? 0xFFB98DEE : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, TrapLayout.VACUUM_HEIGHT, TrapLayout.VACUUM_BODY_BOTTOM, accent);

        Rect eb = TrapLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean suction field divider (no repurposed processing progress bar)
        int divY = top + TrapLayout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAAB98DEE : 0x553B5568);

        Rect og = TrapLayout.VACUUM_OUTPUT_GROUP;
        g.fill(left + og.x(), top + og.y(), left + og.right(), top + og.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + og.x(), top + og.y(), og.width(), og.height(),
                MachineTheme.PURPLE, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.item_vacuum.vacuuming"), 0xFFB98DEE);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.item_vacuum.radius_short",
                        this.menu.getRadiusBlocks()),
                0xFFD1B3FF);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Component info = Component.translatable("gui.industrialcore.item_vacuum.info",
                this.menu.getRadiusBlocks(), this.menu.getCollectedTotal());
        RanchHarvesterScreen.drawFitted(this.font, g, info, GuiMetrics.SIDE,
                TrapLayout.INFO_TEXT_Y, GuiMetrics.CONTENT, 0xFFD1B3FF);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.agri.output"),
                TrapLayout.VACUUM_OUTPUT_LABEL_X, TrapLayout.VACUUM_OUTPUT_LABEL_Y, 76, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapLayout.VACUUM_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapLayout.VACUUM_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapLayout.VACUUM_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapLayout.VACUUM_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapLayout.VACUUM_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapLayout.VACUUM_MOTOR_SLOT, TrapLayout.VACUUM_BATTERY_SLOT,
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
        return ItemVacuumBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
