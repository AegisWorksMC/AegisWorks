package com.industrialcore.reactor;

import com.industrialcore.reactor.ReactorMultiblockLogic.Facing2D;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideCell;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideRole;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.PartKind;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 108: Pure-Java logic for the Factory-Sim Multiblock Construction Guide.
 *
 * <p>Supports variable guide sizes ({@code 3x3x3..5x5x5} for Nuclear and
 * {@code 5x5x5..7x7x7} for Singularity), layer-by-layer vertical slicing ({@link #ALL_LAYERS}
 * vs {@code Layer 1..N}), misplaced-block detection, and missing-block aggregation with zero
 * Minecraft imports so every rule is exhaustively unit-tested.</p>
 */
public final class ReactorGuideLogic {
    /** Layer mode sentinel meaning "display every horizontal layer at once". */
    public static final int ALL_LAYERS = -1;

    /** Maximum player distance (blocks) at which the missing-block HUD panel is shown. */
    public static final double HUD_VISIBLE_RADIUS = 16.0D;

    /** Duration (ms) of the automatic "Construction Complete!" toast after formation. */
    public static final long COMPLETE_TOAST_MS = 4_000L;

    /** Per-cell evaluation state against the real world. */
    public enum CellState {
        /** Valid block already placed at this guide coordinate. */
        SATISFIED,
        /** Air / replaceable block — needs the expected reactor block. */
        MISSING,
        /** Wrong non-air block occupying this guide coordinate — warn in red! */
        MISPLACED
    }

    /** Callback returning both whether a coordinate is air and its classified {@link PartKind}. */
    @FunctionalInterface
    public interface CellInspector {
        CellSample sample(int x, int y, int z);
    }

    public record CellSample(boolean isAir, PartKind kind) {}

    /** Aggregated construction progress for either the whole multiblock or a single sliced layer. */
    public record GuideProgress(
            int totalCells,
            int satisfiedCells,
            int missingCasing,
            int missingGlass,
            int missingCore,
            int misplacedCount
    ) {
        public int missingTotal() {
            return missingCasing + missingGlass + missingCore;
        }

        public boolean isComplete() {
            return totalCells > 0 && missingTotal() == 0 && misplacedCount == 0;
        }

        public int completionPercent() {
            if (totalCells <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(100, (int) Math.round(satisfiedCells * 100.0D / totalCells)));
        }
    }

    private ReactorGuideLogic() {}

    /** Default guide cube dimension for the given reactor type (3 for Nuclear, 5 for Singularity). */
    public static int defaultGuideSize(@Nullable ReactorType type) {
        return type == ReactorType.SINGULARITY ? 5 : 3;
    }

    /** Supported preset guide cube dimensions for the given reactor type. */
    public static int[] supportedGuideSizes(@Nullable ReactorType type) {
        if (type == ReactorType.SINGULARITY) {
            return new int[]{5, 6, 7};
        }
        return new int[]{3, 4, 5};
    }

    /** Clamps {@code size} into the valid guide dimension range for {@code type}. */
    public static int clampGuideSize(@Nullable ReactorType type, int size) {
        int[] supported = supportedGuideSizes(type);
        int min = supported[0];
        int max = supported[supported.length - 1];
        return Math.max(min, Math.min(max, size));
    }

    /** Cycles to the next supported guide size for {@code type}. */
    public static int nextGuideSize(@Nullable ReactorType type, int currentSize) {
        int[] supported = supportedGuideSizes(type);
        for (int i = 0; i < supported.length; i++) {
            if (supported[i] == currentSize) {
                return supported[(i + 1) % supported.length];
            }
        }
        return supported[0];
    }

    /**
     * Generates a variable-size {@code size x size x size} holographic assembly guide anchored at
     * the controller block on the front wall (excluding the controller's own cell).
     *
     * <p>The bottom layer is always at {@code ctrl.y() - 1}, so the controller sits on Layer 2
     * ({@code y = minY + 1}), centered horizontally on the front face ({@code dd = 0}).
     * For {@code size == 3}, this produces the exact same 26 cells as
     * {@link ReactorMultiblockLogic#buildMinGuide3x3x3(IntPos, Facing2D)}.</p>
     */
    public static List<GuideCell> buildGuide(@Nullable IntPos ctrl, @Nullable Facing2D facing, int size) {
        if (ctrl == null || facing == null || size < 3 || size > 7) {
            return List.of();
        }
        int rx = facing.rightX();
        int rz = facing.rightZ();
        int bx = facing.backX();
        int bz = facing.backZ();

        int leftSpan = size / 2;
        int rightSpan = size - 1 - leftSpan;
        int minDy = -1;
        int maxDy = minDy + size - 1;

        List<GuideCell> cells = new ArrayList<>(size * size * size - 1);
        for (int dy = minDy; dy <= maxDy; dy++) {
            boolean onY = (dy == minDy || dy == maxDy);
            for (int dr = -leftSpan; dr <= rightSpan; dr++) {
                boolean onR = (dr == -leftSpan || dr == rightSpan);
                for (int dd = 0; dd < size; dd++) {
                    if (dy == 0 && dr == 0 && dd == 0) {
                        continue; // Controller block itself
                    }
                    boolean onD = (dd == 0 || dd == size - 1);
                    int bc = (onY ? 1 : 0) + (onR ? 1 : 0) + (onD ? 1 : 0);
                    GuideRole role = bc >= 2 ? GuideRole.FRAME_CASING
                            : (bc == 1 ? GuideRole.WALL_GLASS : GuideRole.INTERIOR_CORE);
                    int x = ctrl.x() + rx * dr + bx * dd;
                    int y = ctrl.y() + dy;
                    int z = ctrl.z() + rz * dr + bz * dd;
                    cells.add(new GuideCell(x, y, z, role));
                }
            }
        }
        return cells;
    }

    /** Bottom world Y coordinate of a guide anchored at {@code ctrlY}. */
    public static int guideMinY(int ctrlY) {
        return ctrlY - 1;
    }

    /** Total number of vertical layers for a cube guide of dimension {@code size}. */
    public static int layerCount(int size) {
        return Math.max(1, size);
    }

    /** 0-based layer index for a cell at {@code cellY} given bottom {@code minY}. */
    public static int layerIndexOf(int cellY, int minY) {
        return cellY - minY;
    }

    /**
     * Cycles layer slice mode: {@code ALL_LAYERS (-1) -> 0 -> 1 -> ... -> totalLayers-1 -> ALL_LAYERS (-1)}.
     */
    public static int nextLayerMode(int currentLayer, int totalLayers) {
        int safeTotal = Math.max(1, totalLayers);
        if (currentLayer < 0) {
            return 0;
        }
        int next = currentLayer + 1;
        return next >= safeTotal ? ALL_LAYERS : next;
    }

    /**
     * Cycles the controller Shift+Right-Click sequence when guide is already active:
     * {@code ALL_LAYERS (-1) -> 0 -> 1 -> ... -> totalLayers-1 -> Integer.MIN_VALUE (turn OFF)}.
     */
    public static int nextShiftClickLayerOrOff(int currentLayer, int totalLayers) {
        int safeTotal = Math.max(1, totalLayers);
        if (currentLayer < 0) {
            return 0;
        }
        int next = currentLayer + 1;
        return next >= safeTotal ? Integer.MIN_VALUE : next;
    }

    /** Returns all cells when {@code layerMode == ALL_LAYERS}, or only cells on {@code layerMode}. */
    public static List<GuideCell> filterByLayer(List<GuideCell> allCells, int minY, int layerMode) {
        if (allCells == null || allCells.isEmpty()) {
            return List.of();
        }
        if (layerMode == ALL_LAYERS) {
            return allCells;
        }
        int targetY = minY + layerMode;
        List<GuideCell> out = new ArrayList<>();
        for (GuideCell cell : allCells) {
            if (cell.y() == targetY) {
                out.add(cell);
            }
        }
        return out;
    }

    /** True when the block classified as {@code existing} satisfies the guide {@code role}. */
    public static boolean isCellSatisfied(GuideRole role, @Nullable PartKind existing) {
        if (role == null || existing == null) {
            return false;
        }
        return switch (role) {
            case FRAME_CASING -> existing == PartKind.CASING;
            case WALL_GLASS -> existing == PartKind.CASING || existing == PartKind.GLASS || existing == PartKind.PORT;
            case INTERIOR_CORE -> existing == PartKind.CORE_CELL;
        };
    }

    /** Evaluates one guide cell against the sampled world block. */
    public static CellState classifyCell(GuideRole role, @Nullable CellSample sample) {
        if (sample == null) {
            return CellState.MISSING;
        }
        if (isCellSatisfied(role, sample.kind())) {
            return CellState.SATISFIED;
        }
        return sample.isAir() ? CellState.MISSING : CellState.MISPLACED;
    }

    /**
     * Evaluates every cell in {@code cells}, counting satisfied slots, remaining missing blocks per
     * role (note: misplaced cells ALSO still need the expected block once cleared, OR are tracked
     * separately — we track missing air slots per role AND misplaced non-air count cleanly).
     */
    public static GuideProgress evaluate(List<GuideCell> cells, CellInspector inspector) {
        if (cells == null || cells.isEmpty() || inspector == null) {
            return new GuideProgress(0, 0, 0, 0, 0, 0);
        }
        int satisfied = 0;
        int missCasing = 0;
        int missGlass = 0;
        int missCore = 0;
        int misplaced = 0;

        for (GuideCell cell : cells) {
            CellSample sample = inspector.sample(cell.x(), cell.y(), cell.z());
            CellState st = classifyCell(cell.role(), sample);
            if (st == CellState.SATISFIED) {
                satisfied++;
            } else {
                if (st == CellState.MISPLACED) {
                    misplaced++;
                }
                // Count required block needed at this slot (both air and misplaced slots still need the target block)
                switch (cell.role()) {
                    case FRAME_CASING -> missCasing++;
                    case WALL_GLASS -> missGlass++;
                    case INTERIOR_CORE -> missCore++;
                }
            }
        }
        return new GuideProgress(cells.size(), satisfied, missCasing, missGlass, missCore, misplaced);
    }

    /** True when player is within HUD display radius of the active reactor controller. */
    public static boolean isWithinHudRange(double distSq) {
        return distSq <= HUD_VISIBLE_RADIUS * HUD_VISIBLE_RADIUS;
    }
}
