package com.industrialcore.client;

import com.industrialcore.block.ChunkLoaderBlock;
import com.industrialcore.blockentity.ChunkLoaderBlockEntity;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Phase 110 Tier 2 Hero BER: Chunk Loader ({@code chunk_loader}).
 *
 * <p>When active ({@code lit=true}), floats a slowly rotating cyan spatial-anchor crystal &amp;
 * tilted containment ring inside the hollow open cage of the chunk loader.</p>
 */
public final class ChunkLoaderRenderer implements BlockEntityRenderer<ChunkLoaderBlockEntity> {
    public ChunkLoaderRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(ChunkLoaderBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ChunkLoaderBlock.LIT) || !state.getValue(ChunkLoaderBlock.LIT)) {
            return;
        }
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "chunk_loader",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.80F), 0.25F);
        float deg = LivingFactoryAnimLogic.chunkLoaderCoreDeg(tSec);
        float bob = LivingFactoryAnimLogic.chunkLoaderBobY(tSec);

        // Pass 1: Floating glowing cyan spatial-anchor diamond crystal inside hollow cage (y=0.56)
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.58D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(35.264F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, cm, -0.145F, -0.145F, -0.145F, 0.145F, 0.145F, 0.145F,
                0.32F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Tilted violet-cyan orbital containment ring hovering around the anchor crystal
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.5D, 0.58D + bob * 0.6F, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.35F));
        pose.mulPose(Axis.XP.rotationDegrees(26.0F));
        HeroRenderPrimitives.energyRingXZ(pose, purple, 0.28F, 0.022F, 0.022F,
                0.76F, 0.54F, 1.00F, 0.85F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
