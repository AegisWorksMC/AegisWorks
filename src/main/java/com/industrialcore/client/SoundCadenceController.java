package com.industrialcore.client;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pure-Java intelligent audio cadence, fixed-pitch thinned completion rhythm, periodic level-up
 * streak fanfare, voice-limit, and hero-event suppression controller for the Machine GUI channel.
 *
 * <p>Zero Minecraft imports so every timing, thinning, cooldown, and eviction rule is verified
 * deterministically in unit tests.</p>
 */
public final class SoundCadenceController {
    /** Minimum spacing between intermediate {@code machine_tick_note} pulses (unchanged). */
    public static final long TICK_MIN_INTERVAL_MS = 120L;
    /** Generous cap on intermediate {@code machine_tick_note} pulses per processing cycle. */
    public static final int MAX_TICKS_PER_CYCLE = 16;
    /** Balanced volume bounds for {@code machine_tick_note}. */
    public static final float TICK_VOL_MIN = 0.38f;
    public static final float TICK_VOL_MAX = 0.52f;

    /** Phase 102.5: Minimum spacing between normal {@code machine_complete} notes (rhythmic thinning). */
    public static final long COMPLETE_COOLDOWN_MS = 180L;
    /** Continuous-operation streak window; pauses longer than 3.0s reset streak count to 0. */
    public static final long COMBO_WINDOW_MS = 3000L;

    /** Phase 102.5: Fixed crystal pitch (1.00f) and steady balanced volume (0.42f) for {@code machine_complete}. */
    public static final float COMPLETE_FIXED_PITCH = 1.00f;
    public static final float COMPLETE_STEADY_VOLUME = 0.42f;

    /** Phase 102.5: Periodic level-up arpeggio ({@code machine_streak_up}) every 8 items, >= 2,000ms apart. */
    public static final int STREAK_UP_THRESHOLD = 8;
    public static final int STREAK_UP_INTERVAL = 8;
    public static final long STREAK_UP_COOLDOWN_MS = 2000L;
    public static final float STREAK_UP_VOLUME = 0.56f;
    public static final float STREAK_UP_PITCH = 1.00f;

    /** Legacy volume aliases kept for test/caller compatibility. */
    public static final float COMPLETE_VOL_FIRST = COMPLETE_STEADY_VOLUME;
    public static final float COMPLETE_VOL_STEADY = COMPLETE_STEADY_VOLUME;

    /** Harmonizing extract sound ({@code machine_extract}) manual vs automated pipe parameters. */
    public static final float EXTRACT_MANUAL_VOL = 0.34f;
    public static final long EXTRACT_MANUAL_COOLDOWN_MS = 100L;
    public static final float EXTRACT_AUTO_VOL = 0.20f;
    public static final long EXTRACT_AUTO_COOLDOWN_MS = 220L;
    public static final float EXTRACT_PITCH = 1.00f;

    /** Gentle bloom delay after generator hum starts before the first {@code gen_power_pulse}. */
    public static final long GENERATOR_FIRST_PULSE_DELAY_MS = 350L;

    /** Minimum cooldown between celebratory {@code machine_jackpot} fanfares. */
    public static final long JACKPOT_COOLDOWN_MS = 1500L;
    /** Suppression window for machine GUI audio when a hero event (level-up/unlock/limit break) fires. */
    public static final long HERO_SUPPRESS_MS = 1000L;
    /** Maximum simultaneous active voices on the Machine GUI audio channel. */
    public static final int MAX_MACHINE_VOICES = 5;
    /** Initial grace ticks after opening a machine GUI to ignore first-sync ContainerData deltas. */
    public static final int OPEN_GRACE_TICKS = 3;

    private long lastTickMs = -1L;
    private int ticksInCurrentCycle = 0;

    private long lastCompleteEventMs = -1L;
    private long lastCompleteSoundMs = -1L;
    private int comboCount = 0;
    private int completionsSinceStreakUp = 0;
    private long lastStreakUpMs = -1L;

    private long lastManualExtractMs = -1L;
    private long lastAutoExtractMs = -1L;

    private long generatorActiveSinceMs = -1L;
    private long lastGeneratorPulseMs = -1L;

    private long lastJackpotMs = -1L;
    private long heroSuppressUntilMs = -1L;

    public record TickDecision(boolean allow, float pitch, float volume) {
        public static final TickDecision DENIED = new TickDecision(false, 1.0f, 0.0f);
    }

