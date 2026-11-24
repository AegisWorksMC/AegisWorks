package com.industrialcore.raid;

import com.industrialcore.raid.RiftArenaLogic.ReturnPoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 120: Persistent Overworld {@link SavedData} storing non-overlapping Rift Arena instance indices
 * and exact player return coordinates so victory, timeout, wipe, or relog NEVER strands a player in
 * {@code industrialcore:rift_arena}.
 */
public final class RiftArenaSavedData extends SavedData {
    private static final String DATA_NAME = "industrialcore_rift_arena";
    private int nextInstanceIndex = 0;
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();

    public static RiftArenaSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftArenaSavedData::new, RiftArenaSavedData::load),
                DATA_NAME);
    }

    public int allocateInstanceIndex() {
        int idx = nextInstanceIndex++;
        if (nextInstanceIndex > 100_000) {
            nextInstanceIndex = 0;
        }
        setDirty();
        return idx;
    }

    public void saveReturnPoint(UUID playerId, ReturnPoint point) {
        returnPoints.put(playerId, point);
        setDirty();
    }

    @Nullable
    public ReturnPoint getReturnPoint(UUID playerId) {
        return returnPoints.get(playerId);
    }

    @Nullable
    public ReturnPoint consumeReturnPoint(UUID playerId) {
        ReturnPoint pt = returnPoints.remove(playerId);
        if (pt != null) {
            setDirty();
        }
        return pt;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextInstance", nextInstanceIndex);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, ReturnPoint> e : returnPoints.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Player", e.getKey());
            ReturnPoint p = e.getValue();
            row.putString("Dim", p.dimensionId());
            row.putDouble("X", p.x());
            row.putDouble("Y", p.y());
            row.putDouble("Z", p.z());
            row.putFloat("YRot", p.yRot());
            row.putFloat("XRot", p.xRot());
            list.add(row);
        }
        tag.put("ReturnPoints", list);
        return tag;
    }

    private static RiftArenaSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftArenaSavedData data = new RiftArenaSavedData();
        data.nextInstanceIndex = Math.max(0, tag.getInt("NextInstance"));
        ListTag list = tag.getList("ReturnPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("Player")) continue;
            UUID id = row.getUUID("Player");
            data.returnPoints.put(id, new ReturnPoint(
                    row.getString("Dim"),
                    row.getDouble("X"),
                    row.getDouble("Y"),
                    row.getDouble("Z"),
                    row.getFloat("YRot"),
                    row.getFloat("XRot")));
        }
        return data;
    }
}
