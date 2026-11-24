package com.industrialcore.machine;

/**
 * Order-independent matching of a fixed ingredient list against a fixed slot list.
 *
 * <p>The Phase 24 phase reactor, like the Phase 18 assembler, has interchangeable ingredient
 * slots: a pipe or a player cannot break a recipe by filling the slots "in the wrong order". The
 * matching is a tiny backtracking search over at most 3! = 6 branches, never a full permutation
 * list, so it costs a handful of comparisons per attempt.</p>
 *
 * <p>The class is free of Minecraft classes on purpose: {@link PhaseReactionRecipe} supplies the
 * "ingredient i accepts stack j" relation, and the unit tests can exercise the search with a plain
 * table of booleans.</p>
 */
public final class PhaseReactionRules {
    /** The "ingredient i accepts stack j" relation of one matching attempt. */
    @FunctionalInterface
    public interface Tester {
        boolean accepts(int ingredientIndex, int stackIndex);
    }

    /**
     * True when the stacks cover every ingredient exactly once, in any order.
     *
     * @param ingredientCount number of recipe ingredients (must be positive)
     * @param stackCount      number of slots that hold an item
     */
    public static boolean match(int ingredientCount, int stackCount, Tester tester) {
        if (ingredientCount <= 0 || ingredientCount != stackCount) {
            return false;
        }
        return matchFrom(0, ingredientCount, stackCount, tester, new boolean[stackCount]);
    }

    private static boolean matchFrom(int ingredientIndex, int ingredientCount, int stackCount,
                                     Tester tester, boolean[] used) {
        if (ingredientIndex >= ingredientCount) {
            return true;
        }
        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            if (used[stackIndex] || !tester.accepts(ingredientIndex, stackIndex)) {
                continue;
            }
            used[stackIndex] = true;
            if (matchFrom(ingredientIndex + 1, ingredientCount, stackCount, tester, used)) {
                return true;
            }
            used[stackIndex] = false;
        }
        return false;
    }

    private PhaseReactionRules() {}
}
