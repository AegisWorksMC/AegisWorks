package com.industrialcore.level;

import com.industrialcore.level.MachineSkillTree.Branch;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player machine mastery state (pure Java, no Minecraft imports).
 *
 * <p>Phase 97 extends limit break from a single boolean to per-branch {@link Branch} flags:
 * each branch independently transitions its machines' level cap from Lv.99 to Lv.1000 once
 * all nodes in that branch are unlocked and reach Lv.99+, while remaining 100% backwards-compatible
 * with pre-Phase 97 save snapshots.</p>
 */
public final class PlayerMachineProgress {
    /** Outcome of an XP gain or level change. */
    public record ChangeResult(
            int oldLevel,
            int newLevel,
            boolean leveledUp,
            boolean justLimitBroke,
            long newXp
    ) {}

    private final Map<String, Long> xpByMachine = new LinkedHashMap<>();
    private final Set<String> revealedNodes = new LinkedHashSet<>();
    private final EnumSet<Branch> limitBrokenBranches = EnumSet.noneOf(Branch.class);
    /** Phase 116: Highest unlocked Dimensional Rift Raid depth tier (1..10). */
    private int unlockedRiftTier = com.industrialcore.raid.DimensionalRaidLogic.MIN_TIER;

    public PlayerMachineProgress() {
        seedStarterReveals();
    }

