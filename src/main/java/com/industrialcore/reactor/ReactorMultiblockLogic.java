package com.industrialcore.reactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 103: Pure-Java variable-size multiblock validator and performance scaler for
 * {@code nuclear_generator} (3x3x3 .. 5x5x5) and {@code singularity_reactor} (3x3x3 .. 7x7x7).
 *
 * <p>Zero Minecraft imports so unit tests can exhaustively prove geometry rules, failure reasons,
 * and overflow-safe {@code long}/{@code int} scaling on a plain JVM.</p>
 */
public final class ReactorMultiblockLogic {
    /** Overflow-safe upper bound for scaled Singularity Reactor {@code long} FE capacity (1T FE). */
    public static final long SINGULARITY_MAX_LONG_CAPACITY = 1_000_000_000_000L;
    /** Overflow-safe upper bound for scaled Singularity Reactor FE/t output (10B FE/t). */
    public static final long SINGULARITY_MAX_RATE = 10_000_000_000L;
    /** Overflow-safe upper bound for scaled Nuclear Generator {@code int} FE capacity (1B FE). */
    public static final int NUCLEAR_MAX_INT_CAPACITY = 1_000_000_000;
    /** Maximum coolant burn efficiency multiplier (3.0x). */
    public static final double MAX_COOLANT_EFFICIENCY = 3.0D;

    public enum ReactorType {
        NUCLEAR(3, 5),
        SINGULARITY(3, 7);

        private final int minDim;
        private final int maxDim;

        ReactorType(int minDim, int maxDim) {
            this.minDim = minDim;
            this.maxDim = maxDim;
        }

        public int minDim() {
            return minDim;
        }

        public int maxDim() {
            return maxDim;
        }
    }

    public enum PartKind {
        AIR,
        CASING,
        GLASS,
        CORE_CELL,
        COOLANT,
        PORT,
        NUCLEAR_CTRL,
        SINGULARITY_CTRL,
        OTHER;

        public boolean isController() {
            return this == NUCLEAR_CTRL || this == SINGULARITY_CTRL;
        }

        public boolean isWallAllowed() {
            return this == CASING || this == GLASS || this == PORT || isController();
        }

        public boolean isInteriorAllowed() {
            return this == AIR || this == CORE_CELL || this == COOLANT;
        }
    }

    /** Ordinal is synced across {@code ContainerData} to render concrete GUI failure hints. */
    public enum FailReason {
        FORMED("gui.industrialcore.reactor.status.formed"),
        INCOMPLETE_FRAME("gui.industrialcore.reactor.fail.incomplete_frame"),
        INVALID_WALL("gui.industrialcore.reactor.fail.invalid_wall"),
        INVALID_INTERIOR("gui.industrialcore.reactor.fail.invalid_interior"),
        NO_CORE_CELL("gui.industrialcore.reactor.fail.no_core_cell"),
        DUPLICATE_CONTROLLER("gui.industrialcore.reactor.fail.duplicate_controller"),
        SIZE_OUT_OF_RANGE("gui.industrialcore.reactor.fail.size_out_of_range"),
        OVERLAPPING_REACTOR("gui.industrialcore.reactor.fail.overlapping_reactor");

        private final String translationKey;

        FailReason(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public static FailReason byOrdinal(int ord) {
            FailReason[] vals = values();
            if (ord < 0 || ord >= vals.length) {
                return INCOMPLETE_FRAME;
            }
            return vals[ord];
        }
    }

    /** Horizontal outward facing of the controller block (interior extends in the opposite direction). */
    public enum Facing2D {
        NORTH(0, -1, 1, 0),
        SOUTH(0, 1, -1, 0),
        WEST(-1, 0, 0, -1),
        EAST(1, 0, 0, 1);

        private final int fx;
        private final int fz;
        private final int rx;
        private final int rz;

        Facing2D(int fx, int fz, int rx, int rz) {
            this.fx = fx;
            this.fz = fz;
            this.rx = rx;
            this.rz = rz;
        }

        /** Inward (back) unit vector pointing into the multiblock. */
        public int backX() {
            return -fx;
        }

        public int backZ() {
            return -fz;
        }

        /** Lateral horizontal unit vector perpendicular to facing. */
        public int rightX() {
            return rx;
        }

        public int rightZ() {
            return rz;
        }
    }

    public record IntPos(int x, int y, int z) {}

    @FunctionalInterface
    public interface BlockSampler {
        PartKind sample(int x, int y, int z);
    }

