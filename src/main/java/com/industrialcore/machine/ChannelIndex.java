package com.industrialcore.machine;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The connection cache of the transporter network (Phases 26-29): channel name and role to the
 * registered device positions of one dimension.
 *
 * <p>The index is what makes "never scan the world every tick" true. A sender asks the index for
 * the receivers of its channel (a {@link TreeMap} lookup in X/Y/Z order), walks that small
 * candidate list and checks the loaded candidates - it never touches chunks or block entities it
 * does not need. Entries are added when a device is loaded or its configuration changes, and
 * removed when it is destroyed, unloaded, or its structure/channel becomes unusable.</p>
 *
 * <p>Determinism: candidates iterate in {@link GridPos} order (X, then Y, then Z), so the same
 * world state always produces the same destination choice - never a random one. Multiple senders
 * act in Minecraft's own block-tick order; each of them still moves its items exactly once per
 * interval into at most one receiver, which is what keeps concurrent senders from duplicating or
 * losing items.</p>
 *
 * <p>Not thread safe: like every other game structure, it is only touched on the server thread.</p>
 */
public final class ChannelIndex {

    /** What the index knows about one device position. */
    public record Assignment(String channelKey, TransportRole role) {
    }

    /** channel key -> role -> positions, ordered by (x, y, z). */
    private final Map<String, EnumMap<TransportRole, TreeMap<GridPos, GridPos>>> channels =
            new HashMap<>();
    /** Current assignment of every registered position; keeps re-registration churn-free. */
    private final Map<GridPos, Assignment> byPosition = new HashMap<>();

    /**
     * Registers {@code position} under {@code channelKey}/{@code role}, moving it from its old
     * bucket when the channel or role changed. Re-registering the unchanged assignment does
     * nothing, so a device may refresh itself as often as it likes.
     */
    public void upsert(GridPos position, String channelKey, TransportRole role) {
        if (channelKey == null || channelKey.isEmpty()) {
            remove(position);
            return;
        }
        Assignment next = new Assignment(channelKey, role);
        Assignment previous = byPosition.get(position);
        if (next.equals(previous)) {
            return;
        }
        if (previous != null) {
            unbucket(previous, position);
        }
        channels.computeIfAbsent(channelKey, key -> new EnumMap<>(TransportRole.class))
                .computeIfAbsent(role, ignored -> new TreeMap<>())
                .put(position, position);
        byPosition.put(position, next);
    }

    /** Unregisters a position; returns true when something was removed. */
    public boolean remove(GridPos position) {
        Assignment previous = byPosition.remove(position);
        if (previous == null) {
            return false;
        }
        unbucket(previous, position);
        return true;
    }

    /** Registered receivers of a channel in (x, y, z) order; never {@code null}. */
    public Collection<GridPos> receivers(String channelKey) {
        return bucket(channelKey, TransportRole.RECEIVE);
    }

    /** Registered senders of a channel in (x, y, z) order; never {@code null}. */
    public Collection<GridPos> senders(String channelKey) {
        return bucket(channelKey, TransportRole.SEND);
    }

    public int receiverCount(String channelKey) {
        return bucket(channelKey, TransportRole.RECEIVE).size();
    }

    public int senderCount(String channelKey) {
        return bucket(channelKey, TransportRole.SEND).size();
    }

    /** Current assignment of a position, or {@code null} when it is not registered. */
    public Assignment assignmentOf(GridPos position) {
        return byPosition.get(position);
    }

    /** Number of registered positions (all channels). */
    public int size() {
        return byPosition.size();
    }

    public void clear() {
        channels.clear();
        byPosition.clear();
    }

    private Collection<GridPos> bucket(String channelKey, TransportRole role) {
        if (channelKey == null || channelKey.isEmpty()) {
            return List.of();
        }
        EnumMap<TransportRole, TreeMap<GridPos, GridPos>> roles = channels.get(channelKey);
        if (roles == null) {
            return List.of();
        }
        TreeMap<GridPos, GridPos> positions = roles.get(role);
        return positions == null ? List.of() : positions.values();
    }

    private void unbucket(Assignment assignment, GridPos position) {
        EnumMap<TransportRole, TreeMap<GridPos, GridPos>> roles =
                channels.get(assignment.channelKey());
        if (roles == null) {
            return;
        }
        TreeMap<GridPos, GridPos> positions = roles.get(assignment.role());
        if (positions != null) {
            positions.remove(position);
            if (positions.isEmpty()) {
                roles.remove(assignment.role());
            }
        }
        if (roles.isEmpty()) {
            channels.remove(assignment.channelKey());
        }
    }
}
