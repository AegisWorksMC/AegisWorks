package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Phase 30 follow-up: every shipped crafting recipe through the real recipe manager.
 *
 * <p>1.21.1 ignores an amount written inside an {@code Ingredient}, so the recipes used to cost one
 * item each and the machines were cheaper than their design table. The amounts are now one
 * ingredient entry per item, and this test proves the parsed result: the recipes all load, the
 * entry counts equal the documented amounts, and a representative build matches with exactly its
 * items and stops matching when one is missing. {@code RecipeCraftingFormatTest} checks the same
 * expectations on the raw files, so both the text and the parsed data are pinned.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingRecipeGameTests {
    /**
     * The mod's crafting-table recipes. The count fell from 74 to 56 in Phase 31, when the seven
     * ore amplifier builds and the upgrade kits of the removed ore line left with it; the three
     * sub-assemblies are assembler recipes, not crafting-table ones, so they never counted here.
     * Phase 33 added the twelve build recipes of the ten power devices: 56 + 12 = 68.
     * Phase 41 adds the geothermal generator: 68 + 1 = 69; Phase 82 adds the factory monitor: 70.
     */
    private static final int SHIPPED_CRAFTING_RECIPES = 70;

    /** The crafting grid, and the recipe book that fills it, hold nine items. */
    private static final int MAX_GRID_ITEMS = 9;

    private CraftingRecipeGameTests() {}

    /**
     * Every recipe loads (a file that fails to parse is simply absent), the sub-assemblies are
     * there, and no recipe of the mod needs more items than the grid holds.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void everyShippedCraftingRecipeIsLoaded(GameTestHelper helper) {
        Map<String, CraftingRecipe> recipes = craftingRecipes(helper);

        helper.assertTrue(recipes.size() == SHIPPED_CRAFTING_RECIPES,
                "loaded " + recipes.size() + " crafting recipes of the mod instead of "
                        + SHIPPED_CRAFTING_RECIPES + ": a recipe that fails to parse never reaches "
                        + "the recipe manager and would go missing silently");

        for (String expected : documentedCosts().keySet()) {
            helper.assertTrue(recipes.containsKey(expected),
                    "the crafting recipe " + expected + " did not load");
        }

        for (Map.Entry<String, CraftingRecipe> entry : recipes.entrySet()) {
            int required = requiredItems(entry.getValue());
            helper.assertTrue(required <= MAX_GRID_ITEMS,
                    entry.getKey() + " needs " + required + " items, which the "
                            + MAX_GRID_ITEMS + " slot crafting grid cannot hold");
        }
        helper.succeed();
    }

    /**
     * The parsed entry counts equal the documented amounts: each expected item is tested against
     * every ingredient entry and the number of entries that accept it is its amount. That is the
     * only reading that can tell "two steel plates" from "one", which is what a dropped count
     * looked like.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void everyRecipeConsumesTheDocumentedAmount(GameTestHelper helper) {
        Map<String, CraftingRecipe> recipes = craftingRecipes(helper);

        for (Map.Entry<String, Map<String, Integer>> expected : documentedCosts().entrySet()) {
            CraftingRecipe recipe = recipes.get(expected.getKey());
            helper.assertTrue(recipe != null, "missing recipe " + expected.getKey());

            int counted = 0;
            for (Map.Entry<String, Integer> item : expected.getValue().entrySet()) {
                ItemStack stack = stack(helper, item.getKey());
                int found = countOf(recipe, stack);
                helper.assertTrue(found == item.getValue(),
                        expected.getKey() + " must consume " + item.getValue() + " x "
                                + item.getKey() + " but the parsed recipe accepts " + found);
                counted += found;
            }
            helper.assertTrue(counted == requiredItems(recipe),
                    expected.getKey() + " has " + requiredItems(recipe) + " required items but only "
                            + counted + " of them are in the documented cost");
        }
        helper.succeed();
    }

    /**
     * A representative build: with exactly its items the recipe matches and assembles its result,
     * and with one item missing nothing matches at all.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void aRepresentativeBuildNeedsEveryItem(GameTestHelper helper) {
        for (String name : List.of("machine_frame", "reconstruction_crystal",
                "energy_buffer_mk3", "ore_synthesizer")) {
            Map<String, Integer> cost = documentedCosts().get(name);
            ItemStack result = assemble(helper, "industrialcore:" + name, cost);
            helper.assertTrue(idOf(result).equals("industrialcore:" + name),
                    name + " assembled into " + idOf(result));
            helper.assertTrue(result.getCount() == 1, name + " yields " + result.getCount());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // crafting through the loaded data
    // ------------------------------------------------------------------

    /** Crafts {@code result} from exactly {@code cost} and proves one missing item stops it. */
    private static ItemStack assemble(GameTestHelper helper, String result,
                                      Map<String, Integer> cost) {
        List<ItemStack> exact = new ArrayList<>();
        for (Map.Entry<String, Integer> item : cost.entrySet()) {
            for (int index = 0; index < item.getValue(); index++) {
                exact.add(stack(helper, item.getKey()));
            }
        }
        CraftingInput input = grid(exact);
        Optional<RecipeHolder<CraftingRecipe>> match = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(match.isPresent(),
                result + " does not match its own documented items: " + describe(exact));
        helper.assertTrue(idOf(match.get().id()).equals(result),
                "the items of " + result + " matched " + idOf(match.get().id()) + " instead");

        ItemStack assembled = match.get().value()
                .assemble(input, helper.getLevel().registryAccess());

        List<ItemStack> oneShort = new ArrayList<>(exact);
        oneShort.remove(oneShort.size() - 1);
        CraftingInput shortInput = grid(oneShort);
        Optional<RecipeHolder<CraftingRecipe>> shortMatch = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, shortInput, helper.getLevel());
        helper.assertTrue(shortMatch.isEmpty(),
                result + " still crafted with one item missing: " + describe(oneShort));
        return assembled;
    }

    /** Every crafting recipe of the mod, by path (without the namespace). */
    private static Map<String, CraftingRecipe> craftingRecipes(GameTestHelper helper) {
        Map<String, CraftingRecipe> recipes = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)) {
            if (IndustrialCore.MOD_ID.equals(holder.id().getNamespace())) {
                recipes.put(holder.id().getPath(), holder.value());
            }
        }
        return recipes;
    }

    /** How many items the recipe needs: one per non-empty ingredient entry. */
    private static int requiredItems(CraftingRecipe recipe) {
        int required = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient != null && !ingredient.isEmpty() && !ingredient.hasNoItems()) {
                required++;
            }
        }
        return required;
    }

    /** How many entries of the recipe accept the stack. */
    private static int countOf(CraftingRecipe recipe, ItemStack stack) {
        int found = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) {
                found++;
            }
        }
        return found;
    }

    /** The three by three grid of the crafting table, padded with empty slots. */
    private static CraftingInput grid(List<ItemStack> items) {
        List<ItemStack> padded = new ArrayList<>(items);
        while (padded.size() < MAX_GRID_ITEMS) {
            padded.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, padded);
    }

    private static ItemStack stack(GameTestHelper helper, String id) {
        String full = id.contains(":") ? id : "industrialcore:" + id;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(full)));
        helper.assertTrue(!stack.is(Items.AIR), "there is no registered item " + full);
        return stack;
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String idOf(ResourceLocation location) {
        return location.toString();
    }

    private static String describe(List<ItemStack> stacks) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemStack stack : stacks) {
            counts.merge(idOf(stack), 1, Integer::sum);
        }
        return counts.toString();
    }

    // ------------------------------------------------------------------
    // the documented build costs (item -> amount)
    // ------------------------------------------------------------------

    /**
     * Every crafting recipe this table guards: the frames, the crystal, the synthesizer and the
     * high-density buffer. The amounts are the design table values of Phase 15/17/30, which is
     * exactly what the game has to charge again. (The ore amplifier builds and their three
     * sub-assemblies left with the old ore line in Phase 31.)
     */
    private static Map<String, Map<String, Integer>> documentedCosts() {
        Map<String, Map<String, Integer>> costs = new LinkedHashMap<>();
        costs.put("machine_frame", Map.of("steel_plate", 2, "brass_plate", 1, "gold_plate", 1,
                "copper_plate", 2, "motor", 1));
        costs.put("reconstruction_crystal", Map.of("minecraft:amethyst_shard", 2,
                "minecraft:redstone", 4, "minecraft:glass", 1));
        costs.put("energy_buffer_mk3", Map.of("energy_buffer_mk2", 2, "durable_machine_frame", 1,
                "phase_core", 1, "resonant_catalyst", 2));
        costs.put("ore_synthesizer", Map.of("phase_reactor", 1, "durable_machine_frame", 2,
                "phase_core", 2));
        return costs;
    }
}
