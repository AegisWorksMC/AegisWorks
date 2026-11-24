package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S request asking the server to push a fresh {@link MachineProgressSyncPayload} snapshot
 * (sent when the player opens the Phase 85 Skill Tree screen).
 */
public record RequestProgressPayload() implements CustomPacketPayload {
    public static final RequestProgressPayload INSTANCE = new RequestProgressPayload();

    public static final Type<RequestProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "request_machine_progress"));

    public static final StreamCodec<ByteBuf, RequestProgressPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
