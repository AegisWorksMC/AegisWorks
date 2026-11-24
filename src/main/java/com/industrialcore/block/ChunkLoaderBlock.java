package com.industrialcore.block;

import com.industrialcore.blockentity.ChunkLoaderBlockEntity;
import com.industrialcore.life.ChunkLoaderTickets;
import com.industrialcore.registry.ModBlockEntities;
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

/** Phase 95: Chunk Loader ({@code chunk_loader}) block; releases all chunk tickets on removal. */
public class ChunkLoaderBlock extends AbstractLifeBlock {
    public static final MapCodec<ChunkLoaderBlock> CODEC = simpleCodec(ChunkLoaderBlock::new);

    public ChunkLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CHUNK_LOADER.get(), ChunkLoaderBlockEntity::serverTick);
    }

    @Override
    protected void beforeMachineRemoved(Level level, BlockPos pos, BlockEntity be) {
        if (level instanceof ServerLevel sl) {
            ChunkLoaderTickets.releaseAll(sl, pos);
        }
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof ChunkLoaderBlockEntity b ? b.getInventory() : null;
    }
}
