package com.industrialcore.client;

import com.industrialcore.blockentity.WirelessChargerBlockEntity;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.WirelessChargerMenu;
import com.industrialcore.storage.BigCountFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;

/**
 * Phase 94: Adaptive GUI for the Wireless Charger ({@code wireless_charger}).
 */
public class WirelessChargerScreen extends AdaptiveContainerScreen<WirelessChargerMenu> {

    public WirelessChargerScreen(WirelessChargerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeLayout.WIDTH;
        this.imageHeight = LifeLayout.CHARGER_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getChargerState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeLayout.CHARGER_HEIGHT, LifeLayout.CHARGER_BODY_BOTTOM, accent);

        Rect eb = LifeLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect sb = LifeLayout.CHARGER_STATS_BOX;
        g.fill(left + sb.x(), top + sb.y(), left + sb.right(), top + sb.bottom(), 0xFF070D14);
        MachineTheme.glowRect(g, left + sb.x(), top + sb.y(), sb.width(), sb.height(), accent, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getChargerState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.wireless_charger.radius_short",
                        this.menu.getRadiusBlocks()),
                0xFF6AE8FF);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        int maxW = LifeLayout.CHARGER_STATS_BOX.width() - 12;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.wireless_charger.radius",
                        this.menu.getRadiusBlocks()),
                LifeLayout.CHARGER_STATS_TEXT_X, LifeLayout.CHARGER_ROW_1_Y, maxW, 0xFF7BE8FF);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.wireless_charger.rate",
                        String.format("%,d", this.menu.getMaxRatePerTick())),
                LifeLayout.CHARGER_STATS_TEXT_X, LifeLayout.CHARGER_ROW_2_Y, maxW, 0xFFFFD866);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.wireless_charger.players",
                        this.menu.getNearbyPlayers()),
                LifeLayout.CHARGER_STATS_TEXT_X, LifeLayout.CHARGER_ROW_3_Y, maxW, 0xFF8CFFBE);

        String totalFmt = BigCountFormatter.format(
                BigInteger.valueOf(this.menu.getTotalTransferred()),
                BigCountFormatter.UnitMode.ENGLISH);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.wireless_charger.total", totalFmt),
                LifeLayout.CHARGER_STATS_TEXT_X, LifeLayout.CHARGER_ROW_4_Y, maxW, MachineTheme.TEXT);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLbl,
                LifeLayout.CHARGER_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeLayout.CHARGER_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLbl,
                LifeLayout.CHARGER_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeLayout.CHARGER_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeLayout.CHARGER_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeLayout.CHARGER_MOTOR_SLOT, LifeLayout.CHARGER_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected BlockPos upgradeDockPos() {
        return this.menu.getBlockPos();
    }

    @Override
    protected String skillTreeMachineId() {
        return WirelessChargerBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeLogic.CHARGER_STATE_CHARGING -> 0xFF5CFFA0;
            case LifeLogic.CHARGER_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> 0xFF5CE6FF;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeLogic.CHARGER_STATE_CHARGING -> "gui.industrialcore.wireless_charger.status.charging";
            case LifeLogic.CHARGER_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
