package com.industrialcore.client;

import com.industrialcore.machine.FactoryAudioLogic;
import com.industrialcore.machine.FactoryAudioLogic.MachineAudioProfile;
import com.industrialcore.machine.FactoryAudioLogic.SoundFamily;
import com.industrialcore.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 112: Client-only motion-synchronized factory SFX controller backed by
 * {@link FactoryAudioLogic.BudgetTracker}. Guarantees 0-frame alignment with BER mechanical
 * strokes, strict 12-block distance silence, per-machine volume/pitch/cooldown profiles, and
 * zero OpenAL channel starvation (at most 2 sounds/tick globally).
 */
public final class FactoryAudioController {
    private static final FactoryAudioLogic.BudgetTracker BUDGET = new FactoryAudioLogic.BudgetTracker();
    private static final Map<Long, Float> LAST_PHASES = new HashMap<>(64);
    private static Level lastLevel = null;
    private static long totalPlayedCount = 0L;

    private FactoryAudioController() {}

    public static void reset() {
        BUDGET.reset();
        LAST_PHASES.clear();
        lastLevel = null;
        totalPlayedCount = 0L;
    }

    public static long getTotalPlayedCount() {
        return totalPlayedCount;
    }

    /**
     * Called directly from a Tier 2 BER with the machine's block ID and cyclic motion phase {@code [0, 1)}.
     */
    public static void onMotionPhase(BlockPos pos, String blockId, float currPhase01, float triggerPoint01) {
        MachineAudioProfile profile = FactoryAudioLogic.profileFor(blockId);
        if (profile == null) {
            return;
        }
        onMotionPhase(pos, profile, profile.basePitch(), currPhase01, triggerPoint01);
    }

    /**
     * Overload allowing dynamic pitch scaling (e.g. flywheel charge-linked pitch).
     */
    public static void onMotionPhase(BlockPos pos, String blockId, float pitchOverride,
                                     float currPhase01, float triggerPoint01) {
        MachineAudioProfile profile = FactoryAudioLogic.profileFor(blockId);
        if (profile == null) {
            return;
        }
        onMotionPhase(pos, profile, pitchOverride, currPhase01, triggerPoint01);
    }

    public static void onMotionPhase(BlockPos pos, MachineAudioProfile profile, float basePitch,
                                     float currPhase01, float triggerPoint01) {
        if (profile == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ensureLevel(mc.level);
        double distSq = mc.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (!FactoryAudioLogic.isWithinAudibleRange(distSq)) {
            return;
        }
        long key = pos.asLong();
        float wrapped = FactoryAudioLogic.wrap01(currPhase01);
        Float prev = LAST_PHASES.put(key, wrapped);
        if (prev == null) {
            return;
        }
        if (FactoryAudioLogic.crossedPhaseTrigger(prev, wrapped, triggerPoint01)) {
            playIfBudgetAllows(mc, pos, profile, basePitch, distSq);
        }
    }

    /**
     * Called from {@code animateTick} (for non-BER active machines) using the exact same
     * 12-block cutoff, per-machine profile, and 2-sound/tick budget guard.
     */
    public static void tryPlayAmbientPulse(BlockPos pos, String blockId) {
        MachineAudioProfile profile = FactoryAudioLogic.profileFor(blockId);
        if (profile == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ensureLevel(mc.level);
        double distSq = mc.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (!FactoryAudioLogic.isWithinAudibleRange(distSq)) {
            return;
        }
        playIfBudgetAllows(mc, pos, profile, profile.basePitch(), distSq);
    }

    private static void ensureLevel(Level level) {
        if (lastLevel != level) {
            lastLevel = level;
            BUDGET.reset();
            LAST_PHASES.clear();
        } else if (LAST_PHASES.size() > 512) {
            LAST_PHASES.clear();
        }
    }

    private static void playIfBudgetAllows(Minecraft mc, BlockPos pos, MachineAudioProfile profile,
                                           float basePitch, double distSq) {
        if (WorldJoinAudioGuard.isInJoinGrace()) {
            WorldJoinAudioGuard.recordGraceSuppression();
            return;
        }
        long nowTick = mc.level.getGameTime();
        if (!BUDGET.tryAcquire(profile.family(), profile.cooldownTicks(), nowTick, distSq)) {
            return;
        }
        SoundEvent event = resolveSoundEvent(profile.family());
        float vol = FactoryAudioLogic.attenuatedVolume(profile.volume(), distSq);
        float pitch = FactoryAudioLogic.variedPitch(basePitch, mc.level.random.nextFloat());
        mc.level.playLocalSound(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                event, SoundSource.BLOCKS, vol, pitch, false);
        totalPlayedCount++;
    }

    public static SoundEvent resolveSoundEvent(SoundFamily family) {
        return switch (family) {
            case WIND_BLADE -> ModSounds.MACHINE_WIND_BLADE.get();
            case HYDRO_TURBINE -> ModSounds.MACHINE_HYDRO_TURBINE.get();
            case BOILER_FIRE -> ModSounds.MACHINE_BOILER_FIRE.get();
            case ENGINE_PISTON -> ModSounds.MACHINE_ENGINE_PISTON.get();
            case STEAM_TURBINE -> ModSounds.MACHINE_STEAM_TURBINE.get();
            case NUCLEAR_CORE -> ModSounds.MACHINE_NUCLEAR_CORE.get();
            case SINGULARITY_PULSE -> ModSounds.MACHINE_SINGULARITY_PULSE.get();
            case PRESS_IMPACT -> ModSounds.MACHINE_PRESS_IMPACT.get();
            case CRUSHER_JAW -> ModSounds.MACHINE_CRUSHER_JAW.get();
            case FURNACE_ARC -> ModSounds.MACHINE_FURNACE_ARC.get();
            case ELECTROLYZER_BUBBLE -> ModSounds.MACHINE_ELECTROLYZER_BUBBLE.get();
            case ASSEMBLER_SERVO -> ModSounds.MACHINE_ASSEMBLER_SERVO.get();
            case DRILL_BORE -> ModSounds.MACHINE_DRILL_BORE.get();
            case COMPRESSOR_SLAM -> ModSounds.MACHINE_COMPRESSOR_SLAM.get();
            case HARVESTER_BLADE -> ModSounds.MACHINE_HARVESTER_BLADE.get();
            case WINCH_REEL -> ModSounds.MACHINE_WINCH_REEL.get();
            case FAN_AIRFLOW -> ModSounds.MACHINE_FAN_AIRFLOW.get();
            case QUANTUM_CHIME -> ModSounds.MACHINE_QUANTUM_CHIME.get();
        };
    }
}
