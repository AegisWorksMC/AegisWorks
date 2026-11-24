package com.industrialcore.block;

import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.LivingFactoryParticleHook;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Phase 105: Dedicated multiblock controller block for the Nuclear Generator ({@code nuclear_generator}).
 * Extends {@link GenerationBlock} with {@link #LIT} so the flush recessed front console glows vivid
 * high-pressure emerald/amber while the multiblock reactor is formed and actively generating power.
 */
public class NuclearGeneratorBlock extends GenerationBlock {
    public static final MapCodec<NuclearGeneratorBlock> CODEC = simpleCodec(NuclearGeneratorBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public NuclearGeneratorBlock(Properties properties) {
        super(properties, GenerationKind.NUCLEAR);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            LivingFactoryParticleHook.handler.onAnimateTick(
                    LivingFactoryParticleHook.EffectKind.NUCLEAR_STEAM, state, level, pos, random);
        }
    }
}
