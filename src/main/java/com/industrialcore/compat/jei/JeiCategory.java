package com.industrialcore.compat.jei;

import com.industrialcore.compat.jei.JeiCoverage.Layout;
import com.industrialcore.compat.jei.MachineRecipeViews.Output;
import com.industrialcore.compat.jei.MachineRecipeViews.Slot;
import com.industrialcore.compat.jei.MachineRecipeViews.View;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 105.5: Unified dark-slate + neon-cyan JEI machine recipe category with exact 0px slot
 * alignment ({@code 18x18} recessed sockets directly under every item icon), animated cyan
 * energy arrow, and crisp non-overlapping duration / FE telemetry.
 */
final class JeiCategory<T> implements IRecipeCategory<T> {
    private static final int DIVIDER = 0xFF23384E;
    private static final int COL_TIME = 0xFFB0BEC5;
    private static final int COL_RATE = 0xFF00E5FF;
    private static final int COL_TOTAL = 0xFFFFB74D;

    private final RecipeType<T> recipeType;
    private final Component title;
    private final IDrawable icon;
    private final Layout layout;
    private final View<T> view;

    JeiCategory(IGuiHelper gui, JeiCoverage.Category spec, RecipeType<T> recipeType, View<T> view) {
        this.recipeType = recipeType;
        this.title = Component.translatable(spec.titleKey());
        this.icon = gui.createDrawableItemStack(item(spec.iconItem()));
        this.layout = spec.layout();
        this.view = view;
    }

    @Override
    public RecipeType<T> getRecipeType() {
        return this.recipeType;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public int getWidth() {
        return this.layout.width;
    }

    @Override
    public int getHeight() {
        return this.layout.height;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        List<Slot> inputs = this.view.inputs(recipe);
        for (int index = 0; index < inputs.size(); index++) {
            int column = index % this.layout.columns;
            int row = index / this.layout.columns;
            if (row >= this.layout.maxRows) {
                break;
            }
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT,
                    this.layout.inputX(column), this.layout.inputY(row));
            slot.addItemStacks(amounts(inputs.get(index)));
        }

        List<Output> outputs = this.view.outputs(recipe);
        int outputX = this.layout.outputX(usedColumns(inputs.size()));
        int outputY = this.layout.outputY();
        for (int index = 0; index < outputs.size() && index < this.layout.maxOutputs; index++) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT,
                    outputX + index * JeiCoverage.SLOT_STEP, outputY);
            slot.addItemStack(drawn(outputs.get(index)));
        }
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        int w = getWidth();
        int h = getHeight();
        Font font = Minecraft.getInstance().font;

        // 1. Unified dark-slate + neon-cyan panel background
        PowerGenerationJeiCategory.drawIndustrialPanel(g, w, h);

        // 2. Exact 0px-aligned 18x18 slot sockets for every active input & output slot
        List<Slot> inputs = this.view.inputs(recipe);
        int usedCols = usedColumns(inputs.size());
        int shownInputs = Math.min(inputs.size(), this.layout.columns * this.layout.maxRows);
        for (int index = 0; index < shownInputs; index++) {
            int col = index % this.layout.columns;
            int row = index / this.layout.columns;
            PowerGenerationJeiCategory.drawSlotFrame(g,
                    this.layout.inputX(col) - 1, this.layout.inputY(row) - 1, false);
        }

        List<Output> outputs = this.view.outputs(recipe);
        int outputX = this.layout.outputX(usedCols);
        int outputY = this.layout.outputY();
        int shownOutputs = Math.min(outputs.size(), this.layout.maxOutputs);
        for (int index = 0; index < shownOutputs; index++) {
            PowerGenerationJeiCategory.drawSlotFrame(g,
                    outputX + index * JeiCoverage.SLOT_STEP - 1, outputY - 1, true);
        }

        // 3. Animated glowing cyan progress arrow
        float anim = ((Util.getMillis() % 1800L) / 1800.0F);
        PowerGenerationJeiCategory.drawEnergyArrow(g,
                this.layout.arrowX(usedCols), this.layout.arrowY(),
                JeiCoverage.ARROW_WIDTH, JeiCoverage.ARROW_HEIGHT, anim);

        // 4. Crisp divider line + non-overlapping telemetry footer
        int top = this.layout.textTop();
        g.fill(6, top - 4, w - 6, top - 3, DIVIDER);

        int timeTicks = Math.max(1, this.view.timeTicks(recipe));
        String seconds = String.format(Locale.ROOT, "%.1f", timeTicks / 20.0D);
        String timeLine = Component.translatable("jei.industrialcore.time",
                format(timeTicks), seconds).getString();
        g.drawString(font, PowerGenerationJeiCategory.trimToWidth(font, timeLine, w - 16),
                JeiCoverage.PAD_X, top, COL_TIME, false);

        int energyPerTick = this.view.energyPerTick(recipe);
        if (energyPerTick > 0) {
            long total = (long) timeTicks * energyPerTick;
            String rateLine = Component.translatable("jei.industrialcore.energy_per_tick",
                    format(energyPerTick)).getString();
            String totalLine = Component.translatable("jei.industrialcore.energy_total",
                    format(total)).getString();
            g.drawString(font, PowerGenerationJeiCategory.trimToWidth(font, rateLine, w - 16),
                    JeiCoverage.PAD_X, top + 10, COL_RATE, false);
            g.drawString(font, PowerGenerationJeiCategory.trimToWidth(font, totalLine, w - 16),
                    JeiCoverage.PAD_X, top + 20, COL_TOTAL, false);
        } else {
            String fuelHint = Component.translatable("jei.industrialcore.fuel_powered").getString();
            g.drawString(font, PowerGenerationJeiCategory.trimToWidth(font, fuelHint, w - 16),
                    JeiCoverage.PAD_X, top + 10, COL_TOTAL, false);
        }
    }

    /** How many of the ingredient columns the recipe really fills, for the arrow position. */
    private int usedColumns(int ingredientCount) {
        return Math.max(1, Math.min(ingredientCount, this.layout.columns));
    }

    private static List<ItemStack> amounts(Slot input) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : input.ingredient().getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.setCount(Math.min(input.count(), Math.max(1, copy.getMaxStackSize())));
            stacks.add(copy);
        }
        return stacks;
    }

    private static ItemStack drawn(Output output) {
        int max = Math.max(1, output.stack().getMaxStackSize());
        if (output.count() <= Math.min(64, max)) {
            return output.stack().copyWithCount(output.count());
        }
        return output.stack().copyWithCount(1);
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
