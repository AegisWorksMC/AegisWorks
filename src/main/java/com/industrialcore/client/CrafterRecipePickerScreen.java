package com.industrialcore.client;

import com.industrialcore.machine.CrafterRecipeRules;
import com.industrialcore.menu.CrafterMenu;
import com.industrialcore.network.SetCrafterRecipePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Recipe picker for the auto crafter.
 *
 * <p>The list is built once when the screen opens from the recipes the server already synchronised
 * to the client. Nothing is sent to the server while browsing: only the clicked recipe id goes over
 * the network, and the server validates it against its own recipe manager. Returning to the
 * crafter screen keeps the container open, so ingredients and settings are untouched.</p>
 */
public class CrafterRecipePickerScreen extends Screen {
    // Palette from MachineTheme (Phase 42); a list dialog keeps the colours, not the housing.
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ROW = MachineTheme.PANEL_TOP;
    private static final int COLOR_ROW_SELECTED = MachineTheme.CYAN_DEEP;
    private static final int ROW_WIDTH = 320;

    // Phase 43: the dialog takes the shared housing and header band, but not the machine body —
    // a picker is a list, so it keeps one header, one search row, one list and one button row.
    private static final int PANEL_TOP = 14;
    private static final int HEADER = 30;
    private static final int SEARCH_Y = HEADER + 6;
    private static final int LIST_TOP = HEADER + SEARCH_Y + 18;
    private static final int LIST_BOTTOM_MARGIN = 38;

    private final CrafterMenu menu;
    private final Screen parent;

    private List<RecipeChoice> choices = List.of();
    private RecipeList list;
    private EditBox search;
    private int panelLeft;
    private int panelWidth;
    private int panelHeight;

