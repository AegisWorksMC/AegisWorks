package com.industrialcore.machine;

import com.industrialcore.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Data-driven assembly recipe ({@code industrialcore:assembling}) of the Phase 18 assembler.
 *
 * <p>An assembly recipe has three ingredients and one result. Unlike the single-input processors
 * the ingredient order does not matter at all: three interchangeable slots are matched against the
 * list, so a pipe or a player cannot break a recipe by filling the slots "in the wrong order".</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/assemble_electronic_component.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:assembling",
 *   "ingredients": [
 *     { "item": "industrialcore:copper_plate" },
 *     { "item": "industrialcore:gold_plate" },
 *     { "item": "minecraft:redstone" }
 *   ],
 *   "result": { "id": "industrialcore:electronic_component", "count": 1 },
 *   "time": 120,
 *   "energy": 10
 * }
 * }</pre>
 */
public record AssemblerRecipe(List<Ingredient> ingredients, ItemStack result, int timeTicks,
                              int energyPerTick) implements Recipe<AssemblerRecipe.Input> {

    /** The three ingredient slots of the assembler. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index >= 0 && index < this.stacks.size() ? this.stacks.get(index) : ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return this.stacks.size();
        }
    }

    public static MapCodec<AssemblerRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RecipeLimits.ingredientList("assembling", Ingredient.CODEC_NONEMPTY)
                                .fieldOf("ingredients")
                                .forGetter(AssemblerRecipe::ingredients),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AssemblerRecipe::result),
                        Codec.INT.optionalFieldOf("time", AssemblerBalance.TIME_TICKS)
                                .forGetter(AssemblerRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy", AssemblerBalance.ENERGY_PER_TICK)
                                .forGetter(AssemblerRecipe::energyPerTick))
                .apply(instance, AssemblerRecipe::new));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, AssemblerRecipe> streamCodec() {
        return StreamCodec.composite(
                // The same bound as the JSON codec above: a recipe that loaded can always be sent.
                Ingredient.CONTENTS_STREAM_CODEC.apply(
                        ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)),
                AssemblerRecipe::ingredients,
                ItemStack.STREAM_CODEC, AssemblerRecipe::result,
                ByteBufCodecs.VAR_INT, AssemblerRecipe::timeTicks,
                ByteBufCodecs.VAR_INT, AssemblerRecipe::energyPerTick,
                AssemblerRecipe::new);
    }

    public AssemblerRecipe {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    /**
     * True when the given stacks (one per recipe ingredient) match this recipe in any order.
     *
     * <p>The list must have exactly as many non-empty stacks as the recipe has ingredients. The
     * matching is a tiny backtracking search (at most 3! = 6 branches), not a repeated full scan of
     * every permutation of the slot list.</p>
     */
    public boolean matchesStacks(List<ItemStack> stacks) {
        if (stacks.size() != this.ingredients.size() || this.ingredients.isEmpty()) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return matchFrom(0, stacks, new boolean[stacks.size()]);
    }

    private boolean matchFrom(int ingredientIndex, List<ItemStack> stacks, boolean[] used) {
        if (ingredientIndex >= this.ingredients.size()) {
            return true;
        }
        Ingredient ingredient = this.ingredients.get(ingredientIndex);
        for (int index = 0; index < stacks.size(); index++) {
            if (used[index] || !ingredient.test(stacks.get(index))) {
                continue;
            }
            used[index] = true;
            if (matchFrom(ingredientIndex + 1, stacks, used)) {
                return true;
            }
            used[index] = false;
        }
        return false;
    }

    /** True when the item is one of the ingredients (used by the slot filters). */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Ingredient ingredient : this.ingredients) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return matchesStacks(input.stacks());
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ASSEMBLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ASSEMBLING_TYPE.get();
    }

    /** Finds the recipe of the three stacks; {@code null} when nothing matches. */
    public static AssemblerRecipe find(List<AssemblerRecipe> recipes, List<ItemStack> stacks) {
        for (AssemblerRecipe recipe : recipes) {
            if (recipe.matchesStacks(stacks)) {
                return recipe;
            }
        }
        return null;
    }

    /** True when any recipe accepts the item (used by the slot filters). */
    public static boolean anyRecipeAccepts(List<AssemblerRecipe> recipes, ItemStack stack) {
        for (AssemblerRecipe recipe : recipes) {
            if (recipe.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Serializer of the assembling recipe type. */
    public static final class Serializer implements RecipeSerializer<AssemblerRecipe> {
        @Override
        public MapCodec<AssemblerRecipe> codec() {
            return AssemblerRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AssemblerRecipe> streamCodec() {
            return AssemblerRecipe.streamCodec();
        }
    }
}
