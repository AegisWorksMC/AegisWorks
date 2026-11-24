package com.industrialcore.client;

import com.industrialcore.blockentity.RotaryDrillBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.RotaryDrillMenu;
import com.industrialcore.trap.TrapLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * Phase 92: Adaptive GUI for the 6-direction Rotary Blade Drill ({@code rotary_drill}).
 */
public class RotaryDrillScreen extends AdaptiveContainerScreen<RotaryDrillMenu> {

    public RotaryDrillScreen(RotaryDrillMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapLayout.WIDTH;
        this.imageHeight = TrapLayout.DRILL_HEIGHT;
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
        int accent = st == MachineStatus.WORKING ? 0xFFFF6B7A : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, TrapLayout.DRILL_HEIGHT, TrapLayout.DRILL_BODY_BOTTOM, accent);

        Rect eb = TrapLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean drill status divider (no repurposed processing progress bar)
        int divY = top + TrapLayout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAAFF6B7A : 0x553B5568);

        Rect wg = TrapLayout.DRILL_WEAPON_GROUP;
        MachineTheme.ingredientGroup(g, left, top, wg.x(), wg.y(), wg.width(), wg.height());

        Rect box = TrapLayout.DRILL_STATS_BOX;
        g.fill(left + box.x(), top + box.y(), left + box.right(), top + box.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + box.x(), top + box.y(), box.width(), box.height(),
                0xFFD94A5A, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.rotary_drill.drilling"), 0xFFFF6B7A);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        int total = this.menu.getTotalTime();
        String dmgStr = String.format(Locale.ROOT, "%.1f", this.menu.getDamageX10() / 10.0F);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.rotary_drill.dps_short", dmgStr),
                0xFFFF8C98);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.rotary_drill.weapon"),
                TrapLayout.DRILL_WEAPON_LABEL_X, TrapLayout.DRILL_WEAPON_LABEL_Y, 30, MachineTheme.MUTED);

        int maxLineW = TrapLayout.DRILL_STATS_BOX.width() - 10;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.rotary_drill.damage", dmgStr),
                TrapLayout.DRILL_STATS_TEXT_X, TrapLayout.DRILL_ROW_1_Y, maxLineW, 0xFFFF8C98);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.rotary_drill.interval", total),
                TrapLayout.DRILL_STATS_TEXT_X, TrapLayout.DRILL_ROW_2_Y, maxLineW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.rotary_drill.range"),
                TrapLayout.DRILL_STATS_TEXT_X, TrapLayout.DRILL_ROW_3_Y, maxLineW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.rotary_drill.kills", this.menu.getKillsTotal()),
                TrapLayout.DRILL_STATS_TEXT_X, TrapLayout.DRILL_ROW_4_Y, maxLineW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapLayout.DRILL_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapLayout.DRILL_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapLayout.DRILL_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapLayout.DRILL_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapLayout.DRILL_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapLayout.DRILL_MOTOR_SLOT, TrapLayout.DRILL_BATTERY_SLOT,
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
        return RotaryDrillBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
