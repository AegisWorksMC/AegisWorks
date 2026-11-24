package com.industrialcore.level;

import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.level.MachineSkillTree.Node;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Phase 108: Pure-Java search, state filter, unlockable frontier detection, Stage mapping, and
 * upstream/downstream dependency route extraction for the MMO-style Skill Tree &amp; Guide screen.
 *
 * <p>Zero Minecraft imports so every filter, search query, stage tag, and DAG route query is
 * verified directly by unit tests.</p>
 */
public final class SkillTreeFilterLogic {
    /** State filter buttons shown at the top of the Skill Tree tab. */
    public enum FilterMode {
        ALL("gui.industrialcore.skill_tree.filter.all"),
        UNLOCKABLE("gui.industrialcore.skill_tree.filter.unlockable"),
        UNLOCKED("gui.industrialcore.skill_tree.filter.unlocked"),
        LOCKED("gui.industrialcore.skill_tree.filter.locked");

        private final String translationKey;

        FilterMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    /** Directed DAG edge from prerequisite {@code parentId} to derived {@code childId}. */
    public record DirectedEdge(String parentId, String childId) {}

    /** Complete upstream (prerequisite) and downstream (derived) route sets for a focused node. */
    public record DependencyRoutes(
            String focusId,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<DirectedEdge> upstreamEdges,
            Set<DirectedEdge> downstreamEdges
    ) {
        public static final DependencyRoutes EMPTY =
                new DependencyRoutes("", Set.of(), Set.of(), Set.of(), Set.of());

        public boolean isUpstreamEdge(String parentId, String childId) {
            return upstreamEdges.contains(new DirectedEdge(parentId, childId));
        }

        public boolean isDownstreamEdge(String parentId, String childId) {
            return downstreamEdges.contains(new DirectedEdge(parentId, childId));
        }

        public boolean isRelatedNode(String machineId) {
            return focusId.equals(machineId)
                    || upstreamNodes.contains(machineId)
                    || downstreamNodes.contains(machineId);
        }
    }

    private static final Map<String, List<String>> CHILDREN_BY_PARENT = buildChildrenIndex();

    private SkillTreeFilterLogic() {}

