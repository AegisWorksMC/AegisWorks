package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 95: S2C payload synchronizing a Warp Pad's own name/visibility and all accessible target
 * pads (with server-computed distance and FE cost) to the client's open {@code WarpPadMenu}.
 */
public record WarpListSyncPayload(BlockPos pos, String selfName, boolean selfPublic,
                                  List<TargetRow> targets) implements CustomPacketPayload {
    public record TargetRow(String padId, String name, String dimensionId,
                            int distance, int costFe, boolean crossDim, boolean isPublic) {}

    /** Client hook wired in {@code ClientSetup} so dedicated servers never load client classes. */
    public static Consumer<WarpListSyncPayload> clientHandler = ignored -> {};

    public static final CustomPacketPayload.Type<WarpListSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    IndustrialCore.MOD_ID, "warp_list_sync"));

    public static final StreamCodec<FriendlyByteBuf, WarpListSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public WarpListSyncPayload decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String selfName = buf.readUtf(64);
            boolean selfPublic = buf.readBoolean();
            int count = Math.max(0, Math.min(256, buf.readVarInt()));
            List<TargetRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(128);
                String name = buf.readUtf(64);
                String dim = buf.readUtf(96);
                int dist = buf.readVarInt();
                int cost = buf.readVarInt();
                boolean cross = buf.readBoolean();
                boolean pub = buf.readBoolean();
                rows.add(new TargetRow(id, name, dim, dist, cost, cross, pub));
            }
            return new WarpListSyncPayload(pos, selfName, selfPublic, rows);
        }

        @Override
        public void encode(FriendlyByteBuf buf, WarpListSyncPayload value) {
            buf.writeBlockPos(value.pos());
            buf.writeUtf(value.selfName(), 64);
            buf.writeBoolean(value.selfPublic());
            int count = Math.min(256, value.targets().size());
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                TargetRow r = value.targets().get(i);
                buf.writeUtf(r.padId(), 128);
                buf.writeUtf(r.name(), 64);
                buf.writeUtf(r.dimensionId(), 96);
                buf.writeVarInt(r.distance());
                buf.writeVarInt(r.costFe());
                buf.writeBoolean(r.crossDim());
                buf.writeBoolean(r.isPublic());
            }
        }
    };

    public static void handle(WarpListSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
