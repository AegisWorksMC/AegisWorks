package com.industrialcore.client;

import com.industrialcore.block.AutoFisherBlock;
import com.industrialcore.blockentity.AutoFisherBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Phase 110 Tier 2 Hero BER: Auto Fisher ({@code auto_fisher}).
 *
 * <p>When active ({@code lit=true}), smoothly rotates the horizontal metallic winch & glowing
 * cyan fishing-line reel mounted between the two upper stanchions.</p>
 */
public final class AutoFisherRenderer implements BlockEntityRenderer<AutoFisherBlockEntity> {
    public AutoFisherRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(AutoFisherBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(AutoFisherBlock.LIT) || !state.getValue(AutoFisherBlock.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(AutoFisherBlock.FACING)
                ? state.getValue(AutoFisherBlock.FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "auto_fisher",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.80F), 0.20F);
        float deg = LivingFactoryAnimLogic.autoFisherReelDeg(tSec);

        // Pass 1: Rotating horizontal steel winch drum & crank spokes between pillars (x=4..12, y=13.75, z=8)
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.86D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(deg));
        Matrix4f m = pose.last().pose();
        // Left & right reel flanges + cross-bars
        HeroRenderPrimitives.box(steel, m, -0.23F, -0.13F, -0.035F, -0.18F, 0.13F, 0.035F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, -0.23F, -0.035F, -0.13F, -0.18F, 0.035F, 0.13F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.18F, -0.13F, -0.035F, 0.23F, 0.13F, 0.035F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.18F, -0.035F, -0.13F, 0.23F, 0.035F, 0.13F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        // Rotating winch bar spanning the reel
        HeroRenderPrimitives.box(steel, m, -0.20F, 0.095F, -0.025F, 0.20F, 0.135F, 0.025F,
                0.92F, 0.95F, 1.00F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, -0.20F, -0.135F, -0.025F, 0.20F, -0.095F, 0.025F,
                0.92F, 0.95F, 1.00F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Glowing cyan wound fishing line coil rotating around the drum
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.86D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(deg + 45.0F));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, cm, -0.16F, -0.115F, -0.022F, 0.16F, 0.115F, 0.022F,
                0.30F, 0.95F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, cm, -0.16F, -0.022F, -0.115F, 0.16F, 0.022F, 0.115F,
                0.30F, 0.95F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
