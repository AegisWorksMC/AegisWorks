package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.endgame.SingularityReactorLogic;
import com.industrialcore.level.AegisAdvancements;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.MachineStopReason;
import com.industrialcore.menu.GenerationLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.reactor.ReactorOverdriveLogic;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 114 verification suite:
 * - All 28 AegisWorks Advancements JSON syntax, parent DAG connectivity, icons &amp; ja/en translations
 * - Explicit stop-reason classification (MachineStopReason) for machines, generators &amp; reactors
 * - Reactor Overdrive 1.45x FE/t, 1.25x XP, 1.6x fuel, 2.0x coolant &amp; safe 200-tick cooldown math
 * - Overdrive GUI button non-overlap geometry &amp; 15-tick ritual buildup curve
 * - Strict Jade optional integration isolation (zero snownee.jade imports outside compat.jade)
 */
class Phase114ExperiencePolishTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/industrialcore");
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ADV_DIR = RES.resolve("data/industrialcore/advancement/aegisworks");

    private static JsonObject readLang(String file) throws IOException {
        return JsonParser.parseString(
                Files.readString(RES.resolve("assets/industrialcore/lang/" + file))).getAsJsonObject();
    }

    @Test
    void allAdvancementsHaveValidJsonConnectedParentDagIconsAndBilingualText() throws IOException {
        assertEquals(29, AegisAdvancements.ALL_PATHS.size(), "AegisWorks must define exactly 29 advancements");
        List<Path> jsonFiles;
        try (Stream<Path> s = Files.list(ADV_DIR)) {
            jsonFiles = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(29, jsonFiles.size(), "Advancement directory must contain exactly 29 JSON files");

        JsonObject ja = readLang("ja_jp.json");
        JsonObject en = readLang("en_us.json");

        Map<String, String> parentOf = new HashMap<>();
        for (String path : AegisAdvancements.ALL_PATHS) {
            String shortId = path.substring("aegisworks/".length());
            Path file = ADV_DIR.resolve(shortId + ".json");
            assertTrue(Files.isRegularFile(file), "Missing advancement JSON: " + file);

            JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject display = obj.getAsJsonObject("display");
            assertNotNull(display, shortId + " missing display object");

            String iconId = display.getAsJsonObject("icon").get("id").getAsString();
            assertTrue(iconId.startsWith("industrialcore:"), shortId + " icon must be an AegisWorks item: " + iconId);
            String iconPath = iconId.substring("industrialcore:".length());
            assertTrue(Files.isRegularFile(RES.resolve("assets/industrialcore/models/item/" + iconPath + ".json")),
                    shortId + " icon model missing: " + iconPath);

            String titleKey = display.getAsJsonObject("title").get("translate").getAsString();
            String descKey = display.getAsJsonObject("description").get("translate").getAsString();
            assertTrue(ja.has(titleKey) && !ja.get(titleKey).getAsString().isBlank(), "Missing ja title: " + titleKey);
            assertTrue(en.has(titleKey) && !en.get(titleKey).getAsString().isBlank(), "Missing en title: " + titleKey);
            assertTrue(ja.has(descKey) && !ja.get(descKey).getAsString().isBlank(), "Missing ja desc: " + descKey);
            assertTrue(en.has(descKey) && !en.get(descKey).getAsString().isBlank(), "Missing en desc: " + descKey);

            JsonObject criteria = obj.getAsJsonObject("criteria");
            assertNotNull(criteria, shortId + " missing criteria");
            assertFalse(criteria.isEmpty(), shortId + " has empty criteria");

            String fullId = "industrialcore:" + path;
            if (AegisAdvancements.ROOT.equals(path)) {
                assertFalse(obj.has("parent"), "root advancement must not have parent");
                assertTrue(display.has("background"), "root advancement must define tab background");
                parentOf.put(fullId, null);
            } else {
                assertTrue(obj.has("parent"), shortId + " must declare parent");
                parentOf.put(fullId, obj.get("parent").getAsString());
            }
        }

        // Verify every single advancement reaches industrialcore:aegisworks/root without cycles
        for (String node : parentOf.keySet()) {
            Set<String> seen = new HashSet<>();
            String cur = node;
            while (cur != null) {
                assertTrue(seen.add(cur), "Cycle detected in advancement tree at " + cur);
                assertTrue(parentOf.containsKey(cur), "Dangling parent reference: " + cur);
                cur = parentOf.get(cur);
            }
        }
    }

    @Test
    void stopReasonClassifierCoversAllMachineGeneratorAndReactorHalts() throws IOException {
        JsonObject ja = readLang("ja_jp.json");
        JsonObject en = readLang("en_us.json");
        for (MachineStopReason r : MachineStopReason.values()) {
            assertTrue(ja.has(r.translationKey()), "Missing ja stop reason: " + r.translationKey());
            assertTrue(en.has(r.translationKey()), "Missing en stop reason: " + r.translationKey());
        }

        assertEquals(MachineStopReason.REDSTONE_DISABLED,
                MachineStopReason.fromMachineStatus(MachineStatus.WORKING, true));
        assertEquals(MachineStopReason.NO_ENERGY,
                MachineStopReason.fromMachineStatus(MachineStatus.NO_ENERGY, false));
        assertEquals(MachineStopReason.OUTPUT_FULL,
                MachineStopReason.fromMachineStatus(MachineStatus.OUTPUT_FULL, false));
        assertEquals(MachineStopReason.WAITING_INPUT,
                MachineStopReason.fromMachineStatus(MachineStatus.NO_INPUT, false));
        assertEquals(MachineStopReason.NO_RECIPE,
                MachineStopReason.fromMachineStatus(MachineStatus.NO_RECIPE, false));
        assertEquals(MachineStopReason.NONE,
                MachineStopReason.fromMachineStatus(MachineStatus.WORKING, false));

        // Nuclear coolant dry / cooldown / incomplete
        assertEquals(MachineStopReason.OVERDRIVE_COOLDOWN,
                MachineStopReason.fromGeneratorStatus(0, true, 1000, true));
        assertEquals(MachineStopReason.INCOMPLETE_STRUCTURE,
                MachineStopReason.fromGeneratorStatus(7, true, 1000, false));
        assertEquals(MachineStopReason.NO_COOLANT,
                MachineStopReason.fromGeneratorStatus(0, true, 0, false));
        assertEquals(MachineStopReason.NO_FUEL,
                MachineStopReason.fromGeneratorStatus(2, true, 5000, false));
        assertEquals(MachineStopReason.NONE,
                MachineStopReason.fromGeneratorStatus(0, true, 5000, false));

        // Singularity states
        assertEquals(MachineStopReason.OVERDRIVE_COOLDOWN,
                MachineStopReason.fromSingularityState(SingularityReactorLogic.STATE_COOLDOWN, false));
        assertEquals(MachineStopReason.INCOMPLETE_STRUCTURE,
                MachineStopReason.fromSingularityState(SingularityReactorLogic.STATE_INCOMPLETE, false));
        assertEquals(MachineStopReason.BUFFER_FULL,
                MachineStopReason.fromSingularityState(SingularityReactorLogic.STATE_FULL, false));
        assertEquals(MachineStopReason.NO_FUEL,
                MachineStopReason.fromSingularityState(SingularityReactorLogic.STATE_NO_FUEL, false));
        assertEquals(MachineStopReason.NONE,
                MachineStopReason.fromSingularityState(SingularityReactorLogic.STATE_GENERATING, false));
    }

    @Test
    void reactorOverdriveMultipliersEmergencyCooldownRitualAndLayoutsAreExact() {
        // 1.45x FE/t output, 0 during cooldown
        assertEquals(1_000L, ReactorOverdriveLogic.effectiveOutput(1_000L, false, 0));
        assertEquals(1_450L, ReactorOverdriveLogic.effectiveOutput(1_000L, true, 0));
        assertEquals(0L, ReactorOverdriveLogic.effectiveOutput(1_000L, true, 50));
        assertEquals(1_450, ReactorOverdriveLogic.effectiveIntOutput(1_000, true, 0));
        assertEquals(0, ReactorOverdriveLogic.effectiveIntOutput(1_000, true, 1));

        // 1.25x XP, 1.60x fuel burn, 2.00x coolant consumption (half interval)
        assertEquals(100L, ReactorOverdriveLogic.effectiveXp(100L, false));
        assertEquals(125L, ReactorOverdriveLogic.effectiveXp(100L, true));
        assertEquals(1_000, ReactorOverdriveLogic.burnProgressDelta(1_000, false));
        assertEquals(1_600, ReactorOverdriveLogic.burnProgressDelta(1_000, true));
        assertEquals(40, ReactorOverdriveLogic.coolantConsumeInterval(40, false));
        assertEquals(20, ReactorOverdriveLogic.coolantConsumeInterval(40, true));

        // Safe emergency shutdown triggers only when Overdrive + burning + depleted
        assertTrue(ReactorOverdriveLogic.shouldTriggerEmergencyShutdown(true, true, true));
        assertFalse(ReactorOverdriveLogic.shouldTriggerEmergencyShutdown(false, true, true));
        assertFalse(ReactorOverdriveLogic.shouldTriggerEmergencyShutdown(true, false, true));
        assertFalse(ReactorOverdriveLogic.shouldTriggerEmergencyShutdown(true, true, false));
        assertEquals(200, ReactorOverdriveLogic.EMERGENCY_COOLDOWN_TICKS);

        // 15-tick buildup ritual charge progress & rising pitch
        assertEquals(15, ReactorOverdriveLogic.RITUAL_CHARGE_TICKS);
        assertEquals(0.0F, ReactorOverdriveLogic.ritualChargeProgress(15), 1.0E-5F);
        assertEquals(1.0F, ReactorOverdriveLogic.ritualChargeProgress(0), 1.0E-5F);
        assertTrue(ReactorOverdriveLogic.ritualChargePitch(1) > ReactorOverdriveLogic.ritualChargePitch(15));
        assertTrue(ReactorOverdriveLogic.ringSpeedMultiplier(true) > ReactorOverdriveLogic.ringSpeedMultiplier(false));

        // Overdrive toggle buttons do not overlap existing controls
        Rect nucOd = GenerationLayout.NUCLEAR_OVERDRIVE_BTN;
        assertFalse(nucOd.intersects(GenerationLayout.NUCLEAR_GUIDE_BTN));
        assertFalse(nucOd.intersects(GenerationLayout.NUCLEAR_COOLANT_COLUMN));
        assertFalse(nucOd.intersects(GenerationLayout.ENERGY_COLUMN));
        assertFalse(nucOd.intersects(GenerationLayout.NUCLEAR_BURN_BAR));
        assertFalse(nucOd.intersects(GenerationLayout.FUEL_SLOT));

        Rect singOd = SingularityReactorLayout.OVERDRIVE_BUTTON;
        assertTrue(singOd.x() >= SingularityReactorLayout.TELEMETRY_PANEL.x()
                && singOd.right() <= SingularityReactorLayout.TELEMETRY_PANEL.right());
        assertFalse(singOd.intersects(SingularityReactorLayout.BURN_BAR));
        assertFalse(singOd.intersects(SingularityReactorLayout.GUIDE_BUTTON));
        assertFalse(singOd.intersects(SingularityReactorLayout.MULTIBLOCK_BAR));
    }

    @Test
    void jadePluginIsStrictlyIsolatedForZeroDependencySafety() throws IOException {
        Path jadeDir = MAIN_JAVA.resolve("compat/jade");
        Path pluginFile = jadeDir.resolve("AegisWorksJadePlugin.java");
        assertTrue(Files.isRegularFile(pluginFile), "Missing AegisWorksJadePlugin.java");
        String pluginSrc = Files.readString(pluginFile);
        assertTrue(pluginSrc.contains("@WailaPlugin"), "AegisWorksJadePlugin must be annotated with @WailaPlugin");
        assertTrue(pluginSrc.contains("implements IWailaPlugin"), "AegisWorksJadePlugin must implement IWailaPlugin");

        // Ensure NO core file outside compat/jade ever imports snownee.jade.*
        try (Stream<Path> walk = Files.walk(MAIN_JAVA)) {
            List<Path> offenders = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(jadeDir))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("snownee.jade.");
                        } catch (IOException e) {
                            return true;
                        }
                    })
                    .toList();
            assertTrue(offenders.isEmpty(), "Core classes must never reference snownee.jade.*: " + offenders);
        }
    }
}
