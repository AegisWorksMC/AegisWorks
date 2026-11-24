package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Menu type of the Phase 17 metal press; all behaviour lives in {@link ProcessingMenu}. */
public class MetalPressMenu extends ProcessingMenu {
    public MetalPressMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(ModMenus.METAL_PRESS.get(), containerId, playerInventory, buffer);
    }

    public MetalPressMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.METAL_PRESS.get(), containerId, playerInventory, pos);
    }

    public MetalPressMenu(int containerId, Inventory playerInventory,
                          ProcessingBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.METAL_PRESS.get(), containerId, playerInventory, blockEntity, data);
    }
}
