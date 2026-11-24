package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.AmplificationRecipe;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.AlloyRecipe;
import com.industrialcore.machine.AssemblerRecipe;
import com.industrialcore.machine.CultivationRecipe;
import com.industrialcore.machine.PhaseReactionRecipe;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.ReconstructionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Recipe types and serializers added by this mod.
 *
 * <p>The vanilla smelting type is reused by the electric furnace. The Phase 12 ore line adds two
 * more single-input types (electrolysis and precision crushing) next to the Phase 7 crushing
 * type, so each machine only ever sees its own recipes.</p>
 */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, IndustrialCore.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, IndustrialCore.MOD_ID);

    public static final Supplier<RecipeType<CrushingRecipe>> CRUSHING_TYPE = RECIPE_TYPES.register(
            "crushing",
            () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "crushing")));

    public static final Supplier<RecipeSerializer<CrushingRecipe>> CRUSHING_SERIALIZER =
            RECIPE_SERIALIZERS.register("crushing", CrushingRecipe.Serializer::new);

    /** Phase 12: raw ore / exposed ore -> pure ore. */
    public static final Supplier<RecipeType<ProcessorRecipe>> ELECTROLYSIS_TYPE = RECIPE_TYPES.register(
            "electrolysis",
            () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "electrolysis")));

    public static final Supplier<RecipeSerializer<ProcessorRecipe>> ELECTROLYSIS_SERIALIZER =
            RECIPE_SERIALIZERS.register("electrolysis",
                    () -> new ProcessorRecipe.Serializer(ProcessorRecipe.Family.ELECTROLYSIS));

    /** Phase 12: silk-touch ore block -> exposed ore. */
    public static final Supplier<RecipeType<ProcessorRecipe>> PRECISION_CRUSHING_TYPE =
            RECIPE_TYPES.register("precision_crushing",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "precision_crushing")));

    public static final Supplier<RecipeSerializer<ProcessorRecipe>> PRECISION_CRUSHING_SERIALIZER =
            RECIPE_SERIALIZERS.register("precision_crushing",
                    () -> new ProcessorRecipe.Serializer(ProcessorRecipe.Family.PRECISION_CRUSHING));

    /** Phase 17: ingot -> plate (metal press). */
    public static final Supplier<RecipeType<ProcessorRecipe>> PRESSING_TYPE =
            RECIPE_TYPES.register("pressing",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "pressing")));

    public static final Supplier<RecipeSerializer<ProcessorRecipe>> PRESSING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pressing",
                    () -> new ProcessorRecipe.Serializer(ProcessorRecipe.Family.PRESSING));

    /**
     * Phase 17: two ingredients and a fuel (alloy furnace).
     *
     * <p>A separate type again, so an alloy recipe can never be picked up by the single-input
     * machines and the metal press can never be fed with an alloy recipe.</p>
     */
    public static final Supplier<RecipeType<AlloyRecipe>> ALLOYING_TYPE =
            RECIPE_TYPES.register("alloying",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "alloying")));

    public static final Supplier<RecipeSerializer<AlloyRecipe>> ALLOYING_SERIALIZER =
            RECIPE_SERIALIZERS.register("alloying", AlloyRecipe.Serializer::new);

    /** Phase 15: sample + material + crystal -> raw ore. */
    public static final Supplier<RecipeType<ReconstructionRecipe>> RECONSTRUCTION_TYPE =
            RECIPE_TYPES.register("reconstruction",
                    () -> RecipeType.simple(
                            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "reconstruction")));

    public static final Supplier<RecipeSerializer<ReconstructionRecipe>> RECONSTRUCTION_SERIALIZER =
            RECIPE_SERIALIZERS.register("reconstruction", ReconstructionRecipe.Serializer::new);

    /**
     * Phase 18: three ingredients and one result (assembler).
     *
     * <p>Its own type again: the three interchangeable slots must never be matched against the
     * two-input alloy recipes or the single-input processor recipes.</p>
     */
    public static final Supplier<RecipeType<AssemblerRecipe>> ASSEMBLING_TYPE =
            RECIPE_TYPES.register("assembling",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "assembling")));

    public static final Supplier<RecipeSerializer<AssemblerRecipe>> ASSEMBLING_SERIALIZER =
            RECIPE_SERIALIZERS.register("assembling", AssemblerRecipe.Serializer::new);

    /**
     * Phase 21: seed + dust feed -> billet (seed cultivator).
     *
     * <p>Its own type: the seed slot selects the recipe, so a copper seed can never be matched
     * against the iron recipe.</p>
     */
    public static final Supplier<RecipeType<CultivationRecipe>> CULTIVATION_TYPE =
            RECIPE_TYPES.register("cultivation",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "cultivation")));

    public static final Supplier<RecipeSerializer<CultivationRecipe>> CULTIVATION_SERIALIZER =
            RECIPE_SERIALIZERS.register("cultivation", CultivationRecipe.Serializer::new);

    /**
     * Phase 21: billet + crystal -> ore blocks + dust (matter amplifier).
     *
     * <p>Its own type again, because it is the only recipe shape with a catalyst and two outputs.</p>
     */
    public static final Supplier<RecipeType<AmplificationRecipe>> AMPLIFICATION_TYPE =
            RECIPE_TYPES.register("amplification",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "amplification")));

    public static final Supplier<RecipeSerializer<AmplificationRecipe>> AMPLIFICATION_SERIALIZER =
            RECIPE_SERIALIZERS.register("amplification", AmplificationRecipe.Serializer::new);

    /**
     * Phase 24: three ingredients and one result (phase reactor).
     *
     * <p>Its own type again: the three interchangeable slots must never be matched against the
     * single-input processor recipes or the two-input alloy recipes, and the reactor must never
     * pick up an assembler recipe.</p>
     */
    public static final Supplier<RecipeType<PhaseReactionRecipe>> PHASE_REACTION_TYPE =
            RECIPE_TYPES.register("phase_reaction",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "phase_reaction")));

    public static final Supplier<RecipeSerializer<PhaseReactionRecipe>> PHASE_REACTION_SERIALIZER =
            RECIPE_SERIALIZERS.register("phase_reaction", PhaseReactionRecipe.Serializer::new);

    /**
     * Phase 30: synthetic ore synthesis. Its own type as well: reusing {@code phase_reaction}
     * would make every synthesis recipe runnable in the existing phase reactor.
     */
    public static final Supplier<RecipeType<SynthesisRecipe>> SYNTHESIS_TYPE =
            RECIPE_TYPES.register("synthesis",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "synthesis")));

    public static final Supplier<RecipeSerializer<SynthesisRecipe>> SYNTHESIS_SERIALIZER =
            RECIPE_SERIALIZERS.register("synthesis", SynthesisRecipe.Serializer::new);

    private ModRecipes() {}
}
