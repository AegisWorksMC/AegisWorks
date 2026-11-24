package com.industrialcore.menu;

import com.industrialcore.agri.AgriLayout;
import com.industrialcore.blockentity.TreeHarvesterBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.machine.MachineStatus;
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

/**
 * Phase 90: Menu for the Automatic Tree Harvester ({@code tree_harvester}).
 */
public class TreeHarvesterMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = TreeHarvesterBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final TreeHarvesterBlockEntity blockEntity;
    private final ContainerData data;

    public TreeHarvesterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public TreeHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(TreeHarvesterBlockEntity.DATA_COUNT));
    }

    public TreeHarvesterMenu(int containerId, Inventory playerInventory,
                             TreeHarvesterBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private TreeHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                              @Nullable TreeHarvesterBlockEntity be, ContainerData data) {
        super(ModMenus.TREE_HARVESTER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(TreeHarvesterBlockEntity.SLOT_COUNT);

        // 0..2: Sapling slots
        for (int i = 0; i < TreeHarvesterBlockEntity.SAPLING_COUNT; i++) {
            Rect r = AgriLayout.inputSlot3(i);
            this.addSlot(new SlotItemHandler(inv, TreeHarvesterBlockEntity.SAPLING_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TreeHarvesterBlockEntity.isSaplingItem(stack);
                }
            });
        }

        // 3..11: 3x3 Output slots
        for (int i = 0; i < TreeHarvesterBlockEntity.OUTPUT_COUNT; i++) {
            Rect r = AgriLayout.outputSlot(i);
            this.addSlot(new SlotItemHandler(inv, TreeHarvesterBlockEntity.OUTPUT_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // 12: Motor slot
        Rect mr = AgriLayout.MOTOR_SLOT;
        this.addSlot(new SlotItemHandler(inv, TreeHarvesterBlockEntity.MOTOR_SLOT, mr.x(), mr.y()) {
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

        // 13: Battery slot
        Rect br = AgriLayout.BATTERY_SLOT;
        this.addSlot(new SlotItemHandler(inv, TreeHarvesterBlockEntity.BATTERY_SLOT, br.x(), br.y()) {
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

        // 14..49: Player inventory + hotbar
        int pIdx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Rect r = AgriLayout.playerSlot(pIdx++);
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = AgriLayout.playerSlot(pIdx++);
            this.addSlot(new Slot(playerInventory, col, r.x(), r.y()));
        }

        this.addDataSlots(data);
    }

    @Nullable
    private static TreeHarvesterBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof TreeHarvesterBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, TreeHarvesterBlockEntity.MACHINE_ID);
    }

    public int getEnergyStored() {
        return data.get(TreeHarvesterBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(TreeHarvesterBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(TreeHarvesterBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public int getProgress() {
        return data.get(TreeHarvesterBlockEntity.DATA_PROGRESS);
    }

    public int getTotalTime() {
        return Math.max(1, data.get(TreeHarvesterBlockEntity.DATA_TOTAL));
    }

    public int getTreesFelled() {
        return data.get(TreeHarvesterBlockEntity.DATA_TREES_FELLED);
    }

    public int getMotorCount() {
        return this.slots.get(TreeHarvesterBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(TreeHarvesterBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, TreeHarvesterBlockEntity.MOTOR_SLOT,
                        TreeHarvesterBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, TreeHarvesterBlockEntity.BATTERY_SLOT,
                        TreeHarvesterBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (TreeHarvesterBlockEntity.isSaplingItem(stack)) {
                if (!this.moveItemStackTo(stack, TreeHarvesterBlockEntity.SAPLING_START,
                        TreeHarvesterBlockEntity.SAPLING_START + TreeHarvesterBlockEntity.SAPLING_COUNT, false)) {
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
