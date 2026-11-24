package com.industrialcore.client;

import com.industrialcore.blockentity.AutoBuilderBlockEntity;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.menu.AutoBuilderMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.AutoBuilderActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 94: Adaptive GUI for the Auto Builder ({@code auto_builder}).
 */
public class AutoBuilderScreen extends AdaptiveContainerScreen<AutoBuilderMenu> {

    public AutoBuilderScreen(AutoBuilderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeLayout.WIDTH;
        this.imageHeight = LifeLayout.BUILDER_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getBuilderState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeLayout.BUILDER_HEIGHT, LifeLayout.BUILDER_BODY_BOTTOM, accent);

        Rect eb = LifeLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect pb = LifeLayout.BUILDER_PROGRESS_BAR;
        MachineTheme.progressBar(g, left + pb.x(), top + pb.y(), pb.width(), pb.height(),
                this.menu.getPlacedProgress(), this.menu.getTotalBlocks());

        int lx = (int) Math.round(panelMouseX(mouseX) - left);
        int ly = (int) Math.round(panelMouseY(mouseY) - top);

        boolean run = this.menu.isRunning();
        drawButtonBg(g, left, top, LifeLayout.BUILDER_BTN_TOGGLE,
                LifeLayout.BUILDER_BTN_TOGGLE.contains(lx, ly),
                run ? 0xFF143C29 : 0xFF102533,
                run ? 0xFF5CFFA0 : 0xFF42C8FF);
        drawButtonBg(g, left, top, LifeLayout.BUILDER_BTN_MODE,
                LifeLayout.BUILDER_BTN_MODE.contains(lx, ly), 0xFF122334, 0xFF55D6FF);
        drawButtonBg(g, left, top, LifeLayout.BUILDER_BTN_RESET,
                LifeLayout.BUILDER_BTN_RESET.contains(lx, ly), 0xFF241B12, 0xFFFFB84D);

        for (Rect r : new Rect[]{
                LifeLayout.BUILDER_W_DEC, LifeLayout.BUILDER_W_INC,
                LifeLayout.BUILDER_D_DEC, LifeLayout.BUILDER_D_INC,
                LifeLayout.BUILDER_H_DEC, LifeLayout.BUILDER_H_INC
        }) {
            drawButtonBg(g, left, top, r, r.contains(lx, ly), 0xFF0E1D2A, 0xFF49B8E6);
        }

        Rect mg = LifeLayout.BUILDER_MAT_GROUP;
        g.fill(left + mg.x(), top + mg.y(), left + mg.right(), top + mg.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + mg.x(), top + mg.y(), mg.width(), mg.height(), 0xFFFFB84D, 0.28D);

