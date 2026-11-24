package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Phase 109: Shared zero-allocation-per-frame box and octagonal-ring primitives for Tier 2
 * hero {@code BlockEntityRenderer}s.
 */
final class HeroRenderPrimitives {
    static final ResourceLocation TEX_CASING =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/reactor_casing.png");
    static final ResourceLocation TEX_FRAME =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/parts/part_chassis_frame.png");
    static final ResourceLocation TEX_AMBER =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/parts/part_accent_amber.png");
    static final ResourceLocation TEX_GLOW_CYAN =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_cyan.png");
    static final ResourceLocation TEX_GLOW_PURPLE =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_purple.png");
    static final ResourceLocation TEX_GLOW_GREEN =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_green.png");
    static final ResourceLocation TEX_GLOW_ORANGE =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_orange.png");

    private static final int RING_SEGMENTS = 8;
    private static final float[] SEG_ANGLES = new float[RING_SEGMENTS];

    static {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            SEG_ANGLES[i] = i * (360.0F / RING_SEGMENTS);
        }
    }

    private HeroRenderPrimitives() {}

    /** Horizontal facing rotation matching standard blockstate Y-rotations (model faces -Z / NORTH). */
    static float horizontalRotationDeg(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    /** Applies 6-direction rotation around block center so local -Z points along {@code facing}. */
    static void alignLocalNorthToFacing(PoseStack pose, Direction facing) {
        switch (facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            default -> {}
        }
    }

    /**
     * Emits an 8-segment octagonal glowing energy ring lying in the local XZ plane around origin.
     * Zero heap allocations per call.
     */
    static void energyRingXZ(PoseStack pose, VertexConsumer vc,
                             float radius, float halfThickness, float halfHeight,
                             float r, float g, float b, float a, int light) {
        // Chord half-length for an octagon circumscribing radius: radius * tan(22.5 deg) ~= 0.4142 * r
        float halfSegLen = radius * 0.435F;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(SEG_ANGLES[i]));
            Matrix4f m = pose.last().pose();
            box(vc, m,
                    -halfSegLen, -halfHeight, radius - halfThickness,
                    halfSegLen, halfHeight, radius + halfThickness,
                    r, g, b, a, light);
            pose.popPose();
        }
    }

    static void box(VertexConsumer vc, Matrix4f m,
                    float x0, float y0, float z0,
                    float x1, float y1, float z1,
                    float r, float g, float b, float a, int light) {
        face(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b, a, light);
        face(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r * 0.62F, g * 0.62F, b * 0.62F, a, light);
        face(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r * 0.86F, g * 0.86F, b * 0.86F, a, light);
        face(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r * 0.86F, g * 0.86F, b * 0.86F, a, light);
        face(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r * 0.74F, g * 0.74F, b * 0.74F, a, light);
        face(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r * 0.74F, g * 0.74F, b * 0.74F, a, light);
    }

    private static void face(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             float r, float g, float b, float a, int light) {
        int ri = clampByte(r);
        int gi = clampByte(g);
        int bi = clampByte(b);
        int ai = clampByte(a);
        vc.addVertex(m, x0, y0, z0).setColor(ri, gi, bi, ai).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x1, y1, z1).setColor(ri, gi, bi, ai).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(ri, gi, bi, ai).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(ri, gi, bi, ai).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    private static int clampByte(float v) {
        int i = Math.round(v * 255.0F);
        return i < 0 ? 0 : Math.min(255, i);
    }
}
