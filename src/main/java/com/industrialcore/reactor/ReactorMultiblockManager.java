package com.industrialcore.reactor;

import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Phase 103: Server-side per-level ownership registry for formed multiblock reactors.
 *
 * <ul>
 *   <li>Prevents two formed multiblocks from sharing any block (Rule 5).</li>
 *   <li>Triggers immediate teardown on the owning controller the exact tick any constituent block
 *       is broken or replaced.</li>
 * </ul>
 */
public final class ReactorMultiblockManager {
    private static final Map<ResourceKey<Level>, LevelRegistry> LEVELS = new HashMap<>();

    private ReactorMultiblockManager() {}

    private static LevelRegistry forLevel(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new LevelRegistry());
    }

    /** True if {@code pos} currently belongs to a DIFFERENT formed reactor controller. */
    public static boolean isClaimedByOther(ServerLevel level, BlockPos pos, BlockPos selfController) {
        LevelRegistry reg = LEVELS.get(level.dimension());
        if (reg == null) {
            return false;
        }
        BlockPos owner = reg.blockToController.get(pos);
        return owner != null && !owner.equals(selfController);
    }

    /** Claims all constituent blocks for a formed controller (replacing any prior claim). */
    public static void registerFormed(ServerLevel level, BlockPos controller, Set<BlockPos> blocks) {
        LevelRegistry reg = forLevel(level);
        unregisterFrom(reg, controller);
        BlockPos immutableCtrl = controller.immutable();
        Set<BlockPos> copy = new HashSet<>(blocks.size());
        for (BlockPos p : blocks) {
            BlockPos im = p.immutable();
            copy.add(im);
            reg.blockToController.put(im, immutableCtrl);
        }
        reg.controllerToBlocks.put(immutableCtrl, copy);
    }

    /** Releases all blocks previously claimed by {@code controller}. */
    public static void unregisterController(ServerLevel level, BlockPos controller) {
        LevelRegistry reg = LEVELS.get(level.dimension());
        if (reg != null) {
            unregisterFrom(reg, controller);
        }
    }

    private static void unregisterFrom(LevelRegistry reg, BlockPos controller) {
        Set<BlockPos> prev = reg.controllerToBlocks.remove(controller);
        if (prev != null) {
            for (BlockPos p : prev) {
                reg.blockToController.remove(p, controller);
            }
        }
    }

    /**
     * Called when any reactor structure block is broken/replaced: immediately notifies the owning
     * controller so generation halts, port links clear, and core cells turn unlit on the spot.
     */
    public static void notifyStructureBlockBroken(ServerLevel level, BlockPos brokenPos) {
        LevelRegistry reg = LEVELS.get(level.dimension());
        if (reg == null) {
            return;
        }
        BlockPos owner = reg.blockToController.get(brokenPos);
        if (owner == null) {
            return;
        }
        // Release immediately and tell controller to unform now
        unregisterFrom(reg, owner);
        if (!level.isLoaded(owner)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(owner);
        if (be instanceof GenerationBlockEntity gen) {
            gen.onMultiblockBroken(level);
        } else if (be instanceof SingularityReactorBlockEntity sr) {
            sr.onMultiblockBroken(level);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            LEVELS.remove(sl.dimension());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.clear();
    }

    private static final class LevelRegistry {
        final Map<BlockPos, Set<BlockPos>> controllerToBlocks = new HashMap<>();
        final Map<BlockPos, BlockPos> blockToController = new HashMap<>();
    }
}
