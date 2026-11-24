package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 35 integration audit: the ten power devices combined with the existing machines and
 * both buffer tiers. Every test measures one fixed window and asserts exact numbers or the
 * conservation bound, so a mismatch is a real defect, not a tolerance.
 *
 * <p>Measurement technique is the established manual-tick loop ({@link GenerationGameTests}):
 * the test drives {@code serverTick} itself, which makes every count deterministic. The load
 * test reports wall-clock times as audit data; it does not assert on machine speed.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerIntegrationAuditGameTests {
    private static final int Y = 6;

    private PowerIntegrationAuditGameTests() {
    }

    /**
     * Five conventional generators (coal, fuel can, fuel rod, water, wind) plus solar feed one
     * basic Energy Buffer for 150 ticks while a flywheel hangs off the buffer. Every FE is
     * accounted: sources sum exactly, the buffer forwards exactly, friction is exactly 5%.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void mixedFarmFeedsOneBufferExactly(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000L);
        // Open sky for solar (4,6,2) and the full air column for the wind turbine (2,7,2).
        for (int height = 1; height <= 64; height++) {
            helper.setBlock(new BlockPos(4, Y + height, 2), Blocks.AIR.defaultBlockState());
        }
        for (int height = 1; height <= 16; height++) {
            helper.setBlock(new BlockPos(2, Y + 1 + height, 2), Blocks.AIR.defaultBlockState());
        }
        helper.setBlock(new BlockPos(1, Y, 1), Blocks.WATER.defaultBlockState());
        helper.setBlock(new BlockPos(3, Y, 1), Blocks.WATER.defaultBlockState());

        GenerationBlockEntity thermal =
                placeGeneration(helper, new BlockPos(2, Y, 1), ModBlocks.THERMAL_GENERATOR.get());
        GenerationBlockEntity combustion = placeGeneration(helper,
                new BlockPos(2, Y, 3), ModBlocks.COMBUSTION_GENERATOR.get());
        GenerationBlockEntity nuclear =
                placeGeneration(helper, new BlockPos(3, Y, 2), ModBlocks.NUCLEAR_GENERATOR.get());
        GenerationBlockEntity hydro =
                placeGeneration(helper, new BlockPos(1, Y, 2), ModBlocks.HYDRO_GENERATOR.get());
        GenerationBlockEntity wind =
                placeGeneration(helper, new BlockPos(2, Y + 1, 2), ModBlocks.WIND_GENERATOR.get());
        GenerationBlockEntity solar =
                placeGeneration(helper, new BlockPos(4, Y, 2), ModBlocks.SOLAR_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(2, Y, 2), BufferTier.BASIC);
        RecoveryBlockEntity flywheel =
                placeRecovery(helper, new BlockPos(2, Y - 1, 2), ModBlocks.FLYWHEEL.get());

        thermal.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 4));
        combustion.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_CAN.get(), 2));
        nuclear.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 2));

        // Deterministic order: generators fill the buffer, the buffer feeds the flywheel,
        // the flywheel bursts back - the 5% friction hop is visible in the totals.
        for (int index = 0; index < 150; index++) {
            tickDevice(helper, thermal, 1);
            tickDevice(helper, combustion, 1);
            tickDevice(helper, nuclear, 1);
            tickDevice(helper, hydro, 1);
            tickDevice(helper, wind, 1);
            tickDevice(helper, solar, 1);
            tickDevice(helper, buffer, 1);
            tickDevice(helper, flywheel, 1);
        }

        // Sources: 90*150 + 160*150 + 400*150 + 50*150 + 70*150 = 115,500 FE.
        long expectedSources = 90L * 150 + 160L * 150 + 400L * 150 + 50L * 150 + 70L * 150;
        long bufferOut = buffer.getEnergyStorage().getConsumedTotal();
        long bufferIn = (long) buffer.getEnergyStorage().getEnergyStored() + bufferOut;
        // The flywheel hands 95% of what it was charged straight back into the same
        // buffer, so the generator-side total is the gross intake minus that round trip.
        long flywheelReturn = bufferOut * 95 / 100;
        helper.assertTrue(bufferIn - flywheelReturn == expectedSources,
                "the generators fed " + (bufferIn - flywheelReturn)
                        + " FE into the buffer instead of " + expectedSources
                        + " (gross intake " + bufferIn + ", flywheel return "
                        + flywheelReturn + ")");
        // Buffer: in 115,500 - 30,000 to the flywheel + 28,500 back = 114,000 (friction 1,500).
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == 114_000,
                "the buffer holds " + buffer.getEnergyStorage().getEnergyStored()
                        + " FE instead of 114,000 after 150 ticks");
        helper.assertTrue(buffer.getEnergyStorage().getConsumedTotal() == 30_000L,
                "the buffer forwarded " + buffer.getEnergyStorage().getConsumedTotal()
                        + " FE to the flywheel instead of 30,000 (200/t receive limit)");
        helper.assertTrue(flywheel.getEnergyStorage().getEnergyStored() == 0,
                "the flywheel holds " + flywheel.getEnergyStorage().getEnergyStored()
                        + " FE - it must burst everything back each tick (friction only)");
        helper.assertTrue(solar.getEnergyStorage().getEnergyStored() == 9_000,
                "solar stored " + solar.getEnergyStorage().getEnergyStored()
                        + " FE instead of 60 * 150 = 9,000 (no accepting neighbour)");
        // Every generator forwarded its whole per-tick output: no stall, no leftover.
        int[] forwarded = {thermal.getEnergyStorage().getEnergyStored(),
                combustion.getEnergyStorage().getEnergyStored(),
                nuclear.getEnergyStorage().getEnergyStored(),
                hydro.getEnergyStorage().getEnergyStored(),
                wind.getEnergyStorage().getEnergyStored()};
        for (int stored : forwarded) {
            helper.assertTrue(stored == 0,
                    "a generator kept " + stored + " FE internally - the buffer rejected FE"
                            + " (receive window or capacity problem)");
        }
        // Burn clocks prove the window was fully productive (no stall mid-window).
        helper.assertTrue(thermal.getBurnTime() == 640 - 150,
                "thermal burn clock " + thermal.getBurnTime() + " instead of 490");
        helper.assertTrue(combustion.getBurnTime() == 900 - 150,
                "combustion burn clock " + combustion.getBurnTime() + " instead of 750");
        helper.assertTrue(nuclear.getBurnTime() == 180 - 150,
                "nuclear burn clock " + nuclear.getBurnTime() + " instead of 30");
        helper.assertTrue(thermal.getStatus() == GenerationBlockEntity.Status.RUNNING
                        && nuclear.getStatus() == GenerationBlockEntity.Status.RUNNING
                        && hydro.getStatus() == GenerationBlockEntity.Status.RUNNING
                        && wind.getStatus() == GenerationBlockEntity.Status.RUNNING
                        && solar.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "a generator left RUNNING during the window");
        System.out.println("[Phase35-farm] 150t window: sources=" + expectedSources
                + " grossIn=" + bufferIn + " return=" + flywheelReturn
                + " buffer=" + buffer.getEnergyStorage().getEnergyStored()
                + " forward=" + bufferOut
                + " flywheel=" + flywheel.getEnergyStorage().getEnergyStored()
                + " solar=" + solar.getEnergyStorage().getEnergyStored());
        helper.succeed();
    }

    /**
     * Six generators press on one buffer in a single tick: the receive limit is per
     * capability call, so the effective window grows with the neighbour count. Measured,
     * not judged - the Phase 17 semantics stay untouched.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void receiveWindowPerCallIsMeasuredOnBothBufferTiers(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000L);
        for (int height = 1; height <= 16; height++) {
            helper.setBlock(new BlockPos(2, Y + 1 + height, 2), Blocks.AIR.defaultBlockState());
        }
        GenerationBlockEntity thermal =
                placeGeneration(helper, new BlockPos(2, Y, 1), ModBlocks.THERMAL_GENERATOR.get());
        GenerationBlockEntity combustion = placeGeneration(helper,
                new BlockPos(2, Y, 3), ModBlocks.COMBUSTION_GENERATOR.get());
        GenerationBlockEntity nuclear =
                placeGeneration(helper, new BlockPos(3, Y, 2), ModBlocks.NUCLEAR_GENERATOR.get());
        GenerationBlockEntity hydro =
                placeGeneration(helper, new BlockPos(1, Y, 2), ModBlocks.HYDRO_GENERATOR.get());
        GenerationBlockEntity wind =
                placeGeneration(helper, new BlockPos(2, Y + 1, 2), ModBlocks.WIND_GENERATOR.get());
        // Solar takes the sixth (down) slot: the buffer above blocks its sky view, so it
        // contributes a pure 200 FE/t push with no generation on top.
        GenerationBlockEntity solar = placeGeneration(helper,
                new BlockPos(2, Y - 1, 2), ModBlocks.SOLAR_GENERATOR.get());
        GenerationBlockEntity[] generators = {thermal, hydro, wind, solar, combustion, nuclear};
        for (GenerationBlockEntity generator : generators) {
            generator.getEnergyStorage().setEnergy(10_000);
        }

        EnergyBufferBlockEntity basic = placeBuffer(helper, new BlockPos(2, Y, 2), BufferTier.BASIC);
        for (GenerationBlockEntity generator : generators) {
            tickDevice(helper, generator, 1);
        }
        int basicReceived = basic.getEnergyStorage().getEnergyStored();
        // pushPerTick caps: 256 / 200 / 200 / 200 / 512, nuclear capped by 512 per call.
        int expectedBasic = 256 + 200 + 200 + 200 + 512 + 512;
        helper.assertTrue(basicReceived == expectedBasic,
                "the basic buffer accepted " + basicReceived + " FE in one tick, expected "
                        + expectedBasic + " (per-call receive of 512 x 6 neighbours)");

        helper.setBlock(new BlockPos(2, Y, 2), ModBlocks.ENERGY_BUFFER_MK2.get().defaultBlockState());
        EnergyBufferBlockEntity mk2 = placeBuffer(helper, new BlockPos(2, Y, 2), BufferTier.REINFORCED);
        for (GenerationBlockEntity generator : generators) {
            tickDevice(helper, generator, 1);
        }
        int mk2Received = mk2.getEnergyStorage().getEnergyStored();
        // MK2 receive is 1,024 per call: nuclear can now push its full 900 budget.
        int expectedMk2 = 256 + 200 + 200 + 200 + 512 + 900;
        helper.assertTrue(mk2Received == expectedMk2,
                "the MK2 buffer accepted " + mk2Received + " FE in one tick, expected "
                        + expectedMk2 + " (per-call receive of 1,024 x 6 neighbours)");
        System.out.println("[Phase35-window] one tick, 6 neighbours: basic=" + basicReceived
                + " FE (nominal 512 = x" + (basicReceived / 512) + "), mk2=" + mk2Received
                + " FE (nominal 1,024 = x" + (mk2Received / 1024) + ")");
        helper.succeed();
    }

    /**
     * Closed loop in one window: thermal coal -> basic buffer -> ore synthesizer (real
     * recipe) with waste-heat and regen recovery on the machine plus pressure recovery on
     * the generator. The conservation bound proves that no path can multiply energy:
     * end total <= fuel input + exhaust share + 17.75% of what the machine spent.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 700)
    public static void recoveryLoopNeverMultipliesTheSystemTotal(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000L);
        GenerationBlockEntity thermal =
                placeGeneration(helper, new BlockPos(2, Y, 1), ModBlocks.THERMAL_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(2, Y, 2), BufferTier.BASIC);
        OreSynthesizerBlockEntity synth =
                placeSynthesizer(helper, new BlockPos(2, Y, 3));
        RecoveryBlockEntity heat =
                placeRecovery(helper, new BlockPos(1, Y, 3), ModBlocks.HEAT_RECOVERY.get());
        RecoveryBlockEntity regen =
                placeRecovery(helper, new BlockPos(3, Y, 3), ModBlocks.REGEN_GENERATOR.get());
        RecoveryBlockEntity pressure =
                placeRecovery(helper, new BlockPos(1, Y, 1), ModBlocks.PRESSURE_RECOVERY.get());
        thermal.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));
        refillSynthesizer(synth);

        long consumedBefore = synth.getEnergyStorage().getConsumedTotal();
        // 700 ticks > the 640-tick coal cycle: the whole load burns inside the window.
        for (int index = 0; index < 700; index++) {
            tickDevice(helper, thermal, 1);
            tickDevice(helper, buffer, 1);
            tickDevice(helper, synth, 1);
            tickDevice(helper, heat, 1);
            tickDevice(helper, regen, 1);
            tickDevice(helper, pressure, 1);
        }

        long consumed = synth.getEnergyStorage().getConsumedTotal() - consumedBefore;
        int outputCount = synth.getInventory()
                .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT).getCount();
        helper.assertTrue(consumed > 0,
                "the synthesizer never consumed FE - the loop measured nothing");
        helper.assertTrue(outputCount > 0,
                "the synthesizer produced no output despite consuming " + consumed + " FE");
        helper.assertTrue(thermal.getBurnTime() == 0 && fuelCount(thermal) == 0,
                "the coal did not burn out in 700 ticks (burn=" + thermal.getBurnTime()
                        + ", fuel=" + fuelCount(thermal) + ")");
        // The generator forwarded its whole direct share: 57,600 FE into the buffer chain.
        long bufferIn = (long) buffer.getEnergyStorage().getEnergyStored()
                + buffer.getEnergyStorage().getConsumedTotal();
        helper.assertTrue(bufferIn == 57_600,
                "the buffer chain saw " + bufferIn + " FE instead of the 57,600 direct share");
        long pressureFe = pressure.getEnergyStorage().getEnergyStored();
        helper.assertTrue(pressureFe > 0 && pressureFe <= 5_440,
                "pressure recovery ended with " + pressureFe + " FE, expected 1..5,440"
                        + " (85% of the 6,400 exhaust units)");

        long systemEnd = (long) thermal.getEnergyStorage().getEnergyStored()
                + buffer.getEnergyStorage().getEnergyStored()
                + synth.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored()
                + regen.getEnergyStorage().getEnergyStored() + pressureFe;
        long systemStart = 0L; // every device started empty in this test
        long bound = 57_600L + 5_440L + consumed * 1_775L / 10_000L;
        helper.assertTrue(systemEnd >= systemStart,
                "the system total went backwards: " + systemEnd + " < " + systemStart);
        helper.assertTrue(systemEnd <= bound,
                "the system total " + systemEnd + " FE exceeds the conservation bound "
                        + bound + " (fuel 57,600 + exhaust 5,440 + 17.75% of consumed "
                        + consumed + ") - energy was multiplied");
        System.out.println("[Phase35-loop] 700t window: end=" + systemEnd + " bound=" + bound
                + " consumed=" + consumed + " buffer=" + buffer.getEnergyStorage().getEnergyStored()
                + " bufferOut=" + buffer.getEnergyStorage().getConsumedTotal()
                + " synth=" + synth.getEnergyStorage().getEnergyStored()
                + " pressure=" + pressureFe
                + " heat=" + heat.getEnergyStorage().getEnergyStored()
                + " regen=" + regen.getEnergyStorage().getEnergyStored()
                + " output=" + outputCount);
        helper.succeed();
    }

    /**
     * A running farm saved mid-operation and restored elsewhere: burn clock, fuel, FE,
     * exhaust pool and buffers must come back identical, the next tick must produce
     * exactly one rated tick (no double generation), and a neighbour-cache reset must
     * re-baseline the recovery poll without crediting the gap.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void saveLoadOfRunningFarmKeepsEveryCounter(GameTestHelper helper) {
        GenerationBlockEntity thermal =
                placeGeneration(helper, new BlockPos(2, Y, 1), ModBlocks.THERMAL_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(2, Y, 2), BufferTier.BASIC);
        OreSynthesizerBlockEntity synth = placeSynthesizer(helper, new BlockPos(2, Y, 3));
        RecoveryBlockEntity heat =
                placeRecovery(helper, new BlockPos(1, Y, 3), ModBlocks.HEAT_RECOVERY.get());
        RecoveryBlockEntity pressure =
                placeRecovery(helper, new BlockPos(1, Y, 1), ModBlocks.PRESSURE_RECOVERY.get());
        thermal.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 4));
        refillSynthesizer(synth);

        // 30 ticks: mid-burn, mid-craft, exhaust still draining (6,400 - 30*141 > 0).
        for (int index = 0; index < 30; index++) {
            tickDevice(helper, thermal, 1);
            tickDevice(helper, buffer, 1);
            tickDevice(helper, synth, 1);
            tickDevice(helper, heat, 1);
            tickDevice(helper, pressure, 1);
        }
        int thermalEnergyBefore = thermal.getEnergyStorage().getEnergyStored();
        int burnBefore = thermal.getBurnTime();
        int fuelBefore = fuelCount(thermal);
        long exhaustBefore = thermal.getByproduct();
        int bufferEnergyBefore = buffer.getEnergyStorage().getEnergyStored();
        int synthEnergyBefore = synth.getEnergyStorage().getEnergyStored();
        int heatEnergyBefore = heat.getEnergyStorage().getEnergyStored();
        long pressureExhaustBefore = pressure.getEnergyStorage().getEnergyStored();
        helper.assertTrue(burnBefore > 0 && exhaustBefore > 0,
                "the save probe ran out of fuel or exhaust before saving (burn=" + burnBefore
                        + ", exhaust=" + exhaustBefore + ")");

        // Restore every device at an offset cluster and compare field by field.
        GenerationBlockEntity thermal2 = restoreGeneration(helper,
                save(helper, thermal), new BlockPos(6, Y, 1));
        EnergyBufferBlockEntity buffer2 =
                restoreBuffer(helper, save(helper, buffer), new BlockPos(6, Y, 2), BufferTier.BASIC);
        OreSynthesizerBlockEntity synth2 =
                restoreSynth(helper, save(helper, synth), new BlockPos(6, Y, 3));
        RecoveryBlockEntity heat2 =
                restoreRecovery(helper, save(helper, heat), new BlockPos(5, Y, 3),
                        ModBlocks.HEAT_RECOVERY.get());
        RecoveryBlockEntity pressure2 =
                restoreRecovery(helper, save(helper, pressure), new BlockPos(5, Y, 1),
                        ModBlocks.PRESSURE_RECOVERY.get());

        helper.assertTrue(thermal2.getEnergyStorage().getEnergyStored() == thermalEnergyBefore,
                "thermal FE changed on reload: " + thermalEnergyBefore + " -> "
                        + thermal2.getEnergyStorage().getEnergyStored());
        helper.assertTrue(thermal2.getBurnTime() == burnBefore,
                "burn clock changed on reload: " + burnBefore + " -> " + thermal2.getBurnTime());
        helper.assertTrue(fuelCount(thermal2) == fuelBefore,
                "fuel stack changed on reload: " + fuelBefore + " -> " + fuelCount(thermal2));
        helper.assertTrue(thermal2.getByproduct() == exhaustBefore,
                "exhaust pool changed on reload: " + exhaustBefore + " -> "
                        + thermal2.getByproduct());
        helper.assertTrue(buffer2.getEnergyStorage().getEnergyStored() == bufferEnergyBefore,
                "buffer FE changed on reload: " + bufferEnergyBefore + " -> "
                        + buffer2.getEnergyStorage().getEnergyStored());
        helper.assertTrue(synth2.getEnergyStorage().getEnergyStored() == synthEnergyBefore,
                "synth FE changed on reload: " + synthEnergyBefore + " -> "
                        + synth2.getEnergyStorage().getEnergyStored());
        helper.assertTrue(heat2.getEnergyStorage().getEnergyStored() == heatEnergyBefore,
                "heat FE changed on reload: " + heatEnergyBefore + " -> "
                        + heat2.getEnergyStorage().getEnergyStored());
        helper.assertTrue(pressure2.getEnergyStorage().getEnergyStored()
                        == pressureExhaustBefore,
                "pressure FE changed on reload: " + pressureExhaustBefore + " -> "
                        + pressure2.getEnergyStorage().getEnergyStored());
        helper.assertTrue(synth2.getEnergyStorage().getConsumedTotal() == 0,
                "the spend counter must restart after a reload, got "
                        + synth2.getEnergyStorage().getConsumedTotal());

        // One tick on the restored pair: exactly one rated tick - no bonus generation.
        int bufferEnergyBeforeTick = buffer2.getEnergyStorage().getEnergyStored();
        int burnBeforeTick = thermal2.getBurnTime();
        tickDevice(helper, thermal2, 1);
        helper.assertTrue(thermal2.getBurnTime() == burnBeforeTick - 1,
                "the restored burn clock changed by " + (burnBeforeTick - thermal2.getBurnTime())
                        + " instead of exactly one tick");
        helper.assertTrue(buffer2.getEnergyStorage().getEnergyStored()
                        == bufferEnergyBeforeTick + 90,
                "the restored generator forwarded "
                        + (buffer2.getEnergyStorage().getEnergyStored() - bufferEnergyBeforeTick)
                        + " FE instead of exactly 90");

        // The recovery poll re-baselines after the reload without crediting anything.
        tickDevice(helper, heat2, 1);
        helper.assertTrue(heat2.getHeatPool() == 0 && heat2.getRegenPool() == 0,
                "the restored recovery device credited on its first tick: heat="
                        + heat2.getHeatPool() + " regen=" + heat2.getRegenPool());
        tickDevice(helper, heat2, 1);
        helper.assertTrue(heat2.getHeatPool() == 0,
                "the second restored tick invented heat without any spend: "
                        + heat2.getHeatPool());

        // Neighbour block swap = cache invalidation: re-baseline, never credit the gap.
        helper.setBlock(new BlockPos(6, Y, 3), Blocks.STONE.defaultBlockState());
        tickDevice(helper, heat2, 1);
        helper.setBlock(new BlockPos(6, Y, 3),
                ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        tickDevice(helper, heat2, 1);
        tickDevice(helper, heat2, 1);
        helper.assertTrue(heat2.getHeatPool() == 0 && heat2.getRegenPool() == 0,
                "a neighbour cache reset credited heat=" + heat2.getHeatPool()
                        + " regen=" + heat2.getRegenPool() + " - the gap became free energy");
        System.out.println("[Phase35-saveload] FE/burn/fuel/exhaust restored identical;"
                + " post-reload tick = exactly 90 FE; cache reset credited 0");
        helper.succeed();
    }

    /**
     * Load probe: 100 burning thermal generators in a grid, measured at three scales
     * (10 / 50 / 100 devices, 50 passes each) with a 5-tick warmup. Wall-clock times are
     * audit data for this host, not a universal guarantee; the exact FE totals per group
     * prove the devices still behave correctly under load.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void tickCostMeasuredAtTenFiftyAndHundredDevices(GameTestHelper helper) {
        GenerationBlockEntity[] devices = new GenerationBlockEntity[100];
        int index = 0;
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                GenerationBlockEntity device =
                        placeGeneration(helper, new BlockPos(x, Y, z),
                                ModBlocks.THERMAL_GENERATOR.get());
                device.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                        new ItemStack(Items.COAL, 1));
                devices[index++] = device;
            }
        }

        for (int pass = 0; pass < 5; pass++) {
            for (GenerationBlockEntity device : devices) {
                tickDevice(helper, device, 1);
            }
        }

        long warmupNanos = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (int d = 0; d < 10; d++) {
                tickDevice(helper, devices[d], 1);
            }
        }
        long scale10 = System.nanoTime() - warmupNanos;

        long start50 = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (int d = 0; d < 50; d++) {
                tickDevice(helper, devices[d], 1);
            }
        }
        long scale50 = System.nanoTime() - start50;

        long start100 = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (GenerationBlockEntity device : devices) {
                tickDevice(helper, device, 1);
            }
        }
        long scale100 = System.nanoTime() - start100;

        // Exact FE per group: warmup 5 + 50 + 50 + 50 / 5 + 50 + 50 / 5 + 50 ticks at 90 FE/t.
        for (int d = 0; d < 10; d++) {
            helper.assertTrue(devices[d].getEnergyStorage().getEnergyStored() == 155 * 90,
                    "device " + d + " holds " + devices[d].getEnergyStorage().getEnergyStored()
                            + " FE instead of " + 155 * 90);
        }
        for (int d = 10; d < 50; d++) {
            helper.assertTrue(devices[d].getEnergyStorage().getEnergyStored() == 105 * 90,
                    "device " + d + " holds " + devices[d].getEnergyStorage().getEnergyStored()
                            + " FE instead of " + 105 * 90);
        }
        for (int d = 50; d < 100; d++) {
            helper.assertTrue(devices[d].getEnergyStorage().getEnergyStored() == 55 * 90,
                    "device " + d + " holds " + devices[d].getEnergyStorage().getEnergyStored()
                            + " FE instead of " + 55 * 90);
        }

        double ms10 = scale10 / 1_000_000.0;
        double ms50 = scale50 / 1_000_000.0;
        double ms100 = scale100 / 1_000_000.0;
        double us10 = scale10 / 1_000.0 / (50 * 10);
        double us50 = scale50 / 1_000.0 / (50 * 50);
        double us100 = scale100 / 1_000.0 / (50 * 100);
        helper.assertTrue(ms10 < 60_000 && ms50 < 60_000 && ms100 < 60_000,
                "the load probe took longer than a minute - something is pathologically slow");
        System.out.println("[Phase35-load] GameTest server, single thread, "
                + Runtime.getRuntime().availableProcessors() + " cores, "
                + System.getProperty("os.name") + ": "
                + "10 devices x50 passes = " + String.format("%.2f", ms10) + " ms ("
                + String.format("%.2f", us10) + " us/device-tick), "
                + "50 devices = " + String.format("%.2f", ms50) + " ms ("
                + String.format("%.2f", us50) + " us/device-tick), "
                + "100 devices = " + String.format("%.2f", ms100) + " ms ("
                + String.format("%.2f", us100) + " us/device-tick)");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** Fuel stack count in the single fuel slot. */
    private static int fuelCount(GenerationBlockEntity device) {
        return device.getInventory().getStackInSlot(GenerationBlockEntity.FUEL_SLOT).getCount();
    }

    /** The stage-15 iron inputs that let the synthesizer craft (PowerGrid pattern). */
    private static void refillSynthesizer(OreSynthesizerBlockEntity synthesizer) {
        synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START,
                new ItemStack(ModItems.IRON_STABILIZED.get(), 32));
        synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.SYNTHETIC_ORE_ULTIMATE.get(), 1));
    }

    private static CompoundTag save(GameTestHelper helper, BlockEntity device) {
        return device.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    // Manual tick loops - one overload per block entity type.
    private static void tickDevice(GameTestHelper helper, GenerationBlockEntity device, int times) {
        for (int index = 0; index < times; index++) {
            GenerationBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static void tickDevice(GameTestHelper helper, RecoveryBlockEntity device, int times) {
        for (int index = 0; index < times; index++) {
            RecoveryBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static void tickDevice(GameTestHelper helper, EnergyBufferBlockEntity device,
                                   int times) {
        for (int index = 0; index < times; index++) {
            EnergyBufferBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static void tickDevice(GameTestHelper helper, OreSynthesizerBlockEntity device,
                                   int times) {
        for (int index = 0; index < times; index++) {
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    // Placement helpers.
    private static GenerationBlockEntity placeGeneration(GameTestHelper helper, BlockPos pos,
                                                          Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof GenerationBlockEntity device)) {
            throw new IllegalStateException("generation device missing at " + pos);
        }
        return device;
    }

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos,
                                                        BufferTier tier) {
        Block block = switch (tier) {
            case BASIC -> ModBlocks.ENERGY_BUFFER.get();
            case REINFORCED -> ModBlocks.ENERGY_BUFFER_MK2.get();
            case HIGH_DENSITY -> ModBlocks.ENERGY_BUFFER_MK3.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof EnergyBufferBlockEntity buffer)) {
            throw new IllegalStateException("energy buffer missing at " + pos);
        }
        return buffer;
    }

    private static RecoveryBlockEntity placeRecovery(GameTestHelper helper, BlockPos pos,
                                                      Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof RecoveryBlockEntity device)) {
            throw new IllegalStateException("recovery device missing at " + pos);
        }
        return device;
    }

    private static OreSynthesizerBlockEntity placeSynthesizer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof OreSynthesizerBlockEntity synthesizer)) {
            throw new IllegalStateException("synthesizer missing at " + pos);
        }
        return synthesizer;
    }

    // Save/load restores: place the block fresh, then replay the stored tag.
    private static GenerationBlockEntity restoreGeneration(GameTestHelper helper, CompoundTag tag,
                                                            BlockPos pos) {
        GenerationBlockEntity device =
                placeGeneration(helper, pos, ModBlocks.THERMAL_GENERATOR.get());
        device.loadWithComponents(tag, helper.getLevel().registryAccess());
        return device;
    }

    private static EnergyBufferBlockEntity restoreBuffer(GameTestHelper helper, CompoundTag tag,
                                                          BlockPos pos, BufferTier tier) {
        EnergyBufferBlockEntity buffer = placeBuffer(helper, pos, tier);
        buffer.loadWithComponents(tag, helper.getLevel().registryAccess());
        return buffer;
    }

    private static OreSynthesizerBlockEntity restoreSynth(GameTestHelper helper, CompoundTag tag,
                                                           BlockPos pos) {
        OreSynthesizerBlockEntity synthesizer = placeSynthesizer(helper, pos);
        synthesizer.loadWithComponents(tag, helper.getLevel().registryAccess());
        return synthesizer;
    }

    private static RecoveryBlockEntity restoreRecovery(GameTestHelper helper, CompoundTag tag,
                                                        BlockPos pos, Block block) {
        RecoveryBlockEntity device = placeRecovery(helper, pos, block);
        device.loadWithComponents(tag, helper.getLevel().registryAccess());
        return device;
    }
}
