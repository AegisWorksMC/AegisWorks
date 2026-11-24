package com.industrialcore.client;

import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.TransporterKind;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 111 Tier 2 Hero BER: Quantum Transporter series (all 4 tiers:
 * {@code basic_item_transporter}, {@code advanced_item_transporter},
 * {@code quantum_transporter}, {@code quantum_transporter_array}).
 *
 * <p>Floats a tier-colored rotating quantum transfer ring &amp; crystalline core cleanly inside/above
 * the transporter chamber with 36m distance culling and calm {@link SoundFamily#QUANTUM_CHIME}
 * audio budget control.</p>
 */
public final class TransporterHeroRenderer implements BlockEntityRenderer<TransporterBlockEntity> {
    public TransporterHeroRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(TransporterBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.2D, p.getY(), p.getZ() - 0.2D,
                p.getX() + 1.2D, p.getY() + 1.6D, p.getZ() + 1.2D);
    }

    @Override
    public void render(TransporterBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        TransporterKind kind = be.getKind();
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        String transporterId = switch (kind) {
            case BASIC -> "basic_item_transporter";
            case ADVANCED -> "advanced_item_transporter";
            case QUANTUM -> "quantum_transporter";
            case ARRAY -> "quantum_transporter_array";
        };
        FactoryAudioController.onMotionPhase(be.getBlockPos(), transporterId,
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.90F), 0.25F);

        float deg = LivingFactoryAnimLogic.transporterRingDeg(tSec);
        float bob = 0.045F * (float) Math.sin(tSec * 2.6D);

        ResourceLocation primaryTex;
        float r, g, b;
        switch (kind) {
            case BASIC -> {
                primaryTex = HeroRenderPrimitives.TEX_GLOW_CYAN;
                r = 0.28F; g = 0.94F; b = 1.00F;
            }
            case ADVANCED -> {
                primaryTex = HeroRenderPrimitives.TEX_GLOW_ORANGE;
                r = 1.00F; g = 0.78F; b = 0.28F;
            }
            case QUANTUM, ARRAY -> {
                primaryTex = HeroRenderPrimitives.TEX_GLOW_PURPLE;
                r = 0.86F; g = 0.42F; b = 1.00F;
            }
            default -> {
                primaryTex = HeroRenderPrimitives.TEX_GLOW_CYAN;
                r = 0.28F; g = 0.94F; b = 1.00F;
            }
        }

        // Pass 1: Floating rotating quantum transfer ring & diamond core
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(primaryTex));
        pose.pushPose();
        pose.translate(0.5D, 1.08D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(18.0F));
        HeroRenderPrimitives.energyRingXZ(pose, vc, 0.28F, 0.022F, 0.020F,
                r, g, b, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 1.08D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.4F));
        pose.mulPose(Axis.XP.rotationDegrees(35.264F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(vc, cm, -0.075F, -0.075F, -0.075F, 0.075F, 0.075F, 0.075F,
                r, g, b, 0.95F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Higher tiers (QUANTUM & ARRAY) get a counter-rotating cyan outer containment ring
        if (kind == TransporterKind.QUANTUM || kind == TransporterKind.ARRAY) {
            VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
            pose.pushPose();
            pose.translate(0.5D, (kind == TransporterKind.ARRAY ? 1.22D : 1.08D) - bob * 0.5F, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.35F));
            pose.mulPose(Axis.ZP.rotationDegrees(22.0F));
            HeroRenderPrimitives.energyRingXZ(pose, cyan, kind == TransporterKind.ARRAY ? 0.34F : 0.21F,
                    0.020F, 0.020F, 0.30F, 0.96F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
