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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Data-driven crushing recipe ({@code industrialcore:crushing}).
 *
 * <p>A recipe file only needs the ingredient, the result and optionally the time and the FE per
 * tick; omitted values fall back to the configured defaults. Using a real recipe type keeps the
 * machine compatible with data packs and recipe reloads, exactly like the auto crafter validates
 * its recipe id against the recipe manager instead of caching a stale object.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:crushing",
 *   "ingredient": { "item": "minecraft:iron_ore" },
 *   "result": { "id": "industrialcore:iron_dust", "count": 2 },
 *   "time": 120,
 *   "energy": 10
 * }
 * }</pre>
 */
public record CrushingRecipe(Ingredient ingredient, ItemStack result, int timeTicks, int energyPerTick)
        implements Recipe<SingleRecipeInput> {

    public static final MapCodec<CrushingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CrushingRecipe::ingredient),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(CrushingRecipe::result),
                    Codec.INT.optionalFieldOf("time", ProcessingBalance.CRUSHER_TIME_TICKS)
                            .forGetter(CrushingRecipe::timeTicks),
                    Codec.INT.optionalFieldOf("energy", ProcessingBalance.CRUSHER_ENERGY_PER_TICK)
                            .forGetter(CrushingRecipe::energyPerTick))
            .apply(instance, CrushingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, CrushingRecipe::ingredient,
                    ItemStack.STREAM_CODEC, CrushingRecipe::result,
                    ByteBufCodecs.VAR_INT, CrushingRecipe::timeTicks,
                    ByteBufCodecs.VAR_INT, CrushingRecipe::energyPerTick,
                    CrushingRecipe::new);

    public CrushingRecipe {
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
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
        list.add(this.ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CRUSHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CRUSHING_TYPE.get();
    }

    /** Serializer of {@link CrushingRecipe}; registered as {@code industrialcore:crushing}. */
    public static final class Serializer implements RecipeSerializer<CrushingRecipe> {
        @Override
        public MapCodec<CrushingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
