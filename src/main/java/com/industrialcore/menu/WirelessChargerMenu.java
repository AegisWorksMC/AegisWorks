package com.industrialcore.menu;

import com.industrialcore.blockentity.WirelessChargerBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 94: Menu for the Wireless Charger ({@code wireless_charger}). */
public class WirelessChargerMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = WirelessChargerBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final WirelessChargerBlockEntity blockEntity;
    private final ContainerData data;

    public WirelessChargerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public WirelessChargerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(WirelessChargerBlockEntity.DATA_COUNT));
    }

    public WirelessChargerMenu(int containerId, Inventory playerInventory,
                               WirelessChargerBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private WirelessChargerMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                @Nullable WirelessChargerBlockEntity be, ContainerData data) {
        super(ModMenus.WIRELESS_CHARGER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(WirelessChargerBlockEntity.SLOT_COUNT);

        Rect mr = LifeLayout.CHARGER_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, WirelessChargerBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeLayout.CHARGER_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, WirelessChargerBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeLayout::chargerPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static WirelessChargerBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof WirelessChargerBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, WirelessChargerBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Nullable
    public WirelessChargerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return data.get(WirelessChargerBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(WirelessChargerBlockEntity.DATA_CAPACITY));
    }

    public int getChargerState() {
        return data.get(WirelessChargerBlockEntity.DATA_STATE);
    }

    public int getRadiusBlocks() {
        int r = data.get(WirelessChargerBlockEntity.DATA_RADIUS);
        return r > 0 ? r : LifeLogic.CHARGER_BASE_RADIUS;
    }

    public int getMaxRatePerTick() {
        int lo = data.get(WirelessChargerBlockEntity.DATA_RATE_LO) & 0xFFFF;
        int hi = data.get(WirelessChargerBlockEntity.DATA_RATE_HI) & 0xFFFF;
        int val = (hi << 16) | lo;
        return val > 0 ? val : LifeLogic.CHARGER_MIN_RATE;
    }

    public int getNearbyPlayers() {
        return Math.max(0, data.get(WirelessChargerBlockEntity.DATA_PLAYERS));
    }

    public long getTotalTransferred() {
        long w0 = data.get(WirelessChargerBlockEntity.DATA_TOTAL_0) & 0xFFFFL;
        long w1 = data.get(WirelessChargerBlockEntity.DATA_TOTAL_1) & 0xFFFFL;
        long w2 = data.get(WirelessChargerBlockEntity.DATA_TOTAL_2) & 0xFFFFL;
        return w0 | (w1 << 16) | (w2 << 32);
    }

    public int getMotorCount() {
        return this.slots.get(WirelessChargerBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(WirelessChargerBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, WirelessChargerBlockEntity.MOTOR_SLOT,
                        WirelessChargerBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, WirelessChargerBlockEntity.BATTERY_SLOT,
                        WirelessChargerBlockEntity.BATTERY_SLOT + 1, false)) {
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
        return XpCollectorMenu.isWithinReach(blockEntity, pos, p);
    }
}
