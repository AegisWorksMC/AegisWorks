package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.SeedLineBalance;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests of the Phase 21 seed line.
 *
 * <p>The tests drive the real server tick and the real recipe manager (they tick the machines in a
 * loop inside one game tick, like the other machine tests). They cover:</p>
 * <ul>
 *   <li>the cultivator: one billet, the four feed instalments, a whole stack of feed, a full
 *       output, the wrong metal and the save/load cycle,</li>
 *   <li>the amplifier: two ore blocks plus four dust, consumption only on completion, one full
 *       output, the wrong metal, save/load and the connection to the existing 2x route.</li>
 * </ul>
 *
 * <p>Phase 31 removed the composite 12x / 16x line tests together with the old multiplication
 * line they drove (their machine routes no longer exist). The fifteen stage replacement has its
 * own GameTests in {@code OreChainGameTests}.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeedLineGameTests {
    private static final int Y = 6;
    /** Starting / topped up FE of every machine (the buffer size of both new machines). */
    private static final int START_ENERGY = 20_000;

    private SeedLineGameTests() {}

    // ------------------------------------------------------------------
    // Seed cultivator
    // ------------------------------------------------------------------

    /** Seed + 4 dust -> 1 billet for exactly 24,000 FE in 600 ticks. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorProducesOneBillet(GameTestHelper helper) {
        SeedCultivatorBlockEntity machine = placeCultivator(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 4));

        long spent = runCultivator(helper, machine, 700);

        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) == 1,
                "the machine produced " + count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) + " billets instead of 1");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.SEED_SLOT,
                ModItems.IRON_SEED.get()) == 0, "the seed was not consumed");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.FEED_SLOT,
                ModItems.IRON_DUST.get()) == 0, "the feed was not consumed");
        helper.assertTrue(spent == SeedLineBalance.billetEnergy(1),
                "the machine spent " + spent + " FE instead of " + SeedLineBalance.billetEnergy(1));
        helper.assertTrue(machine.getBatch() == 0, "the batch was not cleared after the run");
        helper.assertItemEntityNotPresent(ModItems.IRON_BILLET.get());
        helper.succeed();
    }

    /**
     * The feed is paid in four instalments: missing one pauses the run without FE or progress,
     * and the run resumes when the dust arrives.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorConsumesFeedAtCheckpoints(GameTestHelper helper) {
        SeedCultivatorBlockEntity machine = placeCultivator(helper, new BlockPos(2, Y, 2));
        // 8 dust -> a batch of two billets, paid as 2 + 2 + 2 + 2.
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 8));

        tickCultivatorWithEnergy(helper, machine);
        helper.assertTrue(machine.getBatch() == 2,
                "the batch is " + machine.getBatch() + " instead of 2");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.FEED_SLOT,
                ModItems.IRON_DUST.get()) == 6, "the first instalment was not taken");

        // A pipe drains the rest of the feed: the next instalment cannot be paid.
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT, ItemStack.EMPTY);
        int drained = 0;
        // The first checkpoint is at 25% of 2,250 ticks: 563 ticks after the start.
        for (int tick = 0; tick < 900 && machine.getStatus() != MachineStatus.NO_MATERIAL; tick++) {
            drained += tickCultivatorWithEnergy(helper, machine);
        }
        helper.assertTrue(machine.getStatus() == MachineStatus.NO_MATERIAL,
                "the machine did not stop on a missing instalment: " + machine.getStatus());
        int pausedProgress = machine.getProgress();
        int pausedEnergy = machine.getEnergyStoredForTesting();
        for (int tick = 0; tick < 50; tick++) {
            tickCultivatorWithEnergy(helper, machine);
        }
        helper.assertTrue(machine.getProgress() == pausedProgress,
                "the progress advanced without feed");
        helper.assertTrue(machine.getEnergyStoredForTesting() == pausedEnergy,
                "the paused machine still spent FE");

        // Supplying the instalment restarts the run and it finishes with two billets.
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 6));
        long spent = drained + runCultivator(helper, machine, 3_500);
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) == 2,
                "the resumed machine produced "
                        + count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) + " billets instead of 2");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.FEED_SLOT,
                        ModItems.IRON_DUST.get()) == 0,
                "the machine did not consume exactly four instalments");
        helper.assertTrue(spent == SeedLineBalance.billetEnergy(2),
                "the two billet batch spent " + spent + " FE instead of "
                        + SeedLineBalance.billetEnergy(2));
        helper.succeed();
    }

    /**
     * A full stack of feed (64 dust) grows 16 billets in one run.
     *
     * <p>16 is the largest batch a single stack can pay for: a batch of N consumes 4N dust, so the
     * documented N = 64 would need 256 dust. The formula for 64 is covered by the unit test.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorGrowsAWholeStackOfFeed(GameTestHelper helper) {
        SeedCultivatorBlockEntity machine = placeCultivator(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 64));

        long spent = runCultivator(helper, machine, SeedLineBalance.billetTime(16) + 200);

        int billets = count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                ModItems.IRON_BILLET.get());
        helper.assertTrue(billets == 16, "the machine produced " + billets + " billets instead of 16");
        helper.assertTrue(spent == SeedLineBalance.billetEnergy(16),
                "the 16 billet batch spent " + spent + " FE instead of "
                        + SeedLineBalance.billetEnergy(16));
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.FEED_SLOT,
                ModItems.IRON_DUST.get()) == 0, "the feed is not empty");
        helper.succeed();
    }

    /** A full output stops the machine before it consumes anything. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorStopsWhenTheOutputIsFull(GameTestHelper helper) {
        SeedCultivatorBlockEntity machine = placeCultivator(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 4));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 64));
        machine.setEnergyStored(START_ENERGY);

        long spent = runCultivator(helper, machine, 200);

        helper.assertTrue(machine.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + machine.getStatus());
        helper.assertTrue(spent == 0, "the blocked machine spent " + spent + " FE");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.SEED_SLOT,
                ModItems.IRON_SEED.get()) == 1, "the seed was eaten while the output was full");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.FEED_SLOT,
                ModItems.IRON_DUST.get()) == 4, "the feed was eaten while the output was full");

        // Emptying the output lets the run start on its own.
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.OUTPUT_SLOT,
                ItemStack.EMPTY);
        spent = runCultivator(helper, machine, 700);
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 1, "the machine did not resume");
        helper.assertTrue(spent == SeedLineBalance.billetEnergy(1),
                "the resumed machine spent " + spent + " FE");
        helper.succeed();
    }

    /** An iron seed never grows a copper billet, and the wrong feed is refused. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorRejectsTheWrongMetal(GameTestHelper helper) {
        SeedCultivatorBlockEntity machine = placeCultivator(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.COPPER_SEED.get(), 1));
        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 4));

        long spent = runCultivator(helper, machine, 200);
        helper.assertTrue(machine.getStatus() == MachineStatus.NO_MATERIAL,
                "the iron feed was accepted for a copper seed: " + machine.getStatus());
        helper.assertTrue(spent == 0, "the machine spent FE on a rejected pair");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.SEED_SLOT,
                ModItems.COPPER_SEED.get()) == 1, "the copper seed was consumed");

        machine.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.COPPER_DUST.get(), 4));
        runCultivator(helper, machine, 700);
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.COPPER_BILLET.get()) == 1,
                "the copper seed did not produce a copper billet");
        helper.assertTrue(count(machine, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 0, "the copper seed produced an iron billet");
        helper.succeed();
    }

    /** Batch, instalments, progress, FE and the inventory survive a reload. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void cultivatorSurvivesSaveAndLoad(GameTestHelper helper) {
        SeedCultivatorBlockEntity original = placeCultivator(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        original.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 8));
        for (int tick = 0; tick < 120; tick++) {
            tickCultivatorWithEnergy(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the machine did not start working");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        SeedCultivatorBlockEntity restored = placeCultivator(helper, new BlockPos(4, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getBatch() == original.getBatch(),
                "the batch was restored as " + restored.getBatch());
        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(count(restored, SeedCultivatorBlockEntity.FEED_SLOT,
                        ModItems.IRON_DUST.get()) == count(original,
                        SeedCultivatorBlockEntity.FEED_SLOT, ModItems.IRON_DUST.get()),
                "the feed was lost");

        runCultivator(helper, restored, 3_000);
        helper.assertTrue(count(restored, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) == 2,
                "the restored machine did not finish its batch: "
                        + count(restored, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Matter amplifier
    // ------------------------------------------------------------------

    /** One billet plus one crystal -> two ore blocks and four dust for 72,000 FE. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierProducesOreAndDust(GameTestHelper helper) {
        MatterAmplifierBlockEntity machine = placeAmplifier(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));

        long spent = runAmplifier(helper, machine, SeedLineBalance.AMPLIFIER_TIME_TICKS + 100);

        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE) == 2,
                "the amplifier produced "
                        + count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE)
                        + " ore blocks instead of 2");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 4,
                "the amplifier produced "
                        + count(machine, MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) + " dust instead of 4");
        helper.assertTrue(spent == SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN,
                "the run spent " + spent + " FE instead of "
                        + SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN);
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 0, "the billet was not consumed");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.CATALYST_SLOT,
                ModItems.RECONSTRUCTION_CRYSTAL.get()) == 0, "the crystal was not consumed");
        helper.assertItemEntityNotPresent(Items.IRON_ORE);
        helper.assertItemEntityNotPresent(ModItems.IRON_DUST.get());
        helper.succeed();
    }

    /** Nothing is consumed before the run finishes. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierConsumesInputsOnlyOnCompletion(GameTestHelper helper) {
        MatterAmplifierBlockEntity machine = placeAmplifier(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));

        runAmplifier(helper, machine, 600);
        helper.assertTrue(machine.getProgress() > 0, "the machine did not start");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 1, "the billet was consumed before completion");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.CATALYST_SLOT,
                ModItems.RECONSTRUCTION_CRYSTAL.get()) == 1,
                "the crystal was consumed before completion");
        helper.assertTrue(machine.getInventory()
                .getStackInSlot(MatterAmplifierBlockEntity.OUTPUT_SLOT).isEmpty(),
                "the machine produced an ore block before completion");

        runAmplifier(helper, machine, SeedLineBalance.AMPLIFIER_TIME_TICKS);
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE) == 2,
                "the run did not finish after the full duration");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 0, "the billet was not consumed at completion");
        helper.succeed();
    }

    /**
     * Pulling the billet out pauses the run without losing progress, spending FE or paying out;
     * putting it back resumes and the whole run still costs exactly 72,000 FE.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierKeepsProgressWhenTheBilletIsPulled(GameTestHelper helper) {
        MatterAmplifierBlockEntity machine = placeAmplifier(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));

        long spent = runAmplifier(helper, machine, 300);
        int pausedProgress = machine.getProgress();
        helper.assertTrue(pausedProgress > 0,
                "the machine did not start: " + pausedProgress);

        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                ItemStack.EMPTY);
        long idle = runAmplifier(helper, machine, 50);
        helper.assertTrue(idle == 0, "the paused machine spent " + idle + " FE");
        helper.assertTrue(machine.getStatus() == MachineStatus.NO_INPUT,
                "expected NO_INPUT but was " + machine.getStatus());
        helper.assertTrue(machine.getProgress() == pausedProgress,
                "the progress was lost while the billet was out");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT,
                Items.IRON_ORE) == 0, "the paused machine produced ore");

        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        long rest = runAmplifier(helper, machine, SeedLineBalance.AMPLIFIER_TIME_TICKS);
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT,
                        Items.IRON_ORE) == 2,
                "the resumed run produced "
                        + count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE)
                        + " ore blocks instead of 2");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 0, "the resumed billet was not consumed");
        helper.assertTrue(spent + rest == SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN,
                "the interrupted run spent " + (spent + rest) + " FE instead of "
                        + SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN);
        helper.succeed();
    }

    /** One full output freezes the machine without spending FE or consuming anything. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierStopsWhenOneOutputIsFull(GameTestHelper helper) {
        MatterAmplifierBlockEntity machine = placeAmplifier(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 64));

        long spent = runAmplifier(helper, machine, 400);

        helper.assertTrue(machine.getStatus() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + machine.getStatus());
        helper.assertTrue(spent == 0, "the blocked machine spent " + spent + " FE");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 1, "the billet was eaten by a blocked machine");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                ModItems.IRON_DUST.get()) == 64, "the full dust stack changed");

        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                ItemStack.EMPTY);
        spent = runAmplifier(helper, machine, SeedLineBalance.AMPLIFIER_TIME_TICKS + 100);
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE) == 2,
                "the machine did not resume after the dust slot was emptied");
        helper.assertTrue(spent == SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN,
                "the resumed run spent " + spent + " FE");
        helper.succeed();
    }

    /**
     * Without FE both machines wait: nothing is consumed, nothing is produced and the progress
     * stays at 0; the same inputs then complete normally once power arrives.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void bothMachinesWaitForPowerWithoutLosingInputs(GameTestHelper helper) {
        SeedCultivatorBlockEntity cultivator = placeCultivator(helper, new BlockPos(2, Y, 2));
        cultivator.getInventory().setStackInSlot(SeedCultivatorBlockEntity.SEED_SLOT,
                new ItemStack(ModItems.IRON_SEED.get(), 1));
        cultivator.getInventory().setStackInSlot(SeedCultivatorBlockEntity.FEED_SLOT,
                new ItemStack(ModItems.IRON_DUST.get(), 4));
        cultivator.setEnergyStored(0);
        for (int tick = 0; tick < 5; tick++) {
            SeedCultivatorBlockEntity.serverTick(helper.getLevel(), cultivator.getBlockPos(),
                    cultivator.getBlockState(), cultivator);
        }
        helper.assertTrue(cultivator.getStatus() == MachineStatus.NO_ENERGY,
                "the cultivator ran without FE: " + cultivator.getStatus());
        helper.assertTrue(count(cultivator, SeedCultivatorBlockEntity.SEED_SLOT,
                ModItems.IRON_SEED.get()) == 1, "the seed was eaten without FE");
        helper.assertTrue(count(cultivator, SeedCultivatorBlockEntity.FEED_SLOT,
                ModItems.IRON_DUST.get()) == 4, "the feed was eaten without FE");
        helper.assertTrue(cultivator.getProgress() == 0, "the cultivator advanced without FE");
        long spent = runCultivator(helper, cultivator, 700);
        helper.assertTrue(count(cultivator, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_BILLET.get()) == 1,
                "the cultivator did not finish once power arrived");
        helper.assertTrue(spent == SeedLineBalance.billetEnergy(1),
                "the cultivator spent " + spent + " FE instead of "
                        + SeedLineBalance.billetEnergy(1));

        MatterAmplifierBlockEntity amplifier = placeAmplifier(helper, new BlockPos(4, Y, 2));
        amplifier.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        amplifier.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        amplifier.setEnergyStored(0);
        for (int tick = 0; tick < 5; tick++) {
            MatterAmplifierBlockEntity.serverTick(helper.getLevel(), amplifier.getBlockPos(),
                    amplifier.getBlockState(), amplifier);
        }
        helper.assertTrue(amplifier.getStatus() == MachineStatus.NO_ENERGY,
                "the amplifier ran without FE: " + amplifier.getStatus());
        helper.assertTrue(count(amplifier, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.IRON_BILLET.get()) == 1, "the billet was eaten without FE");
        helper.assertTrue(count(amplifier, MatterAmplifierBlockEntity.CATALYST_SLOT,
                ModItems.RECONSTRUCTION_CRYSTAL.get()) == 1, "the crystal was eaten without FE");
        helper.assertTrue(amplifier.getProgress() == 0, "the amplifier advanced without FE");
        spent = runAmplifier(helper, amplifier, SeedLineBalance.AMPLIFIER_TIME_TICKS + 100);
        helper.assertTrue(count(amplifier, MatterAmplifierBlockEntity.OUTPUT_SLOT,
                        Items.IRON_ORE) == 2,
                "the amplifier produced "
                        + count(amplifier, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE)
                        + " ore blocks once power arrived instead of 2");
        helper.assertTrue(spent == SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN,
                "the amplifier spent " + spent + " FE instead of "
                        + SeedLineBalance.AMPLIFIER_ENERGY_PER_RUN);
        helper.succeed();
    }

    /** An iron billet never yields copper ore. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierRejectsTheWrongMetal(GameTestHelper helper) {
        MatterAmplifierBlockEntity machine = placeAmplifier(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.COPPER_BILLET.get(), 1));
        machine.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));

        runAmplifier(helper, machine, SeedLineBalance.AMPLIFIER_TIME_TICKS + 100);
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.COPPER_ORE) == 2,
                "a copper billet did not produce copper ore");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.IRON_ORE) == 0,
                "a copper billet produced iron ore");
        helper.assertTrue(count(machine, MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                ModItems.COPPER_DUST.get()) == 4, "the by-product is not copper dust");
        helper.succeed();
    }

    /** Inventory, progress and FE survive a reload. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierSurvivesSaveAndLoad(GameTestHelper helper) {
        MatterAmplifierBlockEntity original = placeAmplifier(helper, new BlockPos(2, Y, 2));
        original.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.GOLD_BILLET.get(), 1));
        original.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        for (int tick = 0; tick < 300; tick++) {
            tickAmplifierWithEnergy(helper, original);
        }
        int savedProgress = original.getProgress();
        helper.assertTrue(savedProgress > 0, "the machine did not start working");

        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        MatterAmplifierBlockEntity restored = placeAmplifier(helper, new BlockPos(4, Y, 2));
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getProgress() == savedProgress,
                "the progress was restored as " + restored.getProgress()
                        + " instead of " + savedProgress);
        helper.assertTrue(restored.getEnergyStoredForTesting()
                        == original.getEnergyStoredForTesting(),
                "the FE was restored as " + restored.getEnergyStoredForTesting());
        helper.assertTrue(count(restored, MatterAmplifierBlockEntity.INPUT_SLOT,
                ModItems.GOLD_BILLET.get()) == 1, "the billet was lost");

        runAmplifier(helper, restored, SeedLineBalance.AMPLIFIER_TIME_TICKS + 100);
        helper.assertTrue(count(restored, MatterAmplifierBlockEntity.OUTPUT_SLOT, Items.GOLD_ORE) == 2,
                "the restored machine did not finish its run");
        helper.succeed();
    }

    /** The ore blocks the amplifier produces really enter the existing 2x route. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void amplifierOreEntersTheTwoTimesRoute(GameTestHelper helper) {
        MatterAmplifierBlockEntity amplifier = placeAmplifier(helper, new BlockPos(2, Y, 2));
        ProcessingBlockEntity crusher = placeProcessing(helper, new BlockPos(4, Y, 2),
                ModBlocks.CRUSHER.get());
        amplifier.getInventory().setStackInSlot(MatterAmplifierBlockEntity.INPUT_SLOT,
                new ItemStack(ModItems.IRON_BILLET.get(), 1));
        amplifier.getInventory().setStackInSlot(MatterAmplifierBlockEntity.CATALYST_SLOT,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));

        for (int tick = 0; tick < SeedLineBalance.AMPLIFIER_TIME_TICKS + 1_000
                && count(crusher, ProcessingBlockEntity.OUTPUT_SLOT, ModItems.IRON_DUST.get()) < 2;
             tick++) {
            tickAmplifierWithEnergy(helper, amplifier);
            tickProcessingWithEnergy(helper, crusher);
            transfer(amplifier.getInventory(), MatterAmplifierBlockEntity.OUTPUT_SLOT,
                    crusher.getInventory(), ProcessingBlockEntity.INPUT_SLOT);
        }

        helper.assertTrue(count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) == 2,
                "the crusher produced "
                        + count(crusher, ProcessingBlockEntity.OUTPUT_SLOT,
                        ModItems.IRON_DUST.get()) + " dust instead of 2");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Crafting recipes (they are data driven, so the grid must really match)
    // ------------------------------------------------------------------

    /** The three seed recipes are shapeless and produce one seed each. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void seedRecipesCraft(GameTestHelper helper) {
        for (String metal : List.of("iron", "copper", "gold")) {
            Item dust = switch (metal) {
                case "iron" -> ModItems.IRON_DUST.get();
                case "copper" -> ModItems.COPPER_DUST.get();
                default -> ModItems.GOLD_DUST.get();
            };
            CraftingInput input = grid(
                    new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(dust), new ItemStack(dust),
                    new ItemStack(dust), new ItemStack(dust),
                    new ItemStack(Items.GLASS), new ItemStack(Items.GLASS),
                    ItemStack.EMPTY);
            Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
            helper.assertTrue(recipe.isPresent(), "the " + metal + " seed recipe does not match");
            ItemStack result = recipe.get().value()
                    .getResultItem(helper.getLevel().registryAccess());
            String expected = "industrialcore:" + metal + "_seed";
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem()).toString()
                            .equals(expected),
                    "the " + metal + " seed recipe produced " + result);
            helper.assertTrue(result.getCount() == 1, "the seed recipe produced " + result.getCount());
        }
        helper.succeed();
    }

    /** Both machines are crafted from their 3x3 pattern. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void machineRecipesCraft(GameTestHelper helper) {
        // Pattern GPR / GFG / RPG: 2 plates, 4 glass, 2 redstone, 1 machine frame.
        CraftingInput cultivator = grid(
                new ItemStack(Items.GLASS), new ItemStack(ModItems.IRON_PLATE.get()),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.GLASS), new ItemStack(ModItems.MACHINE_FRAME.get()),
                new ItemStack(Items.GLASS),
                new ItemStack(Items.REDSTONE), new ItemStack(ModItems.IRON_PLATE.get()),
                new ItemStack(Items.GLASS));
        assertCraft(helper, cultivator, "industrialcore:seed_cultivator", "seed cultivator");

        CraftingInput amplifier = grid(
                new ItemStack(ModItems.IRON_PLATE.get()),
                new ItemStack(ModItems.ELECTRONIC_COMPONENT.get()),
                new ItemStack(ModItems.IRON_PLATE.get()),
                new ItemStack(Items.DIAMOND),
                new ItemStack(ModItems.REINFORCED_MACHINE_FRAME.get()),
                new ItemStack(Items.DIAMOND),
                new ItemStack(ModItems.IRON_PLATE.get()),
                new ItemStack(ModItems.ELECTRONIC_COMPONENT.get()),
                new ItemStack(ModItems.IRON_PLATE.get()));
        assertCraft(helper, amplifier, "industrialcore:matter_amplifier", "matter amplifier");
        helper.succeed();
    }

    private static void assertCraft(GameTestHelper helper, CraftingInput input, String expected,
                                    String what) {
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "the " + what + " recipe does not match its pattern");
        ItemStack result = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem()).toString()
                .equals(expected), "the " + what + " recipe produced " + result);
        helper.assertTrue(result.getCount() == 1, "the " + what + " recipe produced "
                + result.getCount());
    }

    /** Builds a 3x3 crafting grid from nine stacks (empty stacks pad the rest). */
    private static CraftingInput grid(ItemStack... stacks) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            items.add(stack.copy());
        }
        while (items.size() < 9) {
            items.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static SeedCultivatorBlockEntity placeCultivator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.SEED_CULTIVATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof SeedCultivatorBlockEntity machine)) {
            helper.fail("seed cultivator block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static MatterAmplifierBlockEntity placeAmplifier(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.MATTER_AMPLIFIER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof MatterAmplifierBlockEntity machine)) {
            helper.fail("matter amplifier block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static ProcessingBlockEntity placeProcessing(GameTestHelper helper, BlockPos pos,
                                                          Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ProcessingBlockEntity machine)) {
            helper.fail("processing block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    /** Ticks the machine once with a full buffer and returns the FE it consumed. */
    private static long tickCultivatorWithEnergy(GameTestHelper helper,
                                                 SeedCultivatorBlockEntity machine) {
        machine.setEnergyStored(START_ENERGY);
        BlockState state = machine.getBlockState();
        SeedCultivatorBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
        return START_ENERGY - machine.getEnergyStoredForTesting();
    }

    private static long tickAmplifierWithEnergy(GameTestHelper helper,
                                                MatterAmplifierBlockEntity machine) {
        machine.setEnergyStored(START_ENERGY);
        BlockState state = machine.getBlockState();
        MatterAmplifierBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
        return START_ENERGY - machine.getEnergyStoredForTesting();
    }

    private static long tickProcessingWithEnergy(GameTestHelper helper,
                                                 ProcessingBlockEntity machine) {
        machine.setEnergyStored(START_ENERGY);
        BlockState state = machine.getBlockState();
        ProcessingBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state, machine);
        return START_ENERGY - machine.getEnergyStoredForTesting();
    }

    /** Runs the machine for at most {@code ticks} ticks, returning the total FE it consumed. */
    private static long runCultivator(GameTestHelper helper, SeedCultivatorBlockEntity machine,
                                      int ticks) {
        long spent = 0;
        for (int tick = 0; tick < ticks; tick++) {
            spent += tickCultivatorWithEnergy(helper, machine);
        }
        return spent;
    }

    private static long runAmplifier(GameTestHelper helper, MatterAmplifierBlockEntity machine,
                                     int ticks) {
        long spent = 0;
        for (int tick = 0; tick < ticks; tick++) {
            spent += tickAmplifierWithEnergy(helper, machine);
        }
        return spent;
    }

    private static int count(SeedCultivatorBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(MatterAmplifierBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static int count(ProcessingBlockEntity machine, int slot, Item item) {
        ItemStack stack = machine.getInventory().getStackInSlot(slot);
        return stack.is(item) ? stack.getCount() : 0;
    }

    /** Moves as much as fits from one slot into another (what a pipe would do). */
    private static void transfer(net.neoforged.neoforge.items.ItemStackHandler from, int fromSlot,
                                 net.neoforged.neoforge.items.ItemStackHandler to, int toSlot) {
        ItemStack source = from.getStackInSlot(fromSlot);
        if (source.isEmpty()) {
            return;
        }
        ItemStack remaining = to.insertItem(toSlot, source.copy(), false);
        int moved = source.getCount() - remaining.getCount();
        if (moved <= 0) {
            return;
        }
        source.shrink(moved);
        from.setStackInSlot(fromSlot, source.isEmpty() ? ItemStack.EMPTY : source);
    }
}
