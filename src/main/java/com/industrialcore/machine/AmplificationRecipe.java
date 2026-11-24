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
 * Data-driven amplification recipe of the Phase 21 matter amplifier
 * ({@code industrialcore:amplification}).
 *
 * <p>The amplifier has two outputs, so the recipe carries a {@code result} and a
 * {@code secondary}. Both are <em>existing</em> items: the recipe just describes how a billet plus a
 * reconstruction crystal turns into ore blocks and dust, which is why no new intermediate item is
 * needed for the 12x / 16x lines.</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/amplification_iron.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:amplification",
 *   "ingredient": { "item": "industrialcore:iron_billet" },
 *   "catalyst": { "item": "industrialcore:reconstruction_crystal" },
 *   "result": { "id": "minecraft:iron_ore", "count": 2 },
 *   "secondary": { "id": "industrialcore:iron_dust", "count": 4 },
 *   "time": 1200,
 *   "energy": 60
 * }
 * }</pre>
 */
public record AmplificationRecipe(Ingredient ingredient, Ingredient catalyst, ItemStack result,
                                  ItemStack secondary, int timeTicks, int energyPerTick)
        implements Recipe<SingleRecipeInput> {

    public static MapCodec<AmplificationRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient")
                                .forGetter(AmplificationRecipe::ingredient),
                        Ingredient.CODEC_NONEMPTY.fieldOf("catalyst")
                                .forGetter(AmplificationRecipe::catalyst),
                        ItemStack.STRICT_CODEC.fieldOf("result")
                                .forGetter(AmplificationRecipe::result),
                        ItemStack.STRICT_CODEC.fieldOf("secondary")
                                .forGetter(AmplificationRecipe::secondary),
                        Codec.INT.optionalFieldOf("time", SeedLineBalance.AMPLIFIER_TIME_TICKS)
                                .forGetter(AmplificationRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy",
                                        SeedLineBalance.AMPLIFIER_ENERGY_PER_TICK)
                                .forGetter(AmplificationRecipe::energyPerTick))
                .apply(instance, AmplificationRecipe::new));
    }

    /** Network codec; written field by field so the order is explicit on both sides. */
    public static StreamCodec<RegistryFriendlyByteBuf, AmplificationRecipe> streamCodec() {
        return StreamCodec.of(
                (buffer, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ingredient());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.catalyst());
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result());
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.secondary());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.timeTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.energyPerTick());
                },
                buffer -> new AmplificationRecipe(
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer)));
    }

    public AmplificationRecipe {
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    /** True when the item is the billet of this recipe. */
    public boolean matchesIngredient(ItemStack stack) {
        return !stack.isEmpty() && this.ingredient.test(stack);
    }

    /** True when the item is the catalyst of this recipe. */
    public boolean matchesCatalyst(ItemStack stack) {
        return !stack.isEmpty() && this.catalyst.test(stack);
    }

    /** Total FE of one run. */
    public long totalEnergy() {
        return (long) this.timeTicks * this.energyPerTick;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return matchesIngredient(input.item());
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
        list.add(this.catalyst);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.AMPLIFICATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.AMPLIFICATION_TYPE.get();
    }

    /** Serializer of the amplification recipe type. */
    public static final class Serializer implements RecipeSerializer<AmplificationRecipe> {
        @Override
        public MapCodec<AmplificationRecipe> codec() {
            return AmplificationRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AmplificationRecipe> streamCodec() {
            return AmplificationRecipe.streamCodec();
        }
    }
}
