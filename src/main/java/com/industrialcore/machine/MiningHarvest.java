package com.industrialcore.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Shared block harvesting of the quarry family (Phase 20).
 *
 * <p>Both the normal quarry and the large quarry have to break a real block, roll its loot table
 * with a fake player, keep the contents of containers and store the result without ever voiding an
 * item. The steps are kept in one place so the two machines cannot drift apart: the collection step
 * is read only, the container is cleared only after the block is really removed, and the storage
 * step re-checks that the simulated insert fits.</p>
 *
 * <p>No chunk is ever force loaded here; callers must check {@link Level#isLoaded(BlockPos)} first.</p>
 */
public final class MiningHarvest {
    private MiningHarvest() {}

    /**
     * Rolls the loot table of one block and copies the contents of its container into
     * {@code output}. The container is <b>not</b> cleared: a caller that cannot store the drops
     * must be able to retry without losing items.
     */
    public static void collectDrops(ServerLevel level, BlockPos pos, BlockState state,
                                    FakePlayer player, ItemStack tool, List<ItemStack> output) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> loot = Block.getDrops(state, level, pos, blockEntity, player, tool);

        boolean contentsAlreadyIncluded = false;
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            if (hasNonEmptyContainer(stack)) {
                // Some drops (for example shulker boxes) carry their contents in the item itself;
                // copying the block entity's slots too would duplicate them. A bare presence check
                // is not enough: a GameTest probe showed that a mined chest drops with a present
                // but empty CONTAINER component, so only a non-empty one proves the contents are
                // already part of the loot.
                contentsAlreadyIncluded = true;
            }
            output.add(stack.copy());
        }

        if (!contentsAlreadyIncluded && blockEntity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    output.add(stack.copy());
                }
            }
        }
    }

    /** True when the stack carries its own, non-empty container contents. */
    private static boolean hasNonEmptyContainer(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return false;
        }
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (!contents.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Empties the container at {@code pos} (if any). Must be called right before the block is
     * removed: vanilla containers drop their contents in {@code preRemoveSideEffects}, so the
     * copies already taken by {@link #collectDrops} would otherwise be duplicated.
     */
    public static void clearContainerContents(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            container.clearContent();
        }
    }

    /**
     * Simulates inserting every drop into the output view.
     *
     * @param output     the real output view, whose slots are copied into {@code simulation}
     * @param simulation a scratch handler with one slot per output slot
     */
    public static boolean canStore(IItemHandler output, ItemStackHandler simulation,
                                   List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        int slots = Math.min(output.getSlots(), simulation.getSlots());
        for (int slot = 0; slot < slots; slot++) {
            simulation.setStackInSlot(slot, output.getStackInSlot(slot).copy());
        }
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemHandlerHelper.insertItemStacked(simulation, stack.copy(), false).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Inserts drops that were checked with {@link #canStore}. A remainder (which should not
     * happen) is dropped at the machine instead of being deleted.
     */
    public static void store(ServerLevel level, BlockPos machinePos, IItemHandler output,
                             List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, stack.copy(), false);
            if (!remainder.isEmpty()) {
                Block.popResource(level, machinePos, remainder);
            }
        }
        drops.clear();
    }
}
