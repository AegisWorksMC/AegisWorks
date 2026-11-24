package com.industrialcore.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Lightweight per-BlockEntity helper for ownership persistence and batched work-tick XP (Phase 84).
 *
 * <p>Zero heap allocations per tick: counts active FE-consuming ticks locally and flushes to
 * {@link MachineProgressSavedData} only once every {@link MachineLevelMath#BATCH_WORK_TICKS} (20)
 * active ticks.</p>
 */
public final class MachineWorkTracker {
    public static final String TAG_OWNER = "OwnerUuid";

    @Nullable
    private UUID ownerUuid;
    private int activeWorkTicks;
    /** Transient flag set when a Creative player opens or operates the machine's upgrade GUI. */
    private boolean creativeOperator;

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void setCreativeOperator(boolean creative) {
        this.creativeOperator = creative;
    }

    /**
     * Records one server tick where this machine genuinely consumed FE to perform work.
     * Flushes one XP batch to Overworld SavedData every 20 active ticks.
     */
    public void recordActiveTick(ServerLevel level, String machineId) {
        if (ownerUuid == null) {
            return;
        }
        if (++activeWorkTicks >= MachineLevelMath.BATCH_WORK_TICKS) {
            activeWorkTicks = 0;
            MachineProgressSavedData.recordWorkBatch(
                    level, ownerUuid, machineId, MachineLevelMath.XP_PER_WORK_SECOND);
        }
    }

    /** True when Creative bypass applies (current operator or online owner is Creative). */
    public boolean isCreativeBypass(ServerLevel level) {
        if (creativeOperator) {
            return true;
        }
        if (ownerUuid != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null && owner.isCreative()) {
                return true;
            }
        }
        return false;
    }

    /** Effective upgrade cap (1..64 by owner level, or 64 when unowned / Creative). */
    public int upgradeLimit(ServerLevel level, String machineId) {
        return MachineProgressSavedData.effectiveUpgradeLimit(
                level, ownerUuid, machineId, isCreativeBypass(level));
    }

    /** Phase 113: Effective total virtual upgrade cap (4 at Lv.1 -> 96 at Lv.99 -> 1000 at Lv.1000). */
    public int totalUpgradeLimit(ServerLevel level, String machineId) {
        return MachineProgressSavedData.effectiveTotalUpgradeLimit(
                level, ownerUuid, machineId, isCreativeBypass(level));
    }

    /** Owner's current machine level (0 when unowned). */
    public int ownerLevel(ServerLevel level, String machineId) {
        return MachineProgressSavedData.ownerLevel(level, ownerUuid, machineId);
    }

    /** Phase 99: True when the owner has activated Lv.1000 Limit Break on this machine's branch. */
    public boolean isOwnerBranchLimitBroken(ServerLevel level, String machineId) {
        return MachineProgressSavedData.isOwnerBranchLimitBroken(level, ownerUuid, machineId);
    }

    public void save(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
    }

    public void load(CompoundTag tag) {
        if (tag.hasUUID(TAG_OWNER)) {
            ownerUuid = tag.getUUID(TAG_OWNER);
        } else {
            ownerUuid = null;
        }
        activeWorkTicks = 0;
    }
}
