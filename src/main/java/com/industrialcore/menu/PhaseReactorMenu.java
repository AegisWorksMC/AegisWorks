package com.industrialcore.menu;

import com.industrialcore.blockentity.PhaseReactorBlockEntity;
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
 * Menu of the Phase 24 phase reactor.
 *
 * <p>Three interchangeable ingredient slots and one result slot. Shift-click moves the machine
 * contents into the player inventory and only ever moves player items into the ingredient slots,
 * so the result slot can never be filled from the outside - the same contract as the assembler.</p>
 */
public class PhaseReactorMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return PhaseReactorBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final PhaseReactorBlockEntity blockEntity;

    public PhaseReactorMenu(int containerId, Inventory playerInventory,
                            RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public PhaseReactorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public PhaseReactorMenu(int containerId, Inventory playerInventory,
                            PhaseReactorBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private PhaseReactorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                             @Nullable PhaseReactorBlockEntity blockEntity,
                             @Nullable ContainerData data) {
        super(ModMenus.PHASE_REACTOR.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(PhaseReactorBlockEntity.SLOT_COUNT);

        for (int index = 0; index < PhaseReactorBlockEntity.INPUT_COUNT; index++) {
            Rect rect = PhaseReactorLayout.inputSlot(index);
            this.addSlot(new SlotItemHandler(machineInventory,
                    PhaseReactorBlockEntity.INPUT_START + index, rect.x(), rect.y()));
        }
        Rect output = PhaseReactorLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, PhaseReactorBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect motor = PhaseReactorLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, PhaseReactorBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "phase_reactor")));
        Rect battery = PhaseReactorLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, PhaseReactorBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "phase_reactor")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = PhaseReactorLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = PhaseReactorLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable PhaseReactorBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(PhaseReactorBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static PhaseReactorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof PhaseReactorBlockEntity reactor
                ? reactor : null;
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
        int machineSlots = PhaseReactorBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ModItems.MOTOR.get())) {
            moved = this.moveItemStackTo(stack, PhaseReactorBlockEntity.MOTOR_SLOT,
                    PhaseReactorBlockEntity.MOTOR_SLOT + 1, false);
        } else if (stack.is(ModItems.BATTERY.get())) {
            moved = this.moveItemStackTo(stack, PhaseReactorBlockEntity.BATTERY_SLOT,
                    PhaseReactorBlockEntity.BATTERY_SLOT + 1, false);
        } else {
            moved = this.moveItemStackTo(stack, PhaseReactorBlockEntity.INPUT_START,
                    PhaseReactorBlockEntity.OUTPUT_SLOT, false);
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
        return this.data.get(PhaseReactorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(PhaseReactorBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(PhaseReactorBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(PhaseReactorBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(PhaseReactorBlockEntity.DATA_TIME));
    }

    public int getMotorCount() {
        ItemStack stack = this.slots.get(PhaseReactorBlockEntity.MOTOR_SLOT).getItem();
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = this.slots.get(PhaseReactorBlockEntity.BATTERY_SLOT).getItem();
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public PhaseReactorBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
