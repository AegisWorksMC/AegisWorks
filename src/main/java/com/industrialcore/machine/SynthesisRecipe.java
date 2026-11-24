package com.industrialcore.machine;

import com.industrialcore.registry.ModRecipes;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Data-driven synthetic ore synthesis ({@code industrialcore:synthesis}).
 *
 * <p>A dedicated type: {@code phase_reaction} would make every synthesis recipe runnable in the
 * existing phase reactor, which would quietly change what that machine can do. The synthesis
 * machine therefore owns its own type, exactly like the assembler and the reactor own theirs.</p>
 *
 * <p>Unlike the phase reaction, an entry carries a <em>count</em>, because the line needs four
 * adamantite or two synthetic ore per craft while the machine has only three slots. The recipe
 * consumes {@code count} items from the matched slot, so a run can never take less than listed.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * {
 *   "type": "industrialcore:synthesis",
 *   "ingredients": [
 *     { "ingredient": { "item": "industrialcore:adamantite_ingot" }, "count": 4 },
 *     { "ingredient": { "item": "industrialcore:mithril_ingot" }, "count": 4 },
 *     { "ingredient": { "item": "industrialcore:resonant_catalyst" }, "count": 1 }
 *   ],
 *   "result": { "id": "industrialcore:synthetic_ore_1", "count": 1 },
 *   "time": 200,
 *   "energy": 20
 * }
 * }</pre>
 */
