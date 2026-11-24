package com.industrialcore.level;

import com.industrialcore.level.MachineSkillTree.Branch;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Pure-Java layout and geometry definition for the Phase 97 4-Branch Skill Tree &amp; Industrial Guide.
 *
 * <p>Zero Minecraft imports so every panel rect, mode tab, 4-branch tab, branch limit-break button,
 * all 38 node card coordinates, pan clamp, and hit-test are verified directly by JUnit tests.</p>
 */
public final class SkillTreeLayout {
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    public enum NodeVisualState {
        /** Prerequisites not yet met. */
        LOCKED,
        /** Unlocked and actively leveling. */
        UNLOCKED,
        /** All child prerequisite levels satisfied (or Lv.99 reached for a leaf). */
        MASTERED,
        /** Limit-broken mastery (Lv.100+). */
        LIMIT_BROKEN
    }

    public enum NodeVisibility {
        REVEALED,
        NEXT_TARGET,
        MYSTERY
    }

    public record NodeSlot(String machineId, Branch branch, int tier, Rect rect) {}

    public record OreLineStage(
            int stage,
            String machineId,
            String outputItemId,
            boolean isVanillaOutput,
            int cumulativeMultiplier,
            boolean isSynthesizerStage
    ) {}

    public record InfraEntry(
            String sectionKey,
            String blockId,
            String specKey
    ) {}

    // ---- Authored panel canvas (420 x 280, crisp 1.0x at 1280x720 guiScale 2) ----
    public static final int WIDTH = 420;
    public static final int HEIGHT = 280;

    public static final int TAB_TREE = 0;
    public static final int TAB_ORELINE = 1;
    public static final int TAB_INFRA = 2;

    public static final Rect HEADER = new Rect(2, 2, WIDTH - 4, 26);
    public static final Rect CLOSE_BUTTON = new Rect(WIDTH - 20, 8, 14, 14);

    // Top mode tabs (y = 31..47)
    public static final Rect TAB_TREE_RECT = new Rect(8, 31, 132, 16);
    public static final Rect TAB_ORELINE_RECT = new Rect(144, 31, 132, 16);
    public static final Rect TAB_INFRA_RECT = new Rect(280, 31, 132, 16);
    public static final List<Rect> TAB_RECTS = List.of(
            TAB_TREE_RECT, TAB_ORELINE_RECT, TAB_INFRA_RECT);

    // Phase 97: 4-Branch selector tabs inside TAB_TREE (y = 50..66)
    public static final Rect BRANCH_TAB_PRODUCTION = new Rect(8, 50, 99, 16);
    public static final Rect BRANCH_TAB_NATURE = new Rect(110, 50, 99, 16);
    public static final Rect BRANCH_TAB_TRAP = new Rect(212, 50, 99, 16);
    public static final Rect BRANCH_TAB_LIFE = new Rect(314, 50, 98, 16);
    public static final List<Rect> BRANCH_TAB_RECTS = List.of(
            BRANCH_TAB_PRODUCTION, BRANCH_TAB_NATURE, BRANCH_TAB_TRAP, BRANCH_TAB_LIFE);

    // Phase 108: MMO Search box & 4 State Filter pills inside TAB_TREE (y = 69..85)
    public static final Rect SEARCH_BOX = new Rect(8, 69, 140, 16);
    public static final Rect SEARCH_CLEAR_BUTTON = new Rect(134, 71, 12, 12);
    public static final Rect FILTER_ALL_RECT = new Rect(152, 69, 62, 16);
    public static final Rect FILTER_UNLOCKABLE_RECT = new Rect(217, 69, 64, 16);
    public static final Rect FILTER_UNLOCKED_RECT = new Rect(284, 69, 62, 16);
    public static final Rect FILTER_LOCKED_RECT = new Rect(349, 69, 63, 16);
    public static final List<Rect> FILTER_RECTS = List.of(
            FILTER_ALL_RECT, FILTER_UNLOCKABLE_RECT, FILTER_UNLOCKED_RECT, FILTER_LOCKED_RECT);

