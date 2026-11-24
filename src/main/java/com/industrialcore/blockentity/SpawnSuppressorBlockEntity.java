package com.industrialcore.blockentity;

import com.industrialcore.block.SpawnSuppressorBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.SpawnSuppressorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.SpawnSuppressorRegistry;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Phase 93: BlockEntity for the Spawn Suppressor Beacon ({@code spawn_suppressor}).
 *
 * <p>Consumes FE continuously to suppress natural hostile spawns within 32..64 blocks while
 * leaving {@code SPAWNER}, spawn eggs, commands, and peaceful/animal spawns unaffected.</p>
 */
public class SpawnSuppressorBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "spawn_suppressor";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_RADIUS = 3;
    public static final int DATA_FE_PER_TICK = 4;
    public static final int DATA_BLOCKED = 5;
    public static final int DATA_COUNT = 6;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_BLOCKED = "Blocked";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final SuppressorInventory inventory = new SuppressorInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapWave2Logic.SUP_BASE_CAPACITY, TrapWave2Logic.SUP_MAX_RECEIVE);

    private int blockedTotal;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_RADIUS -> TrapWave2Logic.suppressorRadius(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_FE_PER_TICK -> currentEnergyPerTick();
                case DATA_BLOCKED -> blockedTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SpawnSuppressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWN_SUPPRESSOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnSuppressorBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int cost = currentEnergyPerTick();
        int radius = TrapWave2Logic.suppressorRadius(getMotorCount())
                + UpgradeMath.areaRangeBonus(getSpecialCount());

        if (energy.getEnergyStored() < cost) {
            status = MachineStatus.NO_ENERGY;
            SpawnSuppressorRegistry.remove(level, worldPosition);
            updateLit(level, state, false);
            return;
        }

        energy.consume(cost);
        status = MachineStatus.WORKING;
        SpawnSuppressorRegistry.upsert(level, worldPosition, radius);
        updateLit(level, state, true);
    }

    public boolean isSuppressing() {
        return !isRemoved() && status == MachineStatus.WORKING && energy.getEnergyStored() > 0;
    }

    public void recordSuppressedSpawn(ServerLevel level) {
        blockedTotal++;
        workTracker.recordActiveTick(level, MACHINE_ID);
        setChanged();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            SpawnSuppressorRegistry.remove(sl, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel sl) {
            SpawnSuppressorRegistry.remove(sl, worldPosition);
        }
        super.onChunkUnloaded();
    }

    public int currentEnergyPerTick() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapWave2Logic.suppressorEnergyPerTick(getBatteryCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(SpawnSuppressorBlock.LIT) && state.getValue(SpawnSuppressorBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(SpawnSuppressorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapWave2Logic.SUP_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int getMotorCount() {
        return upgradeBank.motors() + inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return upgradeBank.batteries() + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getSpecialCount() {
        return upgradeBank.specials();
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank getUpgradeBank() {
        return upgradeBank;
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory getSpecialCategory() {
        return com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory.FIELD;
    }

    @Override
    public String getUpgradeMachineId() {
        return MACHINE_ID;
    }

    @Override
    public int getLegacyUpgradeCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount() + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    @Override
    public int getTotalUpgradeLimit() {
        return (level instanceof ServerLevel sl)
                ? workTracker.totalUpgradeLimit(sl, MACHINE_ID)
                : com.industrialcore.level.MachineLevelMath.totalUpgradeLimitForLevel(1);
    }

    @Override
    public void onUpgradeBankChanged() {
        syncBatteryCapacity();
        setChanged();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public int getBlockedTotal() {
        return blockedTotal;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public MachineStatus getStatus() {
        return status;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
        setChanged();
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.spawn_suppressor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new SpawnSuppressorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_BLOCKED, blockedTotal);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        upgradeBank.save(tag);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        upgradeBank.load(tag);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        blockedTotal = Math.max(0, tag.getInt(TAG_BLOCKED));
        workTracker.load(tag);
    }

    private static final class SuppressorInventory extends ItemStackHandler {
        private final SpawnSuppressorBlockEntity owner;

        SuppressorInventory(SpawnSuppressorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot == MOTOR_SLOT || slot == BATTERY_SLOT)
                    ? owner.getUpgradeLimit() : super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == BATTERY_SLOT) {
                owner.syncBatteryCapacity();
            }
            owner.setChanged();
        }
    }
}
