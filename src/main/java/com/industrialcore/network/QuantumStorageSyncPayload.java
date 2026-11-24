package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 89 / 96 (S2C): Syncs Quantum Storage Core or Portable Terminal contents (BigInteger item &amp;
 * fluid counts, limits, active frequency channel, and accessible channel list) to viewers.
 */
public record QuantumStorageSyncPayload(
        BlockPos pos,
        boolean portable,
        int maxTypes,
        String maxPerType,
        String activeChannelId,
        String activeChannelName,
        List<ItemEntry> items,
        List<FluidEntry> fluids,
        List<ChannelSummary> channels
) implements CustomPacketPayload {

    public record ItemEntry(ItemStack stack, BigInteger count) {}

    public record FluidEntry(FluidStack fluid, BigInteger amount) {}

    public record ChannelSummary(
            String channelId,
            String name,
            boolean isPublic,
            boolean ownedByViewer,
            int usedTypes,
            int maxTypes
    ) {}

    public static final Type<QuantumStorageSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "quantum_storage_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumStorageSyncPayload> CODEC =
            StreamCodec.of(QuantumStorageSyncPayload::encode, QuantumStorageSyncPayload::decode);

    /** Registered from ClientSetup to keep this class dedicated-server safe. */
    public static Consumer<QuantumStorageSyncPayload> clientHandler;

    public static void encodeChannelSummary(RegistryFriendlyByteBuf buf, ChannelSummary ch) {
        buf.writeUtf(ch.channelId(), 64);
        buf.writeUtf(ch.name(), 64);
        buf.writeBoolean(ch.isPublic());
        buf.writeBoolean(ch.ownedByViewer());
        buf.writeVarInt(ch.usedTypes());
        buf.writeVarInt(ch.maxTypes());
    }

    public static ChannelSummary decodeChannelSummary(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        String name = buf.readUtf(64);
        boolean pub = buf.readBoolean();
        boolean owned = buf.readBoolean();
        int used = buf.readVarInt();
        int max = buf.readVarInt();
        return new ChannelSummary(id, name, pub, owned, used, max);
    }

    private static void encode(RegistryFriendlyByteBuf buf, QuantumStorageSyncPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeBoolean(payload.portable);
        buf.writeVarInt(payload.maxTypes);
        buf.writeUtf(payload.maxPerType, 256);
        buf.writeUtf(payload.activeChannelId == null ? "" : payload.activeChannelId, 64);
        buf.writeUtf(payload.activeChannelName == null ? "" : payload.activeChannelName, 64);

        buf.writeVarInt(payload.items.size());
        for (ItemEntry entry : payload.items) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.stack);
            buf.writeUtf(entry.count.toString(), 256);
        }

        buf.writeVarInt(payload.fluids.size());
        for (FluidEntry entry : payload.fluids) {
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.fluid);
            buf.writeUtf(entry.amount.toString(), 256);
        }

        buf.writeVarInt(payload.channels.size());
        for (ChannelSummary ch : payload.channels) {
            encodeChannelSummary(buf, ch);
        }
    }

    private static QuantumStorageSyncPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean portable = buf.readBoolean();
        int maxTypes = buf.readVarInt();
        String maxPerType = buf.readUtf(256);
        String activeId = buf.readUtf(64);
        String activeName = buf.readUtf(64);

        int itemCount = Math.max(0, buf.readVarInt());
        List<ItemEntry> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            BigInteger count = parseSafe(buf.readUtf(256));
            if (!stack.isEmpty() && count.signum() > 0) {
                items.add(new ItemEntry(stack, count));
            }
        }

        int fluidCount = Math.max(0, buf.readVarInt());
        List<FluidEntry> fluids = new ArrayList<>(fluidCount);
        for (int i = 0; i < fluidCount; i++) {
            FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
            BigInteger amt = parseSafe(buf.readUtf(256));
            if (!fluid.isEmpty() && amt.signum() > 0) {
                fluids.add(new FluidEntry(fluid, amt));
            }
        }

        int chCount = Math.max(0, Math.min(256, buf.readVarInt()));
        List<ChannelSummary> channels = new ArrayList<>(chCount);
        for (int i = 0; i < chCount; i++) {
            channels.add(decodeChannelSummary(buf));
        }
        return new QuantumStorageSyncPayload(
                pos, portable, maxTypes, maxPerType, activeId, activeName, items, fluids, channels);
    }

    private static BigInteger parseSafe(String raw) {
        try {
            BigInteger v = new BigInteger(raw);
            return v.signum() > 0 ? v : BigInteger.ZERO;
        } catch (NumberFormatException ex) {
            return BigInteger.ZERO;
        }
    }

    public static void handle(QuantumStorageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Consumer<QuantumStorageSyncPayload> handler = clientHandler;
            if (handler != null) {
                handler.accept(payload);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
