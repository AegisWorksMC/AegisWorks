package com.industrialcore.block;

import com.industrialcore.blockentity.WirelessChargerBlockEntity;
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

/** Phase 94: Wireless Charger ({@code wireless_charger}) block. */
public class WirelessChargerBlock extends AbstractLifeBlock {
    public static final MapCodec<WirelessChargerBlock> CODEC = simpleCodec(WirelessChargerBlock::new);

    public WirelessChargerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.WIRELESS_CHARGER.get(),
                WirelessChargerBlockEntity::serverTick);
    }

    @Nullable
    @Override
    protected ItemStackHandler inventoryOf(BlockEntity be) {
        return be instanceof WirelessChargerBlockEntity b ? b.getInventory() : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
            com.industrialcore.machine.LivingFactoryParticleHook.handler.onAnimateTick(
                    com.industrialcore.machine.LivingFactoryParticleHook.EffectKind.WIRELESS_CHARGER_ARC, state, level, pos, random);
        }
    }
}
