package com.industrialcore.suit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.level.SkillTreeLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 98 pure-Java unit tests for Virtual Aegis armor energy absorption, module management,
 * {@link SuitModifierLayout} geometry, {@link VirtualHudMath} AR HUD math, and shipped assets.
 */
class VirtualSuitTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path DATA = Path.of("src/main/resources/data/industrialcore");

    @Test
    void damageAbsorptionShieldFullPartialFallbackAndNonNegativeAreExact() {
        // 1. Shield active + full FE -> 100% absorption (0 damage remaining), exact 500 FE per dmg
        int[] fullPower = {10_000_000, 10_000_000, 10_000_000, 10_000_000};
        VirtualSuitLogic.DamageAbsorbResult shieldRes =
                VirtualSuitLogic.absorbDamage(10.0F, fullPower, true, true);
        assertEquals(0.0F, shieldRes.remainingDamage(), 1.0E-5F);
        assertTrue(shieldRes.fullyAbsorbed());
        long totalDrained = 0;
        for (int i = 0; i < 4; i++) {
            assertTrue(shieldRes.energyConsumed()[i] >= 0);
            assertTrue(shieldRes.energyConsumed()[i] <= fullPower[i]);
            totalDrained += shieldRes.energyConsumed()[i];
        }
        assertEquals(5_000L, totalDrained, "10 damage * 500 FE/dmg = 5,000 FE");

        // 2. Full set without Shield -> 95% absorption
        VirtualSuitLogic.DamageAbsorbResult fullSetRes =
                VirtualSuitLogic.absorbDamage(20.0F, fullPower, false, true);
        assertEquals(1.0F, fullSetRes.remainingDamage(), 0.01F);
        assertFalse(fullSetRes.fullyAbsorbed());

        // 3. Single piece without Shield -> 85% absorption
        int[] singlePiece = {0, 1_000_000, 0, 0};
        VirtualSuitLogic.DamageAbsorbResult singleRes =
                VirtualSuitLogic.absorbDamage(10.0F, singlePiece, false, false);
        assertEquals(1.5F, singleRes.remainingDamage(), 0.01F);
        assertEquals(4_250, singleRes.energyConsumed()[1]);

        // 4. Low FE fallback: 10 damage with shield needs 5,000 FE, only 2,500 FE available -> 50% absorbed
        int[] lowFe = {1_000, 1_500, 0, 0};
        VirtualSuitLogic.DamageAbsorbResult lowRes =
                VirtualSuitLogic.absorbDamage(10.0F, lowFe, true, true);
        assertEquals(5.0F, lowRes.remainingDamage(), 0.01F);
        assertEquals(1_000, lowRes.energyConsumed()[0]);
        assertEquals(1_500, lowRes.energyConsumed()[1]);

        // 5. Zero FE -> full damage passes through safely
        int[] emptyFe = {0, 0, 0, 0};
        VirtualSuitLogic.DamageAbsorbResult emptyRes =
                VirtualSuitLogic.absorbDamage(8.0F, emptyFe, true, true);
        assertEquals(8.0F, emptyRes.remainingDamage(), 1.0E-5F);
        assertFalse(emptyRes.fullyAbsorbed());
        for (int c : emptyRes.energyConsumed()) {
            assertEquals(0, c);
        }

        // 6. Zero/negative incoming damage stays safe
        VirtualSuitLogic.DamageAbsorbResult zeroRes =
                VirtualSuitLogic.absorbDamage(-5.0F, fullPower, true, true);
        assertEquals(0.0F, zeroRes.remainingDamage(), 1.0E-5F);
    }

    @Test
    void moduleCompatibilityInstallDuplicateGuardToggleAndUninstallWorkCleanly() {
        assertEquals(8, SuitModule.values().length);
        for (SuitModule.SlotKind slot : SuitModule.SlotKind.values()) {
            assertEquals(2, SuitModule.forSlot(slot).size(), "Each armor piece has 2 compatible modules: " + slot);
        }

        Map<String, Boolean> chestMods = new LinkedHashMap<>();
        // Incompatible module rejected
        assertFalse(VirtualSuitLogic.canInstall(SuitModule.SlotKind.CHESTPLATE, SuitModule.VISION, chestMods));
        assertFalse(VirtualSuitLogic.install(SuitModule.SlotKind.CHESTPLATE, SuitModule.VISION, chestMods));
        assertTrue(chestMods.isEmpty());

        // Compatible module installs enabled by default
        assertTrue(VirtualSuitLogic.install(SuitModule.SlotKind.CHESTPLATE, SuitModule.FLIGHT, chestMods));
        assertTrue(VirtualSuitLogic.isModuleEnabled(SuitModule.FLIGHT, chestMods));

        // Duplicate install rejected
        assertFalse(VirtualSuitLogic.canInstall(SuitModule.SlotKind.CHESTPLATE, SuitModule.FLIGHT, chestMods));
        assertFalse(VirtualSuitLogic.install(SuitModule.SlotKind.CHESTPLATE, SuitModule.FLIGHT, chestMods));

        // Toggle OFF -> ON
        assertTrue(VirtualSuitLogic.toggle(SuitModule.FLIGHT, chestMods));
        assertFalse(VirtualSuitLogic.isModuleEnabled(SuitModule.FLIGHT, chestMods));
        assertTrue(VirtualSuitLogic.toggle(SuitModule.FLIGHT, chestMods));
        assertTrue(VirtualSuitLogic.isModuleEnabled(SuitModule.FLIGHT, chestMods));

        // Uninstall & verify absent module cannot toggle
        assertTrue(VirtualSuitLogic.uninstall(SuitModule.FLIGHT, chestMods));
        assertFalse(VirtualSuitLogic.toggle(SuitModule.FLIGHT, chestMods));
        assertFalse(VirtualSuitLogic.uninstall(SuitModule.FLIGHT, chestMods));

        // Upgrade math monotonicity
        assertTrue(VirtualSuitLogic.modifierChargeRate(64, 99) > VirtualSuitLogic.modifierChargeRate(0, 1));
        assertTrue(VirtualSuitLogic.modifierInstallCost(64, 99) < VirtualSuitLogic.modifierInstallCost(0, 1));
        assertEquals(0, VirtualSuitLogic.fallNegationCost(3.0F));
        assertTrue(VirtualSuitLogic.fallNegationCost(12.0F) > 0);
    }

    @Test
    void suitModifierLayoutHasZeroOverlapsAndStaysInsideBounds() {
        Rect panel = new Rect(0, 0, SuitModifierLayout.WIDTH, SuitModifierLayout.HEIGHT);
        Rect headerBtn = new Rect(
                SkillTreeLayout.MACHINE_HEADER_BUTTON.x(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.y(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.width(),
                SkillTreeLayout.MACHINE_HEADER_BUTTON.height());
        Rect invArea = new Rect(8, SuitModifierLayout.PLAYER_INV_Y, 162, 76);

        List<Rect> topLevel = List.of(
                headerBtn,
                SuitModifierLayout.ENERGY_BAR,
                SuitModifierLayout.SUIT_SLOT_BOX,
                SuitModifierLayout.MODULE_SLOT_BOX,
                SuitModifierLayout.MOTOR_SLOT_BOX,
                SuitModifierLayout.BATTERY_SLOT_BOX,
                SuitModifierLayout.INSTALL_BUTTON,
                SuitModifierLayout.LASER_RANGE_BUTTON,
                SuitModifierLayout.MODULE_PANEL,
                invArea
        );
        for (int i = 0; i < topLevel.size(); i++) {
            Rect a = topLevel.get(i);
            assertInside(panel, a, "TopLevel_" + i);
            for (int j = i + 1; j < topLevel.size(); j++) {
                assertFalse(overlaps(a, topLevel.get(j)),
                        "Overlap between rect " + i + " (" + a + ") and " + j + " (" + topLevel.get(j) + ")");
            }
        }

        // Rows, toggle buttons, and remove buttons sit cleanly inside MODULE_PANEL
        for (int i = 0; i < SuitModifierLayout.VISIBLE_ROWS; i++) {
            Rect row = SuitModifierLayout.MODULE_ROWS.get(i);
            Rect tog = SuitModifierLayout.TOGGLE_BUTTONS.get(i);
            Rect rem = SuitModifierLayout.REMOVE_BUTTONS.get(i);
            assertInside(SuitModifierLayout.MODULE_PANEL, row, "Row_" + i);
            assertInside(row, tog, "Toggle_" + i);
            assertInside(row, rem, "Remove_" + i);
            assertFalse(overlaps(tog, rem), "Toggle and remove overlap on row " + i);
            assertEquals(i, SuitModifierLayout.toggleButtonAt(tog.x() + 2, tog.y() + 2));
            assertEquals(i, SuitModifierLayout.removeButtonAt(rem.x() + 2, rem.y() + 2));
        }
        assertFalse(overlaps(SuitModifierLayout.ROW_0, SuitModifierLayout.ROW_1));
        assertEquals(-1, SuitModifierLayout.toggleButtonAt(0, 0));
        assertEquals(-1, SuitModifierLayout.removeButtonAt(0, 0));
    }

    @Test
    void virtualHudMathParallaxBootSequenceColorsAndFormattingAreExact() {
        assertTrue(VirtualHudMath.isFullSet(true, true, true, true));
        assertFalse(VirtualHudMath.isFullSet(true, true, true, false));
        assertFalse(VirtualHudMath.isFullSet(false, true, true, true));

        // Parallax smoothing & strict clamping under huge spikes
        float p = 0.0F;
        for (int i = 0; i < 60; i++) {
            p = VirtualHudMath.stepParallax(p, 999.0F, 0.15F, VirtualHudMath.MAX_PARALLAX_X);
            assertTrue(p >= -VirtualHudMath.MAX_PARALLAX_X && p <= VirtualHudMath.MAX_PARALLAX_X);
        }
        assertTrue(p > 11.5F && p <= VirtualHudMath.MAX_PARALLAX_X);
        float neg = VirtualHudMath.stepParallax(0.0F, -500.0F, 1.0F, VirtualHudMath.MAX_PARALLAX_Y);
        assertEquals(-VirtualHudMath.MAX_PARALLAX_Y, neg, 1.0E-5F);

        // Angle wrap handles 359 -> 1 (+2 deg) and 1 -> 359 (-2 deg)
        assertEquals(2.0F, VirtualHudMath.wrapAngleDelta(1.0F, 359.0F), 1.0E-4F);
        assertEquals(-2.0F, VirtualHudMath.wrapAngleDelta(359.0F, 1.0F), 1.0E-4F);

        // Boot sequence monotonic 0 -> 1 over 1200ms
        assertEquals(0.0D, VirtualHudMath.bootProgress(-10L), 1.0E-6D);
        assertEquals(0.0D, VirtualHudMath.bootProgress(0L), 1.0E-6D);
        double mid = VirtualHudMath.bootProgress(600L);
        assertTrue(mid > 0.5D && mid < 1.0D, "Ease-out cubic midpoint must be > 0.5");
        assertEquals(1.0D, VirtualHudMath.bootProgress(VirtualHudMath.BOOT_DURATION_MS), 1.0E-6D);
        assertEquals(1.0D, VirtualHudMath.bootProgress(5_000L), 1.0E-6D);

        // HP color transitions
        assertEquals(VirtualHudMath.HP_EMERALD, VirtualHudMath.hpColor(1.0F));
        assertEquals(VirtualHudMath.HP_EMERALD, VirtualHudMath.hpColor(0.65F));
        assertEquals(VirtualHudMath.HP_AMBER, VirtualHudMath.hpColor(0.30F));
        assertEquals(VirtualHudMath.HP_CRIMSON, VirtualHudMath.hpColor(0.0F));

        // Compact FE formatting
        assertEquals("40.0M", VirtualHudMath.formatCompactFe(40_000_000L));
        assertEquals("250.0K", VirtualHudMath.formatCompactFe(250_000L));
        assertEquals("750", VirtualHudMath.formatCompactFe(750L));

        // Emissive breathing pulse (0.85..1.0) & restrained neon shimmer (0.22..0.30) + UV scroll
        for (int t = 0; t <= 400; t += 7) {
            float pVal = VirtualArmorGlowMath.emissivePulse(t);
            assertTrue(pVal >= VirtualArmorGlowMath.EMISSIVE_MIN - 1e-5F
                    && pVal <= VirtualArmorGlowMath.EMISSIVE_MAX + 1e-5F);
            float sVal = VirtualArmorGlowMath.shimmerIntensity(t);
            assertTrue(sVal >= VirtualArmorGlowMath.SHIMMER_MIN - 1e-5F
                    && sVal <= VirtualArmorGlowMath.SHIMMER_MAX + 1e-5F);
            float uVal = VirtualArmorGlowMath.shimmerScrollU(t);
            float vVal = VirtualArmorGlowMath.shimmerScrollV(t);
            assertTrue(uVal >= 0.0F && uVal < 1.0F);
            assertTrue(vVal >= 0.0F && vVal < 1.0F);
        }
        assertEquals(0xFFFFFFFF, VirtualArmorGlowMath.packArgb(1.0F, 1.0F, 1.0F, 1.0F));
        assertEquals(0xFF0080FF, VirtualArmorGlowMath.packArgb(1.0F, 0.0F, 128.0F / 255.0F, 1.0F));
    }

    @Test
    void allPhase98AssetsRecipesModelsLootTablesAndPickaxeTagExist() throws IOException {
        assertTrue(Files.exists(ASSETS.resolve("blockstates/suit_modifier.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/block/suit_modifier.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/block/suit_modifier_on.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/item/suit_modifier.json")));
        assertTrue(Files.exists(DATA.resolve("loot_table/blocks/suit_modifier.json")));
        assertTrue(Files.exists(DATA.resolve("recipe/suit_modifier.json")));
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/va_layer_1.png")));
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/va_layer_2.png")));
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/va_layer_1_emissive.png")));
        assertTrue(Files.exists(ASSETS.resolve("textures/models/armor/va_layer_2_emissive.png")));
        assertTrue(Files.exists(ASSETS.resolve("textures/misc/va_neon_glint.png")));

        String pickaxe = Files.readString(Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(pickaxe.contains("\"industrialcore:suit_modifier\""));

        List<String> items = List.of(
                "virtual_helmet", "virtual_chestplate", "virtual_leggings", "virtual_boots",
                "mod_vision", "mod_autofeed", "mod_flight", "mod_shield",
                "mod_speed", "mod_step", "mod_nofall", "mod_jump"
        );
        for (String id : items) {
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + id + ".png")), "Missing tex: " + id);
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")), "Missing model: " + id);
            assertTrue(Files.exists(DATA.resolve("recipe/" + id + ".json")), "Missing recipe: " + id);
        }

        for (String langFile : new String[] {"ja_jp.json", "en_us.json"}) {
            JsonObject lang = JsonParser.parseString(
                    Files.readString(ASSETS.resolve("lang/" + langFile))).getAsJsonObject();
            assertNotNull(lang.get("block.industrialcore.suit_modifier"));
            assertNotNull(lang.get("key.industrialcore.ar_hud_toggle"));
            assertNotNull(lang.get("gui.industrialcore.ar_hud.link_established"));
            for (String id : items) {
                assertTrue(lang.has("item.industrialcore." + id), langFile + " missing " + id);
            }
        }
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }

    private static void assertInside(Rect outer, Rect inner, String label) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                        && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                label + " out of bounds: " + inner + " not in " + outer);
    }
}
