package com.industrialcore.client;

import com.industrialcore.blockentity.FluidPumpBlockEntity;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.menu.FluidPumpMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Phase 94: Adaptive GUI for the Industrial Fluid Pump ({@code fluid_pump}).
 */
public class FluidPumpScreen extends AdaptiveContainerScreen<FluidPumpMenu> {

    public FluidPumpScreen(FluidPumpMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeLayout.WIDTH;
        this.imageHeight = LifeLayout.PUMP_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getPumpState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeLayout.PUMP_HEIGHT, LifeLayout.PUMP_BODY_BOTTOM, accent);

        Rect eb = LifeLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Fluid tank gauge bar
        Rect tb = LifeLayout.PUMP_TANK_BAR;
        int tx = left + tb.x();
        int ty = top + tb.y();
        g.fill(tx, ty, tx + tb.width(), ty + tb.height(), 0xFF070E16);
        int amount = this.menu.getFluidAmount();
        int cap = this.menu.getFluidCapacity();
        Fluid fluid = this.menu.getFluid();
        int fluidColor = resolveFluidColor(fluid);
        if (amount > 0 && cap > 0) {
            int fillW = Math.max(2, (int) Math.round((tb.width() - 2) * (amount / (double) cap)));
            g.fill(tx + 1, ty + 1, tx + 1 + fillW, ty + tb.height() - 1, fluidColor);
            // Subtle top specular highlight on liquid
            g.fill(tx + 1, ty + 1, tx + 1 + fillW, ty + 4, 0x44FFFFFF);
        }
        MachineTheme.glowRect(g, tx, ty, tb.width(), tb.height(),
                amount > 0 ? fluidColor : 0xFF2B4A62, 0.35D);

        Rect ig = LifeLayout.PUMP_BUCKET_IN_GROUP;
        g.fill(left + ig.x(), top + ig.y(), left + ig.right(), top + ig.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + ig.x(), top + ig.y(), ig.width(), ig.height(), 0xFF5CE6FF, 0.30D);

        Rect og = LifeLayout.PUMP_BUCKET_OUT_GROUP;
        g.fill(left + og.x(), top + og.y(), left + og.right(), top + og.bottom(), 0xFF070C12);
        MachineTheme.glowRect(g, left + og.x(), top + og.y(), og.width(), og.height(), 0xFF3FA8FF, 0.34D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    private static int resolveFluidColor(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return 0xFF38A8FF;
        }
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return 0xFF2DA4FF;
        }
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return 0xFFFF6D22;
        }
        try {
            int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
            return (tint & 0x00FFFFFF) == 0 ? 0xFF42D8FF : (0xFF000000 | (tint & 0x00FFFFFF));
        } catch (Throwable ignored) {
            return 0xFF42D8FF;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getPumpState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.fluid_pump.radius_short",
                        this.menu.getRadiusBlocks()),
                0xFF5CE6FF);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        Fluid fluid = this.menu.getFluid();
        int amt = this.menu.getFluidAmount();
        Component fluidName = (fluid == Fluids.EMPTY || amt <= 0)
                ? Component.translatable("gui.industrialcore.fluid_pump.empty")
                : fluid.getFluidType().getDescription();
        Rect tb = LifeLayout.PUMP_TANK_BAR;
        Component barLabel = MachineTheme.fit(this.font, fluidName, tb.width() - 8);
        g.drawString(this.font, barLabel,
                tb.x() + (tb.width() - this.font.width(barLabel)) / 2,
                tb.y() + 4, 0xFFFFFFFF, true);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.fluid_pump.range",
                        this.menu.getRadiusBlocks(), LifeLogic.PUMP_MAX_DEPTH),
                GuiMetrics.SIDE, LifeLayout.PUMP_INFO_Y, 120, MachineTheme.TEXT);

        String mbLine = String.format("%,d / %,d mB", amt, this.menu.getFluidCapacity());
        RanchHarvesterScreen.drawFitted(this.font, g, Component.literal(mbLine),
                GuiMetrics.SIDE, LifeLayout.PUMP_FLUID_TEXT_Y, 120, 0xFF7BE3FF);

        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.fluid_pump.bucket_in"),
                LifeLayout.PUMP_BUCKET_IN_LABEL_X, LifeLayout.PUMP_BUCKET_LABEL_Y, 48, MachineTheme.MUTED);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.fluid_pump.bucket_out"),
                LifeLayout.PUMP_BUCKET_OUT_LABEL_X, LifeLayout.PUMP_BUCKET_LABEL_Y, 56, MachineTheme.MUTED);

        g.drawString(this.font, ">>", LifeLayout.PUMP_ARROW_X, LifeLayout.PUMP_ARROW_Y, 0xFF5CE6FF, false);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 50);
        g.drawString(this.font, motorLbl,
                LifeLayout.PUMP_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeLayout.PUMP_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 50);
        g.drawString(this.font, battLbl,
                LifeLayout.PUMP_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeLayout.PUMP_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeLayout.PUMP_BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeLayout.PUMP_MOTOR_SLOT, LifeLayout.PUMP_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return FluidPumpBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeLogic.PUMP_STATE_PUMPING -> 0xFF42D4FF;
            case LifeLogic.PUMP_STATE_TANK_FULL -> MachineTheme.STATUS_WARN;
            case LifeLogic.PUMP_STATE_NO_FLUID -> MachineTheme.MUTED;
            case LifeLogic.PUMP_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> MachineTheme.MUTED;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeLogic.PUMP_STATE_PUMPING -> "gui.industrialcore.fluid_pump.status.pumping";
            case LifeLogic.PUMP_STATE_TANK_FULL -> "gui.industrialcore.fluid_pump.status.tank_full";
            case LifeLogic.PUMP_STATE_NO_FLUID -> "gui.industrialcore.fluid_pump.status.no_fluid";
            case LifeLogic.PUMP_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
