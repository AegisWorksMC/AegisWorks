package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the auto crafter GUI geometry and Phase 83 layout/ghost invariants.
 *
 * <p>Checks not only the 16x16 inner slot cells, but also the drawn slot rims (20x20), the cyan
 * output ring (24x24), the neon output bloom (30x30), the 3x3 input group frame, and every label's
 * reserved bounding box, so a label can never touch or overlap a slot frame again.</p>
 */
class CrafterLayoutTest {

    @Test
    void crafterLayoutHasNoOverlapsIncludingDrawnFramesAndLabels() {
        List<Rect> rects = new ArrayList<>();
        rects.add(CrafterLayout.ENERGY_BAR);
        rects.add(CrafterLayout.RECIPE_BUTTON);
        rects.add(CrafterLayout.REGISTER_BUTTON);
        rects.add(CrafterLayout.INPUT_GROUP);
        rects.add(CrafterLayout.PROGRESS_BAR);
        rects.add(CrafterLayout.outputGlowBounds());
        rects.add(CrafterLayout.slotWellBounds(CrafterLayout.UPGRADE_SLOT));
        rects.add(CrafterLayout.slotWellBounds(CrafterLayout.BATTERY_SLOT));
        addPlayerSlots(rects);

        // Text bands (x, y, reserved width, vanilla 9 px line height).
        rects.add(new Rect(GuiMetrics.SIDE, CrafterLayout.ENERGY_TEXT_Y, GuiMetrics.CONTENT, CrafterLayout.LABEL_HEIGHT));
        rects.add(new Rect(GuiMetrics.SIDE, CrafterLayout.RECIPE_TEXT_Y, GuiMetrics.CONTENT, CrafterLayout.LABEL_HEIGHT));
        rects.add(CrafterLayout.progressTextBounds());
        rects.add(CrafterLayout.outputLabelBounds());
        rects.add(CrafterLayout.upgradeLabelBounds());
        rects.add(CrafterLayout.batteryLabelBounds());

        assertNoOverlaps(rects);
        assertInsidePanel(rects);
    }

    @Test
    void outputAndMotorLabelsStayClearOfSlotsFramesAndGlow() {
        Rect outputLabel = CrafterLayout.outputLabelBounds();
        Rect outputWell = CrafterLayout.outputWellBounds();
        Rect outputGlow = CrafterLayout.outputGlowBounds();
        assertFalse(outputLabel.intersects(CrafterLayout.OUTPUT_SLOT),
                "Output label must not overlap the output slot");
        assertFalse(outputLabel.intersects(outputWell),
                "Output label must not overlap the 24x24 cyan output ring");
        assertFalse(outputLabel.intersects(outputGlow),
                "Output label must not overlap the output neon bloom");
        assertTrue(outputWell.y() - outputLabel.bottom() >= 6,
                "Output label needs >= 6 px breathing room above the cyan ring, got "
                        + (outputWell.y() - outputLabel.bottom()));

        Rect upgradeLabel = CrafterLayout.upgradeLabelBounds();
        Rect upgradeWell = CrafterLayout.slotWellBounds(CrafterLayout.UPGRADE_SLOT);
        assertFalse(upgradeLabel.intersects(CrafterLayout.UPGRADE_SLOT),
                "Motor label must not overlap the motor slot");
        assertFalse(upgradeLabel.intersects(upgradeWell),
                "Motor label must not overlap the 20x20 motor slot bezel");
        assertFalse(upgradeLabel.intersects(outputGlow),
                "Motor label must not touch the output glow above it");
        assertTrue(upgradeWell.y() - upgradeLabel.bottom() >= 4,
                "Motor label needs >= 4 px breathing room above the motor bezel, got "
                        + (upgradeWell.y() - upgradeLabel.bottom()));

        // Shared vertical axis aligns "Output", output well, "Motor", and motor slot cleanly.
        assertEquals(CrafterLayout.RIGHT_CENTER_X,
                CrafterLayout.OUTPUT_SLOT.x() + CrafterLayout.OUTPUT_SLOT.width() / 2);
        assertEquals(CrafterLayout.RIGHT_CENTER_X,
                CrafterLayout.UPGRADE_SLOT.x() + CrafterLayout.UPGRADE_SLOT.width() / 2);
        assertEquals(CrafterLayout.RIGHT_CENTER_X,
                outputLabel.x() + outputLabel.width() / 2);
        assertEquals(CrafterLayout.RIGHT_CENTER_X,
                upgradeLabel.x() + upgradeLabel.width() / 2);
    }

    @Test
    void leftToRightFlowFollowsGoldenRatioAndSymmetricMargins() {
        // Golden-ratio split on the two action buttons (104 : 64 = 1.625 ~ 1.618).
        double buttonRatio = (double) CrafterLayout.RECIPE_BUTTON.width()
                / CrafterLayout.REGISTER_BUTTON.width();
        assertTrue(buttonRatio >= 1.55D && buttonRatio <= 1.70D,
                "Button split should follow the golden ratio (~1.618), got " + buttonRatio);
        assertEquals(GuiMetrics.contentRight(), CrafterLayout.REGISTER_BUTTON.right(),
                "Register button right edge must align with contentRight");

        // Left -> Center -> Right crafting bay ordering and symmetry.
        Rect group = CrafterLayout.INPUT_GROUP;
        Rect progress = CrafterLayout.PROGRESS_BAR;
        Rect outWell = CrafterLayout.outputWellBounds();
        assertTrue(group.right() < progress.x(), "3x3 input group must stay left of the progress conduit");
        assertTrue(progress.right() < outWell.x(), "progress conduit must stay left of the output well");
        assertEquals(progress.x() - group.right(), outWell.x() - progress.right(),
                "progress conduit should sit symmetrically between the input group and output well");

        // Row 1 of the 3x3 grid, the progress conduit, and the output slot share one horizontal axis.
        Rect midRowSlot = CrafterLayout.inputSlot(3);
        int midRowCenterY = midRowSlot.y() + midRowSlot.height() / 2;
        int progressCenterY = progress.y() + progress.height() / 2;
        int outputCenterY = CrafterLayout.OUTPUT_SLOT.y() + CrafterLayout.OUTPUT_SLOT.height() / 2;
        assertEquals(midRowCenterY, progressCenterY, "progress bar must align with the middle grid row");
        assertEquals(midRowCenterY, outputCenterY, "output slot must align with the middle grid row");

        // Exit chevrons of MachineTheme.outputSlot (x + 23..x + 29) must stay inside contentRight.
        assertTrue(CrafterLayout.OUTPUT_SLOT.x() + 30 <= GuiMetrics.contentRight(),
                "output chevrons must stay inside the content area");
    }

