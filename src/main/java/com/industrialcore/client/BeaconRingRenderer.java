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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Phase 110 Tier 2 Hero BER: Reusable calm floating energy-ring renderer for tower/beacon
 * machines ({@code spawn_suppressor} &amp; {@code wireless_charger}).
 *
 * <p>When active ({@code lit=true}), floats two gently counter-rotating glowing energy rings
 * around the top spire/coil. Skips rendering completely when idle or beyond 36 blocks.</p>
 */
public final class BeaconRingRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final String blockId;
    private final ResourceLocation primaryTex;
    private final ResourceLocation secondaryTex;
    private final float r1, g1, b1;
    private final float r2, g2, b2;
    private final float baseY;

    public BeaconRingRenderer(BlockEntityRendererProvider.Context ctx, String blockId,
                              ResourceLocation primaryTex, float r1, float g1, float b1,
                              ResourceLocation secondaryTex, float r2, float g2, float b2,
                              float baseY) {
        this.blockId = blockId;
        this.primaryTex = primaryTex;
        this.secondaryTex = secondaryTex;
        this.r1 = r1;
        this.g1 = g1;
        this.b1 = b1;
        this.r2 = r2;
        this.g2 = g2;
        this.b2 = b2;
        this.baseY = baseY;
    }

    public static <T extends BlockEntity> BeaconRingRenderer<T> spawnSuppressor(
            BlockEntityRendererProvider.Context ctx) {
        return new BeaconRingRenderer<>(ctx, "spawn_suppressor",
                HeroRenderPrimitives.TEX_GLOW_ORANGE, 1.00F, 0.82F, 0.28F,
                HeroRenderPrimitives.TEX_GLOW_CYAN, 0.30F, 0.94F, 1.00F,
                0.96F);
    }

    public static <T extends BlockEntity> BeaconRingRenderer<T> wirelessCharger(
            BlockEntityRendererProvider.Context ctx) {
        return new BeaconRingRenderer<>(ctx, "wireless_charger",
                HeroRenderPrimitives.TEX_GLOW_CYAN, 0.28F, 0.96F, 1.00F,
                HeroRenderPrimitives.TEX_GLOW_PURPLE, 0.72F, 0.48F, 1.00F,
                0.98F);
    }

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) {
            return;
        }
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), blockId,
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.80F), 0.25F);
        float deg = LivingFactoryAnimLogic.beaconRingDeg(tSec);
        float bob = LivingFactoryAnimLogic.beaconRingBobY(tSec);

        // Pass 1: Primary floating outer beacon ring
        VertexConsumer vc1 = buffers.getBuffer(RenderType.entityTranslucent(primaryTex));
        pose.pushPose();
        pose.translate(0.5D, baseY + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(10.0F));
        HeroRenderPrimitives.energyRingXZ(pose, vc1, 0.34F, 0.026F, 0.024F,
                r1, g1, b1, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Secondary counter-rotating upper halo ring
        VertexConsumer vc2 = buffers.getBuffer(RenderType.entityTranslucent(secondaryTex));
        pose.pushPose();
        pose.translate(0.5D, baseY + 0.16D - bob * 0.7F, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.45F));
        pose.mulPose(Axis.ZP.rotationDegrees(14.0F));
        HeroRenderPrimitives.energyRingXZ(pose, vc2, 0.23F, 0.022F, 0.022F,
                r2, g2, b2, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
