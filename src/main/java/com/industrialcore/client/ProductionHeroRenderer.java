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
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 111 Tier 2 Hero BER: Production, Mining &amp; Synthesis machines
 * ({@code assembler}, {@code quantum_compressor}, {@code reconstructor}, {@code quarry},
 * {@code large_quarry}, {@code ore_synthesizer}, {@code phase_reactor}, {@code matter_amplifier}).
 *
 * <p>Zero per-frame allocations, strict {@link LivingFactoryAnimLogic#BER_VIEW_DISTANCE} (36m)
 * distance culling, immediate early-return when {@code lit=false}, and 0-frame phase-locked
 * {@link FactoryAudioController} triggers.</p>
 */
public final class ProductionHeroRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public enum Kind {
        ASSEMBLER,
        QUANTUM_COMPRESSOR,
        RECONSTRUCTOR,
        QUARRY,
        LARGE_QUARRY,
        ORE_SYNTHESIZER,
        PHASE_REACTOR,
        MATTER_AMPLIFIER
    }

    private final Kind kind;

    public ProductionHeroRenderer(BlockEntityRendererProvider.Context ctx, Kind kind) {
        this.kind = kind;
    }

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.25D, p.getY(), p.getZ() - 0.25D,
                p.getX() + 1.25D, p.getY() + 1.6D, p.getZ() + 1.25D);
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
            case ASSEMBLER -> renderAssembler(pos, facing, tSec, pose, buffers, packedLight);
            case QUANTUM_COMPRESSOR -> renderQuantumCompressor(pos, facing, tSec, pose, buffers, packedLight);
            case RECONSTRUCTOR -> renderReconstructor(pos, tSec, pose, buffers);
            case QUARRY -> renderQuarry(pos, tSec, pose, buffers, false);
            case LARGE_QUARRY -> renderQuarry(pos, tSec, pose, buffers, true);
            case ORE_SYNTHESIZER -> renderOreSynthesizer(pos, tSec, pose, buffers);
            case PHASE_REACTOR -> renderPhaseReactor(pos, tSec, pose, buffers);
            case MATTER_AMPLIFIER -> renderMatterAmplifier(pos, facing, tSec, pose, buffers);
        }
    }

    private static void renderAssembler(BlockPos pos, Direction facing, float tSec,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "assembler",
                LivingFactoryAnimLogic.cyclePhase(tSec, LivingFactoryAnimLogic.ASSEMBLER_PERIOD_SEC), 0.25F);
        // Phase 117: 2-axis X/Z gantry bridge + articulated multi-jointed robotic welding arm + hologram workpiece
        float headX = 0.50F + LivingFactoryAnimLogic.assemblerHeadOffsetX(tSec);
        float headZ = 0.50F + 0.14F * (float) Math.sin(tSec * 2.15D + 0.8D);
        float elbowDeg = 18.0F * (float) Math.sin(tSec * 3.6D);

        // Pass 1: Gunmetal X/Z gantry rail bridge + sliding servo carriage + vertical laser head
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m, 0.12F, 0.79F, headZ - 0.045F, 0.88F, 0.85F, headZ + 0.045F,
                0.76F, 0.82F, 0.90F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, headX - 0.065F, 0.73F, headZ - 0.065F, headX + 0.065F, 0.86F, headZ + 0.065F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, headX - 0.032F, 0.54F, headZ - 0.032F, headX + 0.032F, 0.62F, headZ + 0.032F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Industrial Amber articulated robotic elbow arm
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        pose.translate(headX, 0.73D, headZ);
        pose.mulPose(Axis.ZP.rotationDegrees(elbowDeg));
        HeroRenderPrimitives.box(amber, pose.last().pose(),
                -0.036F, -0.15F, -0.036F, 0.036F, 0.01F, 0.036F,
                1.00F, 0.92F, 0.74F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Focused cyan precision welding laser beam + rotating holographic workpiece on worktable
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f gm = pose.last().pose();
        HeroRenderPrimitives.box(cyan, gm, headX - 0.014F, 0.31F, headZ - 0.014F, headX + 0.014F, 0.55F, headZ + 0.014F,
                0.38F, 0.98F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, gm, headX - 0.048F, 0.285F, headZ - 0.048F, headX + 0.048F, 0.335F, headZ + 0.048F,
                0.80F, 1.00F, 1.00F, 0.98F, LightTexture.FULL_BRIGHT);
        pose.pushPose();
        pose.translate(0.50D, 0.36D, 0.50D);
        pose.mulPose(Axis.YP.rotationDegrees((tSec * 72.0F) % 360.0F));
        pose.mulPose(Axis.XP.rotationDegrees(22.0F));
        HeroRenderPrimitives.box(cyan, pose.last().pose(), -0.095F, -0.065F, -0.095F, 0.095F, 0.065F, 0.095F,
                0.28F, 0.94F, 1.00F, 0.62F, LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
    }

    private static void renderQuantumCompressor(BlockPos pos, Direction facing, float tSec,
                                                PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float phase = LivingFactoryAnimLogic.cyclePhase(tSec, LivingFactoryAnimLogic.COMPRESSOR_PERIOD_SEC);
        FactoryAudioController.onMotionPhase(pos, "quantum_compressor",
                phase, LivingFactoryAnimLogic.COMPRESSOR_PEAK_PHASE);
        float stroke = LivingFactoryAnimLogic.quantumCompressorStroke01(tSec);
        boolean atPeak = stroke >= 0.86F;
        // Phase 117: Opposing upper & lower compression pistons converging toward center y=0.50!
        float upperBotY = 0.66F - stroke * 0.14F;
        float lowerTopY = 0.34F + stroke * 0.14F;

        // Pass 1: Gunmetal opposing upper & lower hydraulic compression rams inside open 4-pillar chamber
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m, 0.41F, upperBotY + 0.07F, 0.41F, 0.59F, 0.82F, 0.59F,
                0.76F, 0.82F, 0.90F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.30F, upperBotY, 0.30F, 0.70F, upperBotY + 0.075F, 0.70F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.41F, 0.18F, 0.41F, 0.59F, lowerTopY - 0.07F, 0.59F,
                0.76F, 0.82F, 0.90F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.30F, lowerTopY - 0.075F, 0.30F, 0.70F, lowerTopY, 0.70F,
                0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Spinning purple magnetic containment ring around chamber midpoint
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.5D, 0.50D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees((tSec * 145.0F) % 360.0F));
        pose.mulPose(Axis.XP.rotationDegrees(16.0F));
        HeroRenderPrimitives.energyRingXZ(pose, purple, 0.30F, 0.024F, 0.022F,
                0.84F, 0.36F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Cyan quantum convergence core + bursting shockwave ring at peak compression!
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.50D, 0.5D);
        float coreR = atPeak ? 0.115F : (0.065F + stroke * 0.04F);
        HeroRenderPrimitives.box(cyan, pose.last().pose(), -coreR, -coreR, -coreR, coreR, coreR, coreR,
                0.55F, 0.98F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        float shockR = atPeak ? 0.44F : (0.24F + stroke * 0.10F);
        HeroRenderPrimitives.energyRingXZ(pose, cyan, shockR, 0.028F, 0.024F,
                0.30F, 0.96F, 1.00F, atPeak ? 0.98F : 0.65F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderReconstructor(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "reconstructor",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.50F), 0.15F);
        float deg = LivingFactoryAnimLogic.reconstructorRingDeg(tSec);
        float rad = LivingFactoryAnimLogic.reconstructorRadius(tSec);

        // Pass 1: Converging amber reconstruction ring wrapping the open cage
        VertexConsumer orange = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.58D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(14.0F));
        HeroRenderPrimitives.energyRingXZ(pose, orange, rad + 0.10F, 0.024F, 0.024F,
                1.00F, 0.76F, 0.26F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Counter-rotating cyan holo-convergence ring + top emitter halo
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.58D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.4F));
        pose.mulPose(Axis.ZP.rotationDegrees(18.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, rad + 0.06F, 0.022F, 0.022F,
                0.30F, 0.96F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 1.03D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg * 0.8F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.24F, 0.022F, 0.020F,
                0.34F, 0.96F, 1.00F, 0.86F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderQuarry(BlockPos pos, float tSec, PoseStack pose,
                                     MultiBufferSource buffers, boolean large) {
        FactoryAudioController.onMotionPhase(pos, large ? "large_quarry" : "quarry",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.40F), 0.20F);
        float deg = LivingFactoryAnimLogic.quarryRingDeg(tSec);
        float pulse = LivingFactoryAnimLogic.quarryLaserPulse(tSec);

        // Pass 1: Spinning top cyan mining emitter ring + vertical pulsing laser focus column
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.07D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, large ? 0.36F : 0.30F, 0.026F, 0.024F,
                0.28F, 0.96F * pulse, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pulsing laser focus spire above emitter
        pose.pushPose();
        pose.translate(0.5D, 1.00D, 0.5D);
        Matrix4f m = pose.last().pose();
        float beamR = (large ? 0.055F : 0.040F) * pulse;
        float beamH = large ? 0.38F : 0.28F;
        HeroRenderPrimitives.box(cyan, m, -beamR, 0.0F, -beamR, beamR, beamH, beamR,
                0.42F, 0.98F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        if (large) {
            VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
            pose.pushPose();
            pose.translate(0.5D, 1.18D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.35F));
            pose.mulPose(Axis.XP.rotationDegrees(16.0F));
            HeroRenderPrimitives.energyRingXZ(pose, purple, 0.24F, 0.022F, 0.020F,
                    0.82F, 0.44F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    private static void renderOreSynthesizer(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "ore_synthesizer",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.50F), 0.20F);
        float deg = LivingFactoryAnimLogic.oreSynthesizerRingDeg(tSec);

        // Pass 1: Orbiting cyan synthesis ring above top emitter disc
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.11D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(18.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.34F, 0.026F, 0.024F,
                0.28F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Floating golden-amber synthesized ore crystal inside the ring
        VertexConsumer gold = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 1.14D + 0.03D * Math.sin(tSec * 3.0D), 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.3F));
        pose.mulPose(Axis.XP.rotationDegrees(35.264F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(gold, m, -0.095F, -0.095F, -0.095F, 0.095F, 0.095F, 0.095F,
                1.00F, 0.84F, 0.30F, 0.95F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderPhaseReactor(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "phase_reactor",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.60F), 0.20F);
        float deg = LivingFactoryAnimLogic.phaseReactorRingDeg(tSec);

        // Pass 1: Tilted rotating purple phase ring orbiting top purple dome
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.5D, 1.10D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(26.0F));
        HeroRenderPrimitives.energyRingXZ(pose, purple, 0.35F, 0.028F, 0.026F,
                0.88F, 0.42F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Counter-rotating cyan inner stabilizer halo
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.10D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.6F));
        pose.mulPose(Axis.ZP.rotationDegrees(22.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.22F, 0.022F, 0.020F,
                0.34F, 0.96F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderMatterAmplifier(BlockPos pos, Direction facing, float tSec,
                                              PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "matter_amplifier",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.50F), 0.25F);
        float px = LivingFactoryAnimLogic.matterAmplifierPulseX(tSec);

        // Pass 1: Oscillating violet matter-energy pulse travelling between left & right capacitor pylons
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        // Horizontal energy conduit beam across top
        HeroRenderPrimitives.box(purple, m, 0.06F, 1.055F, 0.475F, 0.94F, 1.105F, 0.525F,
                0.86F, 0.38F, 1.00F, 0.72F, LightTexture.FULL_BRIGHT);
        // Travelling amplified pulse node
        HeroRenderPrimitives.box(purple, m,
                0.50F + px - 0.085F, 1.01F, 0.415F,
                0.50F + px + 0.085F, 1.15F, 0.585F,
                0.96F, 0.56F, 1.00F, 0.95F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 2: Pulsing cyan vertical resonance rings on left & right side capacitor pylons
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f cm = pose.last().pose();
        float sideGlowY = 0.50F + px * 0.42F;
        HeroRenderPrimitives.box(cyan, cm, 0.015F, sideGlowY - 0.05F, 0.30F, 0.055F, sideGlowY + 0.05F, 0.70F,
                0.32F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, cm, 0.945F, 1.00F - sideGlowY - 0.05F, 0.30F, 0.985F, 1.00F - sideGlowY + 0.05F, 0.70F,
                0.32F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
