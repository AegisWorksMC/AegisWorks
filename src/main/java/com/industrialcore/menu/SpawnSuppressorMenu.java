package com.industrialcore.menu;

import com.industrialcore.blockentity.SpawnSuppressorBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.trap.TrapWave2Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 93: Menu for the Spawn Suppressor Beacon ({@code spawn_suppressor}). */
public class SpawnSuppressorMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = SpawnSuppressorBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final SpawnSuppressorBlockEntity blockEntity;
    private final ContainerData data;

    public SpawnSuppressorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public SpawnSuppressorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(SpawnSuppressorBlockEntity.DATA_COUNT));
    }

    public SpawnSuppressorMenu(int containerId, Inventory playerInventory,
                               SpawnSuppressorBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private SpawnSuppressorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                @Nullable SpawnSuppressorBlockEntity be, ContainerData data) {
        super(ModMenus.SPAWN_SUPPRESSOR.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(SpawnSuppressorBlockEntity.SLOT_COUNT);

        Rect mr = TrapWave2Layout.SUP_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, SpawnSuppressorBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = TrapWave2Layout.SUP_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, SpawnSuppressorBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, TrapWave2Layout::supPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static SpawnSuppressorBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof SpawnSuppressorBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, SpawnSuppressorBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getEnergyStored() {
        return data.get(SpawnSuppressorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(SpawnSuppressorBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(SpawnSuppressorBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public int getRadiusBlocks() {
        return data.get(SpawnSuppressorBlockEntity.DATA_RADIUS);
    }

    public int getEnergyPerTick() {
        return data.get(SpawnSuppressorBlockEntity.DATA_FE_PER_TICK);
    }

    public int getBlockedTotal() {
        return data.get(SpawnSuppressorBlockEntity.DATA_BLOCKED);
    }

    public int getMotorCount() {
        return this.slots.get(SpawnSuppressorBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(SpawnSuppressorBlockEntity.BATTERY_SLOT).getItem().getCount();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MOTOR.get())) {
            if (!this.moveItemStackTo(stack, SpawnSuppressorBlockEntity.MOTOR_SLOT,
                    SpawnSuppressorBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, SpawnSuppressorBlockEntity.BATTERY_SLOT,
                    SpawnSuppressorBlockEntity.BATTERY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(p, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player p) {
        return XpCollectorMenu.isWithinReach(blockEntity, pos, p);
    }
}
