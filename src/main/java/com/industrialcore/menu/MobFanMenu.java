package com.industrialcore.menu;

import com.industrialcore.blockentity.MobFanBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.trap.TrapLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Phase 92: Menu for the 6-direction Mob Movement Fan ({@code mob_fan}).
 */
public class MobFanMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = MobFanBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final MobFanBlockEntity blockEntity;
    private final ContainerData data;

    public MobFanMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public MobFanMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(MobFanBlockEntity.DATA_COUNT));
    }

    public MobFanMenu(int containerId, Inventory playerInventory,
                      MobFanBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private MobFanMenu(int containerId, Inventory playerInventory, BlockPos pos,
                       @Nullable MobFanBlockEntity be, ContainerData data) {
        super(ModMenus.MOB_FAN.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(MobFanBlockEntity.SLOT_COUNT);

        Rect mr = TrapLayout.FAN_MOTOR_SLOT;
        this.addSlot(new SlotItemHandler(inv, MobFanBlockEntity.MOTOR_SLOT, mr.x(), mr.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MOTOR.get());
            }

            @Override
            public int getMaxStackSize() {
                return upgradeLimit();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit();
            }
        });

        Rect br = TrapLayout.FAN_BATTERY_SLOT;
        this.addSlot(new SlotItemHandler(inv, MobFanBlockEntity.BATTERY_SLOT, br.x(), br.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BATTERY.get());
            }

            @Override
            public int getMaxStackSize() {
                return upgradeLimit();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit();
            }
        });

        int pIdx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Rect r = TrapLayout.fanPlayerSlot(pIdx++);
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = TrapLayout.fanPlayerSlot(pIdx++);
            this.addSlot(new Slot(playerInventory, col, r.x(), r.y()));
        }

        this.addDataSlots(data);
    }

    @Nullable
    private static MobFanBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof MobFanBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, MobFanBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getEnergyStored() {
        return data.get(MobFanBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(MobFanBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(MobFanBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public Direction getFacing() {
        Direction[] dirs = Direction.values();
        int ord = data.get(MobFanBlockEntity.DATA_FACING);
        return (ord >= 0 && ord < dirs.length) ? dirs[ord] : Direction.NORTH;
    }

    public int getRangeBlocks() {
        return data.get(MobFanBlockEntity.DATA_RANGE);
    }

    public int getPowerPercent() {
        return data.get(MobFanBlockEntity.DATA_POWER_PCT);
    }

    public int getTargetsCount() {
        return data.get(MobFanBlockEntity.DATA_TARGETS);
    }

    public int getMotorCount() {
        return this.slots.get(MobFanBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(MobFanBlockEntity.BATTERY_SLOT).getItem().getCount();
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
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                if (!this.moveItemStackTo(stack, MobFanBlockEntity.MOTOR_SLOT,
                        MobFanBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, MobFanBlockEntity.BATTERY_SLOT,
                        MobFanBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
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
        if (blockEntity != null) {
            return !blockEntity.isRemoved()
                    && Vec3.atCenterOf(pos).distanceToSqr(p.position()) <= 64.0D;
        }
        return p.level().isClientSide && Vec3.atCenterOf(pos).distanceToSqr(p.position()) <= 64.0D;
    }
}
