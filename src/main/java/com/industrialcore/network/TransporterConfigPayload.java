package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server configuration request of a transporter (Phases 26-29).
 *
 * <p>The server checks distance, target block and - for the channel - the name rules before
 * anything changes; a modified client can therefore not set an oversized name, reach a machine
 * across the map, or bypass the role/toggle gates of the lower tiers. The value string is capped
 * at the wire level as well, so a hostile packet stays cheap to reject.</p>
 */
public record TransporterConfigPayload(BlockPos pos, int action, String value)
        implements CustomPacketPayload {

    public static final int ACTION_SET_CHANNEL = 0;
    public static final int ACTION_CYCLE_ROLE = 1;
    public static final int ACTION_TOGGLE_ITEM = 2;
    public static final int ACTION_TOGGLE_POWER = 3;

    /** Wire cap for the value; the channel rules are stricter (see TransportChannel). */
    private static final int MAX_VALUE_LENGTH = 64;

    public static final Type<TransporterConfigPayload> TYPE = new Type<>(ResourceLocation
            .fromNamespaceAndPath(IndustrialCore.MOD_ID, "transporter_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransporterConfigPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos());
                        buffer.writeVarInt(payload.action());
                        buffer.writeUtf(payload.value(), MAX_VALUE_LENGTH);
                    },
                    buffer -> new TransporterConfigPayload(buffer.readBlockPos(),
                            buffer.readVarInt(), buffer.readUtf(MAX_VALUE_LENGTH)));

    public static boolean isValidAction(int action) {
        return action >= ACTION_SET_CHANNEL && action <= ACTION_TOGGLE_POWER;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
