package com.industrialcore.compat.jei;

import com.industrialcore.compat.jei.PowerGenerationCatalog.Entry;
import com.industrialcore.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Phase 105.5: Dedicated JEI Power Generation Catalog category rendered in IndustrialCore's
 * unified dark-slate + neon-cyan technical panel style with exact 0px slot alignment and an
 * animated virtual FE gauge bar.
 */
final class PowerGenerationJeiCategory implements IRecipeCategory<Entry> {
    private static final int BG_DARK = 0xFF0E1520;
    private static final int BG_INNER = 0xFF141E2E;
    private static final int BORDER_CYAN = 0xFF00E5FF;
    private static final int BORDER_DIM = 0xFF23384E;
    private static final int SLOT_BG = 0xFF090E16;
    private static final int SLOT_RIM = 0xFF2F4A66;
    private static final int SLOT_RIM_ACCENT = 0xFF00B8D4;

    private static final int COL_TITLE = 0xFF00E5FF;
    private static final int COL_RATE = 0xFF00E676;
    private static final int COL_RATE_MB = 0xFFFFB74D;
    private static final int COL_TEXT = 0xFFE0F2F1;
    private static final int COL_SUB = 0xFF90A4AE;
    private static final int COL_WARN = 0xFFFFD54F;

    private final RecipeType<Entry> recipeType;
    private final Component title;
    private final IDrawable icon;

    PowerGenerationJeiCategory(IGuiHelper gui, RecipeType<Entry> recipeType) {
        this.recipeType = recipeType;
        this.title = Component.translatable("jei.industrialcore.power_generation");
        this.icon = gui.createDrawableItemStack(new ItemStack(ModBlocks.NUCLEAR_GENERATOR.get()));
    }

    @Override
    public RecipeType<Entry> getRecipeType() {
        return this.recipeType;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public int getWidth() {
        return PowerGenerationCatalog.WIDTH;
    }

    @Override
    public int getHeight() {
        return PowerGenerationCatalog.HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry recipe, IFocusGroup focuses) {
        Item genItem = resolveItem(recipe.generatorId());
        if (genItem != Items.AIR) {
            builder.addSlot(RecipeIngredientRole.CATALYST,
                            PowerGenerationCatalog.GEN_SLOT_X, PowerGenerationCatalog.GEN_SLOT_Y)
                    .addItemStack(new ItemStack(genItem));
        }
        if (!recipe.fuelLess() && recipe.fuelItemId() != null && !recipe.fuelItemId().isEmpty()) {
            Item fuelItem = resolveItem(recipe.fuelItemId());
            if (fuelItem != Items.AIR) {
                builder.addSlot(RecipeIngredientRole.INPUT,
                                PowerGenerationCatalog.FUEL_SLOT_X, PowerGenerationCatalog.FUEL_SLOT_Y)
                        .addItemStack(new ItemStack(fuelItem));
            }
        }
    }

    @Override
    public void draw(Entry recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        int w = getWidth();
        int h = getHeight();
        Font font = Minecraft.getInstance().font;

        // 1. Unified dark-slate industrial panel + neon cyan technical border
        drawIndustrialPanel(g, w, h);

        // 2. Top header bar: generator name (left) + output rate badge (right)
        String rateStr = "+" + formatCompact(recipe.fePerTick()) + " FE/t";
        int rateW = font.width(rateStr);
        int maxTitleW = w - 16 - rateW - 6;
        String genName = trimToWidth(font, Component.translatable(recipe.generatorNameKey()).getString(), maxTitleW);
        g.drawString(font, genName, 8, 6, COL_TITLE, false);
        g.drawString(font, rateStr, w - 8 - rateW, 6, recipe.multiblock() ? COL_RATE_MB : COL_RATE, false);
        g.fill(6, 18, w - 6, 19, BORDER_DIM);

        // 3. Generator slot socket (exact 0px alignment: 18x18 frame at x-1, y-1)
        drawSlotFrame(g, PowerGenerationCatalog.GEN_SLOT_X - 1, PowerGenerationCatalog.GEN_SLOT_Y - 1, true);

        // Plus sign between generator & fuel
        g.drawString(font, "+", 31, PowerGenerationCatalog.GEN_SLOT_Y + 4, COL_SUB, false);

        // 4. Fuel slot socket OR crisp "No Fuel" badge
        if (!recipe.fuelLess()) {
            drawSlotFrame(g, PowerGenerationCatalog.FUEL_SLOT_X - 1, PowerGenerationCatalog.FUEL_SLOT_Y - 1, false);
        } else {
            int bx = PowerGenerationCatalog.FUEL_SLOT_X - 3;
            int by = PowerGenerationCatalog.FUEL_SLOT_Y + 1;
            int bw = 24;
            int bh = 14;
            g.fill(bx, by, bx + bw, by + bh, SLOT_BG);
            drawOutline(g, bx, by, bw, bh, 0xFF00897B);
            String nf = Component.translatable("jei.industrialcore.gen.no_fuel_short").getString();
            int nfw = font.width(nf);
            g.drawString(font, nf, bx + (bw - nfw) / 2, by + 3, 0xFF4DB6AC, false);
        }

        // 5. Animated energy flow arrow
        float anim = ((Util.getMillis() % 1800L) / 1800.0F);
        drawEnergyArrow(g, PowerGenerationCatalog.ARROW_X, PowerGenerationCatalog.ARROW_Y,
                PowerGenerationCatalog.ARROW_W, PowerGenerationCatalog.ARROW_H, anim);

        // 6. Virtual FE Energy Gauge Bar (Visual FE energy output bar)
        drawFeGauge(g, font, PowerGenerationCatalog.GAUGE_X, PowerGenerationCatalog.GAUGE_Y,
                PowerGenerationCatalog.GAUGE_W, PowerGenerationCatalog.GAUGE_H, anim, recipe.multiblock());

        // 7. Divider + clean two-line telemetry footer (zero slot overlap!)
        g.fill(6, 45, w - 6, 46, BORDER_DIM);
        if (!recipe.fuelLess()) {
            String sec = String.format(Locale.ROOT, "%.1f", recipe.burnSeconds());
            String line1 = Component.translatable("jei.industrialcore.gen.burn_and_total",
                    formatInt(recipe.burnTicks()), sec, formatCompact(recipe.totalFe())).getString();
            g.drawString(font, trimToWidth(font, line1, w - 16), 8, PowerGenerationCatalog.FOOTER_Y1, COL_TEXT, false);
        } else {
            String line1 = Component.translatable("jei.industrialcore.gen.continuous").getString();
            g.drawString(font, trimToWidth(font, line1, w - 16), 8, PowerGenerationCatalog.FOOTER_Y1, COL_TEXT, false);
        }

        String line2 = Component.translatable(recipe.conditionKey()).getString();
        g.drawString(font, trimToWidth(font, line2, w - 16), 8, PowerGenerationCatalog.FOOTER_Y2,
                recipe.multiblock() ? COL_WARN : COL_SUB, false);
    }

    static void drawIndustrialPanel(GuiGraphics g, int w, int h) {
        g.fill(0, 0, w, h, BG_DARK);
        g.fill(2, 2, w - 2, h - 2, BG_INNER);
        drawOutline(g, 0, 0, w, h, BORDER_DIM);
        drawOutline(g, 1, 1, w - 2, h - 2, 0xFF0A3A4A);
        // Bright cyan corner tech accents
        g.fill(0, 0, 10, 1, BORDER_CYAN);
        g.fill(0, 0, 1, 10, BORDER_CYAN);
        g.fill(w - 10, 0, w, 1, BORDER_CYAN);
        g.fill(w - 1, 0, w, 10, BORDER_CYAN);
        g.fill(0, h - 1, 10, h, BORDER_CYAN);
        g.fill(0, h - 10, 1, h, BORDER_CYAN);
        g.fill(w - 10, h - 1, w, h, BORDER_CYAN);
        g.fill(w - 1, h - 10, w, h, BORDER_CYAN);
    }

    static void drawSlotFrame(GuiGraphics g, int x, int y, boolean accent) {
        g.fill(x, y, x + 18, y + 18, SLOT_BG);
        drawOutline(g, x, y, 18, 18, accent ? SLOT_RIM_ACCENT : SLOT_RIM);
        g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF060A10);
    }

