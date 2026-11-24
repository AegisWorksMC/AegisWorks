package com.industrialcore.client;

import com.industrialcore.blockentity.MobDuplicatorBlockEntity;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.MobDuplicatorMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.trap.TrapWave2Layout;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Phase 93: Adaptive GUI for the Mob Automatic Spawner ({@code mob_duplicator}).
 */
public class MobDuplicatorScreen extends AdaptiveContainerScreen<MobDuplicatorMenu> {

    public MobDuplicatorScreen(MobDuplicatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TrapWave2Layout.WIDTH;
        this.imageHeight = TrapWave2Layout.DUP_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int dupState = this.menu.getDupState();
        int accent = switch (dupState) {
            case TrapWave2Logic.DUP_STATE_WORKING -> 0xFFC06BFF;
            case TrapWave2Logic.DUP_STATE_CROWDED -> 0xFFFFB03A;
            case TrapWave2Logic.DUP_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> MachineTheme.STATUS_WARN;
        };
        MachineTheme.chrome(g, left, top, TrapWave2Layout.DUP_HEIGHT, TrapWave2Layout.DUP_BODY_BOTTOM, accent);

        Rect eb = TrapWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        Rect pb = TrapWave2Layout.PROGRESS_BAR;
        MachineTheme.progressBar(g, left + pb.x(), top + pb.y(), pb.width(), pb.height(),
                this.menu.getProgress(), this.menu.getTotalTime());

        Rect cg = TrapWave2Layout.DUP_CAPSULE_GROUP;
        g.fill(left + cg.x(), top + cg.y(), left + cg.right(), top + cg.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + cg.x(), top + cg.y(), cg.width(), cg.height(),
                0xFFC06BFF, 0.35D);

        Rect box = TrapWave2Layout.DUP_STATS_BOX;
        g.fill(left + box.x(), top + box.y(), left + box.right(), top + box.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + box.x(), top + box.y(), box.width(), box.height(),
                0xFFC06BFF, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineTheme.title(this.font, g, 0, 0, this.title);
        int dupState = this.menu.getDupState();
        Component statusComp = switch (dupState) {
            case TrapWave2Logic.DUP_STATE_WORKING ->
                    Component.translatable("gui.industrialcore.mob_duplicator.spawning");
            case TrapWave2Logic.DUP_STATE_CROWDED ->
                    Component.translatable("gui.industrialcore.mob_duplicator.crowded",
                            TrapWave2Logic.DUPLICATOR_CROWD_LIMIT);
            case TrapWave2Logic.DUP_STATE_NO_ENERGY ->
                    Component.translatable("gui.industrialcore.status.no_energy");
            case TrapWave2Logic.DUP_STATE_NO_CAPSULE ->
                    Component.translatable("gui.industrialcore.mob_duplicator.no_capsule");
            default -> Component.translatable("gui.industrialcore.status.idle");
        };
        int statusCol = switch (dupState) {
            case TrapWave2Logic.DUP_STATE_WORKING -> 0xFFD99BFF;
            case TrapWave2Logic.DUP_STATE_CROWDED -> 0xFFFFB03A;
            case TrapWave2Logic.DUP_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> MachineTheme.STATUS_WARN;
        };
        MachineTheme.statusText(this.font, g, 0, 0, statusComp, statusCol);

        int total = this.menu.getTotalTime();
        int pct = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.machine.progress", pct), MachineTheme.MUTED);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, TrapWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_duplicator.capsule"),
                TrapWave2Layout.DUP_CAPSULE_LABEL_X, TrapWave2Layout.DUP_CAPSULE_LABEL_Y, 30, MachineTheme.MUTED);

        Component mobName = this.menu.getCapturedMobName();
        Component targetLabel = mobName != null
                ? mobName
                : Component.translatable("gui.industrialcore.mob_duplicator.none");
        int maxLineW = TrapWave2Layout.DUP_STATS_BOX.width() - 10;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_duplicator.target", targetLabel),
                TrapWave2Layout.DUP_STATS_TEXT_X, TrapWave2Layout.DUP_ROW_1_Y, maxLineW, 0xFFE3B8FF);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_duplicator.rate",
                        this.menu.getTotalTime(), this.menu.getBatchCount()),
                TrapWave2Layout.DUP_STATS_TEXT_X, TrapWave2Layout.DUP_ROW_2_Y, maxLineW, MachineTheme.CYAN_BRIGHT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_duplicator.nearby",
                        this.menu.getNearbyCount(), TrapWave2Logic.DUPLICATOR_CROWD_LIMIT),
                TrapWave2Layout.DUP_STATS_TEXT_X, TrapWave2Layout.DUP_ROW_3_Y, maxLineW,
                dupState == TrapWave2Logic.DUP_STATE_CROWDED ? 0xFFFFB03A : MachineTheme.TEXT);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.mob_duplicator.spawned",
                        this.menu.getSpawnedTotal()),
                TrapWave2Layout.DUP_STATS_TEXT_X, TrapWave2Layout.DUP_ROW_4_Y, maxLineW, MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 52);
        g.drawString(this.font, motorLbl,
                TrapWave2Layout.DUP_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                TrapWave2Layout.DUP_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 52);
        g.drawString(this.font, battLbl,
                TrapWave2Layout.DUP_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                TrapWave2Layout.DUP_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                TrapWave2Layout.DUP_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                TrapWave2Layout.DUP_MOTOR_SLOT, TrapWave2Layout.DUP_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return MobDuplicatorBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
