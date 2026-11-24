package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.raid.DimensionalRaidManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Phase 115 / 116: Client -> Server request to launch a voluntary Dimensional Rift Raid at a
 * formed Singularity Reactor at {@code selectedTier} (1..unlockedMaxTier).
 */
public record RiftRaidStartPayload(BlockPos reactorPos, int selectedTier) implements CustomPacketPayload {
    public RiftRaidStartPayload(BlockPos reactorPos) {
        this(reactorPos, DimensionalRaidLogic.MIN_TIER);
    }

    public static final Type<RiftRaidStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "rift_raid_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftRaidStartPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.reactorPos());
                buf.writeVarInt(DimensionalRaidLogic.clampTier(payload.selectedTier()));
            },
            buf -> new RiftRaidStartPayload(buf.readBlockPos(), DimensionalRaidLogic.clampTier(buf.readVarInt()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RiftRaidStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                BlockPos pos = payload.reactorPos();
                if (sp.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 196.0D) {
                    DimensionalRaidManager.tryStartRaid(sp.serverLevel(), pos, sp, payload.selectedTier());
                }
            }
        });
    }
}
