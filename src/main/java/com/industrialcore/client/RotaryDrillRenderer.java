package com.industrialcore.client;

import com.industrialcore.block.RotaryDrillBlock;
import com.industrialcore.blockentity.RotaryDrillBlockEntity;
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
 * Phase 110 Tier 2 Hero BER: 6-Direction Rotary Drill ({@code rotary_drill}).
 *
 * <p>When active ({@code lit=true}), spins a metallic 4-flute drill bit & glowing cyan laser
 * cutting rings rapidly around the front protruding cone along any of the 6 facing directions.</p>
 */
public final class RotaryDrillRenderer implements BlockEntityRenderer<RotaryDrillBlockEntity> {
    public RotaryDrillRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(RotaryDrillBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(RotaryDrillBlock.LIT) || !state.getValue(RotaryDrillBlock.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(RotaryDrillBlock.FACING)
                ? state.getValue(RotaryDrillBlock.FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "rotary_drill",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.20F), 0.20F);
        float deg = LivingFactoryAnimLogic.rotaryDrillHeadDeg(tSec);

        // Pass 1: 4 stepped metallic drill flutes wrapping the front cone (local -Z = 0.22..0.55 from center)
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        HeroRenderPrimitives.alignLocalNorthToFacing(pose, facing);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));

        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 90.0F));
            Matrix4f m = pose.last().pose();
            // Outer flute step (near base of cone)
            HeroRenderPrimitives.box(steel, m,
                    0.22F, -0.045F, -0.34F,
                    0.41F, 0.045F, -0.24F,
                    0.86F, 0.90F, 0.96F, 1.0F, packedLight);
            // Mid flute step
            HeroRenderPrimitives.box(steel, m,
                    0.12F, -0.040F, -0.44F,
                    0.29F, 0.040F, -0.34F,
                    0.90F, 0.94F, 0.98F, 1.0F, packedLight);
            // Tip auger blade
            HeroRenderPrimitives.box(steel, m,
                    0.03F, -0.032F, -0.54F,
                    0.15F, 0.032F, -0.44F,
                    0.94F, 0.96F, 1.00F, 1.0F, packedLight);
            pose.popPose();
        }
        pose.popPose();

        // Pass 2: Glowing cyan high-energy cutting rings & spinning drill tip
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        HeroRenderPrimitives.alignLocalNorthToFacing(pose, facing);
        pose.mulPose(Axis.ZP.rotationDegrees(-deg * 0.75F));

        // Base cutting ring in XY plane (rotate 90 around X so XZ ring maps to XY)
        pose.pushPose();
        pose.translate(0.0D, 0.0D, -0.31D);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.37F, 0.028F, 0.026F,
                0.28F, 0.96F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Forward cutting ring
        pose.pushPose();
        pose.translate(0.0D, 0.0D, -0.43D);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.22F, 0.026F, 0.024F,
                0.36F, 0.98F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Glowing drill point
        Matrix4f tipM = pose.last().pose();
        HeroRenderPrimitives.box(cyan, tipM,
                -0.065F, -0.065F, -0.57F,
                0.065F, 0.065F, -0.49F,
                0.50F, 1.00F, 1.00F, 0.95F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