    @Test
    void theBodyFollowsTheSharedGrid() {
        assertEquals(GuiMetrics.WIDTH, CrafterLayout.WIDTH);
        assertEquals(GuiMetrics.height(CrafterLayout.BODY_BOTTOM), CrafterLayout.HEIGHT);
        assertEquals(GuiMetrics.BODY_TOP, CrafterLayout.ENERGY_BAR.y());
        assertTrue(CrafterLayout.slotWellBounds(CrafterLayout.UPGRADE_SLOT).bottom() <= CrafterLayout.BODY_BOTTOM,
                "the motor slot bezel must fit inside the body");
        // The player inventory is fixed to the panel bottom, not to the body height.
        assertEquals(GuiMetrics.playerInvY(CrafterLayout.BODY_BOTTOM),
                CrafterLayout.playerSlot(0).y());
        assertEquals(GuiMetrics.hotbarY(CrafterLayout.BODY_BOTTOM),
                CrafterLayout.playerSlot(27).y());
    }

    @Test
    void inputSlotsAreNineDistinctCellsInsideInputGroupAndSeparateFromOutput() {
        List<Rect> inputs = new ArrayList<>();
        Rect group = CrafterLayout.INPUT_GROUP;
        for (int index = 0; index < 9; index++) {
            Rect slot = CrafterLayout.inputSlot(index);
            assertEquals(16, slot.width());
            assertEquals(16, slot.height());
            assertTrue(slot.x() >= group.x() && slot.right() <= group.right()
                            && slot.y() >= group.y() && slot.bottom() <= group.bottom(),
                    "input slot " + index + " must sit inside INPUT_GROUP");
            for (Rect other : inputs) {
                assertFalse(slot.intersects(other), "input slots overlap: " + slot + " and " + other);
            }
            inputs.add(slot);
        }
        assertEquals(9, inputs.size());
        assertFalse(CrafterLayout.OUTPUT_SLOT.intersects(CrafterLayout.inputSlot(2)),
                "the output slot sits on an input slot");

        // Phase 87: Battery slot sits cleanly below the 3x3 input group on LEFT_CENTER_X.
        Rect batteryWell = CrafterLayout.slotWellBounds(CrafterLayout.BATTERY_SLOT);
        Rect batteryLabel = CrafterLayout.batteryLabelBounds();
        assertFalse(batteryWell.intersects(CrafterLayout.INPUT_GROUP));
        assertFalse(batteryLabel.intersects(CrafterLayout.INPUT_GROUP));
        assertFalse(batteryLabel.intersects(batteryWell));
        assertFalse(batteryWell.intersects(CrafterLayout.slotWellBounds(CrafterLayout.UPGRADE_SLOT)));
        assertTrue(batteryWell.y() - batteryLabel.bottom() >= 4,
                "Battery label needs >= 4 px breathing room above the battery bezel");
        assertEquals(CrafterLayout.LEFT_CENTER_X,
                CrafterLayout.BATTERY_SLOT.x() + CrafterLayout.BATTERY_SLOT.width() / 2);
        assertEquals(CrafterLayout.LEFT_CENTER_X,
                batteryLabel.x() + batteryLabel.width() / 2);
    }

    @Test
    void ghostRenderingIsStrictlySuppressedWheneverARealItemIsPresent() {
        assertFalse(CrafterLayout.shouldRenderGhost(true, true),
                "Real item present: ghost must be suppressed to prevent double drawing");
        assertFalse(CrafterLayout.shouldRenderGhost(true, false));
        assertFalse(CrafterLayout.shouldRenderGhost(false, false));
        assertTrue(CrafterLayout.shouldRenderGhost(false, true),
                "Empty slot with a guide/preview stack should show the dimmed ghost");
    }

    @Test
    void playerSlotsFitThePanel() {
        List<Rect> rects = new ArrayList<>();
        addPlayerSlots(rects);
        assertEquals(36, rects.size());
        assertNoOverlaps(rects);
        assertInsidePanel(rects);
    }

    private static void addPlayerSlots(List<Rect> rects) {
        for (int index = 0; index < 36; index++) {
            rects.add(CrafterLayout.playerSlot(index));
        }
    }

    private static void assertNoOverlaps(List<Rect> rects) {
        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                assertFalse(rects.get(first).intersects(rects.get(second)),
                        "GUI elements overlap: " + rects.get(first) + " and " + rects.get(second));
            }
        }
    }

    private static void assertInsidePanel(List<Rect> rects) {
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0, "element starts outside the panel: " + rect);
            assertTrue(rect.right() <= CrafterLayout.WIDTH, "element exceeds the panel width: " + rect);
            assertTrue(rect.bottom() <= CrafterLayout.HEIGHT, "element exceeds the panel height: " + rect);
        }
    }
}
