package com.industrialcore.reactor;

import com.industrialcore.reactor.ReactorMultiblockLogic.Facing2D;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.PartKind;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import com.industrialcore.reactor.ReactorMultiblockLogic.ValidationResult;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 103: Bridges Minecraft {@link Level} block states and {@link Direction} onto
 * {@link ReactorMultiblockLogic} for both server-side validation and client-side 3D guide auto-hide.
 */
public final class ReactorWorldHelper {
    private ReactorWorldHelper() {}

    public static Facing2D toFacing2D(Direction dir) {
        return switch (dir) {
            case SOUTH -> Facing2D.SOUTH;
            case WEST -> Facing2D.WEST;
            case EAST -> Facing2D.EAST;
            default -> Facing2D.NORTH;
        };
    }

    public static PartKind classifyState(BlockState state) {
        if (state.isAir()) {
            return PartKind.AIR;
        }
        if (state.is(ModBlocks.REACTOR_CASING.get())) {
            return PartKind.CASING;
        }
        if (state.is(ModBlocks.REACTOR_GLASS.get())) {
            return PartKind.GLASS;
        }
        if (state.is(ModBlocks.REACTOR_CORE_CELL.get())) {
            return PartKind.CORE_CELL;
        }
        if (state.is(ModBlocks.REACTOR_COOLANT_BLOCK.get())) {
            return PartKind.COOLANT;
        }
        if (state.is(ModBlocks.REACTOR_PORT.get())) {
            return PartKind.PORT;
        }
        if (state.is(ModBlocks.NUCLEAR_GENERATOR.get())) {
            return PartKind.NUCLEAR_CTRL;
        }
        if (state.is(ModBlocks.SINGULARITY_REACTOR.get())) {
            return PartKind.SINGULARITY_CTRL;
        }
        return PartKind.OTHER;
    }

    public static ValidationResult validateInWorld(Level level, ReactorType type,
                                                   BlockPos ctrlPos, Direction facing) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        IntPos ctrl = new IntPos(ctrlPos.getX(), ctrlPos.getY(), ctrlPos.getZ());
        Facing2D f = toFacing2D(facing);

        ReactorMultiblockLogic.BlockSampler sampler = (x, y, z) -> {
            cursor.set(x, y, z);
            if (!level.isLoaded(cursor)) {
                return PartKind.OTHER;
            }
            return classifyState(level.getBlockState(cursor));
        };

        ReactorMultiblockLogic.OverlapChecker overlap = (level instanceof ServerLevel sl)
                ? (x, y, z) -> {
                    cursor.set(x, y, z);
                    return ReactorMultiblockManager.isClaimedByOther(sl, cursor, ctrlPos);
                }
                : (x, y, z) -> false;

        return ReactorMultiblockLogic.validate(type, ctrl, f, sampler, overlap);
    }
}
