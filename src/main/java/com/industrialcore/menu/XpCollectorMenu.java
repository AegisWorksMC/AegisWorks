package com.industrialcore.menu;

import com.industrialcore.blockentity.XpCollectorBlockEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/** Phase 93: Menu for the Experience Collector ({@code xp_collector}). */
public class XpCollectorMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = XpCollectorBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final XpCollectorBlockEntity blockEntity;
    private final ContainerData data;

    public XpCollectorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public XpCollectorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(XpCollectorBlockEntity.DATA_COUNT));
    }

    public XpCollectorMenu(int containerId, Inventory playerInventory,
                           XpCollectorBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private XpCollectorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable XpCollectorBlockEntity be, ContainerData data) {
        super(ModMenus.XP_COLLECTOR.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(XpCollectorBlockEntity.SLOT_COUNT);

        Rect in = TrapWave2Layout.XP_INPUT_SLOT;
        this.addSlot(new SlotItemHandler(inv, XpCollectorBlockEntity.INPUT_SLOT, in.x(), in.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GLASS_BOTTLE);
            }
        });

        for (int i = 0; i < XpCollectorBlockEntity.OUTPUT_COUNT; i++) {
            Rect r = TrapWave2Layout.xpOutputSlot(i);
            this.addSlot(new SlotItemHandler(inv, XpCollectorBlockEntity.OUTPUT_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        Rect mr = TrapWave2Layout.XP_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, XpCollectorBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = TrapWave2Layout.XP_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, XpCollectorBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        addPlayerSlots(this::addSlot, playerInventory, TrapWave2Layout::xpPlayerSlot);
        this.addDataSlots(data);
    }

    static void addPlayerSlots(java.util.function.Consumer<Slot> adder,
                               Inventory playerInventory, IntFunction<Rect> slotPos) {
        int pIdx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Rect r = slotPos.apply(pIdx++);
                adder.accept(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = slotPos.apply(pIdx++);
            adder.accept(new Slot(playerInventory, col, r.x(), r.y()));
        }
    }

    static boolean isWithinReach(BlockEntity be, BlockPos pos, Player player) {
        if (be != null) {
            return !be.isRemoved()
                    && Vec3.atCenterOf(pos).distanceToSqr(player.position()) <= 64.0D;
        }
        return player.level().isClientSide
                && Vec3.atCenterOf(pos).distanceToSqr(player.position()) <= 64.0D;
    }

    @Nullable
    private static XpCollectorBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof XpCollectorBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, XpCollectorBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Nullable
    public XpCollectorBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return data.get(XpCollectorBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(XpCollectorBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(XpCollectorBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public int getProgress() {
        return data.get(XpCollectorBlockEntity.DATA_PROGRESS);
    }

    public int getTotalTime() {
        return Math.max(1, data.get(XpCollectorBlockEntity.DATA_TOTAL));
    }

    public long getStoredXp() {
        long lo = data.get(XpCollectorBlockEntity.DATA_XP_LO) & 0xFFFFL;
        long hi = data.get(XpCollectorBlockEntity.DATA_XP_HI) & 0xFFFFL;
        return (hi << 16) | lo;
    }

    public int getRadiusBlocks() {
        return data.get(XpCollectorBlockEntity.DATA_RADIUS);
    }

    public int getBottledTotal() {
        return data.get(XpCollectorBlockEntity.DATA_BOTTLED);
    }

    public int getMotorCount() {
        return this.slots.get(XpCollectorBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(XpCollectorBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, XpCollectorBlockEntity.MOTOR_SLOT,
                        XpCollectorBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, XpCollectorBlockEntity.BATTERY_SLOT,
                        XpCollectorBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Items.GLASS_BOTTLE)) {
                if (!this.moveItemStackTo(stack, XpCollectorBlockEntity.INPUT_SLOT,
                        XpCollectorBlockEntity.INPUT_SLOT + 1, false)) {
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
        return isWithinReach(blockEntity, pos, p);
    }
}