        Rect sb = LifeLayout.BUILDER_STATS_BOX;
        g.fill(left + sb.x(), top + sb.y(), left + sb.right(), top + sb.bottom(), 0xFF070D14);
        MachineTheme.glowRect(g, left + sb.x(), top + sb.y(), sb.width(), sb.height(), accent, 0.25D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    private static void drawButtonBg(GuiGraphics g, int left, int top, Rect r,
                                     boolean hover, int baseBg, int borderCol) {
        int bx = left + r.x();
        int by = top + r.y();
        int bg = hover ? brighten(baseBg) : baseBg;
        if (hover) {
            MachineTheme.glowRect(g, bx, by, r.width(), r.height(), borderCol, 0.42D);
        }
        g.fill(bx, by, bx + r.width(), by + r.height(), bg);
        g.fill(bx, by, bx + r.width(), by + 1, borderCol);
        g.fill(bx, by + r.height() - 1, bx + r.width(), by + r.height(), borderCol);
        g.fill(bx, by, bx + 1, by + r.height(), borderCol);
        g.fill(bx + r.width() - 1, by, bx + r.width(), by + r.height(), borderCol);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 18);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 22);
        int b = Math.min(255, (argb & 0xFF) + 26);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getBuilderState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.literal(this.menu.getPlacedProgress() + "/" + this.menu.getTotalBlocks()),
                accent);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        int pct = Math.min(100, (this.menu.getPlacedProgress() * 100) / Math.max(1, this.menu.getTotalBlocks()));
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.auto_builder.progress_line",
                        this.menu.getPlacedProgress(), this.menu.getTotalBlocks(), pct),
                GuiMetrics.SIDE, LifeLayout.BUILDER_INFO_Y, GuiMetrics.CONTENT, MachineTheme.TEXT);

        boolean run = this.menu.isRunning();
        drawCentered(g, LifeLayout.BUILDER_BTN_TOGGLE,
                Component.translatable(run
                        ? "gui.industrialcore.auto_builder.btn_stop"
                        : "gui.industrialcore.auto_builder.btn_start"),
                run ? 0xFFE6FFF2 : 0xFFE2F7FF);
        drawCentered(g, LifeLayout.BUILDER_BTN_MODE,
                Component.translatable(this.menu.getMode().translationKey()), 0xFFE2F7FF);
        drawCentered(g, LifeLayout.BUILDER_BTN_RESET,
                Component.translatable("gui.industrialcore.auto_builder.btn_reset"), 0xFFFFE5B8);

        g.drawString(this.font, "W", LifeLayout.BUILDER_W_LABEL_X, LifeLayout.BUILDER_DIM_Y + 2, MachineTheme.MUTED, false);
        drawCentered(g, LifeLayout.BUILDER_W_DEC, Component.literal("-"), 0xFFE2F7FF);
        drawCenteredText(g, LifeLayout.BUILDER_W_VAL_CX, LifeLayout.BUILDER_DIM_Y + 2,
                Integer.toString(this.menu.getBuildWidth()), MachineTheme.TEXT);
        drawCentered(g, LifeLayout.BUILDER_W_INC, Component.literal("+"), 0xFFE2F7FF);

        g.drawString(this.font, "D", LifeLayout.BUILDER_D_LABEL_X, LifeLayout.BUILDER_DIM_Y + 2, MachineTheme.MUTED, false);
        drawCentered(g, LifeLayout.BUILDER_D_DEC, Component.literal("-"), 0xFFE2F7FF);
        drawCenteredText(g, LifeLayout.BUILDER_D_VAL_CX, LifeLayout.BUILDER_DIM_Y + 2,
                Integer.toString(this.menu.getBuildDepth()), MachineTheme.TEXT);
        drawCentered(g, LifeLayout.BUILDER_D_INC, Component.literal("+"), 0xFFE2F7FF);

        g.drawString(this.font, "H", LifeLayout.BUILDER_H_LABEL_X, LifeLayout.BUILDER_DIM_Y + 2, MachineTheme.MUTED, false);
        drawCentered(g, LifeLayout.BUILDER_H_DEC, Component.literal("-"), 0xFFE2F7FF);
        drawCenteredText(g, LifeLayout.BUILDER_H_VAL_CX, LifeLayout.BUILDER_DIM_Y + 2,
                Integer.toString(this.menu.getBuildHeight()), MachineTheme.TEXT);
        drawCentered(g, LifeLayout.BUILDER_H_INC, Component.literal("+"), 0xFFE2F7FF);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.auto_builder.materials"),
                LifeLayout.BUILDER_MAT_LABEL_X, LifeLayout.BUILDER_MAT_LABEL_Y, 60, MachineTheme.MUTED);

        int maxW = LifeLayout.BUILDER_STATS_BOX.width() - 8;
        int motors = this.menu.getMotorCount();
        int batch = LifeLogic.builderBatchSize(motors);
        int interval = LifeLogic.builderInterval(motors, 0);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.literal(this.menu.getBuildWidth() + "x" + this.menu.getBuildDepth()
                        + "x" + this.menu.getBuildHeight()),
                LifeLayout.BUILDER_STATS_TEXT_X, LifeLayout.BUILDER_STATS_ROW_1_Y, maxW, 0xFFFFD480);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.auto_builder.speed", batch, interval),
                LifeLayout.BUILDER_STATS_TEXT_X, LifeLayout.BUILDER_STATS_ROW_2_Y, maxW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.auto_builder.safe_tag"),
                LifeLayout.BUILDER_STATS_TEXT_X, LifeLayout.BUILDER_STATS_ROW_3_Y, maxW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLbl,
                LifeLayout.BUILDER_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeLayout.BUILDER_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLbl,
                LifeLayout.BUILDER_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeLayout.BUILDER_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeLayout.BUILDER_BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawCentered(GuiGraphics g, Rect r, Component label, int color) {
        Component fitted = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = r.x() + (r.width() - this.font.width(fitted)) / 2;
        int ty = r.y() + (r.height() - 8) / 2 + 1;
        g.drawString(this.font, fitted, tx, ty, color, false);
    }

    private void drawCenteredText(GuiGraphics g, int cx, int y, String txt, int color) {
        g.drawString(this.font, txt, cx - this.font.width(txt) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double lx = panelMouseX(mouseX) - this.leftPos;
            double ly = panelMouseY(mouseY) - this.topPos;
            int action = hitAction(lx, ly);
            if (action >= 0) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new AutoBuilderActionPayload(this.menu.getBlockPos(), action));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int hitAction(double lx, double ly) {
        if (LifeLayout.BUILDER_BTN_TOGGLE.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_TOGGLE_RUN;
        if (LifeLayout.BUILDER_BTN_MODE.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_CYCLE_MODE;
        if (LifeLayout.BUILDER_BTN_RESET.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_RESET;
        if (LifeLayout.BUILDER_W_DEC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_W_DEC;
        if (LifeLayout.BUILDER_W_INC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_W_INC;
        if (LifeLayout.BUILDER_D_DEC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_D_DEC;
        if (LifeLayout.BUILDER_D_INC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_D_INC;
        if (LifeLayout.BUILDER_H_DEC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_H_DEC;
        if (LifeLayout.BUILDER_H_INC.contains(lx, ly)) return AutoBuilderActionPayload.ACTION_H_INC;
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeLayout.BUILDER_MOTOR_SLOT, LifeLayout.BUILDER_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return AutoBuilderBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeLogic.BUILDER_STATE_BUILDING -> 0xFF5CFFA0;
            case LifeLogic.BUILDER_STATE_COMPLETED -> 0xFF42F2FF;
            case LifeLogic.BUILDER_STATE_NO_MATERIAL -> MachineTheme.STATUS_WARN;
            case LifeLogic.BUILDER_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> MachineTheme.MUTED;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeLogic.BUILDER_STATE_BUILDING -> "gui.industrialcore.auto_builder.status.building";
            case LifeLogic.BUILDER_STATE_COMPLETED -> "gui.industrialcore.auto_builder.status.completed";
            case LifeLogic.BUILDER_STATE_NO_MATERIAL -> "gui.industrialcore.auto_builder.status.no_material";
            case LifeLogic.BUILDER_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
