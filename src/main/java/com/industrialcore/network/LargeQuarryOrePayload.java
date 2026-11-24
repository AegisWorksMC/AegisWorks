package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change which ores the large quarry may mine (Phase 22).
 *
 * <p>The payload only names an action and an index into the server's own candidate list. It never
 * carries block ids: the server resolves the index against the live {@code c:ores} tag, so a
 * modified client cannot select a block that is not an ore, and a stale client cannot select the
 * wrong block after a datapack change.</p>
 *
 * @param action one of {@link #ACTION_TOGGLE}, {@link #ACTION_SELECT_ALL}, {@link #ACTION_CLEAR_ALL}
 * @param index  candidate index for {@link #ACTION_TOGGLE}; ignored otherwise
 */
public record LargeQuarryOrePayload(BlockPos pos, int action, int index)
        implements CustomPacketPayload {
    public static final int ACTION_TOGGLE = 0;
    public static final int ACTION_SELECT_ALL = 1;
    public static final int ACTION_CLEAR_ALL = 2;

    public static final Type<LargeQuarryOrePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "large_quarry_ore"));

    public static final StreamCodec<FriendlyByteBuf, LargeQuarryOrePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos());
                        buffer.writeVarInt(payload.action());
                        buffer.writeVarInt(payload.index());
                    },
                    buffer -> new LargeQuarryOrePayload(
                            buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt()));

    public static LargeQuarryOrePayload toggle(BlockPos pos, int index) {
        return new LargeQuarryOrePayload(pos, ACTION_TOGGLE, index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
