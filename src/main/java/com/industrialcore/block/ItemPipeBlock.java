package com.industrialcore.block;

import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.TubeProfile;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Item pipe. The six boolean states describe the visual/functional connections; the transport
 * itself is handled by {@link ItemPipeBlockEntity}, which always looks at all six neighbours.
 *
 * <p>A pipe connects to another pipe, to a conveyor, or to any block that exposes the NeoForge
 * item handler capability (chests, machines, hoppers, ...).</p>
 */
public class ItemPipeBlock extends BaseEntityBlock {
    // Registered instances carry the real tier; the codec is only a reconstruction fallback.
    public static final MapCodec<ItemPipeBlock> CODEC = simpleCodec(
            properties -> new ItemPipeBlock(properties, PipeTier.BASIC));

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

    //
    // The per-face transport mode deliberately stays out of the block state. Phase 22 first
    // mirrored it into six four-valued properties, which multiplied the state space of every pipe
    // by 4^6 = 4,096 (1,048,576 states for the four tiers) because the state list is the product
    // of all properties. The client bakes a model for every state while starting, so that version
    // could stall or exhaust the heap before the main menu. The face markers are drawn by the
    // block entity renderer from the synced block entity data instead; the state itself only
    // carries the six connection booleans (64 states per pipe).

    private final VoxelShape[] shapes = new VoxelShape[64];
    private final PipeTier tier;

    public ItemPipeBlock(Properties properties, PipeTier tier) {
        super(properties);
        this.tier = tier;
        BlockState base = this.stateDefinition.any();
        for (BooleanProperty property : CONNECTION_BY_DIRECTION.values()) {
            base = base.setValue(property, false);
        }
        this.registerDefaultState(base);
        double tube = TubeProfile.pipeTube(tier);
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

    public PipeTier tier() {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return recalculate(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty property = CONNECTION_BY_DIRECTION.get(direction);
        if (property == null) {
            return state;
        }
        return switch (linkTo(level, pos, direction, neighborState)) {
            case LINKED -> state.setValue(property, true);
            case UNLINKED -> state.setValue(property, false);
            // The neighbour may live in a chunk that is not loaded right now. Its block reads as
            // air, which is not the same thing as a removed block, so the last known value is kept
            // and the face is re-checked once that chunk is loaded (see ItemPipeBlockEntity).
            case UNLOADED -> {
                ItemPipeBlockEntity.markUnresolved(level, pos, direction);
                yield state;
            }
        };
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        BlockState updated = recalculate(state, level, pos);
        if (updated != state) {
            // Shape/connection only: notifying neighbours again is unnecessary.
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) {
            pipe.invalidateNeighbourCache();
        }
    }

    public static BlockState recalculate(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            BooleanProperty property = CONNECTION_BY_DIRECTION.get(direction);
            switch (linkTo(level, pos, direction, neighborState)) {
                case LINKED -> result = result.setValue(property, true);
                case UNLINKED -> result = result.setValue(property, false);
                case UNLOADED -> ItemPipeBlockEntity.markUnresolved(level, pos, direction);
            }
        }
        return result;
    }

    /**
     * The three answers a neighbour can give. {@link #UNLOADED} exists so that a chunk that is
     * merely not loaded is never mistaken for a removed block and never silently drops a
     * connection: both the visual state and the saved state keep the last real answer.
     */
    public enum Link {
        LINKED,
        UNLINKED,
        UNLOADED
    }

    /**
     * Classifies one neighbour without loading anything. {@code LevelReader#hasChunk} performs no
     * chunk load, and {@code getCapability} is only consulted for a position whose chunk is
     * loaded, so calling this on a chunk edge cannot pull a chunk in.
     */
    public static Link linkTo(LevelAccessor level, BlockPos pos, Direction direction, BlockState neighborState) {
        Block neighbor = neighborState.getBlock();
        if (neighbor instanceof ItemPipeBlock || neighbor instanceof ConveyorBlock) {
            return Link.LINKED;
        }
        BlockPos neighborPos = pos.relative(direction);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(neighborPos.getX()),
                SectionPos.blockToSectionCoord(neighborPos.getZ()))) {
            return Link.UNLOADED;
        }
        if (level instanceof Level realLevel) {
            IItemHandler handler = realLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());
            return handler != null ? Link.LINKED : Link.UNLINKED;
        }
        return Link.UNLINKED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.ITEM_PIPE.get(), ItemPipeBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) {
            // An item inside the pipe must never be voided by breaking the pipe.
            pipe.dropContents();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
