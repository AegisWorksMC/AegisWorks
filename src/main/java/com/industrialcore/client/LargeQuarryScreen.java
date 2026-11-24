package com.industrialcore.client;

import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.machine.LargeQuarryBalance;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.LargeQuarryLayout;
import com.industrialcore.menu.LargeQuarryMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.QuarryActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * GUI of the large quarry.
 *
 * <p>It answers what the machine is doing and why it is not: running/paused state, progress through
 * the fixed area, how many ores are selected, how many pickaxes are installed, the merged
 * enchantments and the FE cost of one ore block. The ore selection itself lives on a separate
 * picker screen ({@link LargeQuarryOrePickerScreen}), so a long ore list never squeezes the machine
 * panel.</p>
 */
public class LargeQuarryScreen extends AdaptiveContainerScreen<LargeQuarryMenu> {
    // Palette and primitives come from MachineTheme (Phase 42).
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_WARN = MachineTheme.STATUS_WARN;

    private Button startButton;

    public LargeQuarryScreen(LargeQuarryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LargeQuarryLayout.WIDTH;
        this.imageHeight = LargeQuarryLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000; // Hidden; the shared label pass draws it.
    }

    @Override
    protected void init() {
        super.init();
        this.startButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.start"),
                        button -> PacketDistributor.sendToServer(new QuarryActionPayload(
                                this.menu.getBlockPos(),
                                QuarryActionPayload.ACTION_TOGGLE_RUNNING)))
                .bounds(this.leftPos + LargeQuarryLayout.START_BUTTON.x(),
                        this.topPos + LargeQuarryLayout.START_BUTTON.y(),
                        LargeQuarryLayout.START_BUTTON.width(),
                        LargeQuarryLayout.START_BUTTON.height())
                .build());
        updateButtonLabel();

        Rect oreButton = LargeQuarryLayout.ORE_BUTTON;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.large_quarry.select_ores"),
                        button -> this.openOrePicker())
                .bounds(this.leftPos + oreButton.x(), this.topPos + oreButton.y(),
                        oreButton.width(), oreButton.height())
                .build());
    }

    private void openOrePicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new LargeQuarryOrePickerScreen(this.menu, this));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonLabel();
    }

    private void updateButtonLabel() {
        if (this.startButton != null) {
            this.startButton.setMessage(Component.translatable(this.menu.isRunning()
                    ? "gui.industrialcore.stop" : "gui.industrialcore.start"));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        MachineTheme.chrome(graphics, left, top, LargeQuarryLayout.HEIGHT,
                LargeQuarryLayout.BODY_BOTTOM,
                this.menu.getStatus().isPaused() ? MachineTheme.STATUS_WARN
                        : MachineTheme.STATUS_OK);

        // FE buffer.
        Rect energyBar = LargeQuarryLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        // Progress through the fixed area.
        Rect bar = LargeQuarryLayout.PROGRESS_BAR;
        int percent = UpgradeMath.progressPercent(this.menu.getCursor(), this.menu.getVolume());
        MachineTheme.progressBar(graphics, left + bar.x(), top + bar.y(), bar.width(),
                bar.height(), percent, 100);

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        QuarryStatus status = this.menu.getStatus();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(status.translationKey()),
                status.isPaused() ? COLOR_WARN : COLOR_TEXT);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LargeQuarryLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);

        if (status.isPaused()) {
            graphics.drawString(this.font,
                    MachineTheme.fit(this.font,
                            Component.translatable("gui.industrialcore.paused_hint"),
                            GuiMetrics.CONTENT),
                    GuiMetrics.SIDE, LargeQuarryLayout.PAUSED_HINT_Y, COLOR_MUTED, false);
        }

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.large_quarry.progress",
                        this.menu.getCursor(), this.menu.getVolume()),
                GuiMetrics.SIDE, LargeQuarryLayout.PROGRESS_TEXT_Y, COLOR_MUTED, false);
        // The mining cost shares the progress row: on the enchant line it pushed past the edge.
        Component cost = Component.translatable("gui.industrialcore.large_quarry.cost",
                this.menu.getMiningCost());
        graphics.drawString(this.font, cost,
                GuiMetrics.contentRight() - this.font.width(cost),
                LargeQuarryLayout.PROGRESS_TEXT_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.large_quarry.tools",
                        this.menu.getSelectedOreCount(), this.menu.getOreTotal(),
                        this.menu.getPickaxeCount(), this.menu.getToolSlots()),
                GuiMetrics.SIDE, LargeQuarryLayout.TOOLS_TEXT_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.large_quarry.enchants",
                        this.menu.getEfficiency(), this.menu.getFortune(),
                        this.menu.getSilkTouch()),
                GuiMetrics.SIDE, LargeQuarryLayout.ENCHANT_TEXT_Y, COLOR_TEXT, false);

        graphics.drawString(this.font, Component.translatable("gui.industrialcore.loot_title"),
                GuiMetrics.SIDE, LargeQuarryLayout.LOOT_TITLE_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.large_quarry.tool_title"),
                GuiMetrics.SIDE, LargeQuarryLayout.TOOL_TITLE_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.large_quarry.upgrade"),
                LargeQuarryLayout.UPGRADE_LABEL_X, LargeQuarryLayout.TOOL_TITLE_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.industrialcore.battery"),
                LargeQuarryLayout.BATTERY_LABEL_X, LargeQuarryLayout.BATTERY_LABEL_Y,
                COLOR_MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                LargeQuarryLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredSlot != null
                && this.hoveredSlot.index == LargeQuarryBlockEntity.TOOL_START + this.menu.getOpenToolSlots()) {
            // Phase 82: the motor slot reports what the installed motors do right now.
            java.util.List<Component> lines = new java.util.ArrayList<>();
            if (this.hoveredSlot.hasItem()) {
                lines.add(this.hoveredSlot.getItem().getHoverName());
            }
            lines.add(Component.translatable("gui.industrialcore.tooltip.motor"));
            lines.add(Component.translatable("gui.industrialcore.tooltip.upgrade_value",
                    this.menu.getMotorCount(), this.menu.getToolSlots(),
                    String.format(java.util.Locale.ROOT, "%.2f",
                            LargeQuarryBalance.motorSpeedMultiplier(this.menu.getMotorCount())))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            ClientMachineProgress.appendUpgradeTooltip(lines, "large_quarry");
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                null, LargeQuarryLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "large_quarry";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
