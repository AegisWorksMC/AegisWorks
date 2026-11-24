package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.life.LifeLogic.WorldDelta;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.FluidPumpMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Phase 94: BlockEntity for the Industrial Fluid Pump ({@code fluid_pump}).
 *
 * <p>Scans top-down within horizontal radius 8..16 and depth 32 for source fluid blocks, pumps
 * them into a 64,000 mB internal {@link FluidTank} (treating 2+ adjacent water sources as an
 * infinite water source), pushes to adjacent {@link IFluidHandler}s, exposes {@link IFluidHandler}
 * for pipes/Quantum Storage, and fills empty buckets via GUI slots or block right-click.</p>
 */
public class FluidPumpBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "fluid_pump";

    public static final int BUCKET_IN_SLOT = 0;
    public static final int BUCKET_OUT_SLOT = 1;
    public static final int MOTOR_SLOT = 2;
    public static final int BATTERY_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_FLUID_LO = 3;
    public static final int DATA_FLUID_HI = 4;
    public static final int DATA_FLUID_ID = 5;
    public static final int DATA_RADIUS = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_TANK = "FluidTank";
    private static final String TAG_SCAN = "ScanCursor";
    private static final String TAG_STATE = "PumpState";
    private static final String TAG_INVENTORY = "Inventory";

    private static final int SCAN_STEPS_PER_CYCLE = 96;
    private static final int AUTO_PUSH_MB = 1_000;
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Direction[] ALL_DIRS = Direction.values();

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final PumpInventory inventory = new PumpInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeLogic.PUMP_BASE_CAPACITY, LifeLogic.PUMP_MAX_RECEIVE);
    private final FluidTank tank = new FluidTank(LifeLogic.PUMP_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            if (level != null) {
                level.blockEntityChanged(worldPosition);
            }
        }
    };

    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IFluidHandler, Direction>[] neighborFluidCaches = new BlockCapabilityCache[6];
    private int scanCursor;
    private int scannedWithoutFind;
    private int cooldownTicks;
    private int rescanDelayTicks;
    private int activeGlowTicks;
    private int pumpState = LifeLogic.PUMP_STATE_IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            FluidStack fs = tank.getFluid();
            int amt = fs.getAmount();
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> pumpState;
                case DATA_FLUID_LO -> amt & 0xFFFF;
                case DATA_FLUID_HI -> (amt >>> 16) & 0xFFFF;
                case DATA_FLUID_ID -> fs.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fs.getFluid());
                case DATA_RADIUS -> LifeLogic.pumpRadius(getMotorCount());
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

    public FluidPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PUMP.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPumpBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();

        // 1. Always process GUI bucket filling & gentle push to adjacent tanks/Quantum Storage
        processBucketFilling();
        pushFluidToNeighbors(level);

        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int energyCost = LifeLogic.pumpEnergyPerBucket(batteries, ownerLvl);

        if (energy.getEnergyStored() < energyCost) {
            pumpState = LifeLogic.PUMP_STATE_NO_ENERGY;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (!LifeLogic.canAcceptBucket(tank.getFluidAmount())) {
            pumpState = LifeLogic.PUMP_STATE_TANK_FULL;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        if (rescanDelayTicks > 0) {
            rescanDelayTicks--;
            pumpState = LifeLogic.PUMP_STATE_NO_FLUID;
            updateLit(level, state, false);
            return;
        }

        cooldownTicks++;
        int interval = LifeLogic.pumpInterval(motors, ownerLvl);
        if (cooldownTicks < interval) {
            pumpState = activeGlowTicks > 0 ? LifeLogic.PUMP_STATE_PUMPING : LifeLogic.PUMP_STATE_IDLE;
            updateLit(level, state, activeGlowTicks > 0);
            return;
        }
        cooldownTicks = 0;

        int radius = LifeLogic.pumpRadius(motors);
        int scanVolume = LifeLogic.pumpScanVolume(radius, LifeLogic.PUMP_MAX_DEPTH);
        boolean pumped = false;

        for (int step = 0; step < SCAN_STEPS_PER_CYCLE; step++) {
            WorldDelta d = LifeLogic.pumpDeltaForIndex(scanCursor, radius, LifeLogic.PUMP_MAX_DEPTH);
            scanCursor = (scanCursor + 1) % scanVolume;
            scannedWithoutFind++;

            if (!LifeLogic.isWithinPumpRadius(d.dx(), d.dz(), radius)) {
                if (scannedWithoutFind >= scanVolume) {
                    break;
                }
                continue;
            }
            BlockPos target = worldPosition.offset(d.dx(), d.dy(), d.dz());
            if (!level.isInWorldBounds(target) || !level.isLoaded(target)) {
                if (scannedWithoutFind >= scanVolume) {
                    break;
                }
                continue;
            }
            FluidState fs = level.getFluidState(target);
            if (!fs.isSource() || fs.isEmpty()) {
                if (scannedWithoutFind >= scanVolume) {
                    break;
                }
                continue;
            }
            Fluid fluid = fs.getType();
            FluidStack candidate = new FluidStack(fluid, LifeLogic.PUMP_BUCKET_MB);
            if (tank.fill(candidate, IFluidHandler.FluidAction.SIMULATE) < LifeLogic.PUMP_BUCKET_MB) {
                if (scannedWithoutFind >= scanVolume) {
                    break;
                }
                continue;
            }

            // Infinite water source check: keep source intact if >= 2 horizontal adjacent water sources
            boolean isWater = fs.is(FluidTags.WATER);
            int adjWater = 0;
            if (isWater) {
                for (Direction dir : HORIZONTALS) {
                    FluidState adj = level.getFluidState(target.relative(dir));
                    if (adj.isSource() && adj.is(FluidTags.WATER)) {
                        adjWater++;
                    }
                }
            }
            if (!LifeLogic.isInfiniteWaterSource(isWater, adjWater)) {
                consumeFluidSource(level, target);
            } else {
                // Infinite water found: keep cursor near this source for ultra-low-lag continuous pumping
                scanCursor = Math.floorMod(scanCursor - 1, scanVolume);
            }

            tank.fill(candidate, IFluidHandler.FluidAction.EXECUTE);
            energy.consume(energyCost);
            scannedWithoutFind = 0;
            activeGlowTicks = Math.max(25, interval + 8);
            pumpState = LifeLogic.PUMP_STATE_PUMPING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            level.blockEntityChanged(worldPosition);
            pumped = true;
            break;
        }

        if (!pumped) {
            if (scannedWithoutFind >= scanVolume) {
                scannedWithoutFind = 0;
                rescanDelayTicks = 40;
                pumpState = LifeLogic.PUMP_STATE_NO_FLUID;
                activeGlowTicks = 0;
                updateLit(level, state, false);
            } else {
                pumpState = activeGlowTicks > 0 ? LifeLogic.PUMP_STATE_PUMPING : LifeLogic.PUMP_STATE_IDLE;
                updateLit(level, state, activeGlowTicks > 0);
            }
        }
    }

    private static void consumeFluidSource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof LiquidBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void processBucketFilling() {
        if (tank.getFluidAmount() < LifeLogic.PUMP_BUCKET_MB) {
            return;
        }
        ItemStack inStack = inventory.getStackInSlot(BUCKET_IN_SLOT);
        if (inStack.isEmpty()) {
            return;
        }
        ItemStack single = inStack.copyWithCount(1);
        FluidActionResult sim = FluidUtil.tryFillContainer(single, tank, LifeLogic.PUMP_BUCKET_MB, null, false);
        if (!sim.isSuccess() || sim.getResult().isEmpty()) {
            return;
        }
        ItemStack result = sim.getResult();
        ItemStack outStack = inventory.getStackInSlot(BUCKET_OUT_SLOT);
        boolean canPlace = outStack.isEmpty()
                || (ItemStack.isSameItemSameComponents(outStack, result)
                && outStack.getCount() + result.getCount() <= outStack.getMaxStackSize());
        if (!canPlace) {
            return;
        }
        FluidActionResult exec = FluidUtil.tryFillContainer(single, tank, LifeLogic.PUMP_BUCKET_MB, null, true);
        if (exec.isSuccess() && !exec.getResult().isEmpty()) {
            inventory.extractItem(BUCKET_IN_SLOT, 1, false);
            ItemStack filled = exec.getResult();
            if (outStack.isEmpty()) {
                inventory.setStackInSlot(BUCKET_OUT_SLOT, filled);
            } else {
                outStack.grow(filled.getCount());
                inventory.setStackInSlot(BUCKET_OUT_SLOT, outStack);
            }
            setChanged();
        }
    }

    private void pushFluidToNeighbors(ServerLevel level) {
        if (tank.getFluidAmount() <= 0) {
            return;
        }
        for (int i = 0; i < ALL_DIRS.length; i++) {
            if (tank.getFluidAmount() <= 0) {
                break;
            }
            Direction dir = ALL_DIRS[i];
            BlockPos nPos = worldPosition.relative(dir);
            if (!level.isLoaded(nPos)) {
                continue;
            }
            BlockCapabilityCache<IFluidHandler, Direction> cache = neighborFluidCaches[i];
            if (cache == null) {
                cache = BlockCapabilityCache.create(
                        Capabilities.FluidHandler.BLOCK, level, nPos.immutable(), dir.getOpposite());
                neighborFluidCaches[i] = cache;
            }
            IFluidHandler target = cache.getCapability();
            if (target == null || level.getBlockEntity(nPos) instanceof FluidPumpBlockEntity) {
                continue;
            }
            FluidStack avail = tank.drain(AUTO_PUSH_MB, IFluidHandler.FluidAction.SIMULATE);
            if (avail.isEmpty()) {
                break;
            }
            int accepted = target.fill(avail, IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                level.blockEntityChanged(worldPosition);
            }
        }
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeLogic.PUMP_BASE_CAPACITY, getBatteryCount());
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

    public FluidTank getFluidTank() {
        return tank;
    }

    public IFluidHandler getFluidHandler() {
        return tank;
    }

    public int getPumpState() {
        return pumpState;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
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
        return Component.translatable("block.industrialcore.fluid_pump");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new FluidPumpMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        CompoundTag tankTag = new CompoundTag();
        tank.writeToNBT(registries, tankTag);
        tag.put(TAG_TANK, tankTag);
        tag.putInt(TAG_SCAN, scanCursor);
        tag.putInt(TAG_STATE, pumpState);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        if (tag.contains(TAG_TANK)) {
            tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
        }
        scanCursor = Math.max(0, tag.getInt(TAG_SCAN));
        pumpState = tag.getInt(TAG_STATE);
        workTracker.load(tag);
    }

    public static boolean isFillableContainer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.BUCKET)) {
            return true;
        }
        return FluidUtil.getFluidHandler(stack.copyWithCount(1)).isPresent();
    }

    private static final class PumpInventory extends ItemStackHandler {
        private final FluidPumpBlockEntity owner;

        PumpInventory(FluidPumpBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == BUCKET_IN_SLOT) {
                return isFillableContainer(stack);
            }
            if (slot == BUCKET_OUT_SLOT) {
                return !stack.isEmpty();
            }
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

    /** External automation: insert empty containers into slot 0, extract filled containers from slot 1 only. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot == BUCKET_IN_SLOT || slot == BUCKET_OUT_SLOT)
                    ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != BUCKET_IN_SLOT || !isFillableContainer(stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != BUCKET_OUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot == BUCKET_IN_SLOT || slot == BUCKET_OUT_SLOT)
                    ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == BUCKET_IN_SLOT && isFillableContainer(stack);
        }
    }
}
