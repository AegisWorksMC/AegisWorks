package com.industrialcore.machine;

import java.util.Locale;

/**
 * State of one distributor face, mirrored to its GUI (Phase 19).
 *
 * <p>The ordinal travels to the client through the container data, so the order of the constants
 * must never change; new states are appended at the end.</p>
 */
public enum DistributorOutputState {
    /** The face is set to INPUT or NONE, so the distributor never pushes through it. */
    DISABLED,
    /** The neighbour exposes an inventory that still accepts the buffered item. */
    READY,
    /** An inventory is connected but has no room for the buffered item. */
    FULL,
    /** Nothing with an item inventory is attached to this face. */
    UNCONNECTED,
    /** This is the face the buffered items just came in through; never pushed back. */
    INTAKE;

    public String translationKey() {
        return "gui.industrialcore.distributor.state." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isProblem() {
        return this == FULL || this == UNCONNECTED || this == DISABLED;
    }
}
