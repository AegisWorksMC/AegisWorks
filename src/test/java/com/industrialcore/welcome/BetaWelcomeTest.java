package com.industrialcore.welcome;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM unit tests for the AegisWorks Open Beta first-login Discord welcome notice:
 * deduplication &amp; config gating, brand colors, click URL / hover specs, config registration,
 * and exact Japanese/English localization strings.
 */
class BetaWelcomeTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/industrialcore/lang");

    @Test
    void showsOnceWhenEnabledAndNeverRepeatsOrShowsWhenDisabled() {
        // First login with config enabled -> show
        assertTrue(BetaWelcomeLogic.shouldSend(false, true));
        // Relog (alreadyShown=true) -> suppress
        assertFalse(BetaWelcomeLogic.shouldSend(true, true));
        // Config disabled even on first login -> suppress
        assertFalse(BetaWelcomeLogic.shouldSend(false, false));
        // Config disabled and already shown -> suppress
        assertFalse(BetaWelcomeLogic.shouldSend(true, false));
    }

    @Test
    void messageLineSpecsMatchBrandColorsClickUrlAndTranslatableKeys() {
        List<BetaWelcomeLogic.LineSpec> lines = BetaWelcomeLogic.messageLines();
        assertEquals(3, lines.size());

        // Header: bold cyan (#00E5FF)
        BetaWelcomeLogic.LineSpec header = lines.get(0);
        assertEquals("message.aegisworks.beta_welcome.header", header.translationKey());
        assertEquals(0x00E5FF, header.rgbColor());
        assertTrue(header.bold());
        assertNull(header.clickOpenUrl());

        // Body: gray (#B0BEC5)
        BetaWelcomeLogic.LineSpec body = lines.get(1);
        assertEquals("message.aegisworks.beta_welcome.body", body.translationKey());
        assertEquals(0xB0BEC5, body.rgbColor());
        assertFalse(body.bold());
        assertNull(body.clickOpenUrl());

        // Link: bold underlined orange (#FFA000) + exact Discord URL + hover key
        BetaWelcomeLogic.LineSpec link = lines.get(2);
        assertEquals("message.aegisworks.beta_welcome.link", link.translationKey());
        assertEquals(0xFFA000, link.rgbColor());
        assertTrue(link.bold());
        assertTrue(link.underlined());
        assertEquals("https://discord.gg/QKW9fhP2de", link.clickOpenUrl());
        assertEquals("message.aegisworks.beta_welcome.hover", link.hoverTranslationKey());
    }

    @Test
    void configAndEventListenersAreRegisteredProperly() throws IOException {
        String configSrc = Files.readString(Path.of("src/main/java/com/industrialcore/Config.java"));
        assertTrue(configSrc.contains("\"showBetaWelcomeMessage\", true"),
                "Config must define showBetaWelcomeMessage defaulting to true");
        assertTrue(configSrc.contains("public static boolean showBetaWelcomeMessage()"));

        String modSrc = Files.readString(Path.of("src/main/java/com/industrialcore/IndustrialCore.java"));
        assertTrue(modSrc.contains("BetaWelcomeHandler::onPlayerLoggedIn"),
                "IndustrialCore must register PlayerLoggedInEvent listener");
        assertTrue(modSrc.contains("BetaWelcomeHandler::onPlayerClone"),
                "IndustrialCore must register PlayerEvent.Clone listener");
    }

    @Test
    void exactJapaneseAndEnglishTranslationsExist() throws IOException {
        JsonObject ja = JsonParser.parseString(Files.readString(LANG_DIR.resolve("ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(LANG_DIR.resolve("en_us.json"))).getAsJsonObject();

        assertEquals("━━━ [ AegisWorks Open Beta ] ━━━",
                ja.get("message.aegisworks.beta_welcome.header").getAsString());
        assertEquals("AegisWorks βテストへのご参加ありがとうございます！バグ報告やバランス調整のご要望は公式Discordでお待ちしています。",
                ja.get("message.aegisworks.beta_welcome.body").getAsString());
        assertEquals("[ ▶ 公式Discordサーバーに参加する ]",
                ja.get("message.aegisworks.beta_welcome.link").getAsString());
        assertEquals("クリックしてブラウザでDiscord招待リンクを開きます",
                ja.get("message.aegisworks.beta_welcome.hover").getAsString());

        assertEquals("━━━ [ AegisWorks Open Beta ] ━━━",
                en.get("message.aegisworks.beta_welcome.header").getAsString());
        assertEquals("Thanks for joining the AegisWorks Open Beta! Report bugs, share feedback, and claim your @Beta Tester role on our Discord.",
                en.get("message.aegisworks.beta_welcome.body").getAsString());
        assertEquals("[ ▶ Join Official Discord Server ]",
                en.get("message.aegisworks.beta_welcome.link").getAsString());
        assertEquals("Click to open the Discord invite link in your browser",
                en.get("message.aegisworks.beta_welcome.hover").getAsString());
    }
}
