package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Phase 95: C2S rename / visibility toggle / teleport / sync-request payload for {@code warp_pad}. */
public record WarpActionPayload(BlockPos pos, int action, String value) implements CustomPacketPayload {
    public static final int ACTION_RENAME = 0;
    public static final int ACTION_TOGGLE_PUBLIC = 1;
    public static final int ACTION_TELEPORT = 2;
    public static final int ACTION_REQUEST_SYNC = 3;

    public static final CustomPacketPayload.Type<WarpActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    IndustrialCore.MOD_ID, "warp_action"));

    public static final StreamCodec<FriendlyByteBuf, WarpActionPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WarpActionPayload::pos,
                    ByteBufCodecs.VAR_INT, WarpActionPayload::action,
                    ByteBufCodecs.stringUtf8(128), WarpActionPayload::value,
                    WarpActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
