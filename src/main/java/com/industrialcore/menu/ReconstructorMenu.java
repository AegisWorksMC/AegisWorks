package com.industrialcore.menu;

import com.industrialcore.blockentity.ReconstructorBlockEntity;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Menu of the Phase 15 matter reconstructor.
 *
 * <p>The menu shows the four machine slots (analysis sample, material, crystal, output), the
 * energy and progress data, the unlocked minerals and the per-face settings of Phase 11.3.</p>
 *
 * <p>Shift-click moves machine contents into the player inventory. Player items only ever go
 * into the material or the crystal slot, never into the analysis slot (only the analyse button
 * consumes a sample) and never into the output slot, so a transfer can neither duplicate items
 * nor unlock a mineral by accident.</p>
 */
public class ReconstructorMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ReconstructorBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final ReconstructorBlockEntity blockEntity;

    public ReconstructorMenu(int containerId, Inventory playerInventory,
                              RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public ReconstructorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public ReconstructorMenu(int containerId, Inventory playerInventory,
                              ReconstructorBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private ReconstructorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                              @Nullable ReconstructorBlockEntity blockEntity,
                              @Nullable ContainerData data) {
        super(ModMenus.RECONSTRUCTOR.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(ReconstructorBlockEntity.SLOT_COUNT);

        Rect analysis = ReconstructorLayout.ANALYSIS_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ReconstructorBlockEntity.ANALYSIS_SLOT,
                analysis.x(), analysis.y()));
        Rect material = ReconstructorLayout.MATERIAL_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ReconstructorBlockEntity.MATERIAL_SLOT,
                material.x(), material.y()));
        Rect crystal = ReconstructorLayout.CRYSTAL_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ReconstructorBlockEntity.CRYSTAL_SLOT,
                crystal.x(), crystal.y()));
        Rect output = ReconstructorLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ReconstructorBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect motor = ReconstructorLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, ReconstructorBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "reconstructor")));
        Rect battery = ReconstructorLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, ReconstructorBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "reconstructor")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = ReconstructorLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = ReconstructorLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable ReconstructorBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(ReconstructorBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static ReconstructorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ReconstructorBlockEntity machine
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
        int machineSlots = ReconstructorBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                moved = this.moveItemStackTo(stack, ReconstructorBlockEntity.MOTOR_SLOT,
                        ReconstructorBlockEntity.MOTOR_SLOT + 1, false);
            } else if (stack.is(ModItems.BATTERY.get())) {
                moved = this.moveItemStackTo(stack, ReconstructorBlockEntity.BATTERY_SLOT,
                        ReconstructorBlockEntity.BATTERY_SLOT + 1, false);
            } else {
                // Only the material and the crystal slot accept player items; the analysis slot is
                // filled by hand and the output slot is never filled from the outside.
                moved = this.moveItemStackTo(stack, ReconstructorBlockEntity.MATERIAL_SLOT,
                        ReconstructorBlockEntity.CRYSTAL_SLOT + 1, false);
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
        return this.data.get(ReconstructorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(ReconstructorBlockEntity.DATA_CAPACITY);
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(ReconstructorBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(ReconstructorBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(ReconstructorBlockEntity.DATA_TIME));
    }

    /** Per-face settings (Phase 11.3), synced through the block entity update tag. */
    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    /** Unlocked minerals in the order they were unlocked. */
    public List<String> getUnlockedMinerals() {
        return this.blockEntity == null ? List.of() : this.blockEntity.getUnlockedMinerals();
    }

    /** The selected mineral, empty when nothing is selected. */
    public String getSelectedMineral() {
        return this.blockEntity == null ? "" : this.blockEntity.getSelectedMineral();
    }

    /** The sample in the analysis slot. */
    public ItemStack getSample() {
        Slot slot = this.slots.get(ReconstructorBlockEntity.ANALYSIS_SLOT);
        return slot == null ? ItemStack.EMPTY : slot.getItem();
    }

    /**
     * True when the analyse button may be pressed: the sample belongs to a mineral that is not
     * unlocked yet.
     */
    public boolean canAnalyze() {
        if (this.blockEntity == null) {
            return false;
        }
        return this.blockEntity.canAnalyse(getSample());
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    public int getMotorCount() {
        return this.slots.size() > ReconstructorBlockEntity.MOTOR_SLOT
                ? this.slots.get(ReconstructorBlockEntity.MOTOR_SLOT).getItem().getCount() : 0;
    }

    public int getBatteryCount() {
        return this.slots.size() > ReconstructorBlockEntity.BATTERY_SLOT
                ? this.slots.get(ReconstructorBlockEntity.BATTERY_SLOT).getItem().getCount() : 0;
    }

    @Nullable
    public ReconstructorBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
