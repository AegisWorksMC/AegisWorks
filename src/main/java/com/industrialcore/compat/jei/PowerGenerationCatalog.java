package com.industrialcore.compat.jei;

import com.industrialcore.endgame.SingularityReactorLogic;
import com.industrialcore.energy.GenerationBalance;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.RecoveryKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 105.5: Pure-Java catalog of every IndustrialCore power generator, its fuels (or
 * environmental/recovery condition), rated FE/t, burn duration, and total FE output.
 *
 * <p>Zero Minecraft or JEI imports so unit tests verify every generator, every positive FE/t
 * &amp; burn-time math invariant, and every UI slot rectangle on a plain JVM.</p>
 */
public final class PowerGenerationCatalog {
    /** JEI category UID for Power Generation. */
    public static final String CATEGORY_UID = "industrialcore:power_generation";

    /** Layout constants for the unified dark-slate Power Generation JEI card (176x76). */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 76;
    public static final int SLOT_SIZE = 18;
    public static final int GEN_SLOT_X = 10;
    public static final int GEN_SLOT_Y = 24;
    public static final int FUEL_SLOT_X = 42;
    public static final int FUEL_SLOT_Y = 24;
    public static final int ARROW_X = 66;
    public static final int ARROW_Y = 27;
    public static final int ARROW_W = 24;
    public static final int ARROW_H = 10;
    public static final int GAUGE_X = 96;
    public static final int GAUGE_Y = 23;
    public static final int GAUGE_W = 72;
    public static final int GAUGE_H = 18;
    public static final int FOOTER_Y1 = 49;
    public static final int FOOTER_Y2 = 61;

    /** Simple axis-aligned rectangle for layout non-overlap unit testing. */
    public record Rect(int x, int y, int w, int h) {
        public boolean overlaps(Rect other) {
            return this.x < other.x + other.w && other.x < this.x + this.w
                    && this.y < other.y + other.h && other.y < this.y + this.h;
        }
    }

    /** One JEI recipe entry in the Power Generation catalog. */
    public record Entry(
            String generatorId,
            String generatorNameKey,
            String fuelItemId,
            long fePerTick,
            int burnTicks,
            long totalFe,
            String conditionKey,
            boolean fuelLess,
            boolean multiblock
    ) {
        public double burnSeconds() {
            return burnTicks / 20.0D;
        }
    }

    private static final List<Entry> ENTRIES = buildEntries();
    private static final List<String> CATALYSTS = buildCatalysts();

    private PowerGenerationCatalog() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** Every power-generating machine registered as a JEI catalyst for this category. */
    public static List<String> catalysts() {
        return CATALYSTS;
    }

    /** Non-overlapping UI bounds of the Power Generation card (frame-inclusive). */
    public static List<Rect> layoutRects() {
        return List.of(
                new Rect(4, 3, WIDTH - 8, 14),                           // Header title + FE/t badge
                new Rect(GEN_SLOT_X - 1, GEN_SLOT_Y - 1, SLOT_SIZE, SLOT_SIZE),   // Generator slot frame
                new Rect(FUEL_SLOT_X - 1, FUEL_SLOT_Y - 1, SLOT_SIZE, SLOT_SIZE), // Fuel slot frame
                new Rect(ARROW_X, ARROW_Y, ARROW_W, ARROW_H),            // Energy flow arrow
                new Rect(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H),            // FE bar gauge
                new Rect(8, FOOTER_Y1, WIDTH - 16, 10),                  // Burn/total telemetry line
                new Rect(8, FOOTER_Y2, WIDTH - 16, 10)                   // Condition/multiblock line
        );
    }

