package com.industrialcore.client;

import com.industrialcore.Config;
import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.endgame.SingularityReactorLogic;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.SingularityReactorMenu;
import com.industrialcore.network.RiftRaidStartPayload;
import com.industrialcore.reactor.ReactorMultiblockLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.FailReason;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import com.industrialcore.storage.BigCountFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 99: Adaptive neon industrial GUI for the Singularity Reactor Generator
 * ({@code singularity_reactor}).
 *
 * <p>Connects to the REACTOR generator audio profile ({@code gen_reactor_hum} +
 * {@code gen_power_pulse}) via {@link GuiDopamine#observeGenerator}, honoring silent-open and
 * instant-stop-on-close rules.</p>
 */
public class SingularityReactorScreen extends AdaptiveContainerScreen<SingularityReactorMenu> {
    private static final int GOLD = 0xFFFFD166;
    private static final int PRISM_CYAN = 0xFF5CE6FF;
    private static final int NEON_EMERALD = 0xFF3BF5A8;
    private static final int NEON_AMBER = 0xFFFFB84D;
    private static final int NEON_OVERDRIVE = 0xFFFF5E3A;
    private static final int RIFT_PURPLE = 0xFFD070FF;
    private static final int RIFT_PURPLE_DIM = 0xFF6B3299;

    /** Phase 116: Selected Dimensional Rift depth tier (1..max unlocked, or 1..10 in Creative). */
    private int selectedRiftTier = 1;
    private boolean tierInitialized = false;

    private int maxSelectableRiftTier() {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.isCreative()) {
            return com.industrialcore.raid.DimensionalRaidLogic.MAX_TIER;
        }
        return com.industrialcore.raid.DimensionalRaidLogic.clampTier(ClientMachineProgress.unlockedRiftTier());
    }

    private void syncSelectedRiftTier() {
        int maxT = maxSelectableRiftTier();
        if (!tierInitialized) {
            selectedRiftTier = ClientMachineProgress.unlockedRiftTier();
            tierInitialized = true;
        }
        selectedRiftTier = Math.max(1, Math.min(maxT, selectedRiftTier));
    }

    public SingularityReactorScreen(SingularityReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SingularityReactorLayout.WIDTH;
        this.imageHeight = SingularityReactorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected int skillTreeDefaultTab() {
        return com.industrialcore.level.SkillTreeLayout.TAB_INFRA;
    }

    @Override
    protected int extraRightPanelWidth() {
        return SingularityReactorLayout.RIFT_CARD_GAP + SingularityReactorLayout.RIFT_CARD_WIDTH;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        Rect rc = SingularityReactorLayout.RIFT_CARD;
        int rx = this.leftPos + rc.x();
        int ry = this.topPos + rc.y();
        if (mouseX >= rx - SingularityReactorLayout.RIFT_CARD_GAP && mouseX <= rx + rc.width()
                && mouseY >= ry && mouseY <= ry + rc.height()) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getDisplayState();
        boolean generating = (st == SingularityReactorLogic.STATE_GENERATING);

        // Wire into Phase 93+ reactor hum + power pulse sound engine (silent on open, instant stop on close)
        GuiDopamine.observeGenerator(this,
                new GeneratorAudioProfile.Profile(GeneratorAudioProfile.Category.REACTOR, 1.15f),
                generating);

        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, SingularityReactorLayout.HEIGHT, SingularityReactorLayout.BODY_BOTTOM, accent);

        // Energy bar (scaled cleanly from 64-bit long values)
        long stored = this.menu.getStoredEnergyLong();
        long cap = this.menu.getCapacityLong();
        int barCur = (int) Math.round(10_000.0D * Math.min(1.0D, (double) stored / (double) cap));
        Rect eb = SingularityReactorLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(), barCur, 10_000);

        // Right telemetry card
        Rect tp = SingularityReactorLayout.TELEMETRY_PANEL;
        g.fill(left + tp.x(), top + tp.y(), left + tp.right(), top + tp.bottom(), 0xFF070D15);
        MachineTheme.glowRect(g, left + tp.x(), top + tp.y(), tp.width(), tp.height(), accent, 0.34D);

        // Fuel burn progress bar
        Rect bb = SingularityReactorLayout.BURN_BAR;
        g.fill(left + bb.x(), top + bb.y(), left + bb.right(), top + bb.bottom(), 0xFF0E1824);
        int burn = this.menu.getBurnRemaining();
        int btot = Math.max(1, this.menu.getBurnTotal());
        if (burn > 0) {
            int fillW = Math.max(1, Math.min(bb.width(), Math.round(bb.width() * (float) burn / (float) btot)));
            g.fillGradient(left + bb.x(), top + bb.y(), left + bb.x() + fillW, top + bb.bottom(), GOLD, 0xFFFF6B3D);
        }

        MachineTheme.slots(g, left, top, this.menu.slots);

        // Phase 103: Multiblock status card + 3D holographic guide toggle button
        Rect mb = SingularityReactorLayout.MULTIBLOCK_BAR;
        boolean formed = this.menu.isMultiblockFormed();
        int mbAccent = formed ? NEON_EMERALD : NEON_AMBER;
        g.fill(left + mb.x(), top + mb.y(), left + mb.right(), top + mb.bottom(), 0xFF070D15);
        MachineTheme.glowRect(g, left + mb.x(), top + mb.y(), mb.width(), mb.height(), mbAccent, 0.30D);

        Rect gb = SingularityReactorLayout.GUIDE_BUTTON;
        boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
        int bx = left + gb.x();
        int by = top + gb.y();
        boolean hovered = mouseX >= bx && mouseX < bx + gb.width()
                && mouseY >= by && mouseY < by + gb.height();
        int btnBg = guideOn ? 0xFF0A3242 : (hovered ? 0xFF1A2838 : 0xFF101924);
        int btnBorder = guideOn ? PRISM_CYAN : (hovered ? 0xFF7CA4BC : MachineTheme.SLOT_EDGE);
        g.fill(bx, by, bx + gb.width(), by + gb.height(), btnBg);
        g.fill(bx, by, bx + gb.width(), by + 1, btnBorder);
        g.fill(bx, by + gb.height() - 1, bx + gb.width(), by + gb.height(), btnBorder);
        g.fill(bx, by, bx + 1, by + gb.height(), btnBorder);
        g.fill(bx + gb.width() - 1, by, bx + gb.width(), by + gb.height(), btnBorder);

        // Phase 114: High-Output Overdrive toggle button inside telemetry panel
        Rect ob = SingularityReactorLayout.OVERDRIVE_BUTTON;
        boolean od = this.menu.isOverdriveMode();
        boolean cd = this.menu.getOverdriveCooldownTicks() > 0;
        int ox = left + ob.x();
        int oy = top + ob.y();
        boolean odHov = mouseX >= ox && mouseX < ox + ob.width() && mouseY >= oy && mouseY < oy + ob.height();
        int odBg = cd ? 0xFF2B1212 : (od ? 0xFF3B170A : (odHov ? 0xFF1A2838 : 0xFF101924));
        int odBorder = cd ? MachineTheme.STATUS_BAD : (od ? NEON_OVERDRIVE : (odHov ? 0xFF7CA4BC : MachineTheme.SLOT_EDGE));
        if (od && !cd) {
            MachineTheme.glowRect(g, ox, oy, ob.width(), ob.height(), NEON_OVERDRIVE, 0.45D);
        }
        g.fill(ox, oy, ox + ob.width(), oy + ob.height(), odBg);
        g.fill(ox, oy, ox + ob.width(), oy + 1, odBorder);
        g.fill(ox, oy + ob.height() - 1, ox + ob.width(), oy + ob.height(), odBorder);
        g.fill(ox, oy, ox + 1, oy + ob.height(), odBorder);
        g.fill(ox + ob.width() - 1, oy, ox + ob.width(), oy + ob.height(), odBorder);

        // Phase 115 / 116: Dimensional Rift Challenge side card with Tier 1..10 selector & activation button
        syncSelectedRiftTier();
        int maxTier = maxSelectableRiftTier();
        Rect rc = SingularityReactorLayout.RIFT_CARD;
        int rcx = left + rc.x();
        int rcy = top + rc.y();
        boolean raidEnabled = Config.enableDimensionalRaid();
        long raidCost = com.industrialcore.raid.DimensionalRaidLogic.activationCostForTier(
                Config.raidActivationCostFe(), selectedRiftTier);
        boolean riftReady = raidEnabled && formed && stored >= raidCost;
        int tierAccent = selectedRiftTier >= 6 ? 0xFFFF4D8D : (selectedRiftTier >= 3 ? GOLD : RIFT_PURPLE);

        g.fill(rcx, rcy, rcx + rc.width(), rcy + rc.height(), 0xFF0A0916);
        int cardBorder = riftReady ? tierAccent : RIFT_PURPLE_DIM;
        if (riftReady) {
            MachineTheme.glowRect(g, rcx, rcy, rc.width(), rc.height(), tierAccent, 0.28D);
        }
        g.fill(rcx, rcy, rcx + rc.width(), rcy + 1, cardBorder);
        g.fill(rcx, rcy + rc.height() - 1, rcx + rc.width(), rcy + rc.height(), cardBorder);
        g.fill(rcx, rcy, rcx + 1, rcy + rc.height(), cardBorder);
        g.fill(rcx + rc.width() - 1, rcy, rcx + rc.width(), rcy + rc.height(), cardBorder);
        g.fill(rcx + 1, rcy + 1, rcx + rc.width() - 1, rcy + 3, 0xFF2B1242);
        g.fill(rcx + 5, rcy + 16, rcx + rc.width() - 5, rcy + 17, 0xFF3A1D56);

        // Tier selector prev/next buttons (◀ / ▶)
        drawMiniTierBtn(g, left, top, SingularityReactorLayout.RIFT_TIER_PREV, mouseX, mouseY, selectedRiftTier > 1, tierAccent);
        drawMiniTierBtn(g, left, top, SingularityReactorLayout.RIFT_TIER_NEXT, mouseX, mouseY, selectedRiftTier < maxTier, tierAccent);

        Rect rb = SingularityReactorLayout.RIFT_BUTTON;
        int rbx = left + rb.x();
        int rby = top + rb.y();
        boolean rbHov = mouseX >= rbx && mouseX < rbx + rb.width()
                && mouseY >= rby && mouseY < rby + rb.height();
        int rbBg = riftReady
                ? (rbHov ? 0xFF471C6E : 0xFF2D1049)
                : (rbHov ? 0xFF1E1A2C : 0xFF14111E);
        int rbBorder = riftReady
                ? (rbHov ? 0xFFF0A6FF : tierAccent)
                : (rbHov ? 0xFF7B6399 : 0xFF3E2D52);
        if (riftReady) {
            MachineTheme.glowRect(g, rbx, rby, rb.width(), rb.height(), tierAccent, rbHov ? 0.55D : 0.35D);
        }
        g.fill(rbx, rby, rbx + rb.width(), rby + rb.height(), rbBg);
        g.fill(rbx, rby, rbx + rb.width(), rby + 1, rbBorder);
        g.fill(rbx, rby + rb.height() - 1, rbx + rb.width(), rby + rb.height(), rbBorder);
        g.fill(rbx, rby, rbx + 1, rby + rb.height(), rbBorder);
        g.fill(rbx + rb.width() - 1, rby, rbx + rb.width(), rby + rb.height(), rbBorder);
    }

    private static void drawMiniTierBtn(GuiGraphics g, int left, int top, Rect r,
                                        int mouseX, int mouseY, boolean enabled, int accent) {
        int x = left + r.x();
        int y = top + r.y();
        boolean hov = enabled && mouseX >= x && mouseX < x + r.width() && mouseY >= y && mouseY < y + r.height();
        int bg = enabled ? (hov ? 0xFF361754 : 0xFF1A0F2B) : 0xFF100C18;
        int border = enabled ? (hov ? 0xFFF0A6FF : accent) : 0xFF2C213D;
        g.fill(x, y, x + r.width(), y + r.height(), bg);
        g.fill(x, y, x + r.width(), y + 1, border);
        g.fill(x, y + r.height() - 1, x + r.width(), y + r.height(), border);
        g.fill(x, y, x + 1, y + r.height(), border);
        g.fill(x + r.width() - 1, y, x + r.width(), y + r.height(), border);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getDisplayState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);

        long rate = this.menu.getFePerTick();
        String rateFmt = "+" + BigCountFormatter.formatCompact(BigInteger.valueOf(rate),
                BigCountFormatter.UnitMode.ENGLISH) + " FE/t";
        Component rateComp = Component.literal(rateFmt);
        int rateW = Math.min(82, this.font.width(rateComp));
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent, rateW);
        MachineTheme.statusValue(this.font, g, 0, 0, rateComp, GOLD);

        // Telemetry panel readouts
        Rect tp = SingularityReactorLayout.TELEMETRY_PANEL;
        long stored = this.menu.getStoredEnergyLong();
        long cap = this.menu.getCapacityLong();
        String curFmt = BigCountFormatter.formatCompact(BigInteger.valueOf(stored), BigCountFormatter.UnitMode.ENGLISH);
        String capFmt = BigCountFormatter.formatCompact(BigInteger.valueOf(cap), BigCountFormatter.UnitMode.ENGLISH);

        Component energyLine = Component.literal(curFmt + " / " + capFmt + " FE");
        g.drawString(this.font, MachineTheme.fit(this.font, energyLine, tp.width() - 8),
                tp.x() + 4, tp.y() + 5, PRISM_CYAN, false);

        Component outLine = Component.translatable("gui.industrialcore.singularity_reactor.output", rateFmt);
        g.drawString(this.font, MachineTheme.fit(this.font, outLine, 70),
                tp.x() + 4, tp.y() + 17, this.menu.isOverdriveMode() ? NEON_OVERDRIVE : GOLD, false);

        Rect ob = SingularityReactorLayout.OVERDRIVE_BUTTON;
        boolean od = this.menu.isOverdriveMode();
        boolean cd = this.menu.getOverdriveCooldownTicks() > 0;
        Component odTxt = Component.translatable(cd
                ? "gui.industrialcore.reactor.overdrive_btn_cooldown"
                : (od ? "gui.industrialcore.reactor.overdrive_btn_on"
                      : "gui.industrialcore.reactor.overdrive_btn_off"));
        Component fittedOd = MachineTheme.fit(this.font, odTxt, ob.width() - 4);
        int otx = ob.x() + (ob.width() - this.font.width(fittedOd)) / 2;
        g.drawString(this.font, fittedOd, otx, ob.y() + 3,
                cd ? MachineTheme.STATUS_BAD : (od ? NEON_AMBER : MachineTheme.MUTED), false);

        int burn = this.menu.getBurnRemaining();
        if (!this.menu.isMultiblockFormed()) {
            Component reqLine = Component.translatable("gui.industrialcore.reactor.min_req_short");
            g.drawString(this.font, MachineTheme.fit(this.font, reqLine, tp.width() - 8),
                    tp.x() + 4, tp.y() + 43, NEON_AMBER, false);
        } else {
            String secStr = burn > 0 ? String.format(Locale.ROOT, "%.1fs", burn / 20.0D) : "--";
            double combinedEff = SingularityReactorLogic.burnEfficiencyMultiplier(this.menu.getBatteryCount())
                    * ReactorMultiblockLogic.burnEfficiencyMultiplier(this.menu.getMultiblockCoolantBlocks());
            Component effLine = Component.translatable("gui.industrialcore.singularity_reactor.eff",
                    String.format(Locale.ROOT, "%.2f", combinedEff),
                    secStr);
            g.drawString(this.font, MachineTheme.fit(this.font, effLine, tp.width() - 8),
                    tp.x() + 4, tp.y() + 43, 0xFF9AC8E0, false);
        }

        // Fuel slot mini label above fuel slot (cleanly clear of status strip & slots)
        g.drawString(this.font, Component.translatable("gui.industrialcore.singularity_reactor.fuel_short"),
                SingularityReactorLayout.FUEL_SLOT_X + 20, SingularityReactorLayout.FUEL_SLOT_Y + 4,
                0xFF90B8CE, false);

        // Phase 103: Multiblock status strip & guide button label
        Rect mb = SingularityReactorLayout.MULTIBLOCK_BAR;
        Component mbText;
        int mbColor;
        if (this.menu.isMultiblockFormed()) {
            int packed = this.menu.getMultiblockDimsPacked();
            String mult = String.format(Locale.ROOT, "%.2f", this.menu.getMultiblockMultX100() / 100.0D);
            mbText = Component.translatable("gui.industrialcore.reactor.status.formed",
                    ReactorMultiblockLogic.unpackWidth(packed),
                    ReactorMultiblockLogic.unpackHeight(packed),
                    ReactorMultiblockLogic.unpackDepth(packed),
                    this.menu.getMultiblockCoreCells(),
                    this.menu.getMultiblockCoolantBlocks(),
                    mult);
            mbColor = NEON_EMERALD;
        } else {
            FailReason reason = FailReason.byOrdinal(this.menu.getMultiblockFailReason());
            mbText = Component.translatable(reason.translationKey());
            mbColor = reason == FailReason.NO_CORE_CELL ? NEON_AMBER : MachineTheme.STATUS_BAD;
        }
        g.drawString(this.font, MachineTheme.fit(this.font, mbText, mb.width() - 8),
                mb.x() + 4, mb.y() + 3, mbColor, false);

        Rect gb = SingularityReactorLayout.GUIDE_BUTTON;
        boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
        Component btnTxt = Component.translatable(guideOn
                ? "gui.industrialcore.reactor.guide_btn_on"
                : "gui.industrialcore.reactor.guide_btn_off");
        Component fittedBtn = MachineTheme.fit(this.font, btnTxt, gb.width() - 4);
        int tx = gb.x() + (gb.width() - this.font.width(fittedBtn)) / 2;
        g.drawString(this.font, fittedBtn, tx, gb.y() + 3, guideOn ? PRISM_CYAN : MachineTheme.MUTED, false);

        // Phase 115 / 116: Dimensional Rift Challenge side card labels with Tier 1..10 selector
        syncSelectedRiftTier();
        int maxTier = maxSelectableRiftTier();
        Rect rc = SingularityReactorLayout.RIFT_CARD;
        boolean raidEnabled = Config.enableDimensionalRaid();
        long raidCost = com.industrialcore.raid.DimensionalRaidLogic.activationCostForTier(
                Config.raidActivationCostFe(), selectedRiftTier);
        boolean formed = this.menu.isMultiblockFormed();
        boolean enoughFe = stored >= raidCost;
        boolean riftReady = raidEnabled && formed && enoughFe;
        int tierAccent = selectedRiftTier >= 6 ? 0xFFFF4D8D : (selectedRiftTier >= 3 ? GOLD : PRISM_CYAN);

        Component riftTitle = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.singularity_reactor.rift_title"), rc.width() - 8);
        g.drawString(this.font, riftTitle, rc.x() + (rc.width() - this.font.width(riftTitle)) / 2,
                rc.y() + 5, RIFT_PURPLE, false);

        // Tier selector arrows & readout (◀ T1/1 ▶)
        Rect prevR = SingularityReactorLayout.RIFT_TIER_PREV;
        Rect nextR = SingularityReactorLayout.RIFT_TIER_NEXT;
        g.drawString(this.font, Component.literal("<"), prevR.x() + 4, prevR.y() + 3,
                selectedRiftTier > 1 ? 0xFFFFFFFF : MachineTheme.MUTED, false);
        g.drawString(this.font, Component.literal(">"), nextR.x() + 4, nextR.y() + 3,
                selectedRiftTier < maxTier ? 0xFFFFFFFF : MachineTheme.MUTED, false);
        Component tierTxt = MachineTheme.fit(this.font,
                Component.literal("T" + selectedRiftTier + " / " + maxTier), rc.width() - 40);
        g.drawString(this.font, tierTxt, rc.x() + (rc.width() - this.font.width(tierTxt)) / 2,
                prevR.y() + 3, tierAccent, false);

        String rankKey = selectedRiftTier >= 6
                ? "gui.industrialcore.singularity_reactor.rift_rank_endgame"
                : (selectedRiftTier >= 3
                    ? "gui.industrialcore.singularity_reactor.rift_rank_aegis"
                    : "gui.industrialcore.singularity_reactor.rift_rank_intro");
        Component rankBadge = MachineTheme.fit(this.font, Component.translatable(rankKey), rc.width() - 10);
        g.drawString(this.font, rankBadge, rc.x() + (rc.width() - this.font.width(rankBadge)) / 2,
                rc.y() + 39, tierAccent, false);

        Component safeBadge = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.singularity_reactor.rift_safe_badge"), rc.width() - 10);
        g.drawString(this.font, safeBadge, rc.x() + 5, rc.y() + 51, NEON_EMERALD, false);

        String costFmt = BigCountFormatter.formatCompact(BigInteger.valueOf(raidCost), BigCountFormatter.UnitMode.ENGLISH);
        Component costLine = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.singularity_reactor.rift_cost", costFmt), rc.width() - 10);
        g.drawString(this.font, costLine, rc.x() + 5, rc.y() + 63,
                enoughFe ? GOLD : MachineTheme.STATUS_BAD, false);

        Rect rb = SingularityReactorLayout.RIFT_BUTTON;
        Component riftBtnTxt = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.singularity_reactor.rift_btn"), rb.width() - 6);
        int rtx = rb.x() + (rb.width() - this.font.width(riftBtnTxt)) / 2;
        g.drawString(this.font, riftBtnTxt, rtx, rb.y() + 5,
                riftReady ? 0xFFFFFFFF : MachineTheme.MUTED, false);

        String statusBadgeKey;
        int statusBadgeCol;
        if (!raidEnabled) {
            statusBadgeKey = "gui.industrialcore.singularity_reactor.rift_status_disabled";
            statusBadgeCol = MachineTheme.MUTED;
        } else if (!formed) {
            statusBadgeKey = "gui.industrialcore.singularity_reactor.rift_status_unformed";
            statusBadgeCol = NEON_AMBER;
        } else if (!enoughFe) {
            statusBadgeKey = "gui.industrialcore.singularity_reactor.rift_status_no_fe";
            statusBadgeCol = MachineTheme.STATUS_BAD;
        } else {
            statusBadgeKey = "gui.industrialcore.singularity_reactor.rift_status_ready";
            statusBadgeCol = NEON_EMERALD;
        }
        Component statusBadge = MachineTheme.fit(this.font, Component.translatable(statusBadgeKey), rc.width() - 10);
        g.drawString(this.font, statusBadge, rc.x() + (rc.width() - this.font.width(statusBadge)) / 2,
                rc.y() + 102, statusBadgeCol, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            double pmx = panelMouseX(mouseX);
            double pmy = panelMouseY(mouseY);
            Rect ob = SingularityReactorLayout.OVERDRIVE_BUTTON;
            int ox = this.leftPos + ob.x();
            int oy = this.topPos + ob.y();
            if (button == 0 && pmx >= ox && pmx < ox + ob.width() && pmy >= oy && pmy < oy + ob.height()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.industrialcore.network.ReactorOverdriveTogglePayload(this.menu.getBlockPos()));
                return true;
            }
            Rect prevR = SingularityReactorLayout.RIFT_TIER_PREV;
            if (button == 0 && pmx >= this.leftPos + prevR.x() && pmx < this.leftPos + prevR.right()
                    && pmy >= this.topPos + prevR.y() && pmy < this.topPos + prevR.bottom()) {
                syncSelectedRiftTier();
                if (selectedRiftTier > 1) {
                    selectedRiftTier--;
                    SoundDirector.playClick();
                } else {
                    SoundDirector.playUiLocked();
                }
                return true;
            }
            Rect nextR = SingularityReactorLayout.RIFT_TIER_NEXT;
            if (button == 0 && pmx >= this.leftPos + nextR.x() && pmx < this.leftPos + nextR.right()
                    && pmy >= this.topPos + nextR.y() && pmy < this.topPos + nextR.bottom()) {
                syncSelectedRiftTier();
                int maxT = maxSelectableRiftTier();
                if (selectedRiftTier < maxT) {
                    selectedRiftTier++;
                    SoundDirector.playClick();
                } else {
                    SoundDirector.playUiLocked();
                }
                return true;
            }
            Rect rb = SingularityReactorLayout.RIFT_BUTTON;
            int rbx = this.leftPos + rb.x();
            int rby = this.topPos + rb.y();
            if (button == 0 && pmx >= rbx && pmx < rbx + rb.width() && pmy >= rby && pmy < rby + rb.height()) {
                syncSelectedRiftTier();
                long cost = com.industrialcore.raid.DimensionalRaidLogic.activationCostForTier(
                        Config.raidActivationCostFe(), selectedRiftTier);
                SoundDirector.playClick();
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new RiftRaidStartPayload(this.menu.getBlockPos(), selectedRiftTier));
                if (Config.enableDimensionalRaid()
                        && this.menu.isMultiblockFormed()
                        && this.menu.getStoredEnergyLong() >= cost) {
                    this.onClose();
                }
                return true;
            }
            Rect gb = SingularityReactorLayout.GUIDE_BUTTON;
            int bx = this.leftPos + gb.x();
            int by = this.topPos + gb.y();
            if (pmx >= bx && pmx < bx + gb.width() && pmy >= by && pmy < by + gb.height()) {
                BlockPos pos = this.menu.getBlockPos();
                if (button == 1 && ReactorGuideRenderer.isGuideActiveFor(pos)) {
                    if (hasShiftDown()) {
                        ReactorGuideRenderer.cycleSize();
                    } else {
                        ReactorGuideRenderer.cycleLayer();
                    }
                    return true;
                }
                Direction facing = Direction.NORTH;
                if (this.minecraft != null && this.minecraft.level != null) {
                    BlockState st = this.minecraft.level.getBlockState(pos);
                    if (st.hasProperty(AbstractLifeBlock.FACING)) {
                        facing = st.getValue(AbstractLifeBlock.FACING);
                    }
                }
                ReactorGuideRenderer.toggleGuide(pos, facing, ReactorType.SINGULARITY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        double pmx = panelMouseX(mouseX);
        double pmy = panelMouseY(mouseY);
        Rect ob = SingularityReactorLayout.OVERDRIVE_BUTTON;
        int ox = this.leftPos + ob.x();
        int oy = this.topPos + ob.y();
        if (pmx >= ox && pmx < ox + ob.width() && pmy >= oy && pmy < oy + ob.height()) {
            g.renderComponentTooltip(this.font,
                    GenerationScreen.buildOverdriveTooltip(
                            this.menu.isOverdriveMode(), this.menu.getOverdriveCooldownTicks(), false),
                    mouseX, mouseY);
            return;
        }
        Rect rc = SingularityReactorLayout.RIFT_CARD;
        int rcx = this.leftPos + rc.x();
        int rcy = this.topPos + rc.y();
        if (pmx >= rcx && pmx < rcx + rc.width() && pmy >= rcy && pmy < rcy + rc.height()) {
            syncSelectedRiftTier();
            long tierCost = com.industrialcore.raid.DimensionalRaidLogic.activationCostForTier(
                    Config.raidActivationCostFe(), selectedRiftTier);
            var rew = com.industrialcore.raid.DimensionalRaidLogic.victoryRewards(
                    selectedRiftTier, Config.raidDifficultyMultiplier());
            String costFmt = BigCountFormatter.formatCompact(
                    BigInteger.valueOf(tierCost), BigCountFormatter.UnitMode.ENGLISH);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.industrialcore.singularity_reactor.rift_tip_header")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            tip.add(Component.translatable("gui.industrialcore.singularity_reactor.rift_tip_tier",
                    selectedRiftTier, maxSelectableRiftTier(),
                    String.format(Locale.ROOT, "%.1f",
                            com.industrialcore.raid.DimensionalRaidLogic.tierHealthMultiplier(selectedRiftTier)))
                    .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("gui.industrialcore.singularity_reactor.rift_tip_safe")
                    .withStyle(ChatFormatting.GREEN));
            tip.add(Component.translatable("gui.industrialcore.singularity_reactor.rift_tip_cost", costFmt)
                    .withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("gui.industrialcore.singularity_reactor.rift_tip_reward_tier",
                    rew.specializationModules(), String.format("%,d", rew.machineXp()))
                    .withStyle(ChatFormatting.YELLOW));
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            return;
        }
        Rect gb = SingularityReactorLayout.GUIDE_BUTTON;
        Rect mb = SingularityReactorLayout.MULTIBLOCK_BAR;
        int bx = this.leftPos + gb.x();
        int by = this.topPos + gb.y();
        int mx = this.leftPos + mb.x();
        int my = this.topPos + mb.y();
        boolean overGuide = pmx >= bx && pmx < bx + gb.width() && pmy >= by && pmy < by + gb.height();
        boolean overMbBar = !this.menu.isMultiblockFormed()
                && pmx >= mx && pmx < mx + mb.width() && pmy >= my && pmy < my + mb.height();
        if (overGuide || overMbBar) {
            boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
            g.renderComponentTooltip(this.font,
                    GenerationScreen.buildReactorGuideTooltip(guideOn, ReactorType.SINGULARITY), mouseX, mouseY);
            return;
        }
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                SingularityReactorLayout.MOTOR_SLOT_BOX, SingularityReactorLayout.BATTERY_SLOT_BOX,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case SingularityReactorLogic.STATE_GENERATING -> GOLD;
            case SingularityReactorLogic.STATE_FULL -> PRISM_CYAN;
            case SingularityReactorLogic.STATE_INCOMPLETE -> NEON_AMBER;
            default -> MachineTheme.STATUS_BAD;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case SingularityReactorLogic.STATE_GENERATING -> "gui.industrialcore.singularity_reactor.status.active";
            case SingularityReactorLogic.STATE_FULL -> "gui.industrialcore.singularity_reactor.status.full";
            case SingularityReactorLogic.STATE_INCOMPLETE -> "gui.industrialcore.reactor.status.incomplete";
            case SingularityReactorLogic.STATE_COOLDOWN -> "gui.industrialcore.stop_reason.overdrive_cooldown";
            default -> "gui.industrialcore.singularity_reactor.status.no_fuel";
        };
    }
}
