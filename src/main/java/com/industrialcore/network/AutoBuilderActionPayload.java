package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 94: Client -> Server configuration payload for the Auto Builder ({@code auto_builder}):
 * toggle running state, cycle build pattern (FLOOR / WALL / HOLLOW_BOX), reset scan, and adjust
 * W / D / H dimensions.
 */
public record AutoBuilderActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int ACTION_TOGGLE_RUN = 0;
    public static final int ACTION_CYCLE_MODE = 1;
    public static final int ACTION_RESET = 2;
    public static final int ACTION_W_DEC = 3;
    public static final int ACTION_W_INC = 4;
    public static final int ACTION_D_DEC = 5;
    public static final int ACTION_D_INC = 6;
    public static final int ACTION_H_DEC = 7;
    public static final int ACTION_H_INC = 8;

    public static final CustomPacketPayload.Type<AutoBuilderActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    IndustrialCore.MOD_ID, "auto_builder_action"));

    public static final StreamCodec<FriendlyByteBuf, AutoBuilderActionPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, AutoBuilderActionPayload::pos,
                    ByteBufCodecs.VAR_INT, AutoBuilderActionPayload::action,
                    AutoBuilderActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
