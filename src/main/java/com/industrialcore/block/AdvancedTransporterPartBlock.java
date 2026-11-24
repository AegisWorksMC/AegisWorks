package com.industrialcore.block;

import com.industrialcore.machine.AdvancedTransportLayout;
import com.industrialcore.machine.AdvancedTransportMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * One of the 17 non-controller cells of the Phase 63 Advanced Transporter.
 *
 * <p>A part is deliberately inert: no block entity, no ticker, no menu, no inventory, no FE and no
 * independent machine logic. It is a plain full block - full cube collision and outline, so the
 * hit box of the finished machine is exactly the 18 cells the player sees - that only remembers
 * which cell of the machine it is ({@link #PART}) and which way the machine faces.</p>
 *
 * <p>Breaking a part tears the whole machine down through {@link AdvancedTransportMachine}, and a
 * part never drops anything on its own: the single machine item is dropped once by the teardown.
 * The same holds for explosions and for any other route that removes the block, which is why the
 * teardown trigger sits in {@link #onRemove} as well as in {@link #playerWillDestroy}.</p>
 */
public class AdvancedTransporterPartBlock extends Block {
    /** Cell index inside the machine (see {@link AdvancedTransportLayout}). */
    public static final IntegerProperty PART =
            IntegerProperty.create("part", 0, AdvancedTransportLayout.CELL_COUNT - 1);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AdvancedTransporterPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, AdvancedTransportLayout.indexOf(-1, 0, 0))
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** A part is not an item: the machine drops as one item from the teardown. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            BlockPos owner = AdvancedTransportMachine.ownerOf(state, pos);
            boolean drop = !(player instanceof ServerPlayer serverPlayer && serverPlayer.isCreative());
            if (owner != null) {
                AdvancedTransportMachine.remove(server, owner, pos, drop);
            }
        }
        return state;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        // Any route that removes a part without a player (commands, pistons, other mods) still
        // has to take the machine down; the teardown guard makes this a no-op while it runs.
        if (level instanceof ServerLevel server && !newState.is(this)) {
            BlockPos owner = AdvancedTransportMachine.ownerOf(state, pos);
            if (owner != null) {
                AdvancedTransportMachine.remove(server, owner, pos, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                               BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level instanceof ServerLevel server) {
            BlockPos owner = AdvancedTransportMachine.ownerOf(state, pos);
            if (owner != null) {
                // The machine item is the only drop; the explosion must not scatter the parts.
                AdvancedTransportMachine.remove(server, owner, pos, true);
            }
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }
}
