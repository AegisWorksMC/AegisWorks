package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * Phase 113 S2C payload synchronizing a machine's live virtual upgrade totals, XP-linked cap,
 * and specialization category to the open client GUI.
 */
public record UpgradeBankSyncPayload(
        BlockPos pos,
        int motors,
        int batteries,
        int specials,
        int totalLimit,
        int categoryOrdinal,
        String machineId
) implements CustomPacketPayload {
    public static final Type<UpgradeBankSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "upgrade_bank_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeBankSyncPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public UpgradeBankSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int m = buf.readVarInt();
                    int b = buf.readVarInt();
                    int s = buf.readVarInt();
                    int lim = buf.readVarInt();
                    int cat = buf.readVarInt();
                    String id = buf.readUtf(64);
                    return new UpgradeBankSyncPayload(pos, m, b, s, lim, cat, id);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, UpgradeBankSyncPayload p) {
                    BlockPos.STREAM_CODEC.encode(buf, p.pos());
                    buf.writeVarInt(p.motors());
                    buf.writeVarInt(p.batteries());
                    buf.writeVarInt(p.specials());
                    buf.writeVarInt(p.totalLimit());
                    buf.writeVarInt(p.categoryOrdinal());
                    buf.writeUtf(p.machineId(), 64);
                }
            };

    public static volatile Consumer<UpgradeBankSyncPayload> clientHandler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpgradeBankSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }
}
