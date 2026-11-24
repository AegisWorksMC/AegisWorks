package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-game tests running on a real (dedicated) server through {@code runGameTestServer}.
 * They exercise the real block breaking, drop collection, pausing and persistence paths.
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryGameTests {
    private static final int QUARRY_X = 2;
    private static final int QUARRY_Y = 6;
    private static final int QUARRY_Z = 2;

    private QuarryGameTests() {}

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void minesAreaAndCollectsDrops(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 2, 2, 2);
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.STONE);
        }
        BlockPos outsideDepth = new BlockPos(5, QUARRY_Y, 2);
        BlockPos outsideBottom = new BlockPos(3, 4, 2);
        helper.setBlock(outsideDepth, Blocks.STONE);
        helper.setBlock(outsideBottom, Blocks.STONE);

        quarry.setEnergyStored(20_000);
        quarry.applyRange(2, 2, 2);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    for (BlockPos cell : cells) {
                        helper.assertTrue(helper.getBlockState(cell).isAir(),
                                "expected mined block at " + cell + " but found " + helper.getBlockState(cell));
                    }
                    helper.assertTrue(helper.getBlockState(outsideDepth).is(Blocks.STONE),
                            "block outside the depth limit was mined");
                    helper.assertTrue(helper.getBlockState(outsideBottom).is(Blocks.STONE),
                            "block below the height limit was mined");
                    // Mined stone drops cobblestone with the quarry's (non silk touch) tool.
                    int cobblestone = countItems(quarry, Items.COBBLESTONE);
                    helper.assertTrue(cobblestone == 8, "expected 8 collected cobblestone but found " + cobblestone);
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pausesWithoutEnergyAndResumes(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos target = new BlockPos(3, QUARRY_Y, 2);
        helper.setBlock(target, Blocks.STONE);

        quarry.setEnergyStored(0);
        quarry.applyRange(1, 1, 1);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(target).is(Blocks.STONE),
                            "block was mined without energy");
                    helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_NO_ENERGY,
                            "expected PAUSED_NO_ENERGY but was " + quarry.getStatus());
                })
                .thenExecute(() -> quarry.setEnergyStored(20_000))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(target).isAir(),
                            "mining did not resume after the energy was restored");
                    helper.assertTrue(countItems(quarry, Items.COBBLESTONE) == 1,
                            "expected one collected cobblestone after resuming but found "
                                    + countItems(quarry, Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void doesNotDestroyBlocksWhenInventoryIsFull(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos target = new BlockPos(3, QUARRY_Y, 2);
        helper.setBlock(target, Blocks.STONE);

        for (int slot = QuarryBlockEntity.OUTPUT_START; slot < QuarryBlockEntity.TOTAL_SLOTS; slot++) {
            quarry.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        }

        quarry.setEnergyStored(20_000);
        quarry.applyRange(1, 1, 1);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(target).is(Blocks.STONE),
                            "block was destroyed while the inventory was full");
                    helper.assertTrue(quarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL,
                            "expected PAUSED_INVENTORY_FULL but was " + quarry.getStatus());
                    int stone = countItems(quarry, Items.STONE);
                    helper.assertTrue(stone == 18 * 64, "collected stone changed to " + stone);
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void skipsProtectedBlocksWithoutBreakingThem(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos target = new BlockPos(3, QUARRY_Y, 2);
        helper.setBlock(target, Blocks.BEDROCK);

        quarry.setEnergyStored(20_000);
        quarry.applyRange(2, 2, 2);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(target).is(Blocks.BEDROCK),
                            "bedrock was destroyed by the quarry");
                    helper.assertTrue(quarry.getBlocksSkipped() >= 1, "the protected block was not reported as skipped");
                    helper.assertTrue(quarry.getStatus() == QuarryStatus.COMPLETE,
                            "expected COMPLETE but was " + quarry.getStatus());
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void installsUpgradesExactlyOnceAndRestoresThem(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT, new ItemStack(ModItems.MOTOR.get(), 4));
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.BATTERY_SLOT, new ItemStack(ModItems.BATTERY.get(), 3));
        quarry.applyRange(2, 2, 2);
        quarry.setCursorForTesting(5);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getMotorCount() == 4,
                            "expected 4 installed motors but found " + quarry.getMotorCount());
                    helper.assertTrue(quarry.getBatteryCount() == 3,
                            "expected 3 installed batteries but found " + quarry.getBatteryCount());
                    helper.assertTrue(quarry.getInventory().getStackInSlot(QuarryBlockEntity.MOTOR_SLOT).isEmpty(),
                            "motor slot was not consumed");
                    int expected = Config.baseEnergyCapacity() + 3 * Config.energyPerBattery();
                    helper.assertTrue(quarry.getEnergyStorage().getMaxEnergyStored() == expected,
                            "unexpected capacity " + quarry.getEnergyStorage().getMaxEnergyStored());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getMotorCount() == 4 && quarry.getBatteryCount() == 3,
                            "upgrade counts were applied more than once");
                })
                .thenExecute(() -> {
                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    CompoundTag tag = quarry.saveCustomOnly(registries);
                    QuarryBlockEntity restored = new QuarryBlockEntity(
                            helper.absolutePos(new BlockPos(9, QUARRY_Y, 9)),
                            ModBlocks.QUARRY.get().defaultBlockState());
                    restored.loadCustomOnly(tag, registries);
                    helper.assertTrue(restored.getMotorCount() == 4 && restored.getBatteryCount() == 3,
                            "upgrade counts were not restored from NBT");
                    helper.assertTrue(restored.getRangeWidth() == 2 && restored.getRangeDepth() == 2
                                    && restored.getRangeHeight() == 2,
                            "range was not restored from NBT");
                    helper.assertTrue(restored.getCursor() == 5, "cursor was not restored from NBT");
                    helper.assertTrue(restored.getEnergyStorage().getMaxEnergyStored()
                                    == Config.baseEnergyCapacity() + 3 * Config.energyPerBattery(),
                            "capacity was not restored from NBT");
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void clampsRequestedRange(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        quarry.applyRange(9_999, 9_999, 9_999);

        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getRangeWidth() <= Config.maxRangeWidth(),
                            "width was not clamped: " + quarry.getRangeWidth());
                    helper.assertTrue(quarry.getRangeDepth() <= Config.maxRangeDepth(),
                            "depth was not clamped: " + quarry.getRangeDepth());
                    helper.assertTrue(quarry.getRangeHeight() <= Config.maxRangeHeight(),
                            "height was not clamped: " + quarry.getRangeHeight());
                    long volume = (long) quarry.getRangeWidth() * quarry.getRangeDepth() * quarry.getRangeHeight();
                    helper.assertTrue(volume > 0 && volume <= Config.maxVolume(), "volume out of range: " + volume);
                })
                .thenExecute(() -> quarry.applyRange(0, 0, 0))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getRangeWidth() == 1 && quarry.getRangeDepth() == 1
                                    && quarry.getRangeHeight() == 1,
                            "non-positive values were not clamped to 1");
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void keepsCollectedItemsWhenTheRangeChanges(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos target = new BlockPos(3, QUARRY_Y, 2);
        helper.setBlock(target, Blocks.DIAMOND_ORE);

        quarry.setEnergyStored(20_000);
        quarry.applyRange(1, 1, 1);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(countItems(quarry, Items.DIAMOND) == 1,
                        "diamond was not collected"))
                .thenExecute(() -> quarry.applyRange(3, 3, 3))
                .thenExecute(() -> {
                    helper.assertTrue(countItems(quarry, Items.DIAMOND) == 1,
                            "collected items were lost when the range changed");
                    helper.assertTrue(quarry.getCursor() == 0, "cursor was not reset after the range change");
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 100)
    public static void contentDataAndTagsAreLoaded(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {
                "quarry", "thermal_generator", "motor", "battery", "crafter",
                "item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate",
                "cable_basic", "cable_improved", "cable_advanced", "cable_ultimate" }) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, id);
            helper.assertTrue(recipes.byKey(key).isPresent(), "missing recipe " + key);
        }

        // Every new logistics block must drop itself (loot table data check).
        Player lootPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Block block : new Block[] {
                ModBlocks.ITEM_PIPE.get(), ModBlocks.IMPROVED_ITEM_PIPE.get(),
                ModBlocks.ADVANCED_ITEM_PIPE.get(), ModBlocks.ULTIMATE_ITEM_PIPE.get(),
                ModBlocks.CABLE_BASIC.get(), ModBlocks.CABLE_IMPROVED.get(),
                ModBlocks.CABLE_ADVANCED.get(), ModBlocks.CABLE_ULTIMATE.get() }) {
            BlockPos pos = new BlockPos(1, QUARRY_Y, 1);
            helper.setBlock(pos, block.defaultBlockState());
            List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(),
                    helper.absolutePos(pos), helper.getLevel().getBlockEntity(helper.absolutePos(pos)),
                    lootPlayer, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(block.asItem())),
                    "loot table did not drop " + block.getName());
            helper.getLevel().removeBlock(helper.absolutePos(pos), false);
        }

        Optional<HolderSet.Named<Block>> tag = BuiltInRegistries.BLOCK.getTag(ModTags.QUARRY_UNBREAKABLE);
        helper.assertTrue(tag.isPresent(), "quarry_unbreakable block tag was not loaded");
        helper.assertTrue(tag.get().size() >= 10,
                "quarry_unbreakable tag is unexpectedly small: " + tag.get().size());
        helper.assertTrue(tag.get().contains(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.BEDROCK)),
                "bedrock is missing from the quarry_unbreakable tag");
        helper.succeed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void removalReturnsLootAndContents(GameTestHelper helper) {
        BlockPos relative = quarryPos();
        BlockPos absolute = helper.absolutePos(relative);
        QuarryBlockEntity quarry = placeQuarry(helper, relative, Direction.EAST);
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.OUTPUT_START, new ItemStack(Items.COBBLESTONE, 5));
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT, new ItemStack(ModItems.MOTOR.get(), 2));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = helper.getBlockState(relative);
        List<ItemStack> loot = Block.getDrops(state, helper.getLevel(), absolute, quarry, player,
                new ItemStack(Items.NETHERITE_PICKAXE));
        helper.assertTrue(loot.stream().anyMatch(stack -> stack.is(ModItems.QUARRY.get())),
                "the quarry loot table did not drop the quarry item");

        // Mirrors ServerPlayerGameMode: playerWillDestroy (drops the contents) then remove the block.
        ModBlocks.QUARRY.get().playerWillDestroy(helper.getLevel(), absolute, state, player);
        helper.getLevel().removeBlock(absolute, false);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(relative).isAir(), "the quarry block was not removed");
                    helper.assertTrue(countItems(quarry, Items.COBBLESTONE) == 0,
                            "collected items were not cleared from the removed machine");
                    helper.assertItemEntityPresent(Items.COBBLESTONE, relative, 3.0);
                    helper.assertItemEntityPresent(ModItems.MOTOR.get(), relative, 3.0);
                    helper.assertItemEntityNotPresent(ModItems.BATTERY.get());
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void generatorPowersTheQuarryEndToEnd(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(2, QUARRY_Y, 2);
        BlockPos quarryPos = new BlockPos(3, QUARRY_Y, 2);
        helper.setBlock(generatorPos, ModBlocks.THERMAL_GENERATOR.get().defaultBlockState()
                .setValue(GenerationBlock.FACING, Direction.EAST));
        BlockEntity generatorEntity = helper.getLevel().getBlockEntity(helper.absolutePos(generatorPos));
        if (!(generatorEntity instanceof GenerationBlockEntity generator)) {
            helper.fail("generator block entity missing at " + generatorPos);
            return;
        }

        BlockPos target = new BlockPos(4, QUARRY_Y, 2);
        helper.setBlock(target, Blocks.STONE);
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos, Direction.EAST);
        quarry.applyRange(1, 1, 1);
        quarry.setRunning(true);
        generator.getInventory().setStackInSlot(GenerationBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 1));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(generator.getBurnTime() > 0,
                            "the generator did not start burning fuel: burn=" + generator.getBurnTime()
                                    + " genFE=" + generator.getEnergyStorage().getEnergyStored());
                    helper.assertTrue(helper.getBlockState(target).isAir(),
                            "the quarry was not powered by the generator: quarryFE="
                                    + quarry.getEnergyStoredForTesting()
                                    + " genFE=" + generator.getEnergyStorage().getEnergyStored()
                                    + " status=" + quarry.getStatus()
                                    + " target=" + target + " state=" + helper.getBlockState(target));
                    helper.assertTrue(countItems(quarry, Items.COBBLESTONE) == 1,
                            "expected one collected cobblestone but found "
                                    + countItems(quarry, Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void motorsIncreaseMiningSpeed(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 2, 1, 2);
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.STONE);
        }
        quarry.getInventory().setStackInSlot(QuarryBlockEntity.MOTOR_SLOT, new ItemStack(ModItems.MOTOR.get(), 20));
        quarry.setEnergyStored(20_000);

        helper.startSequence()
                // Installing 20 motors takes 20 server ticks (one per tick).
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getMotorCount() == 20,
                            "motors were not installed: " + quarry.getMotorCount());
                    quarry.applyRange(2, 1, 2);
                    quarry.setRunning(true);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    int remaining = 0;
                    for (BlockPos cell : cells) {
                        if (!helper.getBlockState(cell).isAir()) {
                            remaining++;
                        }
                    }
                    helper.assertTrue(remaining == 0,
                            "20 motors should mine 4 blocks within 8 ticks, but " + remaining + " remained");
                })
                .thenSucceed();
    }

    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void withoutMotorsMiningIsSlower(GameTestHelper helper) {
        QuarryBlockEntity quarry = placeQuarry(helper, quarryPos(), Direction.EAST);
        BlockPos[] cells = cells(new BlockPos(3, QUARRY_Y, 2), Direction.EAST, 2, 1, 2);
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.STONE);
        }
        quarry.setEnergyStored(20_000);
        quarry.applyRange(2, 1, 2);
        quarry.setRunning(true);

        helper.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    int mined = 0;
                    for (BlockPos cell : cells) {
                        if (helper.getBlockState(cell).isAir()) {
                            mined++;
                        }
                    }
                    // Base speed is 0.25 blocks per tick, so only two of the four blocks
                    // can be finished after eight ticks.
                    helper.assertTrue(mined == 2,
                            "expected exactly 2 mined blocks without motors but found " + mined);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static BlockPos quarryPos() {
        return new BlockPos(QUARRY_X, QUARRY_Y, QUARRY_Z);
    }

    private static QuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos, Direction facing) {
        // GameTest suites share one server: disable the cross-machine budget for every test so
        // a parallel test cannot starve this machine's work units.
        com.industrialcore.machine.MiningScheduler.setBudgetOverrideForTesting(
                com.industrialcore.machine.MiningScheduler.UNLIMITED);
        helper.setBlock(pos, ModBlocks.QUARRY.get().defaultBlockState().setValue(QuarryBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof QuarryBlockEntity quarry)) {
            helper.fail("quarry block entity missing at " + pos);
            return null;
        }
        return quarry;
    }

    private static BlockPos[] cells(BlockPos origin, Direction facing, int width, int depth, int height) {
        Direction right = facing.getClockWise();
        List<BlockPos> list = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    list.add(origin.relative(right, x).relative(facing, z).below(y));
                }
            }
        }
        return list.toArray(new BlockPos[0]);
    }

    private static int countItems(QuarryBlockEntity quarry, Item item) {
        int total = 0;
        for (int slot = QuarryBlockEntity.OUTPUT_START; slot < QuarryBlockEntity.TOTAL_SLOTS; slot++) {
            ItemStack stack = quarry.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
