package com.industrialcore.client;

import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.EnergyBowItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.QuantumBladeItem;
import com.industrialcore.item.RailgunItem;
import com.industrialcore.item.VirtualSuitArmorItem;
import com.industrialcore.suit.SquadLogic.SquadMember;
import com.industrialcore.suit.SuitModule;
import com.industrialcore.suit.VirtualHudMath;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 98 / 100: Next-gen 3-layer depth-parallax AegisWorks 3D Tactical AR Hologram HUD.
 *
 * <ul>
 *   <li>3-layer depth parallax (0.60x back shadow/glass, 1.00x chamfered neon frame, 1.35x front data)
 *       plus subtle VR visor inward tilt.</li>
 *   <li>Tactical left vitals panel: self skin face + segmented FE Energy Shield bar directly above HP +
 *       food/O2, plus up to 2 nearby same-dimension squad members (max 3 total).</li>
 *   <li>Focused right panel: Active Modules glowing chips + 24m LivingEntity Target Scan.</li>
 *   <li>Bottom Virtual Hologram Hotbar dock with smooth sliding cyan-gold selector + offhand pod
 *       and non-overlapping Slim EXP &amp; Coordinate bar above.</li>
 * </ul>
 */
public final class VirtualHudOverlay {
    private static final Set<ResourceLocation> SUPPRESSED_LAYERS = Set.of(
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.SELECTED_ITEM_NAME
    );

    private static final int GLASS_BG = 0xB8040C16;
    private static final int SHADOW_BG = 0x7802050B;
    private static final int CYAN = 0xFF42F5FF;
    private static final int CYAN_DIM = 0x881E7C99;
    private static final int MAGENTA = 0xFFD95BFF;
    private static final int GOLD = 0xFFFFD166;
    private static final int TEXT_LIGHT = 0xFFE8FAFF;
    private static final int TEXT_MUTED = 0xFF7CA4BC;

    private static boolean hudEnabled = true;
    private static boolean wasFullSet = false;
    private static long bootStartMs = 0L;

    private static boolean rotInit = false;
    private static float lastYRot = 0.0F;
    private static float lastXRot = 0.0F;
    private static float parX = 0.0F;
    private static float parY = 0.0F;
    private static float ghostHpRatio = 1.0F;
    private static float hotbarSelectorPos = 0.0F;
    private static boolean selectorInit = false;
    private static int lastSelectedSlot = -1;
    private static ItemStack lastSelectedStack = ItemStack.EMPTY;
    private static long itemNameShownAtMs = -10_000L;

