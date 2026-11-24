package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.block.TransporterBlock;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.GridPos;
import com.industrialcore.machine.TransportChannel;
import com.industrialcore.machine.TransportRole;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.machine.TransporterRegistry;
import com.industrialcore.machine.TransporterStatus;
import com.industrialcore.machine.TransporterStructure;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * GameTests of the Phase 26-29 channel transporters.
 *
 * <p>Every test drives the real server tick, the real shell validation and the real channel
 * registry. The rules under test, straight from the spec:</p>
 * <ul>
 *   <li>a shell (3x3x3 / 4x4x4 / 5x5x5) must validate, a broken one must stop transfers,</li>
 *   <li>one transfer attempt moves to exactly one destination on the same channel,</li>
 *   <li>tiers differ: 1 item / 20t, 64 items / 5t, 64 items / 1t, 4096 items + 1M FE / 1t,</li>
 *   <li>transfers cost 0 FE and lose 0 FE - the totals before and after must be equal,</li>
 *   <li>a full receiver, a missing receiver or a channel mismatch never voids or duplicates
 *       anything,</li>
 *   <li>settings and inventory survive a save and load, and the restored device transfers,</li>
 *   <li>the existing item distributor can fill a receiver through the item capability.</li>
 * </ul>
 *
 * <p>Every test uses its own channel name: the connection cache is shared by the whole test
 * level, so unique channels keep the tests independent of each other.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransporterGameTests {
    /** Machines sit on row 7 so a 5x5x5 shell (y 5..9) still fits inside the 12x12x12 plot. */
    private static final int Y = 7;
    private static final Direction FACING = Direction.NORTH;

    private TransporterGameTests() {}

    // ------------------------------------------------------------------
    // structures
    // ------------------------------------------------------------------

    /** A correct shell validates; the wrong casing type does not; the single block needs none. */
    @GameTest(template = "quarry_test", timeoutTicks = 200)
    public static void shellsValidateAndWrongCasingsDoNot(GameTestHelper helper) {
        TransporterBlockEntity basic = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        helper.assertTrue(basic.isStructureValid(),
                "the 3x3x3 shell with its own casing must validate");

        // The advanced controller demands the advanced casing: the basic one is rejected.
        TransporterBlockEntity advanced = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.ADVANCED);
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.ADVANCED,
                ModBlocks.TRANSPORTER_CASING.get());
        helper.assertTrue(!advanced.isStructureValid(),
                "a 4x4x4 shell built from the basic casing must not validate");
        // Upgrading every casing cell heals the structure.
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.ADVANCED,
                ModBlocks.ADVANCED_TRANSPORTER_CASING.get());
        helper.assertTrue(advanced.isStructureValid(),
                "replacing all casings with the advanced ones must heal the shell");

        // Phase 28 is a single block: no shell, always structurally valid.
        TransporterBlockEntity quantum = placeController(helper, new BlockPos(2, Y, 7),
                TransporterKind.QUANTUM);
        helper.assertTrue(quantum.isStructureValid(),
                "the single-block tier must never require a shell");
        helper.succeed();
    }

    /** Without (or after losing) a shell the device stops and keeps its items. */
    @GameTest(template = "quarry_test", timeoutTicks = 300)
    public static void anInvalidStructureStopsTheTransfer(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        configure(sender, "p26broken", TransportRole.SEND);
        configure(receiver, "p26broken", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 5);

        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(sender.getStatus() == TransporterStatus.NO_STRUCTURE,
                "a shell-less controller must report NO_STRUCTURE but reported "
                        + sender.getStatus());
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 5
                        && countOf(receiver, Items.IRON_INGOT) == 0,
                "an invalid structure must not move items");

        // Build both shells: the transfer starts.
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        helper.assertTrue(sender.isStructureValid(), "the shell must validate after building");
        for (int tick = 0; tick < 20; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 1,
                "the repaired structure must start transferring, receiver holds "
                        + countOf(receiver, Items.IRON_INGOT));

        // Break one casing: the next validation stops the machine, nothing is voided.
        GridPos brokenCell = TransporterStructure.casingCells(3,
                2, Y, 2, FACING.get3DDataValue()).stream()
                .filter(cell -> !cell.equals(new GridPos(2, Y, 2)))
                .findFirst().orElseThrow();
        helper.setBlock(new BlockPos(brokenCell.x(), brokenCell.y(), brokenCell.z()),
                Blocks.AIR);
        sender.revalidateNow();
        helper.assertTrue(!sender.isStructureValid(),
                "removing one casing must invalidate the shell");
        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(sender.getStatus() == TransporterStatus.NO_STRUCTURE,
                "the broken machine must report NO_STRUCTURE but reported "
                        + sender.getStatus());
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 4,
                "breaking the shell must not consume items, sender holds "
                        + countOf(sender, Items.IRON_INGOT));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 26: one item per 20 ticks, 0 FE
    // ------------------------------------------------------------------

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void basicMovesExactlyOneItemEveryTwentyTicks(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(sender, "p26basic", TransportRole.SEND);
        configure(receiver, "p26basic", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 3);
        helper.assertTrue(sender.getEnergyStorage() == null,
                "an item-only tier must have no FE buffer at all");

        for (int tick = 0; tick < 19; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 0,
                "the transfer must fire on the 20th tick, not earlier");

        tick(helper, sender); // 20th tick: first fire
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 1,
                "exactly one item must move per interval, receiver holds "
                        + countOf(receiver, Items.IRON_INGOT));
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 2,
                "the sender must keep the remaining two items");
        helper.assertTrue(sender.getStatus() == TransporterStatus.WORKING,
                "a successful transfer must report WORKING but reported "
                        + sender.getStatus());

        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 3
                        && countOf(sender, Items.IRON_INGOT) == 0,
                "three intervals must drain exactly three items");
        helper.assertTrue(sender.getEnergyStorage() == null,
                "an item transfer must not touch FE: the tier still has no FE buffer");
        helper.assertTrue(TransporterKind.FE_COST_PER_TRANSFER == 0,
                "the tier table must declare a zero transfer cost");
        helper.succeed();
    }

    /** No receiver on the channel: everything stays, the search backs off. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aChannelMismatchMovesNothing(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(sender, "p26alpha", TransportRole.SEND);
        configure(receiver, "p26beta", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 5);

        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 5
                        && countOf(receiver, Items.IRON_INGOT) == 0,
                "different channels must never transfer");
        helper.assertTrue(sender.getStatus() == TransporterStatus.NO_RECEIVER,
                "a sender without receivers must report NO_RECEIVER but reported "
                        + sender.getStatus());
        helper.succeed();
    }

    /** A receiver whose buffer is full never receives, and the sender keeps everything. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aFullReceiverKeepsEverythingInTheSender(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(sender, "p26full", TransportRole.SEND);
        configure(receiver, "p26full", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 5);
        // Two slots of dirt: no room for a single iron ingot.
        for (int slot = 0; slot < receiver.getInventory().getSlots(); slot++) {
            receiver.getInventory().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        }

        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 5,
                "a full receiver must leave the sender untouched");
        helper.assertTrue(countOf(receiver, Items.DIRT) == 128
                        && countOf(receiver, Items.IRON_INGOT) == 0,
                "the full receiver must not accept iron or lose dirt");
        helper.assertTrue(sender.getStatus() == TransporterStatus.RECEIVER_FULL,
                "a blocked transfer must report RECEIVER_FULL but reported "
                        + sender.getStatus());
        helper.succeed();
    }

    /** A receiver whose chunk never loads: the sender waits without pulling the chunk in. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void anUnloadedReceiverIsWaitedForWithoutForceLoadingTheChunk(
            GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(sender, "p26unloaded", TransportRole.SEND);
        fill(sender, Items.IRON_INGOT, 5);

        ServerLevel level = helper.getLevel();
        // Registered, but 1000 blocks (62 chunks) away: outside every load radius of the server.
        BlockPos farPos = sender.getBlockPos().offset(1_000, 0, 0);
        helper.assertTrue(!level.isLoaded(farPos),
                "the far chunk must start unloaded for this test to mean anything");
        GridPos far = new GridPos(farPos.getX(), farPos.getY(), farPos.getZ());
        TransporterRegistry.upsert(level, far, TransportChannel.key("p26unloaded"),
                TransportRole.RECEIVE);
        helper.assertTrue(TransporterRegistry.receiverCount(level, "p26unloaded") == 1,
                "the far receiver must be registered on the channel");

        for (int tick = 0; tick < 40; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 5,
                "items must wait for the unloaded receiver but the sender holds "
                        + countOf(sender, Items.IRON_INGOT));
        helper.assertTrue(sender.getStatus() == TransporterStatus.RECEIVER_FULL,
                "an unloadable receiver must report RECEIVER_FULL but reported "
                        + sender.getStatus());
        helper.assertTrue(!level.isLoaded(farPos),
                "the transfer must never force-load the receiver's chunk");
        TransporterRegistry.remove(level, far);
        helper.succeed();
    }

    /** With two receivers one attempt still moves to exactly one of them, never splits. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void exactlyOneDestinationPerTransfer(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity west = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity south = placeController(helper, new BlockPos(7, Y, 7),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 7), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(sender, "p26two", TransportRole.SEND);
        configure(west, "p26two", TransportRole.RECEIVE);
        configure(south, "p26two", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 1);

        tick(helper, sender);
        for (int tick = 1; tick < 20; tick++) {
            tick(helper, sender);
        }
        int total = countOf(west, Items.IRON_INGOT) + countOf(south, Items.IRON_INGOT);
        helper.assertTrue(total == 1,
                "one attempt must deliver to exactly one destination, found " + total
                        + " items in the receivers");
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 0,
                "the sender must have given up its single item");
        helper.succeed();
    }

    /** Two senders on one channel drain into one receiver without duplicating anything. */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void twoSendersDoNotDuplicateItems(GameTestHelper helper) {
        TransporterBlockEntity first = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        TransporterBlockEntity second = placeController(helper, new BlockPos(2, Y, 7),
                TransporterKind.BASIC);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(2, Y, 7), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(first, "p26twosenders", TransportRole.SEND);
        configure(second, "p26twosenders", TransportRole.SEND);
        configure(receiver, "p26twosenders", TransportRole.RECEIVE);
        fill(first, Items.IRON_INGOT, 3);
        fill(second, Items.COPPER_INGOT, 5);

        for (int tick = 0; tick < 300
                && (countOf(first, Items.IRON_INGOT) + countOf(second, Items.COPPER_INGOT)) > 0;
             tick++) {
            tick(helper, first);
            tick(helper, second);
        }
        helper.assertTrue(countOf(first, Items.IRON_INGOT) == 0
                        && countOf(second, Items.COPPER_INGOT) == 0,
                "both senders must drain");
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 3
                        && countOf(receiver, Items.COPPER_INGOT) == 5,
                "the receiver must hold exactly 3 + 5 items but holds "
                        + countOf(receiver, Items.IRON_INGOT) + " + "
                        + countOf(receiver, Items.COPPER_INGOT));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 27: 64 items every 5 ticks, stack units
    // ------------------------------------------------------------------

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void advancedMovesSixtyFourMixedItemsInStackUnits(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 1),
                TransporterKind.ADVANCED);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 1),
                TransporterKind.ADVANCED);
        buildShell(helper, new BlockPos(2, Y, 1), TransporterKind.ADVANCED,
                ModBlocks.ADVANCED_TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(7, Y, 1), TransporterKind.ADVANCED,
                ModBlocks.ADVANCED_TRANSPORTER_CASING.get());
        configure(sender, "p27adv", TransportRole.SEND);
        configure(receiver, "p27adv", TransportRole.RECEIVE);
        // Three different stacks: the cap of 64 spans them without item-by-item looping.
        sender.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 40));
        sender.getInventory().setStackInSlot(1, new ItemStack(Items.COPPER_INGOT, 24));
        sender.getInventory().setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 64));

        for (int tick = 0; tick < 5; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 40
                        && countOf(receiver, Items.COPPER_INGOT) == 24,
                "the first interval must move 40 iron + 24 copper = 64 items, got "
                        + countOf(receiver, Items.IRON_INGOT) + " + "
                        + countOf(receiver, Items.COPPER_INGOT));
        helper.assertTrue(countOf(sender, Items.GOLD_INGOT) == 64,
                "the 64-item cap must stop before the gold stack");
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 0
                        && countOf(sender, Items.COPPER_INGOT) == 0,
                "moved stacks must leave the sender");

        for (int tick = 0; tick < 5; tick++) {
            tick(helper, sender);
        }
        helper.assertTrue(countOf(sender, Items.GOLD_INGOT) == 0,
                "the second interval must move the gold stack");
        helper.assertTrue(countOf(receiver, Items.GOLD_INGOT) == 64,
                "the receiver must hold the gold stack");
        helper.assertTrue(totalItems(sender) == 0 && totalItems(receiver) == 128,
                "nothing may be created or voided: sender " + totalItems(sender)
                        + ", receiver " + totalItems(receiver));
        helper.assertTrue(sender.getEnergyStorage() == null,
                "item transfers must cost 0 FE: the item-only tier must have no FE buffer");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 28: one stack per tick plus FE, 0 FE cost, 0 loss
    // ------------------------------------------------------------------

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void quantumMovesAStackAndFiftyThousandFEPertick(GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.QUANTUM);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.QUANTUM);
        configure(sender, "p28quantum", TransportRole.SEND);
        configure(receiver, "p28quantum", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 100);
        sender.getEnergyStorage().setEnergy(100_000);

        // First tick: one stack (64) and one FE tranche (50,000).
        tick(helper, sender);
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 36
                        && countOf(receiver, Items.IRON_INGOT) == 64,
                "one stack per tick: sender " + countOf(sender, Items.IRON_INGOT)
                        + ", receiver " + countOf(receiver, Items.IRON_INGOT));
        helper.assertTrue(sender.getEnergyStorage().getEnergyStored() == 50_000
                        && receiver.getEnergyStorage().getEnergyStored() == 50_000,
                "50,000 FE must move per tick: sender "
                        + sender.getEnergyStorage().getEnergyStored() + ", receiver "
                        + receiver.getEnergyStorage().getEnergyStored());

        // Second tick: the rest of the items and another 50,000 FE.
        tick(helper, sender);
        helper.assertTrue(countOf(sender, Items.IRON_INGOT) == 0
                        && countOf(receiver, Items.IRON_INGOT) == 100,
                "the second tick must move the remaining 36 items");
        int senderFe = sender.getEnergyStorage().getEnergyStored();
        int receiverFe = receiver.getEnergyStorage().getEnergyStored();
        helper.assertTrue(senderFe == 0 && receiverFe == 100_000,
                "FE conservation failed: sender " + senderFe + ", receiver " + receiverFe
                        + " (start was 100,000)");
        helper.assertTrue(senderFe + receiverFe == 100_000,
                "transfers must neither create nor destroy FE");

        // Third tick: nothing left - no free items, no free FE.
        tick(helper, sender);
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 100
                        && receiver.getEnergyStorage().getEnergyStored() == 100_000,
                "an idle sender must not conjure items or FE");

        // The receiver exports its FE to an adjacent consumer (the energy buffer).
        helper.setBlock(new BlockPos(7, Y, 1), ModBlocks.ENERGY_BUFFER.get().defaultBlockState());
        BlockEntity bufferBlock =
                helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, Y, 1)));
        helper.assertTrue(bufferBlock instanceof EnergyBufferBlockEntity,
                "energy buffer block entity missing");
        EnergyBufferBlockEntity buffer = (EnergyBufferBlockEntity) bufferBlock;
        int receiverBefore = receiver.getEnergyStorage().getEnergyStored();
        int bufferBefore = buffer.getEnergyStorage().getEnergyStored();
        tick(helper, receiver);
        int pushed = buffer.getEnergyStorage().getEnergyStored() - bufferBefore;
        helper.assertTrue(pushed > 0,
                "a receive-side device with FE must export it to its neighbours");
        helper.assertTrue(receiver.getEnergyStorage().getEnergyStored()
                        == receiverBefore - pushed,
                "the pushed FE must leave the receiver exactly: lost "
                        + (receiverBefore - receiver.getEnergyStorage().getEnergyStored())
                        + " but the buffer gained " + pushed);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Phase 29: 4096 items and 1,000,000 FE per tick
    // ------------------------------------------------------------------

    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void arrayMovesFourThousandNinetySixItemsAndOneMillionFE(
            GameTestHelper helper) {
        TransporterBlockEntity sender = placeController(helper, new BlockPos(2, Y, 1),
                TransporterKind.ARRAY);
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(9, Y, 1),
                TransporterKind.ARRAY);
        buildShell(helper, new BlockPos(2, Y, 1), TransporterKind.ARRAY,
                ModBlocks.QUANTUM_TRANSPORTER_CASING.get());
        buildShell(helper, new BlockPos(9, Y, 1), TransporterKind.ARRAY,
                ModBlocks.QUANTUM_TRANSPORTER_CASING.get());
        helper.assertTrue(sender.isStructureValid() && receiver.isStructureValid(),
                "the two 5x5x5 shells must validate before the transfer test");
        configure(sender, "p29array", TransportRole.SEND);
        configure(receiver, "p29array", TransportRole.RECEIVE);
        fill(sender, Items.IRON_INGOT, 4096);
        sender.getEnergyStorage().setEnergy(2_000_000);
        helper.assertTrue(totalItems(sender) == 4096,
                "the sender must start with exactly 4096 items but has "
                        + totalItems(sender));

        // One tick: the full 4096 items and one 1,000,000 FE tranche.
        tick(helper, sender);
        helper.assertTrue(totalItems(sender) == 0 && totalItems(receiver) == 4096,
                "one interval must move 4096 items: sender " + totalItems(sender)
                        + ", receiver " + totalItems(receiver));
        helper.assertTrue(sender.getEnergyStorage().getEnergyStored() == 1_000_000
                        && receiver.getEnergyStorage().getEnergyStored() == 1_000_000,
                "one interval must move exactly 1,000,000 FE: sender "
                        + sender.getEnergyStorage().getEnergyStored() + ", receiver "
                        + receiver.getEnergyStorage().getEnergyStored());

        // Second tick: the remaining FE tranche, no more items.
        tick(helper, sender);
        int senderFe = sender.getEnergyStorage().getEnergyStored();
        int receiverFe = receiver.getEnergyStorage().getEnergyStored();
        helper.assertTrue(senderFe == 0 && receiverFe == 2_000_000,
                "FE conservation failed at the boundary: sender " + senderFe
                        + ", receiver " + receiverFe);
        helper.assertTrue(senderFe + receiverFe == 2_000_000,
                "1,000,000 FE per tick must neither create nor destroy FE");
        helper.assertTrue(totalItems(receiver) == 4096,
                "items must not be duplicated by the FE pass");
        helper.assertTrue(TransporterKind.POWER_LOSS_PERCENT == 0,
                "the tier table must declare zero transfer loss");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // save / load and existing logistics
    // ------------------------------------------------------------------

    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void settingsAndInventorySurviveSaveAndLoad(GameTestHelper helper) {
        TransporterBlockEntity original = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(original, "p26save", TransportRole.SEND);
        fill(original, Items.IRON_INGOT, 7);
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        // Retire the original device so only the restored copy owns the channel.
        helper.setBlock(new BlockPos(2, Y, 2), Blocks.AIR);

        TransporterBlockEntity restored = placeController(helper, new BlockPos(7, Y, 2),
                TransporterKind.BASIC);
        restored.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getChannel().equalsIgnoreCase("p26save"),
                "the channel must survive the save but came back as '"
                        + restored.getChannel() + "'");
        helper.assertTrue(restored.getRole() == TransportRole.SEND,
                "the role must survive the save but came back as " + restored.getRole());
        helper.assertTrue(countOf(restored, Items.IRON_INGOT) == 7,
                "the inventory must survive the save but holds "
                        + countOf(restored, Items.IRON_INGOT));

        buildShell(helper, new BlockPos(7, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        helper.assertTrue(restored.isStructureValid(),
                "the restored device must revalidate its shell");
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(2, Y, 7),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 7), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(receiver, "p26save", TransportRole.RECEIVE);

        for (int tick = 0; tick < 20; tick++) {
            tick(helper, restored);
        }
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 1
                        && countOf(restored, Items.IRON_INGOT) == 6,
                "the restored sender must keep transferring: receiver "
                        + countOf(receiver, Items.IRON_INGOT) + ", sender "
                        + countOf(restored, Items.IRON_INGOT));
        helper.succeed();
    }

    /** The existing item distributor fills a receiver through the shared item capability. */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void aDistributorFillsTheReceiverBuffer(GameTestHelper helper) {
        TransporterBlockEntity receiver = placeController(helper, new BlockPos(2, Y, 2),
                TransporterKind.BASIC);
        buildShell(helper, new BlockPos(2, Y, 2), TransporterKind.BASIC,
                ModBlocks.TRANSPORTER_CASING.get());
        configure(receiver, "p26dist", TransportRole.RECEIVE);

        // The distributor sits in front of the controller, exactly like a machine neighbour.
        helper.setBlock(new BlockPos(2, Y, 1),
                ModBlocks.ITEM_DISTRIBUTOR.get().defaultBlockState());
        BlockEntity distributorBlock = helper.getLevel()
                .getBlockEntity(helper.absolutePos(new BlockPos(2, Y, 1)));
        helper.assertTrue(distributorBlock instanceof ItemDistributorBlockEntity,
                "item distributor block entity missing");
        ItemDistributorBlockEntity distributor = (ItemDistributorBlockEntity) distributorBlock;

        ItemStack leftover = distributor.getItemHandler(Direction.UP).insertItem(
                ItemDistributorBlockEntity.BUFFER_SLOT,
                new ItemStack(Items.IRON_INGOT, 4), false);
        helper.assertTrue(leftover.isEmpty(), "the distributor refused the items: " + leftover);
        for (int pass = 0; pass < 4 && distributor.getBufferCount() > 0; pass++) {
            ItemDistributorBlockEntity.serverTick(helper.getLevel(), distributor.getBlockPos(),
                    distributor.getBlockState(), distributor);
        }
        helper.assertTrue(distributor.getBufferCount() == 0,
                "the distributor must empty its buffer, holds "
                        + distributor.getBufferCount());
        helper.assertTrue(countOf(receiver, Items.IRON_INGOT) == 4,
                "the transporter receiver must accept the distributor's items but holds "
                        + countOf(receiver, Items.IRON_INGOT));

        // Pipes push through the very same capability on any face.
        net.neoforged.neoforge.items.IItemHandler handler =
                receiver.getItemHandler(Direction.DOWN);
        int emptySlot = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) {
                emptySlot = slot;
                break;
            }
        }
        helper.assertTrue(emptySlot >= 0,
                "the receiver must still have an empty slot for a pipe push");
        ItemStack refused = handler.insertItem(emptySlot,
                new ItemStack(Items.COPPER_INGOT, 2), false);
        helper.assertTrue(refused.isEmpty(),
                "a pipe push into the receiver must be accepted but was refused: " + refused);
        helper.assertTrue(countOf(receiver, Items.COPPER_INGOT) == 2,
                "the pipe push must land in the buffer");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static TransporterBlockEntity placeController(GameTestHelper helper, BlockPos pos,
                                                          TransporterKind kind) {
        Block block = switch (kind) {
            case BASIC -> ModBlocks.BASIC_ITEM_TRANSPORTER.get();
            case ADVANCED -> ModBlocks.ADVANCED_ITEM_TRANSPORTER.get();
            case QUANTUM -> ModBlocks.QUANTUM_TRANSPORTER.get();
            case ARRAY -> ModBlocks.QUANTUM_TRANSPORTER_ARRAY.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof TransporterBlockEntity transporter)) {
            helper.fail(kind + " controller block entity missing at " + pos);
            return null;
        }
        return transporter;
    }

    /** Places the shell around an already existing controller and revalidates it. */
    private static void buildShell(GameTestHelper helper, BlockPos controller,
                                   TransporterKind kind, Block casing) {
        int size = kind.structureSize();
        int facing = FACING.get3DDataValue();
        for (GridPos cell : TransporterStructure.casingCells(size,
                controller.getX(), controller.getY(), controller.getZ(), facing)) {
            helper.setBlock(new BlockPos(cell.x(), cell.y(), cell.z()),
                    casing.defaultBlockState());
        }
        for (GridPos cell : TransporterStructure.airCells(size,
                controller.getX(), controller.getY(), controller.getZ(), facing)) {
            helper.setBlock(new BlockPos(cell.x(), cell.y(), cell.z()), Blocks.AIR);
        }
        BlockEntity blockEntity =
                helper.getLevel().getBlockEntity(helper.absolutePos(controller));
        if (blockEntity instanceof TransporterBlockEntity transporter) {
            transporter.revalidateNow();
        }
    }

    private static void configure(TransporterBlockEntity transporter, String channel,
                                  TransportRole role) {
        transporter.setChannel(channel);
        transporter.setRole(role);
    }

    /** Fills the buffer from slot 0 with full stacks of one item. */
    private static void fill(TransporterBlockEntity transporter, Item item, int amount) {
        ItemStackHandler inventory = transporter.getInventory();
        int slot = 0;
        int left = amount;
        while (left > 0 && slot < inventory.getSlots()) {
            int put = Math.min(64, left);
            inventory.setStackInSlot(slot, new ItemStack(item, put));
            left -= put;
            slot++;
        }
        if (left > 0) {
            throw new IllegalStateException("buffer too small for " + amount + " items");
        }
    }

    private static int countOf(TransporterBlockEntity transporter, Item item) {
        int total = 0;
        ItemStackHandler inventory = transporter.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int totalItems(TransporterBlockEntity transporter) {
        int total = 0;
        ItemStackHandler inventory = transporter.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            total += inventory.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static void tick(GameTestHelper helper, TransporterBlockEntity transporter) {
        TransporterBlockEntity.serverTick(helper.getLevel(), transporter.getBlockPos(),
                transporter.getBlockState(), transporter);
    }
}
