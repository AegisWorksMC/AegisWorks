package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.audio.WorldJoinAudioGuardLogic;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.raid.DimensionalRaidLogic.ShieldHitOutcome;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 119 verification suite:
 * <ul>
 *   <li>High-detail 32x32 textures for all 4 Rift enemies &amp; translucent hexagonal Phase Shield.</li>
 *   <li>Overdrive Phase 2 threshold predicate, directional Phase Shield hit classification,
 *       and particle burst clamp / {@code ParticleStatus.MINIMAL} guard.</li>
 *   <li>High-priority raid milestone audio bypass in {@link WorldJoinAudioGuardLogic}.</li>
 *   <li>All 8 new heavy combat, boss &amp; victory SFX OGGs: valid 1ch Mono / 44.1kHz Vorbis,
 *       plus complete {@code ModSounds}, {@code sounds.json}, and bilingual subtitle registration.</li>
 * </ul>
 */
class Phase119RiftVisualAndFxTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path ENTITY_TEX = ASSETS.resolve("textures/entity");
    private static final Path SOUNDS_DIR = ASSETS.resolve("sounds");

    private static final List<String> PHASE119_SOUNDS = List.of(
            "rift_portal_horn",
            "rift_stalker_blink",
            "void_sentinel_cannon",
            "phase_juggernaut_slam",
            "rift_shield_deflect",
            "sovereign_roar_overdrive",
            "sovereign_defeat_collapse",
            "rift_raid_victory_fanfare"
    );

    @Test
    void allFourEntityTexturesAndTranslucentHexShieldAreHighDetail32x32() throws IOException {
        for (String name : List.of("rift_stalker", "void_sentinel", "phase_juggernaut", "dimensional_sovereign")) {
            Path p = ENTITY_TEX.resolve(name + ".png");
            assertTrue(Files.exists(p), "Missing 32x32 Rift entity texture: " + name);
            BufferedImage img = ImageIO.read(p.toFile());
            assertEquals(32, img.getWidth(), name + " width must be 32px");
            assertEquals(32, img.getHeight(), name + " height must be 32px");
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    colors.add(img.getRGB(x, y));
                }
            }
            assertTrue(colors.size() >= 12, name + " lacks SF armor detail (colors=" + colors.size() + ")");
        }

        // Translucent hexagonal grid phase shield texture
        Path shieldPath = ENTITY_TEX.resolve("rift_phase_shield.png");
        assertTrue(Files.exists(shieldPath), "Missing rift_phase_shield.png");
        BufferedImage sh = ImageIO.read(shieldPath.toFile());
        assertEquals(32, sh.getWidth());
        assertEquals(32, sh.getHeight());
        int translucentInterior = 0;
        for (int y = 3; y < 29; y++) {
            for (int x = 3; x < 29; x++) {
                int alpha = (sh.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 20 && alpha < 220) {
                    translucentInterior++;
                }
            }
        }
        assertTrue(translucentInterior >= 400, "rift_phase_shield interior must be translucent hex grid");
    }

    @Test
    void overdriveTransitionShieldClassificationAndParticleClampingAreExact() throws IOException {
        // Boss Overdrive Phase 2 triggers strictly at HP <= 50% (once)
        assertFalse(DimensionalRaidLogic.shouldEnterOverdrive(120.0F, 220.0F, false));
        assertTrue(DimensionalRaidLogic.shouldEnterOverdrive(110.0F, 220.0F, false));
        assertTrue(DimensionalRaidLogic.shouldEnterOverdrive(40.0F, 220.0F, false));
        assertFalse(DimensionalRaidLogic.shouldEnterOverdrive(40.0F, 220.0F, true));
        assertFalse(DimensionalRaidLogic.shouldEnterOverdrive(0.0F, 220.0F, false));

        // Phase Juggernaut frontal directional shield vs piercing vs flank
        assertEquals(ShieldHitOutcome.FRONT_DEFLECT, DimensionalRaidLogic.evaluateShieldHit(0.95D, false));
        assertEquals(0.40F, ShieldHitOutcome.FRONT_DEFLECT.damageMultiplier(), 1e-5F);
        assertEquals(ShieldHitOutcome.FRONT_PIERCE, DimensionalRaidLogic.evaluateShieldHit(0.95D, true));
        assertEquals(0.75F, ShieldHitOutcome.FRONT_PIERCE.damageMultiplier(), 1e-5F);
        assertEquals(ShieldHitOutcome.FLANK_EXPOSED, DimensionalRaidLogic.evaluateShieldHit(0.10D, false));
        assertEquals(ShieldHitOutcome.FLANK_EXPOSED, DimensionalRaidLogic.evaluateShieldHit(-0.85D, true));
        assertEquals(1.00F, ShieldHitOutcome.FLANK_EXPOSED.damageMultiplier(), 1e-5F);

        // Client FX burst clamp & ParticleStatus.MINIMAL guard
        assertEquals(0, DimensionalRaidLogic.clampFxParticleCount(12, true));
        assertEquals(0, DimensionalRaidLogic.clampFxParticleCount(0, false));
        assertEquals(8, DimensionalRaidLogic.clampFxParticleCount(8, false));
        assertEquals(DimensionalRaidLogic.MAX_PARTICLES_PER_FX_BURST,
                DimensionalRaidLogic.clampFxParticleCount(99, false));

        // New Phase 119 3D World FX payload kinds & multi-part renderer hooks exist
        String payloadSrc = Files.readString(Path.of("src/main/java/com/industrialcore/network/WeaponBeamFxPayload.java"));
        assertTrue(payloadSrc.contains("KIND_RIFT_PORTAL_VORTEX = 8")
                && payloadSrc.contains("KIND_RIFT_SHIELD_HIT = 9")
                && payloadSrc.contains("KIND_BOSS_COLLAPSE_SHOCKWAVE = 10"));
        String rendererSrc = Files.readString(Path.of("src/main/java/com/industrialcore/client/RiftInvaderRenderer.java"));
        assertTrue(rendererSrc.contains("TEX_PHASE_SHIELD")
                && rendererSrc.contains("ACTION_BLINK_SLASH")
                && rendererSrc.contains("ACTION_SHIELD_FLASH")
                && rendererSrc.contains("ACTION_SKILL_FLARE"));
    }

    @Test
    void highPriorityRaidMilestonesNeverGetSilencedByCombatRateCap() {
        WorldJoinAudioGuardLogic guard = new WorldJoinAudioGuardLogic();
        guard.onWorldJoinOrDimensionChange();
        // During 60-tick login grace, even high-priority sounds stay muted cleanly
        assertFalse(guard.tryAllowSound("industrialcore:rift_raid_victory_fanfare", true, false));
        assertFalse(guard.tryAllowSound("industrialcore:sovereign_defeat_collapse", true, false));

        for (int i = 0; i < WorldJoinAudioGuardLogic.JOIN_GRACE_TICKS; i++) {
            guard.tick();
        }
        assertFalse(guard.isInJoinGrace());

        // Exhaust the normal 2-sounds/tick combat-chatter budget
        assertTrue(guard.tryAllowSound("industrialcore:rift_stalker_blink", true, false));
        assertTrue(guard.tryAllowSound("industrialcore:void_sentinel_cannon", true, false));
        assertFalse(guard.tryAllowSound("industrialcore:rift_shield_deflect", true, false));

        // High-priority boss defeat collapse & raid victory fanfare STILL play on the same tick!
        assertTrue(guard.tryAllowSound("industrialcore:sovereign_defeat_collapse", true, false));
        assertTrue(guard.tryAllowSound("industrialcore:rift_raid_victory_fanfare", true, false));
        // Same-ID 6-tick dedup still prevents duplicate double-firing
        assertFalse(guard.tryAllowSound("industrialcore:rift_raid_victory_fanfare", true, false));
    }

    @Test
    void allEightPhase119SoundsAreValidMono44100VorbisAndFullyRegistered() throws IOException {
        String modSoundsSrc = Files.readString(Path.of("src/main/java/com/industrialcore/registry/ModSounds.java"));
        JsonObject soundsJson = JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        JsonObject ja = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/ja_jp.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();

        for (String id : PHASE119_SOUNDS) {
            // 1. ModSounds, sounds.json, ja_jp & en_us registration
            assertTrue(modSoundsSrc.contains("register(\"" + id + "\")"), "Missing ModSounds entry: " + id);
            assertTrue(soundsJson.has(id), "Missing sounds.json entry: " + id);
            String subKey = "subtitles.industrialcore." + id;
            assertTrue(ja.has(subKey) && !ja.get(subKey).getAsString().isBlank(), "Missing ja subtitle: " + subKey);
            assertTrue(en.has(subKey) && !en.get(subKey).getAsString().isBlank(), "Missing en subtitle: " + subKey);

            // 2. Ogg Vorbis binary header check (OggS magic + \x01vorbis + 1ch mono + 44100Hz)
            Path ogg = SOUNDS_DIR.resolve(id + ".ogg");
            assertTrue(Files.exists(ogg), "Missing OGG file: " + ogg);
            byte[] bytes = Files.readAllBytes(ogg);
            assertTrue(bytes.length >= 2048, id + ".ogg suspiciously small: " + bytes.length);
            assertEquals('O', (char) bytes[0], id + " missing OggS magic");
            assertEquals('g', (char) bytes[1]);
            assertEquals('g', (char) bytes[2]);
            assertEquals('S', (char) bytes[3]);

            int vIdx = findVorbisIdHeader(bytes);
            assertTrue(vIdx >= 0, id + ".ogg missing Vorbis identification header");
            // Layout after "\x01vorbis" (7 bytes): version(4 bytes), channels(1 byte), sampleRate(4 bytes LE)
            int channels = bytes[vIdx + 11] & 0xFF;
            int sampleRate = ByteBuffer.wrap(bytes, vIdx + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertEquals(1, channels, id + ".ogg must be 1ch mono, got " + channels);
            assertEquals(44100, sampleRate, id + ".ogg must be 44100Hz, got " + sampleRate);
        }
    }

    private static int findVorbisIdHeader(byte[] data) {
        byte[] sig = {0x01, 'v', 'o', 'r', 'b', 'i', 's'};
        outer:
        for (int i = 0; i <= Math.min(data.length - 16, 256); i++) {
            for (int j = 0; j < sig.length; j++) {
                if (data[i + j] != sig[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
