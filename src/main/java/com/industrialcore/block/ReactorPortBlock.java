package com.industrialcore.block;

import com.industrialcore.blockentity.ReactorPortBlockEntity;
import com.industrialcore.reactor.ReactorMultiblockManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 103: Multiblock reactor I/O port ({@code reactor_port}).
 *
 * <p>When part of a formed reactor, proxies Energy &amp; Item capabilities to the controller and
 * opens the controller's GUI when right-clicked.</p>
 */
public class ReactorPortBlock extends BaseEntityBlock {
    public static final MapCodec<ReactorPortBlock> CODEC = simpleCodec(ReactorPortBlock::new);

    public ReactorPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorPortBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) {
            BlockPos ctrlPos = port.getControllerPos();
            BlockEntity ctrl = port.getLinkedController();
            if (ctrlPos != null && ctrl instanceof MenuProvider provider) {
                if (ctrl instanceof com.industrialcore.blockentity.GenerationBlockEntity) {
                    player.openMenu(provider, buf -> {
                        buf.writeBlockPos(ctrlPos);
                        buf.writeByte(com.industrialcore.machine.GenerationKind.NUCLEAR.ordinal());
                        buf.writeByte(-1);
                    });
                } else {
                    player.openMenu(provider, buf -> buf.writeBlockPos(ctrlPos));
                }
            } else {
                player.displayClientMessage(
                        Component.translatable("message.industrialcore.reactor.port_unlinked")
                                .withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            ReactorMultiblockManager.notifyStructureBlockBroken(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
