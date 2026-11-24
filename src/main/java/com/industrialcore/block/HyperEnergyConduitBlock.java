package com.industrialcore.block;

import com.industrialcore.blockentity.HyperEnergyConduitBlockEntity;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Phase 99: Unlimited Energy Conduit ({@code hyper_energy_conduit}) — 6-way auto-connecting
 * endgame power conduit transferring up to {@code Integer.MAX_VALUE} FE/tick across its network.
 */
public class HyperEnergyConduitBlock extends BaseEntityBlock {
    public static final MapCodec<HyperEnergyConduitBlock> CODEC = simpleCodec(HyperEnergyConduitBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public static final Map<Direction, BooleanProperty> DIR_PROPS = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    );

    private static final VoxelShape[] SHAPES = buildShapes();

    public HyperEnergyConduitBlock(Properties properties) {
        super(properties);
        BlockState def = this.stateDefinition.any();
        for (BooleanProperty p : DIR_PROPS.values()) {
            def = def.setValue(p, false);
        }
        this.registerDefaultState(def);
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape core = Block.box(4.5, 4.5, 4.5, 11.5, 11.5, 11.5);
        VoxelShape[] arms = {
                Block.box(5.5, 0.0, 5.5, 10.5, 5.0, 10.5),  // DOWN (0)
                Block.box(5.5, 11.0, 5.5, 10.5, 16.0, 10.5), // UP (1)
                Block.box(5.5, 5.5, 0.0, 10.5, 10.5, 5.0),  // NORTH (2)
                Block.box(5.5, 5.5, 11.0, 10.5, 10.5, 16.0), // SOUTH (3)
                Block.box(0.0, 5.5, 5.5, 5.0, 10.5, 10.5),  // WEST (4)
                Block.box(11.0, 5.5, 5.5, 16.0, 10.5, 10.5)  // EAST (5)
        };
        VoxelShape[] table = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape s = core;
            for (Direction d : Direction.values()) {
                int idx = d.get3DDataValue();
                if ((mask & (1 << idx)) != 0) {
                    s = Shapes.or(s, arms[idx]);
                }
            }
            table[mask] = s;
        }
        return table;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(DIR_PROPS.get(d))) {
                mask |= 1 << d.get3DDataValue();
            }
        }
        return SHAPES[mask];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return computeState(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            HyperEnergyConduitBlockEntity.invalidateAllNetworks();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            HyperEnergyConduitBlockEntity.invalidateAllNetworks();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level lvl) {
            boolean next = canConnect(lvl, neighborPos, direction.getOpposite());
            BooleanProperty prop = DIR_PROPS.get(direction);
            if (!lvl.isClientSide && state.getValue(prop) != next) {
                HyperEnergyConduitBlockEntity.invalidateAllNetworks();
            }
            return state.setValue(prop, next);
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            BlockState updated = computeState(state, level, pos);
            if (!updated.equals(state)) {
                level.setBlock(pos, updated, Block.UPDATE_ALL);
                HyperEnergyConduitBlockEntity.invalidateAllNetworks();
            } else if (neighborBlock != this && neighborBlock != level.getBlockState(neighborPos).getBlock()) {
                HyperEnergyConduitBlockEntity.invalidateAllNetworks();
            }
        }
    }

    private BlockState computeState(BlockState state, Level level, BlockPos pos) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(DIR_PROPS.get(d), canConnect(level, pos.relative(d), d.getOpposite()));
        }
        return out;
    }

    public static boolean canConnect(Level level, BlockPos neighborPos, Direction sideOnNeighbor) {
        BlockState ns = level.getBlockState(neighborPos);
        if (ns.getBlock() instanceof HyperEnergyConduitBlock) {
            return true;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, sideOnNeighbor) != null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperEnergyConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.HYPER_ENERGY_CONDUIT.get(),
                        HyperEnergyConduitBlockEntity::serverTick);
    }
}
