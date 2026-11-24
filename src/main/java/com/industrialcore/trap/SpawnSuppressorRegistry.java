package com.industrialcore.trap;

import com.industrialcore.blockentity.SpawnSuppressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Phase 93: Per-dimension registry of actively powered {@code spawn_suppressor} beacons.
 *
 * <p>Registered beacons cancel {@link MobSpawnType#NATURAL} hostile spawns within their spherical
 * radius while strictly allowing {@code SPAWNER}, spawn eggs, commands, and passive/animal spawns.
 * Entries are cleaned up automatically on power loss, chunk unload, block removal, level unload,
 * and server stop so no stale coordinates outlive their chunk or dimension.</p>
 */
public final class SpawnSuppressorRegistry {
    private static final Map<ServerLevel, Map<BlockPos, Integer>> ACTIVE = new IdentityHashMap<>();

    private SpawnSuppressorRegistry() {}

    public static void upsert(ServerLevel level, BlockPos pos, int radius) {
        if (level == null || pos == null || radius <= 0) {
            return;
        }
        ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), radius);
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        Map<BlockPos, Integer> map = ACTIVE.get(level);
        if (map != null) {
            map.remove(pos);
            if (map.isEmpty()) {
                ACTIVE.remove(level);
            }
        }
    }

    public static int activeCount(ServerLevel level) {
        Map<BlockPos, Integer> map = ACTIVE.get(level);
        return map == null ? 0 : map.size();
    }

    public static void onMobSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (!(event.getLevel().getLevel() instanceof ServerLevel sl)) {
            return;
        }
        Mob mob = event.getEntity();
        boolean hostile = (mob instanceof Enemy) || mob.getType().getCategory() == MobCategory.MONSTER;
        boolean natural = event.getSpawnType() == MobSpawnType.NATURAL;
        if (!TrapWave2Logic.shouldSuppressSpawn(natural, hostile)) {
            return;
        }
        Map<BlockPos, Integer> map = ACTIVE.get(sl);
        if (map == null || map.isEmpty()) {
            return;
        }

        double sx = event.getX();
        double sy = event.getY();
        double sz = event.getZ();
        Iterator<Map.Entry<BlockPos, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            BlockPos pos = entry.getKey();
            int radius = entry.getValue();
            double dx = sx - (pos.getX() + 0.5D);
            double dy = sy - (pos.getY() + 0.5D);
            double dz = sz - (pos.getZ() + 0.5D);
            double distSq = dx * dx + dy * dy + dz * dz;
            if (!TrapWave2Logic.isInSuppressorRange(distSq, radius)) {
                continue;
            }
            if (!sl.isLoaded(pos)
                    || !(sl.getBlockEntity(pos) instanceof SpawnSuppressorBlockEntity be)
                    || !be.isSuppressing()) {
                it.remove();
                continue;
            }
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            be.recordSuppressedSpawn(sl);
            return;
        }
        if (map.isEmpty()) {
            ACTIVE.remove(sl);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            ACTIVE.remove(sl);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    public static void resetForTesting() {
        ACTIVE.clear();
    }
}
