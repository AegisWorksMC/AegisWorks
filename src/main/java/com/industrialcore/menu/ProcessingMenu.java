package com.industrialcore.menu;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Shared menu of the crusher, electric furnace, electrolyzer and precision crusher: one input
 * slot, one output slot, the energy and progress data, then the player inventory.
 *
 * <p>Shift-click moves machine contents into the player inventory and only ever moves player
 * items into the single input slot, so the result slot can never be filled and no item can be
 * duplicated by a transfer.</p>
 */
public abstract class ProcessingMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ProcessingBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final ProcessingBlockEntity blockEntity;

    protected ProcessingMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                             RegistryFriendlyByteBuf buffer) {
        this(type, containerId, playerInventory, buffer.readBlockPos());
    }

    protected ProcessingMenu(MenuType<?> type, int containerId, Inventory playerInventory, BlockPos pos) {
        this(type, containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    protected ProcessingMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                             ProcessingBlockEntity blockEntity, ContainerData data) {
        this(type, containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private ProcessingMenu(MenuType<?> type, int containerId, Inventory playerInventory, BlockPos pos,
                           @Nullable ProcessingBlockEntity blockEntity, @Nullable ContainerData data) {
        super(type, containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(ProcessingBlockEntity.SLOT_COUNT);

        Rect input = ProcessingLayout.INPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ProcessingBlockEntity.INPUT_SLOT,
                input.x(), input.y()));
        Rect output = ProcessingLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ProcessingBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        String machineId = blockEntity != null ? blockEntity.getKind().id() : "crusher";
        Rect motor = ProcessingLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, ProcessingBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, machineId)));
        Rect battery = ProcessingLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, ProcessingBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, machineId)));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = ProcessingLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = ProcessingLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable ProcessingBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(ProcessingBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static ProcessingBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ProcessingBlockEntity machine
                ? machine : null;
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
        int machineSlots = ProcessingBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                moved = this.moveItemStackTo(stack, ProcessingBlockEntity.MOTOR_SLOT,
                        ProcessingBlockEntity.MOTOR_SLOT + 1, false);
            } else if (stack.is(ModItems.BATTERY.get())) {
                moved = this.moveItemStackTo(stack, ProcessingBlockEntity.BATTERY_SLOT,
                        ProcessingBlockEntity.BATTERY_SLOT + 1, false);
            } else {
                moved = this.moveItemStackTo(stack, ProcessingBlockEntity.INPUT_SLOT,
                        ProcessingBlockEntity.INPUT_SLOT + 1, false);
            }
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
        return this.data.get(ProcessingBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(ProcessingBlockEntity.DATA_CAPACITY);
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(ProcessingBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(ProcessingBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(ProcessingBlockEntity.DATA_TIME));
    }

    /** Phase 11.6: per-face settings, synced through the block entity update tag. */
    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public ProcessingBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public int getMotorCount() {
        return this.slots.size() > ProcessingBlockEntity.MOTOR_SLOT
                ? this.slots.get(ProcessingBlockEntity.MOTOR_SLOT).getItem().getCount() : 0;
    }

    public int getBatteryCount() {
        return this.slots.size() > ProcessingBlockEntity.BATTERY_SLOT
                ? this.slots.get(ProcessingBlockEntity.BATTERY_SLOT).getItem().getCount() : 0;
    }
}
