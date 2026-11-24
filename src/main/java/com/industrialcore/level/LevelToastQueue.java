package com.industrialcore.level;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java coalescing & throttling queue for machine level-up, new skill-tree unlock, and
 * limit-break HUD toasts (Phase 85–86).
 */
public final class LevelToastQueue {
    public static final long TOAST_DURATION_MS = 3400L;
    public static final long NEW_UNLOCK_DURATION_MS = 4400L;
    public static final long LIMIT_BREAK_DURATION_MS = 5200L;
    public static final long SOUND_COOLDOWN_MS = 350L;
    public static final long COALESCE_WINDOW_MS = 900L;
    public static final int MAX_VISIBLE = 2;

    public enum Kind {
        LEVEL_UP,
        NEW_UNLOCK,
        LIMIT_BREAK
    }

    public record Entry(
            Kind kind,
            String primaryMachineId,
            int newLevel,
            int maxLevel,
            int extraMachines,
            long createdAtMs,
            long durationMs
    ) {
        public boolean limitBreak() {
            return kind == Kind.LIMIT_BREAK;
        }

        public boolean newUnlock() {
            return kind == Kind.NEW_UNLOCK;
        }

        public double ageRatio(long nowMs) {
            long elapsed = Math.max(0L, nowMs - createdAtMs);
            return Math.min(1.0D, (double) elapsed / Math.max(1L, durationMs));
        }

        public boolean isExpired(long nowMs) {
            return nowMs - createdAtMs >= durationMs;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private long lastLevelUpSoundMs = Long.MIN_VALUE / 4;

    public synchronized void clear() {
        entries.clear();
        lastLevelUpSoundMs = Long.MIN_VALUE / 4;
    }

    /**
     * Pushes a limit-break celebration banner (highest priority, never coalesced away).
     */
    public synchronized void pushLimitBreak(long nowMs) {
        prune(nowMs);
        entries.removeIf(Entry::limitBreak);
        entries.add(0, new Entry(Kind.LIMIT_BREAK, "", MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL,
                MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL, 0, nowMs, LIMIT_BREAK_DURATION_MS));
        trimToMax();
    }

    /**
     * Phase 86: pushes or merges a "New technology unlocked! Press [K] to inspect" banner.
     *
     * @return {@code true} if an unlock chime should play
     */
    public synchronized boolean pushNewUnlock(int newlyUnlockedCount, long nowMs) {
        prune(nowMs);
        int count = Math.max(1, newlyUnlockedCount);
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (existing.kind() == Kind.NEW_UNLOCK) {
                entries.set(i, new Entry(Kind.NEW_UNLOCK, "", existing.newLevel() + count,
                        0, 0, nowMs, NEW_UNLOCK_DURATION_MS));
                return consumeSoundCooldown(nowMs);
            }
        }
        int insertAt = (!entries.isEmpty() && entries.get(0).limitBreak()) ? 1 : 0;
        entries.add(insertAt, new Entry(Kind.NEW_UNLOCK, "", count, 0, 0, nowMs, NEW_UNLOCK_DURATION_MS));
        trimToMax();
        return consumeSoundCooldown(nowMs);
    }

    /**
     * Pushes one or more machine level-ups detected in a sync packet.
     *
     * @return {@code true} if the caller should play the level-up SE (respecting cooldown)
     */
    public synchronized boolean pushLevelUp(String machineId, int newLevel, int maxLevel,
                                            int extraMachinesInSameBatch, long nowMs) {
        prune(nowMs);
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (existing.kind() == Kind.LEVEL_UP && (nowMs - existing.createdAtMs()) <= COALESCE_WINDOW_MS) {
                boolean sameMachine = existing.primaryMachineId().equals(machineId);
                int addedExtras = sameMachine ? extraMachinesInSameBatch : (1 + extraMachinesInSameBatch);
                Entry merged = new Entry(
                        Kind.LEVEL_UP,
                        machineId,
                        newLevel,
                        maxLevel,
                        existing.extraMachines() + addedExtras,
                        nowMs,
                        TOAST_DURATION_MS
                );
                entries.set(i, merged);
                return consumeSoundCooldown(nowMs);
            }
        }
        entries.add(new Entry(Kind.LEVEL_UP, machineId, newLevel, maxLevel,
                Math.max(0, extraMachinesInSameBatch), nowMs, TOAST_DURATION_MS));
        trimToMax();
        return consumeSoundCooldown(nowMs);
    }

    public synchronized List<Entry> activeEntries(long nowMs) {
        prune(nowMs);
        return List.copyOf(entries);
    }

    private void prune(long nowMs) {
        entries.removeIf(e -> e.isExpired(nowMs));
    }

    private void trimToMax() {
        while (entries.size() > MAX_VISIBLE) {
            int dropIdx = -1;
            // Drop oldest LEVEL_UP first, preserving LIMIT_BREAK and NEW_UNLOCK banners.
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i).kind() == Kind.LEVEL_UP) {
                    dropIdx = i;
                    break;
                }
            }
            if (dropIdx < 0) {
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (!entries.get(i).limitBreak()) {
                        dropIdx = i;
                        break;
                    }
                }
            }
            entries.remove(dropIdx >= 0 ? dropIdx : entries.size() - 1);
        }
    }

    private boolean consumeSoundCooldown(long nowMs) {
        if (nowMs - lastLevelUpSoundMs >= SOUND_COOLDOWN_MS) {
            lastLevelUpSoundMs = nowMs;
            return true;
        }
        return false;
    }
}
