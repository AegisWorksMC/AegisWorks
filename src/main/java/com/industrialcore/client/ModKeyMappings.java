package com.industrialcore.client;

import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.network.LaserRangeTogglePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for Industrial Core (Phase 85: dedicated Skill Tree & Guide hotkey, default {@code K}).
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.industrialcore";

    public static final KeyMapping SKILL_TREE = new KeyMapping(
            "key.industrialcore.skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    /** Phase 98: Instant toggle for Virtual Aegis 3D AR Hologram HUD (default {@code V}). */
    public static final KeyMapping AR_HUD_TOGGLE = new KeyMapping(
            "key.industrialcore.ar_hud_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    /** Pulse Laser auto-aim FOV range toggle (NARROW 50° / WIDE 120° / FULL_360, default {@code R}). */
    public static final KeyMapping LASER_RANGE_TOGGLE = new KeyMapping(
            "key.industrialcore.laser_range",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    /** Phase 108: Multiblock Guide layer slice toggle (ALL / Layer 1..N; Shift+G cycles size, default {@code G}). */
    public static final KeyMapping REACTOR_GUIDE_LAYER = new KeyMapping(
            "key.industrialcore.reactor_guide_layer",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    private ModKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SKILL_TREE);
        event.register(AR_HUD_TOGGLE);
        event.register(LASER_RANGE_TOGGLE);
        event.register(REACTOR_GUIDE_LAYER);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.screen != null) {
            return;
        }
        while (SKILL_TREE.consumeClick()) {
            SoundDirector.playClick();
            mc.setScreen(new SkillTreeScreen(null, null));
        }
        while (AR_HUD_TOGGLE.consumeClick()) {
            SoundDirector.playClick();
            VirtualHudOverlay.toggleHud();
        }
        while (LASER_RANGE_TOGGLE.consumeClick()) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            if (main.getItem() instanceof PulseLaserItem || off.getItem() instanceof PulseLaserItem) {
                PacketDistributor.sendToServer(LaserRangeTogglePayload.INSTANCE);
            }
        }
        while (REACTOR_GUIDE_LAYER.consumeClick()) {
            if (ReactorGuideRenderer.isAnyGuideActive()) {
                if (mc.player.isShiftKeyDown()) {
                    ReactorGuideRenderer.cycleSize();
                } else {
                    ReactorGuideRenderer.cycleLayer();
                }
            }
        }
    }
}
