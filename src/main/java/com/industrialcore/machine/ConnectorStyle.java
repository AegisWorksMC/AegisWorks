package com.industrialcore.machine;

/**
 * The three candidate pipe/cable connector designs, as pure geometry data.
 *
 * <p>The connector is the part that touches the neighbour: a gasket plate, a slim coupling or a thin
 * digital frame. It is drawn in front of the block boundary by
 * {@code com.industrialcore.client.ConnectorJoint}, so it can differ per neighbour without changing
 * the block model. Keeping the numbers here, free of game classes, lets the unit tests check that
 * every style stays inside the tube envelope and that a style is always resolvable.</p>
 *
 * <p>All lengths are block units; one model pixel is 1/16.</p>
 */
public enum ConnectorStyle {
    /** A: flat gasket. Nothing sticks out, the plate lies on the neighbour's surface. */
    GASKET(0.055, 0.045, 0.000, 0.070, 0.000, 0.010,
            0.23, 0.25, 0.28, 0.60, 0.66, 0.73),
    /**
     * B: smart connection pad (default). A 7.0 x 7.0 px dark slate plate only 1.2 px thick lies on
     * the machine or chest face, with a 1 px accent bezel inlaid just inside its edge.
     */
    COUPLING(0.0625, 0.045, 0.030, 0.0975, 0.004, 0.036,
            0.20, 0.22, 0.26, 0.36, 0.78, 0.85),
    /** C: digital frame. A thin square frame sits proud of the surface with a dark inner plate. */
    FRAME(0.030, 0.018, 0.010, 0.1400, 0.004, 0.018,
            0.17, 0.19, 0.22, 0.42, 0.72, 0.80);

    /** System property that overrides the design, for the in-game comparison. */
    public static final String PROPERTY = "industrialcore.connectorStyle";
    /** The design used when the property is absent. Chosen after the in-game comparison. */
    public static final ConnectorStyle DEFAULT = COUPLING;

    private final double plateExtra;
    private final double innerDepth;
    private final double outerDepth;
    private final double frameExtra;
    private final double frameInner;
    private final double frameOuter;
    private final float plateRed;
    private final float plateGreen;
    private final float plateBlue;
    private final float accentRed;
    private final float accentGreen;
    private final float accentBlue;

    ConnectorStyle(double plateExtra, double innerDepth, double outerDepth,
                   double frameExtra, double frameInner, double frameOuter,
                   double plateRed, double plateGreen, double plateBlue,
                   double accentRed, double accentGreen, double accentBlue) {
        this.plateExtra = plateExtra;
        this.innerDepth = innerDepth;
        this.outerDepth = outerDepth;
        this.frameExtra = frameExtra;
        this.frameInner = frameInner;
        this.frameOuter = frameOuter;
        this.plateRed = (float) plateRed;
        this.plateGreen = (float) plateGreen;
        this.plateBlue = (float) plateBlue;
        this.accentRed = (float) accentRed;
        this.accentGreen = (float) accentGreen;
        this.accentBlue = (float) accentBlue;
    }

    /** How much wider than the tube the plate is, per side. */
    public double plateExtra() {
        return plateExtra;
    }

    /** How far the plate reaches into the connector's own block. */
    public double innerDepth() {
        return innerDepth;
    }

    /** How far the plate reaches into the neighbour (0 for a flush gasket). */
    public double outerDepth() {
        return outerDepth;
    }

    /** How much wider than the tube the accent frame is, per side; 0 hides the frame. */
    public double frameExtra() {
        return frameExtra;
    }

    /** Inner edge of the accent frame, measured outwards from the boundary. */
    public double frameInner() {
        return frameInner;
    }

    /** Outer edge of the accent frame, measured outwards from the boundary. */
    public double frameOuter() {
        return frameOuter;
    }

    public float plateRed() {
        return plateRed;
    }

    public float plateGreen() {
        return plateGreen;
    }

    public float plateBlue() {
        return plateBlue;
    }

    public float accentRed() {
        return accentRed;
    }

    public float accentGreen() {
        return accentGreen;
    }

    public float accentBlue() {
        return accentBlue;
    }

    /** Set only by the in-game comparison harness; {@code null} in a normal game. */
    private static volatile ConnectorStyle override;

    /**
     * Resolves the active design: the comparison override first, then {@link #PROPERTY}, then
     * {@link #DEFAULT}.
     *
     * <p>Accepts the enum name or its first letter, case-insensitively, so a run can be started with
     * {@code -Dindustrialcore.connectorStyle=b}.</p>
     */
    public static ConnectorStyle active() {
        ConnectorStyle current = override;
        if (current != null) {
            return current;
        }
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return parse(value);
    }

    /**
     * Switches the design at runtime.
     *
     * <p>Only the comparison harness uses this: all three designs have to be photographed on the
     * same scene, which a launch-time property cannot do. Passing {@code null} restores the normal
     * resolution order.</p>
     */
    public static void override(@org.jetbrains.annotations.Nullable ConnectorStyle style) {
        override = style;
    }

    /** Parses a style name or prefix; unknown values fall back to {@link #DEFAULT}. */
    public static ConnectorStyle parse(String value) {
        String trimmed = value.trim();
        for (ConnectorStyle style : values()) {
            if (style.name().equalsIgnoreCase(trimmed)) {
                return style;
            }
        }
        for (ConnectorStyle style : values()) {
            if (!trimmed.isEmpty() && style.name().startsWith(trimmed.toUpperCase(java.util.Locale.ROOT))) {
                return style;
            }
        }
        return DEFAULT;
    }
}
