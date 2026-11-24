package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriWave2Logic;
import com.industrialcore.block.BioFeederBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.BioFeederMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 91: BlockEntity for the Bio Feeder Beacon ({@code bio_feeder}).
 *
 * <p>Covers a 48-block base radius (expanded up to 96 blocks with Motors). Consumes safe food
 * items from its 9-slot food buffer and FE to keep survival/adventure players topped up in
 * hunger and saturation, with per-player cooldowns and upgrade-slot protection.</p>
 */
public class BioFeederBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "bio_feeder";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int FOOD_START = 0;
    public static final int FOOD_COUNT = 9;
    public static final int MOTOR_SLOT = 9;
    public static final int BATTERY_SLOT = 10;
    public static final int SLOT_COUNT = 11;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_RADIUS = 5;
    public static final int DATA_PLAYERS = 6;
    public static final int DATA_FED_TOTAL = 7;
    public static final int DATA_COUNT = 8;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_FED_TOTAL = "FedTotal";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final FeederInventory inventory = new FeederInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriWave2Logic.FEEDER_BASE_CAPACITY, AgriWave2Logic.FEEDER_MAX_RECEIVE);

    private final Map<UUID, Long> lastFedTick = new HashMap<>();

    private int progress;
    private int playersInRange;
    private int fedTotal;
    private int activeGlowTicks;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentInterval();
                case DATA_RADIUS -> AgriWave2Logic.feederRadius(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_PLAYERS -> playersInRange;
                case DATA_FED_TOTAL -> fedTotal;
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

    public BioFeederBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIO_FEEDER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BioFeederBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int feedCost = AgriWave2Logic.feederFeedEnergy(getBatteryCount(), ownerLvl);
        int interval = currentInterval();

        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer sp : level.players()) {
            if (!sp.isSpectator() && !sp.isCreative() && sp.isAlive()
                    && AgriWave2Logic.isInFeederRange(sp.distanceToSqr(cx, cy, cz), motors)) {
                eligible.add(sp);
            }
        }
        playersInRange = eligible.size();

        if (energy.getEnergyStored() < feedCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (findSafeFoodSlot() < 0) {
            status = MachineStatus.NO_INPUT;
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
            if (status == MachineStatus.NO_ENERGY || status == MachineStatus.NO_INPUT) {
                status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            }
            return;
        }
        progress = 0;

        boolean fedAny = false;
        long now = level.getGameTime();
        for (ServerPlayer sp : eligible) {
            if (!AgriWave2Logic.needsFeeding(sp.getFoodData().getFoodLevel(), sp.getFoodData().getSaturationLevel())) {
                continue;
            }
            long prev = lastFedTick.getOrDefault(sp.getUUID(), -1000L);
            if (now - prev < AgriWave2Logic.FEEDER_PLAYER_COOLDOWN_TICKS) {
                continue;
            }
            int slot = findSafeFoodSlot();
            if (slot < 0 || energy.getEnergyStored() < feedCost) {
                break;
            }
            ItemStack foodStack = inventory.getStackInSlot(slot);
            FoodProperties props = foodStack.get(DataComponents.FOOD);
            if (props == null) {
                continue;
            }

            // Use FoodData#eat(FoodProperties) so vanilla saturation (exact float, not modifier) applies.
            sp.getFoodData().eat(props);
            foodStack.shrink(1);
            inventory.setStackInSlot(slot, foodStack);
            energy.consume(feedCost);

            fedTotal++;
            lastFedTick.put(sp.getUUID(), now);
            fedAny = true;
            workTracker.recordActiveTick(level, MACHINE_ID);

            level.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_EAT,
                    SoundSource.PLAYERS, 0.25F, 1.15F);
        }

        if (fedAny) {
            activeGlowTicks = 30;
            status = MachineStatus.WORKING;
            updateLit(level, state, true);
            setChanged();
        } else if (activeGlowTicks > 0) {
            status = MachineStatus.WORKING;
            updateLit(level, state, true);
        } else {
            status = MachineStatus.IDLE;
            updateLit(level, state, false);
        }
    }

    private int findSafeFoodSlot() {
        for (int i = FOOD_START; i < FOOD_START + FOOD_COUNT; i++) {
            if (isSafeFood(inventory.getStackInSlot(i))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean hasFoodComponent(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    public static boolean isSafeFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FoodProperties props = stack.get(DataComponents.FOOD);
        if (props == null) {
            return false;
        }
        boolean harmful = false;
        for (FoodProperties.PossibleEffect eff : props.effects()) {
            if (eff.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful = true;
                break;
            }
        }
        return AgriWave2Logic.isSafeFood(true, harmful);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(BioFeederBlock.LIT) && state.getValue(BioFeederBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(BioFeederBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriWave2Logic.FEEDER_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriWave2Logic.feederCheckInterval(getMotorCount(), lvl);
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
        return Component.translatable("block.industrialcore.bio_feeder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new BioFeederMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_FED_TOTAL, fedTotal);
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
        fedTotal = Math.max(0, tag.getInt(TAG_FED_TOTAL));
        workTracker.load(tag);
    }

    private static final class FeederInventory extends ItemStackHandler {
        private final BioFeederBlockEntity owner;

        FeederInventory(BioFeederBlockEntity owner) {
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
            if (slot >= FOOD_START && slot < FOOD_START + FOOD_COUNT) {
                return hasFoodComponent(stack);
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

    /** External pipe/hopper handler: inserts safe food into slots 0..8; never extracts upgrades or food stock. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return FOOD_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= FOOD_START && slot < FOOD_START + FOOD_COUNT)
                    ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < FOOD_START || slot >= FOOD_START + FOOD_COUNT || !isSafeFood(stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= FOOD_START && slot < FOOD_START + FOOD_COUNT && isSafeFood(stack);
        }
    }
}
