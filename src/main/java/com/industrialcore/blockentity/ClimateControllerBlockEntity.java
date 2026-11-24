package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.ClimateControllerMenu;
import com.industrialcore.network.ClimateActionPayload;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Phase 95: BlockEntity for the Climate &amp; Time Controller ({@code climate_controller}).
 *
 * <p>Provides one-click Clear / Rain / Thunder and Morning / Night control plus optional continuous
 * Clear-Lock &amp; Day-Lock toggles, safely guarding dimensions without sky light.</p>
 */
public class ClimateControllerBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "climate_controller";

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_LOCK_CLEAR = 3;
    public static final int DATA_LOCK_DAY = 4;
    public static final int DATA_COOLDOWN = 5;
    public static final int DATA_WEATHER = 6; // 0=clear, 1=rain, 2=thunder
    public static final int DATA_DAY_TIME = 7; // 0..23999
    public static final int DATA_SUPPORTED = 8;
    public static final int DATA_COUNT = 9;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_LOCK_CLEAR = "LockClear";
    private static final String TAG_LOCK_DAY = "LockDay";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final ClimateInventory inventory = new ClimateInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeWave2Logic.CLIMATE_BASE_CAPACITY, LifeWave2Logic.CLIMATE_MAX_RECEIVE);

    private boolean lockClear;
    private boolean lockDay;
    private int cooldownTicks;
    private int climateState = LifeWave2Logic.CLIMATE_STATE_IDLE;
    private int cachedWeather;
    private int cachedTimeOfDay;
    private boolean cachedSupported = true;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> climateState;
                case DATA_LOCK_CLEAR -> lockClear ? 1 : 0;
                case DATA_LOCK_DAY -> lockDay ? 1 : 0;
                case DATA_COOLDOWN -> cooldownTicks;
                case DATA_WEATHER -> cachedWeather;
                case DATA_DAY_TIME -> cachedTimeOfDay;
                case DATA_SUPPORTED -> cachedSupported ? 1 : 0;
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

    public ClimateControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLIMATE_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClimateControllerBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        cachedSupported = level.dimensionType().hasSkyLight();
        cachedWeather = level.isThundering() ? 2 : (level.isRaining() ? 1 : 0);
        cachedTimeOfDay = (int) Math.floorMod(level.getDayTime(), LifeWave2Logic.TICKS_PER_DAY);

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (!cachedSupported) {
            climateState = LifeWave2Logic.CLIMATE_STATE_UNSUPPORTED;
            updateLit(level, state, false);
            return;
        }

        int activeHolds = (lockClear ? 1 : 0) + (lockDay ? 1 : 0);
        if (activeHolds > 0) {
            int motors = getMotorCount();
            int batteries = getBatteryCount();
            int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
            int holdCost = LifeWave2Logic.climateHoldCostPerTick(activeHolds, motors, batteries, ownerLvl);
            if (energy.getEnergyStored() < holdCost) {
                climateState = LifeWave2Logic.CLIMATE_STATE_NO_ENERGY;
                updateLit(level, state, false);
                return;
            }
            energy.consume(holdCost);
            if (lockClear && (level.isRaining() || level.isThundering())) {
                level.setWeatherParameters(6_000, 0, false, false);
                cachedWeather = 0;
            }
            if (lockDay && cachedTimeOfDay >= 12_000) {
                level.setDayTime(LifeWave2Logic.nextDayTime(level.getDayTime(), LifeWave2Logic.DAY_TIME_MORNING));
                cachedTimeOfDay = (int) LifeWave2Logic.DAY_TIME_MORNING;
            }
            climateState = LifeWave2Logic.CLIMATE_STATE_HOLDING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
        } else {
            int minAction = LifeWave2Logic.climateActionCost(false, getBatteryCount(),
                    workTracker.ownerLevel(level, MACHINE_ID));
            climateState = energy.getEnergyStored() < minAction
                    ? LifeWave2Logic.CLIMATE_STATE_NO_ENERGY
                    : LifeWave2Logic.CLIMATE_STATE_IDLE;
            updateLit(level, state, cooldownTicks > 0);
        }
    }

    public boolean handleAction(int action) {
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }
        if (!sl.dimensionType().hasSkyLight()) {
            climateState = LifeWave2Logic.CLIMATE_STATE_UNSUPPORTED;
            return false;
        }

        if (action == ClimateActionPayload.ACTION_TOGGLE_LOCK_CLEAR) {
            lockClear = !lockClear;
            setChanged();
            return true;
        }
        if (action == ClimateActionPayload.ACTION_TOGGLE_LOCK_DAY) {
            lockDay = !lockDay;
            setChanged();
            return true;
        }

        if (cooldownTicks > 0) {
            return false;
        }

        boolean isWeather = (action == ClimateActionPayload.ACTION_CLEAR
                || action == ClimateActionPayload.ACTION_RAIN
                || action == ClimateActionPayload.ACTION_THUNDER);
        boolean isTime = (action == ClimateActionPayload.ACTION_DAY
                || action == ClimateActionPayload.ACTION_NIGHT);
        if (!isWeather && !isTime) {
            return false;
        }

        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(sl, MACHINE_ID);
        int cost = LifeWave2Logic.climateActionCost(isWeather, batteries, ownerLvl);
        if (energy.getEnergyStored() < cost) {
            climateState = LifeWave2Logic.CLIMATE_STATE_NO_ENERGY;
            return false;
        }

        switch (action) {
            case ClimateActionPayload.ACTION_CLEAR -> {
                sl.setWeatherParameters(6_000, 0, false, false);
                sl.setRainLevel(0.0F);
                sl.setThunderLevel(0.0F);
            }
            case ClimateActionPayload.ACTION_RAIN -> {
                lockClear = false;
                sl.setWeatherParameters(0, 6_000, true, false);
                sl.setRainLevel(1.0F);
                sl.setThunderLevel(0.0F);
            }
            case ClimateActionPayload.ACTION_THUNDER -> {
                lockClear = false;
                sl.setWeatherParameters(0, 6_000, true, true);
                sl.setRainLevel(1.0F);
                sl.setThunderLevel(1.0F);
            }
            case ClimateActionPayload.ACTION_DAY ->
                    sl.setDayTime(LifeWave2Logic.nextDayTime(sl.getDayTime(), LifeWave2Logic.DAY_TIME_MORNING));
            case ClimateActionPayload.ACTION_NIGHT -> {
                lockDay = false;
                sl.setDayTime(LifeWave2Logic.nextDayTime(sl.getDayTime(), LifeWave2Logic.DAY_TIME_NIGHT));
            }
            default -> {
                return false;
            }
        }

        energy.consume(cost);
        cooldownTicks = LifeWave2Logic.climateCooldownTicks(motors, ownerLvl);
        cachedWeather = sl.isThundering() ? 2 : (sl.isRaining() ? 1 : 0);
        cachedTimeOfDay = (int) Math.floorMod(sl.getDayTime(), LifeWave2Logic.TICKS_PER_DAY);
        workTracker.recordActiveTick(sl, MACHINE_ID);
        sl.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55F, 1.25F);
        setChanged();
        return true;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeWave2Logic.CLIMATE_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int getMotorCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public int getClimateState() {
        return climateState;
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
        return Component.translatable("block.industrialcore.climate_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new ClimateControllerMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putBoolean(TAG_LOCK_CLEAR, lockClear);
        tag.putBoolean(TAG_LOCK_DAY, lockDay);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        lockClear = tag.getBoolean(TAG_LOCK_CLEAR);
        lockDay = tag.getBoolean(TAG_LOCK_DAY);
        workTracker.load(tag);
    }

    private static final class ClimateInventory extends ItemStackHandler {
        private final ClimateControllerBlockEntity owner;

        ClimateInventory(ClimateControllerBlockEntity owner) {
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
