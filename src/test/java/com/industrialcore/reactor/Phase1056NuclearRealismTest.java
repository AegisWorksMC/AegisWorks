package com.industrialcore.reactor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.compat.jei.PowerGenerationCatalog;
import com.industrialcore.energy.GenerationBalance;
import com.industrialcore.life.FluidPipeLogic;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.menu.GenerationLayout;
import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 105.6 unit tests for Generator Realism Enhancement:
 * <ul>
 *   <li>Uranium ore worldgen, Fortune/Silk-Touch loot tables, processing recipes, and Uranium Fuel Rod energy math.</li>
 *   <li>Connected Fluid Pipe transfer cap and fair multi-sink fluid distribution.</li>
 *   <li>Nuclear multiblock hybrid water-cooling tank, comfortable 1-bucket runtime (~2.8 min),
 *       non-destructive 25% dry safety throttle, and non-overlapping GUI coolant layout.</li>
 * </ul>
 */
class Phase1056NuclearRealismTest {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/industrialcore");

    @Test
    void nuclearCoolantLogicAppliesFullOutputWhenCooledAnd25PercentSafetyThrottleWhenDry() {
        assertTrue(NuclearCoolantLogic.isCooled(1));
        assertTrue(NuclearCoolantLogic.isCooled(16_000));
        assertFalse(NuclearCoolantLogic.isCooled(0));

        // 100% rated output when cooled
        assertEquals(400, NuclearCoolantLogic.effectiveOutput(400, 1_000));
        assertEquals(1_600, NuclearCoolantLogic.effectiveOutput(1_600, 1));

        // 25% safety throttle when dry (never 0 when base > 0, never explodes)
        assertEquals(100, NuclearCoolantLogic.effectiveOutput(400, 0));
        assertEquals(400, NuclearCoolantLogic.effectiveOutput(1_600, 0));
        assertEquals(1, NuclearCoolantLogic.effectiveOutput(2, 0));
        assertEquals(0, NuclearCoolantLogic.effectiveOutput(0, 1_000));
    }

    @Test
    void waterBucketRefillAndComfortableTwoToThreeMinuteConsumptionDuration() {
        // 1 bucket (1,000 mB) lasts ~2.8 minutes (between 2 and 3 minutes = 2,400..3,600 ticks)
        int ticks = NuclearCoolantLogic.ticksPerBucket();
        assertTrue(ticks >= 2_400 && ticks <= 3_600,
                "Expected 1 bucket to last 2..3 minutes (2400..3600 ticks), got " + ticks);

        assertTrue(NuclearCoolantLogic.canAcceptWaterBucket(0));
        assertTrue(NuclearCoolantLogic.canAcceptWaterBucket(15_000));
        assertFalse(NuclearCoolantLogic.canAcceptWaterBucket(15_001));
        assertFalse(NuclearCoolantLogic.canAcceptWaterBucket(16_000));

        assertEquals(1_000, NuclearCoolantLogic.afterWaterBucket(0));
        assertEquals(16_000, NuclearCoolantLogic.afterWaterBucket(15_500));
        assertEquals(994, NuclearCoolantLogic.afterConsumePulse(1_000));
        assertEquals(0, NuclearCoolantLogic.afterConsumePulse(3));
        assertEquals(0.5F, NuclearCoolantLogic.fillRatio(8_000), 1e-6F);
    }

    @Test
    void fluidPipeLogicCapsTransferAndDistributesFairlyWithoutLoss() {
        assertEquals(0, FluidPipeLogic.clampTransfer(0));
        assertEquals(0, FluidPipeLogic.clampTransfer(-100));
        assertEquals(250, FluidPipeLogic.clampTransfer(250));
        assertEquals(FluidPipeLogic.MAX_TRANSFER_MB_PER_TICK, FluidPipeLogic.clampTransfer(1_000));

        // Equal split across 2 sinks
        assertArrayEquals(new int[]{250, 250},
                FluidPipeLogic.distributeFluid(500, new int[]{1_000, 1_000}));
        // Overflow from smaller sink spills cleanly to larger sink with zero loss
        assertArrayEquals(new int[]{100, 400},
                FluidPipeLogic.distributeFluid(500, new int[]{100, 1_000}));
        // Total demand less than supply
        assertArrayEquals(new int[]{120, 80},
                FluidPipeLogic.distributeFluid(500, new int[]{120, 80}));
    }

