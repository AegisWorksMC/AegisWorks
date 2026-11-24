package com.industrialcore.blockentity;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.FactoryStats;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.menu.MonitorMenu;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Factory monitor (Phase 82).
 *
 * <p>Once per second it walks every loaded block entity within {@link FactoryStats#SCAN_RADIUS}
 * blocks, counts what each machine is doing, sums the FE buffers through the energy capability and
 * notes stuck pipes and full outputs. The result is a {@link FactoryStats} snapshot that is synced
 * to the client with the ordinary block entity update tag; the panel only renders it.</p>
 *
 * <p>The scan is deliberately outside the machine tick: a monitor never runs a factory rule, never
 * inserts or extracts anything, and cannot loop through pipes. A large base therefore costs a
 * bounded chunk walk once per second per monitor.</p>
 */
public class MonitorBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_STATS = "Stats";
    private static final String TAG_MACHINES = "Machines";
    private static final String TAG_WORKING = "Working";
    private static final String TAG_IDLE = "Idle";
    private static final String TAG_STOPPED = "Stopped";
    private static final String TAG_GENERATORS = "Generators";
    private static final String TAG_GENERATORS_RUNNING = "GeneratorsRunning";
    private static final String TAG_PIPES = "Pipes";
    private static final String TAG_BLOCKED = "BlockedPipes";
    private static final String TAG_FULL = "FullOutputs";
    private static final String TAG_STORED = "Stored";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_TREND = "Trend";

    private FactoryStats stats = FactoryStats.EMPTY;
    private int scanCooldown;
    private long lastStored;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY_MONITOR.get(), pos, state);
        this.scanCooldown = Math.floorMod(pos.hashCode(), FactoryStats.SCAN_INTERVAL_TICKS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MonitorBlockEntity monitor) {
        if (level instanceof ServerLevel serverLevel) {
            monitor.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = FactoryStats.SCAN_INTERVAL_TICKS;
        FactoryStats next = scan(level);
        long delta = next.energyStored() - lastStored;
        lastStored = next.energyStored();
        int trend = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta));
        FactoryStats withTrend = next.withEnergyTrend(trend);
        if (!withTrend.equals(this.stats)) {
            this.stats = withTrend;
            setChanged();
            syncNow();
        }
    }

    private FactoryStats scan(ServerLevel level) {
        FactoryStats.Builder builder = FactoryStats.builder();
        BlockPos center = worldPosition;
        int radius = FactoryStats.SCAN_RADIUS;
        int radiusSqr = radius * radius;
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity == this || pos.distSqr(center) > radiusSqr) {
                        continue;
                    }
                    observe(level, builder, blockEntity);
                }
            }
        }
        return builder.build();
    }

    /** Performs an immediate scan and sync when a player opens the monitor UI. */
    public void syncOnOpen() {
        if (level instanceof ServerLevel serverLevel) {
            FactoryStats next = scan(serverLevel);
            long delta = next.energyStored() - lastStored;
            lastStored = next.energyStored();
            int trend = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta));
            this.stats = next.withEnergyTrend(trend);
            this.scanCooldown = FactoryStats.SCAN_INTERVAL_TICKS;
            setChanged();
            syncNow();
        }
    }

    private static void observe(ServerLevel level, FactoryStats.Builder builder, BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }
        var blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        if (!IndustrialCore.MOD_ID.equals(blockId.getNamespace())) {
            return;
        }
        if (blockEntity instanceof ItemPipeBlockEntity pipe) {
            builder.addPipe(pipe.isBlocked());
            return;
        }
        if (isTransportOnly(blockEntity)) {
            return;
        }
        boolean generator = blockEntity instanceof GenerationBlockEntity
                || blockEntity instanceof RecoveryBlockEntity
                || blockEntity instanceof SingularityReactorBlockEntity;
        if (generator) {
            builder.addGenerator(isWorking(blockEntity));
        }
        builder.addMachine(stateOf(blockEntity));
        if (isOutputFull(blockEntity)) {
            builder.addFullOutput();
        }
        if (blockEntity instanceof SingularityReactorBlockEntity reactor) {
            builder.addEnergy(reactor.getStoredEnergyLong(), reactor.getEnergyCapacityLong());
            return;
        }
        try {
            IEnergyStorage storage = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, blockEntity.getBlockPos(), null);
            if (storage != null) {
                builder.addEnergy(storage.getEnergyStored(), storage.getMaxEnergyStored());
            }
        } catch (Throwable ignored) {
        }
    }

    /** Conveyors, cables, hyper conduits, and transporters move items/energy and are not counted as machines. */
    private static boolean isTransportOnly(BlockEntity blockEntity) {
        return blockEntity instanceof ConveyorBlockEntity
                || blockEntity instanceof TransporterBlockEntity
                || blockEntity instanceof ItemDistributorBlockEntity
                || blockEntity instanceof CableBlockEntity
                || blockEntity instanceof HyperEnergyConduitBlockEntity
                || blockEntity instanceof HyperItemConduitBlockEntity;
    }

    private static FactoryStats.MachineState stateOf(BlockEntity blockEntity) {
        if (isWorking(blockEntity)) {
            return FactoryStats.MachineState.WORKING;
        }
        if (hasProblem(blockEntity)) {
            return FactoryStats.MachineState.STOPPED;
        }
        return FactoryStats.MachineState.IDLE;
    }

    private static boolean isWorking(BlockEntity blockEntity) {
        BlockState state = blockEntity.getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            return true;
        }
        if (blockEntity instanceof ProcessingBlockEntity processing) {
            return processing.getStatus() == MachineStatus.WORKING;
        }
        if (blockEntity instanceof GenerationBlockEntity generation) {
            return generation.getRate() > 0 || generation.getBurnTime() > 0;
        }
        if (blockEntity instanceof RecoveryBlockEntity recovery) {
            return recovery.getRate() > 0;
        }
        if (blockEntity instanceof AlloyFurnaceBlockEntity alloy) {
            return alloy.getStatus() == MachineStatus.WORKING || alloy.getProgress() > 0;
        }
        if (blockEntity instanceof QuarryBlockEntity quarry) {
            return quarry.isRunning();
        }
        if (blockEntity instanceof LargeQuarryBlockEntity largeQuarry) {
            return largeQuarry.isRunning();
        }
        if (blockEntity instanceof MatterAmplifierBlockEntity amplifier) {
            return amplifier.getStatus() == MachineStatus.WORKING || amplifier.getProgress() > 0;
        }
        if (blockEntity instanceof PhaseReactorBlockEntity reactor) {
            return reactor.getStatus() == MachineStatus.WORKING || reactor.getProgress() > 0;
        }
        if (blockEntity instanceof QuantumCompressorBlockEntity compressor) {
            return compressor.getStatus() == MachineStatus.WORKING || compressor.getProgress() > 0;
        }
        if (blockEntity instanceof SeedCultivatorBlockEntity cultivator) {
            return cultivator.getStatus() == MachineStatus.WORKING || cultivator.getProgress() > 0;
        }
        if (blockEntity instanceof OreSynthesizerBlockEntity synthesizer) {
            return synthesizer.getStatusForTesting() == MachineStatus.WORKING
                    || synthesizer.getProgressForTesting() > 0;
        }
        if (blockEntity instanceof ReconstructorBlockEntity reconstructor) {
            return reconstructor.getStatus() == MachineStatus.WORKING || reconstructor.getProgress() > 0;
        }
        if (blockEntity instanceof AssemblerBlockEntity assembler) {
            return assembler.getStatus() == MachineStatus.WORKING || assembler.getProgress() > 0;
        }
        if (blockEntity instanceof CrafterBlockEntity crafter) {
            return crafter.getStatus() == CrafterStatus.CRAFTING || crafter.getProgress() > 0;
        }
        return false;
    }

    private static boolean hasProblem(BlockEntity blockEntity) {
        if (blockEntity instanceof ProcessingBlockEntity processing) {
            return processing.getStatus().isProblem();
        }
        if (blockEntity instanceof CrafterBlockEntity crafter) {
            return crafter.getStatus().isProblem();
        }
        if (blockEntity instanceof QuarryBlockEntity quarry) {
            return quarry.getStatus().isPaused();
        }
        if (blockEntity instanceof LargeQuarryBlockEntity largeQuarry) {
            return largeQuarry.getStatus().isPaused();
        }
        if (blockEntity instanceof AlloyFurnaceBlockEntity alloy) {
            return alloy.getStatus().isProblem();
        }
        if (blockEntity instanceof MatterAmplifierBlockEntity amplifier) {
            return amplifier.getStatus().isProblem();
        }
        if (blockEntity instanceof PhaseReactorBlockEntity reactor) {
            return reactor.getStatus().isProblem();
        }
        if (blockEntity instanceof QuantumCompressorBlockEntity compressor) {
            return compressor.getStatus().isProblem();
        }
        if (blockEntity instanceof SeedCultivatorBlockEntity cultivator) {
            return cultivator.getStatus().isProblem();
        }
        if (blockEntity instanceof ReconstructorBlockEntity reconstructor) {
            return reconstructor.getStatus().isProblem();
        }
        if (blockEntity instanceof AssemblerBlockEntity assembler) {
            return assembler.getStatus().isProblem();
        }
        return false;
    }

    private static boolean isOutputFull(BlockEntity blockEntity) {
        if (blockEntity instanceof ProcessingBlockEntity processing) {
            return processing.getStatus() == MachineStatus.OUTPUT_FULL;
        }
        if (blockEntity instanceof CrafterBlockEntity crafter) {
            return crafter.getStatus() == CrafterStatus.OUTPUT_FULL;
        }
        if (blockEntity instanceof QuarryBlockEntity quarry) {
            return quarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL;
        }
        if (blockEntity instanceof LargeQuarryBlockEntity largeQuarry) {
            return largeQuarry.getStatus() == QuarryStatus.PAUSED_INVENTORY_FULL;
        }
        return false;
    }

    private void syncNow() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // Menu / sync
    // ------------------------------------------------------------------

    public FactoryStats getStats() {
        return stats;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.factory_monitor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MonitorMenu(containerId, playerInventory, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeStats(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writeStats(CompoundTag tag) {
        CompoundTag statsTag = new CompoundTag();
        statsTag.putInt(TAG_MACHINES, stats.machines());
        statsTag.putInt(TAG_WORKING, stats.working());
        statsTag.putInt(TAG_IDLE, stats.idle());
        statsTag.putInt(TAG_STOPPED, stats.stopped());
        statsTag.putInt(TAG_GENERATORS, stats.generators());
        statsTag.putInt(TAG_GENERATORS_RUNNING, stats.generatorsRunning());
        statsTag.putInt(TAG_PIPES, stats.pipes());
        statsTag.putInt(TAG_BLOCKED, stats.blockedPipes());
        statsTag.putInt(TAG_FULL, stats.fullOutputs());
        statsTag.putLong(TAG_STORED, stats.energyStored());
        statsTag.putLong(TAG_CAPACITY, stats.energyCapacity());
        statsTag.putInt(TAG_TREND, stats.energyTrend());
        tag.put(TAG_STATS, statsTag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeStats(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(TAG_STATS)) {
            return;
        }
        CompoundTag statsTag = tag.getCompound(TAG_STATS);
        stats = new FactoryStats(
                statsTag.getInt(TAG_MACHINES),
                statsTag.getInt(TAG_WORKING),
                statsTag.getInt(TAG_IDLE),
                statsTag.getInt(TAG_STOPPED),
                statsTag.getInt(TAG_GENERATORS),
                statsTag.getInt(TAG_GENERATORS_RUNNING),
                statsTag.getInt(TAG_PIPES),
                statsTag.getInt(TAG_BLOCKED),
                statsTag.getInt(TAG_FULL),
                statsTag.getLong(TAG_STORED),
                statsTag.getLong(TAG_CAPACITY),
                statsTag.getInt(TAG_TREND));
    }
}
