package com.industrialcore.block;

import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.LivingFactoryParticleHook;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Block of the Phase 7 processing machines.
 *
 * <p>Both machines share this class and the shared {@link ProcessingBlockEntity} type; the kind
 * selects the recipe source and the configured values. Phase 109 adds {@link #LIT} for GPU-side
 * {@code .mcmeta} active-panel animations and lightweight client-only particles.</p>
 */
public class ProcessingBlock extends BaseEntityBlock {
    // Only used when a block has to be reconstructed from its codec; registered instances keep
    // their real kind.
    public static final MapCodec<ProcessingBlock> CODEC = simpleCodec(
            properties -> new ProcessingBlock(properties, ProcessingKind.CRUSHER));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final ProcessingKind kind;

    public ProcessingBlock(Properties properties, ProcessingKind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    public ProcessingKind kind() {
        return this.kind;
    }

    /**
     * Every processor draws an inset or oversized housing, so Phase 79 takes the collision and
     * selection shape straight from the model instead of the unit cube.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return ModelShapeRegistry.shape(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        LivingFactoryParticleHook.EffectKind effect = switch (this.kind) {
            case ELECTRIC_FURNACE -> LivingFactoryParticleHook.EffectKind.PROCESSING_HEAT_EMBER;
            case ELECTROLYZER -> LivingFactoryParticleHook.EffectKind.PROCESSING_ARCANE_SPARK;
            default -> LivingFactoryParticleHook.EffectKind.PROCESSING_METAL_SPARK;
        };
        LivingFactoryParticleHook.handler.onAnimateTick(effect, state, level, pos, random);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessingBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.PROCESSING.get(),
                        ProcessingBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider provider) {
                player.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ProcessingBlockEntity machine) {
            machine.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
