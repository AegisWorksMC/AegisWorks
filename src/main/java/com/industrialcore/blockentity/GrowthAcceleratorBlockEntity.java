package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriWave2Logic;
import com.industrialcore.block.GrowthAcceleratorBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.GrowthAcceleratorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Phase 91: BlockEntity for the Growth Accelerator Pulse Machine ({@code growth_accelerator}).
 *
 * <p>Surrounds itself with a 9x9 (height -1..+3) growth field. Every FE-powered pulse cycle
 * selects up to {@code 3..16} (motor-scaled) whitelisted crops/saplings/stems/reeds and applies
 * bonemeal-grade growth with subtle capped emerald particles.</p>
 */
public class GrowthAcceleratorBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "growth_accelerator";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_TARGETS = 5;
    public static final int DATA_PULSES = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_PULSES = "Pulses";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final UpgradeOnlyInventory inventory = new UpgradeOnlyInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriWave2Logic.GROWTH_BASE_CAPACITY, AgriWave2Logic.GROWTH_MAX_RECEIVE);

    private int progress;
    private int pulsesTotal;
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
                case DATA_TARGETS -> AgriWave2Logic.growthPulseTargets(getMotorCount());
                case DATA_PULSES -> pulsesTotal;
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

    public GrowthAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GROWTH_ACCELERATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrowthAcceleratorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int pulseCost = AgriWave2Logic.growthPulseEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < pulseCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            updateLit(level, state, false);
            return;
        }
        // Phase 113: Always-on standby bio-pulse rings while powered & not redstone-disabled
        if (level.hasNeighborSignal(worldPosition)) {
            status = MachineStatus.IDLE;
            updateLit(level, state, false);
            return;
        }
        updateLit(level, state, true);

        progress++;
        if (progress < interval) {
            if (status == MachineStatus.NO_ENERGY) {
                status = MachineStatus.IDLE;
            }
            return;
        }
        progress = 0;

        List<BlockPos> candidates = findGrowables(level);
        if (candidates.isEmpty()) {
            status = MachineStatus.IDLE;
            return;
        }

        Collections.shuffle(candidates);
        int targetBudget = AgriWave2Logic.growthPulseTargets(getMotorCount())
                + Math.max(0, UpgradeMath.areaRangeBonus(getSpecialCount()) / 2);
        List<BlockPos> grownPositions = new ArrayList<>();
        int pulsed = 0;

        for (BlockPos target : candidates) {
            if (pulsed >= targetBudget) {
                break;
            }
            BlockState before = level.getBlockState(target);
            BlockState beforeAbove = level.getBlockState(target.above());
            if (applyGrowthPulse(level, target, before)) {
                pulsed++;
                BlockState after = level.getBlockState(target);
                BlockState afterAbove = level.getBlockState(target.above());
                if (!after.equals(before) || !afterAbove.equals(beforeAbove)) {
                    grownPositions.add(!afterAbove.equals(beforeAbove) ? target.above() : target);
                } else {
                    grownPositions.add(target);
                }
            }
        }

        if (pulsed == 0) {
            status = MachineStatus.IDLE;
            return;
        }

        energy.consume(pulseCost);
        pulsesTotal++;
        status = MachineStatus.WORKING;
        workTracker.recordActiveTick(level, MACHINE_ID);
        updateLit(level, state, true);
        setChanged();
    }

    private List<BlockPos> findGrowables(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        int ox = worldPosition.getX();
        int oy = worldPosition.getY();
        int oz = worldPosition.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = AgriWave2Logic.GROWTH_MIN_DY; dy <= AgriWave2Logic.GROWTH_MAX_DY; dy++) {
            for (int dz = -AgriWave2Logic.GROWTH_RADIUS; dz <= AgriWave2Logic.GROWTH_RADIUS; dz++) {
                for (int dx = -AgriWave2Logic.GROWTH_RADIUS; dx <= AgriWave2Logic.GROWTH_RADIUS; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    cursor.set(ox + dx, oy + dy, oz + dz);
                    BlockState bs = level.getBlockState(cursor);
                    if (!bs.isAir() && isGrowableTarget(level, cursor, bs)) {
                        result.add(cursor.immutable());
                    }
                }
            }
        }
        return result;
    }

    private static boolean isGrowableTarget(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return !crop.isMaxAge(state) && crop.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof SaplingBlock sapling) {
            return sapling.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof StemBlock) {
            return true;
        }
        if (block instanceof SweetBerryBushBlock bush) {
            return state.getValue(SweetBerryBushBlock.AGE) < 3 && bush.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) < 3;
        }
        if (block instanceof BambooSaplingBlock sap) {
            return sap.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof BambooStalkBlock stalk) {
            return level.getBlockState(pos.above()).isAir()
                    && stalkHeightBelow(level, pos, block, 12) < 12
                    && stalk.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
            return level.getBlockState(pos.above()).isAir()
                    && stalkHeightBelow(level, pos, block, 3) < 3;
        }
        if (block instanceof CocoaBlock cocoa) {
            return cocoa.isValidBonemealTarget(level, pos, state);
        }
        if (block instanceof PitcherCropBlock || block instanceof CaveVinesBlock || block instanceof CaveVinesPlantBlock) {
            return ((BonemealableBlock) block).isValidBonemealTarget(level, pos, state);
        }
        return false;
    }

    private static int stalkHeightBelow(ServerLevel level, BlockPos top, Block block, int limit) {
        int h = 1;
        BlockPos cursor = top.below();
        while (h < limit && level.getBlockState(cursor).is(block)) {
            h++;
            cursor = cursor.below();
        }
        return h;
    }

    private static boolean applyGrowthPulse(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StemBlock stem) {
            if (stem.isValidBonemealTarget(level, pos, state)) {
                stem.performBonemeal(level, level.random, pos, state);
            } else {
                state.randomTick(level, pos, level.random);
                level.getBlockState(pos).randomTick(level, pos, level.random);
            }
            return true;
        }
        if (block instanceof NetherWartBlock) {
            int age = state.getValue(NetherWartBlock.AGE);
            if (age < 3) {
                level.setBlock(pos, state.setValue(NetherWartBlock.AGE, age + 1), Block.UPDATE_CLIENTS);
                return true;
            }
            return false;
        }
        if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
            if (level.getBlockState(pos.above()).isAir() && stalkHeightBelow(level, pos, block, 3) < 3) {
                level.setBlockAndUpdate(pos.above(), block.defaultBlockState());
                return true;
            }
            return false;
        }
        if (block instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, pos, state)) {
            if (block instanceof CropBlock || block instanceof SaplingBlock || block instanceof MangrovePropaguleBlock
                    || block instanceof SweetBerryBushBlock || block instanceof BambooSaplingBlock
                    || block instanceof BambooStalkBlock || block instanceof CocoaBlock
                    || block instanceof PitcherCropBlock || block instanceof CaveVinesBlock
                    || block instanceof CaveVinesPlantBlock) {
                growable.performBonemeal(level, level.random, pos, state);
                return true;
            }
        }
        return false;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(GrowthAcceleratorBlock.LIT) && state.getValue(GrowthAcceleratorBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(GrowthAcceleratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriWave2Logic.GROWTH_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriWave2Logic.growthPulseInterval(getMotorCount(), lvl);
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
        return Component.translatable("block.industrialcore.growth_accelerator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new GrowthAcceleratorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_PULSES, pulsesTotal);
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
        pulsesTotal = Math.max(0, tag.getInt(TAG_PULSES));
        workTracker.load(tag);
    }

    private static final class UpgradeOnlyInventory extends ItemStackHandler {
        private final GrowthAcceleratorBlockEntity owner;

        UpgradeOnlyInventory(GrowthAcceleratorBlockEntity owner) {
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
