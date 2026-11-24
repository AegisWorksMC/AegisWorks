package com.industrialcore.command;

import com.industrialcore.IndustrialCore;
import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineProgressSavedData;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.level.PlayerMachineProgress;
import com.industrialcore.machine.MiningProfiler;
import com.industrialcore.machine.MiningScheduler;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Operator-only server commands for factory diagnostics and Phase 84 machine-level testing.
 *
 * <ul>
 *   <li>{@code /industrialcore perf} — live mining profiler &amp; scheduler counters.</li>
 *   <li>{@code /industrialcore level status [player]} — prints limit-break state, tree unlock count
 *       and machine levels/XP/caps.</li>
 *   <li>{@code /industrialcore level set <machine|all> <level> [player]} — sets machine level.</li>
 *   <li>{@code /industrialcore level addxp <machine|all> <xp> [player]} — adds XP (respecting caps).</li>
 *   <li>{@code /industrialcore level limitbreak <true|false> [player]} — toggles limit-break flag.</li>
 *   <li>{@code /industrialcore level reset [player]} — resets all progress for the player.</li>
 * </ul>
 */
public final class ModCommands {
    private static final SuggestionProvider<CommandSourceStack> MACHINE_SUGGESTIONS =
            (ctx, builder) -> {
                List<String> ids = new ArrayList<>(MachineSkillTree.totalNodes() + 1);
                ids.add("all");
                for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
                    ids.add(node.machineId());
                }
                return SharedSuggestionProvider.suggest(ids, builder);
            };

    private ModCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var levelCmd = Commands.literal("level")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showStatus(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("machine", StringArgumentType.word())
                                .suggests(MACHINE_SUGGESTIONS)
                                .then(Commands.argument("level", IntegerArgumentType.integer(
                                                MachineLevelMath.MIN_LEVEL,
                                                MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL))
                                        .executes(ctx -> setLevel(ctx.getSource(),
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "machine"),
                                                IntegerArgumentType.getInteger(ctx, "level")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> setLevel(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "machine"),
                                                        IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("machine", StringArgumentType.word())
                                .suggests(MACHINE_SUGGESTIONS)
                                .then(Commands.argument("xp", LongArgumentType.longArg(1L))
                                        .executes(ctx -> addXp(ctx.getSource(),
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "machine"),
                                                LongArgumentType.getLong(ctx, "xp")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> addXp(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "machine"),
                                                        LongArgumentType.getLong(ctx, "xp")))))))
                .then(Commands.literal("limitbreak")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setLimitBreak(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
                                        BoolArgumentType.getBool(ctx, "enabled")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setLimitBreak(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetProgress(ctx.getSource(),
                                ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetProgress(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));

        event.getDispatcher().register(Commands.literal(IndustrialCore.MOD_ID)
                .then(Commands.literal("perf")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            MiningProfiler.Snapshot snapshot = MiningProfiler.snapshot();
                            source.sendSuccess(() -> Component.literal(String.format(
                                    "mining: ticks=%d running=%d avg=%.2fus max=%.2fus blocks/tick=%.3f visualSyncs=%d",
                                    snapshot.ticks(), snapshot.runningTicks(),
                                    snapshot.averageMicros(), snapshot.maxMicros(),
                                    snapshot.blocksPerTick(), MiningProfiler.visualSyncs())), false);
                            source.sendSuccess(() -> Component.literal(String.format(
                                    "scheduler: budget=%d/tick active=%d requested=%d granted=%d throttled=%d",
                                    MiningScheduler.currentBudget(), MiningScheduler.activeMachines(),
                                    MiningScheduler.requestedOperations(), MiningScheduler.grantedOperations(),
                                    MiningScheduler.throttledClaims())), false);
                            return 1;
                        }))
                .then(levelCmd));
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer target) {
        MachineProgressSavedData data = MachineProgressSavedData.get(target.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(target.getUUID());
        int unlocked = MachineSkillTree.unlockedCount(progress::getLevel);
        source.sendSuccess(() -> Component.literal(String.format(
                "[%s] limitBroken=%s maxLv=%d tree=%d/%d",
                target.getGameProfile().getName(),
                progress.isLimitBroken(),
                progress.getMaxLevel(),
                unlocked,
                MachineSkillTree.totalNodes())), false);
        for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
            String id = node.machineId();
            int lvl = progress.getLevel(id);
            long xp = progress.getXp(id);
            int cap = progress.getUpgradeLimit(id);
            if (xp > 0L || lvl > 1) {
                source.sendSuccess(() -> Component.literal(String.format(
                        "  %s: Lv.%d/%d xp=%d cap=%d/64 speed=x%.2f",
                        id, lvl, progress.getMaxLevel(), xp, cap,
                        MachineLevelMath.masterySpeedMultiplier(lvl))), false);
            }
        }
        return 1;
    }

    private static int setLevel(CommandSourceStack source, ServerPlayer target,
                                String machine, int level) {
        MachineProgressSavedData data = MachineProgressSavedData.get(target.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(target.getUUID());
        List<String> targets = resolveTargets(source, machine);
        if (targets.isEmpty()) {
            return 0;
        }
        boolean broke = false;
        for (String id : targets) {
            PlayerMachineProgress.ChangeResult res = progress.setLevel(id, level);
            broke |= res.justLimitBroke();
        }
        data.setDirty();
        MachineProgressSavedData.syncToPlayer(target, progress);
        if (broke) {
            MachineProgressSavedData.notifyProgress(target, targets.getLast(), progress,
                    new PlayerMachineProgress.ChangeResult(level, level, false, true, progress.getXp(targets.getLast())));
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Set %s -> Lv.%d (max=%d, limitBroken=%s) for %s",
                machine, Math.min(level, progress.getMaxLevel()), progress.getMaxLevel(),
                progress.isLimitBroken(), target.getGameProfile().getName())), true);
        return targets.size();
    }

    private static int addXp(CommandSourceStack source, ServerPlayer target,
                             String machine, long xp) {
        MachineProgressSavedData data = MachineProgressSavedData.get(target.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(target.getUUID());
        List<String> targets = resolveTargets(source, machine);
        if (targets.isEmpty()) {
            return 0;
        }
        boolean broke = false;
        for (String id : targets) {
            PlayerMachineProgress.ChangeResult res = progress.addXp(id, xp);
            broke |= res.justLimitBroke();
        }
        data.setDirty();
        MachineProgressSavedData.syncToPlayer(target, progress);
        if (broke) {
            MachineProgressSavedData.notifyProgress(target, targets.getLast(), progress,
                    new PlayerMachineProgress.ChangeResult(1, progress.getLevel(targets.getLast()),
                            false, true, progress.getXp(targets.getLast())));
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Added +%d XP to %s (limitBroken=%s) for %s",
                xp, machine, progress.isLimitBroken(), target.getGameProfile().getName())), true);
        return targets.size();
    }

    private static int setLimitBreak(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        MachineProgressSavedData data = MachineProgressSavedData.get(target.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(target.getUUID());
        progress.setLimitBroken(enabled);
        data.setDirty();
        MachineProgressSavedData.syncToPlayer(target, progress);
        source.sendSuccess(() -> Component.literal(String.format(
                "Set limitBroken=%s (maxLv=%d) for %s",
                progress.isLimitBroken(), progress.getMaxLevel(), target.getGameProfile().getName())), true);
        return 1;
    }

    private static int resetProgress(CommandSourceStack source, ServerPlayer target) {
        MachineProgressSavedData data = MachineProgressSavedData.get(target.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(target.getUUID());
        progress.reset();
        data.setDirty();
        MachineProgressSavedData.syncToPlayer(target, progress);
        source.sendSuccess(() -> Component.literal(String.format(
                "Reset machine progress for %s", target.getGameProfile().getName())), true);
        return 1;
    }

    private static List<String> resolveTargets(CommandSourceStack source, String machine) {
        if ("all".equals(machine)) {
            List<String> all = new ArrayList<>(MachineSkillTree.totalNodes());
            for (MachineSkillTree.Node node : MachineSkillTree.nodes()) {
                all.add(node.machineId());
            }
            return all;
        }
        if (!MachineSkillTree.isTracked(machine)) {
            source.sendFailure(Component.literal("Unknown machine id: " + machine));
            return List.of();
        }
        return List.of(machine);
    }
}
