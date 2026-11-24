package com.industrialcore.compat.jei;

import com.industrialcore.machine.AlloyRecipe;
import com.industrialcore.machine.AmplificationRecipe;
import com.industrialcore.machine.AssemblerRecipe;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.CultivationRecipe;
import com.industrialcore.machine.PhaseReactionRecipe;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.ReconstructionRecipe;
import com.industrialcore.machine.SynthesisRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the numbers JEI shows straight out of the recipe objects.
 *
 * <p>There is no second copy of any recipe here: an adapter only says <em>which</em> fields of a
 * recipe are ingredients, which are results, and where the duration and the FE are. A recipe
 * edited by a data pack therefore changes what JEI shows without any code change, which is what
 * keeps the display from drifting away from what the machine actually runs.</p>
 *
 * <p>Nothing in this class references JEI, so the server can run the same adapters in a GameTest
 * and check that every shipped recipe produces a drawable category entry.</p>
 */
public final class MachineRecipeViews {

    /** Returned by {@link View#energyPerTick(Object)} for recipes that burn fuel instead of FE. */
    public static final int NO_ENERGY = -1;

    /** One ingredient and how many items of it a run needs. */
    public record Slot(Ingredient ingredient, int count) {
        public Slot {
            count = Math.max(1, count);
        }
    }

    /**
     * One result: the prototype stack (always a single item) and the real amount.
     *
     * <p>The amount is kept apart from the stack because an ore tier yields up to 32,768 items,
     * which no item stack can hold; the category draws the amount as text when it does not fit.</p>
     */
    public record Output(ItemStack stack, int count) {
        public Output {
            count = Math.max(0, count);
        }

        public static Output of(ItemStack stack) {
            return new Output(stack.copyWithCount(1), stack.getCount());
        }
    }

    /** How one recipe type is displayed. */
    public interface View<T> {
        List<Slot> inputs(T recipe);

        List<Output> outputs(T recipe);

        int timeTicks(T recipe);

        int energyPerTick(T recipe);
    }

    public static final View<CrushingRecipe> CRUSHING = new View<>() {
        @Override
        public List<Slot> inputs(CrushingRecipe recipe) {
            return List.of(new Slot(recipe.ingredient(), 1));
        }

        @Override
        public List<Output> outputs(CrushingRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(CrushingRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(CrushingRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    /** Electrolysis, precision crushing and pressing share one recipe class. */
    public static final View<ProcessorRecipe> PROCESSOR = new View<>() {
        @Override
        public List<Slot> inputs(ProcessorRecipe recipe) {
            return List.of(new Slot(recipe.ingredient(), 1));
        }

        @Override
        public List<Output> outputs(ProcessorRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(ProcessorRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(ProcessorRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    /** The alloy furnace burns fuel, so it has no FE per tick and none is shown. */
    public static final View<AlloyRecipe> ALLOYING = new View<>() {
        @Override
        public List<Slot> inputs(AlloyRecipe recipe) {
            return List.of(new Slot(recipe.first(), 1), new Slot(recipe.second(), 1));
        }

        @Override
        public List<Output> outputs(AlloyRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(AlloyRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(AlloyRecipe recipe) {
            return NO_ENERGY;
        }
    };

    /** Sample, then the material and the crystal in the amounts the recipe lists. */
    public static final View<ReconstructionRecipe> RECONSTRUCTION = new View<>() {
        @Override
        public List<Slot> inputs(ReconstructionRecipe recipe) {
            return List.of(new Slot(recipe.analysisSample(), 1),
                    new Slot(recipe.material(), recipe.materialCount()),
                    new Slot(recipe.crystal(), recipe.crystalCount()));
        }

        @Override
        public List<Output> outputs(ReconstructionRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(ReconstructionRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(ReconstructionRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    public static final View<AssemblerRecipe> ASSEMBLING = new View<>() {
        @Override
        public List<Slot> inputs(AssemblerRecipe recipe) {
            List<Slot> slots = new ArrayList<>();
            for (Ingredient ingredient : recipe.ingredients()) {
                slots.add(new Slot(ingredient, 1));
            }
            return slots;
        }

        @Override
        public List<Output> outputs(AssemblerRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(AssemblerRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(AssemblerRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    /** The cultivator's own time and FE are the N = 1 values; the feed amount is per billet. */
    public static final View<CultivationRecipe> CULTIVATION = new View<>() {
        @Override
        public List<Slot> inputs(CultivationRecipe recipe) {
            return List.of(new Slot(recipe.seed(), 1),
                    new Slot(recipe.feed(), recipe.feedPerUnit()));
        }

        @Override
        public List<Output> outputs(CultivationRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(CultivationRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(CultivationRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    /** Two results: the ore blocks and the dust. */
    public static final View<AmplificationRecipe> AMPLIFICATION = new View<>() {
        @Override
        public List<Slot> inputs(AmplificationRecipe recipe) {
            return List.of(new Slot(recipe.ingredient(), 1), new Slot(recipe.catalyst(), 1));
        }

        @Override
        public List<Output> outputs(AmplificationRecipe recipe) {
            List<Output> outputs = new ArrayList<>(2);
            if (!recipe.result().isEmpty()) {
                outputs.add(Output.of(recipe.result()));
            }
            if (!recipe.secondary().isEmpty()) {
                outputs.add(Output.of(recipe.secondary()));
            }
            return outputs;
        }

        @Override
        public int timeTicks(AmplificationRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(AmplificationRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    public static final View<PhaseReactionRecipe> PHASE_REACTION = new View<>() {
        @Override
        public List<Slot> inputs(PhaseReactionRecipe recipe) {
            List<Slot> slots = new ArrayList<>();
            for (Ingredient ingredient : recipe.ingredients()) {
                slots.add(new Slot(ingredient, 1));
            }
            return slots;
        }

        @Override
        public List<Output> outputs(PhaseReactionRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(PhaseReactionRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(PhaseReactionRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    /** Up to ten entries, each with its own count; the amounts are the recipe's, not a guess. */
    public static final View<SynthesisRecipe> SYNTHESIS = new View<>() {
        @Override
        public List<Slot> inputs(SynthesisRecipe recipe) {
            List<Slot> slots = new ArrayList<>(recipe.ingredients().size());
            for (SynthesisRecipe.Entry entry : recipe.ingredients()) {
                slots.add(new Slot(entry.ingredient(), entry.count()));
            }
            return slots;
        }

        @Override
        public List<Output> outputs(SynthesisRecipe recipe) {
            return one(recipe.result());
        }

        @Override
        public int timeTicks(SynthesisRecipe recipe) {
            return recipe.timeTicks();
        }

        @Override
        public int energyPerTick(SynthesisRecipe recipe) {
            return recipe.energyPerTick();
        }
    };

    private MachineRecipeViews() {}

    /** A single non-empty result, or an empty list when the recipe has none. */
    private static List<Output> one(ItemStack result) {
        if (result.isEmpty()) {
            return List.of();
        }
        return List.of(Output.of(result));
    }
}
