package com.industrialcore.machine;

import java.util.Locale;

/**
 * Validation and canonical comparison of transporter channel names (Phases 26-29).
 *
 * <p>Every comparison of two channel names in the mod goes through {@link #key(String)}: the
 * display name keeps what the player typed (trimmed), the key is lower-cased so "Ore" and "ore"
 * address the same channel on a server where two players disagree about capitalisation. The
 * server re-validates every name that arrives from a GUI payload; an empty or oversized name is
 * rejected and the previous channel stays active.</p>
 */
public final class TransportChannel {
    /** Longest accepted channel name after trimming. */
    public static final int MAX_LENGTH = 24;
    /** The name of a device that has not been configured yet. */
    public static final String NONE = "";

    private TransportChannel() {}

    /** True when {@code raw} is a usable channel name (trimmed length 1..{@link #MAX_LENGTH}). */
    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            // Control characters would break the GUI line drawing and the NBT round trip.
            if (character < 0x20 || character == 0x7F) {
                return false;
            }
        }
        return true;
    }

    /** Trimmed display name, or {@link #NONE} when the input is not a valid channel. */
    public static String display(String raw) {
        return isValid(raw) ? raw.trim() : NONE;
    }

    /** Canonical, case-insensitive comparison key of a display name. */
    public static String key(String display) {
        return display == null || display.isEmpty()
                ? NONE
                : display.trim().toLowerCase(Locale.ROOT);
    }
}
