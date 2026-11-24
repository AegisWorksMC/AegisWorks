package com.industrialcore.block;

import com.industrialcore.blockentity.ClimateControllerBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 95: Climate &amp; Time Controller ({@code climate_controller}) block. */
public class ClimateControllerBlock extends AbstractLifeBlock {
    public static final MapCodec<ClimateControllerBlock> CODEC = simpleCodec(ClimateControllerBlock::new);

    public ClimateControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClimateControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CLIMATE_CONTROLLER.get(),
                ClimateControllerBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!LargeMachineHelper.checkPlacementSpace(context, MachineSpec.CLIMATE_CONTROLLER, facing)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(LIT, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LargeMachineHelper.expandProxies(level, MachineSpec.CLIMATE_CONTROLLER, pos, state.getValue(FACING));
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        BlockState state = be.getBlockState();
        LargeMachineHelper.removeProxiesForMaster(level, MachineSpec.CLIMATE_CONTROLLER, pos,
                state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof ClimateControllerBlockEntity b ? b.getInventory() : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            com.industrialcore.machine.LivingFactoryParticleHook.handler.onAnimateTick(
                    com.industrialcore.machine.LivingFactoryParticleHook.EffectKind.CLIMATE_CONTROLLER_BEACON, state, level, pos, random);
        }
    }
}
