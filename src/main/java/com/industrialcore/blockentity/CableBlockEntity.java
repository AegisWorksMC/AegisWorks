package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.block.CableBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.CableNetworkTraversal;
import com.industrialcore.machine.CableTier;
import com.industrialcore.client.PortArmCache;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-authoritative FE cable.
 *
 * <p>Each cable stores a small buffer and accepts FE from adjacent generators through the normal
 * NeoForge energy capability. All connected cables form one network: the cable with the lowest
 * position acts as the distributor, moves at most the <em>lowest</em> tier throughput of the
 * network per tick from the pooled buffers into adjacent machines, and never invents or deletes
 * FE (energy is staged out of the buffers, pushed, and any unaccepted remainder is refunded).</p>
 *
 * <p><b>Phase 8: no size cap.</b> The old implementation refused to transport energy once a
 * component had more than 4,096 cables. That limit is gone. A topology change now starts an
 * <em>incremental</em> breadth-first scan ({@link CableNetworkTraversal}) that is spread over as
 * many ticks as the per-tick node budget requires, and the network distributes energy again the
 * moment the scan finishes. The budget is the only limit, and it bounds work per tick, not the
 * size of a network: 4,096, 8,192 or 65,536 cables all work, they simply take a few more ticks
 * to reconnect. Cable buffers keep accepting and holding FE while a scan runs.</p>
 *
 * <p>Guarantees of this design:</p>
 * <ul>
 *   <li>Each visited cable is enqueued at most once, so loops and duplicate edges terminate.</li>
 *   <li>At most one scan runs per level at a time (static registry), so a world load with
 *       thousands of cables does not start thousands of duplicate scans.</li>
 *   <li>Exactly one cable per component distributes (lowest position), and a cable ticks once per
 *       tick, so a network can never be drained twice in the same tick. Two snapshots of the same
 *       component therefore cannot double-spend.</li>
 *   <li>A component is only cached as stable when its scan completed for the current topology
 *       version; any change bumps that static version and every member rebuilds.</li>
 *   <li>Consumers that moved into an unloaded chunk invalidate the network instead of receiving
 *       (and losing) energy.</li>
 * </ul>
 *
 * <p>Incomplete scans never distribute. That is the important difference to the pre-Phase-6.2
 * implementation, which treated a truncated search as a complete network: several leaders then
 * distributed the same energy in the same tick, exceeding the tier limit and breaking the
 * "lowest tier wins" rule.</p>
 */
