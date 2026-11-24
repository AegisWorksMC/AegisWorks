package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 96 (S2C): Syncs active frequency channel, 9 ghost filter items, blacklist flag, and
 * accessible frequency directory to players viewing a Quantum Importer or Exporter GUI.
 */
public record QuantumIoSyncPayload(
        BlockPos pos,
        String activeChannelId,
        String activeChannelName,
        boolean blacklist,
        List<ItemStack> filters,
        List<ChannelSummary> channels
) implements CustomPacketPayload {

    public static final Type<QuantumIoSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_io_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumIoSyncPayload> CODEC =
            StreamCodec.of(QuantumIoSyncPayload::encode, QuantumIoSyncPayload::decode);

    /** Registered from ClientSetup to remain dedicated-server safe. */
    public static Consumer<QuantumIoSyncPayload> clientHandler;

    private static void encode(RegistryFriendlyByteBuf buf, QuantumIoSyncPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.activeChannelId == null ? "" : payload.activeChannelId, 64);
        buf.writeUtf(payload.activeChannelName == null ? "" : payload.activeChannelName, 64);
        buf.writeBoolean(payload.blacklist);

        buf.writeVarInt(payload.filters.size());
        for (ItemStack s : payload.filters) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s == null ? ItemStack.EMPTY : s);
        }

        buf.writeVarInt(payload.channels.size());
        for (ChannelSummary ch : payload.channels) {
            QuantumStorageSyncPayload.encodeChannelSummary(buf, ch);
        }
    }

    private static QuantumIoSyncPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String chId = buf.readUtf(64);
        String chName = buf.readUtf(64);
        boolean blacklist = buf.readBoolean();

        int filterCount = Math.max(0, Math.min(16, buf.readVarInt()));
        List<ItemStack> filters = new ArrayList<>(filterCount);
        for (int i = 0; i < filterCount; i++) {
            filters.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        int chCount = Math.max(0, Math.min(256, buf.readVarInt()));
        List<ChannelSummary> channels = new ArrayList<>(chCount);
        for (int i = 0; i < chCount; i++) {
            channels.add(QuantumStorageSyncPayload.decodeChannelSummary(buf));
        }
        return new QuantumIoSyncPayload(pos, chId, chName, blacklist, filters, channels);
    }

    public static void handle(QuantumIoSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Consumer<QuantumIoSyncPayload> h = clientHandler;
            if (h != null) {
                h.accept(payload);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
