package com.industrialcore.client;

import com.industrialcore.block.NuclearGeneratorBlock;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
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
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 109 / 117 Tier 2 Hero BER: Generator Flagships registered on
 * {@code ModBlockEntities.GENERATION} ({@code wind_generator}, {@code hydro_generator},
 * {@code thermal_generator}, {@code nuclear_generator}).
 *
 * <p>Zero per-frame allocations, strictly sequential {@link RenderType} passes, and
 * {@link LivingFactoryAnimLogic#BER_VIEW_DISTANCE} (36m) distance culling.</p>
 */
public final class NuclearReactorRenderer implements BlockEntityRenderer<GenerationBlockEntity> {
    private static final float[][] ROD_OFFSETS_LARGE = {
            {-0.82F, -0.82F}, {0.82F, -0.82F}, {-0.82F, 0.82F}, {0.82F, 0.82F}
    };
    private static final float[][] ROD_OFFSETS_COMPACT = {
            {-0.55F, -0.55F}, {0.55F, -0.55F}, {-0.55F, 0.55F}, {0.55F, 0.55F}
    };

    public NuclearReactorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(GenerationBlockEntity be) {
        if (be.kind() == GenerationKind.WIND) {
            BlockPos p = be.getBlockPos();
            return new AABB(p.getX() - 1.5D, p.getY(), p.getZ() - 1.5D,
                    p.getX() + 2.5D, p.getY() + 2.6D, p.getZ() + 2.5D);
        }
        if (be.kind() == GenerationKind.THERMAL || be.kind() == GenerationKind.HYDRO
                || be.kind() == GenerationKind.COMBUSTION) {
            BlockPos p = be.getBlockPos();
            return new AABB(p.getX() - 0.3D, p.getY(), p.getZ() - 0.3D,
                    p.getX() + 1.3D, p.getY() + 1.45D, p.getZ() + 1.3D);
        }
        if (be.kind() != GenerationKind.NUCLEAR) {
            return new AABB(be.getBlockPos());
        }
        IntPos min = be.getClientMinPos();
        IntPos max = be.getClientMaxPos();
        if (min != null && max != null) {
            return new AABB(min.x(), min.y(), min.z(), max.x() + 1.0D, max.y() + 1.0D, max.z() + 1.0D);
        }
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 5.0D, p.getY() - 5.0D, p.getZ() - 5.0D,
                p.getX() + 6.0D, p.getY() + 6.0D, p.getZ() + 6.0D);
    }

    @Override
    public void render(GenerationBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        GenerationKind kind = be.kind();
        if (kind == GenerationKind.WIND) {
            renderWindTurbine(be, pose, buffers, packedLight);
            return;
        }
        if (kind == GenerationKind.HYDRO) {
            renderHydroTurbine(be, pose, buffers, packedLight);
            return;
        }
        if (kind == GenerationKind.THERMAL) {
            renderThermalBoiler(be, pose, buffers, packedLight);
            return;
        }
        if (kind == GenerationKind.COMBUSTION) {
            renderCombustionEngine(be, pose, buffers, packedLight);
            return;
        }
        if (kind != GenerationKind.NUCLEAR || !be.isMultiblockFormed()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(NuclearGeneratorBlock.LIT) || !state.getValue(NuclearGeneratorBlock.LIT)) {
            return;
        }
        IntPos min = be.getClientMinPos();
        IntPos max = be.getClientMaxPos();
        if (min == null || max == null) {
            return;
        }

        BlockPos origin = be.getBlockPos();
        double cx = (min.x() + max.x() + 1.0D) * 0.5D - origin.getX();
        double cy = (min.y() + max.y() + 1.0D) * 0.5D - origin.getY();
        double cz = (min.z() + max.z() + 1.0D) * 0.5D - origin.getZ();

        int intW = Math.max(1, (max.x() - min.x() + 1) - 2);
        int intH = Math.max(1, (max.y() - min.y() + 1) - 2);
        int intD = Math.max(1, (max.z() - min.z() + 1) - 2);
        boolean largeChamber = Math.min(intW, intD) >= 3;

        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        boolean overdrive = be.isClientOverdrive();
        float speedMult = com.industrialcore.reactor.ReactorOverdriveLogic.ringSpeedMultiplier(overdrive);
        float glowBoost = com.industrialcore.reactor.ReactorOverdriveLogic.glowPulseBoost(overdrive);
        float animT = tSec * speedMult;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "nuclear_generator",
                LivingFactoryAnimLogic.cyclePhase(tSec, overdrive ? 1.60F : 2.80F), 0.10F);
        float bobY = LivingFactoryAnimLogic.nuclearCoreBobY(animT) * (largeChamber ? 1.35F : 0.70F);
        float glow = Math.min(1.0F, LivingFactoryAnimLogic.nuclearGlowPulse(animT) * glowBoost);

        float rodHalfH = largeChamber ? Math.min(1.18F, intH * 0.39F) : 0.34F;
        float rodThick = largeChamber ? 0.14F : 0.055F;
        float[][] offsets = largeChamber ? ROD_OFFSETS_LARGE : ROD_OFFSETS_COMPACT;

        // Pass 1: Reciprocating Boron-Carbide Control-Rod Clusters & armored fuel end-caps
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(cx, cy + bobY, cz);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < offsets.length; i++) {
            float rx = offsets[i][0];
            float rz = offsets[i][1];
            float capR = rodThick + 0.032F;
            HeroRenderPrimitives.box(steel, m,
                    rx - capR, rodHalfH - 0.08F, rz - capR,
                    rx + capR, rodHalfH + 0.06F, rz + capR,
                    0.82F, 0.88F, 0.94F, 1.0F, LightTexture.FULL_BRIGHT);
            HeroRenderPrimitives.box(steel, m,
                    rx - capR, -rodHalfH - 0.06F, rz - capR,
                    rx + capR, -rodHalfH + 0.08F, rz + capR,
                    0.82F, 0.88F, 0.94F, 1.0F, LightTexture.FULL_BRIGHT);
            // Independent reciprocating control-rod actuator plunging from top header
            float ctrlShift = 0.14F * (float) Math.sin(animT * 1.9D + i * 1.57D);
            float cr = rodThick * 0.55F;
            HeroRenderPrimitives.box(steel, m,
                    rx - cr, rodHalfH + 0.05F + ctrlShift, rz - cr,
                    rx + cr, rodHalfH + 0.32F + ctrlShift, rz + cr,
                    0.46F, 0.52F, 0.62F, 1.0F, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();

        // Pass 2: Glowing emerald Uranium Fuel Rods + moderator ring
        VertexConsumer green = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_GREEN));
        pose.pushPose();
        pose.translate(cx, cy + bobY, cz);
        Matrix4f gm = pose.last().pose();
        for (int i = 0; i < offsets.length; i++) {
            float rx = offsets[i][0];
            float rz = offsets[i][1];
            HeroRenderPrimitives.box(green, gm,
                    rx - rodThick, -rodHalfH + 0.06F, rz - rodThick,
                    rx + rodThick, rodHalfH - 0.06F, rz + rodThick,
                    0.26F, 1.00F * glow, 0.44F, 0.94F, LightTexture.FULL_BRIGHT);
        }
        float ringR = largeChamber ? 0.94F : 0.565F;
        HeroRenderPrimitives.energyRingXZ(pose, green, ringR, 0.045F, 0.055F,
                0.32F, 1.00F * glow, 0.52F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Multi-axis counter-rotating Cyan Magnetic Stabilizer Rings (orange-gold in Overdrive!)
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(
                overdrive ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_CYAN));
        float coolR = largeChamber ? 1.12F : 0.60F;
        pose.pushPose();
        pose.translate(cx, cy - bobY * 0.55F, cz);
        pose.mulPose(Axis.YP.rotationDegrees((animT * 38.0F) % 360.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, coolR, 0.038F, 0.065F,
                overdrive ? 1.00F : 0.22F, overdrive ? 0.65F * glow : 0.94F * glow,
                overdrive ? 0.25F : 1.00F, overdrive ? 0.88F : 0.72F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.XP.rotationDegrees(32.0F));
        pose.mulPose(Axis.YP.rotationDegrees((-animT * 52.0F) % 360.0F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, coolR * 0.92F, 0.032F, 0.042F,
                overdrive ? 1.00F : 0.34F, overdrive ? 0.78F * glow : 0.98F * glow,
                overdrive ? 0.30F : 1.00F, overdrive ? 0.84F : 0.64F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 117 Flagship #6: Wind Generator ({@code wind_generator}) sculpted 3-blade airfoil rotor
     * with tapered nose spinner cone and glowing cyan wingtip navigation beacons.
     */
    private static void renderWindTurbine(GenerationBlockEntity be, PoseStack pose,
                                          MultiBufferSource buffers, int packedLight) {
        int windOut = be.getClientWindOutput();
        if (windOut <= 0) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(com.industrialcore.block.GenerationBlock.FACING)
                ? state.getValue(com.industrialcore.block.GenerationBlock.FACING)
                : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "wind_generator",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.40F), 0.10F);
        float deg = LivingFactoryAnimLogic.windTurbineBladeDeg(tSec, windOut);

        // Pass 1: Tapered spinner cone + 3 multi-segment airfoil-tapered aerodynamic blades
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 1.14D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.0D, -0.63D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));

        Matrix4f hm = pose.last().pose();
        HeroRenderPrimitives.box(steel, hm, -0.10F, -0.10F, -0.055F, 0.10F, 0.10F, 0.055F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, hm, -0.065F, -0.065F, -0.115F, 0.065F, 0.065F, -0.055F,
                0.92F, 0.96F, 1.00F, 1.0F, packedLight);

        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 120.0F));
            pose.mulPose(Axis.YP.rotationDegrees(16.0F));
            Matrix4f bm = pose.last().pose();
            // Broad root section
            HeroRenderPrimitives.box(steel, bm, -0.068F, 0.08F, -0.022F, 0.068F, 0.34F, 0.022F,
                    0.82F, 0.88F, 0.94F, 1.0F, packedLight);
            // Tapered mid-span airfoil
            HeroRenderPrimitives.box(steel, bm, -0.052F, 0.34F, -0.017F, 0.052F, 0.62F, 0.017F,
                    0.88F, 0.93F, 0.98F, 1.0F, packedLight);
            // Slender high-speed outer tip
            HeroRenderPrimitives.box(steel, bm, -0.036F, 0.62F, -0.013F, 0.036F, 0.84F, 0.013F,
                    0.94F, 0.97F, 1.00F, 1.0F, packedLight);
            pose.popPose();
        }
        pose.popPose();

        // Pass 2: Glowing cyan wingtip navigation lights & spinner nose beacon
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 1.14D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.0D, -0.635D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));

        HeroRenderPrimitives.box(cyan, pose.last().pose(), -0.045F, -0.045F, -0.130F, 0.045F, 0.045F, -0.090F,
                0.34F, 0.98F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);

        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 120.0F));
            pose.mulPose(Axis.YP.rotationDegrees(16.0F));
            HeroRenderPrimitives.box(cyan, pose.last().pose(), -0.042F, 0.72F, -0.018F, 0.042F, 0.86F, 0.018F,
                    0.32F, 0.98F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * Phase 117 Flagship #7: Hydro Generator ({@code hydro_generator}) 6-vane Francis waterwheel
     * turbine + vertical drive shaft spinning inside the glass casing.
     */
    private static void renderHydroTurbine(GenerationBlockEntity be, PoseStack pose,
                                           MultiBufferSource buffers, int packedLight) {
        if (!be.isClientRunning()) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(com.industrialcore.block.GenerationBlock.FACING)
                ? state.getValue(com.industrialcore.block.GenerationBlock.FACING)
                : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "hydro_generator",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.20F), 0.20F);
        float deg = LivingFactoryAnimLogic.hydroTurbineDeg(tSec, true);

        // Pass 1: Vertical drive shaft + 6-vane Francis turbine rotor visible in front viewport & crown
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.02D, -0.515D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        Matrix4f m = pose.last().pose();
        HeroRenderPrimitives.box(steel, m, -0.075F, -0.075F, -0.028F, 0.075F, 0.075F, 0.025F,
                0.86F, 0.92F, 0.98F, 1.0F, packedLight);
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 60.0F));
            pose.mulPose(Axis.XP.rotationDegrees(18.0F));
            HeroRenderPrimitives.box(steel, pose.last().pose(), 0.055F, -0.036F, -0.016F, 0.27F, 0.036F, 0.016F,
                    0.80F, 0.88F, 0.96F, 1.0F, packedLight);
            pose.popPose();
        }
        pose.popPose();

        // Top vertical drive shaft coupling spinning above turbine housing
        pose.pushPose();
        pose.translate(0.5D, 1.01D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        HeroRenderPrimitives.box(steel, pose.last().pose(), -0.065F, -0.03F, -0.065F, 0.065F, 0.08F, 0.065F,
                0.88F, 0.94F, 1.00F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Glowing cyan hydrodynamic vane tips & twin counter-spinning flow rings
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.0D, 0.02D, -0.522D);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 60.0F));
            HeroRenderPrimitives.box(cyan, pose.last().pose(), 0.18F, -0.042F, -0.020F, 0.285F, 0.042F, 0.020F,
                    0.26F, 0.96F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 1.035D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 0.85F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, 0.27F, 0.024F, 0.020F,
                0.24F, 0.94F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 117 Flagship #8: Thermal Boiler Generator ({@code thermal_generator}) flickering
     * incandescent firebox flame core + external rotating pressure flywheel &amp; amber steam valve.
     */
    private static void renderThermalBoiler(GenerationBlockEntity be, PoseStack pose,
                                            MultiBufferSource buffers, int packedLight) {
        if (!be.isClientRunning()) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(com.industrialcore.block.GenerationBlock.FACING)
                ? state.getValue(com.industrialcore.block.GenerationBlock.FACING)
                : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "thermal_generator",
                LivingFactoryAnimLogic.cyclePhase(tSec, 1.60F), 0.15F);
        float wheelDeg = (tSec * 220.0F) % 360.0F;
        float valveDeg = (tSec * 95.0F) % 360.0F;
        float flamePulse = 0.85F + 0.15F * (float) Math.sin(tSec * 14.0D);

        // Pass 1: Gunmetal external side pressure flywheel on RIGHT (+X) face
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.518D, -0.02D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(wheelDeg));
        Matrix4f wm = pose.last().pose();
        HeroRenderPrimitives.box(steel, wm, -0.022F, -0.22F, -0.042F, 0.028F, 0.22F, 0.042F,
                0.84F, 0.88F, 0.94F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, wm, -0.022F, -0.042F, -0.22F, 0.028F, 0.042F, 0.22F,
                0.84F, 0.88F, 0.94F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, wm, -0.030F, -0.075F, -0.075F, 0.036F, 0.075F, 0.075F,
                0.92F, 0.95F, 1.00F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Industrial Amber spoked steam pressure valve on LEFT (-X) face
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.518D, 0.06D, -0.04D);
        pose.mulPose(Axis.XP.rotationDegrees(-valveDeg));
        Matrix4f vm = pose.last().pose();
        HeroRenderPrimitives.box(amber, vm, -0.028F, -0.15F, -0.032F, 0.022F, 0.15F, 0.032F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, vm, -0.028F, -0.032F, -0.15F, 0.022F, 0.032F, 0.15F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Flickering incandescent firebox flame core visible through front firebox window
        VertexConsumer fire = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        Matrix4f fm = pose.last().pose();
        float fh = 0.16F * flamePulse;
        HeroRenderPrimitives.box(fire, fm, -0.17F, -0.16F, -0.512F, 0.17F, -0.16F + fh * 2.0F, -0.496F,
                1.00F, 0.58F * flamePulse, 0.18F, 0.92F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(fire, fm, -0.10F, -0.14F, -0.516F, 0.10F, -0.14F + fh * 1.4F, -0.502F,
                1.00F, 0.90F, 0.42F, 0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * Phase 118: Combustion Engine Generator ({@code combustion_generator}) twin 180-degree
     * opposing reciprocating cylinder pistons, spinning crankshaft flywheel, and TDC ignition glow.
     */
    private static void renderCombustionEngine(GenerationBlockEntity be, PoseStack pose,
                                               MultiBufferSource buffers, int packedLight) {
        if (!be.isClientRunning()) {
            return;
        }
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(com.industrialcore.block.GenerationBlock.FACING)
                ? state.getValue(com.industrialcore.block.GenerationBlock.FACING)
                : Direction.NORTH;
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        FactoryAudioController.onMotionPhase(be.getBlockPos(), "combustion_generator",
                LivingFactoryAnimLogic.cyclePhase(tSec, 0.90F), 0.16F);

        double omega = tSec * 16.0D;
        float strokeA = (float) (0.5D + 0.5D * Math.sin(omega));       // Cylinder 1 (0..1)
        float strokeB = (float) (0.5D + 0.5D * Math.sin(omega + Math.PI)); // Cylinder 2 (180 deg offset)
        float crankDeg = (tSec * 420.0F) % 360.0F;

        // Pass 1: Twin reciprocating gunmetal piston crowns atop cylinder head + spinning side crankshaft disc
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f sm = pose.last().pose();
        float pyA = 0.98F + strokeA * 0.14F;
        float pyB = 0.98F + strokeB * 0.14F;
        HeroRenderPrimitives.box(steel, sm, 0.25F, pyA, 0.36F, 0.45F, pyA + 0.075F, 0.56F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, sm, 0.55F, pyB, 0.36F, 0.75F, pyB + 0.075F, 0.56F,
                0.86F, 0.90F, 0.96F, 1.0F, packedLight);
        pose.popPose();

        // Spinning side crankshaft flywheel on right (+X) face
        pose.pushPose();
        pose.translate(0.5D, 0.44D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(0.515D, 0.0D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(crankDeg));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(steel, cm, -0.022F, -0.19F, -0.038F, 0.026F, 0.19F, 0.038F,
                0.84F, 0.88F, 0.94F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, cm, -0.022F, -0.038F, -0.19F, 0.026F, 0.038F, 0.19F,
                0.84F, 0.88F, 0.94F, 1.0F, packedLight);
        pose.popPose();

        // Pass 2: Industrial Amber piston pushrods driving each cylinder crown
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f am = pose.last().pose();
        HeroRenderPrimitives.box(amber, am, 0.31F, 0.90F, 0.42F, 0.39F, pyA, 0.50F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am, 0.61F, 0.90F, 0.42F, 0.69F, pyB, 0.50F,
                1.00F, 0.92F, 0.72F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Twin incandescent TDC ignition flashes at cylinder ports
        VertexConsumer fire = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(HeroRenderPrimitives.horizontalRotationDeg(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);
        Matrix4f fm = pose.last().pose();
        if (strokeA > 0.65F) {
            HeroRenderPrimitives.box(fire, fm, 0.27F, 0.94F, 0.38F, 0.43F, 0.99F, 0.54F,
                    1.00F, 0.72F, 0.22F, 0.90F, LightTexture.FULL_BRIGHT);
        }
        if (strokeB > 0.65F) {
            HeroRenderPrimitives.box(fire, fm, 0.57F, 0.94F, 0.38F, 0.73F, 0.99F, 0.54F,
                    1.00F, 0.72F, 0.22F, 0.90F, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
