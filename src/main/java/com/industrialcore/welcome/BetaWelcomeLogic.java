package com.industrialcore.welcome;

import java.util.List;

/**
 * Pure-Java specification and deduplication logic for the AegisWorks Open Beta first-login
 * Discord welcome message. Zero Minecraft/NeoForge imports for deterministic JVM unit testing.
 */
public final class BetaWelcomeLogic {
    public static final String PERSISTENT_FLAG_KEY = "aegisworks_beta_welcome_shown";
    public static final String DISCORD_INVITE_URL = "https://discord.gg/QKW9fhP2de";

    public static final String KEY_HEADER = "message.aegisworks.beta_welcome.header";
    public static final String KEY_BODY = "message.aegisworks.beta_welcome.body";
    public static final String KEY_LINK = "message.aegisworks.beta_welcome.link";
    public static final String KEY_HOVER = "message.aegisworks.beta_welcome.hover";

    /** AegisWorks brand palette */
    public static final int COLOR_CYAN = 0x00E5FF;
    public static final int COLOR_ORANGE = 0xFFA000;
    public static final int COLOR_GRAY = 0xB0BEC5;

    public record LineSpec(
            String translationKey,
            int rgbColor,
            boolean bold,
            boolean underlined,
            String clickOpenUrl,
            String hoverTranslationKey
    ) {}

    private BetaWelcomeLogic() {}

    /**
     * Returns {@code true} only when the welcome message is enabled in config and has not yet
     * been displayed to this player in the current world.
     */
    public static boolean shouldSend(boolean alreadyShown, boolean configEnabled) {
        return configEnabled && !alreadyShown;
    }

    /** Returns the 3 styled message specifications (header, body, clickable Discord link). */
    public static List<LineSpec> messageLines() {
        return List.of(
                new LineSpec(KEY_HEADER, COLOR_CYAN, true, false, null, null),
                new LineSpec(KEY_BODY, COLOR_GRAY, false, false, null, null),
                new LineSpec(KEY_LINK, COLOR_ORANGE, true, true, DISCORD_INVITE_URL, KEY_HOVER)
        );
    }
}
