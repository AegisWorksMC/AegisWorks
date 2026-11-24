package com.industrialcore.client;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 111 Tier 2 Hero BER: Life &amp; Utility machines
 * ({@code fluid_pump}, {@code auto_builder}).
 */
public final class LifeHeroRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public enum Kind {
        FLUID_PUMP,
        AUTO_BUILDER
    }

    private final Kind kind;

    public LifeHeroRenderer(BlockEntityRendererProvider.Context ctx, Kind kind) {
        this.kind = kind;
    }

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.25D, p.getY(), p.getZ() - 0.25D,
                p.getX() + 1.25D, p.getY() + 1.8D, p.getZ() + 1.25D);
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        BlockPos pos = be.getBlockPos();

        switch (kind) {
            case FLUID_PUMP -> renderFluidPump(pos, facing, tSec, pose, buffers, packedLight);
            case AUTO_BUILDER -> renderAutoBuilder(pos, facing, tSec, pose, buffers, packedLight);
        }
    }

    private static void renderFluidPump(BlockPos pos, Direction facing, float tSec,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight) {
        // Trigger hydraulic pump stroke right at peak piston top-dead-center (0-frame synced)
        FactoryAudioController.onMotionPhase(pos, "fluid_pump",
                LivingFactoryAnimLogic.cyclePhase(tSec, LivingFactoryAnimLogic.FLUID_PUMP_PERIOD_SEC), 0.25F);
        float leftY = LivingFactoryAnimLogic.fluidPumpPistonOffsetY(tSec, false);
        float rightY = LivingFactoryAnimLogic.fluidPumpPistonOffsetY(tSec, true);
        float centerY = 0.04F + 0.04F * (float) Math.sin(tSec * (Math.PI * 4.0D / LivingFactoryAnimLogic.FLUID_PUMP_PERIOD_SEC));

        // Pass 1: Alternating dual steel piston rods & caps above twin cylinders + center pressure valve
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        // Left cylinder piston shaft + head cap (x=0.265, z=0.50)
        HeroRenderPrimitives.box(steel, m, 0.21F, 0.96F, 0.44F, 0.32F, 1.04F + leftY, 0.56F,
                0.80F, 0.86F, 0.92F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.16F, 1.04F + leftY, 0.36F, 0.37F, 1.11F + leftY, 0.64F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        // Right cylinder piston shaft + head cap (x=0.735, z=0.50)
        HeroRenderPrimitives.box(steel, m, 0.68F, 0.96F, 0.44F, 0.79F, 1.04F + rightY, 0.56F,
                0.80F, 0.86F, 0.92F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.63F, 1.04F + rightY, 0.36F, 0.84F, 1.11F + rightY, 0.64F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        // Center hydraulic manifold valve plunger
        HeroRenderPrimitives.box(steel, m, 0.45F, 0.90F, 0.45F, 0.55F, 0.98F + centerY, 0.55F,
                0.84F, 0.90F, 0.96F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Glowing cyan fluid-pressure collars riding on each piston
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f gm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, gm, 0.15F, 1.015F + leftY, 0.35F, 0.38F, 1.045F + leftY, 0.65F,
                0.24F, 0.94F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, gm, 0.62F, 1.015F + rightY, 0.35F, 0.85F, 1.045F + rightY, 0.65F,
                0.24F, 0.94F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderAutoBuilder(BlockPos pos, Direction facing, float tSec,
                                          PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "auto_builder",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.60F), 0.25F);
        float sweep = LivingFactoryAnimLogic.autoBuilderScanDeg(tSec);

        // Pass 1: Twin rotating projector optic heads mounted atop the left/right projectors
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        renderProjectorHead(pose, steel, 0.28F, 1.03F, 0.50F, sweep, packedLight);
        renderProjectorHead(pose, steel, 0.72F, 1.03F, 0.50F, -sweep, packedLight);
        pose.popPose();

        // Pass 2: Twin sweeping translucent cyan architectural holo-scan beams
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        renderScanBeam(pose, cyan, 0.28F, 1.05F, 0.50F, sweep);
        renderScanBeam(pose, cyan, 0.72F, 1.05F, 0.50F, -sweep);
        pose.popPose();
    }

    private static void renderProjectorHead(PoseStack pose, VertexConsumer vc,
                                            float cx, float cy, float cz, float yawDeg, int light) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(vc, m, -0.075F, -0.035F, -0.095F, 0.075F, 0.055F, 0.085F,
                0.86F, 0.92F, 0.98F, 1.0F, light);
        pose.popPose();
    }

    private static void renderScanBeam(PoseStack pose, VertexConsumer vc,
                                       float cx, float cy, float cz, float yawDeg) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        pose.mulPose(Axis.XP.rotationDegrees(-24.0F));
        Matrix4f m = pose.last().pose();
        // Glowing emitter lens
        HeroRenderPrimitives.box(vc, m, -0.052F, -0.025F, -0.105F, 0.052F, 0.035F, -0.065F,
                0.45F, 1.00F, 1.00F, 0.95F, LightTexture.FULL_BRIGHT);
        // Sweeping architectural cyan scan ray
        HeroRenderPrimitives.box(vc, m, -0.018F, -0.014F, -0.58F, 0.018F, 0.018F, -0.10F,
                0.28F, 0.96F, 1.00F, 0.78F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
