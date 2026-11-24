package com.industrialcore.client;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.level.CraftUnlockLogic;
import com.industrialcore.level.CraftUnlockLogic.MissingPrereq;
import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.network.MachineProgressSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Client-side mirror of the local player's {@link PlayerMachineProgress} (Phase 84–97).
 */
public final class ClientMachineProgress {
    private static final PlayerMachineProgress PROGRESS = new PlayerMachineProgress();
    private static boolean initialized = false;

    private ClientMachineProgress() {}

    public static void apply(MachineProgressSyncPayload payload) {
        boolean wasInit = initialized;
        Set<Branch> oldLimitBranches = Set.copyOf(PROGRESS.snapshotLimitBrokenBranches());
        int[] oldLevels = null;
        Set<String> oldPending = null;
        List<MachineSkillTree.Node> nodes = MachineSkillTree.nodes();
        if (wasInit) {
            oldLevels = new int[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                oldLevels[i] = PROGRESS.getLevel(nodes.get(i).machineId());
            }
            oldPending = new HashSet<>(PROGRESS.pendingReveals());
        }

        PROGRESS.loadFrom(payload.limitBroken(), payload.xpByMachine(),
                payload.revealedNodes(), payload.limitBrokenBranches());
        PROGRESS.setUnlockedRiftTier(payload.unlockedRiftTier());
        initialized = true;

        if (!wasInit) {
            return;
        }

        boolean newBranchBroke = false;
        for (Branch b : PROGRESS.snapshotLimitBrokenBranches()) {
            if (!oldLimitBranches.contains(b)) {
                newBranchBroke = true;
                break;
            }
        }
        if (newBranchBroke) {
            LevelToastOverlay.notifyLimitBreak();
        }

        int newlyPending = 0;
        for (String id : PROGRESS.pendingReveals()) {
            if (!oldPending.contains(id)) {
                newlyPending++;
            }
        }
        if (newlyPending > 0) {
            LevelToastOverlay.notifyNewUnlock(newlyPending);
        }

        String primaryId = null;
        int primaryLevel = 0;
        int leveledCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            String id = nodes.get(i).machineId();
            int newLvl = PROGRESS.getLevel(id);
            if (newLvl > oldLevels[i]) {
                leveledCount++;
                if (primaryId == null || newLvl > primaryLevel) {
                    primaryId = id;
                    primaryLevel = newLvl;
                }
            }
        }
        if (primaryId != null) {
            LevelToastOverlay.notifyLevelUp(primaryId, primaryLevel, PROGRESS.getMaxLevel(primaryId), leveledCount - 1);
        }
    }

    public static void reset() {
        PROGRESS.reset();
        initialized = false;
        LevelToastOverlay.clear();
    }

    public static int unlockedRiftTier() {
        return PROGRESS.getUnlockedRiftTier();
    }

    public static Set<String> revealedNodes() {
        return PROGRESS.snapshotRevealedNodes();
    }

    public static boolean isNodeRevealed(String machineId) {
        return PROGRESS.isNodeRevealed(machineId);
    }

    public static List<String> pendingReveals() {
        return PROGRESS.pendingReveals();
    }

    public static List<String> pendingRevealsInBranch(Branch branch) {
        return PROGRESS.pendingRevealsInBranch(branch);
    }

    public static boolean hasPendingReveals() {
        return !PROGRESS.pendingReveals().isEmpty();
    }

    public static void markRevealedLocally(Collection<String> machineIds) {
        PROGRESS.markRevealed(machineIds);
    }

    public static int level(String machineId) {
        return PROGRESS.getLevel(machineId);
    }

    public static long xp(String machineId) {
        return PROGRESS.getXp(machineId);
    }

    public static int maxLevel() {
        return PROGRESS.getMaxLevel();
    }

    public static int maxLevel(String machineId) {
        return PROGRESS.getMaxLevel(machineId);
    }

    public static boolean isLimitBroken() {
        return PROGRESS.isLimitBroken();
    }

    public static boolean isBranchLimitBroken(Branch branch) {
        return PROGRESS.isBranchLimitBroken(branch);
    }

    public static boolean isMachineLimitBroken(String machineId) {
        return PROGRESS.isMachineLimitBroken(machineId);
    }

    public static boolean canLimitBreakBranch(Branch branch) {
        return PROGRESS.canLimitBreakBranch(branch);
    }

    public static int upgradeLimit(String machineId) {
        return PROGRESS.getUpgradeLimit(machineId);
    }

    public static double masterySpeed(String machineId) {
        return MachineLevelMath.masterySpeedMultiplier(level(machineId));
    }

    public static int masteryEnergySavingPercent(String machineId) {
        return MachineLevelMath.masteryEnergySavingPercent(level(machineId));
    }

    /** Phase 97: appends craft unlock requirement or unlocked badge to tracked machine item tooltips. */
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !Config.enableCraftUnlockGate()) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return;
        }
        MachineSkillTree.Node node = MachineSkillTree.byId(key.getPath());
        if (node == null || node.parents().isEmpty()) {
            return;
        }
        List<MissingPrereq> missing = CraftUnlockLogic.missingPrerequisites(node.machineId(), ClientMachineProgress::level);
        if (missing.isEmpty()) {
            event.getToolTip().add(Component.translatable("tooltip.industrialcore.craft_unlocked")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            Minecraft mc = Minecraft.getInstance();
            boolean creative = mc.player != null && mc.player.isCreative();
            for (MissingPrereq mp : missing) {
                Component parentName = Component.translatable("block.industrialcore." + mp.parentId());
                event.getToolTip().add(Component.translatable(
                                "tooltip.industrialcore.craft_locked_req",
                                parentName, mp.requiredLevel(), mp.currentLevel())
                        .withStyle(creative ? ChatFormatting.YELLOW : ChatFormatting.RED));
            }
        }
    }

    public static void appendUpgradeTooltip(List<Component> lines, String machineId) {
        int lvl = level(machineId);
        int maxLvl = maxLevel(machineId);
        int cap = upgradeLimit(machineId);
        lines.add(Component.translatable("gui.industrialcore.tooltip.machine_level",
                        lvl, maxLvl, cap, MachineLevelMath.MAX_UPGRADE_CAP)
                .withStyle(isMachineLimitBroken(machineId) ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        if (lvl > MachineLevelMath.NORMAL_MAX_LEVEL) {
            lines.add(Component.translatable("gui.industrialcore.tooltip.machine_mastery",
                            String.format(Locale.ROOT, "%.2f", masterySpeed(machineId)),
                            masteryEnergySavingPercent(machineId))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void appendMotorTooltip(List<Component> lines, String machineId, int installedMotors) {
        double mult = com.industrialcore.machine.UpgradeMath.processingSpeedMultiplier(installedMotors);
        lines.add(Component.translatable("gui.industrialcore.tooltip.motor_title")
                .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("gui.industrialcore.tooltip.motor_effect",
                        installedMotors, String.format(Locale.ROOT, "%.2f", mult))
                .withStyle(ChatFormatting.GREEN));
        appendUpgradeTooltip(lines, machineId);
    }

    public static void appendBatteryTooltip(List<Component> lines, String machineId,
                                            int installedBatteries, boolean fuelMachine) {
        int savePct = com.industrialcore.machine.UpgradeMath.batterySavingPercent(installedBatteries);
        lines.add(Component.translatable("gui.industrialcore.tooltip.battery_title")
                .withStyle(ChatFormatting.WHITE));
        if (fuelMachine) {
            int bonusPct = installedBatteries <= 0 ? 0
                    : (int) Math.round((1.0D / com.industrialcore.machine.UpgradeMath.energyCostMultiplier(installedBatteries) - 1.0D) * 100.0D);
            lines.add(Component.translatable("gui.industrialcore.tooltip.battery_fuel_effect",
                            installedBatteries, bonusPct)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            int bonusFe = installedBatteries * com.industrialcore.machine.UpgradeMath.DEFAULT_CAPACITY_PER_BATTERY;
            lines.add(Component.translatable("gui.industrialcore.tooltip.battery_fe_effect",
                            installedBatteries, String.format(Locale.ROOT, "%,d", bonusFe), savePct)
                    .withStyle(ChatFormatting.AQUA));
        }
        appendUpgradeTooltip(lines, machineId);
    }
}
