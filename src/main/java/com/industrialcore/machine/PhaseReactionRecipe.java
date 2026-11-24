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
 * Data-driven phase reaction ({@code industrialcore:phase_reaction}) of the Phase 24 reactor.
 *
 * <p>Three ingredients, one result. The ingredient order does not matter, and a repeated item is
 * allowed: listing the same ingredient twice means "two of them", exactly the assembler's rule.
 * The machine consumes one item from each of its three slots when the run finishes, so a recipe
 * can never consume less than it lists.</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/phase_catalyst.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:phase_reaction",
 *   "ingredients": [
 *     { "item": "industrialcore:nebula_crystal" },
 *     { "item": "industrialcore:nebula_crystal" },
 *     { "item": "industrialcore:orichalcum_ingot" }
 *   ],
 *   "result": { "id": "industrialcore:resonant_catalyst", "count": 1 },
 *   "time": 200,
 *   "energy": 20
 * }
 * }</pre>
 */
public record PhaseReactionRecipe(List<Ingredient> ingredients, ItemStack result, int timeTicks,
                                  int energyPerTick) implements Recipe<PhaseReactionRecipe.Input> {

    /** The three ingredient slots of the reactor. */
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

    public static MapCodec<PhaseReactionRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RecipeLimits.ingredientList("phase_reaction", Ingredient.CODEC_NONEMPTY)
                                .fieldOf("ingredients")
                                .forGetter(PhaseReactionRecipe::ingredients),
                        ItemStack.STRICT_CODEC.fieldOf("result")
                                .forGetter(PhaseReactionRecipe::result),
                        Codec.INT.optionalFieldOf("time", PhaseReactorBalance.CATALYST_TIME_TICKS)
                                .forGetter(PhaseReactionRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy",
                                        PhaseReactorBalance.CATALYST_ENERGY_PER_TICK)
                                .forGetter(PhaseReactionRecipe::energyPerTick))
                .apply(instance, PhaseReactionRecipe::new));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, PhaseReactionRecipe> streamCodec() {
        return StreamCodec.composite(
                // The same bound as the JSON codec above: a recipe that loaded can always be sent.
                Ingredient.CONTENTS_STREAM_CODEC.apply(
                        ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)),
                PhaseReactionRecipe::ingredients,
                ItemStack.STREAM_CODEC, PhaseReactionRecipe::result,
                ByteBufCodecs.VAR_INT, PhaseReactionRecipe::timeTicks,
                ByteBufCodecs.VAR_INT, PhaseReactionRecipe::energyPerTick,
                PhaseReactionRecipe::new);
    }

    public PhaseReactionRecipe {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    /**
     * True when the given stacks (one per ingredient slot) match this recipe in any order.
     *
     * <p>The list must have exactly as many non-empty stacks as the recipe has ingredients. The
     * relation is evaluated through {@link Ingredient#test(ItemStack)}, so item tags in a
     * datapack recipe keep working.</p>
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
        List<Ingredient> list = this.ingredients;
        return PhaseReactionRules.match(this.ingredients.size(), stacks.size(),
                (ingredientIndex, stackIndex) -> list.get(ingredientIndex).test(stacks.get(stackIndex)));
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
        return ModRecipes.PHASE_REACTION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.PHASE_REACTION_TYPE.get();
    }

    /** Finds the recipe of the three stacks; {@code null} when nothing matches. */
    public static PhaseReactionRecipe find(List<PhaseReactionRecipe> recipes, List<ItemStack> stacks) {
        for (PhaseReactionRecipe recipe : recipes) {
            if (recipe.matchesStacks(stacks)) {
                return recipe;
            }
        }
        return null;
    }

    /** True when any recipe accepts the item (used by the slot filters). */
    public static boolean anyRecipeAccepts(List<PhaseReactionRecipe> recipes, ItemStack stack) {
        for (PhaseReactionRecipe recipe : recipes) {
            if (recipe.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Serializer of the phase reaction recipe type. */
    public static final class Serializer implements RecipeSerializer<PhaseReactionRecipe> {
        @Override
        public MapCodec<PhaseReactionRecipe> codec() {
            return PhaseReactionRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PhaseReactionRecipe> streamCodec() {
            return PhaseReactionRecipe.streamCodec();
        }
    }
}
