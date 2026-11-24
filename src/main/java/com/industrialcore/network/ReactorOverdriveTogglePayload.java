package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 114 C2S payload toggling High-Output Overdrive Mode on {@code nuclear_generator}
 * or {@code singularity_reactor}.
 */
public record ReactorOverdriveTogglePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ReactorOverdriveTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "reactor_overdrive_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorOverdriveTogglePayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ReactorOverdriveTogglePayload::pos,
                    ReactorOverdriveTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
