package com.industrialcore.client;

import com.industrialcore.item.EnergyBowItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.QuantumBladeItem;
import com.industrialcore.item.RailgunItem;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/**
 * Custom 1st-person animations &amp; FPS stances for all 4 Electric Weapons:
 * <ul>
 *   <li><b>Quantum Blade</b>: Katana guard stance + 3-hit diagonal/horizontal combo + Phase Dash thrust.</li>
 *   <li><b>Pulse Laser</b>: Stable FPS front-aim stance (barrel leveled dead-forward, aligned with world muzzle beam)
 *       with subtle high-frequency firing micro-recoil.</li>
 *   <li><b>Railgun</b>: Commanding <b>bottom-center FPS heavy cannon stance</b> with heavy recoil kickback and
 *       dramatic charge-reactive visuals: dark &amp; steaming at 0%, progressively extending energy rails from
 *       1%..99%, and blazing cyan-white overload + high-voltage tremor + crackling electric arcs at 100% READY!</li>
 *   <li><b>Energy Bow</b>: Smooth forward-aligned draw pull-back matching the 3D converging optical ring.</li>
 * </ul>
 */
public final class WeaponFirstPersonRenderer {
    private static final long BLADE_SWING_DURATION_MS = 270L;
    private static final long BLADE_DASH_DURATION_MS = 320L;
    private static final long RAILGUN_RECOIL_DURATION_MS = 520L;
    private static final long RAILGUN_VENT_DURATION_MS = 2_400L;

    private static boolean wasHoldingBlade = false;
    private static int lastSwingTime = 0;

    private static int comboStage = 0;
    private static long lastSwingStartMs = -10_000L;
    private static long lastDashStartMs = -10_000L;
    private static long lastRailgunRecoilMs = -10_000L;

    private WeaponFirstPersonRenderer() {}

    public static void reset() {
        wasHoldingBlade = false;
        lastSwingTime = 0;
        comboStage = 0;
        lastSwingStartMs = -10_000L;
        lastDashStartMs = -10_000L;
        lastRailgunRecoilMs = -10_000L;
    }

    /** Triggers next katana combo slash animation (0: Kesagiri -> 1: Gyaku-Kesa -> 2: Horizontal Sweep). */
    public static void triggerBladeSwing() {
        long now = Util.getMillis();
        if (now - lastSwingStartMs < 90L) {
            return;
        }
        long elapsedTicks = Math.max(0L, (now - lastSwingStartMs) / 50L);
        comboStage = VirtualWeaponLogic.nextBladeComboStage(comboStage, elapsedTicks);
        lastSwingStartMs = now;
    }

    /** Triggers Phase Dash forward blade-thrust animation. */
    public static void triggerBladeDash() {
        lastDashStartMs = Util.getMillis();
    }

    /** Triggers heavy Railgun Plasma Wave recoil kickback + post-shot cooling vent steam. */
    public static void triggerRailgunRecoil() {
        lastRailgunRecoilMs = Util.getMillis();
    }

