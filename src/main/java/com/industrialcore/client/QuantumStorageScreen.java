package com.industrialcore.client;

import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.menu.QuantumStorageMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.QuantumFreqActionPayload;
import com.industrialcore.network.QuantumStorageActionPayload;
import com.industrialcore.network.QuantumStorageSyncPayload;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import com.industrialcore.storage.BigCountFormatter;
import com.industrialcore.storage.BigCountFormatter.ColorTier;
import com.industrialcore.storage.BigCountFormatter.UnitMode;
import com.industrialcore.storage.QuantumNetLogic;
import com.industrialcore.storage.QuantumStorageLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.industrialcore.client.MachineTheme.CYAN;
import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.CYAN_DIM;
import static com.industrialcore.client.MachineTheme.MUTED;

/**
 * Adaptive high-capacity GUI for the Quantum Storage Core &amp; Portable Quantum Terminal.
 *
 * <p>Features a wide 13x6 (78-cell) scrollable item+fluid grid with draggable scrollbar,
 * right-click-clearable search with optional JEI search synchronization, Ctrl/Shift mouse-wheel
 * 1-item pull/push, collapsible right-side Capacity Core &amp; Frequency Network dock (JEI
 * collision-aware), side-by-side 3x3 auto-refill crafting bay &amp; player inventory, and
 * Mushroom-Legend 8-tier inflation color badges up to 10^68 無量大数.</p>
 */
public class QuantumStorageScreen extends AdaptiveContainerScreen<QuantumStorageMenu> {
    private static final int GOLD = 0xFFFFD700;
    private static final int VIOLET = 0xFFD978FF;
    private static final int PANEL_BG = 0xFF0A0F16;
    private static final int HEADER_BG = 0xFF0F1824;
    private static final int SUBPANEL_BG = 0xFF080C12;
    private static final int BORDER_OUTER = 0xFF1F3447;

    /** Persists across screen re-opens within the client session. */
    private static UnitMode unitMode = UnitMode.JAPANESE;
    private static int sortMode = QuantumStorageLayout.SORT_COUNT;
    private static int savedBlockDockMode = QuantumStorageLayout.DOCK_CORE;
    private static boolean jeiSyncEnabled = false;

    private int dockMode;
    private int filterTab = QuantumStorageLayout.FILTER_ALL;
    private String searchQuery = "";
    private String lastSyncedJeiText = "";
    private boolean searchFocused = false;
    private boolean draggingScrollbar = false;
    private int scrollRow = 0;

    // Phase 96: Frequency dock state
    private String freqNameInput = "";
    private boolean freqNameFocused = false;
    private int freqSelectedIdx = -1;
    private int freqScrollOffset = 0;
    private long freqLastClickMs = 0L;
    private int freqLastClickIdx = -1;

    private long cachedSyncCounter = -1L;
    private boolean entriesDirty = true;
    private final List<DisplayEntry> filteredEntries = new ArrayList<>();

    private record DisplayEntry(
            boolean isFluid,
            @Nullable ItemStack item,
            @Nullable FluidStack fluid,
            BigInteger count,
            String displayName,
            String modNamespace
    ) {}

    public QuantumStorageScreen(QuantumStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = QuantumStorageLayout.WIDTH;
        this.imageHeight = QuantumStorageLayout.HEIGHT;
        this.titleLabelX = QuantumStorageLayout.TITLE_X;
        this.titleLabelY = QuantumStorageLayout.TITLE_Y;
        this.inventoryLabelX = QuantumStorageLayout.PLAYER_INV_X;
        this.inventoryLabelY = 10000;
        this.dockMode = menu.isPortableMode() ? QuantumStorageLayout.DOCK_FREQ : savedBlockDockMode;
        menu.setCoreDockVisible(this.dockMode == QuantumStorageLayout.DOCK_CORE);
    }

    @Override
    protected void init() {
        super.init();
        this.menu.setCoreDockVisible(this.dockMode == QuantumStorageLayout.DOCK_CORE);
        if (jeiSyncEnabled && JeiSearchBridge.isAvailable()) {
            String jeiText = JeiSearchBridge.getFilterText();
            if (!jeiText.equals(searchQuery)) {
                searchQuery = jeiText;
                lastSyncedJeiText = jeiText;
                entriesDirty = true;
            }
        }
    }

    /** Public helpers for JEI GUI extra areas and live verification. */
    public static UnitMode getUnitMode() {
        return unitMode;
    }

    public static void setUnitMode(UnitMode mode) {
        if (mode != null) {
            unitMode = mode;
        }
    }

    public int getDockMode() {
        return dockMode;
    }

    public void setDockMode(int mode) {
        if (this.menu.isPortableMode() && mode == QuantumStorageLayout.DOCK_CORE) {
            mode = QuantumStorageLayout.DOCK_FREQ;
        }
        this.dockMode = mode;
        if (mode != QuantumStorageLayout.DOCK_FREQ) {
            this.freqNameFocused = false;
        }
        if (!this.menu.isPortableMode()) {
            savedBlockDockMode = mode;
        }
        this.menu.setCoreDockVisible(mode == QuantumStorageLayout.DOCK_CORE);
    }

    public boolean isCoreDockOpen() {
        return dockMode == QuantumStorageLayout.DOCK_CORE;
    }

    public void setCoreDockOpen(boolean open) {
        setDockMode(open ? QuantumStorageLayout.DOCK_CORE : QuantumStorageLayout.DOCK_CLOSED);
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQueryForUi(String query) {
        this.searchQuery = query != null ? query : "";
        this.scrollRow = 0;
        this.entriesDirty = true;
        pushSearchToJeiIfSynced();
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (jeiSyncEnabled && JeiSearchBridge.isAvailable() && !searchFocused && !freqNameFocused) {
            String jeiText = JeiSearchBridge.getFilterText();
            if (!jeiText.equals(lastSyncedJeiText)) {
                lastSyncedJeiText = jeiText;
                if (!jeiText.equals(searchQuery)) {
                    searchQuery = jeiText;
                    scrollRow = 0;
                    entriesDirty = true;
                }
            }
        }
    }

    private void pushSearchToJeiIfSynced() {
        if (jeiSyncEnabled && JeiSearchBridge.isAvailable()) {
            lastSyncedJeiText = searchQuery;
            JeiSearchBridge.setFilterText(searchQuery);
        }
    }

    private void rebuildEntriesIfNeeded() {
        long sync = this.menu.getClientSyncCounter();
        if (!entriesDirty && sync == cachedSyncCounter) {
            return;
        }
        cachedSyncCounter = sync;
        entriesDirty = false;
        filteredEntries.clear();

        String needle = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (filterTab == QuantumStorageLayout.FILTER_ALL
                || filterTab == QuantumStorageLayout.FILTER_ITEMS) {
            for (QuantumStorageSyncPayload.ItemEntry e : this.menu.getClientItems()) {
                String name = e.stack().getHoverName().getString();
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(e.stack().getItem());
                String ns = id != null ? id.getNamespace() : "minecraft";
                if (!matchesSearch(needle, name, id, ns)) {
                    continue;
                }
                filteredEntries.add(new DisplayEntry(false, e.stack(), null, e.count(), name, ns));
            }
        }
        if (filterTab == QuantumStorageLayout.FILTER_ALL
                || filterTab == QuantumStorageLayout.FILTER_FLUIDS) {
            for (QuantumStorageSyncPayload.FluidEntry e : this.menu.getClientFluids()) {
                String name = e.fluid().getHoverName().getString();
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(e.fluid().getFluid());
                String ns = id != null ? id.getNamespace() : "minecraft";
                if (!matchesSearch(needle, name, id, ns)) {
                    continue;
                }
                filteredEntries.add(new DisplayEntry(true, null, e.fluid(), e.amount(), name, ns));
            }
        }

        Comparator<DisplayEntry> cmp = switch (sortMode) {
            case QuantumStorageLayout.SORT_NAME ->
                    Comparator.comparing((DisplayEntry d) -> d.displayName.toLowerCase(Locale.ROOT))
                            .thenComparing(DisplayEntry::count, Comparator.reverseOrder());
            case QuantumStorageLayout.SORT_MOD ->
                    Comparator.comparing(DisplayEntry::modNamespace)
                            .thenComparing(d -> d.displayName.toLowerCase(Locale.ROOT));
            default ->
                    Comparator.comparing(DisplayEntry::count, Comparator.reverseOrder())
                            .thenComparing(d -> d.displayName.toLowerCase(Locale.ROOT));
        };
        filteredEntries.sort(cmp);

        int maxScroll = QuantumStorageLayout.maxScrollRows(filteredEntries.size());
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow));

