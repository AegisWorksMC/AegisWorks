package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.block.ReactorCoreCellBlock;
import com.industrialcore.endgame.SingularityReactorLogic;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.menu.SingularityReactorMenu;
import com.industrialcore.reactor.ReactorMultiblockLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.FailReason;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import com.industrialcore.reactor.ReactorMultiblockLogic.ValidationResult;
import com.industrialcore.reactor.ReactorMultiblockManager;
import com.industrialcore.reactor.ReactorWorldHelper;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 99: BlockEntity for the Singularity Reactor Generator ({@code singularity_reactor}).
 *
 * <p>Stores internal energy in {@code long} (1G FE base, up to 17G FE with 64 batteries),
 * generates 100K FE/t up to 10M+ FE/t with 64 motors, extends fuel burn duration up to +100%
 * with batteries, and auto-pushes FE to all 6 adjacent energy receivers up to {@code Integer.MAX_VALUE}
 * per tick with strict {@code long} conservation.</p>
 */
public class SingularityReactorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "singularity_reactor";

    public static final int FUEL_SLOT = 0;
    public static final int MOTOR_SLOT = 1;
    public static final int BATTERY_SLOT = 2;
    public static final int SLOT_COUNT = 3;

    // 15 shorts for lossless 64-bit energy, 64-bit capacity, 32-bit rate, burn ticks, and state
    public static final int DATA_E0 = 0;
    public static final int DATA_E1 = 1;
    public static final int DATA_E2 = 2;
    public static final int DATA_E3 = 3;
    public static final int DATA_C0 = 4;
    public static final int DATA_C1 = 5;
    public static final int DATA_C2 = 6;
    public static final int DATA_C3 = 7;
    public static final int DATA_RATE_LO = 8;
    public static final int DATA_RATE_HI = 9;
    public static final int DATA_BURN_LO = 10;
    public static final int DATA_BURN_HI = 11;
    public static final int DATA_BTOT_LO = 12;
    public static final int DATA_BTOT_HI = 13;
    public static final int DATA_STATE = 14;
    public static final int DATA_MB_FORMED = 15;
    public static final int DATA_MB_REASON = 16;
    public static final int DATA_MB_DIMS = 17;
    public static final int DATA_MB_CORES = 18;
    public static final int DATA_MB_COOLANT = 19;
    public static final int DATA_MB_MULT_X100 = 20;
    /** Phase 114: Reactor Overdrive flag (0/1) & emergency cooldown ticks. */
    public static final int DATA_OVERDRIVE = 21;
    public static final int DATA_COOLDOWN = 22;
    public static final int DATA_COUNT = 23;

    private static final String TAG_ENERGY = "LongEnergy";
    private static final String TAG_BURN = "BurnRemaining";
    private static final String TAG_BURN_TOTAL = "BurnTotal";
    private static final String TAG_INV = "Inventory";
    private static final String TAG_OVERDRIVE = "OverdriveMode";
    private static final String TAG_COOLDOWN = "OverdriveCooldown";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final ReactorInventory inventory = new ReactorInventory(this);
    private final IEnergyStorage externalEnergy = new ExternalReactorEnergy();
    private final IItemHandler automationItems = new AutomationFuelHandler();

    private long storedEnergy = 0L;
    private int burnRemaining = 0;
    private int burnTotal = 0;
    private int displayState = SingularityReactorLogic.STATE_INCOMPLETE;

    // Phase 103: Multiblock reactor controller state (3x3x3 .. 7x7x7)
    private boolean mbFormed = false;
    private FailReason mbFailReason = FailReason.INCOMPLETE_FRAME;
    private int mbWidth = 0;
    private int mbHeight = 0;
    private int mbDepth = 0;
    private int mbCoreCells = 0;
    private int mbCoolantBlocks = 0;
    private double mbOutputMult = 1.0D;
    private double mbCapacityMult = 1.0D;
    private double mbBurnEffMult = 1.0D;
    @Nullable
    private IntPos mbMinPos = null;
    @Nullable
    private IntPos mbMaxPos = null;
    private final List<BlockPos> mbCorePositions = new ArrayList<>();
    private final List<BlockPos> mbPortPositions = new ArrayList<>();
    private static final Direction[] DIRECTIONS = Direction.values();
    private int mbCheckTimer = 0;
    private boolean mbCoresLit = false;
    private boolean mbCoresLitDirty = true;
    // Phase 114: Reactor High-Output Overdrive & multiblock formation ritual state
    private boolean overdriveMode = false;
    private int overdriveCooldownTicks = 0;
    private int overdriveBurnTenths = 0;
    private int overdriveXpBonusCounter = 0;
    private int ritualTicks = 0;
    private boolean clientOverdrive = false;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            long cap = getEnergyCapacityLong();
            long rate = mbFormed ? currentFePerTick() : 0L;
            return switch (index) {
                case DATA_E0 -> (int) (storedEnergy & 0xFFFFL);
                case DATA_E1 -> (int) ((storedEnergy >>> 16) & 0xFFFFL);
                case DATA_E2 -> (int) ((storedEnergy >>> 32) & 0xFFFFL);
                case DATA_E3 -> (int) ((storedEnergy >>> 48) & 0xFFFFL);
                case DATA_C0 -> (int) (cap & 0xFFFFL);
                case DATA_C1 -> (int) ((cap >>> 16) & 0xFFFFL);
                case DATA_C2 -> (int) ((cap >>> 32) & 0xFFFFL);
                case DATA_C3 -> (int) ((cap >>> 48) & 0xFFFFL);
                case DATA_RATE_LO -> (int) (rate & 0xFFFFL);
                case DATA_RATE_HI -> (int) ((rate >>> 16) & 0xFFFFL);
                case DATA_BURN_LO -> burnRemaining & 0xFFFF;
                case DATA_BURN_HI -> (burnRemaining >>> 16) & 0xFFFF;
                case DATA_BTOT_LO -> burnTotal & 0xFFFF;
                case DATA_BTOT_HI -> (burnTotal >>> 16) & 0xFFFF;
                case DATA_STATE -> displayState;
                case DATA_MB_FORMED -> mbFormed ? 1 : 0;
                case DATA_MB_REASON -> mbFailReason.ordinal();
                case DATA_MB_DIMS -> ReactorMultiblockLogic.packDims(mbWidth, mbHeight, mbDepth);
                case DATA_MB_CORES -> mbCoreCells;
                case DATA_MB_COOLANT -> mbCoolantBlocks;
                case DATA_MB_MULT_X100 -> (int) Math.min(30_000L, Math.round(mbOutputMult * 100.0D));
                case DATA_OVERDRIVE -> overdriveMode ? 1 : 0;
                case DATA_COOLDOWN -> overdriveCooldownTicks;
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

    public SingularityReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULARITY_REACTOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SingularityReactorBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        boolean dirty = false;

        // 0. Periodic / on-demand variable-size (3..7) multiblock structure verification
        // When already formed, ReactorMultiblockManager notifies immediately on any broken block,
        // so periodic sanity checks can safely run every 100 ticks instead of 20 ticks.
        if (mbCheckTimer <= 0) {
            dirty |= revalidateMultiblock(level);
            mbCheckTimer = mbFormed ? 100 + Math.floorMod(worldPosition.hashCode(), 20) : 20;
        }
        mbCheckTimer--;

        if (!mbFormed) {
            displayState = SingularityReactorLogic.STATE_INCOMPLETE;
            updateLit(level, state, false);
            updateCoreCellsLit(level, false);
            if (dirty) {
                setChanged();
            }
            return;
        }

        if (ritualTicks > 0) {
            ritualTicks = com.industrialcore.reactor.ReactorRitualHelper.tickRitual(
                    level, worldPosition, mbMinPos, mbMaxPos, true, ritualTicks);
        }

        if (overdriveCooldownTicks > 0) {
            overdriveCooldownTicks--;
            displayState = SingularityReactorLogic.STATE_COOLDOWN;
            updateLit(level, state, false);
            updateCoreCellsLit(level, false);
            if (storedEnergy > 0L) {
                pushFromPosition(level, worldPosition, false);
                for (BlockPos portPos : mbPortPositions) {
                    if (storedEnergy <= 0L) break;
                    pushFromPosition(level, portPos, true);
                }
            }
            level.blockEntityChanged(worldPosition);
            return;
        }

        long cap = getEnergyCapacityLong();
        if (storedEnergy > cap) {
            storedEnergy = cap;
        }

        // 1. Ignite new fuel if buffer has room and burn timer is depleted
        if (burnRemaining <= 0 && storedEnergy < cap) {
            ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
            if (!fuel.isEmpty()) {
                int baseTicks = fuelTicksFor(fuel);
                if (baseTicks > 0) {
                    int batteryTicks = SingularityReactorLogic.effectiveBurnTicks(baseTicks, getBatteryCount());
                    int effTicks = ReactorMultiblockLogic.scaleIntSaturating(
                            batteryTicks, mbBurnEffMult, Integer.MAX_VALUE);
                    burnRemaining = effTicks;
                    burnTotal = effTicks;
                    fuel.shrink(1);
                    inventory.setStackInSlot(FUEL_SLOT, fuel);
                    dirty = true;
                }
            }
        }

        // 2. Generate FE while burning (Overdrive: 1.45x FE/t, 1.60x fuel burn, 1.25x XP)
        boolean generating = false;
        if (burnRemaining > 0) {
            if (storedEnergy < cap) {
                long feGen = currentFePerTick();
                long next = SingularityReactorLogic.addEnergy(storedEnergy, feGen, cap);
                if (next != storedEnergy) {
                    storedEnergy = next;
                    dirty = true;
                }
                if (overdriveMode) {
                    overdriveBurnTenths += 16; // 1.6x fuel burn speed
                    int take = overdriveBurnTenths / 10;
                    overdriveBurnTenths %= 10;
                    burnRemaining = Math.max(0, burnRemaining - take);
                    // 1.25x XP: record standard active tick + extra bonus tick every 4 ticks
                    workTracker.recordActiveTick(level, MACHINE_ID);
                    if (++overdriveXpBonusCounter >= 4) {
                        overdriveXpBonusCounter = 0;
                        workTracker.recordActiveTick(level, MACHINE_ID);
                    }
                    // If catalyst ran completely dry mid-Overdrive with no spare fuel, trigger safe cooldown
                    if (burnRemaining <= 0 && inventory.getStackInSlot(FUEL_SLOT).isEmpty()) {
                        overdriveMode = false;
                        overdriveCooldownTicks = com.industrialcore.reactor.ReactorOverdriveLogic.EMERGENCY_COOLDOWN_TICKS;
                        displayState = SingularityReactorLogic.STATE_COOLDOWN;
                        com.industrialcore.reactor.ReactorRitualHelper.notifyOverdriveEmergencyShutdown(
                                level, worldPosition, true);
                        BlockState cur = getBlockState();
                        level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
                    } else {
                        displayState = SingularityReactorLogic.STATE_GENERATING;
                    }
                } else {
                    burnRemaining--;
                    workTracker.recordActiveTick(level, MACHINE_ID);
                    displayState = SingularityReactorLogic.STATE_GENERATING;
                }
                generating = true;
                dirty = true;
            } else {
                displayState = SingularityReactorLogic.STATE_FULL;
            }
        } else {
            displayState = storedEnergy >= cap
                    ? SingularityReactorLogic.STATE_FULL
                    : SingularityReactorLogic.STATE_NO_FUEL;
        }

        // 3. Auto-push FE from controller AND all linked reactor_port outward faces
        if (storedEnergy > 0L) {
            dirty |= pushFromPosition(level, worldPosition, false);
            for (BlockPos portPos : mbPortPositions) {
                if (storedEnergy <= 0L) {
                    break;
                }
                dirty |= pushFromPosition(level, portPos, true);
            }
        }

        updateLit(level, state, generating);
        updateCoreCellsLit(level, generating);

        if (dirty) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private boolean pushFromPosition(ServerLevel level, BlockPos origin, boolean skipInterior) {
        boolean moved = false;
        for (Direction dir : DIRECTIONS) {
            if (storedEnergy <= 0L) {
                break;
            }
            BlockPos targetPos = origin.relative(dir);
            if ((skipInterior && isInsideBounds(targetPos)) || !level.isLoaded(targetPos)) {
                continue;
            }
            if (level.getBlockState(targetPos).isAir()) {
                continue;
            }
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, dir.getOpposite());
            if (sink == null || !sink.canReceive()) {
                continue;
            }
            int offer = SingularityReactorLogic.clampToInt(storedEnergy);
            if (offer <= 0) {
                continue;
            }
            int accepted = sink.receiveEnergy(offer, false);
            if (accepted > 0) {
                long taken = SingularityReactorLogic.extractEnergy(storedEnergy, accepted);
                storedEnergy -= taken;
                moved = true;
            }
        }
        return moved;
    }

    public static int fuelTicksFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return SingularityReactorLogic.baseFuelTicks(id);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public long currentFePerTick() {
        int ownerLvl = level instanceof ServerLevel sl ? workTracker.ownerLevel(sl, MACHINE_ID) : 1;
        long base = SingularityReactorLogic.fePerTick(getMotorCount(), ownerLvl);
        if (!mbFormed) {
            return 0L;
        }
        long mbScaled = ReactorMultiblockLogic.scaleLongSaturating(
                base, mbOutputMult, ReactorMultiblockLogic.SINGULARITY_MAX_RATE);
        if (overdriveCooldownTicks > 0) {
            return 0L;
        }
        long odScaled = overdriveMode
                ? com.industrialcore.ConfigBalanceHelper.scalePositiveLong(
                        mbScaled, com.industrialcore.Config.overdriveOutputMultiplier())
                : mbScaled;
        return com.industrialcore.ConfigBalanceHelper.scalePositiveLong(
                odScaled, com.industrialcore.Config.generationOutputMultiplier());
    }

    public boolean isOverdriveMode() {
        return overdriveMode;
    }

    public boolean isClientOverdrive() {
        return clientOverdrive;
    }

    public int getOverdriveCooldownTicks() {
        return overdriveCooldownTicks;
    }

    public int getDisplayState() {
        return displayState;
    }

    public void toggleOverdriveMode(ServerLevel level, @Nullable net.minecraft.server.level.ServerPlayer player) {
        if (overdriveCooldownTicks > 0 && !overdriveMode) {
            if (player != null) {
                player.playNotifySound(com.industrialcore.registry.ModSounds.UI_LOCKED.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 0.9F);
            }
            return;
        }
        overdriveMode = !overdriveMode;
        if (overdriveMode && mbFormed && player != null) {
            com.industrialcore.level.AegisAdvancements.grant(
                    player, com.industrialcore.level.AegisAdvancements.REACTOR_OVERDRIVE);
        }
        if (player != null) {
            player.playNotifySound(com.industrialcore.registry.ModSounds.UI_CLICK.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, overdriveMode ? 1.3F : 0.9F);
        }
        BlockState cur = getBlockState();
        level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        setChanged();
    }

    public long getStoredEnergyLong() {
        return storedEnergy;
    }

    public long getEnergyCapacityLong() {
        long base = SingularityReactorLogic.capacity(getBatteryCount());
        long scaled = mbFormed
                ? ReactorMultiblockLogic.scaleLongSaturating(
                        base, mbCapacityMult, ReactorMultiblockLogic.SINGULARITY_MAX_LONG_CAPACITY)
                : base;
        // Never shrink displayed capacity below stored energy while remodeling
        return Math.max(scaled, storedEnergy);
    }

    public boolean isMultiblockFormed() {
        return mbFormed;
    }

    public FailReason getMultiblockFailReason() {
        return mbFailReason;
    }

    public int getMultiblockCoreCells() {
        return mbCoreCells;
    }

    public int getMultiblockCoolantBlocks() {
        return mbCoolantBlocks;
    }

    public void onMultiblockBroken(ServerLevel level) {
        disassembleMultiblock(level, FailReason.INCOMPLETE_FRAME);
        mbCheckTimer = 0;
        setChanged();
    }

    public boolean revalidateMultiblock(ServerLevel level) {
        Direction facing = getBlockState().hasProperty(AbstractLifeBlock.FACING)
                ? getBlockState().getValue(AbstractLifeBlock.FACING)
                : Direction.NORTH;
        ValidationResult res = ReactorWorldHelper.validateInWorld(
                level, ReactorType.SINGULARITY, worldPosition, facing);

        if (!res.formed()) {
            boolean wasChanged = mbFormed || mbFailReason != res.reason();
            disassembleMultiblock(level, res.reason());
            return wasChanged;
        }

        Set<BlockPos> newPortSet = new HashSet<>(res.portPositions().size());
        for (IntPos p : res.portPositions()) {
            newPortSet.add(new BlockPos(p.x(), p.y(), p.z()));
        }
        for (BlockPos oldPort : mbPortPositions) {
            if (!newPortSet.contains(oldPort) && level.isLoaded(oldPort)
                    && level.getBlockEntity(oldPort) instanceof ReactorPortBlockEntity portBe) {
                portBe.unlink();
            }
        }
        Set<BlockPos> newCoreSet = new HashSet<>(res.corePositions().size());
        for (IntPos p : res.corePositions()) {
            newCoreSet.add(new BlockPos(p.x(), p.y(), p.z()));
        }
        for (BlockPos oldCore : mbCorePositions) {
            if (!newCoreSet.contains(oldCore)) {
                setCoreLitAt(level, oldCore, false);
            }
        }

        boolean justFormed = !mbFormed;
        boolean changed = !mbFormed || mbWidth != res.width() || mbHeight != res.height()
                || mbDepth != res.depth() || mbCoreCells != res.coreCells()
                || mbCoolantBlocks != res.coolantBlocks();

        mbFormed = true;
        if (justFormed) {
            ritualTicks = com.industrialcore.reactor.ReactorOverdriveLogic.RITUAL_CHARGE_TICKS;
            com.industrialcore.level.AegisAdvancements.grantNearby(
                    level, worldPosition, 24.0D,
                    com.industrialcore.level.AegisAdvancements.SINGULARITY_REACTOR);
        }
        mbFailReason = FailReason.FORMED;
        mbWidth = res.width();
        mbHeight = res.height();
        mbDepth = res.depth();
        mbCoreCells = res.coreCells();
        mbCoolantBlocks = res.coolantBlocks();
        mbOutputMult = ReactorMultiblockLogic.outputMultiplier(mbCoreCells, mbCoolantBlocks);
        mbCapacityMult = ReactorMultiblockLogic.capacityMultiplier(mbCoreCells);
        mbBurnEffMult = ReactorMultiblockLogic.burnEfficiencyMultiplier(mbCoolantBlocks);
        mbMinPos = res.minPos();
        mbMaxPos = res.maxPos();

        mbCorePositions.clear();
        mbCorePositions.addAll(newCoreSet);
        mbPortPositions.clear();
        mbPortPositions.addAll(newPortSet);
        if (changed) {
            mbCoresLitDirty = true;
            BlockState cur = getBlockState();
            level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        }

        Set<BlockPos> claimed = new HashSet<>(res.allPositions().size());
        for (IntPos p : res.allPositions()) {
            claimed.add(new BlockPos(p.x(), p.y(), p.z()));
        }
        ReactorMultiblockManager.registerFormed(level, worldPosition, claimed);

        for (BlockPos portPos : mbPortPositions) {
            if (level.isLoaded(portPos)
                    && level.getBlockEntity(portPos) instanceof ReactorPortBlockEntity portBe) {
                portBe.linkTo(worldPosition);
            }
        }
        return changed;
    }

    private void disassembleMultiblock(ServerLevel level, FailReason reason) {
        updateCoreCellsLit(level, false);
        for (BlockPos portPos : mbPortPositions) {
            if (level.isLoaded(portPos)
                    && level.getBlockEntity(portPos) instanceof ReactorPortBlockEntity portBe) {
                portBe.unlink();
            }
        }
        ReactorMultiblockManager.unregisterController(level, worldPosition);
        boolean wasFormed = mbFormed;
        mbFormed = false;
        mbFailReason = reason;
        mbWidth = 0;
        mbHeight = 0;
        mbDepth = 0;
        mbCoreCells = 0;
        mbCoolantBlocks = 0;
        mbOutputMult = 1.0D;
        mbCapacityMult = 1.0D;
        mbBurnEffMult = 1.0D;
        mbMinPos = null;
        mbMaxPos = null;
        mbCorePositions.clear();
        mbPortPositions.clear();
        displayState = SingularityReactorLogic.STATE_INCOMPLETE;
        updateLit(level, getBlockState(), false);
        if (wasFormed) {
            BlockState cur = getBlockState();
            level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        }
    }

    private void updateCoreCellsLit(ServerLevel level, boolean lit) {
        if (!mbCoresLitDirty && mbCoresLit == lit) {
            return;
        }
        mbCoresLit = lit;
        mbCoresLitDirty = false;
        for (BlockPos corePos : mbCorePositions) {
            setCoreLitAt(level, corePos, lit);
        }
    }

    private static void setCoreLitAt(ServerLevel level, BlockPos pos, boolean lit) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState st = level.getBlockState(pos);
        if (st.hasProperty(ReactorCoreCellBlock.LIT) && st.getValue(ReactorCoreCellBlock.LIT) != lit) {
            level.setBlock(pos, st.setValue(ReactorCoreCellBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isInsideBounds(BlockPos pos) {
        return mbMinPos != null && mbMaxPos != null
                && pos.getX() >= mbMinPos.x() && pos.getX() <= mbMaxPos.x()
                && pos.getY() >= mbMinPos.y() && pos.getY() <= mbMaxPos.y()
                && pos.getZ() >= mbMinPos.z() && pos.getZ() <= mbMaxPos.z();
    }

    public void setStoredEnergyLong(long value) {
        storedEnergy = Math.max(0L, Math.min(getEnergyCapacityLong(), value));
        setChanged();
    }

    public int getMotorCount() {
        ItemStack s = inventory.getStackInSlot(MOTOR_SLOT);
        return s.is(ModItems.MOTOR.get()) ? s.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack s = inventory.getStackInSlot(BATTERY_SLOT);
        return s.is(ModItems.BATTERY.get()) ? s.getCount() : 0;
    }

    public int getUpgradeSlotCap(@Nullable Player player) {
        return UpgradeCapBridge.effectiveLimit(player, this, MACHINE_ID);
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage() {
        return externalEnergy;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItems;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID uuid) {
        workTracker.setOwnerUuid(uuid);
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.singularity_reactor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new SingularityReactorMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_ENERGY, storedEnergy);
        tag.putInt(TAG_BURN, burnRemaining);
        tag.putInt(TAG_BURN_TOTAL, burnTotal);
        tag.put(TAG_INV, inventory.serializeNBT(registries));
        tag.putBoolean(TAG_OVERDRIVE, overdriveMode);
        tag.putInt(TAG_COOLDOWN, overdriveCooldownTicks);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        workTracker.load(tag);
        if (tag.contains(TAG_INV)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INV));
        }
        storedEnergy = Math.max(0L, Math.min(ReactorMultiblockLogic.SINGULARITY_MAX_LONG_CAPACITY, tag.getLong(TAG_ENERGY)));
        burnRemaining = Math.max(0, tag.getInt(TAG_BURN));
        burnTotal = Math.max(0, tag.getInt(TAG_BURN_TOTAL));
        overdriveMode = tag.getBoolean(TAG_OVERDRIVE);
        overdriveCooldownTicks = Math.max(0, tag.getInt(TAG_COOLDOWN));
    }

    // Phase 109: Lightweight client sync for hero BER & client-only particles (sent only on form/unform)
    @Nullable
    public IntPos getClientMinPos() {
        return mbMinPos;
    }

    @Nullable
    public IntPos getClientMaxPos() {
        return mbMaxPos;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("MbFormed", mbFormed);
        tag.putBoolean("Overdrive", overdriveMode && overdriveCooldownTicks <= 0);
        if (mbFormed && mbMinPos != null && mbMaxPos != null) {
            tag.putInt("MbMinX", mbMinPos.x());
            tag.putInt("MbMinY", mbMinPos.y());
            tag.putInt("MbMinZ", mbMinPos.z());
            tag.putInt("MbMaxX", mbMaxPos.x());
            tag.putInt("MbMaxY", mbMaxPos.y());
            tag.putInt("MbMaxZ", mbMaxPos.z());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        mbFormed = tag.getBoolean("MbFormed");
        clientOverdrive = tag.getBoolean("Overdrive");
        if (mbFormed && tag.contains("MbMinX")) {
            mbMinPos = new IntPos(tag.getInt("MbMinX"), tag.getInt("MbMinY"), tag.getInt("MbMinZ"));
            mbMaxPos = new IntPos(tag.getInt("MbMaxX"), tag.getInt("MbMaxY"), tag.getInt("MbMaxZ"));
            mbWidth = mbMaxPos.x() - mbMinPos.x() + 1;
            mbHeight = mbMaxPos.y() - mbMinPos.y() + 1;
            mbDepth = mbMaxPos.z() - mbMinPos.z() + 1;
        } else {
            mbMinPos = null;
            mbMaxPos = null;
            mbWidth = mbHeight = mbDepth = 0;
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, lookupProvider);
        }
    }

    /** Extract-only IEnergyStorage bridge clamped safely to Integer.MAX_VALUE. */
    private final class ExternalReactorEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (toExtract <= 0 || storedEnergy <= 0L) {
                return 0;
            }
            long taken = SingularityReactorLogic.extractEnergy(storedEnergy, toExtract);
            int clamped = SingularityReactorLogic.clampToInt(taken);
            if (!simulate && clamped > 0) {
                storedEnergy -= clamped;
                setChanged();
            }
            return clamped;
        }

        @Override
        public int getEnergyStored() {
            return SingularityReactorLogic.clampToInt(storedEnergy);
        }

        @Override
        public int getMaxEnergyStored() {
            return SingularityReactorLogic.clampToInt(getEnergyCapacityLong());
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    /** Automation handler exposing only fuel slot insertion. */
    private final class AutomationFuelHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inventory.getStackInSlot(FUEL_SLOT) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || fuelTicksFor(stack) <= 0) {
                return stack;
            }
            return inventory.insertItem(FUEL_SLOT, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && fuelTicksFor(stack) > 0;
        }
    }

    private static final class ReactorInventory extends ItemStackHandler {
        private final SingularityReactorBlockEntity owner;

        ReactorInventory(SingularityReactorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case FUEL_SLOT -> fuelTicksFor(stack) > 0;
                case MOTOR_SLOT -> stack.is(ModItems.MOTOR.get());
                case BATTERY_SLOT -> stack.is(ModItems.BATTERY.get());
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
        }
    }
}