    public CrafterRecipePickerScreen(CrafterMenu menu, Screen parent) {
        super(Component.translatable("gui.industrialcore.crafter.picker_title"));
        this.menu = menu;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.choices = this.collectChoices();

        this.panelWidth = Math.min(432, Math.max(228, this.width - 20));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelHeight = this.height - PANEL_TOP * 2;

        int listWidth = this.panelWidth - 16;
        int listHeight = Math.max(40, this.panelHeight - LIST_TOP - LIST_BOTTOM_MARGIN);
        this.list = new RecipeList(this.minecraft, listWidth, listHeight, PANEL_TOP + LIST_TOP, 20);
        this.list.setX(this.panelLeft + 8);
        this.addRenderableWidget(this.list);

        this.search = new EditBox(this.font, this.panelLeft + 8, PANEL_TOP + SEARCH_Y,
                this.panelWidth - 16, 16,
                Component.translatable("gui.industrialcore.crafter.search"));
        this.search.setHint(Component.translatable("gui.industrialcore.crafter.search_hint"));
        this.search.setResponder(value -> this.applyFilter(value, true));
        this.addRenderableWidget(this.search);

        int buttonRow = PANEL_TOP + this.panelHeight - 30;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.industrialcore.crafter.clear"),
                        pressed -> this.select(""))
                .bounds(this.width / 2 - 84, buttonRow, 80, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.industrialcore.crafter.back"),
                        pressed -> this.onClose())
                .bounds(this.width / 2 + 4, buttonRow, 80, 20)
                .build());

        this.applyFilter("", true);
        this.selectCurrentRecipe();
    }

    /**
     * Draws the dialog housing under the widgets: the vanilla blur and gradient first, then the
     * shared frame, header band and title. Same order as the ore picker, so the list, the icons
     * and the buttons always stay above the panel.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        MachineTheme.frame(graphics, this.panelLeft, PANEL_TOP, this.panelWidth, this.panelHeight);
        MachineTheme.band(graphics, this.panelLeft, PANEL_TOP, this.panelWidth);
        MachineTheme.title(this.font, graphics, this.panelLeft, PANEL_TOP, this.title);
    }

    private void selectCurrentRecipe() {
        String selectedId = this.menu.getSelectedRecipeId();
        if (selectedId.isEmpty() || this.list == null) {
            return;
        }
        for (RecipeEntry entry : this.list.children()) {
            if (entry.choice.id().toString().equals(selectedId)) {
                this.list.setSelected(entry);
                this.list.reveal(entry);
                return;
            }
        }
    }

    private List<RecipeChoice> collectChoices() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        List<RecipeChoice> result = new ArrayList<>();
        for (RecipeHolder<?> holder : this.minecraft.level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            try {
                if (!CrafterRecipeRules.isSupported(recipe, this.minecraft.level.registryAccess())) {
                    continue;
                }
                ItemStack output = recipe.getResultItem(this.minecraft.level.registryAccess());
                if (output.isEmpty()) {
                    continue;
                }
                String name = output.getHoverName().getString();
                result.add(new RecipeChoice(holder.id(), output.copy(), name, holder.id().toString()));
            } catch (RuntimeException ignored) {
                // A broken third-party recipe must not break the whole picker.
            }
        }
        result.sort(Comparator
                .comparing((RecipeChoice choice) -> choice.name().toLowerCase(Locale.ROOT))
                .thenComparing(choice -> choice.id().toString()));
        return List.copyOf(result);
    }

    private void applyFilter(String value, boolean resetScroll) {
        if (this.list == null) {
            return;
        }
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        List<RecipeChoice> matching = new ArrayList<>();
        for (RecipeChoice choice : this.choices) {
            if (query.isEmpty()
                    || choice.name().toLowerCase(Locale.ROOT).contains(query)
                    || choice.idText().toLowerCase(Locale.ROOT).contains(query)) {
                matching.add(choice);
            }
        }
        this.list.rebuild(matching);
        if (resetScroll) {
            this.list.setScrollAmount(0);
        }
        this.selectCurrentRecipe();
    }

    private void select(String recipeId) {
        PacketDistributor.sendToServer(new SetCrafterRecipePayload(this.menu.getBlockPos(), recipeId));
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.list != null && this.list.children().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.industrialcore.crafter.no_matches"),
                    this.width / 2, this.height / 2, COLOR_MUTED);
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

    /** One selectable recipe, resolved when the screen opens. */
    private record RecipeChoice(ResourceLocation id, ItemStack output, String name, String idText) {}

    private final class RecipeEntry extends ObjectSelectionList.Entry<RecipeEntry> {
        private final RecipeChoice choice;

        private RecipeEntry(RecipeChoice choice) {
            this.choice = choice;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean selected = CrafterRecipePickerScreen.this.list.getSelected() == this;
            graphics.fill(left - 2, top - 1, left + width + 2, top + height + 1,
                    selected ? COLOR_ROW_SELECTED : (hovering ? COLOR_ROW : 0x00000000));
            graphics.renderItem(this.choice.output(), left + 2, top + 2);
            String name = CrafterRecipePickerScreen.this.font
                    .plainSubstrByWidth(this.choice.name(), Math.min(180, width - 24));
            graphics.drawString(CrafterRecipePickerScreen.this.font, name, left + 24, top + 2, COLOR_TEXT, false);
            if (width >= 280) {
                int idWidth = Math.max(10, width - 24 - CrafterRecipePickerScreen.this.font.width(name) - 8);
                String id = CrafterRecipePickerScreen.this.font.plainSubstrByWidth(this.choice.idText(), idWidth);
                graphics.drawString(CrafterRecipePickerScreen.this.font, id, left + width - 2
                        - CrafterRecipePickerScreen.this.font.width(id), top + 11, COLOR_MUTED, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                CrafterRecipePickerScreen.this.select(this.choice.id().toString());
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.choice.name());
        }
    }

    private final class RecipeList extends ObjectSelectionList<RecipeEntry> {
        private RecipeList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        /** Replaces all rows. Kept here because clearEntries/addEntry are protected. */
        private void rebuild(List<RecipeChoice> choices) {
            this.clearEntries();
            for (RecipeChoice choice : choices) {
                this.addEntry(new RecipeEntry(choice));
            }
        }

        private void reveal(RecipeEntry entry) {
            this.ensureVisible(entry);
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }
    }
}
