package com.industrialcore.compat.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 105.5 unit tests for the JEI Power Generation catalog and unified dark-slate JEI layouts:
 * generator completeness, positive FE/t &amp; burn-time math consistency, translation symmetry,
 * and zero UI slot/arrow/text overlaps across every JEI category.
 */
class JeiPowerGenerationTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void everyPowerGeneratorIsCoveredWithValidPositiveMath() {
        List<PowerGenerationCatalog.Entry> entries = PowerGenerationCatalog.entries();
        assertTrue(entries.size() >= 25, "Expected comprehensive generator & fuel catalog");

        Set<String> expectedGenerators = Set.of(
                "industrialcore:thermal_generator",
                "industrialcore:combustion_generator",
                "industrialcore:hydro_generator",
                "industrialcore:wind_generator",
                "industrialcore:solar_generator",
                "industrialcore:geothermal_generator",
                "industrialcore:nuclear_generator",
                "industrialcore:singularity_reactor",
                "industrialcore:pressure_recovery",
                "industrialcore:heat_recovery",
                "industrialcore:regen_generator"
        );
        Set<String> actualCatalysts = new TreeSet<>(PowerGenerationCatalog.catalysts());
        assertEquals(expectedGenerators, actualCatalysts,
                "All 11 IndustrialCore generators & recovery devices must be JEI catalysts");

        for (PowerGenerationCatalog.Entry e : entries) {
            assertTrue(e.fePerTick() > 0, e.generatorId() + " FE/t must be positive");
            if (e.fuelLess()) {
                assertEquals(0, e.burnTicks(), e.generatorId() + " fuel-less entry should have 0 burnTicks");
                assertTrue(e.fuelItemId() == null || e.fuelItemId().isEmpty());
            } else {
                assertFalse(e.fuelItemId() == null || e.fuelItemId().isEmpty(),
                        e.generatorId() + " fuel entry missing fuelItemId");
                assertTrue(e.burnTicks() > 0, e.generatorId() + "/" + e.fuelItemId() + " burnTicks must be > 0");
                assertEquals(e.fePerTick() * e.burnTicks(), e.totalFe(),
                        e.generatorId() + "/" + e.fuelItemId() + " totalFe must equal fePerTick * burnTicks");
            }
            boolean isMb = e.generatorId().equals("industrialcore:nuclear_generator")
                    || e.generatorId().equals("industrialcore:singularity_reactor");
            assertEquals(isMb, e.multiblock(), e.generatorId() + " multiblock flag mismatch");
        }
    }

    @Test
    void powerGenerationAndAllMachineJeiLayoutsHaveZeroOverlapsAndFitInsidePanel() {
        assertNonOverlappingInside(PowerGenerationCatalog.layoutRects(),
                PowerGenerationCatalog.WIDTH, PowerGenerationCatalog.HEIGHT, "power_generation");

        for (JeiCoverage.Layout layout : JeiCoverage.Layout.values()) {
            assertNonOverlappingInside(layout.allRects(), layout.width, layout.height, layout.name());
        }
    }

    @Test
    void allPowerGenerationTranslationsExistInJapaneseAndEnglish() throws IOException {
        JsonObject ja = JsonParser.parseString(Files.readString(LANG_DIR.resolve("ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(LANG_DIR.resolve("en_us.json"))).getAsJsonObject();

        for (String key : List.of(
                "jei.industrialcore.power_generation",
                "jei.industrialcore.fuel_powered",
                "jei.industrialcore.gen.no_fuel_short",
                "jei.industrialcore.gen.burn_and_total",
                "jei.industrialcore.gen.continuous")) {
            assertTrue(ja.has(key), "ja_jp missing " + key);
            assertTrue(en.has(key), "en_us missing " + key);
        }
        for (PowerGenerationCatalog.Entry e : PowerGenerationCatalog.entries()) {
            assertTrue(ja.has(e.generatorNameKey()), "ja_jp missing " + e.generatorNameKey());
            assertTrue(en.has(e.generatorNameKey()), "en_us missing " + e.generatorNameKey());
            assertTrue(ja.has(e.conditionKey()), "ja_jp missing " + e.conditionKey());
            assertTrue(en.has(e.conditionKey()), "en_us missing " + e.conditionKey());
        }
    }

    private static void assertNonOverlappingInside(List<PowerGenerationCatalog.Rect> rects,
                                                   int w, int h, String label) {
        for (int i = 0; i < rects.size(); i++) {
            PowerGenerationCatalog.Rect a = rects.get(i);
            assertTrue(a.x() >= 2 && a.y() >= 2 && a.x() + a.w() <= w - 2 && a.y() + a.h() <= h - 2,
                    label + " rect[" + i + "] escapes panel bounds: " + a + " panel=" + w + "x" + h);
            for (int j = i + 1; j < rects.size(); j++) {
                PowerGenerationCatalog.Rect b = rects.get(j);
                assertFalse(a.overlaps(b), label + " rect[" + i + "] " + a + " overlaps rect[" + j + "] " + b);
            }
        }
    }
}
