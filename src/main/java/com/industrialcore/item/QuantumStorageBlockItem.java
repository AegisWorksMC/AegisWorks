package com.industrialcore.item;

import com.industrialcore.blockentity.QuantumStorageBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Phase 89: BlockItem for the Quantum Storage Core, displaying preserved inventory summary in tooltip.
 */
public class QuantumStorageBlockItem extends BlockItem {
    public QuantumStorageBlockItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.quantum_storage_core")
                .withStyle(ChatFormatting.GRAY));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(QuantumStorageBlockEntity.TAG_STORAGE_ROOT)) {
            CompoundTag root = data.copyTag().getCompound(QuantumStorageBlockEntity.TAG_STORAGE_ROOT);
            int items = root.getList(QuantumStorageBlockEntity.TAG_ITEMS, Tag.TAG_COMPOUND).size();
            int fluids = root.getList(QuantumStorageBlockEntity.TAG_FLUIDS, Tag.TAG_COMPOUND).size();
            int cores = 0;
            CompoundTag coreTag = root.getCompound(QuantumStorageBlockEntity.TAG_CORES);
            if (coreTag.contains("Items")) {
                cores = coreTag.getList("Items", Tag.TAG_COMPOUND).size();
            }
            tooltip.add(Component.translatable("gui.industrialcore.tooltip.quantum_storage_saved",
                            items, fluids, cores)
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
