package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.EnergyBowItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.QuantumBladeItem;
import com.industrialcore.item.RailgunItem;
import com.industrialcore.network.LaserModeTogglePayload;
import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.network.WeaponSwingPayload;
import com.industrialcore.weapon.LaserAimRange;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Phase 101: Client-side 3D world-space renderer for Electric Weapon effects:
 * <ul>
 *   <li>Continuous Pulse Laser beams &amp; Prism Lens refraction chains</li>
 *   <li>Lingering Railgun electromagnetic afterglow beams</li>
 *   <li>Sweeping Quantum Blade plasma slash arcs &amp; Blink trails</li>
 *   <li>Rotating Energy Bow gravity singularity vortex rings</li>
 *   <li>Railgun {@code att_wall_scanner} through-wall hostile AR bounding boxes</li>
 * </ul>
 */
public final class WeaponWorldFxRenderer {
    public static final ResourceLocation TEX_LASER_CORE = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/laser_beam_core.png");
    public static final ResourceLocation TEX_LASER_AURA = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/laser_beam_aura.png");
    public static final ResourceLocation TEX_LASER_FLARE = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/laser_flare.png");
    public static final ResourceLocation TEX_BLADE_SLASH_ARC = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/blade_slash_arc.png");
    public static final ResourceLocation TEX_RAILGUN_CANNON = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/railgun_cannon_beam.png");
    public static final ResourceLocation TEX_BOW_CHARGE_RING = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/bow_charge_ring.png");

    private static final class ActiveFx {
        final int kind;
        final Vec3 p0;
        final Vec3 p1;
        final int argb;
        final int maxTicks;
        final float radius;
        final float charge;
        final boolean hittingTarget;
        int ageTicks;

        ActiveFx(WeaponBeamFxPayload p) {
            this.kind = p.fxKind();
            this.p0 = new Vec3(p.x0(), p.y0(), p.z0());
            this.p1 = new Vec3(p.x1(), p.y1(), p.z1());
            this.argb = p.colorArgb();
            this.maxTicks = Math.max(1, p.durationTicks());
            this.radius = p.radius();
            this.charge = Math.max(0.0F, Math.min(1.0F, p.charge()));
            this.hittingTarget = p.hittingTarget();
            this.ageTicks = 0;
        }
    }

    private static final List<ActiveFx> ACTIVE_FX = new ArrayList<>();
    private static int lockedTargetId = -1;

    private WeaponWorldFxRenderer() {}

    public static int getLockedTargetId() {
        return lockedTargetId;
    }

    /** Phase 119: Hard cap on active 3D world FX entries &amp; per-event client particle bursts. */
    public static final int MAX_ACTIVE_FX = 96;
    public static final int MAX_PARTICLES_PER_FX_BURST = 12;

    /** Pure-Java particle burst clamp respecting hard cap and {@code ParticleStatus.MINIMAL}. */
    public static int clampFxParticleCount(int requested, boolean minimalSetting) {
        return com.industrialcore.raid.DimensionalRaidLogic.clampFxParticleCount(requested, minimalSetting);
    }

