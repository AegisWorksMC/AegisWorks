package com.industrialcore.client;

import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.LargeQuarryLayout;
import com.industrialcore.menu.LargeQuarryMenu;
import com.industrialcore.network.LargeQuarryOrePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ore selection of the large quarry (Phase 22, rendering and sync fixed in Phase 23).
 *
 * <p>The candidate list is exactly the blocks of the {@code c:ores} tag, in the registry order
 * both sides agree on. The screen is paginated instead of scrolled so a hundred modded ores still
 * fit without a tiny scrollbar, and every click only names an index: the server resolves that
 * index against its own live list and toggles the ore there.</p>
 *
 * <p><b>Render order (Phase 23):</b> {@link Screen#render} calls {@code renderBackground} first
 * and then renders the widgets. The panel, cells and item icons are therefore drawn inside the
 * {@link #renderBackground} override - after the vanilla blur pass, below the buttons. They used
 * to be drawn before {@code super.render()}, which handed them to a <i>second</i> background
 * pass: {@code renderBlurredBackground} runs {@code GameRenderer.processBlurEffect} immediately
 * on the framebuffer, while GuiGraphics had already flushed the queued fills and items there.
 * The result was the reported bug - icons blurred into an unreadable mosaic, with the
 * selected/unselected colours smeared beyond recognition - and a duplicated dark gradient on
 * top.</p>
 *
 * <p>The selection shown here comes from the block update the machine sends after every change,
 * so the screen never predicts a state the server has not accepted.</p>
 */
public class LargeQuarryOrePickerScreen extends Screen {
    // Palette from MachineTheme (Phase 42).
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_CELL = MachineTheme.SLOT_CELL;
    private static final int COLOR_CELL_SELECTED = MachineTheme.CYAN_DEEP;
    private static final int COLOR_CELL_BORDER = MachineTheme.SLOT_EDGE;
    private static final int COLOR_CELL_BORDER_SELECTED = MachineTheme.CYAN;

    /** Geometry lives in {@link LargeQuarryLayout} so drawing, clicking and tests share it. */
    private static final int WIDTH = LargeQuarryLayout.ORE_PICKER_WIDTH;
    private static final int HEIGHT = LargeQuarryLayout.ORE_PICKER_HEIGHT;
    private static final int COLUMNS = LargeQuarryLayout.ORE_PICKER_COLUMNS;
    private static final int ROWS = LargeQuarryLayout.ORE_PICKER_ROWS;
    private static final int PAGE_SIZE = LargeQuarryLayout.ORE_PICKER_PAGE_SIZE;
    private static final int GRID_X = LargeQuarryLayout.ORE_PICKER_GRID_X;
    private static final int GRID_Y = LargeQuarryLayout.ORE_PICKER_GRID_Y;
    private static final int CELL = LargeQuarryLayout.ORE_PICKER_CELL;

    private final LargeQuarryMenu menu;
    private final Screen parent;

    private List<Block> candidates = List.of();
    private Set<String> selected = Set.of();
    private List<String> selectedSource = List.of();
    private int page;

    public LargeQuarryOrePickerScreen(LargeQuarryMenu menu, Screen parent) {
        super(Component.translatable("gui.industrialcore.large_quarry.ore_picker_title"));
        this.menu = menu;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.candidates = LargeQuarryBlockEntity.computeOreCandidates();
        if (this.page > 0 && this.page >= pageCount()) {
            this.page = pageCount() - 1;
        }
        this.refreshSelection();

        int panelLeft = (this.width - WIDTH) / 2;
        int panelTop = (this.height - HEIGHT) / 2;

        int pageRow = LargeQuarryLayout.ORE_PICKER_PAGE_ROW_Y;
        int actionRow = LargeQuarryLayout.ORE_PICKER_ACTION_ROW_Y;
        this.addRenderableWidget(Button.builder(Component.literal("<"),
                        pressed -> this.changePage(-1))
                .bounds(panelLeft + 17, panelTop + pageRow, 20, 16)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(">"),
                        pressed -> this.changePage(1))
                .bounds(panelLeft + WIDTH - 37, panelTop + pageRow, 20, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.large_quarry.select_all"),
                        pressed -> send(LargeQuarryOrePayload.ACTION_SELECT_ALL, -1))
                .bounds(panelLeft + 17, panelTop + actionRow, 52, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.large_quarry.select_none"),
                        pressed -> send(LargeQuarryOrePayload.ACTION_CLEAR_ALL, -1))
                .bounds(panelLeft + 73, panelTop + actionRow, 52, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.large_quarry.close"),
                        pressed -> this.onClose())
                .bounds(panelLeft + 129, panelTop + actionRow, 50, 18)
                .build());
    }

    /** Re-reads the synced selection; the list identity only changes on a block update. */
    private void refreshSelection() {
        List<String> ids = this.menu.getSelectedOreIds();
        if (ids == this.selectedSource) {
            return;
        }
        this.selectedSource = ids;
        this.selected = new HashSet<>(ids);
    }

    private int pageCount() {
        return Math.max(1, (this.candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void changePage(int delta) {
        this.page = Math.max(0, Math.min(pageCount() - 1, this.page + delta));
    }

    private void send(int action, int index) {
        PacketDistributor.sendToServer(
                new LargeQuarryOrePayload(this.menu.getBlockPos(), action, index));
    }

    private static ItemStack icon(Block block) {
        return block.asItem().getDefaultInstance();
    }

    private int indexAt(double mouseX, double mouseY) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        return LargeQuarryLayout.orePickerIndexAt(mouseX, mouseY, left, top,
                this.page, this.candidates.size());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            this.send(LargeQuarryOrePayload.ACTION_TOGGLE, index);
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return false;
    }

    /**
     * Called exactly once per frame by {@link Screen#render}, before the widgets: the vanilla
     * blur and gradient first, then the panel content that must sit above them and below the
     * buttons.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.drawPanel(graphics);
    }

    /** Panel, title, counter, the cell grid with the item icons and the page label. */
    private void drawPanel(GuiGraphics graphics) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        // A picker is a list dialog, not a machine panel: it takes the housing, the header band
        // and the palette, but not the status strip or the inventory block.
        MachineTheme.frame(graphics, left, top, WIDTH, HEIGHT);
        MachineTheme.band(graphics, left, top);
        MachineTheme.title(this.font, graphics, left, top, this.title);
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.large_quarry.ore_picker_count",
                        this.selected.size(), this.candidates.size()),
                left + GuiMetrics.SIDE, top + LargeQuarryLayout.ORE_PICKER_COUNT_Y,
                COLOR_MUTED, false);

        for (int local = 0; local < PAGE_SIZE; local++) {
            int index = this.page * PAGE_SIZE + local;
            if (index >= this.candidates.size()) {
                break;
            }
            int column = local % COLUMNS;
            int row = local / COLUMNS;
            int x = left + GRID_X + column * CELL;
            int y = top + GRID_Y + row * CELL;
            Block block = this.candidates.get(index);
            boolean isSelected = this.selected.contains(
                    BuiltInRegistries.BLOCK.getKey(block).toString());
            graphics.fill(x, y, x + 17, y + 17,
                    isSelected ? COLOR_CELL_SELECTED : COLOR_CELL);
            graphics.fill(x, y, x + 17, y + 1,
                    isSelected ? COLOR_CELL_BORDER_SELECTED : COLOR_CELL_BORDER);
            graphics.fill(x, y + 16, x + 17, y + 17,
                    isSelected ? COLOR_CELL_BORDER_SELECTED : COLOR_CELL_BORDER);
            graphics.fill(x, y, x + 1, y + 17,
                    isSelected ? COLOR_CELL_BORDER_SELECTED : COLOR_CELL_BORDER);
            graphics.fill(x + 16, y, x + 17, y + 17,
                    isSelected ? COLOR_CELL_BORDER_SELECTED : COLOR_CELL_BORDER);
            ItemStack stack = icon(block);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
            }
        }

        graphics.drawCenteredString(this.font, Component.translatable(
                        "gui.industrialcore.large_quarry.page",
                        this.page + 1, pageCount()),
                left + WIDTH / 2, top + LargeQuarryLayout.ORE_PICKER_PAGE_ROW_Y + 4,
                COLOR_MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.refreshSelection();
        // Screen#render: renderBackground (blur + panel + icons) and then the widgets.
        super.render(graphics, mouseX, mouseY, partialTick);

        int hovered = this.indexAt(mouseX, mouseY);
        if (hovered >= 0) {
            Block block = this.candidates.get(hovered);
            ItemStack stack = icon(block);
            Component name = stack.isEmpty()
                    ? Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString())
                    : stack.getHoverName();
            boolean isSelected = this.selected.contains(
                    BuiltInRegistries.BLOCK.getKey(block).toString());
            graphics.renderTooltip(this.font, name.copy().append(" ")
                    .append(Component.translatable(isSelected
                            ? "gui.industrialcore.large_quarry.ore_selected"
                            : "gui.industrialcore.large_quarry.ore_unselected")), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
