package com.industrialcore.menu;

import com.industrialcore.level.MachineLevelMath;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Shared Phase 87 upgrade slot (motor or battery) used across all 17 industrial machines.
 *
 * <p>Enforces the expected upgrade item and the per-player Machine Level stack cap (1..64) while
 * always allowing players to pick up existing items (including stacks exceeding a lower cap).</p>
 */
public class MachineUpgradeSlot extends SlotItemHandler {
    private final Supplier<Item> expectedItem;
    private final IntSupplier limitSupplier;

    public MachineUpgradeSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition,
                              Supplier<Item> expectedItem, IntSupplier limitSupplier) {
        super(itemHandler, index, xPosition, yPosition);
        this.expectedItem = expectedItem;
        this.limitSupplier = limitSupplier;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.is(this.expectedItem.get()) && super.mayPlace(stack);
    }

    @Override
    public int getMaxStackSize() {
        return Math.max(MachineLevelMath.MIN_UPGRADE_CAP,
                Math.min(MachineLevelMath.MAX_UPGRADE_CAP, this.limitSupplier.getAsInt()));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.getMaxStackSize();
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return true;
    }
}