    private void seedStarterReveals() {
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            if (node.parents().isEmpty()) {
                revealedNodes.add(node.machineId());
            }
        }
    }

    /** True if {@code branch} has activated Lv.1000 Limit Break. */
    public boolean isBranchLimitBroken(@Nullable Branch branch) {
        return branch != null && limitBrokenBranches.contains(branch);
    }

    /** True if {@code machineId}'s branch has activated Lv.1000 Limit Break. */
    public boolean isMachineLimitBroken(@Nullable String machineId) {
        Branch b = MachineSkillTree.branchOf(machineId);
        if (b != null) {
            return limitBrokenBranches.contains(b);
        }
        return !limitBrokenBranches.isEmpty();
    }

    /** True if at least one branch has activated Limit Break. */
    public boolean isLimitBroken() {
        return !limitBrokenBranches.isEmpty();
    }

    /** Immutable snapshot of limit-broken branch ids (lowercase) for NBT and S2C sync. */
    public Set<String> snapshotLimitBrokenBranchIds() {
        Set<String> out = new LinkedHashSet<>();
        for (Branch b : limitBrokenBranches) {
            out.add(b.id());
        }
        return Collections.unmodifiableSet(out);
    }

    public Set<Branch> snapshotLimitBrokenBranches() {
        return Collections.unmodifiableSet(EnumSet.copyOf(limitBrokenBranches));
    }

    /**
     * True when {@code branch} has not yet activated Limit Break and EVERY node in {@code branch}
     * is unlocked and at Lv.99+.
     */
    public boolean canLimitBreakBranch(@Nullable Branch branch) {
        if (branch == null || limitBrokenBranches.contains(branch)) {
            return false;
        }
        return MachineSkillTree.canLimitBreakBranch(branch, this::getLevel);
    }

    /**
     * Server-authoritative activation of per-branch Lv.1000 Limit Break.
     *
     * @return {@code true} if the branch met all requirements and just transitioned to Lv.1000 cap
     */
    public boolean tryLimitBreakBranch(@Nullable Branch branch) {
        if (!canLimitBreakBranch(branch)) {
            return false;
        }
        limitBrokenBranches.add(branch);
        return true;
    }

    /** Explicitly toggles a single branch's limit-break state (clamps XP to Lv.99 when disabled). */
    public void setBranchLimitBroken(Branch branch, boolean value) {
        if (branch == null) {
            return;
        }
        if (value) {
            limitBrokenBranches.add(branch);
        } else {
            limitBrokenBranches.remove(branch);
            long cap = MachineLevelMath.maxXp(false);
            for (MachineSkillTree.Node node : MachineSkillTree.nodesInBranch(branch)) {
                Long cur = xpByMachine.get(node.machineId());
                if (cur != null && cur > cap) {
                    xpByMachine.put(node.machineId(), cap);
                }
            }
        }
    }

    /**
     * Legacy / admin global limit-break setter: enabling unlocks all 4 branches; disabling resets
     * all branches and clamps stored XP above Lv.99 back cleanly.
     */
    public void setLimitBroken(boolean value) {
        if (value) {
            limitBrokenBranches.addAll(EnumSet.allOf(Branch.class));
        } else {
            limitBrokenBranches.clear();
            long cap = MachineLevelMath.maxXp(false);
            for (Map.Entry<String, Long> entry : xpByMachine.entrySet()) {
                if (entry.getValue() > cap) {
                    entry.setValue(cap);
                }
            }
        }
    }

    public int getUnlockedRiftTier() {
        return unlockedRiftTier;
    }

    public void setUnlockedRiftTier(int tier) {
        this.unlockedRiftTier = com.industrialcore.raid.DimensionalRaidLogic.clampTier(tier);
    }

    /** Advances unlocked Rift Tier if {@code clearedTier} equals or exceeds the current max. */
    public boolean recordRiftTierCleared(int clearedTier) {
        int next = com.industrialcore.raid.DimensionalRaidLogic.nextUnlockedTier(unlockedRiftTier, clearedTier);
        if (next > unlockedRiftTier) {
            unlockedRiftTier = next;
            return true;
        }
        return false;
    }

    public long getXp(String machineId) {
        if (machineId == null) {
            return 0L;
        }
        Long stored = xpByMachine.get(machineId);
        return stored == null ? 0L : MachineLevelMath.clampXp(stored, isMachineLimitBroken(machineId));
    }

    public int getLevel(String machineId) {
        boolean lb = isMachineLimitBroken(machineId);
        return MachineLevelMath.levelForXp(getXp(machineId), lb);
    }

    /** Max reachable level for {@code machineId}'s branch (99 or 1000). */
    public int getMaxLevel(@Nullable String machineId) {
        return MachineLevelMath.maxLevel(isMachineLimitBroken(machineId));
    }

    /** Legacy global max level (1000 if any branch is limit-broken, else 99). */
    public int getMaxLevel() {
        return MachineLevelMath.maxLevel(isLimitBroken());
    }

    public int getUpgradeLimit(String machineId) {
        return MachineLevelMath.upgradeLimitForLevel(getLevel(machineId));
    }

    /** Adds {@code delta} XP to {@code machineId}, enforcing its branch's current level cap (99 or 1000). */
    public ChangeResult addXp(String machineId, long delta) {
        if (machineId == null || machineId.isEmpty() || delta <= 0L) {
            int cur = getLevel(machineId);
            return new ChangeResult(cur, cur, false, false, getXp(machineId));
        }
        boolean lb = isMachineLimitBroken(machineId);
        long beforeXp = getXp(machineId);
        int oldLevel = MachineLevelMath.levelForXp(beforeXp, lb);
        long nextXp = MachineLevelMath.addXp(beforeXp, delta, lb);
        if (nextXp > 0L) {
            xpByMachine.put(machineId, nextXp);
        }
        int newLevel = MachineLevelMath.levelForXp(nextXp, lb);
        return new ChangeResult(oldLevel, newLevel, newLevel > oldLevel, false, nextXp);
    }

    /** Sets {@code machineId} directly to {@code targetLevel} (clamped to its branch's current max level). */
    public ChangeResult setLevel(String machineId, int targetLevel) {
        if (machineId == null || machineId.isEmpty()) {
            return new ChangeResult(1, 1, false, false, 0L);
        }
        int oldLevel = getLevel(machineId);
        int clamped = Math.max(MachineLevelMath.MIN_LEVEL, Math.min(getMaxLevel(machineId), targetLevel));
        long targetXp = MachineLevelMath.xpForLevel(clamped);
        if (targetXp <= 0L) {
            xpByMachine.remove(machineId);
        } else {
            xpByMachine.put(machineId, targetXp);
        }
        return new ChangeResult(oldLevel, clamped, clamped > oldLevel, false, targetXp);
    }

    /** Clears all machine XP, resets limit-break state, and resets revealed nodes to Tier 1 starters. */
    public void reset() {
        xpByMachine.clear();
        limitBrokenBranches.clear();
        revealedNodes.clear();
        unlockedRiftTier = com.industrialcore.raid.DimensionalRaidLogic.MIN_TIER;
        seedStarterReveals();
    }

    public boolean isNodeRevealed(String machineId) {
        return machineId != null && revealedNodes.contains(machineId);
    }

    public Set<String> snapshotRevealedNodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(revealedNodes));
    }

    /** Newly unlocked nodes across all branches whose reveal animation has not yet played, in Tier order. */
    public List<String> pendingReveals() {
        List<String> pending = new ArrayList<>();
        for (int tier = 1; tier <= 5; tier++) {
            for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
                if (node.tier() != tier) {
                    continue;
                }
                String id = node.machineId();
                if (!revealedNodes.contains(id) && MachineSkillTree.isUnlocked(node, this::getLevel)) {
                    pending.add(id);
                }
            }
        }
        return List.copyOf(pending);
    }

    /** Pending reveal ids restricted to a single {@code branch}. */
    public List<String> pendingRevealsInBranch(Branch branch) {
        List<String> pending = new ArrayList<>();
        for (MachineSkillTree.Node node : MachineSkillTree.nodesInBranch(branch)) {
            String id = node.machineId();
            if (!revealedNodes.contains(id) && MachineSkillTree.isUnlocked(node, this::getLevel)) {
                pending.add(id);
            }
        }
        return List.copyOf(pending);
    }

    public boolean markRevealed(@Nullable Collection<String> machineIds) {
        if (machineIds == null || machineIds.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (String id : machineIds) {
            MachineSkillTree.Node node = MachineSkillTree.byId(id);
            if (node != null && MachineSkillTree.isUnlocked(node, this::getLevel)) {
                if (revealedNodes.add(id)) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    public Map<String, Long> snapshotXp() {
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : xpByMachine.entrySet()) {
            long val = MachineLevelMath.clampXp(entry.getValue(), isMachineLimitBroken(entry.getKey()));
            if (val > 0L) {
                copy.put(entry.getKey(), val);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Legacy overload (pre-Phase 86 save compat). */
    public void loadFrom(boolean limitBroken, Map<String, Long> xpMap) {
        loadFrom(limitBroken, xpMap, null, null);
    }

    /** Legacy overload (pre-Phase 97 save compat). */
    public void loadFrom(boolean limitBroken, Map<String, Long> xpMap,
                         @Nullable Collection<String> revealed) {
        loadFrom(limitBroken, xpMap, revealed, null);
    }

    /**
     * Full loader supporting per-branch limit-break sets while preserving pre-Phase 97 save
     * compatibility: when {@code branchIds} is {@code null} and legacy {@code limitBroken == true},
     * all branches that had legacy progress ({@link Branch#PRODUCTION}) are restored as limit broken.
     */
    public void loadFrom(boolean legacyLimitBroken,
                         Map<String, Long> xpMap,
                         @Nullable Collection<String> revealed,
                         @Nullable Collection<String> branchIds) {
        this.xpByMachine.clear();
        this.limitBrokenBranches.clear();

        if (branchIds != null) {
            for (String bid : branchIds) {
                Branch b = Branch.byId(bid);
                if (b != null) {
                    this.limitBrokenBranches.add(b);
                }
            }
        } else if (legacyLimitBroken) {
            this.limitBrokenBranches.addAll(EnumSet.allOf(Branch.class));
        }

        if (xpMap != null) {
            for (Map.Entry<String, Long> entry : xpMap.entrySet()) {
                String id = entry.getKey();
                Long raw = entry.getValue();
                if (id != null && !id.isEmpty() && raw != null && raw > 0L) {
                    long clamped = MachineLevelMath.clampXp(raw, isMachineLimitBroken(id));
                    if (clamped > 0L) {
                        this.xpByMachine.put(id, clamped);
                    }
                }
            }
        }

        this.revealedNodes.clear();
        seedStarterReveals();
        if (revealed == null) {
            for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
                if (MachineSkillTree.isUnlocked(node, this::getLevel)) {
                    this.revealedNodes.add(node.machineId());
                }
            }
        } else {
            for (String id : revealed) {
                if (MachineSkillTree.isTracked(id)) {
                    this.revealedNodes.add(id);
                }
            }
        }
    }

    public static PlayerMachineProgress fromSnapshot(boolean limitBroken, Map<String, Long> xpMap) {
        return fromSnapshot(limitBroken, xpMap, null, null);
    }

    public static PlayerMachineProgress fromSnapshot(boolean limitBroken, Map<String, Long> xpMap,
                                                     @Nullable Collection<String> revealed) {
        return fromSnapshot(limitBroken, xpMap, revealed, null);
    }

    public static PlayerMachineProgress fromSnapshot(boolean limitBroken, Map<String, Long> xpMap,
                                                     @Nullable Collection<String> revealed,
                                                     @Nullable Collection<String> branchIds) {
        PlayerMachineProgress progress = new PlayerMachineProgress();
        progress.loadFrom(limitBroken, xpMap, revealed, branchIds);
        return progress;
    }
}
