package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.item.QuantumBladeItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Phase 101: C2S packet sent when a player left-click swings the {@code quantum_blade} in air
 * so the server can launch an {@code att_plasma_wave} energy crescent cleanly.
 */
public record WeaponSwingPayload() implements CustomPacketPayload {
    public static final WeaponSwingPayload INSTANCE = new WeaponSwingPayload();

    public static final Type<WeaponSwingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "weapon_swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponSwingPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponSwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof QuantumBladeItem blade) {
                blade.performKatanaSwing(sp, main);
            }
        });
    }
}
