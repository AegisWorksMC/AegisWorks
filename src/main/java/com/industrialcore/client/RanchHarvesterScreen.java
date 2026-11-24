package com.industrialcore.client;

import com.industrialcore.agri.AgriWave2Layout;
import com.industrialcore.blockentity.RanchHarvesterBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.RanchHarvesterMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 91: Adaptive GUI for the Automatic Ranch Harvester ({@code ranch_harvester}).
 */
public class RanchHarvesterScreen extends AdaptiveContainerScreen<RanchHarvesterMenu> {

    public RanchHarvesterScreen(RanchHarvesterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AgriWave2Layout.WIDTH;
        this.imageHeight = AgriWave2Layout.RANCH_HEIGHT;
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
        boolean cappedIdle = this.menu.isBreedCapped() && st == MachineStatus.IDLE;
        int accent = cappedIdle ? 0xFFFFC14D : MachineTheme.statusColor(st);
        MachineTheme.chrome(g, left, top, AgriWave2Layout.RANCH_HEIGHT, AgriWave2Layout.RANCH_BODY_BOTTOM, accent);

        Rect eb = AgriWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect pb = AgriWave2Layout.PROGRESS_BAR;
        MachineTheme.progressBar(g, left + pb.x(), top + pb.y(), pb.width(), pb.height(),
                this.menu.getProgress(), this.menu.getTotalTime());

        Rect fg = AgriWave2Layout.RANCH_FEED_GROUP;
        MachineTheme.ingredientGroup(g, left, top, fg.x(), fg.y(), fg.width(), fg.height());

        Rect og = AgriWave2Layout.RANCH_OUTPUT_GROUP;
        g.fill(left + og.x(), top + og.y(), left + og.right(), top + og.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + og.x(), top + og.y(), og.width(), og.height(),
                MachineTheme.CYAN_DIM, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineStatus st = this.menu.getStatus();
        boolean capped = this.menu.isBreedCapped();
        MachineTheme.title(this.font, g, 0, 0, this.title);
        if (capped && st == MachineStatus.IDLE) {
            MachineTheme.statusText(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.ranch_harvester.capped_short"), 0xFFFFC14D);
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
                GuiMetrics.SIDE, AgriWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Component info = capped
                ? Component.translatable("gui.industrialcore.ranch_harvester.info_capped", this.menu.getAnimalCount())
                : Component.translatable("gui.industrialcore.ranch_harvester.info", this.menu.getAnimalCount());
        drawFitted(this.font, g, info, GuiMetrics.SIDE, AgriWave2Layout.INFO_TEXT_Y,
                GuiMetrics.CONTENT, capped ? 0xFFFFC14D : 0xFFFFD875);

        drawFitted(this.font, g, Component.translatable("gui.industrialcore.ranch_harvester.feed"),
                AgriWave2Layout.RANCH_FEED_LABEL_X, AgriWave2Layout.RANCH_FEED_LABEL_Y, 44, MachineTheme.MUTED);
        drawFitted(this.font, g, Component.translatable("gui.industrialcore.agri.output"),
                AgriWave2Layout.RANCH_OUTPUT_LABEL_X, AgriWave2Layout.RANCH_OUTPUT_LABEL_Y, 58, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                AgriWave2Layout.RANCH_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                AgriWave2Layout.RANCH_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                AgriWave2Layout.RANCH_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                AgriWave2Layout.RANCH_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                AgriWave2Layout.RANCH_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AgriWave2Layout.RANCH_MOTOR_SLOT, AgriWave2Layout.RANCH_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return RanchHarvesterBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static void drawFitted(Font font, GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(0.70F, (float) maxWidth / (float) w);
        g.pose().pushPose();
        g.pose().translate(x, y + 0.5F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
