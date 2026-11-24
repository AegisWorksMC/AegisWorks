package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.WirelessChargerMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 94: BlockEntity for the Wireless Charger ({@code wireless_charger}).
 *
 * <p>Scans players within 32..64 blocks in the same dimension and wirelessly delivers 2,000..32,000
 * FE/tick into any {@link Capabilities.EnergyStorage#ITEM} item carried in main inventory, hotbar,
 * armor slots, or offhand with strict energy conservation.</p>
 */
public class WirelessChargerBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "wireless_charger";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_RADIUS = 3;
    public static final int DATA_RATE_LO = 4;
    public static final int DATA_RATE_HI = 5;
    public static final int DATA_PLAYERS = 6;
    public static final int DATA_TOTAL_0 = 7;
    public static final int DATA_TOTAL_1 = 8;
    public static final int DATA_TOTAL_2 = 9;
    public static final int DATA_COUNT = 10;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_TOTAL = "TotalTransferred";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final ChargerInventory inventory = new ChargerInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeLogic.CHARGER_BASE_CAPACITY, LifeLogic.CHARGER_MAX_RECEIVE);

    private long totalTransferred;
    private int nearbyPlayers;
    private int chargerState = LifeLogic.CHARGER_STATE_IDLE;
    private int activeGlowTicks;
    private int particleTick;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            int rate = LifeLogic.chargerRatePerTick(getMotorCount());
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> chargerState;
                case DATA_RADIUS -> LifeLogic.chargerRadius(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_RATE_LO -> rate & 0xFFFF;
                case DATA_RATE_HI -> (rate >>> 16) & 0xFFFF;
                case DATA_PLAYERS -> nearbyPlayers;
                case DATA_TOTAL_0 -> (int) (totalTransferred & 0xFFFFL);
                case DATA_TOTAL_1 -> (int) ((totalTransferred >>> 16) & 0xFFFFL);
                case DATA_TOTAL_2 -> (int) ((totalTransferred >>> 32) & 0xFFFFL);
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

    public WirelessChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_CHARGER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WirelessChargerBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);

        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;

        List<ServerPlayer> inRange = new ArrayList<>();
        int effectiveRadius = LifeLogic.chargerRadius(motors) + UpgradeMath.areaRangeBonus(getSpecialCount());
        double rSq = (double) effectiveRadius * (double) effectiveRadius;
        for (ServerPlayer sp : level.players()) {
            if (!sp.isAlive() || sp.isSpectator()) {
                continue;
            }
            if (sp.distanceToSqr(cx, cy, cz) <= rSq) {
                inRange.add(sp);
            }
        }
        nearbyPlayers = inRange.size();

        if (energy.getEnergyStored() <= 0) {
            chargerState = LifeLogic.CHARGER_STATE_NO_ENERGY;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        int maxRate = LifeLogic.chargerRatePerTick(motors);
        List<IEnergyStorage> targets = new ArrayList<>();
        List<Integer> needList = new ArrayList<>();
        List<ServerPlayer> chargedPlayers = new ArrayList<>();

        for (ServerPlayer sp : inRange) {
            Inventory pinv = sp.getInventory();
            int beforeCount = targets.size();
            collectChargeable(pinv.items, maxRate, targets, needList);
            collectChargeable(pinv.armor, maxRate, targets, needList);
            collectChargeable(pinv.offhand, maxRate, targets, needList);
            if (targets.size() > beforeCount) {
                chargedPlayers.add(sp);
            }
        }

        int deliveredThisTick = 0;
        if (!targets.isEmpty()) {
            int[] needs = new int[needList.size()];
            for (int i = 0; i < needs.length; i++) {
                needs[i] = needList.get(i);
            }
            int[] alloc = LifeLogic.distributeCharge(energy.getEnergyStored(), maxRate, needs);
            for (int i = 0; i < targets.size(); i++) {
                int offer = Math.min(alloc[i], energy.getEnergyStored());
                if (offer <= 0) {
                    continue;
                }
                int accepted = targets.get(i).receiveEnergy(offer, false);
                if (accepted > 0) {
                    int actual = Math.min(accepted, energy.getEnergyStored());
                    energy.consume(actual);
                    deliveredThisTick += actual;
                }
            }
        }

        if (deliveredThisTick > 0) {
            int fieldCost = Math.min(energy.getEnergyStored(), LifeLogic.chargerFieldCost(batteries, ownerLvl));
            if (fieldCost > 0) {
                energy.consume(fieldCost);
            }
            totalTransferred += deliveredThisTick;
            activeGlowTicks = 25;
            chargerState = LifeLogic.CHARGER_STATE_CHARGING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
        } else {
            chargerState = activeGlowTicks > 0 ? LifeLogic.CHARGER_STATE_CHARGING : LifeLogic.CHARGER_STATE_IDLE;
            // Phase 113: Always-on standby glow/rotation whenever powered & not redstone-disabled
            updateLit(level, state, !level.hasNeighborSignal(worldPosition));
        }
    }

    private static void collectChargeable(List<ItemStack> stacks, int maxRate,
                                          List<IEnergyStorage> outTargets, List<Integer> outNeeds) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            IEnergyStorage es = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (es == null || !es.canReceive()) {
                continue;
            }
            int need = es.receiveEnergy(maxRate, true);
            if (need > 0) {
                outTargets.add(es);
                outNeeds.add(need);
            }
        }
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeLogic.CHARGER_BASE_CAPACITY, getBatteryCount());
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

    public long getTotalTransferred() {
        return totalTransferred;
    }

    public int getNearbyPlayers() {
        return nearbyPlayers;
    }

    public int getChargerState() {
        return chargerState;
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
        return Component.translatable("block.industrialcore.wireless_charger");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new WirelessChargerMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putLong(TAG_TOTAL, totalTransferred);
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
        totalTransferred = Math.max(0L, tag.getLong(TAG_TOTAL));
        workTracker.load(tag);
    }

    private static final class ChargerInventory extends ItemStackHandler {
        private final WirelessChargerBlockEntity owner;

        ChargerInventory(WirelessChargerBlockEntity owner) {
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
