package com.industrialcore.machine;

import java.util.Locale;

/**
 * Phase 114: Explicit, player-friendly stop reason classification shared by both Jade tooltips
 * and in-game machine GUIs so a halted machine always reports WHY it stopped in one clear phrase.
 *
 * <p>Kept strictly free of Minecraft/Jade classes so every classification rule is unit testable.</p>
 */
public enum MachineStopReason {
    /** Actively working / generating / standing by normally. */
    NONE,
    /** Internal FE buffer cannot pay for operation. */
    NO_ENERGY,
    /** Output slot(s) are full or blocked. */
    OUTPUT_FULL,
    /** Nuclear reactor coolant water tank is empty. */
    NO_COOLANT,
    /** Halted by an external Redstone signal. */
    REDSTONE_DISABLED,
    /** Ready and powered, waiting for input items / targets. */
    WAITING_INPUT,
    /** Fuel or catalyst slot is empty. */
    NO_FUEL,
    /** Input item has no valid recipe. */
    NO_RECIPE,
    /** Multiblock reactor structure is not yet complete. */
    INCOMPLETE_STRUCTURE,
    /** Reactor triggered safety emergency cooldown after Overdrive depletion. */
    OVERDRIVE_COOLDOWN,
    /** Energy buffer is full, pausing fuel burn without wasting resources. */
    BUFFER_FULL,
    /** Missing environmental condition (water, lava, sky, open air, daylight). */
    NO_SOURCE;

    public String translationKey() {
        return "gui.industrialcore.stop_reason." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isHalted() {
        return this != NONE;
    }

    public boolean isCritical() {
        return this == NO_ENERGY
                || this == OUTPUT_FULL
                || this == NO_COOLANT
                || this == NO_RECIPE
                || this == INCOMPLETE_STRUCTURE
                || this == OVERDRIVE_COOLDOWN
                || this == NO_SOURCE;
    }

    /** Classifies any {@link MachineStatus} (plus Redstone halt override) into an explicit stop reason. */
    public static MachineStopReason fromMachineStatus(MachineStatus status, boolean redstoneDisabled) {
        if (redstoneDisabled) {
            return REDSTONE_DISABLED;
        }
        if (status == null) {
            return NONE;
        }
        return switch (status) {
            case WORKING -> NONE;
            case NO_ENERGY -> NO_ENERGY;
            case OUTPUT_FULL -> OUTPUT_FULL;
            case NO_RECIPE -> NO_RECIPE;
            case NO_FUEL -> NO_FUEL;
            case NO_INPUT, NO_MATERIAL, NO_CRYSTAL, NO_SELECTION -> WAITING_INPUT;
            case IDLE -> NONE;
        };
    }

    /**
     * Classifies a {@code GenerationBlockEntity.Status} ordinal (0..8) into an explicit stop reason.
     * Ordinals: 0=RUNNING, 1=IDLE, 2=NO_FUEL, 3=NO_WATER, 4=NIGHT, 5=NO_LAVA, 6=NO_AIR,
     * 7=INCOMPLETE, 8=COOLDOWN.
     */
    public static MachineStopReason fromGeneratorStatus(int statusOrdinal, boolean isNuclear,
                                                        int coolantMb, boolean overdriveCooldown) {
        if (overdriveCooldown || statusOrdinal == 8) {
            return OVERDRIVE_COOLDOWN;
        }
        if (statusOrdinal == 7) {
            return INCOMPLETE_STRUCTURE;
        }
        if (isNuclear && coolantMb <= 0) {
            return NO_COOLANT;
        }
        return switch (statusOrdinal) {
            case 0 -> NONE;
            case 1 -> BUFFER_FULL;
            case 2 -> NO_FUEL;
            case 3, 4, 5, 6 -> NO_SOURCE;
            default -> NONE;
        };
    }

    /**
     * Classifies a {@code SingularityReactorLogic} display state (0..4) into an explicit stop reason.
     * States: 0=NO_FUEL, 1=GENERATING, 2=FULL, 3=INCOMPLETE, 4=COOLDOWN.
     */
    public static MachineStopReason fromSingularityState(int displayState, boolean overdriveCooldown) {
        if (overdriveCooldown || displayState == 4) {
            return OVERDRIVE_COOLDOWN;
        }
        return switch (displayState) {
            case 1 -> NONE;
            case 2 -> BUFFER_FULL;
            case 3 -> INCOMPLETE_STRUCTURE;
            case 0 -> NO_FUEL;
            default -> NONE;
        };
    }

    public static MachineStopReason byNameOrNone(String name) {
        if (name == null || name.isEmpty()) {
            return NONE;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
