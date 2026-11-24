package com.industrialcore.menu;

/**
 * Uniform fit of a fixed-size machine panel into the current window (Phase 80).
 *
 * <p>Every Industrial Core panel is authored at a fixed pixel size (196 px wide, 240..420 px
 * tall) because its slots, gauges and labels are laid out on the shared grid of
 * {@link GuiMetrics}. That is the right way to keep the family consistent, but it must not push
 * the panel off a small window or a narrow aspect ratio: at a low GUI scale a 1280x720 window
 * offers only 1280x720 logical pixels, and the eight-row transporter panel is taller than that.</p>
 *
 * <p>This record answers one question without touching Minecraft: given the available screen and
 * the authored panel, how much may the panel be scaled down, and where does it sit? The scale is
 * never above 1 (a panel is not blown up), never below {@link #MIN_SCALE} (below that the text is
 * unreadable and the panel would be unusable), and the offsets centre the scaled panel with at
 * least {@code margin} pixels of breathing room.</p>
 *
 * <p>Because the result depends only on integers, it is unit tested across window sizes, aspect
 * ratios and the GUI scales that produce them.</p>
 */
public record GuiFit(float scale, int x, int y) {
    /** Smallest scale a panel may be drawn at; below this the GUI stops being usable. */
    public static final float MIN_SCALE = 0.5F;
    /** Never enlarge a panel: the authored size is the intended size. */
    public static final float MAX_SCALE = 1.0F;

    /**
     * Fits {@code panelWidth x panelHeight} into {@code screenWidth x screenHeight}.
     *
     * @param screenWidth  logical width of the window (already divided by the GUI scale)
     * @param screenHeight logical height of the window
     * @param panelWidth   authored panel width
     * @param panelHeight  authored panel height
     * @param margin       free pixels kept on every side
     */
    public static GuiFit fit(int screenWidth, int screenHeight, int panelWidth, int panelHeight,
                             int margin) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            return new GuiFit(MAX_SCALE, 0, 0);
        }
        int safeMargin = Math.max(0, margin);
        float widthScale = (screenWidth - 2.0F * safeMargin) / panelWidth;
        float heightScale = (screenHeight - 2.0F * safeMargin) / panelHeight;
        float scale = Math.min(MAX_SCALE, Math.min(widthScale, heightScale));
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            scale = MIN_SCALE;
        }
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);
        int x = Math.max(0, (screenWidth - scaledWidth) / 2);
        int y = Math.max(0, (screenHeight - scaledHeight) / 2);
        return new GuiFit(scale, x, y);
    }

    /** True when the panel had to shrink for this window. */
    public boolean shrunk() {
        return scale < MAX_SCALE - 1.0E-4F;
    }
}
