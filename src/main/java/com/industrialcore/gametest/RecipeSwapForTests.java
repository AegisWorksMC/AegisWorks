package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test-only swap of the ore line's production duration, so a GameTest can measure a machine that
 * really works for another number of ticks instead of only scheduling it that way.
 *
 * <p><b>Safety premise (do not break it).</b> {@link #install} and {@link Swap#restore} are called
 * from the same GameTest method, one straight after the other: between the swap and the restore
 * there is no asynchronous work, no waiting and no game tick boundary, so the shared
 * {@link RecipeManager} shows the swapped recipes only while that one synchronous method body
 * runs. No other GameTest can observe them (the server thread is inside this method), normal play
 * never sees them, and the {@code finally} in the calling test restores the original
 * {@link RecipeHolder} list even when an assertion throws.</p>
 *
 * <p><b>Scope.</b> Only recipes of the five chain recipe types the ore line probe builds its plan
 * from - crushing, electrolysis, precision crushing, pressing and synthesis - that carry the
 * production duration ({@value #PRODUCTION_TICKS} ticks) are swapped. Everything else keeps its
 * holder instance: other recipe types (alloy, assembler, phase reaction, smelting, ...) and chain
 * recipes with a different duration (the 60/80 tick plates, the 120 tick ore crushing, the 200
 * tick bulk catalyst). The replacement keeps the id, the type, the position in the list, the
 * inputs, the output, the output count and the FE per tick; only the duration changes, and that
 * is verified by content comparison before the swap and by instance identity after the restore.</p>
 *
 * <p>All diagnostics are logged with the {@code [swap]} tag.</p>
 */
public final class RecipeSwapForTests {
    /** The production duration the chain recipes ship with and must be restored to. */
    public static final int PRODUCTION_TICKS = 10;

    private RecipeSwapForTests() {}

    /**
     * Replaces every production-duration chain recipe with the same recipe carrying
     * {@code ticks} instead. The returned {@link Swap} holds the snapshot used for the restore.
     *
     * @param manager the live server recipe manager (never a client copy)
     * @param ticks   the duration the swapped recipes should carry instead
     */
    public static Swap install(RecipeManager manager, int ticks) {
        Set<RecipeType<?>> chainTypes = Set.of(ModRecipes.CRUSHING_TYPE.get(),
                ModRecipes.ELECTROLYSIS_TYPE.get(), ModRecipes.PRECISION_CRUSHING_TYPE.get(),
                ModRecipes.PRESSING_TYPE.get(), ModRecipes.SYNTHESIS_TYPE.get());
        List<RecipeHolder<?>> snapshot = new ArrayList<>(manager.getRecipes());
        Swap swap = new Swap(manager, snapshot, ticks);

        List<RecipeHolder<?>> replacement = new ArrayList<>(snapshot.size());
        for (RecipeHolder<?> holder : snapshot) {
            replacement.add(swap.replacementFor(holder, chainTypes, ticks));
        }
        if (swap.swappedIds.isEmpty()) {
            swap.swapSummary = "no chain recipe carries " + PRODUCTION_TICKS + " ticks";
            IndustrialCore.LOGGER.warn("[swap] {}", swap.swapSummary);
            swap.swapped = false;
            return swap;
        }

        try {
            manager.replaceRecipes(replacement);
            swap.swapped = swap.verifyInstalled();
        } catch (RuntimeException error) {
            // Never leave the shared manager half swapped: put the snapshot back, then report.
            try {
                manager.replaceRecipes(snapshot);
            } catch (RuntimeException ignored) {
                swap.restoreSummary = "emergency restore also failed after " + error;
            }
            swap.swapped = false;
            swap.swapSummary = "replaceRecipes failed: " + error;
            IndustrialCore.LOGGER.error("[swap] {}", swap.swapSummary);
            throw error;
        }
        IndustrialCore.LOGGER.info("[swap] installed {} recipes at {} ticks [{}]",
                swap.swappedIds.size(), ticks, swap.swapSummary);
        return swap;
    }

    /**
     * The snapshot plus everything needed to prove that only the duration changed and that the
     * original list came back untouched. Created by {@link #install}; not useful on its own.
     */
    public static final class Swap {
        private final RecipeManager manager;
        private final List<RecipeHolder<?>> snapshot;
        private final Map<ResourceLocation, RecipeHolder<?>> originalById = new LinkedHashMap<>();
        private final List<ResourceLocation> swappedIds = new ArrayList<>();
        private final Map<ResourceLocation, Integer> originalTimes = new LinkedHashMap<>();
        private final Map<ResourceLocation, Recipe<?>> swappedRecipes = new LinkedHashMap<>();
        private final List<String> contentProblems = new ArrayList<>();
        private boolean swapped;
        private boolean restored;
        private final int installedTicks;
        private String swapSummary = "not installed";
        private String restoreSummary = "not restored";

        private Swap(RecipeManager manager, List<RecipeHolder<?>> snapshot, int ticks) {
            this.manager = manager;
            this.snapshot = snapshot;
            this.installedTicks = ticks;
            for (RecipeHolder<?> holder : snapshot) {
                this.originalById.put(holder.id(), holder);
            }
        }

        /** How many recipes the swap really replaced (0 would mean the test measured nothing). */
        public int swappedCount() {
            return this.swappedIds.size();
        }

        /** True when the manager really serves the swapped duration for every swapped id. */
        public boolean isSwapped() {
            return this.swapped;
        }

        /** True when the original holders are back, in order, at the production duration. */
        public boolean isRestored() {
            return this.restored;
        }

        public String swapSummary() {
            return this.swapSummary;
        }

        public String restoreSummary() {
            return this.restoreSummary;
        }

        /** Builds the replacement holder for one recipe, or the original holder when out of scope. */
        private RecipeHolder<?> replacementFor(RecipeHolder<?> holder,
                                               Set<RecipeType<?>> chainTypes, int ticks) {
            Recipe<?> original = holder.value();
            boolean chainClass = original instanceof CrushingRecipe
                    || original instanceof ProcessorRecipe || original instanceof SynthesisRecipe;
            if (!chainClass || !chainTypes.contains(original.getType())) {
                return holder;
            }
            int time = timeOf(original);
            if (time != PRODUCTION_TICKS) {
                return holder;
            }
            Recipe<?> copy = withTicks(original, ticks);
            if (copy == null) {
                return holder;
            }
            if (!sameContent(original, copy)) {
                this.contentProblems.add(holder.id() + " changed more than its duration");
            }
            this.swappedIds.add(holder.id());
            this.originalTimes.put(holder.id(), time);
            this.swappedRecipes.put(holder.id(), copy);
            return new RecipeHolder<>(holder.id(), copy);
        }

        /** Re-reads every swapped id from the manager and checks the duration really is the new one. */
        private boolean verifyInstalled() {
            List<String> problems = new ArrayList<>(this.contentProblems);
            int verified = 0;
            for (ResourceLocation id : this.swappedIds) {
                Optional<RecipeHolder<?>> holder = this.manager.byKey(id);
                if (holder.isEmpty()) {
                    problems.add(id + " missing after the swap");
                    continue;
                }
                if (holder.get().value() != this.swappedRecipes.get(id)) {
                    problems.add(id + " is not the swapped holder instance");
                    continue;
                }
                int time = timeOf(holder.get().value());
                if (time != this.installedTicks) {
                    problems.add(String.format(Locale.ROOT, "%s is %d ticks instead of %d", id,
                            time, this.installedTicks));
                    continue;
                }
                verified++;
            }
            this.swapSummary = verified + "/" + this.swappedIds.size() + " recipes re-read at "
                    + this.installedTicks + " ticks, first=" + this.swappedIds.get(0)
                    + ", last=" + this.swappedIds.get(this.swappedIds.size() - 1)
                    + (problems.isEmpty() ? "" : "; problems: " + problems);
            if (!problems.isEmpty()) {
                IndustrialCore.LOGGER.error("[swap] install check failed: {}", problems);
            }
            return problems.isEmpty();
        }

        /**
         * Puts the snapshotted holder list back. Never throws: a failure is recorded so the calling
         * test can report it after its own measurement finished.
         */
        void restore() {
            List<String> problems = new ArrayList<>();
            try {
                this.manager.replaceRecipes(this.snapshot);
                List<ResourceLocation> now = new ArrayList<>();
                for (RecipeHolder<?> holder : this.manager.getRecipes()) {
                    now.add(holder.id());
                }
                List<ResourceLocation> before = new ArrayList<>();
                for (RecipeHolder<?> holder : this.snapshot) {
                    before.add(holder.id());
                }
                if (!now.equals(before)) {
                    problems.add("recipe list differs after restore (" + now.size() + " vs "
                            + before.size() + ")");
                }
                for (Map.Entry<ResourceLocation, Integer> entry : this.originalTimes.entrySet()) {
                    ResourceLocation id = entry.getKey();
                    Optional<RecipeHolder<?>> holder = this.manager.byKey(id);
                    if (holder.isEmpty()) {
                        problems.add(id + " missing after restore");
                        continue;
                    }
                    if (holder.get() != this.originalById.get(id)) {
                        problems.add(id + " holder instance differs after restore");
                        continue;
                    }
                    int time = timeOf(holder.get().value());
                    if (time != entry.getValue()) {
                        problems.add(String.format(Locale.ROOT, "%s is %d ticks instead of %d",
                                id, time, entry.getValue()));
                    }
                }
            } catch (RuntimeException error) {
                problems.add("restore threw " + error);
            }
            this.restored = problems.isEmpty();
            this.restoreSummary = this.restored
                    ? this.originalTimes.size() + " recipes back at " + PRODUCTION_TICKS
                        + " ticks, " + this.snapshot.size() + " recipes, order preserved"
                    : String.join("; ", problems);
            IndustrialCore.LOGGER.info("[swap] restore: {}", this.restoreSummary);
        }
    }

    // ------------------------------------------------------------------
    // recipe helpers
    // ------------------------------------------------------------------

    /** The duration of a chain recipe, or {@code -1} for a recipe type outside the swap. */
    private static int timeOf(Recipe<?> recipe) {
        if (recipe instanceof CrushingRecipe crushing) {
            return crushing.timeTicks();
        }
        if (recipe instanceof ProcessorRecipe processor) {
            return processor.timeTicks();
        }
        if (recipe instanceof SynthesisRecipe synthesis) {
            return synthesis.timeTicks();
        }
        return -1;
    }

    /** The same recipe carrying {@code ticks} instead: same id, inputs, output, count and FE/t. */
    private static Recipe<?> withTicks(Recipe<?> recipe, int ticks) {
        if (recipe instanceof CrushingRecipe crushing) {
            return new CrushingRecipe(crushing.ingredient(), crushing.result().copy(), ticks,
                    crushing.energyPerTick());
        }
        if (recipe instanceof ProcessorRecipe processor) {
            return new ProcessorRecipe(processor.ingredient(), processor.result().copy(), ticks,
                    processor.energyPerTick(), processor.family());
        }
        if (recipe instanceof SynthesisRecipe synthesis) {
            return new SynthesisRecipe(synthesis.ingredients(), synthesis.result().copy(), ticks,
                    synthesis.energyPerTick());
        }
        return null;
    }

    /** Everything but the duration must be identical; this is what the swap asserts. */
    private static boolean sameContent(Recipe<?> original, Recipe<?> swapped) {
        ItemStack originalResult = resultOf(original);
        ItemStack swappedResult = resultOf(swapped);
        if (originalResult.isEmpty() != swappedResult.isEmpty()) {
            return false;
        }
        if (!originalResult.isEmpty() && (!ItemStack.isSameItemSameComponents(originalResult,
                swappedResult) || originalResult.getCount() != swappedResult.getCount())) {
            return false;
        }
        if (energyOf(original) != energyOf(swapped)) {
            return false;
        }
        if (original instanceof CrushingRecipe left && swapped instanceof CrushingRecipe right) {
            return left.ingredient() == right.ingredient();
        }
        if (original instanceof ProcessorRecipe left && swapped instanceof ProcessorRecipe right) {
            return left.ingredient() == right.ingredient() && left.family() == right.family();
        }
        if (original instanceof SynthesisRecipe left && swapped instanceof SynthesisRecipe right) {
            if (left.ingredients().size() != right.ingredients().size()) {
                return false;
            }
            for (int index = 0; index < left.ingredients().size(); index++) {
                SynthesisRecipe.Entry before = left.ingredients().get(index);
                SynthesisRecipe.Entry after = right.ingredients().get(index);
                if (before.ingredient() != after.ingredient()
                        || before.count() != after.count()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static ItemStack resultOf(Recipe<?> recipe) {
        if (recipe instanceof CrushingRecipe crushing) {
            return crushing.result();
        }
        if (recipe instanceof ProcessorRecipe processor) {
            return processor.result();
        }
        if (recipe instanceof SynthesisRecipe synthesis) {
            return synthesis.result();
        }
        return ItemStack.EMPTY;
    }

    private static int energyOf(Recipe<?> recipe) {
        if (recipe instanceof CrushingRecipe crushing) {
            return crushing.energyPerTick();
        }
        if (recipe instanceof ProcessorRecipe processor) {
            return processor.energyPerTick();
        }
        if (recipe instanceof SynthesisRecipe synthesis) {
            return synthesis.energyPerTick();
        }
        return -1;
    }
}
