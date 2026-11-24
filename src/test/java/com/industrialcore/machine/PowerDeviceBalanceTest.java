package com.industrialcore.machine;

import com.industrialcore.energy.GenerationBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 41: unit checks of the power device table and the fuel arithmetic.
 *
 * <p>The nuclear rule is the one that has to hold in both operating modes: a fuel rod holds
 * {@link GenerationBalance#FUEL_ROD_FE} FE, {@code 90%} of it becomes direct FE, and that total
 * must come out whether the reactor runs at 400 FE/t (cooled) or 200 FE/t (dry). The block
 * entity implements it by advancing the burn clock per full output of produced FE, so the two
 * tick counts below are what the GUI shows in each mode.</p>
 */
class PowerDeviceBalanceTest {

    @Test
    void geothermalMatchesThePhase41Specification() {
        GenerationKind geo = GenerationKind.GEOTHERMAL;
        assertEquals("geothermal_generator", geo.id());
        assertEquals(90, geo.output());
        assertEquals(100_000, geo.capacity());
        assertEquals(256, geo.pushPerTick());
        assertFalse(geo.hasFuel());
        assertTrue(geo.needsLava());
        assertFalse(geo.needsWater());
        assertEquals(GenerationKind.Byproduct.NONE, geo.byproduct());
    }

    @Test
    void geothermalPushCoversItsRatedOutput() {
        GenerationKind geo = GenerationKind.GEOTHERMAL;
        assertTrue(geo.pushPerTick() >= geo.output(),
                "the geothermal generator must be able to push its whole rated output");
    }

    @Test
    void nuclearStillNeedsWaterAndPushesItsFullRating() {
        GenerationKind nuclear = GenerationKind.NUCLEAR;
        assertEquals(400, nuclear.output());
        assertTrue(nuclear.needsWater());
        assertFalse(nuclear.needsLava());
        assertEquals(900, nuclear.pushPerTick());
        assertTrue(nuclear.pushPerTick() >= nuclear.output());
    }

    @Test
    void oneFuelRodYieldsTheSameTotalWithAndWithoutCoolingWater() {
        int content = GenerationBalance.FUEL_ROD_FE;
        int direct = content * GenerationBalance.DIRECT_PERCENT / 100;
        assertEquals(72_000, direct);

        int cooledTicks = GenerationBalance.burnTicksFor(content, GenerationKind.NUCLEAR.output());
        assertEquals(180, cooledTicks);
        assertEquals(direct, cooledTicks * 400);

        // Dry operation: the same content at half the rate needs twice the ticks.
        int dryRate = GenerationKind.NUCLEAR.output() / 2;
        int dryTicks = direct / dryRate;
        assertEquals(360, dryTicks);
        assertEquals(direct, dryTicks * dryRate);
        assertEquals(cooledTicks * 2, dryTicks);
    }

    @Test
    void byproductStaysTheSameShareInBothModes() {
        assertEquals(8_000L, GenerationBalance.byproductFor(GenerationBalance.FUEL_ROD_FE));
        assertEquals(6_400L, GenerationBalance.byproductFor(64_000));
    }

    @Test
    void everyPowerDevicePushesAtLeastItsRatedOutput() {
        for (GenerationKind kind : GenerationKind.values()) {
            if (kind == GenerationKind.WIND) {
                // The wind turbine derives its output from the air above it; use the maximum.
                assertTrue(kind.pushPerTick() >= GenerationBalance.WIND_MAX_OUTPUT,
                        kind.id() + " cannot push its maximum wind output");
                continue;
            }
            assertTrue(kind.pushPerTick() >= kind.output(),
                    kind.id() + " pushes " + kind.pushPerTick()
                            + " FE/t but generates " + kind.output() + " FE/t");
        }
    }

    @Test
    void fuelDevicesAndConditionDevicesAreDisjoint() {
        for (GenerationKind kind : GenerationKind.values()) {
            if (kind.hasFuel()) {
                assertFalse(kind.needsLava(), kind.id() + " is both fuelled and geothermal");
            }
        }
        assertTrue(GenerationKind.GEOTHERMAL.needsLava());
        assertTrue(GenerationKind.HYDRO.needsWater());
        assertFalse(GenerationKind.GEOTHERMAL.needsWater());
    }
}
