package com.industrialcore.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the Phase 63 machine footprint and its generated assets.
 *
 * <p>The layout is the single source of truth for the item placement, the ownership check the
 * controller performs and the model/blockstate indices, so it is pinned here: 18 unique cells,
 * the controller in the front-bottom-centre, a reversible world mapping for every facing, and a
 * model plus blockstate entry for every cell index.</p>
 */
class AdvancedTransportLayoutTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources", "assets", "industrialcore");

    @Test
    void theMachineIsThreeByTwoByThreeCells() {
        List<AdvancedTransportLayout.Cell> cells = AdvancedTransportLayout.cells();
        assertEquals(18, cells.size());
        assertEquals(18, AdvancedTransportLayout.CELL_COUNT);
        Set<Integer> indices = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        for (AdvancedTransportLayout.Cell cell : cells) {
            assertTrue(indices.add(cell.index()), "duplicate index " + cell.index());
            assertTrue(coordinates.add(cell.x() + "," + cell.y() + "," + cell.z()),
                    "duplicate coordinate");
            assertTrue(cell.x() >= -1 && cell.x() <= 1, "x outside the machine");
            assertTrue(cell.y() >= 0 && cell.y() <= 1, "y outside the machine");
            assertTrue(cell.z() >= 0 && cell.z() <= 2, "z outside the machine");
        }
        assertEquals(18, indices.size());
        for (int index = 0; index < 18; index++) {
            assertTrue(indices.contains(index), "index " + index + " is never used");
        }
    }

    @Test
    void theControllerIsTheFrontBottomCentreCell() {
        AdvancedTransportLayout.Cell controller = AdvancedTransportLayout.cell(
                AdvancedTransportLayout.indexOf(0, 0, 0));
        assertTrue(controller.controller());
        assertEquals(0, controller.x());
        assertEquals(0, controller.y());
        assertEquals(0, controller.z());
        assertEquals((0 * 2 + 0) * 3 + (0 + 1), controller.index(),
                "(z*2 + y)*3 + (x+1) for (0,0,0)");
        int controllers = 0;
        for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
            if (cell.controller()) {
                controllers++;
            }
        }
        assertEquals(1, controllers, "exactly one cell may be the controller");
    }

    @Test
    void everyCellMapsToAUniqueWorldPositionForEveryFacing() {
        int[][] facings = {{2, 0, 0}, {3, 10, -4}, {4, -7, 3}, {5, 30, 30}};
        for (int[] testCase : facings) {
            int facing = testCase[0];
            int cx = testCase[1];
            int cy = 64;
            int cz = testCase[2];
            Set<GridPos> positions = new HashSet<>();
            for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
                GridPos world = AdvancedTransportLayout.world(cx, cy, cz, facing, cell);
                assertTrue(positions.add(world), "two cells share " + world);
                assertEquals(ty(cell), world.y() - cy, "height of " + cell);
            }
            assertEquals(18, positions.size());
            // The controller cell stays exactly on the controller position.
            GridPos controller = AdvancedTransportLayout.world(cx, cy, cz, facing,
                    AdvancedTransportLayout.cell(AdvancedTransportLayout.indexOf(0, 0, 0)));
            assertEquals(new GridPos(cx, cy, cz), controller);
        }
    }

    private static int ty(AdvancedTransportLayout.Cell cell) {
        return cell.y();
    }

    @Test
    void theControllerCanAlwaysBeRecoveredFromAPart() {
        int[][] facings = {{2, 3, 8}, {3, -12, 41}, {4, 0, 0}, {5, 100, -100}};
        for (int[] testCase : facings) {
            int facing = testCase[0];
            int cx = testCase[1];
            int cy = 70;
            int cz = testCase[2];
            for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
                if (cell.controller()) {
                    continue;
                }
                GridPos part = AdvancedTransportLayout.world(cx, cy, cz, facing, cell);
                GridPos owner = AdvancedTransportLayout.controllerOf(part.x(), part.y(), part.z(),
                        facing, cell);
                assertEquals(new GridPos(cx, cy, cz), owner,
                        "part " + cell + " facing " + facing + " must resolve its controller");
            }
        }
    }

    /** Phase 64: the block state index is the only thing a part stores, so it must round trip. */
    @Test
    void everyStoredIndexRebuildsItsOwnCell() {
        for (int index = 0; index < AdvancedTransportLayout.CELL_COUNT; index++) {
            AdvancedTransportLayout.Cell cell = AdvancedTransportLayout.cell(index);
            assertEquals(index, AdvancedTransportLayout.indexOf(cell.x(), cell.y(), cell.z()),
                    "index " + index + " must map back to its own cell");
            assertEquals(index == AdvancedTransportLayout.indexOf(0, 0, 0), cell.controller(),
                    "only the controller index may be flagged as the controller");
        }
    }

    /** Phase 64: every one of the 17 parts must resolve to the controller, never to a sibling. */
    @Test
    void noPartEverResolvesToAnotherPart() {
        int[][] facings = {{2, 11, -6}, {3, -4, 17}, {4, 0, -1}, {5, 64, 64}};
        for (int[] testCase : facings) {
            int facing = testCase[0];
            int cx = testCase[1];
            int cy = 64;
            int cz = testCase[2];
            GridPos controllerPos = AdvancedTransportLayout.world(cx, cy, cz, facing,
                    AdvancedTransportLayout.cell(AdvancedTransportLayout.indexOf(0, 0, 0)));
            assertEquals(new GridPos(cx, cy, cz), controllerPos);
            Set<GridPos> resolved = new HashSet<>();
            int parts = 0;
            for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
                if (cell.controller()) {
                    continue;
                }
                parts++;
                GridPos part = AdvancedTransportLayout.world(cx, cy, cz, facing, cell);
                GridPos owner = AdvancedTransportLayout.controllerOf(part.x(), part.y(), part.z(),
                        facing, cell);
                assertEquals(controllerPos, owner,
                        "part " + cell.index() + " facing " + facing + " must resolve the controller");
                assertFalse(part.equals(controllerPos), "a part never sits on the controller cell");
                assertTrue(resolved.add(part), "the parts keep distinct positions");
            }
            assertEquals(17, parts, "17 parts around the controller");
        }
    }

    /** Phase 64: the indices are baked into 18 model files and 72 blockstate variants. */
    @Test
    void theIndexOrderStaysPinned() {
        assertEquals(0, AdvancedTransportLayout.indexOf(-1, 0, 0));
        assertEquals(2, AdvancedTransportLayout.indexOf(1, 0, 0));
        assertEquals(3, AdvancedTransportLayout.indexOf(-1, 1, 0));
        assertEquals(1, AdvancedTransportLayout.indexOf(0, 0, 0), "controller index");
        assertEquals(15, AdvancedTransportLayout.indexOf(-1, 1, 2));
        assertEquals(17, AdvancedTransportLayout.indexOf(1, 1, 2));
    }

    @Test
    void indicesOutsideTheMachineAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AdvancedTransportLayout.cell(18));
        assertThrows(IllegalArgumentException.class, () -> AdvancedTransportLayout.cell(-1));
        assertThrows(IllegalArgumentException.class, () -> AdvancedTransportLayout.indexOf(2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AdvancedTransportLayout.indexOf(0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> AdvancedTransportLayout.indexOf(0, 0, 3));
        assertFalse(AdvancedTransportLayout.isControllerIndex(0));
        assertTrue(AdvancedTransportLayout.isControllerIndex(
                AdvancedTransportLayout.indexOf(0, 0, 0)));
    }

    @Test
    void everyCellHasAModelAndABlockstateVariant() throws IOException {
        Path models = RESOURCES.resolve("models").resolve("block");
        for (int index = 0; index < AdvancedTransportLayout.CELL_COUNT; index++) {
            Path model = models.resolve("advanced_transporter_part_" + index + ".json");
            assertTrue(Files.exists(model), "missing part model for cell " + index);
            JsonObject json = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
            assertTrue(json.getAsJsonArray("elements").size() > 0,
                    "cell " + index + " has an empty model");
            for (String face : List.of("north", "south", "east", "west", "up", "down")) {
                assertTrue(json.getAsJsonObject("textures").size() > 0,
                        "cell " + index + " has no textures");
                break;
            }
        }
        Path driveModel = models.resolve("advanced_transporter_drive.json");
        assertTrue(Files.exists(driveModel), "missing controller model");

        JsonObject blockstate = JsonParser.parseString(Files.readString(RESOURCES
                .resolve("blockstates").resolve("advanced_transporter_part.json"))).getAsJsonObject();
        JsonObject variants = blockstate.getAsJsonObject("variants");
        assertEquals(AdvancedTransportLayout.CELL_COUNT * 4, variants.size(),
                "one variant per cell and facing");
        for (String facing : List.of("north", "east", "south", "west")) {
            for (int index = 0; index < AdvancedTransportLayout.CELL_COUNT; index++) {
                String key = "part=" + index + ",facing=" + facing;
                assertTrue(variants.has(key), "missing variant " + key);
            }
        }
        JsonObject driveState = JsonParser.parseString(Files.readString(RESOURCES
                .resolve("blockstates").resolve("advanced_transporter_drive.json"))).getAsJsonObject();
        assertEquals(4, driveState.getAsJsonObject("variants").size());
    }
}