    public record CompleteDecision(boolean allow, float pitch, float volume,
                                   boolean allowJackpot, boolean allowStreakUp, int comboCount) {
        public static final CompleteDecision DENIED =
                new CompleteDecision(false, 1.0f, 0.0f, false, false, 0);
    }

    public record ExtractDecision(boolean allow, float pitch, float volume, boolean manual) {
        public static final ExtractDecision DENIED = new ExtractDecision(false, 1.0f, 0.0f, false);
    }

    public record GeneratorPulseDecision(boolean allow, float volume) {
        public static final GeneratorPulseDecision DENIED = new GeneratorPulseDecision(false, 0.0f);
    }

    /**
     * Evaluates whether an intermediate {@code machine_tick_note} should play right now.
     * Unchanged per regression-prohibition rule.
     */
    public TickDecision tryPlayTickNote(long nowMs, int estimatedCycleTicks, double progressRatio) {
        if (isMachineSuppressed(nowMs)) {
            return TickDecision.DENIED;
        }
        if (ticksInCurrentCycle >= MAX_TICKS_PER_CYCLE) {
            return TickDecision.DENIED;
        }
        if (lastTickMs >= 0L && (nowMs - lastTickMs) < TICK_MIN_INTERVAL_MS) {
            return TickDecision.DENIED;
        }

        lastTickMs = nowMs;
        ticksInCurrentCycle++;
        double clamped = Math.max(0.0, Math.min(1.0, progressRatio));
        float pitch = (float) (0.94 + 0.30 * clamped);
        float vol = (float) (TICK_VOL_MIN + (TICK_VOL_MAX - TICK_VOL_MIN) * clamped);
        return new TickDecision(true, pitch, vol);
    }

    /**
     * Phase 102.5 retuned completion cadence:
     * <ul>
     *   <li>Fixed crystal pitch ({@value #COMPLETE_FIXED_PITCH}f) and steady volume ({@value #COMPLETE_STEADY_VOLUME}f).</li>
     *   <li>Every completion advances {@code comboCount} (resets to 1 after a &gt;3.0s pause).</li>
     *   <li>Normal {@code machine_complete} is thinned to at most once every {@value #COMPLETE_COOLDOWN_MS}ms
     *       so ultra-fast machines tap cleanly without muddying.</li>
     *   <li>Every {@value #STREAK_UP_INTERVAL} completions (counting thinned events!) and at least
     *       {@value #STREAK_UP_COOLDOWN_MS}ms since the last level-up arpeggio, fires
     *       {@code machine_streak_up} ({@code allowStreakUp = true}) at 1.00f pitch / 0.56f volume!</li>
     * </ul>
     */
    public CompleteDecision tryPlayComplete(long nowMs, int producedAmount, int externalCombo) {
        if (isMachineSuppressed(nowMs)) {
            return CompleteDecision.DENIED;
        }

        if (lastCompleteEventMs >= 0L && (nowMs - lastCompleteEventMs) <= COMBO_WINDOW_MS) {
            comboCount++;
            completionsSinceStreakUp++;
        } else {
            comboCount = 1;
            completionsSinceStreakUp = 1;
            lastStreakUpMs = -1L;
        }
        lastCompleteEventMs = nowMs;
        ticksInCurrentCycle = 0;

        // Rhythmic thinning for normal completion note (180ms minimum spacing)
        boolean allowNormal = (lastCompleteSoundMs < 0L || (nowMs - lastCompleteSoundMs) >= COMPLETE_COOLDOWN_MS);
        if (allowNormal) {
            lastCompleteSoundMs = nowMs;
        }

        // Periodic level-up arpeggio every 8 completions (including thinned ones) with >= 2,000ms spacing
        boolean allowStreakUp = false;
        if (completionsSinceStreakUp >= STREAK_UP_INTERVAL
                && (lastStreakUpMs < 0L || (nowMs - lastStreakUpMs) >= STREAK_UP_COOLDOWN_MS)) {
            allowStreakUp = true;
            lastStreakUpMs = nowMs;
            completionsSinceStreakUp = 0;
        }

        boolean jackpotWorthy = producedAmount >= 16 || externalCombo >= 10;
        boolean allowJackpot = false;
        if (allowNormal && !allowStreakUp && jackpotWorthy
                && (lastJackpotMs < 0L || (nowMs - lastJackpotMs) >= JACKPOT_COOLDOWN_MS)) {
            lastJackpotMs = nowMs;
            allowJackpot = true;
        }

        return new CompleteDecision(
                allowNormal,
                COMPLETE_FIXED_PITCH,
                COMPLETE_STEADY_VOLUME,
                allowJackpot,
                allowStreakUp,
                comboCount
        );
    }

