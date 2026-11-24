package com.industrialcore.blockentity;

import com.industrialcore.block.ItemVacuumBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.ItemVacuumMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.TrapLayout;
import com.industrialcore.trap.TrapLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 92: BlockEntity for the Wide-Area Item Vacuum ({@code item_vacuum}).
 *
 * <p>Collects dropped {@link ItemEntity} stacks within an 8..16 block radius (motor-scaled)
 * into 12 internal output slots with upgrade-protected {@link IItemHandler} extraction. Items
 * that cannot fit stay safely in the world while transitioning to {@link MachineStatus#OUTPUT_FULL}.</p>
 */
public class ItemVacuumBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "item_vacuum";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int OUTPUT_START = 0;
    public static final int OUTPUT_COUNT = TrapLayout.VACUUM_OUTPUT_COUNT; // 12
    public static final int MOTOR_SLOT = 12;
    public static final int BATTERY_SLOT = 13;
    public static final int SLOT_COUNT = 14;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_RADIUS = 5;
    public static final int DATA_COLLECTED = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_COLLECTED = "Collected";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final VacuumInventory inventory = new VacuumInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapLogic.VACUUM_BASE_CAPACITY, TrapLogic.VACUUM_MAX_RECEIVE);

    private int progress;
    private int collectedTotal;
    private int activeGlowTicks;
    private MachineStatus status = MachineStatus.IDLE;
    @Nullable
    private AABB cachedVacuumBox;
    private int cachedVacuumMotors = -1;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentInterval();
                case DATA_RADIUS -> TrapLogic.vacuumRadius(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_COLLECTED -> collectedTotal;
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

    public ItemVacuumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_VACUUM.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemVacuumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int pickupCost = TrapLogic.vacuumPickupEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < pickupCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        progress++;
        if (progress < interval) {
            if (status == MachineStatus.NO_ENERGY) {
                status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            }
            return;
        }
        progress = 0;

        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        int radius = TrapLogic.vacuumRadius(motors) + UpgradeMath.areaRangeBonus(getSpecialCount());
        int cacheKey = motors * 1009 + getSpecialCount();
        AABB box = cachedVacuumBox;
        if (box == null || cachedVacuumMotors != cacheKey) {
            box = new AABB(cx - radius, cy - radius, cz - radius,
                    cx + radius, cy + radius, cz + radius);
            cachedVacuumBox = box;
            cachedVacuumMotors = cacheKey;
        }
        double rSq = (double) radius * (double) radius;
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, box,
                ie -> ie.isAlive() && !ie.isRemoved() && !ie.hasPickUpDelay()
                        && !ie.getItem().isEmpty()
                        && ie.distanceToSqr(cx, cy, cz) <= rSq);

        if (candidates.isEmpty()) {
            status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            // Phase 113: Always-on standby vortex whenever powered & not redstone-disabled
            updateLit(level, state, !level.hasNeighborSignal(worldPosition));
            return;
        }

        boolean collectedAny = false;
        boolean anyBlocked = false;
        List<Vec3> pickedPositions = new ArrayList<>();

        for (ItemEntity ie : candidates) {
            if (energy.getEnergyStored() < pickupCost) {
                break;
            }
            ItemStack raw = ie.getItem();
            int originalCount = raw.getCount();
            ItemStack rem = insertIntoOutputs(raw);
            int moved = originalCount - rem.getCount();
            if (moved > 0) {
                energy.consume(pickupCost);
                collectedTotal += moved;
                collectedAny = true;
                pickedPositions.add(ie.position());
                if (rem.isEmpty()) {
                    ie.discard();
                } else {
                    ie.setItem(rem);
                    anyBlocked = true;
                }
            } else {
                anyBlocked = true;
            }
        }

        if (collectedAny) {
            activeGlowTicks = 25;
            status = anyBlocked ? MachineStatus.OUTPUT_FULL : MachineStatus.WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
        } else if (anyBlocked) {
            status = MachineStatus.OUTPUT_FULL;
            activeGlowTicks = 0;
            updateLit(level, state, false);
        } else {
            status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            updateLit(level, state, !level.hasNeighborSignal(worldPosition));
        }
    }

    private ItemStack insertIntoOutputs(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
            rem = inventory.insertItem(i, rem, false);
        }
        return rem;
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapLogic.vacuumInterval(getMotorCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(ItemVacuumBlock.LIT) && state.getValue(ItemVacuumBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(ItemVacuumBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapLogic.VACUUM_BASE_CAPACITY, getBatteryCount());
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
        cachedVacuumBox = null;
        setChanged();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return com.industrialcore.level.UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
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

    public void setCreativeOperator(boolean creative) {
        workTracker.setCreativeOperator(creative);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.item_vacuum");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new ItemVacuumMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_COLLECTED, collectedTotal);
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
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        collectedTotal = Math.max(0, tag.getInt(TAG_COLLECTED));
        workTracker.load(tag);
    }

    private static final class VacuumInventory extends ItemStackHandler {
        private final ItemVacuumBlockEntity owner;

        VacuumInventory(ItemVacuumBlockEntity owner) {
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
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == BATTERY_SLOT) {
                owner.syncBatteryCapacity();
            }
            owner.setChanged();
        }
    }

    /** External pipe/hopper handler: extract-only across output slots 0..11; upgrades strictly protected. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT)
                    ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < OUTPUT_START || slot >= OUTPUT_START + OUTPUT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
