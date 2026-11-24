package com.industrialcore.block;

import com.industrialcore.blockentity.WarpPadBlockEntity;
import com.industrialcore.life.WarpNetworkData;
import com.industrialcore.multiblock.LargeMachineHelper;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 95: Teleport Warp Pad ({@code warp_pad}) block with auto-registration &amp; removal cleanup. */
public class WarpPadBlock extends AbstractLifeBlock {
    public static final MapCodec<WarpPadBlock> CODEC = simpleCodec(WarpPadBlock::new);

    public WarpPadBlock(Properties properties) {
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
        return new WarpPadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.WARP_PAD.get(), WarpPadBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!LargeMachineHelper.checkPlacementSpace(context, MachineSpec.WARP_PAD, facing)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(LIT, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LargeMachineHelper.expandProxies(level, MachineSpec.WARP_PAD, pos, state.getValue(FACING));
        if (level instanceof ServerLevel sl && sl.getBlockEntity(pos) instanceof WarpPadBlockEntity be) {
            be.ensureRegistered(sl);
        }
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        BlockState state = be.getBlockState();
        LargeMachineHelper.removeProxiesForMaster(level, MachineSpec.WARP_PAD, pos,
                state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
        if (level instanceof ServerLevel sl) {
            WarpNetworkData.get(sl).unregisterPad(sl.dimension(), pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp
                && sl.getBlockEntity(pos) instanceof WarpPadBlockEntity be) {
            be.ensureRegistered(sl);
            sp.openMenu((MenuProvider) be, buf -> buf.writeBlockPos(pos));
            be.syncListToPlayer(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof WarpPadBlockEntity b ? b.getInventory() : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            com.industrialcore.machine.LivingFactoryParticleHook.handler.onAnimateTick(
                    com.industrialcore.machine.LivingFactoryParticleHook.EffectKind.WARP_PAD_PORTAL, state, level, pos, random);
        }
    }
}