public class CableBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Bumped on any cable topology change; every cached network with an older value rebuilds. */
    private static long topologyVersion;
    /** Global (per game tick) budget for network rebuild work, shared by every running scan. */
    private static long budgetGameTime = Long.MIN_VALUE;
    private static int budgetRemaining;
    @Nullable
    private static Integer budgetOverrideForTesting;
    /** At most one rebuild per level at a time; avoids thousands of duplicate scans after a load. */
    private static final Map<ServerLevel, RebuildJob> ACTIVE_REBUILDS = new WeakHashMap<>();

    private static final String TAG_ENERGY = "Energy";

    private final CableTier tier;
    private final ModEnergyStorage buffer;
    /** Last known block in each direction, used to notice replaced machines without churn. */
    private final Block[] neighbourBlocks = new Block[6];

    @Nullable
    private Network network;
    /** Rotating start for consumer distribution and for draining, so no buffer starves. */
    private int distributionCursor;
    private int drainCursor;

    private long rebuildSteps;
    private long rebuildNodes;
    private long distributions;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
        this.tier = state.getBlock() instanceof CableBlock cableBlock ? cableBlock.tier() : CableTier.BASIC;
        this.buffer = new ModEnergyStorage(tier.bufferCapacity(), tier.throughput());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CableBlockEntity cable) {
        if (level instanceof ServerLevel serverLevel) {
            cable.tick(serverLevel);
        }
    }

    /** Client-only cache of the port anchors around this cable; see {@link PortArmCache}. */
    @Nullable
    private PortArmCache armCache;

    /** Client-only cache of the port anchors around this cable. */
    public PortArmCache armCache() {
        if (armCache == null) {
            armCache = new PortArmCache();
        }
        return armCache;
    }

    /** Called by the block when a neighbour or the connection shape changed. */
    public void invalidatePortArms() {
        if (armCache != null) {
            armCache.invalidate();
        }
    }

    private void tick(ServerLevel level) {
        if (isRemoved()) {
            // A stale reference (tests, a breaking player) must never resurrect a component that
            // includes a block which no longer exists.
            return;
        }
        Network current = network;
        if (current != null && current.isStable(topologyVersion)) {
            if (current.leader == this && current.totalEnergy() > 0) {
                distribute(current);
            }
            return;
        }
        advanceRebuild(level);
    }

    // ------------------------------------------------------------------
    // incremental rebuild
    // ------------------------------------------------------------------

    /**
     * Moves the rebuild of this cable's component forward by at most one budget slice.
     *
     * <p>Only the cable that started a scan steps it; every other member of the component waits
     * for the result. Cables that are not reachable from the running scan (a split half, a second
     * network) start their own scan once the running one is done, which keeps the total work per
     * topology change linear in the number of cables.</p>
     */
    private void advanceRebuild(ServerLevel level) {
        Network current = network;
        if (current != null && current.job != null) {
            if (current.version != topologyVersion) {
                // The topology changed while this scan was running; drop it and start over.
                dropActiveRebuild(current.job);
                current.dissolve();
                network = null;
                return;
            }
            stepRebuild(level, current);
            return;
        }
        RebuildJob active = ACTIVE_REBUILDS.get(level);
        if (active != null) {
            if (active.version == topologyVersion && !active.owner.isRemoved()) {
                // Another cable is already scanning. Joining would duplicate the work, so wait.
                return;
            }
            // Abandoned scan (topology moved on, or its owner was removed without a callback):
            // taking over is always safe because nothing distributes from an unfinished scan.
            ACTIVE_REBUILDS.remove(level);
        }
        Network container = new Network(level, topologyVersion);
        RebuildJob job = new RebuildJob(level, topologyVersion, this);
        container.job = job;
        network = container;
        ACTIVE_REBUILDS.put(level, job);
        stepRebuild(level, container);
    }

    private void stepRebuild(ServerLevel level, Network container) {
        RebuildJob job = container.job;
        if (job == null) {
            return;
        }
        int granted = grantRebuildBudget(level);
        if (granted > 0) {
            int processed = job.step(granted);
            spendRebuildBudget(processed);
            rebuildSteps++;
            rebuildNodes += processed;
        }
        if (!job.isComplete()) {
            return;
        }
        job.finish(container);
        dropActiveRebuild(job);
        // The scan finished in this tick, so the finished network may already distribute now
        // instead of wasting a tick (the leader is the lowest cable, which is often this one).
        if (container.leader == this && container.totalEnergy() > 0) {
            distribute(container);
        }
    }

    private static int grantRebuildBudget(ServerLevel level) {
        Integer override = budgetOverrideForTesting;
        if (override != null) {
            return Math.max(0, override);
        }
        long now = level.getGameTime();
        if (now != budgetGameTime) {
            budgetGameTime = now;
            budgetRemaining = Math.max(1, Config.cableRebuildNodesPerTick());
        }
        return budgetRemaining;
    }

    private static void spendRebuildBudget(int nodes) {
        if (budgetOverrideForTesting != null) {
            return;
        }
        budgetRemaining = Math.max(0, budgetRemaining - nodes);
    }

    private static void dropActiveRebuild(RebuildJob job) {
        ACTIVE_REBUILDS.remove(job.level, job);
    }

    // ------------------------------------------------------------------
    // distribution
    // ------------------------------------------------------------------

    /**
     * Moves at most one tier throughput from the pooled buffers into the consumers.
     *
     * <p>Energy is first staged out of the buffers, then handed to the targets, and the part no
     * target accepted is refunded. Pushing first and draining afterwards would duplicate FE if a
     * member disappeared in between.</p>
     */
    private void distribute(Network current) {
        if (current.consumers.isEmpty()) {
            return;
        }
        long budget = Math.min(current.throughput, current.totalEnergy());
        if (budget <= 0) {
            return;
        }
        int staged = drain(current, (int) Math.min(Integer.MAX_VALUE, budget));
        if (staged <= 0) {
            return;
        }
        distributions++;
        int remaining = staged;
        int size = current.consumers.size();
        int start = Math.floorMod(distributionCursor, size);
        boolean moved = false;
        boolean unloadedTarget = false;
        for (int offset = 0; offset < size && remaining > 0; offset++) {
            ConsumerRef ref = current.consumers.get((start + offset) % size);
            if (!current.level.isLoaded(ref.pos())) {
                // A machine whose chunk unloaded must not receive energy into a detached storage.
                unloadedTarget = true;
                continue;
            }
            IEnergyStorage target = ref.storage();
            if (!target.canReceive()) {
                continue;
            }
            int accepted = target.receiveEnergy(remaining, false);
            if (accepted <= 0) {
                continue;
            }
            remaining -= accepted;
            moved = true;
        }
        distributionCursor = (start + 1) % size;
        if (remaining > 0) {
            refund(current, remaining);
        }
        if (moved && level != null) {
            level.blockEntityChanged(worldPosition);
        }
        if (unloadedTarget) {
            invalidateNetwork();
        }
    }

    /** Removes up to {@code amount} FE from the members, starting after the previous drain. */
    private int drain(Network current, int amount) {
        int remaining = amount;
        int size = current.cables.size();
        if (size == 0) {
            return 0;
        }
        int start = Math.floorMod(drainCursor, size);
        for (int offset = 0; offset < size && remaining > 0; offset++) {
            CableBlockEntity cable = current.cables.get((start + offset) % size);
            int taken = cable.buffer.consume(remaining);
            if (taken > 0) {
                if (cable.level != null) {
                    cable.level.blockEntityChanged(cable.worldPosition);
                }
                remaining -= taken;
            }
        }
        drainCursor = (start + 1) % size;
        return amount - remaining;
    }

    /** Returns staged-but-unaccepted FE to the buffers (generate() bypasses the receive limit). */
    private void refund(Network current, int amount) {
        int remaining = amount;
        int size = current.cables.size();
        if (size == 0) {
            return;
        }
        int start = Math.floorMod(drainCursor, size);
        for (int offset = 0; offset < size && remaining > 0; offset++) {
            CableBlockEntity cable = current.cables.get((start + offset) % size);
            int accepted = cable.buffer.generate(remaining);
            if (accepted > 0) {
                if (cable.level != null) {
                    cable.level.blockEntityChanged(cable.worldPosition);
                }
                remaining -= accepted;
            }
        }
    }

    // ------------------------------------------------------------------
    // invalidation
    // ------------------------------------------------------------------

    /** Invalidates this cable's cached network and every other member's cache. */
    public void invalidateNetwork() {
        topologyVersion++;
        Network current = network;
        if (current != null) {
            if (current.job != null) {
                dropActiveRebuild(current.job);
            }
            current.dissolve();
        }
        network = null;
    }

    /**
     * True when the neighbour block in this direction is a different block than the one seen by
     * the last network scan, which means a machine was replaced (its capability may be a new
     * instance). The new block is remembered so one replacement invalidates only once.
     */
    public boolean isNeighbourReplaced(Direction direction, Block neighbourBlock) {
        int index = direction.get3DDataValue();
        Block known = neighbourBlocks[index];
        if (known == null || known == neighbourBlock) {
            return false;
        }
        neighbourBlocks[index] = neighbourBlock;
        return true;
    }

    // ------------------------------------------------------------------
    // accessors
    // ------------------------------------------------------------------

    public CableTier getTier() {
        return tier;
    }

    public ModEnergyStorage getEnergyStorage() {
        return buffer;
    }

    public int getEnergyStoredForTesting() {
        return buffer.getEnergyStored();
    }

    public int getNetworkSizeForTesting() {
        return network == null ? 0 : network.cables.size();
    }

    public int getConsumerCountForTesting() {
        return network == null ? 0 : network.consumers.size();
    }

    public int getNetworkThroughputForTesting() {
        return network == null ? 0 : network.throughput;
    }

    public boolean isNetworkLeaderForTesting() {
        return network != null && network.leader == this;
    }

    /** True once the component of this cable was fully scanned for the current topology. */
    public boolean isNetworkStableForTesting() {
        return network != null && network.isStable(topologyVersion);
    }

    /** Keep the Phase 6.2 name: a stable snapshot is by definition a complete one. */
    public boolean isNetworkCompleteForTesting() {
        return isNetworkStableForTesting();
    }

    public long getRebuildStepCountForTesting() {
        return rebuildSteps;
    }

    public long getRebuildNodeCountForTesting() {
        return rebuildNodes;
    }

    public long getDistributionCountForTesting() {
        return distributions;
    }

    /** Test hook: fixed per-tick rebuild budget instead of the configured value. */
    public static void setRebuildBudgetForTesting(int nodesPerTick) {
        budgetOverrideForTesting = Math.max(0, nodesPerTick);
    }

    public static void clearRebuildBudgetForTesting() {
        budgetOverrideForTesting = null;
    }

    // ------------------------------------------------------------------
    // block entity lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // A network that spans an unload boundary must be rebuilt by the remaining chunks,
        // otherwise this detached block entity would still be drained.
        Network current = network;
        boolean spansBoundary = current != null && (current.cables.size() > 1 || current.job != null);
        invalidate(spansBoundary);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // A removed cable is only part of a cached component when it holds a snapshot or owns a
        // running scan. Neighbours still get neighbourChanged (connection mask), which covers a
        // cable that was broken before the running scan ever reached it.
        invalidate(network != null);
    }

    private void invalidate(boolean bumpVersion) {
        if (bumpVersion) {
            topologyVersion++;
        }
        Network current = network;
        if (current != null) {
            if (current.job != null) {
                dropActiveRebuild(current.job);
            }
            current.dissolve();
        }
        network = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, buffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.setEnergy(Math.max(0, tag.getInt(TAG_ENERGY)));
        network = null;
    }

    // ------------------------------------------------------------------
    // network snapshot and scan
    // ------------------------------------------------------------------

    /** One machine touching the network; the position is kept so unloads can be detected. */
    private record ConsumerRef(BlockPos pos, IEnergyStorage storage) {}

    /** Cached connected component. All members share one instance. */
    private static final class Network {
        private final ServerLevel level;
        private final long version;
        private List<CableBlockEntity> cables = List.of();
        private List<ConsumerRef> consumers = List.of();
        @Nullable
        private CableBlockEntity leader;
        private int throughput;
        @Nullable
        private RebuildJob job;
        private boolean alive = true;

        private Network(ServerLevel level, long version) {
            this.level = level;
            this.version = version;
        }

        /** True only for a finished scan of the current topology. */
        private boolean isStable(long currentVersion) {
            return alive && job == null && version == currentVersion;
        }

        private long totalEnergy() {
            long total = 0L;
            for (CableBlockEntity cable : cables) {
                total += cable.buffer.getEnergyStored();
            }
            return total;
        }

        /** Drops every cached reference so a stale snapshot can never distribute again. */
        private void dissolve() {
            alive = false;
            job = null;
            cables = List.of();
            consumers = List.of();
            leader = null;
            throughput = 0;
        }
    }

    /** One in-progress scan. Only its owner steps it; the result is shared by all members. */
    private static final class RebuildJob {
        private final ServerLevel level;
        private final long version;
        private final CableBlockEntity owner;
        private final CableNetworkTraversal<CableBlockEntity> traversal;
        private final Set<Long> inspectedConsumers = new HashSet<>();
        private final List<ConsumerRef> consumers = new ArrayList<>();

        private RebuildJob(ServerLevel level, long version, CableBlockEntity start) {
            this.level = level;
            this.version = version;
            this.owner = start;
            this.traversal = new CableNetworkTraversal<>(
                    start,
                    this::scan,
                    Comparator.comparingLong(cable -> cable.getBlockPos().asLong()));
        }

        private int step(int budget) {
            return traversal.step(budget);
        }

        private boolean isComplete() {
            return traversal.isComplete();
        }

        /**
         * Loaded cable neighbours only; an unloaded chunk splits the network instead of
         * force-loading it. Non-cable neighbours are resolved once per position.
         */
        private List<CableBlockEntity> scan(CableBlockEntity cable) {
            List<CableBlockEntity> adjacent = new ArrayList<>(6);
            for (Direction direction : DIRECTIONS) {
                int index = direction.get3DDataValue();
                BlockPos target = cable.getBlockPos().relative(direction);
                if (!level.isLoaded(target)) {
                    cable.neighbourBlocks[index] = null;
                    continue;
                }
                BlockState state = level.getBlockState(target);
                cable.neighbourBlocks[index] = state.getBlock();
                BlockEntity neighbor = level.getBlockEntity(target);
                if (neighbor instanceof CableBlockEntity next) {
                    adjacent.add(next);
                    continue;
                }
                if (state.isAir()) {
                    continue;
                }
                if (!inspectedConsumers.add(target.asLong())) {
                    continue;
                }
                IEnergyStorage storage = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, target, direction.getOpposite());
                if (storage != null && storage.canReceive()) {
                    consumers.add(new ConsumerRef(target.immutable(), storage));
                }
            }
            return adjacent;
        }

        /** Publishes the finished scan into {@code container}, which becomes the snapshot. */
        private void finish(Network container) {
            List<CableBlockEntity> members = List.copyOf(traversal.members());
            int lowest = Integer.MAX_VALUE;
            for (CableBlockEntity cable : members) {
                lowest = Math.min(lowest, cable.tier.throughput());
            }
            container.cables = members;
            container.consumers = List.copyOf(consumers);
            container.throughput = lowest == Integer.MAX_VALUE ? 0 : lowest;
            container.leader = traversal.leader();
            container.job = null;
            for (CableBlockEntity cable : members) {
                cable.network = container;
            }
        }
    }
}
