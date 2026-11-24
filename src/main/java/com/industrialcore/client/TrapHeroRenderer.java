package com.industrialcore.client;

import com.industrialcore.blockentity.LaserTurretBlockEntity;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 111 / 113 Tier 2 Hero BER: Trap &amp; Defense machines
 * ({@code item_vacuum}, {@code xp_collector}, {@code laser_turret}).
 *
 * <p>Phase 113 upgrades {@code laser_turret} with real-time Yaw/Pitch tracking toward the synced
 * target coordinates and a high-luminance 3D dual-core cyan/magenta pulse laser beam rendered
 * directly from the twin barrel muzzles to the locked target.</p>
 */
public final class TrapHeroRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public enum Kind {
        ITEM_VACUUM,
        XP_COLLECTOR,
        LASER_TURRET
    }

    private final Kind kind;
    private final Map<Long, LivingFactoryAnimLogic.TurretAimState> turretPoses = new HashMap<>(16);

    public TrapHeroRenderer(BlockEntityRendererProvider.Context ctx, Kind kind) {
        this.kind = kind;
    }

    @Override
    public int getViewDistance() {
        return LivingFactoryAnimLogic.BER_VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        BlockPos p = be.getBlockPos();
        if (kind == Kind.LASER_TURRET && be instanceof LaserTurretBlockEntity turret && turret.hasClientTarget()) {
            double tx = turret.getClientTargetX();
            double ty = turret.getClientTargetY();
            double tz = turret.getClientTargetZ();
            return new AABB(
                    Math.min(p.getX() - 0.5D, tx - 0.5D),
                    Math.min(p.getY(), ty - 0.5D),
                    Math.min(p.getZ() - 0.5D, tz - 0.5D),
                    Math.max(p.getX() + 1.5D, tx + 0.5D),
                    Math.max(p.getY() + 1.8D, ty + 0.5D),
                    Math.max(p.getZ() + 1.5D, tz + 0.5D));
        }
        return new AABB(p.getX() - 0.3D, p.getY(), p.getZ() - 0.3D,
                p.getX() + 1.3D, p.getY() + 1.6D, p.getZ() + 1.3D);
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
            case ITEM_VACUUM -> renderItemVacuum(pos, tSec, pose, buffers);
            case XP_COLLECTOR -> renderXpCollector(pos, tSec, pose, buffers);
            case LASER_TURRET -> renderLaserTurret(be, pos, facing, tSec, pose, buffers, packedLight);
        }
    }

    private static void renderItemVacuum(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "item_vacuum",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.10F), 0.25F);
        float deg = LivingFactoryAnimLogic.vacuumVortexDeg(tSec);
        float r1 = LivingFactoryAnimLogic.vacuumVortexRadius(tSec);
        float r2 = LivingFactoryAnimLogic.vacuumVortexRadius(tSec + 0.45F);

        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.5D, 1.02D + (r1 - 0.24D) * 0.35D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        HeroRenderPrimitives.energyRingXZ(pose, purple, r1, 0.024F, 0.022F,
                0.86F, 0.42F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.95D + (r2 - 0.24D) * 0.30D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.4F));
        HeroRenderPrimitives.energyRingXZ(pose, cyan, r2, 0.022F, 0.020F,
                0.30F, 0.96F, 1.00F, 0.90F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderXpCollector(BlockPos pos, float tSec, PoseStack pose, MultiBufferSource buffers) {
        FactoryAudioController.onMotionPhase(pos, "xp_collector",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.10F), 0.20F);
        float deg = LivingFactoryAnimLogic.xpOrbDeg(tSec);
        float bob = LivingFactoryAnimLogic.xpOrbBobY(tSec);

        VertexConsumer green = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_GREEN));
        pose.pushPose();
        pose.translate(0.5D, 1.15D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(deg));
        pose.mulPose(Axis.XP.rotationDegrees(35.264F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        Matrix4f cm = pose.last().pose();
        HeroRenderPrimitives.box(green, cm, -0.105F, -0.105F, -0.105F, 0.105F, 0.105F, 0.105F,
                0.34F, 1.00F, 0.48F, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 0.52D - bob * 0.6F, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.3F));
        HeroRenderPrimitives.energyRingXZ(pose, green, 0.33F, 0.022F, 0.022F,
                0.30F, 1.00F, 0.44F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        VertexConsumer gold = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 1.15D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-deg * 1.5F));
        pose.mulPose(Axis.XP.rotationDegrees(22.0F));
        HeroRenderPrimitives.energyRingXZ(pose, gold, 0.23F, 0.018F, 0.018F,
                0.86F, 1.00F, 0.34F, 0.86F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private void renderLaserTurret(T be, BlockPos pos, Direction facing, float tSec,
                                   PoseStack pose, MultiBufferSource buffers, int packedLight) {
        FactoryAudioController.onMotionPhase(pos, "laser_turret",
                LivingFactoryAnimLogic.cyclePhase(tSec, 2.40F), 0.25F);

        boolean locked = false;
        boolean firing = false;
        double dx = 0.0D, dy = 0.0D, dz = 0.0D;
        double dist = 0.0D;
        float targetYaw = 0.0F;
        float targetPitch = 0.0F;

        if (be instanceof LaserTurretBlockEntity turret && turret.hasClientTarget()) {
            locked = true;
            firing = turret.isClientFiringPulse();
            dx = turret.getClientTargetX() - (pos.getX() + 0.5D);
            dy = turret.getClientTargetY() - (pos.getY() + 0.78D);
            dz = turret.getClientTargetZ() - (pos.getZ() + 0.5D);
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            targetYaw = LivingFactoryAnimLogic.turretAimYawDeg(dx, dz);
            targetPitch = LivingFactoryAnimLogic.turretAimPitchDeg(dx, dy, dz);
        }

        if (turretPoses.size() > 128) {
            turretPoses.clear();
        }
        LivingFactoryAnimLogic.TurretAimState tp = turretPoses.computeIfAbsent(
                pos.asLong(), k -> new LivingFactoryAnimLogic.TurretAimState());
        LivingFactoryAnimLogic.stepTurretAim(
                tp, HeroRenderPrimitives.horizontalRotationDeg(facing), locked, targetYaw, targetPitch, tSec);
        float yaw = tp.yaw;
        float pitch = tp.pitch;

        // Phase 117: Upgraded twin Pulse Laser turret mounted on tripod + 360° slewing ring (y=0.72)
        // with independent pitch, stacked heat-sink cooling fins, amber recoil cradle, and firing kickback!
        float recoilZ = firing ? 0.095F : 0.0F;

        // Pass 1: Gunmetal gimbal housing, twin Pulse Laser barrels & stacked cooling fins
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_FRAME));
        pose.pushPose();
        pose.translate(0.5D, 0.78D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Matrix4f dm = pose.last().pose();
        // Slewing yoke pedestal & armored turret head
        HeroRenderPrimitives.box(steel, dm, -0.17F, -0.08F, -0.15F, 0.17F, 0.11F, 0.21F,
                0.84F, 0.88F, 0.94F, 1.0F, packedLight);
        // Pitching twin Pulse Laser barrels with firing recoil kickback
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0.0D, 0.0D, recoilZ);
        Matrix4f bm = pose.last().pose();
        HeroRenderPrimitives.box(steel, bm, -0.15F, -0.025F, -0.56F, -0.06F, 0.060F, -0.12F,
                0.78F, 0.84F, 0.92F, 1.0F, packedLight);
        HeroRenderPrimitives.box(steel, bm, 0.06F, -0.025F, -0.56F, 0.15F, 0.060F, -0.12F,
                0.78F, 0.84F, 0.92F, 1.0F, packedLight);
        // Stacked cooling fins along both barrels
        for (int fin = 0; fin < 3; fin++) {
            float fz = -0.22F - fin * 0.10F;
            HeroRenderPrimitives.box(steel, bm, -0.175F, -0.038F, fz - 0.02F, -0.045F, 0.074F, fz + 0.02F,
                    0.90F, 0.94F, 0.98F, 1.0F, packedLight);
            HeroRenderPrimitives.box(steel, bm, 0.045F, -0.038F, fz - 0.02F, 0.175F, 0.074F, fz + 0.02F,
                    0.90F, 0.94F, 0.98F, 1.0F, packedLight);
        }
        pose.popPose();

        // Pass 2: Industrial Amber hydraulic recoil cradle & side cheek armor
        VertexConsumer amber = buffers.getBuffer(RenderType.entityCutoutNoCull(HeroRenderPrimitives.TEX_AMBER));
        pose.pushPose();
        pose.translate(0.5D, 0.78D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Matrix4f am = pose.last().pose();
        HeroRenderPrimitives.box(amber, am, -0.21F, -0.03F, -0.10F, -0.16F, 0.09F, 0.16F,
                1.00F, 0.92F, 0.74F, 1.0F, packedLight);
        HeroRenderPrimitives.box(amber, am, 0.16F, -0.03F, -0.10F, 0.21F, 0.09F, 0.16F,
                1.00F, 0.92F, 0.74F, 1.0F, packedLight);
        pose.popPose();

        // Pass 3: Twin glowing cyan Pulse Laser muzzle accelerators (main beam drawn by WeaponWorldFxRenderer)
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        pose.pushPose();
        pose.translate(0.5D, 0.78D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0.0D, 0.0D, recoilZ);
        Matrix4f cm = pose.last().pose();
        float muzzleBright = firing ? 1.00F : 0.80F;
        HeroRenderPrimitives.box(cyan, cm, -0.145F, -0.018F, -0.60F, -0.065F, 0.052F, -0.52F,
                0.36F * muzzleBright, 0.98F, 1.00F, 0.98F, LightTexture.FULL_BRIGHT);
        HeroRenderPrimitives.box(cyan, cm, 0.065F, -0.018F, -0.60F, 0.145F, 0.052F, -0.52F,
                0.36F * muzzleBright, 0.98F, 1.00F, 0.98F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 4: Targeting optic eye (blazing cyan while firing, amber-red on lock/standby)
        VertexConsumer optic = buffers.getBuffer(RenderType.entityTranslucent(
                firing ? HeroRenderPrimitives.TEX_GLOW_CYAN : HeroRenderPrimitives.TEX_GLOW_ORANGE));
        pose.pushPose();
        pose.translate(0.5D, 0.78D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        Matrix4f om = pose.last().pose();
        HeroRenderPrimitives.box(optic, om, -0.048F, 0.015F, -0.20F, 0.048F, 0.088F, -0.14F,
                firing ? 0.55F : (locked ? 1.00F : 0.95F),
                firing ? 0.98F : (locked ? 0.24F : 0.48F),
                firing ? 1.00F : (locked ? 0.65F : 0.22F),
                0.96F, LightTexture.FULL_BRIGHT);
        if (!firing && !locked) {
            HeroRenderPrimitives.box(optic, om, -0.008F, 0.042F, -0.78F, 0.008F, 0.058F, -0.20F,
                    0.35F, 0.92F, 1.00F, 0.38F, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
