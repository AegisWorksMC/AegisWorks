package com.industrialcore.block;

import com.industrialcore.blockentity.SpawnSuppressorBlockEntity;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.trap.SpawnSuppressorRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 93: Spawn Suppressor Beacon block ({@code spawn_suppressor}). */
public class SpawnSuppressorBlock extends AbstractTrapWave2Block {
    public static final MapCodec<SpawnSuppressorBlock> CODEC = simpleCodec(SpawnSuppressorBlock::new);

    public SpawnSuppressorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnSuppressorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SPAWN_SUPPRESSOR.get(),
                SpawnSuppressorBlockEntity::serverTick);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof SpawnSuppressorBlockEntity sup ? sup.getInventory() : null;
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        if (level instanceof ServerLevel sl) {
            SpawnSuppressorRegistry.remove(sl, pos);
        }
    }
}