    public ExtractDecision tryPlayExtract(long nowMs, int count, boolean manual) {
        if (count <= 0 || isMachineSuppressed(nowMs)) {
            return ExtractDecision.DENIED;
        }
        if (manual) {
            if (lastManualExtractMs >= 0L && (nowMs - lastManualExtractMs) < EXTRACT_MANUAL_COOLDOWN_MS) {
                return ExtractDecision.DENIED;
            }
            lastManualExtractMs = nowMs;
            lastAutoExtractMs = nowMs;
            return new ExtractDecision(true, EXTRACT_PITCH, EXTRACT_MANUAL_VOL, true);
        }
        if (lastAutoExtractMs >= 0L && (nowMs - lastAutoExtractMs) < EXTRACT_AUTO_COOLDOWN_MS) {
            return ExtractDecision.DENIED;
        }
        lastAutoExtractMs = nowMs;
        return new ExtractDecision(true, EXTRACT_PITCH, EXTRACT_AUTO_VOL, false);
    }

    public GeneratorPulseDecision tryPlayGeneratorPulse(long nowMs, GeneratorAudioProfile.Category category) {
        if (category == null || isMachineSuppressed(nowMs)) {
            return GeneratorPulseDecision.DENIED;
        }
        if (generatorActiveSinceMs < 0L) {
            generatorActiveSinceMs = nowMs;
            return GeneratorPulseDecision.DENIED;
        }
        if ((nowMs - generatorActiveSinceMs) < GENERATOR_FIRST_PULSE_DELAY_MS) {
            return GeneratorPulseDecision.DENIED;
        }
        if (lastGeneratorPulseMs >= 0L && (nowMs - lastGeneratorPulseMs) < category.pulseIntervalMs()) {
            return GeneratorPulseDecision.DENIED;
        }
        lastGeneratorPulseMs = nowMs;
        return new GeneratorPulseDecision(true, category.pulseVolume());
    }

    /** Phase 102.5: Fixed crystal pitch (1.00f) for every completion. */
    public static float comboPitch(int combo) {
        return COMPLETE_FIXED_PITCH;
    }

    /** Phase 102.5: Consistent balanced volume (0.42f) for every completion. */
    public static float comboVolume(int combo) {
        return COMPLETE_STEADY_VOLUME;
    }

    public void recordHeroEvent(long nowMs) {
        heroSuppressUntilMs = Math.max(heroSuppressUntilMs, nowMs + HERO_SUPPRESS_MS);
    }

    public boolean isMachineSuppressed(long nowMs) {
        return heroSuppressUntilMs >= 0L && nowMs < heroSuppressUntilMs;
    }

    public static <T> List<T> pushWithVoiceCap(Deque<T> activeVoices, T incoming) {
        List<T> evicted = new ArrayList<>();
        while (activeVoices.size() >= MAX_MACHINE_VOICES) {
            T oldest = activeVoices.pollFirst();
            if (oldest != null) {
                evicted.add(oldest);
            }
        }
        activeVoices.addLast(incoming);
        return evicted;
    }

    public void resetGeneratorSession() {
        generatorActiveSinceMs = -1L;
        lastGeneratorPulseMs = -1L;
    }

    public void resetMachineSession() {
        lastTickMs = -1L;
        ticksInCurrentCycle = 0;
        lastCompleteEventMs = -1L;
        lastCompleteSoundMs = -1L;
        comboCount = 0;
        completionsSinceStreakUp = 0;
        lastStreakUpMs = -1L;
        lastManualExtractMs = -1L;
        lastAutoExtractMs = -1L;
        resetGeneratorSession();
    }

    public void resetAll() {
        resetMachineSession();
        lastJackpotMs = -1L;
        heroSuppressUntilMs = -1L;
    }

    public int getComboCount() {
        return comboCount;
    }

    public int getTicksInCurrentCycle() {
        return ticksInCurrentCycle;
    }
}
