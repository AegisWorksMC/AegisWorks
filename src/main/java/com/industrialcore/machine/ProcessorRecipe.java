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

import java.util.function.Supplier;

/**
 * Data-driven single-input recipe of the Phase 12 ore line.
 *
 * <p>Electrolysis, precision crushing and pressing need the same shape (one ingredient, one
 * result, time, FE per tick) as the existing {@link CrushingRecipe}, but each machine must only
 * see its own recipes: an ore block belongs in the precision crusher, an exposed ore in the
 * electrolyzer and an ingot in the metal press.
 * Each {@link Family} therefore owns its own recipe type and serializer, so
 * {@code getRecipeFor(type, ...)} can never return a recipe of another machine.</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/electrolysis_iron.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:electrolysis",
 *   "ingredient": { "item": "minecraft:raw_iron" },
 *   "result": { "id": "industrialcore:pure_iron", "count": 1 },
 *   "time": 240,
 *   "energy": 20
 * }
 * }</pre>
 */
public record ProcessorRecipe(Ingredient ingredient, ItemStack result, int timeTicks,
                              int energyPerTick, Family family) implements Recipe<SingleRecipeInput> {

    /** One recipe family per machine; the enum owns the registry entries to avoid duplication. */
    public enum Family {
        ELECTROLYSIS,
        PRECISION_CRUSHING,
        /** Phase 17: metal press (ingot -> plate). */
        PRESSING;

        public Supplier<RecipeType<ProcessorRecipe>> type() {
            return switch (this) {
                case ELECTROLYSIS -> ModRecipes.ELECTROLYSIS_TYPE;
                case PRECISION_CRUSHING -> ModRecipes.PRECISION_CRUSHING_TYPE;
                case PRESSING -> ModRecipes.PRESSING_TYPE;
            };
        }

        public Supplier<RecipeSerializer<ProcessorRecipe>> serializer() {
            return switch (this) {
                case ELECTROLYSIS -> ModRecipes.ELECTROLYSIS_SERIALIZER;
                case PRECISION_CRUSHING -> ModRecipes.PRECISION_CRUSHING_SERIALIZER;
                case PRESSING -> ModRecipes.PRESSING_SERIALIZER;
            };
        }

        public String id() {
            return switch (this) {
                case ELECTROLYSIS -> "electrolysis";
                case PRECISION_CRUSHING -> "precision_crushing";
                case PRESSING -> "pressing";
            };
        }
    }

    public static MapCodec<ProcessorRecipe> codec(Family family) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient")
                                .forGetter(ProcessorRecipe::ingredient),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ProcessorRecipe::result),
                        Codec.INT.optionalFieldOf("time", ProcessingBalance.ELECTROLYZER_TIME_TICKS)
                                .forGetter(ProcessorRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy",
                                        ProcessingBalance.ELECTROLYZER_ENERGY_PER_TICK)
                                .forGetter(ProcessorRecipe::energyPerTick))
                .apply(instance, (ingredient, result, time, energy) ->
                        new ProcessorRecipe(ingredient, result, time, energy, family)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ProcessorRecipe> streamCodec(Family family) {
        return StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ProcessorRecipe::ingredient,
                ItemStack.STREAM_CODEC, ProcessorRecipe::result,
                ByteBufCodecs.VAR_INT, ProcessorRecipe::timeTicks,
                ByteBufCodecs.VAR_INT, ProcessorRecipe::energyPerTick,
                (ingredient, result, time, energy) ->
                        new ProcessorRecipe(ingredient, result, time, energy, family));
    }

    public ProcessorRecipe {
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
        return this.family.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return this.family.type().get();
    }

    /** Serializer bound to one family; registered once per recipe type. */
    public static final class Serializer implements RecipeSerializer<ProcessorRecipe> {
        private final Family family;

        public Serializer(Family family) {
            this.family = family;
        }

        @Override
        public MapCodec<ProcessorRecipe> codec() {
            return ProcessorRecipe.codec(this.family);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ProcessorRecipe> streamCodec() {
            return ProcessorRecipe.streamCodec(this.family);
        }
    }
}
