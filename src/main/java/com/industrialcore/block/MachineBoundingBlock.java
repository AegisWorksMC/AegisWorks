package com.industrialcore.block;

import com.industrialcore.blockentity.MachineBoundingBlockEntity;
import com.industrialcore.multiblock.LargeMachineHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 104: Common invisible proxy block ({@code machine_bounding}) shared by all auto-expanding
 * large machines.
 *
 * <ul>
 *   <li>Renders invisibly ({@link RenderShape#INVISIBLE}) while providing physical collision
 *       (full block or walkable 5px pad when {@link #LOW_PAD} is true).</li>
 *   <li>Forwards right-click ({@code useItemOn} / {@code useWithoutItem}) directly to the master.</li>
 *   <li>Delegates Energy, Item, and Fluid capabilities to the master via
 *       {@link MachineBoundingBlockEntity}.</li>
 *   <li>Atomically removes all siblings + master on destruction, dropping strictly 1 master item.</li>
 * </ul>
 */
public class MachineBoundingBlock extends BaseEntityBlock {
    public static final MapCodec<MachineBoundingBlock> CODEC = simpleCodec(MachineBoundingBlock::new);
    public static final BooleanProperty LOW_PAD = BooleanProperty.create("low_pad");

    private static final VoxelShape PAD_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 5.0D, 16.0D);

    public MachineBoundingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LOW_PAD, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOW_PAD);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LOW_PAD) ? PAD_SHAPE : Shapes.block();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBoundingBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxy) {
            BlockPos masterPos = proxy.getMasterPos();
            if (masterPos != null) {
                BlockState masterState = level.getBlockState(masterPos);
                if (!masterState.isAir() && !masterState.is(this)) {
                    return masterState.getCloneItemStack(target, level, masterPos, player);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxy) {
            BlockPos masterPos = proxy.getMasterPos();
            if (masterPos != null && !masterPos.equals(pos)) {
                BlockState masterState = level.getBlockState(masterPos);
                if (!masterState.isAir() && !masterState.is(this)) {
                    BlockHitResult forwarded = new BlockHitResult(
                            hitResult.getLocation(), hitResult.getDirection(), masterPos, hitResult.isInside());
                    return masterState.useItemOn(stack, level, player, hand, forwarded);
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxy) {
            BlockPos masterPos = proxy.getMasterPos();
            if (masterPos != null && !masterPos.equals(pos)) {
                BlockState masterState = level.getBlockState(masterPos);
                if (!masterState.isAir() && !masterState.is(this)) {
                    BlockHitResult forwarded = new BlockHitResult(
                            hitResult.getLocation(), hitResult.getDirection(), masterPos, hitResult.isInside());
                    return masterState.useWithoutItem(level, player, forwarded);
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxy) {
            BlockPos masterPos = proxy.getMasterPos();
            if (masterPos != null && !masterPos.equals(pos)) {
                LargeMachineHelper.destroyFromProxy(sl, pos, masterPos, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && !LargeMachineHelper.isTearingDown()
                && level.getBlockEntity(pos) instanceof MachineBoundingBlockEntity proxy) {
            BlockPos masterPos = proxy.getMasterPos();
            if (masterPos != null && !masterPos.equals(pos)) {
                LargeMachineHelper.destroyFromProxy(sl, pos, masterPos, null);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
