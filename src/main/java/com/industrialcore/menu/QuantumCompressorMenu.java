package com.industrialcore.menu;

import com.industrialcore.blockentity.QuantumCompressorBlockEntity;
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
 * Menu of the Phase 25 quantum compressor.
 *
 * <p>One input slot and four output slots. Shift-click moves the machine contents into the player
 * inventory and only ever moves player items into the input slot, so the output slots can never be
 * filled from the outside - the same contract as the other dedicated machines.</p>
 */
public class QuantumCompressorMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return QuantumCompressorBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final QuantumCompressorBlockEntity blockEntity;

    public QuantumCompressorMenu(int containerId, Inventory playerInventory,
                                 RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public QuantumCompressorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public QuantumCompressorMenu(int containerId, Inventory playerInventory,
                                 QuantumCompressorBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private QuantumCompressorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                  @Nullable QuantumCompressorBlockEntity blockEntity,
                                  @Nullable ContainerData data) {
        super(ModMenus.QUANTUM_COMPRESSOR.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(QuantumCompressorBlockEntity.SLOT_COUNT);

        Rect input = QuantumCompressorLayout.INPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory,
                QuantumCompressorBlockEntity.INPUT_SLOT, input.x(), input.y()));
        for (int index = 0; index < QuantumCompressorBlockEntity.OUTPUT_COUNT; index++) {
            Rect rect = QuantumCompressorLayout.outputSlot(index);
            this.addSlot(new SlotItemHandler(machineInventory,
                    QuantumCompressorBlockEntity.OUTPUT_START + index, rect.x(), rect.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // The results are read-only for players; automation uses the item handler.
                    return false;
                }
            });
        }
        Rect motor = QuantumCompressorLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory,
                QuantumCompressorBlockEntity.MOTOR_SLOT, motor.x(), motor.y(), ModItems.MOTOR::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity,
                        "quantum_compressor")));
        Rect battery = QuantumCompressorLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory,
                QuantumCompressorBlockEntity.BATTERY_SLOT, battery.x(), battery.y(),
                ModItems.BATTERY::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity,
                        "quantum_compressor")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = QuantumCompressorLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = QuantumCompressorLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable QuantumCompressorBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(QuantumCompressorBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static QuantumCompressorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level()
                .getBlockEntity(pos) instanceof QuantumCompressorBlockEntity compressor
                ? compressor : null;
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
        int machineSlots = QuantumCompressorBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ModItems.MOTOR.get())) {
            moved = this.moveItemStackTo(stack, QuantumCompressorBlockEntity.MOTOR_SLOT,
                    QuantumCompressorBlockEntity.MOTOR_SLOT + 1, false);
        } else if (stack.is(ModItems.BATTERY.get())) {
            moved = this.moveItemStackTo(stack, QuantumCompressorBlockEntity.BATTERY_SLOT,
                    QuantumCompressorBlockEntity.BATTERY_SLOT + 1, false);
        } else {
            moved = this.moveItemStackTo(stack, QuantumCompressorBlockEntity.INPUT_SLOT,
                    QuantumCompressorBlockEntity.INPUT_SLOT + 1, false);
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
        return this.data.get(QuantumCompressorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(QuantumCompressorBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(QuantumCompressorBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(QuantumCompressorBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(QuantumCompressorBlockEntity.DATA_TIME));
    }

    /** Items the running cycle processes, {@code 0} while idle. */
    public int getBatch() {
        return this.data.get(QuantumCompressorBlockEntity.DATA_BATCH);
    }

    /** Average FE per tick of the running cycle. */
    public int getEnergyPerTick() {
        return this.data.get(QuantumCompressorBlockEntity.DATA_COST);
    }

    public int getMotorCount() {
        ItemStack stack = this.slots.get(QuantumCompressorBlockEntity.MOTOR_SLOT).getItem();
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = this.slots.get(QuantumCompressorBlockEntity.BATTERY_SLOT).getItem();
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public QuantumCompressorBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
