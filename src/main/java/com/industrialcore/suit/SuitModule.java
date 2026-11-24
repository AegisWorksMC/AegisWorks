package com.industrialcore.suit;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 98: All 8 installable Virtual Aegis suit modules, grouped by compatible armor slot.
 */
public enum SuitModule {
    VISION("mod_vision", SlotKind.HELMET, "VISION", 0xFF5CE6FF),
    AUTOFEED("mod_autofeed", SlotKind.HELMET, "FEED", 0xFF5CFFA0),
    FLIGHT("mod_flight", SlotKind.CHESTPLATE, "FLIGHT", 0xFF7CF2FF),
    SHIELD("mod_shield", SlotKind.CHESTPLATE, "SHIELD", 0xFFFFD166),
    SPEED("mod_speed", SlotKind.LEGGINGS, "SPEED", 0xFF66D9FF),
    STEP("mod_step", SlotKind.LEGGINGS, "STEP", 0xFF8CFF99),
    NOFALL("mod_nofall", SlotKind.BOOTS, "NOFALL", 0xFFFF96D5),
    JUMP("mod_jump", SlotKind.BOOTS, "JUMP", 0xFFFFD866);

    public enum SlotKind {
        HELMET("helmet", "gui.industrialcore.suit.slot.helmet"),
        CHESTPLATE("chestplate", "gui.industrialcore.suit.slot.chestplate"),
        LEGGINGS("leggings", "gui.industrialcore.suit.slot.leggings"),
        BOOTS("boots", "gui.industrialcore.suit.slot.boots");

        private final String id;
        private final String translationKey;

        SlotKind(String id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private final String id;
    private final SlotKind slot;
    private final String hudCode;
    private final int accentColor;

    SuitModule(String id, SlotKind slot, String hudCode, int accentColor) {
        this.id = id;
        this.slot = slot;
        this.hudCode = hudCode;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public SlotKind slot() {
        return slot;
    }

    /** Short ASCII telemetry chip label displayed on the 3D AR Hologram HUD. */
    public String hudCode() {
        return hudCode;
    }

    public int accentColor() {
        return accentColor;
    }

    public String itemTranslationKey() {
        return "item.industrialcore." + id;
    }

    public String descTranslationKey() {
        return "tooltip.industrialcore." + id + ".desc";
    }

    public boolean matches(@Nullable SlotKind targetSlot) {
        return targetSlot != null && this.slot == targetSlot;
    }

    @Nullable
    public static SuitModule byId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (SuitModule m : values()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** Returns all modules compatible with {@code slot}, in deterministic display order. */
    public static List<SuitModule> forSlot(@Nullable SlotKind slot) {
        if (slot == null) {
            return List.of();
        }
        List<SuitModule> out = new ArrayList<>(2);
        for (SuitModule m : values()) {
            if (m.slot == slot) {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }
}
