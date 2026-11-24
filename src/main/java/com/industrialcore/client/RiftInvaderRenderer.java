package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.raid.entity.AbstractRiftInvaderEntity;
import com.industrialcore.raid.entity.DimensionalSovereignEntity;
import com.industrialcore.raid.entity.PhaseJuggernautEntity;
import com.industrialcore.raid.entity.RiftStalkerEntity;
import com.industrialcore.raid.entity.VoidSentinelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Phase 120: Mecha / Sci-Fi Anime Inspired High-Detail Multi-Part 3D Renderer for all 4 original
 * AegisWorks Dimensional Rift enemies:
 * <ul>
 *   <li>{@code rift_stalker}: Orbital Frame / NieR Flight Unit silhouette with V-visor mono-eye,
 *       digitigrade reverse-joint hovering legs, 4 articulated 3-segment X-wing blades, and tail stabilizer.</li>
 *   <li>{@code void_sentinel}: Gundam Funnel / AC Satellite autonomous weapon with 4 armored diamond petals
 *       that mechanically fold shut while cruising and unfold radially during charge to reveal gyro-rings
 *       and an extending focused rail-cannon barrel.</li>
 *   <li>{@code phase_juggernaut}: Heavy Knight Mecha (Sazabi/Tallgeese/Reinhardt) with layered shoulder
 *       pauldrons, twin dorsal verniers, and twin forearm tower shields clasped to project a hex energy wall
 *       (blown wide open exposing the chest core during Quantum Blade Shield Break!).</li>
 *   <li>{@code dimensional_sovereign}: Eva Angel x AC Endgame Boss with angular crowned head, curved
 *       armored rib-cage around a pitch-black singularity heart, 6 rear halo monolith wings, and 4 detached
 *       floating multi-jointed energy gauntlet arms (blazing crimson-gold in Overdrive Phase 2).</li>
 * </ul>
 */
public final class RiftInvaderRenderer<T extends AbstractRiftInvaderEntity> extends EntityRenderer<T> {
    public static final ResourceLocation TEX_PHASE_SHIELD = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/entity/rift_phase_shield.png");

    private static final int[] SIDES = {-1, 1};

    public enum Kind {
        RIFT_STALKER("textures/entity/rift_stalker.png"),
        VOID_SENTINEL("textures/entity/void_sentinel.png"),
        PHASE_JUGGERNAUT("textures/entity/phase_juggernaut.png"),
        DIMENSIONAL_SOVEREIGN("textures/entity/dimensional_sovereign.png");

        final ResourceLocation texture;

