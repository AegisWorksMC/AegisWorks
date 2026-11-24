package com.industrialcore.level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Shared 4-branch, 38-machine skill tree definition for Industrial Core (Phase 84–97).
 *
 * <ul>
 *   <li>{@link Branch#PRODUCTION} (19 nodes): original 17 electric machines + Phase 96 Quantum IO (2)</li>
 *   <li>{@link Branch#NATURE} (6 nodes): Agriculture &amp; Forestry wave 1–2</li>
 *   <li>{@link Branch#TRAP} (7 nodes): Mob Control &amp; Trap wave 1–2</li>
 *   <li>{@link Branch#LIFE} (6 nodes): Building &amp; Life wave 1–2</li>
 * </ul>
 *
 * <p>Every branch has Tier 1 roots (immediately craftable at Lv.1) and progresses through Tier 2–5
 * prerequisites. Reaching Lv.99 on all unlocked nodes in a branch unlocks that branch's explicit
 * Lv.1000 Limit Break.</p>
 */
public final class MachineSkillTree {

    public enum Branch {
        PRODUCTION("production", "gui.industrialcore.skill_tree.branch.production",
                0xFF38D4FF, 0xFF9BE6FF, 0xFF174E63),
        NATURE("nature", "gui.industrialcore.skill_tree.branch.nature",
                0xFF3EE898, 0xFFA6FFD5, 0xFF165237),
        TRAP("trap", "gui.industrialcore.skill_tree.branch.trap",
                0xFFFF5C77, 0xFFFFB0BD, 0xFF611D2B),
        LIFE("life", "gui.industrialcore.skill_tree.branch.life",
                0xFFFFBE44, 0xFFFFE59E, 0xFF5E4315);

        private final String id;
        private final String translationKey;
        private final int accentColor;
        private final int brightColor;
        private final int dimColor;

        Branch(String id, String translationKey, int accentColor, int brightColor, int dimColor) {
            this.id = id;
            this.translationKey = translationKey;
            this.accentColor = accentColor;
            this.brightColor = brightColor;
            this.dimColor = dimColor;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public int accentColor() {
            return accentColor;
        }

        public int brightColor() {
            return brightColor;
        }

        public int dimColor() {
            return dimColor;
        }

        @Nullable
        public static Branch byId(@Nullable String id) {
            if (id == null || id.isEmpty()) {
                return null;
            }
            for (Branch b : values()) {
                if (b.id.equalsIgnoreCase(id) || b.name().equalsIgnoreCase(id)) {
                    return b;
                }
            }
            return null;
        }
    }

    /**
     * One node in the 4-branch machine mastery tree.
     *
     * @param machineId      block registry path (e.g. {@code "crusher"})
     * @param translationKey lang key for the machine display name
     * @param branch         skill tree branch this machine belongs to
     * @param tier           visual progression column (1..5)
     * @param requiredLevel  minimum level required on every {@code parent} to unlock crafting
     * @param parents        prerequisite machine ids (empty for Tier 1 roots)
     */
    public record Node(
            String machineId,
            String translationKey,
            Branch branch,
            int tier,
            int requiredLevel,
            List<String> parents
    ) {
        public Node {
            parents = List.copyOf(parents);
        }

        /** Lang key for the beginner guide / recommended-line hint shown in the Skill Tree GUI. */
        public String guideKey() {
            return "gui.industrialcore.skill_tree.guide." + machineId;
        }

        /** True for machines with a motor upgrade slot whose stack cap scales with machine level. */
        public boolean hasMotorSlot() {
            return true;
        }

        /** True for machines with a battery upgrade slot whose stack cap scales with machine level. */
        public boolean hasBatterySlot() {
            return true;
        }
    }

    private static final List<Node> ALL_NODES = List.of(
            // =====================================================================
            // Branch A: PRODUCTION (19 nodes = original 17 + Quantum Importer/Exporter)
            // =====================================================================
            node("crusher", Branch.PRODUCTION, 1, 1),
            node("electric_furnace", Branch.PRODUCTION, 1, 1),
            node("metal_press", Branch.PRODUCTION, 1, 1),

            node("alloy_furnace", Branch.PRODUCTION, 2, 3, "metal_press"),
            node("electrolyzer", Branch.PRODUCTION, 2, 3, "crusher"),
            node("crafter", Branch.PRODUCTION, 2, 4, "metal_press"),
            node("quarry", Branch.PRODUCTION, 2, 5, "crusher", "metal_press"),

            node("assembler", Branch.PRODUCTION, 3, 6, "alloy_furnace"),
            node("precision_crusher", Branch.PRODUCTION, 3, 6, "electrolyzer"),
            node("fast_crusher", Branch.PRODUCTION, 3, 8, "crusher", "assembler"),
            node("reconstructor", Branch.PRODUCTION, 3, 8, "electrolyzer"),

            node("seed_cultivator", Branch.PRODUCTION, 4, 10, "electrolyzer", "assembler"),
            node("matter_amplifier", Branch.PRODUCTION, 4, 12, "seed_cultivator", "reconstructor"),
            node("large_quarry", Branch.PRODUCTION, 4, 12, "quarry", "assembler"),
            node("quantum_importer", Branch.PRODUCTION, 4, 8, "assembler"),
            node("quantum_exporter", Branch.PRODUCTION, 4, 8, "assembler"),

            node("phase_reactor", Branch.PRODUCTION, 5, 18, "large_quarry", "assembler"),
            node("quantum_compressor", Branch.PRODUCTION, 5, 20, "phase_reactor", "fast_crusher"),
            node("ore_synthesizer", Branch.PRODUCTION, 5, 25,
                    "phase_reactor", "quantum_compressor", "matter_amplifier", "crafter"),

            // =====================================================================
            // Branch B: NATURE (6 nodes: Agriculture & Forestry)
            // =====================================================================
            node("crop_harvester", Branch.NATURE, 1, 1),
            node("tree_harvester", Branch.NATURE, 2, 3, "crop_harvester"),
            node("auto_fisher", Branch.NATURE, 2, 3, "crop_harvester"),
            node("ranch_harvester", Branch.NATURE, 3, 5, "tree_harvester"),
            node("growth_accelerator", Branch.NATURE, 4, 8, "ranch_harvester"),
            node("bio_feeder", Branch.NATURE, 5, 12, "growth_accelerator", "auto_fisher"),

            // =====================================================================
            // Branch C: TRAP (7 nodes: Mob Control & Traps)
            // =====================================================================
            node("mob_fan", Branch.TRAP, 1, 1),
            node("rotary_drill", Branch.TRAP, 2, 3, "mob_fan"),
            node("item_vacuum", Branch.TRAP, 2, 3, "mob_fan"),
            node("xp_collector", Branch.TRAP, 3, 5, "item_vacuum"),
            node("spawn_suppressor", Branch.TRAP, 3, 5, "rotary_drill"),
            node("laser_turret", Branch.TRAP, 4, 8, "rotary_drill", "spawn_suppressor"),
            node("mob_duplicator", Branch.TRAP, 5, 12, "laser_turret", "xp_collector"),

            // =====================================================================
            // Branch D: LIFE (6 nodes: Building & Daily Convenience)
            // =====================================================================
            node("auto_builder", Branch.LIFE, 1, 1),
            node("fluid_pump", Branch.LIFE, 2, 3, "auto_builder"),
            node("wireless_charger", Branch.LIFE, 3, 5, "fluid_pump"),
            node("chunk_loader", Branch.LIFE, 3, 5, "fluid_pump"),
            node("climate_controller", Branch.LIFE, 4, 8, "chunk_loader"),
            node("warp_pad", Branch.LIFE, 5, 12, "climate_controller", "wireless_charger")
    );

    private static final Map<String, Node> BY_ID = buildIndex();
    private static final Map<Branch, List<Node>> BY_BRANCH = buildBranchIndex();

    private MachineSkillTree() {}

    private static Node node(String id, Branch branch, int tier, int requiredLevel, String... parents) {
        return new Node(id, "block.industrialcore." + id, branch, tier, requiredLevel, List.of(parents));
    }

    private static Map<String, Node> buildIndex() {
        Map<String, Node> map = new LinkedHashMap<>();
        for (Node n : ALL_NODES) {
            map.put(n.machineId(), n);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Branch, List<Node>> buildBranchIndex() {
        Map<Branch, List<Node>> map = new EnumMap<>(Branch.class);
        for (Branch b : Branch.values()) {
            List<Node> list = new ArrayList<>();
            for (Node n : ALL_NODES) {
                if (n.branch() == b) {
                    list.add(n);
                }
            }
            map.put(b, List.copyOf(list));
        }
        return Collections.unmodifiableMap(map);
    }

    /** Ordered list of all 38 machine nodes across all 4 branches. */
    public static List<Node> nodes() {
        return ALL_NODES;
    }

    /** Ordered list of nodes belonging to {@code branch}. */
    public static List<Node> nodesInBranch(Branch branch) {
        if (branch == null) {
            return List.of();
        }
        return BY_BRANCH.getOrDefault(branch, List.of());
    }

    /** Total number of machine nodes in the tree (38). */
    public static int totalNodes() {
        return ALL_NODES.size();
    }

    /** Lookup by machine id, or {@code null} if not part of the tree. */
    @Nullable
    public static Node byId(String machineId) {
        return machineId == null ? null : BY_ID.get(machineId);
    }

    /** Branch containing {@code machineId}, or {@code null} if not tracked. */
    @Nullable
    public static Branch branchOf(String machineId) {
        Node n = byId(machineId);
        return n == null ? null : n.branch();
    }

    /** True when {@code machineId} participates in the machine level system. */
    public static boolean isTracked(String machineId) {
        return machineId != null && BY_ID.containsKey(machineId);
    }

    /**
     * True when every prerequisite parent of {@code node} has reached at least
     * {@code node.requiredLevel()}. Root nodes (no parents) are always unlocked.
     */
    public static boolean isUnlocked(Node node, ToIntFunction<String> levelOf) {
        if (node == null) {
            return false;
        }
        for (String parent : node.parents()) {
            if (levelOf.applyAsInt(parent) < node.requiredLevel()) {
                return false;
            }
        }
        return true;
    }

    /** Convenience overload looking up the node by id. Unknown ids return true (not gated). */
    public static boolean isUnlocked(String machineId, ToIntFunction<String> levelOf) {
        Node node = byId(machineId);
        return node == null || isUnlocked(node, levelOf);
    }

    /** Number of tree nodes across all 4 branches whose unlock prerequisites are satisfied. */
    public static int unlockedCount(ToIntFunction<String> levelOf) {
        int count = 0;
        for (Node node : ALL_NODES) {
            if (isUnlocked(node, levelOf)) {
                count++;
            }
        }
        return count;
    }

    /** Number of unlocked nodes in {@code branch}. */
    public static int unlockedCountInBranch(Branch branch, ToIntFunction<String> levelOf) {
        int count = 0;
        for (Node node : nodesInBranch(branch)) {
            if (isUnlocked(node, levelOf)) {
                count++;
            }
        }
        return count;
    }

    /** Number of nodes in {@code branch} that have reached Lv.99 or above. */
    public static int maxedCountInBranch(Branch branch, ToIntFunction<String> levelOf) {
        int count = 0;
        for (Node node : nodesInBranch(branch)) {
            if (isUnlocked(node, levelOf)
                    && levelOf.applyAsInt(node.machineId()) >= MachineLevelMath.NORMAL_MAX_LEVEL) {
                count++;
            }
        }
        return count;
    }

    /**
     * Phase 97: True when EVERY node in {@code branch} is unlocked AND has reached at least Lv.99,
     * making that branch eligible to activate Lv.1000 Limit Break.
     */
    public static boolean canLimitBreakBranch(Branch branch, ToIntFunction<String> levelOf) {
        List<Node> list = nodesInBranch(branch);
        if (list.isEmpty()) {
            return false;
        }
        for (Node node : list) {
            if (!isUnlocked(node, levelOf)
                    || levelOf.applyAsInt(node.machineId()) < MachineLevelMath.NORMAL_MAX_LEVEL) {
                return false;
            }
        }
        return true;
    }

    /** True when every node across all 4 branches has its unlock condition satisfied. */
    public static boolean isAllCleared(ToIntFunction<String> levelOf) {
        for (Node node : ALL_NODES) {
            if (!isUnlocked(node, levelOf)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Minimum level required on {@code machineId} across all child nodes that depend on it,
     * or {@code 1} for leaf nodes.
     */
    public static int highestRequiredLevelAsParent(String machineId) {
        int req = MachineLevelMath.MIN_LEVEL;
        for (Node node : ALL_NODES) {
            if (node.parents().contains(machineId)) {
                req = Math.max(req, node.requiredLevel());
            }
        }
        return req;
    }
}
