package com.industrialcore.machine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A validated "this exact craft can happen" plan.
 *
 * <p>The machine stores ingredients in nine flat input slots. Phase 81 tightened the mapping: a
 * shaped recipe may only be crafted when every pattern cell that needs an ingredient has that
 * ingredient <em>in the matching slot</em>. The old plan picked any matching slot in the machine
 * (a bipartite matching), so a grid whose shape was broken still crafted and the ingredients were
 * taken from seemingly random slots. That is gone: the pattern decides which slot is consumed.</p>
 *
 * <p>Shapeless recipes have no shape to preserve, so their ingredients are matched greedily against
 * distinct slots; that keeps them usable in any arrangement while still consuming one item per
 * ingredient exactly once.</p>
 *
 * <p>The plan is recomputed on completion. If the inventory changed in the meantime, nothing is
 * consumed and the craft does not happen, which prevents item loss and duplication.</p>
 */
public final class CrafterRecipePlan {
    /** Output slot index in the crafter's ten-slot inventory. */
    public static final int OUTPUT_SLOT = 9;

    private final int[] sourceSlots;
    private final ItemStack result;

    private CrafterRecipePlan(int[] sourceSlots, ItemStack result) {
        this.sourceSlots = sourceSlots;
        this.result = result;
    }

    public ItemStack result() {
        return this.result;
    }

    public int ingredientCount() {
        return this.sourceSlots.length;
    }

    /** Slots this plan consumes from, in pattern order. */
    public int[] sourceSlots() {
        return this.sourceSlots.clone();
    }

    /** Consumes exactly one item from each mapped slot. */
    public void consume(ItemStackHandler inventory) {
        for (int slot : this.sourceSlots) {
            inventory.extractItem(slot, 1, false);
        }
    }

    /**
     * Builds a plan from the nine input stacks, or returns {@code null} when the recipe cannot be
     * matched in the exact arrangement, the virtual grid does not validate, or the recipe would
     * leave remainders.
     */
    @Nullable
    public static CrafterRecipePlan create(CraftingRecipe recipe, List<ItemStack> inputSlots, Level level) {
        if (inputSlots.size() < 9) {
            return null;
        }
        List<Ingredient> ingredients = CrafterRecipeRules.ingredients(recipe);
        int ingredientCount = ingredients.size();
        if (ingredientCount == 0 || ingredientCount > 9) {
            return null;
        }

        List<ItemStack> grid = new ArrayList<>(9);
        for (int index = 0; index < 9; index++) {
            grid.add(ItemStack.EMPTY);
        }
        int[] sourceSlots = new int[ingredientCount];
        int mapped = 0;

        if (recipe instanceof ShapedRecipe) {
            // Strict shape: the pattern cell and the machine slot are the same cell.
            Ingredient[] pattern = CrafterRecipeRules.gridIngredients(recipe);
            for (int slot = 0; slot < 9; slot++) {
                Ingredient ingredient = pattern[slot];
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                if (mapped >= ingredientCount) {
                    return null;
                }
                ItemStack stack = inputSlots.get(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    // The shape is not arranged: refuse instead of taking the item from elsewhere.
                    return null;
                }
                grid.set(slot, stack.copyWithCount(1));
                sourceSlots[mapped++] = slot;
            }
        } else {
            boolean[] used = new boolean[9];
            for (Ingredient ingredient : ingredients) {
                int found = -1;
                for (int slot = 0; slot < 9; slot++) {
                    if (used[slot]) {
                        continue;
                    }
                    ItemStack stack = inputSlots.get(slot);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        found = slot;
                        break;
                    }
                }
                if (found < 0) {
                    return null;
                }
                used[found] = true;
                grid.set(found, inputSlots.get(found).copyWithCount(1));
                sourceSlots[mapped++] = found;
            }
        }
        if (mapped != ingredientCount) {
            return null;
        }

        CraftingInput input = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(input, level)) {
            return null;
        }
        for (ItemStack remainder : recipe.getRemainingItems(input)) {
            if (!remainder.isEmpty()) {
                return null;
            }
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            result = recipe.getResultItem(level.registryAccess()).copy();
        }
        if (result.isEmpty()) {
            return null;
        }
        return new CrafterRecipePlan(sourceSlots, result.copy());
    }

    /** True when the result fits in the output slot (empty or the same item with room). */
    public static boolean canAccept(ItemStackHandler inventory, ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return result.getCount() <= Math.min(64, result.getMaxStackSize());
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= Math.min(64, output.getMaxStackSize());
    }

    /** Inserts a previously checked result. Callers must call {@link #canAccept} first. */
    public static void insertResult(ItemStackHandler inventory, ItemStack result) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
            return;
        }
        output.grow(result.getCount());
        inventory.setStackInSlot(OUTPUT_SLOT, output);
    }
}
