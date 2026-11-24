package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.multiblock.LargeMachineHelper;
import com.industrialcore.multiblock.LargeMachineLogic.MachineSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Phase 104: Unified multi-block 3D {@link BlockEntityRenderer} for the 5 auto-expanding high-tier
 * machines ({@code mob_duplicator} 2x3x2, {@code warp_pad} 3x1x3, {@code climate_controller} 1x3x1,
 * {@code quantum_storage_core} 1x2x1, {@code suit_modifier} 1x2x1).
 *
 * <p>Overrides {@link #getRenderBoundingBox} to cover the entire expanded footprint with generous
 * margin so large structures never vanish when the master corner leaves the camera frustum.
 * Each {@link RenderType} pass completes all its geometry before switching to the next buffer so
 * Minecraft 1.21.1's {@code MultiBufferSource} never flushes an active {@code BufferBuilder}.</p>
 */
public final class LargeMachineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation TEX_CASING =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/reactor_casing.png");
    private static final ResourceLocation TEX_GLOW_CYAN =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_cyan.png");
    private static final ResourceLocation TEX_GLOW_PURPLE =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_purple.png");
    private static final ResourceLocation TEX_GLOW_GREEN =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_green.png");
    private static final ResourceLocation TEX_GLOW_ORANGE =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "textures/block/machine_glow_orange.png");

    private final MachineSpec spec;

    public LargeMachineRenderer(BlockEntityRendererProvider.Context ctx, MachineSpec spec) {
        this.spec = spec;
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        BlockPos p = blockEntity.getBlockPos();
        return new AABB(p.getX() - 2.0D, p.getY() - 0.5D, p.getZ() - 2.0D,
                p.getX() + 3.0D, p.getY() + 4.0D, p.getZ() + 3.0D);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        Direction facing = LargeMachineHelper.facingOf(state);
        boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        float tSec = (Util.getMillis() % 600_000L) / 1000.0F;
        if (lit || spec == MachineSpec.QUANTUM_STORAGE_CORE) {
            String blockId = switch (spec) {
                case MOB_DUPLICATOR -> "mob_duplicator";
                case WARP_PAD -> "warp_pad";
                case CLIMATE_CONTROLLER -> "climate_controller";
                case QUANTUM_STORAGE_CORE -> "quantum_storage_core";
                case SUIT_MODIFIER -> "suit_modifier";
            };
            FactoryAudioController.onMotionPhase(be.getBlockPos(), blockId,
                    LivingFactoryAnimLogic.cyclePhase(tSec, 2.40F), 0.25F);
        }

        pose.pushPose();
        // Align local space so +X = Facing2D.right, +Z = Facing2D.back, +Y = up
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);

        switch (spec) {
            case MOB_DUPLICATOR -> renderMobDuplicator(pose, buffers, packedLight, lit, tSec);
            case WARP_PAD -> renderWarpPad(pose, buffers, packedLight, lit, tSec);
            case CLIMATE_CONTROLLER -> renderClimateController(pose, buffers, packedLight, lit, tSec);
            case QUANTUM_STORAGE_CORE -> renderQuantumStorageCore(pose, buffers, packedLight, tSec);
            case SUIT_MODIFIER -> renderSuitModifier(pose, buffers, packedLight, lit, tSec);
        }

        pose.popPose();
    }

    private static float rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    // =========================================================================
    // 1. Mob Duplicator (2x3x2) — Glass-Walled Bio-Incubation Tank
    // =========================================================================
    private static void renderMobDuplicator(PoseStack pose, MultiBufferSource buffers,
                                            int light, boolean lit, float t) {
        Matrix4f m = pose.last().pose();
        float[][] cols = {{0.04F, 0.04F}, {1.72F, 0.04F}, {0.04F, 1.72F}, {1.72F, 1.72F}};
        float pulse = 0.78F + 0.22F * (float) Math.sin(t * (lit ? 6.0F : 2.2F));
        float bob = 0.08F * (float) Math.sin(t * 2.4F);

        // Pass 1: Steel armor chassis
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_CASING));
        box(steel, m, 0.02F, 0.00F, 0.02F, 1.98F, 0.34F, 1.98F, 0.85F, 0.90F, 0.96F, 1.0F, light);
        for (float[] c : cols) {
            box(steel, m, c[0], 0.34F, c[1], c[0] + 0.24F, 2.64F, c[1] + 0.24F,
                    0.82F, 0.88F, 0.95F, 1.0F, light);
        }
        // Mid-height reinforcement collar
        box(steel, m, 0.04F, 1.44F, 0.04F, 1.96F, 1.54F, 0.16F, 0.78F, 0.85F, 0.92F, 1.0F, light);
        box(steel, m, 0.04F, 1.44F, 1.84F, 1.96F, 1.54F, 1.96F, 0.78F, 0.85F, 0.92F, 1.0F, light);
        box(steel, m, 0.04F, 1.44F, 0.16F, 0.16F, 1.54F, 1.84F, 0.78F, 0.85F, 0.92F, 1.0F, light);
        box(steel, m, 1.84F, 1.44F, 0.16F, 1.96F, 1.54F, 1.84F, 0.78F, 0.85F, 0.92F, 1.0F, light);
        // Upper cap & inner emitter pedestals
        box(steel, m, 0.02F, 2.64F, 0.02F, 1.98F, 2.98F, 1.98F, 0.88F, 0.92F, 0.98F, 1.0F, light);
        box(steel, m, 0.42F, 0.34F, 0.42F, 1.58F, 0.48F, 1.58F, 0.75F, 0.82F, 0.90F, 1.0F, light);
        box(steel, m, 0.48F, 2.50F, 0.48F, 1.52F, 2.64F, 1.52F, 0.75F, 0.82F, 0.90F, 1.0F, light);

        // Pass 2: Bio-green glowing power conduits, crown beacon, & floating specimen core
        VertexConsumer green = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_GREEN));
        for (float[] c : cols) {
            box(green, m, c[0] + 0.07F, 0.40F, c[1] + 0.07F, c[0] + 0.17F, 2.58F, c[1] + 0.17F,
                    0.45F, 1.00F * pulse, 0.72F, lit ? 0.95F : 0.70F, LightTexture.FULL_BRIGHT);
        }
        box(green, m, 0.56F, 2.98F, 0.56F, 1.44F, 3.04F, 1.44F,
                0.35F, 1.00F * pulse, 0.75F, 0.92F, LightTexture.FULL_BRIGHT);

        pose.pushPose();
        pose.translate(1.0D, 1.48D + bob, 1.0D);
        pose.mulPose(Axis.YP.rotationDegrees(t * (lit ? 95.0F : 35.0F)));
        Matrix4f cm = pose.last().pose();
        box(green, cm, -0.22F, -0.34F, -0.22F, 0.22F, 0.34F, 0.22F,
                0.55F, 1.00F, 0.78F, lit ? 0.92F : 0.76F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Cyan rotating containment ring & translucent bio-incubation glass walls
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(1.0D, 1.48D + bob, 1.0D);
        pose.mulPose(Axis.YP.rotationDegrees(t * (lit ? 95.0F : 35.0F)));
        Matrix4f cm2 = pose.last().pose();
        box(cyan, cm2, -0.46F, -0.05F, -0.46F, 0.46F, 0.05F, 0.46F,
                0.30F, 0.96F, 1.00F, 0.55F * pulse, LightTexture.FULL_BRIGHT);
        pose.popPose();

        box(cyan, m, 0.20F, 0.38F, 0.20F, 1.80F, 2.60F, 1.80F,
                0.32F, 0.92F, 0.88F, lit ? 0.28F : 0.22F, LightTexture.FULL_BRIGHT);
    }

    // =========================================================================
    // 2. Warp Pad (3x1x3) — Wide Walk-On Teleport Gate Pedestal
    // =========================================================================
    private static void renderWarpPad(PoseStack pose, MultiBufferSource buffers,
                                      int light, boolean lit, float t) {
        Matrix4f m = pose.last().pose();
        float[][] pylons = {{-0.92F, -0.92F}, {1.64F, -0.92F}, {-0.92F, 1.64F}, {1.64F, 1.64F}};
        float pulse = 0.75F + 0.25F * (float) Math.sin(t * (lit ? 5.5F : 2.6F));

        // Pass 1: Walkable 3x3 titanium deck + 4 corner pylons
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_CASING));
        box(steel, m, -0.96F, 0.00F, -0.96F, 1.96F, 0.24F, 1.96F, 0.85F, 0.90F, 0.96F, 1.0F, light);
        box(steel, m, -0.68F, 0.24F, -0.68F, 1.68F, 0.30F, 1.68F, 0.90F, 0.95F, 1.00F, 1.0F, light);
        for (float[] p : pylons) {
            box(steel, m, p[0], 0.24F, p[1], p[0] + 0.28F, 0.72F, p[1] + 0.28F,
                    0.82F, 0.88F, 0.95F, 1.0F, light);
        }

        // Pass 2: Purple corner crystal emitters & rotating inner warp diamond
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_PURPLE));
        for (float[] p : pylons) {
            box(purple, m, p[0] + 0.06F, 0.72F, p[1] + 0.06F, p[0] + 0.22F, 0.94F, p[1] + 0.22F,
                    0.85F, 0.45F, 1.00F, 0.92F * pulse, LightTexture.FULL_BRIGHT);
        }
        pose.pushPose();
        pose.translate(0.5D, 0.34D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-t * 48.0F + 45.0F));
        Matrix4f rm2 = pose.last().pose();
        box(purple, rm2, -0.58F, 0.0F, -0.58F, 0.58F, 0.03F, 0.58F,
                0.86F, 0.42F, 1.00F, 0.58F * pulse, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Cyan perimeter deck rails & outer rotating holographic warp disc
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_CYAN));
        box(cyan, m, -0.94F, 0.245F, -0.94F, 1.94F, 0.265F, -0.84F, 0.30F, 0.94F, 1.00F, 0.85F, LightTexture.FULL_BRIGHT);
        box(cyan, m, -0.94F, 0.245F, 1.84F, 1.94F, 0.265F, 1.94F, 0.30F, 0.94F, 1.00F, 0.85F, LightTexture.FULL_BRIGHT);
        box(cyan, m, -0.94F, 0.245F, -0.84F, -0.84F, 0.265F, 1.84F, 0.30F, 0.94F, 1.00F, 0.85F, LightTexture.FULL_BRIGHT);
        box(cyan, m, 1.84F, 0.245F, -0.84F, 1.94F, 0.265F, 1.84F, 0.30F, 0.94F, 1.00F, 0.85F, LightTexture.FULL_BRIGHT);

        pose.pushPose();
        pose.translate(0.5D, 0.315D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(t * 48.0F));
        Matrix4f rm = pose.last().pose();
        box(cyan, rm, -0.92F, 0.0F, -0.92F, 0.92F, 0.025F, 0.92F,
                0.28F, 0.96F, 1.00F, 0.42F * pulse, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // =========================================================================
    // 3. Climate Controller (1x3x1) — Towering Weather Antenna Spire
    // =========================================================================
    private static void renderClimateController(PoseStack pose, MultiBufferSource buffers,
                                                int light, boolean lit, float t) {
        Matrix4f m = pose.last().pose();
        float[][] ribs = {{0.16F, 0.16F}, {0.74F, 0.16F}, {0.16F, 0.74F}, {0.74F, 0.74F}};
        float dishDeg = t * (lit ? 90.0F : 36.0F);
        float orbPulse = 0.80F + 0.20F * (float) Math.sin(t * 6.0F);

        // Pass 1: Steel tower spine, corner ribs, hub, & rotating dish crossbars
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_CASING));
        box(steel, m, 0.34F, 0.95F, 0.34F, 0.66F, 2.22F, 0.66F, 0.86F, 0.90F, 0.96F, 1.0F, light);
        for (float[] r : ribs) {
            box(steel, m, r[0], 0.95F, r[1], r[0] + 0.10F, 2.08F, r[1] + 0.10F,
                    0.78F, 0.84F, 0.92F, 1.0F, light);
        }
        box(steel, m, 0.24F, 2.18F, 0.24F, 0.76F, 2.34F, 0.76F, 0.88F, 0.92F, 0.98F, 1.0F, light);

        pose.pushPose();
        pose.translate(0.5D, 2.52D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(dishDeg));
        Matrix4f dm = pose.last().pose();
        box(steel, dm, -0.44F, -0.06F, -0.08F, 0.44F, 0.16F, 0.08F, 0.90F, 0.94F, 0.98F, 1.0F, light);
        box(steel, dm, -0.08F, -0.06F, -0.44F, 0.08F, 0.16F, 0.44F, 0.90F, 0.94F, 0.98F, 1.0F, light);
        pose.popPose();

        // Pass 2: Cyan amplification coils, dish tip emitters, & idle plasma orb
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_CYAN));
        float[] coilYs = {1.12F, 1.46F, 1.80F};
        for (int i = 0; i < coilYs.length; i++) {
            float p = 0.72F + 0.28F * (float) Math.sin(t * 4.5F - i * 1.1F);
            float cy = coilYs[i];
            box(cyan, m, 0.14F, cy, 0.14F, 0.86F, cy + 0.12F, 0.86F,
                    0.30F, 0.95F * p, 1.00F, 0.82F, LightTexture.FULL_BRIGHT);
        }

        pose.pushPose();
        pose.translate(0.5D, 2.52D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(dishDeg));
        Matrix4f dm2 = pose.last().pose();
        box(cyan, dm2, -0.48F, 0.04F, -0.06F, -0.38F, 0.26F, 0.06F, 0.38F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        box(cyan, dm2, 0.38F, 0.04F, -0.06F, 0.48F, 0.26F, 0.06F, 0.38F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        box(cyan, dm2, -0.06F, 0.04F, -0.48F, 0.06F, 0.26F, -0.38F, 0.38F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        box(cyan, dm2, -0.06F, 0.04F, 0.38F, 0.06F, 0.26F, 0.48F, 0.38F, 0.96F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        if (!lit) {
            box(cyan, dm2, -0.14F, 0.18F, -0.14F, 0.14F, 0.44F, 0.14F,
                    0.45F, 0.96F * orbPulse, 1.00F, 0.95F, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();

        // Pass 3: Active gold atmospheric plasma emitter orb when lit
        if (lit) {
            VertexConsumer gold = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_ORANGE));
            pose.pushPose();
            pose.translate(0.5D, 2.52D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(dishDeg));
            Matrix4f dm3 = pose.last().pose();
            box(gold, dm3, -0.14F, 0.18F, -0.14F, 0.14F, 0.44F, 0.14F,
                    1.00F, 0.88F * orbPulse, 0.35F, 0.95F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    // =========================================================================
    // 4. Quantum Storage Core (1x2x1) — Tall 2-Story Quantum Mainframe Rack
    // =========================================================================
    private static void renderQuantumStorageCore(PoseStack pose, MultiBufferSource buffers,
                                                 int light, float t) {
        Matrix4f m = pose.last().pose();

        // Pass 1: Steel upper server rack cabinet & bezels
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_CASING));
        box(steel, m, 0.03F, 1.00F, 0.06F, 0.97F, 1.96F, 0.97F, 0.84F, 0.88F, 0.96F, 1.0F, light);
        box(steel, m, 0.03F, 1.00F, 0.02F, 0.14F, 1.96F, 0.08F, 0.90F, 0.94F, 1.00F, 1.0F, light);
        box(steel, m, 0.86F, 1.00F, 0.02F, 0.97F, 1.96F, 0.08F, 0.90F, 0.94F, 1.00F, 1.0F, light);
        box(steel, m, 0.08F, 1.96F, 0.08F, 0.92F, 2.00F, 0.92F, 0.88F, 0.92F, 0.98F, 1.0F, light);

        // Pass 2: Cyan front blades (even rows) & side fiber coolant strips
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_CYAN));
        for (int i = 0; i < 4; i++) {
            float by = 1.08F + i * 0.21F;
            float wave = 0.55F + 0.45F * (float) Math.sin(t * 4.2F - i * 1.15F);
            if (i % 2 == 0) {
                box(cyan, m, 0.16F, by, 0.015F, 0.84F, by + 0.09F, 0.065F,
                        0.30F, 0.96F * wave, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
            }
            box(cyan, m, 0.015F, by, 0.22F, 0.04F, by + 0.08F, 0.78F,
                    0.28F, 0.92F * wave, 1.00F, 0.78F, LightTexture.FULL_BRIGHT);
            box(cyan, m, 0.96F, by, 0.22F, 0.985F, by + 0.08F, 0.78F,
                    0.28F, 0.92F * wave, 1.00F, 0.78F, LightTexture.FULL_BRIGHT);
        }

        // Pass 3: Purple front blades (odd rows)
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_PURPLE));
        for (int i = 1; i < 4; i += 2) {
            float by = 1.08F + i * 0.21F;
            float wave = 0.55F + 0.45F * (float) Math.sin(t * 4.2F - i * 1.15F);
            box(purple, m, 0.16F, by, 0.015F, 0.84F, by + 0.09F, 0.065F,
                    0.86F, 0.45F * wave, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        }
    }

    // =========================================================================
    // 5. Suit Modifier (1x2x1) — Vertical Holographic Modification Bay
    // =========================================================================
    private static void renderSuitModifier(PoseStack pose, MultiBufferSource buffers,
                                           int light, boolean lit, float t) {
        Matrix4f m = pose.last().pose();
        float pulse = 0.76F + 0.24F * (float) Math.sin(t * (lit ? 6.0F : 3.0F));
        float bob = 0.05F * (float) Math.sin(t * 2.8F);

        // Pass 1: Steel gantry pillars, rear spine, & overhead arch
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX_CASING));
        box(steel, m, 0.04F, 0.92F, 0.22F, 0.18F, 1.94F, 0.78F, 0.86F, 0.90F, 0.96F, 1.0F, light);
        box(steel, m, 0.82F, 0.92F, 0.22F, 0.96F, 1.94F, 0.78F, 0.86F, 0.90F, 0.96F, 1.0F, light);
        box(steel, m, 0.18F, 0.92F, 0.80F, 0.82F, 1.94F, 0.95F, 0.80F, 0.86F, 0.92F, 1.0F, light);
        box(steel, m, 0.04F, 1.82F, 0.16F, 0.96F, 1.98F, 0.95F, 0.90F, 0.94F, 0.98F, 1.0F, light);

        // Pass 2: Cyan projection bar & outer floating hologram ring
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_CYAN));
        box(cyan, m, 0.22F, 1.77F, 0.26F, 0.78F, 1.82F, 0.74F,
                0.30F, 0.96F * pulse, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);

        pose.pushPose();
        pose.translate(0.5D, 1.38D + bob, 0.48D);
        pose.mulPose(Axis.YP.rotationDegrees(t * 64.0F));
        Matrix4f hm = pose.last().pose();
        box(cyan, hm, -0.26F, -0.035F, -0.26F, 0.26F, 0.035F, 0.26F,
                0.34F, 0.96F, 1.00F, 0.56F * pulse, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Gold tilted inner hologram ring
        VertexConsumer gold = buffers.getBuffer(RenderType.entityTranslucent(TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 1.38D + bob, 0.48D);
        pose.mulPose(Axis.YP.rotationDegrees(t * 64.0F));
        pose.mulPose(Axis.XP.rotationDegrees(55.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(t * 45.0F));
        Matrix4f hm2 = pose.last().pose();
        box(gold, hm2, -0.17F, -0.17F, -0.03F, 0.17F, 0.17F, 0.03F,
                1.00F, 0.85F, 0.35F, 0.72F * pulse, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // =========================================================================
    // Crisp Axis-Aligned Shaded Textured Box Emitter
    // =========================================================================
    private static void box(VertexConsumer vc, Matrix4f m,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float r, float g, float b, float a, int light) {
        // Top (+Y)
        face(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b, a, light);
        // Bottom (-Y)
        face(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r * 0.58F, g * 0.58F, b * 0.58F, a, light);
        // North (-Z)
        face(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r * 0.84F, g * 0.84F, b * 0.84F, a, light);
        // South (+Z)
        face(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r * 0.84F, g * 0.84F, b * 0.84F, a, light);
        // West (-X)
        face(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r * 0.72F, g * 0.72F, b * 0.72F, a, light);
        // East (+X)
        face(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r * 0.72F, g * 0.72F, b * 0.72F, a, light);
    }

    private static void face(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             float r, float g, float b, float a, int light) {
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0F)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0F)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0F)));
        int ai = Math.max(0, Math.min(255, Math.round(a * 255.0F)));
        vc.addVertex(m, x0, y0, z0).setColor(ri, gi, bi, ai).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x1, y1, z1).setColor(ri, gi, bi, ai).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(ri, gi, bi, ai).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(ri, gi, bi, ai).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }
}
