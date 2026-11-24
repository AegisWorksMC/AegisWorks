package com.industrialcore.life;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.ChunkLoaderBlockEntity;
import com.industrialcore.life.LifeWave2Logic.ChunkCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 95: NeoForge 1.21.1 {@link TicketController} manager for {@code chunk_loader}.
 *
 * <p>Guarantees zero ghost chunks by reconciling desired vs active chunk sets on every state or
 * radius change, releasing 100% of tickets on block removal/power loss/disable, and validating
 * persisted tickets on world reload via {@link #validateTickets(ServerLevel, TicketHelper)}.</p>
 */
public final class ChunkLoaderTickets {
    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "chunk_loader"),
            ChunkLoaderTickets::validateTickets);

    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<ChunkCoord>>> ACTIVE = new HashMap<>();

    private ChunkLoaderTickets() {}

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Called by NeoForge on world reload to purge any orphaned tickets whose ChunkLoader is gone. */
    public static void validateTickets(ServerLevel level, TicketHelper helper) {
        for (BlockPos ownerPos : helper.getBlockTickets().keySet()) {
            if (!(level.getBlockEntity(ownerPos) instanceof ChunkLoaderBlockEntity be)
                    || !be.shouldKeepChunksLoaded()) {
                helper.removeAllTickets(ownerPos);
            }
        }
    }

    /** Reconciles the forced chunk set for {@code ownerPos} to match {@code desired} cleanly. */
    public static void syncForcedChunks(ServerLevel level, BlockPos ownerPos, List<ChunkCoord> desired) {
        Map<BlockPos, Set<ChunkCoord>> dimMap = ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        BlockPos key = ownerPos.immutable();
        Set<ChunkCoord> current = dimMap.computeIfAbsent(key, k -> new HashSet<>());
        Set<ChunkCoord> wanted = new HashSet<>(desired);

        // Release any chunk no longer desired
        for (ChunkCoord old : Set.copyOf(current)) {
            if (!wanted.contains(old)) {
                CONTROLLER.forceChunk(level, key, old.cx(), old.cz(), false, true);
                current.remove(old);
            }
        }
        // Acquire newly desired chunks
        for (ChunkCoord next : wanted) {
            if (!current.contains(next)) {
                if (CONTROLLER.forceChunk(level, key, next.cx(), next.cz(), true, true)) {
                    current.add(next);
                }
            }
        }
        if (current.isEmpty()) {
            dimMap.remove(key);
        }
    }

    /** Immediately releases every chunk ticket held by {@code ownerPos}. */
    public static void releaseAll(ServerLevel level, BlockPos ownerPos) {
        BlockPos key = ownerPos.immutable();
        Map<BlockPos, Set<ChunkCoord>> dimMap = ACTIVE.get(level.dimension());
        if (dimMap != null) {
            Set<ChunkCoord> held = dimMap.remove(key);
            if (held != null) {
                for (ChunkCoord c : held) {
                    CONTROLLER.forceChunk(level, key, c.cx(), c.cz(), false, true);
                }
            }
        }
        // Defensive sweep of the maximum possible 5x5 region so even post-reload tickets never leak
        int baseCx = key.getX() >> 4;
        int baseCz = key.getZ() >> 4;
        for (ChunkCoord c : LifeWave2Logic.chunkCoords(baseCx, baseCz, LifeWave2Logic.CHUNK_MAX_RADIUS)) {
            CONTROLLER.forceChunk(level, key, c.cx(), c.cz(), false, true);
        }
    }

    public static int getActiveChunkCount(ServerLevel level, BlockPos ownerPos) {
        Map<BlockPos, Set<ChunkCoord>> dimMap = ACTIVE.get(level.dimension());
        if (dimMap == null) {
            return 0;
        }
        Set<ChunkCoord> held = dimMap.get(ownerPos);
        return held == null ? 0 : held.size();
    }

    public static Set<ChunkCoord> getActiveChunks(ServerLevel level, BlockPos ownerPos) {
        Map<BlockPos, Set<ChunkCoord>> dimMap = ACTIVE.get(level.dimension());
        if (dimMap == null) {
            return Set.of();
        }
        Set<ChunkCoord> held = dimMap.get(ownerPos);
        return held == null ? Set.of() : Collections.unmodifiableSet(held);
    }

    public static void onLevelUnload(ServerLevel level) {
        ACTIVE.remove(level.dimension());
    }

    public static void onServerStopped() {
        ACTIVE.clear();
    }
}
