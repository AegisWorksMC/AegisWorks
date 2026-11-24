package com.industrialcore.menu;

import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Menu of the synthesizer: three ingredient slots, one output slot and the FE/progress readout. */
public class OreSynthesizerMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return OreSynthesizerBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final OreSynthesizerBlockEntity blockEntity;

    public OreSynthesizerMenu(int containerId, Inventory playerInventory,
                              RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public OreSynthesizerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public OreSynthesizerMenu(int containerId, Inventory playerInventory,
                              OreSynthesizerBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private OreSynthesizerMenu(int containerId, Inventory playerInventory, BlockPos pos,
                               @Nullable OreSynthesizerBlockEntity blockEntity,
                               @Nullable ContainerData data) {
        super(ModMenus.ORE_SYNTHESIZER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        ItemStackHandler inventory = blockEntity != null ? blockEntity.getInventory()
                : new ItemStackHandler(OreSynthesizerBlockEntity.SLOT_COUNT);
        for (int index = 0; index < OreSynthesizerBlockEntity.INPUT_COUNT; index++) {
            Rect rect = OreSynthesizerLayout.inputSlot(index);
            this.addSlot(new SlotItemHandler(inventory, index, rect.x(), rect.y()));
        }
        this.addSlot(new SlotItemHandler(inventory, OreSynthesizerBlockEntity.OUTPUT_SLOT,
                OreSynthesizerLayout.OUTPUT_SLOT.x(), OreSynthesizerLayout.OUTPUT_SLOT.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        Rect motor = OreSynthesizerLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inventory, OreSynthesizerBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity,
                        "ore_synthesizer")));
        Rect battery = OreSynthesizerLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inventory, OreSynthesizerBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY::get,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity,
                        "ore_synthesizer")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = OreSynthesizerLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = OreSynthesizerLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable OreSynthesizerBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(OreSynthesizerBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static OreSynthesizerBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos)
                instanceof OreSynthesizerBlockEntity machine ? machine : null;
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
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = OreSynthesizerBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MOTOR.get())) {
            if (!this.moveItemStackTo(stack, OreSynthesizerBlockEntity.MOTOR_SLOT,
                    OreSynthesizerBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, OreSynthesizerBlockEntity.BATTERY_SLOT,
                    OreSynthesizerBlockEntity.BATTERY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, OreSynthesizerBlockEntity.INPUT_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    public int getEnergyStored() {
        return this.data.get(OreSynthesizerBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(OreSynthesizerBlockEntity.DATA_CAPACITY));
    }

    public int getProgress() {
        return this.data.get(OreSynthesizerBlockEntity.DATA_PROGRESS);
    }

    public int getTime() {
        return Math.max(1, this.data.get(OreSynthesizerBlockEntity.DATA_TIME));
    }

    public int getStatusOrdinal() {
        return this.data.get(OreSynthesizerBlockEntity.DATA_STATUS);
    }

    public int getMotorCount() {
        ItemStack stack = this.slots.get(OreSynthesizerBlockEntity.MOTOR_SLOT).getItem();
        return stack.is(ModItems.MOTOR.get()) ? stack.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack stack = this.slots.get(OreSynthesizerBlockEntity.BATTERY_SLOT).getItem();
        return stack.is(ModItems.BATTERY.get()) ? stack.getCount() : 0;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public OreSynthesizerBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
