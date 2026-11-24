package com.industrialcore.client;

import com.industrialcore.suit.VirtualHudMath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * Phase 100: Holographic Virtual Inventory screen extending vanilla {@link InventoryScreen}
 * so JEI overlays, Recipe Book, Shift-click, drag-splitting, and armor/offhand slots retain
 * 100% native compatibility.
 *
 * <p>Replaces the gray vanilla background with a 3D chamfered translucent hologram frame,
 * 150ms open unfold animation, mouse-driven background parallax (depth shadow, grid, rotating
 * player projection ring), and neon slot frames — while keeping every slot's click hitbox
 * strictly locked to exact vanilla coordinates.</p>
 */
public class VirtualInventoryScreen extends InventoryScreen {
    private static final int GLASS_BG = 0xCC050E1A;
    private static final int SHADOW_BG = 0x8002050B;
    private static final int CYAN = 0xFF42F5FF;
    private static final int CYAN_DIM = 0x991F7A99;
    private static final int GOLD = 0xFFFFD166;
    private static final int MAGENTA = 0xFFD95BFF;
    private static final int SLOT_BG = 0xB8081422;

    private final long openTimeMs;

    public VirtualInventoryScreen(Player player) {
        super(player);
        this.openTimeMs = Util.getMillis();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        long now = Util.getMillis();

        double unfold = VirtualHudMath.inventoryOpenProgress(now - openTimeMs);
        float bgPx = VirtualHudMath.inventoryBackParallax(
                mouseX, left + w * 0.5D, Math.max(88.0D, w * 0.75D), VirtualHudMath.INV_MAX_PARALLAX_X);
        float bgPy = VirtualHudMath.inventoryBackParallax(
                mouseY, top + h * 0.5D, Math.max(83.0D, h * 0.75D), VirtualHudMath.INV_MAX_PARALLAX_Y);

        PoseStack pose = g.pose();

        // 1. Background Hologram Layers (mouse-parallaxed + 150ms scale unfold, never affects slot clicks!)
        pose.pushPose();
        float cx = left + w * 0.5F;
        float cy = top + h * 0.5F;
        float scale = (float) (0.90D + 0.10D * unfold);
        pose.translate(cx + bgPx, cy + bgPy, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-w * 0.5F, -h * 0.5F, 0.0F);

        int cut = 7;
        // Depth drop-shadow glass
        VirtualHudOverlay.drawChamferedRect(g, 5, 5, w, h, cut, SHADOW_BG);
        // Main translucent hologram glass
        VirtualHudOverlay.drawChamferedRect(g, 0, 0, w, h, cut, GLASS_BG);

        // Micro-grid + animated horizontal scanlines
        for (int gx = 16; gx < w - 4; gx += 16) {
            g.fill(gx, 4, gx + 1, h - 4, 0x1042F5FF);
        }
        int scanPhase = (int) ((now / 95L) % 4L);
        for (int sy = 3 + scanPhase; sy < h - 3; sy += 4) {
            g.fill(4, sy, w - 4, sy + 1, 0x1442F5FF);
        }

        // Chamfered neon cyan/magenta outline
        g.fill(cut, 0, w - 1, 1, CYAN);
        g.fill(1, h - 1, w - cut, h, CYAN);
        g.fill(0, cut, 1, h - 1, CYAN);
        g.fill(w - 1, 1, w, h - cut, CYAN);
        for (int i = 0; i < cut; i++) {
            g.fill(i, cut - i, i + 1, cut - i + 1, MAGENTA);
            g.fill(w - 1 - i, h - cut + i, w - i, h - cut + i + 1, MAGENTA);
        }

        // Floating outer corner brackets
        g.fill(w - 12, -3, w + 3, -2, GOLD);
        g.fill(w + 2, -3, w + 3, 10, GOLD);
        g.fill(-3, h + 2, 12, h + 3, GOLD);
        g.fill(-3, h - 10, -2, h + 3, GOLD);

        // Player Preview Holo-Chamber (x=25..76, y=7..79) + Rotating Cyan Projection Ring!
        g.fill(26, 8, 76, 78, 0x7A040B14);
        g.fill(26, 8, 76, 9, CYAN_DIM);
        g.fill(26, 77, 76, 78, CYAN);
        g.fill(26, 8, 27, 78, CYAN_DIM);
        g.fill(75, 8, 76, 78, CYAN_DIM);
        drawRotatingProjectionRing(g, 51, 70, now);

        pose.popPose();

        // 2. Fixed Foreground Neon Slot Frames (100% locked to exact vanilla slot coordinates!)
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = left + slot.x - 1;
            int sy = top + slot.y - 1;
            int border;
            if (slot.index == 0) {
                border = GOLD; // Craft result
            } else if (slot.index >= 1 && slot.index <= 4) {
                border = MAGENTA; // 2x2 craft grid
            } else if ((slot.index >= 5 && slot.index <= 8) || slot.index == 45) {
                border = GOLD; // Armor & Offhand
            } else {
                border = CYAN_DIM; // Main inventory & hotbar
            }
            g.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
            g.fill(sx, sy, sx + 18, sy + 1, border);
            g.fill(sx, sy + 17, sx + 18, sy + 18, border);
            g.fill(sx, sy, sx + 1, sy + 18, border);
            g.fill(sx + 17, sy, sx + 18, sy + 18, border);
        }

