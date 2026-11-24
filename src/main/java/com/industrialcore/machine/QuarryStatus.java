package com.industrialcore.machine;

import java.util.Locale;

/** Server authoritative quarry state, mirrored to the GUI and the world renderer. */
public enum QuarryStatus {
    /** No valid area configured yet. */
    NO_RANGE,
    /** Area configured, not running. */
    STOPPED,
    /** Running normally. */
    RUNNING,
    /** Auto-paused because the internal FE buffer is below one block's cost. */
    PAUSED_NO_ENERGY,
    /** Auto-paused because the drops of the next block do not fit. */
    PAUSED_INVENTORY_FULL,
    /** Auto-paused because the target chunk is not loaded (no chunk loading is forced). */
    PAUSED_CHUNK_UNLOADED,
    /** Every position in the area has been processed. */
    COMPLETE,
    /** Phase 22: no pickaxe is installed, so the machine cannot recover anything. */
    PAUSED_NO_TOOLS,
    /** Phase 22: the ore selection is empty, so there is nothing the machine is allowed to mine. */
    PAUSED_NO_SELECTION;

    public String translationKey() {
        return "gui.industrialcore.status." + name().toLowerCase(Locale.ROOT);
    }

    public boolean isPaused() {
        return this == PAUSED_NO_ENERGY || this == PAUSED_INVENTORY_FULL
                || this == PAUSED_CHUNK_UNLOADED || this == PAUSED_NO_TOOLS
                || this == PAUSED_NO_SELECTION;
    }
}
