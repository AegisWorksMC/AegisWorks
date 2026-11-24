package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 30: the synthetic ore synthesizer, driven through the real server tick and the real
 * recipe manager.
 *
 * <p>The fifteen ore amplifier tiers this class used to cover were removed in Phase 31: they
 * duplicated raw ore directly, which made the processing line meaningless. The contract under
 * test now is that the synthesizer, the last machine of the ore line, consumes the whole listed
 * count of every ingredient, charges the documented cycle time and FE, and stops on a full
 * output without eating anything.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Phase30GameTests {
    private static final int Y = 6;

    private Phase30GameTests() {}

    /**
     * Synthetic ore I: four adamantite dust, four mithril dust and one reconstruction crystal.
     *
     * <p>The ladder eats the ore line's *dust* grades, not its ingots: an ingot or crystal input
     * would put the ladder and the line it unlocks on one material cycle.</p>
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void theSynthesizerMakesSyntheticOreOne(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(0, new ItemStack(ModItems.ADAMANTITE_DUST.get(), 1));
        machine.getInventory().setStackInSlot(1, new ItemStack(ModItems.MITHRIL_DUST.get(), 1));
        machine.getInventory().setStackInSlot(2,
                new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get(), 1));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        int ticks = 0;
        for (; ticks < 300
                && machine.getInventory().getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT)
                .isEmpty(); ticks++) {
            tickSynthesizer(helper, machine);
        }

        ItemStack output = machine.getInventory()
                .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(output.is(ModItems.SYNTHETIC_ORE_1.get()) && output.getCount() == 1,
                "the synthesizer produced " + output);
        helper.assertTrue(ticks == SynthesisBalance.TIME_TICKS,
                "the craft took " + ticks + " ticks instead of " + SynthesisBalance.TIME_TICKS);
        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(machine.getInventory().getStackInSlot(slot).isEmpty(),
                    "ingredient slot " + slot + " was not fully consumed");
        }
        helper.assertTrue(machine.getEnergyStoredForTesting()
                        == SynthesisBalance.CAPACITY - (int) SynthesisBalance.totalEnergy(
                        SynthesisBalance.TIME_TICKS, SynthesisBalance.ENERGY_PER_TICK),
                "the craft spent " + (SynthesisBalance.CAPACITY
                        - machine.getEnergyStoredForTesting()) + " FE");
        helper.succeed();
    }

    /** The synthesizer stops on a full output without eating the ingredients. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void theSynthesizerStopsOnAFullOutput(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(0, new ItemStack(ModItems.ADAMANTITE_DUST.get(), 1));
        machine.getInventory().setStackInSlot(1, new ItemStack(ModItems.MITHRIL_DUST.get(), 1));
        machine.getInventory().setStackInSlot(2, new ItemStack(ModItems.RECONSTRUCTION_CRYSTAL.get()));
        machine.getInventory().setStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.SYNTHETIC_ORE_1.get(), 64));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        for (int tick = 0; tick < 60; tick++) {
            tickSynthesizer(helper, machine);
        }

        helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 1,
                "an ingredient was consumed while the output was full");
        helper.assertTrue(machine.getStatusForTesting() == MachineStatus.OUTPUT_FULL,
                "expected OUTPUT_FULL but was " + machine.getStatusForTesting());
        helper.succeed();
    }

    /**
     * A batch recipe needs its whole batch: eight concentrate must not start or feed the sixteen
     * concentrate run of stage 7, and nothing may be consumed while the machine waits.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void theSynthesizerWaitsForAFullBatch(GameTestHelper helper) {
        OreSynthesizerBlockEntity machine = placeSynthesizer(helper, new BlockPos(2, Y, 2));
        machine.getInventory().setStackInSlot(0,
                new ItemStack(ModItems.IRON_CONCENTRATE.get(), 8));
        machine.setEnergyStored(SynthesisBalance.CAPACITY);

        for (int tick = 0; tick < 200; tick++) {
            tickSynthesizer(helper, machine);
        }

        helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 8,
                "a partial batch was consumed: " + machine.getInventory().getStackInSlot(0));
        helper.assertTrue(machine.getInventory()
                        .getStackInSlot(OreSynthesizerBlockEntity.OUTPUT_SLOT).isEmpty(),
                "a partial batch produced an item");
        helper.assertTrue(machine.getProgressForTesting() == 0,
                "a partial batch advanced the progress to " + machine.getProgressForTesting());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static OreSynthesizerBlockEntity placeSynthesizer(GameTestHelper helper,
                                                              BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof OreSynthesizerBlockEntity machine)) {
            helper.fail("ore synthesizer block entity missing at " + pos);
            return null;
        }
        return machine;
    }

    private static void tickSynthesizer(GameTestHelper helper, OreSynthesizerBlockEntity machine) {
        BlockState state = machine.getBlockState();
        OreSynthesizerBlockEntity.serverTick(helper.getLevel(), machine.getBlockPos(), state,
                machine);
    }
}
