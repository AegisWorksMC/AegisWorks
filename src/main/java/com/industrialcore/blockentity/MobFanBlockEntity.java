package com.industrialcore.blockentity;

import com.industrialcore.block.MobFanBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.MobFanMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.TrapLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 92: BlockEntity for the 6-direction Mob Movement Fan ({@code mob_fan}).
 *
 * <p>Consumes FE continuously to push mobs and dropped {@link ItemEntity} stacks along
 * {@link MobFanBlock#FACING} across a 3x3 corridor of 8..16 blocks (motor-scaled). Sneaking
 * players remain unaffected for comfortable trap maintenance.</p>
 */
public class MobFanBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "mob_fan";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_FACING = 3;
    public static final int DATA_RANGE = 4;
    public static final int DATA_POWER_PCT = 5;
    public static final int DATA_TARGETS = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final FanInventory inventory = new FanInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapLogic.FAN_BASE_CAPACITY, TrapLogic.FAN_MAX_RECEIVE);

    private int targetCount;
    private MachineStatus status = MachineStatus.IDLE;
    @Nullable
    private AABB cachedFanBox;
    @Nullable
    private Direction cachedFanDir;
    private int cachedFanMotors = -1;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_FACING -> currentFacing().ordinal();
                case DATA_RANGE -> TrapLogic.fanRange(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_POWER_PCT -> TrapLogic.fanPowerPercent(getMotorCount());
                case DATA_TARGETS -> targetCount;
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

    public MobFanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_FAN.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MobFanBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int energyCost = TrapLogic.fanEnergyPerTick(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < energyCost) {
            targetCount = 0;
            status = MachineStatus.NO_ENERGY;
            updateLit(level, state, false);
            return;
        }

        Direction dir = currentFacing();
        AABB box = cachedFanBox;
        if (box == null || cachedFanDir != dir || cachedFanMotors != motors) {
            TrapLogic.Box6 box6 = TrapLogic.fanVolume(
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    dir.getStepX(), dir.getStepY(), dir.getStepZ(), motors);
            box = new AABB(box6.minX(), box6.minY(), box6.minZ(),
                    box6.maxX(), box6.maxY(), box6.maxZ());
            cachedFanBox = box;
            cachedFanDir = dir;
            cachedFanMotors = motors;
        }

        // Adaptive coordinate-staggered scan: every 2 ticks when pushing, every 4 ticks when empty
        long scanInterval = targetCount > 0 ? 2L : 4L;
        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), scanInterval) == 0L) {
            List<Entity> targets = level.getEntitiesOfClass(Entity.class, box,
                    e -> (e instanceof LivingEntity || e instanceof ItemEntity)
                            && TrapLogic.shouldPushEntity(e instanceof Player, e.isShiftKeyDown(),
                            e.isSpectator(), e.isAlive()));

            double strength = TrapLogic.fanPushStrength(motors) * (scanInterval > 1L ? 1.8D : 1.0D);
            double maxSpeed = 0.45D + 0.25D * Math.min(64, Math.max(0, motors)) / 64.0D;
            int pushed = 0;
            for (Entity e : targets) {
                Vec3 dm = e.getDeltaMovement();
                double vx = pushAxis(dm.x, dir.getStepX(), strength, maxSpeed);
                double vy = (dir == Direction.UP)
                        ? pushAxis(dm.y, 1, strength + 0.06D, maxSpeed)
                        : pushAxis(dm.y, dir.getStepY(), strength, maxSpeed);
                double vz = pushAxis(dm.z, dir.getStepZ(), strength, maxSpeed);
                e.setDeltaMovement(vx, vy, vz);
                e.hurtMarked = true;
                if (dir == Direction.UP && e instanceof LivingEntity) {
                    e.fallDistance = 0.0F;
                }
                pushed++;
            }
            targetCount = pushed;
        }

        energy.consume(energyCost);
        status = MachineStatus.WORKING;
        if (targetCount > 0) {
            workTracker.recordActiveTick(level, MACHINE_ID);
        }
        updateLit(level, state, true);
    }

    private static double pushAxis(double current, int step, double strength, double maxSpeed) {
        if (step == 0) {
            return current;
        }
        double updated = current + step * strength;
        return step > 0 ? Math.min(maxSpeed, updated) : Math.max(-maxSpeed, updated);
    }

    private Direction currentFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(MobFanBlock.FACING) ? state.getValue(MobFanBlock.FACING) : Direction.NORTH;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(MobFanBlock.LIT) && state.getValue(MobFanBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(MobFanBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapLogic.FAN_BASE_CAPACITY, getBatteryCount());
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
        cachedFanBox = null;
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
        return Component.translatable("block.industrialcore.mob_fan");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new MobFanMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
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
        workTracker.load(tag);
    }

    private static final class FanInventory extends ItemStackHandler {
        private final MobFanBlockEntity owner;

        FanInventory(MobFanBlockEntity owner) {
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
}
