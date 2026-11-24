package com.industrialcore.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Block item of the item pipes. Adds the Phase 10 explanation (default {@code NONE}, wrench) to
 * the existing tier tooltip, so the behaviour is discoverable without opening a GUI.
 */
public class PipeBlockItem extends BlockItem {
    private final String tooltipKey;

    public PipeBlockItem(Block block, Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.pipe_default")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.pipe_wrench")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
