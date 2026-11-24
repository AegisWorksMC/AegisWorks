package com.industrialcore.machine;

/**
 * Speed-upgrade curve of the auto crafter.
 *
 * <p>Phase 81: the motor slot accepts a full stack (64 motors) instead of stopping at four. The
 * curve is a smooth rational one rather than a compounding percentage, so every motor still
 * shortens the craft while the numbers stay sane at the top of the range:</p>
 *
 * <pre>
 *   craftTime(level)     = ceil(baseTicks / (1 + 0.35 * level))
 *   energyPerTick(level) = ceil(baseEnergy * (1 + 0.50 * level))
 * </pre>
 *
 * <p>At level 0 a 60 tick craft costs 60 x 2 = 120 FE; at level 4 it takes 25 ticks and draws
 * 6 FE/t (150 FE total); at level 64 (one full stack) it takes 3 ticks and draws 66 FE/t
 * (198 FE total). The machine always gets faster, the total FE per craft only rises slightly, and
 * the per-tick draw is what the energy buffer has to cover.</p>
 *
 * <p>No Minecraft classes are used so the curve can be unit tested exactly.</p>
 */
public final class CrafterSpeed {
    /** One full stack of motors: the highest level the machine can install. */
    public static final int MAX_LEVEL = UpgradeMath.MAX_UPGRADE_STACK;

    private static final double TIME_PER_LEVEL = 0.35D;
    private static final double ENERGY_PER_LEVEL = 0.50D;

    private CrafterSpeed() {
    }

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** Craft duration in ticks for the given upgrade level. */
    public static int craftTime(int baseTicks, int level) {
        int base = Math.max(1, baseTicks);
        double scaled = base / (1.0D + clampLevel(level) * TIME_PER_LEVEL);
        return Math.max(1, (int) Math.ceil(scaled - 1.0E-9D));
    }

    /** FE consumed per crafting tick for the given upgrade level. */
    public static int energyPerTick(int baseEnergy, int level) {
        int base = Math.max(0, baseEnergy);
        if (base == 0) {
            return 0;
        }
        double scaled = base * (1.0D + clampLevel(level) * ENERGY_PER_LEVEL);
        return Math.max(1, (int) Math.ceil(scaled - 1.0E-9D));
    }

    /** Total FE a craft costs at this level. */
    public static long totalEnergy(int baseTicks, int baseEnergy, int level) {
        return (long) craftTime(baseTicks, level) * energyPerTick(baseEnergy, level);
    }
}
