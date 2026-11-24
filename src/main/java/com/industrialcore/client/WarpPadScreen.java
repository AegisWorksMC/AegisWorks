package com.industrialcore.client;

import com.industrialcore.blockentity.WarpPadBlockEntity;
import com.industrialcore.life.LifeWave2Layout;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.WarpPadMenu;
import com.industrialcore.network.WarpActionPayload;
import com.industrialcore.network.WarpListSyncPayload.TargetRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Phase 95: Adaptive GUI for the Teleport Warp Pad ({@code warp_pad}). */
public class WarpPadScreen extends AdaptiveContainerScreen<WarpPadMenu> {
    private EditBox nameBox;
    private int scrollOffset;
    private int selectedIndex = -1;
    private long lastRowClickMs;
    private int lastClickedRow = -1;

    public WarpPadScreen(WarpPadMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = LifeWave2Layout.WIDTH;
        this.imageHeight = LifeWave2Layout.WARP_HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        Rect nr = LifeWave2Layout.WARP_NAME_BOX;
        this.nameBox = new EditBox(this.font,
                this.leftPos + nr.x(), this.topPos + nr.y(),
                nr.width(), nr.height(),
                Component.translatable("gui.industrialcore.warp_pad.name_hint"));
        this.nameBox.setMaxLength(LifeWave2Logic.WARP_MAX_NAME_LEN);
        if (!this.menu.getClientSelfName().isEmpty()) {
            this.nameBox.setValue(this.menu.getClientSelfName());
        }
        this.addRenderableWidget(this.nameBox);
        this.menu.setClientSyncListener(() -> {
            if (this.nameBox != null && !this.nameBox.isFocused()) {
                this.nameBox.setValue(this.menu.getClientSelfName());
            }
            clampSelectionAndScroll();
        });
        PacketDistributor.sendToServer(new WarpActionPayload(
                this.menu.getBlockPos(), WarpActionPayload.ACTION_REQUEST_SYNC, ""));
    }

    @Override
    public void removed() {
        this.menu.setClientSyncListener(null);
        super.removed();
    }

    private void clampSelectionAndScroll() {
        List<TargetRow> targets = this.menu.getClientTargets();
        int maxScroll = Math.max(0, targets.size() - LifeWave2Layout.WARP_VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        if (targets.isEmpty()) {
            selectedIndex = -1;
        } else if (selectedIndex < 0 || selectedIndex >= targets.size()) {
            selectedIndex = 0;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getWarpState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, LifeWave2Layout.WARP_HEIGHT, LifeWave2Layout.WARP_BODY_BOTTOM, accent);

        Rect eb = LifeWave2Layout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        int lx = (int) Math.round(panelMouseX(mouseX) - left);
        int ly = (int) Math.round(panelMouseY(mouseY) - top);

        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.WARP_BTN_SAVE,
                LifeWave2Layout.WARP_BTN_SAVE.contains(lx, ly), 0xFF102A3A, 0xFF5CE6FF);
        boolean pub = this.menu.isPublicFlag();
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.WARP_BTN_PUBLIC,
                LifeWave2Layout.WARP_BTN_PUBLIC.contains(lx, ly),
                pub ? 0xFF123A2A : 0xFF2E1E12,
                pub ? 0xFF5CFFA0 : 0xFFFFB84D);

        // Scrollable warp target list frame
        Rect lb = LifeWave2Layout.WARP_LIST_BOX;
        g.fill(left + lb.x(), top + lb.y(), left + lb.right(), top + lb.bottom(), 0xFF060C13);
        MachineTheme.glowRect(g, left + lb.x(), top + lb.y(), lb.width(), lb.height(), 0xFFB86BFF, 0.30D);

