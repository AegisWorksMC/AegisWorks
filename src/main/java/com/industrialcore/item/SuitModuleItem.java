package com.industrialcore.item;

import com.industrialcore.suit.SuitModule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Phase 98: Installable module item for Virtual Aegis armor pieces (via {@code suit_modifier}).
 */
public class SuitModuleItem extends Item {
    private final SuitModule module;

    public SuitModuleItem(SuitModule module, Properties properties) {
        super(properties.stacksTo(16));
        this.module = module;
    }

    public SuitModule getModule() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.industrialcore.suit_module.target",
                        Component.translatable(module.slot().translationKey()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(module.descTranslationKey())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.industrialcore.suit_module.install_hint")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