        // Neon craft arrow between 2x2 grid (x=134) and craft result slot (x=154)
        int ax = left + 136;
        int ay = top + 34;
        g.fill(ax, ay + 2, ax + 10, ay + 4, CYAN);
        g.fill(ax + 8, ay, ax + 10, ay + 6, CYAN);
        g.fill(ax + 10, ay + 1, ax + 12, ay + 5, GOLD);
        g.fill(ax + 12, ay + 2, ax + 14, ay + 4, GOLD);

        // 3. Live 3D Player Entity Preview inside the holo-chamber
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, left + 26, top + 8, left + 75, top + 78,
                    30, 0.0625F, mouseX, mouseY, this.minecraft.player);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Crisp cyan crafting title matching vanilla position (x=97, y=6) — zero overlap with slots!
        g.drawString(this.font, this.title, 97, 6, CYAN, false);
        Component tag = Component.translatable("gui.industrialcore.virtual_inv.badge");
        g.drawString(this.font, tag, this.imageWidth - 7 - this.font.width(tag), 6, GOLD, false);
    }

    private static void drawRotatingProjectionRing(GuiGraphics g, int cx, int cy, long now) {
        double angle = (now * 0.004D) % (Math.PI * 2.0D);
        // Subtle vertical cyan hologram projection beam
        g.fillGradient(cx - 18, cy - 32, cx + 18, cy + 2, 0x0542F5FF, 0x2642F5FF);
        // Elliptical outer & inner rotating segments
        int rx = 20;
        int ry = 5;
        for (int i = 0; i < 24; i++) {
            double a = angle + (i * Math.PI * 2.0D / 24.0D);
            int px = cx + (int) Math.round(Math.cos(a) * rx);
            int py = cy + (int) Math.round(Math.sin(a) * ry);
            int col = (i % 6 == 0) ? GOLD : ((i % 2 == 0) ? CYAN : CYAN_DIM);
            g.fill(px, py, px + 2, py + 1, col);
        }
        int irx = 12;
        int iry = 3;
        for (int i = 0; i < 16; i++) {
            double a = -angle * 1.4D + (i * Math.PI * 2.0D / 16.0D);
            int px = cx + (int) Math.round(Math.cos(a) * irx);
            int py = cy + (int) Math.round(Math.sin(a) * iry);
            g.fill(px, py, px + 1, py + 1, (i % 4 == 0) ? MAGENTA : CYAN);
        }
    }
}
