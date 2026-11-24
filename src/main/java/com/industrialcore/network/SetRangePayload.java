package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server request to set the quarry area. The server clamps every value. */
public record SetRangePayload(BlockPos pos, int width, int depth, int height) implements CustomPacketPayload {
    public static final Type<SetRangePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "set_range"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRangePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeVarInt(payload.width());
                buffer.writeVarInt(payload.depth());
                buffer.writeVarInt(payload.height());
            },
            buffer -> new SetRangePayload(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
