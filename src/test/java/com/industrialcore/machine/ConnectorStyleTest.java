package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the three candidate connector designs (Phase 49).
 *
 * <p>The connector is now the only thing that adapts a pipe or cable to its neighbour, so the
 * numbers decide how a joint looks against a machine, a chest or a wall of other pipes. These checks
 * keep every style inside the envelope the arm leaves for it: a small widening, a shallow crossing
 * of the block boundary and nothing that grows into a collar.</p>
 */
class ConnectorStyleTest {
    /** The arm ends at the boundary; a connector may cross it by at most this much. */
    private static final double MAX_PROTRUSION = 0.05D;
    /** A plate may widen the tube by at most this much per side. */
    private static final double MAX_WIDENING = 0.10D;

    @Test
    void everyStyleIsSmallAndInsideTheArm() {
        for (ConnectorStyle style : ConnectorStyle.values()) {
            assertTrue(style.plateExtra() > 0.0D && style.plateExtra() <= MAX_WIDENING,
                    style + " plate widens the tube too much");
            assertTrue(style.outerDepth() >= 0.0D && style.outerDepth() <= MAX_PROTRUSION,
                    style + " sticks too far into the neighbour");
            assertTrue(style.innerDepth() > 0.0D && style.innerDepth() <= MAX_PROTRUSION,
                    style + " plate is not inside the connector's own block");
            assertTrue(style.frameExtra() == 0.0D || style.frameExtra() > style.plateExtra(),
                    style + " accent frame must sit outside the plate");
            assertTrue(style.frameOuter() >= style.frameInner(),
                    style + " accent frame depth is inverted");
        }
    }

    @Test
    void theFlatGasketStaysFlush() {
        assertEquals(0.0D, ConnectorStyle.GASKET.outerDepth(), 1.0e-9D,
                "the gasket design must not cross the block boundary");
        assertEquals(0.0D, ConnectorStyle.GASKET.frameInner(), 1.0e-9D,
                "the gasket design has no raised frame");
    }

    @Test
    void theOtherTwoDesignsAreRecognisablyDifferent() {
        assertTrue(ConnectorStyle.COUPLING.outerDepth() > 0.0D,
                "the coupling should cross the boundary slightly");
        assertTrue(ConnectorStyle.FRAME.frameExtra() > ConnectorStyle.COUPLING.frameExtra(),
                "the frame design should have the widest accent");
        assertTrue(ConnectorStyle.FRAME.outerDepth() < ConnectorStyle.COUPLING.outerDepth(),
                "the frame design should be flatter than the coupling");
    }

    @Test
    void prefixAndNameBothResolve() {
        for (ConnectorStyle style : ConnectorStyle.values()) {
            assertSame(style, ConnectorStyle.parse(style.name()));
            assertSame(style, ConnectorStyle.parse(style.name().toLowerCase()));
            assertSame(style, ConnectorStyle.parse(style.name().substring(0, 1).toLowerCase()));
        }
        assertSame(ConnectorStyle.DEFAULT, ConnectorStyle.parse("nonsense"));
        assertSame(ConnectorStyle.DEFAULT, ConnectorStyle.parse(""));
    }

    @Test
    void theSystemPropertyOverridesTheDefaultForTheComparison() {
        String key = ConnectorStyle.PROPERTY;
        String previous = System.getProperty(key);
        try {
            for (ConnectorStyle style : ConnectorStyle.values()) {
                System.setProperty(key, style.name().toLowerCase());
                assertSame(style, ConnectorStyle.active());
            }
            System.clearProperty(key);
            assertSame(ConnectorStyle.DEFAULT, ConnectorStyle.active());
            assertNotNull(ConnectorStyle.DEFAULT);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