        Kind(String path) {
            this.texture = ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, path);
        }
    }

    private final Kind kind;

    public RiftInvaderRenderer(EntityRendererProvider.Context ctx, Kind kind) {
        super(ctx);
        this.kind = kind;
        this.shadowRadius = switch (kind) {
            case RIFT_STALKER -> 0.45F;
            case VOID_SENTINEL -> 0.58F;
            case PHASE_JUGGERNAUT -> 0.82F;
            case DIMENSIONAL_SOVEREIGN -> 1.35F;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return kind.texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);

        float tSec = ((Util.getMillis() % 600_000L) / 1000.0F) + (entity.getId() * 0.37F);
        float bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        boolean hurtFlash = entity.hurtTime > 0;
        int actionKind = entity.getActionKind();
        int actionTicks = entity.getActionTicks();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

        switch (kind) {
            case RIFT_STALKER -> renderStalker(entity, pose, buffers, packedLight, tSec, hurtFlash, actionKind, actionTicks);
            case VOID_SENTINEL -> renderSentinel(entity, pose, buffers, packedLight, tSec, hurtFlash, actionKind, actionTicks);
            case PHASE_JUGGERNAUT -> renderJuggernaut(entity, partialTick, pose, buffers, packedLight, tSec, hurtFlash, actionKind, actionTicks);
            case DIMENSIONAL_SOVEREIGN -> renderSovereign(entity, pose, buffers, packedLight, tSec, hurtFlash, actionKind, actionTicks);
        }

        pose.popPose();
    }

    /**
     * #1: Rift Stalker — Orbital Frame / NieR Flight Unit silhouette:
     * V-visor angular head w/ crimson-violet mono-eye slit, sleek torso, digitigrade reverse-joint hovering legs,
     * 4 articulated 3-segment X-wing blades (root -> mid -> tip blade), and tail energy stabilizer.
     */
    private void renderStalker(T entity, PoseStack pose, MultiBufferSource buffers, int light,
                               float tSec, boolean hurt, int actionKind, int actionTicks) {
        boolean jammed = (entity instanceof RiftStalkerEntity st && st.isPhaseJammed());
        float bob = (jammed ? 0.04F : 0.16F) * (float) Math.sin(tSec * (jammed ? 14.0D : 4.6D));
        float slashEnv = (actionKind == AbstractRiftInvaderEntity.ACTION_BLINK_SLASH && actionTicks > 0)
                ? Math.min(1.0F, actionTicks / 10.0F) : 0.0F;
        float wingBreath = 11.0F * (float) Math.sin(tSec * 5.8D);
        float forwardSweep = slashEnv * 52.0F - (jammed ? 16.0F : 0.0F);
        float tintR = hurt ? 1.00F : 0.94F;
        float tintG = hurt ? 0.45F : 0.92F;
        float tintB = hurt ? 0.55F : 1.00F;

        pose.pushPose();
        pose.translate(0.0D, 1.06D + bob, 0.0D);

        // Pass 1: Angular V-visor head, slim armored torso, digitigrade reverse-joint legs, tail stabilizer & 4x 3-seg X-wings
        VertexConsumer armor = buffers.getBuffer(RenderType.entityCutoutNoCull(kind.texture));
        Matrix4f m = pose.last().pose();
        // Sleek swept-back V-visor head + dorsal crest blade
        HeroRenderPrimitives.box(armor, m, -0.17F, 0.42F, -0.26F, 0.17F, 0.68F, 0.16F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, m, -0.11F, 0.56F, -0.34F, 0.11F, 0.65F, -0.18F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, m, -0.05F, 0.68F, -0.12F, 0.05F, 0.84F, 0.28F, tintR, tintG, tintB, 1.0F, light);
        // Slim tapered chest & narrow articulated waist
        HeroRenderPrimitives.box(armor, m, -0.22F, 0.12F, -0.16F, 0.22F, 0.40F, 0.18F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, m, -0.11F, -0.16F, -0.10F, 0.11F, 0.12F, 0.12F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, m, -0.16F, -0.32F, -0.12F, 0.16F, -0.14F, 0.14F, tintR, tintG, tintB, 1.0F, light);
        // Rear tail energy propulsion stabilizer fin
        HeroRenderPrimitives.box(armor, m, -0.06F, -0.30F, 0.14F, 0.06F, -0.16F, 0.58F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, m, -0.03F, -0.26F, 0.44F, 0.03F, 0.02F, 0.68F, tintR, tintG, tintB, 1.0F, light);

        // Digitigrade reverse-joint (bird-like) hovering legs (femur forward -> knee back -> talon down)
        for (int side : SIDES) {
            pose.pushPose();
            pose.translate(side * 0.17F, -0.26D, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(-26.0F + 6.0F * (float) Math.sin(tSec * 4.0D + side)));
            HeroRenderPrimitives.box(armor, pose.last().pose(), -0.06F, -0.32F, -0.06F, 0.06F, 0.02F, 0.06F, tintR, tintG, tintB, 1.0F, light);
            pose.translate(0.0D, -0.30D, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(64.0F)); // Reverse knee bend!
            HeroRenderPrimitives.box(armor, pose.last().pose(), -0.045F, -0.34F, -0.045F, 0.045F, 0.02F, 0.045F, tintR, tintG, tintB, 1.0F, light);
            pose.translate(0.0D, -0.32D, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(-42.0F)); // Hovering blade talon!
            HeroRenderPrimitives.box(armor, pose.last().pose(), -0.035F, -0.24F, -0.05F, 0.035F, 0.02F, 0.04F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // 4 Articulated 3-Segment X-Wing Blades (root hinge -> angled mid-wing -> swept tip blade!)
        for (int tier : SIDES) { // +1 upper X-pair, -1 lower X-pair
            for (int side : SIDES) {
                pose.pushPose();
                pose.translate(side * 0.18F, 0.24D + tier * 0.12D, 0.16D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (-26.0F + forwardSweep - wingBreath)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (tier > 0 ? 34.0F : -30.0F)));
                // Segment 1: Armored root pylon
                HeroRenderPrimitives.box(armor, pose.last().pose(),
                        Math.min(0.0F, side * 0.34F), -0.045F, -0.04F,
                        Math.max(0.0F, side * 0.34F), 0.055F, 0.04F, tintR, tintG, tintB, 1.0F, light);
                // Segment 2: Articulated mid-wing airfoil
                pose.translate(side * 0.32D, 0.0D, 0.0D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (12.0F + slashEnv * 18.0F)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (tier > 0 ? -10.0F : 10.0F)));
                HeroRenderPrimitives.box(armor, pose.last().pose(),
                        Math.min(0.0F, side * 0.42F), -0.038F, -0.032F,
                        Math.max(0.0F, side * 0.42F), 0.045F, 0.032F, tintR, tintG, tintB, 1.0F, light);
                pose.popPose();
            }
        }

        // Pass 2: Glowing violet razor tip blades (Segment 3 of all 4 X-wings) + thoracic core + tail plume
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        pose.pushPose();
        pose.translate(0.0D, 0.24D, -0.10D);
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        HeroRenderPrimitives.box(purple, pose.last().pose(), -0.11F, -0.11F, -0.11F, 0.11F, 0.11F, 0.11F,
                0.96F, 0.28F + slashEnv * 0.40F, 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        for (int tier : SIDES) {
            for (int side : SIDES) {
                pose.pushPose();
                pose.translate(side * 0.18F, 0.24D + tier * 0.12D, 0.16D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (-26.0F + forwardSweep - wingBreath)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (tier > 0 ? 34.0F : -30.0F)));
                pose.translate(side * 0.32D, 0.0D, 0.0D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (12.0F + slashEnv * 18.0F)));
                pose.mulPose(Axis.ZP.rotationDegrees(side * (tier > 0 ? -10.0F : 10.0F)));
                pose.translate(side * 0.40D, 0.0D, 0.0D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (14.0F + slashEnv * 22.0F)));
                // Segment 3: Razor luminous plasma wingtip blade!
                HeroRenderPrimitives.box(purple, pose.last().pose(),
                        Math.min(0.0F, side * 0.56F), -0.025F, -0.042F,
                        Math.max(0.0F, side * 0.56F), 0.032F, 0.042F,
                        0.96F, 0.36F + slashEnv * 0.38F, 1.00F, 0.94F, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
        // Tail propulsion exhaust plume
        HeroRenderPrimitives.box(purple, m, -0.045F, -0.26F, 0.58F, 0.045F, -0.18F, 0.92F + slashEnv * 0.25F,
                0.92F, 0.32F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);

        // Pass 3: Crimson-violet mono-eye visor slit (or jammed cyan electrical arcs!)
        VertexConsumer accent = buffers.getBuffer(RenderType.entityTranslucent(
                jammed ? HeroRenderPrimitives.TEX_GLOW_CYAN : HeroRenderPrimitives.TEX_GLOW_ORANGE));
        HeroRenderPrimitives.box(accent, m, -0.14F, 0.52F, -0.355F, 0.14F, 0.575F, -0.27F,
                jammed ? 0.34F : 1.00F, jammed ? 0.96F : 0.22F, jammed ? 1.00F : 0.48F,
                1.0F, LightTexture.FULL_BRIGHT);

        pose.popPose();
    }

    /**
     * #2: Void Sentinel — Gundam Funnel / AC Autonomous Satellite Weapon:
     * 4 heavy armored diamond petal shields fold shut during cruise and mechanically unfold radially outward
     * during charge/firing to expose internal multi-gyro rings, cooling fins, and an extending focused rail-cannon!
     */
    private void renderSentinel(T entity, PoseStack pose, MultiBufferSource buffers, int light,
                                float tSec, boolean hurt, int actionKind, int actionTicks) {
        boolean open = (entity instanceof VoidSentinelEntity vs && vs.isPetalOpen());
        boolean stunned = (entity instanceof VoidSentinelEntity vs && vs.isCrashStunned());
        float openEnv = open ? 1.0F : 0.0F;
        float bob = stunned ? 0.02F : 0.15F * (float) Math.sin(tSec * 3.1D);
        float recoilZ = (actionKind == AbstractRiftInvaderEntity.ACTION_FIRE_RECOIL && actionTicks > 0)
                ? (actionTicks / 8.0F) * 0.28F : 0.0F;
        float tintR = hurt ? 1.00F : 0.90F;
        float tintG = hurt ? 0.45F : 0.94F;
        float tintB = hurt ? 0.55F : 1.00F;

        pose.pushPose();
        pose.translate(0.0D, (stunned ? 0.62D : 1.04D) + bob, recoilZ);

        // Pass 1: 4 Transforming Armored Diamond Petal Shields + Inner Core Frame + Extending Rail-Cannon Barrel
        VertexConsumer armor = buffers.getBuffer(RenderType.entityCutoutNoCull(kind.texture));
        Matrix4f cm = pose.last().pose();
        // Central armored gyro hub & radial cooling fins (revealed when petals open!)
        HeroRenderPrimitives.box(armor, cm, -0.26F, -0.26F, -0.22F, 0.26F, 0.26F, 0.28F, tintR, tintG, tintB, 1.0F, light);
        // Extending focused rail-cannon barrel (protrudes forward when petals open!)
        float barrelTip = -0.34F - openEnv * 0.52F + recoilZ * 0.60F;
        HeroRenderPrimitives.box(armor, cm, -0.11F, -0.11F, barrelTip, 0.11F, 0.11F, -0.18F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, cm, -0.17F, -0.05F, barrelTip + 0.10F, 0.17F, 0.05F, -0.22F, tintR, tintG, tintB, 1.0F, light);

        // 4 Armored Diamond Petals (Top, Bottom, Left, Right) — fold shut into diamond pod or unfold 38° outward!
        float petalOut = 0.28F + openEnv * 0.30F;
        float petalTilt = openEnv * 38.0F;
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 90.0F));
            pose.translate(0.0D, petalOut, -0.04D);
            pose.mulPose(Axis.XP.rotationDegrees(-petalTilt));
            Matrix4f pm = pose.last().pose();
            // Sculpted 2-tier tapered diamond petal shield plate
            HeroRenderPrimitives.box(armor, pm, -0.26F, -0.08F, -0.36F, 0.26F, 0.08F, 0.30F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, pm, -0.16F, 0.08F, -0.24F, 0.16F, 0.15F, 0.22F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // Pass 2: Dual Counter-Rotating Gyro-Rings (expand & spin rapidly when petals open!)
        VertexConsumer purple = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_PURPLE));
        float spd = open ? 2.4F : 1.0F;
        float ringR = 0.62F + openEnv * 0.26F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 120.0F * spd));
        pose.mulPose(Axis.XP.rotationDegrees(28.0F));
        HeroRenderPrimitives.energyRingXZ(pose, purple, ringR, 0.042F, 0.038F,
                0.88F, 0.32F + openEnv * 0.35F, 1.00F, 0.92F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(62.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-tSec * 160.0F * spd));
        HeroRenderPrimitives.energyRingXZ(pose, purple, ringR * 0.78F, 0.036F, 0.034F,
                0.78F, 0.30F + openEnv * 0.42F, 1.00F, 0.88F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 3: Exposed High-Luminance Optical Weakpoint Core & Rail-Cannon Muzzle Orb!
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        float coreR = 0.14F + openEnv * 0.10F;
        HeroRenderPrimitives.box(cyan, cm, -coreR, -coreR, -0.29F, coreR, coreR, 0.20F,
                open ? 0.72F : 0.28F, 0.98F, 1.00F, open ? 1.00F : 0.75F, LightTexture.FULL_BRIGHT);
        if (open) {
            float mR = 0.13F + 0.04F * (float) Math.sin(tSec * 22.0D);
            HeroRenderPrimitives.box(cyan, cm, -mR, -mR, barrelTip - 0.07F, mR, mR, barrelTip + 0.05F,
                    0.82F, 1.00F, 1.00F, 1.00F, LightTexture.FULL_BRIGHT);
        }

        pose.popPose();
    }

    /**
     * #3: Phase Juggernaut — Heavy Knight Mecha (Sazabi / Tallgeese / Reinhardt silhouette):
     * Massive multi-layer shoulder pauldrons, knight-crested helm, twin dorsal vernier thrusters,
     * and twin curved forearm tower shields clasped together to form a giant hex energy barrier —
     * blown wide apart exposing the blazing chest reactor core during Quantum Blade Shield Break!
     */
    private void renderJuggernaut(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                  int light, float tSec, boolean hurt, int actionKind, int actionTicks) {
        boolean broken = (entity instanceof PhaseJuggernautEntity pj && pj.isShieldBroken());
        float walkPos = entity.walkAnimation.position(partialTick);
        float walkSpeed = entity.walkAnimation.speed(partialTick);
        float legSwing = broken ? 0.0F : Mth.cos(walkPos * 0.66F) * 26.0F * Math.min(1.0F, walkSpeed);
        float shieldFlash = (actionKind == AbstractRiftInvaderEntity.ACTION_SHIELD_FLASH && actionTicks > 0)
                ? (actionTicks / 12.0F) : 0.0F;
        float tintR = hurt ? 1.00F : 0.93F;
        float tintG = hurt ? 0.45F : 0.90F;
        float tintB = hurt ? 0.55F : 1.00F;

        // Pass 1: Heavy Knight Mecha chassis, layered shoulder pauldrons, twin dorsal verniers & twin forearm tower shields
        VertexConsumer armor = buffers.getBuffer(RenderType.entityCutoutNoCull(kind.texture));
        Matrix4f baseM = pose.last().pose();
        // Heavy armored waist skirt & towering dreadnought chest
        HeroRenderPrimitives.box(armor, baseM, -0.48F, 0.82F, -0.32F, 0.48F, 1.06F, 0.32F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, baseM, -0.58F, 1.06F, -0.38F, 0.58F, 1.98F, 0.38F, tintR, tintG, tintB, 1.0F, light);
        // Knight-crested heavy helmet (tall command fin + angled brow)
        HeroRenderPrimitives.box(armor, baseM, -0.28F, 1.98F, -0.28F, 0.28F, 2.42F, 0.28F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, baseM, -0.06F, 2.38F, -0.34F, 0.06F, 2.66F, 0.32F, tintR, tintG, tintB, 1.0F, light);

        // Massive 3-tier layered shoulder pauldrons (Sazabi / Tallgeese silhouette!)
        for (int side : SIDES) {
            HeroRenderPrimitives.box(armor, baseM,
                    Math.min(side * 0.56F, side * 1.02F), 1.54F, -0.44F,
                    Math.max(side * 0.56F, side * 1.02F), 1.98F, 0.44F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, baseM,
                    Math.min(side * 0.66F, side * 1.14F), 1.80F, -0.38F,
                    Math.max(side * 0.66F, side * 1.14F), 2.14F, 0.38F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, baseM,
                    Math.min(side * 0.78F, side * 1.22F), 2.04F, -0.28F,
                    Math.max(side * 0.78F, side * 1.22F), 2.24F, 0.28F, tintR, tintG, tintB, 1.0F, light);
        }

        // Twin angled dorsal vernier booster pods on back
        for (int side : SIDES) {
            pose.pushPose();
            pose.translate(side * 0.30F, 1.56D, 0.46D);
            pose.mulPose(Axis.XP.rotationDegrees(18.0F));
            HeroRenderPrimitives.box(armor, pose.last().pose(), -0.18F, -0.36F, -0.16F, 0.18F, 0.42F, 0.20F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // Heavy articulated legs with oversized knight knee guards & wide sabatons
        for (int side : SIDES) {
            pose.pushPose();
            pose.translate(side * 0.32F, 0.86F, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(side < 0 ? legSwing : -legSwing));
            Matrix4f lm = pose.last().pose();
            HeroRenderPrimitives.box(armor, lm, -0.18F, -0.44F, -0.20F, 0.18F, 0.02F, 0.20F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, lm, -0.22F, -0.56F, -0.28F, 0.22F, -0.32F, 0.18F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, lm, -0.22F, -0.86F, -0.26F, 0.22F, -0.46F, 0.26F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // Twin Heavy Arms + Curved Forearm Tower Shields (clasped forward in guard; blown wide +/-68 deg on Shield Break!)
        float armGuardYaw = broken ? 68.0F : -28.0F;
        for (int side : SIDES) {
            pose.pushPose();
            pose.translate(side * 0.80F, 1.60F, broken ? 0.08D : -0.10D);
            pose.mulPose(Axis.YP.rotationDegrees(side * armGuardYaw));
            Matrix4f am = pose.last().pose();
            HeroRenderPrimitives.box(armor, am, -0.17F, -0.78F, -0.18F, 0.17F, 0.0F, 0.18F, tintR, tintG, tintB, 1.0F, light);
            // Massive curved armored tower shield mounted on forearm!
            HeroRenderPrimitives.box(armor, am, -0.32F, -1.12F, -0.48F, 0.32F, 0.34F, -0.34F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, am, -0.24F, -0.96F, -0.54F, 0.24F, 0.18F, -0.48F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // Pass 2: Dorsal vernier purple plumes & glowing chest reactor core (blazes gold-white when shield-broken!)
        VertexConsumer coreBuf = buffers.getBuffer(RenderType.entityTranslucent(
                broken ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_PURPLE));
        float pulse = 0.80F + 0.20F * (float) Math.sin(tSec * (broken ? 18.0D : 5.0D));
        float coreSize = broken ? 0.32F : 0.20F;
        HeroRenderPrimitives.box(coreBuf, baseM, -coreSize, 1.32F, -0.44F, coreSize, 1.72F, -0.34F,
                broken ? 1.00F : 0.92F, broken ? 0.86F * pulse : 0.36F, broken ? 0.36F : 1.00F,
                0.98F, LightTexture.FULL_BRIGHT);
        for (int side : SIDES) {
            HeroRenderPrimitives.box(coreBuf, baseM,
                    side * 0.30F - 0.11F, 1.02F, 0.56F,
                    side * 0.30F + 0.11F, 1.24F, 0.72F,
                    0.92F, 0.34F, 1.00F, 0.90F * pulse, LightTexture.FULL_BRIGHT);
        }

        // Pass 3: Cyan knight visor slit
        VertexConsumer cyan = buffers.getBuffer(RenderType.entityTranslucent(HeroRenderPrimitives.TEX_GLOW_CYAN));
        HeroRenderPrimitives.box(cyan, baseM, -0.22F, 2.14F, -0.31F, 0.22F, 2.23F, -0.27F,
                0.34F, 0.98F, 1.00F, 1.0F, LightTexture.FULL_BRIGHT);

        // Pass 4: Giant Hexagonal Energy Wall Projected Between the Clasped Tower Shields (SHATTERED when broken!)
        if (!broken) {
            VertexConsumer shield = buffers.getBuffer(RenderType.entityTranslucent(TEX_PHASE_SHIELD));
            float sPulse = 0.86F + 0.14F * (float) Math.sin(tSec * 5.2D);
            float shExpand = shieldFlash * 0.12F;
            float shR = Math.min(1.0F, 0.86F + shieldFlash * 0.14F);
            float shG = Math.min(1.0F, 0.56F + shieldFlash * 0.44F);
            float shA = Math.min(0.98F, (0.68F * sPulse) + shieldFlash * 0.30F);
            HeroRenderPrimitives.box(shield, baseM,
                    -0.84F - shExpand, 0.34F - shExpand * 0.4F, -0.96F - shExpand * 0.3F,
                    0.84F + shExpand, 2.24F + shExpand * 0.4F, -0.88F - shExpand * 0.3F,
                    shR, shG, 1.00F, shA, LightTexture.FULL_BRIGHT);
            for (int side : SIDES) {
                pose.pushPose();
                pose.translate(side * (0.84F + shExpand), 1.29F, -0.92F - shExpand * 0.3F);
                pose.mulPose(Axis.YP.rotationDegrees(side * -26.0F));
                HeroRenderPrimitives.box(shield, pose.last().pose(),
                        Math.min(0.0F, side * 0.46F), -0.90F, -0.035F,
                        Math.max(0.0F, side * 0.46F), 0.90F, 0.035F,
                        shR, shG, 1.00F, shA * 0.92F, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
    }

    /**
     * #4: Dimensional Sovereign (Boss) — Eva Angel x AC Endgame Boss x Mythic Orbital Dreadnought:
     * Angular crowned head, armored curved rib-cage guarding a pitch-black singularity heart,
     * 6 rear halo monolith wings arranged in a divine ring, 4 detached floating multi-jointed energy
     * gauntlet arms, and 3 interlocking gimbal rings. Transforms to incandescent crimson-gold in Overdrive!
     */
    private void renderSovereign(T entity, PoseStack pose, MultiBufferSource buffers,
                                 int light, float tSec, boolean hurt, int actionKind, int actionTicks) {
        boolean od = (entity instanceof DimensionalSovereignEntity boss && boss.isOverdrivePhase());
        boolean barrier = (entity instanceof DimensionalSovereignEntity boss && boss.isAbsoluteBarrierActive());
        boolean stunned = (entity instanceof DimensionalSovereignEntity boss && boss.isCoreExposedStunned());
        float spd = stunned ? 0.35F : (od ? 2.40F : 1.0F);
        float bob = (stunned ? 0.06F : 0.26F) * (float) Math.sin(tSec * 2.5D * (od ? 1.4F : 1.0F));
        float skillFlare = (actionKind == AbstractRiftInvaderEntity.ACTION_SKILL_FLARE && actionTicks > 0)
                ? (float) Math.sin(Math.min(1.0F, actionTicks / 22.0F) * Math.PI) : 0.0F;
        float openCage = (od ? 0.22F : 0.0F) + (stunned ? 0.36F : 0.0F) + skillFlare * 0.20F;
        float tintR = hurt ? 1.00F : (od ? 1.00F : 0.92F);
        float tintG = hurt ? 0.40F : (od ? 0.62F : 0.88F);
        float tintB = hurt ? 0.45F : (od ? 0.56F : 1.00F);

        pose.pushPose();
        pose.translate(0.0D, (stunned ? 1.35D : 2.05D) + bob, 0.0D);

        // Pass 1: Angular Crowned Head, Curved Armored Rib-Cage, Pitch-Black Singularity Heart,
        //         6 Rear Halo Monolith Wings, & 4 Detached Floating Multi-Jointed Energy Gauntlet Arms!
        VertexConsumer armor = buffers.getBuffer(RenderType.entityCutoutNoCull(kind.texture));
        Matrix4f rootM = pose.last().pose();

        // Pitch-black faceted singularity heart spinning inside the rib-cage
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 88.0F * spd));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        HeroRenderPrimitives.box(armor, pose.last().pose(), -0.44F, -0.44F, -0.44F, 0.44F, 0.44F, 0.44F,
                od ? 0.28F : 0.06F, 0.04F, od ? 0.12F : 0.14F, 1.0F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Angular crowned head + 3 tall royal spire horns atop torso (y = +1.00..+1.95)
        HeroRenderPrimitives.box(armor, rootM, -0.34F, 1.02F, -0.32F, 0.34F, 1.56F, 0.32F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, rootM, -0.09F, 1.56F, -0.12F, 0.09F, 2.02F, 0.12F, tintR, tintG, tintB, 1.0F, light);
        for (int side : SIDES) {
            pose.pushPose();
            pose.translate(side * 0.28F, 1.52D, 0.0D);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -22.0F));
            HeroRenderPrimitives.box(armor, pose.last().pose(), -0.08F, 0.0F, -0.09F, 0.08F, 0.56F, 0.09F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }
        // Armored dorsal spine & lower pelvic anchor pylon
        HeroRenderPrimitives.box(armor, rootM, -0.24F, -1.24F, 0.36F, 0.24F, 1.02F, 0.68F, tintR, tintG, tintB, 1.0F, light);
        HeroRenderPrimitives.box(armor, rootM, -0.46F, -1.28F, -0.36F, 0.46F, -0.92F, 0.38F, tintR, tintG, tintB, 1.0F, light);

        // Curved Armored Rib-Cage (4 pairs of sweeping ribs opening wide in Overdrive / Stun!)
        for (int rIdx = 0; rIdx < 4; rIdx++) {
            float ry = 0.68F - rIdx * 0.42F;
            float ribSpread = 0.64F + openCage + (rIdx == 1 || rIdx == 2 ? 0.14F : 0.0F);
            for (int side : SIDES) {
                pose.pushPose();
                pose.translate(side * ribSpread, ry, 0.14D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (-26.0F - openCage * 50.0F)));
                HeroRenderPrimitives.box(armor, pose.last().pose(),
                        -0.11F, -0.10F, -0.66F, 0.11F, 0.10F, 0.36F, tintR, tintG, tintB, 1.0F, light);
                pose.popPose();
            }
        }

        // 6 Rear Halo Monolith Wings arranged in a divine vertical ring behind the boss!
        float haloR = 1.95F + (od ? 0.35F : 0.0F) + skillFlare * 0.55F;
        float haloRot = tSec * 20.0F * spd;
        for (int i = 0; i < 6; i++) {
            float angDeg = haloRot + i * 60.0F;
            float localPulse = 10.0F * (float) Math.sin(tSec * 3.8D + i * 1.05D);
            pose.pushPose();
            pose.translate(0.0D, 0.25D, 0.92D);
            pose.mulPose(Axis.ZP.rotationDegrees(angDeg));
            pose.translate(0.0D, haloR, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(-16.0F - (od ? 18.0F : 0.0F) - skillFlare * 22.0F + localPulse));
            Matrix4f mm = pose.last().pose();
            HeroRenderPrimitives.box(armor, mm, -0.18F, -0.55F, -0.12F, 0.18F, 0.85F, 0.12F, tintR, tintG, tintB, 1.0F, light);
            HeroRenderPrimitives.box(armor, mm, -0.10F, 0.85F, -0.08F, 0.10F, 1.22F, 0.08F, tintR, tintG, tintB, 1.0F, light);
            pose.popPose();
        }

        // 4 Detached Floating Multi-Jointed Energy Gauntlet Arms (upper L/R + lower L/R)
        for (int tier : SIDES) {
            for (int side : SIDES) {
                float armWave = 14.0F * (float) Math.sin(tSec * 3.2D + tier * 1.1D + side);
                pose.pushPose();
                pose.translate(side * (1.38F + (tier > 0 ? 0.25F : 0.0F) + skillFlare * 0.35F),
                        0.28D + tier * 0.58D + 0.12D * Math.sin(tSec * 3.0D + tier),
                        -0.25D - skillFlare * 0.45D);
                pose.mulPose(Axis.YP.rotationDegrees(side * (-18.0F + armWave)));
                pose.mulPose(Axis.XP.rotationDegrees(-22.0F - skillFlare * 30.0F));
                // Detached armored pauldron + articulated forearm gauntlet + twin energy claw blades
                HeroRenderPrimitives.box(armor, pose.last().pose(), -0.22F, -0.20F, -0.22F, 0.22F, 0.22F, 0.22F, tintR, tintG, tintB, 1.0F, light);
                pose.translate(0.0D, -0.42D, -0.28D);
                pose.mulPose(Axis.XP.rotationDegrees(32.0F));
                HeroRenderPrimitives.box(armor, pose.last().pose(), -0.16F, -0.36F, -0.34F, 0.16F, 0.16F, 0.20F, tintR, tintG, tintB, 1.0F, light);
                pose.popPose();
            }
        }

        // Pass 2: 3 Interlocking Gimbal Rings + 6 Halo Monolith Cores (Crimson-Gold in Overdrive!)
        VertexConsumer primary = buffers.getBuffer(RenderType.entityTranslucent(
                od ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_PURPLE));
        float prR = od ? 1.00F : 0.88F;
        float prG = od ? (0.38F + skillFlare * 0.45F) : (0.30F + skillFlare * 0.25F);
        float prB = od ? (0.18F + skillFlare * 0.30F) : 1.00F;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 82.0F * spd));
        pose.mulPose(Axis.XP.rotationDegrees(24.0F));
        HeroRenderPrimitives.energyRingXZ(pose, primary, 1.54F + skillFlare * 0.18F, 0.068F, 0.060F,
                prR, prG, prB, 0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-42.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-tSec * 118.0F * spd));
        HeroRenderPrimitives.energyRingXZ(pose, primary, 1.24F + skillFlare * 0.14F, 0.060F, 0.054F,
                prR, Math.min(1.0F, prG + 0.16F), prB, 0.94F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-62.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(28.0F));
        pose.mulPose(Axis.YP.rotationDegrees(tSec * 165.0F * spd));
        HeroRenderPrimitives.energyRingXZ(pose, primary, 0.96F + skillFlare * 0.10F, 0.050F, 0.046F,
                1.00F, od ? 0.78F : 0.48F, od ? 0.36F : 1.00F, 0.96F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // 6 Halo Monolith luminous energy spines
        for (int i = 0; i < 6; i++) {
            float angDeg = haloRot + i * 60.0F;
            float localPulse = 10.0F * (float) Math.sin(tSec * 3.8D + i * 1.05D);
            pose.pushPose();
            pose.translate(0.0D, 0.25D, 0.92D);
            pose.mulPose(Axis.ZP.rotationDegrees(angDeg));
            pose.translate(0.0D, haloR, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(-16.0F - (od ? 18.0F : 0.0F) - skillFlare * 22.0F + localPulse));
            HeroRenderPrimitives.box(primary, pose.last().pose(), -0.09F, -0.45F, -0.15F, 0.09F, 1.12F, 0.15F,
                    prR, Math.min(1.0F, prG + 0.22F), prB, 0.94F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        // Pass 3: Singularity Lensing Halo, Crown Visor, Gauntlet Claw Emitters & Stunned Exposed Core Glow!
        VertexConsumer accent = buffers.getBuffer(RenderType.entityTranslucent(
                (od || stunned) ? HeroRenderPrimitives.TEX_GLOW_ORANGE : HeroRenderPrimitives.TEX_GLOW_CYAN));
        float acR = (od || stunned) ? 1.00F : 0.34F;
        float acG = stunned ? 0.96F : (od ? 0.84F : 0.96F);
        float acB = stunned ? 0.52F : (od ? 0.34F : 1.00F);

        // Glowing crowned head visor
        HeroRenderPrimitives.box(accent, rootM, -0.26F, 1.24F, -0.35F, 0.26F, 1.36F, -0.29F,
                acR, acG, acB, 1.0F, LightTexture.FULL_BRIGHT);
        // Gravitational lensing shell around the singularity heart
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-tSec * 96.0F * spd));
        pose.mulPose(Axis.XP.rotationDegrees(tSec * 64.0F * spd));
        float lr = (stunned ? 0.84F : (od ? 0.72F : 0.62F)) + 0.06F * (float) Math.sin(tSec * 7.0D);
        HeroRenderPrimitives.box(accent, pose.last().pose(), -lr, -lr, -lr, lr, lr, lr,
                acR, acG, acB, stunned ? 0.86F : 0.62F, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Pass 4: Translucent Hexagonal Absolute Phase Barrier Dome while Anchor Crystals remain alive!
        if (barrier) {
            VertexConsumer dome = buffers.getBuffer(RenderType.entityTranslucent(TEX_PHASE_SHIELD));
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(tSec * 32.0F));
            float dr = 2.05F + 0.08F * (float) Math.sin(tSec * 6.0D);
            HeroRenderPrimitives.box(dome, pose.last().pose(), -dr, -dr * 0.85F, -dr, dr, dr * 0.95F, dr,
                    0.92F, 0.44F, 1.00F, 0.46F, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }

        pose.popPose();
    }
}
