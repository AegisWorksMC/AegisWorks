package com.industrialcore.level;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Server-side lifecycle hooks for the machine level &amp; craft-unlock gate system (Phase 84–97).
 */
public final class MachineProgressEvents {
    private MachineProgressEvents() {}

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (be instanceof IOwnedMachine owned) {
                owned.setOwnerUuid(player.getUUID());
                be.setChanged();
            }
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof net.minecraft.world.level.Level lvl)
                || lvl.isClientSide()) {
            return;
        }
        BlockEntity be = lvl.getBlockEntity(event.getPos());
        if (be instanceof com.industrialcore.machine.IUpgradeableMachine upg) {
            upg.getUpgradeBank().dropAll(lvl, event.getPos());
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraftUnlockGate.wrapResultSlot(player.inventoryMenu, player);
            MachineProgressSavedData.syncToPlayer(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraftUnlockGate.wrapResultSlot(player.inventoryMenu, player);
            MachineProgressSavedData.syncToPlayer(player);
        }
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraftUnlockGate.wrapResultSlot(event.getContainer(), player);
        }
    }
}
