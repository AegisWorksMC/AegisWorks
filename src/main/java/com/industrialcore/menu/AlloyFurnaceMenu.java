package com.industrialcore.menu;

import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the Phase 17 alloy furnace.
 *
 * <p>Shift-click moves the furnace contents into the player inventory; player items only go into
 * the two ingredient slots and the fuel slot, never into the output slot.</p>
 */
public class AlloyFurnaceMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return AlloyFurnaceBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final AlloyFurnaceBlockEntity blockEntity;

    public AlloyFurnaceMenu(int containerId, Inventory playerInventory,
                            RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public AlloyFurnaceMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public AlloyFurnaceMenu(int containerId, Inventory playerInventory,
                            AlloyFurnaceBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private AlloyFurnaceMenu(int containerId, Inventory playerInventory, BlockPos pos,
                             @Nullable AlloyFurnaceBlockEntity blockEntity,
                             @Nullable ContainerData data) {
        super(ModMenus.ALLOY_FURNACE.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(AlloyFurnaceBlockEntity.SLOT_COUNT);

        addMachineSlot(machineInventory, AlloyFurnaceBlockEntity.FIRST_SLOT,
                AlloyFurnaceLayout.FIRST_SLOT);
        addMachineSlot(machineInventory, AlloyFurnaceBlockEntity.SECOND_SLOT,
                AlloyFurnaceLayout.SECOND_SLOT);
        addMachineSlot(machineInventory, AlloyFurnaceBlockEntity.FUEL_SLOT,
                AlloyFurnaceLayout.FUEL_SLOT);
        addMachineSlot(machineInventory, AlloyFurnaceBlockEntity.OUTPUT_SLOT,
                AlloyFurnaceLayout.OUTPUT_SLOT);
        Rect motor = AlloyFurnaceLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, AlloyFurnaceBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "alloy_furnace")));
        Rect battery = AlloyFurnaceLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, AlloyFurnaceBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "alloy_furnace")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = AlloyFurnaceLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = AlloyFurnaceLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private void addMachineSlot(IItemHandler inventory, int index, Rect rect) {
        this.addSlot(new SlotItemHandler(inventory, index, rect.x(), rect.y()));
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable AlloyFurnaceBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(AlloyFurnaceBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static AlloyFurnaceBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof AlloyFurnaceBlockEntity furnace
                ? furnace : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity != null) {
            return !this.blockEntity.isRemoved()
                    && Vec3.atCenterOf(this.pos).distanceToSqr(player.position()) <= 64.0D;
        }
        return player.level().isClientSide
                && Vec3.atCenterOf(this.pos).distanceToSqr(player.position()) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = AlloyFurnaceBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ModItems.MOTOR.get())) {
            moved = this.moveItemStackTo(stack, AlloyFurnaceBlockEntity.MOTOR_SLOT,
                    AlloyFurnaceBlockEntity.MOTOR_SLOT + 1, false);
        } else if (stack.is(ModItems.BATTERY.get())) {
            moved = this.moveItemStackTo(stack, AlloyFurnaceBlockEntity.BATTERY_SLOT,
                    AlloyFurnaceBlockEntity.BATTERY_SLOT + 1, false);
        } else {
            // Player items only go into the two ingredient slots and the fuel slot; the output slot
            // is never filled from the outside.
            moved = this.moveItemStackTo(stack, AlloyFurnaceBlockEntity.FIRST_SLOT,
                    AlloyFurnaceBlockEntity.FUEL_SLOT + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(AlloyFurnaceBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(AlloyFurnaceBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(AlloyFurnaceBlockEntity.DATA_TIME));
    }

    public int getBurnTime() {
        return this.data.get(AlloyFurnaceBlockEntity.DATA_BURN);
    }

    public int getBurnTimeTotal() {
        return Math.max(1, this.data.get(AlloyFurnaceBlockEntity.DATA_BURN_TOTAL));
    }

    public int getMotorCount() {
        ItemStack stack = this.slots.get(AlloyFurnaceBlockEntity.MOTOR_SLOT).getItem();
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = this.slots.get(AlloyFurnaceBlockEntity.BATTERY_SLOT).getItem();
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public AlloyFurnaceBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