        List<ChannelSummary> chs = this.menu.getClientChannels();
        if (chs.isEmpty()) {
            freqSelectedIdx = -1;
            freqScrollOffset = 0;
        } else {
            if (freqSelectedIdx < 0 || freqSelectedIdx >= chs.size()) {
                // Auto-highlight active channel if present
                String act = this.menu.getClientChannelId();
                freqSelectedIdx = 0;
                for (int i = 0; i < chs.size(); i++) {
                    if (chs.get(i).channelId().equals(act)) {
                        freqSelectedIdx = i;
                        break;
                    }
                }
            }
            int maxFreqScroll = Math.max(0, chs.size() - QuantumStorageLayout.FREQ_VISIBLE_ROWS);
            freqScrollOffset = Math.max(0, Math.min(maxFreqScroll, freqScrollOffset));
        }
    }

    @Nullable
    private ChannelSummary selectedChannel() {
        List<ChannelSummary> list = this.menu.getClientChannels();
        if (freqSelectedIdx >= 0 && freqSelectedIdx < list.size()) {
            return list.get(freqSelectedIdx);
        }
        return null;
    }

    private static boolean matchesSearch(String needle, String name, @Nullable ResourceLocation id, String ns) {
        if (needle.isEmpty()) {
            return true;
        }
        if (needle.startsWith("@") && needle.length() > 1) {
            return ns.toLowerCase(Locale.ROOT).contains(needle.substring(1));
        }
        if (name.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return id != null && id.toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        rebuildEntriesIfNeeded();
        int left = this.leftPos;
        int top = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        long now = Util.getMillis();

        double lx = mouseX - left;
        double ly = mouseY - top;

        // 1. Collapsible Right-Side Dock (Core / Frequency / Collapsed Tabs)
        renderSideDock(g, left, top, lx, ly, now);

        // 2. Main Quantum Storage Dashboard outer frame
        g.fill(left, top, left + w, top + h, PANEL_BG);
        drawOutline(g, left, top, w, h, BORDER_OUTER);
        drawOutline(g, left + 1, top + 1, w - 2, h - 2, 0xFF0E1824);

        boolean linked = !this.menu.getClientChannelId().isEmpty();
        int accentLine = linked ? VIOLET : CYAN_BRIGHT;

        // Header band
        g.fill(left + 2, top + 2, left + w - 2, top + QuantumStorageLayout.HEADER_HEIGHT, HEADER_BG);
        g.fill(left + 2, top + QuantumStorageLayout.HEADER_HEIGHT,
                left + w - 2, top + QuantumStorageLayout.HEADER_HEIGHT + 1, CYAN_DIM);
        g.fill(left + 2, top + QuantumStorageLayout.HEADER_HEIGHT,
                left + 98, top + QuantumStorageLayout.HEADER_HEIGHT + 1, accentLine);

        MachineTheme.bracket(g, left + 3, top + 3, 1, 1, 5, accentLine);
        MachineTheme.bracket(g, left + w - 4, top + 3, -1, 1, 5, GOLD);
        MachineTheme.bracket(g, left + 3, top + h - 4, 1, -1, 5, CYAN_DIM);
        MachineTheme.bracket(g, left + w - 4, top + h - 4, -1, -1, 5, CYAN_DIM);

        Rect hs = QuantumStorageLayout.HEADER_STATS;
        g.fill(left + hs.x(), top + hs.y(), left + hs.right(), top + hs.bottom(), 0xFF070C12);
        drawOutline(g, left + hs.x(), top + hs.y(), hs.width(), hs.height(), 0xFF1A2F42);

        renderHeaderSkillTreeButton(g, left, top, lx, ly);

        // 3. Toolbar row
        drawPillButton(g, left, top, QuantumStorageLayout.TAB_ALL,
                Component.translatable("gui.industrialcore.storage.tab_all"),
                filterTab == QuantumStorageLayout.FILTER_ALL, lx, ly);
        drawPillButton(g, left, top, QuantumStorageLayout.TAB_ITEMS,
                Component.translatable("gui.industrialcore.storage.tab_items"),
                filterTab == QuantumStorageLayout.FILTER_ITEMS, lx, ly);
        drawPillButton(g, left, top, QuantumStorageLayout.TAB_FLUIDS,
                Component.translatable("gui.industrialcore.storage.tab_fluids"),
                filterTab == QuantumStorageLayout.FILTER_FLUIDS, lx, ly);

        // Search input box
        Rect sb = QuantumStorageLayout.SEARCH_BOX;
        g.fill(left + sb.x(), top + sb.y(), left + sb.right(), top + sb.bottom(), 0xFF05080D);
        drawOutline(g, left + sb.x(), top + sb.y(), sb.width(), sb.height(),
                searchFocused ? CYAN_BRIGHT : (sb.contains(lx, ly) ? CYAN : 0xFF284257));
        boolean blink = searchFocused && ((now / 450L) % 2L == 0L);
        if (searchQuery.isEmpty() && !searchFocused) {
            Component hint = MachineTheme.fit(this.font,
                    Component.translatable("gui.industrialcore.storage.search_hint"), sb.width() - 6);
            g.drawString(this.font, hint, left + sb.x() + 3, top + sb.y() + 3, 0xFF546B7D, false);
        } else {
            String text = searchQuery + (blink ? "_" : "");
            while (this.font.width(text) > sb.width() - 6 && text.length() > 1) {
                text = text.substring(1);
            }
            g.drawString(this.font, text, left + sb.x() + 3, top + sb.y() + 3, 0xFFE8F6FF, false);
        }

        boolean jeiAvail = JeiSearchBridge.isAvailable();
        drawPillButton(g, left, top, QuantumStorageLayout.JEI_SYNC_BUTTON,
                Component.literal("JEI"),
                jeiAvail && jeiSyncEnabled, lx, ly, jeiAvail);

        String sortKey = switch (sortMode) {
            case QuantumStorageLayout.SORT_NAME -> "gui.industrialcore.storage.sort_name";
            case QuantumStorageLayout.SORT_MOD -> "gui.industrialcore.storage.sort_mod";
            default -> "gui.industrialcore.storage.sort_count";
        };
        drawPillButton(g, left, top, QuantumStorageLayout.SORT_BUTTON,
                Component.translatable(sortKey), false, lx, ly);

        Component unitLabel = Component.translatable(unitMode == UnitMode.JAPANESE
                ? "gui.industrialcore.storage.unit_jp"
                : "gui.industrialcore.storage.unit_en");
        drawPillButton(g, left, top, QuantumStorageLayout.UNIT_BUTTON,
                unitLabel, unitMode == UnitMode.JAPANESE, lx, ly);

        // 4. Wide 13x6 Quantum Storage Grid Viewport
        Rect vp = QuantumStorageLayout.GRID_VIEWPORT;
        g.fill(left + vp.x(), top + vp.y(), left + vp.right(), top + vp.bottom(), SUBPANEL_BG);
        drawOutline(g, left + vp.x(), top + vp.y(), vp.width(), vp.height(), 0xFF1A2E40);

        int startIdx = scrollRow * QuantumStorageLayout.GRID_COLS;
        for (int i = 0; i < QuantumStorageLayout.GRID_VISIBLE; i++) {
            Rect cell = QuantumStorageLayout.gridCell(i);
            int cx = left + cell.x();
            int cy = top + cell.y();
            MachineTheme.slotWell(g, cx, cy);

            int dataIdx = startIdx + i;
            if (dataIdx < filteredEntries.size()) {
                renderStorageCell(g, filteredEntries.get(dataIdx), cx, cy, now);
            }
            if (lx >= cell.x() - 1 && lx <= cell.right() + 1
                    && ly >= cell.y() - 1 && ly <= cell.bottom() + 1) {
                g.fill(cx, cy, cx + 16, cy + 16, 0x3555FFFF);
            }
        }

        // Scrollbar
        Rect bar = QuantumStorageLayout.SCROLLBAR;
        boolean barHover = draggingScrollbar || bar.contains(lx, ly);
        g.fill(left + bar.x(), top + bar.y(), left + bar.right(), top + bar.bottom(), 0xFF060A10);
        drawOutline(g, left + bar.x(), top + bar.y(), bar.width(), bar.height(),
                barHover ? CYAN : 0xFF1B3042);
        int maxScroll = QuantumStorageLayout.maxScrollRows(filteredEntries.size());
        if (maxScroll > 0) {
            int totalRows = (filteredEntries.size() + QuantumStorageLayout.GRID_COLS - 1)
                    / QuantumStorageLayout.GRID_COLS;
            int thumbH = Math.max(16, (bar.height() - 4) * QuantumStorageLayout.GRID_ROWS / totalRows);
            int thumbY = top + bar.y() + 2
                    + (int) Math.round((bar.height() - 4 - thumbH) * (scrollRow / (double) maxScroll));
            int thumbCol = draggingScrollbar ? CYAN_BRIGHT : (barHover ? 0xFF3FD8FF : CYAN);
            g.fill(left + bar.x() + 2, thumbY, left + bar.right() - 2, thumbY + thumbH, thumbCol);
            int midY = thumbY + thumbH / 2;
            g.fill(left + bar.x() + 4, midY - 2, left + bar.right() - 4, midY - 1, 0xFF07131D);
            g.fill(left + bar.x() + 4, midY, left + bar.right() - 4, midY + 1, 0xFF07131D);
            g.fill(left + bar.x() + 4, midY + 2, left + bar.right() - 4, midY + 3, 0xFF07131D);
        } else {
            g.fill(left + bar.x() + 2, top + bar.y() + 2,
                    left + bar.right() - 2, top + bar.bottom() - 2, 0xFF152534);
        }

        g.fill(left + 8, top + 159, left + w - 8, top + 160, 0xFF172838);

        // 5. Lower-left 3x3 auto-refill crafting bay
        Rect cg = QuantumStorageLayout.CRAFT_GROUP;
        MachineTheme.ingredientGroup(g, left, top, cg.x(), cg.y(), cg.width(), cg.height());
        for (int i = 0; i < QuantumStorageMenu.CRAFT_GRID_COUNT; i++) {
            Rect r = QuantumStorageLayout.craftSlot(i);
            MachineTheme.slotWell(g, left + r.x(), top + r.y());
        }

        Rect arr = QuantumStorageLayout.CRAFT_ARROW;
        boolean arrHover = arr.contains(lx, ly);
        int ax = left + arr.x();
        int ay = top + arr.y() + 4;
        g.fill(ax, ay, ax + 8, ay + 2, arrHover ? CYAN_BRIGHT : CYAN_DIM);
        g.fill(ax + 7, ay - 2, ax + 9, ay + 4, arrHover ? 0xFFFFFFFF : CYAN);
        g.fill(ax + 9, ay - 1, ax + 11, ay + 3, CYAN_BRIGHT);
        g.fill(ax + 11, ay, ax + 13, ay + 2, CYAN_BRIGHT);

        Rect out = QuantumStorageLayout.CRAFT_OUTPUT;
        MachineTheme.outputSlot(g, left + out.x(), top + out.y());

        drawPillButton(g, left, top, QuantumStorageLayout.CLEAR_CRAFT_BUTTON,
                Component.translatable("gui.industrialcore.storage.clear_grid"), false, lx, ly);
        drawPillButton(g, left, top, QuantumStorageLayout.DEPOSIT_ALL_BUTTON,
                Component.translatable("gui.industrialcore.storage.deposit_all"), false, lx, ly);

        // 6. Lower-right player inventory (9x3 + hotbar)
        for (int i = 0; i < 36; i++) {
            Rect ps = QuantumStorageLayout.playerSlot(i);
            MachineTheme.slotWell(g, left + ps.x(), top + ps.y());
        }
    }

    private void renderSideDock(GuiGraphics g, int left, int top, double lx, double ly, long now) {
        boolean portable = this.menu.isPortableMode();
        if (dockMode == QuantumStorageLayout.DOCK_CORE && !portable) {
            Rect dk = QuantumStorageLayout.CORE_DOCK;
            g.fill(left + dk.x(), top + dk.y(), left + dk.right(), top + dk.bottom(), PANEL_BG);
            drawOutline(g, left + dk.x() - 1, top + dk.y(), dk.width() + 1, dk.height(), BORDER_OUTER);
            g.fill(left + dk.x(), top + dk.y() + 1, left + dk.right() - 1, top + dk.y() + 2, CYAN_DIM);

            drawPillButton(g, left, top, QuantumStorageLayout.CORE_TAB_OPEN,
                    Component.translatable("gui.industrialcore.storage.dock_close"), true, lx, ly);

            for (int i = 0; i < QuantumStorageMenu.CORE_COUNT; i++) {
                Rect cs = QuantumStorageLayout.coreSlot(i);
                MachineTheme.slotWell(g, left + cs.x(), top + cs.y());
                if (!this.menu.slots.get(QuantumStorageMenu.CORE_START + i).getItem().isEmpty()
                        && !this.menu.canRemoveCoreSlot(i)) {
                    drawOutline(g, left + cs.x() - 1, top + cs.y() - 1, 18, 18, 0xBBFFAA22);
                }
            }

            Rect ub = QuantumStorageLayout.USAGE_BAR;
            int used = this.menu.getClientUsedTypes();
            int maxT = Math.max(1, this.menu.getClientMaxTypes());
            double ratio = Math.min(1.0D, used / (double) maxT);
            g.fill(left + ub.x(), top + ub.y(), left + ub.right(), top + ub.bottom(), 0xFF04070B);
            drawOutline(g, left + ub.x(), top + ub.y(), ub.width(), ub.height(), 0xFF20384D);
            int fillW = (int) Math.round((ub.width() - 2) * ratio);
            if (fillW > 0) {
                int barCol = ratio >= 0.90D ? 0xFFFF6644 : (ratio >= 0.70D ? GOLD : CYAN_BRIGHT);
                g.fill(left + ub.x() + 1, top + ub.y() + 1,
                        left + ub.x() + 1 + fillW, top + ub.bottom() - 1, barCol);
            }

            // Quick switch button to Frequency Dock right below Core Dock
            boolean linked = !this.menu.getClientChannelId().isEmpty();
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_TAB_BELOW_CORE,
                    Component.translatable("gui.industrialcore.quantum_net.tab_freq"),
                    linked, lx, ly, true, VIOLET);
        } else if (dockMode == QuantumStorageLayout.DOCK_FREQ) {
            Rect fk = QuantumStorageLayout.FREQ_DOCK;
            g.fill(left + fk.x(), top + fk.y(), left + fk.right(), top + fk.bottom(), PANEL_BG);
            drawOutline(g, left + fk.x() - 1, top + fk.y(), fk.width() + 1, fk.height(), BORDER_OUTER);
            g.fill(left + fk.x(), top + fk.y() + 1, left + fk.right() - 1, top + fk.y() + 2, VIOLET);

            // Top row: Core tab (disabled in portable mode) + Frequency close button
            drawPillButton(g, left, top, QuantumStorageLayout.FREQ_TAB_TO_CORE,
                    Component.translatable("gui.industrialcore.quantum_net.tab_core"),
                    false, lx, ly, !portable);
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_TAB_CLOSE,
                    Component.translatable("gui.industrialcore.quantum_net.dock_close"),
                    true, lx, ly, true, VIOLET);

            // Current channel status pill + Disconnect button
            boolean linked = !this.menu.getClientChannelId().isEmpty();
            Rect cp = QuantumStorageLayout.FREQ_CURRENT_PILL;
            g.fill(left + cp.x(), top + cp.y(), left + cp.right(), top + cp.bottom(), 0xFF060B12);
            drawOutline(g, left + cp.x(), top + cp.y(), cp.width(), cp.height(),
                    linked ? VIOLET : 0xFF243B4F);
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_DISCONNECT,
                    Component.translatable("gui.industrialcore.quantum_net.btn_disconnect"),
                    false, lx, ly, linked, 0xFFFF7788);

            // Channel list box + scroll buttons
            Rect lb = QuantumStorageLayout.FREQ_LIST_BOX;
            g.fill(left + lb.x(), top + lb.y(), left + lb.right(), top + lb.bottom(), SUBPANEL_BG);
            drawOutline(g, left + lb.x(), top + lb.y(), lb.width(), lb.height(), 0xFF20384E);

            List<ChannelSummary> chs = this.menu.getClientChannels();
            String activeId = this.menu.getClientChannelId();
            for (int v = 0; v < QuantumStorageLayout.FREQ_VISIBLE_ROWS; v++) {
                int idx = freqScrollOffset + v;
                if (idx >= chs.size()) {
                    break;
                }
                ChannelSummary ch = chs.get(idx);
                Rect row = QuantumStorageLayout.freqListRow(v);
                boolean sel = idx == freqSelectedIdx;
                boolean act = ch.channelId().equals(activeId);
                boolean hov = row.contains(lx, ly);
                int rbg = sel ? 0xFF24163A : (act ? 0xFF122638 : (hov ? 0xFF121E2D : 0xFF0B111A));
                g.fill(left + row.x(), top + row.y(), left + row.right(), top + row.bottom(), rbg);
                if (sel) {
                    drawOutline(g, left + row.x(), top + row.y(), row.width(), row.height(), VIOLET);
                } else if (act) {
                    drawOutline(g, left + row.x(), top + row.y(), row.width(), row.height(), CYAN_BRIGHT);
                }
            }

            drawPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_UP,
                    Component.literal("^"), false, lx, ly, freqScrollOffset > 0);
            int maxFScroll = Math.max(0, chs.size() - QuantumStorageLayout.FREQ_VISIBLE_ROWS);
            drawPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_DOWN,
                    Component.literal("v"), false, lx, ly, freqScrollOffset < maxFScroll);

            // Action row: Connect / Public-Private / Delete
            ChannelSummary selCh = selectedChannel();
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_CONNECT,
                    Component.translatable("gui.industrialcore.quantum_net.btn_connect"),
                    false, lx, ly, selCh != null, CYAN_BRIGHT);
            Component pubLabel = selCh != null && !selCh.isPublic()
                    ? Component.translatable("gui.industrialcore.quantum_net.private")
                    : Component.translatable("gui.industrialcore.quantum_net.public");
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_PUBLIC,
                    pubLabel, selCh != null && selCh.isPublic(), lx, ly,
                    selCh != null && selCh.ownedByViewer(), 0xFF44EEAA);
            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_DELETE,
                    Component.translatable("gui.industrialcore.quantum_net.btn_delete"),
                    false, lx, ly, selCh != null && selCh.ownedByViewer(), 0xFFFF6677);

            // Create new channel input box + button
            Rect nb = QuantumStorageLayout.FREQ_NAME_BOX;
            g.fill(left + nb.x(), top + nb.y(), left + nb.right(), top + nb.bottom(), 0xFF05080D);
            drawOutline(g, left + nb.x(), top + nb.y(), nb.width(), nb.height(),
                    freqNameFocused ? VIOLET : (nb.contains(lx, ly) ? CYAN : 0xFF284257));
            boolean blink = freqNameFocused && ((now / 450L) % 2L == 0L);
            if (freqNameInput.isEmpty() && !freqNameFocused) {
                Component hint = MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.quantum_net.name_hint"), nb.width() - 6);
                g.drawString(this.font, hint, left + nb.x() + 3, top + nb.y() + 3, 0xFF546B7D, false);
            } else {
                String txt = freqNameInput + (blink ? "_" : "");
                while (this.font.width(txt) > nb.width() - 6 && txt.length() > 1) {
                    txt = txt.substring(1);
                }
                g.drawString(this.font, txt, left + nb.x() + 3, top + nb.y() + 3, 0xFFF3E6FF, false);
            }

            drawAccentPillButton(g, left, top, QuantumStorageLayout.FREQ_BTN_CREATE,
                    Component.translatable("gui.industrialcore.quantum_net.btn_create"),
                    false, lx, ly, true, VIOLET);

            // Bottom hint box
            Rect hb = QuantumStorageLayout.FREQ_HINT_BAR;
            g.fill(left + hb.x(), top + hb.y(), left + hb.right(), top + hb.bottom(), 0xFF060A10);
            drawOutline(g, left + hb.x(), top + hb.y(), hb.width(), hb.height(), 0xFF1A2D3F);
        } else {
            // Collapsed side tabs
            if (!portable) {
                Rect tb = QuantumStorageLayout.CORE_TAB_COLLAPSED;
                boolean hover = tb.contains(lx, ly);
                g.fill(left + tb.x(), top + tb.y(), left + tb.right(), top + tb.bottom(),
                        hover ? 0xFF153147 : 0xFF0C1520);
                drawOutline(g, left + tb.x() - 1, top + tb.y(), tb.width() + 1, tb.height(),
                        hover ? CYAN_BRIGHT : CYAN_DIM);
                g.drawString(this.font, "▶", left + tb.x() + 5, top + tb.y() + 8,
                        hover ? 0xFFFFFFFF : CYAN_BRIGHT, false);
                String cnt = this.menu.getInstalledCoreCount() + "/8";
                drawMiniOutlinedText(g, cnt, left + tb.x() + 2.5F, top + tb.y() + 26.0F, 0.72F, 0xFFCFE6F5);
            }
            Rect ft = QuantumStorageLayout.FREQ_TAB_COLLAPSED;
            boolean fHover = ft.contains(lx, ly);
            boolean linked = !this.menu.getClientChannelId().isEmpty();
            g.fill(left + ft.x(), top + ft.y(), left + ft.right(), top + ft.bottom(),
                    fHover ? 0xFF28173F : (linked ? 0xFF191229 : 0xFF0C1520));
            drawOutline(g, left + ft.x() - 1, top + ft.y(), ft.width() + 1, ft.height(),
                    fHover || linked ? VIOLET : 0xFF3E2B57);
            g.drawString(this.font, "◎", left + ft.x() + 5, top + ft.y() + 8,
                    fHover || linked ? VIOLET : 0xFFA48CC2, false);
            drawMiniOutlinedText(g, "CH", left + ft.x() + 3.0F, top + ft.y() + 26.0F, 0.72F,
                    linked ? 0xFFF3D8FF : 0xFF9AB4C8);
        }
    }

    private void renderStorageCell(GuiGraphics g, DisplayEntry entry, int cx, int cy, long now) {
        if (entry.isFluid && entry.fluid != null) {
            renderFluidIcon(g, entry.fluid, cx + 1, cy + 1, 14, 14);
            g.fill(cx, cy, cx + 3, cy + 1, CYAN_DIM);
            g.fill(cx + 13, cy, cx + 16, cy + 1, CYAN_DIM);
        } else if (entry.item != null) {
            g.renderFakeItem(entry.item, cx, cy);
        }

        ColorTier tier = BigCountFormatter.colorTier(entry.count, unitMode);
        int textColor = BigCountFormatter.argbColor(entry.count, unitMode, now);
        if (tier == ColorTier.MURYO) {
            drawOutline(g, cx - 1, cy - 1, 18, 18, (textColor & 0x00FFFFFF) | 0xCC000000);
            drawMiniOutlinedText(g, "✦", cx + 1, cy + 1, 0.55F, textColor);
        } else if (tier == ColorTier.PRISM) {
            drawOutline(g, cx - 1, cy - 1, 18, 18, (textColor & 0x00FFFFFF) | 0x77000000);
        }

        String badge = BigCountFormatter.formatCompact(entry.count, unitMode);
        float scale = badge.length() >= 5 ? 0.50F : 0.58F;
        int w = this.font.width(badge);
        float drawX = cx + 15.5F - w * scale;
        float drawY = cy + 15.5F - 8.0F * scale;
        drawMiniOutlinedText(g, badge, drawX, drawY, scale, textColor);
    }

    private void renderFluidIcon(GuiGraphics g, FluidStack fluid, int x, int y, int w, int h) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation tex = ext.getStillTexture(fluid);
        if (tex == null) {
            g.fill(x, y, x + w, y + h, 0xFF3399FF);
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
        int tint = ext.getTintColor(fluid);
        float a = ((tint >>> 24) & 0xFF) / 255.0F;
        float r = ((tint >>> 16) & 0xFF) / 255.0F;
        float gr = ((tint >>> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        if (a <= 0.01F) {
            a = 1.0F;
        }
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(x, y, 0, w, h, sprite);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawMiniOutlinedText(GuiGraphics g, String text, float x, float y, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 300.0F);
        g.pose().scale(scale, scale, 1.0F);
        int outline = 0xFF04070C;
        g.drawString(this.font, text, -1, 0, outline, false);
        g.drawString(this.font, text, 1, 0, outline, false);
        g.drawString(this.font, text, 0, -1, outline, false);
        g.drawString(this.font, text, 0, 1, outline, false);
        g.drawString(this.font, text, 0, 0, argb, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        long now = Util.getMillis();

        // Title in header (shows active frequency badge when linked)
        String chName = this.menu.getClientChannelName();
        Component headerTitle = chName.isEmpty()
                ? this.title
                : Component.literal("◎ " + chName);
        Component titleFit = MachineTheme.fit(this.font, headerTitle, 94);
        g.drawString(this.font, titleFit, QuantumStorageLayout.TITLE_X, QuantumStorageLayout.TITLE_Y,
                chName.isEmpty() ? 0xFFEAF7FF : VIOLET, false);

        // Live type usage + luxury colored per-type cap
        Rect hs = QuantumStorageLayout.HEADER_STATS;
        String typesPart = this.menu.getClientUsedTypes() + "/" + this.menu.getClientMaxTypes();
        BigInteger maxPer = this.menu.getClientMaxPerType();
        int capCol = BigCountFormatter.argbColor(maxPer, unitMode, now);
        String capFmt = (BigCountFormatter.isMuryo(maxPer) ? "✦" : "")
                + BigCountFormatter.format(maxPer, unitMode);
        int ty = hs.y() + 3;
        int typesW = Math.min(62, this.font.width(typesPart));
        g.drawString(this.font, MachineTheme.fit(this.font, Component.literal(typesPart), 62),
                hs.x() + 4, ty, 0xFFCFE6F5, false);
        g.drawString(this.font, "|", hs.x() + 6 + typesW, ty, 0xFF284359, false);
        int capStartX = hs.x() + 12 + typesW;
        int capAvail = Math.max(20, hs.right() - capStartX - 3);
        g.drawString(this.font, MachineTheme.fit(this.font, Component.literal(capFmt), capAvail),
                capStartX, ty, capCol, true);

        // Side dock labels
        if (dockMode == QuantumStorageLayout.DOCK_CORE && !this.menu.isPortableMode()) {
            String coreCnt = this.menu.getInstalledCoreCount() + " / 8";
            int cw = this.font.width(coreCnt);
            int cx = QuantumStorageLayout.CORE_DOCK.x()
                    + (QuantumStorageLayout.CORE_DOCK.width() - cw) / 2;
            g.drawString(this.font, coreCnt, cx, QuantumStorageLayout.CORE_COUNT_Y, 0xFFA8C6DA, false);
        } else if (dockMode == QuantumStorageLayout.DOCK_FREQ) {
            Rect cp = QuantumStorageLayout.FREQ_CURRENT_PILL;
            Component curLbl = chName.isEmpty()
                    ? Component.translatable(this.menu.isPortableMode()
                            ? "gui.industrialcore.quantum_net.unlinked"
                            : "gui.industrialcore.quantum_net.local")
                    : Component.literal(chName);
            g.drawString(this.font, MachineTheme.fit(this.font, curLbl, cp.width() - 6),
                    cp.x() + 3, cp.y() + 3, chName.isEmpty() ? MUTED : VIOLET, false);

            List<ChannelSummary> chs = this.menu.getClientChannels();
            if (chs.isEmpty()) {
                Rect lb = QuantumStorageLayout.FREQ_LIST_BOX;
                Component empty = MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.quantum_net.empty_list"), lb.width() - 8);
                g.drawString(this.font, empty, lb.x() + 4, lb.y() + 30, MUTED, false);
            } else {
                String activeId = this.menu.getClientChannelId();
                for (int v = 0; v < QuantumStorageLayout.FREQ_VISIBLE_ROWS; v++) {
                    int idx = freqScrollOffset + v;
                    if (idx >= chs.size()) {
                        break;
                    }
                    ChannelSummary ch = chs.get(idx);
                    Rect row = QuantumStorageLayout.freqListRow(v);
                    String prefix = ch.channelId().equals(activeId) ? "● " : "";
                    String tag = ch.isPublic() ? "[公]" : "[個]";
                    Component line = MachineTheme.fit(this.font,
                            Component.literal(prefix + ch.name() + " " + tag), row.width() - 4);
                    int col = ch.channelId().equals(activeId) ? CYAN_BRIGHT
                            : (idx == freqSelectedIdx ? 0xFFFFFFFF : 0xFFC5DCF0);
                    g.drawString(this.font, line, row.x() + 2, row.y() + 4, col, false);
                }
            }

            Rect hb = QuantumStorageLayout.FREQ_HINT_BAR;
            Component hint = Component.translatable(
                    this.menu.isPortableMode() && chName.isEmpty()
                            ? "gui.industrialcore.quantum_net.portable_hint"
                            : "gui.industrialcore.quantum_net.channel_count",
                    chs.size());
            g.drawString(this.font, MachineTheme.fit(this.font, hint, hb.width() - 6),
                    hb.x() + 3, hb.y() + 6, 0xFF8EAEC4, false);
        }

        // Section labels
        g.drawString(this.font,
                MachineTheme.fit(this.font, Component.translatable("gui.industrialcore.storage.craft_title"), 94),
                QuantumStorageLayout.CRAFT_LABEL_X, QuantumStorageLayout.CRAFT_LABEL_Y, CYAN_BRIGHT, false);
        g.drawString(this.font, this.playerInventoryTitle,
                QuantumStorageLayout.PLAYER_INV_X, QuantumStorageLayout.PLAYER_INV_LABEL_Y, MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;

        if (QuantumStorageLayout.SKILL_TREE_BUTTON.contains(lx, ly)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.industrialcore.skill_tree.button_guide_tip"));
            int pending = ClientMachineProgress.pendingReveals().size();
            if (pending > 0) {
                tip.add(Component.translatable("gui.industrialcore.skill_tree.button_pending_hint", pending)
                        .withStyle(ChatFormatting.GOLD));
            }
            graphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            return;
        }

        if (QuantumStorageLayout.HEADER_STATS.contains(lx, ly)) {
            long now = Util.getMillis();
            BigInteger maxPer = this.menu.getClientMaxPerType();
            int colRgb = BigCountFormatter.argbColor(maxPer, unitMode, now) & 0x00FFFFFF;
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.industrialcore.storage.types_line",
                    this.menu.getClientUsedTypes(), this.menu.getClientMaxTypes())
                    .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("gui.industrialcore.storage.cap_label")
                    .append(" ")
                    .append(Component.literal((BigCountFormatter.isMuryo(maxPer) ? "✦ " : "")
                                    + BigCountFormatter.format(maxPer, unitMode))
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colRgb)))));
            graphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            return;
        }

        if (QuantumStorageLayout.JEI_SYNC_BUTTON.contains(lx, ly)) {
            Component tip = JeiSearchBridge.isAvailable()
                    ? Component.translatable(jeiSyncEnabled
                            ? "gui.industrialcore.storage.jei_sync_on"
                            : "gui.industrialcore.storage.jei_sync_off")
                    : Component.translatable("gui.industrialcore.storage.jei_not_installed")
                            .withStyle(ChatFormatting.GRAY);
            graphics.renderTooltip(this.font, tip, mouseX, mouseY);
            return;
        }

        if (dockMode == QuantumStorageLayout.DOCK_CLOSED) {
            if (!this.menu.isPortableMode() && QuantumStorageLayout.CORE_TAB_COLLAPSED.contains(lx, ly)) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.industrialcore.storage.dock_open_tip",
                                this.menu.getInstalledCoreCount()),
                        mouseX, mouseY);
                return;
            }
            if (QuantumStorageLayout.FREQ_TAB_COLLAPSED.contains(lx, ly)) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.industrialcore.quantum_net.freq_open_tip"),
                        mouseX, mouseY);
                return;
            }
        }

        int cellIdx = QuantumStorageLayout.gridCellAt(lx, ly);
        if (cellIdx >= 0) {
            int dataIdx = scrollRow * QuantumStorageLayout.GRID_COLS + cellIdx;
            if (dataIdx < filteredEntries.size()) {
                renderEntryTooltip(graphics, filteredEntries.get(dataIdx), mouseX, mouseY);
                return;
            }
        }

        if (dockMode == QuantumStorageLayout.DOCK_CORE && !this.menu.isPortableMode()) {
            for (int i = 0; i < QuantumStorageMenu.CORE_COUNT; i++) {
                Rect cs = QuantumStorageLayout.coreSlot(i);
                if (lx >= cs.x() - 1 && lx <= cs.right() + 1 && ly >= cs.y() - 1 && ly <= cs.bottom() + 1) {
                    ItemStack stack = this.menu.slots.get(QuantumStorageMenu.CORE_START + i).getItem();
                    if (!stack.isEmpty() && !this.menu.canRemoveCoreSlot(i)) {
                        List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(stack));
                        lines.add(Component.translatable("gui.industrialcore.storage.core_locked")
                                .withStyle(ChatFormatting.RED));
                        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                        return;
                    }
                }
            }
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderEntryTooltip(GuiGraphics g, DisplayEntry entry, int mouseX, int mouseY) {
        long now = Util.getMillis();
        List<Component> lines = new ArrayList<>();
        if (entry.isFluid && entry.fluid != null) {
            lines.add(entry.fluid.getHoverName().copy().withStyle(ChatFormatting.AQUA));
        } else if (entry.item != null) {
            lines.addAll(this.getTooltipFromContainerItem(entry.item));
        }

        int colRgb = BigCountFormatter.argbColor(entry.count, unitMode, now) & 0x00FFFFFF;
        String star = BigCountFormatter.isMuryo(entry.count) ? "✦ " : "";
        String pretty = entry.isFluid
                ? star + BigCountFormatter.formatFluidTooltip(entry.count, unitMode)
                : star + BigCountFormatter.format(entry.count, unitMode);
        lines.add(Component.translatable("gui.industrialcore.storage.tooltip_count", pretty)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colRgb))));

        String exact = BigCountFormatter.formatExact(entry.count) + (entry.isFluid ? " mB" : "");
        if (exact.length() <= 30) {
            lines.add(Component.translatable("gui.industrialcore.storage.tooltip_exact", exact)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.industrialcore.storage.tooltip_exact_head")
                    .withStyle(ChatFormatting.GRAY));
            for (int pos = 0; pos < exact.length(); pos += 32) {
                int end = Math.min(exact.length(), pos + 32);
                lines.add(Component.literal("  " + exact.substring(pos, end))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        lines.add(Component.translatable(entry.isFluid
                        ? "gui.industrialcore.storage.tooltip_fluid_hint"
                        : "gui.industrialcore.storage.tooltip_item_hint")
                .withStyle(ChatFormatting.DARK_AQUA));
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // Input Handling
    // ------------------------------------------------------------------

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        double lx = mouseX - guiLeft;
        double ly = mouseY - guiTop;
        if (QuantumStorageLayout.activeSideArea(dockMode, this.menu.isPortableMode()).contains(lx, ly)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;
        boolean portable = this.menu.isPortableMode();

        // Header Skill Tree button
        if (button == 0 && QuantumStorageLayout.SKILL_TREE_BUTTON.contains(lx, ly) && this.minecraft != null) {
            SoundDirector.playClick();
            this.minecraft.setScreen(new SkillTreeScreen(null, this, SkillTreeLayout.TAB_TREE));
            return true;
        }

        // Handle Side Dock interactions
        if (handleSideDockClick(lx, ly, button, portable)) {
            return true;
        }
        freqNameFocused = false;

        // Search box (right-click instant clear)
        if (QuantumStorageLayout.SEARCH_BOX.contains(lx, ly)) {
            if (button == 1 && !searchQuery.isEmpty()) {
                searchQuery = "";
                scrollRow = 0;
                entriesDirty = true;
                pushSearchToJeiIfSynced();
            }
            searchFocused = true;
            SoundDirector.playClick();
            return true;
        } else {
            searchFocused = false;
        }

        // Scrollbar click / drag start
        if (button == 0 && QuantumStorageLayout.SCROLLBAR.contains(lx, ly)) {
            draggingScrollbar = true;
            scrollRow = QuantumStorageLayout.scrollRowForLocalY(ly, filteredEntries.size());
            return true;
        }

        if (button == 0) {
            if (QuantumStorageLayout.TAB_ALL.contains(lx, ly)) {
                setFilter(QuantumStorageLayout.FILTER_ALL);
                return true;
            }
            if (QuantumStorageLayout.TAB_ITEMS.contains(lx, ly)) {
                setFilter(QuantumStorageLayout.FILTER_ITEMS);
                return true;
            }
            if (QuantumStorageLayout.TAB_FLUIDS.contains(lx, ly)) {
                setFilter(QuantumStorageLayout.FILTER_FLUIDS);
                return true;
            }
            if (QuantumStorageLayout.JEI_SYNC_BUTTON.contains(lx, ly)) {
                if (JeiSearchBridge.isAvailable()) {
                    jeiSyncEnabled = !jeiSyncEnabled;
                    SoundDirector.playClick();
                    if (jeiSyncEnabled) {
                        pushSearchToJeiIfSynced();
                    }
                } else {
                    SoundDirector.playUiLocked();
                }
                return true;
            }
            if (QuantumStorageLayout.SORT_BUTTON.contains(lx, ly)) {
                sortMode = (sortMode + 1) % 3;
                entriesDirty = true;
                SoundDirector.playClick();
                return true;
            }
            if (QuantumStorageLayout.UNIT_BUTTON.contains(lx, ly)) {
                unitMode = unitMode.toggle();
                SoundDirector.playClick();
                return true;
            }
            if (QuantumStorageLayout.CLEAR_CRAFT_BUTTON.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                        this.menu.getBlockPos(), QuantumStorageActionPayload.ACTION_CLEAR_CRAFT,
                        0, ItemStack.EMPTY, FluidStack.EMPTY));
                return true;
            }
            if (QuantumStorageLayout.DEPOSIT_ALL_BUTTON.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                        this.menu.getBlockPos(), QuantumStorageActionPayload.ACTION_DEPOSIT_MATCHING,
                        0, ItemStack.EMPTY, FluidStack.EMPTY));
                return true;
            }
        }

        // Storage grid click
        if (button == 0 || button == 1) {
            int cellIdx = QuantumStorageLayout.gridCellAt(lx, ly);
            if (cellIdx >= 0) {
                ItemStack carried = this.menu.getCarried();
                int dataIdx = scrollRow * QuantumStorageLayout.GRID_COLS + cellIdx;
                DisplayEntry entry = dataIdx < filteredEntries.size() ? filteredEntries.get(dataIdx) : null;

                if (entry != null && entry.isFluid && entry.fluid != null) {
                    if (!carried.isEmpty()) {
                        SoundDirector.playClick();
                        PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                                this.menu.getBlockPos(),
                                QuantumStorageActionPayload.ACTION_INTERACT_FLUID,
                                button, ItemStack.EMPTY, entry.fluid));
                    } else {
                        SoundDirector.playUiLocked();
                    }
                    return true;
                }

                if (!carried.isEmpty()) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                            this.menu.getBlockPos(),
                            QuantumStorageActionPayload.ACTION_DEPOSIT_CARRIED,
                            button == 1 ? 1 : 0, ItemStack.EMPTY, FluidStack.EMPTY));
                    return true;
                }

                if (entry != null && !entry.isFluid && entry.item != null) {
                    int mode = hasShiftDown()
                            ? QuantumStorageActionPayload.MODE_SHIFT_TO_INV
                            : (button == 1
                                    ? QuantumStorageActionPayload.MODE_HALF_TO_CURSOR
                                    : QuantumStorageActionPayload.MODE_FULL_TO_CURSOR);
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                            this.menu.getBlockPos(),
                            QuantumStorageActionPayload.ACTION_EXTRACT_ITEM,
                            mode, entry.item, FluidStack.EMPTY));
                    return true;
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSideDockClick(double lx, double ly, int button, boolean portable) {
        if (dockMode == QuantumStorageLayout.DOCK_CORE && !portable) {
            if (button == 0 && QuantumStorageLayout.CORE_TAB_OPEN.contains(lx, ly)) {
                setDockMode(QuantumStorageLayout.DOCK_CLOSED);
                SoundDirector.playClick();
                return true;
            }
            if (button == 0 && QuantumStorageLayout.FREQ_TAB_BELOW_CORE.contains(lx, ly)) {
                setDockMode(QuantumStorageLayout.DOCK_FREQ);
                SoundDirector.playClick();
                return true;
            }
            return false;
        }

        if (dockMode == QuantumStorageLayout.DOCK_CLOSED) {
            if (button == 0 && !portable && QuantumStorageLayout.CORE_TAB_COLLAPSED.contains(lx, ly)) {
                setDockMode(QuantumStorageLayout.DOCK_CORE);
                SoundDirector.playClick();
                return true;
            }
            if (button == 0 && QuantumStorageLayout.FREQ_TAB_COLLAPSED.contains(lx, ly)) {
                setDockMode(QuantumStorageLayout.DOCK_FREQ);
                SoundDirector.playClick();
                return true;
            }
            return false;
        }

        if (dockMode == QuantumStorageLayout.DOCK_FREQ) {
            if (QuantumStorageLayout.FREQ_NAME_BOX.contains(lx, ly)) {
                if (button == 1) {
                    freqNameInput = "";
                }
                freqNameFocused = true;
                searchFocused = false;
                SoundDirector.playClick();
                return true;
            }
            if (button == 0) {
                if (!portable && QuantumStorageLayout.FREQ_TAB_TO_CORE.contains(lx, ly)) {
                    setDockMode(QuantumStorageLayout.DOCK_CORE);
                    SoundDirector.playClick();
                    return true;
                }
                if (QuantumStorageLayout.FREQ_TAB_CLOSE.contains(lx, ly)) {
                    setDockMode(QuantumStorageLayout.DOCK_CLOSED);
                    SoundDirector.playClick();
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_DISCONNECT.contains(lx, ly)
                        && !this.menu.getClientChannelId().isEmpty()) {
                    SoundDirector.playClick();
                    sendFreqAction(QuantumFreqActionPayload.ACTION_DISCONNECT, "", false);
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_UP.contains(lx, ly)) {
                    freqScrollOffset = Math.max(0, freqScrollOffset - 1);
                    SoundDirector.playClick();
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_DOWN.contains(lx, ly)) {
                    int maxS = Math.max(0, this.menu.getClientChannels().size()
                            - QuantumStorageLayout.FREQ_VISIBLE_ROWS);
                    freqScrollOffset = Math.min(maxS, freqScrollOffset + 1);
                    SoundDirector.playClick();
                    return true;
                }
                if (QuantumStorageLayout.FREQ_LIST_BOX.contains(lx, ly)) {
                    List<ChannelSummary> chs = this.menu.getClientChannels();
                    for (int v = 0; v < QuantumStorageLayout.FREQ_VISIBLE_ROWS; v++) {
                        int idx = freqScrollOffset + v;
                        if (idx >= chs.size()) {
                            break;
                        }
                        if (QuantumStorageLayout.freqListRow(v).contains(lx, ly)) {
                            long now = Util.getMillis();
                            freqSelectedIdx = idx;
                            SoundDirector.playClick();
                            if (freqLastClickIdx == idx && (now - freqLastClickMs) <= 350L) {
                                sendFreqAction(QuantumFreqActionPayload.ACTION_CONNECT,
                                        chs.get(idx).channelId(), false);
                                freqLastClickIdx = -1;
                            } else {
                                freqLastClickIdx = idx;
                                freqLastClickMs = now;
                            }
                            return true;
                        }
                    }
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_CONNECT.contains(lx, ly)) {
                    ChannelSummary sel = selectedChannel();
                    if (sel != null) {
                        SoundDirector.playClick();
                        sendFreqAction(QuantumFreqActionPayload.ACTION_CONNECT, sel.channelId(), false);
                    }
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_PUBLIC.contains(lx, ly)) {
                    ChannelSummary sel = selectedChannel();
                    if (sel != null && sel.ownedByViewer()) {
                        SoundDirector.playClick();
                        sendFreqAction(QuantumFreqActionPayload.ACTION_TOGGLE_PUBLIC, sel.channelId(), false);
                    }
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_DELETE.contains(lx, ly)) {
                    ChannelSummary sel = selectedChannel();
                    if (sel != null && sel.ownedByViewer()) {
                        SoundDirector.playClick();
                        sendFreqAction(QuantumFreqActionPayload.ACTION_DELETE, sel.channelId(), false);
                    }
                    return true;
                }
                if (QuantumStorageLayout.FREQ_BTN_CREATE.contains(lx, ly)) {
                    submitCreateChannel();
                    return true;
                }
                if (QuantumStorageLayout.FREQ_DOCK.contains(lx, ly)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void submitCreateChannel() {
        String name = freqNameInput.trim();
        if (name.isEmpty()) {
            name = "CH-" + (this.menu.getClientChannels().size() + 1);
        }
        SoundDirector.playClick();
        sendFreqAction(QuantumFreqActionPayload.ACTION_CREATE, name, true);
        freqNameInput = "";
        freqNameFocused = false;
    }

    private void sendFreqAction(int action, String value, boolean flag) {
        PacketDistributor.sendToServer(new QuantumFreqActionPayload(
                this.menu.getBlockPos(), this.menu.isPortableMode(), action, value, flag));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            double ly = panelMouseY(mouseY) - this.topPos;
            scrollRow = QuantumStorageLayout.scrollRowForLocalY(ly, filteredEntries.size());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setFilter(int next) {
        if (filterTab != next) {
            filterTab = next;
            scrollRow = 0;
            entriesDirty = true;
            SoundDirector.playClick();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = panelMouseX(mouseX) - this.leftPos;
        double ly = panelMouseY(mouseY) - this.topPos;

        if (dockMode == QuantumStorageLayout.DOCK_FREQ
                && QuantumStorageLayout.FREQ_LIST_BOX.contains(lx, ly) && scrollY != 0.0D) {
            int maxS = Math.max(0, this.menu.getClientChannels().size()
                    - QuantumStorageLayout.FREQ_VISIBLE_ROWS);
            freqScrollOffset = Math.max(0, Math.min(maxS, freqScrollOffset + (scrollY > 0 ? -1 : 1)));
            return true;
        }

        if ((hasShiftDown() || hasControlDown()) && scrollY != 0.0D) {
            int cellIdx = QuantumStorageLayout.gridCellAt(lx, ly);
            if (cellIdx >= 0) {
                int dataIdx = scrollRow * QuantumStorageLayout.GRID_COLS + cellIdx;
                if (dataIdx < filteredEntries.size()) {
                    DisplayEntry entry = filteredEntries.get(dataIdx);
                    if (!entry.isFluid && entry.item != null) {
                        int mode = scrollY > 0.0D
                                ? QuantumStorageActionPayload.MODE_PULL_ONE_TO_INV
                                : QuantumStorageActionPayload.MODE_PUSH_ONE_FROM_INV;
                        PacketDistributor.sendToServer(new QuantumStorageActionPayload(
                                this.menu.getBlockPos(),
                                QuantumStorageActionPayload.ACTION_EXTRACT_ITEM,
                                mode, entry.item, FluidStack.EMPTY));
                        SoundDirector.playHoverTick();
                        return true;
                    }
                }
            }
        }

        if (QuantumStorageLayout.GRID_VIEWPORT.contains(lx, ly)
                || QuantumStorageLayout.SCROLLBAR.contains(lx, ly)) {
            int maxScroll = QuantumStorageLayout.maxScrollRows(filteredEntries.size());
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + (scrollY > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (freqNameFocused && !Character.isISOControl(codePoint)) {
            if (freqNameInput.length() < QuantumNetLogic.CHANNEL_NAME_MAX_LEN) {
                freqNameInput += codePoint;
            }
            return true;
        }
        if (searchFocused && !Character.isISOControl(codePoint)) {
            if (searchQuery.length() < 36) {
                searchQuery += codePoint;
                scrollRow = 0;
                entriesDirty = true;
                pushSearchToJeiIfSynced();
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (freqNameFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                freqNameFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!freqNameInput.isEmpty()) {
                    freqNameInput = hasControlDown() ? ""
                            : freqNameInput.substring(0, freqNameInput.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitCreateChannel();
                return true;
            }
            // Swallow E and all other keys while channel name input is focused!
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = hasControlDown() ? "" : searchQuery.substring(0, searchQuery.length() - 1);
                    scrollRow = 0;
                    entriesDirty = true;
                    pushSearchToJeiIfSynced();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void drawPillButton(GuiGraphics g, int left, int top, Rect r,
                                Component label, boolean active, double lx, double ly) {
        drawPillButton(g, left, top, r, label, active, lx, ly, true);
    }

    private void drawPillButton(GuiGraphics g, int left, int top, Rect r,
                                Component label, boolean active, double lx, double ly, boolean enabled) {
        drawAccentPillButton(g, left, top, r, label, active, lx, ly, enabled, CYAN_BRIGHT);
    }

    private void drawAccentPillButton(GuiGraphics g, int left, int top, Rect r,
                                      Component label, boolean active, double lx, double ly,
                                      boolean enabled, int accent) {
        boolean hover = enabled && r.contains(lx, ly);
        int x = left + r.x();
        int y = top + r.y();
        int bg = !enabled ? 0xFF090E14 : (active ? 0xFF17273C : (hover ? 0xFF142536 : 0xFF0C1520));
        int border = !enabled ? 0xFF182633 : (active ? accent : (hover ? accent : 0xFF243B4F));
        int txtCol = !enabled ? 0xFF495E70 : (active ? 0xFFFFFFFF : (hover ? 0xFFEAF7FF : 0xFF9CB8CC));
        g.fill(x, y, x + r.width(), y + r.height(), bg);
        drawOutline(g, x, y, r.width(), r.height(), border);
        Component fit = MachineTheme.fit(this.font, label, r.width() - 4);
        int tx = x + (r.width() - this.font.width(fit)) / 2;
        int ty = y + (r.height() - 8) / 2;
        g.drawString(this.font, fit, tx, ty, txtCol, false);
    }

    private void renderHeaderSkillTreeButton(GuiGraphics g, int left, int top, double lx, double ly) {
        Rect r = QuantumStorageLayout.SKILL_TREE_BUTTON;
        int bx = left + r.x();
        int by = top + r.y();
        boolean hover = r.contains(lx, ly);
        if (hover) {
            MachineTheme.glowRect(g, bx, by, r.width(), r.height(), CYAN, 0.55D);
        }
        g.fill(bx, by, bx + r.width(), by + r.height(), hover ? 0xFF172C3D : 0xFF0C1620);
        drawOutline(g, bx, by, r.width(), r.height(), hover ? CYAN_BRIGHT : CYAN_DIM);

        int lineCol = hover ? 0xFFFFFFFF : CYAN_DIM;
        int nodeCol = hover ? 0xFFFFFFFF : CYAN_BRIGHT;
        g.fill(bx + 2, by + 6, bx + 5, by + 9, nodeCol);
        g.fill(bx + 5, by + 7, bx + 7, by + 8, lineCol);
        g.fill(bx + 6, by + 4, bx + 7, by + 11, lineCol);
        g.fill(bx + 7, by + 4, bx + 9, by + 5, lineCol);
        g.fill(bx + 7, by + 10, bx + 9, by + 11, lineCol);
        g.fill(bx + 9, by + 3, bx + 12, by + 6, nodeCol);
        g.fill(bx + 9, by + 9, bx + 12, by + 12, GOLD);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
