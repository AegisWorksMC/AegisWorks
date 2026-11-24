package com.industrialcore.client;

import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.machine.CrafterRecipeRules;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.menu.CrafterLayout;
import com.industrialcore.menu.CrafterMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.MachineConfigPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Auto crafter GUI.
 *
 * <p>All numbers come from {@link CrafterLayout}, the same class the menu uses for slot positions,
 * so a slot cannot drift under a label. The panel only displays server state: the recipe id is
 * synchronised through the block entity update tag, status/progress/energy through the menu's
 * container data.</p>
 *
 * <p>Phase 83 fixes:
 * <ul>
 *   <li>Single scaled {@code renderBg} pass via {@link AdaptiveContainerScreen} (eliminating the
 *       unscaled duplicate background/preview item pass).</li>
 *   <li>Unified ghost preview on empty slots only ({@link #shouldRenderGhost}): real items and
 *       ghost/preview items never draw over each other.</li>
 *   <li>Left-to-right golden-ratio layout with centred "Output" and "Motor" labels clearly
 *       separated from slot frames and neon rings.</li>
 * </ul></p>
 */
public class CrafterScreen extends AdaptiveContainerScreen<CrafterMenu> {
    // Palette and primitives come from MachineTheme (Phase 42).
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_PROGRESS = MachineTheme.STATUS_OK;
    private static final int COLOR_WARN = MachineTheme.STATUS_WARN;
    private static final int COLOR_BUTTON = MachineTheme.PANEL_TOP;
    private static final int COLOR_BUTTON_BORDER = MachineTheme.LINE_SOFT;
    /** Dark translucent veil blended exclusively over ghost item pixels via guiGhostRecipeOverlay. */
    private static final int GHOST_VEIL = 0x90080D12;

    private String cachedRecipeId;
    private Component cachedRecipeName = Component.empty();
    private ItemStack cachedRecipeOutput = ItemStack.EMPTY;
    private final Ingredient[] cachedPattern = new Ingredient[CrafterBlockEntity.INPUT_SLOTS];
    private ItemStack previewResult = ItemStack.EMPTY;
    private long lastGridHash = Long.MIN_VALUE;

    public CrafterScreen(CrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CrafterLayout.WIDTH;
        this.imageHeight = CrafterLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
        Arrays.fill(this.cachedPattern, Ingredient.EMPTY);
    }

    @Override
    protected void init() {
        super.init();
        Rect button = CrafterLayout.RECIPE_BUTTON;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.crafter.select"),
                        pressed -> this.openRecipePicker())
                .bounds(this.leftPos + button.x(), this.topPos + button.y(), button.width(), button.height())
                .build());

        Rect register = CrafterLayout.REGISTER_BUTTON;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.crafter.register"),
                        pressed -> send(MachineConfigPayload.ACTION_REGISTER_GRID, -1))
                .bounds(this.leftPos + register.x(), this.topPos + register.y(),
                        register.width(), register.height())
                .build());
    }

    private void send(int action, int side) {
        PacketDistributor.sendToServer(new MachineConfigPayload(this.menu.getBlockPos(), side, action));
    }

    /** Recomputes the grid result preview whenever the 3x3 input arrangement changes. */
    private void updatePreview() {
        long hash = 17L;
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            ItemStack stack = this.menu.slots.get(slot).getItem();
            hash = hash * 31L + (stack.isEmpty() ? 0 : Item.getId(stack.getItem()) + 1);
            hash = hash * 31L + stack.getCount();
        }
        if (hash == this.lastGridHash) {
            return;
        }
        this.lastGridHash = hash;
        if (this.minecraft == null || this.minecraft.level == null) {
            this.previewResult = ItemStack.EMPTY;
            return;
        }
        List<ItemStack> stacks = new ArrayList<>(CrafterBlockEntity.INPUT_SLOTS);
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            stacks.add(this.menu.slots.get(slot).getItem().copy());
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        if (input.isEmpty()) {
            this.previewResult = ItemStack.EMPTY;
            return;
        }
        try {
            this.previewResult = CrafterRecipeRules.findMatchingRecipe(
                            this.minecraft.level.getRecipeManager(), input, this.minecraft.level)
                    .map(holder -> holder.value().getResultItem(this.minecraft.level.registryAccess()).copy())
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            this.previewResult = ItemStack.EMPTY;
        }
    }

    private void openRecipePicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CrafterRecipePickerScreen(this.menu, this));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, CrafterLayout.HEIGHT,
                CrafterLayout.BODY_BOTTOM, statusColor(this.menu.getStatus()));

        Rect energyBar = CrafterLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                this.menu.getEnergyCapacity());

        Rect progress = CrafterLayout.PROGRESS_BAR;
        MachineTheme.progressBar(graphics, left + progress.x(), top + progress.y(),
                progress.width(), progress.height(), this.menu.getProgress(),
                this.menu.getCraftTime());

        for (Rect button : new Rect[] {CrafterLayout.RECIPE_BUTTON, CrafterLayout.REGISTER_BUTTON}) {
            graphics.fill(left + button.x(), top + button.y(), left + button.right(), top + button.bottom(),
                    COLOR_BUTTON);
            graphics.fill(left + button.x(), top + button.y(), left + button.right(), top + button.y() + 1,
                    COLOR_BUTTON_BORDER);
            graphics.fill(left + button.x(), top + button.bottom() - 1, left + button.right(),
                    top + button.bottom(), COLOR_BUTTON_BORDER);
        }

        // Left: purple containment frame around the 3x3 pattern input grid.
        Rect inputGroup = CrafterLayout.INPUT_GROUP;
        MachineTheme.ingredientGroup(graphics, left, top,
                inputGroup.x(), inputGroup.y(), inputGroup.width(), inputGroup.height());

        // Right: cyan result well with exit chevrons; registers output coordinates with GuiDopamine.
        Rect out = CrafterLayout.OUTPUT_SLOT;
        MachineTheme.outputSlot(graphics, left + out.x(), top + out.y());

        MachineTheme.slots(graphics, left, top, this.menu.slots);

        // Ghost overlays drawn strictly on empty slots so real and ghost items never double-draw.
        renderGhostOverlays(graphics, left, top);
    }

    /**
     * Draws dimmed recipe guides on empty 3x3 slots and a dimmed result preview on an empty output
     * slot. Whenever a slot holds a real item, its ghost is suppressed completely.
     */
    private void renderGhostOverlays(GuiGraphics graphics, int left, int top) {
        refreshSelectedRecipe();
        for (int index = 0; index < CrafterBlockEntity.INPUT_SLOTS; index++) {
            Slot slot = this.menu.slots.get(index);
            ItemStack ghost = ghostIngredientAt(index);
            if (CrafterLayout.shouldRenderGhost(slot.hasItem(), !ghost.isEmpty())) {
                drawGhostItem(graphics, ghost, left + slot.x, top + slot.y);
            }
        }
        Slot outputSlot = this.menu.slots.get(CrafterBlockEntity.OUTPUT_SLOT);
        ItemStack ghostOutput = currentGhostOutput();
        if (CrafterLayout.shouldRenderGhost(outputSlot.hasItem(), !ghostOutput.isEmpty())) {
            drawGhostItem(graphics, ghostOutput, left + outputSlot.x, top + outputSlot.y);
        }
    }

    private ItemStack ghostIngredientAt(int slotIndex) {
        Ingredient ingredient = this.cachedPattern[slotIndex];
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        int pick = items.length == 1 ? 0 : (int) ((Util.getMillis() / 1200L) % items.length);
        return items[pick];
    }

    private ItemStack currentGhostOutput() {
        return !this.previewResult.isEmpty() ? this.previewResult : this.cachedRecipeOutput;
    }

    private static void drawGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderFakeItem(stack, x, y);
        graphics.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, GHOST_VEIL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        CrafterStatus status = this.menu.getStatus();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        Component speed = Component.translatable("gui.industrialcore.crafter.speed",
                this.menu.getSpeedLevel(), this.menu.getMaxSpeedLevel());
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(status.translationKey()), statusColor(status),
                this.font.width(speed));
        MachineTheme.statusValue(this.font, graphics, 0, 0, speed, COLOR_MUTED);

        Component energyText = MachineTheme.fit(this.font, Component.translatable(
                        "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                        this.menu.getEnergyCapacity()),
                GuiMetrics.CONTENT);
        graphics.drawString(this.font, energyText,
                GuiMetrics.SIDE, CrafterLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);

        // Clip the item name with ellipsis so a long recipe result cannot run past the content width.
        Component recipeLine = MachineTheme.fit(this.font,
                Component.translatable("gui.industrialcore.crafter.recipe", recipeName()),
                GuiMetrics.CONTENT);
        graphics.drawString(this.font, recipeLine,
                GuiMetrics.SIDE, CrafterLayout.RECIPE_TEXT_Y, COLOR_TEXT, false);

        int total = this.menu.getCraftTime();
        int percent = total <= 0 ? 0 : Math.min(100, this.menu.getProgress() * 100 / total);
        Component progressText = Component.translatable("gui.industrialcore.machine.progress", percent);
        graphics.drawString(this.font, progressText,
                CrafterLayout.PROGRESS_TEXT_X - this.font.width(progressText) / 2,
                CrafterLayout.PROGRESS_TEXT_Y, COLOR_MUTED, false);

        // Centred right-column labels aligned directly on RIGHT_CENTER_X above their slots.
        drawCenteredFitted(graphics, Component.translatable("gui.industrialcore.crafter.output"),
                CrafterLayout.RIGHT_CENTER_X, CrafterLayout.OUTPUT_LABEL_Y,
                CrafterLayout.LABEL_MAX_WIDTH, COLOR_MUTED);
        drawCenteredFitted(graphics, Component.translatable("gui.industrialcore.crafter.upgrade"),
                CrafterLayout.RIGHT_CENTER_X, CrafterLayout.UPGRADE_LABEL_Y,
                CrafterLayout.LABEL_MAX_WIDTH, COLOR_MUTED);
        drawCenteredFitted(graphics, Component.translatable("gui.industrialcore.battery"),
                CrafterLayout.LEFT_CENTER_X, CrafterLayout.BATTERY_LABEL_Y,
                CrafterLayout.LABEL_MAX_WIDTH, COLOR_MUTED);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                CrafterLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawCenteredFitted(GuiGraphics graphics, Component label, int centerX, int y,
                                    int maxWidth, int color) {
        Component fitted = MachineTheme.fit(this.font, label, maxWidth);
        graphics.drawString(this.font, fitted, centerX - this.font.width(fitted) / 2, y, color, false);
    }

    /** Resolves the selected recipe's result name, output stack, and 3x3 guide once per selection. */
    private void refreshSelectedRecipe() {
        String id = this.menu.getSelectedRecipeId();
        if (id.equals(this.cachedRecipeId)) {
            return;
        }
        this.cachedRecipeId = id;
        this.cachedRecipeOutput = ItemStack.EMPTY;
        Arrays.fill(this.cachedPattern, Ingredient.EMPTY);
        if (id.isEmpty() || this.minecraft == null || this.minecraft.level == null) {
            this.cachedRecipeName = Component.translatable("gui.industrialcore.crafter.no_recipe");
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            this.cachedRecipeName = Component.translatable("gui.industrialcore.crafter.no_recipe");
            return;
        }
        try {
            Optional<RecipeHolder<?>> holder = this.minecraft.level.getRecipeManager().byKey(location);
            if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
                this.cachedRecipeName = Component.translatable("gui.industrialcore.crafter.recipe_missing_short");
                return;
            }
            ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess());
            this.cachedRecipeOutput = result.isEmpty() ? ItemStack.EMPTY : result.copy();
            this.cachedRecipeName = result.isEmpty()
                    ? Component.literal(location.toString())
                    : result.getHoverName();
            if (recipe instanceof ShapedRecipe) {
                Ingredient[] pattern = CrafterRecipeRules.gridIngredients(recipe);
                int count = Math.min(this.cachedPattern.length, pattern.length);
                for (int i = 0; i < count; i++) {
                    this.cachedPattern[i] = pattern[i] == null ? Ingredient.EMPTY : pattern[i];
                }
            } else {
                List<Ingredient> ingredients = CrafterRecipeRules.ingredients(recipe);
                int count = Math.min(this.cachedPattern.length, ingredients.size());
                for (int i = 0; i < count; i++) {
                    this.cachedPattern[i] = ingredients.get(i);
                }
            }
        } catch (RuntimeException exception) {
            // A third-party recipe must never crash the screen while it is being drawn.
            this.cachedRecipeName = Component.literal(location.toString());
        }
    }

    private Component recipeName() {
        refreshSelectedRecipe();
        return this.cachedRecipeName;
    }

    private static int statusColor(CrafterStatus status) {
        return switch (status) {
            case CRAFTING -> COLOR_PROGRESS;
            case NO_ENERGY -> MachineTheme.STATUS_BAD;
            case OUTPUT_FULL, MISSING_INGREDIENTS, RECIPE_MISSING -> COLOR_WARN;
            case NO_RECIPE, IDLE -> COLOR_MUTED;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePreview();
        refreshSelectedRecipe();
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (this.hoveredSlot != null && this.hoveredSlot.index == CrafterBlockEntity.UPGRADE_SLOT) {
            // Phase 82: the motor slot shows the resulting craft time and draw, not just its name.
            List<Component> lines = new ArrayList<>();
            if (this.hoveredSlot.hasItem()) {
                lines.add(this.hoveredSlot.getItem().getHoverName());
            }
            lines.add(Component.translatable("gui.industrialcore.tooltip.crafter_upgrade"));
            lines.add(Component.translatable("gui.industrialcore.tooltip.crafter_upgrade_value",
                    this.menu.getSpeedLevel(), this.menu.getMaxSpeedLevel(),
                    this.menu.getCraftTime(),
                    com.industrialcore.machine.UpgradeMath.discountedEnergy(
                            com.industrialcore.machine.CrafterSpeed.energyPerTick(
                                    com.industrialcore.Config.crafterEnergyPerTick(),
                                    this.menu.getSpeedLevel()),
                            this.menu.getBatteryCount()))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            ClientMachineProgress.appendUpgradeTooltip(lines, "crafter");
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (renderMachineUpgradeTooltips(graphics, mouseX, mouseY,
                null, CrafterLayout.BATTERY_SLOT,
                this.menu.getSpeedLevel(), this.menu.getBatteryCount(), false)) {
            return;
        } else if (this.hoveredSlot != null
                && this.hoveredSlot.index == CrafterBlockEntity.OUTPUT_SLOT
                && !this.hoveredSlot.hasItem()) {
            ItemStack ghost = currentGhostOutput();
            if (!ghost.isEmpty()) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.industrialcore.crafter.recipe", ghost.getHoverName()),
                        mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.industrialcore.crafter.no_grid_recipe"),
                        mouseX, mouseY);
            }
        }
    }

    @Override
    protected String skillTreeMachineId() {
        return "crafter";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
