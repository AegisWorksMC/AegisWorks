package com.industrialcore.machine;

import java.util.Locale;

/** State of a processing machine (crusher / electric furnace), mirrored to its GUI. */
public enum MachineStatus {
    /** Ready, but the input slot is empty. */
    NO_INPUT,
    /** The item in the input slot has no matching recipe. */
    NO_RECIPE,
    /** The internal FE buffer cannot pay for the next tick. */
    NO_ENERGY,
    /** The result cannot be inserted into the output slot. */
    OUTPUT_FULL,
    /** An operation is running. */
    WORKING,
    /** Nothing to do. */
    IDLE,
    /**
     * Phase 15 (reconstructor): no mineral is selected yet.
     *
     * <p>The three new states are appended at the end on purpose: the ordinal is sent to the
     * client through the container data, so an existing state must never change its number.</p>
     */
    NO_SELECTION,
    /** Phase 15 (reconstructor): the material slot does not hold enough material. */
    NO_MATERIAL,
    /** Phase 15 (reconstructor): the crystal slot does not hold enough crystals. */
    NO_CRYSTAL,
    /**
     * Phase 17 (alloy furnace): the fuel slot is empty, so the run waits and burns nothing.
     *
     * <p>Appended at the end for the same reason as the Phase 15 states: the ordinal travels to
     * the client as container data.</p>
     */
    NO_FUEL;

    public String translationKey() {
        return "gui.industrialcore.machine.status." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isProblem() {
        return this == NO_RECIPE || this == NO_ENERGY || this == OUTPUT_FULL || this == NO_FUEL;
    }
}
