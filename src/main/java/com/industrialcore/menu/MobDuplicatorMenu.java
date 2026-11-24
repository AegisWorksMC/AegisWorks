package com.industrialcore.menu;

import com.industrialcore.blockentity.MobDuplicatorBlockEntity;
import com.industrialcore.item.MobCapsuleItem;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.trap.TrapWave2Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 93: Menu for the Mob Automatic Spawner ({@code mob_duplicator}). */
public class MobDuplicatorMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = MobDuplicatorBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final MobDuplicatorBlockEntity blockEntity;
    private final ContainerData data;

    public MobDuplicatorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public MobDuplicatorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(MobDuplicatorBlockEntity.DATA_COUNT));
    }

    public MobDuplicatorMenu(int containerId, Inventory playerInventory,
                             MobDuplicatorBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private MobDuplicatorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                              @Nullable MobDuplicatorBlockEntity be, ContainerData data) {
        super(ModMenus.MOB_DUPLICATOR.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(MobDuplicatorBlockEntity.SLOT_COUNT);

        Rect cr = TrapWave2Layout.DUP_CAPSULE_SLOT;
        this.addSlot(new SlotItemHandler(inv, MobDuplicatorBlockEntity.CAPSULE_SLOT, cr.x(), cr.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof MobCapsuleItem
                        && MobCapsuleItem.hasCapturedEntity(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        Rect mr = TrapWave2Layout.DUP_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, MobDuplicatorBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = TrapWave2Layout.DUP_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, MobDuplicatorBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, TrapWave2Layout::dupPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static MobDuplicatorBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof MobDuplicatorBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, MobDuplicatorBlockEntity.MACHINE_ID);
    }

    public int getEnergyStored() {
        return data.get(MobDuplicatorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(MobDuplicatorBlockEntity.DATA_CAPACITY));
    }

    public int getDupState() {
        return data.get(MobDuplicatorBlockEntity.DATA_STATE);
    }

    public int getProgress() {
        return data.get(MobDuplicatorBlockEntity.DATA_PROGRESS);
    }

    public int getTotalTime() {
        return Math.max(1, data.get(MobDuplicatorBlockEntity.DATA_TOTAL));
    }

    public int getBatchCount() {
        return Math.max(1, data.get(MobDuplicatorBlockEntity.DATA_BATCH));
    }

    public int getNearbyCount() {
        return data.get(MobDuplicatorBlockEntity.DATA_NEARBY);
    }

    public int getSpawnedTotal() {
        return data.get(MobDuplicatorBlockEntity.DATA_SPAWNED);
    }

    @Nullable
    public Component getCapturedMobName() {
        ItemStack stack = this.slots.get(MobDuplicatorBlockEntity.CAPSULE_SLOT).getItem();
        if (MobCapsuleItem.hasCapturedEntity(stack)) {
            return MobCapsuleItem.getCapturedDisplayName(stack);
        }
        return null;
    }

    public int getMotorCount() {
        return this.slots.get(MobDuplicatorBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(MobDuplicatorBlockEntity.BATTERY_SLOT).getItem().getCount();
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
        } else if (stack.getItem() instanceof MobCapsuleItem && MobCapsuleItem.hasCapturedEntity(stack)) {
            if (!this.moveItemStackTo(stack, MobDuplicatorBlockEntity.CAPSULE_SLOT,
                    MobDuplicatorBlockEntity.CAPSULE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MOTOR.get())) {
            if (!this.moveItemStackTo(stack, MobDuplicatorBlockEntity.MOTOR_SLOT,
                    MobDuplicatorBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, MobDuplicatorBlockEntity.BATTERY_SLOT,
                    MobDuplicatorBlockEntity.BATTERY_SLOT + 1, false)) {
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
