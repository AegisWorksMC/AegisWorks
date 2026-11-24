package com.industrialcore.client;

import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.LaserAudioLogic;
import com.industrialcore.weapon.VirtualWeaponLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side real-time 2-layer crossfade + hit-sizzle audio controller for {@code pulse_laser}.
 *
 * <p>Drives {@code weapon_laser_loop_light}, {@code weapon_laser_loop_heavy}, and
 * {@code weapon_laser_hit_sizzle} via {@link LaserLoopSoundInstance} with zero leaks on release,
 * weapon switch, overheat, dimension change, or logout.</p>
 */
public final class LaserAudioController {
    private static LaserLoopSoundInstance lightLoop;
    private static LaserLoopSoundInstance heavyLoop;
    private static LaserLoopSoundInstance sizzleLoop;

    private static ResourceKey<Level> lastDimension;
    private static long lastLocalBeamTick = -100L;
    private static long lastLocalHitTick = -100L;
    private static float lastBeamCharge = 0.0F;
    private static boolean activeNow = false;

    private LaserAudioController() {}

    /** Called when a local {@code KIND_LASER_BEAM} packet arrives from the server. */
    public static void notifyLocalLaserBeam(float charge, boolean hittingTarget) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        lastLocalBeamTick = now;
        lastBeamCharge = Math.max(0.0F, Math.min(1.0F, charge));
        if (hittingTarget) {
            lastLocalHitTick = now;
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isPaused()) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            stopAll();
            return;
        }
        ResourceKey<Level> dim = mc.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dim)) {
            stopAll();
        }
        lastDimension = dim;

        Player player = mc.player;
        long now = mc.level.getGameTime();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        ItemStack laser = main.getItem() instanceof PulseLaserItem
                ? main
                : (off.getItem() instanceof PulseLaserItem ? off : ItemStack.EMPTY);

        boolean holdingLaser = !laser.isEmpty();
        boolean usingLaser = player.isUsingItem() && player.getUseItem().getItem() instanceof PulseLaserItem;
        boolean recentBeam = (now - lastLocalBeamTick) <= 3L;
        boolean overheated = holdingLaser && PulseLaserItem.isOverheated(laser);
        boolean hasPower = holdingLaser && (player.isCreative()
                || ElectricWeaponItem.getEnergy(laser) >= VirtualWeaponLogic.LASER_FE_PER_TICK);

        boolean active = LaserAudioLogic.shouldBeActive(holdingLaser, usingLaser || recentBeam, overheated, hasPower);
        activeNow = active;

        float charge = holdingLaser ? Math.max(PulseLaserItem.getCharge(laser), recentBeam ? lastBeamCharge : 0.0F) : 0.0F;
        boolean hitting = active && ((now - lastLocalHitTick) <= 3L
                || (holdingLaser && PulseLaserItem.getCharge(laser) > 0.0F && PulseLaserItem.getTicksSinceHit(laser) <= 1));

        LaserAudioLogic.LaserMix mix = LaserAudioLogic.computeMix(active, charge, hitting);
        SoundManager sm = mc.getSoundManager();

        // 1. Light & Heavy main beam layers
        if (mix.active()) {
            lightLoop = ensureRunning(sm, lightLoop, ModSounds.WEAPON_LASER_LOOP_LIGHT.get(),
                    mix.lightVolume(), mix.lightPitch());
            heavyLoop = ensureRunning(sm, heavyLoop, ModSounds.WEAPON_LASER_LOOP_HEAVY.get(),
                    mix.heavyVolume(), mix.heavyPitch());
        } else {
            lightLoop = releaseOrPrune(lightLoop);
            heavyLoop = releaseOrPrune(heavyLoop);
        }

        // 2. Hit Sizzle layer (only while burning an entity!)
        if (mix.active() && mix.sizzleVolume() > 0.01F) {
            sizzleLoop = ensureRunning(sm, sizzleLoop, ModSounds.WEAPON_LASER_HIT_SIZZLE.get(),
                    mix.sizzleVolume(), mix.sizzlePitch());
        } else {
            sizzleLoop = releaseOrPrune(sizzleLoop);
        }
    }

    private static LaserLoopSoundInstance ensureRunning(SoundManager sm,
                                                        LaserLoopSoundInstance current,
                                                        net.minecraft.sounds.SoundEvent event,
                                                        float targetVol,
                                                        float targetPitch) {
        if (current == null || current.isStopped()) {
            LaserLoopSoundInstance fresh = new LaserLoopSoundInstance(event, targetVol, targetPitch);
            sm.play(fresh);
            return fresh;
        }
        current.updateTargets(targetVol, targetPitch);
        return current;
    }

    private static LaserLoopSoundInstance releaseOrPrune(LaserLoopSoundInstance current) {
        if (current == null) {
            return null;
        }
        if (current.isStopped()) {
            return null;
        }
        if (!current.isReleasing()) {
            current.beginRelease();
        }
        return current;
    }

    /** Immediately stops and releases every active laser audio loop (logout / dimension change). */
    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc != null ? mc.getSoundManager() : null;
        stopOne(sm, lightLoop);
        stopOne(sm, heavyLoop);
        stopOne(sm, sizzleLoop);
        lightLoop = null;
        heavyLoop = null;
        sizzleLoop = null;
        lastDimension = null;
        lastLocalBeamTick = -100L;
        lastLocalHitTick = -100L;
        lastBeamCharge = 0.0F;
        activeNow = false;
    }

    private static void stopOne(SoundManager sm, LaserLoopSoundInstance inst) {
        if (inst == null) {
            return;
        }
        inst.stopImmediately();
        if (sm != null) {
            try {
                sm.stop(inst);
            } catch (Throwable ignored) {
            }
        }
    }

    // Read-only telemetry accessors for diagnostics & live verification
    public static boolean isActive() {
        return activeNow;
    }

    public static float getCurrentLightVolume() {
        return lightLoop != null && !lightLoop.isStopped() ? lightLoop.currentVolume() : 0.0F;
    }

    public static float getCurrentLightPitch() {
        return lightLoop != null && !lightLoop.isStopped() ? lightLoop.currentPitch() : 0.0F;
    }

    public static float getCurrentHeavyVolume() {
        return heavyLoop != null && !heavyLoop.isStopped() ? heavyLoop.currentVolume() : 0.0F;
    }

    public static float getCurrentHeavyPitch() {
        return heavyLoop != null && !heavyLoop.isStopped() ? heavyLoop.currentPitch() : 0.0F;
    }

    public static float getCurrentSizzleVolume() {
        return sizzleLoop != null && !sizzleLoop.isStopped() ? sizzleLoop.currentVolume() : 0.0F;
    }

    public static int getActiveLoopCount() {
        int c = 0;
        if (lightLoop != null && !lightLoop.isStopped()) c++;
        if (heavyLoop != null && !heavyLoop.isStopped()) c++;
        if (sizzleLoop != null && !sizzleLoop.isStopped()) c++;
        return c;
    }
}
