package com.industrialcore.reactor;

import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Phase 114: Server-side two-stage "Build-Up &amp; Release" ritual FX for Nuclear &amp; Singularity
 * multiblock completion (15-tick converging charge -&gt; shockwave burst + resonant chime + online alert),
 * plus emergency Overdrive cooldown warning alert.
 */
public final class ReactorRitualHelper {
    private static final double NOTIFY_RADIUS = 24.0D;

    private ReactorRitualHelper() {}

    /**
     * Advances one server tick of the 15-tick formation ritual.
     *
     * @return updated remaining ritual ticks ({@code 0} once the release shockwave has fired)
     */
    public static int tickRitual(ServerLevel level, BlockPos controllerPos,
                                 @Nullable IntPos minPos, @Nullable IntPos maxPos,
                                 boolean singularity, int remainingTicks) {
        if (remainingTicks <= 0) {
            return 0;
        }
        double cx = (minPos != null && maxPos != null)
                ? (minPos.x() + maxPos.x() + 1.0D) * 0.5D
                : controllerPos.getX() + 0.5D;
        double cy = (minPos != null && maxPos != null)
                ? (minPos.y() + maxPos.y() + 1.0D) * 0.5D
                : controllerPos.getY() + 0.5D;
        double cz = (minPos != null && maxPos != null)
                ? (minPos.z() + maxPos.z() + 1.0D) * 0.5D
                : controllerPos.getZ() + 0.5D;
        double radius = (minPos != null && maxPos != null)
                ? Math.max(2.0D, Math.max(maxPos.x() - minPos.x(), maxPos.z() - minPos.z()) * 0.65D)
                : 2.2D;

        if (remainingTicks > 1) {
            // Stage 1: Build-up ("tame") — converging cyan/violet particles pulling toward reactor core
            float prog = ReactorOverdriveLogic.ritualChargeProgress(remainingTicks);
            double ringR = radius * (1.15D - 0.65D * prog);
            int count = singularity ? 12 : 9;
            for (int i = 0; i < count; i++) {
                double ang = (i * (Math.PI * 2.0D / count)) + prog * 4.5D;
                double sx = cx + Math.cos(ang) * ringR;
                double sy = cy + Math.sin(ang * 2.0D) * (ringR * 0.35D);
                double sz = cz + Math.sin(ang) * ringR;
                double vx = (cx - sx) * 0.16D;
                double vy = (cy - sy) * 0.16D;
                double vz = (cz - sz) * 0.16D;
                level.sendParticles(singularity ? ParticleTypes.PORTAL : ParticleTypes.ELECTRIC_SPARK,
                        sx, sy, sz, 0, vx, vy, vz, 1.0D);
                if (i % 3 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, sx, sy, sz, 0, vx * 0.8D, vy * 0.8D, vz * 0.8D, 0.9D);
                }
            }
            if (remainingTicks == ReactorOverdriveLogic.RITUAL_CHARGE_TICKS || remainingTicks % 3 == 0) {
                float pitch = ReactorOverdriveLogic.ritualChargePitch(remainingTicks);
                level.playSound(null, controllerPos,
                        singularity ? ModSounds.TREE_RISER.get() : ModSounds.MACHINE_QUANTUM_CHIME.get(),
                        SoundSource.BLOCKS, 0.72F, pitch);
            }
            return remainingTicks - 1;
        }

        // Stage 2: Release ("kaihou") — expanding radial shockwave + resonant activation sound + online notice
        int burstCount = singularity ? 32 : 24;
        for (int i = 0; i < burstCount; i++) {
            double ang = i * (Math.PI * 2.0D / burstCount);
            double vx = Math.cos(ang) * 0.42D;
            double vz = Math.sin(ang) * 0.42D;
            level.sendParticles(singularity ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                    cx, cy, cz, 0, vx, 0.08D, vz, 1.0D);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        cx, cy + 0.2D, cz, 0, vx * 1.25D, 0.18D, vz * 1.25D, 1.0D);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);

        level.playSound(null, controllerPos, ModSounds.LIMIT_BREAK.get(), SoundSource.BLOCKS, 0.95F,
                singularity ? 0.90F : 1.05F);
        level.playSound(null, controllerPos,
                singularity ? ModSounds.MACHINE_SINGULARITY_PULSE.get() : ModSounds.MACHINE_NUCLEAR_CORE.get(),
                SoundSource.BLOCKS, 0.85F, 0.85F);

        Component msg = Component.translatable(singularity
                        ? "message.industrialcore.reactor_online_singularity"
                        : "message.industrialcore.reactor_online_nuclear")
                .withStyle(singularity ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA, ChatFormatting.BOLD);
        for (ServerPlayer sp : nearbyPlayers(level, controllerPos)) {
            sp.displayClientMessage(msg, true);
        }
        return 0;
    }

    /** Plays non-destructive emergency cooldown alert when Overdrive runs dry on coolant or catalyst. */
    public static void notifyOverdriveEmergencyShutdown(ServerLevel level, BlockPos pos, boolean singularity) {
        level.playSound(null, pos, ModSounds.UI_LOCKED.get(), SoundSource.BLOCKS, 0.95F, 0.65F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                10, 0.35D, 0.30D, 0.35D, 0.03D);
        Component msg = Component.translatable(singularity
                        ? "message.industrialcore.overdrive_cooldown_singularity"
                        : "message.industrialcore.overdrive_cooldown_nuclear",
                ReactorOverdriveLogic.EMERGENCY_COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer sp : nearbyPlayers(level, pos)) {
            sp.displayClientMessage(msg, true);
        }
    }

    public static List<ServerPlayer> nearbyPlayers(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(NOTIFY_RADIUS);
        return level.getEntitiesOfClass(ServerPlayer.class, box);
    }
}
