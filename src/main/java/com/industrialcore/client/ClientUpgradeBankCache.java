package com.industrialcore.client;

import com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory;
import com.industrialcore.network.UpgradeBankSyncPayload;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 113 client-side snapshot cache for open machine {@link UpgradeBankSyncPayload} data.
 */
public final class ClientUpgradeBankCache {
    public record Snapshot(
            int motors,
            int batteries,
            int specials,
            int totalLimit,
            SpecialCategory category,
            String machineId
    ) {
        public int total() {
            return motors + batteries + specials;
        }
    }

    private static final Map<BlockPos, Snapshot> CACHE = new HashMap<>();

    private ClientUpgradeBankCache() {}

    public static void apply(UpgradeBankSyncPayload p) {
        SpecialCategory[] vals = SpecialCategory.values();
        SpecialCategory cat = (p.categoryOrdinal() >= 0 && p.categoryOrdinal() < vals.length)
                ? vals[p.categoryOrdinal()] : SpecialCategory.PROCESSING;
        CACHE.put(p.pos(), new Snapshot(p.motors(), p.batteries(), p.specials(),
                p.totalLimit(), cat, p.machineId()));
    }

    @Nullable
    public static Snapshot get(@Nullable BlockPos pos) {
        return pos == null ? null : CACHE.get(pos);
    }

    public static void clear() {
        CACHE.clear();
    }
}
