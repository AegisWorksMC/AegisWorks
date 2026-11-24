package com.industrialcore.machine;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension registry of the loaded transporters (Phases 26-29).
 *
 * <p>Each {@link ServerLevel} owns one {@link ChannelIndex}; devices register on load and
 * configuration changes and unregister on removal or chunk unload. The static map is a lookup
 * cache for the server thread, not saved data: a device's channel and role live in its own NBT,
 * and the index is rebuilt from them while chunks load. Everything is dropped when a level
 * unloads or the server stops, so no connection information outlives the world it belongs to.</p>
 */
public final class TransporterRegistry {
    private static final Map<ServerLevel, ChannelIndex> INDEXES = new IdentityHashMap<>();

    private TransporterRegistry() {}

    /** The index of a dimension, created on first use. */
    public static ChannelIndex indexFor(ServerLevel level) {
        return INDEXES.computeIfAbsent(level, ignored -> new ChannelIndex());
    }

    public static void upsert(ServerLevel level, GridPos position, String channelKey,
                              TransportRole role) {
        indexFor(level).upsert(position, channelKey, role);
    }

    public static void remove(ServerLevel level, GridPos position) {
        ChannelIndex index = INDEXES.get(level);
        if (index != null) {
            index.remove(position);
        }
    }

    /** Registered receivers of a channel in deterministic order; empty when the channel is new. */
    public static Collection<GridPos> receivers(ServerLevel level, String channelKey) {
        ChannelIndex index = INDEXES.get(level);
        return index == null ? List.of() : index.receivers(channelKey);
    }

    public static int receiverCount(ServerLevel level, String channelKey) {
        ChannelIndex index = INDEXES.get(level);
        return index == null ? 0 : index.receiverCount(channelKey);
    }

    public static int senderCount(ServerLevel level, String channelKey) {
        ChannelIndex index = INDEXES.get(level);
        return index == null ? 0 : index.senderCount(channelKey);
    }

    /**
     * World switch / dimension unload: forget the dimension's index. Registered positions are
     * plain values (no block entity references), so nothing can leak even if a level object is
     * dropped without this event - this only keeps the map itself tidy.
     */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            INDEXES.remove(serverLevel);
        }
    }

    /** Server stop: drop every index, including integrated-server singleplayer worlds. */
    public static void onServerStopped(ServerStoppedEvent event) {
        INDEXES.clear();
    }

    /** Test hook: the live index of a dimension without creating one. */
    public static ChannelIndex indexForTesting(ServerLevel level) {
        return INDEXES.get(level);
    }

    /** Test hook: forget everything (the server-stopped event does the same in production). */
    public static void resetForTesting() {
        INDEXES.clear();
    }
}
