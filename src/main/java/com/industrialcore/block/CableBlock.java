package com.industrialcore.block;

import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.TubeProfile;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * FE cable. Six connection states describe the visible arms; the energy transport itself is done
 * by {@link CableBlockEntity}. A cable connects to another cable or to any block that exposes the
 * NeoForge energy capability, so generators and machines keep working without special cases.
 */
public class CableBlock extends BaseEntityBlock {
    // The codec is only used when a block instance has to be reconstructed; the registered
    // per-tier instances keep their real tier. New placements always come from those instances.
    public static final MapCodec<CableBlock> CODEC = simpleCodec(
            properties -> new CableBlock(properties, CableTier.BASIC));

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public static final Map<Direction, BooleanProperty> CONNECTION_BY_DIRECTION = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN);

    private final VoxelShape[] shapes = new VoxelShape[64];
    private final CableTier tier;

    public CableBlock(Properties properties, CableTier tier) {
        super(properties);
        this.tier = tier;
        BlockState base = this.stateDefinition.any();
        for (BooleanProperty property : CONNECTION_BY_DIRECTION.values()) {
            base = base.setValue(property, false);
        }
        this.registerDefaultState(base);
        double tube = TubeProfile.cableTube(tier);
        VoxelShape core = TubeShapes.core(tube);
        VoxelShape[] arms = new VoxelShape[6];
        for (Direction direction : Direction.values()) {
            arms[direction.get3DDataValue()] = TubeShapes.arm(direction, tube);
        }
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = core;
            for (Direction direction : Direction.values()) {
                int index = direction.get3DDataValue();
                if ((mask & (1 << index)) != 0) {
                    shape = Shapes.or(shape, arms[index]);
                }
            }
            shapes[mask] = shape;
        }
    }

    public CableTier tier() {
        return tier;
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
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTION_BY_DIRECTION.get(direction))) {
                mask |= 1 << direction.get3DDataValue();
            }
        }
        return shapes[mask];
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return recalculate(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty property = CONNECTION_BY_DIRECTION.get(direction);
        if (property == null) {
            return state;
        }
        boolean connects = connectsTo(level, pos, direction);
        if (connects != state.getValue(property) && level instanceof Level realLevel && !realLevel.isClientSide
                && realLevel.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            // A neighbour appeared or disappeared: the cached consumer set and topology are stale.
            cable.invalidateNetwork();
        }
        return state.setValue(property, connects);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            // The client only needs the cached port anchors dropped; the shape comes from updateShape.
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) {
                cable.invalidatePortArms();
            }
            return;
        }
        BlockState updated = recalculate(state, level, pos);
        boolean shapeChanged = updated != state;
        if (shapeChanged) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        CableBlockEntity self = level.getBlockEntity(pos) instanceof CableBlockEntity cable ? cable : null;
        // Machines flip their "active" state often; only an actual connection change or a
        // replaced neighbour block (new capability instance) invalidates the cached network.
        Direction changedDirection = Direction.fromDelta(
                neighborPos.getX() - pos.getX(),
                neighborPos.getY() - pos.getY(),
                neighborPos.getZ() - pos.getZ());
        boolean neighbourReplaced = self != null && changedDirection != null
                && self.isNeighbourReplaced(changedDirection, neighborBlock);
        if (!shapeChanged && !neighbourReplaced) {
            return;
        }
        if (self != null) {
            self.invalidateNetwork();
        }
        // The neighbour's connection state and the network topology both changed.
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof CableBlockEntity cable) {
                cable.invalidateNetwork();
            }
        }
    }

    public static BlockState recalculate(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            result = result.setValue(CONNECTION_BY_DIRECTION.get(direction),
                    connectsTo(level, pos, direction));
        }
        return result;
    }

    private static boolean connectsTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState neighborState = level.getBlockState(pos.relative(direction));
        if (neighborState.getBlock() instanceof CableBlock) {
            return true;
        }
        if (level instanceof Level realLevel) {
            IEnergyStorage storage = realLevel.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
            return storage != null;
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CABLE.get(), CableBlockEntity::serverTick);
    }
}
