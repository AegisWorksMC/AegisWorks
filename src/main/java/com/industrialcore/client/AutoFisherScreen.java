package com.industrialcore.client;

import com.industrialcore.agri.AgriLayout;
import com.industrialcore.blockentity.AutoFisherBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.AutoFisherMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 90: Adaptive GUI for the Automatic Fisher ({@code auto_fisher}).
 *
 * <p>Displays live water-source detection badge ({@code 水源 OK} / {@code 水源が必要です}),
 * fishing progress bar, optional enchanted fishing rod slot, 3x3 catch output grid, and
 * Motor &amp; Battery upgrade slots.</p>
 */
public class AutoFisherScreen extends AdaptiveContainerScreen<AutoFisherMenu> {

    public AutoFisherScreen(AutoFisherMenu menu, Inventory playerInventory, Component title) {
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
        int accent = this.menu.hasWater()
                ? MachineTheme.statusColor(this.menu.getStatus())
                : 0xFFFF6B57;
        MachineTheme.chrome(g, left, top, AgriLayout.HEIGHT, AgriLayout.BODY_BOTTOM, accent);

        Rect eb = AgriLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect pb = AgriLayout.PROGRESS_BAR;
        MachineTheme.progressBar(g, left + pb.x(), top + pb.y(), pb.width(), pb.height(),
                this.menu.getProgress(), this.menu.getTotalTime());

        Rect rg = AgriLayout.ROD_GROUP;
        MachineTheme.ingredientGroup(g, left, top, rg.x(), rg.y(), rg.width(), rg.height());

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
        if (!this.menu.hasWater()) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.auto_fisher.no_water_short"), 0xFFFF6B57);
        } else {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable(st.translationKey()), MachineTheme.statusColor(st));
        }

        int total = this.menu.getTotalTime();
        int pct = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.machine.progress", pct), MachineTheme.MUTED);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, AgriLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Component waterLine = this.menu.hasWater()
                ? Component.translatable("gui.industrialcore.auto_fisher.water_ok", this.menu.getCaughtCount())
                : Component.translatable("gui.industrialcore.auto_fisher.water_missing");
        int waterColor = this.menu.hasWater() ? 0xFF5CE6FF : 0xFFFF7B66;
        int waterW = this.font.width(waterLine);
        if (waterW <= GuiMetrics.CONTENT) {
            g.drawString(this.font, waterLine, GuiMetrics.SIDE, AgriLayout.INFO_TEXT_Y, waterColor, false);
        } else {
            float s = Math.max(0.72F, (float) GuiMetrics.CONTENT / (float) waterW);
            g.pose().pushPose();
            g.pose().translate(GuiMetrics.SIDE, AgriLayout.INFO_TEXT_Y + 0.5F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawString(this.font, waterLine, 0, 0, waterColor, false);
            g.pose().popPose();
        }

        g.drawString(this.font, MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.auto_fisher.rod"), 36),
                AgriLayout.ROD_LABEL_X, AgriLayout.ROD_LABEL_Y, MachineTheme.MUTED, false);
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
        return AutoFisherBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
