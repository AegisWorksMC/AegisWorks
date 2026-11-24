package com.industrialcore.client;

import com.industrialcore.block.ProcessingBlock;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.ProcessingKind;
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
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Phase 109 / 117 Tier 2 Hero BER: Processing Flagships registered on
 * {@code ModBlockEntities.PROCESSING} ({@code metal_press}, {@code crusher},
 * {@code precision_crusher}, {@code fast_crusher}, {@code electric_furnace}).
 *
 * <p>Zero per-frame allocations, strictly sequential {@link RenderType} passes, and
 * {@link LivingFactoryAnimLogic#BER_VIEW_DISTANCE} (36m) distance culling.</p>
 */
public final class CrusherRollerRenderer implements BlockEntityRenderer<ProcessingBlockEntity> {
    public CrusherRollerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public void render(ProcessingBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ProcessingKind kind = be.getKind();
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ProcessingBlock.LIT) || !state.getValue(ProcessingBlock.LIT)) {
            return;
        }
        Direction facing = state.hasProperty(ProcessingBlock.FACING)
                ? state.getValue(ProcessingBlock.FACING) : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;

        if (kind == ProcessingKind.METAL_PRESS) {
            renderMetalPress(be.getBlockPos(), facing, tSec, pose, buffers, packedLight);
            return;
        }
        if (kind == ProcessingKind.ELECTRIC_FURNACE) {
            renderInductionFurnaceCore(be.getBlockPos(), "electric_furnace", facing, tSec, false, pose, buffers, packedLight);
            return;
        }
        if (kind == ProcessingKind.ELECTROLYZER) {
            renderElectrolyzer(be.getBlockPos(), facing, tSec, pose, buffers, packedLight);
            return;
        }
        if (kind != ProcessingKind.CRUSHER
                && kind != ProcessingKind.FAST_CRUSHER
                && kind != ProcessingKind.PRECISION_CRUSHER) {
            return;
        }

        String crusherId = switch (kind) {
            case FAST_CRUSHER -> "fast_crusher";
            case PRECISION_CRUSHER -> "precision_crusher";
            default -> "crusher";
        };
        boolean precision = (kind == ProcessingKind.PRECISION_CRUSHER);
        FactoryAudioController.onMotionPhase(be.getBlockPos(), crusherId,
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.20F), 0.15F);
        float degA = LivingFactoryAnimLogic.crusherRollerPrimaryDeg(tSec);
        float degB = LivingFactoryAnimLogic.crusherRollerCounterDeg(tSec);

        // Pass 1: Twin counter-rotating meshed toothed crusher rollers inside the open glass bay
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        // Primary & counter-rotating twin rollers inside chamber (y=0.53, z=0.35 & z=0.63)
        renderToothedRoller(pose, steel, 0.50F, 0.53F, 0.35F, 0.28F, 0.115F, degA, packedLight);
        renderToothedRoller(pose, steel, 0.50F, 0.53F, 0.63F, 0.28F, 0.115F, degB, packedLight);
        // Upper hopper pre-crusher rollers visible through open top funnel (y=0.84)
        renderToothedRoller(pose, steel, 0.50F, 0.84F, 0.40F, 0.24F, 0.072F, degA * 1.25F, packedLight);
        renderToothedRoller(pose, steel, 0.50F, 0.84F, 0.60F, 0.24F, 0.072F, degB * 1.25F, packedLight);
        pose.popPose();

        // Pass 2: Industrial amber spur-gear drive discs on roller ends
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        renderGearDisc(pose, amber, 0.21F, 0.53F, 0.35F, 0.125F, degA, packedLight);
        renderGearDisc(pose, amber, 0.79F, 0.53F, 0.63F, 0.125F, degB, packedLight);
        pose.popPose();

        // Pass 3: Glowing friction tooth highlights (amber for standard, neon cyan for precision)
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(
                precision ? HeroRenderPrimitives.TEX_GLOW_CYAN : HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        float gr = precision ? 0.28F : 1.00F;
        float gg = precision ? 0.96F : 0.72F;
        float gb = precision ? 1.00F : 0.22F;
        renderRollerGlowTeeth(pose, glow, 0.50F, 0.53F, 0.35F, 0.25F, 0.122F, degA, gr, gg, gb);
        renderRollerGlowTeeth(pose, glow, 0.50F, 0.53F, 0.63F, 0.25F, 0.122F, degB, gr, gg, gb);
        pose.popPose();
    }

    private static void renderToothedRoller(PoseStack pose, VertexConsumer vc,
                                            float cx, float cy, float cz,
                                            float halfLenX, float radius, float angleDeg, int light) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(angleDeg));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(vc, m,
                -halfLenX, -radius * 0.68F, -radius * 0.68F,
                halfLenX, radius * 0.68F, radius * 0.68F,
                0.78F, 0.84F, 0.92F, 1.0F, light);
        // 4 interlocking tooth bars
        HeroRenderPrimitives.box(vc, m,
                -halfLenX * 0.92F, -radius, -radius * 0.26F,
                halfLenX * 0.92F, radius, radius * 0.26F,
                0.92F, 0.96F, 1.00F, 1.0F, light);
        HeroRenderPrimitives.box(vc, m,
                -halfLenX * 0.92F, -radius * 0.26F, -radius,
                halfLenX * 0.92F, radius * 0.26F, radius,
                0.92F, 0.96F, 1.00F, 1.0F, light);
        pose.popPose();
    }

    private static void renderGearDisc(PoseStack pose, VertexConsumer vc,
                                       float cx, float cy, float cz, float r, float angleDeg, int light) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(angleDeg));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(vc, m, -0.025F, -r, -r * 0.45F, 0.025F, r, r * 0.45F, 1.0F, 0.92F, 0.72F, 1.0F, light);
        HeroRenderPrimitives.box(vc, m, -0.025F, -r * 0.45F, -r, 0.025F, r * 0.45F, r, 1.0F, 0.92F, 0.72F, 1.0F, light);
        pose.popPose();
    }

    private static void renderRollerGlowTeeth(PoseStack pose, VertexConsumer vc,
                                              float cx, float cy, float cz,
                                              float halfLenX, float radius, float angleDeg,
                                              float r, float g, float b) {
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(angleDeg + 45.0F));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(vc, m,
                -halfLenX, -radius, -0.016F,
                halfLenX, radius, 0.016F,
                r, g, b, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 117 Flagship #1: Hydraulic Metal Press ({@code metal_press}).
     * Multi-stage telescoping hydraulic cylinder + industrial amber heavy stamp head plunging cleanly
     * into the open C-frame cavity onto the anvil (y=0.281) with an expanding shockwave ring on impact!
     */
    private static void renderMetalPress(BlockPos pos, Direction facing, float tSec,
                                         PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float phase = LivingFactoryAnimLogic.cyclePhase(tSec, LivingFactoryAnimLogic.METAL_PRESS_PERIOD_SEC);
        FactoryAudioController.onMotionPhase(pos, "metal_press",
                phase, LivingFactoryAnimLogic.METAL_PRESS_IMPACT_PHASE);

        float stroke = LivingFactoryAnimLogic.metalPressStroke01(tSec);
        boolean stamping = LivingFactoryAnimLogic.metalPressIsStamping(tSec);
        // Plunges smoothly from y=0.58 down to anvil top y=0.285!
        float dieBottomY = 0.580F - stroke * 0.295F;
        float dieTopY = dieBottomY + 0.125F;
        float midCollarY = 0.785F - stroke * 0.135F;

        // Pass 1: Gunmetal multi-stage telescoping hydraulic cylinder & chrome guide rods
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        // Stage-1 outer hydraulic sleeve
        HeroRenderPrimitives.box(steel, m, 0.37F, midCollarY, 0.29F, 0.63F, 0.84F, 0.55F,
                0.76F, 0.82F, 0.90F, 1.0F, packedLight);
        // Stage-2 inner chrome telescoping piston rod
        HeroRenderPrimitives.box(steel, m, 0.42F, dieTopY, 0.34F, 0.58F, midCollarY + 0.02F, 0.50F,
                0.94F, 0.96F, 1.00F, 1.0F, packedLight);
        // Twin side alignment guide shafts
        HeroRenderPrimitives.box(steel, m, 0.27F, dieTopY, 0.39F, 0.32F, 0.84F, 0.44F,
                0.78F, 0.84F, 0.90F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.68F, dieTopY, 0.39F, 0.73F, 0.84F, 0.44F,
                0.78F, 0.84F, 0.90F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Industrial Amber heavy stamping press head
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        HeroRenderPrimitives.box(amber, pose.last().pose(),
                0.24F, dieBottomY, 0.18F,
                0.76F, dieTopY, 0.62F,
                1.00F, 0.92F, 0.74F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Cyan/amber head telemetry strip + expanding shockwave ring on anvil impact!
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(
                stamping ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f gm = pose.last().pose();
        if (stamping) {
            HeroRenderPrimitives.box(glow, gm, 0.23F, dieBottomY + 0.02F, 0.165F, 0.77F, dieBottomY + 0.06F, 0.185F,
                    1.00F, 0.80F, 0.26F, 0.98F, LightTexture.FULL_BRIGHT);
            pose.pushPose();
            pose.translate(0.50D, 0.295D, 0.40D);
            HeroRenderPrimitives.energyRingXZ(pose, glow, 0.36F, 0.026F, 0.020F,
                    1.00F, 0.74F, 0.22F, 0.92F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        } else {
            HeroRenderPrimitives.box(glow, gm, 0.30F, dieBottomY + 0.03F, 0.168F, 0.70F, dieBottomY + 0.065F, 0.184F,
                    0.26F, 0.94F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    /**
     * Phase 117 Flagship #3: Shared Induction Heating Coil &amp; Molten Crucible Core BER for
     * {@code electric_furnace} and {@code alloy_furnace} inside the deep recessed furnace hearth.
     */
    static void renderInductionFurnaceCore(BlockPos pos, String machineId, Direction facing, float tSec,
                                           boolean alloyGold, PoseStack pose,
                                           MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, machineId,
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.80F), 0.20F);
        float pulse = 0.78F + 0.22F * (float) Math.sin(tSec * 5.2D);
        float coreBob = 0.028F * (float) Math.sin(tSec * 3.4D);

        // Pass 1: Crucible pedestal base & upper induction cap
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m, 0.33F, 0.20F, 0.28F, 0.67F, 0.27F, 0.58F,
                0.72F, 0.78F, 0.86F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, m, 0.36F, 0.74F, 0.31F, 0.64F, 0.81F, 0.55F,
                0.72F, 0.78F, 0.86F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: 3 stacked red-orange to incandescent white-hot pulsing induction heating coils + molten crucible core
        VertexConsumer fire = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);

        // 3 stacked induction coil rings at y=0.34, 0.50, 0.66
        for (int i = 0; i < 3; i++) {
            float cy = 0.34F + i * 0.16F;
            float coilGlow = Math.min(1.0F, 0.72F + 0.28F * (float) Math.sin(tSec * 5.2D + i * 1.1D));
            pose.pushPose();
            pose.translate(0.50D, cy, 0.43D);
            pose.mulPose(Axis.YP.rotationDegrees(tSec * (i % 2 == 0 ? 35.0F : -35.0F)));
            HeroRenderPrimitives.energyRingXZ(pose, fire, 0.21F, 0.024F, 0.024F,
                    1.00F, (alloyGold ? 0.68F : 0.44F) * coilGlow, 0.16F * coilGlow,
                    0.96F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        // Pulsing molten crucible core at center
        Matrix4f fm = pose.last().pose();
        float cr = 0.12F * (0.94F + 0.10F * pulse);
        HeroRenderPrimitives.box(fire, fm,
                0.50F - cr, 0.33F + coreBob, 0.43F - cr,
                0.50F + cr, 0.68F + coreBob, 0.43F + cr,
                1.00F, (alloyGold ? 0.86F : 0.72F) * pulse, 0.34F * pulse,
                0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 118: Electrolyzer ({@code electrolyzer}) twin pulsing electrode plates, cyan high-voltage
     * electrolytic plasma arc, and 6 rising translucent bubbles visible inside the 4-pillar glass tank.
     */
    private static void renderElectrolyzer(BlockPos pos, Direction facing, float tSec,
                                           PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "electrolyzer",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.50F), 0.18F);
        float arcPulse = 0.72F + 0.28F * (float) Math.sin(tSec * 9.0D);
        float plateShift = 0.014F * (float) Math.sin(tSec * 4.2D);

        // Pass 1: Twin reciprocating titanium-amber electrode cores inside the glass electrolysis tank
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f am = pose.last().pose();
        HeroRenderPrimitives.box(amber, am,
                0.31F + plateShift, 0.26F, 0.38F,
                0.37F + plateShift, 0.78F, 0.62F,
                0.96F, 0.88F, 0.68F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am,
                0.63F - plateShift, 0.26F, 0.38F,
                0.69F - plateShift, 0.78F, 0.62F,
                0.96F, 0.88F, 0.68F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: High-voltage cyan plasma arc + glowing electrolyte fluid column + 6 rising micro-bubbles
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f cm = pose.last().pose();

        // Translucent cyan electrolyte bath glow filling lower/mid chamber
        HeroRenderPrimitives.box(cyan, cm, 0.20F, 0.22F, 0.20F, 0.80F, 0.72F, 0.80F,
                0.18F, 0.82F, 0.96F, 0.30F, LightTexture.FULL_BRIGHT);

        // Twin horizontal electrolytic plasma arcs bridging anode <-> cathode
        float arcR = 0.022F * arcPulse;
        HeroRenderPrimitives.box(cyan, cm, 0.36F, 0.44F - arcR, 0.50F - arcR, 0.64F, 0.44F + arcR, 0.50F + arcR,
                0.56F, 0.98F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, cm, 0.36F, 0.60F - arcR, 0.50F - arcR, 0.64F, 0.60F + arcR, 0.50F + arcR,
                0.42F, 0.96F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);

        // 6 rising micro-bubbles looping smoothly from y=0.25 to y=0.80
        for (int i = 0; i < 6; i++) {
            float phase = (tSec * 0.68F + i * 0.1667F) % 1.0F;
            float by = 0.25F + phase * 0.54F;
            float bx = 0.38F + ((i % 3) * 0.12F) + 0.025F * (float) Math.sin(tSec * 4.0D + i * 1.7D);
            float bz = (i < 3) ? 0.34F : 0.60F;
            float br = 0.026F * (1.0F - 0.35F * phase);
            HeroRenderPrimitives.box(cyan, cm, bx - br, by - br, bz - br, bx + br, by + br, bz + br,
                    0.76F, 0.99F, 1.00F, 0.88F * (1.0F - 0.45F * phase), LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