    private static Map<String, List<String>> buildChildrenIndex() {
        Map<String, List<String>> map = new HashMap<>();
        for (Node node : MachineSkillTree.nodes()) {
            for (String parent : node.parents()) {
                map.computeIfAbsent(parent, k -> new ArrayList<>()).add(node.machineId());
            }
        }
        Map<String, List<String>> frozen = new HashMap<>();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /** Direct child machine IDs whose prerequisites include {@code parentId}. */
    public static List<String> directChildrenOf(@Nullable String parentId) {
        if (parentId == null) {
            return List.of();
        }
        return CHILDREN_BY_PARENT.getOrDefault(parentId, List.of());
    }

    /**
     * Maps a skill tree node to the recommended survival progression Stage (1..5).
     */
    public static int stageForNode(@Nullable Node node) {
        if (node == null) {
            return 1;
        }
        return Math.max(1, Math.min(5, node.tier()));
    }

    /**
     * True when {@code node} has all prerequisites satisfied right now AND has not yet accrued
     * active work XP (making it an immediate next actionable node to craft &amp; deploy).
     */
    public static boolean isUnlockableFrontier(@Nullable Node node,
                                               ToIntFunction<String> levelOf,
                                               ToLongFunction<String> xpOf) {
        if (node == null || levelOf == null || xpOf == null) {
            return false;
        }
        return MachineSkillTree.isUnlocked(node, levelOf)
                && xpOf.applyAsLong(node.machineId()) <= 0L;
    }

    /** Evaluates whether {@code node} passes the selected {@link FilterMode}. */
    public static boolean matchesFilter(@Nullable Node node,
                                        FilterMode mode,
                                        ToIntFunction<String> levelOf,
                                        ToLongFunction<String> xpOf) {
        if (node == null) {
            return false;
        }
        FilterMode safeMode = mode == null ? FilterMode.ALL : mode;
        return switch (safeMode) {
            case ALL -> true;
            case UNLOCKABLE -> isUnlockableFrontier(node, levelOf, xpOf);
            case UNLOCKED -> MachineSkillTree.isUnlocked(node, levelOf);
            case LOCKED -> !MachineSkillTree.isUnlocked(node, levelOf);
        };
    }

    /**
     * Case-insensitive keyword matching across localized machine name (JP/EN), internal machine ID
     * (underscores or spaces), localized branch name, and branch ID.
     */
    public static boolean matchesQuery(@Nullable Node node,
                                       @Nullable String rawQuery,
                                       @Nullable String localizedMachineName,
                                       @Nullable String localizedBranchName) {
        if (node == null) {
            return false;
        }
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }
        String needle = rawQuery.trim().toLowerCase(Locale.ROOT);
        String idLower = node.machineId().toLowerCase(Locale.ROOT);
        if (idLower.contains(needle) || idLower.replace('_', ' ').contains(needle)) {
            return true;
        }
        if (localizedMachineName != null && localizedMachineName.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (localizedBranchName != null && localizedBranchName.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (node.branch().id().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        String stageToken = "stage " + stageForNode(node);
        String stageCompact = "stage" + stageForNode(node);
        return stageToken.contains(needle) || stageCompact.contains(needle);
    }

    /** Combined search + filter check for a node. */
    public static boolean matches(@Nullable Node node,
                                  @Nullable String rawQuery,
                                  FilterMode mode,
                                  ToIntFunction<String> levelOf,
                                  ToLongFunction<String> xpOf,
                                  @Nullable String localizedMachineName,
                                  @Nullable String localizedBranchName) {
        return matchesFilter(node, mode, levelOf, xpOf)
                && matchesQuery(node, rawQuery, localizedMachineName, localizedBranchName);
    }

    /** Counts matching nodes in every branch (useful for showing match count badges on branch tabs). */
    public static Map<Branch, Integer> countMatchesByBranch(@Nullable String rawQuery,
                                                            FilterMode mode,
                                                            ToIntFunction<String> levelOf,
                                                            ToLongFunction<String> xpOf,
                                                            Function<Node, String> localizedMachineName,
                                                            Function<Branch, String> localizedBranchName) {
        Map<Branch, Integer> counts = new EnumMap<>(Branch.class);
        for (Branch b : Branch.values()) {
            int c = 0;
            String bName = localizedBranchName != null ? localizedBranchName.apply(b) : b.id();
            for (Node n : MachineSkillTree.nodesInBranch(b)) {
                String mName = localizedMachineName != null ? localizedMachineName.apply(n) : n.machineId();
                if (matches(n, rawQuery, mode, levelOf, xpOf, mName, bName)) {
                    c++;
                }
            }
            counts.put(b, c);
        }
        return counts;
    }

    /**
     * Extracts ALL upstream prerequisite nodes/edges (ancestors) and ALL downstream derived
     * nodes/edges (descendants) for {@code focusMachineId}.
     */
    public static DependencyRoutes extractRoutes(@Nullable String focusMachineId) {
        if (focusMachineId == null || !MachineSkillTree.isTracked(focusMachineId)) {
            return DependencyRoutes.EMPTY;
        }
        Set<String> upNodes = new LinkedHashSet<>();
        Set<DirectedEdge> upEdges = new LinkedHashSet<>();
        ArrayDeque<String> upQ = new ArrayDeque<>();
        Set<String> upVisited = new HashSet<>();
        upQ.add(focusMachineId);
        upVisited.add(focusMachineId);

        while (!upQ.isEmpty()) {
            String curId = upQ.poll();
            Node cur = MachineSkillTree.byId(curId);
            if (cur == null) {
                continue;
            }
            for (String parentId : cur.parents()) {
                upEdges.add(new DirectedEdge(parentId, curId));
                upNodes.add(parentId);
                if (upVisited.add(parentId)) {
                    upQ.add(parentId);
                }
            }
        }

        Set<String> downNodes = new LinkedHashSet<>();
        Set<DirectedEdge> downEdges = new LinkedHashSet<>();
        ArrayDeque<String> downQ = new ArrayDeque<>();
        Set<String> downVisited = new HashSet<>();
        downQ.add(focusMachineId);
        downVisited.add(focusMachineId);

        while (!downQ.isEmpty()) {
            String curId = downQ.poll();
            for (String childId : directChildrenOf(curId)) {
                downEdges.add(new DirectedEdge(curId, childId));
                downNodes.add(childId);
                if (downVisited.add(childId)) {
                    downQ.add(childId);
                }
            }
        }

        return new DependencyRoutes(
                focusMachineId,
                Collections.unmodifiableSet(upNodes),
                Collections.unmodifiableSet(downNodes),
                Collections.unmodifiableSet(upEdges),
                Collections.unmodifiableSet(downEdges)
        );
    }
}
