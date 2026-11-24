package com.industrialcore.client;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 109 / 117 Tier 2 Hero BER: Singularity Reactor ({@code singularity_reactor}).
 *
 * <p>Renders a warped-light Black Hole event horizon core surrounded by 3 differently-tilted
 * interlocking counter-rotating gimbal rings (cyan, neon purple, and blazing overdrive/magenta)
 * plus orbiting gravity-containment collector pylons inside the multiblock chamber.</p>
 */
public final class SingularityReactorRenderer implements BlockEntityRenderer<SingularityReactorBlockEntity> {
    public SingularityReactorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(SingularityReactorBlockEntity be) {
        IntPos min = be.getClientMinPos();
        IntPos max = be.getClientMaxPos();
        if (min != null && max != null) {
            return new AABB(min.x(), min.y(), min.z(), max.x() + 1.0D, max.y() + 1.0D, max.z() + 1.0D);
        }
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 7.0D, p.getY() - 7.0D, p.getZ() - 7.0D,
                p.getX() + 8.0D, p.getY() + 8.0D, p.getZ() + 8.0D);
    }

    @Override
    public void render(SingularityReactorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.isMultiblockFormed()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(AbstractLifeBlock.LIT) || !state.getValue(AbstractLifeBlock.LIT)) {
            return;
        }
        IntPos min = be.getClientMinPos();
        IntPos max = be.getClientMaxPos();
        if (min == null || max == null) {
            return;
        }

        BlockPos origin = be.getBlockPos();
        double cx = (min.x() + max.x() + 1.0D) * 0.5D - origin.getX();
        double cy = (min.y() + max.y() + 1.0D) * 0.5D - origin.getY();
        double cz = (min.z() + max.z() + 1.0D) * 0.5D - origin.getZ();

        int intW = Math.max(1, (max.x() - min.x() + 1) - 2);
        int intH = Math.max(1, (max.y() - min.y() + 1) - 2);
        int intD = Math.max(1, (max.z() - min.z() + 1) - 2);
        float minSpan = Math.min(intW, Math.min(intH, intD));

        float outerR = minSpan <= 1.5F ? 0.65F : Math.min(2.15F, minSpan * 0.43F);
        float midR = outerR * 0.84F;
        float innerR = outerR * 0.68F;
        float voidR = minSpan <= 1.5F ? 0.24F : Math.min(0.46F, outerR * 0.28F);
        float lensR = voidR * 1.48F;

        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        boolean overdrive = be.isClientOverdrive();
        float animT = tSec * com.industrialcore.reactor.ReactorOverdriveLogic.ringSpeedMultiplier(overdrive);
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "singularity_reactor",
                LivingFactoryAnimLogic.cyclePhase(tSec, overdrive ? 1.55F : 2.80F), 0.15F);
        float outerDeg = LivingFactoryAnimLogic.singularityOuterRingDeg(animT);
        float innerDeg = LivingFactoryAnimLogic.singularityInnerRingDeg(animT);
        float precDeg = LivingFactoryAnimLogic.singularityPrecessionDeg(animT);
        float pulse = LivingFactoryAnimLogic.singularityCorePulse(animT) * (overdrive ? 1.14F : 1.0F);

        // Pass 1: Pitch-black Singularity Event Horizon core + orbiting gunmetal collector pylons (for >= 5x5x5)
        VertexConsumer dark = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(precDeg * 1.6F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        HeroRenderPrimitives.box(dark, pose.last().pose(), -voidR, -voidR, -voidR, voidR, voidR, voidR,
                0.06F, 0.04F, 0.12F, 1.0F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        if (minSpan >= 2.5F) {
            float capY = Math.min(1.28F, intH * 0.42F);
            pose.pushPose();
            pose.translate(cx, cy, cz);
            pose.mulPose(Axis.YP.rotationDegrees(-precDeg));
            Matrix4f pm = pose.last().pose();
            HeroRenderPrimitives.box(dark, pm, -0.18F, capY - 0.12F, -0.18F, 0.18F, capY + 0.12F, 0.18F,
                    0.75F, 0.80F, 0.90F, 1.0F, packedLight);
            HeroRenderPrimitives.box(dark, pm, -0.18F, -capY - 0.12F, -0.18F, 0.18F, -capY + 0.12F, 0.18F,
                    0.75F, 0.80F, 0.90F, 1.0F, packedLight);
            pose.popPose();
        }

        // Pass 2: Gimbal Ring #1 (Outer Cyan / Overdrive Gold, tilted XP +22 deg) & Polar Emitters
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(
                overdrive ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(outerDeg));
        pose.mulPose(Axis.XP.rotationDegrees(22.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, outerR, 0.058F, 0.048F,
                overdrive ? 1.00F : 0.24F, overdrive ? 0.72F : 0.96F, overdrive ? 0.22F : 1.00F,
                0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Gimbal Ring #3 (Inner high-speed Cyan/White tilted XP -58 deg, ZP +24 deg)
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(-58.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(24.0F));
        pose.mulPose(Axis.YP.rotationDegrees(outerDeg * 1.55F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, innerR, 0.042F, 0.036F,
                overdrive ? 1.00F : 0.65F, overdrive ? 0.88F : 0.98F, overdrive ? 0.45F : 1.00F,
                0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Gimbal Ring #2 (Middle Neon Purple, tilted ZP -38 deg) + Warped-Light Lensing Core Halo
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.ZP.rotationDegrees(-38.0F));
        pose.mulPose(Axis.YP.rotationDegrees(innerDeg));
        HeroRenderPrimitives.energyRingXZ(pose, purple, midR, 0.054F, 0.046F,
                0.86F, 0.32F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Warped gravitational lensing shell surrounding the black-hole event horizon
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-precDeg * 1.2F));
        pose.mulPose(Axis.XP.rotationDegrees(precDeg * 0.8F));
        float lr = lensR * (minSpan <= 1.5F ? 0.96F : pulse);
        HeroRenderPrimitives.box(purple, pose.last().pose(), -lr, -lr, -lr, lr, lr, lr,
                0.88F, 0.44F, 1.00F, 0.56F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
