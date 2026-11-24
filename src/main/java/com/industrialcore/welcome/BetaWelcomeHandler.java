package com.industrialcore.welcome;

import com.industrialcore.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a one-time AegisWorks Open Beta welcome message with a clickable official Discord invite
 * link on a player's first login to a world.
 *
 * <p>Controlled by {@link Config#showBetaWelcomeMessage()} (default {@code true}) so streamers and
 * modpack authors can disable it cleanly. Deduplication state is stored in the player's persistent
 * NBT ({@code PlayerPersisted/aegisworks_beta_welcome_shown}) so it survives both relogs and
 * death/respawn within the same world.</p>
 */
public final class BetaWelcomeHandler {
    private BetaWelcomeHandler() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        trySendWelcome(player, Config.showBetaWelcomeMessage());
    }

    /** Preserves the shown flag across player death/respawn clones so relogging after death never re-triggers it. */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        CompoundTag oldPersisted = oldPlayer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (oldPersisted.getBoolean(BetaWelcomeLogic.PERSISTENT_FLAG_KEY)) {
            CompoundTag newRoot = newPlayer.getPersistentData();
            CompoundTag newPersisted = newRoot.getCompound(Player.PERSISTED_NBT_TAG);
            newPersisted.putBoolean(BetaWelcomeLogic.PERSISTENT_FLAG_KEY, true);
            newRoot.put(Player.PERSISTED_NBT_TAG, newPersisted);
        }
    }

    /**
     * Checks config &amp; player persistent NBT, marks the flag on first show, and sends the
     * formatted welcome lines to the player. Returns {@code true} if the message was sent.
     */
    public static boolean trySendWelcome(ServerPlayer player, boolean configEnabled) {
        if (player == null) {
            return false;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        boolean alreadyShown = persisted.getBoolean(BetaWelcomeLogic.PERSISTENT_FLAG_KEY);
        if (!BetaWelcomeLogic.shouldSend(alreadyShown, configEnabled)) {
            return false;
        }
        persisted.putBoolean(BetaWelcomeLogic.PERSISTENT_FLAG_KEY, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        for (MutableComponent line : buildWelcomeMessages()) {
            player.sendSystemMessage(line);
        }
        return true;
    }

    /** Builds the styled, translatable AegisWorks Open Beta chat components. */
    public static List<MutableComponent> buildWelcomeMessages() {
        List<BetaWelcomeLogic.LineSpec> specs = BetaWelcomeLogic.messageLines();
        List<MutableComponent> out = new ArrayList<>(specs.size());
        for (BetaWelcomeLogic.LineSpec spec : specs) {
            Style style = Style.EMPTY
                    .withColor(TextColor.fromRgb(spec.rgbColor()))
                    .withBold(spec.bold())
                    .withUnderlined(spec.underlined());
            if (spec.clickOpenUrl() != null) {
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, spec.clickOpenUrl()));
            }
            if (spec.hoverTranslationKey() != null) {
                MutableComponent hoverText = Component.translatable(spec.hoverTranslationKey())
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(BetaWelcomeLogic.COLOR_CYAN)));
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
            }
            out.add(Component.translatable(spec.translationKey()).withStyle(style));
        }
        return out;
    }
}
