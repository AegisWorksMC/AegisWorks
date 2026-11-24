package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 96 (C2S): Requests frequency channel operations (connect, disconnect, create, delete,
 * toggle public/private) from Quantum Storage Core, Portable Terminal, Importer, or Exporter.
 */
public record QuantumFreqActionPayload(
        BlockPos pos,
        boolean portable,
        int action,
        String value,
        boolean flag
) implements CustomPacketPayload {

    public static final int ACTION_CONNECT = 0;
    public static final int ACTION_DISCONNECT = 1;
    public static final int ACTION_CREATE = 2;
    public static final int ACTION_DELETE = 3;
    public static final int ACTION_TOGGLE_PUBLIC = 4;

    public static final Type<QuantumFreqActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_freq_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumFreqActionPayload> CODEC =
            StreamCodec.of(QuantumFreqActionPayload::encode, QuantumFreqActionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, QuantumFreqActionPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeBoolean(payload.portable);
        buf.writeVarInt(payload.action);
        buf.writeUtf(payload.value == null ? "" : payload.value, 64);
        buf.writeBoolean(payload.flag);
    }

    private static QuantumFreqActionPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean portable = buf.readBoolean();
        int action = buf.readVarInt();
        String value = buf.readUtf(64);
        boolean flag = buf.readBoolean();
        return new QuantumFreqActionPayload(pos, portable, action, value, flag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
