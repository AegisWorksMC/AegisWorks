package com.industrialcore.client;

import com.industrialcore.blockentity.XpCollectorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.XpCollectorMenu;
import com.industrialcore.network.XpCollectorActionPayload;
import com.industrialcore.trap.TrapWave2Layout;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 93: Adaptive GUI for the Experience Collector ({@code xp_collector}).
 * Includes one-click {@code +1 Lv}, {@code +10 Lv}, and {@code All} withdrawal buttons,
 * XP storage &amp; radius summary, glass-bottle auto-bottling slots, and Motor/Battery upgrades.
 */
public class XpCollectorScreen extends AdaptiveContainerScreen<XpCollectorMenu> {

    public XpCollectorScreen(XpCollectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapWave2Layout.WIDTH;
        this.imageHeight = TrapWave2Layout.XP_HEIGHT;
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
        int accent = st == MachineStatus.WORKING ? 0xFF5CFFA0 : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, TrapWave2Layout.XP_HEIGHT, TrapWave2Layout.XP_BODY_BOTTOM, accent);

        Rect eb = TrapWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Phase 113: Clean XP field divider (no repurposed processing progress bar)
        int divY = top + TrapWave2Layout.PROGRESS_BAR.y() + 3;
        g.fill(left + GuiMetrics.SIDE, divY, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 1, 0xFF1A2D3D);
        g.fill(left + GuiMetrics.SIDE, divY + 1, left + GuiMetrics.SIDE + GuiMetrics.CONTENT, divY + 2,
                st == MachineStatus.WORKING ? 0xAA5CFFA0 : 0x553B5568);

        boolean hasXp = this.menu.getStoredXp() > 0L;
        int lx = mouseX - left;
        int ly = mouseY - top;
        drawActionButtonBg(g, left, top, TrapWave2Layout.XP_BTN_1LV,
                TrapWave2Layout.XP_BTN_1LV.contains(lx, ly), hasXp);
        drawActionButtonBg(g, left, top, TrapWave2Layout.XP_BTN_10LV,
                TrapWave2Layout.XP_BTN_10LV.contains(lx, ly), hasXp);
        drawActionButtonBg(g, left, top, TrapWave2Layout.XP_BTN_ALL,
                TrapWave2Layout.XP_BTN_ALL.contains(lx, ly), hasXp);

        Rect ig = TrapWave2Layout.XP_INPUT_GROUP;
        g.fill(left + ig.x(), top + ig.y(), left + ig.right(), top + ig.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + ig.x(), top + ig.y(), ig.width(), ig.height(),
                0xFF5CE6FF, 0.30D);

        Rect og = TrapWave2Layout.XP_OUTPUT_GROUP;
        g.fill(left + og.x(), top + og.y(), left + og.right(), top + og.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + og.x(), top + og.y(), og.width(), og.height(),
                0xFF5CFFA0, 0.30D);

