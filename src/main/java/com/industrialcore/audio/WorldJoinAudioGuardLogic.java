package com.industrialcore.audio;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 116: Pure-Java 3-layer client audio burst guard preventing world-join / dimension-change
 * sound explosions from simultaneous equipment sync, chunk loads, and ambient machine startup.
 *
 * <ol>
 *   <li><b>60-tick (3.0s) World-Join Grace Period</b>: blocks all automatic AegisWorks ambient,
 *       machine, reactor, and equipment-sync sounds during the first 60 client ticks after joining
 *       or changing dimension.</li>
 *   <li><b>Silent Initial Equipment Sync</b>: suppresses Virtual Aegis full-set boot chime and
 *       Quantum Blade equip ignition when already equipped on login; plays only on genuine player
 *       equip transitions after initial sync and grace.</li>
 *   <li><b>Global Mod Sound Limiter</b>: caps total AegisWorks sounds to at most {@value #MAX_SOUNDS_PER_TICK}
 *       per tick and enforces at least {@value #SAME_SOUND_MIN_INTERVAL_TICKS} ticks between identical
 *       sound IDs.</li>
 * </ol>
 */
public final class WorldJoinAudioGuardLogic {
    public static final int JOIN_GRACE_TICKS = 60;
    public static final int MAX_SOUNDS_PER_TICK = 2;
    public static final int SAME_SOUND_MIN_INTERVAL_TICKS = 6;

    private long clientTick = 0L;
    private int graceRemainingTicks = 0;
    private int joinMutedCount = 0;
    private int joinPlayedCount = 0;

    private long budgetTick = -1L;
    private int playedThisTick = 0;
    private final Map<String, Long> lastPlayedTickById = new HashMap<>(64);

    private boolean suitSynced = false;
    private boolean wasFullSuit = false;
    private boolean bladeSynced = false;
    private boolean wasHoldingBlade = false;

    /** Called on login, world load, logout, or dimension change. */
    public void onWorldJoinOrDimensionChange() {
        graceRemainingTicks = JOIN_GRACE_TICKS;
        joinMutedCount = 0;
        joinPlayedCount = 0;
        budgetTick = -1L;
        playedThisTick = 0;
        lastPlayedTickById.clear();
        suitSynced = false;
        wasFullSuit = false;
        bladeSynced = false;
        wasHoldingBlade = false;
    }

    /** Advances one client tick. */
    public void tick() {
        clientTick++;
        if (graceRemainingTicks > 0) {
            graceRemainingTicks--;
        }
        if (lastPlayedTickById.size() > 128) {
            lastPlayedTickById.clear();
        }
    }

    public boolean isInJoinGrace() {
        return graceRemainingTicks > 0;
    }

    public int graceRemainingTicks() {
        return graceRemainingTicks;
    }

    public int joinMutedCount() {
        return joinMutedCount;
    }

    public int joinPlayedCount() {
        return joinPlayedCount;
    }

    public long currentTick() {
        return clientTick;
    }

    /**
     * Evaluates whether a sound ID is permitted to play right now.
     *
     * @param soundId               canonical sound location (e.g. {@code industrialcore:machine_crusher_jaw})
     * @param isModSound            true if the sound belongs to {@code industrialcore}
     * @param isVanillaEquipOrSync  true if the sound is a vanilla armor equip sound during world join
     * @return {@code true} if allowed, {@code false} if muted/throttled
     */
    /**
     * Phase 119: High-priority raid milestone sounds (boss defeat collapse, raid victory fanfare,
     * boss overdrive roar, portal horn) bypass the 2-sounds/tick combat-chatter rate cap after the
     * 60-tick login grace window so heavy combat SFX can never silence milestone climaxes.
     */
    public static boolean isHighPriorityMilestone(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            return false;
        }
        return soundId.endsWith("rift_raid_victory_fanfare")
                || soundId.endsWith("sovereign_defeat_collapse")
                || soundId.endsWith("sovereign_roar_overdrive")
                || soundId.endsWith("rift_portal_horn");
    }

    public boolean tryAllowSound(String soundId, boolean isModSound, boolean isVanillaEquipOrSync) {
        if (soundId == null || soundId.isEmpty()) {
            return true;
        }
        if (isInJoinGrace()) {
            if (isModSound || isVanillaEquipOrSync) {
                joinMutedCount++;
                return false;
            }
            return true;
        }
        if (!isModSound) {
            return true;
        }
        if (budgetTick != clientTick) {
            budgetTick = clientTick;
            playedThisTick = 0;
        }
        boolean highPriority = isHighPriorityMilestone(soundId);
        if (!highPriority && playedThisTick >= MAX_SOUNDS_PER_TICK) {
            return false;
        }
        Long lastTick = lastPlayedTickById.get(soundId);
        if (lastTick != null && (clientTick - lastTick) < SAME_SOUND_MIN_INTERVAL_TICKS) {
            return false;
        }
        if (!highPriority) {
            playedThisTick++;
        }
        lastPlayedTickById.put(soundId, clientTick);
        return true;
    }

    /**
     * Records a block during the join grace window when an automatic controller skips playback
     * before even reaching the SoundManager (keeps {@link #joinMutedCount()} accurate).
     */
    public void recordGraceSuppression() {
        if (isInJoinGrace()) {
            joinMutedCount++;
        }
    }

    /**
     * Layer 2: Suppresses Virtual Aegis suit boot chime on initial login/dimension inventory sync.
     * Returns {@code true} ONLY when the player manually equips the full set AFTER initial sync
     * and outside the join grace window.
     */
    public boolean shouldPlaySuitBootSound(boolean currentlyFullSet) {
        if (!suitSynced) {
            suitSynced = true;
            wasFullSuit = currentlyFullSet;
            if (currentlyFullSet && isInJoinGrace()) {
                joinMutedCount++;
            }
            return false;
        }
        if (currentlyFullSet && !wasFullSuit) {
            wasFullSuit = true;
            if (isInJoinGrace()) {
                joinMutedCount++;
                return false;
            }
            return true;
        }
        if (!currentlyFullSet) {
            wasFullSuit = false;
        }
        return false;
    }

    /**
     * Layer 2: Suppresses Quantum Blade equip ignition SE when logging in already holding the blade.
     */
    public boolean shouldPlayBladeIgniteSound(boolean currentlyHoldingBlade) {
        if (!bladeSynced) {
            bladeSynced = true;
            wasHoldingBlade = currentlyHoldingBlade;
            if (currentlyHoldingBlade && isInJoinGrace()) {
                joinMutedCount++;
            }
            return false;
        }
        if (currentlyHoldingBlade && !wasHoldingBlade) {
            wasHoldingBlade = true;
            if (isInJoinGrace()) {
                joinMutedCount++;
                return false;
            }
            return true;
        }
        if (!currentlyHoldingBlade) {
            wasHoldingBlade = false;
        }
        return false;
    }

    /** Records that a Mod sound actually passed into the SoundManager during join grace (for diagnostics). */
    public void recordPlayedDuringGraceIfActive(boolean isModSound) {
        if (isModSound && isInJoinGrace()) {
            joinPlayedCount++;
        }
    }
}
