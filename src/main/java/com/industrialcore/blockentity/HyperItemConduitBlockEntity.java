package com.industrialcore.blockentity;

import com.industrialcore.endgame.HyperConduitLogic;
import com.industrialcore.endgame.HyperConduitLogic.FaceMode;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 99 / 107: BlockEntity for the Unlimited Item Conduit ({@code hyper_item_conduit}).
 *
 * <p>Transfers up to 64 full stacks per server tick across a cached connected conduit network using
 * strict simulate-first extraction and insertion so item duplication or loss is impossible.</p>
 *
 * <p>Phase 107 optimization: caches the connected network graph, leader, and pre-sorted
 * {@link BlockCapabilityCache} source/destination lists per topology version so steady-state ticks
 * execute only on the leader with zero BFS and zero empty-neighbor capability scans.</p>
 */
public class HyperItemConduitBlockEntity extends BlockEntity {
    private static final String TAG_FACE_MODES = "FaceModes";
    private static final Direction[] DIRECTIONS = Direction.values();
    private static long topologyVersion = 1L;

    private final FaceMode[] faceModes = new FaceMode[6];
    @Nullable
    private CachedNetwork cachedNetwork;

    public HyperItemConduitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYPER_ITEM_CONDUIT.get(), pos, state);
        Arrays.fill(faceModes, FaceMode.BOTH);
    }

    /** Invalidates cached hyper item conduit networks after topology or face-mode changes. */
    public static void invalidateAllNetworks() {
        topologyVersion++;
    }

    public FaceMode getFaceMode(Direction dir) {
        return faceModes[dir.get3DDataValue()];
    }

    public void setFaceMode(Direction dir, FaceMode mode) {
        FaceMode next = mode == null ? FaceMode.BOTH : mode;
        if (faceModes[dir.get3DDataValue()] == next) {
            return;
        }
        faceModes[dir.get3DDataValue()] = next;
        invalidateAllNetworks();
        setChanged();
    }

    public FaceMode cycleFaceMode(Direction dir) {
        FaceMode next = getFaceMode(dir).next();
        setFaceMode(dir, next);
        return next;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HyperItemConduitBlockEntity be) {
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
        List<HyperItemConduitBlockEntity> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(worldPosition.asLong());
        queue.add(worldPosition);

        HyperItemConduitBlockEntity leader = this;
        long leaderKey = worldPosition.asLong();

        while (!queue.isEmpty() && nodes.size() < HyperConduitLogic.MAX_NETWORK_NODES) {
            BlockPos cur = queue.poll();
            if (level.getBlockEntity(cur) instanceof HyperItemConduitBlockEntity be && !be.isRemoved()) {
                nodes.add(be);
                long k = cur.asLong();
                if (k < leaderKey) {
                    leaderKey = k;
                    leader = be;
                }
                for (Direction d : DIRECTIONS) {
                    if (!be.getFaceMode(d).isConnected()) {
                        continue;
                    }
                    BlockPos next = cur.relative(d);
                    long nk = next.asLong();
                    if (visited.add(nk) && level.isLoaded(next)
                            && level.getBlockEntity(next) instanceof HyperItemConduitBlockEntity nbe
                            && !nbe.isRemoved()
                            && nbe.getFaceMode(d.getOpposite()).isConnected()) {
                        queue.add(next);
                    }
                }
            }
        }

        List<CachedEndpoint> sources = new ArrayList<>();
        List<CachedEndpoint> dests = new ArrayList<>();
        Set<Long> seenPorts = new HashSet<>();

        for (HyperItemConduitBlockEntity node : nodes) {
            for (Direction dir : DIRECTIONS) {
                FaceMode mode = node.getFaceMode(dir);
                if (!mode.isConnected()) {
                    continue;
                }
                BlockPos targetPos = node.worldPosition.relative(dir);
                if (!level.isLoaded(targetPos)) {
                    continue;
                }
                BlockState targetState = level.getBlockState(targetPos);
                if (targetState.isAir() || level.getBlockEntity(targetPos) instanceof HyperItemConduitBlockEntity) {
                    continue;
                }
                Direction faceOnTarget = dir.getOpposite();
                long portKey = targetPos.asLong() * 31L + faceOnTarget.ordinal();
                if (!seenPorts.add(portKey)) {
                    continue;
                }
                IItemHandler handler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, targetPos, faceOnTarget);
                if (handler == null) {
                    continue;
                }
                BlockPos immutableTarget = targetPos.immutable();
                BlockCapabilityCache<IItemHandler, Direction> capCache = BlockCapabilityCache.create(
                        Capabilities.ItemHandler.BLOCK, level, immutableTarget, faceOnTarget);
                CachedEndpoint ep = new CachedEndpoint(immutableTarget, capCache, mode);
                if (mode.allowsExtract()) {
                    sources.add(ep);
                }
                if (mode.allowsInsert()) {
                    dests.add(ep);
                }
            }
        }

        // Prioritize explicit EXTRACT sources and explicit INSERT destinations before BOTH
        sources.sort((a, b) -> Boolean.compare(b.mode == FaceMode.EXTRACT, a.mode == FaceMode.EXTRACT));
        dests.sort((a, b) -> Boolean.compare(b.mode == FaceMode.INSERT, a.mode == FaceMode.INSERT));

        CachedNetwork built = new CachedNetwork(level, topologyVersion, leader,
                List.copyOf(sources), List.copyOf(dests));
        for (HyperItemConduitBlockEntity node : nodes) {
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

        if (net.sources.isEmpty() || net.dests.isEmpty()) {
            return;
        }

        List<ItemEndpoint> activeDests = new ArrayList<>(net.dests.size());
        for (CachedEndpoint cd : net.dests) {
            if (!level.isLoaded(cd.pos)) {
                continue;
            }
            IItemHandler h = cd.cache.getCapability();
            if (h != null) {
                activeDests.add(new ItemEndpoint(cd.pos, h, cd.mode));
            }
        }
        if (activeDests.isEmpty()) {
            return;
        }

        int stacksMoved = 0;
        for (CachedEndpoint cs : net.sources) {
            if (stacksMoved >= HyperConduitLogic.MAX_STACKS_PER_TICK) {
                break;
            }
            if (!level.isLoaded(cs.pos)) {
                continue;
            }
            IItemHandler srcHandler = cs.cache.getCapability();
            if (srcHandler == null) {
                continue;
            }
            int slots = srcHandler.getSlots();
            for (int slot = 0; slot < slots && stacksMoved < HyperConduitLogic.MAX_STACKS_PER_TICK; slot++) {
                ItemStack peek = srcHandler.getStackInSlot(slot);
                if (peek.isEmpty()) {
                    continue;
                }
                for (ItemEndpoint dst : activeDests) {
                    if (dst.pos.equals(cs.pos)) {
                        continue;
                    }
                    // Prevent ping-pong between two BOTH endpoints: only move BOTH->INSERT, EXTRACT->INSERT, EXTRACT->BOTH
                    if (cs.mode == FaceMode.BOTH && dst.mode == FaceMode.BOTH) {
                        continue;
                    }
                    ItemStack simExtract = srcHandler.extractItem(slot, peek.getMaxStackSize(), true);
                    if (simExtract.isEmpty()) {
                        break;
                    }
                    ItemStack simRem = ItemHandlerHelper.insertItemStacked(dst.handler, simExtract, true);
                    int accepted = simExtract.getCount() - simRem.getCount();
                    if (accepted <= 0) {
                        continue;
                    }
                    // Execute real extract & insert with strict conservation fallback
                    ItemStack realExtracted = srcHandler.extractItem(slot, accepted, false);
                    if (realExtracted.isEmpty()) {
                        break;
                    }
                    ItemStack leftover = ItemHandlerHelper.insertItemStacked(dst.handler, realExtracted, false);
                    if (!leftover.isEmpty()) {
                        ItemHandlerHelper.insertItemStacked(srcHandler, leftover, false);
                    }
                    stacksMoved++;
                    if (stacksMoved >= HyperConduitLogic.MAX_STACKS_PER_TICK) {
                        break;
                    }
                    if (srcHandler.getStackInSlot(slot).isEmpty()) {
                        break;
                    }
                }
            }
        }
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        int[] raw = new int[6];
        for (int i = 0; i < 6; i++) {
            raw[i] = faceModes[i].id();
        }
        tag.putIntArray(TAG_FACE_MODES, raw);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] raw = tag.getIntArray(TAG_FACE_MODES);
        for (int i = 0; i < 6; i++) {
            faceModes[i] = i < raw.length ? FaceMode.fromId(raw[i]) : FaceMode.BOTH;
        }
        cachedNetwork = null;
    }

    private record CachedEndpoint(BlockPos pos, BlockCapabilityCache<IItemHandler, Direction> cache, FaceMode mode) {}

    private record ItemEndpoint(BlockPos pos, IItemHandler handler, FaceMode mode) {}

    private static final class CachedNetwork {
        final ServerLevel level;
        final long version;
        final HyperItemConduitBlockEntity leader;
        final List<CachedEndpoint> sources;
        final List<CachedEndpoint> dests;
        long lastTick = Long.MIN_VALUE;

        CachedNetwork(ServerLevel level, long version, HyperItemConduitBlockEntity leader,
                      List<CachedEndpoint> sources, List<CachedEndpoint> dests) {
            this.level = level;
            this.version = version;
            this.leader = leader;
            this.sources = sources;
            this.dests = dests;
        }
    }
}
