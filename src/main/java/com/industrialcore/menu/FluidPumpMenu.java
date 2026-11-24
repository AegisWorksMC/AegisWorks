package com.industrialcore.menu;

import com.industrialcore.blockentity.FluidPumpBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 94: Menu for the Industrial Fluid Pump ({@code fluid_pump}). */
public class FluidPumpMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = FluidPumpBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final FluidPumpBlockEntity blockEntity;
    private final ContainerData data;

    public FluidPumpMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public FluidPumpMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(FluidPumpBlockEntity.DATA_COUNT));
    }

    public FluidPumpMenu(int containerId, Inventory playerInventory,
                         FluidPumpBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private FluidPumpMenu(int containerId, Inventory playerInventory, BlockPos pos,
                          @Nullable FluidPumpBlockEntity be, ContainerData data) {
        super(ModMenus.FLUID_PUMP.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(FluidPumpBlockEntity.SLOT_COUNT);

        Rect in = LifeLayout.PUMP_BUCKET_IN_SLOT;
        this.addSlot(new SlotItemHandler(inv, FluidPumpBlockEntity.BUCKET_IN_SLOT, in.x(), in.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FluidPumpBlockEntity.isFillableContainer(stack);
            }
        });

        Rect out = LifeLayout.PUMP_BUCKET_OUT_SLOT;
        this.addSlot(new SlotItemHandler(inv, FluidPumpBlockEntity.BUCKET_OUT_SLOT, out.x(), out.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        Rect mr = LifeLayout.PUMP_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, FluidPumpBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeLayout.PUMP_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, FluidPumpBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeLayout::pumpPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static FluidPumpBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof FluidPumpBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, FluidPumpBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Nullable
    public FluidPumpBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return data.get(FluidPumpBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(FluidPumpBlockEntity.DATA_CAPACITY));
    }

    public int getPumpState() {
        return data.get(FluidPumpBlockEntity.DATA_STATE);
    }

    public int getFluidAmount() {
        int lo = data.get(FluidPumpBlockEntity.DATA_FLUID_LO) & 0xFFFF;
        int hi = data.get(FluidPumpBlockEntity.DATA_FLUID_HI) & 0xFFFF;
        return (hi << 16) | lo;
    }

    public int getFluidCapacity() {
        return LifeLogic.PUMP_TANK_CAPACITY;
    }

    public Fluid getFluid() {
        int id = data.get(FluidPumpBlockEntity.DATA_FLUID_ID);
        if (id <= 0 || getFluidAmount() <= 0) {
            return Fluids.EMPTY;
        }
        Fluid f = BuiltInRegistries.FLUID.byId(id);
        return f == null ? Fluids.EMPTY : f;
    }

    public int getRadiusBlocks() {
        int r = data.get(FluidPumpBlockEntity.DATA_RADIUS);
        return r > 0 ? r : LifeLogic.PUMP_BASE_RADIUS;
    }

    public int getMotorCount() {
        return this.slots.get(FluidPumpBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(FluidPumpBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, FluidPumpBlockEntity.MOTOR_SLOT,
                        FluidPumpBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, FluidPumpBlockEntity.BATTERY_SLOT,
                        FluidPumpBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (FluidPumpBlockEntity.isFillableContainer(stack)) {
                if (!this.moveItemStackTo(stack, FluidPumpBlockEntity.BUCKET_IN_SLOT,
                        FluidPumpBlockEntity.BUCKET_IN_SLOT + 1, false)) {
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
