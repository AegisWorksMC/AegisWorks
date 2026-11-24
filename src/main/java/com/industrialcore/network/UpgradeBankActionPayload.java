package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 113 C2S payload for interacting with a machine's 3-slot Virtual Upgrade Dock.
 * Actions: {@code 0=DEPOSIT_1}, {@code 1=DEPOSIT_BULK}, {@code 2=WITHDRAW_1},
 * {@code 3=WITHDRAW_64}, {@code 4=REQUEST_SYNC}.
 */
public record UpgradeBankActionPayload(BlockPos pos, int kindId, int action) implements CustomPacketPayload {
    public static final int ACTION_DEPOSIT_1 = 0;
    public static final int ACTION_DEPOSIT_BULK = 1;
    public static final int ACTION_WITHDRAW_1 = 2;
    public static final int ACTION_WITHDRAW_64 = 3;
    public static final int ACTION_REQUEST_SYNC = 4;

    public static final Type<UpgradeBankActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "upgrade_bank_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeBankActionPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UpgradeBankActionPayload::pos,
                    ByteBufCodecs.VAR_INT, UpgradeBankActionPayload::kindId,
                    ByteBufCodecs.VAR_INT, UpgradeBankActionPayload::action,
                    UpgradeBankActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
