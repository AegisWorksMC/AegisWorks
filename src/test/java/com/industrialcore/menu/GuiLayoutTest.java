package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the GUI geometry.
 *
 * <p>The actual drawing cannot be tested headless, but the numbers can: these tests fail if a
 * field, button, tab or slot overlaps another element of the same tab, or leaves the panel. That is
 * the class of bug reported for Phase 5 (range fields under the upgrade slots, loot inventory
 * under the progress text).</p>
 *
 * <p>What is still NOT covered: text widths, font rendering, and how the layout looks in a small
 * window. Those need an in-game check.</p>
 */
class GuiLayoutTest {

    @Test
    void quarrySettingsTabHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(QuarryLayout.SETTINGS_TAB);
        rects.add(QuarryLayout.STATUS_TAB);
        rects.add(QuarryLayout.START_BUTTON);
        rects.add(QuarryLayout.PREVIEW_BUTTON);
        rects.add(QuarryLayout.ENERGY_BAR);
        rects.add(QuarryLayout.WIDTH_FIELD);
        rects.add(QuarryLayout.DEPTH_FIELD);
        rects.add(QuarryLayout.HEIGHT_FIELD);
        rects.add(QuarryLayout.APPLY_BUTTON);
        rects.add(QuarryLayout.MOTOR_SLOT);
        rects.add(QuarryLayout.BATTERY_SLOT);
        addPlayerSlots(rects);
        // Text bands: FE read-out, the three field labels and the two short slot labels.
        rects.add(new Rect(GuiMetrics.SIDE, QuarryLayout.ENERGY_TEXT_Y, 130, 9));
        rects.add(new Rect(QuarryLayout.WIDTH_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, 40, 9));
        rects.add(new Rect(QuarryLayout.DEPTH_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, 40, 9));
        rects.add(new Rect(QuarryLayout.HEIGHT_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, 40, 9));
        rects.add(new Rect(QuarryLayout.MOTOR_LABEL_X, QuarryLayout.SLOT_LABEL_Y, 40, 9));
        rects.add(new Rect(QuarryLayout.BATTERY_LABEL_X, QuarryLayout.SLOT_LABEL_Y, 44, 9));

