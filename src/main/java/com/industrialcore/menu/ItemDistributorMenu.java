package com.industrialcore.menu;

import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.machine.DistributorOutputState;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
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
 * Menu of the Phase 19 item distributor: the internal buffer slot, the face settings of the GUI
 * and the player inventory.
 */
public class ItemDistributorMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ItemDistributorBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final ItemDistributorBlockEntity blockEntity;

    public ItemDistributorMenu(int containerId, Inventory playerInventory,
                               RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public ItemDistributorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public ItemDistributorMenu(int containerId, Inventory playerInventory,
                               ItemDistributorBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private ItemDistributorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                @Nullable ItemDistributorBlockEntity blockEntity,
                                @Nullable ContainerData data) {
        super(ModMenus.ITEM_DISTRIBUTOR.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(ItemDistributorBlockEntity.SLOT_COUNT);
        Rect buffer = ItemDistributorLayout.BUFFER_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, ItemDistributorBlockEntity.BUFFER_SLOT,
                buffer.x(), buffer.y()));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = ItemDistributorLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = ItemDistributorLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable ItemDistributorBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(ItemDistributorBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static ItemDistributorBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ItemDistributorBlockEntity distributor
                ? distributor : null;
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
        int machineSlots = ItemDistributorBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            moved = this.moveItemStackTo(stack, 0, machineSlots, false);
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
        int ordinal = this.data.get(ItemDistributorBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    /** Side the next distribution pass starts at. */
    public int getNextSide() {
        return this.data.get(ItemDistributorBlockEntity.DATA_NEXT_SIDE);
    }

    public DistributorOutputState getOutputState(int side) {
        DistributorOutputState[] values = DistributorOutputState.values();
        int ordinal = this.data.get(ItemDistributorBlockEntity.DATA_OUTPUT_BASE + side);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public ItemDistributorBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
