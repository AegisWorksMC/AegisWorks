package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Phase 95: C2S control payload for {@code chunk_loader}. */
public record ChunkLoaderActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int ACTION_TOGGLE = 0;
    public static final int ACTION_RADIUS_DEC = 1;
    public static final int ACTION_RADIUS_INC = 2;

    public static final CustomPacketPayload.Type<ChunkLoaderActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    IndustrialCore.MOD_ID, "chunk_loader_action"));

    public static final StreamCodec<FriendlyByteBuf, ChunkLoaderActionPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ChunkLoaderActionPayload::pos,
                    ByteBufCodecs.VAR_INT, ChunkLoaderActionPayload::action,
                    ChunkLoaderActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
