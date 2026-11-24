package com.industrialcore.client;

import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.machine.FactoryAudioLogic;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.RecoveryKind;
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
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 110 / 118 Tier 2 Hero BER: Recovery Devices ({@code flywheel}, {@code pressure_recovery},
 * {@code heat_recovery}) registered on {@code ModBlockEntities.RECOVERY}.
 *
 * <p>With Phase 118 translucent dark tinted glass viewports enabled on all three recovery housings,
 * the internal high-inertia kinetic flywheel disc, 6-blade exhaust pressure turbine, and pulsing
 * thermal recovery exchanger coils are clearly visible through the reinforced glass.</p>
 */
public final class FlywheelRenderer implements BlockEntityRenderer<RecoveryBlockEntity> {
    public FlywheelRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(RecoveryBlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (be.kind() == RecoveryKind.PRESSURE) {
            return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0D, p.getY() + 1.6D, p.getZ() + 1.0D);
        }
        return new AABB(p);
    }

    @Override
    public void render(RecoveryBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be.kind() == RecoveryKind.PRESSURE) {
            renderPressureTurbine(be, pose, buffers, packedLight);
            return;
        }
        if (be.kind() == RecoveryKind.HEAT) {
            renderHeatExchanger(be, pose, buffers, packedLight);
            return;
        }
        if (be.kind() != RecoveryKind.FLYWHEEL) {
            return;
        }
        float chargeRatio = be.getClientChargeRatio();
        if (chargeRatio <= 0.01F) {
            return;
        }

        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "flywheel",
                FactoryAudioLogic.flywheelPitch(chargeRatio),
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.20F), 0.15F);
        float deg = LivingFactoryAnimLogic.flywheelRotorDeg(tSec, chargeRatio);

        // Pass 1: Heavy gunmetal vertical axle + large central inertial rotor spokes visible through translucent glass
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.52D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        Matrix4f m = pose.last().pose();
        // Central vertical drive shaft
        HeroRenderPrimitives.box(steel, m, -0.075F, -0.30F, -0.075F, 0.075F, 0.42F, 0.075F,
                0.88F, 0.92F, 0.98F, 1.0F, packedLight);
        // Main heavy inertial rotor spokes at mid-chamber (y=0.52)
        HeroRenderPrimitives.box(steel, m, -0.34F, -0.055F, -0.048F, 0.34F, 0.055F, 0.048F,
                0.80F, 0.86F, 0.94F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, -0.048F, -0.055F, -0.34F, 0.048F, 0.055F, 0.34F,
                0.80F, 0.86F, 0.94F, 1.0F, packedLight);
        // Upper viewport cross-spokes (y=0.90)
        HeroRenderPrimitives.box(steel, m, -0.22F, 0.36F, -0.032F, 0.22F, 0.41F, 0.032F,
                0.84F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, -0.032F, 0.36F, -0.22F, 0.032F, 0.41F, 0.22F,
                0.84F, 0.90F, 0.96F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Industrial Amber inertia counterweight blocks on rotor tips
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.52D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        Matrix4f am = pose.last().pose();
        HeroRenderPrimitives.box(amber, am, 0.26F, -0.085F, -0.075F, 0.36F, 0.085F, 0.075F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am, -0.36F, -0.085F, -0.075F, -0.26F, 0.085F, 0.075F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am, -0.075F, -0.085F, 0.26F, 0.075F, 0.085F, 0.36F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am, -0.075F, -0.085F, -0.36F, 0.075F, 0.085F, -0.26F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Glowing cyan kinetic energy rim & high-speed upper/side ring indicators
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        float brightness = 0.65F + 0.35F * chargeRatio;
        pose.pushPose();
        pose.translate(0.5D, 0.915D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.21F, 0.028F, 0.028F,
                0.26F, 0.95F * brightness, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 0.52D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 0.55F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.36F, 0.032F, 0.048F,
                0.24F, 0.92F * brightness, 1.00F, 0.82F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 111 / 118 Tier 2 Hero BER: Pressure Recovery ({@code pressure_recovery}) 6-blade
     * high-speed exhaust turbine + dual counter-spinning cyan pressure rings.
     */
    private static void renderPressureTurbine(RecoveryBlockEntity be, PoseStack pose,
                                              MultiBufferSource buffers, int packedLight) {
        if (!be.isClientRunning()) {
            return;
        }
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "pressure_recovery",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.10F), 0.25F);
        float deg = LivingFactoryAnimLogic.pressureTurbineDeg(tSec, true);

        // Pass 1: Spinning 6-blade gunmetal exhaust turbine inside top tower (y=1.24) + inner chamber shaft
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 1.24D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m, -0.068F, -0.45F, -0.068F, 0.068F, 0.035F, 0.068F,
                0.88F, 0.92F, 0.98F, 1.0F, packedLight);
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            pose.mulPose(Axis.XP.rotationDegrees(20.0F));
            Matrix4f bm = pose.last().pose();
            HeroRenderPrimitives.box(steel, bm, 0.05F, -0.016F, -0.036F, 0.25F, 0.016F, 0.036F,
                    0.82F, 0.88F, 0.96F, 1.0F, packedLight);
            pose.popPose();
        }
        pose.popPose();

        // Pass 2: Glowing cyan pressure-exhaust rings circling tower vent & internal translucent chamber
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.26D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 0.65F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.25F, 0.024F, 0.024F,
                0.30F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 0.54D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg * 0.80F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.28F, 0.024F, 0.032F,
                0.24F, 0.92F, 1.00F, 0.80F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 118: Heat Recovery ({@code heat_recovery}) pulsing thermal heat-exchanger coils
     * visible through the translucent dark tinted glass viewports while recovering waste heat.
     */
    private static void renderHeatExchanger(RecoveryBlockEntity be, PoseStack pose,
                                            MultiBufferSource buffers, int packedLight) {
        if (!be.isClientRunning()) {
            return;
        }
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        float pulse = 0.72F + 0.28F * (float) Math.sin(tSec * 4.8D);
        float deg = (tSec * 48.0F) % 360.0F;

        VertexConsumer fire = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5D, 0.40D + i * 0.26D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(i == 0 ? deg : -deg));
            HeroRenderPrimitives.energyRingXZ(pose, fire, 0.26F, 0.028F, 0.028F,
                    1.00F, 0.58F * pulse, 0.16F, 0.92F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
