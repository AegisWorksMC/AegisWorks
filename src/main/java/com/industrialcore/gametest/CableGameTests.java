package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.CableTier;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for the Phase 5.1 FE cables.
 *
 * <p>They verify the real capacities (not just names), that a mixed-tier network is limited by its
 * lowest tier, that energy is conserved when a target cannot accept everything, and that a
 * generator still powers a machine through a cable.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CableGameTests {
    private static final int Y = 6;

    private CableGameTests() {}

    /** Each tier accepts its documented throughput from one receive call. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void cableTiersAcceptTheirThroughput(GameTestHelper helper) {
        int[] accepted = new int[CableTier.values().length];
        for (int index = 0; index < CableTier.values().length; index++) {
            CableBlockEntity cable = placeCable(helper, new BlockPos(1 + index, Y, 1), CableTier.byIndex(index));
            accepted[index] = cable.getEnergyStorage().receiveEnergy(1_000_000, false);
            helper.assertTrue(accepted[index] == CableTier.byIndex(index).throughput(),
                    "tier " + index + " accepted " + accepted[index] + " instead of "
                            + CableTier.byIndex(index).throughput());
        }
        for (int index = 1; index < accepted.length; index++) {
            helper.assertTrue(accepted[index] > accepted[index - 1],
                    "cable tier " + index + " is not faster than tier " + (index - 1));
        }
        IndustrialCore.LOGGER.info("[perf] cable accepted FE/tick: {}", java.util.Arrays.toString(accepted));
        helper.succeed();
    }

    /** Basic + Ultimate in one line: the line moves at Basic speed and conserves FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void mixedCableNetworkUsesLowestTier(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 1));
        CableBlockEntity basic = placeCable(helper, new BlockPos(2, Y, 1), CableTier.BASIC);
        CableBlockEntity ultimate = placeCable(helper, new BlockPos(3, Y, 1), CableTier.ULTIMATE);
        // Build the network before asserting its cached values.
        tickCable(helper, basic);
        tickCable(helper, ultimate);
        helper.assertTrue(basic.getNetworkSizeForTesting() == 2,
                "the two cables did not form one network: " + basic.getNetworkSizeForTesting());
        helper.assertTrue(basic.getNetworkThroughputForTesting() == CableTier.BASIC.throughput(),
                "the network bottleneck is " + basic.getNetworkThroughputForTesting()
                        + " instead of " + CableTier.BASIC.throughput());

        // A Basic cable buffer holds 512 FE (four ticks of throughput), so use a value it can hold.
        basic.getEnergyStorage().setEnergy(500);
        tickCable(helper, basic);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == CableTier.BASIC.throughput(),
                "the quarry received " + quarry.getEnergyStoredForTesting() + " instead of "
                        + CableTier.BASIC.throughput());
        helper.assertTrue(basic.getEnergyStoredForTesting() + ultimate.getEnergyStoredForTesting()
                        + quarry.getEnergyStoredForTesting() == 500,
                "FE was lost or duplicated in the cable network");
        helper.succeed();
    }

    /** A full target leaves the unaccepted FE in the cable buffer instead of deleting it. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void cableKeepsEnergyTargetCannotAccept(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 1));
        CableBlockEntity cable = placeCable(helper, new BlockPos(2, Y, 1), CableTier.ULTIMATE);
        int capacity = quarry.getEnergyStorage().getMaxEnergyStored();
        quarry.setEnergyStored(capacity - 10);
        cable.getEnergyStorage().setEnergy(1_000);

        tickCable(helper, cable);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == capacity,
                "the quarry did not fill up: " + quarry.getEnergyStoredForTesting() + " of " + capacity);
        helper.assertTrue(cable.getEnergyStoredForTesting() == 990,
                "the cable deleted FE the target could not accept: " + cable.getEnergyStoredForTesting());
        helper.succeed();
    }

    /** Generator -> Basic cable -> quarry, with the real block tickers. */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void generatorPowersQuarryThroughCable(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(1, Y, 1);
        helper.setBlock(generatorPos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                .setValue(GenerationBlock.FACING, Direction.EAST));
        BlockEntity generatorEntity = helper.getLevel().getBlockEntity(helper.absolutePos(generatorPos));
        if (!(generatorEntity instanceof GenerationBlockEntity generator)) {
            helper.fail("generator block entity missing");
            return;
        }
        CableBlockEntity cable = placeCable(helper, new BlockPos(2, Y, 1), CableTier.BASIC);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(3, Y, 1));
        helper.setBlock(new BlockPos(4, Y, 1), Blocks.STONE);
        quarry.applyRange(1, 1, 1);
        quarry.setRunning(true);
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 1));

        for (int tick = 0; tick < 300 && !helper.getBlockState(new BlockPos(4, Y, 1)).isAir(); tick++) {
            tickGenerator(helper, generator);
            tickCable(helper, cable);
            BlockState state = quarry.getBlockState();
            QuarryBlockEntity.serverTick(helper.getLevel(), quarry.getBlockPos(), state, quarry);
        }
        helper.assertTrue(generator.getBurnTime() > 0, "the generator did not burn fuel");
        helper.assertTrue(helper.getBlockState(new BlockPos(4, Y, 1)).isAir(),
                "the cable did not power the quarry: quarryFE=" + quarry.getEnergyStoredForTesting()
                        + " cableFE=" + cable.getEnergyStoredForTesting());
        helper.succeed();
    }

    /**
     * Three generators (3 x 40 FE/t) keep one stage-15 synthesizer at full speed.
     *
     * <p>Stage 15 draws 120 FE/t for 10 ticks, so three generators are the smallest bank that
     * can feed one such machine. That is the per-machine figure behind the design statement
     * "a five-synthesizer stage-15 peak of 600 FE/t needs fifteen generators": this test pins the
     * supply side of it against the real generator, cable and synthesizer tickers.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void threeGeneratorsKeepOneStageFifteenSynthesizerAtFullSpeed(
            GameTestHelper helper) {
        OreSynthesizerBlockEntity synthesizer = placeStageFifteenSynthesizer(helper,
                new BlockPos(2, Y, 1));
        CableBlockEntity cable = placeCable(helper, new BlockPos(2, Y, 2), CableTier.ADVANCED);
        List<GenerationBlockEntity> generators = List.of(
                placeGenerator(helper, new BlockPos(1, Y, 2)),
                placeGenerator(helper, new BlockPos(2, Y, 3)),
                placeGenerator(helper, new BlockPos(3, Y, 2)));

        int ticks = runPower(helper, generators, cable, synthesizer, 60);
        IndustrialCore.LOGGER.info("[perf] 3 generators (120 FE/t) -> stage 15 (120 FE/t, 10 t): "
                        + "{} ticks, synthesizer FE {}, cable FE {}",
                ticks, synthesizer.getEnergyStoredForTesting(),
                cable.getEnergyStoredForTesting());
        helper.assertTrue(ticks > 0, "three generators did not finish a 10 tick stage-15 run in "
                + "60 ticks: synthesizerFE=" + synthesizer.getEnergyStoredForTesting()
                + " cableFE=" + cable.getEnergyStoredForTesting());
        helper.assertTrue(ticks <= 20, "three generators needed " + ticks
                + " ticks for a 10 tick run, so the supply was short");
        helper.succeed();
    }

    /**
     * One generator (40 FE/t) cannot keep a 120 FE/t stage-15 run at full speed, but the run is
     * not lost: the machine waits, then continues from the progress it had.
     *
     * <p>This is the starvation case of the power budget. The measured makespan must be about
     * 120 / 40 = three times the recipe time, and the craft must still finish with both inputs
     * consumed exactly once.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void oneGeneratorCannotKeepUpButTheRunResumes(GameTestHelper helper) {
        OreSynthesizerBlockEntity synthesizer = placeStageFifteenSynthesizer(helper,
                new BlockPos(2, Y, 1));
        CableBlockEntity cable = placeCable(helper, new BlockPos(2, Y, 2), CableTier.ADVANCED);
        List<GenerationBlockEntity> generators = List.of(
                placeGenerator(helper, new BlockPos(1, Y, 2)));

        int ticks = runPower(helper, generators, cable, synthesizer, 120);
        IndustrialCore.LOGGER.info("[perf] 1 generator (40 FE/t) -> stage 15 (120 FE/t, 10 t): "
                        + "{} ticks (3x the recipe time is the expected shortage)", ticks);
        helper.assertTrue(ticks > 0, "one generator never finished the run in 120 ticks");
        helper.assertTrue(ticks > 15, "one 40 FE/t generator finished a 120 FE/t run in " + ticks
                + " ticks, so the draw was not the documented one");
        helper.assertTrue(ticks <= 60, "one generator needed " + ticks
                + " ticks, far more than the expected three times the 10 tick recipe");
        helper.succeed();
    }

    /** Cable energy survives a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void cableEnergySurvivesSaveAndLoad(GameTestHelper helper) {
        CableBlockEntity cable = placeCable(helper, new BlockPos(1, Y, 1), CableTier.ULTIMATE);
        cable.getEnergyStorage().setEnergy(12_345);
        CompoundTag tag = cable.saveWithoutMetadata(helper.getLevel().registryAccess());
        CableBlockEntity restored = placeCable(helper, new BlockPos(4, Y, 1), CableTier.ULTIMATE);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getEnergyStoredForTesting() == 12_345,
                "the cable restored " + restored.getEnergyStoredForTesting() + " instead of 12,345 FE");
        helper.succeed();
    }

    /**
     * A network of several cables still has exactly one distributor and moves its pooled energy
     * only once per tick. With the old 1024-cable cut-off a truncated search produced several
     * "leaders" (one per partial network), which would show up here as a doubled transfer.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void oneLeaderDistributesOncePerNetwork(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 1));
        CableBlockEntity[] cables = new CableBlockEntity[5];
        for (int index = 0; index < cables.length; index++) {
            cables[index] = placeCable(helper, new BlockPos(2 + index, Y, 1), CableTier.ULTIMATE);
        }
        for (CableBlockEntity cable : cables) {
            tickCable(helper, cable);
        }
        int leaders = 0;
        for (CableBlockEntity cable : cables) {
            helper.assertTrue(cable.isNetworkCompleteForTesting(),
                    "a five-cable network must not be reported as truncated");
            helper.assertTrue(cable.getNetworkSizeForTesting() == cables.length,
                    "a cable saw " + cable.getNetworkSizeForTesting() + " of " + cables.length + " cables");
            if (cable.isNetworkLeaderForTesting()) {
                leaders++;
            }
        }
        helper.assertTrue(leaders == 1, "the network elected " + leaders + " leaders instead of 1");

        for (CableBlockEntity cable : cables) {
            cable.getEnergyStorage().setEnergy(1_000);
        }
        for (CableBlockEntity cable : cables) {
            tickCable(helper, cable);
        }
        // The quarry accepts at most its own receive limit per tick, which is exactly what one
        // distribution may deliver. A second (erroneous) leader would double this value.
        int accepted = Math.min(Config.maxEnergyReceive(), 5_000);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == accepted,
                "the quarry received " + quarry.getEnergyStoredForTesting()
                        + " instead of " + accepted + " FE from one distribution");
        helper.assertTrue(totalEnergy(cables, quarry) == 5_000,
                "FE was duplicated or lost in a multi-cable network");
        helper.succeed();
    }

    /** Adding a Basic cable lowers the whole network, removing it restores the Ultimate capacity. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void capacityFollowsTheLowestTierAndRecovers(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 1));
        CableBlockEntity ultimate = placeCable(helper, new BlockPos(2, Y, 1), CableTier.ULTIMATE);
        CableBlockEntity basic = placeCable(helper, new BlockPos(3, Y, 1), CableTier.BASIC);
        tickCable(helper, ultimate);
        tickCable(helper, basic);
        helper.assertTrue(ultimate.getNetworkThroughputForTesting() == CableTier.BASIC.throughput(),
                "mixed network throughput is " + ultimate.getNetworkThroughputForTesting());

        ultimate.getEnergyStorage().setEnergy(1_000);
        // A Basic cable buffer only holds four ticks (512 FE), so setEnergy clamps this value.
        basic.getEnergyStorage().setEnergy(1_000);
        int mixedBefore = ultimate.getEnergyStoredForTesting() + basic.getEnergyStoredForTesting();
        tickCable(helper, ultimate);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == CableTier.BASIC.throughput(),
                "the mixed network moved " + quarry.getEnergyStoredForTesting()
                        + " instead of " + CableTier.BASIC.throughput());
        int mixedTotal = totalEnergy(new CableBlockEntity[]{ultimate, basic}, quarry);
        helper.assertTrue(mixedTotal == mixedBefore,
                "FE was duplicated or lost in the mixed network: total=" + mixedTotal
                        + " before=" + mixedBefore
                        + " (ultimate=" + ultimate.getEnergyStoredForTesting()
                        + ", basic=" + basic.getEnergyStoredForTesting()
                        + ", quarry=" + quarry.getEnergyStoredForTesting() + ")");

        helper.setBlock(new BlockPos(3, Y, 1), Blocks.AIR);
        tickCable(helper, ultimate);
        helper.assertTrue(ultimate.getNetworkSizeForTesting() == 1,
                "the removed cable is still part of the network: " + ultimate.getNetworkSizeForTesting());
        helper.assertTrue(ultimate.getNetworkThroughputForTesting() == CableTier.ULTIMATE.throughput(),
                "the capacity did not recover after removing the Basic cable: "
                        + ultimate.getNetworkThroughputForTesting());
        ultimate.getEnergyStorage().setEnergy(1_000);
        int before = quarry.getEnergyStoredForTesting();
        tickCable(helper, ultimate);
        int expectedGain = Math.min(Config.maxEnergyReceive(), 1_000);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == before + expectedGain,
                "the recovered network moved " + (quarry.getEnergyStoredForTesting() - before)
                        + " instead of " + expectedGain);
        helper.succeed();
    }

    /** A broken cable splits the network; placing it again merges and re-elects one leader. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void networkSplitsAndMergesWithoutStaleCache(GameTestHelper helper) {
        CableBlockEntity left = placeCable(helper, new BlockPos(2, Y, 2), CableTier.ADVANCED);
        CableBlockEntity middle = placeCable(helper, new BlockPos(3, Y, 2), CableTier.ADVANCED);
        CableBlockEntity right = placeCable(helper, new BlockPos(4, Y, 2), CableTier.ADVANCED);
        tickCable(helper, left);
        helper.assertTrue(left.getNetworkSizeForTesting() == 3,
                "expected one network of three cables, got " + left.getNetworkSizeForTesting());

        helper.setBlock(new BlockPos(3, Y, 2), Blocks.AIR);
        tickCable(helper, left);
        tickCable(helper, right);
        helper.assertTrue(left.getNetworkSizeForTesting() == 1,
                "the left cable kept the old three-cable network: " + left.getNetworkSizeForTesting());
        helper.assertTrue(right.getNetworkSizeForTesting() == 1,
                "the right cable kept the old three-cable network: " + right.getNetworkSizeForTesting());
        helper.assertTrue(left.isNetworkLeaderForTesting() && right.isNetworkLeaderForTesting(),
                "each split network needs its own leader");
        helper.assertTrue(middle.isRemoved(), "the removed cable block entity is still attached");

        CableBlockEntity rejoined = placeCable(helper, new BlockPos(3, Y, 2), CableTier.BASIC);
        tickCable(helper, left);
        tickCable(helper, rejoined);
        helper.assertTrue(left.getNetworkSizeForTesting() == 3,
                "the merged network has " + left.getNetworkSizeForTesting() + " cables instead of 3");
        helper.assertTrue(left.getNetworkThroughputForTesting() == CableTier.BASIC.throughput(),
                "the merged network ignored the new Basic cable: " + left.getNetworkThroughputForTesting());
        int leaders = 0;
        for (CableBlockEntity cable : new CableBlockEntity[]{left, rejoined, right}) {
            if (cable.isNetworkLeaderForTesting()) {
                leaders++;
            }
        }
        helper.assertTrue(leaders == 1, "the merged network elected " + leaders + " leaders");
        helper.succeed();
    }

    /** A cable break must not leave energy behind in a detached network that is still drained. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void splitNetworkNeverDoubleDistributes(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 1));
        CableBlockEntity first = placeCable(helper, new BlockPos(2, Y, 1), CableTier.BASIC);
        CableBlockEntity second = placeCable(helper, new BlockPos(3, Y, 1), CableTier.BASIC);
        tickCable(helper, first);
        helper.setBlock(new BlockPos(3, Y, 1), Blocks.AIR);
        tickCable(helper, first);
        first.getEnergyStorage().setEnergy(300);
        tickCable(helper, first);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == CableTier.BASIC.throughput(),
                "the detached cable was still distributed: quarry has "
                        + quarry.getEnergyStoredForTesting() + " FE instead of "
                        + CableTier.BASIC.throughput());
        helper.assertTrue(second.getEnergyStoredForTesting() == 0,
                "the detached cable kept distributing: " + second.getEnergyStoredForTesting() + " FE");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static CableBlockEntity placeCable(GameTestHelper helper, BlockPos pos, CableTier tier) {
        Block block = switch (tier) {
            case BASIC -> ModBlocks.CABLE_BASIC.get();
            case IMPROVED -> ModBlocks.CABLE_IMPROVED.get();
            case ADVANCED -> ModBlocks.CABLE_ADVANCED.get();
            case ULTIMATE -> ModBlocks.CABLE_ULTIMATE.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof CableBlockEntity cable)) {
            helper.fail("cable block entity missing at " + pos);
            return null;
        }
        return cable;
    }

    private static QuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.QUARRY.get().defaultBlockState()
                .setValue(QuarryBlock.FACING, Direction.EAST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            helper.fail("quarry block entity missing at " + pos);
            return null;
        }
        return quarry;
    }

    private static int totalEnergy(CableBlockEntity[] cables, QuarryBlockEntity quarry) {
        int total = quarry.getEnergyStoredForTesting();
        for (CableBlockEntity cable : cables) {
            total += cable.getEnergyStoredForTesting();
        }
        return total;
    }

    private static void tickCable(GameTestHelper helper, CableBlockEntity cable) {
        CableBlockEntity.serverTick(helper.getLevel(), cable.getBlockPos(), cable.getBlockState(), cable);
    }

    private static void tickGenerator(GameTestHelper helper, GenerationBlockEntity generator) {
        GenerationBlockEntity.serverTick(helper.getLevel(), generator.getBlockPos(),
                generator.getBlockState(), generator);
    }

    /** A synthesizer loaded with the stage-15 iron recipe (32 stabilized + 1 ultimate). */
    private static OreSynthesizerBlockEntity placeStageFifteenSynthesizer(GameTestHelper helper,
                                                                          BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof OreSynthesizerBlockEntity synthesizer)) {
            helper.fail("synthesizer block entity missing at " + pos);
            return null;
        }
        synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START,
                new ItemStack(ModItems.IRON_STABILIZED.get(), 32));
        synthesizer.getInventory().setStackInSlot(OreSynthesizerBlockEntity.INPUT_START + 1,
                new ItemStack(ModItems.SYNTHETIC_ORE_ULTIMATE.get(), 1));
        return synthesizer;
    }

    /** A generator with a coal stack in its fuel slot. */
    private static GenerationBlockEntity placeGenerator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                .setValue(GenerationBlock.FACING, Direction.EAST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof GenerationBlockEntity generator)) {
            helper.fail("generator block entity missing at " + pos);
            return null;
        }
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL, 8));
        return generator;
    }

    /**
     * Ticks generators, then the cable, then the synthesizer, and returns the tick the run
     * finished on (-1 if it did not finish within {@code limit}).
     */
    private static int runPower(GameTestHelper helper, List<GenerationBlockEntity> generators,
                                CableBlockEntity cable, OreSynthesizerBlockEntity synthesizer,
                                int limit) {
        for (int tick = 1; tick <= limit; tick++) {
            for (GenerationBlockEntity generator : generators) {
                tickGenerator(helper, generator);
            }
            tickCable(helper, cable);
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), synthesizer.getBlockPos(),
                    synthesizer.getBlockState(), synthesizer);
            if (!synthesizer.getInventory()
                    .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT).isEmpty()) {
                return tick;
            }
        }
        return -1;
    }
}