        List<TargetRow> targets = this.menu.getClientTargets();
        for (int v = 0; v < LifeWave2Layout.WARP_VISIBLE_ROWS; v++) {
            int idx = scrollOffset + v;
            if (idx >= targets.size()) {
                break;
            }
            Rect rowR = LifeWave2Layout.warpListRow(v);
            boolean sel = (idx == selectedIndex);
            boolean hov = rowR.contains(lx, ly);
            int bg = sel ? 0xFF1E1938 : (hov ? 0xFF121C2B : 0xFF0A111A);
            int bd = sel ? 0xFFD978FF : (hov ? 0xFF5CE6FF : 0xFF1C2C3D);
            g.fill(left + rowR.x(), top + rowR.y(), left + rowR.right(), top + rowR.bottom(), bg);
            g.fill(left + rowR.x(), top + rowR.bottom() - 1, left + rowR.right(), top + rowR.bottom(), bd);
            if (sel) {
                g.fill(left + rowR.x(), top + rowR.y(), left + rowR.x() + 2, top + rowR.bottom(), 0xFFE58CFF);
            }
        }

        int maxScroll = Math.max(0, targets.size() - LifeWave2Layout.WARP_VISIBLE_ROWS);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.WARP_BTN_SCROLL_UP,
                scrollOffset > 0 && LifeWave2Layout.WARP_BTN_SCROLL_UP.contains(lx, ly),
                scrollOffset > 0 ? 0xFF102030 : 0xFF091018,
                scrollOffset > 0 ? 0xFF5CE6FF : 0xFF283A4A);
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.WARP_BTN_SCROLL_DOWN,
                scrollOffset < maxScroll && LifeWave2Layout.WARP_BTN_SCROLL_DOWN.contains(lx, ly),
                scrollOffset < maxScroll ? 0xFF102030 : 0xFF091018,
                scrollOffset < maxScroll ? 0xFF5CE6FF : 0xFF283A4A);

        TargetRow selRow = selectedTarget();
        boolean canWarp = selRow != null && this.menu.getCooldownTicks() <= 0
                && this.menu.getEnergyStored() >= selRow.costFe();
        ChunkLoaderScreen.drawBtnBg(g, left, top, LifeWave2Layout.WARP_BTN_WARP,
                canWarp && LifeWave2Layout.WARP_BTN_WARP.contains(lx, ly),
                canWarp ? 0xFF251542 : 0xFF0B1118,
                canWarp ? 0xFFD978FF : 0xFF2D394A);

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    private TargetRow selectedTarget() {
        List<TargetRow> targets = this.menu.getClientTargets();
        if (selectedIndex >= 0 && selectedIndex < targets.size()) {
            return targets.get(selectedIndex);
        }
        return null;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getWarpState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent);
        int cd = this.menu.getCooldownTicks();
        if (cd > 0) {
            MachineTheme.statusValue(this.font, g, 0, 0, Component.literal(cd + "t"), MachineTheme.STATUS_WARN);
        } else {
            MachineTheme.statusValue(this.font, g, 0, 0,
                    Component.translatable("gui.industrialcore.warp_pad.count_short",
                            this.menu.getClientTargets().size()),
                    0xFFD978FF);
        }

        g.drawString(this.font, Component.translatable("gui.industrialcore.energy_value",
                        this.menu.getEnergyStored(), this.menu.getEnergyCapacity()),
                GuiMetrics.SIDE, LifeWave2Layout.ENERGY_TEXT_Y, MachineTheme.CYAN_BRIGHT, false);

        drawCentered(g, LifeWave2Layout.WARP_BTN_SAVE,
                Component.translatable("gui.industrialcore.warp_pad.btn_save"), 0xFFE2F7FF);
        boolean pub = this.menu.isPublicFlag();
        drawCentered(g, LifeWave2Layout.WARP_BTN_PUBLIC,
                Component.translatable(pub
                        ? "gui.industrialcore.warp_pad.public"
                        : "gui.industrialcore.warp_pad.private"),
                pub ? 0xFFE6FFF2 : 0xFFFFE5B8);

        List<TargetRow> targets = this.menu.getClientTargets();
        if (targets.isEmpty()) {
            RanchHarvesterScreen.drawFitted(this.font, g,
                    Component.translatable("gui.industrialcore.warp_pad.empty_list"),
                    LifeWave2Layout.WARP_LIST_BOX.x() + 6,
                    LifeWave2Layout.WARP_LIST_BOX.y() + 36,
                    LifeWave2Layout.WARP_LIST_BOX.width() - 12,
                    MachineTheme.MUTED);
        } else {
            for (int v = 0; v < LifeWave2Layout.WARP_VISIBLE_ROWS; v++) {
                int idx = scrollOffset + v;
                if (idx >= targets.size()) {
                    break;
                }
                TargetRow row = targets.get(idx);
                Rect rr = LifeWave2Layout.warpListRow(v);
                int maxW = rr.width() - 8;
                RanchHarvesterScreen.drawFitted(this.font, g,
                        Component.literal(row.name()),
                        rr.x() + 4, rr.y() + 1, maxW,
                        idx == selectedIndex ? 0xFFFFFFFF : 0xFFD0E4F5);
                String meta = row.crossDim()
                        ? shortDim(row.dimensionId()) + " / " + row.costFe() + " FE"
                        : row.distance() + "m / " + row.costFe() + " FE";
                RanchHarvesterScreen.drawFitted(this.font, g,
                        Component.literal(meta),
                        rr.x() + 4, rr.y() + 10, maxW,
                        row.crossDim() ? 0xFFE58CFF : 0xFF7BE8FF);
            }
        }

        drawCentered(g, LifeWave2Layout.WARP_BTN_SCROLL_UP, Component.literal("^"), 0xFFE2F7FF);
        drawCentered(g, LifeWave2Layout.WARP_BTN_SCROLL_DOWN, Component.literal("v"), 0xFFE2F7FF);

        TargetRow selRow = selectedTarget();
        Component warpLabel = selRow == null
                ? Component.translatable("gui.industrialcore.warp_pad.btn_warp")
                : Component.translatable("gui.industrialcore.warp_pad.btn_warp_cost",
                        String.format("%,d", selRow.costFe()));
        boolean canWarp = selRow != null && this.menu.getCooldownTicks() <= 0
                && this.menu.getEnergyStored() >= selRow.costFe();
        drawCentered(g, LifeWave2Layout.WARP_BTN_WARP, warpLabel,
                canWarp ? 0xFFF5E6FF : MachineTheme.MUTED);

        Component motorLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.motor"), 48);
        g.drawString(this.font, motorLbl,
                LifeWave2Layout.WARP_UPGRADE_CENTER_X - this.font.width(motorLbl) / 2,
                LifeWave2Layout.WARP_MOTOR_LABEL_Y, MachineTheme.MUTED, false);

        Component battLbl = MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.battery"), 48);
        g.drawString(this.font, battLbl,
                LifeWave2Layout.WARP_UPGRADE_CENTER_X - this.font.width(battLbl) / 2,
                LifeWave2Layout.WARP_BATTERY_LABEL_Y, MachineTheme.MUTED, false);

        MachineTheme.inventoryLabel(this.font, g, 0, 0,
                LifeWave2Layout.WARP_BODY_BOTTOM, this.playerInventoryTitle);
    }

    private static String shortDim(String dimId) {
        if ("minecraft:overworld".equals(dimId)) return "OW";
        if ("minecraft:the_nether".equals(dimId)) return "Nether";
        if ("minecraft:the_end".equals(dimId)) return "End";
        int idx = dimId.indexOf(':');
        return idx >= 0 ? dimId.substring(idx + 1) : dimId;
    }

    private void drawCentered(GuiGraphics g, Rect r, Component label, int color) {
        Component fitted = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = r.x() + (r.width() - this.font.width(fitted)) / 2;
        int ty = r.y() + (r.height() - 8) / 2 + 1;
        g.drawString(this.font, fitted, tx, ty, color, false);
    }

    private void saveName() {
        if (this.nameBox == null) {
            return;
        }
        SoundDirector.playClick();
        PacketDistributor.sendToServer(new WarpActionPayload(
                this.menu.getBlockPos(), WarpActionPayload.ACTION_RENAME, this.nameBox.getValue()));
    }

    private void triggerWarp(TargetRow target) {
        if (target == null) {
            return;
        }
        if (this.menu.getCooldownTicks() > 0 || this.menu.getEnergyStored() < target.costFe()) {
            SoundDirector.playUiLocked();
            return;
        }
        SoundDirector.playWarpChime();
        PacketDistributor.sendToServer(new WarpActionPayload(
                this.menu.getBlockPos(), WarpActionPayload.ACTION_TELEPORT, target.padId()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double lx = panelMouseX(mouseX) - this.leftPos;
            double ly = panelMouseY(mouseY) - this.topPos;
            if (!LifeWave2Layout.WARP_NAME_BOX.contains(lx, ly) && this.nameBox != null) {
                this.nameBox.setFocused(false);
            }
            if (LifeWave2Layout.WARP_BTN_SAVE.contains(lx, ly)) {
                saveName();
                return true;
            }
            if (LifeWave2Layout.WARP_BTN_PUBLIC.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new WarpActionPayload(
                        this.menu.getBlockPos(), WarpActionPayload.ACTION_TOGGLE_PUBLIC, ""));
                return true;
            }
            if (LifeWave2Layout.WARP_BTN_SCROLL_UP.contains(lx, ly)) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                SoundDirector.playClick();
                return true;
            }
            if (LifeWave2Layout.WARP_BTN_SCROLL_DOWN.contains(lx, ly)) {
                int maxScroll = Math.max(0, this.menu.getClientTargets().size() - LifeWave2Layout.WARP_VISIBLE_ROWS);
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                SoundDirector.playClick();
                return true;
            }
            if (LifeWave2Layout.WARP_BTN_WARP.contains(lx, ly)) {
                triggerWarp(selectedTarget());
                return true;
            }
            List<TargetRow> targets = this.menu.getClientTargets();
            for (int v = 0; v < LifeWave2Layout.WARP_VISIBLE_ROWS; v++) {
                int idx = scrollOffset + v;
                if (idx >= targets.size()) {
                    break;
                }
                if (LifeWave2Layout.warpListRow(v).contains(lx, ly)) {
                    long now = System.currentTimeMillis();
                    if (idx == lastClickedRow && (now - lastRowClickMs) < 350L) {
                        triggerWarp(targets.get(idx));
                    } else {
                        selectedIndex = idx;
                        lastClickedRow = idx;
                        lastRowClickMs = now;
                        SoundDirector.playClick();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;
        if (LifeWave2Layout.WARP_LIST_BOX.contains(lx, ly)) {
            int maxScroll = Math.max(0, this.menu.getClientTargets().size() - LifeWave2Layout.WARP_VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameBox != null && this.nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.nameBox.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                saveName();
                this.nameBox.setFocused(false);
                return true;
            }
            if (this.nameBox.keyPressed(keyCode, scanCode, modifiers) || this.nameBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                LifeWave2Layout.WARP_MOTOR_SLOT, LifeWave2Layout.WARP_BATTERY_SLOT,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return WarpPadBlockEntity.MACHINE_ID;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case LifeWave2Logic.WARP_STATE_COOLDOWN -> MachineTheme.STATUS_WARN;
            case LifeWave2Logic.WARP_STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> 0xFFD978FF;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case LifeWave2Logic.WARP_STATE_COOLDOWN -> "gui.industrialcore.warp_pad.status.cooldown";
            case LifeWave2Logic.WARP_STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.machine.status.idle";
        };
    }
}
