package com.industrialcore.block;

import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.LivingFactoryParticleHook;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Block of the six conventional power devices (Phase 33). The block instance carries the
 * {@link GenerationKind}, so all six share one block entity type, one menu and one screen -
 * the same pattern as the processing machines, where the block selects the behaviour.
 *
 * <p>Condition-based devices (water, wind openness, daylight) invalidate their cached
 * condition as soon as a neighbour changes; the periodic re-check covers everything else.</p>
 */
public class GenerationBlock extends BaseEntityBlock {
    public static final MapCodec<GenerationBlock> CODEC = simpleCodec(
            properties -> new GenerationBlock(properties, GenerationKind.THERMAL));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final GenerationKind kind;

    public GenerationBlock(Properties properties, GenerationKind kind) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Power device kind of this block instance. */
    public GenerationKind kind() {
        return this.kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        LivingFactoryParticleHook.EffectKind effect = switch (this.kind) {
            case THERMAL, COMBUSTION -> LivingFactoryParticleHook.EffectKind.GENERATOR_SMOKE;
            case GEOTHERMAL -> LivingFactoryParticleHook.EffectKind.GENERATOR_HEAT_SPARK;
            case HYDRO -> LivingFactoryParticleHook.EffectKind.GENERATOR_WATER_MIST;
            default -> null;
        };
        if (effect != null) {
            LivingFactoryParticleHook.handler.onAnimateTick(effect, state, level, pos, random);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Shape of one power device kind, or {@code null} when the device keeps the plain 1x1x1 cube.
     *
     * <p>Only the combustion generator has a model that leaves the block cell (Phase 71). The other
     * kinds share this block class, so the decision is kept in one pure method: the override below
     * falls back to {@code super} for every kind that returns {@code null}, and the unit test can
     * pin that without bootstrapping a world.</p>
     */
    public static VoxelShape shapeFor(GenerationKind kind, Direction facing) {
        if (kind == GenerationKind.COMBUSTION) {
            return MachineShapes.combustion(facing);
        }
        if (kind == GenerationKind.NUCLEAR) {
            return MachineShapes.nuclear(facing);
        }
        if (kind == GenerationKind.WIND) {
            return MachineShapes.wind(facing);
        }
        if (kind == GenerationKind.SOLAR) {
            return MachineShapes.solar(facing);
        }
        if (kind == GenerationKind.GEOTHERMAL) {
            return MachineShapes.geothermal(facing);
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, net.minecraft.core.BlockPos pos,
                                  CollisionContext context) {
        return ModelShapeRegistry.shape(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GenerationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.GENERATION.get(), GenerationBlockEntity::serverTick);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof GenerationBlockEntity device) {
            // Water can flow in, a roof block can change: re-validate on the next tick instead
            // of waiting for the periodic re-check.
            device.invalidateConditions();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (this.kind == GenerationKind.NUCLEAR && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.industrialcore.reactor.ReactorGuideHook.clientToggle.toggle(
                        pos, state.getValue(FACING),
                        com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType.NUCLEAR);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider provider) {
                player.openMenu(provider, buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(this.kind.ordinal());
                    buffer.writeByte(-1);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && this.kind == GenerationKind.NUCLEAR
                && level instanceof net.minecraft.server.level.ServerLevel sl
                && level.getBlockEntity(pos) instanceof GenerationBlockEntity device) {
            device.onMultiblockBroken(sl);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GenerationBlockEntity device) {
            device.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}