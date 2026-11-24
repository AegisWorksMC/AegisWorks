package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Menu type of the electric crusher; all behaviour lives in {@link ProcessingMenu}. */
public class CrusherMenu extends ProcessingMenu {
    public CrusherMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(ModMenus.CRUSHER.get(), containerId, playerInventory, buffer);
    }

    public CrusherMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.CRUSHER.get(), containerId, playerInventory, pos);
    }

    public CrusherMenu(int containerId, Inventory playerInventory, ProcessingBlockEntity blockEntity,
                       ContainerData data) {
        super(ModMenus.CRUSHER.get(), containerId, playerInventory, blockEntity, data);
    }
}
