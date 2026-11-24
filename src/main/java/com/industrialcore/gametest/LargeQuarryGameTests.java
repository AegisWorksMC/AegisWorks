package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.LargeQuarryBlock;
import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.LargeQuarryBalance;
import com.industrialcore.machine.MiningScheduler;
import com.industrialcore.machine.PickaxeStats;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests of the reworked (Phase 22) large quarry.
 *
 * <p>They drive the real server tick, real block breaking, real loot tables and the real item
 * handler capabilities:</p>
 * <pre>
 *   fixed area --scan--> selected c:ores block --pickaxe enchantments--> output
 *       |                                                              |
 *       +-- stone/dirt untouched                                       +-- pipe / distributor
 *       +-- pause on no FE / full output / no tool / empty selection
 * </pre>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LargeQuarryGameTests {
    private static final int Y = 6;

    private LargeQuarryGameTests() {}

    // ------------------------------------------------------------------
    // ore selection
    // ------------------------------------------------------------------

    /** Stone is stepped over; only the selected ore is mined and stored. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void minesOnlyTheSelectedOreAndKeepsStone(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(3, 1, 1);
        BlockPos[] cells = cells(new BlockPos(3, Y, 2), Direction.EAST, 3, 1, 1);
        BlockPos first = cells[0];
        BlockPos ore = cells[1];
        BlockPos last = cells[2];
        helper.setBlock(first, Blocks.STONE);
        helper.setBlock(ore, Blocks.IRON_ORE);
        helper.setBlock(last, Blocks.DEEPSLATE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 12);

        helper.assertTrue(helper.getBlockState(ore).isAir(), "the selected iron ore was not mined");
        helper.assertTrue(helper.getBlockState(first).is(Blocks.STONE),
                "stone was destroyed although only iron ore is selected");
        helper.assertTrue(helper.getBlockState(last).is(Blocks.DEEPSLATE),
                "deepslate was destroyed although only iron ore is selected");
        helper.assertTrue(count(quarry, Items.RAW_IRON) == 1,
                "expected one raw iron but found " + count(quarry, Items.RAW_IRON));
        helper.assertTrue(quarry.getBlocksMined() == 1,
                "expected one mined block but the machine reports " + quarry.getBlocksMined());
        helper.assertTrue(quarry.getBlocksSkipped() == 2,
                "expected two skipped positions but the machine reports "
                        + quarry.getBlocksSkipped());
        helper.assertTrue(quarry.getStatus() == QuarryStatus.COMPLETE,
                "expected COMPLETE but was " + quarry.getStatus());
        helper.succeed();
    }

    /** Every ore is selected by default, so a fresh machine works without opening the GUI. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void aFreshMachineSelectsEveryOreByDefault(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        tick(quarry);
        helper.assertTrue(quarry.getOreCandidates().contains(Blocks.IRON_ORE),
                "the c:ores tag does not contain iron ore; the ore picker would stay empty");
        helper.assertTrue(quarry.isOreSelected(Blocks.IRON_ORE),
                "a new machine should select every ore by default");
        helper.succeed();
    }

    /** Clearing the selection pauses instead of mining something the player did not allow. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void pausesWhenNoOreIsSelected(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.clearOres();
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 6);

        helper.assertTrue(helper.getBlockState(ore).is(Blocks.IRON_ORE),
                "an ore was mined although the selection is empty");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_SELECTION,
                "expected PAUSED_NO_SELECTION but was " + quarry.getStatus());
        helper.assertTrue(quarry.getEnergyStoredForTesting() == 100_000,
                "FE was consumed while the machine was paused");
        helper.succeed();
    }

    /** Without a pickaxe nothing can be recovered; inserting one resumes the run. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void pausesWithoutPickaxeAndResumesAfterInstallingOne(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 4);
        helper.assertTrue(helper.getBlockState(ore).is(Blocks.IRON_ORE),
                "an ore was mined without an installed pickaxe");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_TOOLS,
                "expected PAUSED_NO_TOOLS but was " + quarry.getStatus());

        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.setRunning(true);
        tickFor(quarry, 12);
        helper.assertTrue(helper.getBlockState(ore).isAir(),
                "mining did not resume after a pickaxe was installed");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // enchantment merging
    // ------------------------------------------------------------------

    /** Silk Touch recovers the ore block itself instead of the raw drop. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void silkTouchRecoversTheOreBlock(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 1);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 12);

        helper.assertTrue(helper.getBlockState(ore).isAir(), "the iron ore was not mined");
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 1,
                "expected one iron ore block but found " + count(quarry, Items.IRON_ORE));
        helper.assertTrue(count(quarry, Items.RAW_IRON) == 0,
                "silk touch must not also drop the raw ore");
        helper.succeed();
    }

    /** Two pickaxes merge their enchantments; Fortune and Silk Touch both stay active. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void mergesEnchantmentsAndAppliesSilkWithFortune(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(2, 1, 1);
        BlockPos[] cells = cells(new BlockPos(3, Y, 2), Direction.EAST, 2, 1, 1);
        helper.setBlock(cells[0], Blocks.IRON_ORE);
        helper.setBlock(cells[1], Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        unlockToolSlots(quarry, 2);
        // Fortune II + Fortune II = IV, Silk Touch I on the first pickaxe = I.
        insertPickaxe(helper, quarry, 0, 2, 0, 1);
        insertPickaxe(helper, quarry, 1, 2, 0, 0);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 20);

        PickaxeStats stats = quarry.getPickaxeStats();
        helper.assertTrue(stats.efficiency() == 0 && stats.fortune() == 4 && stats.silkTouch() == 1,
                "merged enchantments are " + stats);
        // 1 + fortune / 2 = 3 blocks per mined ore block, two ore blocks mined.
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 6,
                "expected 6 silk-touched iron ore blocks but found "
                        + count(quarry, Items.IRON_ORE));
        helper.assertTrue(count(quarry, Items.RAW_IRON) == 0,
                "the ore was also dropped as raw ore (double drop)");
        int expectedCost = LargeQuarryBalance.miningCost(
                Config.largeQuarryEnergyPerBlock(), stats, 2);
        helper.assertTrue(quarry.getEnergyStoredForTesting() == 100_000 - 2 * expectedCost,
                "expected " + (2 * expectedCost) + " FE to be consumed but the buffer holds "
                        + quarry.getEnergyStoredForTesting());
        helper.succeed();
    }

    /** Without Silk Touch, Fortune multiplies the ordinary drops. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void fortuneMultipliesRawDrops(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        unlockToolSlots(quarry, 2);
        insertPickaxe(helper, quarry, 0, 2, 0, 0);
        insertPickaxe(helper, quarry, 1, 2, 0, 0);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 16);

        helper.assertTrue(count(quarry, Items.RAW_IRON) == 5,
                "expected 1 + 4 = 5 raw iron but found " + count(quarry, Items.RAW_IRON));
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 0,
                "fortune without silk touch must not yield ore blocks");
        helper.succeed();
    }

    /** Absurd enchantment levels stay deterministic and never overflow the buffer arithmetic. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void veryHighEnchantmentLevelsStaySafe(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 1_000, 0, 1);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 20);

        // A single item's enchantment component caps one level at 255 (vanilla storage limit, not
        // a mod rule), so Fortune 1000 is stored as 255: 1 + 255 / 2 = 128 ore blocks.
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 128,
                "expected 128 ore blocks but found " + count(quarry, Items.IRON_ORE));
        helper.assertTrue(quarry.getMiningCost() > 100,
                "the per block cost did not grow with the merged level");
        helper.assertTrue(quarry.getEnergyStoredForTesting() >= 0,
                "the FE buffer became negative");
        helper.succeed();
    }

    /** Each installed pickaxe raises the FE cost of one mined ore block. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void extraPickaxesAndEnchantmentsRaiseTheEnergyCost(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        helper.setBlock(new BlockPos(3, Y, 2), Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        int single = quarry.getMiningCost();
        helper.assertTrue(single == LargeQuarryBalance.miningCost(
                        Config.largeQuarryEnergyPerBlock(), quarry.getPickaxeStats(), 1),
                "the reported cost does not match the formula: " + single);

        quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.TOOL_START + 1,
                enchantedPickaxe(helper, 1, 0, 0, 0));
        quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.UPGRADE_SLOT,
                new ItemStack(ModItems.MOTOR.get()));
        tick(quarry);
        helper.assertTrue(quarry.getToolSlotCount() == 2,
                "the motor did not unlock a second pickaxe slot");
        helper.assertTrue(count(quarry, ModItems.MOTOR.get()) == 0,
                "the motor was not consumed");
        helper.assertTrue(quarry.getMiningCost() > single,
                "a second pickaxe did not raise the cost (" + single + " -> "
                        + quarry.getMiningCost() + ")");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // output, energy and safety
    // ------------------------------------------------------------------

    /** A full output stops the machine without breaking the ore or consuming FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void stopsWhenTheOutputIsFull(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        for (int slot = LargeQuarryBlockEntity.OUTPUT_START;
                slot < LargeQuarryBlockEntity.TOOL_START; slot++) {
            quarry.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 12);

        helper.assertTrue(helper.getBlockState(ore).is(Blocks.IRON_ORE),
                "the ore was destroyed while the output was full");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL,
                "expected PAUSED_INVENTORY_FULL but was " + quarry.getStatus());
        helper.assertTrue(quarry.getEnergyStoredForTesting() == 100_000,
                "FE was consumed although the ore was not mined");

        // Freeing one slot resumes the machine without a restart.
        quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.OUTPUT_START, ItemStack.EMPTY);
        tickFor(quarry, 12);
        helper.assertTrue(helper.getBlockState(ore).isAir(),
                "the machine did not resume after a slot was freed");
        helper.assertTrue(count(quarry, Items.RAW_IRON) == 1,
                "expected one collected raw iron after resuming but found "
                        + count(quarry, Items.RAW_IRON));
        helper.succeed();
    }

    /** Without FE nothing is mined; the machine resumes as soon as power arrives. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void pausesWithoutEnergyAndResumes(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.setEnergyStored(0);
        quarry.setRunning(true);

        tickFor(quarry, 4);
        helper.assertTrue(helper.getBlockState(ore).is(Blocks.IRON_ORE),
                "the ore was mined without energy");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                "expected PAUSED_NO_ENERGY but was " + quarry.getStatus());

        quarry.setEnergyStored(100_000);
        tickFor(quarry, 12);
        helper.assertTrue(helper.getBlockState(ore).isAir(),
                "mining did not resume after the energy was restored");
        helper.assertTrue(count(quarry, Items.RAW_IRON) == 1,
                "expected one raw iron after resuming but found "
                        + count(quarry, Items.RAW_IRON));
        helper.succeed();
    }

    /** A protected block is reported as skipped and never destroyed. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void skipsProtectedBlocks(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos target = new BlockPos(3, Y, 2);
        helper.setBlock(target, Blocks.BEDROCK);
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 8);

        helper.assertTrue(helper.getBlockState(target).is(Blocks.BEDROCK),
                "bedrock was destroyed by the large quarry");
        helper.assertTrue(quarry.getBlocksSkipped() == 1,
                "the protected block was not reported as skipped");
        helper.assertTrue(quarry.getStatus() == QuarryStatus.COMPLETE,
                "expected COMPLETE but was " + quarry.getStatus());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // logistics
    // ------------------------------------------------------------------

    /** A real pipe pulls the collected ore out of the machine and carries it into a chest. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void aPipeExtractsTheOutput(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 1);
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, Y, 3));
        pipe.setMode(Direction.NORTH, PipeConnectionMode.PULL);
        pipe.setMode(Direction.SOUTH, PipeConnectionMode.PUSH);
        Container chest = placeChest(helper, new BlockPos(2, Y, 4));

        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);
        tickFor(quarry, 12);
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 1,
                "the machine did not collect the ore block before the pipe ran");

        for (int tick = 0; tick < 40; tick++) {
            tickPipe(helper, pipe);
        }

        helper.assertTrue(count(chest, Items.IRON_ORE) == 1,
                "the chest holds " + count(chest, Items.IRON_ORE) + " instead of 1");
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 0,
                "the machine still holds " + count(quarry, Items.IRON_ORE) + " items");
        helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds items");
        helper.succeed();
    }

    /** The real distributor receives the pipe's stack and hands it to a chest. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aDistributorDeliversTheOutput(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(1, 1, 1);
        helper.setBlock(new BlockPos(2, Y, 2), Blocks.IRON_ORE);
        quarry.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, quarry, 0, 0, 0, 1);
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(1, Y, 3));
        pipe.setMode(Direction.NORTH, PipeConnectionMode.PULL);
        pipe.setMode(Direction.SOUTH, PipeConnectionMode.PUSH);
        ItemDistributorBlockEntity distributor = placeDistributor(helper, new BlockPos(1, Y, 4));
        distributor.getFaceConfig().setMode(Direction.NORTH.get3DDataValue(), FaceMode.INPUT);
        Container chest = placeChest(helper, new BlockPos(1, Y, 5));

        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);
        tickFor(quarry, 12);

        for (int tick = 0; tick < 80; tick++) {
            tickPipe(helper, pipe);
            tickDistributor(helper, distributor);
        }

        helper.assertTrue(count(chest, Items.IRON_ORE) == 1,
                "the chest holds " + count(chest, Items.IRON_ORE) + " instead of 1"
                        + " (pipe " + pipe.getBufferCount()
                        + ", distributor " + distributor.getBufferCount() + ")");
        helper.assertTrue(distributor.getBufferCount() == 0, "the distributor still holds items");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // slot layout / migration (Phase 22 crash regression)
    // ------------------------------------------------------------------

    /**
     * Reproduces the reported crash: a Phase 20 world stores an 18 slot inventory while the
     * current machine addresses pickaxes from slot 18. The saved size must never resize the array,
     * the outputs must be kept and the next tick must run instead of throwing
     * {@code Slot 18 not in valid range - [0,18)}.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void legacyInventorySizeIsMigratedWithoutCrashing(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        CompoundTag tag = quarry.saveWithoutMetadata(helper.getLevel().registryAccess());

        // Exactly the shape the Phase 20 machine wrote: Size 18, outputs only.
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", 18);
        net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putInt("Slot", LargeQuarryBlockEntity.OUTPUT_START);
        // The shape NeoForge 1.21 actually writes: the stack is inline in the entry.
        entry.putString("id", "minecraft:iron_ore");
        entry.putInt("count", 3);
        items.add(entry);
        inventory.put("Items", items);
        tag.put("Inventory", inventory);
        tag.putInt("ToolSlots", 1);

        quarry.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(quarry.getInventory().getSlots() == LargeQuarryBlockEntity.TOTAL_SLOTS,
                "a legacy save resized the inventory to " + quarry.getInventory().getSlots()
                        + " slots instead of " + LargeQuarryBlockEntity.TOTAL_SLOTS);
        helper.assertTrue(count(quarry, Items.IRON_ORE) == 3,
                "the legacy output was not migrated (found " + count(quarry, Items.IRON_ORE) + ")");
        helper.assertTrue(quarry.getInventory().getStackInSlot(
                        LargeQuarryBlockEntity.UPGRADE_SLOT).isEmpty(),
                "the motor slot must be empty after migrating an output-only save");

        // The tick that used to throw.
        tick(quarry);
        helper.assertTrue(quarry.getPickaxeStats().tools() == 0,
                "the migrated machine should have no pickaxes installed");
        helper.succeed();
    }

    /** An empty machine ticks without a pickaxe and without exception. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void anEmptyQuarryTicksSafely(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        tickFor(quarry, 5);
        helper.assertTrue(quarry.getToolSlotCount() == LargeQuarryBalance.BASE_TOOL_SLOTS,
                "a new machine should start with one arm");
        helper.assertTrue(quarry.getPickaxeStats().tools() == 0,
                "no pickaxe is installed, so the tool count must be 0");
        helper.assertTrue(quarry.getMiningCost() == LargeQuarryBalance.ENERGY_PER_BLOCK,
                "an empty machine must still report the base cost");
        helper.succeed();
    }

    /** All eight arms are counted, including the last physical slot (25). */
    @GameTest(template = "quarry_test", timeoutTicks = 150)
    public static void everyUnlockedToolSlotIsCounted(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        unlockToolSlots(quarry, LargeQuarryBalance.MAX_TOOL_SLOTS);
        helper.assertTrue(quarry.getToolSlotCount() == LargeQuarryBalance.MAX_TOOL_SLOTS,
                "motors did not unlock all arms, count is " + quarry.getToolSlotCount());
        for (int index = 0; index < LargeQuarryBalance.MAX_TOOL_SLOTS; index++) {
            insertPickaxe(helper, quarry, index, 1, 0, 0);
        }
        // The last arm lives at UPGRADE_SLOT - 1; reading it used to be the out-of-range case.
        helper.assertTrue(!quarry.getInventory().getStackInSlot(
                        LargeQuarryBlockEntity.UPGRADE_SLOT - 1).isEmpty(),
                "the last pickaxe slot is not inside the inventory");
        tick(quarry);
        PickaxeStats stats = quarry.getPickaxeStats();
        helper.assertTrue(stats.tools() == LargeQuarryBalance.MAX_TOOL_SLOTS,
                "counted " + stats.tools() + " pickaxes instead of "
                        + LargeQuarryBalance.MAX_TOOL_SLOTS);
        helper.assertTrue(stats.fortune() == LargeQuarryBalance.MAX_TOOL_SLOTS,
                "the merged fortune is " + stats.fortune());
        helper.assertTrue(quarry.getMiningCost() == LargeQuarryBalance.miningCost(
                        Config.largeQuarryEnergyPerBlock(), stats, stats.tools()),
                "the FE cost does not match the documented formula");
        helper.succeed();
    }

    /** A hostile or old {@code ToolSlots} value is clamped instead of addressing outside the array. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void aCorruptToolSlotCountIsClamped(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        CompoundTag tag = quarry.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putInt("ToolSlots", 999);
        quarry.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(quarry.getToolSlotCount() == LargeQuarryBalance.MAX_TOOL_SLOTS,
                "ToolSlots 999 was not clamped, got " + quarry.getToolSlotCount());
        tickFor(quarry, 3);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // persistence and removal
    // ------------------------------------------------------------------

    /** Cursor, selection, tool slots, pickaxes, FE and output survive a save and load. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void survivesSaveAndLoad(GameTestHelper helper) {
        LargeQuarryBlockEntity original = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        original.setAreaForTesting(1, 1, 1);
        BlockPos ore = new BlockPos(3, Y, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        original.selectOnlyForTesting(Blocks.IRON_ORE);
        insertPickaxe(helper, original, 0, 0, 0, 1);
        original.setEnergyStored(50_000);
        original.setRunning(true);
        tickFor(original, 12);
        int storedEnergy = original.getEnergyStoredForTesting();
        helper.assertTrue(original.getCursor() == 1, "the ore was not mined before the save");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        LargeQuarryBlockEntity restored = placeQuarry(
                helper, new BlockPos(8, Y, 8), Direction.EAST);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        restored.onChunkUnloaded();

        helper.assertTrue(restored.getCursor() == 1,
                "the restored cursor is " + restored.getCursor() + " instead of 1");
        helper.assertTrue(restored.getBlocksMined() == 1,
                "the restored counter is " + restored.getBlocksMined() + " instead of 1");
        helper.assertTrue(restored.getEnergyStoredForTesting() == storedEnergy,
                "the restored FE buffer is " + restored.getEnergyStoredForTesting()
                        + " instead of " + storedEnergy);
        helper.assertTrue(count(restored, Items.IRON_ORE) == 1,
                "the restored output holds " + count(restored, Items.IRON_ORE)
                        + " instead of 1");
        helper.assertTrue(restored.getInventory().getStackInSlot(
                        LargeQuarryBlockEntity.TOOL_START).is(Items.NETHERITE_PICKAXE),
                "the installed pickaxe was not restored");
        helper.assertTrue(restored.isOreSelected(Blocks.IRON_ORE)
                        && !restored.isOreSelected(Blocks.GOLD_ORE),
                "the ore selection was not restored");
        helper.succeed();
    }

    /** Breaking the machine returns the collected ore, the pickaxes and a leftover motor. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void removalReturnsTheContents(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, Y, 2);
        BlockPos absolute = helper.absolutePos(relative);
        LargeQuarryBlockEntity quarry = placeQuarry(helper, relative, Direction.EAST);
        quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.OUTPUT_START,
                new ItemStack(Items.IRON_ORE, 5));
        quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.TOOL_START,
                new ItemStack(Items.DIAMOND_PICKAXE));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = helper.getBlockState(relative);

        ModBlocks.LARGE_QUARRY.get().playerWillDestroy(helper.getLevel(), absolute, state, player);
        helper.getLevel().removeBlock(absolute, false);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(relative).isAir(),
                            "the machine block was not removed");
                    helper.assertTrue(count(quarry, Items.IRON_ORE) == 0,
                            "the collected items were not cleared from the removed machine");
                    helper.assertItemEntityPresent(Items.IRON_ORE, relative, 3.0);
                    helper.assertItemEntityPresent(Items.DIAMOND_PICKAXE, relative, 3.0);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // configured area
    // ------------------------------------------------------------------

    /** The working area comes from the server config and is not a GUI value. */
    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void usesTheConfiguredFixedArea(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        tick(quarry);

        int width = Config.largeQuarryWidth();
        int depth = Config.largeQuarryDepth();
        int height = Config.largeQuarryHeight();
        helper.assertTrue(quarry.getAreaWidth() == width
                        && quarry.getAreaDepth() == depth
                        && quarry.getAreaHeight() == height,
                "the machine uses " + quarry.getAreaWidth() + "x" + quarry.getAreaDepth()
                        + "x" + quarry.getAreaHeight() + " instead of "
                        + width + "x" + depth + "x" + height);
        helper.assertTrue(quarry.getVolume() == width * depth * height,
                "the volume is " + quarry.getVolume() + " instead of "
                        + width * depth * height);
        BlockPos first = quarry.targetFor(0);
        BlockPos expected = helper.absolutePos(new BlockPos(3, Y, 2));
        helper.assertTrue(first.equals(expected),
                "the first target is " + first + " instead of " + expected);
        helper.assertTrue(quarry.getStatus() == QuarryStatus.STOPPED,
                "a new machine must wait for the start button, but reports "
                        + quarry.getStatus());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static LargeQuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos,
                                                      Direction facing) {
        // GameTest suites share one server: disable the cross-machine budget for every test so
        // a parallel test cannot starve this machine's work units.
        MiningScheduler.setBudgetOverrideForTesting(MiningScheduler.UNLIMITED);
        helper.setBlock(pos, ModBlocks.LARGE_QUARRY.get().defaultBlockState()
                .setValue(LargeQuarryBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof LargeQuarryBlockEntity quarry)) {
            helper.fail("large quarry block entity missing at " + pos);
            return null;
        }
        return quarry;
    }

    /** Inserts a pickaxe with the given merged levels and refreshes the cached tool values. */
    private static void insertPickaxe(GameTestHelper helper, LargeQuarryBlockEntity quarry,
                                      int slot, int fortune, int efficiency, int silk) {
        quarry.getInventory().setStackInSlot(
                LargeQuarryBlockEntity.TOOL_START + slot,
                enchantedPickaxe(helper, fortune, efficiency, silk, 0));
        quarry.markToolsChangedForTesting();
    }

    private static ItemStack enchantedPickaxe(GameTestHelper helper, int fortune, int efficiency,
                                              int silk, int unused) {
        ItemStack stack = new ItemStack(Items.NETHERITE_PICKAXE);
        if (fortune > 0) {
            stack.enchant(holder(helper, Enchantments.FORTUNE), fortune);
        }
        if (efficiency > 0) {
            stack.enchant(holder(helper, Enchantments.EFFICIENCY), efficiency);
        }
        if (silk > 0) {
            stack.enchant(holder(helper, Enchantments.SILK_TOUCH), silk);
        }
        return stack;
    }

    private static net.minecraft.core.Holder<Enchantment> holder(
            GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().holderOrThrow(key);
    }

    /** Positions of the fixed plan in the order the machine visits them. */
    private static BlockPos[] cells(BlockPos origin, Direction facing, int width, int depth,
                                    int height) {
        Direction right = facing.getClockWise();
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    list.add(origin.relative(right, x).relative(facing, z).below(y));
                }
            }
        }
        return list.toArray(new BlockPos[0]);
    }

    /** Installs motors one by one, exactly like a player filling the upgrade slot. */
    private static void unlockToolSlots(LargeQuarryBlockEntity quarry, int slots) {
        while (quarry.getToolSlotCount() < slots) {
            quarry.getInventory().setStackInSlot(LargeQuarryBlockEntity.UPGRADE_SLOT,
                    new ItemStack(ModItems.MOTOR.get()));
            tick(quarry);
        }
    }

    private static void tick(LargeQuarryBlockEntity quarry) {
        LargeQuarryBlockEntity.serverTick(quarry.getLevel(), quarry.getBlockPos(),
                quarry.getBlockState(), quarry);
    }

    private static void tickFor(LargeQuarryBlockEntity quarry, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tick(quarry);
        }
    }

    // ------------------------------------------------------------------
    // Phase 23: selection toggling and the client display sync
    // ------------------------------------------------------------------

    /**
     * Toggling one ore flips exactly that ore: the rest of the selection (the initial "select
     * every ore") stays untouched, every change requests a client sync, and a malformed index is
     * rejected instead of throwing.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void togglingOneOreKeepsTheOthersSelected(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        tick(quarry);
        List<Block> candidates = quarry.getOreCandidates();
        int ironIndex = candidates.indexOf(Blocks.IRON_ORE);
        helper.assertTrue(ironIndex >= 0, "iron ore must be a c:ores candidate");
        helper.assertTrue(quarry.isOreSelected(Blocks.IRON_ORE),
                "a fresh machine must start with iron ore selected");

        int syncsBefore = quarry.getClientSyncRequestsForTesting();
        helper.assertTrue(quarry.toggleOre(ironIndex),
                "the toggle must accept an index taken from the candidate list");
        helper.assertTrue(!quarry.isOreSelected(Blocks.IRON_ORE),
                "iron ore must be off after the toggle");
        helper.assertTrue(quarry.isOreSelected(Blocks.GOLD_ORE),
                "turning iron ore off must not touch gold ore");
        helper.assertTrue(quarry.isOreSelected(Blocks.COPPER_ORE),
                "turning iron ore off must not touch copper ore");
        helper.assertTrue(quarry.getClientSyncRequestsForTesting() == syncsBefore + 1,
                "every selection change must push a display update to the client");

        helper.assertTrue(quarry.toggleOre(ironIndex), "toggling back on must work");
        helper.assertTrue(quarry.isOreSelected(Blocks.IRON_ORE),
                "iron ore must be selectable again");
        helper.assertTrue(!quarry.toggleOre(candidates.size()),
                "an index past the end of the candidate list must be rejected");
        helper.assertTrue(!quarry.toggleOre(-1), "a negative index must be rejected");
        helper.succeed();
    }

    /**
     * A fresh machine selects every ore on its first tick, and that automatic selection must be
     * pushed to the client: setChanged() alone sends nothing, which is how the picker used to
     * show an empty selection until the first click made every other cell jump at once.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void aFreshMachinePushesItsInitialSelectionToTheClient(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        helper.assertTrue(quarry.getClientSyncRequestsForTesting() == 0,
                "nothing should have been synced before the first tick");
        tick(quarry);
        helper.assertTrue(quarry.getClientSyncRequestsForTesting() >= 1,
                "the automatic initial selection must be pushed to the client");
        helper.succeed();
    }

    /**
     * The display tag restores the picker state on a client copy that has no level at all - the
     * path the old level-based dispatch got wrong (it fell into the save branch and left the
     * display selection empty) - and it never populates the authoritative selection.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void theDisplayTagRestoresWithoutALevel(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        tick(quarry);
        int ironIndex = quarry.getOreCandidates().indexOf(Blocks.IRON_ORE);
        helper.assertTrue(quarry.toggleOre(ironIndex),
                "the toggle must accept an index taken from the candidate list");

        var registries = helper.getLevel().registryAccess();
        CompoundTag displayTag = quarry.getUpdateTag(registries);
        LargeQuarryBlockEntity clientCopy = new LargeQuarryBlockEntity(BlockPos.ZERO,
                ModBlocks.LARGE_QUARRY.get().defaultBlockState());
        clientCopy.loadWithComponents(displayTag, registries);

        helper.assertTrue(clientCopy.getLevel() == null,
                "this test must cover the path where the level is not set yet");
        List<String> clientIds = clientCopy.getClientOreIds();
        helper.assertTrue(clientIds.contains("minecraft:gold_ore"),
                "the display tag must carry the selection but held " + clientIds);
        helper.assertTrue(!clientIds.contains("minecraft:iron_ore"),
                "the toggled-off ore must be off in the display tag");
        helper.assertTrue(!clientCopy.isOreSelected(Blocks.GOLD_ORE),
                "a display tag must never populate the authoritative selection");
        helper.succeed();
    }

    /**
     * Mining level: an ore the player turned off stays in the world while a selected ore beside
     * it is mined - the toggle must reach the real mining filter, not just the GUI.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void anOreTurnedOffIsSkippedWhileSelectedOnesMine(GameTestHelper helper) {
        LargeQuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(2, Y, 2), Direction.EAST);
        quarry.setAreaForTesting(2, 1, 1);
        BlockPos[] targets = cells(new BlockPos(3, Y, 2), Direction.EAST, 2, 1, 1);
        helper.setBlock(targets[0], Blocks.IRON_ORE);
        helper.setBlock(targets[1], Blocks.GOLD_ORE);
        tick(quarry);
        int ironIndex = quarry.getOreCandidates().indexOf(Blocks.IRON_ORE);
        helper.assertTrue(quarry.toggleOre(ironIndex),
                "the toggle must accept an index taken from the candidate list");
        insertPickaxe(helper, quarry, 0, 0, 0, 0);
        quarry.setEnergyStored(100_000);
        quarry.setRunning(true);

        tickFor(quarry, 12);

        helper.assertTrue(helper.getBlockState(targets[0]).is(Blocks.IRON_ORE),
                "iron ore was mined although it was turned off");
        helper.assertTrue(helper.getBlockState(targets[1]).isAir(),
                "gold ore should have been mined while still selected");
        helper.assertTrue(count(quarry, Items.RAW_GOLD) == 1,
                "expected one raw gold but found " + count(quarry, Items.RAW_GOLD));
        helper.succeed();
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(),
                pipe.getBlockState(), pipe);
    }

    private static void tickDistributor(GameTestHelper helper, ItemDistributorBlockEntity distributor) {
        ItemDistributorBlockEntity.serverTick(helper.getLevel(), distributor.getBlockPos(),
                distributor.getBlockState(), distributor);
    }

    private static Container placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof Container container)) {
            helper.fail("chest block entity missing at " + pos);
            return null;
        }
        return container;
    }

    private static ItemPipeBlockEntity placePipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_PIPE.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ItemPipeBlockEntity pipe)) {
            helper.fail("item pipe block entity missing at " + pos);
            return null;
        }
        return pipe;
    }

    private static ItemDistributorBlockEntity placeDistributor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_DISTRIBUTOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ItemDistributorBlockEntity distributor)) {
            helper.fail("item distributor block entity missing at " + pos);
            return null;
        }
        return distributor;
    }

    private static int count(LargeQuarryBlockEntity quarry, Item item) {
        int total = 0;
        for (int slot = LargeQuarryBlockEntity.OUTPUT_START;
                slot < LargeQuarryBlockEntity.TOOL_START; slot++) {
            ItemStack stack = quarry.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
