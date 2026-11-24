package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.PhaseReactorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.MythicOres;
import com.industrialcore.machine.PhaseReactorBalance;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Phase 24: the phase reactor, driven through the real server tick and the real recipe manager.
 *
 * <p>The contract under test: three interchangeable ingredients produce exactly one result, the
 * recipe's own time and FE per tick are charged, a full output freezes the machine before anything
 * is spent, missing FE keeps the progress, changing an ingredient discards it, the run survives a
 * save and reload, and a pipe can feed the ingredient slots and drain the result.</p>
 *
 * <p>What these tests cannot see: how the reactor looks and feels in a real client, and where the
 * eight ores actually generate in a real world. Those stay marked NOT RUN in the docs.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseReactorGameTests {
    private static final int Y = 6;

    private PhaseReactorGameTests() {}

    /** Two nebula crystals and one orichalcum ingot make one catalyst: 200 ticks, 4,000 FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void theCatalystRunProducesOneCatalyst(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.ORICHALCUM_INGOT.get()));
        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);

        int ticks = runUntilOutput(helper, reactor, 400);

        ItemStack output = reactor.getInventory()
                .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.RESONANT_CATALYST.get()) && output.getCount() == 1,
                "the catalyst run must produce one catalyst, got " + output);
        helper.assertTrue(ticks == PhaseReactorBalance.CATALYST_TIME_TICKS,
                "the run took " + ticks + " ticks instead of "
                        + PhaseReactorBalance.CATALYST_TIME_TICKS);
        for (int slot = 0; slot < PhaseReactorBlockEntity.INPUT_COUNT; slot++) {
            helper.assertTrue(reactor.getInventory().getStackInSlot(slot).isEmpty(),
                    "ingredient slot " + slot + " was not consumed");
        }
        helper.assertTrue(reactor.getEnergyStoredForTesting()
                        == PhaseReactorBalance.CAPACITY - PhaseReactorBalance.catalystCost(),
                "the run spent " + (PhaseReactorBalance.CAPACITY
                        - reactor.getEnergyStoredForTesting()) + " FE instead of "
                        + PhaseReactorBalance.catalystCost());
        helper.succeed();
    }

    /** The top run: void iron, a quantum shard and an aether crystal -> one phase core. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void theCoreRunTakesTheTopMaterials(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.VOID_IRON_INGOT.get()),
                new ItemStack(ModItems.QUANTUM_SHARD.get()),
                new ItemStack(ModItems.AETHER_CRYSTAL.get()));
        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);

        int ticks = runUntilOutput(helper, reactor, 600);
        ItemStack output = reactor.getInventory()
                .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.PHASE_CORE.get()) && output.getCount() == 1,
                "the core run must produce one phase core, got " + output);
        helper.assertTrue(ticks == PhaseReactorBalance.CORE_TIME_TICKS,
                "the core run took " + ticks + " ticks");
        helper.assertTrue(reactor.getEnergyStoredForTesting()
                        == PhaseReactorBalance.CAPACITY - PhaseReactorBalance.coreCost(),
                "the core run spent the wrong amount of FE");
        helper.succeed();
    }

    /** The order of the three ingredients never matters for the real recipe manager. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void theIngredientOrderDoesNotMatter(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.ORICHALCUM_INGOT.get()),
                new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.NEBULA_CRYSTAL.get()));
        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);
        runUntilOutput(helper, reactor, 400);
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.RESONANT_CATALYST.get()),
                "the reversed ingredient order must still make a catalyst");
        helper.succeed();
    }

    /** A full output freezes the run before any FE or item is spent. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aFullOutputFreezesTheRun(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.MITHRIL_INGOT.get()),
                new ItemStack(ModItems.MITHRIL_INGOT.get()),
                new ItemStack(ModItems.ORICHALCUM_INGOT.get()));
        reactor.getInventory().setStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.PHASE_CORE.get(), 64));
        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);

        for (int tick = 0; tick < 60; tick++) {
            tickReactor(helper, reactor);
        }

        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.INPUT_START).getCount() == 1,
                "an ingredient was consumed while the output was full");
        helper.assertTrue(reactor.getEnergyStoredForTesting() == PhaseReactorBalance.CAPACITY,
                "FE was spent while the output was full");
        helper.assertTrue(reactor.getProgress() == 0,
                "the run advanced while the output was full");
        helper.assertTrue(reactor.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + reactor.getStatus());
        helper.succeed();
    }

    /** Missing FE keeps the progress and the ingredients; adding FE finishes the same run. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void missingEnergyKeepsTheProgress(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.ORICHALCUM_INGOT.get()));
        // Exactly half the FE a run needs.
        int half = (int) (PhaseReactorBalance.catalystCost() / 2);
        reactor.setEnergyStored(half);

        for (int tick = 0; tick < 300
                && reactor.getInventory().getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                .isEmpty(); tick++) {
            tickReactor(helper, reactor);
        }

        helper.assertTrue(reactor.getStatus() == MachineStatus.NO_ENERGY,
                "expected NO_ENERGY but was " + reactor.getStatus() + " after "
                        + reactor.getProgress() + " ticks");
        int progress = reactor.getProgress();
        helper.assertTrue(progress > 0 && progress < PhaseReactorBalance.CATALYST_TIME_TICKS,
                "the half-paid run stopped at tick " + progress);
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.INPUT_START).getCount() == 1,
                "the ingredients were consumed before the run finished");

        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);
        int remaining = PhaseReactorBalance.CATALYST_TIME_TICKS - progress;
        for (int tick = 0; tick < remaining + 5
                && reactor.getInventory().getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                .isEmpty(); tick++) {
            tickReactor(helper, reactor);
        }
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.RESONANT_CATALYST.get()),
                "the run did not finish after the FE was restored");
        helper.succeed();
    }

    /** Pulling an ingredient discards the partial run instead of finishing another product. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void changingAnIngredientResetsTheRun(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(reactor, new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.NEBULA_CRYSTAL.get()),
                new ItemStack(ModItems.ORICHALCUM_INGOT.get()));
        reactor.setEnergyStored(PhaseReactorBalance.CAPACITY);

        for (int tick = 0; tick < 30; tick++) {
            tickReactor(helper, reactor);
        }
        helper.assertTrue(reactor.getProgress() > 0, "the run did not start");

        reactor.getInventory().setStackInSlot(PhaseReactorBlockEntity.INPUT_START,
                new ItemStack(ModItems.QUANTUM_SHARD.get()));
        tickReactor(helper, reactor);

        helper.assertTrue(reactor.getProgress() == 0,
                "the run continued after an ingredient changed: " + reactor.getProgress());
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT).isEmpty(),
                "a product was created from a changed recipe");
        helper.assertTrue(reactor.getStatus() == MachineStatus.NO_RECIPE,
                "expected NO_RECIPE but was " + reactor.getStatus());
        helper.succeed();
    }

    /** A save and reload in the middle of a run continues the same run. */
    @GameTest(template = "quarry_test", timeoutTicks = 900)
    public static void theRunSurvivesASaveAndReload(GameTestHelper helper) {
        PhaseReactorBlockEntity original = placeReactor(helper, new BlockPos(2, Y, 2));
        fill(original, new ItemStack(ModItems.MITHRIL_INGOT.get()),
                new ItemStack(ModItems.MITHRIL_INGOT.get()),
                new ItemStack(ModItems.ORICHALCUM_INGOT.get()));
        original.setEnergyStored(PhaseReactorBalance.CAPACITY);
        for (int tick = 0; tick < 50; tick++) {
            tickReactor(helper, original);
        }
        int savedProgress = original.getProgress();

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        PhaseReactorBlockEntity restored = placeReactor(helper, new BlockPos(5, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "progress was restored as " + restored.getProgress() + " instead of "
                        + savedProgress);
        helper.assertTrue(restored.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.INPUT_START).getCount() == 1,
                "the ingredients of the run were not restored");

        int remaining = PhaseReactorBalance.MOTOR_TIME_TICKS - savedProgress;
        for (int tick = 0; tick < remaining + 5
                && restored.getInventory().getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                .isEmpty(); tick++) {
            tickReactor(helper, restored);
        }
        helper.assertTrue(restored.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT)
                        .is(ModItems.HIGH_PERFORMANCE_MOTOR.get()),
                "the restored run did not finish");
        helper.succeed();
    }

    /** Automation fills the three ingredient slots and can only drain the result slot. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void automationCannotFillTheResultSlot(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        IItemHandler automation = reactor.getItemHandler(Direction.NORTH);
        ItemStack leftover = automation.insertItem(PhaseReactorBlockEntity.INPUT_START,
                new ItemStack(ModItems.NEBULA_CRYSTAL.get(), 4), false);
        helper.assertTrue(leftover.isEmpty(),
                "an ingredient was rejected by the automation handler: " + leftover);
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.INPUT_START).getCount() == 4,
                "the pipe insertion did not reach the slot");

        ItemStack intoOutput = automation.insertItem(PhaseReactorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.PHASE_CORE.get()), false);
        helper.assertTrue(intoOutput.getCount() == 1,
                "automation filled the result slot");
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the result slot received an item from automation");

        reactor.getInventory().setStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.PHASE_CORE.get(), 2));
        helper.assertTrue(automation.extractItem(PhaseReactorBlockEntity.INPUT_START, 1, true)
                        .isEmpty(),
                "automation may not drain an ingredient slot");
        helper.assertTrue(automation.extractItem(PhaseReactorBlockEntity.OUTPUT_SLOT, 1, false)
                        .getCount() == 1,
                "automation could not drain the result slot");
        helper.succeed();
    }

    /** A pipe drains the finished product into a chest. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aPipeDrainsTheResult(GameTestHelper helper) {
        PhaseReactorBlockEntity reactor = placeReactor(helper, new BlockPos(2, Y, 2));
        reactor.getInventory().setStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.DURABLE_MACHINE_FRAME.get(), 4));
        ItemPipeBlockEntity pipe = placePipe(helper, new BlockPos(3, Y, 2));
        Container chest = placeChest(helper, new BlockPos(4, Y, 2));
        pipe.setMode(Direction.WEST, PipeConnectionMode.PULL);
        pipe.setMode(Direction.EAST, PipeConnectionMode.PUSH);

        for (int tick = 0; tick < 200
                && chest.countItem(ModItems.DURABLE_MACHINE_FRAME.get()) < 4; tick++) {
            tickPipe(helper, pipe);
        }

        helper.assertTrue(chest.countItem(ModItems.DURABLE_MACHINE_FRAME.get()) == 4,
                "the chest received " + chest.countItem(ModItems.DURABLE_MACHINE_FRAME.get())
                        + " of 4 frames");
        helper.assertTrue(reactor.getInventory()
                        .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the reactor still holds a frame the chest should have received");
        helper.succeed();
    }

    /**
     * Every new ore block is a real ore: in {@code c:ores} and mineable with a pickaxe, so the
     * large quarry ore picker and the vanilla tool rules both see it.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void everyNewOreIsTaggedAsAnOre(GameTestHelper helper) {
        int index = 0;
        for (MythicOres.Ore ore : MythicOres.ALL) {
            BlockPos pos = new BlockPos(1 + (index % 4) * 2, Y, 1 + (index / 4) * 2);
            BlockState state = switch (ore.id()) {
                case "astral_silver" -> ModBlocks.ASTRAL_SILVER_ORE.get().defaultBlockState();
                case "mithril" -> ModBlocks.MITHRIL_ORE.get().defaultBlockState();
                case "nebula_crystal" -> ModBlocks.NEBULA_ORE.get().defaultBlockState();
                case "orichalcum" -> ModBlocks.ORICHALCUM_ORE.get().defaultBlockState();
                case "quantum_ore" -> ModBlocks.QUANTUM_ORE.get().defaultBlockState();
                case "adamantite" -> ModBlocks.ADAMANTITE_ORE.get().defaultBlockState();
                case "aether_crystal" -> ModBlocks.AETHER_ORE.get().defaultBlockState();
                default -> ModBlocks.VOID_IRON_ORE.get().defaultBlockState();
            };
            helper.setBlock(pos, state);
            BlockState placed = helper.getBlockState(pos);
            helper.assertTrue(placed.is(ModTags.ORES),
                    ore.blockId() + " is missing from c:ores (the large quarry would ignore it)");
            helper.assertTrue(placed.is(BlockTags.MINEABLE_WITH_PICKAXE),
                    ore.blockId() + " is not mineable with a pickaxe");
            index++;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static PhaseReactorBlockEntity placeReactor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.PHASE_REACTOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof PhaseReactorBlockEntity reactor)) {
            helper.fail("phase reactor block entity missing at " + pos);
            return null;
        }
        return reactor;
    }

    private static void fill(PhaseReactorBlockEntity reactor, ItemStack... stacks) {
        for (int index = 0; index < stacks.length; index++) {
            reactor.getInventory().setStackInSlot(PhaseReactorBlockEntity.INPUT_START + index,
                    stacks[index]);
        }
    }

    /** Ticks the reactor until the result slot is not empty and returns the tick count. */
    private static int runUntilOutput(GameTestHelper helper, PhaseReactorBlockEntity reactor,
                                      int maxTicks) {
        int ticks = 0;
        for (; ticks < maxTicks
                && reactor.getInventory()
                .getStackInSlot(PhaseReactorBlockEntity.OUTPUT_SLOT).isEmpty(); ticks++) {
            tickReactor(helper, reactor);
        }
        return ticks;
    }

    private static void tickReactor(GameTestHelper helper, PhaseReactorBlockEntity reactor) {
        BlockState state = reactor.getBlockState();
        PhaseReactorBlockEntity.serverTick(helper.getLevel(), reactor.getBlockPos(), state,
                reactor);
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
}
