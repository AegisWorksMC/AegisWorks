package com.industrialcore.block;

import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Controller block of the Phase 26-29 transporters.
 *
 * <p>All four controllers share this class; the {@link TransporterKind} selects the block entity
 * type, the buffer size, the shell geometry and the recipe. The block state carries only the
 * horizontal facing, which serves the model and anchors the shell: the front face of the multiblock
 * is the face the controller looks out of.</p>
 *
 * <p>A change right next to the controller revalidates the shell immediately; changes further
 * inside the shell are picked up by the block entity's own 20-tick validation rhythm, so no code
 * ever walks all 125 cells every tick.</p>
 */
public class TransporterBlock extends BaseEntityBlock {
    // Only used when a block has to be reconstructed from its codec; registered instances keep
    // their real kind.
    public static final MapCodec<TransporterBlock> CODEC = simpleCodec(
            properties -> new TransporterBlock(properties, TransporterKind.BASIC));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final TransporterKind kind;

    public TransporterBlock(Properties properties, TransporterKind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public TransporterKind kind() {
        return this.kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Phase 79: collision, selection and outline are measured from the shipped model. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return ModelShapeRegistry.shape(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransporterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.transporterType(this.kind),
                        TransporterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider provider) {
                player.openMenu(provider, buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(this.kind.ordinal());
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        // A block right at the controller changed: re-check the shell now instead of waiting for
        // the 20-tick rhythm. Cells deeper inside are covered by that rhythm.
        if (level.getBlockEntity(pos) instanceof TransporterBlockEntity transporter) {
            transporter.revalidateNow();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof TransporterBlockEntity transporter) {
            transporter.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
