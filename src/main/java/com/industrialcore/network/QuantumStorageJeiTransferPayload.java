package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S payload requesting a server-authoritative JEI one-tap crafting recipe transfer into the
 * Quantum Storage Core's built-in 3x3 crafting grid.
 *
 * <p>Only carries the recipe id and {@code maxTransfer} flag; the server looks up the live
 * {@code CraftingRecipe} and pulls real items from Quantum Storage + player inventory, so a
 * forged packet can never spawn or duplicate items. Contains zero JEI imports for optionality.</p>
 */
public record QuantumStorageJeiTransferPayload(
        BlockPos pos,
        ResourceLocation recipeId,
        boolean maxTransfer
) implements CustomPacketPayload {

    public static final Type<QuantumStorageJeiTransferPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_storage_jei_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumStorageJeiTransferPayload> CODEC =
            StreamCodec.of(QuantumStorageJeiTransferPayload::encode, QuantumStorageJeiTransferPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, QuantumStorageJeiTransferPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeResourceLocation(payload.recipeId);
        buf.writeBoolean(payload.maxTransfer);
    }

    private static QuantumStorageJeiTransferPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        ResourceLocation recipeId = buf.readResourceLocation();
        boolean maxTransfer = buf.readBoolean();
        return new QuantumStorageJeiTransferPayload(pos, recipeId, maxTransfer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
