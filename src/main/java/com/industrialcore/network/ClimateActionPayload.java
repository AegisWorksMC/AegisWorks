package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Phase 95: C2S weather/time control payload for {@code climate_controller}. */
public record ClimateActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int ACTION_CLEAR = 0;
    public static final int ACTION_RAIN = 1;
    public static final int ACTION_THUNDER = 2;
    public static final int ACTION_DAY = 3;
    public static final int ACTION_NIGHT = 4;
    public static final int ACTION_TOGGLE_LOCK_CLEAR = 5;
    public static final int ACTION_TOGGLE_LOCK_DAY = 6;

    public static final CustomPacketPayload.Type<ClimateActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    IndustrialCore.MOD_ID, "climate_action"));

    public static final StreamCodec<FriendlyByteBuf, ClimateActionPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ClimateActionPayload::pos,
                    ByteBufCodecs.VAR_INT, ClimateActionPayload::action,
                    ClimateActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
