package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server request for the Phase 15 reconstructor.
 *
 * <p>The payload only carries the block position, the action id and, for the selection, the key of
 * the mineral. It never carries items: the server reads the sample from the machine, resolves the
 * recipe itself and rejects a mineral that is not unlocked, so a modified client cannot inject
 * items or select an unknown recipe.</p>
 *
 * @param pos     position of the reconstructor
 * @param action  one of {@link com.industrialcore.machine.ReconstructorActions}
 * @param mineral key of the mineral to select; empty for the other actions
 */
public record ReconstructorActionPayload(BlockPos pos, int action, String mineral)
        implements CustomPacketPayload {

    public static final Type<ReconstructorActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "reconstructor_action"));

    public static final StreamCodec<FriendlyByteBuf, ReconstructorActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeVarInt(payload.action());
                buffer.writeUtf(payload.mineral(), 64);
            },
            buffer -> new ReconstructorActionPayload(buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
