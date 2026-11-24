package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 93: Client -> Server XP withdrawal request for {@code xp_collector}
 * ({@code +1 Lv}, {@code +10 Lv}, or {@code All}).
 */
public record XpCollectorActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final Type<XpCollectorActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "xp_collector_action"));

    public static final StreamCodec<FriendlyByteBuf, XpCollectorActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.action());
            },
            buf -> new XpCollectorActionPayload(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
