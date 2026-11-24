package com.industrialcore.client;

import com.industrialcore.raid.entity.RiftPlasmaBoltEntity;
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
 * Phase 116: Glowing 3D plasma orb renderer for {@link RiftPlasmaBoltEntity}.
 */
public final class RiftPlasmaBoltRenderer extends EntityRenderer<RiftPlasmaBoltEntity> {
    public RiftPlasmaBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftPlasmaBoltEntity entity) {
        return entity.isOverdrive() ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_PURPLE;
    }

    @Override
    public void render(RiftPlasmaBoltEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        boolean od = entity.isOverdrive();
        float spin = ((Util.getMillis() % 60_000L) / 1000.0F) * 320.0F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));

        pose.pushPose();
        pose.translate(0.0D, 0.22D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(vc, m, -0.17F, -0.17F, -0.17F, 0.17F, 0.17F, 0.17F,
                od ? 1.00F : 0.94F, od ? 0.24F : 0.36F, od ? 0.42F : 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(vc, m, -0.09F, -0.09F, -0.09F, 0.09F, 0.09F, 0.09F,
                1.00F, 1.00F, 1.00F, 1.00F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
