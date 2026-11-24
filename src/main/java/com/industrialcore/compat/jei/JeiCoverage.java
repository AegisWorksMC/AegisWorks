package com.industrialcore.compat.jei;

import java.util.ArrayList;
import java.util.List;

/**
 * What JEI shows, as plain data.
 *
 * <p>Phase 105.5: Unified dark-slate + neon-cyan technical layouts with strict 0px slot alignment
 * ({@code 18x18} frames spaced on a {@code 20px} pitch, zero oversized 26px output overlap) and
 * ample horizontal/vertical margins so Japanese &amp; English telemetry never clips.</p>
 */
public final class JeiCoverage {

    /** Width and height of one item slot frame, and of the JEI recipe arrow. */
    public static final int SLOT = 18;
    public static final int SLOT_STEP = 20;
    public static final int PAD_X = 8;
    public static final int PAD_Y = 8;
    public static final int ARROW_WIDTH = 24;
    public static final int ARROW_HEIGHT = 12;

    /**
     * Shape of a machine category.
     *
     * <p>Every layout places input slots in rows of {@link #columns} starting at {@code (PAD_X, PAD_Y)}
     * on a {@code 20px} grid, followed by the glowing progress arrow and aligned {@code 18x18} output
     * slots, with a dedicated telemetry footer below a crisp divider line.</p>
     */
    public enum Layout {
        /** One ingredient, one output: crusher, electrolyzer, precision crusher, metal press. */
        SINGLE_INPUT(1, 1, 1, 170, 68),
        /** Two ingredients, one output: alloy furnace, seed cultivator. */
        PAIR(2, 1, 1, 170, 68),
        /** Three ingredients, one output: assembler, phase reactor, reconstructor. */
        TRIO(3, 1, 1, 170, 68),
        /** Two ingredients, two outputs: matter amplifier. */
        DUAL_OUT(2, 1, 2, 170, 68),
        /** Up to ten ingredients in two rows of five: synthesizer. */
        GRID(5, 2, 1, 176, 88);

        public final int columns;
        public final int maxRows;
        public final int maxOutputs;
        public final int width;
        public final int height;

        Layout(int columns, int maxRows, int maxOutputs, int width, int height) {
            this.columns = columns;
            this.maxRows = maxRows;
            this.maxOutputs = maxOutputs;
            this.width = width;
            this.height = height;
        }

        /** X coordinate of an input item icon (its 18x18 frame sits at x-1, y-1). */
        public int inputX(int column) {
            return PAD_X + column * SLOT_STEP;
        }

        /** Y coordinate of an input item icon. */
        public int inputY(int row) {
            return PAD_Y + row * SLOT_STEP;
        }

        /** First y of the telemetry text block, below the slot region + divider line. */
        public int textTop() {
            return PAD_Y + this.maxRows * SLOT_STEP + 6;
        }

        /** Text lines: time, FE per tick, and FE of one run. */
        public int textLines() {
            return 3;
        }

        /** x of the progress arrow, cleanly placed right of the used ingredient columns. */
        public int arrowX(int usedColumns) {
            int cols = Math.max(1, Math.min(usedColumns, this.columns));
            return PAD_X + cols * SLOT_STEP + 6;
        }

        public int arrowY() {
            int slotsHeight = this.maxRows * SLOT_STEP - 2;
            return PAD_Y + (slotsHeight - ARROW_HEIGHT) / 2 - 1;
        }

        /** x of the first output item icon (its 18x18 frame sits at x-1, y-1). */
        public int outputX(int usedColumns) {
            return arrowX(usedColumns) + ARROW_WIDTH + 8;
        }

        /** y of an output item icon (vertically centered against input rows). */
        public int outputY() {
            if (this.maxRows == 1) {
                return PAD_Y;
            }
            return PAD_Y + ((this.maxRows - 1) * SLOT_STEP) / 2;
        }

