package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 97 (C2S): Requests server-authoritative Lv.1000 Limit Break activation for a single
 * Skill Tree branch once all nodes in that branch have reached Lv.99.
 */
public record BranchLimitBreakPayload(String branchId) implements CustomPacketPayload {

    public static final Type<BranchLimitBreakPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "branch_limit_break"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BranchLimitBreakPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.branchId == null ? "" : p.branchId, 32),
                    buf -> new BranchLimitBreakPayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
