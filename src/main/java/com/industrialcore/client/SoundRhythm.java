package com.industrialcore.client;

/**
 * Pure-Java rhythm scale, timing constants and audio telemetry for {@link SoundDirector}.
 *
 * <h2>Shepard riser (Phase 78)</h2>
 *
 * <p>A normal pitch ladder has to stop or wrap, and both are audible: the run either hits a ceiling
 * or drops back to the bottom. A Shepard-Risset riser hides the wrap by playing <em>two</em> voices
 * one octave apart and cross-fading them with a sine-squared bell, so the voice that is about to
 * reach the top has already faded out while the voice that has just started at the bottom fades in.
 * Twenty four discrete steps fill one octave of phase:</p>
 *
 * <pre>
 *   theta = (step mod 24) / 24        in [0, 1)
 *   pLow  = 0.5 * 2^theta             in [0.5, 1.0)
 *   pHigh = 1.0 * 2^theta             in [1.0, 2.0)
 *   wLow  = sin^2(pi * theta / 2)     wHigh = cos^2(pi * theta / 2)     wLow + wHigh = 1
 * </pre>
 *
 * <p>At step 23 the low voice sits at 0.97 with weight 0.997; at step 24 the high voice sits exactly
 * at 1.0 with weight 1.0. The two are the same pitch at the same volume, so the ear hears one
 * endless climb instead of a loop, for 100 or 1,000 completions alike.</p>
 *
 * <p>Nothing here touches Minecraft, so the maths is unit tested directly.</p>
 */
public final class SoundRhythm {
    public static final float[] PENTATONIC = {
            0.707f, 0.794f, 0.891f, 1.059f, 1.189f, 1.414f, 1.587f, 1.782f
    };

    // ---------------------------------------------------------------- timing
    /** GUI progress pulse while a machine is below the reach zone (150 BPM). */
    public static final int GUI_PULSE_TICKS = 4;
    /** GUI progress pulse inside the reach zone: twice as fast, the "drum roll". */
    public static final int GUI_REACH_TICKS = 2;
    /** Progress ratio at which the reach zone starts. */
    public static final float REACH_THRESHOLD = 0.78f;
    /** World ambience: one faint sound per second, no more. */
    public static final int WORLD_AMBIENT_TICKS = 20;
    /** World ambience is limited to machines within this many blocks. */
    public static final int WORLD_RADIUS = 8;
    /** At most one world sound per ambient tick. */
    public static final int MAX_WORLD_SOUNDS_PER_BEAT = 1;
    /** Quietest and loudest world ambience, far and near. */
    public static final float WORLD_VOLUME_MIN = 0.012f;
    public static final float WORLD_VOLUME_MAX = 0.025f;
    /** Low hum pitch band: the world must never chirp at the player. */
    public static final float WORLD_PITCH_MIN = 0.55f;
    public static final float WORLD_PITCH_MAX = 0.72f;

    // ---------------------------------------------------------------- shepard
    /** Steps per octave of the riser. */
    public static final int SHEPARD_STEPS = 24;
    /** Voices below this weight are skipped, so a wrap never plays a silent note. */
    public static final float SHEPARD_MIN_WEIGHT = 0.04f;
    /** A completion within this window continues the combo (and the riser base). */
    public static final long COMBO_WINDOW_MS = 5000L;

    // ---------------------------------------------------------------- upgrades
    /** One full stack of motors or batteries: the fanfare target. */
    public static final int UPGRADE_STACK = 64;
    /** A second completion inside this many ticks is thinned to one quiet tick. */
    public static final int COMPLETION_MIN_TICKS = 6;
    /** At most this many scheduled notes may wait; a very fast machine cannot flood the queue. */
    public static final int MAX_SCHEDULED_NOTES = 12;

    private static int shepardStep;
    private static int shepardMaxStepReached;
    private static int reachRollCount;
    private static int jackpotFanfareCount;
    private static int midArpeggioCount;

    private static int pulseCount;
    private static int chimeCount;
    private static int comboArpeggioCount;
    private static int worldBeatCount;
    private static int clickCount;
    private static int openCloseCount;
    private static int hoverTickCount;
    private static int slotActionCount;
    private static int milestoneCount;
    private static int upgradeInsertCount;
    private static int upgradeFanfareCount;

    private SoundRhythm() {
    }

    /** Semitones the insert ladder climbs between the first and the 64th upgrade. */
    public static final int UPGRADE_SEMITONES = 18;

    /**
     * Pitch of the n-th installed motor or battery.
     *
     * <p>A quantised semitone ladder from 0.70 (low, well below the usual click) up to 1.98 at one
     * full stack, i.e. exactly 18 semitones over 63 inserts. Quantising keeps it musical, and the
     * exponential mapping means it never drops at an octave boundary the way a plain pentatonic
     * index over 64 items would.</p>
     */
    public static float upgradePitch(int installed) {
        int count = Math.max(1, Math.min(UPGRADE_STACK, installed));
        int semitone = Math.round((count - 1) * UPGRADE_SEMITONES / (float) (UPGRADE_STACK - 1));
        double pitch = 0.70D * Math.pow(2.0D, semitone / 12.0D);
        return (float) Math.min(1.98D, pitch);
    }

    /** True once the slot holds a full stack; the fanfare plays on this insert. */
    public static boolean upgradeComplete(int installed) {
        return installed >= UPGRADE_STACK;
    }

