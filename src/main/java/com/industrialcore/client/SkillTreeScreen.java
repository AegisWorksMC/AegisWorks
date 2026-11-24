package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.level.SkillTreeFilterLogic;
import com.industrialcore.level.SkillTreeFilterLogic.DependencyRoutes;
import com.industrialcore.level.SkillTreeFilterLogic.FilterMode;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.level.SkillTreeLayout.InfraEntry;
import com.industrialcore.level.SkillTreeLayout.NodeSlot;
import com.industrialcore.level.SkillTreeLayout.NodeVisibility;
import com.industrialcore.level.SkillTreeLayout.NodeVisualState;
import com.industrialcore.level.SkillTreeLayout.OreLineStage;
import com.industrialcore.level.SkillTreeLayout.Rect;
import com.industrialcore.level.SkillTreeRevealAnim;
import com.industrialcore.menu.GuiFit;
import com.industrialcore.network.BranchLimitBreakPayload;
import com.industrialcore.network.MarkTreeRevealedPayload;
import com.industrialcore.network.RequestProgressPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 85–86: Beginner Guide &amp; Industrial Skill Tree Screen (3-tab adaptive screen).
 *
 * <ul>
 *   <li>Tab 1: Interactive 17-machine Skill Tree with Phase 86 fog-of-war silhouettes
 *       ({@code REVEALED} / {@code NEXT_TARGET} / {@code MYSTERY}) and 3-phase unlock animations
 *       (wire pulse -> burst &amp; padlock shatter -> fog-clearing reveal)</li>
 *   <li>Tab 2: 32,768x Ore Processing Line reference (Stage 1..15, synced with fog-of-war)</li>
 *   <li>Tab 3: Power Generation, Recovery/Storage &amp; Smart Logistics quick reference</li>
 * </ul>
 */
public class SkillTreeScreen extends Screen {
    private static final int GOLD = 0xFFFFD166;
    private static final int GOLD_BRIGHT = 0xFFFFF0A8;
    private static final int GOLD_DIM = 0xFF7D5F1A;
    private static final int PANEL_MARGIN = 6;
    private static final int ORE_BANNER_H = 28;
    private static final int ORE_ROW_H = 22;
    private static final int INFRA_HEADER_H = 16;
    private static final int INFRA_ROW_H = 24;

    @Nullable
    private final Screen returnScreen;
    private int activeTab;
    private Branch activeBranch;
    private String selectedMachineId;

    private float panelScale = 1.0F;
    private int panelX;
    private int panelY;
    /** Active frame origin including subtle decaying burst shake (keeps scissor & pose locked). */
    private int renderOriginX;
    private int renderOriginY;

    // Tab 1 pan/drag state
    private double panX;
    private double panY;
    private boolean draggingTree;
    private double dragLastPx;
    private double dragLastPy;

    // Tab 2 & 3 scroll state
    private double oreScroll;
    private double infraScroll;
    // Phase 103.5: Advanced Multiblock Reactor Guide modal state
    private boolean showingReactorGuide;

    // Phase 86: sequential unlock animation state
    @Nullable
    private SkillTreeRevealAnim revealAnim;
    private boolean revealCommitted;
    // Phase 87: 20-tick live progress poll counter while this screen stays open
    private int openTicks;

    // Phase 108: MMO Search, State Filter, & Crafting Ingredient cache
    private String searchQuery = "";
    private boolean searchFocused = false;
    private FilterMode filterMode = FilterMode.ALL;
    @Nullable
    private Object cachedRecipeManagerRef;
    private final Map<String, List<IngredientPreview>> recipeIngredientCache = new HashMap<>();
    private final List<IngredientHit> ingredientHits = new ArrayList<>();

    // Hitboxes populated during detail panel render for clickable prerequisite parent jumps
    private final List<ParentHit> parentHits = new ArrayList<>();

    private record ParentHit(Rect rect, String machineId) {}
    private record IngredientPreview(ItemStack stack, int count) {}
    private record IngredientHit(Rect rect, ItemStack stack, int count) {}

    public SkillTreeScreen(@Nullable String initialMachineId, @Nullable Screen returnScreen) {
        this(initialMachineId, returnScreen, SkillTreeLayout.TAB_TREE);
    }

    public SkillTreeScreen(@Nullable String initialMachineId, @Nullable Screen returnScreen, int initialTab) {
        super(Component.translatable("gui.industrialcore.skill_tree.title"));
        this.returnScreen = returnScreen;
        this.activeTab = Math.max(SkillTreeLayout.TAB_TREE, Math.min(SkillTreeLayout.TAB_INFRA, initialTab));
        if (initialMachineId != null && MachineSkillTree.isTracked(initialMachineId)) {
            this.selectedMachineId = initialMachineId;
            Branch b = MachineSkillTree.branchOf(initialMachineId);
            this.activeBranch = b != null ? b : Branch.PRODUCTION;
        } else {
            this.selectedMachineId = "crusher";
            this.activeBranch = Branch.PRODUCTION;
        }

        // Phase 86 / 97: auto-play sequential reveal animation for pending nodes in the focused branch
        List<String> pending = initialMachineId != null
                ? ClientMachineProgress.pendingRevealsInBranch(this.activeBranch)
                : ClientMachineProgress.pendingReveals();
        if (!pending.isEmpty()) {
            this.activeTab = SkillTreeLayout.TAB_TREE;
            String firstId = pending.get(0);
            Branch pb = MachineSkillTree.branchOf(firstId);
            if (pb != null) {
                this.activeBranch = pb;
            }
            List<String> branchPending = ClientMachineProgress.pendingRevealsInBranch(this.activeBranch);
            this.revealAnim = new SkillTreeRevealAnim(
                    ClientMachineProgress.revealedNodes(), branchPending, Util.getMillis());
            this.selectedMachineId = branchPending.get(0);
            this.panX = SkillTreeLayout.centerPanXFor(this.selectedMachineId);
        } else {
            this.panX = SkillTreeLayout.centerPanXFor(this.selectedMachineId);
        }
        this.panY = 0.0D;
    }

