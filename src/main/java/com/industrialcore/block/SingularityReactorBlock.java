package com.industrialcore.block;

import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.machine.LivingFactoryParticleHook;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 99: Singularity Reactor Generator ({@code singularity_reactor}) block.
 */
public class SingularityReactorBlock extends AbstractLifeBlock {
    public static final MapCodec<SingularityReactorBlock> CODEC = simpleCodec(SingularityReactorBlock::new);

    public SingularityReactorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularityReactorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SINGULARITY_REACTOR.get(),
                        SingularityReactorBlockEntity::serverTick);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof SingularityReactorBlockEntity sr ? sr.getInventory() : null;
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl
                && be instanceof SingularityReactorBlockEntity sr) {
            sr.onMultiblockBroken(sl);
        }
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.industrialcore.reactor.ReactorGuideHook.clientToggle.toggle(
                        pos, state.getValue(FACING),
                        com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType.SINGULARITY);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            LivingFactoryParticleHook.handler.onAnimateTick(
                    LivingFactoryParticleHook.EffectKind.SINGULARITY_SPARK, state, level, pos, random);
        }
    }
}