    public static void addFx(WeaponBeamFxPayload payload) {
        if (ACTIVE_FX.size() >= MAX_ACTIVE_FX) {
            ACTIVE_FX.remove(0);
        }
        ACTIVE_FX.add(new ActiveFx(payload));
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        boolean minimal = mc.options != null
                && mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL;
        var r = mc.level.random;
        int kind = payload.fxKind();

        // Phase 116: Client-side impact sparks shared identically by Pulse Laser & Laser Turret!
        if (kind == WeaponBeamFxPayload.KIND_LASER_BEAM && payload.hittingTarget()) {
            int n = clampFxParticleCount(3, minimal);
            for (int i = 0; i < n; i++) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        payload.x1() + (r.nextDouble() - 0.5D) * 0.28D,
                        payload.y1() + (r.nextDouble() - 0.5D) * 0.28D,
                        payload.z1() + (r.nextDouble() - 0.5D) * 0.28D,
                        (r.nextDouble() - 0.5D) * 0.14D,
                        (r.nextDouble() - 0.5D) * 0.14D,
                        (r.nextDouble() - 0.5D) * 0.14D);
            }
            if (n > 0) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        payload.x1(), payload.y1(), payload.z1(),
                        (r.nextDouble() - 0.5D) * 0.08D, 0.04D, (r.nextDouble() - 0.5D) * 0.08D);
            }
        } else if (kind == WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX) {
            int n = clampFxParticleCount(payload.hittingTarget() ? 12 : 8, minimal);
            for (int i = 0; i < n; i++) {
                double ang = (i * Math.PI * 2.0D) / Math.max(1, n);
                double rad = 0.55D + r.nextDouble() * Math.min(2.2D, payload.radius() * 0.5D);
                mc.level.addParticle(i % 2 == 0
                                ? net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL
                                : net.minecraft.core.particles.ParticleTypes.END_ROD,
                        payload.x0() + Math.cos(ang) * rad,
                        payload.y0() + 0.3D + r.nextDouble() * 1.4D,
                        payload.z0() + Math.sin(ang) * rad,
                        -Math.sin(ang) * 0.12D, 0.14D + r.nextDouble() * 0.10D, Math.cos(ang) * 0.12D);
            }
        } else if (kind == WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT) {
            boolean pierce = payload.charge() >= 0.5F;
            int n = clampFxParticleCount(pierce ? 10 : 8, minimal);
            for (int i = 0; i < n; i++) {
                var pType = pierce
                        ? (i % 2 == 0 ? net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK
                                      : net.minecraft.core.particles.ParticleTypes.CRIT)
                        : (i % 2 == 0 ? net.minecraft.core.particles.ParticleTypes.WITCH
                                      : net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL);
                mc.level.addParticle(pType,
                        payload.x0() + (r.nextDouble() - 0.5D) * 0.45D,
                        payload.y0() + (r.nextDouble() - 0.5D) * 0.55D,
                        payload.z0() + (r.nextDouble() - 0.5D) * 0.45D,
                        (r.nextDouble() - 0.5D) * (pierce ? 0.28D : 0.18D),
                        (r.nextDouble() - 0.2D) * 0.20D,
                        (r.nextDouble() - 0.5D) * (pierce ? 0.28D : 0.18D));
            }
        } else if (kind == WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE) {
            int n = clampFxParticleCount(12, minimal);
            for (int i = 0; i < n; i++) {
                double ang = (i * Math.PI * 2.0D) / Math.max(1, n);
                mc.level.addParticle(i % 2 == 0
                                ? net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH
                                : net.minecraft.core.particles.ParticleTypes.END_ROD,
                        payload.x0(), payload.y0(), payload.z0(),
                        Math.cos(ang) * 0.36D, 0.08D + (i % 3) * 0.07D, Math.sin(ang) * 0.36D);
            }
        }
    }

    public static void clear() {
        ACTIVE_FX.clear();
        lockedTargetId = -1;
    }

    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        if (mc.player.isUsingItem()) {
            ItemStack using = mc.player.getUseItem();
            if (using.getItem() instanceof PulseLaserItem
                    && ElectricWeaponItem.isAttachmentEnabled(using, WeaponAttachment.AUTO_AIM)) {
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                float deltaTicks = event.getPartialTick().getGameTimeDeltaTicks();
                applyAutoAimSnap(mc.player, using, partial, deltaTicks);
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.level != null && !mc.isPaused()) {
            // Precision camera-snap auto-aim while actively firing Pulse Laser with att_auto_aim!
            if (mc.player.isUsingItem()) {
                ItemStack using = mc.player.getUseItem();
                if (using.getItem() instanceof PulseLaserItem
                        && ElectricWeaponItem.isAttachmentEnabled(using, WeaponAttachment.AUTO_AIM)) {
                    applyAutoAimSnap(mc.player, using, 1.0F, 1.0F);
                } else {
                    lockedTargetId = -1;
                }
            } else {
                lockedTargetId = -1;
            }
        }

        if (ACTIVE_FX.isEmpty()) {
            return;
        }
        Iterator<ActiveFx> it = ACTIVE_FX.iterator();
        while (it.hasNext()) {
            ActiveFx fx = it.next();
            fx.ageTicks++;
            if (fx.ageTicks >= fx.maxTicks) {
                it.remove();
            }
        }
    }

    /**
     * High-precision auto-aim snap with {@code partialTick} interpolation, velocity lead
     * prediction, lock hysteresis, and zero-error adsorption within 6 degrees.
     */
    @org.jetbrains.annotations.Nullable
    public static LivingEntity applyAutoAimSnap(Player player, ItemStack laserStack) {
        return applyAutoAimSnap(player, laserStack, 1.0F, 1.0F);
    }

    @org.jetbrains.annotations.Nullable
    public static LivingEntity applyAutoAimSnap(Player player, ItemStack laserStack,
                                                float partialTick, float stepFraction) {
        if (player == null || player.level() == null || !(laserStack.getItem() instanceof PulseLaserItem)) {
            lockedTargetId = -1;
            return null;
        }
        if (!ElectricWeaponItem.isAttachmentEnabled(laserStack, WeaponAttachment.AUTO_AIM)) {
            lockedTargetId = -1;
            return null;
        }
        LivingEntity target = PulseLaserItem.findAutoAimTarget(
                player.level(), player, laserStack, lockedTargetId, partialTick);
        if (target == null) {
            lockedTargetId = -1;
            return null;
        }
        lockedTargetId = target.getId();

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 interpPos = target.getPosition(partialTick);
        Vec3 center = target.getBoundingBox().getCenter().add(interpPos.subtract(target.position()));
        Vec3 vel = target.getDeltaMovement();
        VirtualWeaponLogic.Vec3d lead = VirtualWeaponLogic.predictTargetPosition(
                center.x, center.y, center.z, vel.x, vel.y, vel.z, VirtualWeaponLogic.LASER_LEAD_TICKS);
        VirtualWeaponLogic.LookAngles desired = VirtualWeaponLogic.computeLookAngles(
                eye.x, eye.y, eye.z, lead.x(), lead.y(), lead.z());

        LaserAimRange range = PulseLaserItem.getAimRange(laserStack);
        float baseCap = range.isFull360() ? 55.0F : 36.0F;
        float frac = Math.max(0.08F, Math.min(1.0F, stepFraction));
        float smooth = 1.0F - (float) Math.pow(1.0F - 0.72F, frac);
        float maxStep = Math.max(6.0F, baseCap * frac);

        VirtualWeaponLogic.LookAngles next = VirtualWeaponLogic.stepAimRotation(
                player.getYRot(), player.getXRot(),
                desired.yawDeg(), desired.pitchDeg(),
                smooth, maxStep, VirtualWeaponLogic.LASER_AIM_SNAP_THRESHOLD_DEG);
        player.setYRot(next.yawDeg());
        player.yRotO = next.yawDeg();
        player.setXRot(next.pitchDeg());
        player.xRotO = next.pitchDeg();
        player.yHeadRot = next.yawDeg();
        player.yHeadRotO = next.yawDeg();
        return target;
    }

    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof QuantumBladeItem) {
            WeaponFirstPersonRenderer.triggerBladeSwing();
            PacketDistributor.sendToServer(WeaponSwingPayload.INSTANCE);
        } else if (stack.getItem() instanceof PulseLaserItem && event.getEntity().isShiftKeyDown()) {
            PacketDistributor.sendToServer(LaserModeTogglePayload.INSTANCE);
        }
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof QuantumBladeItem
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            WeaponFirstPersonRenderer.triggerBladeSwing();
            PacketDistributor.sendToServer(WeaponSwingPayload.INSTANCE);
        } else if (stack.getItem() instanceof PulseLaserItem && event.getEntity().isShiftKeyDown()) {
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                PacketDistributor.sendToServer(LaserModeTogglePayload.INSTANCE);
            }
            event.setCanceled(true);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }

        Player player = mc.player;
        ItemStack main = player.getMainHandItem();
        boolean showWallScan = main.getItem() instanceof RailgunItem
                && ElectricWeaponItem.isAttachmentEnabled(main, WeaponAttachment.WALL_SCANNER);
        boolean drawingEnergyBow = player.isUsingItem() && player.getUseItem().getItem() instanceof EnergyBowItem;

        if (ACTIVE_FX.isEmpty() && !showWallScan && !drawingEnergyBow) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = pose.last().pose();

        // 1. Render 3D Additive Sci-Fi Weapon FX (Pulse Laser, Wave Cannon, Blade Slash/Dash, Bow Pillar & Black Hole)
        if (!ACTIVE_FX.isEmpty()) {
            renderAdditiveSciFiLasers(mat, cam, partial);
            renderAdditiveWeaponReworkFx(mat, cam, partial);
        }

        // 1C. Energy Bow Converging Additive Optical Magic Circle while drawing!
        if (drawingEnergyBow) {
            renderEnergyBowDrawRing(mat, cam, player, player.getUseItem(), partial);
        }

        // 2. Railgun Wall Scanner: Through-wall crimson/cyan AR bounding boxes for hostiles within 48m!
        if (showWallScan) {
            AABB scanArea = player.getBoundingBox().inflate(VirtualWeaponLogic.RAILGUN_WALL_SCAN_RANGE);
            List<LivingEntity> hostiles = mc.level.getEntitiesOfClass(LivingEntity.class, scanArea,
                    e -> e != player && e.isAlive() && !e.isSpectator() && e instanceof Enemy);
            if (!hostiles.isEmpty()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);

                float alpha = (player.isUsingItem() || player.isShiftKeyDown()) ? 0.92F : 0.72F;
                BufferBuilder boxQuads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (LivingEntity mob : hostiles) {
                    AABB b = mob.getBoundingBox().inflate(0.10D);
                    emitWireBoxQuads(boxQuads, mat, b, cam, 0.025F, 1.0F, 0.28F, 0.44F, alpha);
                }
                var boxMesh = boxQuads.build();
                if (boxMesh != null) {
                    BufferUploader.drawWithShader(boxMesh);
                }

                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
        }

        pose.popPose();
    }

    /**
     * Phase 105.8: Computes the exact 1st-person world-space muzzle tip of the local player's
     * {@code pulse_laser} so the continuous laser beam &amp; muzzle flare erupt directly from the
     * rendered barrel tip with zero lag or parallax offset.
     */
    private static Vec3 resolveLaserOrigin(ActiveFx fx, Vec3 cam, float partial) {
        if (fx.kind != WeaponBeamFxPayload.KIND_LASER_BEAM) {
            return fx.p0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
            return fx.p0;
        }
        Player p = mc.player;
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof PulseLaserItem)) {
            return fx.p0;
        }
        Vec3 eye = p.getEyePosition(partial);
        if (fx.p0.distanceToSqr(eye) > 4.0D) {
            return fx.p0;
        }
        Vec3 fwd = p.getViewVector(partial).normalize();
        Vec3 right = fwd.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 camUp = right.cross(fwd).normalize();
        // Matches WeaponFirstPersonRenderer.renderCustomPulseLaserHand exact horizontal barrel tip!
        return eye.add(fwd.scale(1.06D)).add(right.scale(0.24D)).add(camUp.scale(-0.18D));
    }

    /**
     * Renders true Additive-Blended, UV-scrolling 3-layer Sci-Fi lasers ({@code laser_beam_core.png}
     * + {@code laser_beam_aura.png}) and rotating muzzle/impact plasma flares ({@code laser_flare.png}).
     */
    private static void renderAdditiveSciFiLasers(Matrix4f mat, Vec3 cam, float partial) {
        boolean anyLaser = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind == WeaponBeamFxPayload.KIND_LASER_BEAM
                    || fx.kind == WeaponBeamFxPayload.KIND_PRISM_CHAIN) {
                anyLaser = true;
                break;
            }
        }
        if (!anyLaser) {
            return;
        }

        float tSec = (Util.getMillis() % 100_000L) / 1000.0F;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // Pass A: Layer 1 (Incandescent White Core) + Layer 2 (Charge-Temperature Color Plasma)
        RenderSystem.setShaderTexture(0, TEX_LASER_CORE);
        BufferBuilder coreBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasCore = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_LASER_BEAM
                    && fx.kind != WeaponBeamFxPayload.KIND_PRISM_CHAIN) {
                continue;
            }
            Vec3 origin = resolveLaserOrigin(fx, cam, partial);
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.08F, 1.0F - progress * 0.85F);
            float c = fx.charge;
            float[] rgb = laserChargeRgb(c, fx.kind == WeaponBeamFxPayload.KIND_PRISM_CHAIN);

            float pulse = 1.0F + (0.04F + 0.11F * c) * (float) Math.sin(tSec * (24.0F + 14.0F * c));
            float baseW = fx.radius * (0.72F + 0.42F * fade) * pulse;
            float wCore = baseW * (0.42F + 0.16F * c);
            float vTile = Math.max(0.8F, (float) (origin.distanceTo(fx.p1) * 0.42D));
            float vScrollMid = -tSec * (4.2F + 3.6F * c);
            float vScrollCore = -tSec * (6.0F + 4.4F * c);

            // Layer 2: Color plasma beam (billboard + orthogonal cross for volumetric fullness)
            emitTexturedBeamCross(coreBuf, mat, origin, fx.p1, cam, baseW * 1.15F,
                    vScrollMid, vScrollMid + vTile, rgb[0], rgb[1], rgb[2], (0.78F + 0.22F * c) * fade);
            // Layer 1: Razor-sharp incandescent pure-white core
            emitTexturedBeamCross(coreBuf, mat, origin, fx.p1, cam, wCore,
                    vScrollCore, vScrollCore + vTile, 1.0F, 1.0F, 1.0F, (0.90F + 0.10F * c) * fade);
            hasCore = true;
        }
        if (hasCore) {
            var mesh = coreBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass B: Layer 3 (Helical Swirling Outer Plasma Aura, counter-scrolling with charge surge!)
        RenderSystem.setShaderTexture(0, TEX_LASER_AURA);
        BufferBuilder auraBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasAura = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_LASER_BEAM
                    && fx.kind != WeaponBeamFxPayload.KIND_PRISM_CHAIN) {
                continue;
            }
            Vec3 origin = resolveLaserOrigin(fx, cam, partial);
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.08F, 1.0F - progress * 0.85F);
            float c = fx.charge;
            float[] rgb = laserChargeRgb(c, fx.kind == WeaponBeamFxPayload.KIND_PRISM_CHAIN);

            float pulse = 1.0F + (0.06F + 0.14F * c) * (float) Math.sin(tSec * (28.0F + 16.0F * c) + 1.3F);
            float wAura = fx.radius * (1.35F + 0.72F * c) * (0.75F + 0.35F * fade) * pulse;
            float vTile = Math.max(0.6F, (float) (origin.distanceTo(fx.p1) * 0.32D));
            // Reverse/counter UV scroll creates energetic vortex sheen around the forward core
            float vScrollAura = tSec * (2.8F + 3.2F * c);
            float alpha = (0.34F + 0.50F * c) * fade;
            emitTexturedBeamCross(auraBuf, mat, origin, fx.p1, cam, wAura,
                    vScrollAura, vScrollAura + vTile, rgb[0], rgb[1], rgb[2], alpha);
            hasAura = true;
        }
        if (hasAura) {
            var mesh = auraBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass C: Rotating Muzzle Energy Flare & High-Intensity Impact Scorch Flare
        RenderSystem.setShaderTexture(0, TEX_LASER_FLARE);
        BufferBuilder flareBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasFlare = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_LASER_BEAM
                    && fx.kind != WeaponBeamFxPayload.KIND_PRISM_CHAIN) {
                continue;
            }
            Vec3 origin = resolveLaserOrigin(fx, cam, partial);
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.08F, 1.0F - progress * 0.85F);
            float c = fx.charge;
            float[] rgb = laserChargeRgb(c, fx.kind == WeaponBeamFxPayload.KIND_PRISM_CHAIN);

            // Rotating Muzzle Flare (main beam origin locked to barrel tip)
            if (fx.kind == WeaponBeamFxPayload.KIND_LASER_BEAM) {
                float muzzleR = (0.16F + 0.22F * c) * (0.85F + 0.15F * (float) Math.sin(tSec * 30.0F));
                emitFlareBillboard(flareBuf, mat, origin, cam, muzzleR, tSec * 4.2F,
                        rgb[0], rgb[1], rgb[2], (0.72F + 0.25F * c) * fade);
                emitFlareBillboard(flareBuf, mat, origin, cam, muzzleR * 0.56F, -tSec * 5.8F,
                        1.0F, 1.0F, 1.0F, 0.92F * fade);
            }

            // Intense Impact / Scorch Plasma Flare at hit point
            float hitBoost = fx.hittingTarget ? 1.35F : 1.0F;
            float impactR = (0.28F + 0.58F * c) * hitBoost * (0.90F + 0.14F * (float) Math.cos(tSec * 36.0F));
            emitFlareBillboard(flareBuf, mat, fx.p1, cam, impactR, -tSec * 6.4F,
                    rgb[0], rgb[1], rgb[2], (0.76F + 0.24F * c) * fade);
            emitFlareBillboard(flareBuf, mat, fx.p1, cam, impactR * (0.48F + 0.18F * c), tSec * 8.2F,
                    1.0F, 1.0F, 1.0F, (0.88F + 0.12F * c) * fade);
            hasFlare = true;
        }
        if (hasFlare) {
            var mesh = flareBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Renders additive 3D VFX for:
     * <ul>
     *   <li>Quantum Blade katana crescent slash arcs ({@code KIND_BLADE_SLASH_ARC}) &amp; flying
     *       crescent waves ({@code KIND_PLASMA_WAVE}) using {@code blade_slash_arc.png}</li>
     *   <li>Phase Dash cyan/emerald afterimage ribbon &amp; flash rings ({@code KIND_BLINK_TRAIL})</li>
     *   <li>Railgun colossal 1.35-block Plasma Wave ({@code KIND_RAILGUN_BEAM}) using
     *       {@code railgun_cannon_beam.png} + multi-rotating barrel shockwave rings</li>
     *   <li>Energy Bow skyward light pillar ({@code KIND_BOW_IMPACT_PILLAR}) &amp; Black Hole
     *       singularity void core + spinning neon-purple accretion disk ({@code KIND_GRAVITY_FIELD})</li>
     * </ul>
     */
    private static void renderAdditiveWeaponReworkFx(Matrix4f mat, Vec3 cam, float partial) {
        float tSec = (Util.getMillis() % 100_000L) / 1000.0F;

        // 0. Dark event-horizon core for active Black Hole gravity fields (standard alpha blend)
        boolean anyGravity = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind == WeaponBeamFxPayload.KIND_GRAVITY_FIELD) {
                anyGravity = true;
                break;
            }
        }
        if (anyGravity) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder darkBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (ActiveFx fx : ACTIVE_FX) {
                if (fx.kind != WeaponBeamFxPayload.KIND_GRAVITY_FIELD) {
                    continue;
                }
                float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
                float fade = Math.min(1.0F, (1.0F - progress) * 4.0F);
                emitDarkDiscBillboard(darkBuf, mat, fx.p0.add(0.0D, 0.55D, 0.0D), cam,
                        0.68F * (0.92F + 0.08F * (float) Math.sin(tSec * 9.0F)), 0.03F, 0.01F, 0.06F, 0.94F * fade);
            }
            var dMesh = darkBuf.build();
            if (dMesh != null) {
                BufferUploader.drawWithShader(dMesh);
            }
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }

        // Now switch to True Additive Blending for all glowing Sci-Fi weapon passes!
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // Pass 1: Quantum Blade Katana Crescent Slash Arcs & Flying Plasma Waves (blade_slash_arc.png)
        RenderSystem.setShaderTexture(0, TEX_BLADE_SLASH_ARC);
        BufferBuilder arcBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasArc = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_BLADE_SLASH_ARC
                    && fx.kind != WeaponBeamFxPayload.KIND_PLASMA_WAVE) {
                continue;
            }
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.0F, 1.0F - progress * progress);
            float r = ((fx.argb >> 16) & 0xFF) / 255.0F;
            float g = ((fx.argb >> 8) & 0xFF) / 255.0F;
            float b = (fx.argb & 0xFF) / 255.0F;
            Vec3 dir = fx.p1.subtract(fx.p0);
            if (dir.lengthSqr() < 1.0E-5D) {
                continue;
            }
            Vec3 fwd = dir.normalize();
            // Combo tilt: 0.0 -> -36° Kesagiri, 0.5 -> +36° Gyaku-Kesa, 1.0 -> 0° Horizontal Sweep
            float tiltDeg = fx.charge < 0.25F ? -36.0F : (fx.charge < 0.75F ? 36.0F : 0.0F);
            if (fx.kind == WeaponBeamFxPayload.KIND_BLADE_SLASH_ARC) {
                Vec3 center = fx.p0.lerp(fx.p1, 0.45D + 0.30D * progress);
                float size = fx.radius * (0.85F + 0.45F * progress);
                float sweepAngle = (float) Math.toRadians(lerpFloat(-55.0F, 40.0F, progress) * (tiltDeg >= 0.0F ? -1.0F : 1.0F));
                emitOrientedPlaneQuad(arcBuf, mat, center, fwd, (float) Math.toRadians(tiltDeg), sweepAngle,
                        size, r, g, b, 0.95F * fade);
                emitOrientedPlaneQuad(arcBuf, mat, center, fwd, (float) Math.toRadians(tiltDeg), sweepAngle,
                        size * 0.86F, 1.0F, 1.0F, 1.0F, 0.85F * fade);
                hasArc = true;
            } else {
                // Flying giant plasma crescent wave!
                Vec3 center = fx.p0.lerp(fx.p1, progress);
                float size = fx.radius * (0.90F + 0.55F * progress);
                emitOrientedPlaneQuad(arcBuf, mat, center, fwd, (float) Math.toRadians(tiltDeg * 0.5F), 0.0F,
                        size, r, g, b, 0.95F * fade);
                emitOrientedPlaneQuad(arcBuf, mat, center, fwd, (float) Math.toRadians(tiltDeg * 0.5F), 0.0F,
                        size * 0.78F, 1.0F, 1.0F, 1.0F, 0.90F * fade);
                hasArc = true;
            }
        }
        if (hasArc) {
            var mesh = arcBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass 2: Colossal Railgun Plasma Wave Column (railgun_cannon_beam.png)
        RenderSystem.setShaderTexture(0, TEX_RAILGUN_CANNON);
        BufferBuilder cannonBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasCannon = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_RAILGUN_BEAM) {
                continue;
            }
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            // Blasting full width on opening frames, then dramatic lingering 1.2s shrink-out
            float envelope = progress < 0.15F
                    ? (0.75F + 0.25F * (progress / 0.15F))
                    : (float) Math.pow(Math.max(0.0F, 1.0F - (progress - 0.15F) / 0.85F), 1.35D);
            float r = ((fx.argb >> 16) & 0xFF) / 255.0F;
            float g = ((fx.argb >> 8) & 0xFF) / 255.0F;
            float b = (fx.argb & 0xFF) / 255.0F;
            float wMain = fx.radius * (0.45F + 0.85F * envelope);
            float wCore = wMain * 0.54F;
            float vTile = Math.max(2.0F, (float) (fx.p0.distanceTo(fx.p1) * 0.24D));
            float vScroll = -tSec * 9.5F;

            emitTexturedBeamCross(cannonBuf, mat, fx.p0, fx.p1, cam, wMain * 1.35F,
                    vScroll, vScroll + vTile, r, g, b, 0.85F * envelope);
            emitTexturedBeamCross(cannonBuf, mat, fx.p0, fx.p1, cam, wMain,
                    vScroll * 1.3F, vScroll * 1.3F + vTile, 0.72F, 0.92F, 1.0F, 0.95F * envelope);
            emitTexturedBeamCross(cannonBuf, mat, fx.p0, fx.p1, cam, wCore,
                    vScroll * 1.6F, vScroll * 1.6F + vTile, 1.0F, 1.0F, 1.0F, 1.0F * envelope);
            hasCannon = true;
        }
        if (hasCannon) {
            var mesh = cannonBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass 3: Phase Dash Afterimage Ribbon, Bow Light Pillars, Rift Portal Pillars & Boss Collapse Column
        RenderSystem.setShaderTexture(0, TEX_LASER_CORE);
        BufferBuilder beamBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasBeam = false;
        for (ActiveFx fx : ACTIVE_FX) {
            if (fx.kind != WeaponBeamFxPayload.KIND_BLINK_TRAIL
                    && fx.kind != WeaponBeamFxPayload.KIND_BOW_IMPACT_PILLAR
                    && fx.kind != WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX
                    && fx.kind != WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE) {
                continue;
            }
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.0F, 1.0F - progress);
            float r = ((fx.argb >> 16) & 0xFF) / 255.0F;
            float g = ((fx.argb >> 8) & 0xFF) / 255.0F;
            float b = (fx.argb & 0xFF) / 255.0F;
            float vScroll = -tSec * 7.0F;
            if (fx.kind == WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX) {
                // Vertical purple & cyan breach energy column rising from ground spawn point
                float wCol = Math.min(1.15F, fx.radius * 0.24F) * (0.35F + 0.85F * fade);
                emitTexturedBeamCross(beamBuf, mat, fx.p0, fx.p1, cam, wCol * 1.45F,
                        vScroll, vScroll + 3.0F, r, g, b, 0.78F * fade);
                emitTexturedBeamCross(beamBuf, mat, fx.p0, fx.p1, cam, wCol * 0.52F,
                        vScroll * 1.4F, vScroll * 1.4F + 3.0F, 0.36F, 0.96F, 1.0F, 0.92F * fade);
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE) {
                // Colossal collapsing vertical singularity pillar narrowing inward as the boss shatters!
                float colEnv = (float) Math.pow(fade, 1.25D);
                float wCol = 1.65F * colEnv;
                emitTexturedBeamCross(beamBuf, mat, fx.p0.subtract(0.0D, 1.6D, 0.0D), fx.p1, cam, wCol * 1.45F,
                        vScroll * 1.5F, vScroll * 1.5F + 4.0F, 0.88F, 0.28F, 1.0F, 0.88F * colEnv);
                emitTexturedBeamCross(beamBuf, mat, fx.p0.subtract(0.0D, 1.6D, 0.0D), fx.p1, cam, wCol * 0.58F,
                        vScroll * 2.0F, vScroll * 2.0F + 4.0F, 0.48F, 0.98F, 1.0F, 0.96F * colEnv);
            } else {
                float w = fx.radius * (0.45F + 0.75F * fade);
                emitTexturedBeamCross(beamBuf, mat, fx.p0, fx.p1, cam, w * 1.4F,
                        vScroll, vScroll + 2.5F, r, g, b, 0.85F * fade);
                emitTexturedBeamCross(beamBuf, mat, fx.p0, fx.p1, cam, w * 0.55F,
                        vScroll, vScroll + 2.5F, 1.0F, 1.0F, 1.0F, 0.96F * fade);
            }
            hasBeam = true;
        }
        if (hasBeam) {
            var mesh = beamBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass 4: Rotating Energy Rings — Wave Cannon Barrel Rings, Black Hole Accretion Disk,
        //         Phase Dash Cut Rings, & Bow Impact Shock Ring (bow_charge_ring.png)
        RenderSystem.setShaderTexture(0, TEX_BOW_CHARGE_RING);
        BufferBuilder ringBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasRing = false;
        for (ActiveFx fx : ACTIVE_FX) {
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.0F, 1.0F - progress);
            float r = ((fx.argb >> 16) & 0xFF) / 255.0F;
            float g = ((fx.argb >> 8) & 0xFF) / 255.0F;
            float b = (fx.argb & 0xFF) / 255.0F;

            if (fx.kind == WeaponBeamFxPayload.KIND_RAILGUN_BEAM) {
                Vec3 dir = fx.p1.subtract(fx.p0);
                if (dir.lengthSqr() > 1.0E-4D) {
                    Vec3 fwd = dir.normalize();
                    // Expanding muzzle shockwave ring
                    float shockR = 1.1F + 2.8F * progress;
                    emitPerpendicularRingQuad(ringBuf, mat, fx.p0.add(fwd.scale(0.5D)), fwd,
                            shockR, tSec * 4.5F, 0.55F, 0.92F, 1.0F, 0.95F * fade);
                    // 5 counter-rotating energy barrel rings spaced down the Wave Cannon
                    for (int i = 1; i <= 5; i++) {
                        double dist = i * 6.5D + progress * 4.0D;
                        Vec3 ringPos = fx.p0.add(fwd.scale(dist));
                        float rr = fx.radius * (1.25F + 0.18F * i) * (0.55F + 0.55F * fade);
                        float spin = (i % 2 == 0 ? 1.0F : -1.0F) * (tSec * 5.2F + i);
                        emitPerpendicularRingQuad(ringBuf, mat, ringPos, fwd, rr, spin, r, g, b, 0.86F * fade);
                    }
                    hasRing = true;
                }
            } else if (fx.kind == WeaponBeamFxPayload.KIND_GRAVITY_FIELD) {
                // Spinning neon-purple Black Hole Accretion Disk + tilted gravitational lensing halo!
                Vec3 center = fx.p0.add(0.0D, 0.55D, 0.0D);
                float env = Math.min(1.0F, (1.0F - progress) * 4.0F);
                float diskR = 2.65F * (0.90F + 0.10F * (float) Math.sin(tSec * 8.0F));
                emitPerpendicularRingQuad(ringBuf, mat, center, new Vec3(0.0D, 1.0D, 0.14D).normalize(),
                        diskR, tSec * 3.8F, 0.78F, 0.28F, 1.0F, 0.95F * env);
                emitPerpendicularRingQuad(ringBuf, mat, center, new Vec3(0.0D, 1.0D, -0.14D).normalize(),
                        diskR * 0.72F, -tSec * 5.6F, 0.96F, 0.62F, 1.0F, 0.90F * env);
                emitFlareBillboard(ringBuf, mat, center, cam, diskR * 0.58F, tSec * 2.5F,
                        0.68F, 0.25F, 1.0F, 0.75F * env);
                hasRing = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BOW_IMPACT_PILLAR) {
                float shockR = 0.6F + 2.4F * progress;
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0.add(0.0D, 0.12D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
                        shockR, tSec * 4.0F, r, g, b, 0.92F * fade);
                hasRing = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BLINK_TRAIL) {
                Vec3 dir = fx.p1.subtract(fx.p0);
                if (dir.lengthSqr() > 1.0E-4D) {
                    Vec3 fwd = dir.normalize();
                    for (int i = 1; i <= 3; i++) {
                        Vec3 rp = fx.p0.lerp(fx.p1, i * 0.28D);
                        emitPerpendicularRingQuad(ringBuf, mat, rp, fwd, 0.95F + 0.55F * progress,
                                tSec * 6.0F * i, r, g, b, 0.82F * fade);
                    }
                    hasRing = true;
                }
            } else if (fx.kind == WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX) {
                // Swirling purple + cyan dual vortex rings expanding & rising from ground portal!
                float rOuter = fx.radius * (0.42F + 0.88F * progress);
                float rInner = fx.radius * (0.28F + 0.65F * progress);
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0.add(0.0D, 0.14D, 0.0D),
                        new Vec3(0.0D, 1.0D, 0.0D), rOuter, tSec * 4.8F, r, g, b, 0.94F * fade);
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0.add(0.0D, 0.25D + progress * 2.2D, 0.0D),
                        new Vec3(0.0D, 1.0D, 0.0D), rInner, -tSec * 6.4F, 0.30F, 0.96F, 1.0F, 0.90F * fade);
                hasRing = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT) {
                Vec3 dir = fx.p1.subtract(fx.p0);
                Vec3 normal = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
                boolean pierce = fx.charge >= 0.5F;
                float ringR = fx.radius * (0.35F + 1.05F * progress);
                if (pierce) {
                    // Incandescent cyan & gold armor-pierce shock burst ring
                    emitPerpendicularRingQuad(ringBuf, mat, fx.p0, normal, ringR, tSec * 8.0F,
                            1.0F, 0.86F, 0.34F, 0.96F * fade);
                    emitPerpendicularRingQuad(ringBuf, mat, fx.p0, normal, ringR * 0.68F, -tSec * 10.0F,
                            0.36F, 0.98F, 1.0F, 0.92F * fade);
                } else {
                    // Purple hexagonal shield deflection ripple ring
                    emitPerpendicularRingQuad(ringBuf, mat, fx.p0, normal, ringR, tSec * 5.0F,
                            0.84F, 0.32F, 1.0F, 0.95F * fade);
                    emitPerpendicularRingQuad(ringBuf, mat, fx.p0, normal, ringR * 0.62F, -tSec * 7.0F,
                            0.96F, 0.68F, 1.0F, 0.88F * fade);
                }
                hasRing = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE) {
                // Colossal Dual Expanding Purple & Cyan 3D Boss-Defeat Shockwave Rings (1.2m -> 13.5m)!
                float ease = 1.0F - (float) Math.pow(1.0F - progress, 2.2D);
                float purpleR = 1.2F + fx.radius * ease;
                float cyanR = 0.8F + (fx.radius * 0.82F) * Math.max(0.0F, (ease - 0.08F) / 0.92F);
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0, new Vec3(0.0D, 1.0D, 0.0D),
                        purpleR, tSec * 3.2F, 0.86F, 0.26F, 1.0F, 0.98F * fade);
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0.add(0.0D, 0.25D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
                        cyanR, -tSec * 4.4F, 0.30F, 0.96F, 1.0F, 0.95F * fade);
                // Tilted secondary dimensional shock halo
                emitPerpendicularRingQuad(ringBuf, mat, fx.p0, new Vec3(0.24D, 0.94D, 0.24D).normalize(),
                        purpleR * 0.72F, tSec * 5.0F, 1.0F, 0.56F, 0.92F, 0.80F * fade);
                hasRing = true;
            }
        }
        if (hasRing) {
            var mesh = ringBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        // Pass 5: Blinding Muzzle / Impact / Black Hole Core Flares (laser_flare.png)
        RenderSystem.setShaderTexture(0, TEX_LASER_FLARE);
        BufferBuilder flareBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasFlare = false;
        for (ActiveFx fx : ACTIVE_FX) {
            float progress = Math.min(1.0F, (fx.ageTicks + partial) / (float) fx.maxTicks);
            float fade = Math.max(0.0F, 1.0F - progress);
            float r = ((fx.argb >> 16) & 0xFF) / 255.0F;
            float g = ((fx.argb >> 8) & 0xFF) / 255.0F;
            float b = (fx.argb & 0xFF) / 255.0F;
            if (fx.kind == WeaponBeamFxPayload.KIND_RAILGUN_BEAM) {
                float fr = 1.9F * (0.5F + 0.65F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr, tSec * 4.0F, r, g, b, 0.95F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr * 0.58F, -tSec * 6.5F, 1.0F, 1.0F, 1.0F, 1.0F * fade);
                hasFlare = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BOW_IMPACT_PILLAR) {
                float fr = 1.65F * (0.5F + 0.65F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0.add(0.0D, 0.25D, 0.0D), cam, fr, -tSec * 5.0F, r, g, b, 0.95F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0.add(0.0D, 0.25D, 0.0D), cam, fr * 0.55F, tSec * 7.0F, 1.0F, 1.0F, 1.0F, 1.0F * fade);
                hasFlare = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BLINK_TRAIL) {
                emitFlareBillboard(flareBuf, mat, fx.p1, cam, 1.35F * fade, tSec * 7.0F, 0.30F, 1.0F, 0.85F, 0.95F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p1, cam, 0.72F * fade, -tSec * 9.0F, 1.0F, 1.0F, 1.0F, 0.95F * fade);
                hasFlare = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_GRAVITY_FIELD) {
                float env = Math.min(1.0F, (1.0F - progress) * 4.0F);
                emitFlareBillboard(flareBuf, mat, fx.p0.add(0.0D, 0.55D, 0.0D), cam, 1.35F, tSec * 4.5F,
                        0.82F, 0.35F, 1.0F, 0.72F * env);
                hasFlare = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT) {
                boolean pierce = fx.charge >= 0.5F;
                float fr = (pierce ? 1.55F : 1.25F) * fade;
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr, tSec * 7.5F,
                        pierce ? 1.0F : 0.86F, pierce ? 0.88F : 0.36F, pierce ? 0.40F : 1.0F, 0.95F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr * 0.52F, -tSec * 9.5F,
                        1.0F, 1.0F, 1.0F, 0.98F * fade);
                hasFlare = true;
            } else if (fx.kind == WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE) {
                float fr = 4.6F * (0.25F + 0.75F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr, tSec * 4.2F,
                        0.88F, 0.34F, 1.0F, 0.98F * fade);
                emitFlareBillboard(flareBuf, mat, fx.p0, cam, fr * 0.58F, -tSec * 6.5F,
                        0.45F, 0.98F, 1.0F, 1.0F * fade);
                hasFlare = true;
            }
        }
        if (hasFlare) {
            var mesh = flareBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Renders the converging additive SF optical magic circle in front of the Energy Bow while drawing. */
    private static void renderEnergyBowDrawRing(Matrix4f mat, Vec3 cam, Player player,
                                                ItemStack bow, float partial) {
        boolean autoDraw = ElectricWeaponItem.isAttachmentEnabled(bow, WeaponAttachment.AUTO_DRAW);
        boolean grav = ElectricWeaponItem.isAttachmentEnabled(bow, WeaponAttachment.GRAVITY_ARROW);
        int held = bow.getUseDuration(player) - player.getUseItemRemainingTicks();
        float draw = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
        float tSec = (Util.getMillis() % 100_000L) / 1000.0F;

        Minecraft mc = Minecraft.getInstance();
        boolean firstPersonScope = mc != null && player == mc.player
                && mc.options.getCameraType().isFirstPerson();

        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial).normalize();
        Vec3 ringPos = eye.add(look.scale(1.05D)).add(0.0D, -0.06D, 0.0D);

        // In first-person optical scope zoom, keep the 3D magic ring strictly peripheral & translucent
        // so the sniper's center line-of-sight and target remain 100% unobstructed!
        float outerR = firstPersonScope ? lerpFloat(0.72F, 0.48F, draw) : lerpFloat(0.68F, 0.34F, draw);
        float innerR = outerR * 0.62F;
        float fpFade = firstPersonScope ? lerpFloat(0.32F, 0.14F, draw) : 1.0F;
        float r = grav ? lerpFloat(0.55F, 0.96F, draw) : lerpFloat(0.28F, 1.00F, draw);
        float g = grav ? lerpFloat(0.32F, 0.45F, draw) : lerpFloat(1.00F, 0.88F, draw);
        float b = grav ? 1.00F : lerpFloat(0.75F, 0.38F, draw);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEX_BOW_CHARGE_RING);

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        emitPerpendicularRingQuad(bb, mat, ringPos, look, outerR, tSec * (2.6F + 3.5F * draw),
                r, g, b, (0.55F + 0.45F * draw) * fpFade);
        if (!firstPersonScope) {
            emitPerpendicularRingQuad(bb, mat, ringPos.add(look.scale(0.08D)), look, innerR,
                    -tSec * (4.2F + 4.0F * draw), 1.0F, 1.0F, 1.0F, 0.45F + 0.50F * draw);
        }
        var mesh = bb.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        // Blazing starlight focal flare when fully drawn (third-person only so first-person scope stays crystal clear)
        if (draw >= 0.85F && !firstPersonScope) {
            RenderSystem.setShaderTexture(0, TEX_LASER_FLARE);
            BufferBuilder fb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            emitFlareBillboard(fb, mat, ringPos, cam, 0.26F * draw, tSec * 6.0F, 1.0F, 0.98F, 0.82F, 0.90F);
            var fMesh = fb.build();
            if (fMesh != null) {
                BufferUploader.drawWithShader(fMesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Emits a ring/disc quad lying in the plane perpendicular to {@code normal}. */
    private static void emitPerpendicularRingQuad(BufferBuilder buf, Matrix4f mat, Vec3 center, Vec3 normal,
                                                  float radius, float rotRad,
                                                  float r, float g, float b, float a) {
        Vec3 n = normal.normalize();
        Vec3 upRef = Math.abs(n.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = upRef.cross(n).normalize();
        Vec3 up = n.cross(right).normalize();

        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);
        Vec3 rx = right.scale(cos * radius).add(up.scale(sin * radius));
        Vec3 ry = up.scale(cos * radius).subtract(right.scale(sin * radius));

        Vec3 v0 = center.subtract(rx).subtract(ry);
        Vec3 v1 = center.add(rx).subtract(ry);
        Vec3 v2 = center.add(rx).add(ry);
        Vec3 v3 = center.subtract(rx).add(ry);

        buf.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setUv(0.0F, 1.0F).setColor(r, g, b, a);
    }

    /** Emits an oriented slash plane quad aligned with player forward direction &amp; diagonal combo roll. */
    private static void emitOrientedPlaneQuad(BufferBuilder buf, Matrix4f mat, Vec3 center, Vec3 fwd,
                                              float rollRad, float yawSpinRad, float radius,
                                              float r, float g, float b, float a) {
        Vec3 f = fwd.normalize();
        Vec3 worldUp = Math.abs(f.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = f.cross(worldUp).normalize();
        Vec3 up = right.cross(f).normalize();

        // Roll the slash plane around forward axis (Kesagiri / Gyaku-Kesa / Horizontal)
        double cr = Math.cos(rollRad);
        double sr = Math.sin(rollRad);
        Vec3 slashRight = right.scale(cr).add(up.scale(sr)).normalize();

        // Spin crescent smoothly inside the slash plane along (fwd, slashRight)
        double cs = Math.cos(yawSpinRad);
        double ss = Math.sin(yawSpinRad);
        Vec3 uAxis = f.scale(cs * radius).add(slashRight.scale(ss * radius));
        Vec3 vAxis = slashRight.scale(cs * radius).subtract(f.scale(ss * radius));

        Vec3 v0 = center.subtract(uAxis).subtract(vAxis);
        Vec3 v1 = center.add(uAxis).subtract(vAxis);
        Vec3 v2 = center.add(uAxis).add(vAxis);
        Vec3 v3 = center.subtract(uAxis).add(vAxis);

        buf.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setUv(0.0F, 1.0F).setColor(r, g, b, a);
    }

    /** Emits an octagonal dark void core billboard for Black Hole singularities. */
    private static void emitDarkDiscBillboard(BufferBuilder buf, Matrix4f mat, Vec3 center, Vec3 cam,
                                              float radius, float r, float g, float b, float a) {
        Vec3 toCam = cam.subtract(center);
        if (toCam.lengthSqr() < 1.0E-5D) {
            return;
        }
        Vec3 fwd = toCam.normalize();
        Vec3 worldUp = Math.abs(fwd.y) > 0.96D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = worldUp.cross(fwd).normalize().scale(radius);
        Vec3 up = fwd.cross(right).normalize().scale(radius);

        int segs = 12;
        for (int i = 0; i < segs; i += 2) {
            double a0 = (i * Math.PI * 2.0D) / segs;
            double a1 = ((i + 1) * Math.PI * 2.0D) / segs;
            double a2 = ((i + 2) * Math.PI * 2.0D) / segs;
            Vec3 p0 = center.add(right.scale(Math.cos(a0))).add(up.scale(Math.sin(a0)));
            Vec3 p1 = center.add(right.scale(Math.cos(a1))).add(up.scale(Math.sin(a1)));
            Vec3 p2 = center.add(right.scale(Math.cos(a2))).add(up.scale(Math.sin(a2)));
            buf.addVertex(mat, (float) center.x, (float) center.y, (float) center.z).setColor(r, g, b, a);
            buf.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, a * 0.85F);
            buf.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a * 0.85F);
            buf.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a * 0.85F);
        }
    }

    private static float lerpFloat(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Smooth sci-fi laser color temperature shift across charge {@code [0..1]}:
     * {@code 0%}: crisp icy cyan -> {@code 50%}: electric cobalt-blue -> {@code 100%}: overdrive magenta-white.
     */
    private static float[] laserChargeRgb(float charge, boolean isPrismChain) {
        if (isPrismChain) {
            return new float[] {0.88F, 0.36F, 1.00F};
        }
        float c = Math.max(0.0F, Math.min(1.0F, charge));
        if (c <= 0.5F) {
            float t = c * 2.0F;
            return new float[] {
                    0.18F + 0.28F * t,
                    0.96F - 0.38F * t,
                    1.00F
            };
        }
        float t = (c - 0.5F) * 2.0F;
        return new float[] {
                0.46F + 0.54F * t,
                0.58F - 0.16F * t,
                1.00F - 0.04F * t
        };
    }

    /** Emits a crossed pair of UV-scrolled textured beam quads for volumetric 3D visibility. */
    private static void emitTexturedBeamCross(BufferBuilder buf, Matrix4f mat, Vec3 p0, Vec3 p1, Vec3 cam,
                                              float halfWidth, float v0, float v1,
                                              float r, float g, float b, float a) {
        Vec3 dir = p1.subtract(p0);
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 dirN = dir.normalize();
        Vec3 mid = p0.add(p1).scale(0.5D);
        Vec3 toCam = cam.subtract(mid);
        Vec3 side1 = dirN.cross(toCam);
        if (side1.lengthSqr() < 1.0E-6D) {
            side1 = dirN.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side1.lengthSqr() < 1.0E-6D) {
                side1 = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        side1 = side1.normalize().scale(halfWidth);
        Vec3 side2 = dirN.cross(side1).normalize().scale(halfWidth * 0.85D);

        emitTexturedQuadAlongAxis(buf, mat, p0, p1, side1, v0, v1, r, g, b, a);
        emitTexturedQuadAlongAxis(buf, mat, p0, p1, side2, v0, v1, r, g, b, a * 0.72F);
    }

    private static void emitTexturedQuadAlongAxis(BufferBuilder buf, Matrix4f mat, Vec3 p0, Vec3 p1, Vec3 side,
                                                  float v0, float v1, float r, float g, float b, float a) {
        buf.addVertex(mat, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z))
                .setUv(0.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z))
                .setUv(1.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z))
                .setUv(1.0F, v1).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z))
                .setUv(0.0F, v1).setColor(r, g, b, a);
    }

    /** Emits a rotating camera-facing additive lens flare billboard quad at {@code center}. */
    private static void emitFlareBillboard(BufferBuilder buf, Matrix4f mat, Vec3 center, Vec3 cam,
                                           float radius, float rotRad,
                                           float r, float g, float b, float a) {
        Vec3 toCam = cam.subtract(center);
        if (toCam.lengthSqr() < 1.0E-5D) {
            return;
        }
        Vec3 fwd = toCam.normalize();
        Vec3 worldUp = Math.abs(fwd.y) > 0.96D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = worldUp.cross(fwd).normalize();
        Vec3 up = fwd.cross(right).normalize();

        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);
        Vec3 rx = right.scale(cos * radius).add(up.scale(sin * radius));
        Vec3 ry = up.scale(cos * radius).subtract(right.scale(sin * radius));

        Vec3 v0 = center.subtract(rx).subtract(ry);
        Vec3 v1 = center.add(rx).subtract(ry);
        Vec3 v2 = center.add(rx).add(ry);
        Vec3 v3 = center.subtract(rx).add(ry);

        buf.addVertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buf.addVertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).setUv(0.0F, 1.0F).setColor(r, g, b, a);
    }

    private static void emitBeamQuad(BufferBuilder buf, Matrix4f mat, Vec3 p0, Vec3 p1, Vec3 cam,
                                     float halfWidth, float r, float g, float b, float a) {
        Vec3 dir = p1.subtract(p0);
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 mid = p0.add(p1).scale(0.5D);
        Vec3 toCam = cam.subtract(mid);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            side = side.normalize().scale(halfWidth);
        }

        buf.addVertex(mat, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z)).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z)).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z)).setColor(r, g, b, a);
        buf.addVertex(mat, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z)).setColor(r, g, b, a);
    }

    private static void emitWireBoxQuads(BufferBuilder buf, Matrix4f mat, AABB b, Vec3 cam,
                                         float thickness, float r, float g, float bl, float a) {
        Vec3 c000 = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 c100 = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 c101 = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 c001 = new Vec3(b.minX, b.minY, b.maxZ);
        Vec3 c010 = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 c110 = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 c111 = new Vec3(b.maxX, b.maxY, b.maxZ);
        Vec3 c011 = new Vec3(b.minX, b.maxY, b.maxZ);
        // Bottom loop
        emitBeamQuad(buf, mat, c000, c100, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c100, c101, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c101, c001, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c001, c000, cam, thickness, r, g, bl, a);
        // Top loop
        emitBeamQuad(buf, mat, c010, c110, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c110, c111, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c111, c011, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c011, c010, cam, thickness, r, g, bl, a);
        // Vertical edges
        emitBeamQuad(buf, mat, c000, c010, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c100, c110, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c101, c111, cam, thickness, r, g, bl, a);
        emitBeamQuad(buf, mat, c001, c011, cam, thickness, r, g, bl, a);
    }
}
