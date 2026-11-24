package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.AssemblerLayout;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.AssemblerMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * GUI of the assembler.
 *
 * <p>Phase 42: shared shell, one purple group around the three interchangeable ingredient slots,
 * a cyan result well and the theme's segmented FE bar and progress conduit.</p>
 */
public class AssemblerScreen extends AdaptiveContainerScreen<AssemblerMenu> {

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AssemblerLayout.WIDTH;
        this.imageHeight = AssemblerLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, AssemblerLayout.HEIGHT,
                AssemblerLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = AssemblerLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        // One group for all three ingredient slots: their order does not matter.
        Rect first = AssemblerLayout.INPUT_SLOT_1;
        Rect third = AssemblerLayout.INPUT_SLOT_3;
        MachineTheme.ingredientGroup(graphics, left, top, first.x() - 4, first.y() - 4,
                third.right() - first.x() + 8, 24);

        Rect progress = AssemblerLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        Rect out = AssemblerLayout.OUTPUT_SLOT;
        MachineTheme.outputSlot(graphics, left + out.x(), top + out.y());

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
                0 + GuiMetrics.SIDE, 0 + AssemblerLayout.ENERGY_TEXT_Y,
                CYAN_BRIGHT, false);

        Component input = Component.translatable("gui.industrialcore.assembler.inputs");
        graphics.drawString(this.font, input, 0 + AssemblerLayout.INPUT_SLOT_1.x(),
                0 + AssemblerLayout.SLOT_LABEL_Y, MUTED, false);
        Component output = Component.translatable("gui.industrialcore.assembler.output");
        graphics.drawString(this.font, output,
                0 + AssemblerLayout.OUTPUT_SLOT.x() + 8 - this.font.width(output) / 2,
                0 + AssemblerLayout.SLOT_LABEL_Y, MUTED, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                AssemblerLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                AssemblerLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                AssemblerLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                AssemblerLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                AssemblerLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AssemblerLayout.MOTOR_SLOT, AssemblerLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "assembler";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
