package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.raid.entity.RiftAnchorCrystalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Phase 120: High-visibility 3D procedural renderer for {@code rift_anchor_crystal} —
 * armored dark-gunmetal containment fins orbiting a blazing neon-purple &amp; cyan octahedral crystal.
 */
public final class RiftAnchorCrystalRenderer extends EntityRenderer<RiftAnchorCrystalEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/entity/dimensional_sovereign.png");

    public RiftAnchorCrystalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.55F;
    }

    @Override
    public ResourceLocation getTextureLocation(RiftAnchorCrystalEntity entity) {
        return TEX;
    }

    @Override
    public void render(RiftAnchorCrystalEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        float tSec = ((Util.getMillis() % 600_000L) / 1000.0F) + entity.getId() * 0.51F;
        float bob = 0.22F * (float) Math.sin(tSec * 3.4D);

        pose.pushPose();
        pose.translate(0.0D, 1.15D + bob, 0.0D);

        // Pass 1: 4 orbiting dark gunmetal anchor frame pylons
        VertexConsumer armor = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(tSec * 52.0F + i * 90.0F));
            pose.translate(0.56D, 0.0D, 0.0D);
            Matrix4f m = pose.last().pose();
            HeroRenderPrimitives.box(armor, m, -0.07F, -0.58F, -0.09F, 0.07F, 0.58F, 0.09F,
                    0.92F, 0.90F, 1.00F, 1.0F, packedLight);
            pose.popPose();
        }

        // Pass 2: Blazing rotating violet crystal spire core + horizontal shield-feed ring
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-tSec * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        HeroRenderPrimitives.box(purple, pose.last().pose(), -0.34F, -0.34F, -0.34F, 0.34F, 0.34F, 0.34F,
                0.96F, 0.36F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 90.0F));
        HeroRenderPrimitives.energyRingXZ(pose, purple, 0.72F, 0.044F, 0.040F,
                0.92F, 0.32F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Bright cyan inner core & upper/lower emitter tips (high contrast weakpoint beacon!)
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, cm, -0.14F, -0.88F, -0.14F, 0.14F, 0.88F, 0.14F,
                0.42F, 0.98F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);

        pose.popPose();
    }
}
