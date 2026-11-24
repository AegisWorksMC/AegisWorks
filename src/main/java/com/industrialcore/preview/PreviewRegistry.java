package com.industrialcore.preview;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of quarries that should currently draw their frame. The server decides
 * the range and cursor; this class only stores what was synced plus the client tick at which
 * it arrived, which the renderer uses to interpolate without sending extra packets.
 * No blocks are ever placed.
 */
public final class PreviewRegistry {
    /** A synced snapshot together with the client game tick it was received on. */
    public record Entry(PreviewData data, long arrivedAtTick) {}

    private static final Map<BlockPos, Entry> ENTRIES = new ConcurrentHashMap<>();

    private PreviewRegistry() {}

    public static void update(PreviewData data, long clientGameTime) {
        if (data.preview() || data.running()) {
            ENTRIES.put(data.machinePos(), new Entry(data, clientGameTime));
        } else {
            ENTRIES.remove(data.machinePos());
        }
    }

    public static void remove(BlockPos pos) {
        ENTRIES.remove(pos);
    }

    public static Collection<Entry> snapshot() {
        if (ENTRIES.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(ENTRIES.values());
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
