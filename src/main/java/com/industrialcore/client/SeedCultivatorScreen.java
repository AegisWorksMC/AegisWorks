package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.SeedCultivatorLayout;
import com.industrialcore.menu.SeedCultivatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI of the seed cultivator (Phase 87: face buttons removed, Motor &amp; Battery slots added).
 */
public class SeedCultivatorScreen extends AdaptiveContainerScreen<SeedCultivatorMenu> {
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_PROGRESS = MachineTheme.STATUS_OK;

    public SeedCultivatorScreen(SeedCultivatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SeedCultivatorLayout.WIDTH;
        this.imageHeight = SeedCultivatorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, SeedCultivatorLayout.HEIGHT,
                SeedCultivatorLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = SeedCultivatorLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect progress = SeedCultivatorLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        Rect seed = SeedCultivatorLayout.SEED_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, seed.x() - 4, seed.y() - 4, 24, 24);
        Rect feed = SeedCultivatorLayout.FEED_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, feed.x() - 4, feed.y() - 4, 24, 24);
        MachineTheme.outputSlot(graphics, left + SeedCultivatorLayout.OUTPUT_SLOT.x(),
                top + SeedCultivatorLayout.OUTPUT_SLOT.y());

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineStatus status = this.menu.getStatus();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(status.translationKey()), statusColor(status));

        int total = this.menu.getOperationTime();
        int percent = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        MachineTheme.statusValue(this.font, graphics, 0, 0,
                Component.translatable("gui.industrialcore.machine.progress", percent), COLOR_MUTED);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                        this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, SeedCultivatorLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.seed_cultivator.slot.seed"),
                SeedCultivatorLayout.SEED_SLOT.x(), SeedCultivatorLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.seed_cultivator.slot.feed"),
                SeedCultivatorLayout.FEED_SLOT.x(), SeedCultivatorLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.seed_cultivator.slot.output"),
                SeedCultivatorLayout.OUTPUT_SLOT.x(), SeedCultivatorLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);

        int batch = this.menu.getBatch();
        Component batchText = batch > 0
                ? Component.translatable("gui.industrialcore.seed_cultivator.batch", batch,
                this.menu.getFeedLeft())
                : Component.translatable("gui.industrialcore.seed_cultivator.idle");
        graphics.drawString(this.font, batchText, GuiMetrics.SIDE,
                SeedCultivatorLayout.BATCH_TEXT_Y,
                batch > 0 ? COLOR_PROGRESS : COLOR_MUTED, false);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.seed_cultivator.rule"),
                SeedCultivatorLayout.FEED_TEXT_X, SeedCultivatorLayout.FEED_TEXT_Y,
                COLOR_MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                SeedCultivatorLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                SeedCultivatorLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                SeedCultivatorLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                SeedCultivatorLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                SeedCultivatorLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private static int statusColor(MachineStatus status) {
        return MachineTheme.statusColor(status);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                SeedCultivatorLayout.MOTOR_SLOT, SeedCultivatorLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "seed_cultivator";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
