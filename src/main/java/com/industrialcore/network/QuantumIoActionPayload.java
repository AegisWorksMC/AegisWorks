package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 96 (C2S): Requests ghost filter &amp; whitelist/blacklist actions for Quantum Importer and
 * Quantum Exporter.
 */
public record QuantumIoActionPayload(
        BlockPos pos,
        int action,
        int slotIndex,
        ItemStack filterStack
) implements CustomPacketPayload {

    public static final int ACTION_SET_FILTER = 0;
    public static final int ACTION_CLEAR_FILTER = 1;
    public static final int ACTION_CLEAR_ALL = 2;
    public static final int ACTION_TOGGLE_BLACKLIST = 3;

    public static final Type<QuantumIoActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_io_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumIoActionPayload> CODEC =
            StreamCodec.of(QuantumIoActionPayload::encode, QuantumIoActionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, QuantumIoActionPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.action);
        buf.writeVarInt(payload.slotIndex);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.filterStack);
    }

    private static QuantumIoActionPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int action = buf.readVarInt();
        int slot = buf.readVarInt();
        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new QuantumIoActionPayload(pos, action, slot, stack);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
