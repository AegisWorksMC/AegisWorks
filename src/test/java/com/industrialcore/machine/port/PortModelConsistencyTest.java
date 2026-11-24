package com.industrialcore.machine.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the machine models free of pipe geometry (Phase 49).
 *
 * <p>Phase 45-48 carved a socket and a rim collar into every machine that had a port. That made the
 * machines look like they were built around pipes, which is the opposite of the current design: a
 * machine is finished on its own, and the pipe or cable carries whatever is needed to meet it. This
 * test is the guard rail. It reads every shipped machine model and fails if any element refers to
 * the old port texture, and it checks that the port tables still describe every machine (they are
 * now connection hints - where an arm should aim - not model geometry).</p>
 */
class PortModelConsistencyTest {
    private static final String MODEL_ROOT = "assets/industrialcore/models/block/";

    /** The machines that expose ports, in the same order as {@code MachinePortDefinitions}. */
    private static final String[] MACHINES = {
            "crusher", "assembler", "fast_crusher", "precision_crusher",
            "electric_furnace", "electrolyzer", "metal_press", "alloy_furnace", "quarry",
            "large_quarry", "crafter", "reconstructor", "energy_buffer", "energy_buffer_mk2",
            "energy_buffer_mk3", "item_distributor", "seed_cultivator", "matter_amplifier",
            "phase_reactor", "quantum_compressor", "ore_synthesizer", "combustion_generator",
            "thermal_generator", "nuclear_generator", "hydro_generator", "wind_generator",
            "solar_generator", "geothermal_generator",
            "pressure_recovery", "heat_recovery", "flywheel", "regen_generator",
            "quantum_transporter", "basic_item_transporter",
            "advanced_item_transporter", "quantum_transporter_array",
    };

    @Test
    void noMachineModelCarriesPortGeometry() {
        for (String machine : MACHINES) {
            JsonObject model = model(machine);
            JsonObject textures = model.has("textures")
                    ? model.getAsJsonObject("textures") : new JsonObject();
            assertFalse(textures.has("port"),
                    machine + " still declares the removed port texture");
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                for (String side : new String[]{"north", "south", "east", "west", "up", "down"}) {
                    if (!faces.has(side)) {
                        continue;
                    }
                    String texture = faces.getAsJsonObject(side).get("texture").getAsString();
                    assertFalse(texture.equals("#port") || texture.contains("machine_port"),
                            machine + " still draws a socket/collar on " + side);
                }
            }
        }
    }

    @Test
    void everyMachineStillHasAPortTable() {
        for (String machine : MACHINES) {
            PortLayout layout = PortTables.layout(machine);
            assertNotNull(layout, "no port table for " + machine);
            assertFalse(layout.isEmpty(), machine + " has an empty port table");
            assertTrue(layout.ports().size() <= 4, machine + " has too many ports");
        }
    }

    @Test
    void portCentresStayInsideTheirFace() {
        for (String machine : MACHINES) {
            PortLayout layout = PortTables.layout(machine);
            for (PortSpec spec : layout.ports()) {
                // One axis is the face plane (0 or 16); the other two must be inside the face.
                for (double coordinate : spec.centerPx()) {
                    assertTrue(coordinate >= 0.0D && coordinate <= 16.0D,
                            machine + " port centre leaves the block: " + coordinate);
                }
                assertTrue(spec.radius() > 0.0D && spec.radius() < 8.0D,
                        machine + " port socket is not a sane size");
            }
        }
    }

    private static JsonObject model(String name) {
        String path = MODEL_ROOT + name + ".json";
        InputStream stream = PortModelConsistencyTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing model " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            fail("cannot read " + path + ": " + exception.getMessage());
            return null;
        }
    }
}
