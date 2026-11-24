package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Phase 84–97: Server -> Client snapshot of the local player's machine levels, per-branch
 * limit-break flags, and revealed Skill Tree node ids.
 */
public record MachineProgressSyncPayload(
        boolean limitBroken,
        Map<String, Long> xpByMachine,
        Set<String> revealedNodes,
        Set<String> limitBrokenBranches,
        int unlockedRiftTier
) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 64;

    public MachineProgressSyncPayload(boolean limitBroken,
                                      Map<String, Long> xpByMachine,
                                      Set<String> revealedNodes,
                                      Set<String> limitBrokenBranches) {
        this(limitBroken, xpByMachine, revealedNodes, limitBrokenBranches, 1);
    }

    public static final Type<MachineProgressSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "machine_progress_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineProgressSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.limitBroken());
                Map<String, Long> map = payload.xpByMachine();
                int count = Math.min(MAX_ENTRIES, map.size());
                buf.writeVarInt(count);
                int written = 0;
                for (Map.Entry<String, Long> entry : map.entrySet()) {
                    if (written++ >= count) {
                        break;
                    }
                    buf.writeUtf(entry.getKey(), 64);
                    buf.writeLong(entry.getValue());
                }
                Set<String> revealed = payload.revealedNodes();
                int revCount = Math.min(MAX_ENTRIES, revealed.size());
                buf.writeVarInt(revCount);
                int revWritten = 0;
                for (String id : revealed) {
                    if (revWritten++ >= revCount) {
                        break;
                    }
                    buf.writeUtf(id, 64);
                }
                Set<String> branches = payload.limitBrokenBranches();
                int brCount = Math.min(16, branches.size());
                buf.writeVarInt(brCount);
                int brWritten = 0;
                for (String b : branches) {
                    if (brWritten++ >= brCount) {
                        break;
                    }
                    buf.writeUtf(b, 32);
                }
                buf.writeVarInt(payload.unlockedRiftTier());
            },
            buf -> {
                boolean broken = buf.readBoolean();
                int count = Math.max(0, Math.min(MAX_ENTRIES, buf.readVarInt()));
                Map<String, Long> map = new LinkedHashMap<>(count);
                for (int i = 0; i < count; i++) {
                    String id = buf.readUtf(64);
                    long xp = buf.readLong();
                    if (!id.isEmpty() && xp > 0L) {
                        map.put(id, xp);
                    }
                }
                int revCount = Math.max(0, Math.min(MAX_ENTRIES, buf.readVarInt()));
                Set<String> revealed = new LinkedHashSet<>(revCount);
                for (int i = 0; i < revCount; i++) {
                    String id = buf.readUtf(64);
                    if (!id.isEmpty()) {
                        revealed.add(id);
                    }
                }
                int brCount = Math.max(0, Math.min(16, buf.readVarInt()));
                Set<String> branches = new LinkedHashSet<>(brCount);
                for (int i = 0; i < brCount; i++) {
                    String b = buf.readUtf(32);
                    if (!b.isEmpty()) {
                        branches.add(b);
                    }
                }
                int riftTier = com.industrialcore.raid.DimensionalRaidLogic.clampTier(buf.readVarInt());
                return new MachineProgressSyncPayload(
                        broken,
                        Collections.unmodifiableMap(map),
                        Collections.unmodifiableSet(revealed),
                        Collections.unmodifiableSet(branches),
                        riftTier);
            }
    );

    public MachineProgressSyncPayload {
        xpByMachine = xpByMachine == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(xpByMachine));
        revealedNodes = revealedNodes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(revealedNodes));
        limitBrokenBranches = limitBrokenBranches == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(limitBrokenBranches));
        unlockedRiftTier = com.industrialcore.raid.DimensionalRaidLogic.clampTier(unlockedRiftTier);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static volatile Consumer<MachineProgressSyncPayload> clientHandler;

    public static void handle(MachineProgressSyncPayload payload, IPayloadContext context) {
        Consumer<MachineProgressSyncPayload> handler = clientHandler;
        if (handler != null) {
            context.enqueueWork(() -> handler.accept(payload));
        }
    }
}