    // Phase 97: Branch progress header strip & Limit Break button (y = 88..104)
    public static final Rect BRANCH_HEADER_BAR = new Rect(8, 88, 250, 16);
    public static final Rect BRANCH_LIMIT_BREAK_BUTTON = new Rect(264, 88, 148, 16);

    // ---- Tab 1: Skill Tree split (viewport 250 : detail 148) ----
    public static final Rect TREE_VIEWPORT = new Rect(8, 107, 250, 165);
    public static final Rect DETAIL_PANEL = new Rect(264, 107, 148, 165);

    // ---- Tab 2 & 3: full-width scrollable guide viewport ----
    public static final Rect GUIDE_VIEWPORT = new Rect(8, 51, 404, 221);
    public static final int SCROLLBAR_WIDTH = 5;

    /** Phase 103.5: Top banner button in TAB_INFRA opening the Advanced Multiblock Reactor Guide modal. */
    public static final Rect INFRA_MULTIBLOCK_BANNER = new Rect(12, 55, 396, 18);
    public static final Rect REACTOR_GUIDE_MODAL = new Rect(14, 16, 392, 248);
    public static final Rect REACTOR_GUIDE_CLOSE = new Rect(388, 22, 12, 12);

    // ---- Virtual tree canvas & node card dimensions ----
    public static final int NODE_W = 54;
    public static final int NODE_H = 26;
    public static final int COL_START_X = 14;
    public static final int COL_STRIDE = 74;
    public static final int CANVAS_W = COL_START_X + 4 * COL_STRIDE + NODE_W + 14; // 378
    public static final int CANVAS_H = TREE_VIEWPORT.height(); // 165 (fits up to 5 rows cleanly!)

    /** Header button placement on AdaptiveContainerScreen (196 px wide machine panels). */
    public static final Rect MACHINE_HEADER_BUTTON = new Rect(173, 8, 14, 14);
    public static final int SYNC_POLL_INTERVAL_TICKS = 20;

    public static boolean shouldPollProgress(int openTicks) {
        return openTicks > 0 && (openTicks % SYNC_POLL_INTERVAL_TICKS) == 0;
    }

    private static final Map<Branch, List<NodeSlot>> SLOTS_BY_BRANCH = buildSlotsByBranch();
    private static final List<NodeSlot> ALL_NODE_SLOTS = flattenSlots();
    private static final Map<String, NodeSlot> NODE_BY_ID = indexNodeSlots();

    private static final List<OreLineStage> ORE_LINE_STAGES = List.of(
            new OreLineStage(1, "crusher", "iron_dust", false, 2, false),
            new OreLineStage(2, "electrolyzer", "pure_iron", false, 4, false),
            new OreLineStage(3, "precision_crusher", "exposed_iron", false, 8, false),
            new OreLineStage(4, "metal_press", "iron_washed", false, 16, false),
            new OreLineStage(5, "crusher", "iron_sorted", false, 32, false),
            new OreLineStage(6, "electrolyzer", "iron_concentrate", false, 64, false),
            new OreLineStage(7, "ore_synthesizer", "iron_refined", false, 128, true),
            new OreLineStage(8, "ore_synthesizer", "iron_separated", false, 256, true),
            new OreLineStage(9, "ore_synthesizer", "iron_high_purity", false, 512, true),
            new OreLineStage(10, "ore_synthesizer", "iron_micronized", false, 1_024, true),
            new OreLineStage(11, "ore_synthesizer", "iron_crystallized", false, 2_048, true),
            new OreLineStage(12, "ore_synthesizer", "iron_homogenized", false, 4_096, true),
            new OreLineStage(13, "ore_synthesizer", "iron_compressed", false, 8_192, true),
            new OreLineStage(14, "ore_synthesizer", "iron_stabilized", false, 16_384, true),
            new OreLineStage(15, "ore_synthesizer", "iron_ingot", true, 32_768, true)
    );

