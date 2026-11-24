package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Menu type of the electric furnace; all behaviour lives in {@link ProcessingMenu}. */
public class ElectricFurnaceMenu extends ProcessingMenu {
    public ElectricFurnaceMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(ModMenus.ELECTRIC_FURNACE.get(), containerId, playerInventory, buffer);
    }

    public ElectricFurnaceMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.ELECTRIC_FURNACE.get(), containerId, playerInventory, pos);
    }

    public ElectricFurnaceMenu(int containerId, Inventory playerInventory,
                               ProcessingBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.ELECTRIC_FURNACE.get(), containerId, playerInventory, blockEntity, data);
    }
}
