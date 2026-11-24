package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The ten power devices of Phase 33 in a real level: they generate FE, push it into a
 * neighbour, consume their fuel exactly according to the documented balance and show the
 * same numbers in the GUI as they hold internally.
 *
 * <p>Every test drives the device by calling {@code serverTick} in a loop instead of waiting
 * for real game ticks, the same technique {@link PowerGridGameTests} uses - deterministic and
 * fast, and it makes "one coal = 640 burn ticks = 57,600 direct FE" an exact assertion rather
 * than a tolerance. Nothing here creates energy from nothing: the conservation test adds up
 * generator + buffer after the whole coal is burnt and requires the exact documented total.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenerationGameTests {
    private static final int Y = 6;
    private static final BlockPos GEN = new BlockPos(2, Y, 2);

    private GenerationGameTests() {
    }

    /** One coal produces exactly the documented 57,600 FE and every FE lands in the buffer. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void thermalBurnsOneCoalIntoBuffer(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(3, Y, 2));
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 1));

        // 640 burn ticks + one extra tick that must not produce anything (no fuel left).
        tickDevice(helper, generator, 641);

        int generated = generator.getEnergyStorage().getEnergyStored()
                + buffer.getEnergyStorage().getEnergyStored();
        helper.assertTrue(generated == 57_600,
                "one coal produced " + generated + " FE instead of the documented 57,600 FE");
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() > 0,
                "the buffer was not charged at all");
        helper.assertTrue(generator.getBurnTime() == 0,
                "the burn clock still runs after the coal is gone: " + generator.getBurnTime());
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.NO_FUEL,
                "empty fuel slot reported " + generator.getStatus() + " instead of NO_FUEL");
        helper.succeed();
    }

    /** The fuel stack shrinks once per burn cycle, and the burn clock runs exactly 640 ticks. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void fuelIsConsumedOncePerBurnCycle(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 16));
        int coalFe = GenerationBlockEntity.fuelContentFe(new ItemStack(Items.COAL));
        helper.assertTrue(coalFe == 64_000,
                "one coal is worth " + coalFe + " FE instead of 64,000 FE");

        tickDevice(helper, generator, 1);
        helper.assertTrue(fuelCount(generator) == 15,
                "the first tick did not consume exactly one coal, " + fuelCount(generator) + " left");
        helper.assertTrue(generator.getBurnTimeTotal() == 640,
                "one coal lasts " + generator.getBurnTimeTotal() + " burn ticks instead of 640");

        // 99 more ticks: still the same load, still the same total.
        tickDevice(helper, generator, 99);
        helper.assertTrue(fuelCount(generator) == 15,
                "the fuel stack shrank again while the first load was still burning");
        helper.assertTrue(generator.getBurnTime() == 640 - 100,
                "after 100 ticks the burn clock shows " + generator.getBurnTime()
                        + " instead of 540");
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "a burning generator reported " + generator.getStatus() + " instead of RUNNING");

        // Tick 641 finishes the first load and must start the second one.
        tickDevice(helper, generator, 541);
        helper.assertTrue(fuelCount(generator) == 14,
                "the second coal was not drawn after the first load burnt out");
        helper.assertTrue(generator.getBurnTimeTotal() == 640,
                "the second load has a different burn length: " + generator.getBurnTimeTotal());
        helper.succeed();
    }

    /** With no consumer the internal buffer fills to its capacity and the fuel load stalls. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void outputStopsAtCapacityAndRateStaysRated(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        GenerationKind kind = generator.kind();

        // Fuel can = 160,000 FE, more than the 100,000 FE internal buffer: the device fills up.
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_CAN.get()));
        tickDevice(helper, generator, 1400);
        int burnWhenFull = generator.getBurnTime();
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == kind.capacity(),
                "the internal buffer holds " + generator.getEnergyStorage().getEnergyStored()
                        + " FE instead of its capacity " + kind.capacity());
        helper.assertTrue(generator.getRate() <= kind.output(),
                "GUI rate " + generator.getRate() + " exceeds the rated output " + kind.output());

        // Full device: no further burn tick, no FE invented, rate still within the rating.
        tickDevice(helper, generator, 100);
        helper.assertTrue(generator.getBurnTime() == burnWhenFull,
                "a full device kept burning fuel: " + burnWhenFull + " -> "
                        + generator.getBurnTime());
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() <= kind.capacity(),
                "stored FE exceeded the capacity");
        helper.assertTrue(generator.getRate() <= kind.output(),
                "GUI rate " + generator.getRate() + " exceeds the rated output " + kind.output());
        helper.succeed();
    }

    /** The menu (GUI) reports exactly what the block entity holds. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void menuShowsTheStoredValues(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 4));
        tickDevice(helper, generator, 30);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        GenerationMenu menu = (GenerationMenu) generator.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu.getEnergyStored() == generator.getEnergyStorage().getEnergyStored(),
                "GUI shows " + menu.getEnergyStored() + " FE but the device holds "
                        + generator.getEnergyStorage().getEnergyStored() + " FE");
        helper.assertTrue(menu.getMaxEnergy() == generator.kind().capacity(),
                "GUI shows capacity " + menu.getMaxEnergy() + " but the kind defines "
                        + generator.kind().capacity());
        helper.assertTrue(menu.getRate() == generator.getRate(),
                "GUI shows rate " + menu.getRate() + " but the device reports "
                        + generator.getRate());
        helper.assertTrue(menu.getBurnTotal() == generator.getBurnTimeTotal(),
                "GUI shows burn total " + menu.getBurnTotal() + " but the device reports "
                        + generator.getBurnTimeTotal());
        helper.succeed();
    }

    /** Energy, burn clock and fuel stack survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void valuesSurviveSaveAndLoad(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 16));
        tickDevice(helper, generator, 60);

        int energyBefore = generator.getEnergyStorage().getEnergyStored();
        int burnBefore = generator.getBurnTime();
        int fuelBefore = fuelCount(generator);

        CompoundTag tag = generator.saveWithoutMetadata(helper.getLevel().registryAccess());
        GenerationBlockEntity restored =
                placeGeneration(helper, new BlockPos(6, Y, 6), ModBlocks.THERMAL_GENERATOR.get());
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == energyBefore,
                "the reload restored " + restored.getEnergyStorage().getEnergyStored()
                        + " FE instead of " + energyBefore);
        helper.assertTrue(restored.getBurnTime() == burnBefore,
                "the reload restored a burn clock of " + restored.getBurnTime()
                        + " instead of " + burnBefore);
        helper.assertTrue(fuelCount(restored) == fuelBefore,
                "the reload restored a fuel stack of " + fuelCount(restored)
                        + " instead of " + fuelBefore);
        helper.succeed();
    }

    /** The hydro generator only produces with an adjacent water block. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void hydroNeedsAdjacentWater(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.HYDRO_GENERATOR.get());

        tickDevice(helper, generator, 1);
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.NO_WATER,
                "without water the device reported " + generator.getStatus()
                        + " instead of NO_WATER");
        helper.assertTrue(generator.getRate() == 0,
                "without water the device reports rate " + generator.getRate() + " instead of 0");
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 0,
                "without water the device generated "
                        + generator.getEnergyStorage().getEnergyStored() + " FE");

        helper.setBlock(new BlockPos(3, Y, 2), Blocks.WATER.defaultBlockState());
        // The condition re-check runs at the latest after CONDITION_RECHECK_TICKS (20) ticks,
        // sooner when the neighbour change invalidates the cache.
        tickDevice(helper, generator, 25);
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "with water the device reported " + generator.getStatus() + " instead of RUNNING");
        helper.assertTrue(generator.getRate() == 50,
                "with water the rate is " + generator.getRate() + " instead of the rated 50 FE/t");
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() > 0,
                "the water-driven device generated no FE at all");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 41: nuclear fuel accounting, fuel-less item capability, geothermal
    // ------------------------------------------------------------------

    /**
     * Phase 41: without cooling water the reactor outputs 200 FE/t instead of 400, so the burn
     * clock has to advance at half speed - one rod still yields its whole 72,000 FE.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void nuclearWithoutWaterKeepsTheWholeFuelContent(GameTestHelper helper) {
        GenerationBlockEntity dry =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        dry.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 1));

        tickDevice(helper, dry, 1);
        helper.assertTrue(dry.getStatus() == GenerationBlockEntity.Status.NO_WATER,
                "a dry reactor reported " + dry.getStatus() + " instead of NO_WATER");
        helper.assertTrue(dry.getRate() == 200,
                "a dry reactor runs at " + dry.getRate() + " FE/t instead of 200");

        // 72,000 FE at 200 FE/t needs 360 burn ticks.
        tickDevice(helper, dry, 400);
        helper.assertTrue(dry.getEnergyStorage().getEnergyStored() == 72_000,
                "one rod produced " + dry.getEnergyStorage().getEnergyStored()
                        + " FE without water instead of the full 72,000");
        helper.assertTrue(dry.getBurnTime() == 0,
                "the dry reactor still burns after the rod is used up: " + dry.getBurnTime());
        helper.assertTrue(fuelCount(dry) == 0, "the rod was not consumed exactly once");
        helper.assertTrue(dry.getByproduct() == 8_000L,
                "the exhaust pool is " + dry.getByproduct() + " instead of 8,000");
        helper.succeed();
    }

    /** With cooling water the same rod yields the same total in half the time. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void nuclearWithWaterProducesTheSameTotalFaster(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.WATER.defaultBlockState());
        GenerationBlockEntity wet =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        wet.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 1));

        tickDevice(helper, wet, 1);
        helper.assertTrue(wet.getRate() == 400,
                "a cooled reactor runs at " + wet.getRate() + " FE/t instead of 400");

        tickDevice(helper, wet, 180);
        helper.assertTrue(wet.getEnergyStorage().getEnergyStored() == 72_000,
                "one rod produced " + wet.getEnergyStorage().getEnergyStored()
                        + " FE with water instead of 72,000");
        helper.assertTrue(wet.getBurnTime() == 0,
                "the cooled reactor still burns after the rod is used up: " + wet.getBurnTime());
        helper.assertTrue(wet.getByproduct() == 8_000L,
                "the exhaust pool is " + wet.getByproduct() + " instead of 8,000");
        helper.succeed();
    }

    /**
     * Phase 41: only fuel-burning devices expose an item capability. A fuel-less device has no
     * slot in its GUI, so anything a pipe pushed in could never be taken out again.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void fuellessGeneratorsExposeNoItemCapability(GameTestHelper helper) {
        GenerationBlockEntity solar =
                placeGeneration(helper, GEN, ModBlocks.SOLAR_GENERATOR.get());
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(GEN), Direction.UP) == null,
                "the solar generator still exposes an item capability");
        helper.assertTrue(solar.getInventory().getStackInSlot(0).isEmpty(),
                "the solar generator started with items in a hidden slot");

        GenerationBlockEntity geothermal =
                placeGeneration(helper, new BlockPos(2, Y, 4), ModBlocks.GEOTHERMAL_GENERATOR.get());
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(new BlockPos(2, Y, 4)), Direction.NORTH) == null,
                "the geothermal generator exposes an item capability");
        helper.assertTrue(!geothermal.hasFuel(),
                "the geothermal generator reports a fuel slot");

        GenerationBlockEntity thermal =
                placeGeneration(helper, new BlockPos(2, Y, 6), ModBlocks.THERMAL_GENERATOR.get());
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(new BlockPos(2, Y, 6)), Direction.UP) != null,
                "the thermal generator lost its fuel item capability");
        helper.assertTrue(thermal.hasFuel(), "the thermal generator lost its fuel slot");
        helper.succeed();
    }

    /** Breaking a fuel device still drops its fuel stack (nothing is lost). */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void fuelGeneratorStillDropsItsFuelOnBreak(GameTestHelper helper) {
        GenerationBlockEntity thermal =
                placeGeneration(helper, GEN, ModBlocks.THERMAL_GENERATOR.get());
        thermal.getInventory().setStackInSlot(0, new ItemStack(Items.COAL, 3));
        thermal.dropContents();
        BlockPos absolute = helper.absolutePos(GEN);
        int dropped = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(absolute).inflate(2.0))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(dropped == 3,
                "the broken generator dropped " + dropped + " coal instead of 3");
        helper.succeed();
    }

    /** The geothermal generator needs an adjacent vanilla lava block. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void geothermalNeedsAdjacentLava(GameTestHelper helper) {
        GenerationBlockEntity geo =
                placeGeneration(helper, GEN, ModBlocks.GEOTHERMAL_GENERATOR.get());

        tickDevice(helper, geo, 1);
        helper.assertTrue(geo.getStatus() == GenerationBlockEntity.Status.NO_LAVA,
                "without lava the device reported " + geo.getStatus() + " instead of NO_LAVA");
        helper.assertTrue(geo.getRate() == 0,
                "without lava the rate is " + geo.getRate() + " instead of 0");
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == 0,
                "without lava the device generated "
                        + geo.getEnergyStorage().getEnergyStored() + " FE");

        helper.setBlock(new BlockPos(3, Y, 2), Blocks.LAVA.defaultBlockState());
        geo.invalidateConditions();
        tickDevice(helper, geo, 1);
        helper.assertTrue(geo.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "with lava the device reported " + geo.getStatus() + " instead of RUNNING");
        helper.assertTrue(geo.getRate() == 90,
                "with lava the rate is " + geo.getRate() + " instead of the rated 90 FE/t");
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == 90,
                "one tick with lava produced " + geo.getEnergyStorage().getEnergyStored()
                        + " FE instead of 90");
        helper.succeed();
    }

    /** Extra lava blocks never multiply the output. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void geothermalIgnoresExtraLava(GameTestHelper helper) {
        GenerationBlockEntity geo =
                placeGeneration(helper, GEN, ModBlocks.GEOTHERMAL_GENERATOR.get());
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.LAVA.defaultBlockState());
        helper.setBlock(new BlockPos(2, Y, 1), Blocks.LAVA.defaultBlockState());
        helper.setBlock(new BlockPos(2, Y - 1, 2), Blocks.LAVA.defaultBlockState());

        geo.invalidateConditions();
        tickDevice(helper, geo, 4);
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == 360,
                "four ticks with three lava blocks produced "
                        + geo.getEnergyStorage().getEnergyStored() + " FE instead of 4 x 90 = 360");
        helper.succeed();
    }

    /** Removing the lava stops the device; putting it back restarts it. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void geothermalStopsAndRestartsWithLava(GameTestHelper helper) {
        GenerationBlockEntity geo =
                placeGeneration(helper, GEN, ModBlocks.GEOTHERMAL_GENERATOR.get());
        BlockPos lavaPos = new BlockPos(3, Y, 2);
        helper.setBlock(lavaPos, Blocks.LAVA.defaultBlockState());
        geo.invalidateConditions();
        tickDevice(helper, geo, 2);
        int running = geo.getEnergyStorage().getEnergyStored();
        helper.assertTrue(running == 180, "two ticks with lava produced " + running);

        helper.setBlock(lavaPos, Blocks.AIR.defaultBlockState());
        geo.invalidateConditions();
        tickDevice(helper, geo, 2);
        helper.assertTrue(geo.getStatus() == GenerationBlockEntity.Status.NO_LAVA,
                "after losing the lava the device reported " + geo.getStatus());
        helper.assertTrue(geo.getRate() == 0, "after losing the lava the rate is " + geo.getRate());
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == running,
                "the device kept generating without lava: "
                        + geo.getEnergyStorage().getEnergyStored() + " FE");

        helper.setBlock(lavaPos, Blocks.LAVA.defaultBlockState());
        geo.invalidateConditions();
        tickDevice(helper, geo, 2);
        helper.assertTrue(geo.getStatus() == GenerationBlockEntity.Status.RUNNING,
                "after replacing the lava the device reported " + geo.getStatus());
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == running + 180,
                "after replacing the lava the device holds "
                        + geo.getEnergyStorage().getEnergyStored() + " FE instead of "
                        + (running + 180));
        helper.succeed();
    }

    /** Output goes into the neighbour and the buffer never exceeds its 100,000 FE capacity. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void geothermalPushesIntoTheBuffer(GameTestHelper helper) {
        GenerationBlockEntity geo =
                placeGeneration(helper, GEN, ModBlocks.GEOTHERMAL_GENERATOR.get());
        EnergyBufferBlockEntity buffer = placeBuffer(helper, new BlockPos(3, Y, 2));
        helper.setBlock(new BlockPos(1, Y, 2), Blocks.LAVA.defaultBlockState());
        geo.invalidateConditions();

        tickDevice(helper, geo, 10);
        helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == 900,
                "the buffer received " + buffer.getEnergyStorage().getEnergyStored()
                        + " FE instead of 10 x 90 = 900");
        helper.assertTrue(geo.getEnergyStorage().getEnergyStored() == 0,
                "the geothermal generator kept " + geo.getEnergyStorage().getEnergyStored()
                        + " FE instead of pushing it out");
        helper.succeed();
    }

    /** Energy, capacity and the cached lava state survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void geothermalSurvivesSaveAndLoad(GameTestHelper helper) {
        GenerationBlockEntity geo =
                placeGeneration(helper, GEN, ModBlocks.GEOTHERMAL_GENERATOR.get());
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.LAVA.defaultBlockState());
        geo.invalidateConditions();
        tickDevice(helper, geo, 5);

        int energyBefore = geo.getEnergyStorage().getEnergyStored();
        CompoundTag tag = geo.saveWithoutMetadata(helper.getLevel().registryAccess());
        GenerationBlockEntity restored = placeGeneration(helper, new BlockPos(2, Y, 6),
                ModBlocks.GEOTHERMAL_GENERATOR.get());
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.hasLava(), "the reload lost the cached lava state");
        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == energyBefore,
                "the reload restored " + restored.getEnergyStorage().getEnergyStored()
                        + " FE instead of " + energyBefore);
        helper.assertTrue(restored.getEnergyStorage().getMaxEnergyStored() == 100_000,
                "the reload restored a capacity of "
                        + restored.getEnergyStorage().getMaxEnergyStored());
        tickDevice(helper, restored, 1);
        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() > energyBefore,
                "the restored device did not resume generating");
        helper.succeed();
    }

    /**
     * Load probe for the new device: 100 geothermal generators on a lava floor. All of them must
     * produce exactly 90 FE/t and the whole 25-tick run must stay far below a second per tick.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void geothermalHundredDevicesStayCheap(GameTestHelper helper) {
        GenerationBlockEntity[] devices = new GenerationBlockEntity[100];
        int index = 0;
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, Y - 1, z), Blocks.LAVA.defaultBlockState());
            }
        }
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                GenerationBlockEntity device = placeGeneration(helper, new BlockPos(x, Y, z),
                        ModBlocks.GEOTHERMAL_GENERATOR.get());
                device.invalidateConditions();
                devices[index++] = device;
            }
        }

        for (GenerationBlockEntity device : devices) {
            tickDevice(helper, device, 5);
        }
        long start = System.nanoTime();
        for (int pass = 0; pass < 20; pass++) {
            for (GenerationBlockEntity device : devices) {
                tickDevice(helper, device, 1);
            }
        }
        long elapsed = System.nanoTime() - start;

        for (GenerationBlockEntity device : devices) {
            // 5 warmup + 20 measured ticks, all with an adjacent lava floor.
            helper.assertTrue(device.getEnergyStorage().getEnergyStored() == 25 * 90,
                    "a geothermal device holds " + device.getEnergyStorage().getEnergyStored()
                            + " FE instead of " + (25 * 90));
        }
        double ms = elapsed / 1_000_000.0;
        double usPerDeviceTick = elapsed / 1_000.0 / (20 * 100);
        helper.assertTrue(ms < 60_000,
                "100 geothermal generators took " + ms + " ms - pathologically slow");
        System.out.println("[Phase41-geothermal-load] 100 devices x20 passes = "
                + String.format("%.2f", ms) + " ms ("
                + String.format("%.2f", usPerDeviceTick) + " us/device-tick)");
        helper.succeed();
    }

    /** Fuel stack count in the single fuel slot. */
    private static int fuelCount(GenerationBlockEntity device) {
        return device.getInventory().getStackInSlot(GenerationBlockEntity.FUEL_SLOT).getCount();
    }

    /** Ticks the device manually so every assertion sees a known tick count. */
    private static void tickDevice(GameTestHelper helper, GenerationBlockEntity device, int times) {
        for (int index = 0; index < times; index++) {
            GenerationBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
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

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof EnergyBufferBlockEntity buffer)) {
            throw new IllegalStateException("energy buffer missing at " + pos);
        }
        return buffer;
    }
}
