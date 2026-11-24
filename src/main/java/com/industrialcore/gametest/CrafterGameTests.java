package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.CrafterSpeed;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.menu.CrafterMenu;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * In-game tests for the Phase 6 auto crafter.
 *
 * <p>The tests run the real block ticker on a dedicated server, use the real vanilla recipe
 * manager ({@code minecraft:crafting_table}, {@code minecraft:stick}) and assert exact item
 * totals, so a lost or duplicated item fails the test. Every non-crafting state is checked by
 * both its status and the untouched inventory.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrafterGameTests {
    private static final int Y = 6;
    /** Pipe tests use a higher layer so they cannot see blocks of another test's platform. */
    private static final int PIPE_Y = 9;
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("crafting_table");
    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");

    private CrafterGameTests() {}

    /** Four planks are turned into exactly one crafting table and the power is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void craftsSelectedRecipe(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                            "expected exactly one crafting table, found "
                                    + count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE));
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "the machine still holds planks: " + countInputs(crafter, Items.OAK_PLANKS));
                    helper.assertTrue(crafter.getEnergyStoredForTesting() < 20_000,
                            "crafting did not consume any FE");
                })
                // The recipe has no ingredients left, so extra ticks must not create a second table.
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                            "the machine duplicated its output");
                    helper.assertItemEntityNotPresent(Items.CRAFTING_TABLE);
                })
                .thenSucceed();
    }

    /** The machine does not consume anything while ingredients are missing. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void waitsWithoutIngredients(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(crafter.getStatus() == CrafterStatus.MISSING_INGREDIENTS,
                            "expected MISSING_INGREDIENTS but was " + crafter.getStatus());
                    helper.assertTrue(crafter.getProgress() == 0, "progress advanced without ingredients");
                    helper.assertTrue(isOutputEmpty(crafter), "an item appeared without ingredients");
                    helper.assertTrue(crafter.getEnergyStoredForTesting() == 20_000,
                            "idle machine consumed FE");
                })
                .thenSucceed();
    }

    /** Without FE the craft never starts and the ingredients stay in the input slots. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void waitsWithoutEnergy(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.setEnergyStored(0);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(crafter.getStatus() == CrafterStatus.NO_ENERGY,
                            "expected NO_ENERGY but was " + crafter.getStatus());
                    helper.assertTrue(crafter.getProgress() == 0, "progress advanced without FE");
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 4,
                            "ingredients were consumed without FE: " + countInputs(crafter, Items.OAK_PLANKS));
                    helper.assertTrue(isOutputEmpty(crafter), "an item appeared without FE");
                })
                .thenExecute(() -> crafter.setEnergyStored(20_000))
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                            "crafting did not resume after FE was restored");
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "ingredients were not consumed after resuming");
                })
                .thenSucceed();
    }

    /** A full output slot keeps the ingredients; freeing it resumes the craft. */
    @GameTest(template = "quarry_test", timeoutTicks = 350)
    public static void keepsIngredientsWhenOutputIsFull(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.getInventory().setStackInSlot(CrafterBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STONE, 64));
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(crafter.getStatus() == CrafterStatus.OUTPUT_FULL,
                            "expected OUTPUT_FULL but was " + crafter.getStatus());
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 4,
                            "ingredients were lost while the output was full: "
                                    + countInputs(crafter, Items.OAK_PLANKS));
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STONE) == 64,
                            "the blocked craft changed the full output stack");
                })
                .thenExecute(() -> crafter.getInventory().setStackInSlot(CrafterBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY))
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                            "crafting did not resume after the output was emptied");
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "ingredients were not consumed after resuming");
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STONE) == 0,
                            "the old output was not replaced correctly");
                })
                .thenSucceed();
    }

    /** Continuous crafting consumes exactly the recipe inputs for every produced item. */
    @GameTest(template = "quarry_test", timeoutTicks = 800)
    public static void continuousCraftsStayExact(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, STICK);
        // The stick pattern needs a plank in both cells; 4 + 4 lasts four crafts (4 sticks each).
        putStickIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.setEnergyStored(20_000);

        int craftTime = Config.crafterCraftTimeTicks();
        helper.startSequence()
                .thenIdle(craftTime * 4 + 40)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STICK) == 16,
                            "expected 16 sticks after four crafts, found "
                                    + count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STICK));
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "planks left after four crafts: " + countInputs(crafter, Items.OAK_PLANKS));
                })
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(
                        count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STICK) == 16,
                        "the machine kept producing after the materials were used up"))
                .thenSucceed();
    }

    /** Pipes may fill the inputs and pull the result, but cannot write into the output slot. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void itemHandlerSeparatesInputsAndOutput(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        IItemHandler handler = crafter.getItemHandler();

        ItemStack rejected = handler.insertItem(CrafterBlockEntity.OUTPUT_SLOT,
                new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(rejected.getCount() == 4,
                "the output slot accepted an item from automation: " + rejected);
        helper.assertTrue(handler.getStackInSlot(CrafterBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the output slot is not empty after a rejected insertion");

        ItemStack leftover = handler.insertItem(0, new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(leftover.isEmpty(), "the input slot rejected planks: " + leftover);
        helper.assertTrue(handler.extractItem(0, 1, true).isEmpty(),
                "automation can extract ingredients from an input slot");

        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    ItemStack extracted = handler.extractItem(CrafterBlockEntity.OUTPUT_SLOT, 1, false);
                    helper.assertTrue(extracted.is(Items.CRAFTING_TABLE) && extracted.getCount() == 1,
                            "automation could not extract the crafted result: " + extracted);
                    helper.assertTrue(handler.getStackInSlot(CrafterBlockEntity.OUTPUT_SLOT).isEmpty(),
                            "the output slot still holds items after extraction");
                })
                .thenSucceed();
    }

    /** A real item pipe can insert ingredients into the machine. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void pipeInsertsIngredients(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, PIPE_Y, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, PIPE_Y, 2));
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(3, PIPE_Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        source.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));

        for (int tick = 0; tick < 200 && countInputs(crafter, Items.OAK_PLANKS) < 8; tick++) {
            tickPipe(helper, pipe);
        }

        helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 8,
                "the pipe delivered " + countInputs(crafter, Items.OAK_PLANKS) + " of 8 planks");
        helper.assertTrue(count(source, Items.OAK_PLANKS) == 0,
                "the source chest still holds " + count(source, Items.OAK_PLANKS) + " planks");
        helper.assertTrue(isOutputEmpty(crafter), "an ingredient ended up in the output slot");
        helper.succeed();
    }

    /** A real item pipe can extract the crafted result from the output slot. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pipeExtractsTheResult(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, PIPE_Y, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, PIPE_Y, 2));
        Container sink = placeChest(helper, new BlockPos(4, PIPE_Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    // The pipe has its own ticker, so it may already have moved the result.
                    int delivered = count(sink, Items.CRAFTING_TABLE);
                    int held = count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE);
                    helper.assertTrue(delivered + held == 1,
                            "the machine did not craft before the pipe test (sink=" + delivered
                                    + ", output=" + held + ")");
                })
                .thenExecute(() -> {
                    for (int tick = 0; tick < 200 && count(sink, Items.CRAFTING_TABLE) < 1; tick++) {
                        tickPipe(helper, pipe);
                    }
                })
                .thenExecute(() -> {
                    helper.assertTrue(count(sink, Items.CRAFTING_TABLE) == 1,
                            "the pipe delivered " + count(sink, Items.CRAFTING_TABLE) + " of 1 crafting table");
                    helper.assertTrue(isOutputEmpty(crafter), "the output slot was not emptied by the pipe");
                    helper.assertTrue(pipe.getBufferCount() == 0, "the pipe still holds the result");
                })
                .thenSucceed();
    }

    /** The menu moves ingredients in and the result out, without duplicating either. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void menuMovesItemsBothWays(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        CrafterMenu menu = new CrafterMenu(0, player.getInventory(), crafter, crafter.getContainerData());

        // Menu index 9 is the output, 10 the motor upgrade, 11..37 the main inventory and
        // 38..46 the hotbar.
        menu.quickMoveStack(player, 38);
        helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 4,
                "the menu inserted " + countInputs(crafter, Items.OAK_PLANKS) + " of 4 planks");
        helper.assertTrue(player.getInventory().countItem(Items.OAK_PLANKS) == 0,
                "the player kept the planks after the shift-click");

        // A shift-click fills the input slots in order (0, 1, 2, 3), which is not the 2x2 shape
        // the crafting table needs. Arrange the recipe cells explicitly; Phase 81 refuses to craft
        // a shaped recipe whose cells are wrong.
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            crafter.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        crafter.setEnergyStored(20_000);
        helper.startSequence()
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> {
                    menu.quickMoveStack(player, CrafterBlockEntity.OUTPUT_SLOT);
                    helper.assertTrue(isOutputEmpty(crafter), "the menu did not take the result out");
                    helper.assertTrue(player.getInventory().countItem(Items.CRAFTING_TABLE) == 1,
                            "the player holds " + player.getInventory().countItem(Items.CRAFTING_TABLE)
                                    + " crafting tables instead of 1");
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "the menu duplicated the ingredients");
                })
                .thenSucceed();
    }

    /** The recipe id and all items survive a save/load round trip and the machine keeps working. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void survivesSaveAndLoad(GameTestHelper helper) {
        CrafterBlockEntity original = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, original, CRAFTING_TABLE);
        putIngredients(original, Items.OAK_PLANKS, 4);
        original.setEnergyStored(12_345);

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        CrafterBlockEntity restored = placeCrafter(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(CRAFTING_TABLE.toString().equals(restored.getSelectedRecipeId()),
                "the recipe id was not restored: " + restored.getSelectedRecipeId());
        helper.assertTrue(countInputs(restored, Items.OAK_PLANKS) == 4,
                "the inputs were not restored: " + countInputs(restored, Items.OAK_PLANKS));
        helper.assertTrue(restored.getEnergyStoredForTesting() == 12_345,
                "the FE buffer was not restored: " + restored.getEnergyStoredForTesting());

        helper.startSequence()
                .thenIdle(Config.crafterCraftTimeTicks() + 20)
                .thenExecute(() -> helper.assertTrue(
                        count(restored, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                        "the restored machine did not craft"))
                .thenSucceed();
    }

    /** Two motors shorten the craft and raise the FE/tick, and are consumed exactly once. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void motorsUpgradeSpeedAndPowerUse(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        selectRecipe(helper, crafter, CRAFTING_TABLE);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.getInventory().setStackInSlot(CrafterBlockEntity.UPGRADE_SLOT,
                new ItemStack(ModItems.MOTOR.get(), 2));
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(crafter.getSpeedLevel() == 2,
                            "expected speed level 2 but was " + crafter.getSpeedLevel());
                    helper.assertTrue(crafter.getInventory()
                                    .getStackInSlot(CrafterBlockEntity.UPGRADE_SLOT).isEmpty(),
                            "the installed motors were not consumed exactly once");
                    helper.assertTrue(crafter.getCraftTime()
                                    == CrafterSpeed.craftTime(Config.crafterCraftTimeTicks(), 2),
                            "the upgraded craft time is " + crafter.getCraftTime());
                    helper.assertTrue(crafter.getEnergyPerTick()
                                    == CrafterSpeed.energyPerTick(Config.crafterEnergyPerTick(), 2),
                            "the upgraded FE/tick is " + crafter.getEnergyPerTick());
                })
                .thenIdle(crafterTime(crafter) + 20)
                .thenExecute(() -> {
                    helper.assertTrue(count(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.CRAFTING_TABLE) == 1,
                            "the upgraded machine did not craft exactly one table");
                    helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                            "the upgraded machine duplicated its ingredients");
                })
                .thenSucceed();
    }

    /** A full stack of motors installs in one tick and survives a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void speedLevelSurvivesSaveAndLoad(GameTestHelper helper) {
        CrafterBlockEntity original = placeCrafter(helper, new BlockPos(2, Y, 2));
        // One full stack: the new maximum, installed in a single tick.
        original.getInventory().setStackInSlot(CrafterBlockEntity.UPGRADE_SLOT,
                new ItemStack(ModItems.MOTOR.get(), CrafterSpeed.MAX_LEVEL));
        CrafterBlockEntity.serverTick(helper.getLevel(), original.getBlockPos(),
                original.getBlockState(), original);
        helper.assertTrue(original.getSpeedLevel() == CrafterSpeed.MAX_LEVEL,
                "a full stack installed only " + original.getSpeedLevel() + " levels");
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        CrafterBlockEntity restored = placeCrafter(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getSpeedLevel() == CrafterSpeed.MAX_LEVEL,
                "the speed level was not restored: " + restored.getSpeedLevel());
        helper.assertTrue(restored.getInventory().getStackInSlot(CrafterBlockEntity.UPGRADE_SLOT)
                        .isEmpty(),
                "the installed stack was not consumed");
        helper.succeed();
    }

    /** A recipe id that no longer exists stops the machine instead of using a stale recipe. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void missingRecipeDoesNotCraft(GameTestHelper helper) {
        CrafterBlockEntity source = placeCrafter(helper, new BlockPos(2, Y, 2));
        source.getInventory().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 4));
        CompoundTag tag = source.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.putString("Recipe", "industrialcore:missing_recipe");

        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(5, Y, 2));
        crafter.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue("industrialcore:missing_recipe".equals(crafter.getSelectedRecipeId()),
                "recipe id was not restored by the load: '" + crafter.getSelectedRecipeId() + "'");
        crafter.setEnergyStored(20_000);

        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> helper.assertTrue(crafter.getStatus() == CrafterStatus.RECIPE_MISSING,
                        "expected RECIPE_MISSING but was " + crafter.getStatus()
                                + " (selected='" + crafter.getSelectedRecipeId() + "')"))
                .thenSucceed();
    }

    /** Breaking the block drops the ingredients and the result, as the other machines do. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void dropsContentsWhenBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, Y, 2);
        CrafterBlockEntity crafter = placeCrafter(helper, pos);
        putIngredients(crafter, Items.OAK_PLANKS, 4);
        crafter.getInventory().setStackInSlot(CrafterBlockEntity.OUTPUT_SLOT, new ItemStack(Items.DIAMOND, 3));

        breakByPlayer(helper, pos);

        helper.assertItemEntityPresent(Items.OAK_PLANKS, pos, 4.0D);
        helper.assertItemEntityPresent(Items.DIAMOND, pos, 4.0D);
        helper.succeed();
    }

    /** An idle, unconfigured machine does almost no work per tick. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void idleCrafterIsCheap(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        BlockState state = crafter.getBlockState();
        for (int warm = 0; warm < 100; warm++) {
            CrafterBlockEntity.serverTick(helper.getLevel(), crafter.getBlockPos(), state, crafter);
        }

        int iterations = 1_000;
        long start = System.nanoTime();
        for (int tick = 0; tick < iterations; tick++) {
            CrafterBlockEntity.serverTick(helper.getLevel(), crafter.getBlockPos(), state, crafter);
        }
        double microsPerTick = (System.nanoTime() - start) / (double) iterations / 1_000.0D;
        IndustrialCore.LOGGER.info("[perf] idle auto crafter: {} us/tick", String.format("%.3f", microsPerTick));
        helper.assertTrue(microsPerTick < 20.0D,
                "an idle auto crafter tick should stay far below 20 us but was " + microsPerTick + " us");
        helper.assertTrue(crafter.getStatus() == CrafterStatus.NO_RECIPE,
                "an unconfigured crafter should report NO_RECIPE but was " + crafter.getStatus());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static int crafterTime(CrafterBlockEntity crafter) {
        return crafter.getCraftTime();
    }

    private static void selectRecipe(GameTestHelper helper, CrafterBlockEntity crafter, ResourceLocation id) {
        crafter.setSelectedRecipe(id);
        helper.assertTrue(id.toString().equals(crafter.getSelectedRecipeId()),
                "recipe " + id + " could not be selected (selected=" + crafter.getSelectedRecipeId() + ")");
    }

    private static CrafterBlockEntity placeCrafter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.CRAFTER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof CrafterBlockEntity crafter)) {
            helper.fail("auto crafter block entity missing at " + pos);
            return null;
        }
        return crafter;
    }

    /**
     * Places the crafting-table pattern (2x2 in the top-left corner: grid cells 0, 1, 3, 4).
     * Phase 81 made the shape authoritative, so a test that filled 0..count-1 no longer crafts.
     */
    private static void putIngredients(CrafterBlockEntity crafter, Item item, int count) {
        ItemStackHandler inventory = crafter.getInventory();
        int[] table = {0, 1, 3, 4};
        for (int index = 0; index < count && index < table.length; index++) {
            inventory.setStackInSlot(table[index], new ItemStack(item, 1));
        }
    }

    /** The stick recipe is a vertical 1x2 pattern, i.e. grid cells 0 and 3. */
    private static void putStickIngredients(CrafterBlockEntity crafter, Item item, int perSlot) {
        crafter.getInventory().setStackInSlot(0, new ItemStack(item, perSlot));
        crafter.getInventory().setStackInSlot(3, new ItemStack(item, perSlot));
    }

    private static int count(CrafterBlockEntity crafter, int slot, Item item) {
        ItemStack stack = crafter.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int countInputs(CrafterBlockEntity crafter, Item item) {
        int total = 0;
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            ItemStack stack = crafter.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean isOutputEmpty(CrafterBlockEntity crafter) {
        return crafter.getInventory().getStackInSlot(CrafterBlockEntity.OUTPUT_SLOT).isEmpty();
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

    private static Container placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof Container container)) {
            helper.fail("chest block entity missing at " + pos);
            return null;
        }
        return container;
    }

    private static void tickPipe(GameTestHelper helper, ItemPipeBlockEntity pipe) {
        BlockState state = pipe.getBlockState();
        ItemPipeBlockEntity.serverTick(helper.getLevel(), pipe.getBlockPos(), state, pipe);
    }

    private static int count(Container container, Item item) {
        return container == null ? 0 : container.countItem(item);
    }

    private static void breakByPlayer(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        state.getBlock().playerWillDestroy(helper.getLevel(), absolute, state, player);
        helper.getLevel().removeBlock(absolute, false);
    }
}
