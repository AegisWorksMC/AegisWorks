package com.industrialcore.menu;

import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
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
 * Menu of the Phase 21 seed cultivator.
 *
 * <p>Three machine slots (seed, feed, output), the energy and progress data plus the batch numbers
 * the screen explains to the player (how many billets are growing and how much dust the next
 * instalment needs). Shift-click only ever moves items into the seed or the feed slot, never into
 * the output.</p>
 */
public class SeedCultivatorMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return SeedCultivatorBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final SeedCultivatorBlockEntity blockEntity;

    public SeedCultivatorMenu(int containerId, Inventory playerInventory,
                              RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public SeedCultivatorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public SeedCultivatorMenu(int containerId, Inventory playerInventory,
                              SeedCultivatorBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private SeedCultivatorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                               @Nullable SeedCultivatorBlockEntity blockEntity,
                               @Nullable ContainerData data) {
        super(ModMenus.SEED_CULTIVATOR.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(SeedCultivatorBlockEntity.SLOT_COUNT);

        Rect seed = SeedCultivatorLayout.SEED_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, SeedCultivatorBlockEntity.SEED_SLOT,
                seed.x(), seed.y()));
        Rect feed = SeedCultivatorLayout.FEED_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, SeedCultivatorBlockEntity.FEED_SLOT,
                feed.x(), feed.y()));
        Rect output = SeedCultivatorLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, SeedCultivatorBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect motor = SeedCultivatorLayout.MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, SeedCultivatorBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), ModItems.MOTOR,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "seed_cultivator")));
        Rect battery = SeedCultivatorLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, SeedCultivatorBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "seed_cultivator")));

        for (int index = 0; index < 36; index++) {
            Rect rect = SeedCultivatorLayout.playerSlot(index);
            int playerSlot = index < 27 ? index + 9 : index - 27;
            this.addSlot(new Slot(playerInventory, playerSlot, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable SeedCultivatorBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(SeedCultivatorBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static SeedCultivatorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof SeedCultivatorBlockEntity machine
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
        int machineSlots = SeedCultivatorBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                moved = this.moveItemStackTo(stack, SeedCultivatorBlockEntity.MOTOR_SLOT,
                        SeedCultivatorBlockEntity.MOTOR_SLOT + 1, false);
            } else if (stack.is(ModItems.BATTERY.get())) {
                moved = this.moveItemStackTo(stack, SeedCultivatorBlockEntity.BATTERY_SLOT,
                        SeedCultivatorBlockEntity.BATTERY_SLOT + 1, false);
            } else {
                moved = this.moveItemStackTo(stack, SeedCultivatorBlockEntity.SEED_SLOT,
                        SeedCultivatorBlockEntity.FEED_SLOT + 1, false);
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
        return this.data.get(SeedCultivatorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(SeedCultivatorBlockEntity.DATA_CAPACITY);
    }

    public MachineStatus getStatus() {
        MachineStatus[] values = MachineStatus.values();
        int ordinal = this.data.get(SeedCultivatorBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(SeedCultivatorBlockEntity.DATA_PROGRESS);
    }

    public int getOperationTime() {
        return Math.max(1, this.data.get(SeedCultivatorBlockEntity.DATA_TIME));
    }

    /** Billets of the running batch, 0 while idle. */
    public int getBatch() {
        return this.data.get(SeedCultivatorBlockEntity.DATA_BATCH);
    }

    /** Dust still to be paid for the running batch. */
    public int getFeedLeft() {
        return this.data.get(SeedCultivatorBlockEntity.DATA_FEED_LEFT);
    }

    /** Per-face settings (Phase 11.3), synced through the block entity update tag. */
    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public SeedCultivatorBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public int getMotorCount() {
        return this.slots.size() > SeedCultivatorBlockEntity.MOTOR_SLOT
                ? this.slots.get(SeedCultivatorBlockEntity.MOTOR_SLOT).getItem().getCount() : 0;
    }

    public int getBatteryCount() {
        return this.slots.size() > SeedCultivatorBlockEntity.BATTERY_SLOT
                ? this.slots.get(SeedCultivatorBlockEntity.BATTERY_SLOT).getItem().getCount() : 0;
    }
}
