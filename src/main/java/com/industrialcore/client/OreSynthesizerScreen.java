package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.OreSynthesizerLayout;
import com.industrialcore.menu.OreSynthesizerMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.CYAN_DEEP;
import static com.industrialcore.client.MachineTheme.LINE_SOFT;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * Control panel of the ore synthesizer.
 *
 * <p>Phase 43 puts the panel on {@link GuiMetrics}: the machine name in the header band, the state
 * in the shared status strip, and one recessed bay in the body. Inside the bay the eye travels left
 * to right — FE column, ingredient grid, progress conduit, result well — and the stored FE sits on
 * the shelf under the grid. Everything is drawn through {@link MachineTheme}, so the panel is the
 * same panel as the rest of the family with a different bay.</p>
 */
public class OreSynthesizerScreen extends AdaptiveContainerScreen<OreSynthesizerMenu> {
    public OreSynthesizerScreen(OreSynthesizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = OreSynthesizerLayout.WIDTH;
        this.imageHeight = OreSynthesizerLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineStatus status = status();

        MachineTheme.chrome(graphics, left, top, OreSynthesizerLayout.HEIGHT,
                OreSynthesizerLayout.BODY_BOTTOM, statusColor(status));

        Rect bay = OreSynthesizerLayout.BAY;
        MachineTheme.bay(graphics, left, top, bay.x(), bay.y(), bay.width(), bay.height());

        // Shelf that separates the chamber row from the read-out row underneath.
        int shelf = top + OreSynthesizerLayout.SHELF_Y;
        graphics.fill(left + bay.x() + 4, shelf, left + bay.right() - 4, shelf + 1, LINE_SOFT);
        graphics.fill(left + bay.x() + 4, shelf, left + bay.x() + 14, shelf + 1, CYAN_DEEP);

        Rect column = OreSynthesizerLayout.ENERGY_BAR;
        MachineTheme.energyColumn(graphics, left + column.x(), top + column.y(),
                column.width(), column.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        // Ingredient chamber: one purple group around the 4x3 grid.
        Rect group = OreSynthesizerLayout.INPUT_GROUP;
        MachineTheme.ingredientGroup(graphics, left, top, group.x(), group.y(),
                group.width(), group.height());
        for (int index = 0; index < OreSynthesizerLayout.GRID_COLUMNS
                * OreSynthesizerLayout.GRID_ROWS; index++) {
            Rect cell = OreSynthesizerLayout.inputSlot(index);
            MachineTheme.slotWell(graphics, left + cell.x(), top + cell.y());
        }

        Rect progress = OreSynthesizerLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(), this.menu.getTime());

        Rect out = OreSynthesizerLayout.OUTPUT_SLOT;
        MachineTheme.outputSlot(graphics, left + out.x(), top + out.y());

        // Upgrade slots (motor & battery) + player inventory wells; chamber slots are framed above.
        for (int index = OreSynthesizerLayout.GRID_COLUMNS * OreSynthesizerLayout.GRID_ROWS + 1;
             index < this.menu.slots.size(); index++) {
            MachineTheme.slotWell(graphics, left + this.menu.slots.get(index).x,
                    top + this.menu.slots.get(index).y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineStatus status = status();
        int stored = this.menu.getEnergyStored();
        int capacity = this.menu.getEnergyCapacity();
        int percent = capacity <= 0 ? 0 : Math.min(100, stored * 100 / capacity);
        Component percentText = Component.literal(percent + "%");

        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(status.translationKey()), statusColor(status),
                this.font.width(percentText));
        MachineTheme.statusValue(this.font, graphics, 0, 0, percentText, MUTED);

        graphics.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        stored, capacity),
                OreSynthesizerLayout.ENERGY_TEXT_X, OreSynthesizerLayout.ENERGY_TEXT_Y,
                CYAN_BRIGHT, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                OreSynthesizerLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                OreSynthesizerLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                OreSynthesizerLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                OreSynthesizerLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                OreSynthesizerLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private MachineStatus status() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = Math.max(0, Math.min(values.length - 1, this.menu.getStatusOrdinal()));
        return values[ordinal];
    }

    private int statusColor(MachineStatus status) {
        if (status == MachineStatus.WORKING) {
            return MachineTheme.STATUS_OK;
        }
        if (status.isProblem()) {
            return status == MachineStatus.NO_ENERGY ? MachineTheme.STATUS_BAD
                    : MachineTheme.STATUS_WARN;
        }
        return MUTED;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                OreSynthesizerLayout.MOTOR_SLOT, OreSynthesizerLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "ore_synthesizer";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
