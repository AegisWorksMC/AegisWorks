package com.industrialcore.item;

import com.industrialcore.storage.BigCountFormatter;
import com.industrialcore.storage.BigCountFormatter.UnitMode;
import com.industrialcore.storage.StorageCoreTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Phase 89: Capacity expansion core item (Mk.I - Mk.V) installed in the Quantum Storage Core.
 */
public class StorageCoreItem extends Item {
    private final StorageCoreTier tier;

    public StorageCoreItem(Properties properties, StorageCoreTier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public StorageCoreTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        String enCap = BigCountFormatter.format(tier.bonusPerType(), UnitMode.ENGLISH);
        String jpCap = BigCountFormatter.format(tier.bonusPerType(), UnitMode.JAPANESE);
        String star = tier == StorageCoreTier.MK5 ? "✦ " : "";
        int rgb = tier.accentColor() & 0x00FFFFFF;

        tooltip.add(Component.translatable("gui.industrialcore.tooltip.storage_core.types",
                        String.format("%,d", tier.bonusTypes()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.storage_core.capacity",
                        star + enCap + " / " + jpCap)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.storage_core.hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
