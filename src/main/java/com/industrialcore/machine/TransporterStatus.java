package com.industrialcore.machine;

import java.util.Locale;

/**
 * Operating state of a transporter, mirrored to its GUI (Phases 26-29).
 *
 * <p>Transporters get their own status enum on purpose: {@link MachineStatus} is synchronised to
 * ten existing screens through ordinals, and appending transporter-only states to it would force
 * every existing machine switch statement to change. Nothing else in the mod ever produces these
 * ordinals, so the transporter menu can clamp them safely.</p>
 */
public enum TransporterStatus {
    /** Structure and channel are fine; there is simply nothing waiting to be sent. */
    IDLE,
    /** The last transfer attempt moved items or FE. */
    WORKING,
    /** The multiblock shell is missing or contains the wrong block. */
    NO_STRUCTURE,
    /** No channel name has been set yet. */
    NO_CHANNEL,
    /** The channel has no registered receiver at all; the search backs off. */
    NO_RECEIVER,
    /**
     * Receivers exist on the channel but none of them could take the transfer (buffer full, FE
     * full, or every candidate chunk is unloaded). Everything stays in the sender.
     */
    RECEIVER_FULL;

    public String translationKey() {
        return "gui.industrialcore.transporter.status." + name().toLowerCase(Locale.ROOT);
    }

    /** Clamped lookup for ordinals that arrived through the container data. */
    public static TransporterStatus byOrdinal(int ordinal) {
        TransporterStatus[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }
}