    private static final List<InfraEntry> INFRA_ENTRIES = List.of(
            new InfraEntry("generators_early", "thermal_generator", "thermal"),
            new InfraEntry("generators_early", "hydro_generator", "hydro"),
            new InfraEntry("generators_early", "wind_generator", "wind"),
            new InfraEntry("generators_early", "solar_generator", "solar"),
            new InfraEntry("generators_mid_late", "geothermal_generator", "geothermal"),
            new InfraEntry("generators_mid_late", "combustion_generator", "combustion"),
            new InfraEntry("generators_mid_late", "nuclear_generator", "nuclear"),
            new InfraEntry("recovery_storage", "pressure_recovery", "pressure"),
            new InfraEntry("recovery_storage", "heat_recovery", "heat"),
            new InfraEntry("recovery_storage", "regen_generator", "regen"),
            new InfraEntry("recovery_storage", "flywheel", "flywheel"),
            new InfraEntry("recovery_storage", "energy_buffer", "buffer_mk1"),
            new InfraEntry("recovery_storage", "energy_buffer_mk2", "buffer_mk2"),
            new InfraEntry("recovery_storage", "energy_buffer_mk3", "buffer_mk3"),
            new InfraEntry("cables", "cable_basic", "cable_basic"),
            new InfraEntry("cables", "cable_improved", "cable_improved"),
            new InfraEntry("cables", "cable_advanced", "cable_advanced"),
            new InfraEntry("cables", "cable_ultimate", "cable_ultimate"),
            new InfraEntry("pipes", "item_pipe", "pipe_basic"),
            new InfraEntry("pipes", "pipe_improved", "pipe_improved"),
            new InfraEntry("pipes", "pipe_advanced", "pipe_advanced"),
            new InfraEntry("pipes", "pipe_ultimate", "pipe_ultimate"),
            new InfraEntry("transporters", "basic_item_transporter", "transporter_basic"),
            new InfraEntry("transporters", "advanced_item_transporter", "transporter_advanced"),
            new InfraEntry("transporters", "quantum_transporter", "transporter_quantum"),
            new InfraEntry("transporters", "quantum_transporter_array", "transporter_array"),
            new InfraEntry("transporters", "item_distributor", "item_distributor"),
            new InfraEntry("transporters", "factory_monitor", "factory_monitor")
    );

    private SkillTreeLayout() {}

    private static Map<Branch, List<NodeSlot>> buildSlotsByBranch() {
        Map<Branch, List<NodeSlot>> map = new EnumMap<>(Branch.class);

        map.put(Branch.PRODUCTION, buildBranchSlots(Branch.PRODUCTION, new String[][]{
                {"crusher", "metal_press", "electric_furnace"},
                {"electrolyzer", "quarry", "alloy_furnace", "crafter"},
                {"precision_crusher", "reconstructor", "assembler", "fast_crusher"},
                {"seed_cultivator", "matter_amplifier", "large_quarry", "quantum_importer", "quantum_exporter"},
                {"phase_reactor", "quantum_compressor", "ore_synthesizer"}
        }));

        map.put(Branch.NATURE, buildBranchSlots(Branch.NATURE, new String[][]{
                {"crop_harvester"},
                {"tree_harvester", "auto_fisher"},
                {"ranch_harvester"},
                {"growth_accelerator"},
                {"bio_feeder"}
        }));

        map.put(Branch.TRAP, buildBranchSlots(Branch.TRAP, new String[][]{
                {"mob_fan"},
                {"rotary_drill", "item_vacuum"},
                {"spawn_suppressor", "xp_collector"},
                {"laser_turret"},
                {"mob_duplicator"}
        }));

        map.put(Branch.LIFE, buildBranchSlots(Branch.LIFE, new String[][]{
                {"auto_builder"},
                {"fluid_pump"},
                {"chunk_loader", "wireless_charger"},
                {"climate_controller"},
                {"warp_pad"}
        }));

        return Collections.unmodifiableMap(map);
    }

