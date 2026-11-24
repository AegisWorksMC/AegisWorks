package com.industrialcore.block;

import com.industrialcore.blockentity.HyperItemConduitBlockEntity;
import com.industrialcore.endgame.HyperConduitLogic.FaceMode;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Phase 99: Unlimited Item Conduit ({@code hyper_item_conduit}) — 6-way auto-connecting
 * conduit transferring up to 64 stacks/tick with simulate-first conservation and right-click
 * per-face mode cycling (BOTH / EXTRACT / INSERT / DISABLED).
 */
public class HyperItemConduitBlock extends BaseEntityBlock {
    public static final MapCodec<HyperItemConduitBlock> CODEC = simpleCodec(HyperItemConduitBlock::new);

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

    public HyperItemConduitBlock(Properties properties) {
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
                Block.box(5.5, 0.0, 5.5, 10.5, 5.0, 10.5),
                Block.box(5.5, 11.0, 5.5, 10.5, 16.0, 10.5),
                Block.box(5.5, 5.5, 0.0, 10.5, 10.5, 5.0),
                Block.box(5.5, 5.5, 11.0, 10.5, 10.5, 16.0),
                Block.box(0.0, 5.5, 5.5, 5.0, 10.5, 10.5),
                Block.box(11.0, 5.5, 5.5, 16.0, 10.5, 10.5)
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
            HyperItemConduitBlockEntity.invalidateAllNetworks();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            HyperItemConduitBlockEntity.invalidateAllNetworks();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level lvl) {
            boolean next = canConnect(lvl, pos, direction);
            BooleanProperty prop = DIR_PROPS.get(direction);
            if (!lvl.isClientSide && state.getValue(prop) != next) {
                HyperItemConduitBlockEntity.invalidateAllNetworks();
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
                HyperItemConduitBlockEntity.invalidateAllNetworks();
            } else if (neighborBlock != this && neighborBlock != level.getBlockState(neighborPos).getBlock()) {
                HyperItemConduitBlockEntity.invalidateAllNetworks();
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HyperItemConduitBlockEntity be) {
            Direction face = hitResult.getDirection();
            FaceMode next = be.cycleFaceMode(face);
            BlockState updated = computeState(state, level, pos);
            if (!updated.equals(state)) {
                level.setBlock(pos, updated, Block.UPDATE_ALL);
            }
            level.playSound(null, pos, ModSounds.UI_CLICK.get(), SoundSource.BLOCKS, 0.55F, 1.15F);
            ChatFormatting color = switch (next) {
                case EXTRACT -> ChatFormatting.GOLD;
                case INSERT -> ChatFormatting.AQUA;
                case DISABLED -> ChatFormatting.RED;
                case BOTH -> ChatFormatting.GREEN;
            };
            player.displayClientMessage(
                    Component.translatable("message.industrialcore.conduit_face_mode",
                                    Component.translatable("gui.industrialcore.dir." + face.getName()),
                                    Component.translatable(next.translationKey()))
                            .withStyle(color),
                    true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public BlockState computeState(BlockState state, Level level, BlockPos pos) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(DIR_PROPS.get(d), canConnect(level, pos, d));
        }
        return out;
    }

    public static boolean canConnect(Level level, BlockPos pos, Direction dir) {
        if (level.getBlockEntity(pos) instanceof HyperItemConduitBlockEntity be
                && !be.getFaceMode(dir).isConnected()) {
            return false;
        }
        BlockPos neighborPos = pos.relative(dir);
        BlockState ns = level.getBlockState(neighborPos);
        if (ns.getBlock() instanceof HyperItemConduitBlock) {
            if (level.getBlockEntity(neighborPos) instanceof HyperItemConduitBlockEntity nbe
                    && !nbe.getFaceMode(dir.getOpposite()).isConnected()) {
                return false;
            }
            return true;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.getOpposite()) != null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperItemConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.HYPER_ITEM_CONDUIT.get(),
                        HyperItemConduitBlockEntity::serverTick);
    }
}
