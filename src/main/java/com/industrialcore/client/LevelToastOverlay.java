package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.level.LevelToastQueue;
import com.industrialcore.level.MachineSkillTree;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Phase 85 neon HUD toast overlay for Machine Level-Up and Limit-Break celebrations.
 *
 * <p>Backed by pure-Java {@link LevelToastQueue} so rapid multi-machine level-ups coalesce
 * cleanly without flooding the top of the screen or distorting audio.</p>
 */
public final class LevelToastOverlay {
    private static final LevelToastQueue QUEUE = new LevelToastQueue();
    private static final int GOLD = 0xFFFFD166;
    private static final int GOLD_BRIGHT = 0xFFFFF3B0;

    private LevelToastOverlay() {}

    public static void clear() {
        QUEUE.clear();
    }

    public static void notifyLevelUp(String primaryMachineId, int newLevel, int maxLevel, int extraMachines) {
        long now = Util.getMillis();
        boolean playSe = QUEUE.pushLevelUp(primaryMachineId, newLevel, maxLevel, extraMachines, now);
        if (playSe) {
            SoundDirector.playLevelUp();
        }
    }

    public static void notifyNewUnlock(int newlyUnlockedCount) {
        long now = Util.getMillis();
        boolean playSe = QUEUE.pushNewUnlock(newlyUnlockedCount, now);
        if (playSe) {
            SoundDirector.playNodeUnlock();
        }
    }

    public static void notifyLimitBreak() {
        long now = Util.getMillis();
        QUEUE.pushLimitBreak(now);
        SoundDirector.playLimitBreakFanfare();
    }

    public static boolean hasActiveToasts() {
        return !QUEUE.activeEntries(Util.getMillis()).isEmpty();
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options.hideGui) {
            return;
        }
        long now = Util.getMillis();
        List<LevelToastQueue.Entry> active = QUEUE.activeEntries(now);
        if (active.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int yCursor = 8;

        for (LevelToastQueue.Entry entry : active) {
            double age = entry.ageRatio(now);
            // Smooth slide-in during first 10%, hold, fade/slide-out during last 18%.
            double slideIn = age < 0.10D ? easeOutCubic(age / 0.10D) : 1.0D;
            double fadeOut = age > 0.82D ? Math.max(0.0D, 1.0D - (age - 0.82D) / 0.18D) : 1.0D;
            double vis = Math.min(slideIn, fadeOut);
            if (vis <= 0.02D) {
                continue;
            }

            Component title;
            Component sub;
            ItemStack icon;
            int accent;
            int accentBright;

            if (entry.limitBreak()) {
                title = Component.translatable("gui.industrialcore.toast.limit_break_title");
                sub = Component.translatable("gui.industrialcore.toast.limit_break_sub", entry.maxLevel());
                icon = new ItemStack(Items.NETHER_STAR);
                double pulse = 0.5D + 0.5D * Math.sin(now * 0.012D);
                accent = MachineTheme.blend(GOLD, MachineTheme.PURPLE_BRIGHT, pulse * 0.45D);
                accentBright = GOLD_BRIGHT;
            } else if (entry.newUnlock()) {
                int cnt = Math.max(1, entry.newLevel());
                title = cnt > 1
                        ? Component.translatable("gui.industrialcore.toast.new_unlock_multi", cnt)
                        : Component.translatable("gui.industrialcore.toast.new_unlock");
                sub = Component.translatable("gui.industrialcore.toast.new_unlock_sub",
                        ModKeyMappings.SKILL_TREE.getTranslatedKeyMessage());
                icon = new ItemStack(Items.AMETHYST_SHARD);
                double pulse = 0.5D + 0.5D * Math.sin(now * 0.010D);
                accent = MachineTheme.blend(GOLD, MachineTheme.CYAN, pulse * 0.45D);
                accentBright = GOLD_BRIGHT;
            } else {
                MachineSkillTree.Node node = MachineSkillTree.byId(entry.primaryMachineId());
                Component machineName = node != null
                        ? Component.translatable(node.translationKey())
                        : Component.literal(entry.primaryMachineId());
                if (entry.extraMachines() > 0) {
                    title = Component.translatable("gui.industrialcore.toast.level_up_multi",
                            machineName, entry.newLevel(), entry.extraMachines());
                } else {
                    title = Component.translatable("gui.industrialcore.toast.level_up",
                            machineName, entry.newLevel());
                }
                sub = Component.translatable("gui.industrialcore.toast.level_progress",
                        entry.newLevel(), entry.maxLevel());
                icon = blockIcon(entry.primaryMachineId());
                accent = MachineTheme.CYAN;
                accentBright = MachineTheme.CYAN_BRIGHT;
            }

            int textW = Math.max(font.width(title), font.width(sub));
            int cardW = Math.min(screenW - 16, Math.max(156, textW + 38));
            int cardH = 26;
            int x = (screenW - cardW) / 2;
            int yOffset = (int) Math.round((1.0D - slideIn) * -14.0D);
            int y = yCursor + yOffset;

            int alpha = Math.max(24, Math.min(255, (int) Math.round(235.0D * fadeOut)));
            int bgTop = (alpha << 24) | (MachineTheme.PANEL_TOP & 0x00FFFFFF);
            int bgBot = (alpha << 24) | (MachineTheme.BG_BOTTOM & 0x00FFFFFF);
            int border = (alpha << 24) | (accent & 0x00FFFFFF);

            // Multi-layer neon bloom
            MachineTheme.glowRect(graphics, x, y, cardW, cardH, accent,
                    (entry.limitBreak() || entry.newUnlock() ? 0.90D : 0.65D) * vis);
            MachineTheme.gradient(graphics, x, y, cardW, cardH, bgTop, bgBot);

            // Crisp neon border & top energy filament
            graphics.fill(x, y, x + cardW, y + 1, border);
            graphics.fill(x, y + cardH - 1, x + cardW, y + cardH, border);
            graphics.fill(x, y, x + 1, y + cardH, border);
            graphics.fill(x + cardW - 1, y, x + cardW, y + cardH, border);
            graphics.fill(x + 2, y + 1, x + cardW - 2, y + 2,
                    ((int) Math.round(alpha * 0.7D) << 24) | (accentBright & 0x00FFFFFF));

            // Left accent stripe
            graphics.fill(x + 2, y + 3, x + 4, y + cardH - 3, border);

            // Machine / star icon
            graphics.renderItem(icon, x + 8, y + 5);

            // Fitted text lines
            int maxTextW = cardW - 32;
            boolean goldStyle = entry.limitBreak() || entry.newUnlock();
            int titleCol = (alpha << 24) | ((goldStyle ? GOLD_BRIGHT : MachineTheme.TEXT) & 0x00FFFFFF);
            int subCol = (alpha << 24) | ((entry.limitBreak() ? GOLD : MachineTheme.CYAN_BRIGHT) & 0x00FFFFFF);
            graphics.drawString(font, MachineTheme.fit(font, title, maxTextW), x + 28, y + 5, titleCol, true);
            graphics.drawString(font, MachineTheme.fit(font, sub, maxTextW), x + 28, y + 15, subCol, false);

            yCursor += cardH + 6;
        }
    }

    private static ItemStack blockIcon(String blockPath) {
        if (blockPath == null || blockPath.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, blockPath);
        Block block = BuiltInRegistries.BLOCK.get(id);
        return new ItemStack(block);
    }

    private static double easeOutCubic(double t) {
        double inv = 1.0D - Math.max(0.0D, Math.min(1.0D, t));
        return 1.0D - inv * inv * inv;
    }
}
