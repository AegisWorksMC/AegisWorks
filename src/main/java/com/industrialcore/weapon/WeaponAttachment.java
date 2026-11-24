package com.industrialcore.weapon;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 101: All 12 modular attachments for the 4 endgame electric weapons (3 per weapon).
 * Installed, toggled ON/OFF, and removed through the unified {@code suit_modifier} machine.
 */
public enum WeaponAttachment {
    // Pulse Laser (3)
    AUTO_AIM("att_auto_aim", WeaponKind.PULSE_LASER, "AIM", 0xFF42F5FF),
    CRYO_CORE("att_cryo_core", WeaponKind.PULSE_LASER, "CRYO", 0xFF7BE8FF),
    PRISM_LENS("att_prism_lens", WeaponKind.PULSE_LASER, "PRISM", 0xFFD95BFF),

    // Railgun (3)
    FAST_CAPACITOR("att_fast_capacitor", WeaponKind.RAILGUN, "FAST", 0xFFFFD166),
    WALL_SCANNER("att_wall_scanner", WeaponKind.RAILGUN, "SCAN", 0xFFFF5E7E),
    RECOIL_GYRO("att_recoil_gyro", WeaponKind.RAILGUN, "GYRO", 0xFF66B8FF),

    // Quantum Blade (3)
    PLASMA_WAVE("att_plasma_wave", WeaponKind.QUANTUM_BLADE, "WAVE", 0xFFE26BFF),
    BLINK_DRIVE("att_blink_drive", WeaponKind.QUANTUM_BLADE, "BLINK", 0xFF42F5FF),
    QUANTUM_LOOT("att_quantum_loot", WeaponKind.QUANTUM_BLADE, "LOOT", 0xFF5CFFA8),

    // Energy Bow (3)
    MULTI_LOCK("att_multi_lock", WeaponKind.ENERGY_BOW, "LOCK", 0xFFFFD166),
    GRAVITY_ARROW("att_gravity_arrow", WeaponKind.ENERGY_BOW, "GRAV", 0xFFB86BFF),
    AUTO_DRAW("att_auto_draw", WeaponKind.ENERGY_BOW, "RAPID", 0xFF5CFFA8);

    private final String id;
    private final WeaponKind weaponKind;
    private final String hudCode;
    private final int accentColor;

    WeaponAttachment(String id, WeaponKind weaponKind, String hudCode, int accentColor) {
        this.id = id;
        this.weaponKind = weaponKind;
        this.hudCode = hudCode;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public WeaponKind weaponKind() {
        return weaponKind;
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

    public String descriptionTranslationKey() {
        return "tooltip.industrialcore." + id;
    }

    public boolean matches(@Nullable WeaponKind kind) {
        return kind != null && this.weaponKind == kind;
    }

    public static List<WeaponAttachment> forWeapon(WeaponKind kind) {
        List<WeaponAttachment> list = new ArrayList<>(3);
        for (WeaponAttachment att : values()) {
            if (att.weaponKind == kind) {
                list.add(att);
            }
        }
        return List.copyOf(list);
    }

    @Nullable
    public static WeaponAttachment byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (WeaponAttachment att : values()) {
            if (att.id.equals(id)) {
                return att;
            }
        }
        return null;
    }
}