    @FunctionalInterface
    public interface OverlapChecker {
        boolean isClaimedByOther(int x, int y, int z);
    }

    public record ValidationResult(
            boolean formed,
            FailReason reason,
            int width,
            int height,
            int depth,
            int coreCells,
            int coolantBlocks,
            IntPos minPos,
            IntPos maxPos,
            List<IntPos> corePositions,
            List<IntPos> portPositions,
            List<IntPos> allPositions
    ) {
        public static ValidationResult fail(FailReason reason) {
            return new ValidationResult(false, reason, 0, 0, 0, 0, 0,
                    null, null, List.of(), List.of(), List.of());
        }
    }

    /** Guide cell role for the 3D holographic 3x3x3 assembly preview. */
    public enum GuideRole {
        FRAME_CASING("reactor_casing"),
        WALL_GLASS("reactor_glass"),
        INTERIOR_CORE("reactor_core_cell");

        private final String blockId;

        GuideRole(String blockId) {
            this.blockId = blockId;
        }

        /** Canonical block registry path projected as a translucent ghost for this guide slot. */
        public String expectedBlockId() {
            return blockId;
        }
    }

    public record GuideCell(int x, int y, int z, GuideRole role) {
        public String expectedBlockId() {
            return role.expectedBlockId();
        }
    }

    /** Minimum 3x3x3 required block counts (excluding controller): 20 casings, 5 glass/walls, 1 core. */
    public static final int MIN_GUIDE_CASINGS = 20;
    public static final int MIN_GUIDE_WALLS = 5;
    public static final int MIN_GUIDE_CORES = 1;

    private ReactorMultiblockLogic() {}

