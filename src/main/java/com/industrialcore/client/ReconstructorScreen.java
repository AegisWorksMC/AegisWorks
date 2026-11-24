package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.ReconstructorActions;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.ReconstructorLayout;
import com.industrialcore.menu.ReconstructorMenu;
import com.industrialcore.network.ReconstructorActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * GUI of the matter reconstructor (Phase 87: face buttons removed, Motor &amp; Battery slots added).
 */
public class ReconstructorScreen extends AdaptiveContainerScreen<ReconstructorMenu> {
    private static final int COLOR_BORDER = MachineTheme.LINE_SOFT;
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_PROGRESS = MachineTheme.STATUS_OK;
    private static final int COLOR_BUTTON = MachineTheme.PANEL_TOP;
    private static final int COLOR_BUTTON_SELECTED = MachineTheme.PURPLE_DIM;

    private Button analyzeButton;
    private Button cycleButton;

    public ReconstructorScreen(ReconstructorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ReconstructorLayout.WIDTH;
        this.imageHeight = ReconstructorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        Rect analyze = ReconstructorLayout.ANALYZE_BUTTON;
        this.analyzeButton = Button.builder(
                        Component.translatable("gui.industrialcore.reconstructor.analyze"),
                        pressed -> send(ReconstructorActions.ANALYZE, ""))
                .bounds(this.leftPos + analyze.x(), this.topPos + analyze.y(),
                        analyze.width(), analyze.height())
                .build();
        this.addRenderableWidget(this.analyzeButton);

        Rect cycle = ReconstructorLayout.CYCLE_BUTTON;
        this.cycleButton = Button.builder(
                        Component.translatable("gui.industrialcore.reconstructor.cycle"),
                        pressed -> send(ReconstructorActions.CYCLE_MINERAL, ""))
                .bounds(this.leftPos + cycle.x(), this.topPos + cycle.y(),
                        cycle.width(), cycle.height())
                .build();
        this.addRenderableWidget(this.cycleButton);
    }

    private void send(int action, String mineral) {
        PacketDistributor.sendToServer(new ReconstructorActionPayload(
                this.menu.getBlockPos(), action, mineral));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, ReconstructorLayout.HEIGHT,
                ReconstructorLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));

        Rect energyBar = ReconstructorLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect progress = ReconstructorLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        for (Rect in : new Rect[] {ReconstructorLayout.ANALYSIS_SLOT,
                ReconstructorLayout.MATERIAL_SLOT, ReconstructorLayout.CRYSTAL_SLOT}) {
            MachineTheme.ingredientGroup(graphics, left, top, in.x() - 4, in.y() - 4, 24, 24);
        }
        MachineTheme.outputWell(graphics, left + ReconstructorLayout.OUTPUT_SLOT.x(),
                top + ReconstructorLayout.OUTPUT_SLOT.y());

        List<String> minerals = this.menu.getUnlockedMinerals();
        String selected = this.menu.getSelectedMineral();
        for (int index = 0; index < ReconstructorLayout.MINERAL_BUTTON_COUNT; index++) {
            Rect cell = ReconstructorLayout.mineralButton(index);
            if (index >= minerals.size()) {
                graphics.fill(left + cell.x(), top + cell.y(), left + cell.right(),
                        top + cell.bottom(), 0xFF161C24);
                continue;
            }
            String mineral = minerals.get(index);
            int fill = mineral.equals(selected) ? COLOR_BUTTON_SELECTED : COLOR_BUTTON;
            graphics.fill(left + cell.x(), top + cell.y(), left + cell.right(), top + cell.bottom(),
                    fill);
            graphics.fill(left + cell.x(), top + cell.y(), left + cell.right(), top + cell.y() + 1,
                    COLOR_BORDER);
            graphics.fill(left + cell.x(), top + cell.bottom() - 1, left + cell.right(),
                    top + cell.bottom(), COLOR_BORDER);
        }

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
                GuiMetrics.SIDE, ReconstructorLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.reconstructor.slot.analysis"),
                ReconstructorLayout.ANALYSIS_LABEL_X, ReconstructorLayout.SLOT_LABEL_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.reconstructor.slot.material"),
                ReconstructorLayout.MATERIAL_LABEL_X, ReconstructorLayout.SLOT_LABEL_Y, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.reconstructor.slot.crystal"),
                ReconstructorLayout.CRYSTAL_LABEL_X, ReconstructorLayout.SLOT_LABEL_Y, COLOR_MUTED, false);
        Component outputLabel = Component.translatable(
                "gui.industrialcore.reconstructor.slot.output");
        graphics.drawString(this.font, outputLabel,
                ReconstructorLayout.WIDTH - 8 - this.font.width(outputLabel),
                ReconstructorLayout.SLOT_LABEL_Y, COLOR_MUTED, false);

        String selected = this.menu.getSelectedMineral();
        if (!selected.isEmpty()) {
            Component selectedText = Component.translatable("gui.industrialcore.reconstructor.selected",
                    mineralName(selected));
            graphics.drawString(this.font, MachineTheme.fit(this.font, selectedText,
                            ReconstructorLayout.PROGRESS_TEXT_MAX),
                    ReconstructorLayout.PROGRESS_TEXT_X, ReconstructorLayout.PROGRESS_TEXT_Y,
                    COLOR_PROGRESS, false);
        }

        List<String> minerals = this.menu.getUnlockedMinerals();
        for (int index = 0; index < minerals.size()
                && index < ReconstructorLayout.MINERAL_BUTTON_COUNT; index++) {
            Rect cell = ReconstructorLayout.mineralButton(index);
            Component label = mineralName(minerals.get(index));
            graphics.drawString(this.font, label,
                    cell.x() + Math.max(1, (cell.width() - this.font.width(label)) / 2),
                    cell.y() + 2, COLOR_TEXT, false);
        }

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                ReconstructorLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                ReconstructorLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                ReconstructorLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                ReconstructorLayout.UPGRADE_LABEL_Y, COLOR_MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                ReconstructorLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private Component mineralName(String mineral) {
        String key = "gui.industrialcore.reconstructor.mineral." + mineral;
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.literal(mineral);
    }

    private static int statusColor(MachineStatus status) {
        return MachineTheme.statusColor(status);
    }

    private int mineralAt(double mouseX, double mouseY) {
        double x = panelMouseX(mouseX) - this.leftPos;
        double y = panelMouseY(mouseY) - this.topPos;
        List<String> minerals = this.menu.getUnlockedMinerals();
        for (int index = 0; index < minerals.size()
                && index < ReconstructorLayout.MINERAL_BUTTON_COUNT; index++) {
            Rect rect = ReconstructorLayout.mineralButton(index);
            if (x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom()) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mineral = mineralAt(mouseX, mouseY);
        if (mineral >= 0) {
            List<String> minerals = this.menu.getUnlockedMinerals();
            if (mineral < minerals.size()) {
                send(ReconstructorActions.SELECT_MINERAL, minerals.get(mineral));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.analyzeButton != null) {
            this.analyzeButton.active = this.menu.canAnalyze();
        }
        if (this.cycleButton != null) {
            this.cycleButton.active = !this.menu.getUnlockedMinerals().isEmpty();
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                ReconstructorLayout.MOTOR_SLOT, ReconstructorLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "reconstructor";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
