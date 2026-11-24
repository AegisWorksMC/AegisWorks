package com.industrialcore.item;

import com.industrialcore.weapon.WeaponAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Phase 101: Modular weapon attachment item installed via {@code suit_modifier}.
 */
public class WeaponAttachmentItem extends Item {
    private final WeaponAttachment attachment;

    public WeaponAttachmentItem(WeaponAttachment attachment, Properties properties) {
        super(properties.stacksTo(16));
        this.attachment = attachment;
    }

    public WeaponAttachment getAttachment() {
        return attachment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.industrialcore.attachment.target",
                        Component.translatable(attachment.weaponKind().itemTranslationKey()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(attachment.descriptionTranslationKey())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.industrialcore.attachment.install_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