    private static List<Entry> buildEntries() {
        List<Entry> list = new ArrayList<>();

        // 1. Thermal Generator (90 FE/t) — solid & crafted fuels
        addFuelEntry(list, GenerationKind.THERMAL, "minecraft:coal", 1600 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "minecraft:charcoal", 1600 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "minecraft:coal_block", 16000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "minecraft:blaze_rod", 2400 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "minecraft:lava_bucket", 20000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "industrialcore:fuel_can", GenerationBalance.FUEL_CAN_FE,
                "jei.industrialcore.gen.cond.solid_fuel", false);
        addFuelEntry(list, GenerationKind.THERMAL, "industrialcore:fuel_rod", GenerationBalance.FUEL_ROD_FE,
                "jei.industrialcore.gen.cond.solid_fuel", false);

        // 2. Combustion Generator (160 FE/t) — high-density fuels
        addFuelEntry(list, GenerationKind.COMBUSTION, "industrialcore:fuel_can", GenerationBalance.FUEL_CAN_FE,
                "jei.industrialcore.gen.cond.combustion", false);
        addFuelEntry(list, GenerationKind.COMBUSTION, "minecraft:lava_bucket", 20000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.combustion", false);
        addFuelEntry(list, GenerationKind.COMBUSTION, "minecraft:coal_block", 16000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.combustion", false);
        addFuelEntry(list, GenerationKind.COMBUSTION, "minecraft:coal", 1600 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.combustion", false);

        // 3. Environmental / Fuel-less Generators (Hydro, Wind, Solar, Geothermal)
        list.add(new Entry(
                "industrialcore:" + GenerationKind.HYDRO.id(),
                GenerationKind.HYDRO.translationKey(),
                "",
                GenerationKind.HYDRO.output(),
                0,
                0L,
                "jei.industrialcore.gen.cond.hydro",
                true,
                false));
        list.add(new Entry(
                "industrialcore:" + GenerationKind.WIND.id(),
                GenerationKind.WIND.translationKey(),
                "",
                GenerationBalance.WIND_MAX_OUTPUT,
                0,
                0L,
                "jei.industrialcore.gen.cond.wind",
                true,
                false));
        list.add(new Entry(
                "industrialcore:" + GenerationKind.SOLAR.id(),
                GenerationKind.SOLAR.translationKey(),
                "",
                GenerationKind.SOLAR.output(),
                0,
                0L,
                "jei.industrialcore.gen.cond.solar",
                true,
                false));
        list.add(new Entry(
                "industrialcore:" + GenerationKind.GEOTHERMAL.id(),
                GenerationKind.GEOTHERMAL.translationKey(),
                "",
                GenerationKind.GEOTHERMAL.output(),
                0,
                0L,
                "jei.industrialcore.gen.cond.geothermal",
                true,
                false));

        // 4. Nuclear Generator Multiblock (min 3x3x3 base = 400 FE/t, scales up to 5x5x5 + core cells, water-cooled)
        addFuelEntry(list, GenerationKind.NUCLEAR, "industrialcore:uranium_fuel_rod", GenerationBalance.URANIUM_FUEL_ROD_FE,
                "jei.industrialcore.gen.cond.nuclear_mb", true);
        addFuelEntry(list, GenerationKind.NUCLEAR, "industrialcore:fuel_rod", GenerationBalance.FUEL_ROD_FE,
                "jei.industrialcore.gen.cond.nuclear_mb", true);
        addFuelEntry(list, GenerationKind.NUCLEAR, "industrialcore:fuel_can", GenerationBalance.FUEL_CAN_FE,
                "jei.industrialcore.gen.cond.nuclear_mb", true);
        addFuelEntry(list, GenerationKind.NUCLEAR, "minecraft:coal_block", 16000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.nuclear_mb", true);
        addFuelEntry(list, GenerationKind.NUCLEAR, "minecraft:lava_bucket", 20000 * GenerationBalance.FUEL_FE_PER_BURN_TICK,
                "jei.industrialcore.gen.cond.nuclear_mb", true);

        // 5. Singularity Reactor Multiblock (min 3x3x3 base = 100,000 FE/t, scales up to 7x7x7 + cores + motors)
        String[] singularityFuels = {
                "industrialcore:synthetic_ore_ultimate",
                "minecraft:nether_star",
                "industrialcore:phase_core",
                "minecraft:netherite_ingot",
                "industrialcore:aether_crystal",
                "industrialcore:quantum_shard",
                "industrialcore:nebula_crystal",
                "industrialcore:fuel_rod",
                "minecraft:diamond_block",
                "industrialcore:adamantite_ingot",
                "industrialcore:fuel_can"
        };
        for (String fuelId : singularityFuels) {
            int ticks = SingularityReactorLogic.baseFuelTicks(fuelId);
            if (ticks > 0) {
                long fePerTick = SingularityReactorLogic.BASE_FE_PER_TICK;
                list.add(new Entry(
                        "industrialcore:singularity_reactor",
                        "block.industrialcore.singularity_reactor",
                        fuelId,
                        fePerTick,
                        ticks,
                        fePerTick * ticks,
                        "jei.industrialcore.gen.cond.singularity_mb",
                        false,
                        true));
            }
        }

        // 6. Energy Recovery Devices (Pressure, Heat, Regenerative Braking)
        list.add(new Entry(
                "industrialcore:" + RecoveryKind.PRESSURE.id(),
                RecoveryKind.PRESSURE.translationKey(),
                "",
                RecoveryKind.PRESSURE.maxOutput(),
                0,
                0L,
                "jei.industrialcore.gen.cond.pressure_recovery",
                true,
                false));
        list.add(new Entry(
                "industrialcore:" + RecoveryKind.HEAT.id(),
                RecoveryKind.HEAT.translationKey(),
                "",
                RecoveryKind.HEAT.maxOutput(),
                0,
                0L,
                "jei.industrialcore.gen.cond.heat_recovery",
                true,
                false));
        list.add(new Entry(
                "industrialcore:" + RecoveryKind.REGEN.id(),
                RecoveryKind.REGEN.translationKey(),
                "",
                RecoveryKind.REGEN.maxOutput(),
                0,
                0L,
                "jei.industrialcore.gen.cond.regen_generator",
                true,
                false));

        return Collections.unmodifiableList(list);
    }

    private static void addFuelEntry(List<Entry> list, GenerationKind kind, String fuelId,
                                     int contentFe, String conditionKey, boolean multiblock) {
        int ticks = GenerationBalance.burnTicksFor(contentFe, kind.output());
        long total = (long) ticks * kind.output();
        list.add(new Entry(
                "industrialcore:" + kind.id(),
                kind.translationKey(),
                fuelId,
                kind.output(),
                ticks,
                total,
                conditionKey,
                false,
                multiblock));
    }

    private static List<String> buildCatalysts() {
        Set<String> ids = new LinkedHashSet<>();
        for (Entry e : ENTRIES) {
            ids.add(e.generatorId());
        }
        return List.copyOf(ids);
    }
}
