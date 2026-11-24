package com.industrialcore.endgame;

import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.machine.UpgradeMath;

import java.util.Map;

/**
 * Phase 99: Pure-Java math for the Singularity Reactor Generator ({@code singularity_reactor}).
 *
 * <p>Stores internal energy in {@code long} (base 1,000,000,000 FE = 1G FE, expandable up to
 * 17G FE with 64 batteries) with overflow-free saturating arithmetic and safe {@code int} clamping
 * at the NeoForge {@code IEnergyStorage} boundary.</p>
 */
public final class SingularityReactorLogic {
    /** Base internal long capacity: 1,000,000,000 FE (1G FE). */
    public static final long BASE_CAPACITY = 1_000_000_000L;
    /** Extra internal long capacity per Battery Cell: +250,000,000 FE (up to 17G FE at 64). */
    public static final long CAPACITY_PER_BATTERY = 250_000_000L;
    /** Hard overflow-safe capacity ceiling. */
    public static final long MAX_CAPACITY = 100_000_000_000L;

    /** Base generation output with 0 motors: 100,000 FE/t. */
    public static final long BASE_FE_PER_TICK = 100_000L;
    /** Peak generation output with 64 motors at Lv.1..99: 10,000,000 FE/t. */
    public static final long MAX_MOTOR_FE_PER_TICK = 10_000_000L;

    /** Display states synced to GUI. */
    public static final int STATE_NO_FUEL = 0;
    public static final int STATE_GENERATING = 1;
    public static final int STATE_FULL = 2;
    /** Phase 103: Multiblock structure not yet formed. */
    public static final int STATE_INCOMPLETE = 3;
    /** Phase 114: Emergency non-destructive cooldown after Overdrive catalyst depletion. */
    public static final int STATE_COOLDOWN = 4;

    /** High-value catalyst/fuel burn durations in server ticks (20 ticks = 1s). */
    private static final Map<String, Integer> FUEL_TICKS = Map.ofEntries(
            Map.entry("minecraft:nether_star", 24_000),
            Map.entry("minecraft:netherite_ingot", 12_000),
            Map.entry("minecraft:diamond_block", 3_200),
            Map.entry("minecraft:ender_eye", 1_200),
            Map.entry("minecraft:ender_pearl", 400),
            Map.entry("minecraft:amethyst_block", 600),
            Map.entry("industrialcore:synthetic_ore_ultimate", 30_000),
            Map.entry("industrialcore:phase_core", 18_000),
            Map.entry("industrialcore:aether_crystal", 8_000),
            Map.entry("industrialcore:quantum_shard", 6_000),
            Map.entry("industrialcore:nebula_crystal", 5_000),
            Map.entry("industrialcore:fuel_rod", 4_000),
            Map.entry("industrialcore:adamantite_ingot", 2_400),
            Map.entry("industrialcore:fuel_can", 1_600)
    );

    private SingularityReactorLogic() {}

    /** Returns base burn ticks for a registry item id (e.g. {@code "minecraft:nether_star"}), or 0. */
    public static int baseFuelTicks(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        return FUEL_TICKS.getOrDefault(itemId, 0);
    }

    public static boolean isFuel(String itemId) {
        return baseFuelTicks(itemId) > 0;
    }

    /** Internal {@code long} FE capacity scaled by battery count (1G FE .. 17G FE). */
    public static long capacity(int batteries) {
        int used = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, batteries));
        long total = BASE_CAPACITY + (long) used * CAPACITY_PER_BATTERY;
        if (total < BASE_CAPACITY || total > MAX_CAPACITY) {
            return MAX_CAPACITY;
        }
        return total;
    }

    /**
     * Generation output in FE/t scaled by motor upgrades (100,000 -> 10,000,000 FE/t)
     * and Lv.100..1000 mastery speed multiplier.
     */
    public static long fePerTick(int motors, int ownerLevel) {
        int used = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        long motorScaled = BASE_FE_PER_TICK
                + Math.round((MAX_MOTOR_FE_PER_TICK - BASE_FE_PER_TICK) * (used / (double) UpgradeMath.MAX_UPGRADE_STACK));
        double mastery = MachineLevelMath.masterySpeedMultiplier(ownerLevel);
        long out = Math.round(motorScaled * mastery);
        return Math.max(BASE_FE_PER_TICK, out);
    }

    /** Fuel burn duration multiplier from batteries: 1.0x at 0 -> 2.0x (+100%) at 64. */
    public static double burnEfficiencyMultiplier(int batteries) {
        int used = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, batteries));
        return 1.0D + used / (double) UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Effective burn duration in ticks after battery efficiency bonus. */
    public static int effectiveBurnTicks(int baseTicks, int batteries) {
        if (baseTicks <= 0) {
            return 0;
        }
        long scaled = Math.round(baseTicks * burnEfficiencyMultiplier(batteries));
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    /** Overflow-safe {@code long} energy addition clamped to {@code [0, capacity]}. */
    public static long addEnergy(long current, long delta, long capacity) {
        long safeCap = Math.max(1L, capacity);
        long safeCur = Math.max(0L, Math.min(safeCap, current));
        if (delta <= 0L) {
            return safeCur;
        }
        long room = safeCap - safeCur;
        return delta >= room ? safeCap : safeCur + delta;
    }

    /** Overflow-safe {@code long} energy extraction returning exact amount removed. */
    public static long extractEnergy(long current, long requested) {
        if (current <= 0L || requested <= 0L) {
            return 0L;
        }
        return Math.min(current, requested);
    }

    /** Safely clamps a non-negative {@code long} value to {@code [0, Integer.MAX_VALUE]}. */
    public static int clampToInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
