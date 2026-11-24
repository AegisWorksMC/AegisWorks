package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.MatterAmplifierLayout;
import com.industrialcore.menu.MatterAmplifierMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI of the matter amplifier (Phase 87: face buttons removed, Motor &amp; Battery slots added).
 */
public class MatterAmplifierScreen extends AdaptiveContainerScreen<MatterAmplifierMenu> {
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;

    public MatterAmplifierScreen(MatterAmplifierMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MatterAmplifierLayout.WIDTH;
        this.imageHeight = MatterAmplifierLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, MatterAmplifierLayout.HEIGHT,
                MatterAmplifierLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = MatterAmplifierLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect progress = MatterAmplifierLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        Rect in = MatterAmplifierLayout.INPUT_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, in.x() - 4, in.y() - 4, 24, 24);
        Rect catalyst = MatterAmplifierLayout.CATALYST_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, catalyst.x() - 4, catalyst.y() - 4,
                24, 24);
        Rect ore = MatterAmplifierLayout.OUTPUT_SLOT;
        MachineTheme.outputWell(graphics, left + ore.x(), top + ore.y());
        MachineTheme.outputWell(graphics, left + MatterAmplifierLayout.DUST_OUTPUT_SLOT.x(),
                top + MatterAmplifierLayout.DUST_OUTPUT_SLOT.y());

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
                GuiMetrics.SIDE, MatterAmplifierLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.matter_amplifier.slot.input"),
                MatterAmplifierLayout.INPUT_SLOT.x(), MatterAmplifierLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.matter_amplifier.slot.catalyst"),
                MatterAmplifierLayout.CATALYST_SLOT.x(), MatterAmplifierLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.matter_amplifier.slot.ore"),
                MatterAmplifierLayout.OUTPUT_SLOT.x(), MatterAmplifierLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.matter_amplifier.slot.dust"),
                MatterAmplifierLayout.DUST_OUTPUT_SLOT.x(), MatterAmplifierLayout.SLOT_LABEL_Y,
                COLOR_MUTED, false);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.matter_amplifier.conversion"),
                MatterAmplifierLayout.CONVERSION_TEXT_X,
                MatterAmplifierLayout.CONVERSION_TEXT_Y, COLOR_MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                MatterAmplifierLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                MatterAmplifierLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                MatterAmplifierLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                MatterAmplifierLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                MatterAmplifierLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private static int statusColor(MachineStatus status) {
        return MachineTheme.statusColor(status);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                MatterAmplifierLayout.MOTOR_SLOT, MatterAmplifierLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "matter_amplifier";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
