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
import org.joml.Matrix4f;

/**
 * Phase 111 Tier 2 Hero BER: Agriculture &amp; Forestry machines
 * ({@code crop_harvester}, {@code tree_harvester}, {@code growth_accelerator}).
 */
public final class NatureHeroRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public enum Kind {
        CROP_HARVESTER,
        TREE_HARVESTER,
        GROWTH_ACCELERATOR
    }

    private final Kind kind;

    public NatureHeroRenderer(BlockEntityRendererProvider.Context ctx, Kind kind) {
        this.kind = kind;
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
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        BlockPos pos = be.getBlockPos();

        switch (kind) {
            case CROP_HARVESTER -> renderCropHarvester(pos, facing, tSec, pose, buffers, packedLight);
            case TREE_HARVESTER -> renderTreeHarvester(pos, facing, tSec, pose, buffers, packedLight);
            case GROWTH_ACCELERATOR -> renderGrowthAccelerator(pos, tSec, pose, buffers);
        }
    }

    private static void renderCropHarvester(BlockPos pos, Direction facing, float tSec,
                                            PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "crop_harvester",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.30F), 0.25F);
        float dx = LivingFactoryAnimLogic.cropHarvesterBladeX(tSec);

        // Pass 1: Rapidly reciprocating metallic mower blade teeth across front lower cutter bar
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m,
                0.15F + dx, 0.085F, -0.042F,
                0.85F + dx, 0.135F, 0.008F,
                0.88F, 0.92F, 0.96F, 1.0F, packedLight);
        // 4 cutter teeth along the bar
        for (int i = 0; i < 4; i++) {
            float tx = 0.21F + i * 0.175F + dx;
            HeroRenderPrimitives.box(steel, m,
                    tx, 0.075F, -0.078F,
                    tx + 0.075F, 0.140F, -0.036F,
                    0.94F, 0.96F, 1.00F, 1.0F, packedLight);
        }
        pose.popPose();

        // Pass 2: Glowing emerald-cyan harvest laser edge
        VertexConsumer green = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_GREEN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f gm = pose.last().pose();
        HeroRenderPrimitives.box(green, gm,
                0.14F + dx, 0.100F, -0.088F,
                0.86F + dx, 0.125F, -0.068F,
                0.34F, 1.00F, 0.56F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderTreeHarvester(BlockPos pos, Direction facing, float tSec,
                                            PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "tree_harvester",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.30F), 0.25F);
        float dx = LivingFactoryAnimLogic.treeHarvesterSawX(tSec);

        // Pass 1: Rapidly reciprocating chainsaw bar & teeth mounted between front guide pillars (y=6.5..8)
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m,
                0.20F + dx, 0.425F, -0.055F,
                0.80F + dx, 0.485F, 0.025F,
                0.88F, 0.92F, 0.96F, 1.0F, packedLight);
        for (int i = 0; i < 5; i++) {
            float tx = 0.22F + i * 0.132F + dx;
            HeroRenderPrimitives.box(steel, m,
                    tx, 0.435F, -0.095F,
                    tx + 0.058F, 0.478F, -0.048F,
                    0.96F, 0.98F, 1.00F, 1.0F, packedLight);
        }
        pose.popPose();

        // Pass 2: Heated orange-amber friction edge along the chainsaw teeth
        VertexConsumer orange = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f gm = pose.last().pose();
        HeroRenderPrimitives.box(orange, gm,
                0.19F + dx, 0.440F, -0.105F,
                0.81F + dx, 0.472F, -0.082F,
                1.00F, 0.74F, 0.24F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderGrowthAccelerator(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "growth_accelerator",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.10F), 0.20F);
        float deg = LivingFactoryAnimLogic.growthCoreDeg(tSec);
        float bob = LivingFactoryAnimLogic.growthCoreBobY(tSec);

        // Pass 1: Rotating & vertically pulsing emerald bio-energy rings inside open 4-pillar cage
        VertexConsumer green = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_GREEN));
        pose.pushPose();
        pose.translate(0.5D, 0.54D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        HeroRenderPrimitives.energyRingXZ(pose, green, 0.31F, 0.024F, 0.028F,
                0.26F, 1.00F, 0.48F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 0.54D - bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.4F));
        pose.mulPose(Axis.XP.rotationDegrees(16.0F));
        HeroRenderPrimitives.energyRingXZ(pose, green, 0.26F, 0.020F, 0.022F,
                0.42F, 1.00F, 0.62F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Top emerald emitter halo hovering gently above open top ring
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.05D + bob * 0.5F, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg * 0.7F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.24F, 0.020F, 0.018F,
                0.30F, 0.96F, 0.82F, 0.85F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
