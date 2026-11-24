package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * Phase 101 / 102.5+: S2C visual effect packet for 3D world-space weapon beams, prism chains,
 * railgun afterglow trails, plasma slash crescents, and gravity singularity fields.
 * Carries {@code charge} ({@code 0.0f..1.0f}) for dynamic Pulse Laser beam thickness &amp; overdrive glow.
 */
public record WeaponBeamFxPayload(
        int fxKind,
        double x0, double y0, double z0,
        double x1, double y1, double z1,
        int colorArgb,
        int durationTicks,
        float radius,
        float charge,
        boolean hittingTarget
) implements CustomPacketPayload {
    public static final int KIND_LASER_BEAM = 0;
    public static final int KIND_PRISM_CHAIN = 1;
    public static final int KIND_RAILGUN_BEAM = 2;
    public static final int KIND_PLASMA_WAVE = 3;
    public static final int KIND_GRAVITY_FIELD = 4;
    public static final int KIND_BLINK_TRAIL = 5;
    public static final int KIND_BLADE_SLASH_ARC = 6;
    public static final int KIND_BOW_IMPACT_PILLAR = 7;
    /** Phase 119: Dimensional Rift portal arrival vortex &amp; vertical energy pillar. */
    public static final int KIND_RIFT_PORTAL_VORTEX = 8;
    /** Phase 119: Phase Shield deflection ripple (charge &lt; 0.5) vs Armor-Pierce hit burst (charge &gt;= 0.5). */
    public static final int KIND_RIFT_SHIELD_HIT = 9;
    /** Phase 119: Dimensional Sovereign defeat giant dual purple/cyan shockwave rings &amp; collapsing pillar. */
    public static final int KIND_BOSS_COLLAPSE_SHOCKWAVE = 10;

    public WeaponBeamFxPayload(int fxKind,
                               double x0, double y0, double z0,
                               double x1, double y1, double z1,
                               int colorArgb, int durationTicks, float radius) {
        this(fxKind, x0, y0, z0, x1, y1, z1, colorArgb, durationTicks, radius, 0.0F, false);
    }

    public WeaponBeamFxPayload(int fxKind,
                               double x0, double y0, double z0,
                               double x1, double y1, double z1,
                               int colorArgb, int durationTicks, float radius, float charge) {
        this(fxKind, x0, y0, z0, x1, y1, z1, colorArgb, durationTicks, radius, charge, false);
    }

    public static final Type<WeaponBeamFxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "weapon_beam_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponBeamFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.fxKind);
                buf.writeDouble(p.x0);
                buf.writeDouble(p.y0);
                buf.writeDouble(p.z0);
                buf.writeDouble(p.x1);
                buf.writeDouble(p.y1);
                buf.writeDouble(p.z1);
                buf.writeInt(p.colorArgb);
                buf.writeVarInt(p.durationTicks);
                buf.writeFloat(p.radius);
                buf.writeFloat(p.charge);
                buf.writeBoolean(p.hittingTarget);
            },
            buf -> new WeaponBeamFxPayload(
                    buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(),
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean()
            )
    );

    public static volatile Consumer<WeaponBeamFxPayload> clientHandler = payload -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponBeamFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }
}
