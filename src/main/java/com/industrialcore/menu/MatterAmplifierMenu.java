package com.industrialcore.menu;

import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineFaceConfig;
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
 * Menu of the Phase 21 matter amplifier.
 *
 * <p>Four machine slots (billet, crystal, ore output, dust output). Shift-click only moves items
 * into the billet or the crystal slot, so a transfer can never fill an output slot and duplicate a
 * finished run.</p>
 */
public class MatterAmplifierMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return MatterAmplifierBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final MatterAmplifierBlockEntity blockEntity;

    public MatterAmplifierMenu(int containerId, Inventory playerInventory,
                               RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public MatterAmplifierMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public MatterAmplifierMenu(int containerId, Inventory playerInventory,
                               MatterAmplifierBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private MatterAmplifierMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                @Nullable MatterAmplifierBlockEntity blockEntity,
                                @Nullable ContainerData data) {
        super(ModMenus.MATTER_AMPLIFIER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(MatterAmplifierBlockEntity.SLOT_COUNT);

        Rect input = MatterAmplifierLayout.INPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, MatterAmplifierBlockEntity.INPUT_SLOT,
                input.x(), input.y()));
        Rect catalyst = MatterAmplifierLayout.CATALYST_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, MatterAmplifierBlockEntity.CATALYST_SLOT,
                catalyst.x(), catalyst.y()));
        Rect output = MatterAmplifierLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, MatterAmplifierBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect dust = MatterAmplifierLayout.DUST_OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, MatterAmplifierBlockEntity.DUST_OUTPUT_SLOT,
                dust.x(), dust.y()));
        Rect motor = MatterAmplifierLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, MatterAmplifierBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "matter_amplifier")));
        Rect battery = MatterAmplifierLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, MatterAmplifierBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "matter_amplifier")));

        for (int index = 0; index < 36; index++) {
            Rect rect = MatterAmplifierLayout.playerSlot(index);
            int playerSlot = index < 27 ? index + 9 : index - 27;
            this.addSlot(new Slot(playerInventory, playerSlot, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable MatterAmplifierBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(MatterAmplifierBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static MatterAmplifierBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof MatterAmplifierBlockEntity machine
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
        int machineSlots = MatterAmplifierBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                moved = this.moveItemStackTo(stack, MatterAmplifierBlockEntity.MOTOR_SLOT,
                        MatterAmplifierBlockEntity.MOTOR_SLOT + 1, false);
            } else if (stack.is(ModItems.BATTERY.get())) {
                moved = this.moveItemStackTo(stack, MatterAmplifierBlockEntity.BATTERY_SLOT,
                        MatterAmplifierBlockEntity.BATTERY_SLOT + 1, false);
            } else {
                moved = this.moveItemStackTo(stack, MatterAmplifierBlockEntity.INPUT_SLOT,
                        MatterAmplifierBlockEntity.CATALYST_SLOT + 1, false);
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
        return this.data.get(MatterAmplifierBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(MatterAmplifierBlockEntity.DATA_CAPACITY);
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(MatterAmplifierBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(MatterAmplifierBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(MatterAmplifierBlockEntity.DATA_TIME));
    }

    /** Per-face settings (Phase 11.3), synced through the block entity update tag. */
    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public MatterAmplifierBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public int getMotorCount() {
        return this.slots.size() > MatterAmplifierBlockEntity.MOTOR_SLOT
                ? this.slots.get(MatterAmplifierBlockEntity.MOTOR_SLOT).getItem().getCount() : 0;
    }

    public int getBatteryCount() {
        return this.slots.size() > MatterAmplifierBlockEntity.BATTERY_SLOT
                ? this.slots.get(MatterAmplifierBlockEntity.BATTERY_SLOT).getItem().getCount() : 0;
    }
}
