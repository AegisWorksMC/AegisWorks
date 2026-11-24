package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.SuitModifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Phase 98: C2S action packet for the Suit Module Modifier ({@code suit_modifier}):
 * install a module, toggle an installed module ON/OFF, or uninstall and return it as an item.
 */
public record SuitModifierActionPayload(BlockPos pos, int action, int rowIndex) implements CustomPacketPayload {
    public static final int ACTION_INSTALL = 0;
    public static final int ACTION_TOGGLE = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_CYCLE_LASER_RANGE = 3;

    public static final Type<SuitModifierActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "suit_modifier_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SuitModifierActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeVarInt(payload.action);
                buf.writeVarInt(payload.rowIndex);
            },
            buf -> new SuitModifierActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SuitModifierActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) {
                return;
            }
            if (sp.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) > 64.0D) {
                return;
            }
            if (sp.serverLevel().getBlockEntity(payload.pos) instanceof SuitModifierBlockEntity be) {
                switch (payload.action) {
                    case ACTION_INSTALL -> be.tryInstallModule(sp);
                    case ACTION_TOGGLE -> be.tryToggleModule(sp, payload.rowIndex);
                    case ACTION_REMOVE -> be.tryRemoveModule(sp, payload.rowIndex);
                    case ACTION_CYCLE_LASER_RANGE -> be.tryCycleLaserRange(sp);
                    default -> {}
                }
            }
        });
    }
}
