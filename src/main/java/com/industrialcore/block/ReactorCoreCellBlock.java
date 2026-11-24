package com.industrialcore.block;

import com.industrialcore.reactor.ReactorMultiblockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Phase 103: Interior energy core cell ({@code reactor_core_cell}) for multiblock reactors.
 * Switches {@link #LIT} to {@code true} while the owning reactor is actively generating power.
 */
public class ReactorCoreCellBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ReactorCoreCellBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
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
