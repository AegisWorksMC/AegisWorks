package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Menu type of the electrolyzer; all behaviour lives in {@link ProcessingMenu}. */
public class ElectrolyzerMenu extends ProcessingMenu {
    public ElectrolyzerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(ModMenus.ELECTROLYZER.get(), containerId, playerInventory, buffer);
    }

    public ElectrolyzerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.ELECTROLYZER.get(), containerId, playerInventory, pos);
    }

    public ElectrolyzerMenu(int containerId, Inventory playerInventory,
                            ProcessingBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.ELECTROLYZER.get(), containerId, playerInventory, blockEntity, data);
    }
}
