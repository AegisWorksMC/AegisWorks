package com.industrialcore.blockentity;

import com.industrialcore.energy.FePush;
import com.industrialcore.energy.FlywheelStorage;
import com.industrialcore.energy.GenerationBalance;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.RecoveryKind;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Shared block entity of the four energy recovery devices (Phase 33): flywheel, pressure
 * recovery, waste heat recovery and regenerative generator. The kind arrives from the block
 * instance, so one implementation serves all four.
 *
 * <p>None of these devices creates energy:</p>
 * <ul>
 *   <li>The flywheel only stores grid FE slowly and bursts it back (peak shaving).</li>
 *   <li>Pressure recovery converts the exhaust pool an adjacent thermal/nuclear generator
 *       already budgeted at 85% of its fuel, capped at 120 FE/t.</li>
 *   <li>Waste heat recovery converts heat that is a percentage of FE adjacent machines
 *       already spent, plus a combustion generator's heat pool, at 85%, capped at 100 FE/t.</li>
 *   <li>The regenerative generator credits a small percentage of a machine's spend when it
 *       stops and feeds it back, capped at 40 FE/t across all faces.</li>
 * </ul>
 *
 * <p>Neighbour polling uses the same state-identity caching as {@link FePush}: neighbours are
 * only re-read when their block state changes or after a chunk reload, so polling six
 * machines per tick costs six state reads, no capability lookups.</p>
 */
public class RecoveryBlockEntity extends BlockEntity implements MenuProvider {
    // GUI data indices mirror GenerationBlockEntity (burn fields stay 0 for recovery devices).
    private static final int DATA_ENERGY = GenerationBlockEntity.DATA_ENERGY;
    private static final int DATA_CAPACITY = GenerationBlockEntity.DATA_CAPACITY;
    private static final int DATA_RATE = GenerationBlockEntity.DATA_RATE;
    private static final int DATA_STATE = GenerationBlockEntity.DATA_STATE;
    private static final int DATA_COUNT = GenerationBlockEntity.DATA_COUNT;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_HEAT_POOL = "HeatPool";
    private static final String TAG_REGEN_POOL = "RegenPool";

    private static final Direction[] DIRECTIONS = Direction.values();

    private final RecoveryKind kind;
    private final ModEnergyStorage energy;
    private final FePush push = new FePush();
    private final BlockPos[] neighbours = new BlockPos[DIRECTIONS.length];

    // Neighbour observation cache (state-identity invalidation, reset on chunk reload).
    private final BlockState[] neighbourStates = new BlockState[DIRECTIONS.length];
    private final BlockEntity[] neighbourBes = new BlockEntity[DIRECTIONS.length];
    private final IEnergyStorage[] neighbourStorages = new IEnergyStorage[DIRECTIONS.length];
    private final long[] lastSeen = new long[DIRECTIONS.length];
    private final long[] activeSpend = new long[DIRECTIONS.length];

    private long heatPool;
    private long regenPool;
    private int rate;
    private GenerationBlockEntity.Status status = GenerationBlockEntity.Status.IDLE;
    private boolean clientRunning = false;
    private boolean lastSyncedRunning = false;
    private int clientChargePct = 0;
    private int lastSyncedChargePct = -1;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_RATE -> rate;
                case DATA_STATE -> status.ordinal();
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