    private static List<NodeSlot> buildBranchSlots(Branch branch, String[][] columns) {
        int[] y1 = {65};
        int[] y2 = {40, 90};
        int[] y3 = {24, 65, 106};
        int[] y4 = {18, 52, 86, 120};
        int[] y5 = {13, 41, 69, 97, 125};

        List<NodeSlot> list = new ArrayList<>();
        for (int col = 0; col < columns.length; col++) {
            int tier = col + 1;
            String[] ids = columns[col];
            int x = COL_START_X + col * COL_STRIDE;
            int[] ys = switch (ids.length) {
                case 1 -> y1;
                case 2 -> y2;
                case 3 -> y3;
                case 4 -> y4;
                default -> y5;
            };
            for (int row = 0; row < ids.length; row++) {
                list.add(new NodeSlot(ids[row], branch, tier, new Rect(x, ys[row], NODE_W, NODE_H)));
            }
        }
        return List.copyOf(list);
    }

    private static List<NodeSlot> flattenSlots() {
        List<NodeSlot> all = new ArrayList<>();
        for (Branch b : Branch.values()) {
            all.addAll(SLOTS_BY_BRANCH.getOrDefault(b, List.of()));
        }
        return List.copyOf(all);
    }

    private static Map<String, NodeSlot> indexNodeSlots() {
        Map<String, NodeSlot> map = new LinkedHashMap<>();
        for (NodeSlot slot : ALL_NODE_SLOTS) {
            map.put(slot.machineId(), slot);
        }
        return Collections.unmodifiableMap(map);
    }

    /** All 38 node slots across all 4 branches. */
    public static List<NodeSlot> nodeSlots() {
        return ALL_NODE_SLOTS;
    }

    /** Node slots for a single {@code branch}. */
    public static List<NodeSlot> nodeSlots(Branch branch) {
        return branch == null ? List.of() : SLOTS_BY_BRANCH.getOrDefault(branch, List.of());
    }

    @Nullable
    public static NodeSlot slotFor(String machineId) {
        return machineId == null ? null : NODE_BY_ID.get(machineId);
    }

    public static List<OreLineStage> oreLineStages() {
        return ORE_LINE_STAGES;
    }

    public static List<InfraEntry> infraEntries() {
        return INFRA_ENTRIES;
    }

    public static int maxPanX() {
        return Math.max(0, CANVAS_W - TREE_VIEWPORT.width());
    }

    public static int maxPanY() {
        return Math.max(0, CANVAS_H - TREE_VIEWPORT.height());
    }

    public static double clampPanX(double panX) {
        return Math.max(0.0D, Math.min(maxPanX(), panX));
    }

    public static double clampPanY(double panY) {
        return Math.max(0.0D, Math.min(maxPanY(), panY));
    }

    public static double centerPanXFor(String machineId) {
        NodeSlot slot = slotFor(machineId);
        if (slot == null) {
            return 0.0D;
        }
        double target = slot.rect().centerX() - TREE_VIEWPORT.width() / 2.0D;
        return clampPanX(target);
    }

    /** Returns the machineId under panel-local cursor {@code (px, py)} within {@code branch}, or null. */
    @Nullable
    public static String nodeAt(Branch branch, double px, double py, double panX, double panY) {
        if (branch == null || !TREE_VIEWPORT.contains(px, py)) {
            return null;
        }
        double vx = (px - TREE_VIEWPORT.x()) + panX;
        double vy = (py - TREE_VIEWPORT.y()) + panY;
        for (NodeSlot slot : nodeSlots(branch)) {
            if (slot.rect().contains(vx, vy)) {
                return slot.machineId();
            }
        }
        return null;
    }

    /** Legacy convenience overload defaulting to {@link Branch#PRODUCTION}. */
    @Nullable
    public static String nodeAt(double px, double py, double panX, double panY) {
        return nodeAt(Branch.PRODUCTION, px, py, panX, panY);
    }

