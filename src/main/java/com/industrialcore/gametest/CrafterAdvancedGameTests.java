package com.industrialcore.gametest;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.ItemFilterMode;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 11: recipe-slot distribution, recipe registration from the grid, and per-face routing.
 * Phase 81: the shape fix consumes the exact pattern slots, and the GUI toggle is gone.
 *
 * <p>All tests use the real server block ticker, the real recipe manager and real item handlers, so
 * a wrong slot, a lost item or a duplicated craft fails the test instead of only the GUI.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrafterAdvancedGameTests {
    private static final int Y = 6;
    private static final int PIPE_Y = 9;
    private static final ResourceLocation STICK = ResourceLocation.withDefaultNamespace("stick");
    private static final ResourceLocation CRAFTING_TABLE =
            ResourceLocation.withDefaultNamespace("crafting_table");

    private CrafterAdvancedGameTests() {}

    /**
     * The reported bug: a pipe feeding planks used to fill slot 0 up to 64. The planks must be
     * levelled over both slots of the stick recipe, because the shape fix consumes both.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pipeSpreadsIngredientsOverRecipeSlots(GameTestHelper helper) {
        Container source = placeChest(helper, new BlockPos(1, PIPE_Y, 2));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(2, PIPE_Y, 2));
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(3, PIPE_Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);
        crafter.setSelectedRecipe(STICK);
        crafter.setEnergyStored(20_000);
        source.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));

        for (int tick = 0; tick < 300 && countInputs(crafter, Items.OAK_PLANKS) < 16; tick++) {
            tickPipe(helper, pipe);
        }

        // The stick recipe is a vertical 1x2 pattern, so its two slots are grid cells 0 and 3.
        int slot0 = countInSlot(crafter, 0, Items.OAK_PLANKS);
        int slot3 = countInSlot(crafter, 3, Items.OAK_PLANKS);
        int total = countInputs(crafter, Items.OAK_PLANKS);
        IndustrialCore.LOGGER.info("[perf] crafter slots: slot0={} slot3={} total={}",
                slot0, slot3, total);
        helper.assertTrue(total == 16, "the pipe delivered " + total + " of 16 planks");
        helper.assertTrue(slot3 > 0,
                "the planks were still piled into a single slot (slot0=" + slot0 + ", slot3=" + slot3 + ")");
        assertBalanced(helper, crafter, Items.OAK_PLANKS);
        helper.assertTrue(countInSlot(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STICK) == 0,
                "the machine crafted before the test could inspect the distribution");

        // The recipe still crafts: 8 pairs of planks produce 32 sticks.
        for (int tick = 0; tick < 800; tick++) {
            tickCrafter(helper, crafter);
        }
        int sticks = countInSlot(crafter, CrafterBlockEntity.OUTPUT_SLOT, Items.STICK);
        helper.assertTrue(sticks == 32,
                "the machine produced " + sticks + " sticks instead of 32");
        helper.assertTrue(countInputs(crafter, Items.OAK_PLANKS) == 0,
                "planks were left after crafting: " + countInputs(crafter, Items.OAK_PLANKS));
        helper.succeed();
    }

    /** An item that no recipe slot accepts is rejected instead of being parked wrongly. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void wrongItemIsRejectedWhenARecipeIsSelected(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        crafter.setSelectedRecipe(STICK);
        ItemStack rejected = crafter.getItemHandler().insertItem(0, new ItemStack(Items.DIAMOND, 4), false);
        helper.assertTrue(rejected.getCount() == 4,
                "a diamond was accepted into a stick recipe grid: " + rejected);
        helper.assertTrue(countInputs(crafter, Items.DIAMOND) == 0, "the diamond was stored anyway");
        helper.succeed();
    }

    /** Arranging the grid and pressing "register" selects the matching vanilla recipe. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void registersRecipeFromTheGrid(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        // 2x2 planks in the top-left corner equal the crafting table recipe.
        crafter.getInventory().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 1));
        crafter.getInventory().setStackInSlot(1, new ItemStack(Items.OAK_PLANKS, 1));
        crafter.getInventory().setStackInSlot(3, new ItemStack(Items.OAK_PLANKS, 1));
        crafter.getInventory().setStackInSlot(4, new ItemStack(Items.OAK_PLANKS, 1));

        ItemStack preview = crafter.previewGridResult();
        helper.assertTrue(preview.is(Items.CRAFTING_TABLE),
                "the preview shows " + preview + " instead of a crafting table");
        helper.assertTrue(crafter.registerRecipeFromGrid(), "the grid recipe was not registered");
        helper.assertTrue(CRAFTING_TABLE.toString().equals(crafter.getSelectedRecipeId()),
                "registered '" + crafter.getSelectedRecipeId() + "' instead of the crafting table");

        crafter.setEnergyStored(20_000);
        for (int tick = 0; tick < Config.crafterCraftTimeTicks() + 40; tick++) {
            tickCrafter(helper, crafter);
        }
        helper.assertTrue(countInSlot(crafter, CrafterBlockEntity.OUTPUT_SLOT,
                        Items.CRAFTING_TABLE) == 1,
                "the registered recipe did not craft");
        helper.succeed();
    }

    /** An arrangement that forms no recipe must not change the selection. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void invalidGridIsRejected(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        crafter.setSelectedRecipe(STICK);
        // A lone diamond matches no vanilla crafting recipe at all.
        crafter.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(crafter.previewGridResult().isEmpty(),
                "a lone diamond previewed a result: " + crafter.previewGridResult());
        helper.assertTrue(!crafter.registerRecipeFromGrid(), "an invalid grid was registered");
        helper.assertTrue(STICK.toString().equals(crafter.getSelectedRecipeId()),
                "a failed registration changed the selected recipe to " + crafter.getSelectedRecipeId());

        CrafterBlockEntity empty = placeCrafter(helper, new BlockPos(5, Y, 2));
        helper.assertTrue(!empty.registerRecipeFromGrid(), "an empty grid was registered as a recipe");
        helper.succeed();
    }

    /** Per-face modes and target slots are enforced by the real capability. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void faceConfigRoutesSlots(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        BlockPos pos = crafter.getBlockPos();
        MachineFaceConfig config = crafter.getFaceConfig();
        config.setMode(Direction.UP.get3DDataValue(), FaceMode.INPUT);
        config.setSlot(Direction.UP.get3DDataValue(), 2);
        config.setMode(Direction.DOWN.get3DDataValue(), FaceMode.OUTPUT);
        config.setMode(Direction.EAST.get3DDataValue(), FaceMode.NONE);

        IItemHandler top = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        IItemHandler bottom = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        IItemHandler east = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, pos, Direction.EAST);
        helper.assertTrue(top != null && bottom != null && east != null,
                "a face capability was not registered");

        // The top face only feeds slot 2.
        ItemStack refused = top.insertItem(0, new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(refused.getCount() == 4, "the top face wrote into slot 0");
        ItemStack accepted = top.insertItem(2, new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(accepted.isEmpty(), "the top face did not accept into its target slot");
        helper.assertTrue(countInSlot(crafter, 2, Items.OAK_PLANKS) == 4,
                "slot 2 holds " + countInSlot(crafter, 2, Items.OAK_PLANKS) + " of 4 planks");

        // NONE is a hard wall, even though the item would be accepted by the machine.
        ItemStack blocked = east.insertItem(0, new ItemStack(Items.OAK_PLANKS, 4), false);
        helper.assertTrue(blocked.getCount() == 4, "a NONE face accepted items");

        // The bottom face only extracts, and only from the output slot.
        crafter.getInventory().setStackInSlot(2, new ItemStack(Items.OAK_PLANKS, 2));
        helper.assertTrue(bottom.extractItem(2, 1, true).isEmpty(),
                "an output face extracted from an input slot");
        crafter.getInventory().setStackInSlot(CrafterBlockEntity.OUTPUT_SLOT,
                new ItemStack(Items.CRAFTING_TABLE, 1));
        ItemStack extracted = bottom.extractItem(CrafterBlockEntity.OUTPUT_SLOT, 1, false);
        helper.assertTrue(extracted.is(Items.CRAFTING_TABLE),
                "the output face could not extract the result: " + extracted);
        helper.succeed();
    }

    /** Allow and deny filters are enforced on the capability, not only in the GUI. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void faceFiltersAllowAndDeny(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        MachineFaceConfig config = crafter.getFaceConfig();
        int west = Direction.WEST.get3DDataValue();
        config.setMode(west, FaceMode.INPUT);
        config.setFilterMode(west, ItemFilterMode.ALLOW);
        config.toggleAllowed(west, "minecraft:iron_ore");

        IItemHandler handler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, crafter.getBlockPos(), Direction.WEST);
        helper.assertTrue(handler != null, "the west capability is missing");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_ORE, 2), false).isEmpty(),
                "the allow filter blocked the listed item");
        ItemStack refused = handler.insertItem(0, new ItemStack(Items.GOLD_ORE, 2), false);
        helper.assertTrue(refused.getCount() == 2, "the allow filter let an unlisted item through");

        config.setFilterMode(west, ItemFilterMode.DENY);
        config.setAllowed(west, List.of("minecraft:iron_ore"));
        ItemStack denied = handler.insertItem(0, new ItemStack(Items.IRON_ORE, 2), false);
        helper.assertTrue(denied.getCount() == 2, "the deny filter let the listed item through");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.GOLD_ORE, 2), false).isEmpty(),
                "the deny filter blocked an unlisted item");
        helper.succeed();
    }

    /** Face settings survive a save/load round trip. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void settingsSurviveSaveAndLoad(GameTestHelper helper) {
        CrafterBlockEntity original = placeCrafter(helper, new BlockPos(2, Y, 2));
        MachineFaceConfig config = original.getFaceConfig();
        config.setMode(Direction.NORTH.get3DDataValue(), FaceMode.INPUT);
        config.setSlot(Direction.NORTH.get3DDataValue(), 4);
        config.setFilterMode(Direction.NORTH.get3DDataValue(), ItemFilterMode.ALLOW);
        config.setAllowed(Direction.NORTH.get3DDataValue(), List.of("minecraft:oak_planks"));
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        CrafterBlockEntity restored = placeCrafter(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        MachineFaceConfig restoredConfig = restored.getFaceConfig();
        int north = Direction.NORTH.get3DDataValue();
        helper.assertTrue(restoredConfig.mode(north) == FaceMode.INPUT,
                "the north mode was restored as " + restoredConfig.mode(north));
        helper.assertTrue(restoredConfig.slot(north) == 4, "the target slot was not restored");
        helper.assertTrue(restoredConfig.filterMode(north) == ItemFilterMode.ALLOW,
                "the filter mode was not restored");
        helper.assertTrue(restoredConfig.allowedIds(north).contains("minecraft:oak_planks"),
                "the filter list was not restored");
        helper.succeed();
    }

    /** A machine saved before Phase 11 loads with the default (old) behaviour. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void legacyMachineKeepsWorking(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        CompoundTag tag = crafter.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.remove("Faces");
        CrafterBlockEntity restored = placeCrafter(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getFaceConfig().isDefault(),
                "a legacy machine did not load the default face configuration");
        helper.assertTrue(restored.getItemHandler().insertItem(0,
                new ItemStack(Items.OAK_PLANKS, 2), false).isEmpty(),
                "a legacy machine no longer accepts items");
        helper.succeed();
    }

    /** The per-face settings are part of the client display tag, so the GUI can show them. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void updateTagCarriesFaceSettings(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        crafter.getFaceConfig().setMode(Direction.UP.get3DDataValue(), FaceMode.OUTPUT);
        CompoundTag update = crafter.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(update.contains("Faces"), "the update tag does not carry the face settings");
        helper.assertTrue(update.getCompound("Faces").contains("1"), "the up face is missing");
        helper.succeed();
    }

    /**
     * Regression: the GUI buttons used to map to the wrong action on the crafter, so "mode"
     * changed the item filter. Every button must change exactly the setting it names.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void guiActionsChangeOnlyTheirOwnSetting(GameTestHelper helper) {
        CrafterBlockEntity crafter = placeCrafter(helper, new BlockPos(2, Y, 2));
        int up = Direction.UP.get3DDataValue();
        MachineFaceConfig config = crafter.getFaceConfig();

        helper.assertTrue(crafter.applyFaceAction(up, MachineActions.FACE_MODE, ItemStack.EMPTY),
                "the mode action was rejected");
        helper.assertTrue(config.mode(up) == FaceMode.INPUT,
                "the mode button produced " + config.mode(up));
        helper.assertTrue(config.filterMode(up) == ItemFilterMode.OFF,
                "the mode button changed the filter to " + config.filterMode(up));

        helper.assertTrue(crafter.applyFaceAction(up, MachineActions.FACE_FILTER, ItemStack.EMPTY),
                "the filter action was rejected");
        helper.assertTrue(config.filterMode(up) == ItemFilterMode.ALLOW,
                "the filter button produced " + config.filterMode(up));
        helper.assertTrue(config.mode(up) == FaceMode.INPUT,
                "the filter button changed the mode to " + config.mode(up));

        helper.assertTrue(crafter.applyFaceAction(up, MachineActions.FACE_SLOT, ItemStack.EMPTY),
                "the slot action was rejected");
        helper.assertTrue(config.slot(up) == 0, "the slot button produced " + config.slot(up));

        helper.assertTrue(crafter.applyFaceAction(up, MachineActions.FACE_ADD_ITEM,
                        new ItemStack(Items.OAK_PLANKS)),
                "the add-filter action was rejected");
        helper.assertTrue(config.allowedIds(up).contains("minecraft:oak_planks"),
                "the carried item was not added to the filter");
        helper.assertTrue(crafter.applyFaceAction(up, MachineActions.FACE_CLEAR, ItemStack.EMPTY),
                "the clear-filter action was rejected");
        helper.assertTrue(config.allowedIds(up).isEmpty(), "the filter list was not cleared");
        // The other faces are untouched by any of the clicks above.
        helper.assertTrue(config.mode(Direction.DOWN.get3DDataValue()) == FaceMode.AUTO,
                "a click on the top face changed the bottom face");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void assertBalanced(GameTestHelper helper, CrafterBlockEntity crafter, Item item) {
        List<Integer> counts = new ArrayList<>();
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            int count = countInSlot(crafter, slot, item);
            if (count > 0) {
                counts.add(count);
            }
        }
        int min = counts.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = counts.stream().mapToInt(Integer::intValue).max().orElse(0);
        helper.assertTrue(max - min <= 1,
                "the recipe slots are unbalanced: " + counts);
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

    private static void tickCrafter(GameTestHelper helper, CrafterBlockEntity crafter) {
        BlockState state = crafter.getBlockState();
        CrafterBlockEntity.serverTick(helper.getLevel(), crafter.getBlockPos(), state, crafter);
    }

    private static int countInSlot(CrafterBlockEntity crafter, int slot, Item item) {
        ItemStack stack = crafter.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int countInputs(CrafterBlockEntity crafter, Item item) {
        int total = 0;
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            total += countInSlot(crafter, slot, item);
        }
        return total;
    }
}
