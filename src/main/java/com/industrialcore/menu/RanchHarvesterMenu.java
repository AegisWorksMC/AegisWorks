package com.industrialcore.menu;

import com.industrialcore.agri.AgriWave2Layout;
import com.industrialcore.blockentity.RanchHarvesterBlockEntity;
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
 * Phase 91: Menu for the Automatic Ranch Harvester ({@code ranch_harvester}).
 */
public class RanchHarvesterMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = RanchHarvesterBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final RanchHarvesterBlockEntity blockEntity;
    private final ContainerData data;

    public RanchHarvesterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public RanchHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(RanchHarvesterBlockEntity.DATA_COUNT));
    }

    public RanchHarvesterMenu(int containerId, Inventory playerInventory,
                              RanchHarvesterBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private RanchHarvesterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                               @Nullable RanchHarvesterBlockEntity be, ContainerData data) {
        super(ModMenus.RANCH_HARVESTER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(RanchHarvesterBlockEntity.SLOT_COUNT);

        // 0..2: Feed & container slots (wheat/carrots/seeds/bucket/bowl)
        for (int i = 0; i < RanchHarvesterBlockEntity.FEED_COUNT; i++) {
            Rect r = AgriWave2Layout.ranchFeedSlot(i);
            this.addSlot(new SlotItemHandler(inv, RanchHarvesterBlockEntity.FEED_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RanchHarvesterBlockEntity.isRanchInput(stack);
                }
            });
        }

        // 3..11: 3x3 Output slots (extract only)
        for (int i = 0; i < RanchHarvesterBlockEntity.OUTPUT_COUNT; i++) {
            Rect r = AgriWave2Layout.ranchOutputSlot(i);
            this.addSlot(new SlotItemHandler(inv, RanchHarvesterBlockEntity.OUTPUT_START + i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // 12: Motor upgrade slot
        Rect mr = AgriWave2Layout.RANCH_MOTOR_SLOT;
        this.addSlot(new SlotItemHandler(inv, RanchHarvesterBlockEntity.MOTOR_SLOT, mr.x(), mr.y()) {
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
        Rect br = AgriWave2Layout.RANCH_BATTERY_SLOT;
        this.addSlot(new SlotItemHandler(inv, RanchHarvesterBlockEntity.BATTERY_SLOT, br.x(), br.y()) {
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
                Rect r = AgriWave2Layout.ranchPlayerSlot(pIdx++);
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = AgriWave2Layout.ranchPlayerSlot(pIdx++);
            this.addSlot(new Slot(playerInventory, col, r.x(), r.y()));
        }

        this.addDataSlots(data);
    }

    @Nullable
    private static RanchHarvesterBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof RanchHarvesterBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, RanchHarvesterBlockEntity.MACHINE_ID);
    }

    public int getEnergyStored() {
        return data.get(RanchHarvesterBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(RanchHarvesterBlockEntity.DATA_CAPACITY));
    }

    public MachineStatus getStatus() {
        MachineStatus[] vals = MachineStatus.values();
        int ord = data.get(RanchHarvesterBlockEntity.DATA_STATUS);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : MachineStatus.IDLE;
    }

    public int getProgress() {
        return data.get(RanchHarvesterBlockEntity.DATA_PROGRESS);
    }

    public int getTotalTime() {
        return Math.max(1, data.get(RanchHarvesterBlockEntity.DATA_TOTAL));
    }

    public int getAnimalCount() {
        return data.get(RanchHarvesterBlockEntity.DATA_ANIMALS);
    }

    public boolean isBreedCapped() {
        return data.get(RanchHarvesterBlockEntity.DATA_BREED_CAPPED) != 0;
    }

    public int getMotorCount() {
        return this.slots.get(RanchHarvesterBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(RanchHarvesterBlockEntity.BATTERY_SLOT).getItem().getCount();
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
                if (!this.moveItemStackTo(stack, RanchHarvesterBlockEntity.MOTOR_SLOT,
                        RanchHarvesterBlockEntity.MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, RanchHarvesterBlockEntity.BATTERY_SLOT,
                        RanchHarvesterBlockEntity.BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (RanchHarvesterBlockEntity.isRanchInput(stack)) {
                if (!this.moveItemStackTo(stack, RanchHarvesterBlockEntity.FEED_START,
                        RanchHarvesterBlockEntity.FEED_START + RanchHarvesterBlockEntity.FEED_COUNT, false)) {
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