    public static int tabAt(double px, double py) {
        for (int i = 0; i < TAB_RECTS.size(); i++) {
            if (TAB_RECTS.get(i).contains(px, py)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public static Branch branchTabAt(double px, double py) {
        Branch[] all = Branch.values();
        for (int i = 0; i < BRANCH_TAB_RECTS.size() && i < all.length; i++) {
            if (BRANCH_TAB_RECTS.get(i).contains(px, py)) {
                return all[i];
            }
        }
        return null;
    }

    public static Rect branchTabRect(Branch branch) {
        int idx = branch == null ? 0 : branch.ordinal();
        return BRANCH_TAB_RECTS.get(Math.max(0, Math.min(BRANCH_TAB_RECTS.size() - 1, idx)));
    }

    /** Phase 108: Hit-test for the 4 state filter buttons (ALL / UNLOCKABLE / UNLOCKED / LOCKED). */
    @Nullable
    public static SkillTreeFilterLogic.FilterMode filterAt(double px, double py) {
        SkillTreeFilterLogic.FilterMode[] modes = SkillTreeFilterLogic.FilterMode.values();
        for (int i = 0; i < FILTER_RECTS.size() && i < modes.length; i++) {
            if (FILTER_RECTS.get(i).contains(px, py)) {
                return modes[i];
            }
        }
        return null;
    }

    public static NodeVisualState visualState(MachineSkillTree.Node node,
                                              ToIntFunction<String> levelOf,
                                              boolean branchLimitBroken) {
        if (!MachineSkillTree.isUnlocked(node, levelOf)) {
            return NodeVisualState.LOCKED;
        }
        int lvl = levelOf.applyAsInt(node.machineId());
        if (branchLimitBroken && lvl > MachineLevelMath.NORMAL_MAX_LEVEL) {
            return NodeVisualState.LIMIT_BROKEN;
        }
        int reqAsParent = MachineSkillTree.highestRequiredLevelAsParent(node.machineId());
        if (reqAsParent > MachineLevelMath.MIN_LEVEL && lvl >= reqAsParent) {
            return NodeVisualState.MASTERED;
        }
        if (reqAsParent <= MachineLevelMath.MIN_LEVEL && lvl >= MachineLevelMath.NORMAL_MAX_LEVEL) {
            return NodeVisualState.MASTERED;
        }
        return NodeVisualState.UNLOCKED;
    }

    public static NodeVisibility visibilityOf(@Nullable MachineSkillTree.Node node,
                                              Set<String> revealedNodes) {
        if (node == null) {
            return NodeVisibility.MYSTERY;
        }
        if (node.parents().isEmpty() || revealedNodes.contains(node.machineId())) {
            return NodeVisibility.REVEALED;
        }
        int highestParentTier = 0;
        for (String parentId : node.parents()) {
            MachineSkillTree.Node p = MachineSkillTree.byId(parentId);
            if (p != null && p.tier() > highestParentTier) {
                highestParentTier = p.tier();
            }
        }
        for (String parentId : node.parents()) {
            MachineSkillTree.Node p = MachineSkillTree.byId(parentId);
            if (p != null && p.tier() == highestParentTier
                    && (p.parents().isEmpty() || revealedNodes.contains(parentId))) {
                return NodeVisibility.NEXT_TARGET;
            }
        }
        return NodeVisibility.MYSTERY;
    }

    public static NodeVisibility visibilityOf(@Nullable String machineId, Set<String> revealedNodes) {
        return visibilityOf(MachineSkillTree.byId(machineId), revealedNodes);
    }

    public static List<String> newlyUncoveredChildren(String newlyRevealedId,
                                                      Set<String> beforeRevealed,
                                                      Set<String> afterRevealed) {
        List<String> uncovered = new ArrayList<>();
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            String id = node.machineId();
            if (id.equals(newlyRevealedId)) {
                continue;
            }
            if (visibilityOf(node, beforeRevealed) == NodeVisibility.MYSTERY
                    && visibilityOf(node, afterRevealed) == NodeVisibility.NEXT_TARGET) {
                uncovered.add(id);
            }
        }
        return List.copyOf(uncovered);
    }
}
