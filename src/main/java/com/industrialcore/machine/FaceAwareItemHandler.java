package com.industrialcore.machine;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

/**
 * Item handler exposed on one machine face (Phase 11.3-11.5).
 *
 * <p>The machine's own automation handler already knows which slots may accept items and which may
 * be extracted from. This wrapper adds the per-face rules on top, so the same machine can have
 * "top = material A, side = material B, bottom = output" without changing its processing code.</p>
 *
 * <p>Insert/extract are additionally routed through the machine's distributor, which is how the
 * auto crafter spreads one arriving stack over several recipe slots (Phase 11.1).</p>
 *
 * <p>Nothing here is client controlled: the sides come from the block capability lookup on the
 * server and the configuration lives in the block entity's saved data.</p>
 */
public final class FaceAwareItemHandler implements IItemHandler {
    /** Optional machine hook that may spread an insertion over several slots. */
    public interface Distributor {
        ItemStack insert(int requestedSlot, ItemStack stack, boolean simulate);
    }

    private final IItemHandler base;
    private final MachineFaceConfig config;
    @Nullable
    private final Direction side;
    @Nullable
    private final Distributor distributor;
    private final IntPredicate inputSlots;
    private final IntPredicate outputSlots;

    public FaceAwareItemHandler(IItemHandler base, MachineFaceConfig config,
                                @Nullable Direction side, @Nullable Distributor distributor) {
        this(base, config, side, distributor, slot -> true, slot -> true);
    }

    /**
     * @param inputSlots  slots that may ever receive items from automation
     * @param outputSlots slots that may ever be extracted from
     */
    public FaceAwareItemHandler(IItemHandler base, MachineFaceConfig config, @Nullable Direction side,
                                @Nullable Distributor distributor,
                                IntPredicate inputSlots, IntPredicate outputSlots) {
        this.base = base;
        this.config = config;
        this.side = side;
        this.distributor = distributor;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
    }

    private static String idOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private boolean allowedToInsert(int slot, ItemStack stack) {
        // The machine's own role for the slot always wins: an output slot can never be filled
        // from outside, whatever the face says.
        if (!inputSlots.test(slot)) {
            return false;
        }
        if (side == null) {
            return true;
        }
        return config.canInsert(side.get3DDataValue(), slot, idOf(stack));
    }

    private boolean allowedToExtract(int slot, ItemStack stack) {
        // Symmetrically, an input slot can never be drained through an OUTPUT face.
        if (!outputSlots.test(slot)) {
            return false;
        }
        if (side == null) {
            return true;
        }
        return config.canExtract(side.get3DDataValue(), slot, idOf(stack));
    }

    @Override
    public int getSlots() {
        return base.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return base.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !allowedToInsert(slot, stack)) {
            return stack;
        }
        if (distributor != null) {
            return distributor.insert(slot, stack, simulate);
        }
        return base.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = base.getStackInSlot(slot);
        if (current.isEmpty() || !allowedToExtract(slot, current)) {
            return ItemStack.EMPTY;
        }
        return base.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return base.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return allowedToInsert(slot, stack) && base.isItemValid(slot, stack);
    }

    /** Face mode of this handler, for diagnostics and tests. */
    @Nullable
    public FaceMode faceMode() {
        return side == null ? null : config.mode(side.get3DDataValue());
    }
}