    /**
     * Validates a variable-size rectangular prism multiblock starting from the controller block
     * sitting on the front wall (facing outward along {@code facing}).
     */
    public static ValidationResult validate(ReactorType type, IntPos ctrl, Facing2D facing,
                                            BlockSampler sampler, OverlapChecker overlapChecker) {
        if (type == null || ctrl == null || facing == null || sampler == null) {
            return ValidationResult.fail(FailReason.INCOMPLETE_FRAME);
        }
        OverlapChecker overlap = overlapChecker != null ? overlapChecker : (x, y, z) -> false;
        int maxScan = type.maxDim() + 2; // Scan slightly past maxDim so oversized builds report SIZE_OUT_OF_RANGE

        int rx = facing.rightX();
        int rz = facing.rightZ();
        int bx = facing.backX();
        int bz = facing.backZ();

        // 1. Scan lateral & vertical extents along the controller's front wall plane (depth = 0)
        int leftExt = scanWallLine(ctrl.x(), ctrl.y(), ctrl.z(), -rx, 0, -rz, maxScan, sampler);
        int rightExt = scanWallLine(ctrl.x(), ctrl.y(), ctrl.z(), rx, 0, rz, maxScan, sampler);
        int downExt = scanWallLine(ctrl.x(), ctrl.y(), ctrl.z(), 0, -1, 0, maxScan, sampler);
        int upExt = scanWallLine(ctrl.x(), ctrl.y(), ctrl.z(), 0, 1, 0, maxScan, sampler);

        // Controller must lie on a wall FACE (never an edge or corner), so >= 1 step in all 4 front directions
        if (leftExt < 1 || rightExt < 1 || downExt < 1 || upExt < 1) {
            return ValidationResult.fail(FailReason.INCOMPLETE_FRAME);
        }

        int wFront = leftExt + rightExt + 1;
        int hFront = downExt + upExt + 1;

        // 2. Scan inward depth along the 4 front perimeter edges (all of which must be CASING frames)
        int dBottom = scanWallLine(ctrl.x(), ctrl.y() - downExt, ctrl.z(), bx, 0, bz, maxScan, sampler);
        int dTop = scanWallLine(ctrl.x(), ctrl.y() + upExt, ctrl.z(), bx, 0, bz, maxScan, sampler);
        int dLeft = scanWallLine(ctrl.x() - rx * leftExt, ctrl.y(), ctrl.z() - rz * leftExt, bx, 0, bz, maxScan, sampler);
        int dRight = scanWallLine(ctrl.x() + rx * rightExt, ctrl.y(), ctrl.z() + rz * rightExt, bx, 0, bz, maxScan, sampler);
        int backExt = Math.max(Math.max(dBottom, dTop), Math.max(dLeft, dRight));
        int depth = backExt + 1;

        if (wFront < type.minDim() || hFront < type.minDim() || depth < type.minDim()) {
            return ValidationResult.fail(FailReason.INCOMPLETE_FRAME);
        }
        if (wFront > type.maxDim() || hFront > type.maxDim() || depth > type.maxDim()) {
            return ValidationResult.fail(FailReason.SIZE_OUT_OF_RANGE);
        }

        // 3. Compute axis-aligned bounding box [minX..maxX, minY..maxY, minZ..maxZ]
        int c1x = ctrl.x() - rx * leftExt;
        int c1z = ctrl.z() - rz * leftExt;
        int c2x = ctrl.x() + rx * rightExt + bx * backExt;
        int c2z = ctrl.z() + rz * rightExt + bz * backExt;

        int minX = Math.min(c1x, c2x);
        int maxX = Math.max(c1x, c2x);
        int minY = ctrl.y() - downExt;
        int maxY = ctrl.y() + upExt;
        int minZ = Math.min(c1z, c2z);
        int maxZ = Math.max(c1z, c2z);

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        boolean hasFrameError = false;
        boolean hasWallError = false;
        boolean hasInteriorError = false;
        boolean hasDuplicateController = false;
        boolean hasOverlap = false;

        int coreCells = 0;
        int coolantBlocks = 0;
        List<IntPos> corePositions = new ArrayList<>();
        List<IntPos> portPositions = new ArrayList<>();
        List<IntPos> allPositions = new ArrayList<>(sizeX * sizeY * sizeZ);

        for (int y = minY; y <= maxY; y++) {
            boolean onY = (y == minY || y == maxY);
            for (int x = minX; x <= maxX; x++) {
                boolean onX = (x == minX || x == maxX);
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onZ = (z == minZ || z == maxZ);
                    if (overlap.isClaimedByOther(x, y, z)) {
                        hasOverlap = true;
                    }
                    PartKind kind = sampler.sample(x, y, z);
                    if (kind == null) {
                        kind = PartKind.OTHER;
                    }
                    boolean isSelf = (x == ctrl.x() && y == ctrl.y() && z == ctrl.z());
                    int boundaryCount = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);

                    if (boundaryCount >= 2) {
                        // 12 edges & 8 corners MUST be reactor_casing
                        if (kind.isController() && !isSelf) {
                            hasDuplicateController = true;
                        } else if (kind != PartKind.CASING) {
                            hasFrameError = true;
                        }
                    } else if (boundaryCount == 1) {
                        // 6 outer wall faces (excluding edges/corners)
                        if (isSelf) {
                            // Valid primary controller
                        } else if (kind.isController()) {
                            hasDuplicateController = true;
                        } else if (kind == PartKind.PORT) {
                            portPositions.add(new IntPos(x, y, z));
                        } else if (kind != PartKind.CASING && kind != PartKind.GLASS) {
                            hasWallError = true;
                        }
                    } else {
                        // Interior chamber: reactor_core_cell, reactor_coolant_block, or air
                        if (kind == PartKind.CORE_CELL) {
                            coreCells++;
                            corePositions.add(new IntPos(x, y, z));
                        } else if (kind == PartKind.COOLANT) {
                            coolantBlocks++;
                        } else if (kind.isController()) {
                            hasDuplicateController = true;
                        } else if (kind != PartKind.AIR) {
                            hasInteriorError = true;
                        }
                    }
                    allPositions.add(new IntPos(x, y, z));
                }
            }
        }

        if (hasOverlap) {
            return ValidationResult.fail(FailReason.OVERLAPPING_REACTOR);
        }
        if (hasDuplicateController) {
            return ValidationResult.fail(FailReason.DUPLICATE_CONTROLLER);
        }
        if (hasFrameError) {
            return ValidationResult.fail(FailReason.INCOMPLETE_FRAME);
        }
        if (hasWallError) {
            return ValidationResult.fail(FailReason.INVALID_WALL);
        }
        if (hasInteriorError) {
            return ValidationResult.fail(FailReason.INVALID_INTERIOR);
        }
        if (coreCells < 1) {
            return ValidationResult.fail(FailReason.NO_CORE_CELL);
        }

