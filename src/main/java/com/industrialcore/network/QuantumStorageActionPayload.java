package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Phase 89 (C2S): Requests server-authoritative Quantum Storage Core actions
 * (extract item, deposit carried stack/bucket, interact with fluid cell, clear craft grid, dump).
 */
public record QuantumStorageActionPayload(
        BlockPos pos,
        int action,
        int mode,
        ItemStack targetItem,
        FluidStack targetFluid
) implements CustomPacketPayload {

    public static final int ACTION_EXTRACT_ITEM = 0;
    public static final int ACTION_DEPOSIT_CARRIED = 1;
    public static final int ACTION_INTERACT_FLUID = 2;
    public static final int ACTION_CLEAR_CRAFT = 3;
    public static final int ACTION_DEPOSIT_MATCHING = 4;

    public static final int MODE_FULL_TO_CURSOR = 0;
    public static final int MODE_HALF_TO_CURSOR = 1;
    public static final int MODE_SHIFT_TO_INV = 2;
    public static final int MODE_PULL_ONE_TO_INV = 3;
    public static final int MODE_PUSH_ONE_FROM_INV = 4;

    public static final Type<QuantumStorageActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_storage_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumStorageActionPayload> CODEC =
            StreamCodec.of(QuantumStorageActionPayload::encode, QuantumStorageActionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, QuantumStorageActionPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.action);
        buf.writeVarInt(payload.mode);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.targetItem);
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.targetFluid);
    }

    private static QuantumStorageActionPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int action = buf.readVarInt();
        int mode = buf.readVarInt();
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new QuantumStorageActionPayload(pos, action, mode, item, fluid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
