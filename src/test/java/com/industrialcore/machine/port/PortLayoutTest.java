package com.industrialcore.machine.port;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rotation, lookup and validation table for machine connection ports.
 *
 * <p>These run without a game: the whole point of {@link PortSpec}/{@link PortLayout} is that the
 * geometry the blockstate will rotate is decided in plain Java, so every facing and every face can
 * be asserted instead of being eyeballed in a world.</p>
 */
class PortLayoutTest {

    private static final PortLayout THREE_SIDED = new PortLayout(List.of(
            new PortSpec(PortSide.SOUTH, 0.0, 0.0, 3.5, 1.0, PortKind.ANY),
            new PortSpec(PortSide.WEST, 0.0, 0.0, 3.5, 1.0, PortKind.ANY),
            new PortSpec(PortSide.EAST, 0.0, 0.0, 3.5, 1.0, PortKind.ANY)));

    @Test
    void centreOfEveryFaceIsTheFaceCentre() {
        for (PortSide side : PortSide.values()) {
            assertArrayEquals(expectedCenter(side), PortSpec.centred(side, PortKind.ANY).centerPx(),
                    0.0, "centre of " + side);
        }
    }

    private static double[] expectedCenter(PortSide side) {
        return switch (side) {
            case NORTH -> new double[]{8.0, 8.0, 0.0};
            case SOUTH -> new double[]{8.0, 8.0, 16.0};
            case WEST -> new double[]{0.0, 8.0, 8.0};
            case EAST -> new double[]{16.0, 8.0, 8.0};
            case UP -> new double[]{8.0, 16.0, 8.0};
            case DOWN -> new double[]{8.0, 0.0, 8.0};
        };
    }

    @Test
    void offsetsStayInsideTheFace() {
        PortSpec right = new PortSpec(PortSide.NORTH, 3.0, 2.0, 2.0, 0.0, PortKind.ANY);
        double[] center = right.centerPx();
        // Looking at the north face from outside, +u is to the east and +v is up.
        assertEquals(11.0, center[0], 1e-9);
        assertEquals(10.0, center[1], 1e-9);

        PortSpec south = new PortSpec(PortSide.SOUTH, 3.0, 2.0, 2.0, 0.0, PortKind.ANY);
        double[] southCenter = south.centerPx();
        // The same "right when seen from outside" mirrors on the south face.
        assertEquals(5.0, southCenter[0], 1e-9);
        assertEquals(10.0, southCenter[1], 1e-9);
    }

    @Test
    void everyFaceRotatesWithTheBlock() {
        assertEquals(PortSide.EAST, PortSide.NORTH.rotate(PortFacing.EAST));
        assertEquals(PortSide.SOUTH, PortSide.NORTH.rotate(PortFacing.SOUTH));
        assertEquals(PortSide.WEST, PortSide.NORTH.rotate(PortFacing.WEST));
        assertEquals(PortSide.NORTH, PortSide.EAST.rotate(PortFacing.WEST));
        // Vertical faces never move.
        for (PortFacing facing : PortFacing.values()) {
            assertEquals(PortSide.UP, PortSide.UP.rotate(facing));
            assertEquals(PortSide.DOWN, PortSide.DOWN.rotate(facing));
        }
    }

    @Test
    void fourFacingsReturnToTheOriginalSide() {
        for (PortSide side : PortSide.values()) {
            PortSide rotated = side;
            for (int i = 0; i < 4; i++) {
                rotated = rotated.rotate(PortFacing.EAST);
            }
            assertEquals(side, rotated, "a full turn of " + side);
        }
    }

    @Test
    void centreRotatesWithTheSameTransformAsTheCollisionShape() {
        PortSpec back = PortSpec.centred(PortSide.SOUTH, PortKind.ANY);
        // South face centre is (0.5, 0.5, 1.0); east-facing rotates it onto the west face.
        assertArrayEquals(new double[]{0.0, 0.5, 0.5}, back.centerRotated(PortFacing.EAST), 1e-9);
        // South-facing maps it to the north face.
        assertArrayEquals(new double[]{0.5, 0.5, 0.0}, back.centerRotated(PortFacing.SOUTH), 1e-9);
        // West-facing maps it to the east face.
        assertArrayEquals(new double[]{1.0, 0.5, 0.5}, back.centerRotated(PortFacing.WEST), 1e-9);
    }

