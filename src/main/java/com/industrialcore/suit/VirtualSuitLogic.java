package com.industrialcore.suit;

import com.industrialcore.machine.UpgradeMath;

import java.util.Map;

/**
 * Phase 98: Pure-Java math and rules for the Virtual Aegis 4-piece endgame armor set
 * and the Suit Module Modifier ({@code suit_modifier}).
 */
public final class VirtualSuitLogic {
    /** Per-piece internal FE battery capacity (10,000,000 FE; 40M FE for a full set). */
    public static final int PIECE_CAPACITY = 10_000_000;
    /** Maximum external FE receive rate per tick per piece (fast charging in modifier / wireless charger). */
    public static final int PIECE_MAX_RECEIVE = 200_000;
    /** Base FE consumed per 1.0 point of absorbed incoming damage. */
    public static final int FE_PER_DAMAGE = 500;
    /** Damage absorption ratio for powered pieces without Shield module (partial vs full set). */
    public static final double PARTIAL_ABSORB_RATIO = 0.85D;
    public static final double FULL_SET_ABSORB_RATIO = 0.95D;
    public static final double SHIELD_ABSORB_RATIO = 1.00D;

    /** Passive & active module FE costs. */
    public static final int VISION_COST_PER_20T = 20;
    public static final int AUTOFEED_COST = 400;
    public static final int FLIGHT_COST_PER_TICK = 40;
    public static final int SHIELD_CLEANSE_COST = 300;
    public static final int SPEED_COST_PER_20T = 25;
    public static final int STEP_COST_PER_20T = 10;
    public static final int NOFALL_COST_PER_BLOCK = 60;
    public static final int JUMP_COST = 150;

    /** Suit Module Modifier machine constants. */
    public static final int MODIFIER_BASE_CAPACITY = 2_000_000;
    public static final int MODIFIER_MAX_RECEIVE = 100_000;
    public static final int MODIFIER_BASE_CHARGE_RATE = 25_000;
    public static final int MODIFIER_BASE_INSTALL_COST = 25_000;

    /** Modifier machine display states. */
    public static final int STATE_IDLE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_NO_ENERGY = 2;

    /** Result of pure FE damage absorption calculation across up to 4 worn armor pieces. */
    public record DamageAbsorbResult(
            float remainingDamage,
            int[] energyConsumed,
            boolean fullyAbsorbed
    ) {}

    private VirtualSuitLogic() {}

    /**
     * Computes FE-backed damage absorption across the player's worn Virtual Aegis pieces.
     *
     * @param incomingDamage positive incoming damage amount
     * @param pieceEnergies  current FE of each worn slot (non-suit slots passed as 0)
     * @param shieldActive   true when {@link SuitModule#SHIELD} is installed, enabled, and chest has FE
     * @param fullSet        true when all 4 Virtual Aegis pieces are equipped
     */
    public static DamageAbsorbResult absorbDamage(float incomingDamage,
                                                  int[] pieceEnergies,
                                                  boolean shieldActive,
                                                  boolean fullSet) {
        int len = pieceEnergies == null ? 0 : pieceEnergies.length;
        int[] consumed = new int[len];
        if (incomingDamage <= 0.0F || len == 0) {
            return new DamageAbsorbResult(Math.max(0.0F, incomingDamage), consumed, incomingDamage <= 0.0F);
        }

        long totalAvailable = 0L;
        for (int e : pieceEnergies) {
            if (e > 0) {
                totalAvailable += e;
            }
        }
        if (totalAvailable <= 0L) {
            return new DamageAbsorbResult(incomingDamage, consumed, false);
        }

        double ratio = shieldActive
                ? SHIELD_ABSORB_RATIO
                : (fullSet ? FULL_SET_ABSORB_RATIO : PARTIAL_ABSORB_RATIO);

        double targetAbsorbDamage = incomingDamage * ratio;
        long neededFe = Math.max(1L, Math.round(Math.ceil(targetAbsorbDamage * FE_PER_DAMAGE)));

        long actualFe;
        float remaining;
        if (totalAvailable >= neededFe) {
            actualFe = neededFe;
            remaining = shieldActive ? 0.0F : Math.max(0.0F, (float) (incomingDamage - targetAbsorbDamage));
        } else {
            // Partially powered: absorb proportionally to available FE, never negative!
            actualFe = totalAvailable;
            double fraction = (double) actualFe / (double) neededFe;
            double absorbed = targetAbsorbDamage * fraction;
            remaining = Math.max(0.0F, (float) (incomingDamage - absorbed));
        }

        // Distribute actualFe evenly across powered pieces without exceeding any piece's stored FE
        long leftToDrain = actualFe;
        int[] avail = new int[len];
        for (int i = 0; i < len; i++) {
            avail[i] = Math.max(0, pieceEnergies[i]);
        }
        while (leftToDrain > 0L) {
            int activeCount = 0;
            for (int i = 0; i < len; i++) {
                if (avail[i] > 0) {
                    activeCount++;
                }
            }
            if (activeCount == 0) {
                break;
            }
            long share = Math.max(1L, leftToDrain / activeCount);
            boolean progressed = false;
            for (int i = 0; i < len && leftToDrain > 0L; i++) {
                if (avail[i] > 0) {
                    int take = (int) Math.min(avail[i], Math.min(share, leftToDrain));
                    if (take > 0) {
                        avail[i] -= take;
                        consumed[i] += take;
                        leftToDrain -= take;
                        progressed = true;
                    }
                }
            }
            if (!progressed) {
                break;
            }
        }

        boolean full = remaining <= 0.0001F;
        return new DamageAbsorbResult(full ? 0.0F : remaining, consumed, full);
    }

