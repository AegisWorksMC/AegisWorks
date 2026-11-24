package com.industrialcore.block;

import com.industrialcore.blockentity.MobDuplicatorBlockEntity;
import com.industrialcore.multiblock.LargeMachineHelper;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 93: Mob Automatic Spawner block ({@code mob_duplicator}). */
public class MobDuplicatorBlock extends AbstractTrapWave2Block {
    public static final MapCodec<MobDuplicatorBlock> CODEC = simpleCodec(MobDuplicatorBlock::new);

    public MobDuplicatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobDuplicatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.MOB_DUPLICATOR.get(),
                MobDuplicatorBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!LargeMachineHelper.checkPlacementSpace(context, MachineSpec.MOB_DUPLICATOR, facing)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(LIT, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LargeMachineHelper.expandProxies(level, MachineSpec.MOB_DUPLICATOR, pos, state.getValue(FACING));
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        BlockState state = be.getBlockState();
        LargeMachineHelper.removeProxiesForMaster(level, MachineSpec.MOB_DUPLICATOR, pos,
                state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof MobDuplicatorBlockEntity dup ? dup.getInventory() : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            com.industrialcore.machine.LivingFactoryParticleHook.handler.onAnimateTick(
                    com.industrialcore.machine.LivingFactoryParticleHook.EffectKind.MOB_DUPLICATOR_BIO, state, level, pos, random);
        }
    }
}
