package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 86: C2S packet sent by the client once the Skill Tree unlock animation finishes (or is
 * skipped), instructing the server to persist {@code revealedNodes} in {@code MachineProgressSavedData}.
 */
public record MarkTreeRevealedPayload(List<String> machineIds) implements CustomPacketPayload {
    public static final int MAX_IDS = 32;

    public static final Type<MarkTreeRevealedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "mark_tree_revealed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarkTreeRevealedPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                List<String> ids = payload.machineIds();
                int count = Math.min(MAX_IDS, ids.size());
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    buf.writeUtf(ids.get(i), 64);
                }
            },
            buf -> {
                int count = Math.max(0, Math.min(MAX_IDS, buf.readVarInt()));
                List<String> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String id = buf.readUtf(64);
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
                return new MarkTreeRevealedPayload(List.copyOf(ids));
            }
    );

    public MarkTreeRevealedPayload {
        machineIds = machineIds == null ? List.of() : List.copyOf(machineIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
