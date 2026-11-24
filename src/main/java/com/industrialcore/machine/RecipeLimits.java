package com.industrialcore.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

/**
 * The bound an ingredient list may not exceed, shared by the JSON side and the network side of the
 * mod's data driven recipes.
 *
 * <p>Every recipe travels to a joining client inside
 * {@code clientbound/minecraft:update_recipes}. The recipe's own serializer writes its ingredient
 * list through {@code ByteBufCodecs.list(n)}, which delegates to {@code ByteBufCodecs.writeCount}:
 * a list longer than {@code n} throws an {@code EncoderException} while the packet is being built.
 * The packet is sent before the player is added to the world, so one over-long recipe does not
 * break that recipe alone. The connection fails with {@code 10 elements exceeded max size of: 8}
 * and the world cannot be entered at all, which is exactly what a hard-coded {@code list(8)} next
 * to an unbounded JSON list did.</p>
 *
 * <p>Both sides therefore use this one constant. {@link #ingredientList} is the JSON codec, which
 * refuses to load more than {@link #MAX_INGREDIENTS} entries with a message that names the recipe
 * kind and the count, and {@code ByteBufCodecs.list(RecipeLimits.MAX_INGREDIENTS)} is the network
 * codec, which can then never be handed a list it would refuse.</p>
 *
 * <p>16 leaves room for the next stages of a line while staying far below anything the read side
 * would accept. The largest shipped recipe is {@code synthesis_ultimate} with 10 entries; the
 * assembler and the phase reactor recipes have 3 each.</p>
 */
public final class RecipeLimits {
    /** The most ingredient entries one recipe of this mod may list. */
    public static final int MAX_INGREDIENTS = 16;

    /**
     * The JSON codec of an ingredient list: all elements, but at most {@link #MAX_INGREDIENTS} of
     * them.
     *
     * <p>A longer list is refused when the server loads the recipe, so a data pack that outgrows
     * the limit sees a load error that names the count instead of a packet that cannot be encoded
     * later. {@code recipeKind} is the recipe type without its namespace, used in that message.</p>
     */
    public static <T> Codec<List<T>> ingredientList(String recipeKind, Codec<T> elementCodec) {
        return elementCodec.listOf().validate(entries -> {
            if (entries.size() <= MAX_INGREDIENTS) {
                return DataResult.success(entries);
            }
            return DataResult.error(() -> recipeKind + " recipe lists " + entries.size()
                    + " ingredients, but at most " + MAX_INGREDIENTS
                    + " are supported (RecipeLimits.MAX_INGREDIENTS)");
        });
    }

    private RecipeLimits() {}
}
