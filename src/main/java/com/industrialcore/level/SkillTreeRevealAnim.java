package com.industrialcore.level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java 4-phase sequential unlock animation state machine for Skill Tree reveals (Phase 86+).
 *
 * <ol>
 *   <li>{@link Phase#INTRO_HOLD} ({@value #INTRO_FIRST_MS} ms first / {@value #INTRO_CHAIN_MS} ms
 *       chained): gives the player's eye time to lock onto the target node with a breathing preview
 *       glow and subtle padlock vibration.</li>
 *   <li>{@link Phase#WIRE_PULSE} (450–650 ms by tier): energy pulse(s) race along parent wires.</li>
 *   <li>{@link Phase#UNLOCK_BURST} (450–750 ms by tier): padlock shatters, tier-scaled shockwaves,
 *       star bursts, particles, optional subtle rays/shake/flash fire, and the node flips to
 *       {@code REVEALED}.</li>
 *   <li>{@link Phase#FOG_CLEAR} (350–460 ms by tier): newly uncovered child silhouettes dissolve
 *       from {@code MYSTERY} to {@code NEXT_TARGET}.</li>
 * </ol>
 */
public final class SkillTreeRevealAnim {
    public static final long INTRO_FIRST_MS = 700L;
    public static final long INTRO_CHAIN_MS = 350L;
    public static final long WIRE_PULSE_MS = 450L;
    public static final long UNLOCK_BURST_MS = 450L;
    public static final long FOG_CLEAR_MS = 350L;
    public static final long FOG_SKIP_MS = 60L;

    public enum Phase {
        IDLE,
        INTRO_HOLD,
        WIRE_PULSE,
        UNLOCK_BURST,
        FOG_CLEAR
    }

    /** Tier-scaled animation intensities for Tier 2..5 unlocks (pure Java, testable). */
    public record TierStyle(
            int tier,
            long wirePulseMs,
            long unlockBurstMs,
            long fogClearMs,
            int pulseCount,
            int shockwaveRings,
            int starCount,
            int sparkParticles,
            boolean radialRays,
            double maxFlashAlpha,
            double maxShakePx
    ) {}

    private static final TierStyle TIER_2 = new TierStyle(
            2, 450L, 450L, 350L, 1, 2, 8, 0, false, 0.0D, 0.0D);
    private static final TierStyle TIER_3 = new TierStyle(
            3, 500L, 520L, 380L, 2, 3, 12, 8, false, 0.0D, 0.0D);
    private static final TierStyle TIER_4 = new TierStyle(
            4, 560L, 600L, 420L, 2, 3, 16, 14, true, 0.0D, 1.5D);
    private static final TierStyle TIER_5 = new TierStyle(
            5, 650L, 750L, 460L, 3, 4, 24, 24, true, 0.20D, 2.5D);

    /** Edge events emitted by {@link #tick(long)} so the screen can fire SE &amp; commit once. */
    public record StepEvent(
            boolean enteredWirePulse,
            boolean enteredUnlockBurst,
            boolean enteredFogClear,
            boolean finishedAll,
            @Nullable String targetMachineId,
            int targetTier
    ) {
        public static final StepEvent NONE = new StepEvent(false, false, false, false, null, 2);
    }

    private final List<String> allPendingIds;
    private final Set<String> simulatedRevealed;
    private int currentIndex = -1;
    private Phase phase = Phase.IDLE;
    private long phaseStartMs;
    private List<String> uncoveringChildren = List.of();
    private boolean completed;

    public SkillTreeRevealAnim(Set<String> initialRevealed, List<String> pendingInOrder, long nowMs) {
        this.simulatedRevealed = new LinkedHashSet<>(initialRevealed);
        this.allPendingIds = List.copyOf(pendingInOrder);
        if (!this.allPendingIds.isEmpty()) {
            this.currentIndex = 0;
            this.phase = Phase.INTRO_HOLD;
            this.phaseStartMs = nowMs;
            this.completed = false;
        } else {
            this.completed = true;
        }
    }

    public static TierStyle styleForTier(int tier) {
        if (tier >= 5) {
            return TIER_5;
        }
        if (tier == 4) {
            return TIER_4;
        }
        if (tier == 3) {
            return TIER_3;
        }
        return TIER_2;
    }

    public static int tierOf(@Nullable String machineId) {
        MachineSkillTree.Node node = MachineSkillTree.byId(machineId);
        return node == null ? 2 : Math.max(2, Math.min(5, node.tier()));
    }

    public boolean isActive() {
        return !completed && phase != Phase.IDLE;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Phase phase() {
        return phase;
    }

    public int currentIndex() {
        return currentIndex;
    }

    @Nullable
    public String currentTargetId() {
        if (!isActive() || currentIndex < 0 || currentIndex >= allPendingIds.size()) {
            return null;
        }
        return allPendingIds.get(currentIndex);
    }

    public int currentTier() {
        return tierOf(currentTargetId());
    }

    public TierStyle currentStyle() {
        return styleForTier(currentTier());
    }

    /** All pending node ids queued for this playback (sent to server once finished or skipped). */
    public List<String> allPendingIds() {
        return allPendingIds;
    }

    /** Effective set of REVEALED nodes at the current animation instant. */
    public Set<String> effectiveRevealed() {
        return Collections.unmodifiableSet(simulatedRevealed);
    }

    /** Children currently dissolving from MYSTERY silhouette -> NEXT_TARGET during FOG_CLEAR. */
    public List<String> uncoveringChildren() {
        return uncoveringChildren;
    }

    /** Duration in milliseconds of the currently active phase. */
    public long currentPhaseDuration() {
        TierStyle style = currentStyle();
        return switch (phase) {
            case INTRO_HOLD -> (currentIndex == 0) ? INTRO_FIRST_MS : INTRO_CHAIN_MS;
            case WIRE_PULSE -> style.wirePulseMs();
            case UNLOCK_BURST -> style.unlockBurstMs();
            case FOG_CLEAR -> uncoveringChildren.isEmpty() ? FOG_SKIP_MS : style.fogClearMs();
            case IDLE -> 0L;
        };
    }

    /** Normalized progress {@code [0, 1]} within the current phase. */
    public double phaseProgress(long nowMs) {
        long dur = currentPhaseDuration();
        if (dur <= 0L) {
            return 1.0D;
        }
        long elapsed = Math.max(0L, nowMs - phaseStartMs);
        return Math.min(1.0D, (double) elapsed / dur);
    }

    /** Advances the state machine to {@code nowMs} and reports any transition events. */
    public StepEvent tick(long nowMs) {
        if (!isActive()) {
            return StepEvent.NONE;
        }
        boolean enteredWire = false;
        boolean enteredBurst = false;
        boolean enteredFog = false;
        boolean finished = false;
        String eventTarget = currentTargetId();
        int eventTier = currentTier();

        int guard = 0;
        while (isActive() && guard++ < 64) {
            long dur = currentPhaseDuration();
            if (nowMs - phaseStartMs < dur) {
                break;
            }
            phaseStartMs += dur;
            String target = currentTargetId();
            if (target == null) {
                finishInternal();
                finished = true;
                break;
            }
            int tier = tierOf(target);
            switch (phase) {
                case INTRO_HOLD -> {
                    phase = Phase.WIRE_PULSE;
                    enteredWire = true;
                    eventTarget = target;
                    eventTier = tier;
                }
                case WIRE_PULSE -> {
                    Set<String> before = new LinkedHashSet<>(simulatedRevealed);
                    simulatedRevealed.add(target);
                    uncoveringChildren = SkillTreeLayout.newlyUncoveredChildren(target, before, simulatedRevealed);
                    phase = Phase.UNLOCK_BURST;
                    enteredBurst = true;
                    eventTarget = target;
                    eventTier = tier;
                }
                case UNLOCK_BURST -> {
                    phase = Phase.FOG_CLEAR;
                    if (!uncoveringChildren.isEmpty()) {
                        enteredFog = true;
                        eventTarget = target;
                        eventTier = tier;
                    }
                }
                case FOG_CLEAR -> {
                    uncoveringChildren = List.of();
                    currentIndex++;
                    if (currentIndex >= allPendingIds.size()) {
                        finishInternal();
                        finished = true;
                    } else {
                        phase = Phase.INTRO_HOLD;
                    }
                }
                case IDLE -> finishInternal();
            }
        }
        return new StepEvent(enteredWire, enteredBurst, enteredFog, finished, eventTarget, eventTier);
    }

    /** Immediately skips all remaining phases & queued nodes so the player is never locked out. */
    public List<String> skipAll() {
        if (completed) {
            return List.of();
        }
        simulatedRevealed.addAll(allPendingIds);
        finishInternal();
        return new ArrayList<>(allPendingIds);
    }

    private void finishInternal() {
        uncoveringChildren = List.of();
        phase = Phase.IDLE;
        completed = true;
    }
}
