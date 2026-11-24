package com.industrialcore.item;

import com.industrialcore.storage.BigCountFormatter;
import com.industrialcore.suit.VirtualHudMath;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import com.industrialcore.weapon.WeaponKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 101: Common base class for all 4 FE-powered endgame electric weapons.
 *
 * <ul>
 *   <li>Persists FE ({@code 5,000,000 FE}) and installed {@link WeaponAttachment} states inside
 *       {@link DataComponents#CUSTOM_DATA}.</li>
 *   <li>Exposes {@code Capabilities.EnergyStorage.ITEM} so {@code wireless_charger} and
 *       {@code suit_modifier} charge it automatically.</li>
 *   <li>Zero physical durability wear ({@code isDamageable = false}): never breaks at 0 FE.</li>
 * </ul>
 */
public abstract class ElectricWeaponItem extends Item {
    public static final String TAG_ENERGY = "Energy";
    public static final String TAG_ATTACHMENTS = "Attachments";

    private final WeaponKind weaponKind;

    protected ElectricWeaponItem(WeaponKind weaponKind, Properties properties) {
        super(properties.stacksTo(1));
        this.weaponKind = weaponKind;
    }

    public WeaponKind getWeaponKind() {
        return weaponKind;
    }

    // -------------------------------------------------------------------------
    // Zero physical durability wear; neon FE item bar
    // -------------------------------------------------------------------------

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        // Electric weapons use FE instead of durability and never break.
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int e = getEnergy(stack);
        return Math.round(13.0F * e / (float) VirtualWeaponLogic.WEAPON_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        double ratio = (double) getEnergy(stack) / (double) VirtualWeaponLogic.WEAPON_CAPACITY;
        return VirtualHudMath.energyColor(ratio) & 0x00FFFFFF;
    }

    // -------------------------------------------------------------------------
    // Energy persistence (CUSTOM_DATA)
    // -------------------------------------------------------------------------

    public static int getEnergy(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ElectricWeaponItem)) {
            return 0;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return 0;
        }
        return Math.max(0, Math.min(VirtualWeaponLogic.WEAPON_CAPACITY, cd.copyTag().getInt(TAG_ENERGY)));
    }

    public static void setEnergy(ItemStack stack, int energy) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ElectricWeaponItem)) {
            return;
        }
        int clamped = Math.max(0, Math.min(VirtualWeaponLogic.WEAPON_CAPACITY, energy));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_ENERGY, clamped));
    }

    public static int receiveEnergy(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0 || stack.isEmpty() || !(stack.getItem() instanceof ElectricWeaponItem)) {
            return 0;
        }
        int cur = getEnergy(stack);
        int room = VirtualWeaponLogic.WEAPON_CAPACITY - cur;
        int accepted = Math.min(room, Math.min(amount, VirtualWeaponLogic.WEAPON_MAX_RECEIVE));
        if (accepted > 0 && !simulate) {
            setEnergy(stack, cur + accepted);
        }
        return accepted;
    }

    public static boolean consumeEnergy(ItemStack stack, int cost, boolean creative) {
        if (creative || cost <= 0) {
            return true;
        }
        int cur = getEnergy(stack);
        if (cur < cost) {
            return false;
        }
        setEnergy(stack, cur - cost);
        return true;
    }

    public static IEnergyStorage createEnergyCapability(ItemStack stack) {
        return new WeaponEnergyStorage(stack);
    }

    // -------------------------------------------------------------------------
    // Attachment persistence (CUSTOM_DATA -> "Attachments")
    // -------------------------------------------------------------------------

    public static Map<String, Boolean> getAttachments(ItemStack stack) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (stack.isEmpty() || !(stack.getItem() instanceof ElectricWeaponItem)) {
            return map;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return map;
        }
        CompoundTag root = cd.copyTag();
        if (!root.contains(TAG_ATTACHMENTS)) {
            return map;
        }
        CompoundTag atts = root.getCompound(TAG_ATTACHMENTS);
        for (String key : atts.getAllKeys()) {
            if (WeaponAttachment.byId(key) != null) {
                map.put(key, atts.getBoolean(key));
            }
        }
        return map;
    }

    public static void setAttachments(ItemStack stack, Map<String, Boolean> attachments) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ElectricWeaponItem)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag atts = new CompoundTag();
            for (Map.Entry<String, Boolean> e : attachments.entrySet()) {
                if (WeaponAttachment.byId(e.getKey()) != null) {
                    atts.putBoolean(e.getKey(), Boolean.TRUE.equals(e.getValue()));
                }
            }
            tag.put(TAG_ATTACHMENTS, atts);
        });
    }

    public static boolean hasAttachment(ItemStack stack, WeaponAttachment att) {
        return att != null && getAttachments(stack).containsKey(att.id());
    }

    public static boolean isAttachmentEnabled(ItemStack stack, WeaponAttachment att) {
        return VirtualWeaponLogic.isAttachmentEnabled(att, getAttachments(stack));
    }

    // -------------------------------------------------------------------------
    // Tooltips
    // -------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        int energy = getEnergy(stack);
        String curFmt = BigCountFormatter.formatCompact(BigInteger.valueOf(energy), BigCountFormatter.UnitMode.ENGLISH);
        String maxFmt = BigCountFormatter.formatCompact(
                BigInteger.valueOf(VirtualWeaponLogic.WEAPON_CAPACITY), BigCountFormatter.UnitMode.ENGLISH);
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.energy", curFmt, maxFmt)
                .withStyle(ChatFormatting.AQUA));

        tooltip.add(Component.translatable("tooltip.industrialcore.weapon." + weaponKind.id())
                .withStyle(ChatFormatting.GRAY));

        Map<String, Boolean> atts = getAttachments(stack);
        if (atts.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.industrialcore.weapon.no_attachments")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.industrialcore.weapon.attachments_header")
                    .withStyle(ChatFormatting.GOLD));
            for (WeaponAttachment a : WeaponAttachment.forWeapon(weaponKind)) {
                if (!atts.containsKey(a.id())) {
                    continue;
                }
                boolean on = Boolean.TRUE.equals(atts.get(a.id()));
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable(a.itemTranslationKey())
                                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                        .append(Component.literal(on ? " [ON]" : " [OFF]")
                                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)));
            }
        }
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.modifier_hint")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private record WeaponEnergyStorage(ItemStack stack) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return ElectricWeaponItem.receiveEnergy(stack, toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return ElectricWeaponItem.getEnergy(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return VirtualWeaponLogic.WEAPON_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
