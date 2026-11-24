package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.suit.SquadLogic;
import com.industrialcore.suit.SquadLogic.SquadMember;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Phase 100: Lightweight S2C payload synchronizing up to 2 nearby same-dimension players (within
 * 96 blocks) for the Virtual Aegis AR Squad HUD.
 */
public record SquadStatusSyncPayload(String dimensionId, List<SquadMember> members) implements CustomPacketPayload {
    public static final Type<SquadStatusSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "squad_status_sync"));

    public static volatile Consumer<SquadStatusSyncPayload> clientHandler;

    public static final StreamCodec<RegistryFriendlyByteBuf, SquadStatusSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId, 128);
                int n = Math.min(SquadLogic.MAX_NEARBY_MATES, payload.members.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    SquadMember m = payload.members.get(i);
                    buf.writeUUID(m.uuid());
                    buf.writeUtf(m.name(), 64);
                    buf.writeFloat(m.hp());
                    buf.writeFloat(m.maxHp());
                    buf.writeBoolean(m.suitEquipped());
                    buf.writeVarLong(m.suitEnergy());
                    buf.writeVarLong(m.suitCapacity());
                    buf.writeDouble(m.x());
                    buf.writeDouble(m.y());
                    buf.writeDouble(m.z());
                }
            },
            buf -> {
                String dim = buf.readUtf(128);
                int n = Math.max(0, Math.min(SquadLogic.MAX_NEARBY_MATES, buf.readVarInt()));
                List<SquadMember> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    UUID id = buf.readUUID();
                    String name = buf.readUtf(64);
                    float hp = buf.readFloat();
                    float maxHp = buf.readFloat();
                    boolean suit = buf.readBoolean();
                    long fe = buf.readVarLong();
                    long cap = buf.readVarLong();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    list.add(new SquadMember(id, name, hp, maxHp, suit, fe, cap, x, y, z));
                }
                return new SquadStatusSyncPayload(dim, List.copyOf(list));
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SquadStatusSyncPayload payload, IPayloadContext context) {
        Consumer<SquadStatusSyncPayload> handler = clientHandler;
        if (handler != null) {
            context.enqueueWork(() -> handler.accept(payload));
        }
    }
}
