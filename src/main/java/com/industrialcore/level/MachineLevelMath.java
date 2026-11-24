package com.industrialcore.level;

import com.industrialcore.machine.UpgradeMath;

import java.util.Arrays;

/**
 * Pure math for the per-player machine level system (Phase 84).
 *
 * <p>Kept completely free of Minecraft classes so every boundary value, curve monotonicity rule,
 * {@code long}-overflow guard, upgrade cap, and mastery multiplier is unit testable directly.</p>
 *
 * <ul>
 *   <li>Normal cap: {@link #NORMAL_MAX_LEVEL} (Lv.99). While {@code limitBroken == false}, XP is
 *       capped at {@code xpForLevel(99)} and level never exceeds 99.</li>
 *   <li>Limit break cap: {@link #LIMIT_BROKEN_MAX_LEVEL} (Lv.1000), unlocked automatically once
 *       every node in {@link MachineSkillTree} has its unlock condition satisfied.</li>
 *   <li>Motor / battery upgrade slot cap scales smoothly from {@code 1} at Lv.1 to {@code 64} at
 *       Lv.99. Unowned machines ({@code ownerUuid == null}) and Creative players keep full cap
 *       {@code 64} for backwards compatibility.</li>
 *   <li>Mastery bonus applies only from Lv.100 onward ({@code 1.0} throughout Lv.1..99 so normal
 *       balance is untouched), scaling up to 2.5x speed and 30% FE reduction at Lv.1000.</li>
 * </ul>
 */
public final class MachineLevelMath {
    public static final int MIN_LEVEL = 1;
    public static final int NORMAL_MAX_LEVEL = 99;
    public static final int LIMIT_BROKEN_MAX_LEVEL = 1000;

    public static final int MIN_UPGRADE_CAP = 1;
    public static final int MAX_UPGRADE_CAP = UpgradeMath.MAX_UPGRADE_STACK;

    /** Phase 113: XP-linked total virtual upgrade cap (Motor + Battery + Specialization). */
    public static final int MIN_TOTAL_UPGRADE_CAP = 4;
    public static final int NORMAL_MAX_TOTAL_UPGRADE_CAP = 96;
    public static final int LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP = 1000;

    /** Number of active FE-consuming server ticks buffered locally before flushing one XP batch. */
    public static final int BATCH_WORK_TICKS = 20;
    /** Base XP granted per 20 active working ticks (1 active machine-second). */
    public static final long XP_PER_WORK_SECOND = 10L;

    /** Maximum speed multiplier at Lv.1000 (1.0 + 1.5 = 2.50x). */
    private static final double MAX_MASTERY_SPEED_BONUS = 1.50D;
    /** Maximum FE discount at Lv.1000 (30% off -> 0.70x energy cost). */
    private static final double MAX_MASTERY_ENERGY_SAVING = 0.30D;

    /**
     * Precomputed cumulative XP table: {@code XP_TABLE[L]} is total XP required to reach level L.
     * Index 0 is unused (0); index 1 is 0; index 1000 is ~1.90e9 (far below {@link Long#MAX_VALUE}).
     */
    private static final long[] XP_TABLE = buildXpTable();

    private MachineLevelMath() {}

    private static long[] buildXpTable() {
        long[] table = new long[LIMIT_BROKEN_MAX_LEVEL + 1];
        table[0] = 0L;
        table[1] = 0L;
        long delta98 = 0L;
        for (int level = 1; level < LIMIT_BROKEN_MAX_LEVEL; level++) {
            long delta;
            if (level < NORMAL_MAX_LEVEL) {
                delta = Math.round(25.0D * Math.pow(level, 1.45D)) + 20L;
                if (level == NORMAL_MAX_LEVEL - 1) {
                    delta98 = delta;
                }
            } else {
                double k = level - (NORMAL_MAX_LEVEL - 1);
                delta = delta98 + Math.round(140.0D * Math.pow(k, 1.55D));
            }
            long next = table[level] + delta;
            if (next < table[level]) {
                throw new IllegalStateException("XP table overflow at level " + (level + 1));
            }
            table[level + 1] = next;
        }
        return table;
    }

