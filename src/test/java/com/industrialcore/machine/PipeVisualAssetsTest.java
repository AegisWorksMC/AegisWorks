package com.industrialcore.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 22 data test: the wrench mode is visible on every pipe tier, without a block state for it.
 *
 * <p>Phase 22 first mirrored the six face modes into the pipe block state. Because a block state
 * is the product of all its properties, that turned 64 states per pipe into 262,144
 * (2^6 connections * 4^6 modes) and 1,048,576 states for the four tiers; the client bakes a model
 * for every state while starting, which stalled start-up. The modes are now drawn by
 * {@code client.ItemPipeRenderer} from the synced block entity data.</p>
 *
 * <p>These checks read the shipped JSON, so a regenerated blockstate that reintroduces a mode
 * property - the actual regression - fails the build.</p>
 */
class PipeVisualAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final List<String> PIPES = List.of(
            "item_pipe", "pipe_improved", "pipe_advanced", "pipe_ultimate");
    /** The only properties a pipe block state may use. Six booleans = 64 states. */
    private static final Set<String> CONNECTION_PROPERTIES = Set.of(
            "north", "east", "south", "west", "up", "down");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    /** Every property name a multipart blockstate selects on. */
    private static Set<String> referencedProperties(JsonObject blockstate) {
        Set<String> properties = new TreeSet<>();
        for (JsonElement element : blockstate.getAsJsonArray("multipart")) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject when = entry.getAsJsonObject("when");
            if (when == null) {
                continue;
            }
            for (String key : when.keySet()) {
                properties.add(key);
            }
        }
        return properties;
    }

    @Test
    void everyPipeTierUsesOnlyTheSixConnectionProperties() throws IOException {
        for (String pipe : PIPES) {
            JsonObject blockstate = json(ASSETS.resolve("blockstates/" + pipe + ".json"));
            assertEquals(new TreeSet<>(CONNECTION_PROPERTIES), referencedProperties(blockstate),
                    pipe + ": the block state must stay at 2^6 = 64 combinations; "
                            + "face modes belong to the renderer, not to the state space");
            assertFalse(blockstate.toString().contains("\"mode_"),
                    pipe + " still selects on a mode property");
        }
    }

    @Test
    void everyPipeTierStillDrawsTheCoreAndSixArms() throws IOException {
        for (String pipe : PIPES) {
            JsonObject blockstate = json(ASSETS.resolve("blockstates/" + pipe + ".json"));
            String text = blockstate.toString();
            // Tiers share the arm models and use their own core texture (item_pipe / pipe_core_*).
            assertTrue(text.contains("pipe_core"), pipe + " has no core model");
            for (String direction : CONNECTION_PROPERTIES) {
                assertTrue(text.contains("pipe_arm_" + direction),
                        pipe + " has no arm model for " + direction);
            }
        }
    }

    /** The 18 marker models and 3 textures were removed with the block state approach. */
    @Test
    void noDeadMarkerAssetsAreShipped() throws IOException {
        try (var models = Files.list(ASSETS.resolve("models/block"))) {
            assertFalse(models.anyMatch(path -> path.getFileName().toString().startsWith("pipe_marker_")),
                    "a marker model is still shipped but nothing references it any more");
        }
        try (var textures = Files.list(ASSETS.resolve("textures/block"))) {
            assertFalse(textures.anyMatch(path -> path.getFileName().toString().startsWith("pipe_marker_")),
                    "a marker texture is still shipped but nothing references it any more");
        }
    }

    /**
     * Each transport mode has exactly one id. The ids must stay in sync with the wrench tooltip and
     * with the saved byte codes of existing worlds.
     */
    @Test
    void everyTransportModeHasADistinctId() {
        assertEquals("none", PipeConnectionMode.NONE.id());
        assertEquals("normal", PipeConnectionMode.NORMAL.id());
        assertEquals("push", PipeConnectionMode.PUSH.id());
        assertEquals("pull", PipeConnectionMode.PULL.id());
    }
}
