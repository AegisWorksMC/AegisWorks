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
 * Data-driven cultivation recipe of the Phase 21 seed cultivator
 * ({@code industrialcore:cultivation}).
 *
 * <p>One recipe describes one metal: the seed that is consumed, the dust that feeds the run, the
 * billet that comes out and the two numbers the batch formula scales. The seed slot decides which
 * recipe runs, so a copper seed can never grow an iron billet: the recipe family is per metal and
 * the machine only ever looks up the recipe of the seed that is actually inserted.</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/cultivation_iron.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:cultivation",
 *   "ingredient": { "item": "industrialcore:iron_seed" },
 *   "feed": { "item": "industrialcore:iron_dust" },
 *   "result": { "id": "industrialcore:iron_billet", "count": 1 },
 *   "time": 600,
 *   "energy": 40,
 *   "feedPerUnit": 4
 * }
 * }</pre>
 *
 * <p>{@code time} and {@code energy} are the <em>N = 1</em> values; {@link #timeTicksFor(int)} and
 * {@link #energyFor(int)} scale them with {@link SeedLineBalance}.</p>
 */
public record CultivationRecipe(Ingredient seed, Ingredient feed, ItemStack result, int timeTicks,
                                int energyPerTick, int feedPerUnit)
        implements Recipe<SingleRecipeInput> {

    public static MapCodec<CultivationRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient")
                                .forGetter(CultivationRecipe::seed),
                        Ingredient.CODEC_NONEMPTY.fieldOf("feed").forGetter(CultivationRecipe::feed),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(CultivationRecipe::result),
                        Codec.INT.optionalFieldOf("time", SeedLineBalance.CULTIVATOR_TIME_TICKS)
                                .forGetter(CultivationRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy",
                                        SeedLineBalance.CULTIVATOR_ENERGY_PER_TICK)
                                .forGetter(CultivationRecipe::energyPerTick),
                        Codec.INT.optionalFieldOf("feedPerUnit", SeedLineBalance.FEED_PER_UNIT)
                                .forGetter(CultivationRecipe::feedPerUnit))
                .apply(instance, CultivationRecipe::new));
    }

    /** Network codec; written field by field so the order is explicit on both sides. */
    public static StreamCodec<RegistryFriendlyByteBuf, CultivationRecipe> streamCodec() {
        return StreamCodec.of(
                (buffer, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.seed());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.feed());
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.timeTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.energyPerTick());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.feedPerUnit());
                },
                buffer -> new CultivationRecipe(
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer)));
    }

    public CultivationRecipe {
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
        feedPerUnit = Math.max(1, feedPerUnit);
    }

    /** True when the item is the seed of this recipe. */
    public boolean matchesSeed(ItemStack stack) {
        return !stack.isEmpty() && this.seed.test(stack);
    }

    /** True when the item is the feed of this recipe. */
    public boolean matchesFeed(ItemStack stack) {
        return !stack.isEmpty() && this.feed.test(stack);
    }

    /** Ticks a batch of {@code count} billets takes. */
    public int timeTicksFor(int count) {
        return SeedLineBalance.batchTime(this.timeTicks, count);
    }

    /** Total FE a batch of {@code count} billets costs. */
    public long energyFor(int count) {
        return SeedLineBalance.batchEnergy(this.timeTicks, this.energyPerTick, count);
    }

    /** Feed dust a batch of {@code count} billets consumes. */
    public int feedFor(int count) {
        return SeedLineBalance.clampBatch(count) * this.feedPerUnit;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return matchesSeed(input.item());
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
        list.add(this.seed);
        list.add(this.feed);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CULTIVATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CULTIVATION_TYPE.get();
    }

    /** Serializer of the cultivation recipe type. */
    public static final class Serializer implements RecipeSerializer<CultivationRecipe> {
        @Override
        public MapCodec<CultivationRecipe> codec() {
            return CultivationRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CultivationRecipe> streamCodec() {
            return CultivationRecipe.streamCodec();
        }
    }
}
