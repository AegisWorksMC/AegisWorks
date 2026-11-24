package com.industrialcore.client;

import com.industrialcore.weapon.EnergyArrowEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sci-Fi Entity Renderer for {@link EnergyArrowEntity}: renders the spectral arrow core plus
 * an additive blazing meteor ribbon trail &amp; glowing starlight tip!
 */
public class EnergyArrowRenderer extends ArrowRenderer<EnergyArrowEntity> {
    private static final ResourceLocation SPECTRAL_TEX =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/spectral_arrow.png");

    public EnergyArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EnergyArrowEntity entity) {
        return SPECTRAL_TEX;
    }

    @Override
    public void render(EnergyArrowEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, 0x00F000F0);

        // Render blazing additive meteor stardust ribbon trailing behind the arrow!
        Vec3 vel = entity.getDeltaMovement();
        double speed = vel.length();
        if (speed < 0.08D) {
            return;
        }
        Vec3 dir = vel.normalize();
        double tailLen = Math.min(4.2D, 1.4D + speed * 0.85D);
        Vec3 tail = dir.scale(-tailLen);

        boolean gravity = entity.isGravityArrow();
        boolean homing = entity.isHomingArrow();
        float r = gravity ? 0.82F : (homing ? 1.00F : 0.32F);
        float g = gravity ? 0.34F : (homing ? 0.84F : 1.00F);
        float b = gravity ? 1.00F : (homing ? 0.35F : 0.72F);

        Vec3 side1 = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side1.lengthSqr() < 1.0E-4D) {
            side1 = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side1 = side1.normalize();
        }
        Vec3 side2 = dir.cross(side1).normalize();
        float halfW = gravity ? 0.22F : 0.16F;
        float vScroll = -((Util.getMillis() % 10_000L) / 1000.0F) * 6.0F;

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, WeaponWorldFxRenderer.TEX_LASER_CORE);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        emitTrailRibbon(bb, mat, tail, side1.scale(halfW), vScroll, r, g, b, 0.92F);
        emitTrailRibbon(bb, mat, tail, side2.scale(halfW), vScroll, r, g, b, 0.85F);
        emitTrailRibbon(bb, mat, tail.scale(0.65D), side1.scale(halfW * 0.42F), vScroll, 1.0F, 1.0F, 1.0F, 0.95F);
        var mesh = bb.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void emitTrailRibbon(BufferBuilder bb, Matrix4f mat, Vec3 tail, Vec3 side,
                                        float vScroll, float r, float g, float b, float a) {
        // Head at (0,0,0) bright -> tail fades cleanly to alpha 0
        bb.addVertex(mat, (float) -side.x, (float) -side.y, (float) -side.z).setUv(0.0F, vScroll).setColor(r, g, b, a);
        bb.addVertex(mat, (float) side.x, (float) side.y, (float) side.z).setUv(1.0F, vScroll).setColor(r, g, b, a);
        bb.addVertex(mat, (float) (tail.x + side.x * 0.25D), (float) (tail.y + side.y * 0.25D), (float) (tail.z + side.z * 0.25D))
                .setUv(1.0F, vScroll + 1.4F).setColor(r, g, b, 0.0F);
        bb.addVertex(mat, (float) (tail.x - side.x * 0.25D), (float) (tail.y - side.y * 0.25D), (float) (tail.z - side.z * 0.25D))
                .setUv(0.0F, vScroll + 1.4F).setColor(r, g, b, 0.0F);
    }
}
