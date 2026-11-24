package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.blockentity.AssemblerBlockEntity;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.PhaseReactorBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.blockentity.QuantumCompressorBlockEntity;
import com.industrialcore.blockentity.ReconstructorBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.network.BeatPayload;
import com.industrialcore.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client-only audio director with strict channel separation and intelligent cadence control.
 *
 * <ul>
 *   <li><b>Machine GUI Channel</b> ({@code machine_tick_note}, {@code machine_complete},
 *       {@code machine_jackpot}): strictly gated on an open Industrial Core container screen,
 *       capped at {@value SoundCadenceController#MAX_MACHINE_VOICES} simultaneous voices,
 *       throttled &amp; auto-ducked by {@link SoundCadenceController}, and stopped instantaneously
 *       via {@link #stopMachineGuiSounds()} on screen close or logout.</li>
 *   <li><b>Global Notification / Tree / UI Channel</b> ({@code level_up}, {@code limit_break},
 *       {@code tree_*}, {@code ui_*}): independent of machine GUI close, and hero events briefly
 *       suppress &amp; duck the Machine GUI channel so milestones ring out clearly.</li>
 * </ul>
 */
public final class SoundDirector {
    public static final float[] PENTATONIC = SoundRhythm.PENTATONIC;

    public static final int GUI_PULSE_TICKS = SoundRhythm.GUI_PULSE_TICKS;
    public static final int GUI_REACH_TICKS = SoundRhythm.GUI_REACH_TICKS;
    public static final int WORLD_AMBIENT_TICKS = SoundRhythm.WORLD_AMBIENT_TICKS;
    public static final int WORLD_RADIUS = SoundRhythm.WORLD_RADIUS;
    public static final int MAX_WORLD_SOUNDS_PER_BEAT = SoundRhythm.MAX_WORLD_SOUNDS_PER_BEAT;
    private static final long HOVER_COOLDOWN_MS = 45L;
    private static final long LOCKED_COOLDOWN_MS = 250L;
    private static final long MILESTONE_COOLDOWN_MS = 180L;

    /** Dedicated cadence &amp; auto-ducking controller for the Machine GUI channel. */
    private static final SoundCadenceController CADENCE = new SoundCadenceController();
    /** Dedicated delayed-playback queue for the Machine GUI channel ONLY. */
    private static final Deque<ScheduledNote> MACHINE_QUEUE = new ArrayDeque<>();
    /** Active playing {@link SoundInstance}s on the Machine GUI channel (max 5 voices). */
    private static final Deque<SoundInstance> ACTIVE_MACHINE_SOUNDS = new ArrayDeque<>();
    /** Dedicated looping hum instance for an open actively-generating generator GUI. */
    private static SoundInstance activeGeneratorHum;
    private static GeneratorAudioProfile.Category activeGeneratorCategory;

    private static final List<BeatPayload.Entry> CACHED_BEATS = new ArrayList<>();
    private static long cachedBeatsAtMs;
    private static int tickCounter;
    private static int worldBeatIndex;
    private static long lastHoverMs;
    private static long lastLockedMs;
    private static long lastMilestoneMs;
    private static int machineGuiSoundsPlayed;
    private static int machineTickNotesPlayed;
    private static int machineCompletesPlayed;
    private static int machineExtractsPlayed;
    private static int machineManualExtractsPlayed;
    private static int machineAutoExtractsPlayed;
    private static int machineJackpotsPlayed;
    private static int machineStreakUpsPlayed;
    private static float lastCompletePitch;
    private static int generatorHumsStarted;
    private static int generatorPulsesPlayed;
    private static String lastGeneratorHumSoundId = "";

    private SoundDirector() {}

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public static float pentatonicPitch(double ratio, int stepOffset) {
        return SoundRhythm.pentatonicPitch(ratio, stepOffset);
    }

    /** True only while an Industrial Core container GUI is actively displayed on screen. */
    public static boolean isMachineGuiOpen() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return false;
            }
            Screen screen = mc.screen;
            return screen instanceof AbstractContainerScreen<?>
                    && screen.getClass().getPackageName().equals("com.industrialcore.client");
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Immediate Machine GUI Channel Shutdown & Logout Cleanup
    // ------------------------------------------------------------------

    /**
     * Immediately cancels every scheduled Machine GUI sound, stops all currently playing Machine
     * GUI {@link SoundInstance}s in OpenAL / {@link SoundManager}, and resets combo/riser/progress
     * session state. Global notification &amp; Skill Tree sounds remain unaffected.
     */
    public static void stopMachineGuiSounds() {
        MACHINE_QUEUE.clear();
        stopGeneratorHum();
        SoundManager sm = soundManagerOrNull();
        while (!ACTIVE_MACHINE_SOUNDS.isEmpty()) {
            SoundInstance inst = ACTIVE_MACHINE_SOUNDS.pollFirst();
            if (inst != null && sm != null) {
                try {
                    sm.stop(inst);
                } catch (Throwable ignored) {
                }
            }
        }
        CADENCE.resetMachineSession();
        SoundRhythm.resetShepard();
        lastMilestoneMs = 0L;
        GuiDopamine.clearPendingAudio();
    }

    /** Full audio shutdown when disconnecting from a world / logging out. */
    public static void stopAllOnLogout() {
        stopMachineGuiSounds();
        CADENCE.resetAll();
        CACHED_BEATS.clear();
        cachedBeatsAtMs = 0L;
        lastHoverMs = 0L;
        lastLockedMs = 0L;
        lastMilestoneMs = 0L;
    }

    // ------------------------------------------------------------------
    // Global UI / Notification / Skill Tree Channel
    // ------------------------------------------------------------------

    /** Intentional UI button / tab / node click (clear 0.52 volume). */
    public static void playClick() {
        SoundRhythm.recordClick();
        playGlobalUi(ModSounds.UI_CLICK::get, 1.00f, 0.52f);
    }

    /** Tactile wooden double-knock on locked nodes / guarded core removal (250ms cooldown, 0.55 volume). */
    public static void playUiLocked() {
        long now = nowMs();
        if (now - lastLockedMs < LOCKED_COOLDOWN_MS) {
            return;
        }
        lastLockedMs = now;
        SoundRhythm.recordClick();
        playGlobalUi(ModSounds.UI_LOCKED::get, 1.00f, 0.55f);
    }

    /** Opening a machine GUI is intentionally silent to prevent noise fatigue. */
    public static void playGuiOpen() {
        SoundRhythm.recordOpenClose();
    }

    /** Closing a machine GUI cleanly silences the Machine GUI channel without extra noise. */
    public static void playGuiClose() {
        SoundRhythm.recordOpenClose();
        stopMachineGuiSounds();
    }

    /** Micro-tactile feedback for intentional fine interactions (e.g. Quantum Storage Shift+Wheel ±1). */
    public static void playHoverTick() {
        long now = nowMs();
        if (now - lastHoverMs < HOVER_COOLDOWN_MS) {
            return;
        }
        lastHoverMs = now;
        SoundRhythm.recordHoverTick();
        playGlobalUi(ModSounds.UI_CLICK::get, 1.38f, 0.14f);
    }

    /** Global Hero Event: Machine Level-Up chime (ducks &amp; suppresses machine GUI audio for 1.0s). */
    public static void playLevelUp() {
        SoundRhythm.recordMilestone();
        stopMachineGuiSounds();
        CADENCE.recordHeroEvent(nowMs());
        playGlobalUi(ModSounds.LEVEL_UP::get, 1.00f, 0.50f);
    }

    /** Global Hero Event: Lv.1000 Limit Break fanfare (suppresses machine GUI audio for 1.0s). */
    public static void playLimitBreakFanfare() {
        SoundRhythm.recordMilestone();
        stopMachineGuiSounds();
        CADENCE.recordHeroEvent(nowMs());
        playGlobalUi(ModSounds.LIMIT_BREAK::get, 1.00f, 0.58f);
    }

    /** Skill Tree WIRE_PULSE airy crystal riser, scaled by Tier 2..5. */
    public static void playWireRiser(int tier) {
        if (tier >= 5) {
            playGlobalUi(ModSounds.TREE_RISER::get, 0.88f, 0.48f);
        } else if (tier == 4) {
            playGlobalUi(ModSounds.TREE_RISER::get, 0.96f, 0.40f);
        } else if (tier == 3) {
            playGlobalUi(ModSounds.TREE_RISER::get, 1.04f, 0.34f);
        } else {
            playGlobalUi(ModSounds.TREE_RISER::get, 1.12f, 0.28f);
        }
    }

    public static void playNodeUnlock() {
        playNodeUnlock(2);
    }

    /** Global Hero Event: Tier-scaled Skill Tree unlock SE (suppresses machine GUI audio for 1.0s). */
    public static void playNodeUnlock(int tier) {
        SoundRhythm.recordMilestone();
        stopMachineGuiSounds();
        CADENCE.recordHeroEvent(nowMs());
        if (tier >= 5) {
            playGlobalUi(ModSounds.TREE_UNLOCK_T5::get, 1.00f, 0.56f);
        } else if (tier == 4) {
            playGlobalUi(ModSounds.TREE_UNLOCK_T4::get, 1.00f, 0.50f);
        } else if (tier == 3) {
            playGlobalUi(ModSounds.TREE_UNLOCK_T3::get, 1.00f, 0.46f);
        } else {
            playGlobalUi(ModSounds.TREE_UNLOCK_T2::get, 1.00f, 0.42f);
        }
    }

    public static void playFogClear() {
        playFogClear(2);
    }

    public static void playFogClear(int tier) {
        float pitch = tier >= 4 ? 0.96f : 1.06f;
        float vol = tier >= 4 ? 0.38f : 0.32f;
        playGlobalUi(ModSounds.TREE_FOG_CLEAR::get, pitch, vol);
    }

    public static void playWarpChime() {
        SoundRhythm.recordMilestone();
        playGlobalUi(ModSounds.TREE_FOG_CLEAR::get, 1.08f, 0.32f);
    }

    // ------------------------------------------------------------------
    // Machine GUI Channel (strict screen guard, 5-voice cap, smart cadence)
    // ------------------------------------------------------------------

    public static void playSlotInsert() {
        SoundRhythm.recordSlotAction();
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        playMachineGuiSound(ModSounds.UI_CLICK::get, 1.12f, 0.42f);
    }

    public static void playUpgradeInsert(int installedAfter, int delta) {
        SoundRhythm.recordSlotAction();
        boolean complete = SoundRhythm.upgradeComplete(installedAfter);
        SoundRhythm.recordUpgradeInsert(complete);
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        float pitch = SoundRhythm.upgradePitch(installedAfter);
        float gain = 0.44f + Math.min(0.10f, Math.max(0, delta) * 0.004f);
        playMachineGuiSound(ModSounds.MACHINE_TICK_NOTE::get, pitch, gain);
        if (complete) {
            scheduleMachineSound(1, ModSounds.MACHINE_JACKPOT::get, 1.04f, 0.80f, true);
        }
    }

    public static void playOutputCollect(int count) {
        playOutputExtract(count, true);
    }

    /**
     * Plays the harmonizing {@code machine_extract} crystal swish on the Machine GUI channel.
     * Manual pickups play at {@code 0.34f} (100ms cooldown); automated pipe/hopper extraction
     * plays at {@code 0.20f} (220ms cooldown) so create-and-extract flows stay crisp and musical.
     */
    public static void playOutputExtract(int count, boolean manual) {
        SoundRhythm.recordSlotAction();
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        SoundCadenceController.ExtractDecision decision =
                CADENCE.tryPlayExtract(nowMs(), count, manual);
        if (!decision.allow()) {
            return;
        }
        if (playMachineGuiSound(ModSounds.MACHINE_EXTRACT::get, decision.pitch(), decision.volume())) {
            machineExtractsPlayed++;
            if (manual) {
                machineManualExtractsPlayed++;
            } else {
                machineAutoExtractsPlayed++;
            }
        }
    }

    public static void playMilestone(int quarter) {
        SoundRhythm.recordMilestone();
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        long now = nowMs();
        if (now - lastMilestoneMs < MILESTONE_COOLDOWN_MS) {
            return;
        }
        lastMilestoneMs = now;
        int q = Math.max(1, Math.min(3, quarter));
        playMachineGuiSound(ModSounds.MACHINE_TICK_NOTE::get, 1.00f + q * 0.14f, 0.44f);
    }

    public static void playIgnition() {
        SoundRhythm.recordMilestone();
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        playMachineGuiSound(ModSounds.MACHINE_TICK_NOTE::get, 0.92f, 0.44f);
        scheduleMachineSound(2, ModSounds.MACHINE_TICK_NOTE::get, 1.18f, 0.42f, false);
    }

    public static void playFullCharge() {
        SoundRhythm.recordMilestone();
        if (!isMachineGuiOpen() || GuiDopamine.isInOpenGrace()) {
            return;
        }
        playMachineGuiSound(ModSounds.GEN_POWER_PULSE::get, 1.00f, 0.44f);
        scheduleMachineSound(2, ModSounds.MACHINE_COMPLETE::get, 1.4983f, 0.42f, false);
    }

    /**
     * 24-step Shepard-Risset progress riser played during slow, medium, and fast motor-boosted
     * cycles alike, throttled to {@value SoundCadenceController#TICK_MIN_INTERVAL_MS}ms spacing
     * and up to {@value SoundCadenceController#MAX_TICKS_PER_CYCLE} notes per cycle at
     * {@code 0.38..0.52} volume.
     */
    public static void playProgressPulse(double ratio) {
        if (!isMachineGuiOpen()) {
            stopMachineGuiSounds();
            return;
        }
        if (GuiDopamine.isInOpenGrace()) {
            return;
        }
        int cycleTicks = GuiDopamine.currentCycleTicks();
        SoundCadenceController.TickDecision decision =
                CADENCE.tryPlayTickNote(nowMs(), cycleTicks, ratio);
        if (!decision.allow()) {
            return;
        }
        boolean reach = SoundRhythm.isReachZone(ratio);
        if (reach) {
            SoundRhythm.recordReachPulse();
        } else {
            SoundRhythm.recordPulse();
        }
        int step = SoundRhythm.shepardAdvance();
        float lowW = SoundRhythm.shepardLowWeight(step);
        float highW = SoundRhythm.shepardHighWeight(step);
        float baseVol = decision.volume();
        boolean played = false;
        if (SoundRhythm.audible(lowW)) {
            played |= playMachineGuiSound(ModSounds.MACHINE_TICK_NOTE::get,
                    SoundRhythm.shepardLowPitch(step), baseVol * (float) Math.sqrt(lowW));
        }
        if (SoundRhythm.audible(highW)) {
            played |= playMachineGuiSound(ModSounds.MACHINE_TICK_NOTE::get,
                    SoundRhythm.shepardHighPitch(step), baseVol * (float) Math.sqrt(highW));
        }
        if (played) {
            machineTickNotesPlayed++;
        }
    }

    /**
     * Phase 102.5: Fixed-pitch (1.00f) thinned completion rhythm (180ms spacing, 0.42f volume)
     * plus periodic C-Major level-up arpeggio ({@code machine_streak_up}, 0.56f, 1.00f) every
     * 8 completions (including thinned ones) with >= 2,000ms spacing.
     */
    public static void playComplete(int producedAmount, int comboStreak) {
        SoundRhythm.recordComplete(producedAmount, comboStreak);
        if (!isMachineGuiOpen()) {
            stopMachineGuiSounds();
            return;
        }
        if (GuiDopamine.isInOpenGrace()) {
            return;
        }
        SoundCadenceController.CompleteDecision decision =
                CADENCE.tryPlayComplete(nowMs(), producedAmount, comboStreak);
        if (decision.allow()) {
            if (playMachineGuiSound(ModSounds.MACHINE_COMPLETE::get, decision.pitch(), decision.volume())) {
                machineCompletesPlayed++;
                lastCompletePitch = decision.pitch();
            }
        }
        if (decision.allowStreakUp()) {
            if (playMachineGuiSound(ModSounds.MACHINE_STREAK_UP::get,
                    SoundCadenceController.STREAK_UP_PITCH, SoundCadenceController.STREAK_UP_VOLUME)) {
                machineStreakUpsPlayed++;
            }
        } else if (decision.allowJackpot()) {
            scheduleMachineSound(1, ModSounds.MACHINE_JACKPOT::get, 1.00f, 0.80f, true);
        }
    }

    private static void scheduleMachineSound(int tickOffset, Supplier<SoundEvent> eventSupplier,
                                             float pitch, float volume, boolean jackpot) {
        if (!isMachineGuiOpen()) {
            return;
        }
        while (MACHINE_QUEUE.size() >= SoundRhythm.MAX_SCHEDULED_NOTES) {
            MACHINE_QUEUE.poll();
        }
        MACHINE_QUEUE.add(new ScheduledNote(
                tickCounter + tickOffset, eventSupplier, pitch, volume, jackpot));
    }

    public static void onBeatPacket(BeatPayload payload) {
        CACHED_BEATS.clear();
        CACHED_BEATS.addAll(payload.entries());
        cachedBeatsAtMs = nowMs();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isPaused()) {
            return;
        }
        tickCounter++;

        // Strict screen guard: if no Industrial Core machine GUI is open, drop & silence immediately!
        if (!isMachineGuiOpen()) {
            if (!MACHINE_QUEUE.isEmpty() || !ACTIVE_MACHINE_SOUNDS.isEmpty() || activeGeneratorHum != null) {
                stopMachineGuiSounds();
            }
            GuiDopamine.clearPendingAudio();
        } else {
            boolean inOpenGrace = GuiDopamine.isInOpenGrace();

            // 1. Drain delayed Machine GUI channel queue
            while (!MACHINE_QUEUE.isEmpty() && MACHINE_QUEUE.peek().dueTick <= tickCounter) {
                ScheduledNote note = MACHINE_QUEUE.poll();
                if (!inOpenGrace && !CADENCE.isMachineSuppressed(nowMs())) {
                    if (playMachineGuiSound(note.eventSupplier, note.pitch, note.volume)
                            && note.jackpot) {
                        machineJackpotsPlayed++;
                    }
                }
            }

            // 2. Drain GUI completions detected by GuiDopamine
            for (int packed : GuiDopamine.drainAudioCompletions()) {
                if (inOpenGrace) {
                    continue;
                }
                int count = packed >>> 12;
                int streak = packed & 0xFFF;
                playComplete(count, streak);
            }

            // 3. Intermediate progress pulse (intelligently gated by SoundCadenceController)
            double activeRatio = GuiDopamine.consumeActiveAudioRatio();
            if (!inOpenGrace && activeRatio >= 0.0) {
                playProgressPulse(activeRatio);
            }

            // 3b. Method-specific generator hum & harmonizing energy pulse while actively generating
            updateGeneratorAudio(inOpenGrace);

            GuiDopamine.tickOpenGrace();
        }

        // 4. Subtle world ambience: one faint low hum per second, never a chime
        if ((tickCounter % SoundRhythm.WORLD_AMBIENT_TICKS) == 0 && mc.level != null && mc.player != null
                && !WorldJoinAudioGuard.isInJoinGrace()) {
            tickWorldAmbience(mc, mc.level, mc.player);
        }
    }

    private static void tickWorldAmbience(Minecraft mc, ClientLevel level, Player player) {
        long now = nowMs();
        if (!CACHED_BEATS.isEmpty() && (now - cachedBeatsAtMs) <= 1200L) {
            playBeatEntries(mc, level, player, CACHED_BEATS);
            return;
        }
        BlockPos center = player.blockPosition();
        int r = WORLD_RADIUS;
        int r2 = r * r;
        int minCx = (center.getX() - r) >> 4;
        int maxCx = (center.getX() + r) >> 4;
        int minCz = (center.getZ() - r) >> 4;
        int maxCz = (center.getZ() + r) >> 4;

        List<BlockEntity> candidates = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(center) <= r2 && isActiveWorldMachine(be)) {
                        candidates.add(be);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));
        List<BeatPayload.Entry> entries = new ArrayList<>(Math.min(MAX_WORLD_SOUNDS_PER_BEAT, candidates.size()));
        for (int i = 0; i < Math.min(MAX_WORLD_SOUNDS_PER_BEAT, candidates.size()); i++) {
            entries.add(new BeatPayload.Entry(candidates.get(i).getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 75));
        }
        playBeatEntries(mc, level, player, entries);
    }

    private static void playBeatEntries(Minecraft mc, ClientLevel level, Player player, List<BeatPayload.Entry> entries) {
        BlockPos center = player.blockPosition();
        int limit = Math.min(MAX_WORLD_SOUNDS_PER_BEAT, entries.size());
        RandomSource rand = level.getRandom();
        for (int i = 0; i < limit; i++) {
            BeatPayload.Entry e = entries.get(i);
            BlockPos pos = e.pos();
            double dist = Math.sqrt(pos.distSqr(center));
            float vol = SoundRhythm.worldVolume(dist);
            if (vol <= 0.0f) {
                continue;
            }
            // Phase 102: 3D Mono world hums are handled by WorldAmbientAudioController;
            // keep subtle working particles here without legacy vanilla copper clatter.
            SoundRhythm.recordWorldBeat();

            var pt = switch (e.category()) {
                case BeatPayload.CAT_GENERATION -> ParticleTypes.FLAME;
                case BeatPayload.CAT_TRANSPORTER -> ParticleTypes.END_ROD;
                default -> ParticleTypes.ELECTRIC_SPARK;
            };
            level.addParticle(pt,
                    pos.getX() + 0.3 + rand.nextDouble() * 0.4,
                    pos.getY() + 1.05,
                    pos.getZ() + 0.3 + rand.nextDouble() * 0.4,
                    0.0, 0.03, 0.0);
        }
    }

    static boolean isActiveWorldMachine(BlockEntity be) {
        BlockState state = be.getBlockState();
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            return true;
        }
        if (be instanceof ProcessingBlockEntity p) {
            return p.getStatus() == MachineStatus.WORKING;
        }
        if (be instanceof GenerationBlockEntity g) {
            return g.getRate() > 0 || g.getBurnTime() > 0;
        }
        if (be instanceof RecoveryBlockEntity rec) {
            return rec.getRate() > 0;
        }
        if (be instanceof AlloyFurnaceBlockEntity af) {
            return af.getProgress() > 0 || af.getBurnTime() > 0 || af.getStatus() == MachineStatus.WORKING;
        }
        if (be instanceof QuarryBlockEntity q) {
            return q.isRunning();
        }
        if (be instanceof LargeQuarryBlockEntity lq) {
            return lq.isRunning();
        }
        if (be instanceof MatterAmplifierBlockEntity ma) {
            return ma.getStatus() == MachineStatus.WORKING || ma.getProgress() > 0;
        }
        if (be instanceof PhaseReactorBlockEntity pr) {
            return pr.getStatus() == MachineStatus.WORKING || pr.getProgress() > 0;
        }
        if (be instanceof QuantumCompressorBlockEntity qc) {
            return qc.getStatus() == MachineStatus.WORKING || qc.getProgress() > 0;
        }
        if (be instanceof SeedCultivatorBlockEntity sc) {
            return sc.getStatus() == MachineStatus.WORKING || sc.getProgress() > 0;
        }
        if (be instanceof OreSynthesizerBlockEntity os) {
            return os.getStatusForTesting() == MachineStatus.WORKING || os.getProgressForTesting() > 0;
        }
        if (be instanceof ReconstructorBlockEntity rc) {
            return rc.getStatus() == MachineStatus.WORKING || rc.getProgress() > 0;
        }
        if (be instanceof AssemblerBlockEntity as) {
            return as.getStatus() == MachineStatus.WORKING || as.getProgress() > 0;
        }
        if (be instanceof CrafterBlockEntity cr) {
            return cr.getProgress() > 0 || cr.getStatus() == CrafterStatus.CRAFTING;
        }
        return false;
    }

    private static SoundManager soundManagerOrNull() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null ? null : mc.getSoundManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isMasterAudible() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.options != null
                    && mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0.0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SoundEvent resolveGeneratorHumEvent(GeneratorAudioProfile.Category category) {
        return switch (category) {
            case THERMAL -> ModSounds.GEN_THERMAL_HUM.get();
            case KINETIC -> ModSounds.GEN_KINETIC_HUM.get();
            case REACTOR -> ModSounds.GEN_REACTOR_HUM.get();
        };
    }

    private static void stopGeneratorHumKeepCadence() {
        SoundInstance inst = activeGeneratorHum;
        activeGeneratorHum = null;
        activeGeneratorCategory = null;
        if (inst != null) {
            SoundManager sm = soundManagerOrNull();
            if (sm != null) {
                try {
                    sm.stop(inst);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Immediately stops any active generator hum and resets generator pulse timing. */
    public static void stopGeneratorHum() {
        stopGeneratorHumKeepCadence();
        CADENCE.resetGeneratorSession();
    }

    private static void updateGeneratorAudio(boolean inOpenGrace) {
        long now = nowMs();
        GuiDopamine.GeneratorAudioState genState = GuiDopamine.currentGeneratorAudioState();
        if (inOpenGrace || genState == null || !genState.generating() || CADENCE.isMachineSuppressed(now)) {
            if (activeGeneratorHum != null) {
                stopGeneratorHum();
            } else {
                CADENCE.resetGeneratorSession();
            }
            return;
        }

        GeneratorAudioProfile.Profile profile = genState.profile();
        GeneratorAudioProfile.Category category = profile.category();
        SoundManager sm = soundManagerOrNull();
        if (sm != null) {
            boolean needStart = activeGeneratorHum == null
                    || activeGeneratorCategory != category
                    || (isMasterAudible() && !sm.isActive(activeGeneratorHum));
            if (needStart) {
                stopGeneratorHumKeepCadence();
                try {
                    SoundEvent humEvent = resolveGeneratorHumEvent(category);
                    SoundInstance humInstance = new SimpleSoundInstance(
                            humEvent.getLocation(),
                            SoundSource.MASTER,
                            category.humVolume(),
                            1.00f,
                            SoundInstance.createUnseededRandom(),
                            true,
                            0,
                            SoundInstance.Attenuation.NONE,
                            0.0D, 0.0D, 0.0D,
                            true);
                    sm.play(humInstance);
                    activeGeneratorHum = humInstance;
                    activeGeneratorCategory = category;
                    lastGeneratorHumSoundId = category.humSoundId();
                    generatorHumsStarted++;
                    machineGuiSoundsPlayed++;
                } catch (Throwable ignored) {
                }
            }
        }

        SoundCadenceController.GeneratorPulseDecision pulseDecision =
                CADENCE.tryPlayGeneratorPulse(now, category);
        if (pulseDecision.allow()) {
            if (playMachineGuiSound(ModSounds.GEN_POWER_PULSE::get,
                    profile.pulsePitch(), pulseDecision.volume())) {
                generatorPulsesPlayed++;
            }
        }
    }

    /** Plays a sound on the tracked Machine GUI channel, enforcing the 5-voice cap. */
    private static boolean playMachineGuiSound(Supplier<SoundEvent> soundSupplier,
                                               float pitch, float volume) {
        if (!isMachineGuiOpen() || CADENCE.isMachineSuppressed(nowMs())) {
            return false;
        }
        SoundManager sm = soundManagerOrNull();
        if (sm == null) {
            return false;
        }
        try {
            pruneFinishedMachineSounds(sm);
            SoundInstance instance = SimpleSoundInstance.forUI(soundSupplier.get(), pitch, volume);
            List<SoundInstance> evicted =
                    SoundCadenceController.pushWithVoiceCap(ACTIVE_MACHINE_SOUNDS, instance);
            for (SoundInstance old : evicted) {
                sm.stop(old);
            }
            sm.play(instance);
            machineGuiSoundsPlayed++;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Plays a sound on the Global UI / Notification / Skill Tree channel (never killed on GUI close). */
    private static void playGlobalUi(Supplier<SoundEvent> soundSupplier,
                                     float pitch, float volume) {
        SoundManager sm = soundManagerOrNull();
        if (sm == null) {
            return;
        }
        try {
            sm.play(SimpleSoundInstance.forUI(soundSupplier.get(), pitch, volume));
        } catch (Throwable ignored) {
        }
    }

    private static void pruneFinishedMachineSounds(SoundManager sm) {
        Iterator<SoundInstance> it = ACTIVE_MACHINE_SOUNDS.iterator();
        while (it.hasNext()) {
            SoundInstance s = it.next();
            if (!sm.isActive(s)) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics & Telemetry
    // ------------------------------------------------------------------

    public static int getPendingMachineQueueSize() {
        return MACHINE_QUEUE.size();
    }

    public static int getActiveMachineVoiceCount() {
        SoundManager sm = soundManagerOrNull();
        if (sm != null) {
            pruneFinishedMachineSounds(sm);
        }
        return ACTIVE_MACHINE_SOUNDS.size() + (activeGeneratorHum != null ? 1 : 0);
    }

    public static boolean isGeneratorHumActive() {
        return activeGeneratorHum != null;
    }

    public static int getMachineGuiSoundsPlayed() {
        return machineGuiSoundsPlayed;
    }

    public static int getMachineTickNotesPlayed() {
        return machineTickNotesPlayed;
    }

    public static int getMachineCompletesPlayed() {
        return machineCompletesPlayed;
    }

    public static int getMachineExtractsPlayed() {
        return machineExtractsPlayed;
    }

    public static int getMachineManualExtractsPlayed() {
        return machineManualExtractsPlayed;
    }

    public static int getMachineAutoExtractsPlayed() {
        return machineAutoExtractsPlayed;
    }

    public static int getMachineJackpotsPlayed() {
        return machineJackpotsPlayed;
    }

    public static int getMachineStreakUpsPlayed() {
        return machineStreakUpsPlayed;
    }

    public static float getLastCompletePitch() {
        return lastCompletePitch;
    }

    public static int getGeneratorHumsStarted() {
        return generatorHumsStarted;
    }

    public static int getGeneratorPulsesPlayed() {
        return generatorPulsesPlayed;
    }

    public static String getLastGeneratorHumSoundId() {
        return lastGeneratorHumSoundId;
    }

    public static int getCadenceComboCount() {
        return CADENCE.getComboCount();
    }

    public static void resetCounters() {
        stopAllOnLogout();
        tickCounter = 0;
        worldBeatIndex = 0;
        machineGuiSoundsPlayed = 0;
        machineTickNotesPlayed = 0;
        machineCompletesPlayed = 0;
        machineExtractsPlayed = 0;
        machineManualExtractsPlayed = 0;
        machineAutoExtractsPlayed = 0;
        machineJackpotsPlayed = 0;
        machineStreakUpsPlayed = 0;
        lastCompletePitch = 0.0f;
        generatorHumsStarted = 0;
        generatorPulsesPlayed = 0;
        lastGeneratorHumSoundId = "";
        SoundRhythm.resetCounters();
    }

    public static int getPulseCount() {
        return SoundRhythm.getPulseCount();
    }

    public static int getChimeCount() {
        return SoundRhythm.getChimeCount();
    }

    public static int getComboArpeggioCount() {
        return SoundRhythm.getComboArpeggioCount();
    }

    public static int getWorldBeatCount() {
        return SoundRhythm.getWorldBeatCount();
    }

    public static int getClickCount() {
        return SoundRhythm.getClickCount();
    }

    public static int getOpenCloseCount() {
        return SoundRhythm.getOpenCloseCount();
    }

    public static int getHoverTickCount() {
        return SoundRhythm.getHoverTickCount();
    }

    public static int getSlotActionCount() {
        return SoundRhythm.getSlotActionCount();
    }

    public static int getMilestoneCount() {
        return SoundRhythm.getMilestoneCount();
    }

    public static int getUpgradeInsertCount() {
        return SoundRhythm.getUpgradeInsertCount();
    }

    public static int getUpgradeFanfareCount() {
        return SoundRhythm.getUpgradeFanfareCount();
    }

    public static int getShepardMaxStepReached() {
        return SoundRhythm.getShepardMaxStepReached();
    }

    public static int getReachRollCount() {
        return SoundRhythm.getReachRollCount();
    }

    public static int getJackpotFanfareCount() {
        return SoundRhythm.getJackpotFanfareCount();
    }

    public static int getMidArpeggioCount() {
        return SoundRhythm.getMidArpeggioCount();
    }

    private record ScheduledNote(int dueTick, Supplier<SoundEvent> eventSupplier,
                                 float pitch, float volume, boolean jackpot) {}
}
