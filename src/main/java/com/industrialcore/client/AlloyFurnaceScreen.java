package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.AlloyFurnaceLayout;
import com.industrialcore.menu.AlloyFurnaceMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.HEAT;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * GUI of the alloy furnace (the one machine that burns fuel instead of FE).
 *
 * <p>Phase 43: the two interchangeable ingredients share one purple group on the left, the result
 * well sits on the right, and the fuel well plus the burn gauge own the second half of the body.
 * The state and the progress moved into the shared status strip, which frees the body for the
 * slot flow.</p>
 */
public class AlloyFurnaceScreen extends AdaptiveContainerScreen<AlloyFurnaceMenu> {
    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = AlloyFurnaceLayout.WIDTH;
        this.imageHeight = AlloyFurnaceLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, AlloyFurnaceLayout.HEIGHT,
                AlloyFurnaceLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        // Two interchangeable ingredients get one purple group, the fuel its own well.
        MachineTheme.ingredientGroup(graphics, left, top, AlloyFurnaceLayout.FIRST_SLOT.x() - 4,
                AlloyFurnaceLayout.FIRST_SLOT.y() - 4,
                AlloyFurnaceLayout.SECOND_SLOT.right() - AlloyFurnaceLayout.FIRST_SLOT.x() + 8, 24);
        Rect fuel = AlloyFurnaceLayout.FUEL_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, fuel.x() - 4, fuel.y() - 4, 24, 24);

        Rect progress = AlloyFurnaceLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        Rect result = AlloyFurnaceLayout.OUTPUT_SLOT;
        MachineTheme.outputSlot(graphics, left + result.x(), top + result.y());

        Rect burn = AlloyFurnaceLayout.BURN_BAR;
        MachineTheme.burnBar(graphics, left + burn.x(), top + burn.y(), burn.width(),
                burn.height(), this.menu.getBurnTime(), this.menu.getBurnTimeTotal());

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

        Component fuelText = Component.translatable("gui.industrialcore.alloy.fuel",
                this.menu.getBurnTime(), this.menu.getBurnTimeTotal());
        graphics.drawString(this.font, fuelText, 0 + AlloyFurnaceLayout.BURN_TEXT_X,
                0 + AlloyFurnaceLayout.BURN_TEXT_Y,
                this.menu.getBurnTime() > 0 ? HEAT : MUTED, false);

        // One label above the interchangeable pair, one to the right of the fuel well and one
        // above the result: labels never share a row with a bar or with another label.
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.alloy.slot.inputs"),
                AlloyFurnaceLayout.FIRST_SLOT.x(), AlloyFurnaceLayout.INPUT_LABEL_Y, MUTED, false);
        drawSlotLabel(graphics, "gui.industrialcore.alloy.slot.fuel",
                AlloyFurnaceLayout.FUEL_SLOT);
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.alloy.slot.output"),
                AlloyFurnaceLayout.OUTPUT_SLOT.x(), AlloyFurnaceLayout.OUTPUT_LABEL_Y, MUTED,
                false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                AlloyFurnaceLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                AlloyFurnaceLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                AlloyFurnaceLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                AlloyFurnaceLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                AlloyFurnaceLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                AlloyFurnaceLayout.MOTOR_SLOT, AlloyFurnaceLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), true)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void drawSlotLabel(GuiGraphics graphics, String key, Rect slot) {
        // Called from renderLabels, whose pose is already translated by (leftPos, topPos).
        graphics.drawString(this.font, Component.translatable(key),
                slot.x() + AlloyFurnaceLayout.SLOT_LABEL_OFFSET_X,
                slot.y() + AlloyFurnaceLayout.SLOT_LABEL_OFFSET_Y, MUTED, false);
    }

    @Override
    protected String skillTreeMachineId() {
        return "alloy_furnace";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
