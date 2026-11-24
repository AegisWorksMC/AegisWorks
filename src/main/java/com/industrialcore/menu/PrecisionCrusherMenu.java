package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Menu type of the precision crusher; all behaviour lives in {@link ProcessingMenu}. */
public class PrecisionCrusherMenu extends ProcessingMenu {
    public PrecisionCrusherMenu(int containerId, Inventory playerInventory,
                                RegistryFriendlyByteBuf buffer) {
        super(ModMenus.PRECISION_CRUSHER.get(), containerId, playerInventory, buffer);
    }

    public PrecisionCrusherMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.PRECISION_CRUSHER.get(), containerId, playerInventory, pos);
    }

    public PrecisionCrusherMenu(int containerId, Inventory playerInventory,
                                ProcessingBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.PRECISION_CRUSHER.get(), containerId, playerInventory, blockEntity, data);
    }
}
