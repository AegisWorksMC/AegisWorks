package com.industrialcore.block;

import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.machine.LivingFactoryParticleHook;
import com.industrialcore.machine.RecoveryKind;
import com.industrialcore.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Block of the four energy recovery devices (Phase 33): flywheel, pressure recovery, waste
 * heat recovery and regenerative generator. One block entity type serves all four; the block
 * instance carries the {@link RecoveryKind}.
 *
 * <p>The block has no facing: none of these devices has a front, so the blockstate is a single
 * model and placement never has to compute an orientation.</p>
 */
public class RecoveryBlock extends BaseEntityBlock {
    public static final MapCodec<RecoveryBlock> CODEC = simpleCodec(
            properties -> new RecoveryBlock(properties, RecoveryKind.FLYWHEEL));

    private final RecoveryKind kind;

    public RecoveryBlock(Properties properties, RecoveryKind kind) {
        super(properties);
        this.kind = kind;
    }

    /** Recovery device kind of this block instance. */
    public RecoveryKind kind() {
        return this.kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Returns the custom collision/selection shape for this recovery kind, or {@code null} for the default unit cube. */
    @Nullable
    public static VoxelShape shapeFor(RecoveryKind kind) {
        return switch (kind) {
            case PRESSURE -> MachineShapes.pressureRecovery();
            case HEAT -> MachineShapes.heatRecovery();
            case REGEN -> MachineShapes.regenGenerator();
            case FLYWHEEL -> null;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModelShapeRegistry.shape(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecoveryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.RECOVERY.get(), RecoveryBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider provider) {
                player.openMenu(provider, buffer -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeByte(-1);
                    buffer.writeByte(this.kind.ordinal());
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        LivingFactoryParticleHook.EffectKind effect = switch (this.kind) {
            case HEAT -> LivingFactoryParticleHook.EffectKind.GENERATOR_HEAT_SPARK;
            case PRESSURE -> LivingFactoryParticleHook.EffectKind.GENERATOR_WATER_MIST;
            case REGEN -> LivingFactoryParticleHook.EffectKind.AUDIO_REGEN_HUM;
            default -> null;
        };
        if (effect != null) {
            LivingFactoryParticleHook.handler.onAnimateTick(effect, state, level, pos, random);
        }
    }
}