        assertNoOverlaps(rects, "quarry settings tab");
        assertInsidePanel(rects, QuarryLayout.WIDTH, QuarryLayout.HEIGHT, "quarry settings tab");
    }

    @Test
    void quarryStatusTabHasNoOverlaps() {
        List<Rect> rects = new ArrayList<>();
        rects.add(QuarryLayout.SETTINGS_TAB);
        rects.add(QuarryLayout.STATUS_TAB);
        rects.add(QuarryLayout.START_BUTTON);
        rects.add(QuarryLayout.PREVIEW_BUTTON);
        rects.add(QuarryLayout.PROGRESS_BAR);
        for (int index = 0; index < 18; index++) {
            Rect slot = QuarryLayout.outputSlot(index, QuarryLayout.TAB_STATUS);
            assertNotNull(slot, "output slot " + index + " has no position");
            rects.add(slot);
        }
        addPlayerSlots(rects);
        // Text bands: progress, the two summary lines, the loot header and the paused hint.
        rects.add(new Rect(GuiMetrics.SIDE, QuarryLayout.PROGRESS_TEXT_Y, 90, 9));
        rects.add(new Rect(GuiMetrics.SIDE, QuarryLayout.STATS_TEXT_Y, 150, 9));
        rects.add(new Rect(GuiMetrics.SIDE, QuarryLayout.LOOT_TITLE_Y, 90, 9));
        rects.add(new Rect(GuiMetrics.SIDE, QuarryLayout.PAUSED_HINT_Y, GuiMetrics.CONTENT, 9));

        assertNoOverlaps(rects, "quarry status tab");
        assertInsidePanel(rects, QuarryLayout.WIDTH, QuarryLayout.HEIGHT, "quarry status tab");
    }

    @Test
    void quarryTabsStayClearOfTheHeaderAndTheInventory() {
        // The tabs sit below the shared status strip, so they can never collide with the title.
        assertTrue(QuarryLayout.SETTINGS_TAB.y() >= GuiMetrics.STATUS_TOP + GuiMetrics.STATUS_HEIGHT,
                "the tabs must start below the status strip");
        assertTrue(QuarryLayout.SETTINGS_TAB.y() >= GuiMetrics.HEADER_HEIGHT,
                "the tabs must start below the header band");
        assertTrue(QuarryLayout.SETTINGS_TAB.bottom() <= QuarryLayout.START_BUTTON.y(),
                "the run/preview buttons must stay below the tabs");
        assertTrue(QuarryLayout.SETTINGS_TAB.right() <= QuarryLayout.STATUS_TAB.x()
                        || QuarryLayout.STATUS_TAB.right() <= QuarryLayout.SETTINGS_TAB.x(),
                "the two tabs must not overlap each other");
        // Both tabs keep the inventory at the panel bottom, so switching never moves the hand.
        for (int tab = 0; tab < 2; tab++) {
            assertEquals(GuiMetrics.playerInvY(QuarryLayout.BODY_BOTTOM),
                    QuarryLayout.playerSlot(0).y(),
                    "tab " + tab + " moved the player inventory");
        }
        assertTrue(QuarryLayout.BODY_BOTTOM <= GuiMetrics.playerInvY(QuarryLayout.BODY_BOTTOM));
    }

    @Test
    void inactiveTabSlotsAreHidden() {
        for (int index = 0; index < 18; index++) {
            assertNull(QuarryLayout.outputSlot(index, QuarryLayout.TAB_SETTINGS),
                    "the loot inventory must not be placed over the settings tab");
        }
        assertNull(QuarryLayout.upgradeSlot(0, QuarryLayout.TAB_STATUS));
        assertNull(QuarryLayout.upgradeSlot(1, QuarryLayout.TAB_STATUS));
        assertNull(QuarryLayout.outputSlot(-1, QuarryLayout.TAB_STATUS));
        assertNull(QuarryLayout.outputSlot(18, QuarryLayout.TAB_STATUS));
    }

    @Test
    void upgradeSlotsAreSeparatedFromTheRangeFields() {
        assertFalse(QuarryLayout.MOTOR_SLOT.intersects(QuarryLayout.WIDTH_FIELD));
        assertFalse(QuarryLayout.MOTOR_SLOT.intersects(QuarryLayout.DEPTH_FIELD));
        assertFalse(QuarryLayout.MOTOR_SLOT.intersects(QuarryLayout.HEIGHT_FIELD));
        assertFalse(QuarryLayout.MOTOR_SLOT.intersects(QuarryLayout.APPLY_BUTTON));
        assertFalse(QuarryLayout.BATTERY_SLOT.intersects(QuarryLayout.WIDTH_FIELD));
        assertFalse(QuarryLayout.BATTERY_SLOT.intersects(QuarryLayout.DEPTH_FIELD));
        assertFalse(QuarryLayout.BATTERY_SLOT.intersects(QuarryLayout.HEIGHT_FIELD));
        assertFalse(QuarryLayout.BATTERY_SLOT.intersects(QuarryLayout.APPLY_BUTTON));
        assertFalse(QuarryLayout.MOTOR_SLOT.intersects(QuarryLayout.BATTERY_SLOT));
    }

    @Test
    void tabsAndButtonsDoNotCoverEachOther() {
        assertFalse(QuarryLayout.SETTINGS_TAB.intersects(QuarryLayout.START_BUTTON));
        assertFalse(QuarryLayout.SETTINGS_TAB.intersects(QuarryLayout.PREVIEW_BUTTON));
        assertFalse(QuarryLayout.STATUS_TAB.intersects(QuarryLayout.PREVIEW_BUTTON));
        assertFalse(QuarryLayout.START_BUTTON.intersects(QuarryLayout.PREVIEW_BUTTON));
    }

    @Test
    void processingMachineElementsAreSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(ProcessingLayout.ENERGY_BAR);
        rects.add(ProcessingLayout.INPUT_SLOT);
        rects.add(ProcessingLayout.PROGRESS_BAR);
        rects.add(ProcessingLayout.OUTPUT_SLOT);
        rects.add(ProcessingLayout.MOTOR_SLOT);
        rects.add(ProcessingLayout.BATTERY_SLOT);
        rects.add(new Rect(ProcessingLayout.MOTOR_CENTER_X - 28,
                ProcessingLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(ProcessingLayout.BATTERY_CENTER_X - 28,
                ProcessingLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(ProcessingLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "processing machine");
        assertInsidePanel(rects, ProcessingLayout.WIDTH, ProcessingLayout.HEIGHT, "processing machine");
        assertFalse(ProcessingLayout.INPUT_SLOT.intersects(ProcessingLayout.PROGRESS_BAR));
        assertFalse(ProcessingLayout.PROGRESS_BAR.intersects(ProcessingLayout.OUTPUT_SLOT));
        assertTrue(ProcessingLayout.ENERGY_BAR.bottom() < ProcessingLayout.INPUT_SLOT.y(),
                "the energy bar must stay above the machine slots");
        assertTrue(ProcessingLayout.STATUS_TEXT_Y + 8 < ProcessingLayout.INPUT_SLOT.y(),
                "the status strip must sit above the machine slots");
        assertTrue(ProcessingLayout.UPGRADE_LABEL_Y > ProcessingLayout.INPUT_SLOT.bottom(),
                "the upgrade labels must stay below the input/output row");
        assertTrue(ProcessingLayout.PLAYER_INV_Y > ProcessingLayout.MOTOR_SLOT.bottom(),
                "the player inventory must stay below the upgrade row");
        assertTrue(ProcessingLayout.ENERGY_BAR.right() <= ProcessingLayout.WIDTH - GuiMetrics.SIDE,
                "the energy bar must stay inside the content area");
    }

    @Test
    void seedCultivatorElementsAreSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(SeedCultivatorLayout.ENERGY_BAR);
        rects.add(SeedCultivatorLayout.PROGRESS_BAR);
        rects.add(SeedCultivatorLayout.SEED_SLOT);
        rects.add(SeedCultivatorLayout.FEED_SLOT);
        rects.add(SeedCultivatorLayout.OUTPUT_SLOT);
        rects.add(SeedCultivatorLayout.MOTOR_SLOT);
        rects.add(SeedCultivatorLayout.BATTERY_SLOT);
        rects.add(new Rect(SeedCultivatorLayout.MOTOR_CENTER_X - 28,
                SeedCultivatorLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(SeedCultivatorLayout.BATTERY_CENTER_X - 28,
                SeedCultivatorLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(SeedCultivatorLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "seed cultivator");
        assertInsidePanel(rects, SeedCultivatorLayout.WIDTH, SeedCultivatorLayout.HEIGHT,
                "seed cultivator");
        assertTrue(SeedCultivatorLayout.ENERGY_BAR.bottom() < SeedCultivatorLayout.SLOT_LABEL_Y,
                "the energy bar must stay above the slot labels");
        assertTrue(SeedCultivatorLayout.SLOT_LABEL_Y < SeedCultivatorLayout.SEED_SLOT.y(),
                "the slot labels must stay above the slots");
        assertTrue(SeedCultivatorLayout.PROGRESS_BAR.y() > SeedCultivatorLayout.OUTPUT_SLOT.bottom(),
                "the progress bar must stay below the machine slots");
        assertTrue(SeedCultivatorLayout.BATCH_TEXT_Y > SeedCultivatorLayout.PROGRESS_BAR.bottom(),
                "the batch line must stay below the progress bar");
        assertTrue(SeedCultivatorLayout.FEED_TEXT_Y > SeedCultivatorLayout.BATCH_TEXT_Y + 8,
                "the feed line must not collide with the batch line");
        assertTrue(SeedCultivatorLayout.UPGRADE_LABEL_Y > SeedCultivatorLayout.FEED_TEXT_Y + 8,
                "the upgrade row must stay below the feed line");
        assertTrue(SeedCultivatorLayout.MOTOR_SLOT.bottom() < SeedCultivatorLayout.PLAYER_INV_Y,
                "the upgrade slots must stay above the player inventory");
    }

    @Test
    void matterAmplifierElementsAreSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(MatterAmplifierLayout.ENERGY_BAR);
        rects.add(MatterAmplifierLayout.PROGRESS_BAR);
        rects.add(MatterAmplifierLayout.INPUT_SLOT);
        rects.add(MatterAmplifierLayout.CATALYST_SLOT);
        rects.add(MatterAmplifierLayout.OUTPUT_SLOT);
        rects.add(MatterAmplifierLayout.DUST_OUTPUT_SLOT);
        rects.add(MatterAmplifierLayout.MOTOR_SLOT);
        rects.add(MatterAmplifierLayout.BATTERY_SLOT);
        rects.add(new Rect(MatterAmplifierLayout.MOTOR_CENTER_X - 28,
                MatterAmplifierLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(MatterAmplifierLayout.BATTERY_CENTER_X - 28,
                MatterAmplifierLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(MatterAmplifierLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "matter amplifier");
        assertInsidePanel(rects, MatterAmplifierLayout.WIDTH, MatterAmplifierLayout.HEIGHT,
                "matter amplifier");
        assertTrue(MatterAmplifierLayout.ENERGY_BAR.bottom()
                        < MatterAmplifierLayout.SLOT_LABEL_Y,
                "the energy bar must stay above the slot labels");
        assertTrue(MatterAmplifierLayout.SLOT_LABEL_Y < MatterAmplifierLayout.INPUT_SLOT.y(),
                "the slot labels must stay above the slots");
        assertTrue(MatterAmplifierLayout.PROGRESS_BAR.y()
                        > MatterAmplifierLayout.INPUT_SLOT.bottom(),
                "the progress bar must stay below the machine slots");
        assertTrue(MatterAmplifierLayout.CONVERSION_TEXT_Y
                        > MatterAmplifierLayout.PROGRESS_BAR.bottom(),
                "the conversion line must stay below the progress bar");
        assertTrue(MatterAmplifierLayout.UPGRADE_LABEL_Y
                        > MatterAmplifierLayout.CONVERSION_TEXT_Y + 8,
                "the upgrade row must stay below the conversion line");
        assertTrue(MatterAmplifierLayout.MOTOR_SLOT.bottom()
                        < MatterAmplifierLayout.PLAYER_INV_Y,
                "the upgrade slots must stay above the player inventory");
        // The two outputs are next to each other, not on top of each other.
        assertFalse(MatterAmplifierLayout.OUTPUT_SLOT.intersects(
                MatterAmplifierLayout.DUST_OUTPUT_SLOT));
        assertFalse(MatterAmplifierLayout.INPUT_SLOT.intersects(MatterAmplifierLayout.CATALYST_SLOT));
    }

    @Test
    void alloyFurnaceAndAssemblerElementsAreSeparated() {
        List<Rect> alloyRects = new ArrayList<>();
        alloyRects.add(AlloyFurnaceLayout.FIRST_SLOT);
        alloyRects.add(AlloyFurnaceLayout.SECOND_SLOT);
        alloyRects.add(AlloyFurnaceLayout.FUEL_SLOT);
        alloyRects.add(AlloyFurnaceLayout.OUTPUT_SLOT);
        alloyRects.add(AlloyFurnaceLayout.PROGRESS_BAR);
        alloyRects.add(AlloyFurnaceLayout.BURN_BAR);
        alloyRects.add(new Rect(AlloyFurnaceLayout.BURN_TEXT_X, AlloyFurnaceLayout.BURN_TEXT_Y, 130, 9));
        alloyRects.add(AlloyFurnaceLayout.MOTOR_SLOT);
        alloyRects.add(AlloyFurnaceLayout.BATTERY_SLOT);
        alloyRects.add(new Rect(AlloyFurnaceLayout.MOTOR_CENTER_X - 28,
                AlloyFurnaceLayout.UPGRADE_LABEL_Y, 56, 9));
        alloyRects.add(new Rect(AlloyFurnaceLayout.BATTERY_CENTER_X - 28,
                AlloyFurnaceLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            alloyRects.add(AlloyFurnaceLayout.playerSlot(index));
        }
        assertNoOverlaps(alloyRects, "alloy furnace");
        assertInsidePanel(alloyRects, AlloyFurnaceLayout.WIDTH, AlloyFurnaceLayout.HEIGHT, "alloy furnace");

        List<Rect> asmRects = new ArrayList<>();
        asmRects.add(AssemblerLayout.ENERGY_BAR);
        asmRects.add(AssemblerLayout.PROGRESS_BAR);
        for (int index = 0; index < 3; index++) {
            asmRects.add(AssemblerLayout.inputSlot(index));
        }
        asmRects.add(AssemblerLayout.OUTPUT_SLOT);
        asmRects.add(AssemblerLayout.MOTOR_SLOT);
        asmRects.add(AssemblerLayout.BATTERY_SLOT);
        asmRects.add(new Rect(AssemblerLayout.MOTOR_CENTER_X - 28,
                AssemblerLayout.UPGRADE_LABEL_Y, 56, 9));
        asmRects.add(new Rect(AssemblerLayout.BATTERY_CENTER_X - 28,
                AssemblerLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            asmRects.add(AssemblerLayout.playerSlot(index));
        }
        assertNoOverlaps(asmRects, "assembler");
        assertInsidePanel(asmRects, AssemblerLayout.WIDTH, AssemblerLayout.HEIGHT, "assembler");
    }

    @Test
    void phaseReactorElementsAreSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(PhaseReactorLayout.ENERGY_BAR);
        rects.add(PhaseReactorLayout.PROGRESS_BAR);
        for (int index = 0; index < 3; index++) {
            rects.add(PhaseReactorLayout.inputSlot(index));
        }
        rects.add(PhaseReactorLayout.OUTPUT_SLOT);
        rects.add(PhaseReactorLayout.MOTOR_SLOT);
        rects.add(PhaseReactorLayout.BATTERY_SLOT);
        rects.add(new Rect(PhaseReactorLayout.MOTOR_CENTER_X - 28,
                PhaseReactorLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(PhaseReactorLayout.BATTERY_CENTER_X - 28,
                PhaseReactorLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(PhaseReactorLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "phase reactor");
        assertInsidePanel(rects, PhaseReactorLayout.WIDTH, PhaseReactorLayout.HEIGHT,
                "phase reactor");
        assertTrue(PhaseReactorLayout.ENERGY_BAR.bottom() < PhaseReactorLayout.INPUT_SLOT_1.y(),
                "the energy bar must stay above the machine slots");
        assertTrue(PhaseReactorLayout.STATUS_TEXT_Y + 8 < PhaseReactorLayout.INPUT_SLOT_1.y(),
                "the status strip must sit above the machine slots");
        assertTrue(PhaseReactorLayout.SLOT_LABEL_Y > PhaseReactorLayout.STATUS_TEXT_Y + 8,
                "the slot labels must not collide with the status line");
        assertTrue(PhaseReactorLayout.PLAYER_INV_Y > PhaseReactorLayout.MOTOR_SLOT.bottom(),
                "the player inventory must stay below the upgrade row");
        assertFalse(PhaseReactorLayout.PROGRESS_BAR.intersects(
                PhaseReactorLayout.OUTPUT_SLOT));
        assertFalse(PhaseReactorLayout.inputSlot(2).intersects(
                PhaseReactorLayout.PROGRESS_BAR));
    }

    @Test
    void quantumCompressorElementsAreSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(QuantumCompressorLayout.ENERGY_BAR);
        rects.add(QuantumCompressorLayout.PROGRESS_BAR);
        rects.add(QuantumCompressorLayout.INPUT_SLOT);
        for (int index = 0; index < 4; index++) {
            rects.add(QuantumCompressorLayout.outputSlot(index));
        }
        rects.add(new Rect(GuiMetrics.SIDE, QuantumCompressorLayout.BATCH_TEXT_Y, 150, 9));
        rects.add(QuantumCompressorLayout.MOTOR_SLOT);
        rects.add(QuantumCompressorLayout.BATTERY_SLOT);
        rects.add(new Rect(QuantumCompressorLayout.MOTOR_CENTER_X - 28,
                QuantumCompressorLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(QuantumCompressorLayout.BATTERY_CENTER_X - 28,
                QuantumCompressorLayout.UPGRADE_LABEL_Y, 56, 9));
        for (int index = 0; index < 36; index++) {
            rects.add(QuantumCompressorLayout.playerSlot(index));
        }
        assertNoOverlaps(rects, "quantum compressor");
        assertInsidePanel(rects, QuantumCompressorLayout.WIDTH, QuantumCompressorLayout.HEIGHT,
                "quantum compressor");
        assertTrue(QuantumCompressorLayout.ENERGY_BAR.bottom()
                        < QuantumCompressorLayout.SLOT_LABEL_Y,
                "the energy bar must stay above the slot labels");
        assertTrue(QuantumCompressorLayout.SLOT_LABEL_Y < QuantumCompressorLayout.INPUT_SLOT.y(),
                "the slot labels must stay above the slots");
        assertTrue(QuantumCompressorLayout.STATUS_TEXT_Y + 8
                        < QuantumCompressorLayout.INPUT_SLOT.y(),
                "the status strip must sit above the machine slots");
        assertTrue(QuantumCompressorLayout.BATCH_TEXT_Y
                        > QuantumCompressorLayout.STATUS_TEXT_Y + 8,
                "the batch line must not collide with the status line");
        assertTrue(QuantumCompressorLayout.UPGRADE_LABEL_Y
                        > QuantumCompressorLayout.BATCH_TEXT_Y + 8,
                "the upgrade row must stay below the batch line");
        assertTrue(QuantumCompressorLayout.PLAYER_INV_Y
                        > QuantumCompressorLayout.MOTOR_SLOT.bottom(),
                "the player inventory must stay below the upgrade row");
        // The four outputs form a 2 x 2 block to the right of the progress bar.
        assertFalse(QuantumCompressorLayout.INPUT_SLOT.intersects(
                QuantumCompressorLayout.PROGRESS_BAR));
        assertFalse(QuantumCompressorLayout.PROGRESS_BAR.intersects(
                QuantumCompressorLayout.OUTPUT_SLOT_1));
        assertFalse(QuantumCompressorLayout.PROGRESS_BAR.intersects(
                QuantumCompressorLayout.OUTPUT_SLOT_3));
        assertTrue(QuantumCompressorLayout.OUTPUT_SLOT_4.right()
                        <= QuantumCompressorLayout.WIDTH,
                "the last output slot leaves the panel");
    }

    @Test
    void transporterLayoutsAreSeparatedForEveryTier() {
        for (com.industrialcore.machine.TransporterKind kind
                : com.industrialcore.machine.TransporterKind.values()) {
            List<Rect> rects = new ArrayList<>();
            rects.add(TransporterLayout.CHANNEL_FIELD);
            rects.add(TransporterLayout.SET_BUTTON);
            rects.add(TransporterLayout.ROLE_BUTTON);
            // The FE bar and the two transfer toggles only exist on the Phase 28/29 tiers.
            if (kind.supportsPower()) {
                rects.add(TransporterLayout.ITEM_BUTTON);
                rects.add(TransporterLayout.POWER_BUTTON);
                rects.add(TransporterLayout.ENERGY_BAR);
            }
            for (int index = 0; index < kind.slotCount(); index++) {
                rects.add(TransporterLayout.slot(kind, index));
            }
            for (int index = 0; index < 36; index++) {
                rects.add(TransporterLayout.playerSlot(kind, index));
            }
            assertNoOverlaps(rects, "transporter " + kind);
            assertInsidePanel(rects, TransporterLayout.WIDTH, TransporterLayout.height(kind),
                    "transporter " + kind);

            // The state lives in the shared status strip, so the controls start below it.
            assertTrue(TransporterLayout.STATUS_TEXT_Y + 8 <= TransporterLayout.CHANNEL_FIELD.y(),
                    kind + ": the channel row must start below the status strip");
            assertTrue(TransporterLayout.CHANNEL_FIELD.bottom() <= TransporterLayout.gridTop(kind),
                    kind + ": the controls must stay above the buffer grid");
            assertTrue(TransporterLayout.destTextY(kind) + 8 <= TransporterLayout.gridTop(kind),
                    kind + ": the destination line must end above the grid");
            if (kind.supportsPower()) {
                assertTrue(TransporterLayout.ENERGY_BAR.bottom() <= TransporterLayout.ENERGY_TEXT_Y,
                        kind + ": the FE text must stay below the FE bar");
                assertTrue(TransporterLayout.ENERGY_TEXT_Y + 8 <= TransporterLayout.destTextY(kind),
                        kind + ": the FE text must not run into the destination line");
            }
            // The grid ends clearly above the player inventory, the hotbar sits 58 px below
            // the inventory rows, and the panel leaves room for the hotbar.
            assertTrue(TransporterLayout.gridBottom(kind)
                            < TransporterLayout.playerInvY(kind),
                    kind + ": the buffer grid must not touch the player inventory");
            assertTrue(TransporterLayout.playerInvY(kind) + 3 * 18
                            <= TransporterLayout.hotbarY(kind),
                    kind + ": three inventory rows must fit above the hotbar");
            assertTrue(TransporterLayout.hotbarY(kind) + 16 + 4
                            <= TransporterLayout.height(kind),
                    kind + ": the hotbar must fit inside the panel");
            // Every tier must fit a 1280x800 window at GUI scale 2 (400 px logical height).
            assertTrue(TransporterLayout.height(kind) <= 400,
                    kind + ": the panel is taller than the 400 px a scale-2 window offers");
        }
        // The tiers differ visibly in grid height (1 / 1 / 2 / 8 rows).
        assertTrue(TransporterLayout.gridBottom(
                        com.industrialcore.machine.TransporterKind.ARRAY)
                > TransporterLayout.gridBottom(
                        com.industrialcore.machine.TransporterKind.QUANTUM) + 100,
                "the array tier must offer its 64 slots over eight rows");
        assertEquals(7 * 18, TransporterLayout.slot(
                        com.industrialcore.machine.TransporterKind.ARRAY, 63).y()
                - TransporterLayout.slot(
                        com.industrialcore.machine.TransporterKind.ARRAY, 0).y(),
                "the eighth row starts exactly seven rows below the first");
    }

    @Test
    void generationPanelsAreSeparatedForEveryDeviceKind() {
        List<Rect> rects = new ArrayList<>();
        rects.add(GenerationLayout.ENERGY_COLUMN);
        rects.add(GenerationLayout.FUEL_SLOT);
        rects.add(GenerationLayout.BURN_BAR);
        for (int index = 0; index < 36; index++) {
            rects.add(GenerationLayout.playerSlot(index));
        }
        // Text bands: the two read-outs and the fuel label (a fuel device shows these; a
        // condition device swaps the well and the burn gauge for its source plate and label).
        rects.add(new Rect(GuiMetrics.SIDE, GenerationLayout.RATE_TEXT_Y, 110, 9));
        rects.add(new Rect(GuiMetrics.SIDE, GenerationLayout.ENERGY_TEXT_Y, 130, 9));
        // The label band stops left of the FE column: that is all the room the row has.
        int labelRoom = GenerationLayout.ENERGY_COLUMN.x() - GenerationLayout.SLOT_LABEL_X - 4;
        rects.add(new Rect(GenerationLayout.SLOT_LABEL_X, GenerationLayout.SLOT_LABEL_Y,
                labelRoom, 9));
        assertTrue(labelRoom >= 100, "the fuel label needs room for a long translation");
        // The source label shares the well row with the plate, never with the burn gauge.
        Rect sourceLabel = new Rect(GenerationLayout.SOURCE_LABEL_X,
                GenerationLayout.SOURCE_LABEL_Y,
                GuiMetrics.contentRight() - GenerationLayout.SOURCE_LABEL_X, 9);
        assertFalse(sourceLabel.intersects(GenerationLayout.SOURCE_PLATE),
                "the source label must sit beside the plate, not on it");
        assertTrue(sourceLabel.right() <= GuiMetrics.contentRight(),
                "the source label must stay inside the content area");

        assertNoOverlaps(rects, "power device");
        assertInsidePanel(rects, GenerationLayout.WIDTH, GenerationLayout.HEIGHT, "power device");
        assertTrue(GenerationLayout.ENERGY_COLUMN.right() <= GuiMetrics.contentRight(),
                "the FE column must stay inside the content area");
        assertTrue(GenerationLayout.FUEL_SLOT.bottom() <= GenerationLayout.BODY_BOTTOM,
                "the fuel well must stay inside the body");
        assertTrue(GenerationLayout.SOURCE_PLATE.equals(GenerationLayout.FUEL_SLOT),
                "a condition device draws its plate on the fuel well position");
        assertTrue(GenerationLayout.SLOT_LABEL_Y < GenerationLayout.FUEL_SLOT.y(),
                "the fuel label must sit above its well");
        assertFalse(GenerationLayout.FUEL_SLOT.intersects(GenerationLayout.BURN_BAR));
        assertEquals(GuiMetrics.playerInvY(GenerationLayout.BODY_BOTTOM),
                GenerationLayout.playerSlot(0).y());
    }

    @Test
    void oreSynthesizerPanelIsSeparated() {
        List<Rect> rects = new ArrayList<>();
        rects.add(OreSynthesizerLayout.ENERGY_BAR);
        rects.add(OreSynthesizerLayout.PROGRESS_BAR);
        rects.add(OreSynthesizerLayout.OUTPUT_SLOT);
        // INPUT_GROUP is the frame around the grid, so it contains the wells instead of
        // competing with them; the containment is checked below.
        for (int index = 0; index < OreSynthesizerLayout.GRID_COLUMNS
                * OreSynthesizerLayout.GRID_ROWS; index++) {
            Rect slot = OreSynthesizerLayout.inputSlot(index);
            // Every ingredient well must sit inside its purple group.
            assertTrue(slot.x() >= OreSynthesizerLayout.INPUT_GROUP.x()
                            && slot.right() <= OreSynthesizerLayout.INPUT_GROUP.right()
                            && slot.y() >= OreSynthesizerLayout.INPUT_GROUP.y()
                            && slot.bottom() <= OreSynthesizerLayout.INPUT_GROUP.bottom(),
                    "ingredient " + index + " leaves its group: " + slot);
            rects.add(slot);
        }
        for (int index = 0; index < 36; index++) {
            rects.add(OreSynthesizerLayout.playerSlot(index));
        }
        // The FE read-out sits on the shelf under the grid, inside the bay.
        rects.add(new Rect(OreSynthesizerLayout.ENERGY_TEXT_X, OreSynthesizerLayout.ENERGY_TEXT_Y,
                130, 9));
        rects.add(OreSynthesizerLayout.MOTOR_SLOT);
        rects.add(OreSynthesizerLayout.BATTERY_SLOT);
        rects.add(new Rect(OreSynthesizerLayout.MOTOR_CENTER_X - 28,
                OreSynthesizerLayout.UPGRADE_LABEL_Y, 56, 9));
        rects.add(new Rect(OreSynthesizerLayout.BATTERY_CENTER_X - 28,
                OreSynthesizerLayout.UPGRADE_LABEL_Y, 56, 9));

        assertNoOverlaps(rects, "ore synthesizer");
        assertInsidePanel(rects, OreSynthesizerLayout.WIDTH, OreSynthesizerLayout.HEIGHT,
                "ore synthesizer");
        assertTrue(OreSynthesizerLayout.BAY.y() >= GuiMetrics.BODY_TOP,
                "the bay must start below the status strip");
        assertTrue(OreSynthesizerLayout.BAY.bottom() <= OreSynthesizerLayout.BODY_BOTTOM,
                "the bay must end above the inventory block");
        assertTrue(OreSynthesizerLayout.ENERGY_BAR.bottom() < OreSynthesizerLayout.SHELF_Y,
                "the FE column must stay above the shelf");
        assertTrue(OreSynthesizerLayout.SHELF_Y < OreSynthesizerLayout.ENERGY_TEXT_Y,
                "the FE read-out belongs on the shelf line or below it");
        assertTrue(OreSynthesizerLayout.ENERGY_TEXT_Y + 8 <= OreSynthesizerLayout.BAY.bottom(),
                "the FE read-out must stay inside the bay");
        assertTrue(OreSynthesizerLayout.PROGRESS_BAR.x() >= OreSynthesizerLayout.INPUT_GROUP.right(),
                "the progress conduit must follow the ingredient grid");
        assertTrue(OreSynthesizerLayout.PROGRESS_BAR.right() <= OreSynthesizerLayout.OUTPUT_SLOT.x(),
                "the result well must follow the progress conduit");
        assertEquals(GuiMetrics.playerInvY(OreSynthesizerLayout.BODY_BOTTOM),
                OreSynthesizerLayout.playerSlot(0).y());
    }

    private static void addPlayerSlots(List<Rect> rects) {
        for (int index = 0; index < 36; index++) {
            rects.add(QuarryLayout.playerSlot(index));
        }
    }

    private static void assertNoOverlaps(List<Rect> rects, String where) {
        for (int first = 0; first < rects.size(); first++) {
            for (int second = first + 1; second < rects.size(); second++) {
                Rect a = rects.get(first);
                Rect b = rects.get(second);
                assertFalse(a.intersects(b),
                        where + ": " + a + " overlaps " + b);
            }
        }
    }

    private static void assertInsidePanel(List<Rect> rects, int width, int height, String where) {
        for (Rect rect : rects) {
            assertTrue(rect.x() >= 0 && rect.y() >= 0, where + ": " + rect + " starts outside the panel");
            assertTrue(rect.right() <= width, where + ": " + rect + " exceeds width " + width);
            assertTrue(rect.bottom() <= height, where + ": " + rect + " exceeds height " + height);
        }
    }
}
