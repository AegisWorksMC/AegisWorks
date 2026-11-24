package com.industrialcore.client;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuantumIoMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.QuantumFreqActionPayload;
import com.industrialcore.network.QuantumIoActionPayload;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import com.industrialcore.storage.QuantumIoLayout;
import com.industrialcore.storage.QuantumNetLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Phase 96: Unified industrial GUI for {@code quantum_importer} and {@code quantum_exporter}.
 */
public class QuantumIoScreen extends AdaptiveContainerScreen<QuantumIoMenu> {

    public QuantumIoScreen(QuantumIoMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = QuantumIoLayout.WIDTH;
        this.imageHeight = QuantumIoLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    private int themeAccent() {
        return this.menu.isExporter() ? 0xFFFFB84D : 0xFF3EF2B6;
    }

    private int statusAccent(int st) {
        return switch (st) {
            case QuantumNetLogic.IO_STATE_ACTIVE -> themeAccent();
            case QuantumNetLogic.IO_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            case QuantumNetLogic.IO_STATE_NO_CHANNEL -> MachineTheme.STATUS_WARN;
            default -> MachineTheme.CYAN;
        };
    }

    private static String statusKey(int st) {
        return switch (st) {
            case QuantumNetLogic.IO_STATE_ACTIVE -> "gui.industrialcore.quantum_io.status_active";
            case QuantumNetLogic.IO_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            case QuantumNetLogic.IO_STATE_NO_CHANNEL -> "gui.industrialcore.quantum_io.status_no_ch";
            case QuantumNetLogic.IO_STATE_WAITING -> "gui.industrialcore.quantum_io.status_waiting";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }

    private static String facingKey(int dirIndex) {
        Direction dir = Direction.from3DDataValue(dirIndex);
        return switch (dir) {
            case DOWN -> "gui.industrialcore.dir.down";
            case UP -> "gui.industrialcore.dir.up";
            case NORTH -> "gui.industrialcore.dir.north";
            case SOUTH -> "gui.industrialcore.dir.south";
            case WEST -> "gui.industrialcore.dir.west";
            case EAST -> "gui.industrialcore.dir.east";
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getIoState();
        int accent = statusAccent(st);
        double lx = mouseX - left;
        double ly = mouseY - top;

        MachineTheme.chrome(g, left, top, QuantumIoLayout.HEIGHT, QuantumIoLayout.BODY_BOTTOM, accent);

        // Energy bar
        Rect eb = QuantumIoLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        // Channel selector row
        drawButtonBg(g, left, top, QuantumIoLayout.BTN_CH_PREV, lx, ly, themeAccent(), false);
        drawButtonBg(g, left, top, QuantumIoLayout.BTN_CH_NEXT, lx, ly, themeAccent(), false);
        drawButtonBg(g, left, top, QuantumIoLayout.BTN_CH_CLEAR, lx, ly, 0xFFFF6677, false);
        Rect cb = QuantumIoLayout.CHANNEL_BOX;
        boolean connected = !this.menu.getClientChannelId().isEmpty();
        g.fill(left + cb.x(), top + cb.y(), left + cb.right(), top + cb.bottom(), 0xFF070C14);
        drawOutline(g, left + cb.x(), top + cb.y(), cb.width(), cb.height(),
                connected ? themeAccent() : 0xFF284257);

        // 3x3 Ghost filter grid
        Rect fg = QuantumIoLayout.FILTER_GROUP;
        MachineTheme.ingredientGroup(g, left, top, fg.x(), fg.y(), fg.width(), fg.height());
        List<ItemStack> filters = this.menu.getClientFilters();
        for (int i = 0; i < QuantumIoLayout.FILTER_COUNT; i++) {
            Rect r = QuantumIoLayout.filterSlot(i);
            int sx = left + r.x();
            int sy = top + r.y();
            MachineTheme.slotWell(g, sx, sy);
            ItemStack fs = i < filters.size() ? filters.get(i) : ItemStack.EMPTY;
            if (!fs.isEmpty()) {
                g.renderFakeItem(fs, sx, sy);
                // Subtle ghost tint border
                drawOutline(g, sx - 1, sy - 1, 18, 18,
                        this.menu.isBlacklist() ? 0x88FF6666 : 0x8855FFBB);
            }
            if (lx >= r.x() - 1 && lx <= r.right() + 1 && ly >= r.y() - 1 && ly <= r.bottom() + 1) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x3555FFFF);
            }
        }

        // Filter mode & clear buttons
        boolean bl = this.menu.isBlacklist();
        drawButtonBg(g, left, top, QuantumIoLayout.BTN_FILTER_MODE, lx, ly,
                bl ? 0xFFFF7766 : 0xFF44EEAA, true);
        drawButtonBg(g, left, top, QuantumIoLayout.BTN_FILTER_CLEAR, lx, ly, 0xFF66B8DD, false);

        // Telemetry card
        Rect sb = QuantumIoLayout.STATS_BOX;
        g.fill(left + sb.x(), top + sb.y(), left + sb.right(), top + sb.bottom(), 0xFF080D15);
        drawOutline(g, left + sb.x(), top + sb.y(), sb.width(), sb.height(), 0xFF1F364A);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getIoState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        MachineTheme.statusValue(this.font, g, 0, 0,
                Component.literal(this.menu.getIntervalTicks() + "t"), themeAccent());

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, QuantumIoLayout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        // Channel selector text
        drawCentered(g, QuantumIoLayout.BTN_CH_PREV, Component.literal("<"), 0xFFE8F6FF);
        drawCentered(g, QuantumIoLayout.BTN_CH_NEXT, Component.literal(">"), 0xFFE8F6FF);
        drawCentered(g, QuantumIoLayout.BTN_CH_CLEAR,
                Component.translatable("gui.industrialcore.quantum_net.btn_disconnect"), 0xFFFFCCD5);
        String chName = this.menu.getClientChannelName();
        Component chLabel = chName.isEmpty()
                ? Component.translatable("gui.industrialcore.quantum_io.no_channel")
                : Component.literal(chName);
        drawCentered(g, QuantumIoLayout.CHANNEL_BOX, chLabel,
                chName.isEmpty() ? MachineTheme.MUTED : themeAccent());

        // Filter label & mode buttons
        g.drawString(this.font, Component.translatable("gui.industrialcore.quantum_io.filter_title"),
                QuantumIoLayout.FILTER_GROUP.x(), QuantumIoLayout.FILTER_LABEL_Y, MachineTheme.MUTED, false);
        boolean bl = this.menu.isBlacklist();
        drawCentered(g, QuantumIoLayout.BTN_FILTER_MODE,
                Component.translatable(bl
                        ? "gui.industrialcore.quantum_io.blacklist"
                        : "gui.industrialcore.quantum_io.whitelist"),
                bl ? 0xFFFFD8D0 : 0xFFD8FFEE);
        drawCentered(g, QuantumIoLayout.BTN_FILTER_CLEAR,
                Component.translatable("gui.industrialcore.quantum_io.btn_clear"), 0xFFE2F5FF);

        // Telemetry stats
        int sx = QuantumIoLayout.STATS_TEXT_X;
        int sw = QuantumIoLayout.STATS_BOX.width() - 8;
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.quantum_io.stat_face",
                        Component.translatable(facingKey(this.menu.getFacingIndex()))),
                sx, QuantumIoLayout.STATS_ROW_1_Y, sw, 0xFFCDE6FA);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.quantum_io.stat_items",
                        this.menu.getMaxItemsPerCycle()),
                sx, QuantumIoLayout.STATS_ROW_2_Y, sw, themeAccent());
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.quantum_io.stat_fluid",
                        this.menu.getMaxFluidPerCycle()),
                sx, QuantumIoLayout.STATS_ROW_3_Y, sw, 0xFF7BE8FF);
        RanchHarvesterScreen.drawFitted(this.font, g,
                Component.translatable("gui.industrialcore.quantum_io.stat_speed",
                        this.menu.getIntervalTicks()),
                sx, QuantumIoLayout.STATS_ROW_4_Y, sw, MachineTheme.MUTED);

        // Upgrade labels
        Component motorLabel = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLabel,
                QuantumIoLayout.UPGRADE_CENTER_X - this.font.width(motorLabel) / 2,
                QuantumIoLayout.MOTOR_LABEL_Y, MachineTheme.MUTED, false);
        Component battLabel = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLabel,
                QuantumIoLayout.UPGRADE_CENTER_X - this.font.width(battLabel) / 2,
                QuantumIoLayout.BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                QuantumIoLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    @Override
    protected String skillTreeMachineId() {
        return this.menu.machineId();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;

        int fSlot = QuantumIoLayout.filterSlotAt(lx, ly);
        if (fSlot >= 0) {
            ItemStack fs = fSlot < this.menu.getClientFilters().size()
                    ? this.menu.getClientFilters().get(fSlot) : ItemStack.EMPTY;
            if (!fs.isEmpty()) {
                graphics.renderComponentTooltip(this.font, List.of(
                        fs.getHoverName().copy().withStyle(ChatFormatting.AQUA),
                        Component.translatable("gui.industrialcore.quantum_io.filter_clear_hint")
                                .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.industrialcore.quantum_io.filter_set_hint"),
                        mouseX, mouseY);
            }
            return;
        }

        if (!renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                QuantumIoLayout.MOTOR_SLOT, QuantumIoLayout.BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;

        // Ghost filter slots: left-click sets carried item (without consuming), right-click clears
        int fSlot = QuantumIoLayout.filterSlotAt(lx, ly);
        if (fSlot >= 0 && (button == 0 || button == 1)) {
            SoundDirector.playClick();
            if (button == 1) {
                PacketDistributor.sendToServer(new QuantumIoActionPayload(
                        this.menu.getBlockPos(), QuantumIoActionPayload.ACTION_CLEAR_FILTER,
                        fSlot, ItemStack.EMPTY));
            } else {
                ItemStack carried = this.menu.getCarried();
                if (!carried.isEmpty()) {
                    PacketDistributor.sendToServer(new QuantumIoActionPayload(
                            this.menu.getBlockPos(), QuantumIoActionPayload.ACTION_SET_FILTER,
                            fSlot, carried.copyWithCount(1)));
                } else {
                    PacketDistributor.sendToServer(new QuantumIoActionPayload(
                            this.menu.getBlockPos(), QuantumIoActionPayload.ACTION_CLEAR_FILTER,
                            fSlot, ItemStack.EMPTY));
                }
            }
            return true;
        }

        if (button == 0) {
            if (QuantumIoLayout.BTN_CH_PREV.contains(lx, ly)) {
                cycleChannel(-1);
                return true;
            }
            if (QuantumIoLayout.BTN_CH_NEXT.contains(lx, ly)
                    || QuantumIoLayout.CHANNEL_BOX.contains(lx, ly)) {
                cycleChannel(1);
                return true;
            }
            if (QuantumIoLayout.BTN_CH_CLEAR.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new QuantumFreqActionPayload(
                        this.menu.getBlockPos(), false, QuantumFreqActionPayload.ACTION_DISCONNECT, "", false));
                return true;
            }
            if (QuantumIoLayout.BTN_FILTER_MODE.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new QuantumIoActionPayload(
                        this.menu.getBlockPos(), QuantumIoActionPayload.ACTION_TOGGLE_BLACKLIST,
                        0, ItemStack.EMPTY));
                return true;
            }
            if (QuantumIoLayout.BTN_FILTER_CLEAR.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new QuantumIoActionPayload(
                        this.menu.getBlockPos(), QuantumIoActionPayload.ACTION_CLEAR_ALL,
                        0, ItemStack.EMPTY));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void cycleChannel(int delta) {
        List<ChannelSummary> list = this.menu.getClientChannels();
        if (list.isEmpty()) {
            SoundDirector.playUiLocked();
            return;
        }
        String curId = this.menu.getClientChannelId();
        int curIdx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).channelId().equals(curId)) {
                curIdx = i;
                break;
            }
        }
        int nextIdx;
        if (curIdx < 0) {
            nextIdx = delta >= 0 ? 0 : list.size() - 1;
        } else {
            nextIdx = Math.floorMod(curIdx + delta, list.size());
        }
        SoundDirector.playClick();
        PacketDistributor.sendToServer(new QuantumFreqActionPayload(
                this.menu.getBlockPos(), false, QuantumFreqActionPayload.ACTION_CONNECT,
                list.get(nextIdx).channelId(), false));
    }

    private void drawCentered(GuiGraphics g, Rect r, Component label, int color) {
        Component fit = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = r.x() + (r.width() - this.font.width(fit)) / 2;
        int ty = r.y() + (r.height() - 8) / 2;
        g.drawString(this.font, fit, tx, ty, color, false);
    }

    private static void drawButtonBg(GuiGraphics g, int left, int top, Rect r,
                                     double lx, double ly, int accent, boolean active) {
        boolean hover = r.contains(lx, ly);
        int bg = active ? 0xFF142A36 : (hover ? 0xFF142636 : 0xFF0C1520);
        int border = (active || hover) ? accent : 0xFF284257;
        g.fill(left + r.x(), top + r.y(), left + r.right(), top + r.bottom(), bg);
        drawOutline(g, left + r.x(), top + r.y(), r.width(), r.height(), border);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
