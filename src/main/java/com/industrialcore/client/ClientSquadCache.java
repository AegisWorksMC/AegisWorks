package com.industrialcore.client;

import com.industrialcore.network.SquadStatusSyncPayload;
import com.industrialcore.suit.SquadLogic.SquadMember;

import java.util.List;

/**
 * Phase 100: Client-side cache of nearby squad members synced via {@link SquadStatusSyncPayload}.
 * Automatically clears when the player changes dimensions or logs out.
 */
public final class ClientSquadCache {
    private static volatile String cachedDimension = "";
    private static volatile List<SquadMember> members = List.of();
    private static volatile boolean overrideActive = false;

    private ClientSquadCache() {}

    public static void apply(SquadStatusSyncPayload payload) {
        if (overrideActive) {
            return;
        }
        cachedDimension = payload.dimensionId() == null ? "" : payload.dimensionId();
        members = payload.members() == null ? List.of() : List.copyOf(payload.members());
    }

    /** Returns current squad members for {@code currentDimension}; clears stale cache on dim change. */
    public static List<SquadMember> membersForDimension(String currentDimension) {
        if (overrideActive) {
            return members;
        }
        if (currentDimension == null || !currentDimension.equals(cachedDimension)) {
            if (!members.isEmpty()) {
                clear();
            }
            return List.of();
        }
        return members;
    }

    /** Test/demo helper to inject deterministic squad members on the client. */
    public static void setTestOverride(List<SquadMember> injected) {
        if (injected == null) {
            overrideActive = false;
            clear();
        } else {
            overrideActive = true;
            members = List.copyOf(injected);
        }
    }

    public static void clear() {
        overrideActive = false;
        cachedDimension = "";
        members = List.of();
    }
}
