package com.industrialcore.block;

import com.industrialcore.machine.TransporterCasingPart;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Casing block of the Advanced Transporter multiblock (Phase 62).
 *
 * <p>A casing is a normal full block: same hardness, same drops, full cube collision and outline,
 * so the hit box of a formed machine always matches what the player sees. On top of that it
 * carries the part of the machine it currently renders and the direction that part looks at. The
 * controller stamps both when the 4x4x4 shell validates and resets them to {@code plain} when the
 * shell breaks apart, which is the only thing this block state is used for.</p>
 *
 * <p>Placed by a player a casing is {@code plain}: a plain steel cube that no model of the
 * machine depends on. Nothing about the structure check reads these properties - it only tests
 * that a cell holds this block - so a re-skin can never invalidate a working machine.</p>
 */
public class TransporterCasingBlock extends Block {
    /** Index of {@link TransporterCasingPart}; see {@link TransporterCasingPart#id()}. */
    public static final IntegerProperty PART =
            IntegerProperty.create("part", 0, TransporterCasingPart.values().length - 1);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TransporterCasingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, TransporterCasingPart.PLAIN.id())
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    /** Part this state renders, decoded from the stored id. */
    public static TransporterCasingPart partOf(BlockState state) {
        return TransporterCasingPart.byId(state.getValue(PART));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // A freshly placed casing is always plain; only a formed machine assigns parts.
        return this.defaultBlockState();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
