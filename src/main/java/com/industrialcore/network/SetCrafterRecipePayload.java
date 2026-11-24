package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change the auto crafter's selected recipe.
 *
 * <p>Only the recipe id is sent; the server looks the recipe up in its own recipe manager and
 * rejects unknown, unsupported or non-crafting recipes. An empty id clears the selection.</p>
 */
public record SetCrafterRecipePayload(BlockPos pos, String recipeId) implements CustomPacketPayload {
    /** Longest id that can be transmitted; real ids are far shorter. */
    public static final int MAX_ID_LENGTH = 256;

    public static final Type<SetCrafterRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "set_crafter_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCrafterRecipePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeUtf(payload.recipeId(), MAX_ID_LENGTH);
            },
            buffer -> new SetCrafterRecipePayload(buffer.readBlockPos(), buffer.readUtf(MAX_ID_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
