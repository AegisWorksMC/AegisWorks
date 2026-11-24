package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.audio.WorldJoinAudioGuardLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Phase 116: Client-side 3-layer audio guard wrapping {@link WorldJoinAudioGuardLogic}.
 *
 * <p>Intercepts world join / dimension transitions, suppresses initial Virtual Aegis &amp; Quantum Blade
 * sync sounds, silences all automatic AegisWorks &amp; armor-equip sounds for the first 60 ticks
 * (3.0 seconds), and enforces the global 2-sounds/tick + 6-tick same-ID cooldown limiter across
 * every AegisWorks sound via NeoForge's {@link PlaySoundEvent}.</p>
 */
public final class WorldJoinAudioGuard {
    private static final WorldJoinAudioGuardLogic LOGIC = new WorldJoinAudioGuardLogic();
    private static ClientLevel lastLevel = null;
    private static ResourceKey<Level> lastDimension = null;

    private WorldJoinAudioGuard() {}

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        triggerJoinGrace();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastLevel = null;
        lastDimension = null;
        LOGIC.onWorldJoinOrDimensionChange();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (lastLevel != null) {
                lastLevel = null;
                lastDimension = null;
                LOGIC.onWorldJoinOrDimensionChange();
            }
            return;
        }
        ResourceKey<Level> dim = level.dimension();
        if (lastLevel != level || (lastDimension != null && !lastDimension.equals(dim))) {
            lastLevel = level;
            lastDimension = dim;
            LOGIC.onWorldJoinOrDimensionChange();
            WorldAmbientAudioController.stopAll();
            SoundDirector.stopMachineGuiSounds();
            LaserAudioController.stopAll();
            return;
        }
        LOGIC.tick();
    }

    /** Global SoundManager gate enforcing the 60-tick join mute &amp; 2-sounds/tick + 6-tick cooldown cap. */
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance inst = event.getSound();
        if (inst == null) {
            return;
        }
        ResourceLocation loc = inst.getLocation();
        if (loc == null) {
            return;
        }
        boolean isMod = IndustrialCore.MOD_ID.equals(loc.getNamespace());
        boolean isVanillaEquipOrSync = !isMod
                && "minecraft".equals(loc.getNamespace())
                && (loc.getPath().startsWith("item.armor.equip")
                    || loc.getPath().equals("block.beacon.activate")
                    || loc.getPath().equals("block.beacon.ambient"));

        if (!isMod && !isVanillaEquipOrSync) {
            return;
        }
        if (!LOGIC.tryAllowSound(loc.toString(), isMod, isVanillaEquipOrSync)) {
            event.setSound(null);
        } else {
            LOGIC.recordPlayedDuringGraceIfActive(isMod);
        }
    }

    public static void triggerJoinGrace() {
        LOGIC.onWorldJoinOrDimensionChange();
    }

    public static boolean isInJoinGrace() {
        return LOGIC.isInJoinGrace();
    }

    public static int getGraceRemainingTicks() {
        return LOGIC.graceRemainingTicks();
    }

    public static int getJoinMutedCount() {
        return LOGIC.joinMutedCount();
    }

    public static int getJoinPlayedCount() {
        return LOGIC.joinPlayedCount();
    }

    public static void recordGraceSuppression() {
        LOGIC.recordGraceSuppression();
    }

    public static boolean shouldPlaySuitBootSound(boolean currentlyFullSet) {
        return LOGIC.shouldPlaySuitBootSound(currentlyFullSet);
    }

    public static boolean shouldPlayBladeIgniteSound(boolean currentlyHoldingBlade) {
        return LOGIC.shouldPlayBladeIgniteSound(currentlyHoldingBlade);
    }
}
