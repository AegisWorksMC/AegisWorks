package com.industrialcore.block;

import com.industrialcore.blockentity.AdvancedTransporterDriveBlockEntity;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.AdvancedTransportMachine;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Controller of the Phase 63 Advanced Transporter: the front, bottom, centre cell of the 3x2x3
 * machine.
 *
 * <p>It is a {@link TransporterBlock} like the other tiers, so the menu, the transfer logic and the
 * channel/role model are exactly the same - only the way it is built differs. Placing the item
 * builds all 18 cells ({@link com.industrialcore.item.AdvancedTransporterDriveItem}); breaking it -
 * or any part - takes all 18 down again.</p>
 *
 * <p>The controller is the only block of the machine that ticks, holds an inventory or is a valid
 * menu provider. It drops no loot of its own: the teardown drops exactly one machine item, so a
 * break can never produce two.</p>
 */
public class AdvancedTransporterDriveBlock extends TransporterBlock {

    public AdvancedTransporterDriveBlock(Properties properties) {
        super(properties, TransporterKind.ADVANCED);
    }

    /** Cell offset of the controller inside the 3x2x3 machine (front, bottom, centre). */
    public static final int MACHINE_INDEX = com.industrialcore.machine.AdvancedTransportLayout
            .indexOf(0, 0, 0);

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedTransporterDriveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.ADVANCED_TRANSPORTER_DRIVE.get(),
                        TransporterBlockEntity::serverTick);
    }

    /** The item comes from the teardown, never from the loot table. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            boolean drop = !(player instanceof ServerPlayer serverPlayer && serverPlayer.isCreative());
            // Replaces the base class' inventory drop: the teardown drops the contents and the
            // single machine item in one guarded transaction.
            AdvancedTransportMachine.remove(server, pos, pos, drop);
        }
        return state;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            // By the time onRemove runs the position already holds the new state, so the facing
            // comes from the state we are leaving. Without it a /setblock teardown could not find
            // the cells and would leave 17 orphan parts behind.
            AdvancedTransportMachine.remove(server, pos, pos, true,
                    state.getValue(AdvancedTransporterDriveBlock.FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                               BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level instanceof ServerLevel server) {
            AdvancedTransportMachine.remove(server, pos, pos, true);
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }
}
