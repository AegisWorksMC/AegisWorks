package com.industrialcore.machine;

import java.util.Locale;

/** State of the auto crafter, mirrored to its GUI. */
public enum CrafterStatus {
    /** No recipe has been chosen yet. */
    NO_RECIPE,
    /** A recipe id is stored, but it no longer exists (for example after a data pack reload). */
    RECIPE_MISSING,
    /** The selected recipe needs items that are not in the input slots. */
    MISSING_INGREDIENTS,
    /** The internal FE buffer cannot pay for the next crafting tick. */
    NO_ENERGY,
    /** A craft is running. */
    CRAFTING,
    /** The result cannot be inserted into the output slot. */
    OUTPUT_FULL,
    /** Ready, but no work has started yet. */
    IDLE;

    public String translationKey() {
        return "gui.industrialcore.crafter.status." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isProblem() {
        return this == RECIPE_MISSING || this == MISSING_INGREDIENTS
                || this == NO_ENERGY || this == OUTPUT_FULL || this == NO_RECIPE;
    }
}
