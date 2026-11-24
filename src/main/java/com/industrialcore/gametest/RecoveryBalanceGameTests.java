package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 34 audit: the four recovery devices and the generator/buffer pairings, measured as
 * energy balances instead of status checks.
 *
 * <p>Every test drives ticks manually (the {@link PowerGridGameTests} technique), samples the
 * pools and buffers immediately around one device tick, and requires the exact conversion
 * arithmetic: exhaust/heat units drained times 85%, regen credits times 5%, flywheel storage
 * times 95%. A device can therefore never invent FE, drain a pool twice, or credit a spend the
 * machine never made - each of those would break an equality asserted here.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecoveryBalanceGameTests {
    private static final int Y = 6;
    private static final BlockPos CENTER = new BlockPos(2, Y, 2);

    private RecoveryBalanceGameTests() {
    }

    /** Flywheel keeps exactly 95% of what it accepts (200 FE/t) and bursts it out without gain. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void flywheelStoresNinetyFivePercentAndBurstsWithoutGain(GameTestHelper helper) {
        RecoveryBlockEntity flywheel = placeRecovery(helper, CENTER, ModBlocks.FLYWHEEL.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(3, Y, 2));

        int simulated = flywheel.getEnergyStorage().receiveEnergy(10_000, true);
        helper.assertTrue(simulated == 200,
                "the flywheel simulated acceptance of " + simulated
                        + " FE instead of its 200 FE/t cap");

        int acceptedTotal = 0;
        for (int index = 0; index < 2_000; index++) {
            acceptedTotal += flywheel.getEnergyStorage().receiveEnergy(10_000, false);
        }
        int stored = flywheel.getEnergyStorage().getEnergyStored();
        helper.assertTrue(stored <= 300_000,
                "the flywheel stores " + stored + " FE, above its 300,000 FE capacity");
        helper.assertTrue(stored >= 299_900,
                "2,000 offers of 10,000 FE left only " + stored + " FE stored");
        helper.assertTrue((long) stored * 100L <= (long) acceptedTotal * 95L,
                "stored " + stored + " FE is more than 95% of the accepted " + acceptedTotal + " FE");

        int bufferBefore = buffer.getEnergyStorage().getEnergyStored();
        int flywheelBefore = stored;
        int maxTickGain = 0;
        for (int index = 0; index < 1_000; index++) {
            int before = buffer.getEnergyStorage().getEnergyStored();
            tickDevice(helper, flywheel, 1);
            int gain = buffer.getEnergyStorage().getEnergyStored() - before;
            helper.assertTrue(gain <= 512,
                    "the buffer gained " + gain + " FE in one tick, above its 512 FE/t limit");
            helper.assertTrue(gain <= 2_048,
                    "the flywheel pushed " + gain + " FE in one tick, above its 2,048 FE/t burst");
            maxTickGain = Math.max(maxTickGain, gain);
        }
        int bufferGain = buffer.getEnergyStorage().getEnergyStored() - bufferBefore;
        helper.assertTrue(bufferGain == 250_000,
                "the buffer gained " + bufferGain + " FE instead of filling exactly to 250,000 FE");
        helper.assertTrue(flywheel.getEnergyStorage().getEnergyStored() == flywheelBefore - bufferGain,
                "flywheel " + flywheel.getEnergyStorage().getEnergyStored() + " + buffer gain "
                        + bufferGain + " does not equal the stored " + flywheelBefore);
        System.out.println("[Phase34-audit] flywheel: accepted=" + acceptedTotal
                + " stored=" + stored + " pushed=" + bufferGain + " maxTickGain=" + maxTickGain);
        helper.succeed();
    }

    /**
     * Pressure recovery converts exhaust units taken from an adjacent thermal generator at
     * exactly 85% per tick, never from a heat source, and never without a source.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void pressureRecoveryConvertsOnlyExhaustAtEightyFivePercent(GameTestHelper helper) {
        GenerationBlockEntity thermal =
                placeGeneration(helper, CENTER, ModBlocks.THERMAL_GENERATOR.get());
        RecoveryBlockEntity pressure = placeRecovery(helper, new BlockPos(3, Y, 2),
                ModBlocks.PRESSURE_RECOVERY.get());
        thermal.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));

        long totalFe = 0L;
        long totalUnits = 0L;
        for (int index = 0; index < 700; index++) {
            int feBefore = pressure.getEnergyStorage().getEnergyStored();
            tickDevice(helper, thermal, 1);
            long unitsBeforeDrain = thermal.getByproduct();
            tickDevice(helper, pressure, 1);
            long unitsAfter = thermal.getByproduct();
            int feAfter = pressure.getEnergyStorage().getEnergyStored();

            long drained = unitsBeforeDrain - unitsAfter;
            helper.assertTrue(drained >= 0,
                    "tick " + index + ": the pressure device made the exhaust pool grow");
            int gained = feAfter - feBefore;
            helper.assertTrue(gained == drained * 85 / 100,
                    "tick " + index + ": drained " + drained + " units but gained " + gained
                            + " FE instead of exactly 85%");
            helper.assertTrue(pressure.getRate() <= 120,
                    "GUI rate " + pressure.getRate() + " exceeds the 120 FE/t design cap");
            totalFe += gained;
            totalUnits += drained;
        }
        helper.assertTrue(totalUnits == 6_400,
                "one coal pooled " + totalUnits + " exhaust units instead of 6,400");
        helper.assertTrue(totalFe <= 6_400 * 85 / 100,
                "converted " + totalFe + " FE from 6,400 units, above the 5,440 FE limit");
        helper.assertTrue(totalFe >= 5_390,
                "converted only " + totalFe + " FE from a full coal exhaust pool");
        helper.assertTrue(thermal.getByproduct() == 0,
                "the exhaust pool still holds " + thermal.getByproduct() + " units");

        // Controls: no source, and a heat-only source, must produce nothing.
        RecoveryBlockEntity alone = placeRecovery(helper, new BlockPos(6, Y, 6),
                ModBlocks.PRESSURE_RECOVERY.get());
        tickDevice(helper, alone, 50);
        helper.assertTrue(alone.getEnergyStorage().getEnergyStored() == 0,
                "a pressure device without any source generated "
                        + alone.getEnergyStorage().getEnergyStored() + " FE");

        GenerationBlockEntity combustion = placeGeneration(helper, new BlockPos(4, Y, 4),
                ModBlocks.COMBUSTION_GENERATOR.get());
        RecoveryBlockEntity nextToHeat = placeRecovery(helper, new BlockPos(5, Y, 4),
                ModBlocks.PRESSURE_RECOVERY.get());
        combustion.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_CAN.get()));
        tickDevice(helper, combustion, 30);
        tickDevice(helper, nextToHeat, 30);
        helper.assertTrue(nextToHeat.getEnergyStorage().getEnergyStored() == 0,
                "pressure recovery generated " + nextToHeat.getEnergyStorage().getEnergyStored()
                        + " FE from a heat source instead of exhaust");
        helper.assertTrue(combustion.getByproduct() > 0,
                "the combustion generator pooled no heat at all");
        System.out.println("[Phase34-audit] pressure: units=" + totalUnits + " fe=" + totalFe);
        helper.succeed();
    }

    /** Heat recovery converts a combustion generator's heat pool at exactly 85% per tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void heatRecoveryConvertsCombustionHeatAtEightyFivePercent(GameTestHelper helper) {
        GenerationBlockEntity combustion =
                placeGeneration(helper, CENTER, ModBlocks.COMBUSTION_GENERATOR.get());
        RecoveryBlockEntity heat = placeRecovery(helper, new BlockPos(3, Y, 2),
                ModBlocks.HEAT_RECOVERY.get());
        combustion.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_CAN.get()));

        long totalFe = 0L;
        long totalUnits = 0L;
        for (int index = 0; index < 200; index++) {
            int feBefore = heat.getEnergyStorage().getEnergyStored();
            long heatPoolBefore = heat.getHeatPool();
            tickDevice(helper, combustion, 1);
            long unitsBeforeDrain = combustion.getByproduct();
            tickDevice(helper, heat, 1);
            long unitsAfter = combustion.getByproduct();
            long heatPoolAfter = heat.getHeatPool();

            long drained = unitsBeforeDrain - unitsAfter;
            long poolChange = heatPoolAfter - heatPoolBefore;
            helper.assertTrue(poolChange <= 0,
                    "tick " + index + ": the heat device pooled " + poolChange
                            + " units without a machine spending FE");
            long converted = drained + (-poolChange);
            int gained = heat.getEnergyStorage().getEnergyStored() - feBefore;
            helper.assertTrue(gained == converted * 85 / 100,
                    "tick " + index + ": converted " + converted + " heat units but gained " + gained
                            + " FE instead of exactly 85%");
            helper.assertTrue(heat.getRate() <= 100,
                    "GUI rate " + heat.getRate() + " exceeds the 100 FE/t design cap");
            totalFe += gained;
            totalUnits += converted;
        }
        helper.assertTrue(totalUnits == 16_000,
                "one fuel can pooled " + totalUnits + " heat units instead of 16,000");
        helper.assertTrue(totalFe <= 16_000 * 85 / 100,
                "converted " + totalFe + " FE, above the 13,600 FE limit of the fuel can heat");
        helper.assertTrue(combustion.getByproduct() == 0,
                "the combustion heat pool still holds " + combustion.getByproduct() + " units");
        helper.assertTrue(combustion.getEnergyStorage().getEnergyStored() > 0,
                "the combustion generator produced no direct FE at all");

        // Control: a heat device with no source at all must stay empty.
        RecoveryBlockEntity alone = placeRecovery(helper, new BlockPos(6, Y, 6),
                ModBlocks.HEAT_RECOVERY.get());
        tickDevice(helper, alone, 50);
        helper.assertTrue(alone.getEnergyStorage().getEnergyStored() == 0,
                "a heat device without any source generated "
                        + alone.getEnergyStorage().getEnergyStored() + " FE");
        System.out.println("[Phase34-audit] heat: units=" + totalUnits + " fe=" + totalFe);
        helper.succeed();
    }

    /**
     * Heat recovery credits exactly 15% of FE an adjacent machine already spent, converts it
     * at 85%, and never returns more than that share no matter how long the device runs.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void heatRecoveryCreditsMachineSpendAtFifteenPercent(GameTestHelper helper) {
        RecoveryBlockEntity heat = placeRecovery(helper, CENTER, ModBlocks.HEAT_RECOVERY.get());
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(3, Y, 2));
        crafter.getEnergyStorage().setEnergy(20_000);
        tickDevice(helper, heat, 1); // baseline observation of the machine's counter

        crafter.getEnergyStorage().consume(10_000); // a real spend, counter 0 -> 10,000

        long systemTotal = 0L;
        int maxTickGain = 0;
        for (int index = 0; index < 60; index++) {
            int before = crafter.getEnergyStorage().getEnergyStored()
                    + heat.getEnergyStorage().getEnergyStored();
            long poolBefore = heat.getHeatPool();
            tickDevice(helper, heat, 1);
            int after = crafter.getEnergyStorage().getEnergyStored()
                    + heat.getEnergyStorage().getEnergyStored();
            long poolAfter = heat.getHeatPool();

            int gained = after - before;
            helper.assertTrue(gained >= 0,
                    "tick " + index + ": the heat device lost FE while converting");
            maxTickGain = Math.max(maxTickGain, gained);
            systemTotal += gained;
        }
        helper.assertTrue(heat.getHeatPool() == 0,
                "the heat pool still holds " + heat.getHeatPool() + " units after conversion");
        helper.assertTrue(heat.getEnergyStorage().getEnergyStored() == 0,
                "the heat device still holds " + heat.getEnergyStorage().getEnergyStored() + " FE");
        helper.assertTrue(systemTotal > 0,
                "no heat at all was recovered from a 10,000 FE spend");
        helper.assertTrue(maxTickGain <= 100 && systemTotal <= 10_000L * 15 / 100 * 85 / 100,
                "heat conversion exceeded the design: worst tick " + maxTickGain
                        + " FE (cap 100 FE/t), total " + systemTotal
                        + " FE from a 10,000 FE spend, design max 1,275 FE"
                        + " (15% credit x 85% conversion)");
        helper.assertTrue(crafter.getEnergyStorage().getEnergyStored() == 10_000 + systemTotal,
                "the machine holds " + crafter.getEnergyStorage().getEnergyStored()
                        + " FE, not its original 10,000 plus the recovered " + systemTotal);

        // A second run without a new spend must return nothing: no infinite circulation.
        int idleBefore = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored();
        tickDevice(helper, heat, 20);
        int idleAfter = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored();
        helper.assertTrue(idleAfter == idleBefore,
                "the heat device kept producing without a new spend: " + idleBefore + " -> "
                        + idleAfter);
        System.out.println("[Phase34-audit] heat from machine spend: recovered=" + systemTotal
                + " of 10,000 spent");
        helper.succeed();
    }

    /**
     * The regenerative generator credits 5% of a machine's spend once, when the run stops,
     * returns it at 1:1 - and then stops: no infinite circulation.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void regenCreditsStopOnceAndReturnsFivePercent(GameTestHelper helper) {
        RecoveryBlockEntity regen = placeRecovery(helper, CENTER, ModBlocks.REGEN_GENERATOR.get());
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(3, Y, 2));
        crafter.getEnergyStorage().setEnergy(20_000);
        tickDevice(helper, regen, 1); // baseline observation of the machine's counter

        crafter.getEnergyStorage().consume(10_000); // counter 0 -> 10,000, machine now holds 10,000

        long systemTotal = 0L;
        for (int index = 0; index < 80; index++) {
            int before = crafter.getEnergyStorage().getEnergyStored()
                    + regen.getEnergyStorage().getEnergyStored();
            tickDevice(helper, regen, 1);
            int after = crafter.getEnergyStorage().getEnergyStored()
                    + regen.getEnergyStorage().getEnergyStored();
            int gained = after - before;
            helper.assertTrue(gained >= 0,
                    "tick " + index + ": the regen device lost FE while converting");
            helper.assertTrue(gained <= 40,
                    "tick " + index + ": converted " + gained + " FE, above the 40 FE/t cap");
            helper.assertTrue(regen.getRate() <= 40,
                    "GUI rate " + regen.getRate() + " exceeds the 40 FE/t design cap");
            systemTotal += gained;
        }
        helper.assertTrue(systemTotal == 500,
                "a 10,000 FE spend returned " + systemTotal + " FE instead of exactly 5% (500)");
        helper.assertTrue(regen.getRegenPool() == 0,
                "the regen pool still holds " + regen.getRegenPool() + " units");
        helper.assertTrue(regen.getEnergyStorage().getEnergyStored() == 0,
                "the regen device still holds " + regen.getEnergyStorage().getEnergyStored() + " FE");
        helper.assertTrue(crafter.getEnergyStorage().getEnergyStored() == 10_500,
                "the machine holds " + crafter.getEnergyStorage().getEnergyStored()
                        + " FE instead of 10,000 spent + 500 returned");

        // No new spend: nothing further must flow, ever.
        int idleBefore = crafter.getEnergyStorage().getEnergyStored()
                + regen.getEnergyStorage().getEnergyStored();
        tickDevice(helper, regen, 20);
        int idleAfter = crafter.getEnergyStorage().getEnergyStored()
                + regen.getEnergyStorage().getEnergyStored();
        helper.assertTrue(idleAfter == idleBefore,
                "the regen device kept producing without a new spend: " + idleBefore + " -> "
                        + idleAfter);
        System.out.println("[Phase34-audit] regen: 10,000 spent -> " + systemTotal + " returned");
        helper.succeed();
    }

    /** When device and buffer are full the burn clock freezes: no fuel is wasted, no FE leaks. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void fuelStopsWhenDeviceAndBufferAreFull(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, CENTER, ModBlocks.THERMAL_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(3, Y, 2));
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL_BLOCK, 2));

        int burnAt5000 = -1;
        long start = System.nanoTime();
        for (int index = 0; index < 7_000; index++) {
            tickDevice(helper, generator, 1);
            if (index == 4_999) {
                burnAt5000 = generator.getBurnTime();
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        helper.assertTrue(generator.getBurnTime() == burnAt5000,
                "the burn clock still ran after everything filled: " + burnAt5000 + " -> "
                        + generator.getBurnTime());
        helper.assertTrue(generator.getBurnTime() > 0,
                "the fuel load finished although the FE had nowhere to go");
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == 250_000,
                "the buffer holds " + buffer.getEnergyStorage().getEnergyStored()
                        + " FE instead of exactly 250,000");
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 100_000,
                "the generator holds " + generator.getEnergyStorage().getEnergyStored()
                        + " FE instead of exactly its 100,000 capacity");
        helper.assertTrue(generator.getInventory()
                        .getStackInSlot(GenerationBlockEntity.FUEL_SLOT).getCount() == 1,
                "the second coal block was drawn although the first load never finished");
        helper.assertTrue(generator.getRate() <= 90,
                "GUI rate " + generator.getRate() + " exceeds the rated 90 FE/t");
        helper.assertTrue(elapsedMs < 30_000L,
                "7,000 generator ticks took " + elapsedMs + " ms, far beyond a healthy tick budget");
        System.out.println("[Phase34-audit] full-stall: 7000 ticks in " + elapsedMs
                + " ms, burn frozen at " + generator.getBurnTime());
        helper.succeed();
    }

    /**
     * Four conventional generators, each next to an energy buffer: wind, solar, combustion and
     * nuclear deliver their exact rated output into the buffer when their conditions hold.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void conventionalGeneratorsFeedTheirBuffersExactly(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000L); // noon, so daylight is unambiguous
        BlockPos solarPos = new BlockPos(1, Y, 3);
        for (int height = 1; height <= 64; height++) {
            helper.setBlock(new BlockPos(1, Y + height, 3), Blocks.AIR.defaultBlockState());
        }

        GenerationBlockEntity wind = placeGeneration(helper, new BlockPos(1, Y, 1),
                ModBlocks.WIND_GENERATOR.get());
        EnergyBufferBlockEntity windBuffer = placeBuffer(helper, new BlockPos(2, Y, 1));
        GenerationBlockEntity solar = placeGeneration(helper, solarPos,
                ModBlocks.SOLAR_GENERATOR.get());
        EnergyBufferBlockEntity solarBuffer = placeBuffer(helper, new BlockPos(2, Y, 3));
        GenerationBlockEntity combustion = placeGeneration(helper, new BlockPos(4, Y, 1),
                ModBlocks.COMBUSTION_GENERATOR.get());
        EnergyBufferBlockEntity combustionBuffer = placeBuffer(helper, new BlockPos(5, Y, 1));
        GenerationBlockEntity nuclear = placeGeneration(helper, new BlockPos(4, Y, 3),
                ModBlocks.NUCLEAR_GENERATOR.get());
        EnergyBufferBlockEntity nuclearBuffer = placeBuffer(helper, new BlockPos(5, Y, 3));
        helper.setBlock(new BlockPos(3, Y, 3), Blocks.WATER.defaultBlockState());

        combustion.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_CAN.get()));
        nuclear.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));

        for (int index = 0; index < 60; index++) {
            tickDevice(helper, wind, 1);
            tickDevice(helper, solar, 1);
            tickDevice(helper, combustion, 1);
            tickDevice(helper, nuclear, 1);
        }

        helper.assertTrue(helper.getLevel().canSeeSky(helper.absolutePos(solarPos).above()),
                "the cleared sky column is not visible - the solar test would prove nothing");
        helper.assertTrue(solar.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "solar in daylight reported " + solar.getStatus());
        helper.assertTrue(solar.getRate() == 60,
                "solar rate is " + solar.getRate() + " instead of 60 FE/t");
        helper.assertTrue(wind.getRate() >= 18 && wind.getRate() <= 70,
                "wind rate " + wind.getRate() + " is outside the 18..70 FE/t range");
        helper.assertTrue(combustion.getRate() == 160,
                "combustion rate is " + combustion.getRate() + " instead of 160 FE/t");
        helper.assertTrue(nuclear.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "nuclear with cooling water reported " + nuclear.getStatus());
        helper.assertTrue(nuclear.getRate() == 400,
                "nuclear rate with water is " + nuclear.getRate() + " instead of 400 FE/t");

        checkPair(helper, "wind", wind, windBuffer, wind.getRate() * 60);
        checkPair(helper, "solar", solar, solarBuffer, 60 * 60);
        checkPair(helper, "combustion", combustion, combustionBuffer, 160 * 60);
        checkPair(helper, "nuclear", nuclear, nuclearBuffer, 400 * 60);
        helper.succeed();
    }

    /** One buffer and generator pair must hold exactly rated * ticks after the run. */
    private static void checkPair(GameTestHelper helper, String name, GenerationBlockEntity generator,
                                  EnergyBufferBlockEntity buffer, int expected) {
        int total = generator.getEnergyStorage().getEnergyStored()
                + buffer.getEnergyStorage().getEnergyStored();
        helper.assertTrue(total == expected,
                name + ": generator + buffer hold " + total + " FE instead of " + expected);
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 0,
                name + ": the generator kept " + generator.getEnergyStorage().getEnergyStored()
                        + " FE instead of pushing it all into the buffer");
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() > 0,
                name + ": the buffer was never charged");
    }

    /** Recovery values (stored FE, pools) survive a save/load round trip; the GUI matches. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void recoveryValuesSurviveSaveAndLoadAndGuiMatches(GameTestHelper helper) {
        RecoveryBlockEntity flywheel = placeRecovery(helper, CENTER, ModBlocks.FLYWHEEL.get());
        flywheel.getEnergyStorage().receiveEnergy(5_000, false); // 200 accepted, 190 stored
        int flywheelStored = flywheel.getEnergyStorage().getEnergyStored();

        CompoundTag flywheelTag =
                flywheel.saveWithoutMetadata(helper.getLevel().registryAccess());
        RecoveryBlockEntity flywheelBack = placeRecovery(helper, new BlockPos(6, Y, 6),
                ModBlocks.FLYWHEEL.get());
        flywheelBack.loadWithComponents(flywheelTag, helper.getLevel().registryAccess());
        helper.assertTrue(flywheelBack.getEnergyStorage().getEnergyStored() == flywheelStored,
                "the reload restored " + flywheelBack.getEnergyStorage().getEnergyStored()
                        + " FE instead of " + flywheelStored);
        helper.assertTrue(flywheelBack.getEnergyStorage().getMaxEnergyStored() == 300_000,
                "the reload restored capacity "
                        + flywheelBack.getEnergyStorage().getMaxEnergyStored() + " instead of 300,000");

        RecoveryBlockEntity heat = placeRecovery(helper, new BlockPos(4, Y, 4),
                ModBlocks.HEAT_RECOVERY.get());
        CompoundTag heatTag = heat.saveWithoutMetadata(helper.getLevel().registryAccess());
        heatTag.putLong("HeatPool", 777L);
        heatTag.putLong("RegenPool", 888L);
        RecoveryBlockEntity heatBack = placeRecovery(helper, new BlockPos(6, Y, 4),
                ModBlocks.HEAT_RECOVERY.get());
        heatBack.loadWithComponents(heatTag, helper.getLevel().registryAccess());
        helper.assertTrue(heatBack.getHeatPool() == 777L,
                "the reload restored a heat pool of " + heatBack.getHeatPool() + " instead of 777");
        helper.assertTrue(heatBack.getRegenPool() == 888L,
                "the reload restored a regen pool of " + heatBack.getRegenPool() + " instead of 888");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GenerationMenu menu =
                (GenerationMenu) flywheel.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu.getEnergyStored() == flywheel.getEnergyStorage().getEnergyStored(),
                "GUI shows " + menu.getEnergyStored() + " FE but the flywheel holds "
                        + flywheel.getEnergyStorage().getEnergyStored() + " FE");
        helper.assertTrue(menu.getMaxEnergy() == 300_000,
                "GUI shows capacity " + menu.getMaxEnergy() + " instead of 300,000");
        helper.assertTrue(menu.getRate() == flywheel.getRate(),
                "GUI shows rate " + menu.getRate() + " but the device reports " + flywheel.getRate());
        helper.succeed();
    }

    /**
     * Six thermal generators around one buffer: the sum is exact for the whole coal load, and
     * the wall-clock cost of the run doubles as the server-load probe of Phase 34.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void sixGeneratorsFeedOneBufferExactly(GameTestHelper helper) {
        EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER);
        GenerationBlockEntity[] generators = new GenerationBlockEntity[] {
                placeGeneration(helper, new BlockPos(1, Y, 2), ModBlocks.THERMAL_GENERATOR.get()),
                placeGeneration(helper, new BlockPos(3, Y, 2), ModBlocks.THERMAL_GENERATOR.get()),
                placeGeneration(helper, new BlockPos(2, Y, 1), ModBlocks.THERMAL_GENERATOR.get()),
                placeGeneration(helper, new BlockPos(2, Y, 3), ModBlocks.THERMAL_GENERATOR.get()),
                placeGeneration(helper, new BlockPos(2, Y + 1, 2), ModBlocks.THERMAL_GENERATOR.get()),
                placeGeneration(helper, new BlockPos(4, Y, 2), ModBlocks.THERMAL_GENERATOR.get()),
        };
        for (GenerationBlockEntity generator : generators) {
            generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                    new ItemStack(Items.COAL, 1));
        }

        long start = System.nanoTime();
        for (int index = 0; index < 641; index++) {
            for (GenerationBlockEntity generator : generators) {
                tickDevice(helper, generator, 1);
            }
            tickDevice(helper, buffer, 1);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        long total = buffer.getEnergyStorage().getEnergyStored();
        for (GenerationBlockEntity generator : generators) {
            total += generator.getEnergyStorage().getEnergyStored();
            helper.assertTrue(generator.getBurnTime() == 0,
                    "a generator still burns after its coal is gone: " + generator.getBurnTime());
            helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.NO_FUEL,
                    "an empty generator reported " + generator.getStatus());
            helper.assertTrue(generator.getRate() <= 90,
                    "GUI rate " + generator.getRate() + " exceeds the rated 90 FE/t");
        }
        helper.assertTrue(total == 6L * 57_600,
                "six coals produced " + total + " FE instead of exactly 345,600 FE");
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() <= 250_000,
                "the buffer exceeds its capacity");
        helper.assertTrue(elapsedMs < 30_000L,
                "6 x 641 generator ticks took " + elapsedMs + " ms, far beyond a healthy budget");
        System.out.println("[Phase34-audit] load probe: 6 generators x 641 ticks + buffer in "
                + elapsedMs + " ms (" + (6 * 641) + " device ticks)");
        helper.succeed();
    }

    /** Ticks the device manually so every assertion sees a known tick count. */
    private static void tickDevice(GameTestHelper helper, BlockEntity device, int times) {
        for (int index = 0; index < times; index++) {
            if (device instanceof GenerationBlockEntity generation) {
                GenerationBlockEntity.serverTick(helper.getLevel(), generation.getBlockPos(),
                        generation.getBlockState(), generation);
            } else if (device instanceof RecoveryBlockEntity recovery) {
                RecoveryBlockEntity.serverTick(helper.getLevel(), recovery.getBlockPos(),
                        recovery.getBlockState(), recovery);
            } else if (device instanceof EnergyBufferBlockEntity buffer) {
                EnergyBufferBlockEntity.serverTick(helper.getLevel(), buffer.getBlockPos(),
                        buffer.getBlockState(), buffer);
            } else {
                throw new IllegalStateException("no ticker for " + device);
            }
        }
    }

    private static GenerationBlockEntity placeGeneration(GameTestHelper helper, BlockPos pos,
                                                          Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof GenerationBlockEntity device)) {
            throw new IllegalStateException("generation device missing at " + pos);
        }
        return device;
    }

    private static RecoveryBlockEntity placeRecovery(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof RecoveryBlockEntity device)) {
            throw new IllegalStateException("recovery device missing at " + pos);
        }
        return device;
    }

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof EnergyBufferBlockEntity buffer)) {
            throw new IllegalStateException("energy buffer missing at " + pos);
        }
        return buffer;
    }

    private static CrafterBlockEntity placeCrafter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRAFTER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof CrafterBlockEntity crafter)) {
            throw new IllegalStateException("crafter missing at " + pos);
        }
        return crafter;
    }
}
