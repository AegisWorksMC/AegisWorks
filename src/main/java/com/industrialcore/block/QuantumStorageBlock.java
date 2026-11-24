package com.industrialcore.block;

import com.industrialcore.blockentity.QuantumStorageBlockEntity;
import com.industrialcore.item.PortableQuantumTerminalItem;
import com.industrialcore.multiblock.LargeMachineHelper;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Phase 89 / 96: Quantum Storage Core block.
 *
 * <p>Preserves all stored local items, fluids, and installed capacity cores inside the dropped
 * ItemStack when broken, unregisters from any frequency network cleanly on removal without deleting
 * shared stock, supports direct fluid-container right-click interactions, and allows Shift+click
 * frequency linking with the {@link PortableQuantumTerminalItem}.</p>
 */
public class QuantumStorageBlock extends BaseEntityBlock {
    public static final MapCodec<QuantumStorageBlock> CODEC = simpleCodec(QuantumStorageBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public QuantumStorageBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!LargeMachineHelper.checkPlacementSpace(context, MachineSpec.QUANTUM_STORAGE_CORE, facing)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumStorageBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LargeMachineHelper.expandProxies(level, MachineSpec.QUANTUM_STORAGE_CORE, pos, state.getValue(FACING));
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof QuantumStorageBlockEntity storage) {
            storage.loadContentsFromItem(stack, level.registryAccess());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            LargeMachineHelper.removeProxiesForMaster(level, MachineSpec.QUANTUM_STORAGE_CORE, pos,
                    state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
            if (level instanceof ServerLevel sl
                    && level.getBlockEntity(pos) instanceof QuantumStorageBlockEntity storage) {
                storage.onBlockRemoved(sl);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof QuantumStorageBlockEntity storage && storage.hasAnySavedContent()) {
            for (ItemStack drop : drops) {
                if (drop.is(this.asItem())) {
                    storage.saveContentsToItem(drop, params.getLevel().registryAccess());
                    break;
                }
            }
        }
        return drops;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        // Phase 96: Shift+right-click with Portable Quantum Terminal links its frequency immediately
        if (player.isShiftKeyDown() && stack.getItem() instanceof PortableQuantumTerminalItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof QuantumStorageBlockEntity storage) {
                String chId = storage.getChannelId();
                if (chId.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("gui.industrialcore.quantum_net.link_local_err")
                                    .withStyle(ChatFormatting.YELLOW), true);
                } else {
                    String chName = storage.getActiveChannelName();
                    PortableQuantumTerminalItem.setChannel(stack, chId, chName);
                    player.displayClientMessage(
                            Component.translatable("gui.industrialcore.quantum_net.linked", chName)
                                    .withStyle(ChatFormatting.AQUA), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!stack.isEmpty()
                && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
                player.openMenu(provider, buffer -> {
                    buffer.writeBoolean(false);
                    buffer.writeBlockPos(pos);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
