package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.PhaseReactorLayout;
import com.industrialcore.menu.PhaseReactorMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.CYAN_DEEP;
import static com.industrialcore.client.MachineTheme.CYAN_DIM;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * GUI of the phase reactor (mythic-tier 3-to-1 machine).
 *
 * <p>Phase 42: the reactor keeps its own identity through the purple ingredient group and the
 * purple result frame, but the housing, header, gauge housings and slot wells are the shared ones,
 * so the mythic machine no longer looks like a different mod.</p>
 */
public class PhaseReactorScreen extends AdaptiveContainerScreen<PhaseReactorMenu> {

    public PhaseReactorScreen(PhaseReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PhaseReactorLayout.WIDTH;
        this.imageHeight = PhaseReactorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, PhaseReactorLayout.HEIGHT,
                PhaseReactorLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = PhaseReactorLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect first = PhaseReactorLayout.INPUT_SLOT_1;
        Rect third = PhaseReactorLayout.INPUT_SLOT_3;
        MachineTheme.ingredientGroup(graphics, left, top, first.x() - 4, first.y() - 4,
                third.right() - first.x() + 8, 24);

        Rect progress = PhaseReactorLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        // Result well: purple here, because the reactor's product is a phase-shifted material.
        Rect out = PhaseReactorLayout.OUTPUT_SLOT;
        int ox = left + out.x();
        int oy = top + out.y();
        graphics.fill(ox - 4, oy - 4, ox + 20, oy + 20, CYAN_DEEP);
        MachineTheme.slotWell(graphics, ox, oy);
        graphics.fill(ox - 4, oy - 4, ox + 20, oy - 3, CYAN_DIM);
        graphics.fill(ox - 4, oy + 19, ox + 20, oy + 20, CYAN_DIM);
        graphics.fill(ox - 4, oy - 3, ox - 3, oy + 19, CYAN_DIM);
        graphics.fill(ox + 19, oy - 3, ox + 20, oy + 19, CYAN_DIM);
        MachineTheme.bracket(graphics, ox - 3, oy - 3, 1, 1, 4, CYAN_BRIGHT);
        MachineTheme.bracket(graphics, ox + 18, oy - 3, -1, 1, 4, CYAN_BRIGHT);
        MachineTheme.bracket(graphics, ox - 3, oy + 18, 1, -1, 4, CYAN_BRIGHT);
        MachineTheme.bracket(graphics, ox + 18, oy + 18, -1, -1, 4, CYAN_BRIGHT);

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
                GuiMetrics.SIDE, PhaseReactorLayout.ENERGY_TEXT_Y, CYAN_BRIGHT, false);

        Component input = Component.translatable("gui.industrialcore.phase_reactor.inputs");
        graphics.drawString(this.font, input, PhaseReactorLayout.INPUT_SLOT_1.x(),
                PhaseReactorLayout.SLOT_LABEL_Y, MUTED, false);
        Component output = Component.translatable("gui.industrialcore.phase_reactor.output");
        graphics.drawString(this.font, output,
                PhaseReactorLayout.OUTPUT_SLOT.x() + 8 - this.font.width(output) / 2,
                PhaseReactorLayout.SLOT_LABEL_Y, MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                PhaseReactorLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                PhaseReactorLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                PhaseReactorLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                PhaseReactorLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                PhaseReactorLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                PhaseReactorLayout.MOTOR_SLOT, PhaseReactorLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "phase_reactor";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
