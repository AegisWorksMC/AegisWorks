package com.industrialcore.life;

import com.industrialcore.life.LifeWave2Logic.WarpEntry;
import com.industrialcore.life.LifeWave2Logic.WarpRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 95: World-level persistent Warp Pad directory stored in the Overworld's
 * {@link net.minecraft.world.level.storage.DimensionDataStorage}.
 */
public final class WarpNetworkData extends SavedData {
    private static final String DATA_NAME = "industrialcore_warp_network";
    private static final String TAG_PADS = "Pads";

    private static final SavedData.Factory<WarpNetworkData> FACTORY =
            new SavedData.Factory<>(WarpNetworkData::new, WarpNetworkData::load);

    private final WarpRegistry registry = new WarpRegistry();

    public WarpNetworkData() {}

    public static WarpNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static WarpNetworkData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static String padId(ResourceKey<Level> dimension, BlockPos pos) {
        return LifeWave2Logic.makePadId(dimension.location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public WarpEntry registerPad(ServerLevel level, BlockPos pos, @Nullable UUID ownerUuid,
                                 @Nullable String name, boolean isPublic) {
        String id = padId(level.dimension(), pos);
        WarpEntry existing = registry.get(id);
        String finalName = (name != null && !name.isBlank())
                ? LifeWave2Logic.sanitizePadName(name, pos.getX(), pos.getY(), pos.getZ())
                : (existing != null ? existing.name()
                : LifeWave2Logic.sanitizePadName("", pos.getX(), pos.getY(), pos.getZ()));
        String ownerStr = ownerUuid != null ? ownerUuid.toString()
                : (existing != null ? existing.ownerUuid() : "");
        WarpEntry entry = new WarpEntry(
                id, level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(),
                finalName, ownerStr, isPublic);
        registry.registerOrUpdate(entry);
        setDirty();
        return entry;
    }

    public boolean unregisterPad(ResourceKey<Level> dimension, BlockPos pos) {
        return unregisterPad(padId(dimension, pos));
    }

    public boolean unregisterPad(String padId) {
        boolean removed = registry.remove(padId);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean renamePad(String padId, String newName) {
        boolean changed = registry.rename(padId, newName);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean setPublic(String padId, boolean isPublic) {
        boolean changed = registry.setPublic(padId, isPublic);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    @Nullable
    public WarpEntry get(String padId) {
        return registry.get(padId);
    }

    public List<WarpEntry> visibleTargetsFor(String sourcePadId, @Nullable UUID requesterUuid) {
        return registry.visibleTargetsFor(sourcePadId, requesterUuid == null ? "" : requesterUuid.toString());
    }

    public int count() {
        return registry.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarpEntry e : registry.allEntries()) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Id", e.padId());
            ct.putString("Dim", e.dimensionId());
            ct.putInt("X", e.x());
            ct.putInt("Y", e.y());
            ct.putInt("Z", e.z());
            ct.putString("Name", e.name());
            ct.putString("Owner", e.ownerUuid());
            ct.putBoolean("Public", e.isPublic());
            list.add(ct);
        }
        tag.put(TAG_PADS, list);
        return tag;
    }

    public static WarpNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpNetworkData data = new WarpNetworkData();
        ListTag list = tag.getList(TAG_PADS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            String id = ct.getString("Id");
            String dim = ct.getString("Dim");
            int x = ct.getInt("X");
            int y = ct.getInt("Y");
            int z = ct.getInt("Z");
            String name = LifeWave2Logic.sanitizePadName(ct.getString("Name"), x, y, z);
            String owner = ct.getString("Owner");
            boolean pub = ct.getBoolean("Public");
            if (!id.isEmpty() && !dim.isEmpty()) {
                data.registry.registerOrUpdate(new WarpEntry(id, dim, x, y, z, name, owner, pub));
            }
        }
        return data;
    }
}
