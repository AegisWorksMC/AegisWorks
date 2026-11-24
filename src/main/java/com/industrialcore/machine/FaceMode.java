package com.industrialcore.machine;

/**
 * What a machine face does for item automation (Phase 11.3-11.4).
 *
 * <p>{@link #AUTO} is the pre-Phase-11 behaviour of every machine: the face uses the machine's
 * own automation view (inputs accept, outputs extract). It is the default for all six faces and
 * for machines loaded from an older world, so existing factories keep working unchanged.</p>
 */
public enum FaceMode {
    AUTO("auto"),
    INPUT("input"),
    OUTPUT("output"),
    NONE("none");

    private final String id;

    FaceMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.industrialcore.face_mode." + id;
    }

    public FaceMode next() {
        return switch (this) {
            case AUTO -> INPUT;
            case INPUT -> OUTPUT;
            case OUTPUT -> NONE;
            case NONE -> AUTO;
        };
    }

    public static FaceMode byIndex(int index) {
        FaceMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    public boolean allowsInsert() {
        return this == AUTO || this == INPUT;
    }

    public boolean allowsExtract() {
        return this == AUTO || this == OUTPUT;
    }
}
