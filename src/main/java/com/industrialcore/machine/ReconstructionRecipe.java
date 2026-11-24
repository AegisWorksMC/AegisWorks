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
 * Data-driven reconstruction recipe ({@code industrialcore:reconstruction}).
 *
 * <p>One recipe describes one mineral: the sample that unlocks it, the material and the crystal
 * that are consumed when the run finishes and the raw ore that comes out. The recipe files are the
 * only place where the numbers live, so a datapack can add a new mineral without touching the
 * code.</p>
 *
 * <p>Example ({@code data/industrialcore/recipe/reconstruct_iron.json}):</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:reconstruction",
 *   "mineral": "iron",
 *   "analysis_sample": { "item": "industrialcore:pure_iron" },
 *   "material": { "item": "minecraft:stone" },
 *   "material_count": 8,
 *   "crystal": { "item": "industrialcore:reconstruction_crystal" },
 *   "crystal_count": 1,
 *   "result": { "id": "minecraft:raw_iron", "count": 1 },
 *   "time": 200,
 *   "energy": 20
 * }
 * }</pre>
 */
public record ReconstructionRecipe(String mineral, Ingredient analysisSample, Ingredient material,
                                   int materialCount, Ingredient crystal, int crystalCount,
                                   ItemStack result, int timeTicks, int energyPerTick)
        implements Recipe<SingleRecipeInput> {

    public static MapCodec<ReconstructionRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("mineral").forGetter(ReconstructionRecipe::mineral),
                        Ingredient.CODEC_NONEMPTY.fieldOf("analysis_sample")
                                .forGetter(ReconstructionRecipe::analysisSample),
                        Ingredient.CODEC_NONEMPTY.fieldOf("material")
                                .forGetter(ReconstructionRecipe::material),
                        Codec.INT.optionalFieldOf("material_count", ReconstructorBalance.MATERIAL_COUNT)
                                .forGetter(ReconstructionRecipe::materialCount),
                        Ingredient.CODEC_NONEMPTY.fieldOf("crystal")
                                .forGetter(ReconstructionRecipe::crystal),
                        Codec.INT.optionalFieldOf("crystal_count", ReconstructorBalance.CRYSTAL_COUNT)
                                .forGetter(ReconstructionRecipe::crystalCount),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ReconstructionRecipe::result),
                        Codec.INT.optionalFieldOf("time", ReconstructorBalance.IRON_TIME_TICKS)
                                .forGetter(ReconstructionRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy", ReconstructorBalance.IRON_ENERGY_PER_TICK)
                                .forGetter(ReconstructionRecipe::energyPerTick))
                .apply(instance, ReconstructionRecipe::new));
    }

    /**
     * Network codec of the recipe.
     *
     * <p>The recipe has nine fields, more than {@code StreamCodec.composite} supports, so the
     * fields are written one after another. The order must stay the same in the encoder and in
     * the decoder.</p>
     */
    public static StreamCodec<RegistryFriendlyByteBuf, ReconstructionRecipe> streamCodec() {
        return StreamCodec.of(
                (buffer, recipe) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, recipe.mineral());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.analysisSample());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.material());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.materialCount());
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.crystal());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.crystalCount());
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.timeTicks());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.energyPerTick());
                },
                buffer -> new ReconstructionRecipe(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer)));
    }

    public ReconstructionRecipe {
        mineral = mineral == null ? "" : mineral;
        materialCount = Math.max(1, materialCount);
        crystalCount = Math.max(1, crystalCount);
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    /** True when the item is the sample of this recipe. */
    public boolean matchesSample(ItemStack stack) {
        return !stack.isEmpty() && analysisSample.test(stack);
    }

    /** Total FE of one run. */
    public int totalEnergy() {
        return ReconstructorBalance.totalEnergy(timeTicks, energyPerTick);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return matchesSample(input.item());
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
        list.add(this.analysisSample);
        list.add(this.material);
        list.add(this.crystal);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RECONSTRUCTION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.RECONSTRUCTION_TYPE.get();
    }

    /** Serializer of the reconstruction recipe type. */
    public static final class Serializer implements RecipeSerializer<ReconstructionRecipe> {
        @Override
        public MapCodec<ReconstructionRecipe> codec() {
            return ReconstructionRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReconstructionRecipe> streamCodec() {
            return ReconstructionRecipe.streamCodec();
        }
    }
}