    /**
     * Phase 99: Limit-Break 1-tick hyper-parallel batch multiplier.
     *
     * <p>Strictly {@code 1} for all non-limit-broken machines and Lv.1..99 so existing progression
     * stays 100% untouched. Once limit-broken (Lv.100..1000), scales monotonically through exact
     * milestones: Lv.100 -> 2, Lv.250 -> 8, Lv.500 -> 16, Lv.750 -> 32, Lv.1000 -> 64.</p>
     */
    public static int parallelBatchSize(int level, boolean limitBroken) {
        if (!limitBroken || level <= NORMAL_MAX_LEVEL) {
            return 1;
        }
        if (level >= LIMIT_BROKEN_MAX_LEVEL) {
            return 64;
        }
        if (level >= 750) {
            return 32 + (level - 750) * 32 / 250;
        }
        if (level >= 500) {
            return 16 + (level - 500) * 16 / 250;
        }
        if (level >= 250) {
            return 8 + (level - 250) * 8 / 250;
        }
        return 2 + (level - 100) * 6 / 150;
    }

    /**
     * Phase 99: Compresses processing cycle duration for Limit-Broken machines (Lv.100..1000)
     * down to {@code 1} tick at Lv.1000 + 64 motors, enabling true 1-tick 64-parallel execution.
     * Strictly returns {@code masteryTime} unchanged for all Lv.1..99 or non-limit-broken machines.
     */
    public static int limitBrokenCycleTime(int masteryTime, int level, boolean limitBroken, int motors) {
        int safe = Math.max(1, masteryTime);
        if (!limitBroken || level <= NORMAL_MAX_LEVEL || safe <= 1) {
            return safe;
        }
        double lvFactor = Math.min(1.0D,
                (level - NORMAL_MAX_LEVEL) / (double) (LIMIT_BROKEN_MAX_LEVEL - NORMAL_MAX_LEVEL));
        double motorFactor = Math.min(1.0D, Math.max(0, motors) / (double) MAX_UPGRADE_CAP);
        double compression = lvFactor * motorFactor;
        int compressed = (int) Math.round(safe * (1.0D - compression) + compression);
        return Math.max(1, Math.min(safe, compressed));
    }

    /** Highest reachable level for the given limit-break flag (99 or 1000). */
    public static int maxLevel(boolean limitBroken) {
        return limitBroken ? LIMIT_BROKEN_MAX_LEVEL : NORMAL_MAX_LEVEL;
    }