        /** Frame-inclusive UI rectangles for automated non-overlap &amp; containment tests. */
        public List<PowerGenerationCatalog.Rect> allRects() {
            List<PowerGenerationCatalog.Rect> rects = new ArrayList<>();
            for (int r = 0; r < maxRows; r++) {
                for (int c = 0; c < columns; c++) {
                    rects.add(new PowerGenerationCatalog.Rect(inputX(c) - 1, inputY(r) - 1, SLOT, SLOT));
                }
            }
            rects.add(new PowerGenerationCatalog.Rect(arrowX(columns), arrowY(), ARROW_WIDTH, ARROW_HEIGHT));
            int ox = outputX(columns);
            int oy = outputY();
            for (int o = 0; o < maxOutputs; o++) {
                rects.add(new PowerGenerationCatalog.Rect(ox + o * SLOT_STEP - 1, oy - 1, SLOT, SLOT));
            }
            rects.add(new PowerGenerationCatalog.Rect(PAD_X, textTop(), width - PAD_X * 2, textLines() * 10));
            return rects;
        }
    }

    /** One machine recipe type as JEI sees it. */
    public record Category(String recipeTypeId, String titleKey, String iconItem,
                           List<String> catalysts, Layout layout) {

        /** The JEI category uid, which is the recipe type id in the mod namespace. */
        public String jeiUid() {
            return "industrialcore:" + this.recipeTypeId;
        }
    }

    private static final List<Category> CATEGORIES = List.of(
            new Category("crushing", "block.industrialcore.crusher",
                    "industrialcore:crusher",
                    List.of("industrialcore:crusher", "industrialcore:fast_crusher",
                            "industrialcore:quantum_compressor"),
                    Layout.SINGLE_INPUT),
            new Category("electrolysis", "block.industrialcore.electrolyzer",
                    "industrialcore:electrolyzer",
                    List.of("industrialcore:electrolyzer"),
                    Layout.SINGLE_INPUT),
            new Category("precision_crushing", "block.industrialcore.precision_crusher",
                    "industrialcore:precision_crusher",
                    List.of("industrialcore:precision_crusher"),
                    Layout.SINGLE_INPUT),
            new Category("pressing", "block.industrialcore.metal_press",
                    "industrialcore:metal_press",
                    List.of("industrialcore:metal_press"),
                    Layout.SINGLE_INPUT),
            new Category("alloying", "block.industrialcore.alloy_furnace",
                    "industrialcore:alloy_furnace",
                    List.of("industrialcore:alloy_furnace"),
                    Layout.PAIR),
            new Category("reconstruction", "block.industrialcore.reconstructor",
                    "industrialcore:reconstructor",
                    List.of("industrialcore:reconstructor"),
                    Layout.TRIO),
            new Category("assembling", "block.industrialcore.assembler",
                    "industrialcore:assembler",
                    List.of("industrialcore:assembler"),
                    Layout.TRIO),
            new Category("cultivation", "block.industrialcore.seed_cultivator",
                    "industrialcore:seed_cultivator",
                    List.of("industrialcore:seed_cultivator"),
                    Layout.PAIR),
            new Category("amplification", "block.industrialcore.matter_amplifier",
                    "industrialcore:matter_amplifier",
                    List.of("industrialcore:matter_amplifier"),
                    Layout.DUAL_OUT),
            new Category("phase_reaction", "block.industrialcore.phase_reactor",
                    "industrialcore:phase_reactor",
                    List.of("industrialcore:phase_reactor"),
                    Layout.TRIO),
            new Category("synthesis", "block.industrialcore.ore_synthesizer",
                    "industrialcore:ore_synthesizer",
                    List.of("industrialcore:ore_synthesizer"),
                    Layout.GRID));

    private JeiCoverage() {}

    public static List<Category> categories() {
        return CATEGORIES;
    }

    /** The category of one recipe type id, or {@code null} when it is not covered. */
    public static Category byRecipeType(String recipeTypeId) {
        for (Category category : CATEGORIES) {
            if (category.recipeTypeId().equals(recipeTypeId)) {
                return category;
            }
        }
        return null;
    }
}