public record SynthesisRecipe(List<Entry> ingredients, ItemStack result, int timeTicks,
                              int energyPerTick) implements Recipe<SynthesisRecipe.Input> {

    /** One required stack: an ingredient and how many items of it the run consumes. */
    public record Entry(Ingredient ingredient, int count) {
        public static MapCodec<Entry> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient")
                                    .forGetter(Entry::ingredient),
                            Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count))
                    .apply(instance, Entry::new));
        }

        public static StreamCodec<RegistryFriendlyByteBuf, Entry> streamCodec() {
            return StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, Entry::ingredient,
                    ByteBufCodecs.VAR_INT, Entry::count,
                    Entry::new);
        }

        public Entry {
            count = Math.max(1, count);
        }
    }

    /** The three ingredient slots of the synthesizer. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index >= 0 && index < this.stacks.size() ? this.stacks.get(index) : ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return this.stacks.size();
        }
    }

    public static MapCodec<SynthesisRecipe> codec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        RecipeLimits.ingredientList("synthesis", Entry.codec().codec())
                                .fieldOf("ingredients")
                                .forGetter(SynthesisRecipe::ingredients),
                        ItemStack.STRICT_CODEC.fieldOf("result")
                                .forGetter(SynthesisRecipe::result),
                        Codec.INT.optionalFieldOf("time", SynthesisBalance.TIME_TICKS)
                                .forGetter(SynthesisRecipe::timeTicks),
                        Codec.INT.optionalFieldOf("energy", SynthesisBalance.ENERGY_PER_TICK)
                                .forGetter(SynthesisRecipe::energyPerTick))
                .apply(instance, SynthesisRecipe::new));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, SynthesisRecipe> streamCodec() {
        return StreamCodec.composite(
                // The same bound as the JSON codec above: a recipe that loaded can always be sent.
                Entry.streamCodec().apply(ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)),
                SynthesisRecipe::ingredients,
                ItemStack.STREAM_CODEC, SynthesisRecipe::result,
                ByteBufCodecs.VAR_INT, SynthesisRecipe::timeTicks,
                ByteBufCodecs.VAR_INT, SynthesisRecipe::energyPerTick,
                SynthesisRecipe::new);
    }

    public SynthesisRecipe {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        timeTicks = Math.max(1, timeTicks);
        energyPerTick = Math.max(0, energyPerTick);
    }

    /**
     * Matches the slots against the recipe and returns, for each ingredient, the slot it is taken
     * from. {@code null} when the slots do not satisfy the recipe.
     *
     * <p>Order does not matter; the number of non-empty slots must equal the number of entries, so
     * a leftover item never disappears.</p>
     */
    public int[] matchSlots(List<ItemStack> slots) {
        if (this.ingredients.isEmpty()) {
            return null;
        }
        int[] mapping = new int[this.ingredients.size()];
        java.util.Arrays.fill(mapping, -1);
        return assign(0, slots, new boolean[slots.size()], mapping) ? mapping : null;
    }

    /**
     * Assigns every entry to a distinct slot with enough items. Extra slots are ignored, so a
     * stray item in an unused slot never blocks the machine and is never consumed.
     */
    private boolean assign(int ingredientIndex, List<ItemStack> slots, boolean[] used,
                           int[] mapping) {
        if (ingredientIndex == this.ingredients.size()) {
            return true;
        }
        Entry entry = this.ingredients.get(ingredientIndex);
        for (int slot = 0; slot < slots.size(); slot++) {
            if (used[slot]) {
                continue;
            }
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty() || stack.getCount() < entry.count()
                    || !entry.ingredient().test(stack)) {
                continue;
            }
            used[slot] = true;
            mapping[ingredientIndex] = slot;
            if (assign(ingredientIndex + 1, slots, used, mapping)) {
                return true;
            }
            used[slot] = false;
            mapping[ingredientIndex] = -1;
        }
        return false;
    }

    public boolean matchesStacks(List<ItemStack> slots) {
        return matchSlots(slots) != null;
    }

    /**
     * How many items one run consumes in total - the sum of the entry counts, not the slot count.
     * Used only to break ties between recipes that need the same number of slots.
     */
    public int totalItems() {
        int total = 0;
        for (Entry entry : this.ingredients) {
            total += entry.count();
        }
        return total;
    }

    /** True when any entry accepts the item (used by the slot filters). */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Entry entry : this.ingredients) {
            if (entry.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return matchesStacks(input.stacks());
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
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
        for (Entry entry : this.ingredients) {
            list.add(entry.ingredient());
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SYNTHESIS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.SYNTHESIS_TYPE.get();
    }

    /**
     * Finds the recipe for these slots; {@code null} when nothing matches.
     *
     * <p>Several recipes can match the same slots, because {@link #matchSlots} ignores the slots a
     * recipe does not use: loading the ten ingredients of the ultimate catalyst also satisfies
     * {@code synthesis_1}, whose three entries are a subset of them. Returning the first match
     * would then hand back whichever recipe the (unordered) recipe list happened to list first,
     * and the machine would quietly craft synthetic ore I instead of the ultimate catalyst.
     * The most specific match therefore wins:</p>
     *
     * <ol>
     *   <li>more ingredient entries first - the recipe that needs the most distinct stacks;</li>
     *   <li>then more items consumed in total, as the second tie-break;</li>
     *   <li>then the result item id, so equal-sized candidates resolve the same way every run
     *       regardless of the order of the recipe list.</li>
     * </ol>
     *
     * <p>The slot count and the item count are kept apart on purpose: {@code synthesis_ultimate}
     * has ten entries of one item each while {@code synthesis_catalyst_bulk} has two entries that
     * consume three items, and only the entry count says which recipe is the narrower one.</p>
     */
    public static SynthesisRecipe find(List<SynthesisRecipe> recipes, List<ItemStack> stacks) {
        SynthesisRecipe best = null;
        for (int index = 0; index < recipes.size(); index++) {
            SynthesisRecipe candidate = recipes.get(index);
            if (best != null && candidate.ingredients.size() < best.ingredients.size()) {
                // Cannot win on the first key, so the slot match is skipped: this keeps the
                // common case (one ten-entry match among a hundred three-entry recipes) cheap.
                continue;
            }
            if (!candidate.matchesStacks(stacks)) {
                continue;
            }
            if (best == null || isMoreSpecific(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /** True when {@code candidate} is the narrower of two recipes that both match the slots. */
    private static boolean isMoreSpecific(SynthesisRecipe candidate, SynthesisRecipe current) {
        int byEntries = Integer.compare(candidate.ingredients.size(), current.ingredients.size());
        if (byEntries != 0) {
            return byEntries > 0;
        }
        int byItems = Integer.compare(candidate.totalItems(), current.totalItems());
        if (byItems != 0) {
            return byItems > 0;
        }
        return resultId(candidate.result()).compareTo(resultId(current.result())) < 0;
    }

    /** Registry id of a result stack; empty for an unregistered item, which keeps the order total. */
    private static String resultId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** True when any recipe accepts the item (used by the slot filters). */
    public static boolean anyRecipeAccepts(List<SynthesisRecipe> recipes, ItemStack stack) {
        for (SynthesisRecipe recipe : recipes) {
            if (recipe.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Serializer of the synthesis recipe type. */
    public static final class Serializer implements RecipeSerializer<SynthesisRecipe> {
        @Override
        public MapCodec<SynthesisRecipe> codec() {
            return SynthesisRecipe.codec();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SynthesisRecipe> streamCodec() {
            return SynthesisRecipe.streamCodec();
        }
    }
}
