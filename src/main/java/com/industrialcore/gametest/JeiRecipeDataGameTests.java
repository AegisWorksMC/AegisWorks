package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.compat.jei.JeiCoverage;
import com.industrialcore.compat.jei.MachineRecipeViews;
import com.industrialcore.compat.jei.MachineRecipeViews.Output;
import com.industrialcore.compat.jei.MachineRecipeViews.Slot;
import com.industrialcore.compat.jei.MachineRecipeViews.View;
import com.industrialcore.machine.AlloyRecipe;
import com.industrialcore.machine.AmplificationRecipe;
import com.industrialcore.machine.AssemblerRecipe;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.CultivationRecipe;
import com.industrialcore.machine.PhaseReactionRecipe;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.ReconstructionRecipe;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Checks the data JEI draws, through the real recipe manager.
 *
 * <p>JEI itself is a client mod, so a GameTest cannot open its screens. What it can do is run the
 * exact adapters the categories use against every loaded recipe and fail when a recipe would show
 * nothing: an empty ingredient, a result of zero items, a missing duration, or more slots than the
 * category has room for. Together with the coverage test in the unit suite, that covers everything
 * between "the recipe file exists" and "JEI has something to draw", which is where the machines
 * used to fall through the cracks.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JeiRecipeDataGameTests {

    /** The machine recipes of the mod, by type. */
    private static final int CRUSHING = 31;
    private static final int ELECTROLYSIS = 22;
    private static final int PRECISION_CRUSHING = 11;
    private static final int PRESSING = 16;
    private static final int ALLOYING = 2;
    private static final int RECONSTRUCTION = 3;
    private static final int ASSEMBLING = 2;
    private static final int CULTIVATION = 3;
    private static final int AMPLIFICATION = 3;
    private static final int PHASE_REACTION = 4;
    private static final int SYNTHESIS = 109;

    private JeiRecipeDataGameTests() {}

    /** Every recipe of every machine is loaded and gives JEI at least one input and one output. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void everyMachineRecipeIsDrawable(GameTestHelper helper) {
        check(helper, "crushing", ModRecipes.CRUSHING_TYPE.get(),
                MachineRecipeViews.CRUSHING, CRUSHING);
        check(helper, "electrolysis", ModRecipes.ELECTROLYSIS_TYPE.get(),
                MachineRecipeViews.PROCESSOR, ELECTROLYSIS);
        check(helper, "precision_crushing", ModRecipes.PRECISION_CRUSHING_TYPE.get(),
                MachineRecipeViews.PROCESSOR, PRECISION_CRUSHING);
        check(helper, "pressing", ModRecipes.PRESSING_TYPE.get(),
                MachineRecipeViews.PROCESSOR, PRESSING);
        check(helper, "alloying", ModRecipes.ALLOYING_TYPE.get(),
                MachineRecipeViews.ALLOYING, ALLOYING);
        check(helper, "reconstruction", ModRecipes.RECONSTRUCTION_TYPE.get(),
                MachineRecipeViews.RECONSTRUCTION, RECONSTRUCTION);
        check(helper, "assembling", ModRecipes.ASSEMBLING_TYPE.get(),
                MachineRecipeViews.ASSEMBLING, ASSEMBLING);
        check(helper, "cultivation", ModRecipes.CULTIVATION_TYPE.get(),
                MachineRecipeViews.CULTIVATION, CULTIVATION);
        check(helper, "amplification", ModRecipes.AMPLIFICATION_TYPE.get(),
                MachineRecipeViews.AMPLIFICATION, AMPLIFICATION);
        check(helper, "phase_reaction", ModRecipes.PHASE_REACTION_TYPE.get(),
                MachineRecipeViews.PHASE_REACTION, PHASE_REACTION);
        check(helper, "synthesis", ModRecipes.SYNTHESIS_TYPE.get(),
                MachineRecipeViews.SYNTHESIS, SYNTHESIS);
        helper.succeed();
    }

    /**
     * The widest recipe of the synthesizer fills the category exactly; one more entry would need a
     * layout that does not exist yet.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void theWidestSynthesisRecipeFillsTheCategory(GameTestHelper helper) {
        JeiCoverage.Category spec = JeiCoverage.byRecipeType("synthesis");
        helper.assertTrue(spec != null, "the synthesis recipe type has no JEI category");
        int capacity = spec.layout().columns * spec.layout().maxRows;

        int largest = 0;
        String widest = "";
        for (RecipeHolder<SynthesisRecipe> holder : recipes(helper,
                ModRecipes.SYNTHESIS_TYPE.get())) {
            int entries = holder.value().ingredients().size();
            if (entries > largest) {
                largest = entries;
                widest = holder.id().toString();
            }
            for (SynthesisRecipe.Entry entry : holder.value().ingredients()) {
                helper.assertTrue(entry.count() >= 1,
                        holder.id() + " lists an ingredient with the amount " + entry.count());
            }
        }

        helper.assertTrue(largest > 0, "no synthesis recipe is loaded");
        helper.assertTrue(largest <= capacity,
                widest + " needs " + largest + " slots but the JEI category only draws "
                        + capacity + ", so its ingredients would be cut off");
        helper.assertTrue(largest == capacity,
                "the widest synthesis recipe uses " + largest + " of the " + capacity
                        + " drawn slots; check the still room before adding a wider recipe");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // the shared check
    // ------------------------------------------------------------------

    /**
     * Runs one view over every recipe of one type.
     *
     * @param id       the recipe type id, which is also the JEI category id
     * @param expected how many recipes the data ships
     */
    private static <I extends RecipeInput, T extends Recipe<I>> void check(
            GameTestHelper helper, String id, RecipeType<T> type, View<T> view, int expected) {
        JeiCoverage.Category spec = JeiCoverage.byRecipeType(id);
        helper.assertTrue(spec != null, "no JEI category covers the recipe type " + id);

        List<RecipeHolder<T>> holders = recipes(helper, type);
        helper.assertTrue(holders.size() == expected,
                id + " loaded " + holders.size() + " recipes instead of " + expected
                        + ": a recipe that fails to parse never reaches the recipe manager, so it "
                        + "would also never reach JEI");

        int widestInputs = 0;
        int mostOutputs = 0;
        for (RecipeHolder<T> holder : holders) {
            List<Slot> inputs = view.inputs(holder.value());
            List<Output> outputs = view.outputs(holder.value());
            helper.assertTrue(!inputs.isEmpty(), holder.id() + " has no ingredient to show");
            helper.assertTrue(!outputs.isEmpty(), holder.id() + " has no result to show");

            for (Slot input : inputs) {
                helper.assertTrue(input.ingredient().getItems().length > 0,
                        holder.id() + " has an ingredient that matches no item, so its JEI slot "
                                + "would stay empty");
                helper.assertTrue(input.count() >= 1,
                        holder.id() + " asks for " + input.count() + " items of an ingredient");
            }
            for (Output output : outputs) {
                helper.assertTrue(!output.stack().isEmpty(),
                        holder.id() + " produces an empty stack");
                helper.assertTrue(output.count() >= 1,
                        holder.id() + " produces " + output.count() + " items");
            }

            int time = view.timeTicks(holder.value());
            helper.assertTrue(time >= 1, holder.id() + " lasts " + time + " ticks");
            int energy = view.energyPerTick(holder.value());
            helper.assertTrue(energy >= 0 || energy == MachineRecipeViews.NO_ENERGY,
                    holder.id() + " reports the FE per tick " + energy
                            + "; a recipe either draws FE or has none at all");

            widestInputs = Math.max(widestInputs, inputs.size());
            mostOutputs = Math.max(mostOutputs, outputs.size());
        }

        JeiCoverage.Layout layout = spec.layout();
        helper.assertTrue(widestInputs <= layout.columns * layout.maxRows,
                id + " needs " + widestInputs + " ingredient slots but " + layout
                        + " only draws " + layout.columns * layout.maxRows);
        helper.assertTrue(mostOutputs <= layout.maxOutputs,
                id + " produces " + mostOutputs + " results but " + layout + " only draws "
                        + layout.maxOutputs);
    }

    private static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> recipes(
            GameTestHelper helper, RecipeType<T> type) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(type);
    }
}