    /** Maps a normalized ratio [0, 1] onto the ascending pentatonic pitch ladder. */
    public static float pentatonicPitch(double ratio, int stepOffset) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int base = (int) Math.floor(clamped * (PENTATONIC.length - 1));
        int idx = Math.floorMod(base + stepOffset, PENTATONIC.length);
        return PENTATONIC[idx] * (0.96f + (float) clamped * 0.08f);
    }

    // ---------------------------------------------------------------- riser maths
    /** Phase of a step inside one octave, in [0, 1). */
    public static float shepardTheta(int step) {
        return Math.floorMod(step, SHEPARD_STEPS) / (float) SHEPARD_STEPS;
    }

    /** Lower voice pitch, always in [0.5, 1.0). */
    public static float shepardLowPitch(int step) {
        return (float) (0.5D * Math.pow(2.0D, shepardTheta(step)));
    }

    /** Upper voice pitch, always in [1.0, 2.0). */
    public static float shepardHighPitch(int step) {
        return (float) (1.0D * Math.pow(2.0D, shepardTheta(step)));
    }

    /** Bell weight of the lower voice. */
    public static float shepardLowWeight(int step) {
        double s = Math.sin(Math.PI * shepardTheta(step) / 2.0D);
        return (float) (s * s);
    }

    /** Bell weight of the upper voice; always {@code 1 - lowWeight}. */
    public static float shepardHighWeight(int step) {
        double c = Math.cos(Math.PI * shepardTheta(step) / 2.0D);
        return (float) (c * c);
    }

    /** Advances the riser one step and returns the new step. */
    public static int shepardAdvance() {
        shepardStep++;
        if (shepardStep > shepardMaxStepReached) {
            shepardMaxStepReached = shepardStep;
        }
        return shepardStep;
    }

    /** Raises the base of the riser so a combo keeps climbing instead of restarting. */
    public static int shepardBoost(int steps) {
        return shepardAdvance() + Math.max(0, steps);
    }

    /** Current step of the riser. */
    public static int shepardStep() {
        return shepardStep;
    }

    /** Returns to the bottom of the scale after the combo window closes. */
    public static void resetShepard() {
        shepardStep = 0;
    }

    /** Highest step ever reached: the proof that the riser passed 24 without a jump. */
    public static int getShepardMaxStepReached() {
        return shepardMaxStepReached;
    }

    /** True while the progress ratio is inside the reach zone. */
    public static boolean isReachZone(double ratio) {
        return ratio >= REACH_THRESHOLD;
    }

    /** Pulse period for a progress ratio: 4 ticks normally, 2 ticks inside the reach zone. */
    public static int pulsePeriod(double ratio) {
        return isReachZone(ratio) ? GUI_REACH_TICKS : GUI_PULSE_TICKS;
    }

    /** World ambience volume for a distance in blocks; 0 outside {@link #WORLD_RADIUS}. */
    public static float worldVolume(double distance) {
        if (distance >= WORLD_RADIUS) {
            return 0.0f;
        }
        double falloff = 1.0 - (distance / WORLD_RADIUS) * (distance / WORLD_RADIUS);
        return (float) (WORLD_VOLUME_MIN + (WORLD_VOLUME_MAX - WORLD_VOLUME_MIN) * falloff);
    }

    /** True when the upper voice is audible enough to be worth a channel. */
    public static boolean audible(float weight) {
        return weight >= SHEPARD_MIN_WEIGHT;
    }

    // ---------------------------------------------------------------- counters
    public static void recordClick() {
        clickCount++;
    }

    public static void recordPulse() {
        pulseCount++;
    }

    public static void recordReachPulse() {
        pulseCount++;
        reachRollCount++;
    }

    public static void recordComplete(int producedAmount, int comboStreak) {
        chimeCount++;
        if (producedAmount >= 16 || comboStreak >= 10) {
            comboArpeggioCount++;
            jackpotFanfareCount++;
        } else if (producedAmount >= 4 || comboStreak >= 5) {
            midArpeggioCount++;
        }
    }

    public static void recordWorldBeat() {
        worldBeatCount++;
    }

    public static void recordOpenClose() {
        openCloseCount++;
    }

    public static void recordHoverTick() {
        hoverTickCount++;
    }

    public static void recordSlotAction() {
        slotActionCount++;
    }

    public static void recordMilestone() {
        milestoneCount++;
    }

    public static void recordUpgradeInsert(boolean complete) {
        upgradeInsertCount++;
        if (complete) {
            upgradeFanfareCount++;
        }
    }

    public static void resetCounters() {
        shepardStep = 0;
        shepardMaxStepReached = 0;
        reachRollCount = 0;
        jackpotFanfareCount = 0;
        midArpeggioCount = 0;
        pulseCount = 0;
        chimeCount = 0;
        comboArpeggioCount = 0;
        worldBeatCount = 0;
        clickCount = 0;
        openCloseCount = 0;
        hoverTickCount = 0;
        slotActionCount = 0;
        milestoneCount = 0;
        upgradeInsertCount = 0;
        upgradeFanfareCount = 0;
    }

    public static int getPulseCount() {
        return pulseCount;
    }

    public static int getChimeCount() {
        return chimeCount;
    }

    public static int getComboArpeggioCount() {
        return comboArpeggioCount;
    }

    public static int getWorldBeatCount() {
        return worldBeatCount;
    }

    public static int getClickCount() {
        return clickCount;
    }

    public static int getOpenCloseCount() {
        return openCloseCount;
    }

    public static int getHoverTickCount() {
        return hoverTickCount;
    }

    public static int getSlotActionCount() {
        return slotActionCount;
    }

    public static int getMilestoneCount() {
        return milestoneCount;
    }

    public static int getUpgradeInsertCount() {
        return upgradeInsertCount;
    }

    public static int getUpgradeFanfareCount() {
        return upgradeFanfareCount;
    }

    public static int getReachRollCount() {
        return reachRollCount;
    }

    public static int getJackpotFanfareCount() {
        return jackpotFanfareCount;
    }

    public static int getMidArpeggioCount() {
        return midArpeggioCount;
    }
}
