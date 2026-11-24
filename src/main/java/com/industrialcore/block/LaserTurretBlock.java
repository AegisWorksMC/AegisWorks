package com.industrialcore.block;

import com.industrialcore.blockentity.LaserTurretBlockEntity;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Phase 93: Defense Laser Turret block ({@code laser_turret}). */
public class LaserTurretBlock extends AbstractTrapWave2Block {
    public static final MapCodec<LaserTurretBlock> CODEC = simpleCodec(LaserTurretBlock::new);

    public LaserTurretBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserTurretBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.LASER_TURRET.get(),
                LaserTurretBlockEntity::serverTick);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof LaserTurretBlockEntity tur ? tur.getInventory() : null;
    }
}
