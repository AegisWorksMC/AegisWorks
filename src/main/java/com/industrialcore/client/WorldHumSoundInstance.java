package com.industrialcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Phase 102: 3D Mono looping sound instance for active world machines with smooth linear
 * distance attenuation and click-free fade-in / fade-out.
 *
 * <p>Operates strictly on the World Ambient 3D channel, completely isolated from
 * {@link SoundDirector}'s Machine GUI channel.</p>
 */
public final class WorldHumSoundInstance extends AbstractTickableSoundInstance {
    private final long posKey;
    private final WorldAmbientLogic.Category category;
    private float targetVolume;
    private boolean fadingOut = false;

    public WorldHumSoundInstance(SoundEvent event, BlockPos pos,
                                 WorldAmbientLogic.Category category, float initialTargetVolume) {
        super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.posKey = pos.asLong();
        this.category = category;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
        this.pitch = 1.00f;
        this.targetVolume = Math.max(0.0f, initialTargetVolume);
        // Start silently and fade smoothly upward to prevent any click on playback start
        this.volume = 0.005f;
    }

    public long getPosKey() {
        return posKey;
    }

    public WorldAmbientLogic.Category getCategory() {
        return category;
    }

    public void setTargetVolume(float target) {
        if (!fadingOut) {
            this.targetVolume = Math.max(0.0f, target);
        }
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    public void fadeOutAndStop() {
        this.fadingOut = true;
        this.targetVolume = 0.0f;
    }

    public void stopImmediately() {
        this.fadingOut = true;
        this.targetVolume = 0.0f;
        this.volume = 0.0f;
        this.stop();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            stopImmediately();
            return;
        }
        this.volume = WorldAmbientLogic.stepFade(this.volume, this.targetVolume, WorldAmbientLogic.FADE_STEP);
        if (fadingOut && this.volume <= 0.004f) {
            this.volume = 0.0f;
            this.stop();
        }
    }
}
