package com.industrialcore.machine;

/**
 * Per-face item filter mode (Phase 11.5).
 *
 * <p>{@link #OFF} keeps every item allowed. {@link #ALLOW} only lets the listed items through,
 * {@link #DENY} blocks them and lets everything else through. The filter applies to both
 * directions of the face, so the same list can protect an input from wrong ores and keep a
 * by-product inside an output.</p>
 */
public enum ItemFilterMode {
    OFF("off"),
    ALLOW("allow"),
    DENY("deny");

    private final String id;

    ItemFilterMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.industrialcore.filter_mode." + id;
    }

    public ItemFilterMode next() {
        return switch (this) {
            case OFF -> ALLOW;
            case ALLOW -> DENY;
            case DENY -> OFF;
        };
    }

    public static ItemFilterMode byIndex(int index) {
        ItemFilterMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
