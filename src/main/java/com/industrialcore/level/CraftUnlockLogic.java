package com.industrialcore.level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Phase 97: Pure-Java craft-unlock gating rules for Industrial Core machines.
 *
 * <ul>
 *   <li>Tier 1 starter nodes (no parents) are always craftable so early progression never stalls.</li>
 *   <li>Tier 2–5 machines require every prerequisite parent node in {@link MachineSkillTree} to
 *       reach the node's {@code requiredLevel}.</li>
 *   <li>Creative-mode players and servers with the config toggle disabled bypass the gate cleanly.</li>
 * </ul>
 */
public final class CraftUnlockLogic {

    public record MissingPrereq(String parentId, int requiredLevel, int currentLevel) {}

    public record CraftCheckResult(
            boolean allowed,
            @Nullable String machineId,
            List<MissingPrereq> missing
    ) {
        public static final CraftCheckResult ALLOWED = new CraftCheckResult(true, null, List.of());

        @Nullable
        public MissingPrereq firstMissing() {
            return missing.isEmpty() ? null : missing.get(0);
        }
    }

    private CraftUnlockLogic() {}

    /**
     * Evaluates whether crafting the item with registry path {@code itemPath} is permitted.
     *
     * @param itemPath    registry path of the crafted item (only gated if in {@link MachineSkillTree})
     * @param levelOf     function returning the player's current level for a machine id
     * @param creative    true if the player is in Creative mode (always allowed)
     * @param gateEnabled true when server config enables craft unlock gating
     */
    public static CraftCheckResult evaluate(@Nullable String itemPath,
                                            ToIntFunction<String> levelOf,
                                            boolean creative,
                                            boolean gateEnabled) {
        if (!gateEnabled || creative || itemPath == null || itemPath.isEmpty()) {
            return CraftCheckResult.ALLOWED;
        }
        MachineSkillTree.Node node = MachineSkillTree.byId(itemPath);
        if (node == null || node.parents().isEmpty()) {
            return CraftCheckResult.ALLOWED;
        }
        List<MissingPrereq> missing = missingPrerequisites(node, levelOf);
        if (missing.isEmpty()) {
            return CraftCheckResult.ALLOWED;
        }
        return new CraftCheckResult(false, itemPath, missing);
    }

    /** Returns all unmet prerequisite parents for {@code machineId} (empty when unlocked or untracked). */
    public static List<MissingPrereq> missingPrerequisites(@Nullable String machineId,
                                                           ToIntFunction<String> levelOf) {
        MachineSkillTree.Node node = MachineSkillTree.byId(machineId);
        if (node == null || node.parents().isEmpty()) {
            return List.of();
        }
        return missingPrerequisites(node, levelOf);
    }

    private static List<MissingPrereq> missingPrerequisites(MachineSkillTree.Node node,
                                                            ToIntFunction<String> levelOf) {
        List<MissingPrereq> missing = new ArrayList<>();
        int req = node.requiredLevel();
        for (String parentId : node.parents()) {
            int cur = levelOf.applyAsInt(parentId);
            if (cur < req) {
                missing.add(new MissingPrereq(parentId, req, cur));
            }
        }
        return List.copyOf(missing);
    }
}
