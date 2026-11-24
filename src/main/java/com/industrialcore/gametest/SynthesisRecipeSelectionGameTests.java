package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which recipe does the synthesizer pick when several recipes fit the same slots?
 *
 * <p>{@link SynthesisRecipe#matchSlots} ignores the slots a recipe does not use, so a recipe whose
 * entries are a subset of the loaded stacks matches as well as the recipe that lists them all.
 * The ten ingredients of {@code synthesis_ultimate} therefore also satisfied {@code synthesis_1},
 * and picking "the first match" made the machine craft synthetic ore I from the ultimate set -
 * the stage 15 catalyst was not reliably craftable and the top of the ore line could not be
 * reached from raw materials (finding V-1 of the audit documents).</p>
 *
 * <p>The contract these tests pin: the <em>most specific</em> match wins, ties are broken by the
 * total item count and then by the result item id, and the outcome never depends on the order of
 * the recipe list. Every case runs against the real recipe manager and the real server tick.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SynthesisRecipeSelectionGameTests {
    private static final int Y = 6;
    private static final String ULTIMATE_ID = "industrialcore:synthesis_ultimate";

    private SynthesisRecipeSelectionGameTests() {}

    /** The blocked case: the ten ultimate ingredients must make the ultimate catalyst, not ore I. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void theUltimateRecipeWinsOverTheSubsetRecipe(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        SynthesisRecipe ultimate = recipe(helper, ULTIMATE_ID);
        List<ItemStack> inputs = ingredientsOf(ultimate);
        load(machine, inputs);

        run(helper, machine, ultimate.timeTicks() + 2);

        ItemStack output = output(machine);
        helper.assertTrue(output.is(ModItems.SYNTHETIC_ORE_ULTIMATE.get()),
                "the synthesizer produced " + output + " from the ultimate ingredients");
        helper.assertTrue(output.getCount() == ultimate.result().getCount(),
                "the run produced " + output.getCount() + " items instead of "
                        + ultimate.result().getCount());
        for (int slot = 0; slot < inputs.size(); slot++) {
            helper.assertTrue(machine.getInventory().getStackInSlot(slot).isEmpty(),
                    "ingredient slot " + slot + " was not fully consumed: "
                            + machine.getInventory().getStackInSlot(slot));
        }
        helper.succeed();
    }

    /** The ladder's first recipe still runs on its own three ingredients. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void theSubsetRecipeStillRuns(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        SynthesisRecipe first = recipe(helper, "industrialcore:synthesis_1");
        List<ItemStack> inputs = ingredientsOf(first);
        load(machine, inputs);

        run(helper, machine, first.timeTicks() + 2);

        ItemStack output = output(machine);
        helper.assertTrue(output.is(ModItems.SYNTHETIC_ORE_1.get()) && output.getCount() == 1,
                "the synthesizer produced " + output + " instead of synthetic ore I");
        for (int slot = 0; slot < inputs.size(); slot++) {
            helper.assertTrue(machine.getInventory().getStackInSlot(slot).isEmpty(),
                    "ingredient slot " + slot + " was not fully consumed");
        }
        helper.succeed();
    }

    /**
     * Nine of the ten ultimate ingredients leave the subset recipe as the only match: the machine
     * runs ore I and must not touch the six dusts that recipe does not list.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void aPartialUltimateSetRunsTheRecipeItActuallyMatches(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        List<ItemStack> inputs = ingredientsOf(recipe(helper, ULTIMATE_ID));
        // Drop the astral silver dust; adamantite, mithril and the crystal remain, which is
        // exactly synthesis_1.
        int removed = -1;
        for (int index = 0; index < inputs.size(); index++) {
            if (inputs.get(index).is(ModItems.ASTRAL_SILVER_DUST.get())) {
                removed = index;
                break;
            }
        }
        helper.assertTrue(removed >= 0, "the ultimate recipe no longer lists astral silver dust");
        inputs.remove(removed);
        // The slot of a dust that synthesis_1 does not list, counted after the removal.
        int leftoverSlot = -1;
        for (int index = 0; index < inputs.size(); index++) {
            if (inputs.get(index).is(ModItems.NEBULA_DUST.get())) {
                leftoverSlot = index;
                break;
            }
        }
        helper.assertTrue(leftoverSlot >= 0, "the ultimate recipe no longer lists nebula dust");
        load(machine, inputs);

        run(helper, machine, SynthesisBalance.TIME_TICKS + 2);

        ItemStack output = output(machine);
        helper.assertTrue(output.is(ModItems.SYNTHETIC_ORE_1.get()),
                "a partial ultimate set produced " + output);
        helper.assertTrue(machine.getInventory().getStackInSlot(leftoverSlot)
                        .is(ModItems.NEBULA_DUST.get()),
                "an ingredient the matched recipe does not use was consumed: "
                        + machine.getInventory().getStackInSlot(leftoverSlot));
        helper.succeed();
    }

    /** Missing one of the three ingredients: nothing runs and nothing is consumed. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void anIncompleteSetRunsNothing(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(0,
                new ItemStack(ModItems.ADAMANTITE_DUST.get(), 1));
        machine.getInventory().setStackInSlot(1, new ItemStack(ModItems.MITHRIL_DUST.get(), 1));

        run(helper, machine, 100);

        helper.assertTrue(output(machine).isEmpty(),
                "a set without a crystal produced " + output(machine));
        helper.assertTrue(machine.getProgressForTesting() == 0,
                "the progress advanced to " + machine.getProgressForTesting());
        helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 1
                        && machine.getInventory().getStackInSlot(1).getCount() == 1,
                "ingredients were consumed by a run that could not start");
        helper.succeed();
    }

    /**
     * Selection does not depend on the order of the recipe list, and equal-sized candidates are
     * resolved by the result id - the recipe manager hands the recipes over in an unspecified
     * order, so a first-match rule could differ between two identical worlds.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void selectionIsDeterministicAndOrderIndependent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<SynthesisRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<SynthesisRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(
                com.industrialcore.registry.ModRecipes.SYNTHESIS_TYPE.get())) {
            recipes.add(holder.value());
        }
        List<ItemStack> ultimate = ingredientsOf(recipe(helper, ULTIMATE_ID));
        List<ItemStack> first = ingredientsOf(recipe(helper, "industrialcore:synthesis_1"));

        SynthesisRecipe expectedUltimate = expectResult(helper, recipes, ultimate,
                ModItems.SYNTHETIC_ORE_ULTIMATE.get());
        SynthesisRecipe expectedFirst = expectResult(helper, recipes, first,
                ModItems.SYNTHETIC_ORE_1.get());

        Collections.reverse(recipes);
        helper.assertTrue(SynthesisRecipe.find(recipes, ultimate) == expectedUltimate,
                "reversing the recipe list changed the ultimate selection");
        helper.assertTrue(SynthesisRecipe.find(recipes, first) == expectedFirst,
                "reversing the recipe list changed the ore I selection");

        // Two candidates with the same number of entries: the result id decides, in both orders.
        SynthesisRecipe oreOne = new SynthesisRecipe(
                List.of(new SynthesisRecipe.Entry(Ingredient.of(ModItems.IRON_DUST.get()), 1)),
                new ItemStack(ModItems.SYNTHETIC_ORE_1.get(), 1), 10, 20);
        SynthesisRecipe oreTwo = new SynthesisRecipe(
                List.of(new SynthesisRecipe.Entry(Ingredient.of(ModItems.IRON_DUST.get()), 1)),
                new ItemStack(ModItems.SYNTHETIC_ORE_2.get(), 1), 10, 20);
        List<ItemStack> dust = List.of(new ItemStack(ModItems.IRON_DUST.get(), 1));
        List<SynthesisRecipe> forward = List.of(oreTwo, oreOne);
        List<SynthesisRecipe> backward = List.of(oreOne, oreTwo);
        helper.assertTrue(SynthesisRecipe.find(forward, dust) == oreOne,
                "equal-sized candidates did not resolve to the smaller result id");
        helper.assertTrue(SynthesisRecipe.find(backward, dust) == oreOne,
                "equal-sized candidates resolved differently for the other list order");

        // A recipe with more entries beats a smaller one that also matches, in both orders.
        SynthesisRecipe wide = new SynthesisRecipe(
                List.of(new SynthesisRecipe.Entry(Ingredient.of(ModItems.IRON_DUST.get()), 1),
                        new SynthesisRecipe.Entry(Ingredient.of(ModItems.GOLD_DUST.get()), 1)),
                new ItemStack(ModItems.SYNTHETIC_ORE_3.get(), 1), 10, 20);
        List<ItemStack> wideInput = List.of(new ItemStack(ModItems.IRON_DUST.get(), 1),
                new ItemStack(ModItems.GOLD_DUST.get(), 1));
        helper.assertTrue(SynthesisRecipe.find(List.of(oreOne, wide), wideInput) == wide,
                "the wider recipe lost when it was listed second");
        helper.assertTrue(SynthesisRecipe.find(List.of(wide, oreOne), wideInput) == wide,
                "the wider recipe lost when it was listed first");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static SynthesisRecipe expectResult(GameTestHelper helper, List<SynthesisRecipe> recipes,
                                                List<ItemStack> stacks,
                                                net.minecraft.world.item.Item expected) {
        SynthesisRecipe found = SynthesisRecipe.find(recipes, stacks);
        helper.assertTrue(found != null, "no recipe matched " + stacks);
        helper.assertTrue(found.result().is(expected),
                "the machine would pick " + found.result() + " instead of " + expected);
        return found;
    }

    private static SynthesisRecipe recipe(GameTestHelper helper, String id) {
        return (SynthesisRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("missing recipe " + id))
                .value();
    }

    private static List<ItemStack> ingredientsOf(SynthesisRecipe recipe) {
        List<ItemStack> inputs = new ArrayList<>();
        for (SynthesisRecipe.Entry entry : recipe.ingredients()) {
            inputs.add(entry.ingredient().getItems()[0].copyWithCount(entry.count()));
        }
        return inputs;
    }

    private static void load(OreSynthesizerBlockEntity machine, List<ItemStack> inputs) {
        for (int slot = 0; slot < OreSynthesizerBlockEntity.SLOT_COUNT; slot++) {
            machine.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < inputs.size(); index++) {
            machine.getInventory().setStackInSlot(index, inputs.get(index).copy());
        }
    }

    private static void run(GameTestHelper helper, OreSynthesizerBlockEntity machine, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            machine.setEnergyStored(SynthesisBalance.CAPACITY);
            BlockState state = machine.getBlockState();
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state,
                    machine);
        }
    }

    private static ItemStack output(OreSynthesizerBlockEntity machine) {
        return machine.getInventory().getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
    }

    private static OreSynthesizerBlockEntity placeSynthesizer(GameTestHelper helper,
                                                              BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof OreSynthesizerBlockEntity machine)) {
            throw new IllegalStateException("ore synthesizer block entity missing at " + pos);
        }
        return machine;
    }
}
