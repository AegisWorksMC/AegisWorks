package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.CableTier;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8 GameTests: cable networks without a size cap.
 *
 * <p>The 4,096-cable limit and even larger networks are covered by unit tests
 * ({@code CableNetworkTraversalTest}, up to 65,536 nodes), because a GameTest structure is only
 * 12x12x12 and cannot hold thousands of real blocks. These tests exercise the part a unit test
 * cannot: real {@link CableBlockEntity} instances, real neighbour scans, chunk loading checks,
 * capability lookups, block removal and the server tick path. The largest network here is 120
 * real cable blocks.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CableScaleGameTests {
    private static final int Y = 6;

    private CableScaleGameTests() {}

    /**
     * A rebuild is spread over ticks: no member distributes before the scan finished, no other
     * cable starts a competing scan, and the finished network is complete and single-leader.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void rebuildIsIncrementalAndSingleOwner(GameTestHelper helper) {
        withBudget(8, () -> {
            List<CableBlockEntity> cables = placeWall(helper, 10, 4, 1, CableTier.ULTIMATE);
            CableBlockEntity owner = cables.get(0);

            tickCable(helper, owner);
            helper.assertTrue(!owner.isNetworkStableForTesting(),
                    "a 40-cable network must not be reported as complete after 8 visited nodes");
            helper.assertTrue(owner.getNetworkSizeForTesting() == 0,
                    "no snapshot may be published while the scan is incomplete");
            helper.assertTrue(owner.getRebuildNodeCountForTesting() == 8,
                    "the first slice visited " + owner.getRebuildNodeCountForTesting() + " nodes instead of 8");

            // A second cable must wait for the running scan instead of scanning the same component.
            CableBlockEntity last = cables.get(cables.size() - 1);
            tickCable(helper, last);
            helper.assertTrue(last.getRebuildStepCountForTesting() == 0,
                    "a waiting cable consumed rebuild budget: " + last.getRebuildStepCountForTesting());
            helper.assertTrue(owner.getRebuildNodeCountForTesting() == 8,
                    "a second scan duplicated the work: " + owner.getRebuildNodeCountForTesting() + " nodes");

            int ticks = 1;
            while (!owner.isNetworkStableForTesting() && ticks < 100) {
                tickCable(helper, owner);
                ticks++;
            }
            helper.assertTrue(owner.isNetworkStableForTesting(), "the scan never finished");
            helper.assertTrue(ticks == 5, "40 nodes at 8 per tick should take 5 ticks but took " + ticks);
            helper.assertTrue(owner.getRebuildNodeCountForTesting() == 40,
                    "the scan visited " + owner.getRebuildNodeCountForTesting() + " nodes instead of 40");

            int leaders = 0;
            for (CableBlockEntity cable : cables) {
                helper.assertTrue(cable.getNetworkSizeForTesting() == cables.size(),
                        "cable sees " + cable.getNetworkSizeForTesting() + " of " + cables.size() + " cables");
                if (cable.isNetworkLeaderForTesting()) {
                    leaders++;
                }
            }
            helper.assertTrue(leaders == 1, "the finished network elected " + leaders + " leaders");
        });
        helper.succeed();
    }

    /** Energy put into a cable while its network is being rebuilt is neither sent nor lost. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void distributionWaitsForRebuildWithoutLosingEnergy(GameTestHelper helper) {
        withBudget(6, () -> {
            QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(0, Y, 3));
            List<CableBlockEntity> cables = placeWall(helper, 10, 4, 3, CableTier.ULTIMATE);
            CableBlockEntity owner = cables.get(0);
            owner.getEnergyStorage().setEnergy(1_000);

            for (int tick = 0; tick < 3; tick++) {
                tickCable(helper, owner);
            }
            helper.assertTrue(!owner.isNetworkStableForTesting(), "the rebuild finished too early for this test");
            helper.assertTrue(quarry.getEnergyStoredForTesting() == 0,
                    "an incomplete network distributed " + quarry.getEnergyStoredForTesting() + " FE");
            helper.assertTrue(totalEnergy(cables) == 1_000,
                    "FE was lost while rebuilding: " + totalEnergy(cables));

            while (!owner.isNetworkStableForTesting()) {
                tickCable(helper, owner);
            }
            tickCable(helper, owner);
            int expected = Math.min(1_000, Math.min(CableTier.ULTIMATE.throughput(), Config.maxEnergyReceive()));
            helper.assertTrue(quarry.getEnergyStoredForTesting() == expected,
                    "after the rebuild the quarry holds " + quarry.getEnergyStoredForTesting()
                            + " instead of " + expected);
            helper.assertTrue(totalEnergy(cables) + quarry.getEnergyStoredForTesting() == 1_000,
                    "energy was duplicated or lost across the rebuild");
        });
        helper.succeed();
    }

    /** After a split both halves rebuild their own component and distribute independently. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void splitHalvesDistributeIndependently(GameTestHelper helper) {
        QuarryBlockEntity leftQuarry = placeQuarry(helper, new BlockPos(0, Y, 6));
        QuarryBlockEntity rightQuarry = placeQuarry(helper, new BlockPos(6, Y, 6));
        CableBlockEntity[] cables = new CableBlockEntity[5];
        for (int index = 0; index < cables.length; index++) {
            cables[index] = placeCable(helper, new BlockPos(1 + index, Y, 6), CableTier.BASIC);
        }
        tickCable(helper, cables[0]);
        helper.assertTrue(cables[0].getNetworkSizeForTesting() == 5,
                "expected one network of five cables, got " + cables[0].getNetworkSizeForTesting());

        helper.setBlock(new BlockPos(3, Y, 6), Blocks.AIR);
        for (CableBlockEntity cable : cables) {
            tickCable(helper, cable);
        }
        helper.assertTrue(cables[0].getNetworkSizeForTesting() == 2,
                "the left half has " + cables[0].getNetworkSizeForTesting() + " cables instead of 2");
        helper.assertTrue(cables[4].getNetworkSizeForTesting() == 2,
                "the right half has " + cables[4].getNetworkSizeForTesting() + " cables instead of 2");
        helper.assertTrue(cables[0].isNetworkLeaderForTesting(),
                "the left half did not elect its own leader");
        helper.assertTrue(cables[3].isNetworkLeaderForTesting(),
                "the right half did not elect its own leader");

        cables[0].getEnergyStorage().setEnergy(100);
        cables[4].getEnergyStorage().setEnergy(100);
        for (CableBlockEntity cable : cables) {
            tickCable(helper, cable);
        }
        helper.assertTrue(leftQuarry.getEnergyStoredForTesting() == 100,
                "the left half delivered " + leftQuarry.getEnergyStoredForTesting() + " FE");
        helper.assertTrue(rightQuarry.getEnergyStoredForTesting() == 100,
                "the right half delivered " + rightQuarry.getEnergyStoredForTesting() + " FE");
        helper.assertTrue(totalEnergy(cables) + leftQuarry.getEnergyStoredForTesting()
                        + rightQuarry.getEnergyStoredForTesting() == 200,
                "the split networks duplicated or lost FE");
        helper.succeed();
    }

    /** Merging two networks must not let two leaders distribute the same pool twice. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void mergedNetworkDistributesOnlyOncePerTick(GameTestHelper helper) {
        withBudget(4, () -> {
            QuarryBlockEntity leftQuarry = placeQuarry(helper, new BlockPos(0, Y, 9));
            QuarryBlockEntity rightQuarry = placeQuarry(helper, new BlockPos(6, Y, 9));
            List<CableBlockEntity> cables = new ArrayList<>();
            cables.add(placeCable(helper, new BlockPos(1, Y, 9), CableTier.ULTIMATE));
            cables.add(placeCable(helper, new BlockPos(2, Y, 9), CableTier.ULTIMATE));
            cables.add(placeCable(helper, new BlockPos(4, Y, 9), CableTier.ULTIMATE));
            cables.add(placeCable(helper, new BlockPos(5, Y, 9), CableTier.ULTIMATE));
            for (CableBlockEntity cable : cables) {
                tickCable(helper, cable);
            }
            helper.assertTrue(cables.get(0).getNetworkSizeForTesting() == 2,
                    "the left network is not independent: " + cables.get(0).getNetworkSizeForTesting());

            // Connect both halves; the rebuild is deliberately sliced so the merge is not instant.
            CableBlockEntity bridge = placeCable(helper, new BlockPos(3, Y, 9), CableTier.ULTIMATE);
            cables.add(bridge);
            // The owner keeps stepping the sliced rebuild; the other cables just wait.
            for (int tick = 0; tick < 20 && !cables.get(0).isNetworkStableForTesting(); tick++) {
                tickCable(helper, cables.get(0));
            }
            helper.assertTrue(cables.get(0).isNetworkStableForTesting(), "the merged network never rebuilt");
            helper.assertTrue(bridge.getNetworkSizeForTesting() == cables.size(),
                    "the bridge is not part of the merged network");

            // Now that the merge is stable, one tick must distribute exactly once.
            cables.get(0).getEnergyStorage().setEnergy(500);
            long before = distributions(cables);
            for (CableBlockEntity cable : cables) {
                tickCable(helper, cable);
            }
            long after = distributions(cables);
            helper.assertTrue(after - before == 1,
                    "the merged network distributed " + (after - before) + " times in one tick");
            helper.assertTrue(leftQuarry.getEnergyStoredForTesting() + rightQuarry.getEnergyStoredForTesting() > 0,
                    "the merged network delivered nothing");
            helper.assertTrue(totalEnergy(cables) + leftQuarry.getEnergyStoredForTesting()
                            + rightQuarry.getEnergyStoredForTesting() == 500,
                    "the merge duplicated or lost FE: "
                            + (totalEnergy(cables) + leftQuarry.getEnergyStoredForTesting()
                            + rightQuarry.getEnergyStoredForTesting()));
        });
        helper.succeed();
    }

    /** Swapping the machine next to a cable must not leave a stale consumer behind. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void replacedMachineInvalidatesCachedConsumers(GameTestHelper helper) {
        CableBlockEntity cable = placeCable(helper, new BlockPos(5, Y, 5), CableTier.ADVANCED);
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(4, Y, 5));
        tickCable(helper, cable);
        helper.assertTrue(cable.getConsumerCountForTesting() == 1,
                "the quarry was not registered as a consumer: " + cable.getConsumerCountForTesting());

        helper.setBlock(new BlockPos(4, Y, 5), Blocks.AIR);
        helper.assertTrue(!cable.isNetworkStableForTesting(),
                "removing the machine did not invalidate the cached network");
        tickCable(helper, cable);
        helper.assertTrue(cable.getConsumerCountForTesting() == 0,
                "the removed machine is still a consumer: " + cable.getConsumerCountForTesting());
        helper.assertTrue(quarry.isRemoved(), "the removed quarry block entity is still attached");

        helper.setBlock(new BlockPos(4, Y, 5), ModBlocks.CRUSHER.get().defaultBlockState());
        helper.assertTrue(!cable.isNetworkStableForTesting(),
                "placing a new machine did not invalidate the cached network");
        tickCable(helper, cable);
        helper.assertTrue(cable.isNetworkStableForTesting(),
                "the network did not rebuild after the machine was replaced");
        helper.assertTrue(cable.getConsumerCountForTesting() == 1,
                "the rebuilt network has " + cable.getConsumerCountForTesting() + " consumers instead of 1");
        helper.succeed();
    }

    /** The biggest real-block network this template can hold: 120 cables, default budget. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void largeRealNetworkRebuildsAndDistributes(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(0, Y, 3));
        List<CableBlockEntity> cables = placeVolume(helper, 10, 4, 3, 3, CableTier.ULTIMATE);
        helper.assertTrue(cables.size() == 120, "expected a 120-cable network but built " + cables.size());

        long start = System.nanoTime();
        tickCable(helper, cables.get(0));
        long elapsed = System.nanoTime() - start;
        for (CableBlockEntity cable : cables) {
            helper.assertTrue(cable.isNetworkStableForTesting(),
                    "120 cables did not rebuild within one default-budget tick");
            helper.assertTrue(cable.getNetworkSizeForTesting() == cables.size(),
                    "cable sees " + cable.getNetworkSizeForTesting() + " of " + cables.size());
        }
        int leaders = 0;
        for (CableBlockEntity cable : cables) {
            if (cable.isNetworkLeaderForTesting()) {
                leaders++;
            }
        }
        helper.assertTrue(leaders == 1, "the 120-cable network elected " + leaders + " leaders");
        IndustrialCore.LOGGER.info("[perf] {}-cable rebuild: {} ms", cables.size(),
                String.format("%.3f", elapsed / 1_000_000.0D));
        helper.assertTrue(elapsed < 100_000_000L,
                "a 120-cable rebuild took " + (elapsed / 1_000_000L) + " ms");

        cables.get(0).getEnergyStorage().setEnergy(1_000);
        cables.get(1).getEnergyStorage().setEnergy(500);
        for (CableBlockEntity cable : cables) {
            tickCable(helper, cable);
        }
        int expected = Math.min(1_500, Math.min(CableTier.ULTIMATE.throughput(), Config.maxEnergyReceive()));
        helper.assertTrue(quarry.getEnergyStoredForTesting() == expected,
                "the 120-cable network delivered " + quarry.getEnergyStoredForTesting()
                        + " instead of " + expected);
        helper.assertTrue(totalEnergy(cables) + quarry.getEnergyStoredForTesting() == 1_500,
                "FE was duplicated or lost in the 120-cable network");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Runs {@code body} with a fixed rebuild budget and always restores the config value. */
    private static void withBudget(int budget, Runnable body) {
        CableBlockEntity.setRebuildBudgetForTesting(budget);
        try {
            body.run();
        } finally {
            CableBlockEntity.clearRebuildBudgetForTesting();
        }
    }

    /** Fills a {@code width x height x depth} cable block from {@code zStart}; placement order. */
    private static List<CableBlockEntity> placeVolume(GameTestHelper helper, int width, int height, int depth,
                                                      int zStart, CableTier tier) {
        List<CableBlockEntity> cables = new ArrayList<>();
        for (int x = 1; x <= width; x++) {
            for (int y = Y; y < Y + height; y++) {
                for (int z = zStart; z < zStart + depth; z++) {
                    cables.add(placeCable(helper, new BlockPos(x, y, z), tier));
                }
            }
        }
        return cables;
    }

    /** Fills a {@code width x height} cable wall at {@code z}; returns them in placement order. */
    private static List<CableBlockEntity> placeWall(GameTestHelper helper, int width, int height, int z,
                                                    CableTier tier) {
        List<CableBlockEntity> cables = new ArrayList<>();
        for (int x = 1; x <= width; x++) {
            for (int y = Y; y < Y + height; y++) {
                cables.add(placeCable(helper, new BlockPos(x, y, z), tier));
            }
        }
        return cables;
    }

    private static long distributions(List<CableBlockEntity> cables) {
        long total = 0L;
        for (CableBlockEntity cable : cables) {
            total += cable.getDistributionCountForTesting();
        }
        return total;
    }

    private static int totalEnergy(CableBlockEntity[] cables) {
        return totalEnergy(List.of(cables));
    }

    private static int totalEnergy(List<CableBlockEntity> cables) {
        int total = 0;
        for (CableBlockEntity cable : cables) {
            total += cable.getEnergyStoredForTesting();
        }
        return total;
    }

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

    private static void tickCable(GameTestHelper helper, CableBlockEntity cable) {
        CableBlockEntity.serverTick(helper.getLevel(), cable.getBlockPos(), cable.getBlockState(), cable);
    }
}