    public static int getCurrentComboStage() {
        return comboStage;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        Player player = mc.player;
        ItemStack main = player.getMainHandItem();
        boolean holdingBlade = main.getItem() instanceof QuantumBladeItem;

        // Phase 116: Plasma blade equip ignition sound ("ブォォン！") only on genuine equip after join sync!
        if (WorldJoinAudioGuard.shouldPlayBladeIgniteSound(holdingBlade)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.WEAPON_BLADE_IGNITE.get(), 1.0F, 0.72F));
        }
        wasHoldingBlade = holdingBlade;

        // Catch melee entity swings as well as air swings
        if (holdingBlade && player.swinging && player.swingTime == 1 && lastSwingTime != 1) {
            triggerBladeSwing();
        }
        lastSwingTime = player.swingTime;

        // Subtle cooling vapor / white exhaust smoke right after Railgun fires (charge ~0%)
        if (main.getItem() instanceof RailgunItem) {
            long sinceShot = Util.getMillis() - lastRailgunRecoilMs;
            float charge = RailgunItem.getAutoCharge(main);
            if ((sinceShot >= 80L && sinceShot < RAILGUN_VENT_DURATION_MS && player.tickCount % 2 == 0)
                    || (charge < 0.015F && player.tickCount % 7 == 0)) {
                Vec3 eye = player.getEyePosition();
                Vec3 fwd = player.getViewVector(1.0F).normalize();
                Vec3 ventPos = eye.add(fwd.scale(0.85D)).add(0.0D, -0.26D, 0.0D);
                mc.level.addParticle(ParticleTypes.SMOKE,
                        ventPos.x + (mc.level.random.nextDouble() - 0.5D) * 0.14D,
                        ventPos.y,
                        ventPos.z + (mc.level.random.nextDouble() - 0.5D) * 0.14D,
                        0.0D, 0.028D, 0.0D);
            }
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        long now = Util.getMillis();

        if (stack.getItem() instanceof QuantumBladeItem) {
            renderCustomKatanaHand(mc, event, stack, now);
        } else if (stack.getItem() instanceof PulseLaserItem) {
            renderCustomPulseLaserHand(mc, event, stack, now);
        } else if (stack.getItem() instanceof RailgunItem) {
            renderCustomRailgunHand(mc, event, stack, now);
        } else if (stack.getItem() instanceof EnergyBowItem && mc.player.isUsingItem()
                && mc.player.getUseItem() == stack) {
            applyBowDrawPose(event.getPoseStack(), mc.player, stack, now);
        }
    }

    /**
     * Phase 105.8: Stable FPS front-aim pose for {@code pulse_laser}.
     * Levels the barrel dead-forward toward the crosshair (zero sideways bow tilt) and matches
     * the exact 1st-person world muzzle origin in {@link WeaponWorldFxRenderer}.
     */
    private static void renderCustomPulseLaserHand(Minecraft mc, RenderHandEvent event,
                                                   ItemStack stack, long now) {
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();

        boolean firing = mc.player.isUsingItem() && mc.player.getUseItem() == stack;
        float idleBreath = (float) Math.sin(now * 0.0038D) * 0.009F;

        float tx = firing ? 0.35F : 0.39F;
        float ty = (firing ? -0.34F : -0.37F) + (firing ? 0.0F : idleBreath);
        float tz = firing ? -0.76F : -0.80F;
        // FIRST_PERSON_RIGHT_HAND handheld angles a 45° diagonal sprite steeply upward;
        // pitching forward by -98° levels the barrel DEAD HORIZONTAL aiming straight at the crosshair!
        float rx = -98.0F;
        float ry = firing ? 10.5F : 8.0F;
        float rz = 0.0F;

        if (firing) {
            float charge = PulseLaserItem.getCharge(stack);
            float vib = 0.0032F + 0.0035F * charge;
            tx += (float) Math.sin(now * 0.085D) * vib;
            ty += (float) Math.cos(now * 0.107D) * vib;
            tz += 0.014F + (float) Math.sin(now * 0.064D) * (0.008F + 0.006F * charge);
            rx += (float) Math.sin(now * 0.072D) * (1.0F + 1.2F * charge);
        }

        pose.translate(tx, ty, tz);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(1.08F, 1.08F, 1.08F);

        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                mc.player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false,
                pose, event.getMultiBufferSource(), firing ? LightTexture.FULL_BRIGHT : event.getPackedLight());
        pose.popPose();
    }

    /**
     * Phase 105.8: Commanding bottom-center FPS Twin-Rail Heavy Cannon pose for {@code railgun}
     * with dramatic charge-reactive visuals:
     * <ul>
     *   <li><b>0% (Spent / Cooling)</b>: Darkened silent twin-rail barrel with rising cooling vapor.</li>
     *   <li><b>1%..99% (Charging)</b>: Additive cyan plasma core extending smoothly down the twin-rail channel.</li>
     *   <li><b>100% (READY / Critical)</b>: Blazing cyan-white full-length core, pulsing muzzle star,
     *       rapid high-voltage vibration, and crackling discharge arcs jumping between the twin rails!</li>
     * </ul>
     */
    private static void renderCustomRailgunHand(Minecraft mc, RenderHandEvent event,
                                                ItemStack stack, long now) {
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();

        float charge = Math.max(0.0F, Math.min(1.0F, RailgunItem.getAutoCharge(stack)));
        boolean ready = VirtualWeaponLogic.canFireRailgunWave(charge);
        float idleBreath = (float) Math.sin(now * 0.0032D) * 0.008F;

        // Dead-center bottom FPS heavy cannon stance aiming straight forward along -Z.
        // Both twin rails and the charge overlay share this exact parent pose (0px drift across
        // 0% / 50% / 100% charge and during recoil kickback!).
        float tx = 0.0F;
        float ty = -0.34F + idleBreath;
        float tz = -0.76F;
        float rx = 4.5F;
        float ry = 0.0F;
        float rz = 0.0F;

        // Heavy recoil kickback straight back toward camera
        long elapsed = now - lastRailgunRecoilMs;
        if (elapsed >= 0L && elapsed < RAILGUN_RECOIL_DURATION_MS) {
            float p = elapsed / (float) RAILGUN_RECOIL_DURATION_MS;
            float kick = p < 0.14F
                    ? (p / 0.14F)
                    : (float) Math.pow(1.0F - (p - 0.14F) / 0.86F, 2.2D);
            ty += 0.07F * kick;
            tz += 0.32F * kick;
            rx += 15.0F * kick;
        } else if (ready) {
            // High-voltage critical overload micro-vibration ("今にも暴発しそう")
            tx += (float) Math.sin(now * 0.095D) * 0.0080F;
            ty += (float) Math.cos(now * 0.123D) * 0.0070F;
            tz += (float) Math.sin(now * 0.071D) * 0.0045F;
            rz += (float) Math.sin(now * 0.111D) * 1.0F;
        } else if (charge > 0.55F) {
            float build = (charge - 0.55F) / 0.45F;
            tx += (float) Math.sin(now * 0.070D) * (0.0028F * build);
            ty += (float) Math.cos(now * 0.088D) * (0.0024F * build);
        }

        pose.translate(tx, ty, tz);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));

        // Charge-reactive barrel lighting: visibly dim & powered-down at 0%, blazing bright at 100%
        int barrelLight;
        if (charge < 0.015F) {
            barrelLight = LightTexture.pack(6, 6);
        } else if (ready) {
            barrelLight = LightTexture.FULL_BRIGHT;
        } else {
            int b = Math.round(7 + 8 * charge);
            barrelLight = LightTexture.pack(b, b);
        }

        // Render twin mirror-symmetric electromagnetic rail plates around exact X = 0.0 using
        // ItemDisplayContext.NONE (zero vanilla handheld/fixed translation or 180° flip!).
        // Inner-to-outer transform order:
        //   1. ZP(-45°): rotates (+1,+1,0) sprite barrel -> +X, top -> +Y, grip -> -Y
        //   2. YP(90° + side*3.5°): swings +X barrel straight forward to -Z with slight inward toe-in
        //   3. scale(1.15, 0.72, 1.08): X = plate thickness, Y = sleek cannon height, Z = barrel length
        //   4. translate(side * 0.090, 0, 0): exact mirror-symmetric twin-rail channel centered at X = 0
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.translate(side * 0.090F, 0.0F, 0.0F);
            pose.scale(1.15F, 0.72F, 1.08F);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F + side * 3.5F));
            pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                    mc.player, stack, ItemDisplayContext.NONE, false,
                    pose, event.getMultiBufferSource(), barrelLight);
            pose.popPose();
        }

        // Flush item buffer cleanly before additive energy rail glow & sparks
        if (event.getMultiBufferSource() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        if (charge >= 0.015F) {
            renderRailgunChargeOverlay(pose, charge, ready, now);
        }

        pose.popPose();
    }

    /**
     * Renders FULL_BRIGHT additive energy rails growing down the exact X = 0.0 central channel
     * between the mirror-symmetric twin rails from 1%..100%, plus critical overload plasma core
     * &amp; crackling electric discharge arcs jumping across the rails at 100% READY.
     */
    private static void renderRailgunChargeOverlay(PoseStack pose, float charge, boolean ready, long now) {
        pose.pushPose();
        // Exact geometric center (X = 0.0) between left (-0.090) & right (+0.090) rails!
        pose.translate(0.0F, 0.04F, 0.0F);
        Matrix4f mat = pose.last().pose();

        float tSec = (now % 100_000L) / 1000.0F;
        float pulse = ready
                ? (0.90F + 0.10F * (float) Math.sin(tSec * 38.0F))
                : (0.72F + 0.18F * (float) Math.sin(tSec * 12.0F));

        // Rail energy beam grows smoothly in length & width with charge (0..1) from breech to muzzle tip
        float zStart = 0.22F;
        float zEnd = zStart - (0.14F + 0.72F * charge);
        float halfW = (0.020F + 0.026F * charge) * (ready ? (1.18F + 0.14F * (float) Math.sin(tSec * 46.0F)) : 1.0F);
        float alpha = (0.32F + 0.68F * charge) * pulse;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // 1. Growing glowing cyan/white energy rail core (crossed X/Y quads along barrel)
        RenderSystem.setShaderTexture(0, WeaponWorldFxRenderer.TEX_LASER_CORE);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float vScroll = -tSec * (ready ? 7.8F : 2.5F);
        emitLocalRibbon(buf, mat, zStart, zEnd, halfW * 1.65F, vScroll, vScroll + 1.4F,
                0.12F, 0.86F, 1.0F, alpha * 0.88F);
        emitLocalRibbon(buf, mat, zStart, zEnd, halfW * 0.68F, vScroll * 1.3F, vScroll * 1.3F + 1.4F,
                0.94F, 0.99F, 1.0F, Math.min(1.0F, alpha * (ready ? 1.15F : 0.75F)));
        var railMesh = buf.build();
        if (railMesh != null) {
            BufferUploader.drawWithShader(railMesh);
        }

        // 2. Muzzle plasma flare star (subtle while charging, blindingly intense & spinning at 100% READY!)
        RenderSystem.setShaderTexture(0, WeaponWorldFxRenderer.TEX_LASER_FLARE);
        BufferBuilder flareBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float flareR = ready
                ? (0.175F + 0.035F * (float) Math.sin(tSec * 42.0F))
                : (0.035F + 0.075F * charge);
        emitLocalBillboardZ(flareBuf, mat, 0.0F, 0.0F, zEnd, flareR, tSec * (ready ? 7.5F : 2.5F),
                0.15F, 0.88F, 1.0F, Math.min(1.0F, (ready ? 0.98F : 0.58F * charge) * pulse));
        if (ready) {
            emitLocalBillboardZ(flareBuf, mat, 0.0F, 0.0F, zEnd, flareR * 0.58F, -tSec * 10.0F,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
        var flareMesh = flareBuf.build();
        if (flareMesh != null) {
            BufferUploader.drawWithShader(flareMesh);
        }

        // 3. Critical 100% READY crackling high-voltage electric arc sparks jumping between left & right rails!
        if (ready) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder sparkBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            long seedFrame = now / 38L;
            for (int arc = 0; arc < 5; arc++) {
                long h = (seedFrame * 2654435761L + arc * 374761393L) ^ 0x5BF03635L;
                float z0 = zStart - ((Math.abs((int) h) % 100) / 100.0F) * 0.56F;
                float z1 = z0 - (0.06F + ((Math.abs((int) (h >> 7)) % 100) / 100.0F) * 0.14F);
                float x0 = -0.088F;
                float y0 = (((Math.abs((int) (h >> 19)) % 100) / 100.0F) - 0.5F) * 0.08F;
                float xm = (((Math.abs((int) (h >> 23)) % 100) / 100.0F) - 0.5F) * 0.10F;
                float ym = (((Math.abs((int) (h >> 27)) % 100) / 100.0F) - 0.5F) * 0.12F;
                float x1 = 0.088F;
                float y1 = (((Math.abs((int) (h >> 11)) % 100) / 100.0F) - 0.5F) * 0.08F;
                float zm = (z0 + z1) * 0.5F;
                float sw = 0.0046F;
                emitSparkSegment(sparkBuf, mat, x0, y0, z0, xm, ym, zm, sw, 0.45F, 0.94F, 1.0F, 0.95F);
                emitSparkSegment(sparkBuf, mat, xm, ym, zm, x1, y1, z1, sw * 0.85F, 1.0F, 1.0F, 1.0F, 0.95F);
            }
            var sparkMesh = sparkBuf.build();
            if (sparkMesh != null) {
                BufferUploader.drawWithShader(sparkMesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void emitLocalRibbon(BufferBuilder buf, Matrix4f mat, float z0, float z1, float w,
                                        float v0, float v1, float r, float g, float b, float a) {
        // Horizontal ribbon
        buf.addVertex(mat, -w, 0.0F, z0).setUv(0.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, w, 0.0F, z0).setUv(1.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, w, 0.0F, z1).setUv(1.0F, v1).setColor(r, g, b, a);
        buf.addVertex(mat, -w, 0.0F, z1).setUv(0.0F, v1).setColor(r, g, b, a);
        // Vertical ribbon
        buf.addVertex(mat, 0.0F, -w, z0).setUv(0.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, 0.0F, w, z0).setUv(1.0F, v0).setColor(r, g, b, a);
        buf.addVertex(mat, 0.0F, w, z1).setUv(1.0F, v1).setColor(r, g, b, a);
        buf.addVertex(mat, 0.0F, -w, z1).setUv(0.0F, v1).setColor(r, g, b, a);
    }

    private static void emitLocalBillboardZ(BufferBuilder buf, Matrix4f mat, float cx, float cy, float cz,
                                            float rSize, float rotRad, float r, float g, float b, float a) {
        float c = (float) Math.cos(rotRad) * rSize;
        float s = (float) Math.sin(rotRad) * rSize;
        buf.addVertex(mat, cx - c + s, cy - s - c, cz).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, cx + c + s, cy + s - c, cz).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        buf.addVertex(mat, cx + c - s, cy + s + c, cz).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buf.addVertex(mat, cx - c - s, cy - s + c, cz).setUv(0.0F, 1.0F).setColor(r, g, b, a);
    }

    private static void emitSparkSegment(BufferBuilder buf, Matrix4f mat,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float w, float r, float g, float b, float a) {
        buf.addVertex(mat, x0 - w, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0 + w, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1 + w, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1 - w, y1, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x0, y0 - w, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x0, y0 + w, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1 + w, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1 - w, z1).setColor(r, g, b, a);
    }

    private static void renderCustomKatanaHand(Minecraft mc, RenderHandEvent event,
                                               ItemStack stack, long now) {
        // Completely replace vanilla's dull up-down swing with our custom Katana / Greatsword combo!
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();

        float idleBreath = (float) Math.sin(now * 0.0042D) * 0.014F;
        // Base Vendetta/Katana diagonal ready guard on right side
        float tx = 0.56F;
        float ty = -0.44F + idleBreath;
        float tz = -0.82F;
        float rx = -14.0F;
        float ry = -18.0F;
        float rz = 16.0F;

        long dashElapsed = now - lastDashStartMs;
        long swingElapsed = now - lastSwingStartMs;

        if (dashElapsed >= 0L && dashElapsed < BLADE_DASH_DURATION_MS) {
            // Phase Dash: sharp forward horizontal energy thrust!
            float p = dashElapsed / (float) BLADE_DASH_DURATION_MS;
            float thrust = (float) Math.sin(Math.min(1.0F, p * 1.15F) * Math.PI);
            tx += -0.42F * thrust;
            ty += 0.08F * thrust;
            tz += -0.34F * thrust;
            rx += -54.0F * thrust;
            ry += 42.0F * thrust;
            rz += -78.0F * thrust;
        } else if (swingElapsed >= 0L && swingElapsed < BLADE_SWING_DURATION_MS) {
            float p = swingElapsed / (float) BLADE_SWING_DURATION_MS;
            // Fast whip slash (0..0.65) followed by graceful katana follow-through return (0.65..1.0)
            float slashPhase = p < 0.65F ? easeOutCubic(p / 0.65F) : 1.0F - easeInOut((p - 0.65F) / 0.35F);
            float sweep = p < 0.65F ? easeOutCubic(p / 0.65F) : 1.0F;

            switch (comboStage) {
                case 0 -> {
                    // Combo 1: 袈裟斬り (Upper-Right -> Lower-Left diagonal slash!)
                    tx += lerp(0.32F, -0.62F, sweep) * (p < 0.65F ? 1.0F : slashPhase);
                    ty += lerp(0.34F, -0.40F, sweep) * (p < 0.65F ? 1.0F : slashPhase);
                    tz += -0.18F * (float) Math.sin(p * Math.PI);
                    rx += lerp(24.0F, -68.0F, sweep) * slashPhase;
                    ry += lerp(-16.0F, 52.0F, sweep) * slashPhase;
                    rz += lerp(-32.0F, 64.0F, sweep) * slashPhase;
                }
                case 1 -> {
                    // Combo 2: 逆袈裟 (Lower-Left -> Upper-Right upward diagonal slash!)
                    tx += lerp(-0.56F, 0.36F, sweep) * (p < 0.65F ? 1.0F : slashPhase);
                    ty += lerp(-0.38F, 0.34F, sweep) * (p < 0.65F ? 1.0F : slashPhase);
                    tz += -0.16F * (float) Math.sin(p * Math.PI);
                    rx += lerp(-64.0F, 34.0F, sweep) * slashPhase;
                    ry += lerp(48.0F, -24.0F, sweep) * slashPhase;
                    rz += lerp(112.0F, -42.0F, sweep) * slashPhase;
                }
                default -> {
                    // Combo 3: 高速横薙ぎ (Blistering flat horizontal right-to-left sweep!)
                    tx += lerp(0.42F, -0.74F, sweep) * (p < 0.65F ? 1.0F : slashPhase);
                    ty += -0.04F * slashPhase;
                    tz += -0.22F * (float) Math.sin(p * Math.PI);
                    rx += -28.0F * slashPhase;
                    ry += lerp(-36.0F, 76.0F, sweep) * slashPhase;
                    rz += -84.0F * slashPhase;
                }
            }
        }

        pose.translate(tx, ty, tz);
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.scale(1.16F, 1.16F, 1.16F); // Sleek Vendetta greatsword presence

        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                mc.player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    private static void applyBowDrawPose(PoseStack pose, Player player, ItemStack stack, long now) {
        boolean autoDraw = com.industrialcore.item.ElectricWeaponItem.isAttachmentEnabled(
                stack, com.industrialcore.weapon.WeaponAttachment.AUTO_DRAW);
        int held = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
        float draw = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
        float tremor = draw >= 0.98F ? (float) Math.sin(now * 0.09D) * 0.005F : 0.0F;
        pose.translate(-0.03F * draw + tremor, 0.015F * draw, 0.06F * draw);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeOutCubic(float t) {
        float c = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - c;
        return 1.0F - inv * inv * inv;
    }

    private static float easeInOut(float t) {
        float c = Math.max(0.0F, Math.min(1.0F, t));
        return c * c * (3.0F - 2.0F * c);
    }
}
