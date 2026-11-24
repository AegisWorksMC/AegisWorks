package com.industrialcore.block;

import com.industrialcore.reactor.ReactorMultiblockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 103: Structural block for multiblock reactors ({@code reactor_casing} and
 * {@code reactor_coolant_block}). Immediately notifies {@link ReactorMultiblockManager} on removal.
 */
public class ReactorPartBlock extends Block {
    public ReactorPartBlock(Properties properties) {
        super(properties);
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
