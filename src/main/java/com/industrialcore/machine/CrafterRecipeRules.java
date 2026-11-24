package com.industrialcore.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which vanilla crafting recipes the auto crafter can execute safely.
 *
 * <p>The machine consumes one item per ingredient and produces the recipe result. Recipes whose
 * ingredients would leave a container item (milk bucket, honey bottle, ...) or that would consume a
 * damaged tool are deliberately not accepted: the machine has no way to put those remainders back
 * without risking item loss. Special recipes (fireworks, maps, ...) are excluded for the same
 * reason. Everything else uses the normal ingredient/result API and is supported for shaped and
 * shapeless recipes.</p>
 *
 * <p>These checks are pure metadata reads. They are done when a recipe is selected and when the
 * client builds its recipe list, never once per tick.</p>
 */
public final class CrafterRecipeRules {
    /** The machine has a 3x3 input area. */
    public static final int GRID_SIZE = 3;

    private CrafterRecipeRules() {}

    /** Non-empty ingredients of the recipe, in pattern order for shaped recipes. */
    public static List<Ingredient> ingredients(CraftingRecipe recipe) {
        List<Ingredient> result = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty() || ingredient.hasNoItems()) {
                continue;
            }
            result.add(ingredient);
        }
        return result;
    }

    /**
     * Ingredient of each of the nine crafter input slots, or {@code null} where the recipe leaves
     * the slot empty. Shaped recipes keep their real pattern position, so the GUI grid and the
     * balanced distribution agree on "which ingredient belongs in this slot"; shapeless recipes
     * fill the slots in order.
     */
    public static Ingredient[] gridIngredients(CraftingRecipe recipe) {
        Ingredient[] grid = new Ingredient[GRID_SIZE * GRID_SIZE];
        if (recipe == null) {
            return grid;
        }
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            int width = Math.max(1, shaped.getWidth());
            List<Ingredient> pattern = recipe.getIngredients();
            for (int patternIndex = 0; patternIndex < pattern.size(); patternIndex++) {
                Ingredient ingredient = pattern.get(patternIndex);
                if (ingredient == null || ingredient.isEmpty() || ingredient.hasNoItems()) {
                    continue;
                }
                int column = patternIndex % width;
                int row = patternIndex / width;
                if (column >= GRID_SIZE || row >= GRID_SIZE) {
                    continue;
                }
                grid[column + row * GRID_SIZE] = ingredient;
            }
            return grid;
        }
        int index = 0;
        for (Ingredient ingredient : ingredients(recipe)) {
            if (index >= grid.length) {
                break;
            }
            grid[index++] = ingredient;
        }
        return grid;
    }

    /**
     * Finds the crafting recipe that matches an exactly arranged 3x3 grid (Phase 11.2).
     *
     * <p>Only supported recipes are considered, and ties are resolved by recipe id so a data pack
     * reload cannot change which recipe gets registered for the same grid.</p>
     */
    public static Optional<RecipeHolder<CraftingRecipe>> findMatchingRecipe(
            RecipeManager manager, CraftingInput input, Level level) {
        if (manager == null || input == null || input.isEmpty()) {
            return Optional.empty();
        }
        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            try {
                if (recipe.matches(input, level) && isSupported(recipe, level.registryAccess())) {
                    matches.add(holder);
                }
            } catch (RuntimeException exception) {
                // A broken third-party recipe must not stop the search.
            }
        }
        matches.sort(Comparator.comparing(holder -> {
            ResourceLocation id = holder.id();
            return id == null ? "" : id.toString();
        }));
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * True when this recipe can be crafted without remainder or tool-damage handling.
     *
     * <p>The check is intentionally conservative. A data pack or mod recipe that uses a custom
     * ingredient which throws while enumerating its items is rejected instead of crashing the
     * machine.</p>
     */
    public static boolean isSupported(CraftingRecipe recipe, HolderLookup.Provider registries) {
        try {
            if (recipe.isSpecial() || recipe.isIncomplete()) {
                return false;
            }
            if (!recipe.canCraftInDimensions(GRID_SIZE, GRID_SIZE)) {
                return false;
            }
            List<Ingredient> ingredients = ingredients(recipe);
            if (ingredients.isEmpty() || ingredients.size() > GRID_SIZE * GRID_SIZE) {
                return false;
            }
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty() || result.getCount() <= 0 || result.getCount() > 64) {
                return false;
            }
            for (Ingredient ingredient : ingredients) {
                ItemStack[] items = ingredient.getItems();
                if (items.length == 0) {
                    return false;
                }
                for (ItemStack item : items) {
                    if (item.isEmpty() || item.hasCraftingRemainingItem() || item.isDamageableItem()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
