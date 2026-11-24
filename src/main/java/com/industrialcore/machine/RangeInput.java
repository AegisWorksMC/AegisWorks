package com.industrialcore.machine;

/**
 * Editable state of the three quarry range fields (width, depth, height).
 *
 * <p>This is deliberately separate from the applied/server values. The GUI used to rewrite every
 * unfocused field from the menu data on each client tick, which is why typing into one field and
 * then clicking another silently lost the first value: moving the focus made the previous field
 * "unfocused", and the next tick restored it from the server. Here the typed text is kept until
 * the player applies it or the values are replaced from the server while nothing is being edited.
 * </p>
 *
 * <p>No Minecraft classes are used, so the behaviour is unit tested directly.</p>
 */
public final class RangeInput {
    /** Axis indices used by the GUI. */
    public static final int WIDTH = 0;
    public static final int DEPTH = 1;
    public static final int HEIGHT = 2;
    public static final int AXES = 3;

    /** Same limits as the previous GUI: 1..128 blocks per axis. */
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 128;

    private final int[] applied = new int[AXES];
    private final String[] text = new String[AXES];
    private boolean dirty;

    public RangeInput(int width, int depth, int height) {
        this.applied[WIDTH] = clamp(width);
        this.applied[DEPTH] = clamp(depth);
        this.applied[HEIGHT] = clamp(height);
        for (int axis = 0; axis < AXES; axis++) {
            this.text[axis] = Integer.toString(this.applied[axis]);
        }
    }

    /** Last value that was applied (or received from the server). */
    public int applied(int axis) {
        return this.applied[checkAxis(axis)];
    }

    /** Current text of a field, which may not have been applied yet. */
    public String text(int axis) {
        return this.text[checkAxis(axis)];
    }

    /**
     * Updates one field's text. The other two fields are not touched, so all three can be edited
     * in sequence before a single apply.
     */
    public void setText(int axis, String value) {
        int index = checkAxis(axis);
        String next = value == null ? "" : value;
        if (!next.equals(this.text[index])) {
            this.text[index] = next;
            this.dirty = true;
        }
    }

    /** True while any field differs from the applied values and has not been applied yet. */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Validates and applies all three fields at once.
     *
     * <p>Blank or non-numeric input falls back to the applied value and out-of-range values are
     * clamped to {@link #MIN_VALUE}..{@link #MAX_VALUE}; the fields are normalised to the values
     * that will be sent, so the GUI never shows a value it did not transmit.</p>
     *
     * @return width, depth and height in that order
     */
    public int[] apply() {
        int[] result = new int[AXES];
        for (int axis = 0; axis < AXES; axis++) {
            result[axis] = parse(this.text[axis], this.applied[axis]);
            this.applied[axis] = result[axis];
            this.text[axis] = Integer.toString(result[axis]);
        }
        this.dirty = false;
        return result;
    }

    /**
     * Mirrors server values into the fields, but only while the player has not edited anything.
     * A synced menu update must never overwrite half-typed input.
     *
     * @return true when the fields changed (the caller then has to refresh the widgets)
     */
    public boolean syncFromServer(int width, int depth, int height) {
        if (this.dirty) {
            return false;
        }
        int[] next = {clamp(width), clamp(depth), clamp(height)};
        boolean changed = false;
        for (int axis = 0; axis < AXES; axis++) {
            if (this.applied[axis] != next[axis]) {
                this.applied[axis] = next[axis];
                this.text[axis] = Integer.toString(next[axis]);
                changed = true;
            }
        }
        return changed;
    }

    /** Parses one field; blank/invalid input keeps the fallback instead of throwing. */
    public static int parse(String raw, int fallback) {
        if (raw == null) {
            return clamp(fallback);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return clamp(fallback);
        }
        try {
            return clamp(Integer.parseInt(trimmed));
        } catch (NumberFormatException exception) {
            return clamp(fallback);
        }
    }

    public static int clamp(int value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    private static int checkAxis(int axis) {
        if (axis < 0 || axis >= AXES) {
            throw new IndexOutOfBoundsException("range axis " + axis);
        }
        return axis;
    }
}
