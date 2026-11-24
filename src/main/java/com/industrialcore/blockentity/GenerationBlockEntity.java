package com.industrialcore.blockentity;

import com.industrialcore.block.GenerationBlock;
import com.industrialcore.block.ReactorCoreCellBlock;
import com.industrialcore.energy.FePush;
import com.industrialcore.energy.GenerationBalance;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.reactor.NuclearCoolantLogic;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared block entity of the six conventional power devices (Phase 33): thermal, hydro,
 * wind, solar, combustion and nuclear. The kind arrives from the block instance, so one
 * implementation serves all six - like the processing machines that select their recipe
 * source on the block.
 *
 * <p>Energy conservation rules implemented here:</p>
 * <ul>
 *   <li>Fuel is valued at {@code burnTime * 40} FE, exactly like the original generator
 *       (one coal = 64,000 FE), so the existing balance measurements still hold.</li>
 *   <li>The burn clock only advances when the buffer accepted the full rated output this
 *       tick, so a full buffer pauses burning and no FE is ever lost or duplicated.</li>
 *   <li>Only {@link GenerationBalance#DIRECT_PERCENT}% of the fuel content becomes direct FE;
 *       the remaining {@link GenerationBalance#BYPRODUCT_PERCENT}% is pooled as exhaust /
 *       waste heat for the recovery devices - the sum can never exceed the fuel's content.</li>
 * </ul>
 *
 * <p>Condition-based output (water, sky, open air) is cached and refreshed on a timer or
 * immediately when a neighbour changes; per tick it is only read.</p>
 */
public class GenerationBlockEntity extends BlockEntity
        implements MenuProvider, com.industrialcore.machine.IUpgradeableMachine {
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();
    public static final int FUEL_SLOT = 0;
    /** Phase 105.6: Manual water-bucket input slot (used exclusively when kind == NUCLEAR). */
    public static final int BUCKET_SLOT = 1;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_BURN = 2;
    public static final int DATA_BURN_TOTAL = 3;
    public static final int DATA_RATE = 4;
    public static final int DATA_STATE = 5;
    public static final int DATA_MB_FORMED = 6;
    public static final int DATA_MB_REASON = 7;
    public static final int DATA_MB_DIMS = 8;
    public static final int DATA_MB_CORES = 9;
    public static final int DATA_MB_COOLANT = 10;
    public static final int DATA_MB_MULT_X100 = 11;
    /** Phase 105.6: Stored cooling water (mB, 0..16,000) in the nuclear coolant tank. */
    public static final int DATA_COOLANT_MB = 12;
    /** Phase 114: Reactor High-Output Overdrive flag (0/1) & emergency cooldown ticks. */
    public static final int DATA_OVERDRIVE = 13;
    public static final int DATA_COOLDOWN = 14;
    public static final int DATA_COUNT = 15;

    /** GUI state of a power device, ordinal used by {@link GenerationMenu}. */
    public enum Status {
        RUNNING,
        IDLE,
        NO_FUEL,
        NO_WATER,
        NIGHT,
        /** Phase 41: geothermal generator without an adjacent lava block. */
        NO_LAVA,
        /** Phase 42: wind turbine with no open block above it (enclosed or buried). */
        NO_AIR,
        /** Phase 103: nuclear multiblock structure not yet formed. */
        INCOMPLETE,
        /** Phase 114: nuclear emergency cooldown after Overdrive coolant depletion. */
        COOLDOWN
    }

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_BURN = "BurnTime";
    private static final String TAG_BURN_TOTAL = "BurnTimeTotal";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_BYPRODUCT = "Byproduct";
    private static final String TAG_BURN_PROGRESS = "BurnProgress";
    private static final String TAG_WATER = "HasWater";
    /** Phase 41: cached lava adjacency of the geothermal generator. */
    private static final String TAG_LAVA = "HasLava";
    private static final String TAG_SKY = "SkyVisible";
    private static final String TAG_WIND_AIR = "WindAir";
    private static final String TAG_WIND_OUTPUT = "WindOutput";
    private static final String TAG_CONDITION_TIMER = "ConditionTimer";
    private static final String TAG_COOLANT_TANK = "CoolantTank";
    private static final String TAG_COOLANT_TIMER = "CoolantTimer";
    private static final String TAG_OVERDRIVE = "OverdriveMode";
    private static final String TAG_COOLDOWN = "OverdriveCooldown";

    private static final Direction[] DIRECTIONS = Direction.values();

    private final GenerationKind kind;
    private final ModEnergyStorage energy;
    private final FuelItemHandler inventory;
    private final FePush push = new FePush();

    private int burnTime;
    private int burnTimeTotal;
    /**
     * FE produced inside the current burn tick (Phase 41). One burn tick is worth
     * {@link GenerationKind#output()} FE of the fuel content, so the clock advances only when
     * that much was actually stored - the fuel's content comes out exactly whether the device
     * runs at its full rating or at half of it (nuclear without cooling water).
     */
    private int burnProgress;
    private long byproduct;
    private int rate;
    private Status status = Status.IDLE;

    private boolean hasWater;
    private boolean hasLava;
    private boolean skyVisible;
    private int windAir = -1;
    private int windOutput;
    private int conditionTimer;

    // Phase 103: Multiblock state (used exclusively when kind == GenerationKind.NUCLEAR)
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
    private int mbCheckTimer = 0;
    private boolean mbCoresLit = false;
    private boolean mbCoresLitDirty = true;
    private final IEnergyStorage portEnergyProxy = new PortExtractEnergyProxy();
    // Phase 105.6: Hybrid water-cooling tank (used exclusively when kind == GenerationKind.NUCLEAR)
    private final FluidTank coolantTank = new FluidTank(
            NuclearCoolantLogic.TANK_CAPACITY_MB, fs -> fs.is(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            if (level != null) {
                level.blockEntityChanged(worldPosition);
                if (!level.isClientSide) {
                    boolean cooledNow = getFluidAmount() > 0;
                    if (cooledNow != lastSyncedWaterCooled) {
                        lastSyncedWaterCooled = cooledNow;
                        BlockState cur = getBlockState();
                        level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    };
    private final IFluidHandler coolantIntakeHandler = new CoolantIntakeFluidHandler();
    private int coolantConsumeTimer = 0;
    private boolean clientWaterCooled = false;
    private boolean lastSyncedWaterCooled = false;
    private boolean clientRunning = false;
    private boolean lastSyncedRunning = false;
    private int clientWindOutput = 0;
    private int lastSyncedWindOutput = -1;
    // Phase 114: Reactor High-Output Overdrive & multiblock formation ritual state
    private boolean overdriveMode = false;
    private int overdriveCooldownTicks = 0;
    private int ritualTicks = 0;
    private boolean clientOverdrive = false;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_BURN -> burnTime;
                case DATA_BURN_TOTAL -> burnTimeTotal;
                case DATA_RATE -> rate;
                case DATA_STATE -> status.ordinal();
                case DATA_MB_FORMED -> mbFormed ? 1 : 0;
                case DATA_MB_REASON -> mbFailReason.ordinal();
                case DATA_MB_DIMS -> ReactorMultiblockLogic.packDims(mbWidth, mbHeight, mbDepth);
                case DATA_MB_CORES -> mbCoreCells;
                case DATA_MB_COOLANT -> mbCoolantBlocks;
                case DATA_MB_MULT_X100 -> (int) Math.min(30_000L, Math.round(mbOutputMult * 100.0D));
                case DATA_COOLANT_MB -> coolantTank.getFluidAmount();
                case DATA_OVERDRIVE -> overdriveMode ? 1 : 0;
                case DATA_COOLDOWN -> overdriveCooldownTicks;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-driven data only; the client receives values through the menu.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public GenerationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATION.get(), pos, state);
        this.kind = ((com.industrialcore.block.GenerationBlock) state.getBlock()).kind();
        this.energy = new ModEnergyStorage(kind.capacity(), 0);
        this.inventory = new FuelItemHandler();
        this.push.init(pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GenerationBlockEntity device) {
        if (level instanceof ServerLevel serverLevel) {
            device.tick(serverLevel);
        }
    }

    /**
     * FE value of one stack in a fuel slot: {@code vanilla burn time * 40}, or the fixed
     * content of a fuel can or fuel rod. Any vanilla-smeltable item is valid fuel, exactly
     * like a furnace.
     */
    public static int fuelContentFe(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ModItems.URANIUM_FUEL_ROD.get())) {
            return GenerationBalance.URANIUM_FUEL_ROD_FE;
        }
        if (stack.is(ModItems.FUEL_CAN.get())) {
            return GenerationBalance.FUEL_CAN_FE;
        }
        if (stack.is(ModItems.FUEL_ROD.get())) {
            return GenerationBalance.FUEL_ROD_FE;
        }
        int burnTime = stack.getBurnTime(RecipeType.SMELTING);
        return burnTime > 0 ? burnTime * GenerationBalance.FUEL_FE_PER_BURN_TICK : 0;
    }

    private void tick(ServerLevel level) {
        boolean changed = false;

        // Phase 103: Nuclear Generator acts as a variable-size (3..5) multiblock reactor controller
        // Phase 105.6: Process manual water bucket slot before multiblock check so players can pre-fill coolant.
        if (kind == GenerationKind.NUCLEAR) {
            changed |= processWaterBucketSlot();

            if (mbCheckTimer <= 0) {
                changed |= revalidateMultiblock(level);
                mbCheckTimer = mbFormed ? 100 + Math.floorMod(worldPosition.hashCode(), 20) : 20;
            }
            mbCheckTimer--;

            if (!mbFormed) {
                rate = 0;
                status = Status.INCOMPLETE;
                updateCoreCellsLit(level, false);
                if (changed) {
                    setChanged();
                }
                return;
            }

            if (ritualTicks > 0) {
                ritualTicks = com.industrialcore.reactor.ReactorRitualHelper.tickRitual(
                        level, worldPosition, mbMinPos, mbMaxPos, false, ritualTicks);
            }

            if (overdriveCooldownTicks > 0) {
                overdriveCooldownTicks--;
                rate = 0;
                status = Status.COOLDOWN;
                updateCoreCellsLit(level, false);
                if (energy.getEnergyStored() > 0) {
                    int pushRate = ReactorMultiblockLogic.scaleIntSaturating(
                            kind.pushPerTick(), mbOutputMult, Integer.MAX_VALUE);
                    push.pushEach(level, energy, pushRate);
                    if (!mbPortPositions.isEmpty() && energy.getEnergyStored() > 0) {
                        pushViaPorts(level, pushRate);
                    }
                }
                level.blockEntityChanged(worldPosition);
                return;
            }
        }

        // Condition caches: water / lava / open air / sky access refresh on a periodic timer,
        // or immediately when a neighbour change invalidated them.
        if ((kind.needsWater() && kind != GenerationKind.NUCLEAR) || kind.needsLava() || kind == GenerationKind.WIND
                || kind == GenerationKind.SOLAR) {
            if (conditionTimer <= 0) {
                changed |= refreshConditions(level);
                conditionTimer = kind.needsWater() || kind.needsLava()
                        ? GenerationBalance.CONDITION_RECHECK_TICKS
                        : GenerationBalance.SKY_RECHECK_TICKS;
            }
            conditionTimer--;
        }

        if (kind.hasFuel()) {
            if (burnTime <= 0 && energy.getEnergyStored() < energy.getMaxEnergyStored()) {
                changed |= tryConsumeFuel();
            }
            rate = 0;
            if (burnTime > 0) {
                // Phase 114: Emergency non-destructive cooldown if Overdrive runs dry on cooling water
                if (kind == GenerationKind.NUCLEAR && com.industrialcore.reactor.ReactorOverdriveLogic
                        .shouldTriggerEmergencyShutdown(overdriveMode, true, coolantTank.getFluidAmount() <= 0)) {
                    overdriveMode = false;
                    overdriveCooldownTicks = com.industrialcore.reactor.ReactorOverdriveLogic.EMERGENCY_COOLDOWN_TICKS;
                    rate = 0;
                    status = Status.COOLDOWN;
                    updateCoreCellsLit(level, false);
                    com.industrialcore.reactor.ReactorRitualHelper.notifyOverdriveEmergencyShutdown(
                            level, worldPosition, false);
                    BlockState cur = getBlockState();
                    level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
                    setChanged();
                    return;
                }
                int baseTarget = ratedOutputBase();
                int target = ratedOutput();
                int produced = energy.generate(target);
                if (produced > 0) {
                    changed = true;
                    // Standard: 1.0x fuel burn; Overdrive: 1.60x fuel burn speed for 1.45x FE/t output.
                    boolean od = (kind == GenerationKind.NUCLEAR && overdriveMode);
                    burnProgress += od
                            ? com.industrialcore.reactor.ReactorOverdriveLogic.burnProgressDelta(baseTarget, true)
                            : produced;
                    int perTick = Math.max(1, baseTarget);
                    while (burnProgress >= perTick && burnTime > 0) {
                        burnProgress -= perTick;
                        burnTime--;
                    }
                    // Phase 105.6 / 114: Consume cooling water (2.0x speed in Overdrive).
                    if (kind == GenerationKind.NUCLEAR && coolantTank.getFluidAmount() > 0) {
                        coolantConsumeTimer++;
                        int interval = com.industrialcore.reactor.ReactorOverdriveLogic.coolantConsumeInterval(
                                NuclearCoolantLogic.WATER_CONSUME_INTERVAL_TICKS, overdriveMode);
                        if (coolantConsumeTimer >= interval) {
                            coolantConsumeTimer = 0;
                            coolantTank.drain(NuclearCoolantLogic.WATER_CONSUME_MB_PER_INTERVAL,
                                    IFluidHandler.FluidAction.EXECUTE);
                        }
                    }
                }
                rate = target;
            } else {
                burnProgress = 0;
                coolantConsumeTimer = 0;
            }
        } else {
            rate = switch (kind) {
                case HYDRO -> hasWater ? kind.output() : 0;
                case WIND -> windOutput;
                case SOLAR -> skyVisible && level.isDay() ? kind.output() : 0;
                case GEOTHERMAL -> hasLava ? kind.output() : 0;
                default -> 0;
            };
            if (rate > 0 && upgradeBank.specials() > 0) {
                double mult = com.industrialcore.machine.UpgradeMath.generatorOutputMultiplier(upgradeBank.specials());
                rate = (int) Math.min(Integer.MAX_VALUE / 2, Math.round(rate * mult));
            }
            if (rate > 0) {
                rate = com.industrialcore.ConfigBalanceHelper.scalePositiveInt(
                        rate, com.industrialcore.Config.generationOutputMultiplier());
                energy.generate(rate);
                changed = true;
            }
        }
        updateStatus(level);
        if (kind == GenerationKind.NUCLEAR) {
            updateCoreCellsLit(level, burnTime > 0 && mbFormed);
        } else {
            boolean runningNow = status == Status.RUNNING && rate > 0;
            boolean windChanged = (kind == GenerationKind.WIND && windOutput != lastSyncedWindOutput);
            if (runningNow != lastSyncedRunning || windChanged) {
                lastSyncedRunning = runningNow;
                lastSyncedWindOutput = windOutput;
                BlockState cur = getBlockState();
                level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
            }
        }

        if (energy.getEnergyStored() > 0) {
            int pushRate = kind == GenerationKind.NUCLEAR
                    ? ReactorMultiblockLogic.scaleIntSaturating(kind.pushPerTick(), mbOutputMult, Integer.MAX_VALUE)
                    : kind.pushPerTick();
            changed |= push.pushEach(level, energy, pushRate);
            if (kind == GenerationKind.NUCLEAR && !mbPortPositions.isEmpty() && energy.getEnergyStored() > 0) {
                changed |= pushViaPorts(level, pushRate);
            }
        }
        if (changed) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private int ratedOutputBase() {
        if (kind == GenerationKind.NUCLEAR) {
            if (!mbFormed) {
                return 0;
            }
            return ReactorMultiblockLogic.scaleIntSaturating(kind.output(), mbOutputMult, Integer.MAX_VALUE);
        }
        return kind.output();
    }

    private int ratedOutput() {
        int base = ratedOutputBase();
        if (kind == GenerationKind.NUCLEAR && mbFormed) {
            base = NuclearCoolantLogic.effectiveOutput(base, coolantTank.getFluidAmount());
            if (overdriveCooldownTicks > 0) {
                return 0;
            }
            if (overdriveMode) {
                base = com.industrialcore.ConfigBalanceHelper.scalePositiveInt(
                        base, com.industrialcore.Config.overdriveOutputMultiplier());
            }
        }
        if (upgradeBank.specials() > 0) {
            double mult = com.industrialcore.machine.UpgradeMath.generatorOutputMultiplier(upgradeBank.specials());
            base = (int) Math.min(Integer.MAX_VALUE / 2, Math.round(base * mult));
        }
        return com.industrialcore.ConfigBalanceHelper.scalePositiveInt(
                base, com.industrialcore.Config.generationOutputMultiplier());
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

    public void toggleOverdriveMode(ServerLevel level, @Nullable net.minecraft.server.level.ServerPlayer player) {
        if (kind != GenerationKind.NUCLEAR) {
            return;
        }
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

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank getUpgradeBank() {
        return upgradeBank;
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory getSpecialCategory() {
        return com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory.GENERATOR;
    }

    @Override
    public String getUpgradeMachineId() {
        return kind.id();
    }

    @Override
    public int getTotalUpgradeLimit() {
        return com.industrialcore.level.MachineLevelMath.NORMAL_MAX_TOTAL_UPGRADE_CAP;
    }

    @Override
    public void onUpgradeBankChanged() {
        int cap = com.industrialcore.machine.UpgradeMath.scaledCapacity(kind.capacity(), upgradeBank.batteries());
        energy.setCapacity(cap);
        setChanged();
    }

    private boolean processWaterBucketSlot() {
        if (inventory.getSlots() <= BUCKET_SLOT) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(BUCKET_SLOT);
        if (stack.is(Items.WATER_BUCKET)
                && NuclearCoolantLogic.canAcceptWaterBucket(coolantTank.getFluidAmount())) {
            int filled = coolantTank.fill(
                    new FluidStack(Fluids.WATER, NuclearCoolantLogic.BUCKET_MB),
                    IFluidHandler.FluidAction.EXECUTE);
            if (filled >= NuclearCoolantLogic.BUCKET_MB) {
                inventory.setStackInSlot(BUCKET_SLOT, new ItemStack(Items.BUCKET));
                return true;
            }
        }
        return false;
    }

    private void updateStatus(Level level) {
        status = switch (kind) {
            case HYDRO -> hasWater ? Status.RUNNING : Status.NO_WATER;
            case WIND -> windAir > 0 ? Status.RUNNING : Status.NO_AIR;
            case SOLAR -> !skyVisible ? Status.IDLE
                    : level.isDay() ? Status.RUNNING : Status.NIGHT;
            case GEOTHERMAL -> hasLava ? Status.RUNNING : Status.NO_LAVA;
            case NUCLEAR -> !mbFormed ? Status.INCOMPLETE
                    : (burnTime > 0 ? Status.RUNNING
                    : (fuelAvailable() ? Status.IDLE : Status.NO_FUEL));
            default -> burnTime > 0 ? Status.RUNNING
                    : (fuelAvailable() ? Status.IDLE : Status.NO_FUEL);
        };
    }

    private boolean fuelAvailable() {
        return !inventory.getStackInSlot(FUEL_SLOT).isEmpty();
    }

    /** Re-validates water, wind openness and sky access against the current level state. */
    private boolean refreshConditions(ServerLevel level) {
        boolean changed = false;
        if (kind.needsWater()) {
            boolean water = false;
            for (Direction direction : DIRECTIONS) {
                if (level.getBlockState(worldPosition.relative(direction))
                        .getFluidState().is(FluidTags.WATER)) {
                    water = true;
                    break;
                }
            }
            if (water != hasWater) {
                hasWater = water;
                changed = true;
            }
        }
        if (kind.needsLava()) {
            // Vanilla lava only: checking the block itself keeps other mods' lava fluids out.
            boolean lava = false;
            for (Direction direction : DIRECTIONS) {
                if (level.getBlockState(worldPosition.relative(direction)).is(Blocks.LAVA)) {
                    lava = true;
                    break;
                }
            }
            if (lava != hasLava) {
                hasLava = lava;
                changed = true;
            }
        }
        if (kind == GenerationKind.WIND) {
            int open = 0;
            BlockPos above = worldPosition.above();
            for (int step = 0; step < GenerationBalance.WIND_AIR_COLUMNS; step++) {
                if (level.getBlockState(above.relative(Direction.UP, step)).isAir()) {
                    open++;
                }
            }
            if (open != windAir) {
                windAir = open;
                windOutput = GenerationBalance.windOutput(open);
                changed = true;
            }
        }
        if (kind == GenerationKind.SOLAR) {
            boolean sky = level.canSeeSky(worldPosition.above());
            if (sky != skyVisible) {
                skyVisible = sky;
                changed = true;
            }
        }
        return changed;
    }

    /** A neighbour changed: re-validate conditions on the next tick. */
    public void invalidateConditions() {
        this.conditionTimer = 0;
        if (kind == GenerationKind.NUCLEAR) {
            this.mbCheckTimer = 0;
        }
    }

    // -------------------------------------------------------------------------
    // Phase 103: Nuclear Multiblock Controller lifecycle & port delegation
    // -------------------------------------------------------------------------

    public boolean isMultiblockFormed() {
        return kind == GenerationKind.NUCLEAR && mbFormed;
    }

    public FailReason getMultiblockFailReason() {
        return mbFailReason;
    }

    public int getMultiblockDimsPacked() {
        return ReactorMultiblockLogic.packDims(mbWidth, mbHeight, mbDepth);
    }

    public int getMultiblockCoreCells() {
        return mbCoreCells;
    }

    public int getMultiblockCoolantBlocks() {
        return mbCoolantBlocks;
    }

    public int getMultiblockMultX100() {
        return (int) Math.min(30_000L, Math.round(mbOutputMult * 100.0D));
    }

    /** Extract-capable IEnergyStorage view exposed through linked {@code reactor_port} blocks. */
    public IEnergyStorage getPortEnergyStorage() {
        return portEnergyProxy;
    }

    /** Phase 105.6: Fill-only water intake handler for pipe/port cooling water delivery. */
    public IFluidHandler getCoolantFluidHandler() {
        return coolantIntakeHandler;
    }

    /** Phase 105.6: Current stored cooling water volume in mB (0..16,000). */
    public int getCoolantMb() {
        return coolantTank.getFluidAmount();
    }

    /** Immediately tears down multiblock links & halts generation when a structure block breaks. */
    public void onMultiblockBroken(ServerLevel level) {
        if (kind != GenerationKind.NUCLEAR) {
            return;
        }
        disassembleMultiblock(level, FailReason.INCOMPLETE_FRAME);
        mbCheckTimer = 0;
        setChanged();
    }

    /** Forces immediate validation (useful for server logic & verification). */
    public boolean revalidateMultiblock(ServerLevel level) {
        if (kind != GenerationKind.NUCLEAR) {
            return false;
        }
        Direction facing = getBlockState().hasProperty(GenerationBlock.FACING)
                ? getBlockState().getValue(GenerationBlock.FACING)
                : Direction.NORTH;
        ValidationResult res = ReactorWorldHelper.validateInWorld(
                level, ReactorType.NUCLEAR, worldPosition, facing);

        if (!res.formed()) {
            boolean wasFormed = mbFormed || mbFailReason != res.reason();
            disassembleMultiblock(level, res.reason());
            return wasFormed;
        }

        // Unlink any ports no longer part of this structure
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
        // Turn off LIT on any removed core cells
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
                    com.industrialcore.level.AegisAdvancements.NUCLEAR_REACTOR);
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
            lastSyncedWaterCooled = coolantTank.getFluidAmount() > 0;
            BlockState cur = getBlockState();
            level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        }

        // Register all blocks in the level ownership table
        Set<BlockPos> claimed = new HashSet<>(res.allPositions().size());
        for (IntPos p : res.allPositions()) {
            claimed.add(new BlockPos(p.x(), p.y(), p.z()));
        }
        ReactorMultiblockManager.registerFormed(level, worldPosition, claimed);

        // Link all ports to this controller
        for (BlockPos portPos : mbPortPositions) {
            if (level.isLoaded(portPos)
                    && level.getBlockEntity(portPos) instanceof ReactorPortBlockEntity portBe) {
                portBe.linkTo(worldPosition);
            }
        }

        // Scale internal FE capacity cleanly without ever discarding stored FE
        int scaledCap = ReactorMultiblockLogic.scaleIntSaturating(
                kind.capacity(), mbCapacityMult, ReactorMultiblockLogic.NUCLEAR_MAX_INT_CAPACITY);
        if (energy.getMaxEnergyStored() != scaledCap) {
            energy.setCapacity(Math.max(scaledCap, energy.getEnergyStored()));
            changed = true;
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
        rate = 0;
        status = Status.INCOMPLETE;
        if (wasFormed) {
            BlockState cur = getBlockState();
            level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        }
    }

    private void updateCoreCellsLit(ServerLevel level, boolean lit) {
        BlockState selfState = getBlockState();
        if (selfState.hasProperty(com.industrialcore.block.NuclearGeneratorBlock.LIT)
                && selfState.getValue(com.industrialcore.block.NuclearGeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition,
                    selfState.setValue(com.industrialcore.block.NuclearGeneratorBlock.LIT, lit),
                    Block.UPDATE_CLIENTS);
        }
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

    private boolean pushViaPorts(ServerLevel level, int maxPerSide) {
        boolean moved = false;
        for (BlockPos portPos : mbPortPositions) {
            if (energy.getEnergyStored() <= 0) {
                break;
            }
            for (Direction dir : DIRECTIONS) {
                if (energy.getEnergyStored() <= 0) {
                    break;
                }
                BlockPos target = portPos.relative(dir);
                if (isInsideBounds(target) || !level.isLoaded(target)) {
                    continue;
                }
                IEnergyStorage sink = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, target, dir.getOpposite());
                if (sink == null || !sink.canReceive()) {
                    continue;
                }
                int offer = Math.min(maxPerSide, energy.getEnergyStored());
                if (offer <= 0) {
                    continue;
                }
                int accepted = sink.receiveEnergy(offer, false);
                if (accepted > 0) {
                    energy.consume(accepted);
                    moved = true;
                }
            }
        }
        return moved;
    }

    private final class PortExtractEnergyProxy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (!mbFormed || toExtract <= 0 || energy.getEnergyStored() <= 0) {
                return 0;
            }
            int limit = ReactorMultiblockLogic.scaleIntSaturating(
                    kind.pushPerTick(), mbOutputMult, Integer.MAX_VALUE);
            int taken = Math.min(energy.getEnergyStored(), Math.min(toExtract, limit));
            if (taken > 0 && !simulate) {
                energy.consume(taken);
                setChanged();
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return mbFormed;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    private boolean tryConsumeFuel() {
        ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
        int content = fuelContentFe(fuel);
        if (content <= 0) {
            return false;
        }
        int ticks = GenerationBalance.burnTicksFor(content, kind.output());
        if (kind == GenerationKind.NUCLEAR) {
            ticks = ReactorMultiblockLogic.scaleIntSaturating(ticks, mbBurnEffMult, Integer.MAX_VALUE);
        }
        if (ticks <= 0) {
            return false;
        }
        if (fuel.hasCraftingRemainingItem()) {
            inventory.setStackInSlot(FUEL_SLOT, fuel.getCraftingRemainingItem());
        } else {
            fuel.shrink(1);
        }
        burnTime = ticks;
        burnTimeTotal = ticks;
        burnProgress = 0;
        if (kind.byproduct() != GenerationKind.Byproduct.NONE) {
            // One fuel load pools at most its own byproduct share; a leftover share of the
            // previous load vents when the next fuel starts (recovery devices drain every
            // tick, so in practice nothing is lost).
            byproduct = GenerationBalance.byproductFor(content);
        }
        return true;
    }

    public GenerationKind kind() {
        return this.kind;
    }

    /** True when this device has a fuel slot (and therefore an item capability). */
    public boolean hasFuel() {
        return this.kind.hasFuel();
    }

    /** Phase 41: true while a vanilla lava block touches the geothermal generator. */
    public boolean hasLava() {
        return this.hasLava;
    }

    public ModEnergyStorage getEnergyStorage() {
        return this.energy;
    }

    public IItemHandlerModifiable getInventory() {
        return this.inventory;
    }

    public int getBurnTime() {
        return this.burnTime;
    }

    public int getBurnTimeTotal() {
        return this.burnTimeTotal;
    }

    /** Pooled exhaust / waste heat units of the current fuel load (FE-equivalent). */
    public long getByproduct() {
        return this.byproduct;
    }

    /** Removes up to {@code max} pooled units and returns what was actually taken. */
    public long takeByproduct(long max) {
        long taken = Math.min(this.byproduct, Math.max(0L, max));
        this.byproduct -= taken;
        return taken;
    }

    public Status getStatus() {
        return this.status;
    }

    public int getRate() {
        return this.rate;
    }

    /** Drops the fuel & nuclear bucket slot contents when the block is broken. */
    public void dropContents() {
        if (kind.hasFuel()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack st = inventory.getStackInSlot(slot);
                if (!st.isEmpty()) {
                    Block.popResource(level, worldPosition, st.copy());
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_BURN, burnTime);
        tag.putInt(TAG_BURN_TOTAL, burnTimeTotal);
        tag.putInt(TAG_BURN_PROGRESS, burnProgress);
        tag.putLong(TAG_BYPRODUCT, byproduct);
        tag.putBoolean(TAG_WATER, hasWater);
        tag.putBoolean(TAG_LAVA, hasLava);
        tag.putBoolean(TAG_SKY, skyVisible);
        tag.putInt(TAG_WIND_AIR, windAir);
        tag.putInt(TAG_WIND_OUTPUT, windOutput);
        tag.putInt(TAG_CONDITION_TIMER, conditionTimer);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        upgradeBank.save(tag);
        if (kind == GenerationKind.NUCLEAR) {
            tag.put(TAG_COOLANT_TANK, coolantTank.writeToNBT(registries, new CompoundTag()));
            tag.putInt(TAG_COOLANT_TIMER, coolantConsumeTimer);
            tag.putBoolean(TAG_OVERDRIVE, overdriveMode);
            tag.putInt(TAG_COOLDOWN, overdriveCooldownTicks);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CAPACITY)) {
            this.energy.setCapacity(tag.getInt(TAG_CAPACITY));
        }
        this.energy.setEnergy(tag.getInt(TAG_ENERGY));
        this.burnTime = tag.getInt(TAG_BURN);
        this.burnTimeTotal = tag.getInt(TAG_BURN_TOTAL);
        this.burnProgress = tag.getInt(TAG_BURN_PROGRESS);
        this.byproduct = tag.getLong(TAG_BYPRODUCT);
        this.hasWater = tag.getBoolean(TAG_WATER);
        this.hasLava = tag.getBoolean(TAG_LAVA);
        this.skyVisible = tag.getBoolean(TAG_SKY);
        this.windAir = tag.getInt(TAG_WIND_AIR);
        this.windOutput = tag.getInt(TAG_WIND_OUTPUT);
        this.conditionTimer = tag.getInt(TAG_CONDITION_TIMER);
        inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        upgradeBank.load(tag);
        if (upgradeBank.batteries() > 0) {
            energy.setCapacity(com.industrialcore.machine.UpgradeMath.scaledCapacity(kind.capacity(), upgradeBank.batteries()));
        }
        // Backward-compatible upgrade: old saves stored size 1 for nuclear_generator.
        if (kind == GenerationKind.NUCLEAR && inventory.getSlots() < 2) {
            ItemStack savedFuel = inventory.getSlots() > 0 ? inventory.getStackInSlot(FUEL_SLOT).copy() : ItemStack.EMPTY;
            inventory.setSize(2);
            inventory.setStackInSlot(FUEL_SLOT, savedFuel);
        }
        if (kind == GenerationKind.NUCLEAR) {
            if (tag.contains(TAG_COOLANT_TANK)) {
                coolantTank.readFromNBT(registries, tag.getCompound(TAG_COOLANT_TANK));
            }
            this.coolantConsumeTimer = tag.getInt(TAG_COOLANT_TIMER);
            this.overdriveMode = tag.getBoolean(TAG_OVERDRIVE);
            this.overdriveCooldownTicks = Math.max(0, tag.getInt(TAG_COOLDOWN));
            this.lastSyncedWaterCooled = coolantTank.getFluidAmount() > 0;
        }
    }

    // Phase 109–110: Lightweight client sync for nuclear/wind hero BERs & generator particles
    public boolean isClientWaterCooled() {
        return clientWaterCooled;
    }

    public boolean isClientRunning() {
        return clientRunning;
    }

    public int getClientWindOutput() {
        return clientWindOutput;
    }

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
        tag.putBoolean("GenRunning", status == Status.RUNNING && rate > 0);
        if (kind == GenerationKind.WIND) {
            tag.putInt("WindOut", windOutput);
        }
        if (kind == GenerationKind.NUCLEAR) {
            tag.putBoolean("MbFormed", mbFormed);
            tag.putBoolean("WaterCooled", coolantTank.getFluidAmount() > 0);
            tag.putBoolean("Overdrive", overdriveMode && overdriveCooldownTicks <= 0);
            if (mbFormed && mbMinPos != null && mbMaxPos != null) {
                tag.putInt("MbMinX", mbMinPos.x());
                tag.putInt("MbMinY", mbMinPos.y());
                tag.putInt("MbMinZ", mbMinPos.z());
                tag.putInt("MbMaxX", mbMaxPos.x());
                tag.putInt("MbMaxY", mbMaxPos.y());
                tag.putInt("MbMaxZ", mbMaxPos.z());
            }
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientRunning = tag.getBoolean("GenRunning");
        if (kind == GenerationKind.WIND) {
            clientWindOutput = Math.max(0, tag.getInt("WindOut"));
            return;
        }
        if (kind != GenerationKind.NUCLEAR) {
            return;
        }
        mbFormed = tag.getBoolean("MbFormed");
        clientWaterCooled = tag.getBoolean("WaterCooled");
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, lookupProvider);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(kind.translationKey());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GenerationMenu(containerId, playerInventory, this, dataAccess);
    }

    private final class FuelItemHandler extends ItemStackHandler {
        private FuelItemHandler() {
            super(kind == GenerationKind.NUCLEAR ? 2 : 1);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == BUCKET_SLOT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == BUCKET_SLOT) {
                return kind == GenerationKind.NUCLEAR && stack.is(Items.WATER_BUCKET);
            }
            return fuelContentFe(stack) > 0;
        }
    }

    /** Phase 105.6: Fill-only water intake wrapper so pipes pump water in without draining it back out. */
    private final class CoolantIntakeFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return coolantTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return coolantTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (kind != GenerationKind.NUCLEAR || !resource.is(Fluids.WATER)) {
                return 0;
            }
            return coolantTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}