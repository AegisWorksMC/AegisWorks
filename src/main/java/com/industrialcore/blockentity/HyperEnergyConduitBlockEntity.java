package com.industrialcore.blockentity;

import com.industrialcore.endgame.HyperConduitLogic;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 99 / 107: BlockEntity for the Unlimited Energy Conduit ({@code hyper_energy_conduit}).
 *
 * <p>Performs lossless, overflow-safe FE transfer up to {@code Integer.MAX_VALUE} FE/tick across
 * an entire connected conduit network using {@code long} distribution math.</p>
 *
 * <p>Phase 107 optimization: caches the connected network graph, leader, and external
 * {@link BlockCapabilityCache} endpoints per topology version so steady-state ticks and direct
 * pushes (e.g. from {@code singularity_reactor} or adjacent {@code cable_ultimate} lines) perform
 * zero BFS and zero empty-neighbor capability scans.</p>
 */
public class HyperEnergyConduitBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static long topologyVersion = 1L;

    @Nullable
    private CachedNetwork cachedNetwork;
    private boolean routing = false;

    public HyperEnergyConduitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYPER_ENERGY_CONDUIT.get(), pos, state);
    }

    /** Invalidates cached hyper energy conduit networks after topology or capability changes. */
    public static void invalidateAllNetworks() {
        topologyVersion++;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return new ConduitEnergyEndpoint(side);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HyperEnergyConduitBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickNetwork(sl);
        }
    }

    private CachedNetwork getOrBuildNetwork(ServerLevel level) {
        CachedNetwork net = cachedNetwork;
        if (net != null && net.version == topologyVersion && net.level == level && !net.leader.isRemoved()) {
            return net;
        }
        return rebuildNetwork(level);
    }

    private CachedNetwork rebuildNetwork(ServerLevel level) {
        List<HyperEnergyConduitBlockEntity> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(worldPosition.asLong());
        queue.add(worldPosition);

        HyperEnergyConduitBlockEntity leader = this;
        long leaderKey = worldPosition.asLong();

        while (!queue.isEmpty() && nodes.size() < HyperConduitLogic.MAX_NETWORK_NODES) {
            BlockPos cur = queue.poll();
            if (level.getBlockEntity(cur) instanceof HyperEnergyConduitBlockEntity be && !be.isRemoved()) {
                nodes.add(be);
                long k = cur.asLong();
                if (k < leaderKey) {
                    leaderKey = k;
                    leader = be;
                }
                for (Direction d : DIRECTIONS) {
                    BlockPos next = cur.relative(d);
                    long nk = next.asLong();
                    if (visited.add(nk) && level.isLoaded(next)
                            && level.getBlockEntity(next) instanceof HyperEnergyConduitBlockEntity nbe
                            && !nbe.isRemoved()) {
                        queue.add(next);
                    }
                }
            }
        }

        List<CachedPort> ports = new ArrayList<>();
        Set<Long> seenPorts = new HashSet<>();
        for (HyperEnergyConduitBlockEntity node : nodes) {
            for (Direction dir : DIRECTIONS) {
                BlockPos targetPos = node.worldPosition.relative(dir);
                if (!level.isLoaded(targetPos)) {
                    continue;
                }
                BlockState targetState = level.getBlockState(targetPos);
                if (targetState.isAir() || level.getBlockEntity(targetPos) instanceof HyperEnergyConduitBlockEntity) {
                    continue;
                }
                Direction faceOnTarget = dir.getOpposite();
                long portKey = targetPos.asLong() * 31L + faceOnTarget.ordinal();
                if (!seenPorts.add(portKey)) {
                    continue;
                }
                IEnergyStorage cap = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, targetPos, faceOnTarget);
                if (cap != null) {
                    BlockPos immutableTarget = targetPos.immutable();
                    BlockCapabilityCache<IEnergyStorage, Direction> capCache = BlockCapabilityCache.create(
                            Capabilities.EnergyStorage.BLOCK, level, immutableTarget, faceOnTarget);
                    ports.add(new CachedPort(immutableTarget, capCache));
                }
            }
        }

        CachedNetwork built = new CachedNetwork(level, topologyVersion, leader, List.copyOf(ports));
        for (HyperEnergyConduitBlockEntity node : nodes) {
            node.cachedNetwork = built;
        }
        return built;
    }

    private void tickNetwork(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        CachedNetwork net = getOrBuildNetwork(level);
        if (net.leader != this) {
            return;
        }
        long gameTime = level.getGameTime();
        if (net.lastTick == gameTime) {
            return;
        }
        net.lastTick = gameTime;

        List<CachedPort> cachedPorts = net.ports;
        if (cachedPorts.size() < 2) {
            return;
        }

        List<ExternalPort> sources = new ArrayList<>(cachedPorts.size());
        List<ExternalPort> sinks = new ArrayList<>(cachedPorts.size());
        for (CachedPort cp : cachedPorts) {
            if (!level.isLoaded(cp.pos)) {
                continue;
            }
            IEnergyStorage cap = cp.cache.getCapability();
            if (cap == null) {
                continue;
            }
            if (cap.canExtract() && cap.getEnergyStored() > 0) {
                sources.add(new ExternalPort(cp.pos, cap));
            }
            if (cap.canReceive() && cap.getEnergyStored() < cap.getMaxEnergyStored()) {
                sinks.add(new ExternalPort(cp.pos, cap));
            }
        }

        if (sources.isEmpty() || sinks.isEmpty()) {
            return;
        }

        for (ExternalPort src : sources) {
            int avail = src.storage.extractEnergy(HyperConduitLogic.UNLIMITED_FE_PER_TICK, true);
            if (avail <= 0) {
                continue;
            }
            int sent = routeToSinks(avail, src.pos, sinks);
            if (sent > 0) {
                src.storage.extractEnergy(sent, false);
            }
        }
    }

    private int routeDirectPush(ServerLevel level, int offer, boolean simulate, @Nullable BlockPos excludePos) {
        if (offer <= 0 || routing) {
            return 0;
        }
        routing = true;
        try {
            CachedNetwork net = getOrBuildNetwork(level);
            List<CachedPort> cachedPorts = net.ports;
            if (cachedPorts.isEmpty()) {
                return 0;
            }
            List<ExternalPort> sinks = new ArrayList<>(cachedPorts.size());
            for (CachedPort cp : cachedPorts) {
                if (excludePos != null && cp.pos.equals(excludePos)) {
                    continue;
                }
                if (!level.isLoaded(cp.pos)) {
                    continue;
                }
                IEnergyStorage cap = cp.cache.getCapability();
                if (cap != null && cap.canReceive() && cap.getEnergyStored() < cap.getMaxEnergyStored()) {
                    sinks.add(new ExternalPort(cp.pos, cap));
                }
            }
            if (sinks.isEmpty()) {
                return 0;
            }
            if (simulate) {
                long totalRoom = 0L;
                for (ExternalPort s : sinks) {
                    totalRoom += s.storage.receiveEnergy(offer, true);
                    if (totalRoom >= offer) {
                        return offer;
                    }
                }
                return (int) Math.min(offer, totalRoom);
            }
            return routeToSinks(offer, excludePos, sinks);
        } finally {
            routing = false;
        }
    }

    private static int routeToSinks(int offer, @Nullable BlockPos excludePos, List<ExternalPort> sinks) {
        List<IEnergyStorage> validSinks = new ArrayList<>(sinks.size());
        long[] rooms = new long[sinks.size()];
        int count = 0;
        for (ExternalPort s : sinks) {
            if (excludePos != null && s.pos.equals(excludePos)) {
                continue;
            }
            int simOne = s.storage.receiveEnergy(HyperConduitLogic.UNLIMITED_FE_PER_TICK, true);
            if (simOne > 0) {
                long capRoom = Math.max(simOne,
                        (long) s.storage.getMaxEnergyStored() - (long) s.storage.getEnergyStored());
                validSinks.add(s.storage);
                rooms[count++] = capRoom;
            }
        }
        if (count == 0) {
            return 0;
        }
        long[] exactRooms = new long[count];
        System.arraycopy(rooms, 0, exactRooms, 0, count);
        long[] alloc = HyperConduitLogic.distributeEnergy(offer, exactRooms);

        long totalAccepted = 0L;
        for (int i = 0; i < count; i++) {
            long want = alloc[i];
            IEnergyStorage sink = validSinks.get(i);
            int guard = 0;
            while (want > 0L && guard++ < 1_024) {
                int step = HyperConduitLogic.computeEnergyTransfer(want, exactRooms[i]);
                if (step <= 0) {
                    break;
                }
                int accepted = sink.receiveEnergy(step, false);
                if (accepted <= 0) {
                    break;
                }
                totalAccepted += accepted;
                want -= accepted;
            }
        }
        return (int) Math.min((long) offer, totalAccepted);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cachedNetwork = null;
        invalidateAllNetworks();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        cachedNetwork = null;
        invalidateAllNetworks();
    }

    private record CachedPort(BlockPos pos, BlockCapabilityCache<IEnergyStorage, Direction> cache) {}

    private record ExternalPort(BlockPos pos, IEnergyStorage storage) {}

    private static final class CachedNetwork {
        final ServerLevel level;
        final long version;
        final HyperEnergyConduitBlockEntity leader;
        final List<CachedPort> ports;
        long lastTick = Long.MIN_VALUE;

        CachedNetwork(ServerLevel level, long version, HyperEnergyConduitBlockEntity leader, List<CachedPort> ports) {
            this.level = level;
            this.version = version;
            this.leader = leader;
            this.ports = ports;
        }
    }

    private final class ConduitEnergyEndpoint implements IEnergyStorage {
        @Nullable
        private final Direction side;

        ConduitEnergyEndpoint(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!(level instanceof ServerLevel sl) || toReceive <= 0) {
                return 0;
            }
            BlockPos exclude = side != null ? worldPosition.relative(side) : null;
            return routeDirectPush(sl, toReceive, simulate, exclude);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return HyperConduitLogic.UNLIMITED_FE_PER_TICK;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
