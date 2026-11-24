package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuantumCompressorLayout;
import com.industrialcore.menu.QuantumCompressorMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN;
import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.CYAN_DEEP;
import static com.industrialcore.client.MachineTheme.MUTED;
import static com.industrialcore.client.MachineTheme.STATUS_OK;

/**
 * GUI of the quantum compressor (64-item batch crusher).
 *
 * <p>Phase 42: shared shell and gauges. The four output wells sit in one cyan group, because a
 * 64-batch of a two-dust recipe always spreads over more than one slot - the group says "these four
 * are one result", which a ring per slot would not.</p>
 */
public class QuantumCompressorScreen extends AdaptiveContainerScreen<QuantumCompressorMenu> {

    public QuantumCompressorScreen(QuantumCompressorMenu menu, Inventory playerInventory,
                                   Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = QuantumCompressorLayout.WIDTH;
        this.imageHeight = QuantumCompressorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, QuantumCompressorLayout.HEIGHT,
                QuantumCompressorLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = QuantumCompressorLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect input = QuantumCompressorLayout.INPUT_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, input.x() - 4, input.y() - 4, 24, 24);

        Rect progress = QuantumCompressorLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        // One cyan group around the 2x2 result block.
        Rect firstOut = QuantumCompressorLayout.OUTPUT_SLOT_1;
        Rect lastOut = QuantumCompressorLayout.outputSlot(3);
        int gx = left + firstOut.x() - 4;
        int gy = top + firstOut.y() - 4;
        int gw = lastOut.right() - firstOut.x() + 8;
        int gh = lastOut.bottom() - firstOut.y() + 8;
        graphics.fill(gx, gy, gx + gw, gy + 1, CYAN_DEEP);
        graphics.fill(gx, gy + gh - 1, gx + gw, gy + gh, CYAN_DEEP);
        graphics.fill(gx, gy, gx + 1, gy + gh, CYAN_DEEP);
        graphics.fill(gx + gw - 1, gy, gx + gw, gy + gh, CYAN_DEEP);
        MachineTheme.bracket(graphics, gx - 1, gy - 1, 1, 1, 5, CYAN);
        MachineTheme.bracket(graphics, gx + gw, gy - 1, -1, 1, 5, CYAN);
        MachineTheme.bracket(graphics, gx - 1, gy + gh, 1, -1, 5, CYAN);
        MachineTheme.bracket(graphics, gx + gw, gy + gh, -1, -1, 5, CYAN);

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineStatus status = this.menu.getStatus();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(status.translationKey()), MachineTheme.statusColor(status));

        int total = this.menu.getOperationTime();
        int percent = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        MachineTheme.statusValue(this.font, graphics, 0, 0,
                Component.translatable("gui.industrialcore.machine.progress", percent), MUTED);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                        this.menu.getEnergyCapacity()),
                0 + GuiMetrics.SIDE, 0 + QuantumCompressorLayout.ENERGY_TEXT_Y,
                CYAN_BRIGHT, false);

        Component input = Component.translatable("gui.industrialcore.quantum_compressor.input");
        graphics.drawString(this.font, input,
                0 + QuantumCompressorLayout.INPUT_SLOT.x(),
                0 + QuantumCompressorLayout.SLOT_LABEL_Y, MUTED, false);
        Component output = Component.translatable("gui.industrialcore.quantum_compressor.outputs");
        graphics.drawString(this.font, output,
                0 + QuantumCompressorLayout.OUTPUT_SLOT_1.x(),
                0 + QuantumCompressorLayout.SLOT_LABEL_Y, MUTED, false);

        int batch = this.menu.getBatch();
        Component batchText = batch > 0
                ? Component.translatable("gui.industrialcore.quantum_compressor.batch", batch,
                this.menu.getEnergyPerTick())
                : Component.translatable("gui.industrialcore.quantum_compressor.idle");
        graphics.drawString(this.font, batchText, 0 + GuiMetrics.SIDE,
                0 + QuantumCompressorLayout.BATCH_TEXT_Y,
                batch > 0 ? STATUS_OK : MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                QuantumCompressorLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                QuantumCompressorLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                QuantumCompressorLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                QuantumCompressorLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                QuantumCompressorLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                QuantumCompressorLayout.MOTOR_SLOT, QuantumCompressorLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "quantum_compressor";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
