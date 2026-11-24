package com.industrialcore.menu;

import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.MachineFaceConfig;
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

/** Menu for the auto crafter: 3x3 inputs, one output, then the player inventory. */
public class CrafterMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return CrafterBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final CrafterBlockEntity blockEntity;

    public CrafterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public CrafterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public CrafterMenu(int containerId, Inventory playerInventory, CrafterBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private CrafterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                        @Nullable CrafterBlockEntity blockEntity, @Nullable ContainerData data) {
        super(ModMenus.CRAFTER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(CrafterBlockEntity.SLOT_COUNT);

        for (int index = 0; index < CrafterBlockEntity.INPUT_SLOTS; index++) {
            Rect rect = CrafterLayout.inputSlot(index);
            this.addSlot(new SlotItemHandler(machineInventory, index, rect.x(), rect.y()));
        }
        Rect output = CrafterLayout.OUTPUT_SLOT;
        this.addSlot(new SlotItemHandler(machineInventory, CrafterBlockEntity.OUTPUT_SLOT,
                output.x(), output.y()));
        Rect upgrade = CrafterLayout.UPGRADE_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, CrafterBlockEntity.UPGRADE_SLOT,
                upgrade.x(), upgrade.y(), ModItems.MOTOR,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "crafter")));
        Rect battery = CrafterLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory, CrafterBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), ModItems.BATTERY,
                () -> UpgradeCapBridge.effectiveLimit(playerInventory.player, blockEntity, "crafter")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = CrafterLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = CrafterLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory, @Nullable CrafterBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(CrafterBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static CrafterBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof CrafterBlockEntity crafter ? crafter : null;
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
        int machineSlots = CrafterBlockEntity.SLOT_COUNT;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            // Motors and batteries go to their dedicated upgrade slots first; if full they fall back to inputs.
            if (stack.is(ModItems.MOTOR.get())) {
                moved = this.moveItemStackTo(stack, CrafterBlockEntity.UPGRADE_SLOT,
                        CrafterBlockEntity.UPGRADE_SLOT + 1, false);
            } else if (stack.is(ModItems.BATTERY.get())) {
                moved = this.moveItemStackTo(stack, CrafterBlockEntity.BATTERY_SLOT,
                        CrafterBlockEntity.BATTERY_SLOT + 1, false);
            } else {
                moved = false;
            }
            if (!moved) {
                moved = this.moveItemStackTo(stack, 0, CrafterBlockEntity.INPUT_SLOTS, false);
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
        return this.data.get(CrafterBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(CrafterBlockEntity.DATA_CAPACITY);
    }

    public int getBatteryCount() {
        return this.slots.size() > CrafterBlockEntity.BATTERY_SLOT
                ? this.slots.get(CrafterBlockEntity.BATTERY_SLOT).getItem().getCount() : 0;
    }

    public CrafterStatus getStatus() {
        CrafterStatus[] values = CrafterStatus.values();
        int ordinal = this.data.get(CrafterBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getProgress() {
        return this.data.get(CrafterBlockEntity.DATA_PROGRESS);
    }

    public int getCraftTime() {
        return Math.max(1, this.data.get(CrafterBlockEntity.DATA_CRAFT_TIME));
    }

    public int getSpeedLevel() {
        return this.data.get(CrafterBlockEntity.DATA_SPEED_LEVEL);
    }

    public int getMaxSpeedLevel() {
        int max = this.data.get(CrafterBlockEntity.DATA_MAX_SPEED_LEVEL);
        return max > 0 ? max : 4;
    }

    public String getSelectedRecipeId() {
        return this.blockEntity == null ? "" : this.blockEntity.getSelectedRecipeId();
    }

    /** Phase 11.3: per-face settings, synced through the block entity update tag. */
    public MachineFaceConfig getFaceConfig() {
        return this.blockEntity == null ? new MachineFaceConfig() : this.blockEntity.getFaceConfig();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public CrafterBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}
