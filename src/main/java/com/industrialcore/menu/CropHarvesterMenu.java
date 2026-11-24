package com.industrialcore.menu;

import com.industrialcore.agri.AgriLayout;
import com.industrialcore.blockentity.CropHarvesterBlockEntity;
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
 * Phase 90: Menu for the Automatic Crop Harvester ({@code crop_harvester}).
 */
public class CropHarvesterMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = CropHarvesterBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final CropHarvesterBlockEntity blockEntity;
    private final ContainerData data;

    public CropHarvesterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public CropHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(CropHarvesterBlockEntity.DATA_COUNT));
    }

    public CropHarvesterMenu(int containerId, Inventory playerInventory,
                             CropHarvesterBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private CropHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                              @Nullable CropHarvesterBlockEntity be, ContainerData data) {
        super(ModMenus.CROP_HARVESTER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(CropHarvesterBlockEntity.SLOT_COUNT);

        // 0..2: Seed buffer slots
        for (int i = 0; i < CropHarvesterBlockEntity.SEED_COUNT; i++) {
            Rect r = AgriLayout.inputSlot3(i);
            this.addSlot(new SlotItemHandler(inv, CropHarvesterBlockEntity.SEED_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return CropHarvesterBlockEntity.isSeedLike(stack);
                }
            });
        }

        // 3..11: 3x3 Output slots (output-only)
        for (int i = 0; i < CropHarvesterBlockEntity.OUTPUT_COUNT; i++) {
            Rect r = AgriLayout.outputSlot(i);
            this.addSlot(new SlotItemHandler(inv, CropHarvesterBlockEntity.OUTPUT_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // 12: Motor upgrade slot
        Rect mr = AgriLayout.MOTOR_SLOT;
        this.addSlot(new SlotItemHandler(inv, CropHarvesterBlockEntity.MOTOR_SLOT, mr.x(), mr.y()) {
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

        // 13: Battery upgrade slot
        Rect br = AgriLayout.BATTERY_SLOT;
        this.addSlot(new SlotItemHandler(inv, CropHarvesterBlockEntity.BATTERY_SLOT, br.x(), br.y()) {
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
    private static CropHarvesterBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof CropHarvesterBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, CropHarvesterBlockEntity.MACHINE_ID);
    }

    public int getEnergyStored() {
        return data.get(CropHarvesterBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(CropHarvesterBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(CropHarvesterBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public int getProgress() {
        return data.get(CropHarvesterBlockEntity.DATA_PROGRESS);
    }

    public int getTotalTime() {
        return Math.max(1, data.get(CropHarvesterBlockEntity.DATA_TOTAL));
    }

    public int getHarvestedCount() {
        return data.get(CropHarvesterBlockEntity.DATA_HARVESTED);
    }

    public int getMotorCount() {
        return this.slots.get(CropHarvesterBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(CropHarvesterBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, CropHarvesterBlockEntity.MOTOR_SLOT,
                        CropHarvesterBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, CropHarvesterBlockEntity.BATTERY_SLOT,
                        CropHarvesterBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (CropHarvesterBlockEntity.isSeedLike(stack)) {
                if (!this.moveItemStackTo(stack, CropHarvesterBlockEntity.SEED_START,
                        CropHarvesterBlockEntity.SEED_START + CropHarvesterBlockEntity.SEED_COUNT, false)) {
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
