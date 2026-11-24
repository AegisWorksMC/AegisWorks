package com.industrialcore.menu;

import com.industrialcore.blockentity.SuitModifierBlockEntity;
import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.SuitModuleItem;
import com.industrialcore.item.VirtualSuitArmorItem;
import com.industrialcore.item.WeaponAttachmentItem;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.suit.SuitModifierLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Phase 98: Container menu for the Suit Module Modifier ({@code suit_modifier}).
 */
public class SuitModifierMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = SuitModifierBlockEntity.SLOT_COUNT; // 4
    private static final int PLAYER_INV_START = MACHINE_SLOTS;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    private static final int HOTBAR_END = PLAYER_INV_END + 9;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final SuitModifierBlockEntity blockEntity;

    public SuitModifierMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                extraData.readBlockPos(),
                new ItemStackHandler(MACHINE_SLOTS),
                new SimpleContainerData(SuitModifierBlockEntity.DATA_COUNT),
                null);
    }

    public SuitModifierMenu(int containerId, Inventory playerInv, SuitModifierBlockEntity be) {
        this(containerId, playerInv, be.getBlockPos(), be.getInventory(), be.getDataAccess(), be);
    }

    private SuitModifierMenu(int containerId, Inventory playerInv, BlockPos pos,
                             IItemHandler handler, ContainerData data, SuitModifierBlockEntity be) {
        super(ModMenus.SUIT_MODIFIER.get(), containerId);
        this.pos = pos;
        this.blockEntity = be;
        this.access = be != null && be.getLevel() != null
                ? ContainerLevelAccess.create(be.getLevel(), pos)
                : ContainerLevelAccess.NULL;
        this.data = data;

        // Slot 0: Virtual Aegis armor piece OR Electric Weapon
        this.addSlot(new SlotItemHandler(handler, SuitModifierBlockEntity.SUIT_SLOT,
                SuitModifierLayout.SUIT_SLOT_X, SuitModifierLayout.SUIT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof VirtualSuitArmorItem
                        || stack.getItem() instanceof ElectricWeaponItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 1: Suit Module item OR Weapon Attachment item
        this.addSlot(new SlotItemHandler(handler, SuitModifierBlockEntity.MODULE_SLOT,
                SuitModifierLayout.MODULE_SLOT_X, SuitModifierLayout.MODULE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SuitModuleItem
                        || stack.getItem() instanceof WeaponAttachmentItem;
            }
        });

        // Slot 2: Motor upgrade
        this.addSlot(new SlotItemHandler(handler, SuitModifierBlockEntity.MOTOR_SLOT,
                SuitModifierLayout.MOTOR_SLOT_X, SuitModifierLayout.MOTOR_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MOTOR.get());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit(playerInv.player);
            }
        });

        // Slot 3: Battery upgrade
        this.addSlot(new SlotItemHandler(handler, SuitModifierBlockEntity.BATTERY_SLOT,
                SuitModifierLayout.BATTERY_SLOT_X, SuitModifierLayout.BATTERY_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BATTERY.get());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit(playerInv.player);
            }
        });

        // Player inventory (27) + hotbar (9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        GuiMetrics.SIDE + col * 18, SuitModifierLayout.PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col,
                    GuiMetrics.SIDE + col * 18, SuitModifierLayout.HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    private int upgradeLimit(Player player) {
        return blockEntity != null
                ? blockEntity.getUpgradeSlotCap(player)
                : com.industrialcore.level.UpgradeCapBridge.effectiveLimit(
                        player, null, SuitModifierBlockEntity.MACHINE_ID);
    }

    public BlockPos getPos() {
        return pos;
    }

    public ItemStack getSuitStack() {
        return this.slots.get(SuitModifierBlockEntity.SUIT_SLOT).getItem();
    }

    public ItemStack getModuleStack() {
        return this.slots.get(SuitModifierBlockEntity.MODULE_SLOT).getItem();
    }

    public int getEnergyStored() {
        return (data.get(SuitModifierBlockEntity.DATA_ENERGY_LO) & 0xFFFF)
                | ((data.get(SuitModifierBlockEntity.DATA_ENERGY_HI) & 0xFFFF) << 16);
    }

    public int getEnergyCapacity() {
        int cap = (data.get(SuitModifierBlockEntity.DATA_CAP_LO) & 0xFFFF)
                | ((data.get(SuitModifierBlockEntity.DATA_CAP_HI) & 0xFFFF) << 16);
        return Math.max(1, cap);
    }

    public int getDisplayState() {
        return data.get(SuitModifierBlockEntity.DATA_STATE);
    }

    public int getInstallCost() {
        return Math.max(1_000, data.get(SuitModifierBlockEntity.DATA_INSTALL_COST));
    }

    public int getMotorCount() {
        ItemStack s = this.slots.get(SuitModifierBlockEntity.MOTOR_SLOT).getItem();
        return s.is(ModItems.MOTOR.get()) ? s.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack s = this.slots.get(SuitModifierBlockEntity.BATTERY_SLOT).getItem();
        return s.is(ModItems.BATTERY.get()) ? s.getCount() : 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SUIT_MODIFIER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < MACHINE_SLOTS) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof VirtualSuitArmorItem
                    || stack.getItem() instanceof ElectricWeaponItem) {
                if (!this.moveItemStackTo(stack, SuitModifierBlockEntity.SUIT_SLOT,
                        SuitModifierBlockEntity.SUIT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof SuitModuleItem
                    || stack.getItem() instanceof WeaponAttachmentItem) {
                if (!this.moveItemStackTo(stack, SuitModifierBlockEntity.MODULE_SLOT,
                        SuitModifierBlockEntity.MODULE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.MOTOR.get())) {
                if (!this.moveItemStackTo(stack, SuitModifierBlockEntity.MOTOR_SLOT,
                        SuitModifierBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, SuitModifierBlockEntity.BATTERY_SLOT,
                        SuitModifierBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < PLAYER_INV_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }
}
