package com.industrialcore.item;

import com.industrialcore.storage.BigCountFormatter;
import com.industrialcore.suit.SuitModule;
import com.industrialcore.suit.SuitModule.SlotKind;
import com.industrialcore.suit.VirtualHudMath;
import com.industrialcore.suit.VirtualSuitLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 98: Virtual Aegis endgame powered armor (4 pieces: helmet, chestplate, leggings, boots).
 *
 * <p>Uses internal FE ({@code 10,000,000 FE} per piece, persisted in {@link DataComponents#CUSTOM_DATA})
 * instead of physical durability: never takes vanilla durability damage and never breaks at 0 FE.
 * Supports {@code wireless_charger} via {@code Capabilities.EnergyStorage.ITEM} and modular
 * upgrades installed through {@code suit_modifier}.</p>
 */
public class VirtualSuitArmorItem extends ArmorItem {
    public static final String TAG_ENERGY = "Energy";
    public static final String TAG_MODULES = "Modules";

    public VirtualSuitArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties.stacksTo(1));
    }

    public SlotKind getSlotKind() {
        return switch (this.type) {
            case HELMET -> SlotKind.HELMET;
            case CHESTPLATE -> SlotKind.CHESTPLATE;
            case LEGGINGS -> SlotKind.LEGGINGS;
            case BOOTS -> SlotKind.BOOTS;
            default -> SlotKind.CHESTPLATE;
        };
    }

    // -------------------------------------------------------------------------
    // Zero physical durability loss (FE is durability)
    // -------------------------------------------------------------------------

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        // Virtual Aegis never loses physical durability; FE absorbs damage instead.
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int e = getEnergy(stack);
        return Math.round(13.0F * e / (float) VirtualSuitLogic.PIECE_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        double ratio = (double) getEnergy(stack) / (double) VirtualSuitLogic.PIECE_CAPACITY;
        return VirtualHudMath.energyColor(ratio) & 0x00FFFFFF;
    }

    // -------------------------------------------------------------------------
    // Energy persistence (CUSTOM_DATA)
    // -------------------------------------------------------------------------

    public static int getEnergy(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof VirtualSuitArmorItem)) {
            return 0;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return 0;
        }
        return Math.max(0, Math.min(VirtualSuitLogic.PIECE_CAPACITY, cd.copyTag().getInt(TAG_ENERGY)));
    }

    public static void setEnergy(ItemStack stack, int energy) {
        if (stack.isEmpty() || !(stack.getItem() instanceof VirtualSuitArmorItem)) {
            return;
        }
        int clamped = Math.max(0, Math.min(VirtualSuitLogic.PIECE_CAPACITY, energy));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_ENERGY, clamped));
    }

    public static int receiveEnergy(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0 || stack.isEmpty() || !(stack.getItem() instanceof VirtualSuitArmorItem)) {
            return 0;
        }
        int cur = getEnergy(stack);
        int room = VirtualSuitLogic.PIECE_CAPACITY - cur;
        int accepted = Math.min(room, Math.min(amount, VirtualSuitLogic.PIECE_MAX_RECEIVE));
        if (accepted > 0 && !simulate) {
            setEnergy(stack, cur + accepted);
        }
        return accepted;
    }

    public static boolean consumeEnergy(ItemStack stack, int cost) {
        if (cost <= 0) {
            return true;
        }
        int cur = getEnergy(stack);
        if (cur < cost) {
            return false;
        }
        setEnergy(stack, cur - cost);
        return true;
    }

    public static int drainUpTo(ItemStack stack, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int cur = getEnergy(stack);
        int taken = Math.min(cur, amount);
        if (taken > 0) {
            setEnergy(stack, cur - taken);
        }
        return taken;
    }

    public static IEnergyStorage createEnergyCapability(ItemStack stack) {
        return new SuitEnergyStorage(stack);
    }

    // -------------------------------------------------------------------------
    // Module persistence (CUSTOM_DATA -> "Modules")
    // -------------------------------------------------------------------------

    public static Map<String, Boolean> getModules(ItemStack stack) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (stack.isEmpty() || !(stack.getItem() instanceof VirtualSuitArmorItem)) {
            return map;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return map;
        }
        CompoundTag root = cd.copyTag();
        if (!root.contains(TAG_MODULES)) {
            return map;
        }
        CompoundTag mods = root.getCompound(TAG_MODULES);
        for (String key : mods.getAllKeys()) {
            if (SuitModule.byId(key) != null) {
                map.put(key, mods.getBoolean(key));
            }
        }
        return map;
    }

    public static void setModules(ItemStack stack, Map<String, Boolean> modules) {
        if (stack.isEmpty() || !(stack.getItem() instanceof VirtualSuitArmorItem)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag mods = new CompoundTag();
            for (Map.Entry<String, Boolean> e : modules.entrySet()) {
                if (SuitModule.byId(e.getKey()) != null) {
                    mods.putBoolean(e.getKey(), Boolean.TRUE.equals(e.getValue()));
                }
            }
            tag.put(TAG_MODULES, mods);
        });
    }

    public static boolean hasModule(ItemStack stack, SuitModule module) {
        return module != null && getModules(stack).containsKey(module.id());
    }

    public static boolean isModuleEnabled(ItemStack stack, SuitModule module) {
        return VirtualSuitLogic.isModuleEnabled(module, getModules(stack));
    }

    // -------------------------------------------------------------------------
    // Full-set & player helpers
    // -------------------------------------------------------------------------

    public static boolean isFullSetEquipped(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return VirtualHudMath.isFullSet(
                player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof VirtualSuitArmorItem,
                player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof VirtualSuitArmorItem,
                player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof VirtualSuitArmorItem,
                player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof VirtualSuitArmorItem
        );
    }

    public static long totalSuitEnergy(@Nullable Player player) {
        if (player == null) {
            return 0L;
        }
        long sum = 0L;
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            sum += getEnergy(player.getItemBySlot(slot));
        }
        return sum;
    }

    public static long totalSuitCapacity(@Nullable Player player) {
        if (player == null) {
            return 0L;
        }
        long cap = 0L;
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).getItem() instanceof VirtualSuitArmorItem) {
                cap += VirtualSuitLogic.PIECE_CAPACITY;
            }
        }
        return cap;
    }

    /** Returns all currently installed, enabled, and powered modules worn by {@code player}. */
    public static List<SuitModule> getActiveModules(@Nullable Player player) {
        if (player == null) {
            return List.of();
        }
        List<SuitModule> active = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = player.getItemBySlot(slot);
            if (!(st.getItem() instanceof VirtualSuitArmorItem) || getEnergy(st) <= 0) {
                continue;
            }
            Map<String, Boolean> mods = getModules(st);
            for (SuitModule m : SuitModule.forSlot(((VirtualSuitArmorItem) st.getItem()).getSlotKind())) {
                if (VirtualSuitLogic.isModuleEnabled(m, mods)) {
                    active.add(m);
                }
            }
        }
        return active;
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
                BigInteger.valueOf(VirtualSuitLogic.PIECE_CAPACITY), BigCountFormatter.UnitMode.ENGLISH);
        tooltip.add(Component.translatable("tooltip.industrialcore.virtual_suit.energy", curFmt, maxFmt)
                .withStyle(ChatFormatting.AQUA));

        Map<String, Boolean> mods = getModules(stack);
        if (mods.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.industrialcore.virtual_suit.no_modules")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.industrialcore.virtual_suit.modules_header")
                    .withStyle(ChatFormatting.GOLD));
            for (SuitModule m : SuitModule.forSlot(getSlotKind())) {
                if (!mods.containsKey(m.id())) {
                    continue;
                }
                boolean on = Boolean.TRUE.equals(mods.get(m.id()));
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable(m.itemTranslationKey())
                                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                        .append(Component.literal(on ? " [ON]" : " [OFF]")
                                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)));
            }
        }
        tooltip.add(Component.translatable("tooltip.industrialcore.virtual_suit.set_bonus")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.industrialcore.virtual_suit.wireless_hint")
                .withStyle(ChatFormatting.GRAY));
    }

    private record SuitEnergyStorage(ItemStack stack) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return VirtualSuitArmorItem.receiveEnergy(stack, toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return VirtualSuitArmorItem.getEnergy(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return VirtualSuitLogic.PIECE_CAPACITY;
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