    public RecoveryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECOVERY.get(), pos, state);
        this.kind = ((com.industrialcore.block.RecoveryBlock) state.getBlock()).kind();
        this.energy = kind == RecoveryKind.FLYWHEEL
                ? new FlywheelStorage(kind.capacity(), FLYWHEEL_RECEIVE_PER_TICK, this::onEnergyChanged)
                : new ModEnergyStorage(kind.capacity(), 0);
        this.push.init(pos);
        for (int index = 0; index < DIRECTIONS.length; index++) {
            neighbours[index] = pos.relative(DIRECTIONS[index]);
            lastSeen[index] = -1L;
        }
    }

    private static final int FLYWHEEL_RECEIVE_PER_TICK = 200;

    private void onEnergyChanged() {
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RecoveryBlockEntity device) {
        if (level instanceof ServerLevel serverLevel) {
            device.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        boolean changed = false;
        switch (kind) {
            case FLYWHEEL -> {
                // Charging happens through the capability (FlywheelStorage); here we only
                // report the design burst rate for the GUI.
                rate = kind.maxOutput();
                status = energy.getEnergyStored() > 0
                        ? GenerationBlockEntity.Status.RUNNING
                        : GenerationBlockEntity.Status.IDLE;
            }
            case PRESSURE -> {
                rate = convertExhaust(level);
                changed |= rate > 0;
                status = rate > 0
                        ? GenerationBlockEntity.Status.RUNNING
                        : GenerationBlockEntity.Status.IDLE;
            }
            case HEAT -> {
                pollMachineSpend(level, true);
                rate = convertHeat(level);
                changed |= rate > 0 || heatChangedByPoll;
                heatChangedByPoll = false;
                status = rate > 0
                        ? GenerationBlockEntity.Status.RUNNING
                        : GenerationBlockEntity.Status.IDLE;
            }
            case REGEN -> {
                pollMachineSpend(level, false);
                rate = convertRegen();
                changed |= rate > 0 || regenChangedByPoll;
                regenChangedByPoll = false;
                status = rate > 0
                        ? GenerationBlockEntity.Status.RUNNING
                        : GenerationBlockEntity.Status.IDLE;
            }
        }
        if (energy.getEnergyStored() > 0) {
            if (kind.isSharedPush()) {
                changed |= push.pushShared(level, energy, kind.pushPerTick());
            } else {
                changed |= push.pushEach(level, energy, kind.pushPerTick());
            }
        }
        if (changed) {
            setChanged();
        }
        boolean runningNow = status == GenerationBlockEntity.Status.RUNNING;
        int chargePctNow = computeQuantizedChargePct();
        if (runningNow != lastSyncedRunning || chargePctNow != lastSyncedChargePct) {
            lastSyncedRunning = runningNow;
            lastSyncedChargePct = chargePctNow;
            BlockState cur = getBlockState();
            level.sendBlockUpdated(worldPosition, cur, cur, Block.UPDATE_CLIENTS);
        }
    }

    private int computeQuantizedChargePct() {
        if (kind != RecoveryKind.FLYWHEEL) {
            return 0;
        }
        int cap = energy.getMaxEnergyStored();
        int stored = energy.getEnergyStored();
        if (cap <= 0 || stored <= 0) {
            return 0;
        }
        int pct = (int) Math.round((stored * 100.0D) / cap);
        // Quantize to 10% bands (at least 10% whenever stored > 0) to avoid frequent packet traffic
        return Math.max(10, Math.min(100, ((pct + 5) / 10) * 10));
    }

    // Set by the polling loop when a credit was added to the corresponding pool.
    private boolean heatChangedByPoll;
    private boolean regenChangedByPoll;

    /**
     * Drains the pooled exhaust of adjacent thermal/nuclear generators and converts it at
     * {@link GenerationBalance#RECOVERY_CONVERSION_PERCENT}%, at most
     * {@link RecoveryKind#maxOutput()} FE per tick, never more than the buffer can hold.
     */
    private int convertExhaust(ServerLevel level) {
        int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        if (room <= 0) {
            return 0;
        }
        long maxUnits = (long) Math.min(kind.maxOutput(), room) * 100
                / GenerationBalance.RECOVERY_CONVERSION_PERCENT;
        long units = 0L;
        for (int index = 0; index < DIRECTIONS.length && units < maxUnits; index++) {
            if (refreshNeighbour(level, index) instanceof GenerationBlockEntity source
                    && source.kind().byproduct() == GenerationKind.Byproduct.EXHAUST) {
                units += source.takeByproduct(maxUnits - units);
            }
        }
        if (units <= 0L) {
            return 0;
        }
        int fe = (int) (units * GenerationBalance.RECOVERY_CONVERSION_PERCENT / 100);
        if (fe <= 0) {
            return 0;
        }
        energy.generate(fe);
        return fe;
    }

    /**
     * Polls adjacent work machines' spend counters, then converts the accumulated heat pool
     * plus any combustion generator's heat pool at 85%, at most {@link RecoveryKind#maxOutput()}
     * FE per tick. Heat credit itself is a percentage of FE the machine already spent.
     */
    private int convertHeat(ServerLevel level) {
        int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        if (room <= 0) {
            return 0;
        }
        long generatorUnits = 0L;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (neighbourBes[index] instanceof GenerationBlockEntity source
                    && source.kind().byproduct() == GenerationKind.Byproduct.HEAT) {
                generatorUnits += source.getByproduct();
            }
        }
        long available = heatPool + generatorUnits;
        if (available <= 0L) {
            return 0;
        }
        long maxUnits = (long) Math.min(kind.maxOutput(), room) * 100
                / GenerationBalance.RECOVERY_CONVERSION_PERCENT;
        long take = Math.min(available, maxUnits);
        long fromPool = Math.min(heatPool, take);
        heatPool -= fromPool;
        long fromGenerators = take - fromPool;
        for (int index = 0; index < DIRECTIONS.length && fromGenerators > 0L; index++) {
            if (neighbourBes[index] instanceof GenerationBlockEntity source
                    && source.kind().byproduct() == GenerationKind.Byproduct.HEAT) {
                fromGenerators -= source.takeByproduct(fromGenerators);
            }
        }
        // fromGenerators holds the still-unclaimed remainder after the loop, so the units
        // actually converted are `take - remainder`; adding fromPool again would bill the
        // pool twice (Phase 34.1 audit fix).
        long totalUnits = take - fromGenerators;
        if (totalUnits <= 0L) {
            return 0;
        }
        int fe = (int) (totalUnits * GenerationBalance.RECOVERY_CONVERSION_PERCENT / 100);
        if (fe <= 0) {
            return 0;
        }
        energy.generate(fe);
        return fe;
    }

    /**
     * Converts accumulated regenerative credits, at most {@link RecoveryKind#maxOutput()} FE
     * per tick and only what the buffer can hold.
     */
    private int convertRegen() {
        if (regenPool <= 0L) {
            return 0;
        }
        int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        int take = (int) Math.min(regenPool, Math.min(kind.maxOutput(), room));
        if (take <= 0) {
            return 0;
        }
        regenPool -= take;
        energy.generate(take);
        return take;
    }

    /**
     * Observes adjacent work machines' consumed-FE counters.
     *
     * <p>In heat mode each tick's spend difference is credited as heat (15% of FE the machine
     * already spent). In regen mode the spend of a continuous run is remembered and credited
     * once when the difference stops (5%) - regenerative braking on stop, so a machine that
     * never stops returns nothing while it works.</p>
     */
    private void pollMachineSpend(ServerLevel level, boolean heatMode) {
        for (int index = 0; index < DIRECTIONS.length; index++) {
            BlockEntity neighbour = refreshNeighbour(level, index);
            ModEnergyStorage machine = ModEnergyStorage.ownStorage(neighbourStorages[index]);
            if (neighbour == null || !isWorkMachine(neighbour) || machine == null) {
                continue;
            }
            long counter = machine.getConsumedTotal();
            if (lastSeen[index] < 0L) {
                lastSeen[index] = counter;
                continue;
            }
            long delta = counter - lastSeen[index];
            if (delta > 0L) {
                if (heatMode) {
                    heatPool += delta * GenerationBalance.MACHINE_HEAT_PERCENT / 100;
                    if (heatPool > GenerationBalance.HEAT_POOL_CAP) {
                        // Excess heat dissipates instead of accumulating forever.
                        heatPool = GenerationBalance.HEAT_POOL_CAP;
                    }
                    heatChangedByPoll = true;
                } else {
                    activeSpend[index] += delta;
                }
                lastSeen[index] = counter;
            } else if (delta < 0L) {
                // Machine reloaded mid-run: reset observation without crediting (never free FE).
                lastSeen[index] = counter;
                activeSpend[index] = 0L;
            } else if (heatMode) {
                // No spend this tick; lastSeen is already current.
            } else if (activeSpend[index] > 0L) {
                // The machine stopped: credit a percentage of what it spent in this run.
                regenPool += activeSpend[index] * GenerationBalance.REGEN_PERCENT / 100;
                if (regenPool > kind.capacity()) {
                    regenPool = kind.capacity();
                }
                activeSpend[index] = 0L;
                regenChangedByPoll = true;
            }
        }
    }

    /**
     * The recovery devices are never damaged in gameplay, but the pattern exists for
     * clarity: a work machine is any FE-consuming production block (not generators, not
     * buffers, not transporters - their "spend" is transfer, not consumption).
     */
    private static boolean isWorkMachine(BlockEntity be) {
        return be instanceof ProcessingBlockEntity
                || be instanceof OreSynthesizerBlockEntity
                || be instanceof ReconstructorBlockEntity
                || be instanceof CrafterBlockEntity
                || be instanceof AssemblerBlockEntity
                || be instanceof QuarryBlockEntity
                || be instanceof LargeQuarryBlockEntity
                || be instanceof QuantumCompressorBlockEntity
                || be instanceof PhaseReactorBlockEntity
                || be instanceof SeedCultivatorBlockEntity
                || be instanceof MatterAmplifierBlockEntity;
    }

    /**
     * Returns the neighbour's block entity, refreshing the observation cache when its block
     * state differs (placed/changed block) or after a chunk reload (cache nulled on unload).
     */
    private BlockEntity refreshNeighbour(ServerLevel level, int index) {
        BlockPos neighbourPos = neighbours[index];
        if (!level.isLoaded(neighbourPos)) {
            clearNeighbour(index);
            return null;
        }
        BlockState state = level.getBlockState(neighbourPos);
        if (state != neighbourStates[index]) {
            neighbourStates[index] = state;
            BlockEntity be = level.getBlockEntity(neighbourPos);
            neighbourBes[index] = be;
            // A foreign FE neighbour may implement IEnergyStorage differently; never cast.
            neighbourStorages[index] = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, neighbourPos, null);
            lastSeen[index] = -1L;
            activeSpend[index] = 0L;
        }
        return neighbourBes[index];
    }

    private void clearNeighbour(int index) {
        neighbourStates[index] = null;
        neighbourBes[index] = null;
        neighbourStorages[index] = null;
        lastSeen[index] = -1L;
        activeSpend[index] = 0L;
    }

    public RecoveryKind kind() {
        return this.kind;
    }

    public ModEnergyStorage getEnergyStorage() {
        return this.energy;
    }

    /** Accumulated machine-heat units waiting for conversion (heat mode only). */
    public long getHeatPool() {
        return this.heatPool;
    }

    /** Accumulated regenerative credits waiting for conversion (regen mode only). */
    public long getRegenPool() {
        return this.regenPool;
    }

    public GenerationBlockEntity.Status getStatus() {
        return this.status;
    }

    public int getRate() {
        return this.rate;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putLong(TAG_HEAT_POOL, heatPool);
        tag.putLong(TAG_REGEN_POOL, regenPool);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy.setEnergy(tag.getInt(TAG_ENERGY));
        if (tag.contains(TAG_CAPACITY)) {
            this.energy.setCapacity(tag.getInt(TAG_CAPACITY));
        }
        this.heatPool = tag.getLong(TAG_HEAT_POOL);
        this.regenPool = tag.getLong(TAG_REGEN_POOL);
    }

    // Phase 110: Lightweight client sync for Flywheel BER & Recovery particles
    public boolean isClientRunning() {
        return clientRunning;
    }

    public float getClientChargeRatio() {
        return clientChargePct / 100.0F;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("RecRunning", status == GenerationBlockEntity.Status.RUNNING);
        if (kind == RecoveryKind.FLYWHEEL) {
            tag.putInt("ChargePct", computeQuantizedChargePct());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientRunning = tag.getBoolean("RecRunning");
        if (kind == RecoveryKind.FLYWHEEL) {
            clientChargePct = Math.max(0, Math.min(100, tag.getInt("ChargePct")));
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
}