package com.industrialcore.client;

import com.industrialcore.weapon.LaserAudioLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Clickless, player-attached looping sound instance for one layer of the Pulse Laser
 * (Light base, Heavy high-energy sub-bass growl, or Hit Sizzle burn).
 */
public final class LaserLoopSoundInstance extends AbstractTickableSoundInstance {
    private float targetVolume;
    private float targetPitch;
    private boolean releasing = false;

    public LaserLoopSoundInstance(SoundEvent event, float initialTargetVolume, float initialPitch) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.relative = true;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        this.pitch = Math.max(0.5F, Math.min(2.0F, initialPitch));
        this.targetPitch = this.pitch;
        this.targetVolume = Math.max(0.0F, initialTargetVolume);
        // Start near-silent to prevent click on OpenAL stream start
        this.volume = 0.008F;
    }

    public void updateTargets(float volume, float pitch) {
        this.releasing = false;
        this.targetVolume = Math.max(0.0F, volume);
        this.targetPitch = Math.max(0.5F, Math.min(2.0F, pitch));
    }

    public void beginRelease() {
        this.releasing = true;
        this.targetVolume = 0.0F;
    }

    public void stopImmediately() {
        this.releasing = true;
        this.targetVolume = 0.0F;
        this.volume = 0.0F;
        this.stop();
    }

    public boolean isReleasing() {
        return releasing;
    }

    public float currentVolume() {
        return this.volume;
    }

    public float currentPitch() {
        return this.pitch;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            stopImmediately();
            return;
        }
        this.volume = LaserAudioLogic.stepVolume(this.volume, this.targetVolume);
        this.pitch = LaserAudioLogic.stepPitch(this.pitch, this.targetPitch);
        if (releasing && this.volume <= 0.004F) {
            this.volume = 0.0F;
            this.stop();
        }
    }
}