        return new ValidationResult(
                true,
                FailReason.FORMED,
                wFront,
                hFront,
                depth,
                coreCells,
                coolantBlocks,
                new IntPos(minX, minY, minZ),
                new IntPos(maxX, maxY, maxZ),
                Collections.unmodifiableList(corePositions),
                Collections.unmodifiableList(portPositions),
                Collections.unmodifiableList(allPositions)
        );
    }

    private static int scanWallLine(int sx, int sy, int sz, int dx, int dy, int dz,
                                    int maxSteps, BlockSampler sampler) {
        int steps = 0;
        for (int i = 1; i <= maxSteps; i++) {
            PartKind k = sampler.sample(sx + dx * i, sy + dy * i, sz + dz * i);
            if (k != null && k.isWallAllowed()) {
                steps = i;
            } else {
                break;
            }
        }
        return steps;
    }

    /**
     * Generates the minimum 3x3x3 holographic assembly guide centered on the controller's front face.
     * Excludes the controller's own cell (returns 26 guide cells: 20 frame casings, 5 wall panes, 1 core).
     */
    public static List<GuideCell> buildMinGuide3x3x3(IntPos ctrl, Facing2D facing) {
        if (ctrl == null || facing == null) {
            return List.of();
        }
        int rx = facing.rightX();
        int rz = facing.rightZ();
        int bx = facing.backX();
        int bz = facing.backZ();

        List<GuideCell> cells = new ArrayList<>(26);
        for (int dy = -1; dy <= 1; dy++) {
            boolean onY = (dy == -1 || dy == 1);
            for (int dr = -1; dr <= 1; dr++) {
                boolean onR = (dr == -1 || dr == 1);
                for (int dd = 0; dd <= 2; dd++) {
                    if (dy == 0 && dr == 0 && dd == 0) {
                        continue; // Controller block itself
                    }
                    boolean onD = (dd == 0 || dd == 2);
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

    // -------------------------------------------------------------------------
    // Performance scaling formulas (strictly monotonic, overflow-safe)
    // -------------------------------------------------------------------------

    /**
     * Generation output multiplier: {@code 1.0 + (coreCells - 1) * 0.45 + coolantBlocks * 0.15}.
     * Minimum 3x3x3 (1 core, 0 coolant) = {@code 1.00x}; 5x5x5 (15 cores, 12 coolants) = {@code 9.10x}.
     */
    public static double outputMultiplier(int coreCells, int coolantBlocks) {
        if (coreCells < 1) {
            return 0.0D;
        }
        int safeCool = Math.max(0, coolantBlocks);
        return 1.0D + (coreCells - 1) * 0.45D + safeCool * 0.15D;
    }

    /**
     * Internal FE capacity multiplier: {@code 1.0 + (coreCells - 1) * 0.50}.
     */
    public static double capacityMultiplier(int coreCells) {
        if (coreCells < 1) {
            return 1.0D;
        }
        return 1.0D + (coreCells - 1) * 0.50D;
    }

    /**
     * Fuel burn efficiency (duration) multiplier from coolant blocks:
     * {@code min(3.0, 1.0 + coolantBlocks * 0.08)}.
     */
    public static double burnEfficiencyMultiplier(int coolantBlocks) {
        int safeCool = Math.max(0, coolantBlocks);
        return Math.min(MAX_COOLANT_EFFICIENCY, 1.0D + safeCool * 0.08D);
    }

    /** Overflow-safe {@code long} multiplication clamped to {@code [0, maxCap]}. */
    public static long scaleLongSaturating(long base, double multiplier, long maxCap) {
        if (base <= 0L || multiplier <= 0.0D || maxCap <= 0L) {
            return 0L;
        }
        double prod = (double) base * multiplier;
        if (!Double.isFinite(prod) || prod >= (double) maxCap) {
            return maxCap;
        }
        long rounded = Math.round(prod);
        if (rounded < 0L || rounded > maxCap) {
            return maxCap;
        }
        return rounded;
    }

    /** Overflow-safe {@code int} multiplication clamped to {@code [0, maxCap]}. */
    public static int scaleIntSaturating(int base, double multiplier, int maxCap) {
        if (base <= 0 || multiplier <= 0.0D || maxCap <= 0) {
            return 0;
        }
        long scaled = scaleLongSaturating(base, multiplier, maxCap);
        return (int) Math.min(maxCap, scaled);
    }

    /** Packs (W, H, D) each in [0..15] into a single 16-bit short for {@code ContainerData}. */
    public static int packDims(int w, int h, int d) {
        return ((w & 0xF) << 8) | ((h & 0xF) << 4) | (d & 0xF);
    }

    public static int unpackWidth(int packed) {
        return (packed >>> 8) & 0xF;
    }

    public static int unpackHeight(int packed) {
        return (packed >>> 4) & 0xF;
    }

    public static int unpackDepth(int packed) {
        return packed & 0xF;
    }
}
