package com.industrialcore.client;

import com.industrialcore.block.MobFanBlock;
import com.industrialcore.blockentity.MobFanBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Phase 109 Tier 2 Hero BER #3: Mob Fan ({@code mob_fan}).
 *
 * <p>When active ({@code lit=true}), spins 4 sleek angled industrial turbine blades with glowing
 * cyan leading-edge accents flush inside the front recessed shroud along any of the 6
 * {@link MobFanBlock#FACING} orientations.</p>
 */
public final class MobFanRenderer implements BlockEntityRenderer<MobFanBlockEntity> {
    public MobFanRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(MobFanBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(MobFanBlock.LIT) || !state.getValue(MobFanBlock.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(MobFanBlock.FACING) ? state.getValue(MobFanBlock.FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "mob_fan",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.00F), 0.20F);
        float deg = LivingFactoryAnimLogic.mobFanBladeDeg(tSec);

        // Pass 1: 4 metallic angled turbine blades spinning inside front recess (z = 0.02..0.095)
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_CASING));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        HeroRenderPrimitives.alignLocalNorthToFacing(pose, facing);
        pose.translate(0.0D, 0.0D, -0.445D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));

        // Central rotor spinner cap
        Matrix4f hubM = pose.last().pose();
        HeroRenderPrimitives.box(steel, hubM,
                -0.11F, -0.11F, -0.045F,
                0.11F, 0.11F, 0.035F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);

        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(18.0F));
            Matrix4f bm = pose.last().pose();
            HeroRenderPrimitives.box(steel, bm,
                    0.07F, -0.055F, -0.018F,
                    0.355F, 0.055F, 0.018F,
                    0.82F, 0.88F, 0.96F, 1.0F, packedLight);
            pose.popPose();
        }
        pose.popPose();

        // Pass 2: Glowing cyan leading-edge airflow blade tips & active rotor core
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        HeroRenderPrimitives.alignLocalNorthToFacing(pose, facing);
        pose.translate(0.0D, 0.0D, -0.455D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));

        Matrix4f chm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, chm,
                -0.065F, -0.065F, -0.042F,
                0.065F, 0.065F, 0.010F,
                0.30F, 0.96F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);

        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(18.0F));
            Matrix4f bm = pose.last().pose();
            HeroRenderPrimitives.box(cyan, bm,
                    0.26F, 0.025F, -0.022F,
                    0.365F, 0.062F, 0.022F,
                    0.34F, 0.96F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }
}