        // Phase 113: Show compact bottling arrow ONLY when a glass bottle is inserted
        if (!this.menu.slots.isEmpty() && this.menu.slots.get(XpCollectorBlockEntity.INPUT_SLOT).hasItem()) {
            int ax = left + 43;
            int ay = top + 148;
            int col = this.menu.getStoredXp() >= TrapWave2Logic.XP_PER_BOTTLE ? 0xFF5CFFA0 : 0xFF527285;
            g.fill(ax, ay, ax + 9, ay + 2, col);
            g.fill(ax + 7, ay - 2, ax + 9, ay + 4, col);
            g.fill(ax + 9, ay - 1, ax + 11, ay + 3, col);
            g.fill(ax + 11, ay, ax + 12, ay + 2, col);
        }

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    private static void drawActionButtonBg(GuiGraphics g, int left, int top, Rect r,
                                           boolean hover, boolean enabled) {
        int bx = left + r.x();
        int by = top + r.y();
        int bg = !enabled ? 0xFF0B1117 : (hover ? 0xFF173D2B : 0xFF0E2319);
        int border = !enabled ? 0xFF283845 : (hover ? 0xFF7BFFBA : 0xFF39C97B);
        if (enabled && hover) {
            MachineTheme.glowRect(g, bx, by, r.width(), r.height(), 0xFF5CFFA0, 0.45D);
        }
        g.fill(bx, by, bx + r.width(), by + r.height(), bg);
        g.fill(bx, by, bx + r.width(), by + 1, border);
        g.fill(bx, by + r.height() - 1, bx + r.width(), by + r.height(), border);
        g.fill(bx, by, bx + 1, by + r.height(), border);
        g.fill(bx + r.width() - 1, by, bx + r.width(), by + r.height(), border);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (st == MachineStatus.WORKING) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.xp_collector.collecting"), 0xFF5CFFA0);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.xp_collector.radius_short",
                        this.menu.getRadiusBlocks()),
                0xFF5CFFA0);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        String xpFmt = String.format("%,d", this.menu.getStoredXp());
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.xp_collector.info",
                        xpFmt, this.menu.getRadiusBlocks()),
                GuiMetrics.SIDE, TrapWave2Layout.XP_INFO_TEXT_Y, GuiMetrics.CONTENT, 0xFF8CFFBE);

        boolean hasXp = this.menu.getStoredXp() > 0L;
        int btnTextCol = hasXp ? 0xFFE6FFF2 : MachineTheme.MUTED;
        drawCenteredBtnLabel(g, TrapWave2Layout.XP_BTN_1LV,
                Component.translatable("gui.industrialcore.xp_collector.btn_1lv"), btnTextCol);
        drawCenteredBtnLabel(g, TrapWave2Layout.XP_BTN_10LV,
                Component.translatable("gui.industrialcore.xp_collector.btn_10lv"), btnTextCol);
        drawCenteredBtnLabel(g, TrapWave2Layout.XP_BTN_ALL,
                Component.translatable("gui.industrialcore.xp_collector.btn_all"), btnTextCol);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.xp_collector.bottle_in"),
                TrapWave2Layout.XP_INPUT_LABEL_X, TrapWave2Layout.XP_INPUT_LABEL_Y, 40, MachineTheme.MUTED);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.xp_collector.bottle_out"),
                TrapWave2Layout.XP_OUTPUT_LABEL_X, TrapWave2Layout.XP_OUTPUT_LABEL_Y, 64, MachineTheme.MUTED);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.xp_collector.bottled",
                        this.menu.getBottledTotal()),
                TrapWave2Layout.XP_INPUT_LABEL_X, TrapWave2Layout.XP_BOTTLED_TEXT_Y, 110, MachineTheme.TEXT);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapWave2Layout.XP_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapWave2Layout.XP_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapWave2Layout.XP_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapWave2Layout.XP_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapWave2Layout.XP_BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawCenteredBtnLabel(GuiGraphics g, Rect r, Component label, int color) {
        Component fitted = MachineTheme.fit(this.font, label, r.width() - 6);
        int tx = r.x() + (r.width() - this.font.width(fitted)) / 2;
        int ty = r.y() + (r.height() - 8) / 2 + 1;
        g.drawString(this.font, fitted, tx, ty, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double lx = panelMouseX(mouseX) - this.leftPos;
            double ly = panelMouseY(mouseY) - this.topPos;
            int action = -1;
            if (TrapWave2Layout.XP_BTN_1LV.contains(lx, ly)) {
                action = TrapWave2Logic.XP_ACTION_TAKE_1_LV;
            } else if (TrapWave2Layout.XP_BTN_10LV.contains(lx, ly)) {
                action = TrapWave2Logic.XP_ACTION_TAKE_10_LV;
            } else if (TrapWave2Layout.XP_BTN_ALL.contains(lx, ly)) {
                action = TrapWave2Logic.XP_ACTION_TAKE_ALL;
            }
            if (action >= 0) {
                if (this.menu.getStoredXp() > 0L) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(
                            new XpCollectorActionPayload(this.menu.getBlockPos(), action));
                } else {
                    SoundDirector.playUiLocked();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapWave2Layout.XP_MOTOR_SLOT, TrapWave2Layout.XP_BATTERY_SLOT,
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
        return XpCollectorBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
