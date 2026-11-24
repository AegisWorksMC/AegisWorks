package com.industrialcore.client;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Phase 117 Tier 2 Hero BER: Alloy Furnace ({@code alloy_furnace}) glowing induction heating
 * coils and incandescent molten alloy crucible core inside the deep recessed hearth.
 */
public final class FurnaceCoreRenderer implements BlockEntityRenderer<AlloyFurnaceBlockEntity> {
    public FurnaceCoreRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(AlloyFurnaceBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(AbstractLifeBlock.FACING)
                ? state.getValue(AbstractLifeBlock.FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        CrusherRollerRenderer.renderInductionFurnaceCore(
                be.getBlockPos(), "alloy_furnace", facing, tSec, true, pose, buffers, packedLight);
    }
}
