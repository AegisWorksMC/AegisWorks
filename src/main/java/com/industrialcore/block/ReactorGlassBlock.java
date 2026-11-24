package com.industrialcore.block;

import com.industrialcore.reactor.ReactorMultiblockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Phase 103 / 105.8: Heat-resistant fully transparent wall glass ({@code reactor_glass}) for
 * multiblock reactors. Extends {@link TransparentBlock} and explicitly implements glass culling,
 * empty visual shape, and skylight propagation so illuminated interior {@code reactor_core_cell}s
 * shine crystal-clear from outside with zero seams between adjacent glass blocks.
 */
public class ReactorGlassBlock extends TransparentBlock {
    public ReactorGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            ReactorMultiblockManager.notifyStructureBlockBroken(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
