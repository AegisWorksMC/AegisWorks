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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven alloy recipe ({@code industrialcore:alloying}) of the Phase 17 alloy furnace.
 *
 * <p>Two ingredients and one result. The furnace is fuelled like a vanilla furnace, so a recipe
 * carries no FE and only a duration in ticks. The two ingredients are matched in either slot, so a
 * player or a pipe cannot break a recipe by filling the slots "in the wrong order".</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/alloy_steel.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:alloying",
 *   "first": { "item": "minecraft:iron_ingot" },
 *   "second": { "tag": "minecraft:coals" },
 *   "result": { "id": "industrialcore:steel_ingot", "count": 1 },
 *   "time": 200
 * }
 * }</pre>
 */
public record AlloyRecipe(Ingredient first, Ingredient second, ItemStack result, int timeTicks)
        implements Recipe<AlloyRecipe.Input> {

    /** The two ingredient slots of the alloy furnace. */
    public record Input(ItemStack first, ItemStack second) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> this.first;
                case 1 -> this.second;
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static MapCodec<AlloyRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("first").forGetter(AlloyRecipe::first),
                        Ingredient.CODEC_NONEMPTY.fieldOf("second").forGetter(AlloyRecipe::second),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
                        Codec.INT.optionalFieldOf("time", AlloyBalance.TIME_TICKS)
                                .forGetter(AlloyRecipe::timeTicks))
                .apply(instance, AlloyRecipe::new));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() {
        return StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, AlloyRecipe::first,
                Ingredient.CONTENTS_STREAM_CODEC, AlloyRecipe::second,
                ItemStack.STREAM_CODEC, AlloyRecipe::result,
                ByteBufCodecs.VAR_INT, AlloyRecipe::timeTicks,
                AlloyRecipe::new);
    }

    public AlloyRecipe {
        timeTicks = Math.max(1, timeTicks);
    }

    /** True when the two stacks match this recipe in either order (one item each). */
    public boolean matchesStacks(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return (this.first.test(a) && this.second.test(b))
                || (this.first.test(b) && this.second.test(a));
    }

    /** True when the item is one of the two ingredients (used by the slot filters). */
    public boolean isFirst(ItemStack stack) {
        return !stack.isEmpty() && this.first.test(stack);
    }

    /** True when the item is the second ingredient (used by the slot filters). */
    public boolean isSecond(ItemStack stack) {
        return !stack.isEmpty() && this.second.test(stack);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return matchesStacks(input.first(), input.second());
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
        list.add(this.first);
        list.add(this.second);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ALLOYING_TYPE.get();
    }

    /** Finds the recipe of the two stacks in a recipe list; {@code null} when nothing matches. */
    public static AlloyRecipe find(List<AlloyRecipe> recipes, ItemStack a, ItemStack b) {
        for (AlloyRecipe recipe : recipes) {
            if (recipe.matchesStacks(a, b)) {
                return recipe;
            }
        }
        return null;
    }

    /** A copy of the list, used when the recipe manager is swapped (datapack reload). */
    public static List<AlloyRecipe> copyOf(Iterable<AlloyRecipe> recipes) {
        List<AlloyRecipe> copy = new ArrayList<>();
        for (AlloyRecipe recipe : recipes) {
            copy.add(recipe);
        }
        return List.copyOf(copy);
    }

    /** Serializer of the alloying recipe type. */
    public static final class Serializer implements RecipeSerializer<AlloyRecipe> {
        @Override
        public MapCodec<AlloyRecipe> codec() {
            return AlloyRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() {
            return AlloyRecipe.streamCodec();
        }
    }
}
