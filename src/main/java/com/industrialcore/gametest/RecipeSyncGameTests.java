package com.industrialcore.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.RecipeLimits;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModRecipes;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The recipe sync through the real codecs: what a joining player is sent, and what a recipe file
 * that outgrows the shared bound does.
 *
 * <p>A recipe reaches a client inside {@code clientbound/minecraft:update_recipes}, whose payload
 * is built with the recipe's own network codec. That packet is sent before the player is added to
 * the world, so a recipe that cannot be encoded does not fail as a recipe: the connection fails
 * and the world cannot be entered at all. The shipped mod hit exactly that with a hard-coded
 * {@code ByteBufCodecs.list(8)} next to a JSON side that accepted any number of entries, once the
 * {@code synthesis_ultimate} recipe reached ten.</p>
 *
 * <p>These tests therefore build the packet out of every recipe the server holds, decode it back,
 * and drive an over-long recipe through the load codec. When the packet cannot be encoded, the
 * failing recipes are named, because an {@code EncoderException} on its own does not say which
 * data file broke — and the packet is one blob, so it is all-or-nothing.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSyncGameTests {
    /** Every synthesis recipe that ships; the largest one is what broke the sync. */
    private static final List<String> SYNTHESIS_RECIPES = List.of(
            "industrialcore:synthesis_1",
            "industrialcore:synthesis_2",
            "industrialcore:synthesis_3",
            "industrialcore:synthesis_4",
            "industrialcore:synthesis_5",
            "industrialcore:synthesis_6",
            "industrialcore:synthesis_catalyst_bulk",
            "industrialcore:synthesis_high_density",
            "industrialcore:synthesis_unstable_ultimate",
            "industrialcore:synthesis_ultimate");

    private RecipeSyncGameTests() {}

    /**
     * Every recipe the server holds survives the recipe sync packet, and the client can read the
     * packet back. All the synthesis recipes have to be loaded first: a file that fails to parse is
     * dropped without a word, so "it did not crash" is only meaningful together with "the recipe is
     * actually there".
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void everyLoadedRecipeEncodesIntoTheRecipeSyncPacket(GameTestHelper helper) {
        Collection<RecipeHolder<?>> recipes = helper.getLevel().getRecipeManager()
                .getOrderedRecipes();
        helper.assertTrue(!recipes.isEmpty(), "the recipe manager holds no recipe at all");

        for (String expected : SYNTHESIS_RECIPES) {
            helper.assertTrue(hasRecipe(recipes, expected),
                    expected + " is missing from the recipe manager: a recipe that fails to load is "
                            + "dropped silently");
        }

        RegistryAccess registries = helper.getLevel().registryAccess();
        ByteBuf raw = Unpooled.buffer();
        try {
            try {
                ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(
                        buffer(raw, registries), new ClientboundUpdateRecipesPacket(recipes));
            } catch (RuntimeException exception) {
                helper.fail("the recipe sync packet of " + recipes.size() + " recipes cannot be "
                        + "encoded (" + exception.getClass().getSimpleName() + ": "
                        + exception.getMessage() + "), so no player can join this world; "
                        + "offending recipe(s): " + offenders(registries, recipes));
                return;
            }
            helper.assertTrue(raw.readableBytes() > 0, "the recipe sync packet encoded to nothing");

            ClientboundUpdateRecipesPacket decoded;
            try {
                decoded = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(
                        buffer(raw, registries));
            } catch (RuntimeException exception) {
                helper.fail("the client cannot read back the recipe sync packet ("
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage()
                        + ")");
                return;
            }
            helper.assertTrue(decoded.getRecipes().size() == recipes.size(),
                    "the packet carried " + decoded.getRecipes().size() + " recipes instead of "
                            + recipes.size());
        } finally {
            raw.release();
        }
        helper.succeed();
    }

    /**
     * An ingredient list that outgrows the bound is refused while the recipe is loaded, with a
     * message that names the limit and the recipe kind, and a recipe at the limit still encodes.
     * That is the contract the two codecs have to share: the JSON side is the gate, the network
     * side is the guarantee.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void anOverLongIngredientListIsRefusedAtLoad(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                helper.getLevel().registryAccess());

        DataResult<SynthesisRecipe> overTheLimit =
                parseSynthesis(ops, RecipeLimits.MAX_INGREDIENTS + 1);
        helper.assertTrue(overTheLimit.error().isPresent(),
                "a synthesis recipe with " + (RecipeLimits.MAX_INGREDIENTS + 1) + " ingredients "
                        + "loaded, so the only place it can fail is the recipe sync packet, where "
                        + "the message does not name the recipe");
        String message = overTheLimit.error().get().message();
        helper.assertTrue(message.contains(String.valueOf(RecipeLimits.MAX_INGREDIENTS)),
                "the load error must name the limit, was: " + message);
        helper.assertTrue(message.contains("synthesis"),
                "the load error must name the recipe kind, was: " + message);

        DataResult<SynthesisRecipe> atTheLimit = parseSynthesis(ops, RecipeLimits.MAX_INGREDIENTS);
        helper.assertTrue(atTheLimit.result().isPresent(),
                "a synthesis recipe with exactly " + RecipeLimits.MAX_INGREDIENTS + " ingredients "
                        + "must still load, but: " + atTheLimit.error());

        ByteBuf raw = Unpooled.buffer();
        try {
            try {
                SynthesisRecipe.STREAM_CODEC.encode(
                        buffer(raw, helper.getLevel().registryAccess()), atTheLimit.result().get());
            } catch (RuntimeException exception) {
                helper.fail("a recipe with exactly " + RecipeLimits.MAX_INGREDIENTS
                        + " ingredients does not encode ("
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage()
                        + "), so the network bound is smaller than the JSON bound");
                return;
            }
            helper.assertTrue(raw.readableBytes() > 0,
                    "the recipe of the limit encoded to nothing");
        } finally {
            raw.release();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * A buffer of the kind a client reads: the sync to a modded client is encoded with the Neo
     * connection type, which the codecs may branch on.
     */
    private static RegistryFriendlyByteBuf buffer(ByteBuf raw, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(raw, registries, ConnectionType.NEOFORGE);
    }

    /** A synthesis recipe of the given number of entries, as the JSON codec would read it. */
    private static DataResult<SynthesisRecipe> parseSynthesis(RegistryOps<JsonElement> ops,
                                                             int ingredients) {
        JsonArray list = new JsonArray();
        for (int index = 0; index < ingredients; index++) {
            JsonObject entry = new JsonObject();
            JsonObject item = new JsonObject();
            item.addProperty("item", "industrialcore:adamantite_ingot");
            entry.add("ingredient", item);
            entry.addProperty("count", 1);
            list.add(entry);
        }
        JsonObject result = new JsonObject();
        result.addProperty("id", "industrialcore:synthetic_ore_1");
        result.addProperty("count", 1);

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "industrialcore:synthesis");
        recipe.add("ingredients", list);
        recipe.add("result", result);
        return ModRecipes.SYNTHESIS_SERIALIZER.get().codec().codec().parse(ops, recipe);
    }

    /** The recipes of the packet that fail on their own, so the failure names a data file. */
    private static List<String> offenders(RegistryAccess registries,
                                          Collection<RecipeHolder<?>> recipes) {
        List<String> failing = new ArrayList<>();
        for (RecipeHolder<?> recipe : recipes) {
            ByteBuf raw = Unpooled.buffer();
            try {
                RecipeHolder.STREAM_CODEC.encode(buffer(raw, registries), recipe);
            } catch (RuntimeException exception) {
                failing.add(recipe.id() + " (" + exception.getMessage() + ")");
            } finally {
                raw.release();
            }
        }
        if (failing.isEmpty()) {
            failing.add("no single recipe fails alone, so the packet itself is at fault");
        }
        return failing;
    }

    private static boolean hasRecipe(Collection<RecipeHolder<?>> recipes, String id) {
        for (RecipeHolder<?> recipe : recipes) {
            if (recipe.id().toString().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
