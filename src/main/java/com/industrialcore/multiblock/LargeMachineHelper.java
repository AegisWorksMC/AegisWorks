package com.industrialcore.multiblock;

import com.industrialcore.block.MachineBoundingBlock;
import com.industrialcore.blockentity.MachineBoundingBlockEntity;
import com.industrialcore.multiblock.LargeMachineLogic.Facing2D;
import com.industrialcore.multiblock.LargeMachineLogic.IntPos;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Phase 104: World-level helper for auto-expanding large machines: placement space checks,
 * proxy expansion, and atomic single-drop teardown with re-entrancy protection.
 */
public final class LargeMachineHelper {
    private static final ThreadLocal<Boolean> TEARING_DOWN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LargeMachineHelper() {}

    public static Facing2D toFacing2D(Direction dir) {
        return switch (dir) {
            case SOUTH -> Facing2D.SOUTH;
            case WEST -> Facing2D.WEST;
            case EAST -> Facing2D.EAST;
            default -> Facing2D.NORTH;
        };
    }

    /**
     * Checks whether the full auto-expanding footprint fits in the world at {@code masterPos}.
     * If blocked, notifies the player on the action bar and returns {@code false} so
     * {@code getStateForPlacement} returns {@code null} (zero item consumption, zero blocks placed).
     */
    public static boolean checkPlacementSpace(BlockPlaceContext context, MachineSpec spec, Direction facing) {
        Level level = context.getLevel();
        BlockPos masterPos = context.getClickedPos();
        IntPos origin = new IntPos(masterPos.getX(), masterPos.getY(), masterPos.getZ());
        Facing2D f2d = toFacing2D(facing);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        boolean ok = LargeMachineLogic.canPlace(
                spec, origin, f2d,
                level.getMinBuildHeight(), level.getMaxBuildHeight(),
                (x, y, z) -> {
                    cursor.set(x, y, z);
                    return level.isInWorldBounds(cursor)
                            && level.getBlockState(cursor).canBeReplaced(context);
                });

        if (!ok) {
            Player player = context.getPlayer();
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.industrialcore.large_machine.no_space",
                                spec.width(), spec.height(), spec.depth())
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
        return ok;
    }

    /**
     * Places {@code machine_bounding} proxies at every non-master offset and links each proxy's
     * BlockEntity to {@code masterPos}.
     */
    public static void expandProxies(Level level, MachineSpec spec, BlockPos masterPos, Direction facing) {
        if (level.isClientSide) {
            return;
        }
        IntPos origin = new IntPos(masterPos.getX(), masterPos.getY(), masterPos.getZ());
        Facing2D f2d = toFacing2D(facing);
        BlockState proxyState = ModBlocks.MACHINE_BOUNDING.get().defaultBlockState()
                .setValue(MachineBoundingBlock.LOW_PAD, spec.isLowPad());

        for (IntPos p : LargeMachineLogic.proxyPositions(spec, origin, f2d)) {
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            level.setBlock(pos, proxyState, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxyBe) {
                proxyBe.setMasterPos(masterPos);
            }
        }
    }

    /**
     * Called when the MASTER block is removed: removes all linked proxies cleanly.
     */
    public static void removeProxiesForMaster(Level level, MachineSpec spec, BlockPos masterPos, Direction facing) {
        if (level.isClientSide || Boolean.TRUE.equals(TEARING_DOWN.get())) {
            return;
        }
        TEARING_DOWN.set(Boolean.TRUE);
        try {
            IntPos origin = new IntPos(masterPos.getX(), masterPos.getY(), masterPos.getZ());
            Facing2D f2d = toFacing2D(facing);
            for (IntPos p : LargeMachineLogic.proxyPositions(spec, origin, f2d)) {
                BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                if (level.getBlockState(pos).is(ModBlocks.MACHINE_BOUNDING.get())) {
                    if (level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxyBe) {
                        BlockPos linked = proxyBe.getMasterPos();
                        if (linked != null && !linked.equals(masterPos)) {
                            continue; // Belongs to a different machine
                        }
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        } finally {
            TEARING_DOWN.set(Boolean.FALSE);
        }
    }

    /**
     * Called when ANY proxy block is broken: atomically removes all sibling proxies and the master
     * block, dropping the master item EXACTLY ONCE when broken in survival (or by non-player).
     */
    public static void destroyFromProxy(ServerLevel level, BlockPos brokenProxyPos,
                                        BlockPos masterPos, @Nullable Player breaker) {
        if (Boolean.TRUE.equals(TEARING_DOWN.get())) {
            return;
        }
        TEARING_DOWN.set(Boolean.TRUE);
        try {
            if (!level.isLoaded(masterPos)) {
                return;
            }
            BlockState masterState = level.getBlockState(masterPos);
            MachineSpec spec = specForBlock(masterState.getBlock());
            Direction facing = facingOf(masterState);

            // 1. Remove all sibling proxies first (excluding the one already being broken)
            if (spec != null) {
                IntPos origin = new IntPos(masterPos.getX(), masterPos.getY(), masterPos.getZ());
                List<IntPos> proxies = LargeMachineLogic.proxyPositions(spec, origin, toFacing2D(facing));
                for (IntPos p : proxies) {
                    BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                    if (!pos.equals(brokenProxyPos)
                            && level.getBlockState(pos).is(ModBlocks.MACHINE_BOUNDING.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }

            // 2. Drop EXACTLY ONE master item when broken by survival player or non-player cause
            if (spec != null && (breaker == null || !breaker.isCreative())) {
                BlockEntity masterBe = level.getBlockEntity(masterPos);
                ItemStack tool = breaker != null ? breaker.getMainHandItem() : ItemStack.EMPTY;
                Block.dropResources(masterState, level, masterPos, masterBe, breaker, tool);
            }

            // 3. Remove master block (invokes master.onRemove for inventory drops & warp pad cleanup)
            if (!masterState.isAir()) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            TEARING_DOWN.set(Boolean.FALSE);
        }
    }

    @Nullable
    public static MachineSpec specForBlock(Block block) {
        if (block == ModBlocks.MOB_DUPLICATOR.get()) {
            return MachineSpec.MOB_DUPLICATOR;
        }
        if (block == ModBlocks.WARP_PAD.get()) {
            return MachineSpec.WARP_PAD;
        }
        if (block == ModBlocks.CLIMATE_CONTROLLER.get()) {
            return MachineSpec.CLIMATE_CONTROLLER;
        }
        if (block == ModBlocks.QUANTUM_STORAGE_CORE.get()) {
            return MachineSpec.QUANTUM_STORAGE_CORE;
        }
        if (block == ModBlocks.SUIT_MODIFIER.get()) {
            return MachineSpec.SUIT_MODIFIER;
        }
        return null;
    }

    public static Direction facingOf(BlockState state) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }

    /** Convenience helper: returns true if a master block is currently undergoing coordinated teardown. */
    public static boolean isTearingDown() {
        return Boolean.TRUE.equals(TEARING_DOWN.get());
    }
}
