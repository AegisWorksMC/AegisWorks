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
 * Phase 102.5: C2S packet sent when the player Sneak + Left-Clicks with {@code pulse_laser}
 * to toggle its {@link com.industrialcore.weapon.LaserTargetMode} server-authoritatively.
 */
public record LaserModeTogglePayload() implements CustomPacketPayload {
    public static final LaserModeTogglePayload INSTANCE = new LaserModeTogglePayload();

    public static final Type<LaserModeTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "laser_mode_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LaserModeTogglePayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LaserModeTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof PulseLaserItem) {
                PulseLaserItem.toggleTargetMode(sp, main);
            }
        });
    }
}