    private VirtualHudOverlay() {}

    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
    }

    public static boolean toggleHud() {
        hudEnabled = !hudEnabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable(hudEnabled
                                    ? "message.industrialcore.ar_hud_on"
                                    : "message.industrialcore.ar_hud_off")
                            .withStyle(hudEnabled ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                    true);
        }
        return hudEnabled;
    }

    public static void reset() {
        wasFullSet = false;
        bootStartMs = 0L;
        rotInit = false;
        parX = 0.0F;
        parY = 0.0F;
        ghostHpRatio = 1.0F;
        selectorInit = false;
        lastSelectedSlot = -1;
        lastSelectedStack = ItemStack.EMPTY;
        itemNameShownAtMs = -10_000L;
        ClientSquadCache.clear();
    }

    public static boolean isArHudActive(@Nullable Minecraft mc) {
        if (!hudEnabled || mc == null || mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return false;
        }
        return VirtualSuitArmorItem.isFullSetEquipped(mc.player);
    }

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!SUPPRESSED_LAYERS.contains(event.getName())) {
            return;
        }
        if (isArHudActive(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    /**
     * Smoothly zooms camera FOV (up to ~2.78x optical magnification) while drawing {@code energy_bow}.
     */
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isUsingItem()) {
            return;
        }
        ItemStack using = player.getUseItem();
        if (!(using.getItem() instanceof EnergyBowItem)) {
            return;
        }
        boolean autoDraw = ElectricWeaponItem.isAttachmentEnabled(using, WeaponAttachment.AUTO_DRAW);
        int held = using.getUseDuration(player) - player.getUseItemRemainingTicks();
        float draw = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
        event.setNewFovModifier(event.getNewFovModifier() * VirtualWeaponLogic.bowScopeFovMultiplier(draw));
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        Player player = mc.player;
        boolean fullSet = VirtualSuitArmorItem.isFullSetEquipped(player);
        long now = Util.getMillis();

        if (!fullSet) {
            WorldJoinAudioGuard.shouldPlaySuitBootSound(false);
            if (wasFullSet) {
                reset();
            }
            // Even without Virtual Aegis armor, render standalone weapon center aids (Laser arc / Bow scope)
            if (hudEnabled) {
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                if (player.getMainHandItem().getItem() instanceof PulseLaserItem) {
                    ItemStack laser = player.getMainHandItem();
                    drawLaserRightHeatArc(event.getGuiGraphics(), sw / 2, sh / 2,
                            PulseLaserItem.getHeat(laser) / 100.0F,
                            PulseLaserItem.isOverheated(laser), now);
                } else if (player.isUsingItem() && player.getUseItem().getItem() instanceof EnergyBowItem) {
                    renderEnergyBowScope(mc, event.getGuiGraphics(), mc.font, player,
                            player.getUseItem(), sw, sh);
                }
            }
            return;
        }
        if (!wasFullSet) {
            wasFullSet = true;
            bootStartMs = now;
        }
        // Phase 116: Never fire Virtual Aegis boot SE on initial login/dimension equipment sync!
        if (WorldJoinAudioGuard.shouldPlaySuitBootSound(true) && hudEnabled) {
            SoundDirector.playNodeUnlock();
        }
        if (!hudEnabled) {
            return;
        }

        // Update smooth camera-parallax inertia
        float curY = player.getYRot();
        float curX = player.getXRot();
        if (!rotInit) {
            lastYRot = curY;
            lastXRot = curX;
            rotInit = true;
        }
        float dYaw = VirtualHudMath.wrapAngleDelta(curY, lastYRot);
        float dPitch = curX - lastXRot;
        lastYRot = curY;
        lastXRot = curX;

        parX = VirtualHudMath.stepParallax(parX, -dYaw * 2.2F, 0.14F, VirtualHudMath.MAX_PARALLAX_X);
        parY = VirtualHudMath.stepParallax(parY, -dPitch * 1.5F, 0.14F, VirtualHudMath.MAX_PARALLAX_Y);

        double boot = VirtualHudMath.bootProgress(now - bootStartMs);
        float slidePx = (float) ((1.0D - boot) * 28.0D);

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // 1. Left Tactical Vitals Panel (Self Face + FE Shield over HP + Food/O2 + up to 2 Nearby Teammates)
        String dimId = player.level().dimension().location().toString();
        List<SquadMember> squad = ClientSquadCache.membersForDimension(dimId);
        renderLeftApexSquadStack(mc, g, font, player, squad, now, 12.0F - slidePx, screenH);

        // 2. Right Tactical Column (Bottom-Right Weapon Card -> Active Modules -> Target Scan)
        ItemStack heldWeapon = player.getMainHandItem();
        boolean holdingWeapon = heldWeapon.getItem() instanceof ElectricWeaponItem;
        boolean isLaser = heldWeapon.getItem() instanceof PulseLaserItem;

        VirtualHudMath.HudRect modsRect = VirtualHudMath.rightModulesRect(screenW, screenH, holdingWeapon, isLaser);
        renderRightModulesPanel(g, font, player, now, modsRect.x() + slidePx, modsRect.y());

        // 3. Target Scan Card (auto-expands above Active Modules when targeting a LivingEntity within 24m)
        LivingEntity target = findLookTarget(mc, player, 24.0D);
        if (target != null && boot > 0.35D) {
            VirtualHudMath.HudRect scanRect = VirtualHudMath.rightScanRect(screenW, screenH, holdingWeapon, isLaser);
            renderTargetScanPanel(g, font, player, target, scanRect.x() + slidePx, scanRect.y(), now);
        }

        // 4. Bottom Slim EXP & Coordinate Bar (strictly above Virtual Hotbar)
        renderBottomExpBar(g, font, player, screenW, screenH);

        // 4b. Phase 102.5: Selected Hotbar Item Name strictly above EXP bar (zero overlap!)
        renderSelectedItemName(g, font, player, screenW, screenH, now);

        // 5. Bottom Virtual Hologram Hotbar Dock (+ Offhand Pod + Sliding Neon Selector)
        renderVirtualHotbar(g, font, player, screenW, screenH, now);

        // 5b. Bottom-Right Weapon Tactical AR Card (+ NARROW subtle center lock ring / Bow scope)
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof EnergyBowItem) {
            renderEnergyBowScope(mc, g, font, player, player.getUseItem(), screenW, screenH);
        }
        if (holdingWeapon) {
            renderWeaponTacticalHud(mc, g, font, player, heldWeapon, screenW, screenH, slidePx, now);
        }

        // 6. Boot-up Banner during 1.2s link sequence
        long elapsed = now - bootStartMs;
        if (elapsed < VirtualHudMath.BOOT_DURATION_MS + 600L) {
            renderBootBanner(g, font, screenW, elapsed);
        }
    }

    // -------------------------------------------------------------------------
    // Left Stack: Tactical Self Card + Up to 2 Nearby Squad Cards
    // -------------------------------------------------------------------------

    private static void renderLeftApexSquadStack(Minecraft mc, GuiGraphics g, Font font,
                                                 Player player, List<SquadMember> squad,
                                                 long now, float baseX, int screenH) {
        boolean showO2 = player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater();
        int selfW = VirtualHudMath.SELF_CARD_WIDTH;
        int selfH = showO2 ? VirtualHudMath.SELF_CARD_HEIGHT_O2 : VirtualHudMath.SELF_CARD_HEIGHT;
        int mateW = VirtualHudMath.SQUAD_CARD_WIDTH;
        int mateH = VirtualHudMath.SQUAD_CARD_HEIGHT;
        int gap = VirtualHudMath.SQUAD_CARD_GAP;

        int mateCount = Math.min(2, squad.size());
        int totalH = selfH + (mateCount > 0 ? mateCount * (mateH + gap) : 0);
        int topY = Math.max(12, screenH - 12 - totalH);

        // Render Self Main Card (Tactical Visual Hierarchy: Primary Shield+HP vs Slim Food/O2)
        renderSelfApexCard(mc, g, font, player, now, baseX, topY, selfW, selfH, showO2, mateCount > 0);

        // Render up to 2 Nearby Teammate Cards strictly beneath Self Card (zero overlap)
        for (int i = 0; i < mateCount; i++) {
            int mateY = VirtualHudMath.squadCardTop(topY, selfH, i);
            renderTeammateCard(mc, g, font, player, squad.get(i), now, baseX + 2.0F, mateY, mateW, mateH);
        }
    }

    private static void renderSelfApexCard(Minecraft mc, GuiGraphics g, Font font, Player player,
                                           long now, float baseX, float baseY, int w, int h,
                                           boolean showO2, boolean hasSquad) {
        float tilt = -1.2F + parX * 0.06F;
        renderThreeLayerFrame(g, baseX, baseY, w, h, CYAN, MAGENTA, true, tilt, now);

        // Front Layer (1.35x parallax, integer snapped for razor-sharp typography)
        int fx = Math.round(baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X));
        int fy = Math.round(baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y));

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(fx, fy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));

        // Header
        g.drawString(font, hasSquad ? "◆ AEGIS // SQUAD" : "◆ AEGIS // VITALS", 7, 4, CYAN, false);

        // Left Player Skin Face (24x24 aligned cleanly with the Primary Shield + HP Combat Block)
        int faceX = 7;
        int faceY = 17;
        int faceSz = VirtualHudMath.SELF_FACE_SIZE;
        g.fill(faceX - 1, faceY - 1, faceX + faceSz + 1, faceY + faceSz + 1, GOLD);
        g.fill(faceX, faceY, faceX + faceSz, faceY + faceSz, 0xFF0A1420);
        if (player instanceof AbstractClientPlayer acp) {
            PlayerFaceRenderer.draw(g, acp.getSkin(), faceX, faceY, faceSz);
        } else {
            PlayerFaceRenderer.draw(g, DefaultPlayerSkin.get(player.getUUID()), faceX, faceY, faceSz);
        }

        // Primary Combat Vitals Column (x = 36 .. w - 7)
        int rx = 36;
        int rw = w - rx - 7;

        long totalFe = VirtualSuitArmorItem.totalSuitEnergy(player);
        long maxFe = Math.max(1L, VirtualSuitArmorItem.totalSuitCapacity(player));
        double feRatio = Math.min(1.0D, (double) totalFe / (double) maxFe);
        int feCol = VirtualHudMath.energyColor(feRatio);

        float hp = Math.max(0.0F, player.getHealth());
        float maxHp = Math.max(1.0F, player.getMaxHealth());
        float hpRatio = Math.min(1.0F, hp / maxHp);
        ghostHpRatio = ghostHpRatio > hpRatio ? Math.max(hpRatio, ghostHpRatio - 0.015F) : hpRatio;
        int hpCol = VirtualHudMath.hpColor(hpRatio);

        // Crisp combined combat telemetry line above the twin primary bars
        String feStr = VirtualHudMath.formatCompactFe(totalFe) + " FE";
        String hpStr = String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
        g.drawString(font, feStr, rx, 15, feCol, false);
        g.drawString(font, hpStr, w - 7 - font.width(hpStr), 15, hpCol, false);

        // 1. Primary Hero Bar #1: Thick 7px 4-Segment FE Shield Bar
        drawSegmentedShieldBar(g, rx, VirtualHudMath.SHIELD_BAR_Y, rw, VirtualHudMath.SHIELD_BAR_HEIGHT,
                (float) feRatio, 4, feCol);

        // 2. Primary Hero Bar #2: Thick 7px HP Bar tightly paired (2px gap) directly below Shield
        drawSlantedBar(g, rx, VirtualHudMath.HP_BAR_Y, rw, VirtualHudMath.HP_BAR_HEIGHT,
                hpRatio, ghostHpRatio, hpCol);

        // Subtle separator line separating Primary Combat Vitals from Slim Auxiliary Life Resources
        g.fill(7, 43, w - 7, 44, 0x3342F5FF);

        // 3. Auxiliary Slim FOOD Bar (3px height, muted amber normally; vivid warning when food <= 6)
        int food = player.getFoodData().getFoodLevel();
        boolean hungryWarn = food <= 6;
        int foodLabelCol = hungryWarn ? 0xFFFF6B7A : 0xFF7D7466;
        int foodBarCol = hungryWarn ? 0xFFFF6B5A : 0xFFB8925A;
        String foodNum = Integer.toString(food);
        int foodNumW = font.width(foodNum);
        g.drawString(font, "FOOD", 7, VirtualHudMath.FOOD_BAR_Y - 2, foodLabelCol, false);
        g.drawString(font, foodNum, w - 7 - foodNumW, VirtualHudMath.FOOD_BAR_Y - 2, foodLabelCol, false);
        int auxBarX = 36;
        int auxBarW = Math.max(24, w - 7 - foodNumW - 4 - auxBarX);
        drawSlimAuxBar(g, auxBarX, VirtualHudMath.FOOD_BAR_Y, auxBarW, VirtualHudMath.FOOD_BAR_HEIGHT,
                Math.min(1.0F, food / 20.0F), foodBarCol);

        // 4. Auxiliary Slim O2 Bar (3px height, expands only underwater)
        if (showO2) {
            float airRatio = Math.max(0.0F, Math.min(1.0F,
                    (float) player.getAirSupply() / Math.max(1.0F, (float) player.getMaxAirSupply())));
            boolean o2Warn = airRatio < 0.35F;
            int o2Col = o2Warn ? 0xFFFF5E7E : 0xFF5FB8D6;
            String o2Str = Math.round(airRatio * 100.0F) + "%";
            int o2W = font.width(o2Str);
            g.drawString(font, "O2", 7, VirtualHudMath.O2_BAR_Y - 2, o2Warn ? 0xFFFF6B7A : TEXT_MUTED, false);
            g.drawString(font, o2Str, w - 7 - o2W, VirtualHudMath.O2_BAR_Y - 2, o2Col, false);
            int o2BarW = Math.max(24, w - 7 - o2W - 4 - auxBarX);
            drawSlimAuxBar(g, auxBarX, VirtualHudMath.O2_BAR_Y, o2BarW, VirtualHudMath.O2_BAR_HEIGHT,
                    airRatio, o2Col);
        }

        pose.popPose();
    }

    private static void drawSlimAuxBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        g.fill(x, y, x + w, y + h, 0xAA08111A);
        int fw = Math.round(w * Math.max(0.0F, Math.min(1.0F, ratio)));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, color);
        }
    }

    private static void renderTeammateCard(Minecraft mc, GuiGraphics g, Font font, Player self,
                                           SquadMember mate, long now, float baseX, float baseY,
                                           int w, int h) {
        float tilt = -1.1F + parX * 0.06F;
        renderThreeLayerFrame(g, baseX, baseY, w, h, 0xFF34C8E8, GOLD, true, tilt, now);

        int fx = Math.round(baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X));
        int fy = Math.round(baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y));

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(fx, fy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));

        // 16x16 Teammate Face
        int faceX = 6;
        int faceY = 7;
        g.fill(faceX - 1, faceY - 1, faceX + 17, faceY + 17, mate.suitEquipped() ? CYAN : 0xFF5C7890);
        PlayerSkin skin = resolvePlayerSkin(mc, mate.uuid());
        PlayerFaceRenderer.draw(g, skin, faceX, faceY, 16);

        int rx = 26;
        int rw = w - rx - 6;

        // Name + Distance (e.g. "12m")
        double dist = mate.distanceTo(self.getX(), self.getY(), self.getZ());
        String distStr = String.format(Locale.ROOT, "%.0fm", dist);
        int distW = font.width(distStr);
        Component nameComp = MachineTheme.fit(font, Component.literal(mate.name()), rw - distW - 4);
        g.drawString(font, nameComp, rx, 4, TEXT_LIGHT, false);
        g.drawString(font, distStr, w - 6 - distW, 4, GOLD, false);

        // Shield bar (only if teammate wears Virtual Aegis)
        if (mate.suitEquipped()) {
            drawSegmentedShieldBar(g, rx, 14, rw, 3, (float) mate.energyRatio(), 4,
                    VirtualHudMath.energyColor(mate.energyRatio()));
        }

        // HP bar
        float hpR = mate.hpRatio();
        int hpY = mate.suitEquipped() ? 19 : 16;
        int hpCol = VirtualHudMath.hpColor(hpR);
        drawSlantedBar(g, rx, hpY, rw - 32, 5, hpR, hpR, hpCol);
        String hpTxt = String.format(Locale.ROOT, "%.0f/%.0f", mate.hp(), mate.maxHp());
        g.drawString(font, hpTxt, w - 6 - font.width(hpTxt), hpY - 1, hpCol, false);

        pose.popPose();
    }

    private static PlayerSkin resolvePlayerSkin(Minecraft mc, java.util.UUID uuid) {
        ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(uuid);
    }

    // -------------------------------------------------------------------------
    // Right Panel: Focused Tactical Modules + Target Scan
    // -------------------------------------------------------------------------

    private static void renderRightModulesPanel(GuiGraphics g, Font font, Player player, long now,
                                                float baseX, float baseY) {
        int w = VirtualHudMath.RIGHT_MODULES_WIDTH;
        int h = VirtualHudMath.RIGHT_MODULES_HEIGHT;
        float tilt = 1.3F + parX * 0.07F;

        renderThreeLayerFrame(g, baseX, baseY, w, h, CYAN, GOLD, false, tilt, now);

        int fx = Math.round(baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X));
        int fy = Math.round(baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y));

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(fx, fy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));

        String hdr = "ACTIVE MODULES ◆";
        g.drawString(font, hdr, w - 7 - font.width(hdr), 4, CYAN, false);

        List<SuitModule> active = VirtualSuitArmorItem.getActiveModules(player);
        if (active.isEmpty()) {
            Component none = Component.translatable("gui.industrialcore.ar_hud.no_modules");
            g.drawString(font, MachineTheme.fit(font, none, w - 14), 7, 24, 0xFF567488, false);
        } else {
            int cx = 7;
            int cy = 18;
            for (SuitModule m : active) {
                String code = m.hudCode();
                int cw = font.width(code) + 6;
                if (cx + cw > w - 6) {
                    cx = 7;
                    cy += 12;
                    if (cy + 10 > h - 3) {
                        break;
                    }
                }
                int col = m.accentColor();
                g.fill(cx, cy, cx + cw, cy + 10, 0xCC0A1C28);
                g.fill(cx, cy, cx + cw, cy + 1, col);
                g.fill(cx, cy + 9, cx + cw, cy + 10, col);
                g.fill(cx, cy, cx + 1, cy + 10, col);
                g.fill(cx + cw - 1, cy, cx + cw, cy + 10, col);
                g.drawString(font, code, cx + 3, cy + 1, col, false);
                cx += cw + 3;
            }
        }

        pose.popPose();
    }

    private static void renderTargetScanPanel(GuiGraphics g, Font font, Player player, LivingEntity target,
                                              float baseX, float baseY, long now) {
        int w = 136;
        int h = 40;
        float tilt = 1.0F + parX * 0.06F;

        renderThreeLayerFrame(g, baseX, baseY, w, h, MAGENTA, CYAN, false, tilt, now);

        int fx = Math.round(baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X));
        int fy = Math.round(baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y));

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(fx, fy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));

        float dist = player.distanceTo(target);
        String distText = String.format(Locale.ROOT, "%.1fm", dist);
        g.drawString(font, "TARGET // SCAN", 7, 4, MAGENTA, false);
        g.drawString(font, distText, w - 7 - font.width(distText), 4, GOLD, false);

        Component name = MachineTheme.fit(font, target.getDisplayName(), w - 58);
        float thp = Math.max(0.0F, target.getHealth());
        float tmax = Math.max(1.0F, target.getMaxHealth());
        float tr = Math.min(1.0F, thp / tmax);
        String hpStr = String.format(Locale.ROOT, "%.0f/%.0f", thp, tmax);

        g.drawString(font, name, 7, 15, TEXT_LIGHT, false);
        g.drawString(font, hpStr, w - 7 - font.width(hpStr), 15, VirtualHudMath.hpColor(tr), false);
        drawSlantedBar(g, 7, 27, w - 14, 6, tr, tr, VirtualHudMath.hpColor(tr));

        pose.popPose();
    }

    // -------------------------------------------------------------------------
    // Bottom Slim EXP & Coordinate Bar (strictly above Virtual Hotbar)
    // -------------------------------------------------------------------------

    private static void renderBottomExpBar(GuiGraphics g, Font font, Player player,
                                           int screenW, int screenH) {
        int barW = VirtualHudMath.HOTBAR_WIDTH;
        int bx = (screenW - barW) / 2;
        int topY = VirtualHudMath.expBarTop(screenH);
        int botY = VirtualHudMath.expBarBottom(screenH);

        g.fill(bx, topY, bx + barW, botY, 0x99040B14);
        g.fill(bx, topY, bx + barW, topY + 1, CYAN_DIM);

        String lvText = "LV." + player.experienceLevel;
        g.drawString(font, lvText, bx + 4, topY + 2, GOLD, false);

        String coordText = String.format(Locale.ROOT, "X:%d  Y:%d  Z:%d",
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ());
        g.drawString(font, coordText, bx + barW - 4 - font.width(coordText), topY + 2, TEXT_LIGHT, false);

        // Ultra-slim 3px glowing EXP progress strip at bottom of EXP deck
        int stripY = botY - 3;
        g.fill(bx + 2, stripY, bx + barW - 2, botY, 0xCC0A1826);
        float expRatio = Math.max(0.0F, Math.min(1.0F, player.experienceProgress));
        int span = barW - 4;
        int fillW = Math.round(span * expRatio);
        if (fillW > 0) {
            g.fillGradient(bx + 2, stripY, bx + 2 + fillW, botY, CYAN, GOLD);
            g.fill(bx + 2 + fillW - 1, stripY - 1, bx + 2 + fillW + 1, botY, 0xFFFFFFFF);
        }
    }

    /**
     * Phase 102.5: Renders the fading selected hotbar item name strictly above the Slim EXP &amp;
     * Coordinate bar (minimum 10px air gap) with drop shadow, hidden when holding nothing.
     */
    private static void renderSelectedItemName(GuiGraphics g, Font font, Player player,
                                               int screenW, int screenH, long now) {
        int slot = player.getInventory().selected;
        ItemStack held = player.getMainHandItem();
        if (slot != lastSelectedSlot || !ItemStack.isSameItemSameComponents(held, lastSelectedStack)) {
            lastSelectedSlot = slot;
            lastSelectedStack = held.copy();
            itemNameShownAtMs = held.isEmpty() ? -10_000L : now;
        }
        if (held.isEmpty()) {
            return;
        }
        float alpha = VirtualHudMath.selectedItemNameAlpha(now - itemNameShownAtMs);
        if (alpha <= 0.04F) {
            return;
        }
        int aByte = Math.max(12, Math.min(255, Math.round(alpha * 255.0F)));
        Component name = held.getHoverName();
        int nameY = VirtualHudMath.selectedItemNameTop(screenH);
        int textColor = (aByte << 24) | (TEXT_LIGHT & 0x00FFFFFF);
        g.drawCenteredString(font, name, screenW / 2, nameY, textColor);
    }

    // -------------------------------------------------------------------------
    // Bottom Virtual Hologram Hotbar Dock (+ Sliding Neon Selector + Offhand Pod)
    // -------------------------------------------------------------------------

    private static void renderVirtualHotbar(GuiGraphics g, Font font, Player player,
                                            int screenW, int screenH, long now) {
        int w = VirtualHudMath.HOTBAR_WIDTH;
        int h = VirtualHudMath.HOTBAR_HEIGHT;
        int left = (screenW - w) / 2;
        int top = VirtualHudMath.hotbarTop(screenH);

        // 3D depth drop shadow + chamfered translucent dark-glass dock
        drawChamferedRect(g, left + 2, top + 2, w, h, 4, SHADOW_BG);
        drawChamferedRect(g, left, top, w, h, 4, GLASS_BG);

        // Subtle scanlines inside dock
        int phase = (int) ((now / 110L) % 4L);
        for (int sy = top + 2 + phase; sy < top + h - 2; sy += 4) {
            g.fill(left + 3, sy, left + w - 3, sy + 1, 0x1442F5FF);
        }

        // Neon cyan perimeter + corner accents
        g.fill(left + 4, top, left + w - 1, top + 1, CYAN);
        g.fill(left + 1, top + h - 1, left + w - 4, top + h, CYAN);
        g.fill(left, top + 4, left + 1, top + h - 1, CYAN);
        g.fill(left + w - 1, top + 1, left + w, top + h - 4, CYAN);

        // Vertical micro-dividers between the 9 slots
        for (int i = 1; i < 9; i++) {
            int dx = left + 3 + i * VirtualHudMath.HOTBAR_SLOT_STEP;
            g.fill(dx, top + 4, dx + 1, top + h - 4, 0x3342F5FF);
        }

        // Smooth sliding cyan-gold selection frame
        int selected = player.getInventory().selected;
        if (!selectorInit) {
            hotbarSelectorPos = selected;
            selectorInit = true;
        }
        hotbarSelectorPos = VirtualHudMath.stepHotbarSelector(hotbarSelectorPos, selected, 0.32F);
        int selX = Math.round(left + 3 + hotbarSelectorPos * VirtualHudMath.HOTBAR_SLOT_STEP);
        int selY = top + 2;
        int selW = 20;
        int selH = 20;
        double pulse = 0.5D + 0.5D * Math.sin(now * 0.008D);
        int selCol = VirtualHudMath.blend(CYAN, GOLD, (float) (0.35D + 0.45D * pulse));
        g.fill(selX, selY, selX + selW, selY + selH, 0x3A42F5FF);
        // Glowing selection frame + corner brackets
        g.fill(selX, selY, selX + selW, selY + 1, selCol);
        g.fill(selX, selY + selH - 1, selX + selW, selY + selH, selCol);
        g.fill(selX, selY, selX + 1, selY + selH, selCol);
        g.fill(selX + selW - 1, selY, selX + selW, selY + selH, selCol);
        g.fill(selX - 1, selY - 1, selX + 4, selY, GOLD);
        g.fill(selX + selW - 4, selY + selH, selX + selW + 1, selY + selH + 1, GOLD);

        // Render all 9 hotbar items + stack counts + durability/FE bars + cooldowns
        for (int i = 0; i < 9; i++) {
            ItemStack st = player.getInventory().items.get(i);
            if (!st.isEmpty()) {
                int ix = left + 5 + i * VirtualHudMath.HOTBAR_SLOT_STEP;
                int iy = top + 4;
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(font, st, ix, iy);
            }
        }

        // Offhand Hologram Pod (left of hotbar when holding an offhand item)
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int ox = left - VirtualHudMath.OFFHAND_GAP - VirtualHudMath.OFFHAND_WIDTH;
            int ow = VirtualHudMath.OFFHAND_WIDTH;
            drawChamferedRect(g, ox + 2, top + 2, ow, h, 3, SHADOW_BG);
            drawChamferedRect(g, ox, top, ow, h, 3, GLASS_BG);
            g.fill(ox + 3, top, ox + ow - 1, top + 1, GOLD);
            g.fill(ox + 1, top + h - 1, ox + ow - 3, top + h, GOLD);
            g.fill(ox, top + 3, ox + 1, top + h - 1, GOLD);
            g.fill(ox + ow - 1, top + 1, ox + ow, top + h - 3, GOLD);
            g.renderItem(offhand, ox + 4, top + 4);
            g.renderItemDecorations(font, offhand, ox + 4, top + 4);
        }

        // Subtle Attack Strength Cooldown bar on right side of hotbar when recovering
        float atk = player.getAttackStrengthScale(0.0F);
        if (atk < 1.0F) {
            int ax = left + w + 5;
            int ay = top + 9;
            g.fill(ax, ay, ax + 18, ay + 5, 0xAA06101B);
            g.fill(ax + 1, ay + 1, ax + 1 + Math.round(16 * atk), ay + 4, CYAN);
        }
    }

    // -------------------------------------------------------------------------
    // Bottom-Right Weapon Tactical AR Card (All 4 Electric Weapons)
    // Screen center remains 100% clear except the subtle ultra-thin NARROW circle!
    // -------------------------------------------------------------------------

    private static void renderWeaponTacticalHud(Minecraft mc, GuiGraphics g, Font font,
                                                Player player, ItemStack weapon,
                                                int screenW, int screenH, float slidePx, long now) {
        boolean isLaser = weapon.getItem() instanceof PulseLaserItem;

        // Center crosshair stays completely clear of text, bars, and panels.
        // Only the subtle right-side semicircle overheat arc (and NARROW ring at r=21px) render near center.
        if (isLaser) {
            drawLaserRightHeatArc(g, screenW / 2, screenH / 2,
                    PulseLaserItem.getHeat(weapon) / 100.0F,
                    PulseLaserItem.isOverheated(weapon), now);
            if (ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.AUTO_AIM)
                    && PulseLaserItem.getAimRange(weapon) == com.industrialcore.weapon.LaserAimRange.NARROW) {
                drawSubtleLockCircle(g, screenW / 2, screenH / 2, 21, 0x623CF2FF);
            }
        }

        VirtualHudMath.HudRect card = VirtualHudMath.weaponCardRect(screenW, screenH, isLaser);
        float baseX = card.x() + slidePx;
        float baseY = card.y();
        int w = card.width();
        int h = card.height();
        float tilt = 1.1F + parX * 0.06F;

        int primaryBorder = CYAN;
        int secondaryBorder = MAGENTA;
        if (weapon.getItem() instanceof PulseLaserItem) {
            float charge = PulseLaserItem.getCharge(weapon);
            boolean ov = PulseLaserItem.isOverheated(weapon);
            primaryBorder = ov ? 0xFFFF3855 : (charge >= 0.85F ? 0xFFFF72F5 : CYAN);
            secondaryBorder = charge >= 0.45F ? GOLD : MAGENTA;
        } else if (weapon.getItem() instanceof RailgunItem) {
            primaryBorder = 0xFF7FA8FF;
            secondaryBorder = GOLD;
        } else if (weapon.getItem() instanceof QuantumBladeItem) {
            primaryBorder = MAGENTA;
            secondaryBorder = CYAN;
        } else if (weapon.getItem() instanceof EnergyBowItem) {
            primaryBorder = 0xFF5CFFA8;
            secondaryBorder = GOLD;
        }

        renderThreeLayerFrame(g, baseX, baseY, w, h, primaryBorder, secondaryBorder, false, tilt, now);

        int fx = Math.round(baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_X));
        int fy = Math.round(baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_FRONT, VirtualHudMath.MAX_LAYER_OFFSET_Y));

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(fx, fy, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));

        int padX = 7;
        int innerW = w - padX * 2; // 138px

        if (weapon.getItem() instanceof PulseLaserItem) {
            float heat = PulseLaserItem.getHeat(weapon);
            boolean ov = PulseLaserItem.isOverheated(weapon);
            float charge = PulseLaserItem.getCharge(weapon);
            int ticksSinceHit = PulseLaserItem.getTicksSinceHit(weapon);
            boolean autoAim = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.AUTO_AIM);
            boolean cryo = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.CRYO_CORE);
            com.industrialcore.weapon.LaserTargetMode mode = PulseLaserItem.getTargetMode(weapon);
            com.industrialcore.weapon.LaserAimRange aimRange = PulseLaserItem.getAimRange(weapon);

            boolean hasLock = autoAim && mc.level != null
                    && PulseLaserItem.findAutoAimTarget(
                    mc.level, player, weapon, WeaponWorldFxRenderer.getLockedTargetId(), 1.0F) != null;

            int barCol = ov ? 0xFFFF3855 : (heat > 70.0F ? 0xFFFF9A3C : (cryo ? 0xFF7BE8FF : CYAN));
            int pwrCol = charge >= 0.85F ? 0xFFFF72F5 : (charge >= 0.45F ? GOLD : CYAN);

            // Header row (y=4): LASER | FOV:120° | HOSTILE/ALL
            g.drawString(font, "LASER", padX, 4, primaryBorder, false);
            String fovStr = "FOV:" + aimRange.hudCode();
            int fovCol = switch (aimRange) {
                case NARROW -> CYAN;
                case WIDE -> 0xFF66FFB2;
                case FULL_360 -> MAGENTA;
            };
            g.drawString(font, fovStr, 46, 4, fovCol, false);
            String modeStr = mode.hudCode();
            int modeCol = mode == com.industrialcore.weapon.LaserTargetMode.HOSTILE_ONLY ? CYAN : GOLD;
            g.drawString(font, modeStr, w - padX - font.width(modeStr), 4, modeCol, false);

            // Heat row (y=15) + 3px heat bar (y=24)
            String heatLabel = ov ? "OVERHEAT!" : String.format(Locale.ROOT, "HEAT %d%%", Math.round(heat));
            g.drawString(font, heatLabel, padX, 15, barCol, false);
            if (hasLock) {
                String lockBadge = "LOCK";
                g.drawString(font, lockBadge, w - padX - font.width(lockBadge), 15, GOLD, false);
            }
            drawSlantedBar(g, padX, 24, innerW, 3, heat / 100.0F, heat / 100.0F, barCol);

            // Power row (y=29) + 4px glowing charge bar (y=38)
            String pwrStr = String.format(Locale.ROOT, "PWR %d%%", Math.round(charge * 100.0F));
            g.drawString(font, pwrStr, padX, 29, pwrCol, false);
            String rightBadge;
            int badgeCol;
            if (charge > 0.0F && ticksSinceHit > 2 && ticksSinceHit <= VirtualWeaponLogic.LASER_CHARGE_HOLD_TICKS) {
                float remSec = (VirtualWeaponLogic.LASER_CHARGE_HOLD_TICKS - ticksSinceHit) / 20.0F;
                rightBadge = String.format(Locale.ROOT, "HOLD %.1fs", Math.max(0.0F, remSec));
                badgeCol = GOLD;
            } else {
                float mult = VirtualWeaponLogic.laserChargedDamage(charge, cryo)
                        / VirtualWeaponLogic.LASER_CHARGE_BASE_DAMAGE;
                rightBadge = String.format(Locale.ROOT, "%.1fx", mult);
                badgeCol = pwrCol;
            }
            g.drawString(font, rightBadge, w - padX - font.width(rightBadge), 29, badgeCol, false);
            drawSlantedBar(g, padX, 38, innerW, 4, charge, charge, pwrCol);

        } else if (weapon.getItem() instanceof RailgunItem) {
            boolean fast = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.FAST_CAPACITOR);
            boolean scan = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.WALL_SCANNER);
            float ratio = RailgunItem.getAutoCharge(weapon);
            boolean ready = VirtualWeaponLogic.canFireRailgunWave(ratio);
            int remSec = VirtualWeaponLogic.railgunRemainingSeconds(ratio, fast);
            int barCol = ready ? GOLD : 0xFF7FA8FF;

            g.drawString(font, "RAILGUN", padX, 4, ready ? GOLD : 0xFF7FA8FF, false);
            if (scan && mc.level != null) {
                int hostiles = mc.level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(VirtualWeaponLogic.RAILGUN_WALL_SCAN_RANGE),
                        e -> e != player && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy).size();
                String sStr = "SCAN:" + hostiles;
                g.drawString(font, sStr, w - padX - font.width(sStr), 4, 0xFFFF5E7E, false);
            } else {
                String feStr = VirtualHudMath.formatCompactFe(ElectricWeaponItem.getEnergy(weapon)) + " FE";
                g.drawString(font, feStr, w - padX - font.width(feStr), 4, CYAN, false);
            }

            String chgStr = ready
                    ? "READY"
                    : String.format(Locale.ROOT, "CHG %d%%", Math.round(ratio * 100.0F));
            g.drawString(font, chgStr, padX, 15, barCol, false);
            String rightTag = ready ? (fast ? "FAST" : "WAVE") : (remSec + "s");
            int rightCol = ready ? CYAN : TEXT_MUTED;
            g.drawString(font, rightTag, w - padX - font.width(rightTag), 15, rightCol, false);
            drawSlantedBar(g, padX, 25, innerW, 5, ratio, ratio, barCol);

        } else if (weapon.getItem() instanceof QuantumBladeItem) {
            boolean hasWave = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.PLASMA_WAVE);
            boolean hasBlink = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.BLINK_DRIVE);
            long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
            long waveRem = Math.max(0L, QuantumBladeItem.getWaveReadyTick(weapon) - gameTime);
            long blinkRem = Math.max(0L, QuantumBladeItem.getBlinkReadyTick(weapon) - gameTime);

            g.drawString(font, "Q-BLADE", padX, 4, MAGENTA, false);
            String feStr = VirtualHudMath.formatCompactFe(ElectricWeaponItem.getEnergy(weapon)) + " FE";
            g.drawString(font, feStr, w - padX - font.width(feStr), 4, CYAN, false);

            String wTxt = !hasWave ? "WAVE:-"
                    : (waveRem == 0L ? "WAVE:OK" : String.format(Locale.ROOT, "W:%.1fs", waveRem / 20.0F));
            String bTxt = !hasBlink ? "BLINK:-"
                    : (blinkRem == 0L ? "BLINK:OK" : String.format(Locale.ROOT, "B:%.1fs", blinkRem / 20.0F));
            g.drawString(font, wTxt, padX, 15,
                    hasWave ? (waveRem == 0L ? 0xFFE26BFF : TEXT_MUTED) : 0xFF445566, false);
            g.drawString(font, bTxt, w - padX - font.width(bTxt), 15,
                    hasBlink ? (blinkRem == 0L ? CYAN : TEXT_MUTED) : 0xFF445566, false);

            float wRatio = !hasWave ? 0.0F
                    : Math.max(0.0F, 1.0F - (float) waveRem / (float) VirtualWeaponLogic.BLADE_WAVE_COOLDOWN_TICKS);
            float bRatio = !hasBlink ? 0.0F
                    : Math.max(0.0F, 1.0F - (float) blinkRem / (float) VirtualWeaponLogic.BLADE_BLINK_COOLDOWN_TICKS);
            int halfW = (innerW - 6) / 2;
            drawSlantedBar(g, padX, 26, halfW, 4, wRatio, wRatio, hasWave ? MAGENTA : 0xFF334455);
            drawSlantedBar(g, padX + halfW + 6, 26, halfW, 4, bRatio, bRatio, hasBlink ? CYAN : 0xFF334455);

        } else if (weapon.getItem() instanceof EnergyBowItem) {
            boolean multi = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.MULTI_LOCK);
            boolean autoDraw = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.AUTO_DRAW);
            boolean grav = ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.GRAVITY_ARROW);
            boolean using = player.isUsingItem() && player.getUseItem() == weapon;
            int held = using ? (weapon.getUseDuration(player) - player.getUseItemRemainingTicks()) : 0;
            float draw = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
            int lockCount = (multi && mc.level != null) ? EnergyBowItem.findLockedHostiles(mc.level, player).size() : 0;
            int col = lockCount > 0 ? GOLD : 0xFF5CFFA8;

            g.drawString(font, "ENERGY BOW", padX, 4, 0xFF5CFFA8, false);
            if (multi) {
                String lStr = "LOCK x" + lockCount;
                g.drawString(font, lStr, w - padX - font.width(lStr), 4,
                        lockCount > 0 ? GOLD : TEXT_MUTED, false);
            } else {
                String feStr = VirtualHudMath.formatCompactFe(ElectricWeaponItem.getEnergy(weapon)) + " FE";
                g.drawString(font, feStr, w - padX - font.width(feStr), 4, CYAN, false);
            }

            String dStr = using ? String.format(Locale.ROOT, "DRAW %d%%", Math.round(draw * 100.0F)) : "BOW READY";
            g.drawString(font, dStr, padX, 15, col, false);
            if (grav) {
                String gTag = "GRAV";
                g.drawString(font, gTag, w - padX - font.width(gTag), 15, MAGENTA, false);
            }
            drawSlantedBar(g, padX, 25, innerW, 5, draw, draw, col);
        }

        pose.popPose();
    }

    /**
     * Renders the unobstructed SF Optical Sniper Scope overlay while drawing {@code energy_bow}:
     * subtle peripheral vignette, precision mil-dot rangefinder hairlines, optic ring, and
     * real-time 96m target distance + predicted sniper damage multiplier readout.
     */
    private static void renderEnergyBowScope(Minecraft mc, GuiGraphics g, Font font,
                                             Player player, ItemStack bow,
                                             int screenW, int screenH) {
        boolean autoDraw = ElectricWeaponItem.isAttachmentEnabled(bow, WeaponAttachment.AUTO_DRAW);
        int held = bow.getUseDuration(player) - player.getUseItemRemainingTicks();
        float draw = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
        float alpha = Math.max(0.28F, Math.min(1.0F, draw * 1.15F));

        int cx = screenW / 2;
        int cy = screenH / 2;
        int opticR = Math.max(46, Math.round(Math.min(screenW, screenH) * 0.30F));

        // 1. Subtle peripheral edge vignette (never obscures center combat view!)
        int vigA = Math.round(92.0F * alpha);
        int vigDark = (vigA << 24) | 0x00020A12;
        int edgeX = Math.max(18, screenW / 10);
        int edgeY = Math.max(12, screenH / 10);
        g.fillGradient(0, 0, screenW, edgeY, vigDark, 0x00020A12);
        g.fillGradient(0, screenH - edgeY, screenW, screenH, 0x00020A12, vigDark);
        g.fill(0, 0, edgeX / 2, screenH, (Math.round(58.0F * alpha) << 24) | 0x00020A12);
        g.fill(screenW - edgeX / 2, 0, screenW, screenH, (Math.round(58.0F * alpha) << 24) | 0x00020A12);

        // 2. Crisp thin circular optic ring + cardinal ticks
        int ringA = Math.round(145.0F * alpha);
        int baseRgb = draw >= 0.98F ? 0x00FFD166 : 0x005CFFA8;
        int ringCol = (ringA << 24) | baseRgb;
        int lineCol = (Math.round(115.0F * alpha) << 24) | baseRgb;
        int steps = 112;
        int prevX = Integer.MIN_VALUE;
        int prevY = Integer.MIN_VALUE;
        for (int i = 0; i < steps; i++) {
            double ang = (i * Math.PI * 2.0D) / steps;
            int px = cx + (int) Math.round(Math.cos(ang) * opticR);
            int py = cy + (int) Math.round(Math.sin(ang) * opticR);
            if (px != prevX || py != prevY) {
                g.fill(px, py, px + 1, py + 1, ringCol);
                prevX = px;
                prevY = py;
            }
        }

        // 3. Precision rangefinder hairlines with mil-dot ticks (leaving 10px center clear!)
        int gap = 10;
        g.fill(cx - opticR - 8, cy, cx - gap, cy + 1, lineCol);
        g.fill(cx + gap + 1, cy, cx + opticR + 9, cy + 1, lineCol);
        g.fill(cx, cy - opticR - 6, cx + 1, cy - gap, lineCol);
        g.fill(cx, cy + gap + 1, cx + 1, cy + opticR + 7, lineCol);

        // Mil-dot range ticks along horizontal & vertical hairlines
        for (int m = 1; m <= 3; m++) {
            int off = gap + m * ((opticR - gap) / 4);
            int tickHalf = m == 2 ? 3 : 2;
            g.fill(cx - off, cy - tickHalf, cx - off + 1, cy + tickHalf + 1, ringCol);
            g.fill(cx + off, cy - tickHalf, cx + off + 1, cy + tickHalf + 1, ringCol);
            g.fill(cx - tickHalf, cy + off, cx + tickHalf + 1, cy + off + 1, ringCol);
            g.fill(cx - tickHalf, cy - off, cx + tickHalf + 1, cy - off + 1, ringCol);
        }

        // 4. Real-time 96m Rangefinder raycast (closest LivingEntity or Block) + predicted sniper multiplier
        double dist = measureScopeDistance(mc, player, VirtualWeaponLogic.BOW_RANGEFINDER_MAX_DIST);
        boolean inRange = dist >= 0.0D;
        double effectiveDist = inRange ? dist : VirtualWeaponLogic.BOW_RANGEFINDER_MAX_DIST;
        float mult = VirtualWeaponLogic.bowDistanceDamageMultiplier(effectiveDist);

        String distText = inRange
                ? String.format(Locale.ROOT, "DIST: %dm", Math.round(dist))
                : "DIST: 96m+";
        String multText = String.format(Locale.ROOT, "x%.1f", mult);
        int multCol = mult >= 2.0F ? GOLD : (mult > 1.05F ? 0xFF5CFFA8 : CYAN);

        int tx = cx + 16;
        int ty = cy - 11;
        int boxW = Math.max(font.width(distText), font.width(multText)) + 6;
        g.fill(tx - 3, ty - 2, tx - 3 + boxW, ty + 19, 0x88040C16);
        g.fill(tx - 3, ty - 2, tx - 2, ty + 19, multCol);
        g.drawString(font, distText, tx, ty, TEXT_LIGHT, false);
        g.drawString(font, multText, tx, ty + 10, multCol, false);

        // Subtle magnification readout above optic ring
        float fovMult = VirtualWeaponLogic.bowScopeFovMultiplier(draw);
        float zoom = 1.0F / Math.max(0.1F, fovMult);
        String zoomStr = String.format(Locale.ROOT, "OPTIC %.1fx", zoom);
        g.drawCenteredString(font, zoomStr, cx, cy - opticR - 14, ringCol);
    }

    /** Measures exact 3D distance to the aimed entity or block up to {@code maxRange} (returns -1 if beyond range). */
    private static double measureScopeDistance(Minecraft mc, Player player, double maxRange) {
        if (mc.level == null) {
            return -1.0D;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(look.scale(maxRange));

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double bestDistSqr = blockHit.getType() == HitResult.Type.MISS
                ? maxRange * maxRange
                : blockHit.getLocation().distanceToSqr(eye);
        boolean hitAny = blockHit.getType() != HitResult.Type.MISS;

        AABB box = player.getBoundingBox().expandTowards(look.scale(maxRange)).inflate(1.25D);
        EntityHitResult entHit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(),
                bestDistSqr);
        if (entHit != null) {
            double ed = entHit.getLocation().distanceToSqr(eye);
            if (ed <= bestDistSqr) {
                bestDistSqr = ed;
                hitAny = true;
            }
        }
        return hitAny ? Math.sqrt(bestDistSqr) : -1.0D;
    }

    /**
     * Draws the slim right-side semicircular overheat arc ({@code r=15px, -65°..+65°, 2.5px wide})
     * beside the crosshair while holding {@code pulse_laser}: dark translucent track + smooth
     * bottom-to-top fill in Cyan -> Amber -> Danger Red (flashing when overheated).
     */
    private static void drawLaserRightHeatArc(GuiGraphics g, int cx, int cy,
                                              float heatRatio, boolean overheated, long nowMs) {
        float r = VirtualHudMath.LASER_ARC_RADIUS;
        float ratio = Math.max(0.0F, Math.min(1.0F, heatRatio));
        int trackBg = 0x7705121D;
        int trackRim = 0x553CF2FF;
        int fillCol = VirtualHudMath.laserHeatArcColor(ratio, overheated, nowMs);

        int steps = 44;
        // 1. Subtle 2.5px semi-transparent arc track (-65° .. +65°)
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            for (float dr : new float[] {-0.8F, 0.0F, 0.8F}) {
                VirtualHudMath.ArcPoint pt = VirtualHudMath.laserHeatArcPoint(cx, cy, r + dr, t);
                int px = Math.round(pt.x());
                int py = Math.round(pt.y());
                g.fill(px, py, px + 1, py + 1, dr == 0.8F ? trackRim : trackBg);
            }
        }

        // 2. Active heat fill sweeping smoothly from bottom (+65°, t=0) upward to top (-65°, t=ratio)
        if (ratio > 0.005F || overheated) {
            float fillMax = overheated ? 1.0F : ratio;
            int fillSteps = Math.max(2, Math.round(steps * fillMax));
            for (int i = 0; i <= fillSteps; i++) {
                float t = (float) i / (float) steps;
                for (float dr : new float[] {-0.8F, 0.0F, 0.8F}) {
                    VirtualHudMath.ArcPoint pt = VirtualHudMath.laserHeatArcPoint(cx, cy, r + dr, t);
                    int px = Math.round(pt.x());
                    int py = Math.round(pt.y());
                    g.fill(px, py, px + 1, py + 1, fillCol);
                }
            }
            // Crisp white leading-edge tip spark at the top of the current heat bar
            VirtualHudMath.ArcPoint tip = VirtualHudMath.laserHeatArcPoint(cx, cy, r, fillMax);
            int tx = Math.round(tip.x());
            int ty = Math.round(tip.y());
            g.fill(tx - 1, ty, tx + 2, ty + 1, 0xEEFFFFFF);
        }
    }

    /** Draws an ultra-thin 1px translucent cyan precision lock ring around the crosshair for NARROW mode. */
    private static void drawSubtleLockCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = 72;
        int prevX = Integer.MIN_VALUE;
        int prevY = Integer.MIN_VALUE;
        for (int i = 0; i < steps; i++) {
            double ang = (i * Math.PI * 2.0D) / steps;
            int px = cx + (int) Math.round(Math.cos(ang) * r);
            int py = cy + (int) Math.round(Math.sin(ang) * r);
            if (px != prevX || py != prevY) {
                g.fill(px, py, px + 1, py + 1, color);
                prevX = px;
                prevY = py;
            }
        }
        // Subtle 2px cardinal micro-ticks
        g.fill(cx - r - 2, cy, cx - r, cy + 1, color);
        g.fill(cx + r + 1, cy, cx + r + 3, cy + 1, color);
        g.fill(cx, cy - r - 2, cx + 1, cy - r, color);
        g.fill(cx, cy + r + 1, cx + 1, cy + r + 3, color);
    }

    private static void drawReticleBrackets(GuiGraphics g, int cx, int cy, int r, int color) {
        int arm = 5;
        // Top-left
        g.fill(cx - r, cy - r, cx - r + arm, cy - r + 1, color);
        g.fill(cx - r, cy - r, cx - r + 1, cy - r + arm, color);
        // Top-right
        g.fill(cx + r - arm, cy - r, cx + r, cy - r + 1, color);
        g.fill(cx + r - 1, cy - r, cx + r, cy - r + arm, color);
        // Bottom-left
        g.fill(cx - r, cy + r - 1, cx - r + arm, cy + r, color);
        g.fill(cx - r, cy + r - arm, cx - r + 1, cy + r, color);
        // Bottom-right
        g.fill(cx + r - arm, cy + r - 1, cx + r, cy + r, color);
        g.fill(cx + r - 1, cy + r - arm, cx + r, cy + r, color);
    }

    // -------------------------------------------------------------------------
    // Boot Sequence Banner
    // -------------------------------------------------------------------------

    private static void renderBootBanner(GuiGraphics g, Font font, int screenW, long elapsedMs) {
        double alpha = elapsedMs < 250L
                ? elapsedMs / 250.0D
                : (elapsedMs > VirtualHudMath.BOOT_DURATION_MS
                        ? Math.max(0.0D, 1.0D - (elapsedMs - VirtualHudMath.BOOT_DURATION_MS) / 600.0D)
                        : 1.0D);
        if (alpha <= 0.04D) {
            return;
        }
        int aByte = Math.max(10, Math.min(255, (int) Math.round(alpha * 255.0D)));
        int bgAlpha = Math.max(10, Math.min(200, (int) Math.round(alpha * 190.0D)));

        Component msg = Component.translatable("gui.industrialcore.ar_hud.link_established");
        int w = Math.max(180, font.width(msg) + 28);
        int x = (screenW - w) / 2;
        int y = 16;

        g.fill(x, y, x + w, y + 18, (bgAlpha << 24) | 0x05101C);
        int border = (aByte << 24) | (CYAN & 0x00FFFFFF);
        int goldCol = (aByte << 24) | (GOLD & 0x00FFFFFF);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + 17, x + w, y + 18, border);
        g.fill(x, y, x + 3, y + 18, goldCol);
        g.fill(x + w - 3, y, x + w, y + 18, goldCol);
        g.drawCenteredString(font, msg, screenW / 2, y + 5, (aByte << 24) | (TEXT_LIGHT & 0x00FFFFFF));
    }

    // -------------------------------------------------------------------------
    // 3-Layer Depth Frame, Segmented Shield Bar, & Slanted Bar Primitives
    // -------------------------------------------------------------------------

    private static void renderThreeLayerFrame(GuiGraphics g, float baseX, float baseY,
                                              int w, int h, int primary, int secondary,
                                              boolean leftSide, float tiltDeg, long now) {
        int cut = 6;
        PoseStack pose = g.pose();

        // Layer 1 — Back (0.60x parallax): Depth Drop-Shadow + Translucent Glass + Micro-Grid + Scanlines
        float bx = baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_BACK, VirtualHudMath.MAX_LAYER_OFFSET_X);
        float by = baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_BACK, VirtualHudMath.MAX_LAYER_OFFSET_Y);
        pose.pushPose();
        pose.translate(bx, by, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tiltDeg));

        int depthX = leftSide ? 4 : -4;
        drawChamferedRect(g, depthX, 4, w, h, cut, SHADOW_BG);
        drawChamferedRect(g, 0, 0, w, h, cut, GLASS_BG);

        // Micro-grid + animated scanlines
        for (int gx = 16; gx < w - 4; gx += 16) {
            g.fill(gx, 3, gx + 1, h - 3, 0x0D42F5FF);
        }
        int phase = (int) ((now / 90L) % 4L);
        for (int sy = 2 + phase; sy < h - 2; sy += 4) {
            g.fill(3, sy, w - 3, sy + 1, 0x1642F5FF);
        }
        pose.popPose();

        // Layer 2 — Mid (1.00x parallax): Chamfered Neon Frame + Floating Outer Corner Brackets
        float mx = baseX + VirtualHudMath.layerOffset(parX, VirtualHudMath.DEPTH_MID, VirtualHudMath.MAX_LAYER_OFFSET_X);
        float my = baseY + VirtualHudMath.layerOffset(parY, VirtualHudMath.DEPTH_MID, VirtualHudMath.MAX_LAYER_OFFSET_Y);
        pose.pushPose();
        pose.translate(mx, my, 0.0F);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tiltDeg));

        g.fill(cut, 0, w - 1, 1, primary);
        g.fill(1, h - 1, w - cut, h, primary);
        g.fill(0, cut, 1, h - 1, primary);
        g.fill(w - 1, 1, w, h - cut, primary);

        for (int i = 0; i < cut; i++) {
            g.fill(i, cut - i, i + 1, cut - i + 1, secondary);
            g.fill(w - 1 - i, h - cut + i, w - i, h - cut + i + 1, secondary);
        }

        // Header divider & accent strip
        if (h >= 40) {
            g.fill(2, 13, w - 2, 14, 0x4442F5FF);
        }
        if (leftSide) {
            g.fill(2, 3, 4, 11, secondary);
        } else {
            g.fill(w - 4, 3, w - 2, 11, secondary);
        }

        // Floating outer corner brackets (3D holographic depth feel)
        g.fill(w - 9, -2, w + 2, -1, secondary);
        g.fill(w + 1, -2, w + 2, 8, secondary);
        g.fill(-2, h + 1, 9, h + 2, secondary);
        g.fill(-2, h - 7, -1, h + 2, secondary);

        pose.popPose();
    }

    static void drawChamferedRect(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        for (int row = 0; row < h; row++) {
            int leftInset = row < cut ? (cut - row) : 0;
            int rightInset = row >= h - cut ? (row - (h - cut) + 1) : 0;
            if (x + leftInset < x + w - rightInset) {
                g.fill(x + leftInset, y + row, x + w - rightInset, y + row + 1, color);
            }
        }
    }

    /** Segmented glowing energy shield bar (4 cells with crisp gaps). */
    private static void drawSegmentedShieldBar(GuiGraphics g, int x, int y, int w, int h,
                                               float ratio, int segments, int fillCol) {
        int segCount = Math.max(1, segments);
        int gap = 2;
        int segW = Math.max(4, (w - gap * (segCount - 1)) / segCount);
        float totalFill = Math.max(0.0F, Math.min(1.0F, ratio)) * segCount;

        for (int i = 0; i < segCount; i++) {
            int sx = x + i * (segW + gap);
            g.fill(sx, y, sx + segW, y + h, 0xCC0A1826);
            float cellFill = Math.max(0.0F, Math.min(1.0F, totalFill - i));
            if (cellFill > 0.0F) {
                int fw = Math.max(1, Math.round(segW * cellFill));
                g.fill(sx, y, sx + fw, y + 1, VirtualHudMath.blend(fillCol, 0xFFFFFFFF, 0.45F));
                if (h > 1) {
                    g.fill(sx, y + 1, sx + fw, y + h, fillCol);
                }
            }
        }
    }

    /** Slanted parallelogram glowing gauge bar. */
    private static void drawSlantedBar(GuiGraphics g, int x, int y, int w, int h,
                                       float ratio, float secondaryRatio, int fillCol) {
        int skew = Math.min(4, h / 2 + 1);
        for (int row = 0; row < h; row++) {
            int offset = skew - (row * skew) / Math.max(1, h - 1);
            int rx0 = x + offset;
            int rx1 = x + w - (skew - offset);
            int span = Math.max(1, rx1 - rx0);

            g.fill(rx0, y + row, rx1, y + row + 1, 0xCC0A1622);

            int secW = Math.round(span * Math.max(0.0F, Math.min(1.0F, secondaryRatio)));
            if (secW > 0) {
                g.fill(rx0, y + row, rx0 + secW, y + row + 1, 0x99FFF2B0);
            }

            int fillW = Math.round(span * Math.max(0.0F, Math.min(1.0F, ratio)));
            if (fillW > 0) {
                int col = row == 0 ? VirtualHudMath.blend(fillCol, 0xFFFFFFFF, 0.35F) : fillCol;
                g.fill(rx0, y + row, rx0 + fillW, y + row + 1, col);
            }
        }
    }

    @Nullable
    private static LivingEntity findLookTarget(Minecraft mc, Player player, double range) {
        if (mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable() && e.isAlive(),
                range * range);
        if (hit != null) {
            Entity e = hit.getEntity();
            if (e instanceof LivingEntity le) {
                return le;
            }
        }
        return null;
    }
}
