package com.industrialcore.client;

import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory;
import com.industrialcore.menu.GuiFit;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.UpgradeDockLayout;
import com.industrialcore.network.UpgradeBankActionPayload;
import com.industrialcore.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base of every Industrial Core container screen (Phase 80).
 *
 * <p>The panels keep their authored size and their shared {@link com.industrialcore.menu.GuiMetrics}
 * grid. This class only decides how that fixed panel meets the window: {@link GuiFit} computes a
 * uniform scale (1.0 when the panel fits, smaller on a small window or a tall panel), and the panel
 * is drawn and hit-tested through that transform. Slots, gauges, labels and tooltips therefore stay
 * aligned at every window size, aspect ratio and GUI scale, and the panel can never leave the
 * screen or become unusable.</p>
 *
 * <p>Mouse events arrive in screen pixels, so they are mapped back into panel pixels before the
 * vanilla container logic (slot hover, click, drag, scroll) runs. Screens that hit-test their own
 * decorations must use {@link #panelMouseX(double)} / {@link #panelMouseY(double)} as well; the
 * mapping is the inverse of the render transform and is exact at scale 1.</p>
 */
public abstract class AdaptiveContainerScreen<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {
    /** Free pixels kept around a shrunk panel. */
    private static final int MARGIN = 6;

    private float panelScale = 1.0F;
    private int panelX;
    private int panelY;
    private boolean inScaledRender;

    protected AdaptiveContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * Recomputes the fit. Vanilla calls this on open and on every window resize, so the panel
     * follows a dragged window and a changed GUI scale without any extra hook.
     */
    @Override
    protected void init() {
        super.init();
        int totalWidth = this.imageWidth + extraRightPanelWidth();
        GuiFit fit = GuiFit.fit(this.width, this.height, totalWidth, this.imageHeight, MARGIN);
        this.panelScale = fit.scale();
        this.panelX = fit.x();
        this.panelY = fit.y();

        BlockPos dockPos = upgradeDockPos();
        if (dockPos != null && this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new UpgradeBankActionPayload(
                    dockPos, 0, UpgradeBankActionPayload.ACTION_REQUEST_SYNC));
        }
    }

    /** Uniform scale the panel is drawn at; 1.0 in a normal window. */
    public float panelScale() {
        return panelScale;
    }

    /** Scaled panel origin X in screen pixels. */
    public int panelX() {
        return panelX;
    }

    /** Scaled panel origin Y in screen pixels. */
    public int panelY() {
        return panelY;
    }

    /** Inverse of the render transform: screen X to panel X. */
    public double panelMouseX(double screenX) {
        if (panelScale <= 0.0F) {
            return screenX;
        }
        return (screenX - panelX) / panelScale + this.leftPos;
    }

    /** Inverse of the render transform: screen Y to panel Y. */
    public double panelMouseY(double screenY) {
        if (panelScale <= 0.0F) {
            return screenY;
        }
        return (screenY - panelY) / panelScale + this.topPos;
    }

    /**
     * Phase 83: in 1.21.1 {@link AbstractContainerScreen#renderBackground} draws both the full-screen
     * dim backdrop and {@link #renderBg}. Calling it once outside the scaled pose and again inside
     * {@code super.render} drew an unscaled ghost copy of the panel (and any items rendered in
     * {@code renderBg}) behind the scaled panel. Here {@link #render} draws the dim backdrop once at
     * full window scale and this override delegates to {@link #renderBg} only inside the scaled pass.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.inScaledRender) {
            this.renderBg(graphics, partialTick, mouseX, mouseY);
        }
    }

    /**
     * Phase 113: Block position of the machine that owns a 3-slot Virtual Upgrade Bank,
     * or {@code null} if this screen does not attach the right-side Upgrade &amp; Level Dock.
     */
    @Nullable
    protected BlockPos upgradeDockPos() {
        return null;
    }

    /** True when this screen displays the attached right-side 3-slot Upgrade &amp; Level Dock. */
    protected boolean hasUpgradeDock() {
        return upgradeDockPos() != null;
    }

    /**
     * Phase 115: Extra width reserved to the right of the main panel so {@link GuiFit} centers the
     * combined frame cleanly (used by the Upgrade Dock and the Singularity Reactor Rift Challenge card).
     */
    protected int extraRightPanelWidth() {
        return hasUpgradeDock() ? UpgradeDockLayout.DOCK_GAP + UpgradeDockLayout.DOCK_WIDTH : 0;
    }

    /**
     * Registry id of the machine shown by this screen for the Phase 85 Skill Tree header button,
     * or {@code null} when the screen does not belong to one of the 17 tracked electric machines.
     */
    @Nullable
    protected String skillTreeMachineId() {
        return null;
    }

    /** Default tab opened by the header Skill Tree / Guide button. */
    protected int skillTreeDefaultTab() {
        return SkillTreeLayout.TAB_TREE;
    }

    /** True when the compact header button to open the Skill Tree & Guide should be shown. */
    protected boolean hasSkillTreeButton() {
        return skillTreeMachineId() != null;
    }

    private boolean isOverSkillTreeButton(double screenMouseX, double screenMouseY) {
        if (!hasSkillTreeButton()) {
            return false;
        }
        double lx = panelMouseX(screenMouseX) - this.leftPos;
        double ly = panelMouseY(screenMouseY) - this.topPos;
        return SkillTreeLayout.MACHINE_HEADER_BUTTON.contains(lx, ly);
    }

    private void renderSkillTreeHeaderButton(GuiGraphics graphics, double panelMouseX, double panelMouseY) {
        if (!hasSkillTreeButton()) {
            return;
        }
        SkillTreeLayout.Rect r = SkillTreeLayout.MACHINE_HEADER_BUTTON;
        int bx = this.leftPos + r.x();
        int by = this.topPos + r.y();
        boolean hover = r.contains(panelMouseX - this.leftPos, panelMouseY - this.topPos);

        if (hover) {
            MachineTheme.glowRect(graphics, bx, by, r.width(), r.height(), MachineTheme.CYAN, 0.55D);
        }
        int bg = hover ? 0xFF172C3D : 0xFF0C1620;
        int border = hover ? MachineTheme.CYAN_BRIGHT : MachineTheme.CYAN_DIM;
        graphics.fill(bx, by, bx + r.width(), by + r.height(), bg);
        graphics.fill(bx, by, bx + r.width(), by + 1, border);
        graphics.fill(bx, by + r.height() - 1, bx + r.width(), by + r.height(), border);
        graphics.fill(bx, by, bx + 1, by + r.height(), border);
        graphics.fill(bx + r.width() - 1, by, bx + r.width(), by + r.height(), border);

        // Crisp 3-node branching skill-tree glyph inside the 14x14 button
        int lineCol = hover ? 0xFFFFFFFF : MachineTheme.CYAN_DIM;
        int nodeCol = hover ? 0xFFFFFFFF : MachineTheme.CYAN_BRIGHT;
        // Left root node (3x3)
        graphics.fill(bx + 2, by + 6, bx + 5, by + 9, nodeCol);
        // Horizontal trunk -> vertical split -> two right branches
        graphics.fill(bx + 5, by + 7, bx + 7, by + 8, lineCol);
        graphics.fill(bx + 6, by + 4, bx + 7, by + 11, lineCol);
        graphics.fill(bx + 7, by + 4, bx + 9, by + 5, lineCol);
        graphics.fill(bx + 7, by + 10, bx + 9, by + 11, lineCol);
        // Upper & lower child nodes (3x3)
        graphics.fill(bx + 9, by + 3, bx + 12, by + 6, nodeCol);
        graphics.fill(bx + 9, by + 9, bx + 12, by + 12, 0xFFFFD166);

        // Phase 86: pulsing gold "!" badge on top-right when newly unlocked nodes await reveal
        if (ClientMachineProgress.hasPendingReveals()) {
            double pulse = 0.5D + 0.5D * Math.sin(Util.getMillis() * 0.010D);
            int badgeCol = MachineTheme.blend(0xFFFFB703, 0xFFFFF0A8, pulse);
            int nx = bx + r.width() - 4;
            int ny = by - 3;
            MachineTheme.glowRect(graphics, nx, ny, 7, 7, 0xFFFFD166, 0.55D + 0.40D * pulse);
            graphics.fill(nx, ny, nx + 7, ny + 7, 0xFF1A1204);
            graphics.fill(nx + 1, ny, nx + 6, ny + 7, badgeCol);
            graphics.fill(nx, ny + 1, nx + 7, ny + 6, badgeCol);
            // Crisp dark "!" glyph (stem + dot)
            graphics.fill(nx + 3, ny + 1, nx + 4, ny + 4, 0xFF120C02);
            graphics.fill(nx + 3, ny + 5, nx + 4, ny + 6, 0xFF120C02);
        }
    }

    /**
     * Draws the panel through the fit transform. The dim backdrop is drawn full-window once before
     * pushing the pose; everything the container draws (chrome, slots, items, labels, hover
     * highlight) runs once inside the scaled pose, so nothing can double-draw or drift from hit boxes.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        double pmx = panelMouseX(mouseX);
        double pmy = panelMouseY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);
        graphics.pose().translate(-this.leftPos, -this.topPos, 0.0F);
        this.inScaledRender = true;
        try {
            super.render(graphics, (int) pmx, (int) pmy, partialTick);
            renderSkillTreeHeaderButton(graphics, pmx, pmy);
            renderUpgradeDock(graphics, pmx, pmy);
        } finally {
            this.inScaledRender = false;
        }
        graphics.pose().popPose();

        if (isOverSkillTreeButton(mouseX, mouseY)) {
            String id = skillTreeMachineId();
            List<Component> tip = new ArrayList<>();
            if (id != null) {
                tip.add(Component.translatable("gui.industrialcore.skill_tree.button_tip"));
                int lvl = ClientMachineProgress.level(id);
                tip.add(Component.translatable("gui.industrialcore.skill_tree.button_level_hint",
                        lvl, ClientMachineProgress.maxLevel(id)));
                var br = com.industrialcore.level.MachineSkillTree.branchOf(id);
                int par = com.industrialcore.level.MachineLevelMath.parallelBatchSize(
                        lvl, br != null && ClientMachineProgress.isBranchLimitBroken(br));
                if (par > 1) {
                    tip.add(Component.translatable("gui.industrialcore.machine.parallel_badge", par)
                            .withStyle(ChatFormatting.GOLD));
                }
            } else {
                tip.add(Component.translatable("gui.industrialcore.skill_tree.button_guide_tip"));
            }
            int pending = ClientMachineProgress.pendingReveals().size();
            if (pending > 0) {
                tip.add(Component.translatable("gui.industrialcore.skill_tree.button_pending_hint", pending)
                        .withStyle(ChatFormatting.GOLD));
            }
            graphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        } else {
            renderUpgradeDockTooltip(graphics, mouseX, mouseY);
        }
    }

    private int dockOriginX() {
        return this.leftPos + this.imageWidth + UpgradeDockLayout.DOCK_GAP;
    }

    private int dockOriginY() {
        return this.topPos + UpgradeDockLayout.DOCK_TOP_Y;
    }

    private int hoveredDockRow(double panelMx, double panelMy) {
        if (!hasUpgradeDock()) {
            return -1;
        }
        double rx = panelMx - dockOriginX();
        double ry = panelMy - dockOriginY();
        for (int i = 0; i < 3; i++) {
            if (UpgradeDockLayout.rowRect(i).contains(rx, ry)) {
                return i;
            }
        }
        return -1;
    }

    private void renderUpgradeDock(GuiGraphics g, double panelMx, double panelMy) {
        BlockPos pos = upgradeDockPos();
        if (pos == null) {
            return;
        }
        int dx = dockOriginX();
        int dy = dockOriginY();
        int dw = UpgradeDockLayout.DOCK_WIDTH;
        int dh = UpgradeDockLayout.DOCK_HEIGHT;

        // Outer sci-fi frame
        g.fill(dx, dy, dx + dw, dy + dh, 0xFF0A1118);
        g.fill(dx, dy, dx + dw, dy + 1, MachineTheme.CYAN_DIM);
        g.fill(dx, dy + dh - 1, dx + dw, dy + dh, MachineTheme.CYAN_DIM);
        g.fill(dx, dy, dx + 1, dy + dh, MachineTheme.CYAN_DIM);
        g.fill(dx + dw - 1, dy, dx + dw, dy + dh, MachineTheme.CYAN_DIM);
        g.fill(dx + 1, dy + 1, dx + dw - 1, dy + 3, 0xFF142A3B);

        ClientUpgradeBankCache.Snapshot snap = ClientUpgradeBankCache.get(pos);
        int motors = snap != null ? snap.motors() : 0;
        int batteries = snap != null ? snap.batteries() : 0;
        int specials = snap != null ? snap.specials() : 0;
        int total = snap != null ? snap.total() : 0;
        int totalCap = snap != null ? snap.totalLimit() : MachineLevelMath.MIN_TOTAL_UPGRADE_CAP;
        SpecialCategory cat = snap != null ? snap.category() : SpecialCategory.PROCESSING;
        String mid = (snap != null && !snap.machineId().isEmpty()) ? snap.machineId() : skillTreeMachineId();

        // Title
        Component titleComp = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.upgrade_dock.title"), dw - 10);
        g.drawString(this.font, titleComp, dx + (dw - this.font.width(titleComp)) / 2,
                dy + UpgradeDockLayout.TITLE_Y, MachineTheme.CYAN_BRIGHT, false);

        // Machine Level + XP bar
        Rect xpBar = UpgradeDockLayout.XP_BAR;
        int bx = dx + xpBar.x();
        int by = dy + xpBar.y();
        g.fill(bx, by, bx + xpBar.width(), by + xpBar.height(), 0xFF05090E);
        g.fill(bx, by, bx + xpBar.width(), by + 1, 0xFF1D3547);
        g.fill(bx, by + xpBar.height() - 1, bx + xpBar.width(), by + xpBar.height(), 0xFF1D3547);

        if (mid != null && MachineSkillTree.byId(mid) != null) {
            int lvl = ClientMachineProgress.level(mid);
            int maxLvl = ClientMachineProgress.maxLevel(mid);
            boolean lb = ClientMachineProgress.isMachineLimitBroken(mid);
            Component lvlTxt = MachineTheme.fit(this.font,
                    Component.translatable("gui.industrialcore.upgrade_dock.level", lvl, maxLvl), dw - 12);
            g.drawString(this.font, lvlTxt, dx + UpgradeDockLayout.PAD_X,
                    dy + UpgradeDockLayout.LEVEL_TEXT_Y, lb ? 0xFFFFD166 : 0xFF7BE8FF, false);

            long xp = ClientMachineProgress.xp(mid);
            long into = MachineLevelMath.xpIntoLevel(xp, lb);
            long span = MachineLevelMath.xpSpanForNextLevel(lvl, lb);
            double frac = span <= 0L ? 1.0D : Math.max(0.0D, Math.min(1.0D, (double) into / (double) span));
            int fillW = (int) Math.round((xpBar.width() - 2) * frac);
            if (fillW > 0) {
                g.fill(bx + 1, by + 1, bx + 1 + fillW, by + xpBar.height() - 1,
                        lb ? 0xFFFFD166 : 0xFF39D98A);
            }
        } else {
            Component modeTxt = MachineTheme.fit(this.font,
                    Component.translatable("gui.industrialcore.upgrade_dock.free_mode"), dw - 12);
            g.drawString(this.font, modeTxt, dx + UpgradeDockLayout.PAD_X,
                    dy + UpgradeDockLayout.LEVEL_TEXT_Y, 0xFF7BE8FF, false);
            g.fill(bx + 1, by + 1, bx + xpBar.width() - 1, by + xpBar.height() - 1, MachineTheme.CYAN_DIM);
        }

        // Total upgrade cap readout
        Component capTxt = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.upgrade_dock.cap", total, totalCap), dw - 12);
        int capCol = total >= totalCap ? 0xFFFFB84D : MachineTheme.TEXT;
        g.drawString(this.font, capTxt, dx + UpgradeDockLayout.PAD_X,
                dy + UpgradeDockLayout.CAP_TEXT_Y, capCol, false);

        // Divider
        g.fill(dx + 5, dy + UpgradeDockLayout.DIVIDER_Y, dx + dw - 5, dy + UpgradeDockLayout.DIVIDER_Y + 1, 0xFF1C3244);

        // 3 Virtual Upgrade Rows
        int hoverRow = hoveredDockRow(panelMx, panelMy);
        ItemStack[] icons = {
                new ItemStack(ModItems.MOTOR.get()),
                new ItemStack(ModItems.BATTERY.get()),
                new ItemStack(ModItems.SPECIALIZATION_MODULE.get())
        };
        int[] counts = {motors, batteries, specials};
        int[] accents = {0xFF5CFFA0, 0xFF5CE6FF, 0xFFD96BFF};
        String[] shortKeys = {
                "gui.industrialcore.upgrade_dock.row_motor",
                "gui.industrialcore.upgrade_dock.row_battery",
                "gui.industrialcore.upgrade_dock.row_special"
        };
        Component[] bonusTexts = {
                Component.translatable("gui.industrialcore.upgrade_dock.bonus_motor",
                        String.format(Locale.ROOT, "%.2f", UpgradeMath.processingSpeedMultiplier(motors))),
                Component.translatable("gui.industrialcore.upgrade_dock.bonus_battery",
                        UpgradeMath.batterySavingPercent(batteries)),
                specialBonusShort(cat, specials)
        };

        for (int i = 0; i < 3; i++) {
            Rect row = UpgradeDockLayout.rowRect(i);
            int rx = dx + row.x();
            int ry = dy + row.y();
            boolean hov = (hoverRow == i);
            int bg = hov ? 0xFF162839 : 0xFF0E1721;
            int border = hov ? accents[i] : (counts[i] > 0 ? 0xFF2B4B62 : 0xFF1B2B3A);
            g.fill(rx, ry, rx + row.width(), ry + row.height(), bg);
            g.fill(rx, ry, rx + row.width(), ry + 1, border);
            g.fill(rx, ry + row.height() - 1, rx + row.width(), ry + row.height(), border);
            g.fill(rx, ry, rx + 1, ry + row.height(), border);
            g.fill(rx + row.width() - 1, ry, rx + row.width(), ry + row.height(), border);

            Rect slot = UpgradeDockLayout.slotRect(i);
            int sx = dx + slot.x();
            int sy = dy + slot.y();
            g.fill(sx, sy, sx + slot.width(), sy + slot.height(), 0xFF070C12);
            g.fill(sx, sy, sx + slot.width(), sy + 1, 0xFF284255);
            g.fill(sx, sy + slot.height() - 1, sx + slot.width(), sy + slot.height(), 0xFF284255);
            g.fill(sx, sy, sx + 1, sy + slot.height(), 0xFF284255);
            g.fill(sx + slot.width() - 1, sy, sx + slot.width(), sy + slot.height(), 0xFF284255);
            g.renderItem(icons[i], sx + 1, sy + 1);

            Component lbl = MachineTheme.fit(this.font,
                    Component.translatable(shortKeys[i], counts[i]), UpgradeDockLayout.INFO_WIDTH);
            g.drawString(this.font, lbl, dx + UpgradeDockLayout.INFO_REL_X,
                    dy + UpgradeDockLayout.rowLabelY(i),
                    counts[i] > 0 ? 0xFFFFFFFF : MachineTheme.MUTED, false);

            Component bon = MachineTheme.fit(this.font, bonusTexts[i], UpgradeDockLayout.INFO_WIDTH);
            g.drawString(this.font, bon, dx + UpgradeDockLayout.INFO_REL_X,
                    dy + UpgradeDockLayout.rowBonusY(i),
                    counts[i] > 0 ? accents[i] : 0xFF5D7689, false);
        }
    }

    private static Component specialBonusShort(SpecialCategory cat, int count) {
        return switch (cat) {
            case TURRET -> Component.translatable("gui.industrialcore.upgrade_dock.bonus_spec_turret",
                    UpgradeMath.turretRangeBonus(count),
                    String.format(Locale.ROOT, "%.2f", UpgradeMath.turretDamageMultiplier(count)));
            case PROCESSING -> Component.translatable("gui.industrialcore.upgrade_dock.bonus_spec_processing",
                    UpgradeMath.processingBonusYieldPercent(count));
            case GENERATOR -> Component.translatable("gui.industrialcore.upgrade_dock.bonus_spec_generator",
                    UpgradeMath.generatorOutputBonusPercent(count));
            case FIELD, HARVEST_MINE -> Component.translatable("gui.industrialcore.upgrade_dock.bonus_spec_area",
                    UpgradeMath.areaRangeBonus(count));
            case THROUGHPUT -> Component.translatable("gui.industrialcore.upgrade_dock.bonus_spec_throughput",
                    String.format(Locale.ROOT, "%.2f", UpgradeMath.throughputMultiplier(count)));
        };
    }

    private void renderUpgradeDockTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        BlockPos pos = upgradeDockPos();
        if (pos == null) {
            return;
        }
        int row = hoveredDockRow(panelMouseX(mouseX), panelMouseY(mouseY));
        if (row < 0) {
            return;
        }
        ClientUpgradeBankCache.Snapshot snap = ClientUpgradeBankCache.get(pos);
        int motors = snap != null ? snap.motors() : 0;
        int batteries = snap != null ? snap.batteries() : 0;
        int specials = snap != null ? snap.specials() : 0;
        int total = snap != null ? snap.total() : 0;
        int totalCap = snap != null ? snap.totalLimit() : MachineLevelMath.MIN_TOTAL_UPGRADE_CAP;
        SpecialCategory cat = snap != null ? snap.category() : SpecialCategory.PROCESSING;

        List<Component> lines = new ArrayList<>();
        if (row == 0) {
            lines.add(Component.translatable("gui.industrialcore.tooltip.motor_title")
                    .withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.industrialcore.upgrade_dock.tip_motor",
                    motors, String.format(Locale.ROOT, "%.2f", UpgradeMath.processingSpeedMultiplier(motors)))
                    .withStyle(ChatFormatting.AQUA));
        } else if (row == 1) {
            lines.add(Component.translatable("gui.industrialcore.tooltip.battery_title")
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.industrialcore.upgrade_dock.tip_battery",
                    batteries, UpgradeMath.batterySavingPercent(batteries),
                    String.format(Locale.ROOT, "%.2f", 1.0D + UpgradeMath.BATTERY_MAX_CAP_BONUS * UpgradeMath.diminishingFraction(batteries, UpgradeMath.BATTERY_CAP_HALF_SAT)))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("item.industrialcore.specialization_module")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(specialBonusShort(cat, specials).copy().withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.industrialcore.tooltip.specialization_module")
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.industrialcore.upgrade_dock.tip_cap", total, totalCap)
                .withStyle(total >= totalCap ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        lines.add(Component.translatable("gui.industrialcore.upgrade_dock.tip_diminishing")
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("gui.industrialcore.upgrade_dock.tip_controls")
                .withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (hasUpgradeDock()) {
            int dx = dockOriginX();
            int dy = dockOriginY();
            if (mouseX >= dx - UpgradeDockLayout.DOCK_GAP
                    && mouseX <= dx + UpgradeDockLayout.DOCK_WIDTH
                    && mouseY >= dy && mouseY <= dy + UpgradeDockLayout.DOCK_HEIGHT) {
                return false;
            }
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverSkillTreeButton(mouseX, mouseY) && this.minecraft != null) {
            SoundDirector.playClick();
            this.minecraft.setScreen(new SkillTreeScreen(skillTreeMachineId(), this, skillTreeDefaultTab()));
            return true;
        }
        BlockPos dockPos = upgradeDockPos();
        if (dockPos != null && (button == 0 || button == 1)) {
            int row = hoveredDockRow(panelMouseX(mouseX), panelMouseY(mouseY));
            if (row >= 0) {
                int action;
                if (button == 0) {
                    action = hasShiftDown()
                            ? UpgradeBankActionPayload.ACTION_DEPOSIT_BULK
                            : UpgradeBankActionPayload.ACTION_DEPOSIT_1;
                } else {
                    action = hasShiftDown()
                            ? UpgradeBankActionPayload.ACTION_WITHDRAW_64
                            : UpgradeBankActionPayload.ACTION_WITHDRAW_1;
                }
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new UpgradeBankActionPayload(dockPos, row, action));
                return true;
            }
        }
        return super.mouseClicked(panelMouseX(mouseX), panelMouseY(mouseY), button);
    }

    /**
     * Phase 87: draws unified hover tooltips for standard motor / battery upgrade slots whenever
     * the cursor rests over either slot (works even when the slot is empty).
     *
     * @return {@code true} if an upgrade tooltip was rendered (caller may skip default item tooltip)
     */
    protected boolean renderMachineUpgradeTooltips(GuiGraphics graphics, int mouseX, int mouseY,
                                                   @Nullable com.industrialcore.menu.QuarryLayout.Rect motorSlot,
                                                   @Nullable com.industrialcore.menu.QuarryLayout.Rect batterySlot,
                                                   int installedMotors, int installedBatteries,
                                                   boolean fuelMachine) {
        String id = skillTreeMachineId();
        if (id == null) {
            return false;
        }
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;
        if (motorSlot != null && lx >= motorSlot.x() - 1 && lx <= motorSlot.right() + 1
                && ly >= motorSlot.y() - 1 && ly <= motorSlot.bottom() + 1) {
            List<Component> lines = new ArrayList<>();
            ClientMachineProgress.appendMotorTooltip(lines, id, installedMotors);
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return true;
        }
        if (batterySlot != null && lx >= batterySlot.x() - 1 && lx <= batterySlot.right() + 1
                && ly >= batterySlot.y() - 1 && ly <= batterySlot.bottom() + 1) {
            List<Component> lines = new ArrayList<>();
            ClientMachineProgress.appendBatteryTooltip(lines, id, installedBatteries, fuelMachine);
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(panelMouseX(mouseX), panelMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(panelMouseX(mouseX), panelMouseY(mouseY), button,
                dragX / panelScale, dragY / panelScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(panelMouseX(mouseX), panelMouseY(mouseY), scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(panelMouseX(mouseX), panelMouseY(mouseY));
    }

    @Override
    public void onClose() {
        SoundDirector.stopMachineGuiSounds();
        GuiDopamine.reset();
        super.onClose();
    }

    @Override
    public void removed() {
        SoundDirector.stopMachineGuiSounds();
        GuiDopamine.reset();
        super.removed();
    }
}