    static void drawEnergyArrow(GuiGraphics g, int x, int y, int w, int h, float progress) {
        int midY = y + h / 2;
        int shaftEnd = x + w - 6;
        // Track
        g.fill(x, midY - 2, shaftEnd, midY + 2, 0xFF1E3246);
        // Animated glowing fill
        int litW = Math.max(2, Math.round((shaftEnd - x) * progress));
        g.fill(x, midY - 1, x + litW, midY + 1, BORDER_CYAN);
        // Crisp triangular arrowhead
        for (int i = 0; i < 6; i++) {
            int half = Math.max(0, 5 - i);
            int col = progress > 0.72F ? 0xFF00E5FF : 0xFF2B607A;
            g.fill(shaftEnd + i, midY - half, shaftEnd + i + 1, midY + half + 1, col);
        }
    }

    private static void drawFeGauge(GuiGraphics g, Font font, int x, int y, int w, int h, float anim, boolean mb) {
        g.fill(x, y, x + w, y + h, SLOT_BG);
        drawOutline(g, x, y, w, h, mb ? 0xFFFFA000 : BORDER_CYAN);

        // Mini virtual energy bolt icon on left of gauge
        int ix = x + 4;
        int iy = y + 3;
        int boltCol = mb ? 0xFFFFD54F : 0xFF00E5FF;
        g.fill(ix + 3, iy, ix + 6, iy + 2, boltCol);
        g.fill(ix + 2, iy + 2, ix + 5, iy + 5, boltCol);
        g.fill(ix + 1, iy + 5, ix + 6, iy + 7, 0xFFFFFFFF);
        g.fill(ix + 3, iy + 7, ix + 6, iy + 10, boltCol);
        g.fill(ix + 2, iy + 10, ix + 4, iy + 12, boltCol);

        // Segmented glowing energy bar
        int barX = x + 13;
        int barW = w - 34;
        int barY = y + 5;
        int barH = 8;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF122233);
        int fillW = Math.max(4, Math.round(barW * (0.45F + 0.55F * anim)));
        int fillCol = mb ? 0xFFFFA000 : 0xFF00E676;
        g.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, fillCol);
        // Segment notches
        for (int sx = barX + 6; sx < barX + barW - 2; sx += 6) {
            g.fill(sx, barY, sx + 1, barY + barH, SLOT_BG);
        }

        // Crisp "FE" tag
        g.drawString(font, "FE", x + w - 16, y + 5, mb ? 0xFFFFD54F : 0xFF80DEEA, false);
    }

    static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static String trimToWidth(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxW - font.width("…"))) + "…";
    }

    private static Item resolveItem(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static String formatInt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static String formatCompact(long v) {
        if (v >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fG", v / 1_000_000_000.0D);
        }
        if (v >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0D);
        }
        if (v >= 100_000L) {
            return String.format(Locale.ROOT, "%.0fK", v / 1_000.0D);
        }
        return String.format(Locale.ROOT, "%,d", v);
    }
}
