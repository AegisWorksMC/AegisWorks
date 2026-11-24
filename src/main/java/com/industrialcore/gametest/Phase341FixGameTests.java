package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.energy.GenerationBalance;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 34.1 fixes in a real level: the fuel rod enters the nuclear generator and burns
 * exactly by the documented balance, the heat credit converts once (never twice), and a
 * recovery device next to neighbours without an FE capability stays silent and alive.
 *
 * <p>The same manual-tick technique as {@link GenerationGameTests}: exact assertions instead
 * of tolerances. A foreign FE storage cannot be registered as a neighbour from inside a
 * test, so the narrowing guard itself is exercised directly through
 * {@link com.industrialcore.energy.ModEnergyStorage#ownStorage}; an in-level neighbour
 * without any FE capability covers the empty-lookup path.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Phase341FixGameTests {
    private static final int Y = 6;
    private static final BlockPos GEN = new BlockPos(2, Y, 2);
    private static final BlockPos CENTER = new BlockPos(2, Y, 2);

    private Phase341FixGameTests() {
    }

    /** The rod is valid fuel, burns 180 ticks at 400 FE/t and is drawn once per cycle. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void fuelRodBurnsExactlyInNuclearGenerator(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.WATER.defaultBlockState());
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        ItemStack rod = new ItemStack(ModItems.FUEL_ROD.get());
        int content = GenerationBlockEntity.fuelContentFe(rod);
        helper.assertTrue(content == GenerationBalance.FUEL_ROD_FE,
                "one fuel rod is worth " + content + " FE instead of "
                        + GenerationBalance.FUEL_ROD_FE + " FE");
        helper.assertTrue(generator.getInventory().isItemValid(
                        GenerationBlockEntity.FUEL_SLOT, rod),
                "the nuclear fuel slot rejects the fuel rod");
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 16));
        int expectedByproduct = (int) GenerationBalance.byproductFor(content);

        tickDevice(helper, generator, 1);
        helper.assertTrue(fuelCount(generator) == 15,
                "the first tick did not consume exactly one rod, " + fuelCount(generator) + " left");
        helper.assertTrue(generator.getBurnTimeTotal() == 180,
                "one rod burns " + generator.getBurnTimeTotal() + " ticks instead of 180");
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 400,
                "the cooled reactor produced " + generator.getEnergyStorage().getEnergyStored()
                        + " FE in its first tick instead of 400");
        helper.assertTrue(generator.getByproduct() == expectedByproduct,
                "one rod pools " + generator.getByproduct() + " exhaust units instead of "
                        + expectedByproduct);

        tickDevice(helper, generator, 179);
        helper.assertTrue(generator.getBurnTime() == 0,
                "the burn clock still runs: " + generator.getBurnTime());
        helper.assertTrue(fuelCount(generator) == 15,
                "the stack shrank during the burn: " + fuelCount(generator));
        int generated = generator.getEnergyStorage().getEnergyStored();
        helper.assertTrue(generated == 72_000,
                "one rod produced " + generated + " FE instead of 72,000 (90% of 80,000)");
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.IDLE,
                "an empty-but-fueled reactor reported " + generator.getStatus()
                        + " instead of IDLE");

        tickDevice(helper, generator, 1);
        helper.assertTrue(fuelCount(generator) == 14,
                "the second rod was not drawn after the first burnt out");
        helper.assertTrue(generator.getBurnTimeTotal() == 180,
                "the second load burns " + generator.getBurnTimeTotal() + " ticks instead of 180");
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 72_400,
                "after the second load started the device holds "
                        + generator.getEnergyStorage().getEnergyStored() + " FE instead of 72,400");
        helper.succeed();
    }

    /** One rod only: after 180 ticks the device stops cleanly and invents no FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void fuelRodStopsNuclearGeneratorWhenEmpty(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.WATER.defaultBlockState());
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 1));

        tickDevice(helper, generator, 180);
        helper.assertTrue(generator.getBurnTime() == 0,
                "the single rod still burns: " + generator.getBurnTime());
        helper.assertTrue(fuelCount(generator) == 0,
                "the last rod was not consumed: " + fuelCount(generator));
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 72_000,
                "one rod produced " + generator.getEnergyStorage().getEnergyStored()
                        + " FE instead of 72,000");

        tickDevice(helper, generator, 10);
        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 72_000,
                "an empty reactor kept generating: "
                        + generator.getEnergyStorage().getEnergyStored() + " FE");
        helper.assertTrue(generator.getBurnTime() == 0,
                "an empty reactor restarted its burn clock: " + generator.getBurnTime());
        helper.assertTrue(generator.getStatus() == GenerationBlockEntity.Status.NO_FUEL,
                "an empty reactor reported " + generator.getStatus() + " instead of NO_FUEL");
        helper.assertTrue(generator.getRate() == 0,
                "an empty reactor reports rate " + generator.getRate() + " instead of 0");
        helper.succeed();
    }

    /** Energy, burn clock and the rod stack survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void fuelRodStateSurvivesSaveAndLoad(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.WATER.defaultBlockState());
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 16));
        tickDevice(helper, generator, 60);

        int energyBefore = generator.getEnergyStorage().getEnergyStored();
        int burnBefore = generator.getBurnTime();
        int fuelBefore = fuelCount(generator);

        CompoundTag tag = generator.saveWithoutMetadata(helper.getLevel().registryAccess());
        GenerationBlockEntity restored =
                placeGeneration(helper, new BlockPos(6, Y, 6), ModBlocks.NUCLEAR_GENERATOR.get());
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == energyBefore,
                "the reload restored " + restored.getEnergyStorage().getEnergyStored()
                        + " FE instead of " + energyBefore);
        helper.assertTrue(restored.getBurnTime() == burnBefore,
                "the reload restored a burn clock of " + restored.getBurnTime()
                        + " instead of " + burnBefore);
        helper.assertTrue(restored.getBurnTimeTotal() == 180,
                "the reload restored a burn total of " + restored.getBurnTimeTotal()
                        + " instead of 180");
        helper.assertTrue(fuelCount(restored) == fuelBefore,
                "the reload restored a rod stack of " + fuelCount(restored)
                        + " instead of " + fuelBefore);
        helper.succeed();
    }

    /** A 10,000 FE spend credits exactly 1,500 units, and the pool converts once (99 FE/t). */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void heatCreditIsExactAndNeverConvertedTwice(GameTestHelper helper) {
        RecoveryBlockEntity heat = placeRecovery(helper, CENTER, ModBlocks.HEAT_RECOVERY.get());
        com.industrialcore.blockentity.CrafterBlockEntity crafter =
                placeCrafter(helper, new BlockPos(3, Y, 2));
        crafter.getEnergyStorage().setEnergy(20_000);
        tickDevice(helper, heat, 1); // baseline observation of the machine's counter

        crafter.getEnergyStorage().consume(10_000);
        int before = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored();
        tickDevice(helper, heat, 1);

        // Credit = 15% of 10,000 = 1,500 units; one conversion drains exactly 117 of them,
        // so the pool shows 1,383. An absent or wrong credit shows up here immediately.
        helper.assertTrue(heat.getHeatPool() == 1_383,
                "a 10,000 FE spend left " + heat.getHeatPool()
                        + " heat units, expected 1,500 credit - 117 converted = 1,383");
        int after = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored();
        int gained = after - before;
        helper.assertTrue(gained == 99,
                "one conversion credited " + gained + " FE instead of the single 99"
                        + " (double counting would be 198)");
        helper.assertTrue(gained <= 100,
                "the conversion exceeded the 100 FE/t rating: " + gained);

        // The next tick converts the same single batch again: steady 99 FE/t, pool -117.
        int beforeSecond = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored();
        tickDevice(helper, heat, 1);
        helper.assertTrue(heat.getHeatPool() == 1_266,
                "the second tick drained the pool to " + heat.getHeatPool()
                        + " instead of 1,266 (117 units per tick)");
        int secondGained = crafter.getEnergyStorage().getEnergyStored()
                + heat.getEnergyStorage().getEnergyStored() - beforeSecond;
        helper.assertTrue(secondGained == 99,
                "the second conversion credited " + secondGained + " FE instead of 99");
        helper.succeed();
    }

    /** Neighbours without an FE capability are observed as null: no crash, no credit. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void recoveryIgnoresNeighboursWithoutEnergyCapability(GameTestHelper helper) {
        RecoveryBlockEntity heat = placeRecovery(helper, CENTER, ModBlocks.HEAT_RECOVERY.get());
        for (BlockPos offset : new BlockPos[] {new BlockPos(1, Y, 2), new BlockPos(3, Y, 2),
                new BlockPos(2, Y, 1), new BlockPos(2, Y, 3), new BlockPos(2, Y + 1, 2)}) {
            helper.setBlock(offset, Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(new BlockPos(2, Y - 1, 2), Blocks.STONE.defaultBlockState());

        tickDevice(helper, heat, 60);
        helper.assertTrue(heat.getHeatPool() == 0,
                "a capability-less neighbourhood credited " + heat.getHeatPool() + " heat units");
        helper.assertTrue(heat.getRegenPool() == 0,
                "a capability-less neighbourhood credited " + heat.getRegenPool() + " regen units");
        helper.assertTrue(heat.getEnergyStorage().getEnergyStored() == 0,
                "a capability-less neighbourhood produced "
                        + heat.getEnergyStorage().getEnergyStored() + " FE");
        helper.succeed();
    }

    /** A foreign FE storage implementation narrows to null instead of throwing. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void foreignEnergyStorageIsIgnoredWithoutCrashing(GameTestHelper helper) {
        IEnergyStorage foreign = new ForeignStorage();
        helper.assertTrue(com.industrialcore.energy.ModEnergyStorage.ownStorage(foreign) == null,
                "a foreign IEnergyStorage was not ignored - ClassCastException territory");
        helper.assertTrue(foreign.getEnergyStored() == 5_000,
                "observing a foreign storage modified it to " + foreign.getEnergyStored());
        helper.assertTrue(com.industrialcore.energy.ModEnergyStorage.ownStorage(null) == null,
                "an empty capability lookup did not yield null");
        com.industrialcore.energy.ModEnergyStorage own =
                new com.industrialcore.energy.ModEnergyStorage(20_000, 500);
        helper.assertTrue(com.industrialcore.energy.ModEnergyStorage.ownStorage(own) == own,
                "this mod's own storage was not returned unchanged");
        own.receiveEnergy(1_000, false);
        own.consume(400);
        helper.assertTrue(own.getConsumedTotal() == 400L,
                "the spend counter changed to " + own.getConsumedTotal() + " while narrowing");
        helper.succeed();
    }

    /**
     * Phase 42 (audit P1): a reactor without cooling water runs at half the rate but still gets the
     * full fuel content. The burn clock advances by produced FE, not by ticks, so 360 ticks at
     * 200 FE/t deliver the same 72,000 FE as 180 ticks at 400 FE/t.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void uncooledReactorKeepsTheFullFuelContent(GameTestHelper helper) {
        GenerationBlockEntity generator =
                placeGeneration(helper, GEN, ModBlocks.NUCLEAR_GENERATOR.get());
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(ModItems.FUEL_ROD.get(), 1));

        tickDevice(helper, generator, 360);

        helper.assertTrue(generator.getEnergyStorage().getEnergyStored() == 72_000,
                "an uncooled reactor produced "
                        + generator.getEnergyStorage().getEnergyStored()
                        + " FE instead of the full 72,000 (36,000 would mean half the fuel was lost)");
        helper.assertTrue(generator.getBurnTime() == 0,
                "the rod is still burning after 360 uncooled ticks: " + generator.getBurnTime());
        helper.succeed();
    }

    /**
     * Phase 42 (audit P4): a wind turbine with no open block above it produces nothing and reports
     * {@code NO_AIR} instead of the old 18 FE/t floor.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 120)
    public static void windTurbineFollowsItsOpenAirColumn(GameTestHelper helper) {
        // The formula is the contract: zero open blocks is zero output, and a blocked device is
        // reported as NO_AIR instead of silently paying the old 18 FE/t floor.
        helper.assertTrue(GenerationBalance.windOutput(0) == 0,
                "zero open blocks still yields " + GenerationBalance.windOutput(0) + " FE/t");
        helper.assertTrue(GenerationBalance.windOutput(1) >= GenerationBalance.WIND_MIN_OUTPUT
                        && GenerationBalance.windOutput(1) <= GenerationBalance.WIND_MAX_OUTPUT,
                "one open block yields " + GenerationBalance.windOutput(1)
                        + " FE/t, outside the documented 18..70 range");
        helper.assertTrue(GenerationBalance.windOutput(GenerationBalance.WIND_AIR_COLUMNS)
                        == GenerationBalance.WIND_MAX_OUTPUT,
                "a fully open column yields "
                        + GenerationBalance.windOutput(GenerationBalance.WIND_AIR_COLUMNS)
                        + " FE/t instead of " + GenerationBalance.WIND_MAX_OUTPUT);

        GenerationBlockEntity turbine =
                placeGeneration(helper, GEN, ModBlocks.WIND_GENERATOR.get());
        tickDevice(helper, turbine, 20);

        // The device must derive its rate from the column it can actually see. The test structure
        // has its own ceiling, so the open count is measured here instead of assumed.
        int open = 0;
        BlockPos column = turbine.getBlockPos().above();
        for (int step = 0; step < GenerationBalance.WIND_AIR_COLUMNS; step++) {
            if (turbine.getLevel().getBlockState(column.above(step)).isAir()) {
                open++;
            }
        }
        int expected = GenerationBalance.windOutput(open);
        helper.assertTrue(turbine.getRate() == expected,
                "a turbine with " + open + " open blocks above reports " + turbine.getRate()
                        + " FE/t instead of " + expected);
        if (open == 0) {
            helper.assertTrue(turbine.getStatus() == GenerationBlockEntity.Status.NO_AIR,
                    "a blocked turbine reports " + turbine.getStatus() + " instead of NO_AIR");
        } else {
            helper.assertTrue(turbine.getStatus() == GenerationBlockEntity.Status.RUNNING,
                    "a turbine with airflow reports " + turbine.getStatus()
                            + " instead of RUNNING");
        }
        helper.succeed();
    }

    /** Another mod's FE storage: same interface, entirely different class. */
    private static final class ForeignStorage implements IEnergyStorage {
        private int energy = 5_000;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = Math.min(getMaxEnergyStored() - energy, maxReceive);
            if (!simulate) {
                energy += received;
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(energy, maxExtract);
            if (!simulate) {
                energy -= extracted;
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return 10_000;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
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

    private static void tickDevice(GameTestHelper helper, RecoveryBlockEntity device, int times) {
        for (int index = 0; index < times; index++) {
            RecoveryBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
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

    private static RecoveryBlockEntity placeRecovery(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof RecoveryBlockEntity device)) {
            throw new IllegalStateException("recovery device missing at " + pos);
        }
        return device;
    }

    private static com.industrialcore.blockentity.CrafterBlockEntity placeCrafter(
            GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRAFTER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof com.industrialcore.blockentity.CrafterBlockEntity crafter)) {
            throw new IllegalStateException("crafter missing at " + pos);
        }
        return crafter;
    }
}