    @Override
    protected void init() {
        super.init();
        GuiFit fit = GuiFit.fit(this.width, this.height,
                SkillTreeLayout.WIDTH, SkillTreeLayout.HEIGHT, PANEL_MARGIN);
        this.panelScale = fit.scale();
        this.panelX = fit.x();
        this.panelY = fit.y();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(RequestProgressPayload.INSTANCE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.openTicks++;
        if (SkillTreeLayout.shouldPollProgress(this.openTicks)
                && this.minecraft != null
                && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(RequestProgressPayload.INSTANCE);
        }
    }

    public void setActiveTabForTesting(int tab) {
        this.activeTab = Math.max(SkillTreeLayout.TAB_TREE, Math.min(SkillTreeLayout.TAB_INFRA, tab));
    }

    public void setShowingReactorGuideForTesting(boolean open) {
        this.showingReactorGuide = open;
    }

    public boolean isShowingReactorGuide() {
        return this.showingReactorGuide;
    }

    public void selectMachineForTesting(String machineId) {
        if (MachineSkillTree.isTracked(machineId)) {
            Branch b = MachineSkillTree.branchOf(machineId);
            if (b != null) {
                this.activeBranch = b;
            }
            this.selectedMachineId = machineId;
            this.panX = SkillTreeLayout.centerPanXFor(machineId);
        }
    }

    public Branch getActiveBranch() {
        return this.activeBranch;
    }

    public void setActiveBranchForTesting(Branch branch) {
        if (branch != null) {
            switchBranch(branch);
        }
    }

    public void setSearchQueryForTesting(@Nullable String query) {
        this.searchQuery = query == null ? "" : query;
    }

    public void setFilterModeForTesting(@Nullable FilterMode mode) {
        this.filterMode = mode == null ? FilterMode.ALL : mode;
    }

    public FilterMode getFilterMode() {
        return this.filterMode;
    }

    public String getSearchQuery() {
        return this.searchQuery;
    }

    private void switchBranch(Branch next) {
        if (next == null) {
            return;
        }
        this.activeBranch = next;
        List<MachineSkillTree.Node> nodes = MachineSkillTree.nodesInBranch(next);
        if (!nodes.isEmpty()) {
            this.selectedMachineId = nodes.get(0).machineId();
        }
        this.panX = 0.0D;
        this.panY = 0.0D;
    }

    public boolean isRevealAnimationActive() {
        return revealAnim != null && revealAnim.isActive();
    }

    @Nullable
    public SkillTreeRevealAnim.Phase revealPhaseForTesting() {
        return revealAnim == null ? null : revealAnim.phase();
    }

    private Set<String> effectiveRevealed() {
        if (revealAnim != null && revealAnim.isActive()) {
            return revealAnim.effectiveRevealed();
        }
        return ClientMachineProgress.revealedNodes();
    }

    private void tickRevealAnim(long nowMs) {
        if (revealAnim == null || !revealAnim.isActive()) {
            return;
        }
        SkillTreeRevealAnim.StepEvent ev = revealAnim.tick(nowMs);
        if (ev.enteredWirePulse()) {
            SoundDirector.playWireRiser(ev.targetTier());
        }
        if (ev.enteredUnlockBurst()) {
            SoundDirector.playNodeUnlock(ev.targetTier());
            if (ev.targetMachineId() != null) {
                this.selectedMachineId = ev.targetMachineId();
            }
        }
        if (ev.enteredFogClear()) {
            SoundDirector.playFogClear(ev.targetTier());
        }
        String target = revealAnim.currentTargetId();
        if (target != null) {
            if (revealAnim.phase() == SkillTreeRevealAnim.Phase.INTRO_HOLD) {
                this.selectedMachineId = target;
            }
            double desiredPan = SkillTreeLayout.centerPanXFor(target);
            this.panX = SkillTreeLayout.clampPanX(this.panX + (desiredPan - this.panX) * 0.16D);
        }
        if (ev.finishedAll()) {
            commitRevealedNodes(revealAnim.allPendingIds());
        }
    }

    private void skipRevealAnim() {
        if (revealAnim == null || revealCommitted) {
            return;
        }
        List<String> ids = revealAnim.isActive() ? revealAnim.skipAll() : revealAnim.allPendingIds();
        commitRevealedNodes(ids);
    }

    private void commitRevealedNodes(List<String> ids) {
        if (revealCommitted || ids == null || ids.isEmpty()) {
            return;
        }
        revealCommitted = true;
        ClientMachineProgress.markRevealedLocally(ids);
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new MarkTreeRevealedPayload(ids));
        }
    }

    private double toPanelX(double screenX) {
        return panelScale <= 0.0F ? screenX : (screenX - panelX) / panelScale;
    }

    private double toPanelY(double screenY) {
        return panelScale <= 0.0F ? screenY : (screenY - panelY) / panelScale;
    }

    private void enablePanelScissor(GuiGraphics graphics, Rect rect) {
        int x0 = renderOriginX + (int) Math.floor(rect.x() * panelScale);
        int y0 = renderOriginY + (int) Math.floor(rect.y() * panelScale);
        int x1 = renderOriginX + (int) Math.ceil(rect.right() * panelScale);
        int y1 = renderOriginY + (int) Math.ceil(rect.bottom() * panelScale);
        graphics.enableScissor(x0, y0, x1, y1);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        tickRevealAnim(now);

        this.renderTransparentBackground(graphics);

        // Compute subtle decaying micro-shake for Tier 4–5 UNLOCK_BURST (applied equally to pose & scissor)
        double shakeX = 0.0D;
        double shakeY = 0.0D;
        double screenFlashAlpha = 0.0D;
        if (isRevealAnimationActive() && revealAnim.phase() == SkillTreeRevealAnim.Phase.UNLOCK_BURST) {
            SkillTreeRevealAnim.TierStyle style = revealAnim.currentStyle();
            double t = revealAnim.phaseProgress(now);
            if (style.maxShakePx() > 0.0D) {
                double decay = Math.pow(Math.max(0.0D, 1.0D - t), 2.2D);
                double amp = style.maxShakePx() * decay;
                shakeX = Math.sin(now * 0.095D) * amp;
                shakeY = Math.cos(now * 0.135D) * amp * 0.70D;
            }
            if (style.maxFlashAlpha() > 0.0D) {
                screenFlashAlpha = Math.min(0.22D,
                        style.maxFlashAlpha() * Math.pow(Math.max(0.0D, 1.0D - t), 1.8D));
            }
        }
        this.renderOriginX = panelX + (int) Math.round(shakeX * panelScale);
        this.renderOriginY = panelY + (int) Math.round(shakeY * panelScale);

        double px = toPanelX(mouseX);
        double py = toPanelY(mouseY);

        graphics.pose().pushPose();
        graphics.pose().translate(renderOriginX, renderOriginY, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);

        renderShell(graphics, px, py);
        renderTabs(graphics, px, py);

        switch (activeTab) {
            case SkillTreeLayout.TAB_TREE -> renderTreeTab(graphics, px, py, now);
            case SkillTreeLayout.TAB_ORELINE -> renderOreLineTab(graphics, px, py);
            case SkillTreeLayout.TAB_INFRA -> renderInfraTab(graphics, px, py);
            default -> renderTreeTab(graphics, px, py, now);
        }

        if (showingReactorGuide) {
            renderReactorGuideModal(graphics, px, py);
        }

        graphics.pose().popPose();

        // Subtle full-screen white-gold sheen on Tier 5 UNLOCK_BURST (capped <= 0.22 for eye comfort)
        if (screenFlashAlpha > 0.005D) {
            int fa = Math.max(0, Math.min(56, (int) Math.round(255.0D * screenFlashAlpha)));
            graphics.fill(0, 0, this.width, this.height, (fa << 24) | 0xFFF4D0);
        }

        // Tooltip rendered in screen space for sharp text
        if (!showingReactorGuide) {
            renderHoverTooltip(graphics, mouseX, mouseY, px, py);
        }
    }

    private void renderShell(GuiGraphics graphics, double px, double py) {
        MachineTheme.frame(graphics, 0, 0, SkillTreeLayout.WIDTH, SkillTreeLayout.HEIGHT);
        Rect hdr = SkillTreeLayout.HEADER;
        MachineTheme.gradient(graphics, hdr.x(), hdr.y(), hdr.width(), hdr.height(),
                MachineTheme.PANEL_TOP, MachineTheme.PANEL_BOTTOM);
        graphics.fill(hdr.x(), hdr.bottom() - 1, hdr.right(), hdr.bottom(), MachineTheme.LINE_SOFT);
        graphics.fill(28, hdr.bottom() - 1, 188, hdr.bottom(), MachineTheme.CYAN_DIM);

        MachineTheme.machineIcon(graphics, 10, 8, 12);
        graphics.drawString(this.font,
                MachineTheme.fit(this.font, this.title, 188),
                27, 10, MachineTheme.TEXT, false);

        // Global tree progress pill & branch limit-break count badge in top header
        Set<String> revealed = effectiveRevealed();
        int unlocked = revealed.size();
        int total = MachineSkillTree.totalNodes();
        int brokenBranches = 0;
        for (Branch b : Branch.values()) {
            if (ClientMachineProgress.isBranchLimitBroken(b)) {
                brokenBranches++;
            }
        }

        Component progText = Component.translatable("gui.industrialcore.skill_tree.progress", unlocked, total);
        Component statusText = Component.translatable("gui.industrialcore.skill_tree.branches_broken_badge",
                brokenBranches, Branch.values().length);

        int closeLeft = SkillTreeLayout.CLOSE_BUTTON.x();
        int statusW = Math.min(114, this.font.width(statusText) + 10);
        int statusX = closeLeft - 6 - statusW;
        int progW = Math.min(64, this.font.width(progText) + 10);
        int progX = statusX - 4 - progW;

        // Progress pill
        graphics.fill(progX, 7, progX + progW, 21, 0xFF0A141D);
        int progBorder = unlocked >= total ? GOLD : MachineTheme.CYAN_DIM;
        drawOutline(graphics, progX, 7, progW, 14, progBorder);
        graphics.drawString(this.font, MachineTheme.fit(this.font, progText, progW - 6),
                progX + 4, 10, unlocked >= total ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT, false);

        // Limit Break summary badge
        if (brokenBranches > 0) {
            double pulse = 0.5D + 0.5D * Math.sin(Util.getMillis() * 0.008D);
            int glowCol = MachineTheme.blend(GOLD, MachineTheme.PURPLE_BRIGHT, pulse * 0.5D);
            MachineTheme.glowRect(graphics, statusX, 7, statusW, 14, glowCol, 0.45D + 0.30D * pulse);
            graphics.fill(statusX, 7, statusX + statusW, 21, 0xFF23190B);
            drawOutline(graphics, statusX, 7, statusW, 14, glowCol);
            graphics.drawString(this.font, MachineTheme.fit(this.font, statusText, statusW - 6),
                    statusX + 4, 10, GOLD_BRIGHT, false);
        } else {
            graphics.fill(statusX, 7, statusX + statusW, 21, 0xFF101822);
            drawOutline(graphics, statusX, 7, statusW, 14, GOLD_DIM);
            graphics.drawString(this.font, MachineTheme.fit(this.font, statusText, statusW - 6),
                    statusX + 4, 10, GOLD, false);
        }

        // Close button
        Rect close = SkillTreeLayout.CLOSE_BUTTON;
        boolean closeHover = close.contains(px, py);
        graphics.fill(close.x(), close.y(), close.right(), close.bottom(),
                closeHover ? 0xFF3E1F24 : MachineTheme.PANEL_BOTTOM);
        drawOutline(graphics, close.x(), close.y(), close.width(), close.height(),
                closeHover ? MachineTheme.STATUS_BAD : MachineTheme.LINE_SOFT);
        graphics.drawCenteredString(this.font, "×", close.centerX(), close.y() + 3,
                closeHover ? 0xFFFFFFFF : MachineTheme.MUTED);
    }

    private void renderTabs(GuiGraphics graphics, double px, double py) {
        String[] tabKeys = {
                "gui.industrialcore.skill_tree.tab.tree",
                "gui.industrialcore.skill_tree.tab.oreline",
                "gui.industrialcore.skill_tree.tab.infra"
        };
        for (int i = 0; i < SkillTreeLayout.TAB_RECTS.size(); i++) {
            Rect r = SkillTreeLayout.TAB_RECTS.get(i);
            boolean active = (i == activeTab);
            boolean hover = r.contains(px, py);
            int topCol = active ? 0xFF172B3B : (hover ? 0xFF14212D : 0xFF0B1118);
            int botCol = active ? 0xFF0E1B26 : 0xFF070B10;
            MachineTheme.gradient(graphics, r.x(), r.y(), r.width(), r.height(), topCol, botCol);
            if (active) {
                MachineTheme.glowRect(graphics, r.x(), r.y(), r.width(), r.height(), MachineTheme.CYAN, 0.35D);
                drawOutline(graphics, r.x(), r.y(), r.width(), r.height(), MachineTheme.CYAN);
                graphics.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, MachineTheme.CYAN_BRIGHT);
            } else {
                drawOutline(graphics, r.x(), r.y(), r.width(), r.height(),
                        hover ? MachineTheme.CYAN_DIM : MachineTheme.LINE_SOFT);
            }
            Component label = Component.translatable(tabKeys[i]);
            Component fitted = MachineTheme.fit(this.font, label, r.width() - 8);
            int textCol = active ? MachineTheme.TEXT : (hover ? MachineTheme.CYAN_BRIGHT : MachineTheme.MUTED);
            graphics.drawCenteredString(this.font, fitted, r.centerX(), r.y() + 4, textCol);
        }
    }

    // =========================================================================
    // Tab 1: Skill Tree (Fog-of-War + 3-Phase Unlock Animation)
    // =========================================================================

    private boolean isSearchOrFilterActive() {
        return !searchQuery.isBlank() || filterMode != FilterMode.ALL;
    }

    private boolean nodeMatchesActiveSearchAndFilter(MachineSkillTree.Node node) {
        if (!isSearchOrFilterActive()) {
            return true;
        }
        String mName = Component.translatable(node.translationKey()).getString();
        String bName = Component.translatable(node.branch().translationKey()).getString();
        return SkillTreeFilterLogic.matches(
                node, searchQuery, filterMode,
                ClientMachineProgress::level, ClientMachineProgress::xp,
                mName, bName);
    }

    private void renderBranchSubTabsAndHeader(GuiGraphics graphics, double px, double py, long now) {
        boolean filtering = isSearchOrFilterActive();
        Map<Branch, Integer> matchCounts = filtering
                ? SkillTreeFilterLogic.countMatchesByBranch(
                        searchQuery, filterMode,
                        ClientMachineProgress::level, ClientMachineProgress::xp,
                        n -> Component.translatable(n.translationKey()).getString(),
                        b -> Component.translatable(b.translationKey()).getString())
                : Map.of();

        Branch[] all = Branch.values();
        for (int i = 0; i < all.length && i < SkillTreeLayout.BRANCH_TAB_RECTS.size(); i++) {
            Branch b = all[i];
            Rect r = SkillTreeLayout.BRANCH_TAB_RECTS.get(i);
            boolean active = (b == activeBranch);
            boolean hover = r.contains(px, py);
            int accent = b.accentColor();
            int bright = b.brightColor();
            int dim = b.dimColor();

            int bgTop = active ? blendDark(accent, 0.26D) : (hover ? blendDark(accent, 0.14D) : 0xFF0A1018);
            int bgBot = active ? blendDark(accent, 0.12D) : 0xFF060A0F;
            MachineTheme.gradient(graphics, r.x(), r.y(), r.width(), r.height(), bgTop, bgBot);
            if (active) {
                MachineTheme.glowRect(graphics, r.x(), r.y(), r.width(), r.height(), accent, 0.32D);
                drawOutline(graphics, r.x(), r.y(), r.width(), r.height(), accent);
                graphics.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, bright);
            } else {
                drawOutline(graphics, r.x(), r.y(), r.width(), r.height(), hover ? accent : dim);
            }
            boolean broken = ClientMachineProgress.isBranchLimitBroken(b);
            Component baseLabel = broken
                    ? Component.literal("✦ ").append(Component.translatable(b.translationKey()))
                    : Component.translatable(b.translationKey());
            Component label = filtering
                    ? baseLabel.copy().append(Component.literal(" (" + matchCounts.getOrDefault(b, 0) + ")"))
                    : baseLabel;
            Component fit = MachineTheme.fit(this.font, label, r.width() - 8);
            int txtCol = active ? (broken ? GOLD_BRIGHT : 0xFFFFFFFF) : (hover ? bright : MachineTheme.MUTED);
            if (filtering && !active && matchCounts.getOrDefault(b, 0) == 0) {
                txtCol = 0xFF475766;
            }
            graphics.drawCenteredString(this.font, fit, r.centerX(), r.y() + 4, txtCol);

            // Pending-reveal indicator dot if this branch has unviewed newly unlocked nodes
            if (!ClientMachineProgress.pendingRevealsInBranch(b).isEmpty()) {
                graphics.fill(r.right() - 5, r.y() + 3, r.right() - 2, r.y() + 6, GOLD_BRIGHT);
            }
        }

        // Phase 108: MMO Search box & 4 State Filter pills (y = 69..85)
        renderSearchAndFilterBar(graphics, px, py, now);

        // Branch progress bar (left) + Limit Break button / status (right)
        Rect bh = SkillTreeLayout.BRANCH_HEADER_BAR;
        int bTotal = MachineSkillTree.nodesInBranch(activeBranch).size();
        int bUnlocked = MachineSkillTree.unlockedCountInBranch(activeBranch, ClientMachineProgress::level);
        int bMaxed = MachineSkillTree.maxedCountInBranch(activeBranch, ClientMachineProgress::level);
        graphics.fill(bh.x(), bh.y(), bh.right(), bh.bottom(), 0xFF091019);
        drawOutline(graphics, bh.x(), bh.y(), bh.width(), bh.height(), activeBranch.dimColor());
        graphics.fill(bh.x() + 1, bh.y() + 1, bh.x() + 3, bh.bottom() - 1, activeBranch.accentColor());

        Component brSummary = Component.translatable("gui.industrialcore.skill_tree.branch_progress",
                Component.translatable(activeBranch.translationKey()), bUnlocked, bTotal, bMaxed, bTotal);
        graphics.drawString(this.font, MachineTheme.fit(this.font, brSummary, bh.width() - 10),
                bh.x() + 6, bh.y() + 4, activeBranch.brightColor(), false);

        // Per-branch Lv.1000 Limit Break button / badge
        Rect lb = SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON;
        boolean broken = ClientMachineProgress.isBranchLimitBroken(activeBranch);
        boolean ready = ClientMachineProgress.canLimitBreakBranch(activeBranch);
        boolean lbHover = lb.contains(px, py);
        if (broken) {
            double pulse = 0.5D + 0.5D * Math.sin(now * 0.008D);
            int glowCol = MachineTheme.blend(GOLD, MachineTheme.PURPLE_BRIGHT, pulse * 0.55D);
            MachineTheme.glowRect(graphics, lb.x(), lb.y(), lb.width(), lb.height(), glowCol, 0.35D + 0.25D * pulse);
            graphics.fill(lb.x(), lb.y(), lb.right(), lb.bottom(), 0xFF21160A);
            drawOutline(graphics, lb.x(), lb.y(), lb.width(), lb.height(), glowCol);
            Component txt = Component.translatable("gui.industrialcore.skill_tree.branch_limit_broken",
                    MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL);
            graphics.drawCenteredString(this.font, MachineTheme.fit(this.font, txt, lb.width() - 6),
                    lb.centerX(), lb.y() + 4, GOLD_BRIGHT);
        } else if (ready) {
            double pulse = 0.5D + 0.5D * Math.sin(now * 0.012D);
            int border = lbHover ? 0xFFFFFFFF : MachineTheme.blend(GOLD, GOLD_BRIGHT, pulse);
            MachineTheme.glowRect(graphics, lb.x(), lb.y(), lb.width(), lb.height(), GOLD_BRIGHT, 0.50D + 0.35D * pulse);
            graphics.fill(lb.x(), lb.y(), lb.right(), lb.bottom(), lbHover ? 0xFF3E2C0B : 0xFF2B1F09);
            drawOutline(graphics, lb.x(), lb.y(), lb.width(), lb.height(), border);
            Component txt = Component.translatable("gui.industrialcore.skill_tree.branch_limit_btn",
                    MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL);
            graphics.drawCenteredString(this.font, MachineTheme.fit(this.font, txt, lb.width() - 6),
                    lb.centerX(), lb.y() + 4, 0xFFFFF8D6);
        } else {
            graphics.fill(lb.x(), lb.y(), lb.right(), lb.bottom(), 0xFF0B1118);
            drawOutline(graphics, lb.x(), lb.y(), lb.width(), lb.height(), GOLD_DIM);
            Component txt = Component.translatable("gui.industrialcore.skill_tree.branch_limit_locked",
                    bMaxed, bTotal);
            graphics.drawCenteredString(this.font, MachineTheme.fit(this.font, txt, lb.width() - 6),
                    lb.centerX(), lb.y() + 4, 0xFFB09A6A);
        }
    }

    private void renderSearchAndFilterBar(GuiGraphics graphics, double px, double py, long now) {
        Rect sb = SkillTreeLayout.SEARCH_BOX;
        boolean sbHover = sb.contains(px, py);
        int sbBorder = searchFocused ? MachineTheme.CYAN_BRIGHT
                : (sbHover ? MachineTheme.CYAN : MachineTheme.LINE_SOFT);
        graphics.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), searchFocused ? 0xFF0C1824 : 0xFF081018);
        drawOutline(graphics, sb.x(), sb.y(), sb.width(), sb.height(), sbBorder);

        // Magnifying glass glyph (compact pixel icon)
        graphics.drawString(this.font, "⌕", sb.x() + 4, sb.y() + 4,
                searchFocused ? MachineTheme.CYAN_BRIGHT : MachineTheme.MUTED, false);

        int textMaxW = sb.width() - (searchQuery.isEmpty() ? 16 : 28);
        if (searchQuery.isEmpty() && !searchFocused) {
            Component ph = Component.translatable("gui.industrialcore.skill_tree.search_placeholder");
            graphics.drawString(this.font, MachineTheme.fit(this.font, ph, textMaxW),
                    sb.x() + 13, sb.y() + 4, 0xFF5A6E80, false);
        } else {
            boolean caret = searchFocused && ((now / 450L) & 1L) == 0L;
            String shown = searchQuery + (caret ? "_" : "");
            // Trim left if query exceeds width so the typing caret always stays visible
            while (!shown.isEmpty() && this.font.width(shown) > textMaxW) {
                shown = shown.substring(1);
            }
            graphics.drawString(this.font, shown, sb.x() + 13, sb.y() + 4, 0xFFEAF7FF, false);
        }

        if (!searchQuery.isEmpty()) {
            Rect clr = SkillTreeLayout.SEARCH_CLEAR_BUTTON;
            boolean clrHover = clr.contains(px, py);
            graphics.fill(clr.x(), clr.y(), clr.right(), clr.bottom(),
                    clrHover ? 0xFF3A1E24 : 0xFF13202D);
            graphics.drawCenteredString(this.font, "×", clr.centerX(), clr.y() + 2,
                    clrHover ? 0xFFFFFFFF : MachineTheme.MUTED);
        }

        // 4 State Filter buttons: ALL / UNLOCKABLE / UNLOCKED / LOCKED
        FilterMode[] modes = FilterMode.values();
        for (int i = 0; i < modes.length && i < SkillTreeLayout.FILTER_RECTS.size(); i++) {
            FilterMode m = modes[i];
            Rect fr = SkillTreeLayout.FILTER_RECTS.get(i);
            boolean active = (m == filterMode);
            boolean hover = fr.contains(px, py);
            int accent = switch (m) {
                case UNLOCKABLE -> 0xFF38F8FF;
                case UNLOCKED -> MachineTheme.STATUS_OK;
                case LOCKED -> MachineTheme.STATUS_WARN;
                default -> MachineTheme.CYAN;
            };
            int bgTop = active ? blendDark(accent, 0.28D) : (hover ? blendDark(accent, 0.14D) : 0xFF091018);
            int bgBot = active ? blendDark(accent, 0.12D) : 0xFF060A0F;
            MachineTheme.gradient(graphics, fr.x(), fr.y(), fr.width(), fr.height(), bgTop, bgBot);
            if (active) {
                MachineTheme.glowRect(graphics, fr.x(), fr.y(), fr.width(), fr.height(), accent, 0.28D);
                drawOutline(graphics, fr.x(), fr.y(), fr.width(), fr.height(), accent);
            } else {
                drawOutline(graphics, fr.x(), fr.y(), fr.width(), fr.height(),
                        hover ? accent : MachineTheme.LINE_SOFT);
            }
            Component lbl = Component.translatable(m.translationKey());
            graphics.drawCenteredString(this.font, MachineTheme.fit(this.font, lbl, fr.width() - 6),
                    fr.centerX(), fr.y() + 4,
                    active ? 0xFFFFFFFF : (hover ? accent : MachineTheme.MUTED));
        }
    }

    private static int blendDark(int argb, double factor) {
        int r = (int) Math.round(((argb >> 16) & 0xFF) * factor) + 6;
        int g = (int) Math.round(((argb >> 8) & 0xFF) * factor) + 10;
        int b = (int) Math.round((argb & 0xFF) * factor) + 16;
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private void renderTreeTab(GuiGraphics graphics, double px, double py, long now) {
        renderBranchSubTabsAndHeader(graphics, px, py, now);

        Rect vp = SkillTreeLayout.TREE_VIEWPORT;
        MachineTheme.bay(graphics, 0, 0, vp.x(), vp.y(), vp.width(), vp.height());

        Set<String> revealed = effectiveRevealed();
        String hoveredId = SkillTreeLayout.nodeAt(activeBranch, px, py, panX, panY);
        String focusId = hoveredId != null ? hoveredId : selectedMachineId;
        DependencyRoutes focusRoutes = SkillTreeFilterLogic.extractRoutes(focusId);
        boolean filtering = isSearchOrFilterActive();
        boolean branchLimitBroken = ClientMachineProgress.isBranchLimitBroken(activeBranch);

        enablePanelScissor(graphics, new Rect(vp.x() + 2, vp.y() + 2, vp.width() - 4, vp.height() - 4));
        int ox = vp.x() - (int) Math.round(panX);
        int oy = vp.y() - (int) Math.round(panY);

        // Subtle engineering grid + Tier column headers
        for (int tier = 1; tier <= 5; tier++) {
            int colX = ox + SkillTreeLayout.COL_START_X + (tier - 1) * SkillTreeLayout.COL_STRIDE;
            if (tier > 1) {
                int divX = colX - 10;
                graphics.fill(divX, vp.y() + 3, divX + 1, vp.bottom() - 8, 0xFF0E1720);
            }
            String tierLbl = "T" + tier;
            graphics.drawCenteredString(this.font, tierLbl, colX + SkillTreeLayout.NODE_W / 2,
                    oy + 3, 0xFF45596B);
        }

        // 1. Directed edges for active branch (background pass, then highlighted upstream/downstream pass)
        drawEdges(graphics, ox, oy, revealed, focusRoutes, false);
        drawEdges(graphics, ox, oy, revealed, focusRoutes, true);

        // Phase 86 Phase 1: traveling tier-scaled wire energy pulses toward the animating target
        if (isRevealAnimationActive() && revealAnim.phase() == SkillTreeRevealAnim.Phase.WIRE_PULSE) {
            drawWirePulseOverlay(graphics, ox, oy, revealAnim.currentTargetId(),
                    revealAnim.phaseProgress(now), revealAnim.currentStyle());
        }

        // 2. Machine node cards for active branch
        boolean introHoldPhase = isRevealAnimationActive()
                && revealAnim.phase() == SkillTreeRevealAnim.Phase.INTRO_HOLD;
        boolean burstPhase = isRevealAnimationActive()
                && revealAnim.phase() == SkillTreeRevealAnim.Phase.UNLOCK_BURST;
        boolean fogClearPhase = isRevealAnimationActive()
                && revealAnim.phase() == SkillTreeRevealAnim.Phase.FOG_CLEAR;
        double introT = introHoldPhase ? revealAnim.phaseProgress(now) : 0.0D;
        double fogT = fogClearPhase ? revealAnim.phaseProgress(now) : 1.0D;
        String animTargetId = isRevealAnimationActive() ? revealAnim.currentTargetId() : null;
        int animTier = isRevealAnimationActive() ? revealAnim.currentTier() : 2;
        List<String> uncovering = (burstPhase || fogClearPhase) ? revealAnim.uncoveringChildren() : List.of();

        for (NodeSlot slot : SkillTreeLayout.nodeSlots(activeBranch)) {
            MachineSkillTree.Node node = MachineSkillTree.byId(slot.machineId());
            if (node == null) {
                continue;
            }
            int nx = ox + slot.rect().x();
            int ny = oy + slot.rect().y();
            int nw = slot.rect().width();
            int nh = slot.rect().height();
            boolean selected = slot.machineId().equals(selectedMachineId);
            boolean hovered = slot.machineId().equals(hoveredId);
            boolean matched = nodeMatchesActiveSearchAndFilter(node);
            boolean dimmed = filtering && !matched;
            NodeVisibility vis = SkillTreeLayout.visibilityOf(node, revealed);

            if (vis == NodeVisibility.MYSTERY || (burstPhase && uncovering.contains(slot.machineId()))) {
                renderMysteryNodeCard(graphics, nx, ny, nw, nh, selected, hovered);
                if (dimmed) {
                    graphics.fill(nx, ny, nx + nw, ny + nh, 0xB804070C);
                } else if (filtering && matched) {
                    drawOutline(graphics, nx - 1, ny - 1, nw + 2, nh + 2, 0xFF38F8FF);
                }
            } else {
                NodeVisualState state = (vis == NodeVisibility.NEXT_TARGET)
                        ? NodeVisualState.LOCKED
                        : SkillTreeLayout.visualState(node, ClientMachineProgress::level, branchLimitBroken);
                boolean unlockableFrontier = (vis == NodeVisibility.REVEALED)
                        && SkillTreeFilterLogic.isUnlockableFrontier(
                                node, ClientMachineProgress::level, ClientMachineProgress::xp);
                int padlockJitter = (introHoldPhase && slot.machineId().equals(animTargetId) && introT > 0.25D)
                        ? (int) Math.round(Math.sin(now * 0.075D))
                        : 0;
                renderNodeCard(graphics, node, nx, ny, nw, nh, state, selected, hovered,
                        unlockableFrontier && !dimmed, filtering && matched, now, padlockJitter);
                if (dimmed) {
                    graphics.fill(nx, ny, nx + nw, ny + nh, 0xB804070C);
                }

                if (introHoldPhase && slot.machineId().equals(animTargetId)) {
                    renderIntroHoldOverlay(graphics, nx, ny, nw, nh, introT, animTier);
                }

                if (fogClearPhase && uncovering.contains(slot.machineId())) {
                    renderFogClearOverlay(graphics, nx, ny, nw, nh, fogT, animTier);
                }
            }
        }

        if (burstPhase) {
            NodeSlot targetSlot = SkillTreeLayout.slotFor(animTargetId);
            if (targetSlot != null && targetSlot.branch() == activeBranch) {
                renderUnlockBurstOverlay(graphics,
                        ox + targetSlot.rect().x(), oy + targetSlot.rect().y(),
                        targetSlot.rect().width(), targetSlot.rect().height(),
                        revealAnim.phaseProgress(now), revealAnim.currentStyle());
            }
        }

        if (isRevealAnimationActive()) {
            Component hint = Component.translatable("gui.industrialcore.skill_tree.anim_skip_hint");
            int hw = Math.min(vp.width() - 16, this.font.width(hint) + 12);
            int hx = vp.x() + (vp.width() - hw) / 2;
            int hy = vp.bottom() - 18;
            graphics.fill(hx, hy, hx + hw, hy + 11, 0xCC0A131C);
            drawOutline(graphics, hx, hy, hw, 11, GOLD_DIM);
            graphics.drawCenteredString(this.font, MachineTheme.fit(this.font, hint, hw - 6),
                    hx + hw / 2, hy + 2, GOLD_BRIGHT);
        }

        graphics.disableScissor();

        int maxPan = SkillTreeLayout.maxPanX();
        if (maxPan > 0) {
            int trackX = vp.x() + 6;
            int trackY = vp.bottom() - 5;
            int trackW = vp.width() - 12;
            graphics.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF101922);
            int thumbW = Math.max(24, trackW * vp.width() / SkillTreeLayout.CANVAS_W);
            int thumbX = trackX + (int) Math.round((trackW - thumbW) * (panX / maxPan));
            graphics.fill(thumbX, trackY, thumbX + thumbW, trackY + 2,
                    draggingTree ? activeBranch.brightColor() : activeBranch.accentColor());
        }

        renderDetailPanel(graphics, px, py, revealed, branchLimitBroken);
    }

    private void drawEdges(GuiGraphics graphics, int ox, int oy, Set<String> revealed,
                           DependencyRoutes focusRoutes, boolean activePass) {
        int branchAccent = activeBranch.accentColor();
        for (MachineSkillTree.Node child : MachineSkillTree.nodesInBranch(activeBranch)) {
            NodeSlot childSlot = SkillTreeLayout.slotFor(child.machineId());
            if (childSlot == null) {
                continue;
            }
            NodeVisibility childVis = SkillTreeLayout.visibilityOf(child, revealed);
            for (String parentId : child.parents()) {
                NodeSlot parentSlot = SkillTreeLayout.slotFor(parentId);
                if (parentSlot == null) {
                    continue;
                }
                boolean childRevealed = (childVis == NodeVisibility.REVEALED);
                boolean parentMet = childRevealed || (revealed.contains(parentId)
                        && ClientMachineProgress.level(parentId) >= child.requiredLevel()
                        && childVis != NodeVisibility.MYSTERY);
                boolean isUpstream = focusRoutes.isUpstreamEdge(parentId, child.machineId());
                boolean isDownstream = focusRoutes.isDownstreamEdge(parentId, child.machineId());
                boolean isRouteEdge = isUpstream || isDownstream;
                boolean isHighlight = isRouteEdge || (childVis != NodeVisibility.MYSTERY && parentMet);
                if (isHighlight != activePass) {
                    continue;
                }

                int x1 = ox + parentSlot.rect().right();
                int y1 = oy + parentSlot.rect().centerY();
                int x2 = ox + childSlot.rect().x();
                int y2 = oy + childSlot.rect().centerY();
                int midX = (x2 - x1 <= 24) ? (x1 + x2) / 2 : (x2 - 9);

                // Phase 86 / 108: MYSTERY edges render as dashed lines (brightened when on active downstream route)
                if (childVis == NodeVisibility.MYSTERY) {
                    int fogCol = isDownstream ? 0xFF4CC6E6 : (isUpstream ? 0xFFB89C4E : 0xFF1B2632);
                    drawDashedHLine(graphics, x1, midX, y1, fogCol);
                    drawDashedVLine(graphics, midX, y1, y2, fogCol);
                    drawDashedHLine(graphics, midX, x2, y2, fogCol);
                    if (isRouteEdge) {
                        graphics.fill(x2 - 2, y2 - 1, x2 - 1, y2 + 2, fogCol);
                        graphics.fill(x2 - 1, y2, x2, y2 + 1, fogCol);
                    }
                    continue;
                }

                int col;
                int glowAlpha;
                if (isUpstream) {
                    // High-luminance gold for upstream prerequisites leading into focused node
                    col = parentMet ? 0xFFFFE666 : 0xFFFFB84D;
                    glowAlpha = 0x62;
                } else if (isDownstream) {
                    // High-luminance neon cyan for downstream derived nodes branching out
                    col = 0xFF42F8FF;
                    glowAlpha = 0x5A;
                } else if (parentMet) {
                    col = blendDark(branchAccent, 0.72D);
                    glowAlpha = 0x26;
                } else {
                    col = 0xFF212D3A;
                    glowAlpha = 0;
                }

                if (glowAlpha > 0) {
                    int glow = (glowAlpha << 24) | (col & 0x00FFFFFF);
                    drawHLine(graphics, x1, midX, y1 - 1, glow);
                    drawHLine(graphics, x1, midX, y1 + 1, glow);
                    drawVLine(graphics, midX - 1, y1, y2, glow);
                    drawVLine(graphics, midX + 1, y1, y2, glow);
                    drawHLine(graphics, midX, x2, y2 - 1, glow);
                    drawHLine(graphics, midX, x2, y2 + 1, glow);
                }
                drawHLine(graphics, x1, midX, y1, col);
                drawVLine(graphics, midX, y1, y2, col);
                drawHLine(graphics, midX, x2, y2, col);

                // Arrowhead pointing into child's left border
                graphics.fill(x2 - 2, y2 - 1, x2 - 1, y2 + 2, col);
                graphics.fill(x2 - 1, y2, x2, y2 + 1, col);
            }
        }
    }

    /** Phase 0 (INTRO_HOLD): soft breathing anticipation glow + focus brackets before wire pulse fires. */
    private void renderIntroHoldOverlay(GuiGraphics graphics, int x, int y, int w, int h,
                                        double t, int tier) {
        double breathe = 0.35D + 0.65D * Math.sin(t * Math.PI * 2.0D) * Math.sin(t * Math.PI * 2.0D);
        int accent = switch (tier) {
            case 5 -> MachineTheme.blend(MachineTheme.PURPLE_BRIGHT, GOLD_BRIGHT, 0.5D);
            case 4 -> GOLD_BRIGHT;
            case 3 -> MachineTheme.CYAN_BRIGHT;
            default -> MachineTheme.CYAN;
        };
        MachineTheme.glowRect(graphics, x, y, w, h, accent, 0.45D + 0.50D * breathe);
        int rimAlpha = Math.max(40, Math.min(240, (int) Math.round(190.0D * breathe)));
        drawOutline(graphics, x - 2, y - 2, w + 4, h + 4, (rimAlpha << 24) | (accent & 0x00FFFFFF));
    }

    /** Phase 1 (WIRE_PULSE): tier-scaled traveling energy pulse(s) racing along parent -> target wires. */
    private void drawWirePulseOverlay(GuiGraphics graphics, int ox, int oy,
                                      @Nullable String targetId, double t,
                                      SkillTreeRevealAnim.TierStyle style) {
        MachineSkillTree.Node child = MachineSkillTree.byId(targetId);
        NodeSlot childSlot = SkillTreeLayout.slotFor(targetId);
        if (child == null || childSlot == null) {
            return;
        }
        int x2 = ox + childSlot.rect().x();
        int y2 = oy + childSlot.rect().centerY();
        int pulses = style.pulseCount();
        int trailLen = style.tier() >= 5 ? 44 : (style.tier() == 4 ? 34 : 26);

        for (String parentId : child.parents()) {
            NodeSlot parentSlot = SkillTreeLayout.slotFor(parentId);
            if (parentSlot == null) {
                continue;
            }
            int x1 = ox + parentSlot.rect().right();
            int y1 = oy + parentSlot.rect().centerY();
            int midX = (x2 - x1 <= 24) ? (x1 + x2) / 2 : (x2 - 9);

            int len1 = Math.abs(midX - x1);
            int len2 = Math.abs(y2 - y1);
            int len3 = Math.abs(x2 - midX);
            int totalLen = Math.max(1, len1 + len2 + len3);

            for (int p = 0; p < pulses; p++) {
                double delay = p * 0.16D;
                double span = Math.max(0.25D, 1.0D - (pulses - 1) * 0.16D);
                double localT = (t - delay) / span;
                if (localT < 0.0D || localT > 1.0D) {
                    continue;
                }
                double eased = easeInOutCubic(localT);
                int headDist = (int) Math.round(eased * totalLen);

                int trailCol = switch (style.tier()) {
                    case 5 -> p == 0 ? MachineTheme.PURPLE_BRIGHT
                            : (p == 1 ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT);
                    case 4 -> p == 0 ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT;
                    case 3 -> p == 0 ? MachineTheme.CYAN : 0xFF7CF7FF;
                    default -> MachineTheme.CYAN_BRIGHT;
                };

                for (int d = Math.max(0, headDist - trailLen); d <= headDist; d += 2) {
                    int[] pt = sampleWirePoint(x1, y1, midX, x2, y2, len1, len2, d);
                    double frac = 1.0D - (double) (headDist - d) / trailLen;
                    int a = Math.max(24, Math.min(255, (int) Math.round(235.0D * frac)));
                    int c = (a << 24) | (trailCol & 0x00FFFFFF);
                    int r = (style.tier() >= 4 && frac > 0.65D) ? 2 : 1;
                    graphics.fill(pt[0] - r, pt[1] - r, pt[0] + r + 1, pt[1] + r + 1, c);
                }
                int[] head = sampleWirePoint(x1, y1, midX, x2, y2, len1, len2, headDist);
                int haloPad = style.tier() >= 4 ? 3 : 2;
                graphics.fill(head[0] - haloPad, head[1] - haloPad,
                        head[0] + haloPad + 1, head[1] + haloPad + 1,
                        (0x66 << 24) | (trailCol & 0x00FFFFFF));
                graphics.fill(head[0] - 1, head[1] - 1, head[0] + 2, head[1] + 2, trailCol);
                graphics.fill(head[0], head[1], head[0] + 1, head[1] + 1, 0xFFFFFFFF);
            }
        }
    }

    private static int[] sampleWirePoint(int x1, int y1, int midX, int x2, int y2,
                                         int len1, int len2, int dist) {
        if (dist <= len1) {
            int dir = midX >= x1 ? 1 : -1;
            return new int[] {x1 + dir * dist, y1};
        }
        int rem = dist - len1;
        if (rem <= len2) {
            int dir = y2 >= y1 ? 1 : -1;
            return new int[] {midX, y1 + dir * rem};
        }
        int rem2 = rem - len2;
        int dir = x2 >= midX ? 1 : -1;
        return new int[] {midX + dir * rem2, y2};
    }

    /** Phase 2 (UNLOCK_BURST): tier-scaled shockwave rings, rays, stars & particle shower. */
    private void renderUnlockBurstOverlay(GuiGraphics graphics, int x, int y, int w, int h, double t,
                                          SkillTreeRevealAnim.TierStyle style) {
        double eased = easeOutCubic(t);
        double fade = Math.max(0.0D, 1.0D - t);
        int cx = x + w / 2;
        int cy = y + h / 2;
        int tier = style.tier();

        // 1. Multi-layer card bloom & core flash
        int flashCol = tier >= 5
                ? MachineTheme.blend(0xFFFFFFFF, MachineTheme.PURPLE_BRIGHT, Math.min(1.0D, t * 1.2D))
                : MachineTheme.blend(0xFFFFFFFF, tier >= 4 ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT, Math.min(1.0D, t * 1.4D));
        MachineTheme.glowRect(graphics, x, y, w, h, flashCol, (1.35D + 0.15D * (tier - 2)) * fade);
        if (t < 0.45D) {
            int veilAlpha = (int) Math.round(185.0D * (1.0D - t / 0.45D));
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, (veilAlpha << 24) | 0xFFFFFF);
        }

        // 2. Radial light rays (Tier 4–5)
        if (style.radialRays() && t < 0.80D) {
            double rayFade = Math.max(0.0D, 1.0D - t / 0.80D);
            int rayCount = tier >= 5 ? 12 : 8;
            double maxLen = (tier >= 5 ? 46.0D : 34.0D) * eased;
            for (int r = 0; r < rayCount; r++) {
                double ang = (Math.PI * 2.0D * r) / rayCount + 0.20D;
                int rayBase = tier >= 5
                        ? (r % 3 == 0 ? GOLD_BRIGHT : (r % 3 == 1 ? MachineTheme.PURPLE_BRIGHT : MachineTheme.CYAN_BRIGHT))
                        : GOLD_BRIGHT;
                int steps = 10;
                for (int s = 2; s <= steps; s++) {
                    double frac = (double) s / steps;
                    double dist = 8.0D + maxLen * frac;
                    int rx = cx + (int) Math.round(Math.cos(ang) * dist);
                    int ry = cy + (int) Math.round(Math.sin(ang) * dist * 0.72D);
                    int ra = Math.max(0, Math.min(170, (int) Math.round(150.0D * rayFade * (1.0D - frac * 0.75D))));
                    if (ra > 8) {
                        graphics.fill(rx, ry, rx + 2, ry + 2, (ra << 24) | (rayBase & 0x00FFFFFF));
                    }
                }
            }
        }

        // 3. Expanding concentric shockwave rings (Tier 2: 2, Tier 3: 3, Tier 4: 3 gold, Tier 5: 4 prism)
        int rings = style.shockwaveRings();
        for (int ri = 0; ri < rings; ri++) {
            double ringT = Math.max(0.0D, Math.min(1.0D, (t - ri * 0.09D) / Math.max(0.25D, 1.0D - ri * 0.09D)));
            if (ringT <= 0.0D) {
                continue;
            }
            double ringEase = easeOutCubic(ringT);
            double ringFade = Math.max(0.0D, 1.0D - ringT);
            int maxPad = 18 + (rings - ri) * 7 + (tier >= 5 ? 8 : 0);
            int pad = (int) Math.round(1.0D + maxPad * ringEase);
            int ringCol = switch (tier) {
                case 5 -> switch (ri) {
                    case 0 -> MachineTheme.PURPLE_BRIGHT;
                    case 1 -> GOLD_BRIGHT;
                    case 2 -> MachineTheme.CYAN_BRIGHT;
                    default -> 0xFFFFFFFF;
                };
                case 4 -> ri == 0 ? GOLD_BRIGHT : (ri == 1 ? GOLD : 0xFFFFFFFF);
                case 3 -> ri == 0 ? MachineTheme.CYAN_BRIGHT : (ri == 1 ? 0xFF7CF7FF : 0xFFFFFFFF);
                default -> ri == 0 ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT;
            };
            int alpha = Math.max(0, Math.min(240, (int) Math.round(225.0D * ringFade)));
            if (alpha > 8) {
                drawOutline(graphics, x - pad, y - pad, w + pad * 2, h + pad * 2,
                        (alpha << 24) | (ringCol & 0x00FFFFFF));
            }
        }

        // 4. Radial cross-star sparkles (✦): 8 / 12 / 16 / 24 stars
        int stars = style.starCount();
        for (int i = 0; i < stars; i++) {
            double angle = (Math.PI * 2.0D * i) / stars + 0.35D;
            boolean outerRing = (i % 2 == 0);
            double maxDist = (outerRing ? 24.0D : 16.0D) + (tier - 2) * 6.0D;
            double dist = 10.0D + maxDist * eased;
            int sx = cx + (int) Math.round(Math.cos(angle) * dist);
            int sy = cy + (int) Math.round(Math.sin(angle) * dist * 0.75D);
            int sAlpha = Math.max(0, Math.min(255, (int) Math.round(255.0D * fade)));
            int baseCol = switch (tier) {
                case 5 -> (i % 3 == 0) ? GOLD_BRIGHT
                        : (i % 3 == 1 ? MachineTheme.PURPLE_BRIGHT : MachineTheme.CYAN_BRIGHT);
                case 4 -> (i % 2 == 0) ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT;
                case 3 -> (i % 2 == 0) ? MachineTheme.CYAN_BRIGHT : 0xFF7CF7FF;
                default -> (i % 2 == 0) ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT;
            };
            int sc = (sAlpha << 24) | (baseCol & 0x00FFFFFF);
            int core = (sAlpha << 24) | 0xFFFFFF;
            int arm = (tier >= 4 && outerRing) ? 3 : 2;
            graphics.fill(sx - arm, sy, sx + arm + 1, sy + 1, sc);
            graphics.fill(sx, sy - arm, sx + 1, sy + arm + 1, sc);
            graphics.fill(sx, sy, sx + 1, sy + 1, core);
        }

        // 5. Ambient spark particles / Tier 5 glittering particle shower
        int sparks = style.sparkParticles();
        for (int p = 0; p < sparks; p++) {
            double angle = (Math.PI * 2.0D * p) / sparks + 0.17D * p;
            double speed = 18.0D + ((p * 13) % 24) + (tier >= 5 ? 10.0D : 0.0D);
            double gravity = tier >= 5 ? (14.0D * t * t) : 0.0D;
            int px = cx + (int) Math.round(Math.cos(angle) * speed * eased);
            int py = cy + (int) Math.round(Math.sin(angle) * speed * 0.72D * eased + gravity);
            int pAlpha = Math.max(0, Math.min(240, (int) Math.round(235.0D * fade)));
            int pCol = tier >= 5
                    ? (p % 3 == 0 ? GOLD_BRIGHT : (p % 3 == 1 ? MachineTheme.PURPLE_BRIGHT : MachineTheme.CYAN_BRIGHT))
                    : (tier == 4 ? GOLD_BRIGHT : MachineTheme.CYAN_BRIGHT);
            if (pAlpha > 10) {
                graphics.fill(px, py, px + 2, py + 2, (pAlpha << 24) | (pCol & 0x00FFFFFF));
            }
        }
    }

    /** Phase 3 (FOG_CLEAR): fog silhouette dissolving into real icon/card (Tier 4–5 cyan -> gold flash). */
    private void renderFogClearOverlay(GuiGraphics graphics, int x, int y, int w, int h, double t, int tier) {
        double fogAlpha = Math.max(0.0D, 1.0D - easeOutCubic(t));
        int fa = Math.max(0, Math.min(255, (int) Math.round(240.0D * fogAlpha)));
        if (fa > 10) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, (fa << 24) | 0x070B11);
            if (t < 0.50D) {
                int qAlpha = Math.max(0, Math.min(255, (int) Math.round(255.0D * (1.0D - t / 0.50D))));
                graphics.drawCenteredString(this.font, "?", x + w / 2, y + h / 2 - 4,
                        (qAlpha << 24) | (MachineTheme.CYAN_BRIGHT & 0x00FFFFFF));
            }
        }
        double flash = Math.sin(Math.PI * Math.max(0.0D, Math.min(1.0D, t)));
        if (flash > 0.05D) {
            int flashCol = tier >= 4
                    ? MachineTheme.blend(MachineTheme.CYAN_BRIGHT, GOLD_BRIGHT, t)
                    : MachineTheme.CYAN_BRIGHT;
            MachineTheme.glowRect(graphics, x, y, w, h, flashCol, (tier >= 4 ? 1.25D : 0.95D) * flash);
            if (tier >= 4) {
                int sa = Math.max(0, Math.min(240, (int) Math.round(220.0D * flash)));
                int sc = (sa << 24) | (GOLD_BRIGHT & 0x00FFFFFF);
                int cx = x + w / 2;
                int cy = y + h / 2;
                int d = (int) Math.round(12.0D * t);
                graphics.fill(cx - d, cy, cx - d + 2, cy + 2, sc);
                graphics.fill(cx + d, cy, cx + d + 2, cy + 2, sc);
                graphics.fill(cx, cy - d, cx + 2, cy - d + 2, sc);
                graphics.fill(cx, cy + d, cx + 2, cy + d + 2, sc);
            }
        }
    }

    /** Phase 86: shrouded silhouette card for MYSTERY (2+ steps ahead) nodes. */
    private void renderMysteryNodeCard(GuiGraphics graphics, int x, int y, int w, int h,
                                       boolean selected, boolean hovered) {
        int bgTop = hovered ? 0xFF0E141C : 0xFF090D13;
        int bgBot = 0xFF05080C;
        int border = selected ? 0xFF6B859C : (hovered ? 0xFF394B5C : 0xFF1D2732);

        if (selected) {
            MachineTheme.glowRect(graphics, x, y, w, h, 0xFF3A5166, 0.55D);
        }
        MachineTheme.gradient(graphics, x, y, w, h, bgTop, bgBot);
        drawOutline(graphics, x, y, w, h, border);

        // Dark machine silhouette glyph on left + bold "?" and "???" text
        drawMysteryIcon(graphics, x + 3, y + 5);
        graphics.drawString(this.font, "???", x + 23, y + 9,
                hovered || selected ? 0xFF8FA3B4 : 0xFF536475, false);

        if (selected) {
            MachineTheme.bracket(graphics, x - 1, y - 1, 1, 1, 4, 0xFF8FA3B4);
            MachineTheme.bracket(graphics, x + w, y - 1, -1, 1, 4, 0xFF8FA3B4);
            MachineTheme.bracket(graphics, x - 1, y + h, 1, -1, 4, 0xFF8FA3B4);
            MachineTheme.bracket(graphics, x + w, y + h, -1, -1, 4, 0xFF8FA3B4);
        }
    }

    private void renderNodeCard(GuiGraphics graphics, MachineSkillTree.Node node,
                                int x, int y, int w, int h, NodeVisualState state,
                                boolean selected, boolean hovered,
                                boolean unlockableFrontier, boolean searchMatch,
                                long now, int padlockJitterX) {
        int lvl = ClientMachineProgress.level(node.machineId());
        boolean limitBroken = ClientMachineProgress.isMachineLimitBroken(node.machineId());
        int bAccent = node.branch().accentColor();
        int bBright = node.branch().brightColor();
        long xp = ClientMachineProgress.xp(node.machineId());
        long into = MachineLevelMath.xpIntoLevel(xp, limitBroken);
        long span = MachineLevelMath.xpSpanForNextLevel(lvl, limitBroken);
        double xpRatio = span <= 0L ? 1.0D : Math.min(1.0D, (double) into / span);

        double frontierPulse = 0.5D + 0.5D * Math.sin(now * 0.0055D);
        int bgTop;
        int bgBot;
        int border;
        int lvlCol;
        switch (state) {
            case LOCKED -> {
                bgTop = 0xFF0C1117;
                bgBot = 0xFF070A0E;
                border = hovered ? 0xFF475869 : 0xFF222D38;
                lvlCol = 0xFF6A7B8A;
            }
            case UNLOCKED -> {
                bgTop = blendDark(bAccent, unlockableFrontier ? 0.28D : 0.22D);
                bgBot = blendDark(bAccent, 0.09D);
                border = unlockableFrontier
                        ? MachineTheme.blend(0xFF00B8D4, 0xFF48FFFF, frontierPulse)
                        : (hovered ? bBright : bAccent);
                lvlCol = unlockableFrontier ? 0xFF7AF9FF : bBright;
            }
            case MASTERED -> {
                bgTop = 0xFF211C0E;
                bgBot = 0xFF110E07;
                border = hovered ? GOLD_BRIGHT : GOLD;
                lvlCol = GOLD_BRIGHT;
            }
            case LIMIT_BROKEN -> {
                double pulse = 0.5D + 0.5D * Math.sin(now * 0.008D);
                bgTop = 0xFF221330;
                bgBot = 0xFF100A19;
                border = MachineTheme.blend(MachineTheme.PURPLE_BRIGHT, GOLD_BRIGHT, pulse);
                lvlCol = GOLD_BRIGHT;
            }
            default -> {
                bgTop = MachineTheme.PANEL_TOP;
                bgBot = MachineTheme.PANEL_BOTTOM;
                border = MachineTheme.LINE_SOFT;
                lvlCol = MachineTheme.TEXT;
            }
        }

        if (selected) {
            int selGlow = state == NodeVisualState.LOCKED ? node.branch().dimColor()
                    : (state == NodeVisualState.MASTERED || state == NodeVisualState.LIMIT_BROKEN ? GOLD : bAccent);
            MachineTheme.glowRect(graphics, x, y, w, h, selGlow, 0.85D);
        } else if (unlockableFrontier) {
            // Phase 108: gentle neon-cyan breathing pulse halo on immediately actionable unlockable nodes
            MachineTheme.glowRect(graphics, x, y, w, h, 0xFF38F8FF, 0.30D + 0.42D * frontierPulse);
        } else if (searchMatch) {
            MachineTheme.glowRect(graphics, x, y, w, h, 0xFF38F8FF, 0.50D);
        } else if (state == NodeVisualState.LIMIT_BROKEN) {
            MachineTheme.glowRect(graphics, x, y, w, h, border, 0.45D);
        } else if (state == NodeVisualState.MASTERED) {
            MachineTheme.glowRect(graphics, x, y, w, h, GOLD, 0.28D);
        }

        MachineTheme.gradient(graphics, x, y, w, h, bgTop, bgBot);
        drawOutline(graphics, x, y, w, h, selected ? 0xFFFFFFFF : (searchMatch ? 0xFF5CF9FF : border));
        if (unlockableFrontier && !selected) {
            // Subtle inner corner shimmer dot showing ready-to-start status
            int dotA = (int) Math.round(140.0D + 115.0D * frontierPulse);
            graphics.fill(x + w - 5, y + 3, x + w - 2, y + 6, (dotA << 24) | 0x48FFFF);
        }

        // Machine block icon on the left (16x16 centered vertically inside NODE_H=26)
        graphics.renderItem(blockIcon(node.machineId()), x + 3, y + 5);
        if (state == NodeVisualState.LOCKED) {
            graphics.fill(x + 3, y + 5, x + 19, y + 21, 0x7A06090E);
            drawPadlock(graphics, x + w - 9 + padlockJitterX, y + 3,
                    padlockJitterX != 0 ? GOLD_BRIGHT : 0xFF8A99A8);
            String reqStr = "L" + node.requiredLevel();
            graphics.drawString(this.font, reqStr, x + 22, y + 14, 0xFF8A99A8, false);
        } else {
            String lvlStr = "Lv." + lvl;
            graphics.drawString(this.font, lvlStr, x + 21, y + 5, lvlCol, false);

            int barX = x + 21;
            int barY = y + 17;
            int barW = w - 25;
            graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF05080C);
            int filled = (int) Math.round(barW * xpRatio);
            if (filled > 0) {
                int barCol = state == NodeVisualState.LIMIT_BROKEN ? MachineTheme.PURPLE_BRIGHT
                        : (state == NodeVisualState.MASTERED ? GOLD : bAccent);
                graphics.fill(barX, barY, barX + filled, barY + 3, barCol);
            }
        }

        if (selected) {
            MachineTheme.bracket(graphics, x - 1, y - 1, 1, 1, 4, 0xFFFFFFFF);
            MachineTheme.bracket(graphics, x + w, y - 1, -1, 1, 4, 0xFFFFFFFF);
            MachineTheme.bracket(graphics, x - 1, y + h, 1, -1, 4, 0xFFFFFFFF);
            MachineTheme.bracket(graphics, x + w, y + h, -1, -1, 4, 0xFFFFFFFF);
        }
    }

    private void renderDetailPanel(GuiGraphics graphics, double px, double py,
                                   Set<String> revealed, boolean limitBroken) {
        parentHits.clear();
        ingredientHits.clear();
        Rect dp = SkillTreeLayout.DETAIL_PANEL;
        MachineTheme.bay(graphics, 0, 0, dp.x(), dp.y(), dp.width(), dp.height());

        MachineSkillTree.Node node = MachineSkillTree.byId(selectedMachineId);
        if (node == null) {
            return;
        }

        NodeVisibility vis = SkillTreeLayout.visibilityOf(node, revealed);
        int x = dp.x() + 6;
        int y = dp.y() + 5;
        int innerW = dp.width() - 12;
        int stage = SkillTreeFilterLogic.stageForNode(node);

        // Phase 86: MYSTERY detail panel shrouds machine name, stats & guide
        if (vis == NodeVisibility.MYSTERY) {
            drawMysteryIcon(graphics, x, y);
            graphics.drawString(this.font,
                    Component.translatable("gui.industrialcore.skill_tree.mystery_name"),
                    x + 20, y + 1, MachineTheme.MUTED, false);
            drawStageAndTierRow(graphics, x + 20, y + 10, stage, node.tier(), 0xFF536475);

            y += 21;
            graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
            y += 4;

            graphics.drawString(this.font,
                    Component.translatable("gui.industrialcore.skill_tree.state_mystery"),
                    x, y, 0xFF7B8E9F, false);
            y += 12;

            graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
            y += 4;
            graphics.drawString(this.font,
                    Component.translatable("gui.industrialcore.skill_tree.prereq_header"),
                    x, y, MachineTheme.MUTED, false);
            y += 9;
            y = renderPrerequisiteRows(graphics, node, revealed, x, y, innerW, px, py, dp.bottom() - 28);

            if (y + 16 < dp.bottom() - 4) {
                y += 3;
                graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
                y += 4;
                Component mysteryGuide = Component.translatable("gui.industrialcore.skill_tree.mystery_guide");
                List<FormattedCharSequence> lines = this.font.split(mysteryGuide, innerW);
                int maxY = dp.bottom() - 10;
                for (FormattedCharSequence line : lines) {
                    if (y > maxY) {
                        break;
                    }
                    graphics.drawString(this.font, line, x, y, 0xFF7B8E9F, false);
                    y += 9;
                }
            }
            return;
        }

        // 1. Header: icon + 1-line fitted machine name + Stage badge (Stage 1..5) & Tier tag
        graphics.renderItem(blockIcon(node.machineId()), x, y);
        Component name = Component.translatable(node.translationKey());
        graphics.drawString(this.font, MachineTheme.fit(this.font, name, innerW - 20),
                x + 20, y + 1, MachineTheme.TEXT, false);
        drawStageAndTierRow(graphics, x + 20, y + 10, stage, node.tier(), node.branch().accentColor());

        y += 20;
        graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
        y += 3;

        // 2. Level, State Badge & compact XP bar
        int lvl = ClientMachineProgress.level(node.machineId());
        int maxLvl = ClientMachineProgress.maxLevel(node.machineId());
        boolean nodeLimitBroken = ClientMachineProgress.isMachineLimitBroken(node.machineId());
        long xp = ClientMachineProgress.xp(node.machineId());
        long into = MachineLevelMath.xpIntoLevel(xp, nodeLimitBroken);
        long span = MachineLevelMath.xpSpanForNextLevel(lvl, nodeLimitBroken);
        double ratio = span <= 0L ? 1.0D : Math.min(1.0D, (double) into / span);

        Component lvlLine = Component.translatable("gui.industrialcore.skill_tree.detail_level", lvl, maxLvl);
        int lvlCol = nodeLimitBroken && lvl > MachineLevelMath.NORMAL_MAX_LEVEL
                ? GOLD_BRIGHT : node.branch().brightColor();
        graphics.drawString(this.font, lvlLine, x, y, lvlCol, false);

        boolean unlocked = (vis == NodeVisibility.REVEALED);
        boolean unlockableNow = unlocked && SkillTreeFilterLogic.isUnlockableFrontier(
                node, ClientMachineProgress::level, ClientMachineProgress::xp);
        Component stateTag = Component.translatable(unlockableNow
                ? "gui.industrialcore.skill_tree.state_unlockable"
                : (unlocked ? "gui.industrialcore.skill_tree.state_unlocked"
                        : "gui.industrialcore.skill_tree.state_locked"));
        int tagW = this.font.width(stateTag);
        int tagCol = unlockableNow ? 0xFF38F8FF
                : (unlocked ? MachineTheme.STATUS_OK : MachineTheme.STATUS_BAD);
        graphics.drawString(this.font, stateTag, x + innerW - tagW, y, tagCol, false);
        y += 9;

        // Compact 4px XP bar
        graphics.fill(x, y, x + innerW, y + 4, 0xFF05080C);
        drawOutline(graphics, x, y, innerW, 4, MachineTheme.LINE_SOFT);
        int filled = (int) Math.round((innerW - 2) * ratio);
        if (filled > 0) {
            int barCol = nodeLimitBroken && lvl > MachineLevelMath.NORMAL_MAX_LEVEL
                    ? MachineTheme.PURPLE_BRIGHT : node.branch().accentColor();
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 3, barCol);
        }
        y += 6;

        // Compact Upgrade cap / mastery line
        if (lvl > MachineLevelMath.NORMAL_MAX_LEVEL) {
            Component mastery = Component.translatable("gui.industrialcore.skill_tree.detail_mastery",
                    String.format(Locale.ROOT, "%.2f", ClientMachineProgress.masterySpeed(node.machineId())),
                    ClientMachineProgress.masteryEnergySavingPercent(node.machineId()));
            graphics.drawString(this.font, MachineTheme.fit(this.font, mastery, innerW), x, y, GOLD_BRIGHT, false);
        } else if (node.hasMotorSlot()) {
            int cap = ClientMachineProgress.upgradeLimit(node.machineId());
            String key = node.hasBatterySlot()
                    ? "gui.industrialcore.skill_tree.detail_cap_both"
                    : "gui.industrialcore.skill_tree.detail_cap_motor";
            Component capLine = Component.translatable(key, cap, MachineLevelMath.MAX_UPGRADE_CAP);
            graphics.drawString(this.font, MachineTheme.fit(this.font, capLine, innerW),
                    x, y, MachineTheme.CYAN, false);
        }
        y += 10;

        // Reserve bottom 34px strictly for Key Crafting Ingredients so sections NEVER collide
        int ingredientsTop = dp.bottom() - 34;

        // 3. Role Summary (1-line header + max 2 compact lines)
        graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
        y += 3;
        graphics.drawString(this.font,
                Component.translatable("gui.industrialcore.skill_tree.role_header"),
                x, y, GOLD, false);
        y += 9;
        Component guideText = Component.translatable(node.guideKey());
        List<FormattedCharSequence> roleLines = this.font.split(guideText, innerW);
        int maxRoleLines = node.parents().size() >= 3 ? 1 : 2;
        int showCount = Math.min(maxRoleLines, roleLines.size());
        for (int i = 0; i < showCount; i++) {
            if (i == showCount - 1 && roleLines.size() > maxRoleLines) {
                StringBuilder sb = new StringBuilder();
                roleLines.get(i).accept((idx, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                Component ellipsized = MachineTheme.fit(this.font, Component.literal(sb + "…"), innerW);
                graphics.drawString(this.font, ellipsized, x, y, MachineTheme.TEXT, false);
            } else {
                graphics.drawString(this.font, roleLines.get(i), x, y, MachineTheme.TEXT, false);
            }
            y += 9;
        }
        y += 2;

        // 4. Prerequisites / Unlock Condition (bounded strictly above ingredientsTop)
        if (y + 18 <= ingredientsTop) {
            graphics.fill(x, y, x + innerW, y + 1, MachineTheme.LINE_SOFT);
            y += 3;
            graphics.drawString(this.font,
                    Component.translatable("gui.industrialcore.skill_tree.prereq_header"),
                    x, y, MachineTheme.MUTED, false);
            y += 9;

            if (node.parents().isEmpty()) {
                graphics.drawString(this.font,
                        MachineTheme.fit(this.font,
                                Component.translatable("gui.industrialcore.skill_tree.prereq_none"), innerW),
                        x, y, MachineTheme.STATUS_OK, false);
            } else {
                renderPrerequisiteRows(graphics, node, revealed, x, y, innerW, px, py, ingredientsTop - 2);
            }
        }

        // 5. Key Crafting Ingredients Icon Row (pinned cleanly to bottom of detail panel)
        renderCraftingIngredientsRow(graphics, node.machineId(), x, ingredientsTop, innerW, px, py);
    }

    private void drawStageAndTierRow(GuiGraphics g, int x, int y, int stage, int tier, int accent) {
        Component stageComp = Component.translatable("gui.industrialcore.skill_tree.stage_badge", stage);
        int sw = this.font.width(stageComp) + 6;
        int stageCol = switch (stage) {
            case 1 -> 0xFF3EE898;
            case 2 -> 0xFF38D4FF;
            case 3 -> 0xFF66B2FF;
            case 4 -> 0xFFFFBE44;
            default -> 0xFFD08BFF;
        };
        g.fill(x, y - 1, x + sw, y + 8, blendDark(stageCol, 0.22D));
        drawOutline(g, x, y - 1, sw, 9, stageCol);
        g.drawString(this.font, stageComp, x + 3, y, stageCol, false);

        Component tierComp = Component.translatable("gui.industrialcore.skill_tree.tier_badge", tier);
        g.drawString(this.font, tierComp, x + sw + 4, y, MachineTheme.MUTED, false);
    }

    private void renderCraftingIngredientsRow(GuiGraphics graphics, String machineId,
                                              int x, int topY, int innerW, double px, double py) {
        graphics.fill(x, topY, x + innerW, topY + 1, MachineTheme.LINE_SOFT);
        graphics.drawString(this.font,
                Component.translatable("gui.industrialcore.skill_tree.ingredients_header"),
                x, topY + 3, MachineTheme.CYAN_BRIGHT, false);

        List<IngredientPreview> items = resolveCraftingIngredients(machineId);
        int slotY = topY + 13;
        if (items.isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.industrialcore.skill_tree.ingredients_none"),
                    x, slotY + 4, MachineTheme.MUTED, false);
            return;
        }
        int maxSlots = Math.min(6, items.size());
        for (int i = 0; i < maxSlots; i++) {
            IngredientPreview ip = items.get(i);
            int sx = x + i * 22;
            Rect slotRect = new Rect(sx, slotY, 18, 18);
            ingredientHits.add(new IngredientHit(slotRect, ip.stack(), ip.count()));
            boolean hov = slotRect.contains(px, py);
            graphics.fill(sx, slotY, sx + 18, slotY + 18, hov ? 0xFF152536 : 0xFF070C13);
            drawOutline(graphics, sx, slotY, 18, 18, hov ? MachineTheme.CYAN_BRIGHT : MachineTheme.LINE_SOFT);
            graphics.renderItem(ip.stack(), sx + 1, slotY + 1);
            if (ip.count() > 1) {
                String cnt = String.valueOf(ip.count());
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.drawString(this.font, cnt, sx + 18 - this.font.width(cnt), slotY + 10, 0xFFFFFFFF, true);
                graphics.pose().popPose();
            }
        }
    }

    private List<IngredientPreview> resolveCraftingIngredients(String machineId) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        var rm = this.minecraft.level.getRecipeManager();
        if (rm != cachedRecipeManagerRef) {
            cachedRecipeManagerRef = rm;
            recipeIngredientCache.clear();
        }
        List<IngredientPreview> cached = recipeIngredientCache.get(machineId);
        if (cached != null) {
            return cached;
        }
        Item targetItem = blockIcon(machineId).getItem();
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack out = holder.value().getResultItem(this.minecraft.level.registryAccess());
            if (!out.isEmpty() && out.is(targetItem)) {
                for (Ingredient ing : holder.value().getIngredients()) {
                    if (ing.isEmpty()) {
                        continue;
                    }
                    ItemStack[] choices = ing.getItems();
                    if (choices.length > 0 && !choices[0].isEmpty()) {
                        counts.merge(choices[0].getItem(), 1, Integer::sum);
                    }
                }
                break;
            }
        }
        List<IngredientPreview> built = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            built.add(new IngredientPreview(new ItemStack(e.getKey()), e.getValue()));
            if (built.size() >= 6) {
                break;
            }
        }
        List<IngredientPreview> frozen = List.copyOf(built);
        recipeIngredientCache.put(machineId, frozen);
        return frozen;
    }

    private int renderPrerequisiteRows(GuiGraphics graphics, MachineSkillTree.Node node,
                                       Set<String> revealed, int x, int y, int innerW,
                                       double px, double py, int maxBottomY) {
        int pitch = node.parents().size() >= 4 ? 8 : 9;
        for (String parentId : node.parents()) {
            if (y + pitch > maxBottomY) {
                break;
            }
            MachineSkillTree.Node pNode = MachineSkillTree.byId(parentId);
            NodeVisibility pVis = SkillTreeLayout.visibilityOf(pNode, revealed);
            int cur = ClientMachineProgress.level(parentId);
            int req = node.requiredLevel();
            boolean ok = cur >= req;
            String mark = ok ? "○ " : "× ";

            Component pName;
            if (pVis == NodeVisibility.MYSTERY) {
                pName = Component.translatable("gui.industrialcore.skill_tree.mystery_name");
            } else if (pNode != null) {
                pName = Component.translatable(pNode.translationKey());
            } else {
                pName = Component.literal(parentId);
            }

            String suffix = pVis == NodeVisibility.MYSTERY
                    ? " (?/" + req + ")"
                    : " (" + cur + "/" + req + ")";
            int suffixW = this.font.width(suffix);
            Component nameFit = MachineTheme.fit(this.font,
                    Component.literal(mark).append(pName), Math.max(20, innerW - suffixW - 2));

            Rect hitRect = new Rect(x - 1, y - 1, innerW + 2, pitch);
            parentHits.add(new ParentHit(hitRect, parentId));
            boolean rowHover = hitRect.contains(px, py);
            if (rowHover) {
                graphics.fill(hitRect.x(), hitRect.y(), hitRect.right(), hitRect.bottom(), 0xFF172736);
            }

            int rowCol = pVis == NodeVisibility.MYSTERY
                    ? 0xFF6A7B8A
                    : (ok ? MachineTheme.STATUS_OK : MachineTheme.STATUS_BAD);
            graphics.drawString(this.font, nameFit, x, y, rowHover ? 0xFFFFFFFF : rowCol, false);
            graphics.drawString(this.font, suffix, x + innerW - suffixW, y,
                    pVis == NodeVisibility.MYSTERY ? 0xFF6A7B8A : (ok ? MachineTheme.STATUS_OK : MachineTheme.STATUS_WARN),
                    false);
            y += pitch;
        }
        return y;
    }

    // =========================================================================
    // Tab 2: 32,768x Ore Processing Line Guide (Fog-of-War Synced)
    // =========================================================================

    private void renderOreLineTab(GuiGraphics graphics, double px, double py) {
        Rect vp = SkillTreeLayout.GUIDE_VIEWPORT;
        MachineTheme.bay(graphics, 0, 0, vp.x(), vp.y(), vp.width(), vp.height());

        Set<String> revealed = effectiveRevealed();

        // Top explanation banner
        int bx = vp.x() + 4;
        int by = vp.y() + 4;
        int bw = vp.width() - 8;
        MachineTheme.gradient(graphics, bx, by, bw, ORE_BANNER_H, 0xFF142433, 0xFF0A131C);
        drawOutline(graphics, bx, by, bw, ORE_BANNER_H, MachineTheme.CYAN_DIM);
        graphics.drawString(this.font,
                MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.oreline.banner_1"), bw - 10),
                bx + 5, by + 4, MachineTheme.CYAN_BRIGHT, false);
        graphics.drawString(this.font,
                MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.oreline.banner_2"), bw - 10),
                bx + 5, by + 15, GOLD_BRIGHT, false);

        // Scrollable stage 1..15 list
        Rect listArea = new Rect(vp.x() + 4, by + ORE_BANNER_H + 3,
                vp.width() - 14, vp.height() - ORE_BANNER_H - 11);
        List<OreLineStage> stages = SkillTreeLayout.oreLineStages();
        int totalH = stages.size() * ORE_ROW_H;
        int maxScroll = Math.max(0, totalH - listArea.height());
        oreScroll = Math.max(0.0D, Math.min(maxScroll, oreScroll));

        enablePanelScissor(graphics, listArea);
        int scrollPx = (int) Math.round(oreScroll);
        for (int i = 0; i < stages.size(); i++) {
            OreLineStage st = stages.get(i);
            int ry = listArea.y() + i * ORE_ROW_H - scrollPx;
            if (ry + ORE_ROW_H < listArea.y() || ry > listArea.bottom()) {
                continue;
            }
            Rect rowRect = new Rect(listArea.x(), ry, listArea.width(), ORE_ROW_H - 2);
            boolean hover = listArea.contains(px, py) && rowRect.contains(px, py);
            boolean mysteryMachine = SkillTreeLayout.visibilityOf(st.machineId(), revealed)
                    == NodeVisibility.MYSTERY;

            int rowBg = hover ? 0xFF172938 : (st.isSynthesizerStage() ? 0xFF121622 : 0xFF0C141C);
            int rowEdge = hover ? MachineTheme.CYAN
                    : (st.isSynthesizerStage() ? MachineTheme.PURPLE_DIM : MachineTheme.LINE_SOFT);
            graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), rowBg);
            drawOutline(graphics, rowRect.x(), rowRect.y(), rowRect.width(), rowRect.height(), rowEdge);

            // Stage badge (S1..S15) — always visible
            int badgeCol = st.isSynthesizerStage() ? MachineTheme.PURPLE : MachineTheme.CYAN_DIM;
            graphics.fill(rowRect.x() + 2, rowRect.y() + 2, rowRect.x() + 26, rowRect.bottom() - 2, badgeCol);
            graphics.drawCenteredString(this.font, "S" + st.stage(),
                    rowRect.x() + 14, rowRect.y() + 6, 0xFFFFFFFF);

            // Machine icon + name (shrouded as silhouette + "???" when MYSTERY)
            if (mysteryMachine) {
                drawMysteryIcon(graphics, rowRect.x() + 30, rowRect.y() + 2);
                graphics.drawString(this.font,
                        Component.translatable("gui.industrialcore.skill_tree.mystery_name"),
                        rowRect.x() + 49, rowRect.y() + 6, 0xFF6A7B8A, false);
            } else {
                graphics.renderItem(blockIcon(st.machineId()), rowRect.x() + 30, rowRect.y() + 2);
                Component mName = Component.translatable("block.industrialcore." + st.machineId());
                graphics.drawString(this.font, MachineTheme.fit(this.font, mName, 88),
                        rowRect.x() + 49, rowRect.y() + 6, MachineTheme.TEXT, false);
            }

            // Arrow
            graphics.drawString(this.font, "→", rowRect.x() + 140, rowRect.y() + 6,
                    st.isSynthesizerStage() ? GOLD : MachineTheme.CYAN_BRIGHT, false);

            // Output item icon + name
            ItemStack outStack = st.isVanillaOutput()
                    ? vanillaItemIcon(st.outputItemId())
                    : modItemIcon(st.outputItemId());
            graphics.renderItem(outStack, rowRect.x() + 151, rowRect.y() + 2);
            Component outName = st.isVanillaOutput()
                    ? Component.translatable("item.minecraft." + st.outputItemId())
                    : Component.translatable("item.industrialcore." + st.outputItemId());
            graphics.drawString(this.font, MachineTheme.fit(this.font, outName, 114),
                    rowRect.x() + 170, rowRect.y() + 6,
                    mysteryMachine ? 0xFF6F8294 : MachineTheme.MUTED, false);

            // Cumulative yield right-aligned — always visible so players feel the 32,768x romance!
            Component multText = Component.translatable("gui.industrialcore.oreline.mult",
                    formatNum(st.cumulativeMultiplier()));
            int mw = this.font.width(multText);
            int multCol = st.stage() == 15 ? GOLD_BRIGHT
                    : (st.stage() == 6 ? MachineTheme.CYAN_BRIGHT : (st.isSynthesizerStage() ? GOLD : MachineTheme.STATUS_OK));
            graphics.drawString(this.font, multText, rowRect.right() - mw - 5, rowRect.y() + 6, multCol, false);
        }
        graphics.disableScissor();

        renderVerticalScrollbar(graphics, listArea.right() + 2, listArea.y(),
                SkillTreeLayout.SCROLLBAR_WIDTH, listArea.height(), oreScroll, maxScroll, totalH);
    }

    // =========================================================================
    // Tab 3: Power Generation, Recovery/Storage & Logistics Guide
    // =========================================================================

    private void renderInfraTab(GuiGraphics graphics, double px, double py) {
        Rect vp = SkillTreeLayout.GUIDE_VIEWPORT;
        MachineTheme.bay(graphics, 0, 0, vp.x(), vp.y(), vp.width(), vp.height());

        // Phase 103.5: Top "Advanced: Multiblock Reactor Guide" banner button right above Generators
        Rect banner = SkillTreeLayout.INFRA_MULTIBLOCK_BANNER;
        boolean bannerHover = !showingReactorGuide && banner.contains(px, py);
        graphics.fill(banner.x(), banner.y(), banner.right(), banner.bottom(),
                bannerHover ? 0xFF172D3F : 0xFF0E1D2B);
        graphics.fill(banner.x(), banner.y(), banner.x() + 3, banner.bottom(), GOLD);
        drawOutline(graphics, banner.x(), banner.y(), banner.width(), banner.height(),
                bannerHover ? GOLD_BRIGHT : MachineTheme.CYAN_DIM);
        graphics.renderItem(blockIcon("reactor_core_cell"), banner.x() + 5, banner.y() + 1);
        Component bannerTitle = Component.translatable("gui.industrialcore.skill_tree.reactor_adv_btn");
        Component bannerOpen = Component.translatable("gui.industrialcore.skill_tree.reactor_adv_open");
        int openW = this.font.width(bannerOpen);
        graphics.drawString(this.font, MachineTheme.fit(this.font, bannerTitle, banner.width() - openW - 36),
                banner.x() + 24, banner.y() + 5, GOLD_BRIGHT, false);
        graphics.drawString(this.font, bannerOpen, banner.right() - openW - 6, banner.y() + 5,
                bannerHover ? 0xFFFFFFFF : MachineTheme.CYAN_BRIGHT, false);

        Rect listArea = new Rect(vp.x() + 4, banner.bottom() + 4, vp.width() - 14, vp.bottom() - banner.bottom() - 8);
        List<InfraEntry> entries = SkillTreeLayout.infraEntries();

        int totalH = 0;
        String prevSec = null;
        for (InfraEntry e : entries) {
            if (!e.sectionKey().equals(prevSec)) {
                totalH += INFRA_HEADER_H;
                prevSec = e.sectionKey();
            }
            totalH += INFRA_ROW_H;
        }
        int maxScroll = Math.max(0, totalH - listArea.height());
        infraScroll = Math.max(0.0D, Math.min(maxScroll, infraScroll));

        enablePanelScissor(graphics, listArea);
        int y = listArea.y() - (int) Math.round(infraScroll);
        prevSec = null;
        for (InfraEntry e : entries) {
            if (!e.sectionKey().equals(prevSec)) {
                prevSec = e.sectionKey();
                if (y + INFRA_HEADER_H >= listArea.y() && y <= listArea.bottom()) {
                    graphics.fill(listArea.x(), y + 1, listArea.right(), y + INFRA_HEADER_H - 1, 0xFF132230);
                    graphics.fill(listArea.x(), y + 1, listArea.x() + 3, y + INFRA_HEADER_H - 1, MachineTheme.CYAN);
                    Component secTitle = Component.translatable("gui.industrialcore.infra.section." + prevSec);
                    graphics.drawString(this.font, MachineTheme.fit(this.font, secTitle, listArea.width() - 12),
                            listArea.x() + 7, y + 4, MachineTheme.CYAN_BRIGHT, false);
                }
                y += INFRA_HEADER_H;
            }

            if (y + INFRA_ROW_H >= listArea.y() && y <= listArea.bottom()) {
                Rect r = new Rect(listArea.x(), y, listArea.width(), INFRA_ROW_H - 2);
                boolean hover = listArea.contains(px, py) && r.contains(px, py);
                graphics.fill(r.x(), r.y(), r.right(), r.bottom(), hover ? 0xFF152432 : 0xFF0B121A);
                drawOutline(graphics, r.x(), r.y(), r.width(), r.height(),
                        hover ? MachineTheme.CYAN_DIM : MachineTheme.LINE_SOFT);

                graphics.renderItem(blockIcon(e.blockId()), r.x() + 4, r.y() + 3);
                Component blockName = Component.translatable("block.industrialcore." + e.blockId());
                graphics.drawString(this.font, MachineTheme.fit(this.font, blockName, 118),
                        r.x() + 24, r.y() + 3, MachineTheme.TEXT, false);

                Component spec = Component.translatable("gui.industrialcore.infra.spec." + e.specKey());
                graphics.drawString(this.font, MachineTheme.fit(this.font, spec, r.width() - 28),
                        r.x() + 24, r.y() + 12, MachineTheme.MUTED, false);

                Component rate = Component.translatable("gui.industrialcore.infra.rate." + e.specKey());
                int rw = this.font.width(rate);
                graphics.drawString(this.font, rate, r.right() - rw - 5, r.y() + 3, GOLD_BRIGHT, false);
            }
            y += INFRA_ROW_H;
        }
        graphics.disableScissor();

        renderVerticalScrollbar(graphics, listArea.right() + 2, listArea.y(),
                SkillTreeLayout.SCROLLBAR_WIDTH, listArea.height(), infraScroll, maxScroll, totalH);
    }

    /** Phase 103.5: Detailed modal guide for variable-size Multiblock Reactor specs & scaling. */
    private void renderReactorGuideModal(GuiGraphics g, double px, double py) {
        // Elevate above background GuiGraphics.renderItem() (+150 Z) so ZERO icons or tabs bleed through!
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 350.0F);

        // Dim panel backdrop
        g.fill(2, 2, SkillTreeLayout.WIDTH - 2, SkillTreeLayout.HEIGHT - 2, 0xE603070D);

        Rect m = SkillTreeLayout.REACTOR_GUIDE_MODAL;
        g.fill(m.x(), m.y(), m.right(), m.bottom(), 0xFF08111B);
        drawOutline(g, m.x(), m.y(), m.width(), m.height(), GOLD);
        drawOutline(g, m.x() + 1, m.y() + 1, m.width() - 2, m.height() - 2, MachineTheme.CYAN_DIM);

        // Modal header bar
        g.fill(m.x() + 2, m.y() + 2, m.right() - 2, m.y() + 22, 0xFF102030);
        g.fill(m.x() + 2, m.y() + 22, m.right() - 2, m.y() + 23, GOLD_DIM);
        g.renderItem(blockIcon("reactor_core_cell"), m.x() + 6, m.y() + 4);
        Component title = Component.translatable("gui.industrialcore.reactor_guide.title");
        g.drawString(this.font, MachineTheme.fit(this.font, title, m.width() - 48),
                m.x() + 25, m.y() + 8, GOLD_BRIGHT, false);

        Rect close = SkillTreeLayout.REACTOR_GUIDE_CLOSE;
        boolean closeHover = close.contains(px, py);
        g.fill(close.x(), close.y(), close.right(), close.bottom(), closeHover ? 0xFF5A1D24 : 0xFF1A2836);
        drawOutline(g, close.x(), close.y(), close.width(), close.height(), closeHover ? 0xFFFF6677 : MachineTheme.LINE_SOFT);
        g.drawCenteredString(this.font, "X", close.centerX(), close.y() + 2, closeHover ? 0xFFFFFFFF : MachineTheme.TEXT);

        int x = m.x() + 8;
        int maxW = m.width() - 16;
        int y = m.y() + 28;

        // Section 1: Supported Max Sizes
        y = drawModalSectionHeader(g, x, y, maxW, "gui.industrialcore.reactor_guide.sec_size");
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.size_1", 0xFFE8FAFF);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.size_2", 0xFFE8FAFF);
        y += 4;

        // Section 2: Recognition Rules
        y = drawModalSectionHeader(g, x, y, maxW, "gui.industrialcore.reactor_guide.sec_rules");
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.rule_1", 0xFFD0ECF8);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.rule_2", 0xFFD0ECF8);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.rule_3", 0xFFD0ECF8);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.rule_4", 0xFFD0ECF8);
        y += 4;

        // Section 3: Scaling & Port Usage
        y = drawModalSectionHeader(g, x, y, maxW, "gui.industrialcore.reactor_guide.sec_scale");
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.scale_1", GOLD_BRIGHT);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.scale_2", 0xFF7CE8FF);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.scale_3", 0xFF5CFFA8);
        y = drawModalBullet(g, x, y, maxW, "gui.industrialcore.reactor_guide.scale_4", 0xFFFFD98A);

        // Footer safety note
        Component footer = Component.translatable("gui.industrialcore.reactor_guide.footer");
        g.drawString(this.font, MachineTheme.fit(this.font, footer, maxW),
                x, m.bottom() - 13, MachineTheme.MUTED, false);

        g.pose().popPose();
    }

    private int drawModalSectionHeader(GuiGraphics g, int x, int y, int width, String key) {
        g.fill(x, y, x + width, y + 11, 0xFF132536);
        g.fill(x, y, x + 2, y + 11, MachineTheme.CYAN);
        Component comp = Component.translatable(key);
        g.drawString(this.font, MachineTheme.fit(this.font, comp, width - 8),
                x + 5, y + 2, MachineTheme.CYAN_BRIGHT, false);
        return y + 13;
    }

    private int drawModalBullet(GuiGraphics g, int x, int y, int width, String key, int color) {
        Component comp = Component.translatable(key);
        g.drawString(this.font, MachineTheme.fit(this.font, comp, width - 4),
                x + 2, y, color, false);
        return y + 10;
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY, double px, double py) {
        if (activeTab == SkillTreeLayout.TAB_TREE) {
            for (IngredientHit hit : ingredientHits) {
                if (hit.rect().contains(px, py)) {
                    Component itemName = hit.stack().getHoverName();
                    Component line = hit.count() > 1
                            ? itemName.copy().append(Component.literal(" ×" + hit.count()))
                            : itemName;
                    graphics.renderComponentTooltip(this.font, List.of(line), mouseX, mouseY);
                    return;
                }
            }
            String hoveredId = SkillTreeLayout.nodeAt(activeBranch, px, py, panX, panY);
            if (hoveredId != null) {
                MachineSkillTree.Node node = MachineSkillTree.byId(hoveredId);
                if (node != null) {
                    Set<String> revealed = effectiveRevealed();
                    NodeVisibility vis = SkillTreeLayout.visibilityOf(node, revealed);
                    List<Component> lines;
                    if (vis == NodeVisibility.MYSTERY) {
                        lines = List.of(
                                Component.translatable("gui.industrialcore.skill_tree.mystery_name"),
                                Component.translatable("gui.industrialcore.skill_tree.mystery_guide"));
                    } else {
                        int lvl = ClientMachineProgress.level(hoveredId);
                        int maxLvl = ClientMachineProgress.maxLevel(hoveredId);
                        boolean unlocked = (vis == NodeVisibility.REVEALED);
                        lines = List.of(
                                Component.translatable(node.translationKey()),
                                Component.translatable("gui.industrialcore.skill_tree.hover_line",
                                        lvl, maxLvl,
                                        Component.translatable(unlocked
                                                ? "gui.industrialcore.skill_tree.state_unlocked"
                                                : "gui.industrialcore.skill_tree.state_locked")));
                    }
                    graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                }
            }
        }
    }

    // =========================================================================
    // Input Handling (with instant animation skip)
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isRevealAnimationActive()) {
            skipRevealAnim();
            SoundDirector.playClick();
            return true;
        }

        double px = toPanelX(mouseX);
        double py = toPanelY(mouseY);

        if (button == 0) {
            if (showingReactorGuide) {
                if (SkillTreeLayout.REACTOR_GUIDE_CLOSE.contains(px, py)
                        || !SkillTreeLayout.REACTOR_GUIDE_MODAL.contains(px, py)) {
                    showingReactorGuide = false;
                    SoundDirector.playClick();
                }
                return true;
            }
            if (SkillTreeLayout.CLOSE_BUTTON.contains(px, py)) {
                SoundDirector.playClick();
                onClose();
                return true;
            }
            int tab = SkillTreeLayout.tabAt(px, py);
            if (tab >= 0) {
                if (tab != activeTab) {
                    activeTab = tab;
                    SoundDirector.playClick();
                }
                return true;
            }

            if (activeTab == SkillTreeLayout.TAB_TREE) {
                // Phase 108: Search box focus & clear button + 4 State Filter pills
                if (!searchQuery.isEmpty() && SkillTreeLayout.SEARCH_CLEAR_BUTTON.contains(px, py)) {
                    searchQuery = "";
                    searchFocused = true;
                    SoundDirector.playClick();
                    return true;
                }
                if (SkillTreeLayout.SEARCH_BOX.contains(px, py)) {
                    searchFocused = true;
                    SoundDirector.playClick();
                    return true;
                }
                searchFocused = false;
                FilterMode clickedFilter = SkillTreeLayout.filterAt(px, py);
                if (clickedFilter != null) {
                    if (clickedFilter != filterMode) {
                        filterMode = clickedFilter;
                        SoundDirector.playClick();
                    }
                    return true;
                }

                // Phase 97: 4-branch selector tabs
                Branch clickedBranch = SkillTreeLayout.branchTabAt(px, py);
                if (clickedBranch != null) {
                    if (clickedBranch != activeBranch) {
                        switchBranch(clickedBranch);
                        SoundDirector.playClick();
                    }
                    return true;
                }
                // Phase 97: Per-branch Lv.1000 Limit Break activation button
                if (SkillTreeLayout.BRANCH_LIMIT_BREAK_BUTTON.contains(px, py)) {
                    if (ClientMachineProgress.canLimitBreakBranch(activeBranch)
                            && this.minecraft != null && this.minecraft.getConnection() != null) {
                        SoundDirector.playClick();
                        PacketDistributor.sendToServer(new BranchLimitBreakPayload(activeBranch.id()));
                    } else {
                        SoundDirector.playUiLocked();
                    }
                    return true;
                }
                for (ParentHit hit : parentHits) {
                    if (hit.rect().contains(px, py)) {
                        selectAndFocus(hit.machineId());
                        SoundDirector.playClick();
                        return true;
                    }
                }
                String clickedId = SkillTreeLayout.nodeAt(activeBranch, px, py, panX, panY);
                if (clickedId != null) {
                    selectedMachineId = clickedId;
                    NodeVisibility vis = SkillTreeLayout.visibilityOf(clickedId, effectiveRevealed());
                    if (vis == NodeVisibility.REVEALED) {
                        SoundDirector.playClick();
                    } else {
                        SoundDirector.playUiLocked();
                    }
                    return true;
                }
                if (SkillTreeLayout.TREE_VIEWPORT.contains(px, py)) {
                    draggingTree = true;
                    dragLastPx = px;
                    dragLastPy = py;
                    return true;
                }
            } else if (activeTab == SkillTreeLayout.TAB_ORELINE) {
                Rect vp = SkillTreeLayout.GUIDE_VIEWPORT;
                Rect listArea = new Rect(vp.x() + 4, vp.y() + 4 + ORE_BANNER_H + 3,
                        vp.width() - 14, vp.height() - ORE_BANNER_H - 11);
                if (listArea.contains(px, py)) {
                    int idx = (int) ((py - listArea.y() + oreScroll) / ORE_ROW_H);
                    List<OreLineStage> stages = SkillTreeLayout.oreLineStages();
                    if (idx >= 0 && idx < stages.size()) {
                        String targetId = stages.get(idx).machineId();
                        selectAndFocus(targetId);
                        activeTab = SkillTreeLayout.TAB_TREE;
                        NodeVisibility vis = SkillTreeLayout.visibilityOf(targetId, effectiveRevealed());
                        if (vis == NodeVisibility.REVEALED) {
                            SoundDirector.playClick();
                        } else {
                            SoundDirector.playUiLocked();
                        }
                        return true;
                    }
                }
            } else if (activeTab == SkillTreeLayout.TAB_INFRA) {
                if (SkillTreeLayout.INFRA_MULTIBLOCK_BANNER.contains(px, py)) {
                    showingReactorGuide = true;
                    SoundDirector.playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTree && activeTab == SkillTreeLayout.TAB_TREE && button == 0) {
            double px = toPanelX(mouseX);
            double py = toPanelY(mouseY);
            panX = SkillTreeLayout.clampPanX(panX - (px - dragLastPx));
            panY = SkillTreeLayout.clampPanY(panY - (py - dragLastPy));
            dragLastPx = px;
            dragLastPy = py;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingTree) {
            draggingTree = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == SkillTreeLayout.TAB_TREE) {
            double delta = (Math.abs(scrollX) > 0.01D ? -scrollX : -scrollY) * 24.0D;
            panX = SkillTreeLayout.clampPanX(panX + delta);
            return true;
        }
        if (activeTab == SkillTreeLayout.TAB_ORELINE) {
            oreScroll = Math.max(0.0D, oreScroll - scrollY * 22.0D);
            return true;
        }
        if (activeTab == SkillTreeLayout.TAB_INFRA) {
            infraScroll = Math.max(0.0D, infraScroll - scrollY * 24.0D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeTab == SkillTreeLayout.TAB_TREE && searchFocused && !showingReactorGuide) {
            if (codePoint >= 32 && searchQuery.length() < 32) {
                searchQuery += codePoint;
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isRevealAnimationActive() && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER)) {
            skipRevealAnim();
            SoundDirector.playClick();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && showingReactorGuide) {
            showingReactorGuide = false;
            SoundDirector.playClick();
            return true;
        }
        if (activeTab == SkillTreeLayout.TAB_TREE && searchFocused && !showingReactorGuide) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                            ? ""
                            : searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                focusFirstMatchingNode();
                return true;
            }
            // Swallow regular keys (like K / E) while typing in search box
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || ModKeyMappings.SKILL_TREE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void focusFirstMatchingNode() {
        if (!isSearchOrFilterActive()) {
            return;
        }
        // Prefer current branch first, then scan all branches
        for (MachineSkillTree.Node n : MachineSkillTree.nodesInBranch(activeBranch)) {
            if (nodeMatchesActiveSearchAndFilter(n)) {
                selectAndFocus(n.machineId());
                return;
            }
        }
        for (MachineSkillTree.Node n : MachineSkillTree.nodes()) {
            if (nodeMatchesActiveSearchAndFilter(n)) {
                selectAndFocus(n.machineId());
                return;
            }
        }
    }

    @Override
    public void onClose() {
        skipRevealAnim();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.returnScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectAndFocus(String machineId) {
        if (MachineSkillTree.isTracked(machineId)) {
            Branch b = MachineSkillTree.branchOf(machineId);
            if (b != null) {
                this.activeBranch = b;
            }
            this.selectedMachineId = machineId;
            this.panX = SkillTreeLayout.centerPanXFor(machineId);
        }
    }

    // =========================================================================
    // Drawing Helpers
    // =========================================================================

    /** 16x16 dark isometric industrial machine silhouette with centered "?" mark. */
    private void drawMysteryIcon(GuiGraphics g, int x, int y) {
        // Dark housing silhouette
        g.fill(x + 2, y + 1, x + 14, y + 15, 0xFF0A0F16);
        g.fill(x + 1, y + 3, x + 15, y + 13, 0xFF0E151E);
        // Subtle chamfered rim suggesting an unknown industrial chassis
        g.fill(x + 2, y + 1, x + 14, y + 2, 0xFF233242);
        g.fill(x + 1, y + 3, x + 2, y + 13, 0xFF1D2A38);
        g.fill(x + 14, y + 3, x + 15, y + 13, 0xFF151F2A);
        g.fill(x + 2, y + 14, x + 14, y + 15, 0xFF151F2A);
        // Recessed core shadow
        g.fill(x + 3, y + 3, x + 13, y + 13, 0xFF070B10);
        // Crisp centered "?"
        g.drawCenteredString(this.font, "?", x + 8, y + 4, 0xFF698299);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawHLine(GuiGraphics g, int x0, int x1, int y, int color) {
        int lo = Math.min(x0, x1);
        int hi = Math.max(x0, x1);
        g.fill(lo, y, hi + 1, y + 1, color);
    }

    private static void drawVLine(GuiGraphics g, int x, int y0, int y1, int color) {
        int lo = Math.min(y0, y1);
        int hi = Math.max(y0, y1);
        g.fill(x, lo, x + 1, hi + 1, color);
    }

    private static void drawDashedHLine(GuiGraphics g, int x0, int x1, int y, int color) {
        int lo = Math.min(x0, x1);
        int hi = Math.max(x0, x1);
        for (int x = lo; x <= hi; x += 5) {
            g.fill(x, y, Math.min(hi + 1, x + 2), y + 1, color);
        }
    }

    private static void drawDashedVLine(GuiGraphics g, int x, int y0, int y1, int color) {
        int lo = Math.min(y0, y1);
        int hi = Math.max(y0, y1);
        for (int y = lo; y <= hi; y += 5) {
            g.fill(x, y, x + 1, Math.min(hi + 1, y + 2), color);
        }
    }

    /** Tiny 6x7 pixel padlock glyph for locked nodes. */
    private static void drawPadlock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 3, color);
        g.fill(x + 4, y + 1, x + 5, y + 3, color);
        g.fill(x, y + 3, x + 6, y + 7, color);
        g.fill(x + 2, y + 4, x + 4, y + 6, 0xFF0A0F15);
    }

    private static void renderVerticalScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                                double scroll, int maxScroll, int totalH) {
        g.fill(x, y, x + w, y + h, 0xFF080D13);
        if (maxScroll <= 0 || totalH <= 0) {
            return;
        }
        int thumbH = Math.max(16, h * h / totalH);
        int thumbY = y + (int) Math.round((h - thumbH) * (scroll / maxScroll));
        g.fill(x + 1, thumbY, x + w - 1, thumbY + thumbH, MachineTheme.CYAN_DIM);
    }

    private static ItemStack blockIcon(String path) {
        Block block = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, path));
        return new ItemStack(block);
    }

    private static ItemStack modItemIcon(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, path));
        return new ItemStack(item);
    }

    private static ItemStack vanillaItemIcon(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.withDefaultNamespace(path));
        return new ItemStack(item);
    }

    private static double easeOutCubic(double t) {
        double inv = 1.0D - Math.max(0.0D, Math.min(1.0D, t));
        return 1.0D - inv * inv * inv;
    }

    private static double easeInOutCubic(double t) {
        double c = Math.max(0.0D, Math.min(1.0D, t));
        return c < 0.5D ? 4.0D * c * c * c : 1.0D - Math.pow(-2.0D * c + 2.0D, 3.0D) / 2.0D;
    }

    private static String formatNum(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
