package com.industrialcore.weapon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 105.8 Pre-Beta Polish unit tests:
 * <ul>
 *   <li>All cable/pipe/conduit blocks enforce {@code .forceSolidOn()} for water/lava flow immunity.</li>
 *   <li>{@code reactor_glass} has translucent render_type, transparent viewing pane, and culling overrides.</li>
 *   <li>{@code energy_bow} uses vanilla bow display transforms and top-left aimed 32x32 HD arrow orientation.</li>
 *   <li>{@code weapon_railgun_fire.ogg} is valid Mono 44.1kHz Ogg Vorbis.</li>
 *   <li>Symmetric Japanese &amp; English tooltips exist for {@code fluid_pipe} and {@code fluid_pump}.</li>
 * </ul>
 */
class Phase1058PolishTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path MOD_BLOCKS = Path.of("src/main/java/com/industrialcore/registry/ModBlocks.java");
    private static final Path REACTOR_GLASS_BLOCK = Path.of("src/main/java/com/industrialcore/block/ReactorGlassBlock.java");

    @Test
    void allCablePipeAndConduitBlocksEnforceForceSolidOnForFluidImmunity() throws IOException {
        String src = Files.readString(MOD_BLOCKS);
        for (String marker : List.of(
                "private static DeferredBlock<CableBlock> registerCable",
                "private static DeferredBlock<ItemPipeBlock> registerPipe",
                "\"fluid_pipe\"",
                "\"hyper_energy_conduit\"",
                "\"hyper_item_conduit\"")) {
            int idx = src.indexOf(marker);
            assertTrue(idx >= 0, "Missing registration marker: " + marker);
            String snippet = src.substring(idx, Math.min(src.length(), idx + 420));
            assertTrue(snippet.contains(".forceSolidOn()"),
                    "Conduit/pipe missing .forceSolidOn() water-flow immunity near " + marker);
        }
    }

    @Test
    void reactorGlassIsFullyTransparentWithTranslucentModelAndCulling() throws IOException {
        JsonObject model = JsonParser.parseString(
                Files.readString(ASSETS.resolve("models/block/reactor_glass.json"))).getAsJsonObject();
        assertEquals("minecraft:translucent", model.get("render_type").getAsString());

        BufferedImage img = ImageIO.read(ASSETS.resolve("textures/block/reactor_glass.png").toFile());
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        int clearInterior = 0;
        for (int y = 2; y <= 13; y++) {
            for (int x = 2; x <= 13; x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    clearInterior++;
                }
            }
        }
        assertTrue(clearInterior >= 110,
                "reactor_glass interior must be mostly crystal-clear (alpha=0), got " + clearInterior);

        String glassSrc = Files.readString(REACTOR_GLASS_BLOCK);
        assertTrue(glassSrc.contains("skipRendering"));
        assertTrue(glassSrc.contains("getVisualShape"));
        assertTrue(glassSrc.contains("propagatesSkylightDown"));
    }

    @Test
    void energyBowModelUsesVanillaBowDisplayAndTopLeftAimed32x32Texture() throws IOException {
        JsonObject model = JsonParser.parseString(
                Files.readString(ASSETS.resolve("models/item/energy_bow.json"))).getAsJsonObject();
        assertTrue(model.has("display"), "energy_bow.json must define vanilla bow display transforms");
        JsonObject disp = model.getAsJsonObject("display");
        for (String pose : List.of("firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand")) {
            assertTrue(disp.has(pose), "energy_bow.json missing display." + pose);
        }

        BufferedImage bow = ImageIO.read(ASSETS.resolve("textures/item/energy_bow.png").toFile());
        assertEquals(32, bow.getWidth());
        assertEquals(32, bow.getHeight());
        int topLeftBright = 0;
        for (int y = 1; y <= 8; y++) {
            for (int x = 1; x <= 8; x++) {
                int argb = bow.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int b = argb & 0xFF;
                if (a > 200 && b > 200) {
                    topLeftBright++;
                }
            }
        }
        assertTrue(topLeftBright >= 8,
                "energy_bow.png must aim its glowing energy arrowhead toward top-left (vanilla bow diagonal)");
    }

    @Test
    void railgunNuclearFireSfxIsValidMono44100HzOggVorbis() throws IOException {
        byte[] ogg = Files.readAllBytes(ASSETS.resolve("sounds/weapon_railgun_fire.ogg"));
        assertTrue(ogg.length >= 15_000, "Nuclear railgun fire OGG unexpectedly small: " + ogg.length);
        assertEquals('O', (char) ogg[0]);
        assertEquals('g', (char) ogg[1]);
        assertEquals('g', (char) ogg[2]);
        assertEquals('S', (char) ogg[3]);

        // Locate Vorbis identification header (\x01vorbis)
        int vIdx = -1;
        for (int i = 0; i < Math.min(ogg.length - 16, 128); i++) {
            if (ogg[i] == 1 && ogg[i + 1] == 'v' && ogg[i + 2] == 'o' && ogg[i + 3] == 'r'
                    && ogg[i + 4] == 'b' && ogg[i + 5] == 'i' && ogg[i + 6] == 's') {
                vIdx = i;
                break;
            }
        }
        assertTrue(vIdx > 0, "Missing Vorbis ID header in weapon_railgun_fire.ogg");
        int channels = ogg[vIdx + 11] & 0xFF;
        int sampleRate = ByteBuffer.wrap(ogg, vIdx + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertEquals(1, channels, "weapon_railgun_fire.ogg must be Mono (1 channel)");
        assertEquals(44_100, sampleRate, "weapon_railgun_fire.ogg must be 44,100 Hz");
    }

    @Test
    void fluidPipeAndPumpUsageTooltipsExistInJapaneseAndEnglish() throws IOException {
        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        for (String key : List.of(
                "tooltip.industrialcore.fluid_pipe.usage",
                "tooltip.industrialcore.fluid_pipe.connect",
                "tooltip.industrialcore.fluid_pump.usage",
                "tooltip.industrialcore.fluid_pump.infinite")) {
            assertTrue(ja.has(key), "ja_jp missing " + key);
            assertTrue(en.has(key), "en_us missing " + key);
        }
    }
}
