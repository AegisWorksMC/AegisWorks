package com.industrialcore.client;

import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.ProcessingLayout;
import com.industrialcore.menu.ProcessingMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * GUI of the six processing machines (crusher, electric furnace, electrolyzer, precision crusher,
 * metal press, fast crusher).
 *
 * <p>Phase 87 removes the 6-face configuration row and adds dedicated Motor and Battery upgrade
 * slots with unified machine-level cap tooltips.</p>
 */
public class ProcessingScreen extends AdaptiveContainerScreen<ProcessingMenu> {
    public ProcessingScreen(ProcessingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ProcessingLayout.WIDTH;
        this.imageHeight = ProcessingLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, ProcessingLayout.HEIGHT,
                ProcessingLayout.BODY_BOTTOM, statusColor(this.menu.getStatus()));

        Rect energyBar = ProcessingLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect progress = ProcessingLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getOperationTime());

        // Input well (purple = ingredient) and result well (cyan = product).
        Rect in = ProcessingLayout.INPUT_SLOT;
        MachineTheme.ingredientGroup(graphics, left, top, in.x() - 4, in.y() - 4, 24, 24);
        Rect out = ProcessingLayout.OUTPUT_SLOT;
        MachineTheme.outputSlot(graphics, left + out.x(), top + out.y());

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
                Component.translatable("gui.industrialcore.machine.progress", percent), MUTED);

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                        this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, ProcessingLayout.ENERGY_TEXT_Y,
                CYAN_BRIGHT, false);

        // Phase 99: Show Limit-Break hyper-parallel multiplier in neon gold on the right side
        String mId = skillTreeMachineId();
        if (mId != null) {
            int lvl = ClientMachineProgress.level(mId);
            var br = com.industrialcore.level.MachineSkillTree.branchOf(mId);
            boolean lb = br != null && ClientMachineProgress.isBranchLimitBroken(br);
            int par = com.industrialcore.level.MachineLevelMath.parallelBatchSize(lvl, lb);
            if (par > 1) {
                Component parTxt = Component.translatable("gui.industrialcore.machine.parallel_badge", par);
                graphics.drawString(this.font, parTxt,
                        ProcessingLayout.WIDTH - GuiMetrics.SIDE - this.font.width(parTxt),
                        ProcessingLayout.ENERGY_TEXT_Y, 0xFFFFD166, false);
            }
        }

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 64);
        graphics.drawString(this.font, motorLbl,
                ProcessingLayout.MOTOR_CENTER_X - this.font.width(motorLbl) / 2,
                ProcessingLayout.UPGRADE_LABEL_Y, MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 64);
        graphics.drawString(this.font, battLbl,
                ProcessingLayout.BATTERY_CENTER_X - this.font.width(battLbl) / 2,
                ProcessingLayout.UPGRADE_LABEL_Y, MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                ProcessingLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private static int statusColor(MachineStatus status) {
        return MachineTheme.statusColor(status);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                ProcessingLayout.MOTOR_SLOT, ProcessingLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected BlockPos upgradeDockPos() {
        return this.menu.getBlockPos();
    }

    @Override
    protected String skillTreeMachineId() {
        var be = this.menu.getBlockEntity();
        if (be != null) {
            return be.getKind().id();
        }
        var type = this.menu.getType();
        if (type == com.industrialcore.registry.ModMenus.ELECTRIC_FURNACE.get()) return "electric_furnace";
        if (type == com.industrialcore.registry.ModMenus.CRUSHER.get()) return "crusher";
        if (type == com.industrialcore.registry.ModMenus.ELECTROLYZER.get()) return "electrolyzer";
        if (type == com.industrialcore.registry.ModMenus.PRECISION_CRUSHER.get()) return "precision_crusher";
        if (type == com.industrialcore.registry.ModMenus.FAST_CRUSHER.get()) return "fast_crusher";
        if (type == com.industrialcore.registry.ModMenus.METAL_PRESS.get()) return "metal_press";
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
