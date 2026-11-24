package com.industrialcore.menu;

import com.industrialcore.blockentity.ClimateControllerBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.life.LifeWave2Layout;
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

/** Phase 95: Menu for the Climate &amp; Time Controller ({@code climate_controller}). */
public class ClimateControllerMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = ClimateControllerBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final ClimateControllerBlockEntity blockEntity;
    private final ContainerData data;

    public ClimateControllerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public ClimateControllerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(ClimateControllerBlockEntity.DATA_COUNT));
    }

    public ClimateControllerMenu(int containerId, Inventory playerInventory,
                                 ClimateControllerBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private ClimateControllerMenu(int containerId, Inventory playerInventory, BlockPos pos,
                                  @Nullable ClimateControllerBlockEntity be, ContainerData data) {
        super(ModMenus.CLIMATE_CONTROLLER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(ClimateControllerBlockEntity.SLOT_COUNT);

        Rect mr = LifeWave2Layout.CLIMATE_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, ClimateControllerBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeWave2Layout.CLIMATE_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, ClimateControllerBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeWave2Layout::climatePlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static ClimateControllerBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof ClimateControllerBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, ClimateControllerBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getEnergyStored() {
        return data.get(ClimateControllerBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(ClimateControllerBlockEntity.DATA_CAPACITY));
    }

    public int getClimateState() {
        return data.get(ClimateControllerBlockEntity.DATA_STATE);
    }

    public boolean isLockClear() {
        return data.get(ClimateControllerBlockEntity.DATA_LOCK_CLEAR) != 0;
    }

    public boolean isLockDay() {
        return data.get(ClimateControllerBlockEntity.DATA_LOCK_DAY) != 0;
    }

    public int getCooldownTicks() {
        return Math.max(0, data.get(ClimateControllerBlockEntity.DATA_COOLDOWN));
    }

    public int getWeatherCode() {
        return data.get(ClimateControllerBlockEntity.DATA_WEATHER);
    }

    public int getDayTimeOfDay() {
        return Math.max(0, data.get(ClimateControllerBlockEntity.DATA_DAY_TIME));
    }

    public boolean isDimensionSupported() {
        return data.get(ClimateControllerBlockEntity.DATA_SUPPORTED) != 0;
    }

    public int getMotorCount() {
        return this.slots.get(ClimateControllerBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(ClimateControllerBlockEntity.BATTERY_SLOT).getItem().getCount();
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
            if (!this.moveItemStackTo(stack, ClimateControllerBlockEntity.MOTOR_SLOT,
                    ClimateControllerBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, ClimateControllerBlockEntity.BATTERY_SLOT,
                    ClimateControllerBlockEntity.BATTERY_SLOT + 1, false)) {
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
