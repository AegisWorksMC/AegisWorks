package com.industrialcore.weapon;

import org.jetbrains.annotations.Nullable;

/**
 * Auto-aim field-of-view range preset for the {@code pulse_laser} ({@code att_auto_aim}).
 * Persisted on the item stack in {@code DataComponents.CUSTOM_DATA}.
 *
 * <ul>
 *   <li>{@link #NARROW}: ±25° (50° total cone) — precision front lock, smallest angular error wins.</li>
 *   <li>{@link #WIDE}: ±60° (120° total cone, default) — wide front lock, smallest angular error wins.</li>
 *   <li>{@link #FULL_360}: ±180° (no FOV limit) — omnidirectional counter-snap, nearest target wins.</li>
 * </ul>
 */
public enum LaserAimRange {
    NARROW("narrow", 25.0D, 50, "50°", false),
    WIDE("wide", 60.0D, 120, "120°", false),
    FULL_360("full_360", 180.0D, 360, "360°", true);

    private final String id;
    private final double halfAngleDeg;
    private final int fullAngleDeg;
    private final double minDot;
    private final String hudCode;
    private final boolean full360;

    LaserAimRange(String id, double halfAngleDeg, int fullAngleDeg, String hudCode, boolean full360) {
        this.id = id;
        this.halfAngleDeg = halfAngleDeg;
        this.fullAngleDeg = fullAngleDeg;
        this.minDot = full360 ? -1.0D : Math.cos(Math.toRadians(halfAngleDeg));
        this.hudCode = hudCode;
        this.full360 = full360;
    }

    public String id() {
        return id;
    }

    public double halfAngleDeg() {
        return halfAngleDeg;
    }

    public int fullAngleDeg() {
        return fullAngleDeg;
    }

    public double minDot() {
        return minDot;
    }

    public String hudCode() {
        return hudCode;
    }

    public boolean isFull360() {
        return full360;
    }

    public String translationKey() {
        return "tooltip.industrialcore.weapon.laser_range." + id;
    }

    public LaserAimRange next() {
        return switch (this) {
            case NARROW -> WIDE;
            case WIDE -> FULL_360;
            case FULL_360 -> NARROW;
        };
    }

    public static LaserAimRange byId(@Nullable String id) {
        if (NARROW.id.equals(id)) {
            return NARROW;
        }
        if (FULL_360.id.equals(id)) {
            return FULL_360;
        }
        return WIDE;
    }
}
