package com.industrialcore.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Dist-safe bridge resolving the effective motor/battery upgrade stack cap (1..64) for any machine
 * menu slot on both logical server and logical client (Phase 87).
 */
public final class UpgradeCapBridge {
    /** Installed by {@code ClientSetup}; falls back to 64 on headless / dedicated servers. */
    public static volatile ToIntFunction<String> clientUpgradeLimit =
            ignored -> MachineLevelMath.MAX_UPGRADE_CAP;

    private UpgradeCapBridge() {}

    public static int effectiveLimit(@Nullable Player player, @Nullable IOwnedMachine owned, String machineId) {
        if (player == null) {
            return MachineLevelMath.MAX_UPGRADE_CAP;
        }
        if (player.isCreative()) {
            return MachineLevelMath.MAX_UPGRADE_CAP;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            UUID owner = owned != null ? owned.getOwnerUuid() : null;
            return MachineProgressSavedData.effectiveUpgradeLimit(serverLevel, owner, machineId, false);
        }
        // Client side: mirror local player's level cap when owner is unknown or matches local player
        if (owned != null && owned.getOwnerUuid() != null
                && !owned.getOwnerUuid().equals(player.getUUID())) {
            return MachineLevelMath.MAX_UPGRADE_CAP;
        }
        return clientUpgradeLimit.applyAsInt(machineId);
    }
}
