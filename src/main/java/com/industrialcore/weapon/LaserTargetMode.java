package com.industrialcore.weapon;

import org.jetbrains.annotations.Nullable;

/**
 * Phase 102.5: Target category filter for the {@code pulse_laser} ({@code att_auto_aim} &amp;
 * {@code att_prism_lens}). Persisted on the item stack in {@code CUSTOM_DATA}.
 */
public enum LaserTargetMode {
    HOSTILE_ONLY("hostile_only", "HOSTILE"),
    ALL_MOBS("all_mobs", "ALL");

    private final String id;
    private final String hudCode;

    LaserTargetMode(String id, String hudCode) {
        this.id = id;
        this.hudCode = hudCode;
    }

    public String id() {
        return id;
    }

    public String hudCode() {
        return hudCode;
    }

    public String translationKey() {
        return "tooltip.industrialcore.weapon.laser_mode." + id;
    }

    public LaserTargetMode next() {
        return this == HOSTILE_ONLY ? ALL_MOBS : HOSTILE_ONLY;
    }

    public static LaserTargetMode byId(@Nullable String id) {
        if (ALL_MOBS.id.equals(id)) {
            return ALL_MOBS;
        }
        return HOSTILE_ONLY;
    }
}
