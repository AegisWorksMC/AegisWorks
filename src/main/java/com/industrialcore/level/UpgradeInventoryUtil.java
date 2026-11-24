package com.industrialcore.level;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 87 backwards-compatible NBT loader for {@link ItemStackHandler}s whose slot count grew
 * when motor/battery upgrade slots were appended at the end.
 */
public final class UpgradeInventoryUtil {
    private UpgradeInventoryUtil() {}

    /**
     * Deserializes {@code tag} into {@code handler} and guarantees the handler keeps at least
     * {@code expectedSlots} slots without losing any items saved by an older mod version.
     */
    public static void loadWithMinSize(ItemStackHandler handler, HolderLookup.Provider registries,
                                       CompoundTag tag, int expectedSlots) {
        handler.deserializeNBT(registries, tag);
        if (handler.getSlots() < expectedSlots) {
            int oldSize = handler.getSlots();
            List<ItemStack> saved = new ArrayList<>(oldSize);
            for (int i = 0; i < oldSize; i++) {
                saved.add(handler.getStackInSlot(i).copy());
            }
            handler.setSize(expectedSlots);
            for (int i = 0; i < oldSize; i++) {
                handler.setStackInSlot(i, saved.get(i));
            }
        }
    }
}