    /** Cumulative XP required to reach {@code level} (clamped to {@code 1..1000}). */
    public static long xpForLevel(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(LIMIT_BROKEN_MAX_LEVEL, level));
        return XP_TABLE[clamped];
    }

    /** Maximum stored XP allowed for the given limit-break state. */
    public static long maxXp(boolean limitBroken) {
        return XP_TABLE[maxLevel(limitBroken)];
    }

    /** Clamps raw XP into {@code [0, maxXp(limitBroken)]}. */
    public static long clampXp(long xp, boolean limitBroken) {
        if (xp <= 0L) {
            return 0L;
        }
        long cap = maxXp(limitBroken);
        return Math.min(xp, cap);
    }

    /**
     * Overflow-safe addition of {@code delta} XP, capped at {@code xpForLevel(99)} while
     * {@code limitBroken == false} and at {@code xpForLevel(1000)} when {@code true}.
     */
    public static long addXp(long currentXp, long delta, boolean limitBroken) {
        long safeCurrent = clampXp(currentXp, limitBroken);
        if (delta <= 0L) {
            return safeCurrent;
        }
        long cap = maxXp(limitBroken);
        long room = cap - safeCurrent;
        if (delta >= room) {
            return cap;
        }
        return safeCurrent + delta;
    }

    /** Resolves machine level from cumulative XP and limit-break state in O(log N). */
    public static int levelForXp(long xp, boolean limitBroken) {
        int hi = maxLevel(limitBroken);
        long clamped = clampXp(xp, limitBroken);
        int idx = Arrays.binarySearch(XP_TABLE, MIN_LEVEL, hi + 1, clamped);
        if (idx >= MIN_LEVEL) {
            return Math.min(hi, idx);
        }
        int insertion = -idx - 1;
        return Math.max(MIN_LEVEL, Math.min(hi, insertion - 1));
    }

    /** XP accumulated past the start of the current level. */
    public static long xpIntoLevel(long xp, boolean limitBroken) {
        long clamped = clampXp(xp, limitBroken);
        int level = levelForXp(clamped, limitBroken);
        return clamped - XP_TABLE[level];
    }

    /** XP span required to advance from {@code level} to {@code level + 1}, or 0 at max level. */
    public static long xpSpanForNextLevel(int level, boolean limitBroken) {
        int cap = maxLevel(limitBroken);
        if (level < MIN_LEVEL || level >= cap) {
            return 0L;
        }
        return XP_TABLE[level + 1] - XP_TABLE[level];
    }

    /**
     * Phase 113: Total virtual upgrade capacity (Motor + Battery + Specialization combined)
     * unlocked by machine level:
     * <ul>
     *   <li>Lv.1 -&gt; {@code 4}</li>
     *   <li>Lv.1..99 -&gt; {@code 4 + floor(92 * (lv - 1) / 98.0)} (Lv.50 -&gt; 50, Lv.99 -&gt; 96)</li>
     *   <li>Lv.100..1000 (Limit Break) -&gt; {@code 96 + floor(904 * (lv - 99) / 901.0)} (Lv.1000 -&gt; 1000)</li>
     * </ul>
     */
    public static int totalUpgradeLimitForLevel(int level) {
        if (level <= MIN_LEVEL) {
            return MIN_TOTAL_UPGRADE_CAP;
        }
        if (level <= NORMAL_MAX_LEVEL) {
            return MIN_TOTAL_UPGRADE_CAP + (int) Math.floor(
                    (double) (NORMAL_MAX_TOTAL_UPGRADE_CAP - MIN_TOTAL_UPGRADE_CAP)
                            * (level - MIN_LEVEL)
                            / (double) (NORMAL_MAX_LEVEL - MIN_LEVEL));
        }
        int clamped = Math.min(LIMIT_BROKEN_MAX_LEVEL, level);
        return NORMAL_MAX_TOTAL_UPGRADE_CAP + (int) Math.floor(
                (double) (LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP - NORMAL_MAX_TOTAL_UPGRADE_CAP)
                        * (clamped - NORMAL_MAX_LEVEL)
                        / (double) (LIMIT_BROKEN_MAX_LEVEL - NORMAL_MAX_LEVEL));
    }

    /**
     * Effective total upgrade capacity for a machine's virtual upgrade bank.
     * Unowned machines ({@code hasOwner == false}) and Creative operators receive the full
     * {@link #LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP} (1000).
     */
    public static int effectiveTotalUpgradeLimit(boolean hasOwner, int ownerLevel, boolean creative) {
        if (!hasOwner || creative) {
            return LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP;
        }
        return totalUpgradeLimitForLevel(ownerLevel);
    }

    /**
     * Motor / battery stack cap unlocked at {@code level} for legacy 64-max vanilla slots.
     */
    public static int upgradeLimitForLevel(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(NORMAL_MAX_LEVEL, level));
        return MIN_UPGRADE_CAP + (int) Math.floor(
                (double) (MAX_UPGRADE_CAP - MIN_UPGRADE_CAP)
                        * (clamped - MIN_LEVEL)
                        / (double) (NORMAL_MAX_LEVEL - MIN_LEVEL));
    }

    /**
     * Effective upgrade cap for a machine slot.
     * Unowned machines ({@code hasOwner == false}, e.g. GameTests / commands) and Creative mode
     * operators retain the full 64-item cap for backwards compatibility.
     */
    public static int effectiveUpgradeLimit(boolean hasOwner, int ownerLevel, boolean creative) {
        if (!hasOwner || creative) {
            return MAX_UPGRADE_CAP;
        }
        return upgradeLimitForLevel(ownerLevel);
    }

    /**
     * Post-limit-break work speed multiplier: strictly {@code 1.0} for Lv.1..99 so standard game
     * balance is untouched, rising smoothly to {@code 2.50x} at Lv.1000.
     */
    public static double masterySpeedMultiplier(int level) {
        if (level <= NORMAL_MAX_LEVEL) {
            return 1.0D;
        }
        double t = limitBreakFraction(level);
        return 1.0D + MAX_MASTERY_SPEED_BONUS * Math.pow(t, 0.85D);
    }

    /**
     * Post-limit-break FE cost multiplier: strictly {@code 1.0} for Lv.1..99, falling smoothly to
     * {@code 0.70x} (30% saving) at Lv.1000.
     */
    public static double masteryEnergyMultiplier(int level) {
        if (level <= NORMAL_MAX_LEVEL) {
            return 1.0D;
        }
        return 1.0D - MAX_MASTERY_ENERGY_SAVING * limitBreakFraction(level);
    }

    /** Percentage FE reduction (0..30) for tooltip display. */
    public static int masteryEnergySavingPercent(int level) {
        return (int) Math.round((1.0D - masteryEnergyMultiplier(level)) * 100.0D);
    }

    private static double limitBreakFraction(int level) {
        int clamped = Math.max(NORMAL_MAX_LEVEL, Math.min(LIMIT_BROKEN_MAX_LEVEL, level));
        return (double) (clamped - NORMAL_MAX_LEVEL)
                / (double) (LIMIT_BROKEN_MAX_LEVEL - NORMAL_MAX_LEVEL);
    }
}
