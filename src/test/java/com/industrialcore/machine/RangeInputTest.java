package com.industrialcore.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the quarry range fields.
 *
 * <p>These cover the bug reported for Phase 5: editing the width and then moving to the depth
 * field used to reset the width because every client tick mirrored the server value into every
 * unfocused field. The tests below model that sequence without a client.</p>
 */
class RangeInputTest {

    @Test
    void sequentialEditsAreRetainedUntilApply() {
        RangeInput input = new RangeInput(3, 3, 3);

        input.setText(RangeInput.WIDTH, "12");
        // Moving to the depth field is just another setText; nothing may touch the width here.
        input.setText(RangeInput.DEPTH, "7");
        input.setText(RangeInput.HEIGHT, "9");

        assertEquals("12", input.text(RangeInput.WIDTH));
        assertEquals("7", input.text(RangeInput.DEPTH));
        assertEquals("9", input.text(RangeInput.HEIGHT));
        assertTrue(input.isDirty(), "three unsaved edits must count as dirty");
        // Applied values are untouched until apply().
        assertEquals(3, input.applied(RangeInput.WIDTH));
        assertEquals(3, input.applied(RangeInput.DEPTH));
        assertEquals(3, input.applied(RangeInput.HEIGHT));
    }

    @Test
    void serverValuesDoNotOverwriteAnEdit() {
        RangeInput input = new RangeInput(3, 3, 3);
        input.setText(RangeInput.WIDTH, "12");

        // This is what a menu data sync does every tick while the chest GUI is open.
        assertFalse(input.syncFromServer(5, 6, 7), "a sync while editing must be ignored");
        assertEquals("12", input.text(RangeInput.WIDTH));
        assertEquals(3, input.applied(RangeInput.WIDTH));

        // The other fields were not edited, so they may follow the server.
        assertEquals("3", input.text(RangeInput.DEPTH));
    }

    @Test
    void applySendsAllThreeValuesTogether() {
        RangeInput input = new RangeInput(3, 3, 3);
        input.setText(RangeInput.WIDTH, "20");
        input.setText(RangeInput.DEPTH, "15");
        input.setText(RangeInput.HEIGHT, "9");

        int[] values = input.apply();

        assertArrayEquals(new int[]{20, 15, 9}, values);
        assertFalse(input.isDirty());
        assertEquals("20", input.text(RangeInput.WIDTH));
        assertEquals(20, input.applied(RangeInput.WIDTH));
        assertEquals(15, input.applied(RangeInput.DEPTH));
        assertEquals(9, input.applied(RangeInput.HEIGHT));
    }

    @Test
    void invalidAndOutOfRangeValuesAreHandledSafely() {
        RangeInput input = new RangeInput(4, 5, 6);
        input.setText(RangeInput.WIDTH, "");
        input.setText(RangeInput.DEPTH, "abc");
        input.setText(RangeInput.HEIGHT, "999");

        int[] values = input.apply();

        assertEquals(4, values[RangeInput.WIDTH], "blank falls back to the applied width");
        assertEquals(5, values[RangeInput.DEPTH], "non-numeric falls back to the applied depth");
        assertEquals(RangeInput.MAX_VALUE, values[RangeInput.HEIGHT], "out of range is clamped");
        assertEquals("4", input.text(RangeInput.WIDTH), "the field is normalised to what was sent");
        assertEquals("5", input.text(RangeInput.DEPTH));
        assertEquals("128", input.text(RangeInput.HEIGHT));
    }

    @Test
    void zeroAndNegativeValuesAreClampedToTheMinimum() {
        RangeInput input = new RangeInput(3, 3, 3);
        input.setText(RangeInput.WIDTH, "0");
        input.setText(RangeInput.DEPTH, "-9");
        input.setText(RangeInput.HEIGHT, "1");

        assertArrayEquals(new int[]{1, 1, 1}, input.apply());
    }

    @Test
    void hugeInputDoesNotThrow() {
        RangeInput input = new RangeInput(3, 3, 3);
        input.setText(RangeInput.WIDTH, "999999999999999999999");
        assertEquals(3, input.apply()[RangeInput.WIDTH], "an unparsable number keeps the old value");
    }

    @Test
    void serverSyncUpdatesFieldsWhenNothingIsBeingEdited() {
        RangeInput input = new RangeInput(3, 3, 3);
        assertTrue(input.syncFromServer(8, 9, 10));
        assertEquals("8", input.text(RangeInput.WIDTH));
        assertEquals("9", input.text(RangeInput.DEPTH));
        assertEquals("10", input.text(RangeInput.HEIGHT));

        assertFalse(input.syncFromServer(8, 9, 10), "an unchanged sync is not a change");
        assertTrue(input.syncFromServer(8, 9, 11));
    }

    @Test
    void applyThenServerEchoDoesNotRewriteTheFields() {
        RangeInput input = new RangeInput(12, 7, 9);
        input.syncFromServer(12, 7, 9);
        input.setText(RangeInput.DEPTH, "20");
        assertArrayEquals(new int[]{12, 20, 9}, input.apply());

        // The server echoes the same values one tick later: nothing changes.
        assertFalse(input.syncFromServer(12, 20, 9));
        assertEquals("20", input.text(RangeInput.DEPTH));
    }

    @Test
    void parseClampsAndKeepsTheFallbackForGarbage() {
        assertEquals(5, RangeInput.parse(null, 5));
        assertEquals(5, RangeInput.parse("   ", 5));
        assertEquals(5, RangeInput.parse("x", 5));
        assertEquals(9, RangeInput.parse(" 9 ", 5));
        assertEquals(128, RangeInput.parse("500", 5));
        assertEquals(1, RangeInput.parse("0", 5));
    }

    @Test
    void invalidAxisIsRejected() {
        RangeInput input = new RangeInput(1, 1, 1);
        org.junit.jupiter.api.Assertions.assertThrows(IndexOutOfBoundsException.class,
                () -> input.text(3));
    }
}