    @Test
    void lookupFollowsThePlacedFacing() {
        // North-facing: back = south, sides = west/east, front = north has no port.
        assertEquals(PortSide.SOUTH, THREE_SIDED.portOn(PortFacing.NORTH, PortSide.SOUTH).side());
        assertEquals(PortSide.WEST, THREE_SIDED.portOn(PortFacing.NORTH, PortSide.WEST).side());
        assertNull(THREE_SIDED.portOn(PortFacing.NORTH, PortSide.NORTH));
        assertNull(THREE_SIDED.portOn(PortFacing.NORTH, PortSide.UP));

        // Rotated 90 degrees clockwise: the back port is now on the west face, the west port on
        // the north face and the east port on the south face.
        assertEquals(PortSide.SOUTH, THREE_SIDED.portOn(PortFacing.EAST, PortSide.WEST).side());
        assertEquals(PortSide.WEST, THREE_SIDED.portOn(PortFacing.EAST, PortSide.NORTH).side());
        assertEquals(PortSide.EAST, THREE_SIDED.portOn(PortFacing.EAST, PortSide.SOUTH).side());
        assertNull(THREE_SIDED.portOn(PortFacing.EAST, PortSide.EAST));

        // 180 degrees: the back port faces north and the sides swap.
        assertEquals(PortSide.SOUTH, THREE_SIDED.portOn(PortFacing.SOUTH, PortSide.NORTH).side());
        assertEquals(PortSide.WEST, THREE_SIDED.portOn(PortFacing.SOUTH, PortSide.EAST).side());
        assertEquals(PortSide.EAST, THREE_SIDED.portOn(PortFacing.SOUTH, PortSide.WEST).side());
    }

    @Test
    void everyFacingHasExactlyTheDeclaredPorts() {
        for (PortFacing facing : PortFacing.values()) {
            int found = 0;
            for (PortSide side : PortSide.values()) {
                if (THREE_SIDED.portOn(facing, side) != null) {
                    found++;
                }
            }
            assertEquals(3, found, "ports visible when facing " + facing);
            assertEquals(3, THREE_SIDED.rotated(facing).size());
        }
    }

    @Test
    void centreOnReturnsNullForAFaceWithoutAPort() {
        assertNull(THREE_SIDED.centerOn(PortFacing.NORTH, PortSide.NORTH));
        double[] center = THREE_SIDED.centerOn(PortFacing.EAST, PortSide.WEST);
        assertArrayEquals(new double[]{0.0, 0.5, 0.5}, center, 1e-9);
    }

    @Test
    void duplicatePortOnOneFaceIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PortLayout(List.of(
                        PortSpec.centred(PortSide.SOUTH, PortKind.ANY),
                        PortSpec.centred(PortSide.SOUTH, PortKind.POWER))));
        assertTrue(error.getMessage().contains("duplicate port"));
    }

    @Test
    void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 0, 0, 0.0, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 0, 0, -1.0, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 0, 0, 7.5, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 6.0, 0, 3.0, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 0, 0, 3.5, 3.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, Double.NaN, 0, 3.5, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(null, 0, 0, 3.5, 1.0, PortKind.ANY));
        assertThrows(IllegalArgumentException.class,
                () -> new PortSpec(PortSide.NORTH, 0, 0, 3.5, 1.0, null));
    }

    @Test
    void anEmptyLayoutHasNoPorts() {
        assertTrue(PortLayout.empty().isEmpty());
        for (PortFacing facing : PortFacing.values()) {
            for (PortSide side : PortSide.values()) {
                assertNull(PortLayout.empty().portOn(facing, side));
            }
        }
    }

    @Test
    void kindsAcceptOnlyTheirOwnFamily() {
        assertTrue(PortKind.ANY.accepts(PortKind.POWER));
        assertTrue(PortKind.ANY.accepts(PortKind.ITEM));
        assertTrue(PortKind.ANY.accepts(PortKind.FLUID));
        assertTrue(PortKind.ANY.accepts(PortKind.ANY));

        assertTrue(PortKind.POWER.accepts(PortKind.POWER));
        assertFalse(PortKind.POWER.accepts(PortKind.ITEM));
        assertFalse(PortKind.POWER.accepts(PortKind.FLUID));

        assertTrue(PortKind.FLUID.accepts(PortKind.FLUID));
        assertFalse(PortKind.FLUID.accepts(PortKind.POWER));

        assertTrue(PortKind.ITEM.accepts(PortKind.ITEM));
        assertFalse(PortKind.ITEM.accepts(PortKind.FLUID));
        // A connector that does not care still fits a specific port.
        assertTrue(PortKind.ITEM.accepts(PortKind.ANY));
    }
}
