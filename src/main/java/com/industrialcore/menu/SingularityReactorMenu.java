package com.industrialcore.menu;

import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.endgame.SingularityReactorLayout;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
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
 * Phase 99: Container menu for the Singularity Reactor Generator ({@code singularity_reactor}).
 */
public class SingularityReactorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = SingularityReactorBlockEntity.SLOT_COUNT; // 3
    private static final int PLAYER_INV_START = MACHINE_SLOTS;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    private static final int HOTBAR_END = PLAYER_INV_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final BlockPos blockPos;
    private final SingularityReactorBlockEntity blockEntity;

    public SingularityReactorMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                extraData.readBlockPos(),
                new ItemStackHandler(MACHINE_SLOTS),
                new SimpleContainerData(SingularityReactorBlockEntity.DATA_COUNT),
                null);
    }

    public SingularityReactorMenu(int containerId, Inventory playerInv, SingularityReactorBlockEntity be) {
        this(containerId, playerInv, be.getBlockPos(), be.getInventory(), be.getDataAccess(), be);
    }

    private SingularityReactorMenu(int containerId, Inventory playerInv, BlockPos pos,
                                   IItemHandler handler, ContainerData data, SingularityReactorBlockEntity be) {
        super(ModMenus.SINGULARITY_REACTOR.get(), containerId);
        this.blockPos = pos.immutable();
        this.blockEntity = be;
        this.access = be != null && be.getLevel() != null
                ? ContainerLevelAccess.create(be.getLevel(), pos)
                : ContainerLevelAccess.NULL;
        this.data = data;

        // Slot 0: Fuel / Catalyst
        this.addSlot(new SlotItemHandler(handler, SingularityReactorBlockEntity.FUEL_SLOT,
                SingularityReactorLayout.FUEL_SLOT_X, SingularityReactorLayout.FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SingularityReactorBlockEntity.fuelTicksFor(stack) > 0;
            }
        });

        // Slot 1: Motor upgrade
        this.addSlot(new SlotItemHandler(handler, SingularityReactorBlockEntity.MOTOR_SLOT,
                SingularityReactorLayout.MOTOR_SLOT_X, SingularityReactorLayout.MOTOR_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MOTOR.get());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit(playerInv.player);
            }
        });

        // Slot 2: Battery upgrade
        this.addSlot(new SlotItemHandler(handler, SingularityReactorBlockEntity.BATTERY_SLOT,
                SingularityReactorLayout.BATTERY_SLOT_X, SingularityReactorLayout.BATTERY_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BATTERY.get());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit(playerInv.player);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        GuiMetrics.SIDE + col * 18, SingularityReactorLayout.PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col,
                    GuiMetrics.SIDE + col * 18, SingularityReactorLayout.HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    private int upgradeLimit(Player player) {
        return blockEntity != null
                ? blockEntity.getUpgradeSlotCap(player)
                : com.industrialcore.level.UpgradeCapBridge.effectiveLimit(
                        player, null, SingularityReactorBlockEntity.MACHINE_ID);
    }

    public long getStoredEnergyLong() {
        return unpackLong(SingularityReactorBlockEntity.DATA_E0);
    }

    public long getCapacityLong() {
        return Math.max(1L, unpackLong(SingularityReactorBlockEntity.DATA_C0));
    }

    private long unpackLong(int baseIdx) {
        long b0 = data.get(baseIdx) & 0xFFFFL;
        long b1 = data.get(baseIdx + 1) & 0xFFFFL;
        long b2 = data.get(baseIdx + 2) & 0xFFFFL;
        long b3 = data.get(baseIdx + 3) & 0xFFFFL;
        return b0 | (b1 << 16) | (b2 << 32) | (b3 << 48);
    }

    public long getFePerTick() {
        long lo = data.get(SingularityReactorBlockEntity.DATA_RATE_LO) & 0xFFFFL;
        long hi = data.get(SingularityReactorBlockEntity.DATA_RATE_HI) & 0xFFFFL;
        return lo | (hi << 16);
    }

    public int getBurnRemaining() {
        return (data.get(SingularityReactorBlockEntity.DATA_BURN_LO) & 0xFFFF)
                | ((data.get(SingularityReactorBlockEntity.DATA_BURN_HI) & 0xFFFF) << 16);
    }

    public int getBurnTotal() {
        return (data.get(SingularityReactorBlockEntity.DATA_BTOT_LO) & 0xFFFF)
                | ((data.get(SingularityReactorBlockEntity.DATA_BTOT_HI) & 0xFFFF) << 16);
    }

    public int getDisplayState() {
        return data.get(SingularityReactorBlockEntity.DATA_STATE);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean isMultiblockFormed() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_FORMED) != 0;
    }

    public int getMultiblockFailReason() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_REASON);
    }

    public int getMultiblockDimsPacked() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_DIMS);
    }

    public int getMultiblockCoreCells() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_CORES);
    }

    public int getMultiblockCoolantBlocks() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_COOLANT);
    }

    public int getMultiblockMultX100() {
        return data.get(SingularityReactorBlockEntity.DATA_MB_MULT_X100);
    }

    public boolean isOverdriveMode() {
        return data.get(SingularityReactorBlockEntity.DATA_OVERDRIVE) != 0;
    }

    public int getOverdriveCooldownTicks() {
        return Math.max(0, data.get(SingularityReactorBlockEntity.DATA_COOLDOWN));
    }

    public int getMotorCount() {
        ItemStack s = this.slots.get(SingularityReactorBlockEntity.MOTOR_SLOT).getItem();
        return s.is(ModItems.MOTOR.get()) ? s.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack s = this.slots.get(SingularityReactorBlockEntity.BATTERY_SLOT).getItem();
        return s.is(ModItems.BATTERY.get()) ? s.getCount() : 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(ModBlocks.SINGULARITY_REACTOR.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 196.0D, true);
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
            if (SingularityReactorBlockEntity.fuelTicksFor(stack) > 0) {
                if (!this.moveItemStackTo(stack, SingularityReactorBlockEntity.FUEL_SLOT,
                        SingularityReactorBlockEntity.FUEL_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.MOTOR.get())) {
                if (!this.moveItemStackTo(stack, SingularityReactorBlockEntity.MOTOR_SLOT,
                        SingularityReactorBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, SingularityReactorBlockEntity.BATTERY_SLOT,
                        SingularityReactorBlockEntity.BATTERY_SLOT + 1, false)) {
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
