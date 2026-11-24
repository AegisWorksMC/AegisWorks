package com.industrialcore.menu;

import com.industrialcore.blockentity.AssemblerBlockEntity;
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
 * Menu of the Phase 18 assembler.
 *
 * <p>Three interchangeable ingredient slots and one result slot. Shift-click moves the machine
 * contents into the player inventory and only ever moves player items into the ingredient slots,
 * so the result slot can never be filled from the outside.</p>
 */
public class AssemblerMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return AssemblerBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final AssemblerBlockEntity blockEntity;

    public AssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public AssemblerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public AssemblerMenu(int containerId, Inventory playerInventory,
                         AssemblerBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private AssemblerMenu(int containerId, Inventory playerInventory, BlockPos pos,
                          @Nullable AssemblerBlockEntity blockEntity, @Nullable ContainerData data) {
        super(ModMenus.ASSEMBLER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(AssemblerBlockEntity.SLOT_COUNT);

        for (int index = 0; index < AssemblerBlockEntity.INPUT_COUNT; index++) {
            Rect rect = AssemblerLayout.inputSlot(index);
            this.addSlot(new SlotItemHandler(machineInventory,
                    AssemblerBlockEntity.INPUT_START + index, rect.x(), rect.y()));
        }
        Rect output = AssemblerLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, AssemblerBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect motor = AssemblerLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, AssemblerBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "assembler")));
        Rect battery = AssemblerLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, AssemblerBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "assembler")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = AssemblerLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = AssemblerLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable AssemblerBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(AssemblerBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static AssemblerBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof AssemblerBlockEntity assembler
                ? assembler : null;
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
        int machineSlots = AssemblerBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ModItems.MOTOR.get())) {
            moved = this.moveItemStackTo(stack, AssemblerBlockEntity.MOTOR_SLOT,
                    AssemblerBlockEntity.MOTOR_SLOT + 1, false);
        } else if (stack.is(ModItems.BATTERY.get())) {
            moved = this.moveItemStackTo(stack, AssemblerBlockEntity.BATTERY_SLOT,
                    AssemblerBlockEntity.BATTERY_SLOT + 1, false);
        } else {
            moved = this.moveItemStackTo(stack, AssemblerBlockEntity.INPUT_START,
                    AssemblerBlockEntity.OUTPUT_SLOT, false);
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

    public int getEnergyStored() {
        return this.data.get(AssemblerBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(AssemblerBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(AssemblerBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(AssemblerBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(AssemblerBlockEntity.DATA_TIME));
    }

    public int getMotorCount() {
        ItemStack stack = this.slots.get(AssemblerBlockEntity.MOTOR_SLOT).getItem();
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = this.slots.get(AssemblerBlockEntity.BATTERY_SLOT).getItem();
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public AssemblerBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
