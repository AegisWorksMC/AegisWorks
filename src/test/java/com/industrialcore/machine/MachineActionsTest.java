package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the action ids shared by the machine GUI, the network handler and the block entities.
 *
 * <p>Regression guard: the ids used to be written out as literals in two places. The auto crafter
 * numbered them differently from the processing machines, so its "cycle mode" button flipped the
 * item filter instead. One shared class removes the duplication; this test keeps the numbering
 * itself stable so an old client cannot silently hit a different setting.</p>
 */
class MachineActionsTest {
    @Test
    void idsAreStable() {
        assertEquals(1, MachineActions.REGISTER_GRID);
        assertEquals(2, MachineActions.FACE_MODE);
        assertEquals(3, MachineActions.FACE_SLOT);
        assertEquals(4, MachineActions.FACE_FILTER);
        assertEquals(5, MachineActions.FACE_ADD_ITEM);
        assertEquals(6, MachineActions.FACE_CLEAR);
        assertEquals(MachineActions.FACE_CLEAR, MachineActions.MAX);
    }

    @Test
    void machineWideAndFaceActionsDoNotOverlap() {
        for (int action = 0; action <= MachineActions.MAX; action++) {
            boolean wide = MachineActions.isMachineWide(action);
            boolean face = MachineActions.isFaceAction(action);
            assertFalse(wide && face, "action " + action + " is both kinds");
            assertEquals(wide || face, MachineActions.isValid(action),
                    "action " + action + " validity disagrees with its kind");
        }
        // Id 0 was the removed even-split toggle and must no longer reach a machine.
        assertFalse(MachineActions.isValid(0));
        assertTrue(MachineActions.isValid(MachineActions.REGISTER_GRID));
        assertFalse(MachineActions.isValid(-1));
        assertFalse(MachineActions.isValid(MachineActions.MAX + 1));
    }

    /** The GUI sends a face index or -1; the network handler derives this rule from the ids. */
    @Test
    void sideRequirementMatchesTheActionKind() {
        assertTrue(MachineActions.isMachineWide(MachineActions.REGISTER_GRID));
        for (int action = MachineActions.FACE_MODE; action <= MachineActions.MAX; action++) {
            assertTrue(MachineActions.isFaceAction(action));
            assertFalse(MachineActions.isMachineWide(action));
        }
    }
}
