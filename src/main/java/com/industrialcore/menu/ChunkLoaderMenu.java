package com.industrialcore.menu;

import com.industrialcore.blockentity.ChunkLoaderBlockEntity;
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

/** Phase 95: Menu for the Chunk Loader ({@code chunk_loader}). */
public class ChunkLoaderMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = ChunkLoaderBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final ChunkLoaderBlockEntity blockEntity;
    private final ContainerData data;

    public ChunkLoaderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public ChunkLoaderMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(ChunkLoaderBlockEntity.DATA_COUNT));
    }

    public ChunkLoaderMenu(int containerId, Inventory playerInventory,
                           ChunkLoaderBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private ChunkLoaderMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable ChunkLoaderBlockEntity be, ContainerData data) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(ChunkLoaderBlockEntity.SLOT_COUNT);

        Rect mr = LifeWave2Layout.CHUNK_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, ChunkLoaderBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeWave2Layout.CHUNK_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, ChunkLoaderBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeWave2Layout::chunkPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static ChunkLoaderBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, ChunkLoaderBlockEntity.MACHINE_ID);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getEnergyStored() {
        return data.get(ChunkLoaderBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(ChunkLoaderBlockEntity.DATA_CAPACITY));
    }

    public int getChunkState() {
        return data.get(ChunkLoaderBlockEntity.DATA_STATE);
    }

    public boolean isLoaderEnabled() {
        return data.get(ChunkLoaderBlockEntity.DATA_ENABLED) != 0;
    }

    public int getEffectiveRadius() {
        return Math.max(0, data.get(ChunkLoaderBlockEntity.DATA_RADIUS));
    }

    public int getMaxUnlockedRadius() {
        return Math.max(0, data.get(ChunkLoaderBlockEntity.DATA_MAX_RADIUS));
    }

    public int getLoadedChunks() {
        return Math.max(0, data.get(ChunkLoaderBlockEntity.DATA_LOADED_CHUNKS));
    }

    public int getFePerTick() {
        return Math.max(0, data.get(ChunkLoaderBlockEntity.DATA_FE_PER_TICK));
    }

    public int getMotorCount() {
        return this.slots.get(ChunkLoaderBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(ChunkLoaderBlockEntity.BATTERY_SLOT).getItem().getCount();
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
            if (!this.moveItemStackTo(stack, ChunkLoaderBlockEntity.MOTOR_SLOT,
                    ChunkLoaderBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, ChunkLoaderBlockEntity.BATTERY_SLOT,
                    ChunkLoaderBlockEntity.BATTERY_SLOT + 1, false)) {
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
