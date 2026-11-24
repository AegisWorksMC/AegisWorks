package com.industrialcore.menu;

import com.industrialcore.blockentity.AutoBuilderBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.life.LifeLogic.BuildMode;
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
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 94: Menu for the Auto Builder ({@code auto_builder}). */
public class AutoBuilderMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = AutoBuilderBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final AutoBuilderBlockEntity blockEntity;
    private final ContainerData data;

    public AutoBuilderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public AutoBuilderMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(AutoBuilderBlockEntity.DATA_COUNT));
    }

    public AutoBuilderMenu(int containerId, Inventory playerInventory,
                           AutoBuilderBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private AutoBuilderMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable AutoBuilderBlockEntity be, ContainerData data) {
        super(ModMenus.AUTO_BUILDER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(AutoBuilderBlockEntity.SLOT_COUNT);

        for (int i = 0; i < AutoBuilderBlockEntity.MAT_COUNT; i++) {
            Rect r = LifeLayout.builderMaterialSlot(i);
            this.addSlot(new SlotItemHandler(inv, AutoBuilderBlockEntity.MAT_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AutoBuilderBlockEntity.isSafeBuildableStack(stack);
                }
            });
        }

        Rect mr = LifeLayout.BUILDER_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, AutoBuilderBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeLayout.BUILDER_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, AutoBuilderBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeLayout::builderPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static AutoBuilderBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof AutoBuilderBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, AutoBuilderBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Nullable
    public AutoBuilderBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return data.get(AutoBuilderBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(AutoBuilderBlockEntity.DATA_CAPACITY));
    }

    public int getBuilderState() {
        return data.get(AutoBuilderBlockEntity.DATA_STATE);
    }

    public boolean isRunning() {
        return data.get(AutoBuilderBlockEntity.DATA_RUNNING) != 0;
    }

    public BuildMode getMode() {
        return BuildMode.byId(data.get(AutoBuilderBlockEntity.DATA_MODE));
    }

    public int getBuildWidth() {
        return LifeLogic.clampWidth(data.get(AutoBuilderBlockEntity.DATA_WIDTH));
    }

    public int getBuildDepth() {
        return LifeLogic.clampDepth(data.get(AutoBuilderBlockEntity.DATA_DEPTH));
    }

    public int getBuildHeight() {
        return LifeLogic.clampHeight(data.get(AutoBuilderBlockEntity.DATA_HEIGHT));
    }

    public int getPlacedProgress() {
        return Math.max(0, data.get(AutoBuilderBlockEntity.DATA_PLACED));
    }

    public int getTotalBlocks() {
        return Math.max(1, data.get(AutoBuilderBlockEntity.DATA_TOTAL));
    }

    public int getMotorCount() {
        return this.slots.get(AutoBuilderBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(AutoBuilderBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, AutoBuilderBlockEntity.MOTOR_SLOT,
                        AutoBuilderBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, AutoBuilderBlockEntity.BATTERY_SLOT,
                        AutoBuilderBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (AutoBuilderBlockEntity.isSafeBuildableStack(stack)) {
                if (!this.moveItemStackTo(stack, AutoBuilderBlockEntity.MAT_START,
                        AutoBuilderBlockEntity.MAT_START + AutoBuilderBlockEntity.MAT_COUNT, false)) {
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