    @Test
    void uraniumFuelRodIsHighOutputFlagshipFuelInBalanceAndJeiCatalog() {
        assertEquals(800_000, GenerationBalance.URANIUM_FUEL_ROD_FE);
        int baseTicks = GenerationBalance.burnTicksFor(
                GenerationBalance.URANIUM_FUEL_ROD_FE, GenerationKind.NUCLEAR.output());
        assertEquals(1_800, baseTicks, "800k FE * 90% / 400 FE/t = 1800 ticks (90s)");

        List<PowerGenerationCatalog.Entry> nuclearEntries = PowerGenerationCatalog.entries().stream()
                .filter(e -> e.generatorId().equals("industrialcore:nuclear_generator"))
                .toList();
        assertFalse(nuclearEntries.isEmpty());
        assertEquals("industrialcore:uranium_fuel_rod", nuclearEntries.get(0).fuelItemId(),
                "Uranium Fuel Rod must be the #1 featured fuel for nuclear_generator in JEI");
        assertEquals(1_800, nuclearEntries.get(0).burnTicks());
    }

    @Test
    void nuclearGuiCoolantLayoutElementsFitPanelWithZeroOverlap() {
        List<Rect> rects = List.of(
                GenerationLayout.NUCLEAR_BUCKET_SLOT,
                GenerationLayout.FUEL_SLOT,
                GenerationLayout.NUCLEAR_BURN_BAR,
                GenerationLayout.NUCLEAR_GUIDE_BTN,
                GenerationLayout.NUCLEAR_COOLANT_COLUMN,
                GenerationLayout.ENERGY_COLUMN
        );
        for (int i = 0; i < rects.size(); i++) {
            Rect a = rects.get(i);
            assertTrue(a.x() >= 8 && a.y() >= 56
                            && a.x() + a.width() <= GenerationLayout.WIDTH - 8
                            && a.y() + a.height() <= GenerationLayout.BODY_BOTTOM - 4,
                    "Nuclear layout rect out of body bounds: " + a);
            for (int j = i + 1; j < rects.size(); j++) {
                Rect b = rects.get(j);
                boolean overlaps = a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                        && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
                assertFalse(overlaps, "Nuclear layout rects overlap: " + a + " vs " + b);
            }
        }
    }

    @Test
    void uraniumWorldgenLootTablesAndProcessingChainRecipesExistAndAreValidJson() throws IOException {
        JsonObject biomeMod = JsonParser.parseString(
                Files.readString(DATA_ROOT.resolve("neoforge/biome_modifier/uranium_ore.json"))).getAsJsonObject();
        assertTrue(biomeMod.getAsJsonArray("features").toString().contains("industrialcore:uranium_ore"));

        assertTrue(Files.exists(DATA_ROOT.resolve("worldgen/configured_feature/uranium_ore.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("worldgen/placed_feature/uranium_ore.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("loot_table/blocks/uranium_ore.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("loot_table/blocks/deepslate_uranium_ore.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("loot_table/blocks/fluid_pipe.json")));

        for (String recipe : List.of(
                "uranium_crush_raw", "crush_uranium_ore", "crush_deepslate_uranium_ore",
                "smelt_raw_uranium", "blast_raw_uranium", "smelt_uranium_dust", "blast_uranium_dust",
                "uranium_fuel_rod", "assemble_uranium_fuel_rod", "fluid_pipe")) {
            Path p = DATA_ROOT.resolve("recipe/" + recipe + ".json");
            assertTrue(Files.exists(p), "Missing recipe " + recipe);
            JsonObject obj = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            assertTrue(obj.has("type"), recipe + " missing type");
            assertTrue(obj.has("result"), recipe + " missing result");
        }
    }
}
