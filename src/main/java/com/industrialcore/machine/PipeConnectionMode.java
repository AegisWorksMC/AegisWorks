package com.industrialcore.machine;

/**
 * Per-side transport mode of an item pipe (Phase 10).
 *
 * <p>A pipe still connects visually to any pipe, conveyor or item handler, but the shape says
 * nothing about transport any more. The mode of the face the pipe is touched on decides what
 * happens:</p>
 * <ul>
 *   <li>{@link #NONE} - connection only; nothing enters or leaves this side. This is the default
 *       for a newly placed pipe and for every pipe loaded from an older world, so an existing
 *       factory never starts moving items on its own after the update.</li>
 *   <li>{@link #NORMAL} - the side is part of the pipe network (item pipes hand stacks over), but
 *       an adjacent inventory is neither pulled from nor pushed into.</li>
 *   <li>{@link #PUSH} - the pipe inserts its held stack into the adjacent inventory.</li>
 *   <li>{@link #PULL} - the pipe extracts from the adjacent inventory.</li>
 * </ul>
 *
 * <p>The enum order is the order the wrench cycles through; {@code PUSH}/{@code PULL} towards
 * another pipe behave like {@link #NORMAL}, because a pipe is not an inventory.</p>
 */
public enum PipeConnectionMode {
    NONE("none"),
    NORMAL("normal"),
    PUSH("push"),
    PULL("pull");

    private final String id;

    PipeConnectionMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.industrialcore.pipe_mode." + id;
    }

    public String descriptionKey() {
        return "gui.industrialcore.pipe_mode." + id + ".desc";
    }

    /** Next mode in wrench order, or the previous one when sneaking. */
    public PipeConnectionMode next(boolean reverse) {
        PipeConnectionMode[] values = values();
        int step = reverse ? -1 : 1;
        int index = (ordinal() + step + values.length) % values.length;
        return values[index];
    }

    public static PipeConnectionMode byIndex(int index) {
        PipeConnectionMode[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isPush() {
        return this == PUSH;
    }

    public boolean isPull() {
        return this == PULL;
    }

    /** True when this side may carry a stack through the pipe network. */
    public boolean isTransport() {
        return this != NONE;
    }
}