    /** Computes FE cost to negate a fall of {@code distance} blocks (safe threshold > 3.0). */
    public static int fallNegationCost(float distance) {
        if (distance <= 3.0F) {
            return 0;
        }
        return Math.max(NOFALL_COST_PER_BLOCK, Math.round((distance - 2.0F) * NOFALL_COST_PER_BLOCK));
    }

    /** Rapid charge rate (FE/t) of {@code suit_modifier} scaled by motor upgrades & owner level. */
    public static int modifierChargeRate(int motors, int ownerLevel) {
        int used = Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
        double speed = 1.0D + UpgradeMath.MAX_PROCESSING_SPEED_BONUS * used / (double) UpgradeMath.MAX_UPGRADE_STACK;
        double mastery = com.industrialcore.level.MachineLevelMath.masterySpeedMultiplier(ownerLevel);
        long rate = Math.round(MODIFIER_BASE_CHARGE_RATE * speed * mastery);
        return (int) Math.max(MODIFIER_BASE_CHARGE_RATE, Math.min(PIECE_MAX_RECEIVE, rate));
    }

    /** Module installation FE cost in {@code suit_modifier} reduced by battery upgrades & mastery. */
    public static int modifierInstallCost(int batteries, int ownerLevel) {
        int afterBat = UpgradeMath.discountedEnergy(MODIFIER_BASE_INSTALL_COST, Math.max(0, batteries));
        return Math.max(1_000, UpgradeMath.applyMasteryEnergy(afterBat, ownerLevel));
    }

    /** Suit modifier battery capacity scaled by battery upgrades. */
    public static int modifierCapacity(int batteries) {
        return UpgradeMath.capacity(Math.max(0, batteries),
                MODIFIER_BASE_CAPACITY, UpgradeMath.DEFAULT_CAPACITY_PER_BATTERY, 20_000_000);
    }

    /** Pure-Java module installation rule check (slot match + no duplicate). */
    public static boolean canInstall(SuitModule.SlotKind suitSlot,
                                     SuitModule module,
                                     Map<String, Boolean> installedModules) {
        if (suitSlot == null || module == null || installedModules == null) {
            return false;
        }
        return module.matches(suitSlot) && !installedModules.containsKey(module.id());
    }

    /** Installs a module (enabled by default) into a mutable module map if compatible & absent. */
    public static boolean install(SuitModule.SlotKind suitSlot,
                                  SuitModule module,
                                  Map<String, Boolean> installedModules) {
        if (!canInstall(suitSlot, module, installedModules)) {
            return false;
        }
        installedModules.put(module.id(), Boolean.TRUE);
        return true;
    }

    /** Toggles an installed module's ON/OFF flag; returns false if not installed. */
    public static boolean toggle(SuitModule module, Map<String, Boolean> installedModules) {
        if (module == null || installedModules == null || !installedModules.containsKey(module.id())) {
            return false;
        }
        boolean cur = Boolean.TRUE.equals(installedModules.get(module.id()));
        installedModules.put(module.id(), !cur);
        return true;
    }

    /** Removes an installed module; returns true if removed. */
    public static boolean uninstall(SuitModule module, Map<String, Boolean> installedModules) {
        if (module == null || installedModules == null) {
            return false;
        }
        return installedModules.remove(module.id()) != null;
    }

    /** Returns true iff the module is present in {@code installedModules} and enabled. */
    public static boolean isModuleEnabled(SuitModule module, Map<String, Boolean> installedModules) {
        return module != null && installedModules != null
                && Boolean.TRUE.equals(installedModules.get(module.id()));
    }
}
