package com.industrialcore.client;

import com.industrialcore.menu.EnergyBufferLayout;
import com.industrialcore.menu.EnergyBufferMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.MUTED;
import static com.industrialcore.client.MachineTheme.TEXT;

/**
 * GUI of the three energy buffers.
 *
 * <p>Phase 43: the buffer is the panel where one number matters, so the shared chrome gives the
 * FE bar the largest gauge in the mod (22 px tall, full content width) and the strip carries the
 * fill percentage. Everything else is one line of text at a time.</p>
 */
public class EnergyBufferScreen extends AdaptiveContainerScreen<EnergyBufferMenu> {
    public EnergyBufferScreen(EnergyBufferMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = EnergyBufferLayout.WIDTH;
        this.imageHeight = EnergyBufferLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, EnergyBufferLayout.HEIGHT,
                EnergyBufferLayout.BODY_BOTTOM, MachineTheme.CYAN);

        Rect bar = EnergyBufferLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + bar.x(), top + bar.y(), bar.width(),
                bar.height(), this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable("gui.industrialcore.buffer.state"), MachineTheme.CYAN);
        MachineTheme.statusValue(this.font, graphics, 0, 0,
                Component.literal(fillPercent() + "%"), CYAN_BRIGHT);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                        this.menu.getEnergyCapacity()),
                0 + GuiMetrics.SIDE, 0 + EnergyBufferLayout.ENERGY_TEXT_Y,
                CYAN_BRIGHT, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.buffer.push", this.menu.getPushPerTick()),
                0 + GuiMetrics.SIDE, 0 + EnergyBufferLayout.PUSH_TEXT_Y,
                TEXT, false);
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.buffer.hint"),
                0 + EnergyBufferLayout.HINT_X,
                0 + EnergyBufferLayout.HINT_TEXT_Y, MUTED, false);
        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                EnergyBufferLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private int fillPercent() {
        int capacity = this.menu.getEnergyCapacity();
        return capacity <= 0 ? 0 : Math.min(100,
                (int) ((long) this.menu.getEnergyStored() * 100 / capacity));
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected int skillTreeDefaultTab() {
        return com.industrialcore.level.SkillTreeLayout.TAB_INFRA;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
