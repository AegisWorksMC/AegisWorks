package com.industrialcore.blockentity;

import com.industrialcore.life.FluidPipeLogic;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 105.6 / 107: BlockEntity for {@code fluid_pipe}.
 *
 * <p>Performs lossless fluid transport across a cached connected pipe network (up to
 * {@link FluidPipeLogic#MAX_TRANSFER_MB_PER_TICK} mB/tick per network pulse) from adjacent
 * {@link IFluidHandler} sources (such as {@code fluid_pump}) into adjacent {@link IFluidHandler}
 * receivers (such as {@code reactor_port} or {@code nuclear_generator}), and also accepts direct
 * pushes through its pass-through {@link IFluidHandler} capability.</p>
 *
 * <p>Phase 107 optimization: caches the connected network graph, leader, and external
 * {@link BlockCapabilityCache} endpoints per topology version so steady-state ticks and direct
 * pushes perform zero BFS and zero empty-neighbor capability scans.</p>
 */
public class FluidPipeBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static long topologyVersion = 1L;

    @Nullable
    private CachedNetwork cachedNetwork;
    private boolean routing = false;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PIPE.get(), pos, state);
    }

    /** Invalidates cached fluid pipe networks after topology or adjacent capability changes. */
    public static void invalidateAllNetworks() {
        topologyVersion++;
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        return new PipeFluidEndpoint(side);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity be) {
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
        List<FluidPipeBlockEntity> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(worldPosition.asLong());
        queue.add(worldPosition);

        FluidPipeBlockEntity leader = this;
        long leaderKey = worldPosition.asLong();

        while (!queue.isEmpty() && nodes.size() < FluidPipeLogic.MAX_NETWORK_NODES) {
            BlockPos cur = queue.poll();
            if (level.getBlockEntity(cur) instanceof FluidPipeBlockEntity be && !be.isRemoved()) {
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
                            && level.getBlockEntity(next) instanceof FluidPipeBlockEntity nbe && !nbe.isRemoved()) {
                        queue.add(next);
                    }
                }
            }
        }

        List<CachedPort> ports = new ArrayList<>();
        Set<Long> seenPorts = new HashSet<>();
        for (FluidPipeBlockEntity node : nodes) {
            for (Direction dir : DIRECTIONS) {
                BlockPos targetPos = node.worldPosition.relative(dir);
                if (!level.isLoaded(targetPos)) {
                    continue;
                }
                BlockState targetState = level.getBlockState(targetPos);
                if (targetState.isAir() || level.getBlockEntity(targetPos) instanceof FluidPipeBlockEntity) {
                    continue;
                }
                Direction faceOnTarget = dir.getOpposite();
                long portKey = targetPos.asLong() * 31L + faceOnTarget.ordinal();
                if (!seenPorts.add(portKey)) {
                    continue;
                }
                IFluidHandler cap = level.getCapability(
                        Capabilities.FluidHandler.BLOCK, targetPos, faceOnTarget);
                if (cap != null) {
                    BlockPos immutableTarget = targetPos.immutable();
                    BlockCapabilityCache<IFluidHandler, Direction> capCache = BlockCapabilityCache.create(
                            Capabilities.FluidHandler.BLOCK, level, immutableTarget, faceOnTarget);
                    ports.add(new CachedPort(immutableTarget, capCache));
                }
            }
        }

        CachedNetwork built = new CachedNetwork(level, topologyVersion, leader, List.copyOf(ports));
        for (FluidPipeBlockEntity node : nodes) {
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

        List<ResolvedPort> active = new ArrayList<>(cachedPorts.size());
        for (CachedPort cp : cachedPorts) {
            if (!level.isLoaded(cp.pos)) {
                continue;
            }
            IFluidHandler handler = cp.cache.getCapability();
            if (handler != null) {
                active.add(new ResolvedPort(cp.pos, handler));
            }
        }
        if (active.size() < 2) {
            return;
        }

        for (ResolvedPort src : active) {
            FluidStack simDrain = src.handler.drain(FluidPipeLogic.MAX_TRANSFER_MB_PER_TICK,
                    IFluidHandler.FluidAction.SIMULATE);
            if (simDrain.isEmpty()) {
                continue;
            }
            int sent = routeToSinks(simDrain, src.pos, active, false);
            if (sent > 0) {
                src.handler.drain(simDrain.copyWithAmount(sent), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private int routeDirectPush(ServerLevel level, FluidStack resource, IFluidHandler.FluidAction action,
                                @Nullable BlockPos excludePos) {
        if (resource.isEmpty() || routing) {
            return 0;
        }
        int capped = FluidPipeLogic.clampTransfer(resource.getAmount());
        if (capped <= 0) {
            return 0;
        }
        routing = true;
        try {
            CachedNetwork net = getOrBuildNetwork(level);
            List<CachedPort> cachedPorts = net.ports;
            if (cachedPorts.isEmpty()) {
                return 0;
            }
            List<ResolvedPort> sinks = new ArrayList<>(cachedPorts.size());
            for (CachedPort cp : cachedPorts) {
                if (excludePos != null && cp.pos.equals(excludePos)) {
                    continue;
                }
                if (!level.isLoaded(cp.pos)) {
                    continue;
                }
                IFluidHandler handler = cp.cache.getCapability();
                if (handler != null) {
                    sinks.add(new ResolvedPort(cp.pos, handler));
                }
            }
            if (sinks.isEmpty()) {
                return 0;
            }
            FluidStack offer = resource.copyWithAmount(capped);
            return routeToSinks(offer, excludePos, sinks, action.simulate());
        } finally {
            routing = false;
        }
    }

    private static int routeToSinks(FluidStack offer, @Nullable BlockPos excludePos,
                                    List<ResolvedPort> candidates, boolean simulate) {
        List<IFluidHandler> validSinks = new ArrayList<>(candidates.size());
        int[] spaces = new int[candidates.size()];
        int count = 0;

        for (ResolvedPort port : candidates) {
            if (excludePos != null && port.pos.equals(excludePos)) {
                continue;
            }
            int room = port.handler.fill(offer, IFluidHandler.FluidAction.SIMULATE);
            if (room > 0) {
                validSinks.add(port.handler);
                spaces[count++] = room;
            }
        }
        if (count == 0) {
            return 0;
        }
        int[] exactSpaces = new int[count];
        System.arraycopy(spaces, 0, exactSpaces, 0, count);
        int[] alloc = FluidPipeLogic.distributeFluid(offer.getAmount(), exactSpaces);

        int totalAccepted = 0;
        IFluidHandler.FluidAction act = simulate
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE;
        for (int i = 0; i < count; i++) {
            int want = alloc[i];
            if (want <= 0) {
                continue;
            }
            int accepted = validSinks.get(i).fill(offer.copyWithAmount(want), act);
            totalAccepted += Math.max(0, accepted);
        }
        return Math.min(offer.getAmount(), totalAccepted);
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

    private record CachedPort(BlockPos pos, BlockCapabilityCache<IFluidHandler, Direction> cache) {}

    private record ResolvedPort(BlockPos pos, IFluidHandler handler) {}

    private static final class CachedNetwork {
        final ServerLevel level;
        final long version;
        final FluidPipeBlockEntity leader;
        final List<CachedPort> ports;
        long lastTick = Long.MIN_VALUE;

        CachedNetwork(ServerLevel level, long version, FluidPipeBlockEntity leader, List<CachedPort> ports) {
            this.level = level;
            this.version = version;
            this.leader = leader;
            this.ports = ports;
        }
    }

    private final class PipeFluidEndpoint implements IFluidHandler {
        @Nullable
        private final Direction side;

        PipeFluidEndpoint(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return FluidPipeLogic.MAX_TRANSFER_MB_PER_TICK;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!(level instanceof ServerLevel sl) || resource.isEmpty()) {
                return 0;
            }
            BlockPos exclude = side != null ? worldPosition.relative(side) : null;
            return routeDirectPush(sl, resource, action, exclude);
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
