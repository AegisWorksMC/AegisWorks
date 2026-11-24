package com.industrialcore.client;

import com.industrialcore.blockentity.ChunkLoaderBlockEntity;
import com.industrialcore.life.LifeWave2Layout;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.menu.ChunkLoaderMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.ChunkLoaderActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Phase 95: Adaptive GUI for the Chunk Loader ({@code chunk_loader}). */
public class ChunkLoaderScreen extends AdaptiveContainerScreen<ChunkLoaderMenu> {

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeWave2Layout.WIDTH;
        this.imageHeight = LifeWave2Layout.CHUNK_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getChunkState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeWave2Layout.CHUNK_HEIGHT, LifeWave2Layout.CHUNK_BODY_BOTTOM, accent);

        Rect eb = LifeWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        int lx = (int) Math.round(panelMouseX(mouseX) - left);
        int ly = (int) Math.round(panelMouseY(mouseY) - top);
        boolean on = this.menu.isLoaderEnabled();
        drawBtnBg(g, left, top, LifeWave2Layout.CHUNK_BTN_TOGGLE,
                LifeWave2Layout.CHUNK_BTN_TOGGLE.contains(lx, ly),
                on ? 0xFF123A2A : 0xFF102230,
                on ? 0xFF5CFFA0 : 0xFF42C8FF);

        int effR = this.menu.getEffectiveRadius();
        int maxR = this.menu.getMaxUnlockedRadius();
        drawBtnBg(g, left, top, LifeWave2Layout.CHUNK_BTN_R_DEC,
                effR > 0 && LifeWave2Layout.CHUNK_BTN_R_DEC.contains(lx, ly),
                effR > 0 ? 0xFF0E2030 : 0xFF0A1118,
                effR > 0 ? 0xFF5CE6FF : 0xFF283A4A);
        drawBtnBg(g, left, top, LifeWave2Layout.CHUNK_BTN_R_INC,
                effR < maxR && LifeWave2Layout.CHUNK_BTN_R_INC.contains(lx, ly),
                effR < maxR ? 0xFF0E2030 : 0xFF0A1118,
                effR < maxR ? 0xFF5CE6FF : 0xFF283A4A);

        Rect sb = LifeWave2Layout.CHUNK_STATS_BOX;
        g.fill(left + sb.x(), top + sb.y(), left + sb.right(), top + sb.bottom(), 0xFF070D14);
        MachineTheme.glowRect(g, left + sb.x(), top + sb.y(), sb.width(), sb.height(), accent, 0.30D);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    static void drawBtnBg(GuiGraphics g, int left, int top, Rect r, boolean hover, int bgCol, int borderCol) {
        int bx = left + r.x();
        int by = top + r.y();
        int bg = hover ? brighten(bgCol) : bgCol;
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
        int st = this.menu.getChunkState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.translatable("gui.industrialcore.chunk_loader.chunks_short",
                        this.menu.getLoadedChunks()),
                accent);

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        boolean on = this.menu.isLoaderEnabled();
        drawCentered(g, LifeWave2Layout.CHUNK_BTN_TOGGLE,
                Component.translatable(on
                        ? "gui.industrialcore.chunk_loader.btn_on"
                        : "gui.industrialcore.chunk_loader.btn_off"),
                on ? 0xFFE6FFF2 : 0xFFE2F7FF);

        int effR = this.menu.getEffectiveRadius();
        int maxR = this.menu.getMaxUnlockedRadius();
        int side = 2 * effR + 1;
        int maxSide = 2 * maxR + 1;

        drawCentered(g, LifeWave2Layout.CHUNK_BTN_R_DEC, Component.literal("-"),
                effR > 0 ? 0xFFE2F7FF : MachineTheme.MUTED);
        String dimStr = side + "x" + side;
        g.drawString(this.font, dimStr, LifeWave2Layout.CHUNK_R_VAL_CX - this.font.width(dimStr) / 2,
                LifeWave2Layout.CHUNK_BTN_R_DEC.y() + 3, 0xFF7BE8FF, false);
        drawCentered(g, LifeWave2Layout.CHUNK_BTN_R_INC, Component.literal("+"),
                effR < maxR ? 0xFFE2F7FF : MachineTheme.MUTED);

        int maxW = LifeWave2Layout.CHUNK_STATS_BOX.width() - 12;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.chunk_loader.area", side, side),
                LifeWave2Layout.CHUNK_STATS_TEXT_X, LifeWave2Layout.CHUNK_ROW_1_Y, maxW, 0xFF7BE8FF);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.chunk_loader.cap", maxSide, maxSide),
                LifeWave2Layout.CHUNK_STATS_TEXT_X, LifeWave2Layout.CHUNK_ROW_2_Y, maxW, 0xFFFFD866);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.chunk_loader.loaded", this.menu.getLoadedChunks()),
                LifeWave2Layout.CHUNK_STATS_TEXT_X, LifeWave2Layout.CHUNK_ROW_3_Y, maxW, 0xFF8CFFBE);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.chunk_loader.power", this.menu.getFePerTick()),
                LifeWave2Layout.CHUNK_STATS_TEXT_X, LifeWave2Layout.CHUNK_ROW_4_Y, maxW, MachineTheme.TEXT);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLbl,
                LifeWave2Layout.CHUNK_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeWave2Layout.CHUNK_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLbl,
                LifeWave2Layout.CHUNK_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeWave2Layout.CHUNK_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeWave2Layout.CHUNK_BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawCentered(GuiGraphics g, Rect r, Component label, int color) {
        Component fitted = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = r.x() + (r.width() - this.font.width(fitted)) / 2;
        int ty = r.y() + (r.height() - 8) / 2 + 1;
        g.drawString(this.font, fitted, tx, ty, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double lx = panelMouseX(mouseX) - this.leftPos;
            double ly = panelMouseY(mouseY) - this.topPos;
            int action = -1;
            if (LifeWave2Layout.CHUNK_BTN_TOGGLE.contains(lx, ly)) {
                action = ChunkLoaderActionPayload.ACTION_TOGGLE;
            } else if (LifeWave2Layout.CHUNK_BTN_R_DEC.contains(lx, ly)) {
                action = ChunkLoaderActionPayload.ACTION_RADIUS_DEC;
            } else if (LifeWave2Layout.CHUNK_BTN_R_INC.contains(lx, ly)) {
                action = ChunkLoaderActionPayload.ACTION_RADIUS_INC;
            }
            if (action >= 0) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new ChunkLoaderActionPayload(this.menu.getBlockPos(), action));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeWave2Layout.CHUNK_MOTOR_SLOT, LifeWave2Layout.CHUNK_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return ChunkLoaderBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeWave2Logic.CHUNK_STATE_ACTIVE -> 0xFF5CFFA0;
            case LifeWave2Logic.CHUNK_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> 0xFF5CE6FF;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeWave2Logic.CHUNK_STATE_ACTIVE -> "gui.industrialcore.chunk_loader.status.active";
            case LifeWave2Logic.CHUNK_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
