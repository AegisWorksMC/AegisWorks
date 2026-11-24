package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.item.PulseLaserItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S packet sent when the player presses the laser range toggle key (default {@code R}) while
 * holding {@code pulse_laser} to cycle {@link com.industrialcore.weapon.LaserAimRange}
 * ({@code NARROW -> WIDE -> FULL_360}) server-authoritatively.
 */
public record LaserRangeTogglePayload() implements CustomPacketPayload {
    public static final LaserRangeTogglePayload INSTANCE = new LaserRangeTogglePayload();

    public static final Type<LaserRangeTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "laser_range_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaserRangeTogglePayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaserRangeTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof PulseLaserItem) {
                PulseLaserItem.toggleAimRange(sp, main);
                return;
            }
            ItemStack off = sp.getOffhandItem();
            if (off.getItem() instanceof PulseLaserItem) {
                PulseLaserItem.toggleAimRange(sp, off);
            }
        });
    }
}
