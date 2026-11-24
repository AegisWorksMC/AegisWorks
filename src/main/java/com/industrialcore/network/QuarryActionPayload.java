package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server request for a quarry button action. */
public record QuarryActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_TOGGLE_PREVIEW = 2;
    public static final int ACTION_TOGGLE_RUNNING = 3;

    public static final Type<QuarryActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quarry_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuarryActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeVarInt(payload.action());
            },
            buffer -> new QuarryActionPayload(buffer.readBlockPos(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
