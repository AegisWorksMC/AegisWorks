package com.industrialcore.weapon;

import org.jetbrains.annotations.Nullable;

/**
 * Phase 101: The 4 endgame electric weapons powered by FE instead of physical durability.
 */
public enum WeaponKind {
    PULSE_LASER("pulse_laser", "LASER", 0xFF3EF7FF),
    RAILGUN("railgun", "RAIL", 0xFF7FA8FF),
    QUANTUM_BLADE("quantum_blade", "BLADE", 0xFFD95BFF),
    ENERGY_BOW("energy_bow", "BOW", 0xFF5CFFA8);

    private final String id;
    private final String hudCode;
    private final int accentColor;

    WeaponKind(String id, String hudCode, int accentColor) {
        this.id = id;
        this.hudCode = hudCode;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public String hudCode() {
        return hudCode;
    }

    public int accentColor() {
        return accentColor;
    }

    public String itemTranslationKey() {
        return "item.industrialcore." + id;
    }

    @Nullable
    public static WeaponKind byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (WeaponKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
