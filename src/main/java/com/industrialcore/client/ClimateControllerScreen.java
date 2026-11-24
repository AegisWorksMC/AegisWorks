package com.industrialcore.client;

import com.industrialcore.blockentity.ClimateControllerBlockEntity;
import com.industrialcore.life.LifeWave2Layout;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.menu.ClimateControllerMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.ClimateActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Phase 95: Adaptive GUI for the Climate &amp; Time Controller ({@code climate_controller}). */
public class ClimateControllerScreen extends AdaptiveContainerScreen<ClimateControllerMenu> {

    public ClimateControllerScreen(ClimateControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeWave2Layout.WIDTH;
        this.imageHeight = LifeWave2Layout.CLIMATE_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getClimateState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeWave2Layout.CLIMATE_HEIGHT, LifeWave2Layout.CLIMATE_BODY_BOTTOM, accent);

        Rect eb = LifeWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        boolean ok = this.menu.isDimensionSupported();
        int lx = (int) Math.round(panelMouseX(mouseX) - left);
        int ly = (int) Math.round(panelMouseY(mouseY) - top);

        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_CLEAR,
                ok && LifeWave2Layout.CLIMATE_BTN_CLEAR.contains(lx, ly),
                ok ? 0xFF142B3A : 0xFF0A1118, ok ? 0xFF66E2FF : 0xFF2B3A48);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_RAIN,
                ok && LifeWave2Layout.CLIMATE_BTN_RAIN.contains(lx, ly),
                ok ? 0xFF10243C : 0xFF0A1118, ok ? 0xFF4BA8FF : 0xFF2B3A48);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_THUNDER,
                ok && LifeWave2Layout.CLIMATE_BTN_THUNDER.contains(lx, ly),
                ok ? 0xFF221A3C : 0xFF0A1118, ok ? 0xFFC07BFF : 0xFF2B3A48);

        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_DAY,
                ok && LifeWave2Layout.CLIMATE_BTN_DAY.contains(lx, ly),
                ok ? 0xFF2B2410 : 0xFF0A1118, ok ? 0xFFFFD866 : 0xFF2B3A48);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_NIGHT,
                ok && LifeWave2Layout.CLIMATE_BTN_NIGHT.contains(lx, ly),
                ok ? 0xFF151836 : 0xFF0A1118, ok ? 0xFF8AA8FF : 0xFF2B3A48);

        boolean lc = this.menu.isLockClear();
        boolean ld = this.menu.isLockDay();
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_LOCK_CLEAR,
                ok && LifeWave2Layout.CLIMATE_BTN_LOCK_CLEAR.contains(lx, ly),
                lc ? 0xFF133B2A : 0xFF0E1B26, lc ? 0xFF5CFFA0 : 0xFF3E6C88);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.CLIMATE_BTN_LOCK_DAY,
                ok && LifeWave2Layout.CLIMATE_BTN_LOCK_DAY.contains(lx, ly),
                ld ? 0xFF133B2A : 0xFF0E1B26, ld ? 0xFF5CFFA0 : 0xFF3E6C88);

        Rect ib = LifeWave2Layout.CLIMATE_INFO_BOX;
        g.fill(left + ib.x(), top + ib.y(), left + ib.right(), top + ib.bottom(), 0xFF070D14);
        MachineTheme.glowRect(g, left + ib.x(), top + ib.y(), ib.width(), ib.height(), accent, 0.28D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getClimateState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        int cd = this.menu.getCooldownTicks();
        if (cd > 0) {
            MachineTheme.statusValue(this.font, g, 0, 0, Component.literal(cd + "t"), MachineTheme.STATUS_WARN);
        }

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        boolean ok = this.menu.isDimensionSupported();
        if (!ok) {
            RanchHarvesterScreen.drawFitted(this.font, g,
                    Component.translatable("gui.industrialcore.climate_controller.unsupported_msg"),
                    GuiMetrics.SIDE, LifeWave2Layout.CLIMATE_STATUS_LINE_Y, GuiMetrics.CONTENT, MachineTheme.STATUS_BAD);
        } else {
            Component weatherName = Component.translatable(weatherKey(this.menu.getWeatherCode()));
            int t = this.menu.getDayTimeOfDay();
            int hour = (int) (((t / 1000) + 6) % 24);
            int min = (int) (((t % 1000) * 60) / 1000);
            String clock = String.format("%02d:%02d", hour, min);
            RanchHarvesterScreen.drawFitted(this.font, g,
                    Component.translatable("gui.industrialcore.climate_controller.now", weatherName, clock),
                    GuiMetrics.SIDE, LifeWave2Layout.CLIMATE_STATUS_LINE_Y, GuiMetrics.CONTENT, 0xFF9EEBFF);
        }

        int btnCol = ok ? 0xFFE6FAFF : MachineTheme.MUTED;
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_CLEAR,
                Component.translatable("gui.industrialcore.climate_controller.btn_clear"), btnCol);
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_RAIN,
                Component.translatable("gui.industrialcore.climate_controller.btn_rain"), btnCol);
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_THUNDER,
                Component.translatable("gui.industrialcore.climate_controller.btn_thunder"), btnCol);
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_DAY,
                Component.translatable("gui.industrialcore.climate_controller.btn_day"), btnCol);
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_NIGHT,
                Component.translatable("gui.industrialcore.climate_controller.btn_night"), btnCol);

        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_LOCK_CLEAR,
                Component.translatable("gui.industrialcore.climate_controller.lock_clear"),
                this.menu.isLockClear() ? 0xFFE6FFF2 : btnCol);
        drawCentered(g, LifeWave2Layout.CLIMATE_BTN_LOCK_DAY,
                Component.translatable("gui.industrialcore.climate_controller.lock_day"),
                this.menu.isLockDay() ? 0xFFE6FFF2 : btnCol);

        int maxW = LifeWave2Layout.CLIMATE_INFO_BOX.width() - 10;
        int batt = this.menu.getBatteryCount();
        int mot = this.menu.getMotorCount();
        int wCost = LifeWave2Logic.climateActionCost(true, batt, 0);
        int tCost = LifeWave2Logic.climateActionCost(false, batt, 0);
        int holds = (this.menu.isLockClear() ? 1 : 0) + (this.menu.isLockDay() ? 1 : 0);
        int holdFe = LifeWave2Logic.climateHoldCostPerTick(holds, mot, batt, 0);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.climate_controller.cost_weather", String.format("%,d", wCost)),
                LifeWave2Layout.CLIMATE_INFO_TEXT_X, LifeWave2Layout.CLIMATE_INFO_ROW_1_Y, maxW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.climate_controller.cost_time", String.format("%,d", tCost)),
                LifeWave2Layout.CLIMATE_INFO_TEXT_X, LifeWave2Layout.CLIMATE_INFO_ROW_2_Y, maxW, MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.climate_controller.hold_fe", holdFe),
                LifeWave2Layout.CLIMATE_INFO_TEXT_X, LifeWave2Layout.CLIMATE_INFO_ROW_3_Y, maxW, 0xFF8CFFBE);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLbl,
                LifeWave2Layout.CLIMATE_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeWave2Layout.CLIMATE_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLbl,
                LifeWave2Layout.CLIMATE_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeWave2Layout.CLIMATE_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeWave2Layout.CLIMATE_BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawCentered(GuiGraphics g, Rect r, Component label, int color) {
        Component fitted = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = r.x() + (r.width() - this.font.width(fitted)) / 2;
        int ty = r.y() + (r.height() - 8) / 2 + 1;
        g.drawString(this.font, fitted, tx, ty, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double lx = panelMouseX(mouseX) - this.leftPos;
            double ly = panelMouseY(mouseY) - this.topPos;
            int action = hitAction(lx, ly);
            if (action >= 0) {
                if (!this.menu.isDimensionSupported()) {
                    SoundDirector.playUiLocked();
                    return true;
                }
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new ClimateActionPayload(this.menu.getBlockPos(), action));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int hitAction(double lx, double ly) {
        if (LifeWave2Layout.CLIMATE_BTN_CLEAR.contains(lx, ly)) return ClimateActionPayload.ACTION_CLEAR;
        if (LifeWave2Layout.CLIMATE_BTN_RAIN.contains(lx, ly)) return ClimateActionPayload.ACTION_RAIN;
        if (LifeWave2Layout.CLIMATE_BTN_THUNDER.contains(lx, ly)) return ClimateActionPayload.ACTION_THUNDER;
        if (LifeWave2Layout.CLIMATE_BTN_DAY.contains(lx, ly)) return ClimateActionPayload.ACTION_DAY;
        if (LifeWave2Layout.CLIMATE_BTN_NIGHT.contains(lx, ly)) return ClimateActionPayload.ACTION_NIGHT;
        if (LifeWave2Layout.CLIMATE_BTN_LOCK_CLEAR.contains(lx, ly)) return ClimateActionPayload.ACTION_TOGGLE_LOCK_CLEAR;
        if (LifeWave2Layout.CLIMATE_BTN_LOCK_DAY.contains(lx, ly)) return ClimateActionPayload.ACTION_TOGGLE_LOCK_DAY;
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeWave2Layout.CLIMATE_MOTOR_SLOT, LifeWave2Layout.CLIMATE_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return ClimateControllerBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String weatherKey(int code) {
        return switch (code) {
            case 2 -> "gui.industrialcore.climate_controller.btn_thunder";
            case 1 -> "gui.industrialcore.climate_controller.btn_rain";
            default -> "gui.industrialcore.climate_controller.btn_clear";
        };
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeWave2Logic.CLIMATE_STATE_HOLDING -> 0xFF5CFFA0;
            case LifeWave2Logic.CLIMATE_STATE_NO_ENERGY,
                    LifeWave2Logic.CLIMATE_STATE_UNSUPPORTED -> MachineTheme.STATUS_BAD;
            default -> 0xFF5CE6FF;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeWave2Logic.CLIMATE_STATE_HOLDING -> "gui.industrialcore.climate_controller.status.holding";
            case LifeWave2Logic.CLIMATE_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            case LifeWave2Logic.CLIMATE_STATE_UNSUPPORTED -> "gui.industrialcore.climate_controller.status.unsupported";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
